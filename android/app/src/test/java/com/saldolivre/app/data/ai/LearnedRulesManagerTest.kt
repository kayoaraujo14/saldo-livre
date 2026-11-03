package com.saldolivre.app.data.ai

import com.saldolivre.app.data.model.UserLearnedRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearnedRulesManagerTest {

    private lateinit var rulesManager: LearnedRulesManager
    private val testUid = "user_test_rules"

    @Before
    fun setUp() {
        rulesManager = LearnedRulesManager(context = null, firestore = null)
        // Limpa o cache para o usuário de teste
        rulesManager.saveLocalRules(testUid, emptyList())
    }

    @Test
    fun `addRule creates unique rule and inserts at top of list`() {
        val rule1 = rulesManager.addRule(testUid, "Sempre classificar iFood como Alimentação", "Categorização")
        assertNotNull(rule1.id)
        assertTrue(rule1.id.startsWith("rule_"))
        assertEquals("Sempre classificar iFood como Alimentação", rule1.rule)
        assertEquals("Categorização", rule1.category)
        assertTrue(rule1.active)

        val current = rulesManager.getLocalRules(testUid)
        assertEquals(1, current.size)
        assertEquals(rule1.id, current[0].id)

        // Adiciona segunda regra
        val rule2 = rulesManager.addRule(testUid, "Uber deve ser considerado Transporte", "Transporte")
        val updated = rulesManager.getLocalRules(testUid)
        assertEquals(2, updated.size)
        // Regra mais recente deve ser a primeira
        assertEquals(rule2.id, updated[0].id)
        assertEquals(rule1.id, updated[1].id)
    }

    @Test
    fun `addRule deduplicates existing rule with identical text case-insensitively`() {
        rulesManager.addRule(testUid, "Netflix é Assinatura", "Streaming")
        assertEquals(1, rulesManager.getLocalRules(testUid).size)

        // Adiciona a mesma regra com variação de maiúsculas/minúsculas
        val updatedRule = rulesManager.addRule(testUid, "netflix é assinatura", "Entretenimento")
        val rules = rulesManager.getLocalRules(testUid)

        // Não deve duplicar
        assertEquals(1, rules.size)
        assertEquals("netflix é assinatura", rules[0].rule)
        assertEquals("Entretenimento", rules[0].category)
    }

    @Test
    fun `deleteRule removes rule by exact ID`() {
        val rule1 = rulesManager.addRule(testUid, "Regra A", "Geral")
        val rule2 = rulesManager.addRule(testUid, "Regra B", "Geral")

        assertEquals(2, rulesManager.getLocalRules(testUid).size)

        rulesManager.deleteRule(testUid, rule1.id)
        val remaining = rulesManager.getLocalRules(testUid)

        assertEquals(1, remaining.size)
        assertEquals(rule2.id, remaining[0].id)
    }

    @Test
    fun `deleteRule removes rule by matching text substring`() {
        rulesManager.addRule(testUid, "Lembrar de categorizar compras da farmácia como Saúde", "Saúde")
        rulesManager.addRule(testUid, "Mercado Semanal é Alimentação", "Alimentação")

        assertEquals(2, rulesManager.getLocalRules(testUid).size)

        // Remove buscando por "farmácia"
        rulesManager.deleteRule(testUid, "farmácia")
        val remaining = rulesManager.getLocalRules(testUid)

        assertEquals(1, remaining.size)
        assertEquals("Mercado Semanal é Alimentação", remaining[0].rule)
    }

    @Test
    fun `saveLocalRules replaces entire rule set`() {
        val customRules = listOf(
            UserLearnedRule("r1", "Regra 1", "Cat 1", 1000L, true),
            UserLearnedRule("r2", "Regra 2", "Cat 2", 2000L, false)
        )

        rulesManager.saveLocalRules(testUid, customRules)
        val fetched = rulesManager.getLocalRules(testUid)

        assertEquals(2, fetched.size)
        assertEquals("r1", fetched[0].id)
        assertTrue(fetched[0].active)
        assertFalse(fetched[1].active)
    }
}

