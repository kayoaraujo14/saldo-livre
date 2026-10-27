import { describe, it, expect } from 'vitest'
import { cn } from './utils'

describe('cn()', () => {
  it('should merge standard class names', () => {
    expect(cn('class1', 'class2')).toBe('class1 class2')
  })

  it('should filter out falsy values', () => {
    expect(cn('class1', false, null, undefined, 'class2', '')).toBe('class1 class2')
  })

  it('should merge tailwind classes and resolve conflicts', () => {
    expect(cn('px-2 py-1 bg-red-500', 'px-4', 'bg-blue-500')).toBe('py-1 px-4 bg-blue-500')
  })

  it('should handle arrays of classes', () => {
    expect(cn(['class1', 'class2'], 'class3')).toBe('class1 class2 class3')
  })

  it('should handle objects (clsx behavior)', () => {
    expect(cn({ 'class1': true, 'class2': false, 'class3': true })).toBe('class1 class3')
  })

  it('should handle mixed inputs', () => {
    expect(cn('text-sm', ['font-bold', { 'text-red-500': true, 'text-blue-500': false }], 'p-4 text-center')).toBe('text-sm font-bold text-red-500 p-4 text-center')
  })
})
