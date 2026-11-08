import { describe, it, expect, beforeEach } from 'vitest'
import { calculateFullInstallmentRefund } from '../finance'
import { anticipateInstallments } from '../invoices'
import { createToolExecutor, type AgentEnv } from '../agentTools'
import type { Card, NewTransaction, Transaction } from '@/types'

describe('Issue #79: Ciclo de Vida de Parcelas de Cartão - Antecipação com Desconto, Cancelamento Futuro e Estorno', () => {
  const dummyUser = { uid: 'user_123', displayName: 'Usuário Teste' }

  const dummyCard: Card = {
    id: 'card_nubank',
    ownerUid: 'user_123',
    ownerName: 'Usuário Teste',
    cardName: 'Nubank',
    dueDay: 10,
    closingDay: 3,
    sharedWithUsers: [],
    sharedWithUids: [],
    frequentThirdParties: [],
  }

  const createInstallmentSeries = (
    groupId: string,
    description: string,
    totalInstallments: number,
    paidUntil: number
  ): Transaction[] => {
    const list: Transaction[] = []
    const startYear = 2026
    const startMonth = 6 // Junho

    for (let i = 1; i <= totalInstallments; i++) {
      const m = startMonth + i - 1
      const year = startYear + Math.floor((m - 1) / 12)
      const monthNum = ((m - 1) % 12) + 1
      const compMonth = `${year}-${String(monthNum).padStart(2, '0')}`
      const isPaid = i <= paidUntil

      list.push({
        id: `tx_${groupId}_p${i}`,
        createdByUid: 'user_123',
        paidByUid: 'user_123',
        description: description,
        category: 'Compras & Estilo de Vida',
        subcategory: 'Eletrônicos & Informática',
        amount: 200,
        actualAmount: 200,
        totalAmount: 200,
        type: 'expense',
        paymentMethod: 'credit_card',
        cardId: dummyCard.id,
        cardName: dummyCard.cardName,
        competenceMonth: compMonth,
        dueDate: `${compMonth}-10`,
        status: isPaid ? 'paid' : 'pending',
        installmentNumber: i,
        totalInstallments: totalInstallments,
        installmentGroupId: groupId,
        visibleToUids: ['user_123'],
        createdAt: Date.now(),
      } as Transaction)
    }

    return list
  }

  describe('1. Motor Puro: anticipateInstallments (Invoices)', () => {
    it('deve antecipar as últimas N parcelas pendentes para a fatura de destino em ordem decrescente', () => {
      // 10 parcelas, 2 pagas (1 e 2). Pendentes: 3, 4, 5, 6, 7, 8, 9, 10
      const txs = createInstallmentSeries('grp_notebook', 'Notebook Dell', 10, 2)

      const result = anticipateInstallments(
        txs,
        'grp_notebook',
        3,
        '2026-11',
        40.0,
        '2026-11-05',
        dummyUser
      )

      // Deve ter selecionado as 3 últimas pendentes (10, 9, 8)
      expect(result.updatedTransactions).toHaveLength(3)
      const numbers = result.updatedTransactions.map((t) => t.installmentNumber)
      expect(numbers).toContain(10)
      expect(numbers).toContain(9)
      expect(numbers).toContain(8)

      // Todas devem estar no mês de destino com isAnticipated: true
      result.updatedTransactions.forEach((t) => {
        expect(t.competenceMonth).toBe('2026-11')
        expect(t.isAnticipated).toBe(true)
        expect(t.anticipatedFromMonth).toBeDefined()
      })

      // Deve gerar a transação de crédito do desconto
      expect(result.discountTransaction).not.toBeNull()
      expect(Math.abs(result.discountTransaction?.actualAmount ?? 0)).toBe(40.0)
      expect(result.discountTransaction?.competenceMonth).toBe('2026-11')
      expect(result.discountTransaction?.subcategory).toBe('Crédito na Fatura')
      expect(result.totalAnticipatedAmount).toBe(600.0)
    })

    it('não deve gerar transação de crédito se o desconto for 0', () => {
      const txs = createInstallmentSeries('grp_tv', 'Smart TV', 5, 1)
      const result = anticipateInstallments(
        txs,
        'grp_tv',
        2,
        '2026-11',
        0,
        '2026-11-05',
        dummyUser
      )

      expect(result.updatedTransactions).toHaveLength(2)
      expect(result.discountTransaction).toBeNull()
      expect(result.totalAnticipatedAmount).toBe(400.0)
    })

    it('deve antecipar todas as parcelas pendentes se count for maior que as pendentes', () => {
      const txs = createInstallmentSeries('grp_fone', 'Headphone', 4, 2)
      const result = anticipateInstallments(
        txs,
        'grp_fone',
        10,
        '2026-11',
        0,
        '2026-11-05',
        dummyUser
      )

      // Apenas 2 estavam pendentes (3 e 4)
      expect(result.updatedTransactions).toHaveLength(2)
      const nums = result.updatedTransactions.map((t) => t.installmentNumber)
      expect(nums).toEqual([4, 3])
    })
  })

  describe('2. Motor Puro: calculateFullInstallmentRefund (Finance)', () => {
    it('deve gerar crédito consolidado referente às parcelas pagas e listar pendentes para exclusão', () => {
      // 6 parcelas, 2 pagas (2 x 200 = 400). Pendentes: 4 (4 x 200 = 800)
      const txs = createInstallmentSeries('grp_curso', 'Curso de Inglês', 6, 2)

      const result = calculateFullInstallmentRefund(
        txs,
        dummyUser,
        '2026-11-05',
        '2026-11'
      )

      expect(result.totalPaidAmount).toBe(400.0)
      expect(result.toDeleteIds).toHaveLength(4)
      expect(result.toDeleteIds).toContain('tx_grp_curso_p3')
      expect(result.toDeleteIds).toContain('tx_grp_curso_p6')

      // Transação de estorno consolidada gerada
      expect(result.refundTransaction).not.toBeNull()
      expect(result.refundTransaction?.competenceMonth).toBe('2026-11')
      expect(result.refundTransaction?.description).toContain('Estorno')
      expect(result.refundTransaction?.description).toContain('Curso de Inglês')
    })

    it('não deve gerar crédito de reembolso se nenhuma parcela foi paga ainda', () => {
      // 5 parcelas, 0 pagas
      const txs = createInstallmentSeries('grp_relogio', 'Smartwatch', 5, 0)

      const result = calculateFullInstallmentRefund(
        txs,
        dummyUser,
        '2026-11-05',
        '2026-11'
      )

      expect(result.totalPaidAmount).toBe(0)
      expect(result.toDeleteIds).toHaveLength(5)
      expect(result.refundTransaction).toBeNull()
    })
  })

  describe('3. Copiloto IA / Agente: Ferramentas de Antecipação, Estorno e Cancelamento Futuro', () => {
    let savedTransactions: Transaction[] = []

    const mockEnv: Partial<AgentEnv> = {
      getTransactions: () => savedTransactions,
      getCards: () => [dummyCard],
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

    it('ferramenta anteciparParcelas deve antecipar parcelas com desconto com sucesso', async () => {
      // Cria série de 10 parcelas, 2 pagas
      savedTransactions = createInstallmentSeries('grp_ar', 'Ar Condicionado', 10, 2)
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('anteciparParcelas', {
        termoBusca: 'Ar Condicionado',
        quantidadeParcelas: 3,
        mesDestino: '2026-11',
        desconto: 50.0,
      })

      expect(result.response.ok).toBe(true)
      expect(result.response.mensagem).toContain('parcelas antecipadas')

      // Verifica se as 3 parcelas foram marcadas como antecipadas e transferidas para 2026-11
      const anticipatedTxs = savedTransactions.filter((t) => t.isAnticipated === true)
      expect(anticipatedTxs.length).toBe(3)
      anticipatedTxs.forEach((t) => {
        expect(t.competenceMonth).toBe('2026-11')
        expect(t.anticipatedFromMonth).toBeDefined()
      })

      // Verifica se o crédito do desconto foi adicionado
      const discountTx = savedTransactions.find((t) => t.subcategory === 'Crédito na Fatura')
      expect(discountTx).toBeDefined()
    })

    it('ferramenta estornarCompraParcelada deve cancelar pendentes e creditar pagas', async () => {
      // 8 parcelas, 3 pagas (3 x 200 = 600)
      savedTransactions = createInstallmentSeries('grp_passagem', 'Passagem Aérea', 8, 3)
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('estornarCompraParcelada', {
        termoBusca: 'Passagem Aérea',
        mesDestino: '2026-11',
      })

      expect(result.response.ok).toBe(true)
      expect(result.response.mensagem).toContain('Estorno integral')

      // As 5 pendentes devem ter sido excluídas (restando as 3 parcelas originais pagas)
      const originalPaid = savedTransactions.filter(
        (t) => t.installmentGroupId === 'grp_passagem' && (t.actualAmount ?? 0) > 0
      )
      expect(originalPaid).toHaveLength(3)
      originalPaid.forEach((t) => {
        expect(t.status).toBe('paid')
      })

      // Crédito de R$ 600 deve ter sido criado com valor negativo de alívio na fatura
      const refundTx = savedTransactions.find((t) => (t.actualAmount ?? 0) < 0)
      expect(refundTx).toBeDefined()
      expect(Math.abs(refundTx?.actualAmount ?? 0)).toBe(600)
      expect(refundTx?.competenceMonth).toBe('2026-11')
    })

    it('ferramenta excluirTransacoes com deleteFutureOnly=true deve excluir parcelas futuras preservando as pagas', async () => {
      // 6 parcelas, 2 pagas (1 e 2). Parcela 3 é a atual
      savedTransactions = createInstallmentSeries('grp_academia', 'Plano Anual Academia', 6, 2)
      const executor = createToolExecutor(() => mockEnv as AgentEnv)

      const result = await executor('excluirTransacoes', {
        termoBusca: 'Plano Anual Academia',
        deleteFutureOnly: true,
      })

      expect(result.response.ok).toBe(true)
      expect(result.response.quantidadeExcluida).toBe(4)

      // Devem restar apenas as 2 parcelas pagas
      const remaining = savedTransactions.filter((t) => t.installmentGroupId === 'grp_academia')
      expect(remaining).toHaveLength(2)
      expect(remaining.map((t) => t.installmentNumber)).toEqual([1, 2])
    })
  })
})
