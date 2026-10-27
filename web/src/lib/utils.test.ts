import { describe, it, expect } from 'vitest'
import { monthLabel } from './utils'

describe('utils', () => {
  describe('monthLabel', () => {
    it('returns the correct label for a valid month key', () => {
      expect(monthLabel('2023-01')).toBe('Janeiro 2023')
      expect(monthLabel('2024-12')).toBe('Dezembro 2024')
      expect(monthLabel('2025-06')).toBe('Junho 2025')
    })

    it('returns "Mês" for null or undefined input', () => {
      expect(monthLabel(null)).toBe('Mês')
      expect(monthLabel(undefined)).toBe('Mês')
    })

    it('returns "Mês" if the string does not contain a hyphen', () => {
      expect(monthLabel('202301')).toBe('Mês')
      expect(monthLabel('janeiro')).toBe('Mês')
      expect(monthLabel('')).toBe('Mês')
    })

    it('returns the original input if year or month is not a valid number', () => {
      expect(monthLabel('YYYY-MM')).toBe('YYYY-MM')
      expect(monthLabel('2023-XX')).toBe('2023-XX')
      expect(monthLabel('AAAA-01')).toBe('AAAA-01')
    })

    it('returns the original input if month is out of valid range (1-12)', () => {
      expect(monthLabel('2023-00')).toBe('2023-00')
      expect(monthLabel('2023-13')).toBe('2023-13')
      expect(monthLabel('2023--1')).toBe('2023--1')
    })

    it('handles whitespace around the input correctly', () => {
      expect(monthLabel('  2023-05  ')).toBe('Maio 2023')
      expect(monthLabel('\n2023-05\t')).toBe('Maio 2023')
    })
  })
})
