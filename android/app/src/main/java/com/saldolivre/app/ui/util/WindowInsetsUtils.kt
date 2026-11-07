package com.saldolivre.app.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Retorna o padding inferior seguro somando a barra de navegação do sistema Android com uma margem extra.
 * Garante que botões primários e listas nunca fiquem encobertos pela NavigationBar nativa.
 */
@Composable
fun safeNavigationBarBottomPadding(extraDp: Dp = 24.dp): Dp {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return extraDp + navBarBottom
}
