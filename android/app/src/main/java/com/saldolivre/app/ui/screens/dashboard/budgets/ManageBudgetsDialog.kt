package com.saldolivre.app.ui.screens.dashboard.budgets

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.*
import com.saldolivre.app.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt
@Composable
fun ManageBudgetsDialog(
    initialTab: Int = 0,
    currentBudgets: Map<String, Double>,
    report: CategoryBudgetReport,
    suggestedBudgets: SuggestedBudgetsResult,
    rebalance: BudgetRebalanceResult,
    smartBudgetPlan: SmartBudgetPlan? = null,
    isGeneratingSmartBudget: Boolean = false,
    onGenerateSmartBudget: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSaveBudgets: (Map<String, Double>) -> Unit,
    onApplyRebalance: (String?, String?, Double?) -> Unit
) {
    var activeTab by remember { mutableIntStateOf(initialTab) }
    val budgetInputs = remember(currentBudgets) {
        mutableStateMapOf<String, String>().apply {
            val normalizedCurrent = mutableMapOf<String, Double>()
            for ((rawCat, valAmt) in currentBudgets) {
                if (valAmt > 0.0) {
                    val norm = FinanceEngine.normalizeBudgetCategory(rawCat)
                    normalizedCurrent[norm] = FinanceEngine.round2((normalizedCurrent[norm] ?: 0.0) + valAmt)
                }
            }
            for (cat in BUDGETABLE_EXPENSE_CATEGORIES) {
                val norm = FinanceEngine.normalizeBudgetCategory(cat)
                val v = currentBudgets[cat] ?: normalizedCurrent[norm] ?: 0.0
                if (v > 0.0) {
                    put(cat, if (v % 1.0 == 0.0) v.toInt().toString() else v.toString())
                }
            }
        }
    }

    val expectedIncome = if (report.totalAvailableIncome > 0.0) report.totalAvailableIncome else if (report.expectedIncome > 0.0) report.expectedIncome else suggestedBudgets.expectedIncome
    val formTotal = BUDGETABLE_EXPENSE_CATEGORIES.sumOf { cat ->
        val clean = (budgetInputs[cat] ?: "").replace(",", ".").trim()
        val v = clean.toDoubleOrNull() ?: 0.0
        if (v > 0.0) v else 0.0
    }
    val isOverIncome = expectedIncome > 0.0 && formTotal > expectedIncome
    val incomeUsagePct = if (expectedIncome > 0.0) ((formTotal / expectedIncome) * 100.0).roundToInt() else 0
    val unallocatedIncome = if (expectedIncome > 0.0) FinanceEngine.round2(expectedIncome - formTotal) else 0.0

    val calibrateInputsToIncome = {
        if (expectedIncome > 0.0 && formTotal > expectedIncome) {
            val parsedVals = mutableMapOf<String, Double>()
            for (cat in BUDGETABLE_EXPENSE_CATEGORIES) {
                val raw = (budgetInputs[cat] ?: "").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                if (raw > 0.0) parsedVals[cat] = raw
            }

            // Piso inegociÃ¡vel por categoria: o que jÃ¡ foi gasto no mÃªs ou custo fixo contratual
            val floorVals = mutableMapOf<String, Double>()
            for ((cat, raw) in parsedVals) {
                val spentItem = report.items.find {
                    FinanceEngine.normalizeBudgetCategory(it.category) == FinanceEngine.normalizeBudgetCategory(cat)
                }
                val spentSoFar = spentItem?.spent ?: 0.0
                val isFixed = FinanceEngine.isFixedBudgetCategory(cat)
                floorVals[cat] = if (isFixed) raw else spentSoFar
            }

            val fixedCategories = parsedVals.keys.filter { FinanceEngine.isFixedBudgetCategory(it) }
            val flexCategories = parsedVals.keys.filter { !FinanceEngine.isFixedBudgetCategory(it) }
            val fixedSum = fixedCategories.sumOf { parsedVals[it] ?: 0.0 }
            val availableForFlex = expectedIncome - fixedSum

            if (availableForFlex > 0.0 && flexCategories.isNotEmpty()) {
                val flexFloorsSum = flexCategories.sumOf { floorVals[it] ?: 0.0 }
                if (availableForFlex >= flexFloorsSum) {
                    val discretionaryPool = availableForFlex - flexFloorsSum
                    val currentDiscretionarySum = flexCategories.sumOf {
                        kotlin.math.max(0.0, (parsedVals[it] ?: 0.0) - (floorVals[it] ?: 0.0))
                    }
                    for (cat in flexCategories) {
                        val floor = floorVals[cat] ?: 0.0
                        val excess = kotlin.math.max(0.0, (parsedVals[cat] ?: 0.0) - floor)
                        val share = if (currentDiscretionarySum > 0.0) {
                            (excess / currentDiscretionarySum) * discretionaryPool
                        } else {
                            discretionaryPool / flexCategories.size
                        }
                        val calibrated = kotlin.math.floor((floor + share) / 5.0) * 5.0
                        budgetInputs[cat] = if (calibrated % 1.0 == 0.0) calibrated.toInt().toString() else FinanceEngine.round2(calibrated).toString()
                    }
                } else {
                    // Entrada nÃ£o cobre nem os pisos jÃ¡ gastos das categorias flexÃ­veis; fixa no piso gasto
                    for (cat in flexCategories) {
                        val floor = floorVals[cat] ?: 0.0
                        val calibrated = kotlin.math.max(10.0, kotlin.math.floor(floor / 5.0) * 5.0)
                        budgetInputs[cat] = if (calibrated % 1.0 == 0.0) calibrated.toInt().toString() else FinanceEngine.round2(calibrated).toString()
                    }
                }
            } else {
                // Se as despesas fixas sozinhas jÃ¡ consomem a renda prevista, mantÃ©m os fixos e ajusta flexÃ­veis para o mÃ­nimo
                for (cat in flexCategories) {
                    val floor = floorVals[cat] ?: 0.0
                    val minVal = if (floor > 0.0) floor else 10.0
                    budgetInputs[cat] = if (minVal % 1.0 == 0.0) minVal.toInt().toString() else FinanceEngine.round2(minVal).toString()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarkBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Envelopes OrÃ§amentÃ¡rios Inteligentes",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Defina tetos mensais por categoria ou reequilibre com IA",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextSecondary)
                    }
                }

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { activeTab = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == 0) Emerald600 else DarkSurfaceVariant,
                            contentColor = if (activeTab == 0) Color.White else TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Definir Tetos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { activeTab = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == 1) Purple500 else DarkSurfaceVariant,
                            contentColor = if (activeTab == 1) Color.White else TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (rebalance.suggestions.isNotEmpty()) {
                                "Reequilibrar IA (${rebalance.suggestions.size})"
                            } else {
                                "Reequilibrar IA"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (activeTab == 0) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // TermÃ´metro ao Vivo de AlocaÃ§Ã£o da Entrada Prevista do MÃªs
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isOverIncome) Rose500.copy(alpha = 0.12f) else DarkSurface,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isOverIncome) Rose400.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (report.carryoverAmount > 0.0) "ORÃ‡AMENTO TOTAL VS DISPONÃVEL NO MÃŠS" else "ORÃ‡AMENTO TOTAL VS ENTRADA DO MÃŠS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "${FinanceEngine.formatCurrency(formTotal)} / ${if (expectedIncome > 0.0) FinanceEngine.formatCurrency(expectedIncome) else "â€”"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isOverIncome) Rose400 else Color.White
                                    )
                                    if (report.carryoverAmount > 0.0) {
                                        Text(
                                            text = "Entrada: ${FinanceEngine.formatCurrency(report.expectedIncome)} â€¢ Sobra: +${FinanceEngine.formatCurrency(report.carryoverAmount)}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Cyan400
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isOverIncome) {
                                        Button(
                                            onClick = { calibrateInputsToIncome() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Calibrar Ã  Entrada", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (formTotal > 0.0) {
                                        OutlinedButton(
                                            onClick = { budgetInputs.clear() },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Limpar", fontSize = 10.sp, color = TextSecondary)
                                        }
                                    }
                                }
                            }

                            if (expectedIncome > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = 0.10f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction = (incomeUsagePct / 100f).coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                when {
                                                    isOverIncome -> Rose400
                                                    incomeUsagePct > 85 -> Amber400
                                                    else -> Emerald400
                                                }
                                            )
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isOverIncome) {
                                            "Teto total excede a entrada em ${FinanceEngine.formatCurrency(abs(unallocatedIncome))}"
                                        } else {
                                            "$incomeUsagePct% da entrada alocada nos envelopes"
                                        },
                                        fontSize = 10.sp,
                                        color = if (isOverIncome) Rose400 else TextSecondary
                                    )
                                    if (!isOverIncome) {
                                        Text(
                                            text = "Livre: ${FinanceEngine.formatCurrency(unallocatedIncome)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Emerald400
                                        )
                                    }
                                }
                            }
                        }

                        // Feature: Sugerir OrÃ§amento com Gemini AI
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Purple500.copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, Purple400.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Purple400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "âœ¨ Planejador Inteligente Gemini",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    if (onGenerateSmartBudget != null) {
                                        Button(
                                            onClick = onGenerateSmartBudget,
                                            enabled = !isGeneratingSmartBudget,
                                            colors = ButtonDefaults.buttonColors(containerColor = Purple500, contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            if (isGeneratingSmartBudget) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Planejando...", fontSize = 10.sp)
                                            } else {
                                                Text(if (smartBudgetPlan != null) "Repensar IA" else "Planejar com IA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                if (isGeneratingSmartBudget) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Purple400, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Gemini analisando contas a vencer e histÃ³rico...", fontSize = 11.sp, color = TextSecondary)
                                    }
                                } else if (smartBudgetPlan != null) {
                                    Text(
                                        text = smartBudgetPlan.summaryInsight,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = TextPrimary
                                    )
                                    Button(
                                        onClick = {
                                            for (a in smartBudgetPlan.allocations) {
                                                val v = a.suggestedLimit
                                                budgetInputs[a.category] = if (v % 1.0 == 0.0) v.toInt().toString() else FinanceEngine.round2(v).toString()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.fillMaxWidth().height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Aplicar SugestÃµes da IA nos Campos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else if (suggestedBudgets.suggestedBudgets.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "SugestÃ£o estatÃ­stica: ${FinanceEngine.formatCurrency(suggestedBudgets.totalSuggested)}",
                                            fontSize = 10.5.sp,
                                            color = TextSecondary
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                for ((cat, lim) in suggestedBudgets.suggestedBudgets) {
                                                    budgetInputs[cat] = lim.toInt().toString()
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.4f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Text("Usar HistÃ³rico", fontSize = 10.sp, color = Emerald400, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        for (category in BUDGETABLE_EXPENSE_CATEGORIES) {
                            val spentItem = report.items.find {
                                FinanceEngine.normalizeBudgetCategory(it.category) == FinanceEngine.normalizeBudgetCategory(category)
                            }
                            val spentAmt = spentItem?.spentAmount ?: 0.0
                            val pendingAmt = spentItem?.pendingAmount ?: 0.0
                            val minFloor = spentAmt + pendingAmt
                            val avg3m = suggestedBudgets.categoryAverages[category] ?: 0.0
                            val aiReason = smartBudgetPlan?.allocations?.find { it.category == category }?.reason

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        if (minFloor > 0.0) {
                                            Text(
                                                text = "MÃ­nimo comprometido: ${FinanceEngine.formatCurrency(minFloor)} (${FinanceEngine.formatCurrency(spentAmt)} pagos + ${FinanceEngine.formatCurrency(pendingAmt)} a vencer)",
                                                fontSize = 10.sp,
                                                color = Amber400
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = budgetInputs[category] ?: "",
                                        onValueChange = { raw ->
                                            budgetInputs[category] = raw.filter { it.isDigit() || it == '.' || it == ',' }
                                        },
                                        placeholder = { Text("0", fontSize = 11.sp, color = TextMuted) },
                                        prefix = { Text("R$ ", fontSize = 11.sp, color = TextSecondary) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(115.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White)
                                    )
                                }

                                if (!aiReason.isNullOrBlank()) {
                                    Text(
                                        text = "âœ¨ $aiReason",
                                        fontSize = 10.sp,
                                        color = Purple400,
                                        lineHeight = 14.sp
                                    )
                                }

                                // Quick adjustment chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (avg3m > 0.0) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                                .clickable {
                                                    val rounded = (kotlin.math.ceil(kotlin.math.max(minFloor, avg3m) / 10.0) * 10.0).toInt()
                                                    budgetInputs[category] = rounded.toString()
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("MÃ©dia 3M: ${FinanceEngine.formatCurrency(avg3m)}", fontSize = 9.5.sp, color = Cyan400)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                val current = (budgetInputs[category] ?: "").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                                                val next = kotlin.math.max(minFloor, current - 50.0)
                                                val rounded = (kotlin.math.ceil(next / 10.0) * 10.0).toInt()
                                                budgetInputs[category] = rounded.toString()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("-R$ 50", fontSize = 9.5.sp, color = TextSecondary)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                val current = (budgetInputs[category] ?: "").replace(",", ".").trim().toDoubleOrNull() ?: 0.0
                                                val next = current + 50.0
                                                val rounded = (kotlin.math.ceil(next / 10.0) * 10.0).toInt()
                                                budgetInputs[category] = rounded.toString()
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("+R$ 50", fontSize = 9.5.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    // Footer Save
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val parsed = mutableMapOf<String, Double>()
                                for ((cat, raw) in budgetInputs) {
                                    val clean = raw.replace(",", ".").trim()
                                    val v = clean.toDoubleOrNull() ?: 0.0
                                    if (v > 0.0) {
                                        parsed[cat] = FinanceEngine.round2(v)
                                    }
                                }
                                onSaveBudgets(parsed)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Salvar Envelopes",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Tab 1: Rebalanceamento IA
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (rebalance.suggestions.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface, RoundedCornerShape(14.dp))
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "OrÃ§amento Equilibrado!",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Nenhuma categoria precisa de compensaÃ§Ã£o no momento ou nÃ£o hÃ¡ sobras para realocar.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Text(
                                text = "A IA identificou envelopes com folga que podem cobrir os excessos deste mÃªs sem alterar seu orÃ§amento global:",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )

                            for (s in rebalance.suggestions) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkSurface, RoundedCornerShape(12.dp))
                                        .border(1.dp, Purple400.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${s.compensateFrom} âž” ${s.overspentCategory}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = FinanceEngine.formatCurrency(s.suggestedTransfer),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Purple400
                                        )
                                    }
                                    Text(
                                        text = "Retira da folga de ${FinanceEngine.formatCurrency(s.availableSurplus)} em ${s.compensateFrom} para cobrir o estouro de ${FinanceEngine.formatCurrency(s.overspentAmount)} em ${s.overspentCategory}.",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                    Button(
                                        onClick = {
                                            onApplyRebalance(s.compensateFrom, s.overspentCategory, s.suggestedTransfer)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Purple500, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.align(Alignment.End).height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text("Transferir Agora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    onApplyRebalance(null, null, null)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Aplicar Todo o Rebalanceamento IA", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
