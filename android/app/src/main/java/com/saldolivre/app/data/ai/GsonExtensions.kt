package com.saldolivre.app.data.ai

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Utilitários e extensões seguras para manipulação de JsonElement / JsonObject com Gson.
 *
 * Evita exceções do tipo UnsupportedOperationException ao chamar `.asString`, `.asDouble`,
 * `.asInt`, etc., quando o valor retornado pelo modelo Gemini for `JsonNull` (ex: "status": null).
 * Também evita ClassCastException em getAsJsonObject / getAsJsonArray.
 */

private fun parseLenientDouble(raw: String): Double? {
    val clean = raw.trim()
        .replace("R$", "", ignoreCase = true)
        .replace(" ", "")
    if (clean.isBlank()) return null

    // Caso contenha tanto ponto quanto vírgula (ex: 1.200,50 ou 1,200.50)
    if (clean.contains('.') && clean.contains(',')) {
        val lastDot = clean.lastIndexOf('.')
        val lastComma = clean.lastIndexOf(',')
        return if (lastComma > lastDot) {
            // Formato brasileiro: 1.200,50 -> 1200.50
            clean.replace(".", "").replace(",", ".").toDoubleOrNull()
        } else {
            // Formato americano: 1,200.50 -> 1200.50
            clean.replace(",", "").toDoubleOrNull()
        }
    }

    // Caso contenha apenas vírgula (ex: 1200,50 -> 1200.50)
    if (clean.contains(',')) {
        return clean.replace(",", ".").toDoubleOrNull()
    }

    // Caso contenha apenas ponto ou número inteiro (ex: 1200.50 ou 1200)
    return clean.toDoubleOrNull()
}

fun JsonElement?.asStringOrNull(): String? {
    if (this == null || this.isJsonNull) return null
    return try {
        if (this.isJsonPrimitive) this.asString else this.toString()
    } catch (_: Exception) {
        null
    }
}

fun JsonElement?.asDoubleOrNull(): Double? {
    if (this == null || this.isJsonNull) return null
    return try {
        if (this.isJsonPrimitive) {
            val prim = this.asJsonPrimitive
            if (prim.isNumber) prim.asDouble else parseLenientDouble(prim.asString)
        } else null
    } catch (_: Exception) {
        null
    }
}

fun JsonElement?.asIntOrNull(): Int? {
    if (this == null || this.isJsonNull) return null
    return try {
        if (this.isJsonPrimitive) {
            val prim = this.asJsonPrimitive
            if (prim.isNumber) prim.asInt else prim.asString.trim().toDoubleOrNull()?.toInt()
        } else null
    } catch (_: Exception) {
        null
    }
}

fun JsonElement?.asBooleanOrNull(): Boolean? {
    if (this == null || this.isJsonNull) return null
    return try {
        if (this.isJsonPrimitive) {
            val prim = this.asJsonPrimitive
            if (prim.isBoolean) {
                prim.asBoolean
            } else {
                val str = prim.asString.trim().lowercase()
                when (str) {
                    "true", "1", "sim", "s", "yes", "y" -> true
                    "false", "0", "nao", "não", "n", "no" -> false
                    else -> null
                }
            }
        } else null
    } catch (_: Exception) {
        null
    }
}

fun JsonObject?.getStringOrNull(key: String): String? = this?.get(key).asStringOrNull()

fun JsonObject?.getDoubleOrNull(key: String): Double? = this?.get(key).asDoubleOrNull()

fun JsonObject?.getIntOrNull(key: String): Int? = this?.get(key).asIntOrNull()

fun JsonObject?.getBooleanOrNull(key: String): Boolean? = this?.get(key).asBooleanOrNull()

fun JsonObject?.getAsJsonObjectOrNull(key: String): JsonObject? {
    if (this == null) return null
    val elem = get(key)
    return if (elem != null && !elem.isJsonNull && elem.isJsonObject) elem.asJsonObject else null
}

fun JsonObject?.getAsJsonArrayOrNull(key: String): JsonArray? {
    if (this == null) return null
    val elem = get(key)
    return if (elem != null && !elem.isJsonNull && elem.isJsonArray) elem.asJsonArray else null
}
