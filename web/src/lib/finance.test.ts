import { describe, it, expect } from 'vitest'
import { amountPatch } from './finance'
import type { Transaction } from '@/types'

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
