import { describe, it, expect } from 'vitest'
import {
  computeCategoryBudgets,
  suggestCategoryBudgets,
  suggestBudgetRebalance,
} from '../finance'
import { createToolExecutor } from '../agentTools'
import type { Transaction, UserProfile } from '@/types'

describe('Envelopes Orçamentários Inteligentes (Smart Budgets)', () => {
  describe('computeCategoryBudgets', () => {
    it('calcula o progresso, sobra e status de envelopes normais', () => {
      const spendData = [
        { category: 'Alimentação', amount: 600 },
        { category: 'Transporte', amount: 150 },
      ]
      const budgets = {
        Alimentação: 1200,
        Transporte: 300,
        Lazer: 400,
      }

      // No dia 15 de um mês de 30 dias (ex: 2026-11)
      const report = computeCategoryBudgets(spendData, budgets, '2026-11', '2026-11-15')

      expect(report.totalBudgeted).toBe(1900)
      expect(report.totalSpent).toBe(750)
      expect(report.totalRemaining).toBe(1150)
      expect(report.overallPercentage).toBeCloseTo(39.47, 1)
      expect(report.status).toBe('ok')

      const food = report.items.find((i) => i.category === 'Alimentação')!
      expect(food).toBeDefined()
      expect(food.limit).toBe(1200)
      expect(food.spent).toBe(600)
      expect(food.remaining).toBe(600)
      expect(food.percentage).toBe(50)
      expect(food.status).toBe('ok')
      expect(food.dailyPace).toBe(40) // 600 / 15 dias
      expect(food.projectedSpent).toBe(1200) // 40 * 30 dias
      expect(food.projectedPercentage).toBe(100)
      expect(food.recommendedDailyRemaining).toBe(40) // 600 restante / 15 dias restantes
      expect(food.burnRateStatus).toBe('fast') // projetado em 100% (>=95%)

      const leisure = report.items.find((i) => i.category === 'Lazer')!
      expect(leisure).toBeDefined()
      expect(leisure.spent).toBe(0)
      expect(leisure.remaining).toBe(400)
      expect(leisure.percentage).toBe(0)
      expect(leisure.status).toBe('ok')
      expect(leisure.burnRateStatus).toBe('normal')
    })

    it('identifica categorias em alerta (80-99%) e perigo (>=100%)', () => {
      const spendData = [
        { category: 'Alimentação', amount: 1050 }, // 1050 / 1200 = 87.5% -> warning
        { category: 'Transporte', amount: 350 },  // 350 / 300 = 116.67% -> danger
      ]
      const budgets = {
        Alimentação: 1200,
        Transporte: 300,
      }

      const report = computeCategoryBudgets(spendData, budgets, '2026-11', '2026-11-15')

      const transport = report.items.find((i) => i.category === 'Transporte')!
      expect(transport.status).toBe('danger')
      expect(transport.burnRateStatus).toBe('critical')
      expect(transport.remaining).toBe(-50)
      expect(transport.recommendedDailyRemaining).toBe(0)

      const food = report.items.find((i) => i.category === 'Alimentação')!
      expect(food.status).toBe('warning')
      expect(food.burnRateStatus).toBe('critical') // no dia 15 gastou 1050 -> projeção 2100 (175%)

      expect(report.status).toBe('danger')
      expect(report.criticalCount).toBeGreaterThan(0)
      expect(report.topOverspentCategory?.category).toBe('Transporte')
    })

    it('inclui categoria sem envelope configurado caso tenha gasto expressivo', () => {
      const spendData = [
        { category: 'Saúde', amount: 450 },
      ]
      const budgets = {
        Alimentação: 1000,
      }

      const report = computeCategoryBudgets(spendData, budgets, '2026-11', '2026-11-10')

      const health = report.items.find((i) => i.category === 'Saúde')!
      expect(health).toBeDefined()
      expect(health.limit).toBe(0)
      expect(health.spent).toBe(450)
      expect(health.status).toBe('warning') // gasto sem teto
    })

    it('calcula a partir de lista de Transaction[] respeitando uid e impacto do usuário', () => {
      const txs: Transaction[] = [
        {
          id: 'tx1',
          description: 'Supermercado Mensal',
          category: 'Alimentação',
          type: 'expense',
          paymentMethod: 'pix',
          status: 'paid',
          actualAmount: 500,
          totalAmount: 500,
          estimatedAmount: null,
          dueDate: '2026-11-05',
          competenceMonth: '2026-11',
          paidByUid: 'user-a',
          createdByUid: 'user-a',
          visibleToUids: ['user-a'],
          userImpact: { 'user-a': 500 },
        },
        {
          id: 'tx2',
          description: 'Jantar Romântico Rateado 50%',
          category: 'Alimentação',
          type: 'expense',
          paymentMethod: 'credit_card',
          status: 'pending',
          actualAmount: 200,
          totalAmount: 200,
          estimatedAmount: null,
          dueDate: '2026-11-08',
          competenceMonth: '2026-11',
          paidByUid: 'user-a',
          createdByUid: 'user-a',
          visibleToUids: ['user-a', 'user-b'],
          userImpact: { 'user-a': 100, 'user-b': 100 },
        },
      ]

      const budgets = { Alimentação: 1000 }
      const report = computeCategoryBudgets(txs, budgets, '2026-11', '2026-11-10', 'user-a')

      const food = report.items.find((i) => i.category === 'Alimentação')!
      expect(food.spent).toBe(600) // 500 + 100 (apenas a cota do user-a)
      expect(food.remaining).toBe(400)
    })
  })

  describe('suggestCategoryBudgets (AI Baseline Suggestion)', () => {
    it('analisa o histórico de meses anteriores e sugere tetos com 10% de margem', () => {
      const historyTxs: Transaction[] = [
        // Mês -1 (2026-10)
        {
          id: 'h1',
          description: 'Mercado Outubro',
          category: 'Alimentação',
          type: 'expense',
          paymentMethod: 'pix',
          status: 'paid',
          actualAmount: 900,
          totalAmount: 900,
          estimatedAmount: null,
          dueDate: '2026-10-10',
          competenceMonth: '2026-10',
          paidByUid: 'me',
          createdByUid: 'me',
          visibleToUids: ['me'],
          userImpact: { me: 900 },
        },
        // Mês -2 (2026-09)
        {
          id: 'h2',
          description: 'Mercado Setembro',
          category: 'Alimentação',
          type: 'expense',
          paymentMethod: 'pix',
          status: 'paid',
          actualAmount: 1100,
          totalAmount: 1100,
          estimatedAmount: null,
          dueDate: '2026-09-15',
          competenceMonth: '2026-09',
          paidByUid: 'me',
          createdByUid: 'me',
          visibleToUids: ['me'],
          userImpact: { me: 1100 },
        },
        // Gasolina Setembro
        {
          id: 'h3',
          description: 'Gasolina',
          category: 'Transporte',
          type: 'expense',
          paymentMethod: 'pix',
          status: 'paid',
          actualAmount: 200,
          totalAmount: 200,
          estimatedAmount: null,
          dueDate: '2026-09-20',
          competenceMonth: '2026-09',
          paidByUid: 'me',
          createdByUid: 'me',
          visibleToUids: ['me'],
          userImpact: { me: 200 },
        },
      ]

      const suggestion = suggestCategoryBudgets(historyTxs, 'me', '2026-11', 5000)

      expect(suggestion.categoryAverages['Alimentação']).toBe(1000) // (900 + 1100) / 2
      // 1000 * 1.10 = 1100
      expect(suggestion.suggestedBudgets['Alimentação']).toBe(1100)
      expect(suggestion.totalSuggested).toBeGreaterThan(0)
      expect(suggestion.incomeCoveragePct).toBeGreaterThan(0)
    })
  })

  describe('suggestBudgetRebalance (Dynamic AI Rebalancing)', () => {
    it('sugere transferência entre envelopes quando há categoria estourada e categoria com folga', () => {
      const spendData = [
        { category: 'Alimentação', amount: 1350 }, // limite 1200 -> estourou 150
        { category: 'Lazer & Entretenimento', amount: 100 }, // limite 400 -> sobra 300
      ]
      const budgets = {
        'Alimentação': 1200,
        'Lazer & Entretenimento': 400,
      }

      const report = computeCategoryBudgets(spendData, budgets, '2026-11', '2026-11-20')
      const rebalance = suggestBudgetRebalance(report)

      expect(rebalance.canRebalance).toBe(true)
      expect(rebalance.deficitTotal).toBe(150)
      expect(rebalance.surplusTotal).toBe(300)
      expect(rebalance.suggestions.length).toBe(1)

      const s = rebalance.suggestions[0]
      expect(s.overspentCategory).toBe('Alimentação')
      expect(s.overspentAmount).toBe(150)
      expect(s.compensateFrom).toBe('Lazer & Entretenimento')
      expect(s.suggestedTransfer).toBe(150)
    })

    it('retorna canRebalance false se todas as categorias estiverem dentro do orçamento', () => {
      const spendData = [
        { category: 'Alimentação', amount: 800 },
        { category: 'Lazer & Entretenimento', amount: 200 },
      ]
      const budgets = {
        'Alimentação': 1200,
        'Lazer & Entretenimento': 400,
      }

      const report = computeCategoryBudgets(spendData, budgets, '2026-11', '2026-11-20')
      const rebalance = suggestBudgetRebalance(report)

      expect(rebalance.canRebalance).toBe(false)
      expect(rebalance.deficitTotal).toBe(0)
      expect(rebalance.suggestions.length).toBe(0)
    })
  })

  describe('gerenciarEnvelopesOrcamentarios (Gemini Tool)', () => {
    it('executa consulta, configuração de teto, rebalanceamento e simulação de compra via ferramenta de IA', async () => {
      let currentProfile: UserProfile = {
        uid: 'user-1',
        email: 'user@test.com',
        displayName: 'User Test',
        photoURL: null,
        monthlyIncome: 5000,
        categoryBudgets: { Alimentação: 1200, Transporte: 400 },
      }

      const txs: Transaction[] = [
        {
          id: 'tx1',
          description: 'Mercado',
          category: 'Alimentação',
          type: 'expense',
          paymentMethod: 'pix',
          status: 'paid',
          actualAmount: 500,
          totalAmount: 500,
          estimatedAmount: null,
          dueDate: '2026-11-05',
          competenceMonth: '2026-11',
          paidByUid: 'user-1',
          createdByUid: 'user-1',
          visibleToUids: ['user-1'],
          userImpact: { 'user-1': 500 },
        },
      ]

      const executor = createToolExecutor(() => ({
        me: { uid: 'user-1', name: 'User 1' },
        partner: null,
        monthKey: '2026-11',
        getTransactions: () => txs,
        getCards: () => [],
        getProfile: () => currentProfile,
        updateProfile: async (patch) => {
          currentProfile = { ...currentProfile, ...patch }
        },
        addTransaction: async () => 'tx_new',
        updateTransaction: async () => {},
        deleteTransaction: async () => {},
      }))

      // 1. Consulta
      const res = await executor('gerenciarEnvelopesOrcamentarios', { acao: 'consultar' })
      expect(res.response.ok).toBe(true)
      expect(res.response.resumoGeral).toBeDefined()

      // 2. Define teto
      const resTeto = await executor('gerenciarEnvelopesOrcamentarios', {
        acao: 'definir_teto',
        categoria: 'Lazer & Entretenimento',
        limite: 350,
      })
      expect(resTeto.response.ok).toBe(true)
      expect(currentProfile.categoryBudgets?.['Lazer & Entretenimento']).toBe(350)

      // 3. Rebalanceia
      const resRebalance = await executor('gerenciarEnvelopesOrcamentarios', {
        acao: 'rebalancear',
        categoriaOrigem: 'Alimentação',
        categoriaDestino: 'Lazer & Entretenimento',
        valorTransferencia: 100,
      })
      expect(resRebalance.response.ok).toBe(true)
      expect(currentProfile.categoryBudgets?.['Alimentação']).toBe(1100)
      expect(currentProfile.categoryBudgets?.['Lazer & Entretenimento']).toBe(450)

      // 4. Sugestão de tetos
      const resSug = await executor('gerenciarEnvelopesOrcamentarios', { acao: 'sugerir_tetos' })
      expect(resSug.response.ok).toBe(true)
      expect(resSug.response.tetosRecomendados).toBeDefined()

      // 5. Simula compra viável
      const resSimOk = await executor('gerenciarEnvelopesOrcamentarios', {
        acao: 'simular_compra',
        categoria: 'Alimentação',
        valorCompra: 150,
        parcelas: 1,
      })
      expect(resSimOk.response.ok).toBe(true)
      expect((resSimOk.response.veredito as string).includes('SIM')).toBe(true)

      // 6. Simula compra inviável que estoura envelope
      const resSimOver = await executor('gerenciarEnvelopesOrcamentarios', {
        acao: 'simular_compra',
        categoria: 'Alimentação',
        valorCompra: 2000,
        parcelas: 1,
      })
      expect(resSimOver.response.ok).toBe(true)
      expect((resSimOver.response.veredito as string).includes('ATENÇÃO')).toBe(true)
    })
  })
})
