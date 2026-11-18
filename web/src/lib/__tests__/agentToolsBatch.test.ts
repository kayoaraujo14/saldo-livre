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

describe('createToolExecutor - batch payment method, competence month & edit regressions', () => {
  const vaVrAndThirdPartyCards: Card[] = [
    {
      id: 'card_vavr',
      ownerUid: 'user_123',
      ownerName: 'Usuario Teste',
      cardName: 'Flash Vale',
      cardType: 'va_vr',
      dueDay: 1,
      closingDay: 1,
      sharedWithUsers: [],
      sharedWithUids: [],
      frequentThirdParties: [],
    },
    {
      id: 'card_terceiro',
      ownerUid: 'friend_terceiro',
      ownerName: 'Lucas Silva',
      cardName: 'Nubank Terceiro',
      cardType: 'credit',
      dueDay: 10,
      closingDay: 3,
      sharedWithUsers: ['user_123'],
      sharedWithUids: ['user_123'],
      frequentThirdParties: [],
    },
  ]

  function createStatefulEnv(overrides: Partial<AgentEnv> = {}) {
    const txs: Transaction[] = []
    let pending: PendingBatchTransaction[] = []
    const env: AgentEnv = {
      getTransactions: () => txs,
      getCards: () => vaVrAndThirdPartyCards,
      getFriends: () => [],
      me: { uid: 'user_123', name: 'Usuario Teste' },
      partner: null,
      monthKey: '2026-12',
      requireConfirmation: false,
      getPendingTransactions: () => pending,
      setPendingTransactions: (next) => {
        pending = next
      },
      addTransaction: async (tx) => {
        const id = `tx_${txs.length + 1}`
        txs.push({ ...(tx as Transaction), id })
        return id
      },
      updateTransaction: async (id, patch) => {
        const idx = txs.findIndex((t) => t.id === id)
        if (idx >= 0) {
          txs[idx] = { ...txs[idx], ...patch } as Transaction
        }
      },
      deleteTransaction: async (id) => {
        const idx = txs.findIndex((t) => t.id === id)
        if (idx >= 0) txs.splice(idx, 1)
      },
      ...overrides,
    }
    return { txs, executor: createToolExecutor(() => env) }
  }

  it('registers batch in debit with November competence month even when VA/VR card exists and monthKey is December', async () => {
    const { txs, executor } = createStatefulEnv({ monthKey: '2026-12' })

    const result = await executor('registrarTransacoesLote', {
      __confirmed: true,
      metodoPagamentoPadrao: 'debit',
      origemDocumento: 'extrato_conta',
      mesFaturaPadrao: '2026-12',
      transacoes: [
        { descricao: '99', valor: 7.39, tipo: 'expense', data: '2026-11-17', categoria: 'Transporte' },
        { descricao: '99', valor: 7.14, tipo: 'expense', data: '2026-11-17', categoria: 'Transporte' },
        { descricao: 'Reembolso 99Food', valor: 58.25, tipo: 'income', data: '2026-11-16', categoria: 'Reembolso' },
        { descricao: 'Uber', valor: 6.91, tipo: 'expense', data: '2026-11-16', categoria: 'Transporte' },
      ],
    })

    expect(result.response?.ok).toBe(true)
    expect(txs).toHaveLength(4)
    for (const tx of txs) {
      expect(tx.paymentMethod).toBe('debit')
      expect(tx.cardId).toBeNull()
      expect(tx.competenceMonth).toBe('2026-11')
    }
    const refund = txs.find((t) => t.description === 'Reembolso 99Food')
    expect(refund?.type).toBe('income')
    expect(refund?.paymentMethod).toBe('debit')
  })

  it('requests payment method clarification in batch when payment method is omitted even if third-party or VA/VR card exists', async () => {
    const { txs, executor } = createStatefulEnv()

    const result = await executor('registrarTransacoesLote', {
      transacoes: [
        { descricao: '99', valor: 7.39, data: '2026-11-17' },
        { descricao: 'Uber', valor: 6.91, data: '2026-11-16' },
      ],
    })

    expect(result.response?.ok).toBe(false)
    expect(result.response?.precisaClarificacao).toBe(true)
    expect(result.response?.campoFaltante).toBe('metodoPagamento')
    expect(txs).toHaveLength(0)
  })

  it('requests card clarification for single and batch credit_card when only a third-party card exists and card is omitted', async () => {
    const thirdPartyOnly: Card[] = [vaVrAndThirdPartyCards[1]]
    const { txs, executor } = createStatefulEnv({ getCards: () => thirdPartyOnly })

    const singleRes = await executor('registrarTransacao', {
      descricao: 'Mercado',
      valorTotal: 45,
      metodoPagamento: 'credit_card',
      data: '2026-11-17',
    })
    expect(singleRes.response?.ok).toBe(false)
    expect(singleRes.response?.precisaClarificacao).toBe(true)
    expect(singleRes.response?.campoFaltante).toBe('cartao')

    const batchRes = await executor('registrarTransacoesLote', {
      metodoPagamentoPadrao: 'credit_card',
      transacoes: [{ descricao: 'Mercado', valor: 45, data: '2026-11-17' }],
    })
    expect(batchRes.response?.ok).toBe(false)
    expect(batchRes.response?.precisaClarificacao).toBe(true)
    expect(batchRes.response?.campoFaltante).toBe('cartao')
    expect(txs).toHaveLength(0)
  })

  it('edits misclassified VA/VR and third-party credit card batch to debit, clears cardId, fixes paidByUid & competenceMonth, and updates duplicate descriptions together', async () => {
    const { txs, executor } = createStatefulEnv({ monthKey: '2026-12' })

    txs.push(
      {
        id: 'tx_1',
        type: 'expense',
        amount: 7.39,
        description: '99',
        category: 'Transporte',
        date: '2026-11-17',
        dueDate: '2026-11-17',
        competenceMonth: '2026-12',
        paymentMethod: 'va_vr',
        cardId: 'card_vavr',
        status: 'paid',
        split: { type: 'individual', payers: [{ uid: 'user_123', name: 'Usuario Teste', percentage: 100, amount: 7.39 }] },
        paidByUid: 'user_123',
        createdByUid: 'user_123',
        createdAt: 1700000000000,
      } as Transaction,
      {
        id: 'tx_2',
        type: 'expense',
        amount: 7.14,
        description: '99',
        category: 'Transporte',
        date: '2026-11-17',
        dueDate: '2026-11-17',
        competenceMonth: '2026-12',
        paymentMethod: 'credit_card',
        cardId: 'card_terceiro',
        status: 'pending',
        split: { type: 'individual', payers: [{ uid: 'friend_terceiro', name: 'Lucas Silva', percentage: 100, amount: 7.14 }] },
        paidByUid: 'friend_terceiro',
        createdByUid: 'user_123',
        createdAt: 1700000001000,
      } as Transaction,
    )

    const editRes = await executor('editarTransacoes', {
      __confirmed: true,
      termoBusca: '99',
      mesOrigem: '2026-11',
      novoMetodoPagamento: 'debit',
    })

    expect(editRes.response?.ok).toBe(true)
    expect(txs).toHaveLength(2)
    for (const tx of txs) {
      expect(tx.paymentMethod).toBe('debit')
      expect(tx.cardId).toBeNull()
      expect(tx.paidByUid).toBe('user_123')
      expect(tx.status).toBe('paid')
      expect(tx.competenceMonth).toBe('2026-11')
    }
  })
})
