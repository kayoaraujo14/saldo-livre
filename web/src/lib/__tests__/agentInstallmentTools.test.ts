import { describe, it, expect, beforeEach } from 'vitest'
import { createToolExecutor, type AgentEnv } from '../agentTools'
import type { Card, NewTransaction, Transaction } from '@/types'

describe('Issue #78: AI Engine - Sincronização de Contrato de Parcelamento e Exclusão em Grupo', () => {
  const dummyCards: Card[] = [
    {
      id: 'card_nubank',
      ownerUid: 'user_123',
      ownerName: 'Usuário Teste',
      cardName: 'Nubank',
      dueDay: 10,
      closingDay: 3,
      sharedWithUsers: [],
      sharedWithUids: [],
      frequentThirdParties: [],
    },
  ]

  let savedTransactions: Transaction[] = []

  const mockEnv: Partial<AgentEnv> = {
    getTransactions: () => savedTransactions,
    getCards: () => dummyCards,
    getFriends: () => [],
    me: { uid: 'user_123', name: 'Usuário Teste' },
    partner: null,
    monthKey: '2026-11',
    addTransaction: async (newTx: NewTransaction) => {
      const tx: Transaction = {
        ...newTx,
        id: `tx_${Date.now()}_${Math.random()}`,
      } as Transaction
      savedTransactions.push(tx)
      return tx.id
    },
    addMany: async (newTxs: NewTransaction[]) => {
      const ids: string[] = []
      for (const newTx of newTxs) {
        const tx: Transaction = {
          ...newTx,
          id: `tx_${Date.now()}_${Math.random()}`,
        } as Transaction
        savedTransactions.push(tx)
        ids.push(tx.id)
      }
      return ids
    },
    updateTransaction: async (id: string, patch: Partial<Transaction>) => {
      savedTransactions = savedTransactions.map((t) => (t.id === id ? { ...t, ...patch } : t))
    },
    deleteTransaction: async (id: string) => {
      savedTransactions = savedTransactions.filter((t) => t.id !== id)
    },
    requireConfirmation: false,
  }

  beforeEach(() => {
    savedTransactions = []
  })

  describe('1. Contrato de Parcelamento e Correção de Multiplicação por N', () => {
    it('divide corretamente valorTotal pelo total de parcelas (1200 em 10x = 10x de 120, total 1200)', async () => {
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('registrarTransacao', {
        descricao: 'Smartphone Samsung',
        valorTotal: 1200,
        totalParcelas: 10,
        parcelaAtual: 1,
        categoria: 'Eletrônicos',
        metodoPagamento: 'credit_card',
        cartao: 'Nubank',
        tipoDivisao: 'MEU',
        __confirmed: true,
      })

      expect(result.response.ok).toBe(true)
      expect(savedTransactions).toHaveLength(10)

      // Cada parcela individual deve ter 120,00 e NÃO 1.200,00
      for (const tx of savedTransactions) {
        expect(tx.totalAmount).toBe(120)
        expect(tx.actualAmount).toBe(120)
      }

      const somaTotal = savedTransactions.reduce((acc, t) => acc + (t.totalAmount || 0), 0)
      expect(somaTotal).toBe(1200)
    })

    it('aceita valorParcela explícito mantendo o valor unitário informado', async () => {
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('registrarTransacao', {
        descricao: 'Curso de Design',
        valorParcela: 150,
        totalParcelas: 4,
        parcelaAtual: 1,
        categoria: 'Educação',
        metodoPagamento: 'credit_card',
        cartao: 'Nubank',
        tipoDivisao: 'MEU',
        __confirmed: true,
      })

      expect(result.response.ok).toBe(true)
      expect(savedTransactions).toHaveLength(4)

      for (const tx of savedTransactions) {
        expect(tx.totalAmount).toBe(150)
      }

      const somaTotal = savedTransactions.reduce((acc, t) => acc + (t.totalAmount || 0), 0)
      expect(somaTotal).toBe(600)
    })

    it('absorve centavos residuais na última parcela em divisões com dízima (100 em 3x)', async () => {
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('registrarTransacao', {
        descricao: 'Fone Bluetooth',
        valorTotal: 100,
        totalParcelas: 3,
        parcelaAtual: 1,
        categoria: 'Eletrônicos',
        metodoPagamento: 'credit_card',
        cartao: 'Nubank',
        tipoDivisao: 'MEU',
        __confirmed: true,
      })

      expect(result.response.ok).toBe(true)
      expect(savedTransactions).toHaveLength(3)

      expect(savedTransactions[0].totalAmount).toBe(33.33)
      expect(savedTransactions[1].totalAmount).toBe(33.33)
      expect(savedTransactions[2].totalAmount).toBe(33.34)

      const somaTotal = savedTransactions.reduce((acc, t) => acc + (t.totalAmount || 0), 0)
      expect(Number(somaTotal.toFixed(2))).toBe(100.0)
    })
  })

  describe('2. Exclusão Atômica de Parcelamentos via Chat (deleteAllInstallments)', () => {
    it('exclui todas as parcelas do grupo quando deleteAllInstallments = true', async () => {
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      // Cria 3 parcelas do mesmo grupo
      const groupId = 'inst_group_test_123'
      for (let i = 1; i <= 3; i++) {
        savedTransactions.push({
          id: `tx_${i}`,
          description: `Notebook Dell (${i}/3)`,
          totalAmount: 1000,
          actualAmount: 1000,
          category: 'Eletrônicos',
          type: 'expense',
          paymentMethod: 'credit_card',
          status: i === 1 ? 'paid' : 'pending',
          dueDate: `2026-1${i}-10`,
          competenceMonth: `2026-1${i}`,
          visibleToUids: ['user_123'],
          userImpact: { user_123: 1000 },
          allocations: [],
          installmentGroupId: groupId,
          installmentNumber: i,
          installmentsTotal: 3,
          createdByUid: 'user_123',
          paidByUid: 'user_123',
          cardId: 'card_nubank',
          createdAt: Date.now() + i,
        })
      }

      expect(savedTransactions).toHaveLength(3)

      const result = await executor('excluirTransacoes', {
        termoBusca: 'Notebook Dell',
        deleteAllInstallments: true,
        __confirmed: true,
      })

      expect(result.response.ok).toBe(true)
      expect(savedTransactions).toHaveLength(0)
    })

    it('exclui apenas a parcela alvo quando deleteAllInstallments não é ativado', async () => {
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const groupId = 'inst_group_test_456'
      for (let i = 1; i <= 3; i++) {
        savedTransactions.push({
          id: `tx_${i}`,
          description: `Notebook Dell (${i}/3)`,
          totalAmount: 1000,
          actualAmount: 1000,
          category: 'Eletrônicos',
          type: 'expense',
          paymentMethod: 'credit_card',
          status: 'pending',
          dueDate: `2026-1${i}-10`,
          competenceMonth: `2026-1${i}`,
          visibleToUids: ['user_123'],
          userImpact: { user_123: 1000 },
          allocations: [],
          installmentGroupId: groupId,
          installmentNumber: i,
          installmentsTotal: 3,
          createdByUid: 'user_123',
          paidByUid: 'user_123',
          cardId: 'card_nubank',
          createdAt: Date.now() + i,
        })
      }

      const result = await executor('excluirTransacoes', {
        ids: ['tx_1'],
        deleteAllInstallments: false,
        __confirmed: true,
      })

      expect(result.response.ok).toBe(true)
      expect(savedTransactions).toHaveLength(2)
      expect(savedTransactions.map((t) => t.id)).toEqual(['tx_2', 'tx_3'])
    })
  })
})
