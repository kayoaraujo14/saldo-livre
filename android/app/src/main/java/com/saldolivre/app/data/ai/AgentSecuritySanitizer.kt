package com.saldolivre.app.data.ai

import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.round

/**
 * Módulo de Segurança e Sanitização de Parâmetros para o Copiloto Gemini (Android).
 * Espelha as regras defensivas de `web/src/lib/agent/security.ts`.
 * Protege contra Prompt Injections em extratos bancários, estouros de números,
 * caracteres de controle e formatos corrompidos.
 */
object AgentSecuritySanitizer {

    private val CONTROL_CHARS_REGEX = Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F]""")
    private val HTML_TAGS_REGEX = Regex("""<[^>]*>""")

    private val PROMPT_INJECTION_PATTERNS = listOf(
        Regex("""\bignore\s+(all\s+)?previous\s+instructions\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdisregard\s+(all\s+)?earlier\s+instructions\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsystem\s+prompt\s*:""", RegexOption.IGNORE_CASE),
        Regex("""\byou\s+are\s+now\s+(in\s+)?developer\s+mode\b""", RegexOption.IGNORE_CASE),
        Regex("""\bforget\s+all\s+(prior|previous)\s+rules\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnew\s+instructions\s*:\s*delete\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbypass\s+all\s+safety\s+filters\b""", RegexOption.IGNORE_CASE),
        Regex("""\bexfiltrate\s+(data|database|keys)\b""", RegexOption.IGNORE_CASE)
    )

    fun detectPromptInjection(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return PROMPT_INJECTION_PATTERNS.any { it.containsMatchIn(text) }
    }

    fun sanitizeText(raw: String?, maxLength: Int = 300, fallback: String = ""): String {
        if (raw == null) return fallback
        var str = raw.trim()

        // 1. Remove caracteres de controle invisíveis
        str = CONTROL_CHARS_REGEX.replace(str, "")

        // 2. Remove tags HTML / scripts
        str = HTML_TAGS_REGEX.replace(str, "")

        // 3. Detecta e neutraliza tentativas de prompt injection em extratos bancários
        if (detectPromptInjection(str)) {
            str = str.replace(Regex("""ignore\s+previous\s+instructions""", RegexOption.IGNORE_CASE), "[neutralizado]")
            str = str.replace(Regex("""disregard\s+earlier\s+instructions""", RegexOption.IGNORE_CASE), "[neutralizado]")
            str = str.replace(Regex("""system\s+prompt\s*:""", RegexOption.IGNORE_CASE), "[neutralizado]:")
            str = str.replace(Regex("""developer\s+mode""", RegexOption.IGNORE_CASE), "[neutralizado]")
        }

        // 4. Normaliza espaços múltiplos
        str = Regex("""\s+""").replace(str, " ").trim()

        // 5. Trunca para limite seguro de caracteres
        if (str.length > maxLength) {
            str = str.take(maxLength).trim()
        }

        return if (str.isNotBlank()) str else fallback
    }

    fun sanitizeAmount(
        raw: Any?,
        min: Double = 0.01,
        max: Double = 100_000_000.0,
        allowNegative: Boolean = false
    ): Double? {
        if (raw == null) return null

        val num: Double = when (raw) {
            is Number -> raw.toDouble()
            is String -> {
                val clean = raw.trim().replace(" ", "").replace(",", ".")
                clean.toDoubleOrNull() ?: return null
            }
            else -> return null
        }

        // Bloqueia NaN, Infinity e -Infinity
        if (!num.isFinite()) return null

        // Verifica negatividade
        if (!allowNegative && num < 0.0) return null

        val absNum = abs(num)
        if (absNum < min || absNum > max) return null

        // Arredonda para 2 casas decimais
        return round(num * 100.0) / 100.0
    }

    fun sanitizePercentage(raw: Any?, min: Double = 0.0, max: Double = 100.0): Double? {
        if (raw == null) return null
        val num: Double = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().replace(",", ".").toDoubleOrNull() ?: return null
            else -> return null
        }
        if (!num.isFinite()) return null
        if (num < min || num > max) return null
        return round(num * 100.0) / 100.0
    }

    fun sanitizeIsoDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(clean) ?: return null

        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null

        if (year !in 2000..2099) return null
        if (month !in 1..12) return null
        if (day !in 1..31) return null

        return clean
    }

    fun sanitizeMonthKey(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        val match = Regex("""^(\d{4})-(\d{2})$""").matchEntire(clean) ?: return null

        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null

        if (year !in 2000..2099) return null
        if (month !in 1..12) return null

        return clean
    }

    fun escapeHtmlAndMarkdown(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("`", "&#96;")
    }
}
