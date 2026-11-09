import { describe, expect, it } from 'vitest'
import { normalizeTransaction } from '../firebase'

describe('normalizeTransaction', () => {
  it('preenche campos padrão quando dados estão vazios ou indefinidos', () => {
    const tx = normalizeTransaction('tx-1', {})
    expect(tx.id).toBe('tx-1')
    expect(tx.createdByUid).toBe('')
    expect(tx.paidByUid).toBe('')
    expect(tx.description).toBe('')
    expect(tx.category).toBe('Outros')
    expect(tx.type).toBe('expense')
    expect(tx.paymentMethod).toBe('credit_card')
    expect(tx.status).toBe('confirmed')
    expect(tx.totalAmount).toBe(0)
    expect(tx.visibleToUids).toEqual([])
    expect(tx.allocations).toEqual([])
    expect(tx.userImpact).toEqual({})
    expect(tx.refundedTransactionId).toBeNull()
    expect(tx.isAnticipated).toBe(false)
    expect(tx.anticipatedFromMonth).toBeNull()
    expect(tx.discountAmount).toBeNull()
    expect(tx.externalName).toBeNull()
    expect(tx.notes).toBeNull()
  })

  it('faz parse correto de valores numéricos inteiros, decimais e strings', () => {
    const tx = normalizeTransaction('tx-2', {
      totalAmount: 150, // inteiro
      actualAmount: 150.55,
      estimatedAmount: '200.50',
      discountAmount: 15,
    })

    expect(tx.totalAmount).toBe(150)
    expect(tx.actualAmount).toBe(150.55)
    expect(tx.estimatedAmount).toBe('200.50')
    expect(tx.discountAmount).toBe(15)
  })

  it('normaliza refundedTransactionId, antecipações e anotações', () => {
    const tx = normalizeTransaction('tx-3', {
      refundedTransactionId: 'ref-999',
      isAnticipated: true,
      anticipatedFromMonth: '2026-05',
      discountAmount: '12.50',
      notes: 'Compra antecipada no app do banco',
    })

    expect(tx.refundedTransactionId).toBe('ref-999')
    expect(tx.isAnticipated).toBe(true)
    expect(tx.anticipatedFromMonth).toBe('2026-05')
    expect(tx.discountAmount).toBe(12.5)
    expect(tx.notes).toBe('Compra antecipada no app do banco')
  })

  it('normaliza externalName priorizando externalName sobre thirdPartyName', () => {
    const txWithBoth = normalizeTransaction('tx-4', {
      externalName: 'João da Silva',
      thirdPartyName: 'João Antigo',
    })
    expect(txWithBoth.externalName).toBe('João da Silva')

    const txWithLegacyOnly = normalizeTransaction('tx-5', {
      thirdPartyName: 'Maria Antiga',
    })
    expect(txWithLegacyOnly.externalName).toBe('Maria Antiga')
  })

  it('mantém integridade de visibleToUids e userImpact', () => {
    const tx = normalizeTransaction('tx-6', {
      visibleToUids: ['user-1', 'user-2'],
      userImpact: {
        'user-1': 50,
        'user-2': 50,
      },
      allocations: [
        { type: 'PLATFORM_USER', uid: 'user-1', name: 'User 1', amount: 50, reimbursed: true },
        { type: 'PLATFORM_USER', uid: 'user-2', name: 'User 2', amount: 50, reimbursed: false },
      ],
    })

    expect(tx.visibleToUids).toEqual(['user-1', 'user-2'])
    expect(tx.userImpact).toEqual({ 'user-1': 50, 'user-2': 50 })
    expect(tx.allocations).toHaveLength(2)
  })
})
