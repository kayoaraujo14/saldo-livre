package com.saldolivre.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiLoadBalancerTest {

    private lateinit var loadBalancer: GeminiLoadBalancer

    @Before
    fun setUp() {
        // Inicializa sem Context Android para testes unitários puros na JVM
        loadBalancer = GeminiLoadBalancer(context = null)
    }

    @Test
    fun `default models pool contains expected production models`() {
        val pool = GeminiLoadBalancer.DEFAULT_MODELS_POOL
        assertTrue(pool.contains("gemini-3.5-flash"))
        assertTrue(pool.contains("gemini-3.5-flash-lite"))
        assertTrue(pool.contains("gemini-pro-latest"))
    }

    @Test
    fun `resolveTargetModel routes simple transactional queries to preferred model`() {
        val queries = listOf(
            "Adicionar almoço de 45 reais no cartão",
            "Quanto sobrou no meu saldo livre?",
            "Lembrar de pagar a conta de luz amanhã",
            "Qual foi meu último gasto?"
        )

        for (query in queries) {
            val resolved = loadBalancer.resolveTargetModel(query, "gemini-3.5-flash")
            assertEquals("Deveria manter o modelo preferido para: $query", "gemini-3.5-flash", resolved)
        }
    }

    @Test
    fun `resolveTargetModel elevates complex financial queries to gemini-pro-latest`() {
        val complexQueries = listOf(
            "Faça uma análise completa dos meus gastos deste mês",
            "Gere um relatório trimestral detalhado",
            "Qual o diagnóstico da minha saúde financeira?",
            "Monte um DRE simples com minhas receitas e despesas",
            "Qual o balanço geral dos meus investimentos?",
            "Quero uma projeção dos próximos 6 meses de saldo",
            "Faça uma comparação entre os gastos de outubro e setembro",
            "Me mostre um resumo detalhado por categoria"
        )

        for (query in complexQueries) {
            val resolved = loadBalancer.resolveTargetModel(query, "gemini-3.5-flash")
            assertEquals("Deveria elevar para pro para: $query", "gemini-pro-latest", resolved)
        }
    }

    @Test
    fun `resolveTargetModel is case insensitive and handles variations`() {
        val resolved1 = loadBalancer.resolveTargetModel("ANÁLISE DE GASTOS", "gemini-3.5-flash")
        assertEquals("gemini-pro-latest", resolved1)

        val resolved2 = loadBalancer.resolveTargetModel("auditoria de contas", "gemini-3.5-flash")
        assertEquals("gemini-pro-latest", resolved2)
    }

    @Test
    fun `getSelectedModel defaults to modern flash model when no preferences saved`() {
        val model = loadBalancer.getSelectedModel()
        assertEquals("gemini-3.5-flash", model)
    }
}

