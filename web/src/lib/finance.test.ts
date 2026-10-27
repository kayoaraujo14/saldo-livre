import { describe, it, expect } from 'vitest'
import { friendShareOf } from './finance'
import type { Transaction } from '@/types'

describe('finance > friendShareOf', () => {
  it('should return 0 when tx or assignedFriendUid is missing', () => {
    // @ts-expect-error - testing missing tx
    expect(friendShareOf(null)).toBe(0)
    // @ts-expect-error - testing missing tx
    expect(friendShareOf(undefined)).toBe(0)

    expect(friendShareOf({ assignedFriendUid: undefined } as any)).toBe(0)
    expect(friendShareOf({ assignedFriendUid: null } as any)).toBe(0)
  })

  it('should sum allocation amounts when friend has explicit allocations (array)', () => {
    const tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 100,
      allocations: [
        { type: 'PLATFORM_USER', uid: 'friend_123', name: 'Friend', amount: 30, reimbursed: false },
        { type: 'PLATFORM_USER', uid: 'friend_123', name: 'Friend', amount: 20.5, reimbursed: false },
        { type: 'PLATFORM_USER', uid: 'other', name: 'Other', amount: 49.5, reimbursed: false },
      ]
    }
    expect(friendShareOf(tx)).toBe(50.5)
  })

  it('should sum allocation amounts when friend has explicit allocations (object)', () => {
    const tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 100,
      // Record-style allocations
      allocations: {
        '0': { type: 'PLATFORM_USER', uid: 'friend_123', name: 'Friend', amount: 10, reimbursed: false },
        '1': { type: 'PLATFORM_USER', uid: 'other', name: 'Other', amount: 90, reimbursed: false },
      } as any
    }
    expect(friendShareOf(tx)).toBe(10)
  })

  it('should calculate share using totalAmount and friendSharePct when allocations array is empty', () => {
    const tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 200,
      allocations: [],
      friendSharePct: 30
    }
    expect(friendShareOf(tx)).toBe(60)
  })

  it('should calculate share using totalAmount and friendSharePct when allocations misses the friend', () => {
    const tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 150,
      allocations: [
         { type: 'PLATFORM_USER', uid: 'other', name: 'Other', amount: 150, reimbursed: false }
      ],
      friendSharePct: 40
    }
    expect(friendShareOf(tx)).toBe(60)
  })

  it('should calculate default 50% share when friendSharePct is undefined and no allocations match', () => {
    const tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 100,
      allocations: [],
      // friendSharePct is omitted/undefined
    }
    expect(friendShareOf(tx)).toBe(50)
  })

  it('should clamp invalid friendSharePct values to 100%', () => {
    const txNegative: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 100,
      friendSharePct: -50
    }
    expect(friendShareOf(txNegative)).toBe(100) // Fallback for <= 0 is 100 (clampPct logic)

    const txOver100: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 100,
      friendSharePct: 150
    }
    expect(friendShareOf(txOver100)).toBe(100)

    const txZero: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 100,
      friendSharePct: 0
    }
    expect(friendShareOf(txZero)).toBe(100)
  })

  it('should handle negative totalAmounts correctly', () => {
    const tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: -200.5,
      friendSharePct: 50
    }
    expect(friendShareOf(tx)).toBe(-100.25)
  })

  it('should handle undefined allocations correctly', () => {
    const tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount'> = {
      assignedFriendUid: 'friend_123',
      totalAmount: 300,
      allocations: undefined
    }
    expect(friendShareOf(tx)).toBe(150)
  })
})
