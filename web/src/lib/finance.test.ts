import { describe, it, expect, vi } from 'vitest'
import { getMonthState } from './finance'

vi.mock('./utils', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./utils')>()
  return {
    ...actual,
    currentMonthKey: vi.fn(() => '2023-10'),
  }
})

describe('getMonthState', () => {
  it('should return "current" when monthKey equals the current month', () => {
    expect(getMonthState('2023-10')).toBe('current')
  })

  it('should return "past" when monthKey is before the current month', () => {
    expect(getMonthState('2023-09')).toBe('past')
    expect(getMonthState('2022-12')).toBe('past')
  })

  it('should return "future" when monthKey is after the current month', () => {
    expect(getMonthState('2023-11')).toBe('future')
    expect(getMonthState('2024-01')).toBe('future')
  })
})
