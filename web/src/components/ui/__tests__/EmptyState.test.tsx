// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/skeleton'
import { ReceiptText, Plus, SearchX } from 'lucide-react'

describe('EmptyState component', () => {
  it('renders title and description properly', () => {
    render(
      <EmptyState
        icon={ReceiptText}
        title="Nenhum lançamento no mês"
        description="Você ainda não possui transações registradas."
      />
    )

    expect(screen.getByText('Nenhum lançamento no mês')).toBeInTheDocument()
    expect(screen.getByText('Você ainda não possui transações registradas.')).toBeInTheDocument()
  })

  it('triggers action button click callback', () => {
    const handleAction = vi.fn()
    render(
      <EmptyState
        icon={ReceiptText}
        title="Nenhum lançamento"
        action={{
          label: 'Novo Lançamento',
          onClick: handleAction,
          icon: Plus,
        }}
      />
    )

    const button = screen.getByRole('button', { name: /novo lançamento/i })
    expect(button).toBeInTheDocument()
    fireEvent.click(button)
    expect(handleAction).toHaveBeenCalledTimes(1)
  })

  it('renders secondary action button when provided', () => {
    const handleAction = vi.fn()
    const handleSecondary = vi.fn()

    render(
      <EmptyState
        icon={SearchX}
        title="Nenhum resultado"
        action={{
          label: 'Limpar Filtros',
          onClick: handleAction,
        }}
        secondaryAction={{
          label: 'Ver Histórico',
          onClick: handleSecondary,
        }}
      />
    )

    expect(screen.getByRole('button', { name: /limpar filtros/i })).toBeInTheDocument()
    const secondaryBtn = screen.getByRole('button', { name: /ver histórico/i })
    expect(secondaryBtn).toBeInTheDocument()

    fireEvent.click(secondaryBtn)
    expect(handleSecondary).toHaveBeenCalledTimes(1)
  })
})

describe('Skeleton component', () => {
  it('renders with shimmer class and custom dimensions', () => {
    const { container } = render(<Skeleton className="h-6 w-32 custom-skel" />)
    const element = container.firstChild as HTMLElement
    expect(element).toHaveClass('shimmer')
    expect(element).toHaveClass('h-6')
    expect(element).toHaveClass('w-32')
    expect(element).toHaveClass('custom-skel')
  })
})
