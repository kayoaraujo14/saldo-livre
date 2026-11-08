// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { NetworkStatusIndicator } from '../NetworkStatusIndicator'
import * as networkHook from '@/hooks/useNetworkStatus'

describe('NetworkStatusIndicator', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('não renderiza nada quando está online e sem reconexão recente', () => {
    vi.spyOn(networkHook, 'useNetworkStatus').mockReturnValue({
      isOnline: true,
      justReconnected: false,
    })

    const { container } = render(<NetworkStatusIndicator />)
    expect(container.firstChild).toBeNull()
  })

  it('renderiza o banner offline com texto adequado quando offline', () => {
    vi.spyOn(networkHook, 'useNetworkStatus').mockReturnValue({
      isOnline: false,
      justReconnected: false,
    })

    render(<NetworkStatusIndicator />)
    expect(screen.getByText(/Modo Offline — Alterações salvas localmente/i)).toBeInTheDocument()
  })

  it('renderiza o banner de reconexão quando justReconnected for true', () => {
    vi.spyOn(networkHook, 'useNetworkStatus').mockReturnValue({
      isOnline: true,
      justReconnected: true,
    })

    render(<NetworkStatusIndicator />)
    expect(screen.getByText(/Conexão restabelecida/i)).toBeInTheDocument()
  })
})
