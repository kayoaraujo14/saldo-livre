import { describe, it, expect } from 'vitest'
import { effectiveAmount } from '../finance'
import type { Transaction } from '@/types'

describe('effectiveAmount', () => {
  it('should return actualAmount if it is defined', () => {
    const tx = {
      actualAmount: 100,
      estimatedAmount: 200,
      totalAmount: 300,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(100)
  })

  it('should return actualAmount if it is 0', () => {
    const tx = {
      actualAmount: 0,
      estimatedAmount: 200,
      totalAmount: 300,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(0)
  })

  it('should return actualAmount if it is negative', () => {
    const tx = {
      actualAmount: -50,
      estimatedAmount: 200,
      totalAmount: 300,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(-50)
  })

  it('should return estimatedAmount if actualAmount is null', () => {
    const tx = {
      actualAmount: null,
      estimatedAmount: 200,
      totalAmount: 300,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(200)
  })

  it('should return estimatedAmount if actualAmount is undefined', () => {
    const tx = {
      estimatedAmount: 200,
      totalAmount: 300,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(200)
  })

  it('should return estimatedAmount if it is 0 and actualAmount is null', () => {
    const tx = {
      actualAmount: null,
      estimatedAmount: 0,
      totalAmount: 300,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(0)
  })

  it('should return totalAmount if actualAmount and estimatedAmount are null', () => {
    const tx = {
      actualAmount: null,
      estimatedAmount: null,
      totalAmount: 300,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(300)
  })

  it('should return totalAmount if it is 0 and both actualAmount and estimatedAmount are null', () => {
    const tx = {
      actualAmount: null,
      estimatedAmount: null,
      totalAmount: 0,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(0)
  })

  it('should return totalAmount if it is negative and both actualAmount and estimatedAmount are null', () => {
    const tx = {
      actualAmount: null,
      estimatedAmount: null,
      totalAmount: -300,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(-300)
  })

  it('should return 0 if all values are null', () => {
    const tx = {
      actualAmount: null,
      estimatedAmount: null,
      totalAmount: null,
    } as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(0)
  })

  it('should return 0 if all values are undefined', () => {
    const tx = {} as Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>
    expect(effectiveAmount(tx)).toBe(0)
  })
})
