import { describe, it, expect } from 'vitest'
import { friendPctOf, projectFutureMonths } from '../finance'
import type { Card, RecurringBill, Transaction, UserProfile } from '@/types'

describe('friendPctOf', () => {
  it('returns 100 if tx is falsy', () => {
    expect(friendPctOf(null as any)).toBe(100)
    expect(friendPctOf(undefined as any)).toBe(100)
  })

  it('returns clamped friendSharePct if it exists', () => {
    expect(friendPctOf({ friendSharePct: 30 } as any)).toBe(30)
    expect(friendPctOf({ friendSharePct: -10 } as any)).toBe(100) // clampPct logic
    expect(friendPctOf({ friendSharePct: 150 } as any)).toBe(100) // clampPct logic
  })

  it('returns 100 if totalAmount is falsy or 0', () => {
    expect(friendPctOf({ totalAmount: 0 } as any)).toBe(100)
    expect(friendPctOf({ totalAmount: null } as any)).toBe(100)
    expect(friendPctOf({ totalAmount: undefined } as any)).toBe(100)
  })

  it('calculates the percentage correctly based on friendShareOf', () => {
    // friendShareOf returns 0 if assignedFriendUid is not provided.
    expect(friendPctOf({ totalAmount: 100 } as any)).toBe(100) // (0 / 100) * 100 = 0 -> clamped to 100

    // fallback when assignedFriendUid exists but no allocations -> friendShareOf uses 50%
    expect(friendPctOf({ totalAmount: 200, assignedFriendUid: 'friend-1' } as any)).toBe(50) // (100 / 200) * 100 = 50

    // when allocation exists
    expect(
      friendPctOf({
        totalAmount: 200,
        assignedFriendUid: 'friend-1',
        allocations: [{ uid: 'friend-1', amount: 80 }]
      } as any)
    ).toBe(40) // (80 / 200) * 100 = 40
  })

  it('handles negative totalAmount correctly by taking absolute value', () => {
    expect(
      friendPctOf({
        totalAmount: -200,
        assignedFriendUid: 'friend-1',
        allocations: [{ uid: 'friend-1', amount: 80 }]
      } as any)
    ).toBe(40)
  })
})

describe('projectFutureMonths (12-month projection, channels, categories & items)', () => {
  const me: UserProfile = {
    uid: 'u1',
    displayName: 'Kayo',
    email: 'kayo@test.com',
    photoURL: null,
    monthlyIncome: 5000,
  }

  const cards: Card[] = [
    {
      id: 'card_nu',
      ownerUid: 'u1',
      ownerName: 'Kayo',
      cardName: 'Nubank',
      closingDay: 4,
      dueDay: 11,
      sharedWithUids: [],
      sharedWithUsers: [],
      frequentThirdParties: [],
    },
  ]

  const recurringBills: RecurringBill[] = [
    {
      id: 'rec_aluguel',
      ownerUid: 'u1',
      description: 'Aluguel',
      defaultAmount: 1500,
      dueDay: 10,
      category: 'Moradia',
      type: 'expense',
      paymentMethod: 'pix',
      estimationRule: 'fixed',
      active: true,
    },
    {
      id: 'rec_netflix',
      ownerUid: 'u1',
      description: 'Netflix',
      defaultAmount: 55.9,
      dueDay: 11,
      category: 'Assinaturas',
      type: 'expense',
      paymentMethod: 'credit_card',
      cardId: 'card_nu',
      estimationRule: 'fixed',
      active: true,
    },
  ]

  const transactions: Transaction[] = [
    {
      id: 'tx_1',
      createdByUid: 'u1',
      paidByUid: 'u1',
      cardId: 'card_nu',
      description: 'Supermercado na Fatura',
      category: 'Alimentação',
      subcategory: 'Supermercado',
      type: 'expense',
      paymentMethod: 'credit_card',
      status: 'pending',
      estimatedAmount: null,
      actualAmount: 400,
      totalAmount: 400,
      dueDate: '2026-11-11',
      competenceMonth: '2026-11',
      allocations: [{ type: 'PLATFORM_USER', uid: 'u1', name: 'Kayo', amount: 400, reimbursed: true }],
      userImpact: { u1: 400 },
      visibleToUids: ['u1'],
      createdAt: 1,
    },
    {
      id: 'tx_2',
      createdByUid: 'u1',
      paidByUid: 'u1',
      description: 'Padaria no Pix',
      category: 'Alimentação',
      subcategory: 'Padaria & Café',
      type: 'expense',
      paymentMethod: 'pix',
      status: 'paid',
      estimatedAmount: null,
      actualAmount: 80,
      totalAmount: 80,
      dueDate: '2026-11-05',
      competenceMonth: '2026-11',
      allocations: [{ type: 'PLATFORM_USER', uid: 'u1', name: 'Kayo', amount: 80, reimbursed: true }],
      userImpact: { u1: 80 },
      visibleToUids: ['u1'],
      createdAt: 2,
    },
  ]

  it('defaults to 12 months forward and breaks down invoice, fixed bills, variable pix/debit, categories, and items', () => {
    const result = projectFutureMonths({
      all: transactions,
      bills: recurringBills,
      profile: me,
      uid: 'u1',
      startMonthKey: '2026-11',
      count: 12,
      cards,
    })
    expect(result).toHaveLength(12)

    const nov = result[0]
    expect(nov.monthKey).toBe('2026-11')
    expect(nov.projectedIncome).toBe(5000)
    // Invoice = 400 (Supermercado) + 55.90 (Netflix subscription on card) = 455.90
    expect(nov.invoiceExpense).toBe(455.9)
    // Fixed bills = 1500 (Aluguel)
    expect(nov.fixedBillsExpense).toBe(1500)
    // Variable account (Pix/Debit) = 80 (Padaria)
    expect(nov.variableAccountExpense).toBe(80)
    expect(nov.projectedExpense).toBe(2035.9)
    expect(nov.projectedFreeBalance).toBe(2964.1)

    // Alimentação combines invoice item (400) + pix item (80) = 480
    const alimCat = nov.byCategory?.find((c) => c.category === 'Alimentação')
    expect(alimCat).toBeDefined()
    expect(alimCat?.amount).toBe(480)
    expect(alimCat?.invoiceAmount).toBe(400)
    expect(alimCat?.accountAmount).toBe(80)

    // Nominal items include real txs + projected recurring bills + salary
    expect(nov.items?.some((i) => i.description === 'Supermercado na Fatura' && i.origin === 'invoice')).toBe(true)
    expect(nov.items?.some((i) => i.description === 'Netflix' && i.origin === 'invoice' && i.isRecurringProjection)).toBe(true)
    expect(nov.items?.some((i) => i.description === 'Aluguel' && i.origin === 'fixed_bill' && i.isRecurringProjection)).toBe(true)
    expect(nov.items?.some((i) => i.description === 'Padaria no Pix' && i.origin === 'debit_pix')).toBe(true)
  })
})
