package com.saldolivre.app.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

enum class EstimationRule(val value: String) {
    @SerializedName("fixed")
    FIXED("fixed"),

    @SerializedName("last_month")
    LAST_MONTH("last_month"),

    @SerializedName("avg_3_months")
    AVG_3_MONTHS("avg_3_months");

    companion object {
        fun from(value: String?): EstimationRule =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: FIXED
    }
}

@Immutable
data class RecurringBill(
    val id: String = "",
    val ownerUid: String = "",
    val description: String = "",
    val category: String = "Moradia",
    val subcategory: String? = null,
    val dueDay: Int = 10,
    val defaultAmount: Double = 0.0,
    val estimationRule: EstimationRule = EstimationRule.FIXED,
    val active: Boolean = true,
    val splitType: SplitType? = null,
    val paymentMethod: PaymentMethod? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val startMonth: String? = null,
    val endMonth: String? = null,
    val installmentsCount: Int? = null
)
