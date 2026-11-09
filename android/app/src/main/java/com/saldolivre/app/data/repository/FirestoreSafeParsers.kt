package com.saldolivre.app.data.repository

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Utilitários defensivos para leitura e extração de campos em `DocumentSnapshot` e mapas.
 * Evita `java.lang.ClassCastException` quando números inteiros no Firestore
 * (ex: 50, 100) são devolvidos pela SDK como `java.lang.Long` em vez de `Double`,
 * ou quando números decimais/strings são fornecidos para campos inteiros/booleanos.
 */
fun safeExtractDouble(raw: Any?): Double? {
    if (raw == null) return null
    return when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull()
        else -> null
    }
}

fun safeExtractLong(raw: Any?): Long? {
    if (raw == null) return null
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }
}

fun safeExtractInt(raw: Any?): Int? = safeExtractLong(raw)?.toInt()

fun safeExtractBoolean(raw: Any?): Boolean? {
    if (raw == null) return null
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> when (raw.trim().lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
        else -> null
    }
}

fun DocumentSnapshot.getDoubleSafe(field: String): Double? = safeExtractDouble(get(field))

fun DocumentSnapshot.getLongSafe(field: String): Long? = safeExtractLong(get(field))

fun DocumentSnapshot.getIntSafe(field: String): Int? = safeExtractInt(get(field))

fun DocumentSnapshot.getBooleanSafe(field: String): Boolean? = safeExtractBoolean(get(field))
