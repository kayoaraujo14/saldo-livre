package com.saldolivre.app.data.ai

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    }

    private val gson = Gson()
    private val prefs = context?.getSharedPreferences(PREFS_RULES, Context.MODE_PRIVATE)

    fun getLocalRules(uid: String): List<UserLearnedRule> {
        val key = "$KEY_RULES_PREFIX${if (uid.isBlank()) "guest" else uid}"
        val raw = prefs?.getString(key, null)
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<UserLearnedRule>>() {}.type
            gson.fromJson<List<UserLearnedRule>>(raw, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLocalRules(uid: String, rules: List<UserLearnedRule>) {
        val key = "$KEY_RULES_PREFIX${if (uid.isBlank()) "guest" else uid}"
        val json = gson.toJson(rules)
        prefs?.edit()?.putString(key, json)?.apply()
    }

    fun addRule(uid: String, ruleText: String): UserLearnedRule {
        val clean = ruleText.trim()
        val rule = UserLearnedRule(
            id = "rule_${UUID.randomUUID().toString().take(8)}",
            rule = clean,
            createdAt = System.currentTimeMillis(),
            active = true
        )
        val current = getLocalRules(uid).toMutableList()
        current.add(0, rule)
        saveLocalRules(uid, current)

        // Sincroniza com o Firestore
        if (uid.isNotBlank() && uid != "guest") {
            try {
                firestore.collection("agent_rules").document(rule.id).set(
                    mapOf(
                        "id" to rule.id,
                        "ownerUid" to uid,
                        "rule" to rule.rule,
                        "createdAt" to rule.createdAt,
                        "active" to rule.active
                    )
                )
            } catch (e: Exception) {
                // Ignore sync errors
            }
        }
        return rule
    }

    fun deleteRule(uid: String, ruleIdOrText: String) {
        val clean = ruleIdOrText.trim()
        val current = getLocalRules(uid).toMutableList()
        val target = current.find { it.id == clean || it.rule.contains(clean, ignoreCase = true) }
        if (target != null) {
            current.remove(target)
            saveLocalRules(uid, current)

            if (uid.isNotBlank() && uid != "guest") {
                try {
                    firestore.collection("agent_rules").document(target.id).delete()
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
        }
    }

    fun subscribeCloudRules(uid: String, onUpdate: (List<UserLearnedRule>) -> Unit): ListenerRegistration? {
        if (uid.isBlank() || uid == "guest") return null
        return try {
            firestore.collection("agent_rules")
                .whereEqualTo("ownerUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val cloudRules = mutableListOf<UserLearnedRule>()
                        for (doc in snapshot.documents) {
                            val id = doc.getString("id") ?: doc.id
                            val rule = doc.getString("rule") ?: ""
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                            val active = doc.getBoolean("active") ?: true
                            if (rule.isNotBlank()) {
                                cloudRules.add(
                                    UserLearnedRule(
                                        id = id,
                                        rule = rule,
                                        createdAt = createdAt,
                                        active = active
                                    )
                                )
                            }
                        }
                        if (cloudRules.isNotEmpty()) {
                            saveLocalRules(uid, cloudRules)
                            onUpdate(cloudRules)
                        }
                    }
                }
        } catch (e: Exception) {
            null
        }
    }
}
