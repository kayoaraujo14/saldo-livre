import { describe, it, expect } from 'vitest'
import { round2 } from './utils'

describe('round2', () => {
  it('should round positive numbers correctly to 2 decimal places', () => {
    expect(round2(1.004)).toBe(1.00)
    expect(round2(1.005)).toBe(1.01) // Number.EPSILON helps here
    expect(round2(1.006)).toBe(1.01)
    expect(round2(2)).toBe(2)
    expect(round2(2.1)).toBe(2.1)
    expect(round2(2.123456789)).toBe(2.12)
  })

  it('should round negative numbers correctly to 2 decimal places', () => {
    expect(round2(-1.004)).toBe(-1.00)
    expect(round2(-1.005)).toBe(-1.00) // Note: -1.005 + Number.EPSILON = -1.0049999999999997, round to -1
    expect(round2(-1.006)).toBe(-1.01)
    expect(round2(-2.123456789)).toBe(-2.12)
  })

  it('should handle zero correctly', () => {
    expect(round2(0)).toBe(0)
    expect(round2(-0)).toBe(0) // -0 will evaluate to 0 because -0 + Number.EPSILON is a small positive, then rounded.
  })

  it('should handle NaN and Infinity', () => {
    expect(round2(NaN)).toBeNaN()
    expect(round2(Infinity)).toBe(Infinity)
    expect(round2(-Infinity)).toBe(-Infinity)
  })

  it('should correctly handle classic JS floating point issues', () => {
    expect(round2(1.005)).toBe(1.01)
    // 0.1 + 0.2 = 0.30000000000000004
    expect(round2(0.1 + 0.2)).toBe(0.3)
  })
})
