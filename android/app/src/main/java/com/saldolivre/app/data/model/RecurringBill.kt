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

enum class LaunchTrigger(val value: String) {
    @SerializedName("day_before")
    DAY_BEFORE("day_before"),

    @SerializedName("on_due_date")
    ON_DUE_DATE("on_due_date"),

    @SerializedName("day_after")
    DAY_AFTER("day_after"),

    @SerializedName("first_day_of_month")
    FIRST_DAY_OF_MONTH("first_day_of_month");

    companion object {
        fun from(value: String?): LaunchTrigger =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: DAY_BEFORE
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
    val cardId: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val startMonth: String? = null,
    val endMonth: String? = null,
    val installmentsCount: Int? = null,
    val launchTrigger: LaunchTrigger = LaunchTrigger.DAY_BEFORE
)
