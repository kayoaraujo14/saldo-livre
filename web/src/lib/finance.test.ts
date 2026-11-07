import { describe, it, expect } from 'vitest'
import { amountPatch, calculateFinancialHealthScore } from './finance'
import type { MonthSummary, Transaction } from '@/types'

describe('amountPatch', () => {
  const baseTx: Transaction = {
    id: 'tx_1',
    createdByUid: 'uid1',
    paidByUid: 'uid1',
    description: 'Test Tx',
    category: 'Outros',
    type: 'expense',
    paymentMethod: 'pix',
    status: 'estimated',
    estimatedAmount: 100,
    actualAmount: null,
    totalAmount: 100,
    dueDate: '2023-01-01',
    competenceMonth: '2023-01',
    visibleToUids: ['uid1'],
    userImpact: { uid1: 100 },
    allocations: [{ type: 'PLATFORM_USER', uid: 'uid1', name: 'Você', amount: 100, reimbursed: true }],
    splitType: 'mine',
    createdAt: Date.now(),
  }

  it('updates actualAmount and recalculates total and allocations', () => {
    const patch = amountPatch(baseTx, { actualAmount: 90 })

    expect(patch.actualAmount).toBe(90)
    expect(patch.estimatedAmount).toBe(100)
    expect(patch.totalAmount).toBe(90)

    expect(patch.allocations).toBeDefined()
    expect(patch.allocations![0].amount).toBe(90)

    expect(patch.userImpact).toBeDefined()
    expect(patch.userImpact!['uid1']).toBe(90)
  })

  it('updates estimatedAmount and recalculates total and allocations when actualAmount is null', () => {
    const patch = amountPatch(baseTx, { estimatedAmount: 120 })

    expect(patch.actualAmount).toBe(null) // actualAmount remains unchanged from baseTx
    expect(patch.estimatedAmount).toBe(120)
    expect(patch.totalAmount).toBe(120)

    expect(patch.allocations).toBeDefined()
    expect(patch.allocations![0].amount).toBe(120)

    expect(patch.userImpact).toBeDefined()
    expect(patch.userImpact!['uid1']).toBe(120)
  })

  it('handles both actualAmount and estimatedAmount updates', () => {
    const patch = amountPatch(baseTx, { actualAmount: 85, estimatedAmount: 110 })

    expect(patch.actualAmount).toBe(85)
    expect(patch.estimatedAmount).toBe(110)
    expect(patch.totalAmount).toBe(85) // actualAmount takes precedence for total

    expect(patch.allocations).toBeDefined()
    expect(patch.allocations![0].amount).toBe(85)
  })

  it('preserves existing values if no new values are provided', () => {
    const patch = amountPatch(baseTx, {})

    expect(patch.actualAmount).toBe(null)
    expect(patch.estimatedAmount).toBe(100)
    expect(patch.totalAmount).toBe(100)

    expect(patch.allocations).toBeDefined()
    expect(patch.allocations![0].amount).toBe(100)
  })

  it('handles nulling out actualAmount and falling back to estimatedAmount', () => {
    const txWithActual: Transaction = { ...baseTx, actualAmount: 90, totalAmount: 90 }

    const patch = amountPatch(txWithActual, { actualAmount: null })

    expect(patch.actualAmount).toBe(null)
    expect(patch.estimatedAmount).toBe(100) // from baseTx
    expect(patch.totalAmount).toBe(100) // fell back to estimatedAmount

    expect(patch.allocations).toBeDefined()
    expect(patch.allocations![0].amount).toBe(100)
  })
})

describe('calculateFinancialHealthScore', () => {
  it('returns excellent score (100) when savings >= 20%, expenses <= 70% and reserve >= 6 months', () => {
    const summary = {
      income: 10000,
      paid: 5000,
      committed: 1000,
      totalInvested: 2500,
    } as unknown as MonthSummary

    const result = calculateFinancialHealthScore(summary, 40000)

    expect(result.score).toBe(100)
    expect(result.classification).toBe('excellent')
    expect(result.savingsScore).toBe(30)
    expect(result.spendingLimitScore).toBe(30)
    expect(result.emergencyReserveScore).toBe(25)
    expect(result.envelopeScore).toBe(15)
  })

  it('returns critical score (0) when expenses exceed 110% of income with no savings or reserve', () => {
    const summary = {
      income: 5000,
      paid: 5500,
      committed: 500,
      totalInvested: 0,
    } as unknown as MonthSummary

    const result = calculateFinancialHealthScore(summary, 0)

    expect(result.score).toBe(0)
    expect(result.classification).toBe('critical')
    expect(result.savingsScore).toBe(0)
    expect(result.spendingLimitScore).toBe(0)
    expect(result.emergencyReserveScore).toBe(0)
    expect(result.envelopeScore).toBe(0)
  })

  it('handles null/undefined gracefully with fallback score 50 and warning classification', () => {
    const result = calculateFinancialHealthScore(null, 0)
    expect(result.score).toBe(50)
    expect(result.classification).toBe('warning')
  })

  it('applies 4-pillar synergy and discounts investments from living cost when committed equals paid without estimates', () => {
    const summary = {
      monthKey: '2026-10',
      income: 10000,
      committed: 8000,
      paid: 8000,
      committedEstimated: 0,
      committedPending: 0,
      freeBalance: 2000,
      totalInvested: 3000,
      byCategory: [
        { category: 'Moradia', amount: 3000 },
        { category: 'Alimentação', amount: 2000 },
        { category: 'Investimentos', amount: 3000 },
      ],
    } as unknown as MonthSummary

    const result = calculateFinancialHealthScore(summary, 30000)

    expect(result.monthlyLivingCost).toBe(5000)
    expect(result.spendingRatioPercentage).toBe(50)
    expect(result.spendingLimitScore).toBe(30)
    expect(result.savingsScore).toBe(30)
    expect(result.emergencyReserveScore).toBe(25)
    expect(result.envelopeScore).toBe(15)
    expect(result.score).toBe(100)
    expect(result.classification).toBe('excellent')
  })
})
