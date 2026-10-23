package com.saldolivre.app.data.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saldolivre.app.data.model.UserLearnedRule
import java.util.UUID

class LearnedRulesManager(
    private val context: Context?,
    private val firestore: FirebaseFirestore = runCatching { FirebaseFirestore.getInstance() }.getOrElse { null } ?: FirebaseFirestore.getInstance()
) {
    companion object {
        private const val PREFS_RULES = "saldo_livre_agent_rules_prefs"
        private const val KEY_RULES_PREFIX = "learned_rules_"
        private const val COLLECTION_USERS = "users"
        private const val SUBCOLLECTION_AGENT_RULES = "agent_rules"

        // Cache em memória para acesso instantâneo sem I/O síncrono
        private val memoryRulesCache = java.util.concurrent.ConcurrentHashMap<String, List<UserLearnedRule>>()
    }

    private val gson = Gson()
    private val prefs = context?.getSharedPreferences(PREFS_RULES, Context.MODE_PRIVATE)

    fun getLocalRules(uid: String): List<UserLearnedRule> {
        val cacheKey = if (uid.isBlank()) "guest" else uid
        memoryRulesCache[cacheKey]?.let { return it }

        val key = "$KEY_RULES_PREFIX$cacheKey"
        val raw = prefs?.getString(key, null)
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<UserLearnedRule>>() {}.type
            val result: List<UserLearnedRule> = gson.fromJson(raw, type) ?: emptyList()
            memoryRulesCache[cacheKey] = result
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLocalRules(uid: String, rules: List<UserLearnedRule>) {
        val cacheKey = if (uid.isBlank()) "guest" else uid
        memoryRulesCache[cacheKey] = rules
        val key = "$KEY_RULES_PREFIX$cacheKey"
        val json = gson.toJson(rules)
        prefs?.edit()?.putString(key, json)?.apply()
    }

    fun addRule(uid: String, ruleText: String, category: String = "Geral"): UserLearnedRule {
        val clean = ruleText.trim()
        val now = System.currentTimeMillis()
        val suffix = UUID.randomUUID().toString().replace("-", "").take(4)
        val rule = UserLearnedRule(
            id = "rule_${now}_$suffix",
            rule = clean,
            category = category,
            createdAt = now,
            active = true
        )
        val current = getLocalRules(uid)
            .filterNot { it.rule.equals(clean, ignoreCase = true) }
            .toMutableList()
        current.add(0, rule)
        saveLocalRules(uid, current)

        // Sincroniza no mesmo caminho da Web: /users/{uid}/agent_rules/{ruleId}
        if (uid.isNotBlank() && uid != "guest") {
            try {
                val payload = mapOf(
                    "id" to rule.id,
                    "rule" to rule.rule,
                    "category" to rule.category,
                    "createdAt" to rule.createdAt,
                    "active" to rule.active,
                    "ownerUid" to uid
                )
                firestore.collection(COLLECTION_USERS)
                    .document(uid)
                    .collection(SUBCOLLECTION_AGENT_RULES)
                    .document(rule.id)
                    .set(payload, SetOptions.merge())
            } catch (e: Exception) {
                // Ignore sync errors gracefully
            }
        }
        return rule
    }

    fun deleteRule(uid: String, ruleIdOrText: String) {
        val clean = ruleIdOrText.trim()
        val current = getLocalRules(uid).toMutableList()
        val removed = current.filter { it.id == clean || it.rule.contains(clean, ignoreCase = true) }
        if (removed.isNotEmpty()) {
            current.removeAll(removed.toSet())
            saveLocalRules(uid, current)
        }

        if (uid.isNotBlank() && uid != "guest") {
            val idsToDelete = if (removed.isNotEmpty()) removed.map { it.id } else listOf(clean)
            for (targetId in idsToDelete) {
                try {
                    firestore.collection(COLLECTION_USERS)
                        .document(uid)
                        .collection(SUBCOLLECTION_AGENT_RULES)
                        .document(targetId)
                        .delete()
                    // Limpa também eventual documento legado na coleção raiz
                    firestore.collection(SUBCOLLECTION_AGENT_RULES)
                        .document(targetId)
                        .delete()
                } catch (e: Exception) {
                    // Ignore delete errors
                }
            }
        }
    }

    fun subscribeCloudRules(uid: String, onUpdate: (List<UserLearnedRule>) -> Unit): ListenerRegistration? {
        if (uid.isBlank() || uid == "guest") return null

        // Migra silenciosamente eventuais regras antigas salvas na coleção raiz /agent_rules para /users/{uid}/agent_rules
        try {
            firestore.collection(SUBCOLLECTION_AGENT_RULES)
                .whereEqualTo("ownerUid", uid)
                .get()
                .addOnSuccessListener { legacySnap ->
                    if (legacySnap != null && !legacySnap.isEmpty) {
                        for (doc in legacySnap.documents) {
                            val id = doc.getString("id") ?: doc.id
                            val ruleText = doc.getString("rule")?.trim().orEmpty()
                            if (ruleText.isNotBlank()) {
                                val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
                                val category = doc.getString("category") ?: "Geral"
                                val active = doc.getBoolean("active") ?: true
                                val payload = mapOf(
                                    "id" to id,
                                    "rule" to ruleText,
                                    "category" to category,
                                    "createdAt" to createdAt,
                                    "active" to active,
                                    "ownerUid" to uid
                                )
                                firestore.collection(COLLECTION_USERS)
                                    .document(uid)
                                    .collection(SUBCOLLECTION_AGENT_RULES)
                                    .document(id)
                                    .set(payload, SetOptions.merge())
                                doc.reference.delete()
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            // Ignore legacy migration errors
        }

        return try {
            firestore.collection(COLLECTION_USERS)
                .document(uid)
                .collection(SUBCOLLECTION_AGENT_RULES)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val cloudRules = mutableListOf<UserLearnedRule>()
                        for (doc in snapshot.documents) {
                            val id = doc.getString("id") ?: doc.id
                            val rule = doc.getString("rule")?.trim().orEmpty()
                            val category = doc.getString("category") ?: "Geral"
                            val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
                            val active = doc.getBoolean("active") ?: true
                            if (rule.isNotBlank()) {
                                cloudRules.add(
                                    UserLearnedRule(
                                        id = id,
                                        rule = rule,
                                        category = category,
                                        createdAt = createdAt,
                                        active = active
                                    )
                                )
                            }
                        }
                        val sorted = cloudRules.sortedByDescending { it.createdAt }
                        saveLocalRules(uid, sorted)
                        onUpdate(sorted)
                    }
                }
        } catch (e: Exception) {
            null
        }
    }
}
