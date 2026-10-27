import { describe, it, expect } from 'vitest'
import { clampPct } from '../finance'

describe('clampPct', () => {
  it('should clamp percentage to 100 if null', () => {
    expect(clampPct(null)).toBe(100)
  })

  it('should clamp percentage to 100 if undefined', () => {
    expect(clampPct(undefined)).toBe(100)
  })

  it('should clamp percentage to 100 if negative or zero', () => {
    expect(clampPct(0)).toBe(100)
    expect(clampPct(-10)).toBe(100)
  })

  it('should clamp percentage to 100 if greater than 100', () => {
    expect(clampPct(150)).toBe(100)
    expect(clampPct(100.01)).toBe(100)
  })

  it('should return the percentage if between 0 and 100', () => {
    expect(clampPct(50)).toBe(50)
    expect(clampPct(33.333)).toBe(33.33) // round2 in action
    expect(clampPct(99.99)).toBe(99.99)
    expect(clampPct(100)).toBe(100)
  })

  it('should handle non-finite numbers', () => {
    expect(clampPct(NaN)).toBe(100)
    expect(clampPct(Infinity)).toBe(100)
    expect(clampPct(-Infinity)).toBe(100)
  })
})
