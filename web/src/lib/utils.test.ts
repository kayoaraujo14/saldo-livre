import { describe, it, expect } from 'vitest'
import { formatBRL } from './utils'

describe('formatBRL', () => {
  it('formats positive numbers correctly', () => {
    // Normal formatting uses space for the non-breaking space used by Intl.NumberFormat in some environments
    // We can replace non-breaking spaces with standard spaces or test dynamically.
    // Intl.NumberFormat 'pt-BR' format usually produces something like "R$ 1.500,50" but with non-breaking spaces (\u00A0).
    // The safest way is to just generate the expected value dynamically to avoid encoding issues,
    // or to check if it contains the formatted numeric string.
    const expected = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(1500.5)
    expect(formatBRL(1500.5)).toBe(expected)
  })

  it('formats negative numbers correctly', () => {
    const expected = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(-50.25)
    expect(formatBRL(-50.25)).toBe(expected)
  })

  it('handles zero correctly', () => {
    const expected = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(0)
    expect(formatBRL(0)).toBe(expected)
  })

  it('normalizes negative zero to positive zero', () => {
    const expected = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(0)
    expect(formatBRL(-0)).toBe(expected)
  })

  it('handles compact format correctly (maximumFractionDigits: 0)', () => {
    const expectedCompact = new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      maximumFractionDigits: 0,
    }).format(1500.5)
    expect(formatBRL(1500.5, true)).toBe(expectedCompact)
  })

  it('handles non-finite values by defaulting to 0', () => {
    const expectedZero = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(0)
    expect(formatBRL(NaN)).toBe(expectedZero)
    expect(formatBRL(Infinity)).toBe(expectedZero)
    expect(formatBRL(-Infinity)).toBe(expectedZero)
  })
})
