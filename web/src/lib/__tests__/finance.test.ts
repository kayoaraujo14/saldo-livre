import { describe, it, expect } from 'vitest'
import { friendPctOf } from '../finance'

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
