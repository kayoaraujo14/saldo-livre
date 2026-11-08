// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ErrorBoundary } from '../ErrorBoundary'
import { logClientError } from '@/lib/telemetry'

function BrokenComponent(): never {
  throw new Error('Falha simulada no componente filho')
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    sessionStorage.clear()
    // Suprime mensagens de console.error disparadas pelo React durante o teste de crash
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('renderiza os componentes filhos normalmente quando não há erro', () => {
    render(
      <ErrorBoundary>
        <div data-testid="child">Conteúdo Seguro</div>
      </ErrorBoundary>
    )

    expect(screen.getByTestId('child')).toHaveTextContent('Conteúdo Seguro')
  })

  it('captura erro do componente filho e exibe a tela de recuperação', () => {
    render(
      <ErrorBoundary>
        <BrokenComponent />
      </ErrorBoundary>
    )

    expect(screen.getByText(/Ops! Algo deu errado ao carregar/i)).toBeInTheDocument()
    expect(screen.getByText(/Recarregar Aplicativo/i)).toBeInTheDocument()
    expect(screen.getByText(/Restaurar em Modo Demonstração/i)).toBeInTheDocument()
  })

  it('registra a telemetria do erro no sessionStorage e no console', () => {
    const error = new Error('Erro de teste para telemetria')
    const telemetry = logClientError(error)

    expect(telemetry.message).toBe('Erro de teste para telemetria')
    expect(telemetry.timestamp).toBeDefined()

    const raw = sessionStorage.getItem('saldo-livre:client-errors')
    expect(raw).toBeTruthy()
    const stored = JSON.parse(raw!)
    expect(stored).toHaveLength(1)
    expect(stored[0].message).toBe('Erro de teste para telemetria')
  })

  it('permite alternar a visualização dos detalhes técnicos', () => {
    render(
      <ErrorBoundary>
        <BrokenComponent />
      </ErrorBoundary>
    )

    expect(screen.getByText(/Falha simulada no componente filho/i)).toBeInTheDocument()

    const toggleButton = screen.getByText(/Detalhes técnicos do erro/i)
    fireEvent.click(toggleButton)

    // Detalhes técnicos ocultos
    expect(screen.queryByText(/Falha simulada no componente filho/i)).toBeNull()
  })
})
