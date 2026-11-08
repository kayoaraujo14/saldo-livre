// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useIsMobile } from '../useIsMobile'

describe('useIsMobile', () => {
  let listeners: Array<(e: MediaQueryListEvent) => void> = []
  let matches = false

  beforeEach(() => {
    listeners = []
    matches = false

    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn((fn) => listeners.push(fn)),
      removeListener: vi.fn(),
      addEventListener: vi.fn((_, fn) => listeners.push(fn)),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('retorna false quando a tela é maior ou igual ao breakpoint (desktop)', () => {
    matches = false
    const { result } = renderHook(() => useIsMobile())
    expect(result.current).toBe(false)
  })

  it('retorna true quando a tela é menor que o breakpoint (mobile)', () => {
    matches = true
    const { result } = renderHook(() => useIsMobile())
    expect(result.current).toBe(true)
  })

  it('atualiza reativamente quando o tamanho da tela muda', () => {
    matches = false
    const { result } = renderHook(() => useIsMobile())
    expect(result.current).toBe(false)

    act(() => {
      listeners.forEach((fn) => fn({ matches: true } as MediaQueryListEvent))
    })

    expect(result.current).toBe(true)
  })
})
