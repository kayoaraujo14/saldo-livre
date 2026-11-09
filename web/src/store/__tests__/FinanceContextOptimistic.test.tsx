// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { FinanceProvider, useFinance } from '@/store/FinanceContext'
import type { Transaction } from '@/types'

// Setup initial test transaction
const sampleTx: Transaction = {
  id: 'tx-100',
  description: 'Almoço Restaurante',
  category: 'Alimentação',
  subcategory: 'Restaurante',
  type: 'expense',
  paymentMethod: 'credit_card',
  status: 'pending',
  estimatedAmount: 50,
  actualAmount: null,
  totalAmount: 50,
  dueDate: '2026-11-09',
  competenceMonth: '2026-11',
  createdByUid: 'user-teste',
  paidByUid: 'user-teste',
  visibleToUids: ['user-teste'],
  allocations: [],
  userImpact: { 'user-teste': 50 },
  createdAt: 1000,
}

const sampleInst1: Transaction = {
  id: 'tx-inst-1',
  description: 'Geladeira (1/2)',
  category: 'Compras & Estilo de Vida',
  subcategory: 'Eletrodomésticos',
  type: 'expense',
  paymentMethod: 'credit_card',
  status: 'pending',
  estimatedAmount: null,
  actualAmount: 1000,
  totalAmount: 1000,
  dueDate: '2026-11-10',
  competenceMonth: '2026-11',
  createdByUid: 'user-teste',
  paidByUid: 'user-teste',
  visibleToUids: ['user-teste'],
  allocations: [],
  userImpact: { 'user-teste': 1000 },
  installmentGroupId: 'grp-geladeira',
  installmentNumber: 1,
  installmentsTotal: 2,
  createdAt: 1001,
}

const sampleInst2: Transaction = {
  id: 'tx-inst-2',
  description: 'Geladeira (2/2)',
  category: 'Compras & Estilo de Vida',
  subcategory: 'Eletrodomésticos',
  type: 'expense',
  paymentMethod: 'credit_card',
  status: 'pending',
  estimatedAmount: null,
  actualAmount: 1000,
  totalAmount: 1000,
  dueDate: '2026-12-10',
  competenceMonth: '2026-12',
  createdByUid: 'user-teste',
  paidByUid: 'user-teste',
  visibleToUids: ['user-teste'],
  allocations: [],
  userImpact: { 'user-teste': 1000 },
  installmentGroupId: 'grp-geladeira',
  installmentNumber: 2,
  installmentsTotal: 2,
  createdAt: 1002,
}

const mockFsUpdateTransaction = vi.fn().mockResolvedValue(undefined)
const mockFsUpdateTransactionsBatch = vi.fn().mockResolvedValue(undefined)
const mockFsDeleteTransaction = vi.fn().mockResolvedValue(undefined)
const mockFsDeleteTransactionsBatch = vi.fn().mockResolvedValue(undefined)
const mockFsAddTransaction = vi.fn().mockResolvedValue('tx-new-999')

vi.mock('@/lib/firebase', () => ({
  isFirebaseConfigured: true,
  onAuthChange: (cb: any) => {
    cb({ uid: 'user-teste', displayName: 'Usuário Teste' })
    return () => {}
  },
  upsertUserProfile: vi.fn().mockResolvedValue({
    uid: 'user-teste',
    displayName: 'Usuário Teste',
    email: 'teste@exemplo.com',
  }),
  subscribeUserProfile: (_uid: string, cb: any) => {
    cb({ uid: 'user-teste', displayName: 'Usuário Teste' })
    return () => {}
  },
  subscribeTransactions: (_uid: string, cb: any) => {
    cb([sampleTx, sampleInst1, sampleInst2])
    return () => {}
  },
  subscribeCards: (_uid: string, cb: any) => {
    cb([])
    return () => {}
  },
  subscribeRecurring: (_uid: string, cb: any) => {
    cb([])
    return () => {}
  },
  subscribeFriendships: (_uid: string, cb: any) => {
    cb([])
    return () => {}
  },
  subscribeMonthClosures: (_uid: string, cb: any) => {
    cb([])
    return () => {}
  },
  subscribeAgentRules: (_uid: string, cb: any) => {
    cb([])
    return () => {}
  },
  fsUpdateTransaction: (...args: any[]) => mockFsUpdateTransaction(...args),
  fsUpdateTransactionsBatch: (...args: any[]) => mockFsUpdateTransactionsBatch(...args),
  fsDeleteTransaction: (...args: any[]) => mockFsDeleteTransaction(...args),
  fsDeleteTransactionsBatch: (...args: any[]) => mockFsDeleteTransactionsBatch(...args),
  fsAddTransaction: (...args: any[]) => mockFsAddTransaction(...args),
}))

function TestHarness({
  onHookReady,
}: {
  onHookReady: (hook: ReturnType<typeof useFinance>) => void
}) {
  const finance = useFinance()
  onHookReady(finance)

  return (
    <div>
      <div data-testid="tx-count">{finance.transactions.length}</div>
      <ul>
        {finance.transactions.map((tx) => (
          <li key={tx.id} data-testid={`tx-${tx.id}`}>
            {tx.description} | {tx.totalAmount} | {tx.category} | {tx.status}
          </li>
        ))}
      </ul>
    </div>
  )
}

describe('FinanceContext - Reatividade Imediata e Atualização Otimista em Modo Firebase', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.removeItem('saldo-livre:force-demo')
  })

  it('atualiza imediatamente uma despesa na tela ao chamar updateTransaction sem aguardar recarregar', async () => {
    let hookRef!: ReturnType<typeof useFinance>
    render(
      <FinanceProvider>
        <TestHarness onHookReady={(h) => (hookRef = h)} />
      </FinanceProvider>
    )

    // Estado inicial
    expect(screen.getByTestId('tx-tx-100')).toHaveTextContent('Almoço Restaurante | 50 | Alimentação')

    // Executa a atualização
    await act(async () => {
      await hookRef.updateTransaction('tx-100', {
        description: 'Jantar Executivo',
        totalAmount: 120,
        actualAmount: 120,
        category: 'Outros',
        status: 'paid',
      })
    })

    // Deve refletir IMEDIATAMENTE no DOM com os novos dados
    expect(screen.getByTestId('tx-tx-100')).toHaveTextContent('Jantar Executivo | 120 | Outros | paid')
    expect(mockFsUpdateTransaction).toHaveBeenCalledWith(
      'tx-100',
      expect.objectContaining({
        description: 'Jantar Executivo',
        totalAmount: 120,
        category: 'Outros',
      })
    )
  })

  it('executa rollback do estado se a operação fsUpdateTransaction falhar', async () => {
    mockFsUpdateTransaction.mockRejectedValueOnce(new Error('Erro de conexão com Firestore'))

    let hookRef!: ReturnType<typeof useFinance>
    render(
      <FinanceProvider>
        <TestHarness onHookReady={(h) => (hookRef = h)} />
      </FinanceProvider>
    )

    expect(screen.getByTestId('tx-tx-100')).toHaveTextContent('Almoço Restaurante | 50')

    await act(async () => {
      await expect(
        hookRef.updateTransaction('tx-100', {
          description: 'Valor Inválido que Vai Falhar',
          totalAmount: 999,
        })
      ).rejects.toThrow('Erro de conexão com Firestore')
    })

    // O estado deve ser restaurado para os valores originais
    expect(screen.getByTestId('tx-tx-100')).toHaveTextContent('Almoço Restaurante | 50')
  })

  it('atualiza imediatamente todas as parcelas ao chamar updateInstallmentGroup', async () => {
    let hookRef!: ReturnType<typeof useFinance>
    render(
      <FinanceProvider>
        <TestHarness onHookReady={(h) => (hookRef = h)} />
      </FinanceProvider>
    )

    expect(screen.getByTestId('tx-tx-inst-1')).toHaveTextContent('Geladeira (1/2) | 1000')
    expect(screen.getByTestId('tx-tx-inst-2')).toHaveTextContent('Geladeira (2/2) | 1000')

    await act(async () => {
      await hookRef.updateInstallmentGroup('grp-geladeira', {
        description: 'Geladeira Frost Free',
        amount: 1100,
      })
    })

    // Ambas as parcelas devem refletir a nova descrição e valor imediatamente
    expect(screen.getByTestId('tx-tx-inst-1')).toHaveTextContent('Geladeira Frost Free | 1100')
    expect(screen.getByTestId('tx-tx-inst-2')).toHaveTextContent('Geladeira Frost Free | 1100')
    expect(mockFsUpdateTransactionsBatch).toHaveBeenCalledTimes(1)
  })

  it('remove imediatamente uma transação da lista ao chamar deleteTransaction', async () => {
    let hookRef!: ReturnType<typeof useFinance>
    render(
      <FinanceProvider>
        <TestHarness onHookReady={(h) => (hookRef = h)} />
      </FinanceProvider>
    )

    expect(screen.getByTestId('tx-tx-100')).toBeInTheDocument()

    await act(async () => {
      await hookRef.deleteTransaction('tx-100')
    })

    // Transação deve sumir instantaneamente
    expect(screen.queryByTestId('tx-tx-100')).not.toBeInTheDocument()
    expect(mockFsDeleteTransaction).toHaveBeenCalledWith('tx-100')
  })
})
