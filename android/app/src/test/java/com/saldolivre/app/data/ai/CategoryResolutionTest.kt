package com.saldolivre.app.data.ai

import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryResolutionTest {

    @Test
    fun `resolves direct active categories case and accent insensitively`() {
        val r1 = CategoryRegistry.resolveStrictCategory("alimentacao", null, TransactionType.EXPENSE)
        assertEquals("Alimentação", r1.first)

        val r2 = CategoryRegistry.resolveStrictCategory("ALIMENTAÇÃO", null, TransactionType.EXPENSE)
        assertEquals("Alimentação", r2.first)

        val r3 = CategoryRegistry.resolveStrictCategory("saude", null, TransactionType.EXPENSE)
        assertEquals("Saúde", r3.first)

        val r4 = CategoryRegistry.resolveStrictCategory("SAÚDE", null, TransactionType.EXPENSE)
        assertEquals("Saúde", r4.first)

        val r5 = CategoryRegistry.resolveStrictCategory("salario & remuneracao", null, TransactionType.INCOME)
        assertEquals("Salário & Remuneração", r5.first)
    }

    @Test
    fun `resolves legacy categories to modern active categories`() {
        val lazer = CategoryRegistry.resolveStrictCategory("Lazer", null, TransactionType.EXPENSE)
        assertEquals("Lazer & Entretenimento", lazer.first)

        val assinaturas = CategoryRegistry.resolveStrictCategory("Assinaturas", null, TransactionType.EXPENSE)
        assertEquals("Assinaturas & Serviços", assinaturas.first)

        val compras = CategoryRegistry.resolveStrictCategory("Compras", null, TransactionType.EXPENSE)
        assertEquals("Compras & Estilo de Vida", compras.first)

        val educacao = CategoryRegistry.resolveStrictCategory("Educação", null, TransactionType.EXPENSE)
        assertEquals("Educação & Desenvolvimento", educacao.first)

        val financ = CategoryRegistry.resolveStrictCategory("Financiamentos & Empréstimos", null, TransactionType.EXPENSE)
        assertEquals("Financiamentos & Dívidas", financ.first)

        val receita = CategoryRegistry.resolveStrictCategory("Receita", null, TransactionType.INCOME)
        assertEquals("Outras Receitas", receita.first)

        val terceiros = CategoryRegistry.resolveStrictCategory("Cartão Terceiros", null, TransactionType.EXPENSE)
        assertEquals("Outros", terceiros.first)
    }

    @Test
    fun `resolves reverse subcategories passed in category field`() {
        val superm = CategoryRegistry.resolveStrictCategory("Supermercado", null, TransactionType.EXPENSE)
        assertEquals("Alimentação", superm.first)
        assertEquals("Supermercado", superm.second)

        val farm = CategoryRegistry.resolveStrictCategory("Farmácia", null, TransactionType.EXPENSE)
        assertEquals("Saúde", farm.first)
        assertEquals("Farmácia & Remédios", farm.second)

        val uber = CategoryRegistry.resolveStrictCategory("uber", null, TransactionType.EXPENSE)
        assertEquals("Transporte", uber.first)
        assertEquals("Aplicativos (Uber/99)", uber.second)

        val ifood = CategoryRegistry.resolveStrictCategory("ifood", null, TransactionType.EXPENSE)
        assertEquals("Alimentação", ifood.first)
        assertEquals("Delivery & Lanches", ifood.second)

        val sal = CategoryRegistry.resolveStrictCategory("salario principal", null, TransactionType.INCOME)
        assertEquals("Salário & Remuneração", sal.first)
        assertEquals("Salário Principal", sal.second)
    }

    @Test
    fun `resolves parent category when category is empty but subcategory is provided`() {
        val res = CategoryRegistry.resolveStrictCategory("", "Supermercado", TransactionType.EXPENSE)
        assertEquals("Alimentação", res.first)
        assertEquals("Supermercado", res.second)

        val res2 = CategoryRegistry.resolveStrictCategory(null, "Farmácia", TransactionType.EXPENSE)
        assertEquals("Saúde", res2.first)
        assertEquals("Farmácia & Remédios", res2.second)
    }

    @Test
    fun `normalizes subcategory alias when category is already resolved`() {
        val res = CategoryRegistry.resolveStrictCategory("Alimentação", "mercado", TransactionType.EXPENSE)
        assertEquals("Alimentação", res.first)
        assertEquals("Supermercado", res.second)

        val res2 = CategoryRegistry.resolveStrictCategory("Saúde", "remedio", TransactionType.EXPENSE)
        assertEquals("Saúde", res2.first)
        assertEquals("Farmácia & Remédios", res2.second)
    }

    @Test
    fun `rejects arbitrary categories and falls back strictly to Outros or Outras Receitas`() {
        val arbitraryExpense = CategoryRegistry.resolveStrictCategory("Categoria Inventada", null, TransactionType.EXPENSE)
        assertEquals("Outros", arbitraryExpense.first)

        val crazyExpense = CategoryRegistry.resolveStrictCategory("Cripto Loucura 123", null, TransactionType.EXPENSE)
        assertEquals("Outros", crazyExpense.first)

        val arbitraryIncome = CategoryRegistry.resolveStrictCategory("Renda Secreta", null, TransactionType.INCOME)
        assertEquals("Outras Receitas", arbitraryIncome.first)

        val typeMismatchExpenseToIncome = CategoryRegistry.resolveStrictCategory("Alimentação", null, TransactionType.INCOME)
        assertEquals("Outras Receitas", typeMismatchExpenseToIncome.first)

        val typeMismatchIncomeToExpense = CategoryRegistry.resolveStrictCategory("Salário & Remuneração", null, TransactionType.EXPENSE)
        assertEquals("Outros", typeMismatchIncomeToExpense.first)
    }

    @Test
    fun `nullifies invalid subcategories and cross-group mismatches strictly`() {
        // Arbitrary subcategory alongside valid category -> nullified
        val res1 = CategoryRegistry.resolveStrictCategory("Alimentação", "SubAlimentacaoInventada", TransactionType.EXPENSE)
        assertEquals("Alimentação", res1.first)
        assertNull(res1.second)

        // Cross-category mismatch (IPVA belongs to Impostos & Tributos, not Alimentação) -> nullified
        val res2 = CategoryRegistry.resolveStrictCategory("Alimentação", "IPVA", TransactionType.EXPENSE)
        assertEquals("Alimentação", res2.first)
        assertNull(res2.second)

        // Arbitrary subcategory alongside arbitrary category -> category falls back to Outros and subcategory is nullified
        val res3 = CategoryRegistry.resolveStrictCategory("Xurupita", "SubXurupita", TransactionType.EXPENSE)
        assertEquals("Outros", res3.first)
        assertNull(res3.second)

        // Valid subcategory for Outros is preserved when category falls back to Outros
        val res4 = CategoryRegistry.resolveStrictCategory("Xurupita", "Imprevistos", TransactionType.EXPENSE)
        assertEquals("Outros", res4.first)
        assertEquals("Imprevistos", res4.second)
    }

    @Test
    fun `uses guessFallback only when category was blank or null`() {
        val res = CategoryRegistry.resolveStrictCategory(
            rawCategory = "",
            rawSubcategory = null,
            type = TransactionType.EXPENSE,
            description = "Gasolina Posto Shell",
            guessFallback = { desc, _ -> Pair("Transporte", "Combustível") }
        )
        assertEquals("Transporte", res.first)
        assertEquals("Combustível", res.second)

        // When arbitrary category is passed, guessFallback must NOT override the rejection to Outros
        val resArbitrary = CategoryRegistry.resolveStrictCategory(
            rawCategory = "Categoria Maluca",
            rawSubcategory = null,
            type = TransactionType.EXPENSE,
            description = "Gasolina Posto Shell",
            guessFallback = { desc, _ -> Pair("Transporte", "Combustível") }
        )
        assertEquals("Outros", resArbitrary.first)
    }
}
