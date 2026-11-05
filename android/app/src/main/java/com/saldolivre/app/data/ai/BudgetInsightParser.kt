package com.saldolivre.app.data.ai

import com.saldolivre.app.data.model.BudgetInsight
import com.saldolivre.app.data.model.BudgetInsightAction
import com.saldolivre.app.data.model.BudgetInsightActionType
import com.saldolivre.app.data.model.BudgetInsightSeverity
import org.json.JSONObject

/**
 * Converte a resposta JSON do Gemini em [BudgetInsight] validados.
 * Qualquer ação que referencie categoria inexistente ou valor fora do razoável é descartada
 * (o insight textual é mantido), para que a IA nunca dispare uma alteração perigosa.
 */
object BudgetInsightParser {
    private const val MAX_INSIGHTS = 4
    private const val MAX_ADJUST_ABS = 5000.0

    fun parse(raw: String, validCategories: Collection<String>): List<BudgetInsight> {
        val clean = raw.replace("```json", "").replace("```", "").trim()
        val root = try {
            JSONObject(clean)
        } catch (e: Exception) {
            return emptyList()
        }
        val array = root.optJSONArray("insights") ?: return emptyList()
        val canonical = validCategories.associateBy { it.trim().lowercase() }

        val result = mutableListOf<BudgetInsight>()
        for (i in 0 until array.length()) {
            if (result.size >= MAX_INSIGHTS) break
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title", "").trim()
            val message = obj.optString("message", "").trim()
            if (title.isBlank() || message.isBlank()) continue

            val severity = when (obj.optString("severity", "INFO").trim().uppercase()) {
                "DANGER" -> BudgetInsightSeverity.DANGER
                "ATTENTION" -> BudgetInsightSeverity.ATTENTION
                else -> BudgetInsightSeverity.INFO
            }

            result.add(
                BudgetInsight(
                    id = "ai_$i",
                    severity = severity,
                    title = title.take(70),
                    message = message.take(220),
                    action = parseAction(obj.optJSONObject("action"), canonical, title),
                    fromAi = true
                )
            )
        }
        return result.sortedByDescending { it.severity.ordinal }
    }

    private fun parseAction(
        obj: JSONObject?,
        canonical: Map<String, String>,
        fallbackTitle: String
    ): BudgetInsightAction? {
        obj ?: return null
        return when (obj.optString("type", "NONE").trim().uppercase()) {
            "ADJUST_LIMIT" -> {
                val category = canonical[obj.optString("category", "").trim().lowercase()] ?: return null
                val amount = obj.optDouble("amount", 0.0)
                if (amount.isNaN() || amount == 0.0 || kotlin.math.abs(amount) > MAX_ADJUST_ABS) return null
                // Valores sempre redondos (múltiplos de R$ 10) - nunca centavos quebrados
                val rounded = kotlin.math.round(amount / 10.0) * 10.0
                if (rounded == 0.0) return null
                val label = obj.optString("label", "").trim().ifBlank {
                    if (rounded > 0) "Subir teto em R$ ${rounded.toInt()}" else "Reduzir teto em R$ ${kotlin.math.abs(rounded).toInt()}"
                }
                BudgetInsightAction(
                    type = BudgetInsightActionType.ADJUST_LIMIT,
                    label = label.take(32),
                    category = category,
                    amount = rounded
                )
            }
            "ASK_COPILOT" -> {
                val prompt = obj.optString("prompt", "").trim().ifBlank { "Me ajude com: $fallbackTitle" }
                BudgetInsightAction(
                    type = BudgetInsightActionType.ASK_COPILOT,
                    label = obj.optString("label", "").trim().ifBlank { "Perguntar à IA" }.take(32),
                    prompt = prompt
                )
            }
            else -> null
        }
    }
}
