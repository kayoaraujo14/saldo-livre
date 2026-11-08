// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { TransactionTable } from '@/components/TransactionTable'
import { useFinance } from '@/store/FinanceContext'
import type { Transaction, MonthSummary } from '@/types'

// Mock ResizeObserver and viewport measurements for jsdom
class MockResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver

Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, value: 1024 })
Object.defineProperty(HTMLElement.prototype, 'offsetHeight', { configurable: true, value: 600 })
Object.defineProperty(HTMLElement.prototype, 'clientWidth', { configurable: true, value: 1024 })
Object.defineProperty(HTMLElement.prototype, 'clientHeight', { configurable: true, value: 600 })

HTMLElement.prototype.getBoundingClientRect = () => ({
  width: 1024,
  height: 600,
  top: 0,
  left: 0,
  bottom: 600,
  right: 1024,
  x: 0,
  y: 0,
  toJSON: () => {},
})

vi.mock('@/store/FinanceContext', () => ({
  useFinance: vi.fn(),
}))

const mockTransactions: Transaction[] = [
  {
    id: 'tx-1',
    description: 'Supermercado Central',
    totalAmount: 350.5,
    category: 'Alimentação',
    subcategory: 'Mercado',
    date: '2026-11-05',
    dueDate: '2026-11-05',
    competenceMonth: '2026-11',
    type: 'expense',
    status: 'paid',
    splitType: 'mine',
    paymentMethod: 'PIX',
    createdByUid: 'user-1',
    paidByUid: 'user-1',
    visibleToUids: ['user-1'],
    allocations: [],
    createdAt: 1000,
    updatedAt: 1000,
  },
  {
    id: 'tx-2',
    description: 'Salário Mensal',
    totalAmount: 5000.0,
    category: 'Salário',
    date: '2026-11-01',
    dueDate: '2026-11-01',
    competenceMonth: '2026-11',
    type: 'income',
    status: 'paid',
    splitType: 'mine',
    paymentMethod: 'TRANSFER',
    createdByUid: 'user-1',
    paidByUid: 'user-1',
    visibleToUids: ['user-1'],
    allocations: [],
    createdAt: 1001,
    updatedAt: 1001,
  },
  {
    id: 'tx-3',
    description: 'Aluguel Apartamento',
    totalAmount: 1800.0,
    category: 'Moradia',
    subcategory: 'Aluguel',
    date: '2026-11-10',
    dueDate: '2026-11-10',
    competenceMonth: '2026-11',
    type: 'expense',
    status: 'pending',
    splitType: 'mine',
    paymentMethod: 'BOLETO',
    createdByUid: 'user-1',
    paidByUid: 'user-1',
    visibleToUids: ['user-1'],
    allocations: [],
    createdAt: 1002,
    updatedAt: 1002,
  },
]

const mockSummary: MonthSummary = {
  income: 5000,
  committed: 1800,
  paid: 350.5,
  pending: 1800,
  estimated: 0,
  fixedCommitted: 1800,
  freeBalance: 2849.5,
  carryoverAmount: 400.0,
  previousMonthDeficit: 0,
}

describe('TransactionTable with DOM Virtualization', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders loading skeleton when isLoading is true', () => {
    vi.mocked(useFinance).mockReturnValue({
      summary: null,
      monthTransactions: [],
      transactions: [],
      month: '2026-11',
      cards: [],
      me: { uid: 'user-1', name: 'Kayo' },
      partner: null,
      cycleStatus: vi.fn(),
      setStatus: vi.fn(),
      changeSplit: vi.fn(),
      changeAmount: vi.fn(),
      updateTransaction: vi.fn(),
      updateInstallmentGroup: vi.fn(),
      deleteTransaction: vi.fn(),
      respondChargeRequest: vi.fn(),
      isLoading: true,
    } as unknown as ReturnType<typeof useFinance>)

    const onEditTx = vi.fn()
    render(<TransactionTable onEditTx={onEditTx} />)

    expect(screen.getByRole('status', { name: /carregando transações/i })).toBeInTheDocument()
  })

  it('renders EmptyState when there are no transactions in month', () => {
    vi.mocked(useFinance).mockReturnValue({
      summary: mockSummary,
      monthTransactions: [],
      transactions: [],
      month: '2026-11',
      cards: [],
      me: { uid: 'user-1', name: 'Kayo' },
      partner: null,
      cycleStatus: vi.fn(),
      setStatus: vi.fn(),
      changeSplit: vi.fn(),
      changeAmount: vi.fn(),
      updateTransaction: vi.fn(),
      updateInstallmentGroup: vi.fn(),
      deleteTransaction: vi.fn(),
      respondChargeRequest: vi.fn(),
      isLoading: false,
    } as unknown as ReturnType<typeof useFinance>)

    render(<TransactionTable onEditTx={vi.fn()} />)

    expect(screen.getAllByText('Nenhum lançamento neste mês').length).toBeGreaterThan(0)
  })

  it('renders virtualized rows and calculates dynamic footer totals', () => {
    vi.mocked(useFinance).mockReturnValue({
      summary: mockSummary,
      monthTransactions: mockTransactions,
      transactions: mockTransactions,
      month: '2026-11',
      cards: [],
      me: { uid: 'user-1', name: 'Kayo' },
      partner: null,
      cycleStatus: vi.fn(),
      setStatus: vi.fn(),
      changeSplit: vi.fn(),
      changeAmount: vi.fn(),
      updateTransaction: vi.fn(),
      updateInstallmentGroup: vi.fn(),
      deleteTransaction: vi.fn(),
      respondChargeRequest: vi.fn(),
      isLoading: false,
    } as unknown as ReturnType<typeof useFinance>)

    render(<TransactionTable onEditTx={vi.fn()} />)

    // Verify carryover balance row is present
    expect(screen.getAllByText('Saldo Inicial do Mês').length).toBeGreaterThan(0)

    // Verify transaction descriptions are rendered
    expect(screen.getAllByText('Supermercado Central').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Salário Mensal').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Aluguel Apartamento').length).toBeGreaterThan(0)

    // Verify footer summary reflects transactions
    expect(screen.getByText(/Exibindo/i)).toBeInTheDocument()
    expect(screen.getAllByText(/\+R\$\s*5\.000,00/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/−R\$\s*2\.150,50/).length).toBeGreaterThan(0)
  })

  it('filters by search term correctly and updates count badge', () => {
    vi.mocked(useFinance).mockReturnValue({
      summary: mockSummary,
      monthTransactions: mockTransactions,
      transactions: mockTransactions,
      month: '2026-11',
      cards: [],
      me: { uid: 'user-1', name: 'Kayo' },
      partner: null,
      cycleStatus: vi.fn(),
      setStatus: vi.fn(),
      changeSplit: vi.fn(),
      changeAmount: vi.fn(),
      updateTransaction: vi.fn(),
      updateInstallmentGroup: vi.fn(),
      deleteTransaction: vi.fn(),
      respondChargeRequest: vi.fn(),
      isLoading: false,
    } as unknown as ReturnType<typeof useFinance>)

    render(<TransactionTable onEditTx={vi.fn()} />)

    const searchInput = screen.getByPlaceholderText(/buscar por descrição/i)
    fireEvent.change(searchInput, { target: { value: 'Supermercado' } })

    expect(screen.getAllByText('Supermercado Central').length).toBeGreaterThan(0)
    expect(screen.queryByText('Aluguel Apartamento')).not.toBeInTheDocument()
  })

  it('handles large dataset of 500 transactions with DOM virtualization smoothly', () => {
    const largeList: Transaction[] = Array.from({ length: 500 }).map((_, i) => ({
      id: `bulk-tx-${i}`,
      description: `Lançamento Virtualizado #${i + 1}`,
      totalAmount: 10 + (i % 50),
      category: i % 2 === 0 ? 'Alimentação' : 'Transporte',
      date: `2026-11-${String((i % 28) + 1).padStart(2, '0')}`,
      dueDate: `2026-11-${String((i % 28) + 1).padStart(2, '0')}`,
      competenceMonth: '2026-11',
      type: 'expense',
      status: i % 3 === 0 ? 'paid' : 'pending',
      splitType: 'mine',
      paymentMethod: 'CARD',
      createdByUid: 'user-1',
      paidByUid: 'user-1',
      visibleToUids: ['user-1'],
      allocations: [],
      createdAt: 2000 + i,
      updatedAt: 2000 + i,
    }))

    vi.mocked(useFinance).mockReturnValue({
      summary: {
        income: 10000,
        committed: 5000,
        paid: 2000,
        pending: 3000,
        estimated: 0,
        fixedCommitted: 0,
        freeBalance: 5000,
        carryoverAmount: 0,
        previousMonthDeficit: 0,
      },
      monthTransactions: largeList,
      transactions: largeList,
      month: '2026-11',
      cards: [],
      me: { uid: 'user-1', name: 'Kayo' },
      partner: null,
      cycleStatus: vi.fn(),
      setStatus: vi.fn(),
      changeSplit: vi.fn(),
      changeAmount: vi.fn(),
      updateTransaction: vi.fn(),
      updateInstallmentGroup: vi.fn(),
      deleteTransaction: vi.fn(),
      respondChargeRequest: vi.fn(),
      isLoading: false,
    } as unknown as ReturnType<typeof useFinance>)

    render(<TransactionTable onEditTx={vi.fn()} />)

    // Verify 500 count badge
    expect(screen.getAllByText(/500/).length).toBeGreaterThan(0)
    // Items should be rendered smoothly via virtualizer
    expect(screen.getAllByText(/Lançamento Virtualizado/i).length).toBeGreaterThan(0)
  })
})
