import { describe, it, expect } from 'vitest'
import { createToolExecutor, type AgentEnv } from '../agentTools'
import type { Card, PendingBatchTransaction, Transaction } from '@/types'

describe('createToolExecutor - registrarLote with card & split detection', () => {
  const dummyCards: Card[] = [
    {
      id: 'card_nubank',
      ownerUid: 'user_123',
      ownerName: 'Usuario Teste',
      cardName: 'Nubank',
      dueDay: 10,
      closingDay: 3,
      sharedWithUsers: [],
      sharedWithUids: [],
      frequentThirdParties: [],
    },
  ]

  const multipleCards: Card[] = [
    {
      id: 'card_nubank',
      ownerUid: 'user_123',
      ownerName: 'Usuario Teste',
      cardName: 'Nubank',
      dueDay: 10,
      closingDay: 3,
      sharedWithUsers: [],
      sharedWithUids: [],
      frequentThirdParties: [],
    },
    {
      id: 'card_inter',
      ownerUid: 'user_123',
      ownerName: 'Usuario Teste',
      cardName: 'Inter',
      dueDay: 15,
      closingDay: 7,
      sharedWithUsers: [],
      sharedWithUids: [],
      frequentThirdParties: [],
    },
  ]

  const savedTransactions: Transaction[] = []
  let pendingTxs: PendingBatchTransaction[] = []

  const mockEnv: Partial<AgentEnv> = {
    getTransactions: () => savedTransactions,
    getCards: () => dummyCards,
    getFriends: () => [
      { uid: 'friend_kayo', name: 'Kayo Araujo', email: 'kayo@example.com' },
    ],
    me: { uid: 'user_123', name: 'Usuario Teste' },
    partner: null,
    monthKey: '2026-11',
    getPendingTransactions: () => pendingTxs,
    setPendingTransactions: (txs: PendingBatchTransaction[]) => {
      pendingTxs = txs
    },
    addTransaction: async () => 'tx_123',
    updateTransaction: async () => {},
    deleteTransaction: async () => {},
  }

  it('detects 50% split in description and assigns credit card in draft batch', async () => {
    const executor = createToolExecutor(() => mockEnv as AgentEnv)

    const result = await executor('registrarTransacoesLote', {
      cartao: 'Nubank',
      metodoPagamentoPadrao: 'credit_card',
      transacoes: [
        {
          descricao: '99; R$ 14,40',
          valor: 14.4,
          data: '2026-11-01',
        },
        {
          descricao: '99food - club; R$ 1,99; 50% para Kayo Araujo',
          valor: 1.99,
          data: '2026-11-01',
        },
        {
          descricao: '99food - black sushi; R$ 67,31; 50% para Kayo Araujo',
          valor: 67.31,
          data: '2026-11-01',
        },
      ],
    })

    expect(result.response?.ok).toBe(true)
    expect(result.response?.aguardandoConfirmacao).toBe(true)

    const pendingList = (result.response as any)?.pending_transactions as PendingBatchTransaction[]
    expect(pendingList).toBeDefined()
    expect(pendingList.length).toBe(3)

    // Item 1: 100% user
    expect(pendingList[0].descricao).toBe('99')
    expect(pendingList[0].metodoPagamento).toBe('credit_card')
    expect(pendingList[0].cartao).toBe('Nubank')

    // Item 2: split 50% with Kayo Araujo
    expect(pendingList[1].descricao).toBe('99food - club')
    expect(pendingList[1].nomeAmigo).toBe('Kayo Araujo')
    expect(pendingList[1].porcentagemAmigo).toBe(50)
    expect(pendingList[1].metodoPagamento).toBe('credit_card')
    expect(pendingList[1].cartao).toBe('Nubank')

    // Item 3: split 50% with Kayo Araujo
    expect(pendingList[2].descricao).toBe('99food - black sushi')
    expect(pendingList[2].nomeAmigo).toBe('Kayo Araujo')
    expect(pendingList[2].porcentagemAmigo).toBe(50)
    expect(pendingList[2].metodoPagamento).toBe('credit_card')
    expect(pendingList[2].cartao).toBe('Nubank')
  })

  it('falls back to default user card when paymentMethod is credit_card but cartao is omitted and single card exists', async () => {
    const executor = createToolExecutor(() => mockEnv as AgentEnv)

    const result = await executor('registrarTransacoesLote', {
      metodoPagamentoPadrao: 'credit_card',
      transacoes: [
        {
          descricao: 'Posto Shell',
          valor: 100,
          data: '2026-11-02',
        },
      ],
    })

    expect(result.response?.ok).toBe(true)
    const pendingList = (result.response as any)?.pending_transactions as PendingBatchTransaction[]
    expect(pendingList[0].cartao).toBe('Nubank')
    expect(pendingList[0].metodoPagamento).toBe('credit_card')
  })

  describe('Zero Presumption / Certainty Guardrails', () => {
    it('requests clarification when payment method is omitted and not inferable from description', async () => {
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('registrarTransacao', {
        descricao: 'Almoço Restaurante',
        valorTotal: 50,
      })

      expect(result.response?.ok).toBe(false)
      expect(result.response?.precisaClarificacao).toBe(true)
      expect(result.response?.campoFaltante).toBe('metodoPagamento')
    })

    it('requests clarification when credit card is used, multiple cards exist, and no card is specified', async () => {
      const multiCardEnv: Partial<AgentEnv> = {
        ...mockEnv,
        getCards: () => multipleCards,
      }
      const executor = createToolExecutor(() => multiCardEnv as AgentEnv)

      const result = await executor('registrarTransacao', {
        descricao: 'Farmácia Preço Popular',
        valorTotal: 80,
        metodoPagamento: 'credit_card',
      })

      expect(result.response?.ok).toBe(false)
      expect(result.response?.precisaClarificacao).toBe(true)
      expect(result.response?.campoFaltante).toBe('cartao')
      expect(String(result.response?.erro)).toContain('Nubank')
      expect(String(result.response?.erro)).toContain('Inter')
    })

    it('requests clarification when splitType is FRIEND but no friend is specified', async () => {
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('registrarTransacao', {
        descricao: 'Pizza Especial',
        valorTotal: 100,
        metodoPagamento: 'pix',
        tipoDivisao: 'AMIGO',
      })

      expect(result.response?.ok).toBe(false)
      expect(result.response?.precisaClarificacao).toBe(true)
      expect(result.response?.campoFaltante).toBe('divisao')
    })

    it('requests clarification when amount is missing or zero', async () => {
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('registrarTransacao', {
        descricao: 'Conta de Energia',
        valorTotal: 0,
      })

      expect(result.response?.ok).toBe(false)
      expect(result.response?.precisaClarificacao).toBe(true)
      expect(result.response?.campoFaltante).toBe('valorTotal')
    })

    it('requests clarification in batch when multiple credit cards exist and card is omitted', async () => {
      const multiCardEnv: Partial<AgentEnv> = {
        ...mockEnv,
        getCards: () => multipleCards,
      }
      const executor = createToolExecutor(() => multiCardEnv as AgentEnv)

      const result = await executor('registrarTransacoesLote', {
        metodoPagamentoPadrao: 'credit_card',
        transacoes: [
          {
            descricao: 'Lojas Americanas',
            valor: 50,
            data: '2026-11-01',
          },
        ],
      })

      expect(result.response?.ok).toBe(false)
      expect(result.response?.precisaClarificacao).toBe(true)
      expect(result.response?.campoFaltante).toBe('cartao')
    })
  })
})
