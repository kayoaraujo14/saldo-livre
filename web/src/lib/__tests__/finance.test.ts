import { describe, it, expect } from 'vitest'
import {
  canUserEditTransaction,
  computeIncomeConsumption,
  computeSummary,
  createRefundForTransaction,
  friendPctOf,
  friendShareOf,
  projectFutureMonths,
  resolveNextAcceptanceStatus,
} from '../finance'
import type { Card, MonthClosure, RecurringBill, Transaction, UserProfile } from '@/types'

describe('friendPctOf', () => {
  it('returns 100 if tx is falsy', () => {
    expect(friendPctOf(null as any)).toBe(100)
    expect(friendPctOf(undefined as any)).toBe(100)
  })

  it('returns clamped friendSharePct if it exists', () => {
    expect(friendPctOf({ friendSharePct: 30 } as any)).toBe(30)
    expect(friendPctOf({ friendSharePct: -10 } as any)).toBe(100) // clampPct logic
    expect(friendPctOf({ friendSharePct: 150 } as any)).toBe(100) // clampPct logic
  })

  it('returns 100 if totalAmount is falsy or 0', () => {
    expect(friendPctOf({ totalAmount: 0 } as any)).toBe(100)
    expect(friendPctOf({ totalAmount: null } as any)).toBe(100)
    expect(friendPctOf({ totalAmount: undefined } as any)).toBe(100)
  })

  it('calculates the percentage correctly based on friendShareOf', () => {
    // friendShareOf returns 0 if assignedFriendUid is not provided.
    expect(friendPctOf({ totalAmount: 100 } as any)).toBe(100) // (0 / 100) * 100 = 0 -> clamped to 100

    // fallback when assignedFriendUid exists but no allocations -> friendShareOf uses 50%
    expect(friendPctOf({ totalAmount: 200, assignedFriendUid: 'friend-1' } as any)).toBe(50) // (100 / 200) * 100 = 50

    // when allocation exists
    expect(
      friendPctOf({
        totalAmount: 200,
        assignedFriendUid: 'friend-1',
        allocations: [{ uid: 'friend-1', amount: 80 }]
      } as any)
    ).toBe(40) // (80 / 200) * 100 = 40
  })

  it('handles negative totalAmount correctly by taking absolute value', () => {
    expect(
      friendPctOf({
        totalAmount: -200,
        assignedFriendUid: 'friend-1',
        allocations: [{ uid: 'friend-1', amount: 80 }]
      } as any)
    ).toBe(40)
  })
})

describe('projectFutureMonths (12-month projection, channels, categories & items)', () => {
  const me: UserProfile = {
    uid: 'u1',
    displayName: 'Kayo',
    email: 'kayo@test.com',
    photoURL: null,
    monthlyIncome: 5000,
  }

  const cards: Card[] = [
    {
      id: 'card_nu',
      ownerUid: 'u1',
      ownerName: 'Kayo',
      cardName: 'Nubank',
      closingDay: 4,
      dueDay: 11,
      sharedWithUids: [],
      sharedWithUsers: [],
      frequentThirdParties: [],
    },
  ]

  const recurringBills: RecurringBill[] = [
    {
      id: 'rec_aluguel',
      ownerUid: 'u1',
      description: 'Aluguel',
      defaultAmount: 1500,
      dueDay: 10,
      category: 'Moradia',
      type: 'expense',
      paymentMethod: 'pix',
      estimationRule: 'fixed',
      active: true,
    },
    {
      id: 'rec_netflix',
      ownerUid: 'u1',
      description: 'Netflix',
      defaultAmount: 55.9,
      dueDay: 11,
      category: 'Assinaturas',
      type: 'expense',
      paymentMethod: 'credit_card',
      cardId: 'card_nu',
      estimationRule: 'fixed',
      active: true,
    },
  ]

  const transactions: Transaction[] = [
    {
      id: 'tx_1',
      createdByUid: 'u1',
      paidByUid: 'u1',
      cardId: 'card_nu',
      description: 'Supermercado na Fatura',
      category: 'Alimentação',
      subcategory: 'Supermercado',
      type: 'expense',
      paymentMethod: 'credit_card',
      status: 'pending',
      estimatedAmount: null,
      actualAmount: 400,
      totalAmount: 400,
      dueDate: '2026-11-11',
      competenceMonth: '2026-11',
      allocations: [{ type: 'PLATFORM_USER', uid: 'u1', name: 'Kayo', amount: 400, reimbursed: true }],
      userImpact: { u1: 400 },
      visibleToUids: ['u1'],
      createdAt: 1,
    },
    {
      id: 'tx_2',
      createdByUid: 'u1',
      paidByUid: 'u1',
      description: 'Padaria no Pix',
      category: 'Alimentação',
      subcategory: 'Padaria & Café',
      type: 'expense',
      paymentMethod: 'pix',
      status: 'paid',
      estimatedAmount: null,
      actualAmount: 80,
      totalAmount: 80,
      dueDate: '2026-11-05',
      competenceMonth: '2026-11',
      allocations: [{ type: 'PLATFORM_USER', uid: 'u1', name: 'Kayo', amount: 80, reimbursed: true }],
      userImpact: { u1: 80 },
      visibleToUids: ['u1'],
      createdAt: 2,
    },
  ]

  it('defaults to 12 months forward and breaks down invoice, fixed bills, variable pix/debit, categories, and items', () => {
    const result = projectFutureMonths({
      all: transactions,
      bills: recurringBills,
      profile: me,
      uid: 'u1',
      startMonthKey: '2026-11',
      count: 12,
      cards,
    })
    expect(result).toHaveLength(12)

    const nov = result[0]
    expect(nov.monthKey).toBe('2026-11')
    expect(nov.projectedIncome).toBe(5000)
    // Invoice = 400 (Supermercado) + 55.90 (Netflix subscription on card) = 455.90
    expect(nov.invoiceExpense).toBe(455.9)
    // Fixed bills = 1500 (Aluguel)
    expect(nov.fixedBillsExpense).toBe(1500)
    // Variable account (Pix/Debit) = 80 (Padaria)
    expect(nov.variableAccountExpense).toBe(80)
    expect(nov.projectedExpense).toBe(2035.9)
    expect(nov.projectedFreeBalance).toBe(2964.1)

    // Alimentação combines invoice item (400) + pix item (80) = 480
    const alimCat = nov.byCategory?.find((c) => c.category === 'Alimentação')
    expect(alimCat).toBeDefined()
    expect(alimCat?.amount).toBe(480)
    expect(alimCat?.invoiceAmount).toBe(400)
    expect(alimCat?.accountAmount).toBe(80)

    // Nominal items include real txs + projected recurring bills + salary
    expect(nov.items?.some((i) => i.description === 'Supermercado na Fatura' && i.origin === 'invoice')).toBe(true)
    expect(nov.items?.some((i) => i.description === 'Netflix' && i.origin === 'invoice' && i.isRecurringProjection)).toBe(true)
    expect(nov.items?.some((i) => i.description === 'Aluguel' && i.origin === 'fixed_bill' && i.isRecurringProjection)).toBe(true)
    expect(nov.items?.some((i) => i.description === 'Padaria no Pix' && i.origin === 'debit_pix')).toBe(true)
  })

  it('considers carryoverAmount from closures in projectedFreeBalance and items list', () => {
    const closures: MonthClosure[] = [
      {
        id: 'closure_2026_10',
        uid: 'u1',
        closedMonth: '2026-10',
        targetMonth: '2026-11',
        totalSurplus: 450,
        carryoverToNextMonth: 450,
        investedAmount: 0,
        closedAt: Date.now(),
      },
    ]

    const result = projectFutureMonths({
      all: transactions,
      bills: recurringBills,
      profile: me,
      uid: 'u1',
      startMonthKey: '2026-11',
      count: 3,
      cards,
      closures,
    })

    const nov = result[0]
    expect(nov.monthKey).toBe('2026-11')
    expect(nov.carryoverAmount).toBe(450)
    expect(nov.projectedIncome).toBe(5000)
    expect(nov.projectedExpense).toBe(2035.9)
    // 5000 (income) + 450 (carryover) - 2035.9 (expense) = 3414.1
    expect(nov.projectedFreeBalance).toBe(3414.1)
    expect(nov.items?.some((i) => i.id === 'proj_carryover_2026-11' && i.amount === 450)).toBe(true)
  })

  it('cascades projected deficit to next month as previousMonthDeficit (Conservative Cascade)', () => {
    // Cenário:
    // Mês 1 (2026-11): Renda 3000, Despesas 4000 -> Déficit projetado de -1000
    // Mês 2 (2026-12): Renda 3000, Despesas 1500 -> Deve herdar previousMonthDeficit = 1000! Saldo Livre = 3000 - 1000 - 1500 = +500
    // Mês 3 (2027-01): Renda 3000, Despesas 1500 -> Como Mês 2 fechou positivo (+500), Mês 3 NÃO herda déficit nem acumula sobra sem fechamento (conservador)
    const customTxs: Transaction[] = [
      {
        id: 'tx_nov_extra',
        description: 'Despesa Pesada Nov',
        category: 'Outros',
        type: 'expense',
        status: 'paid',
        paymentMethod: 'pix',
        totalAmount: 4000,
        actualAmount: 4000,
        dueDate: '2026-11-10',
        competenceMonth: '2026-11',
        createdByUid: 'u1',
        paidByUid: 'u1',
        visibleToUids: ['u1'],
        userImpact: { u1: 4000 },
        splitType: 'mine',
        allocations: [{ type: 'PLATFORM_USER', uid: 'u1', name: 'Você', amount: 4000, reimbursed: true }],
        createdAt: Date.now(),
      },
      {
        id: 'tx_dec_light',
        description: 'Despesa Leve Dez',
        category: 'Outros',
        type: 'expense',
        status: 'estimated',
        paymentMethod: 'pix',
        totalAmount: 1500,
        estimatedAmount: 1500,
        dueDate: '2026-12-10',
        competenceMonth: '2026-12',
        createdByUid: 'u1',
        paidByUid: 'u1',
        visibleToUids: ['u1'],
        userImpact: { u1: 1500 },
        splitType: 'mine',
        allocations: [{ type: 'PLATFORM_USER', uid: 'u1', name: 'Você', amount: 1500, reimbursed: false }],
        createdAt: Date.now(),
      },
      {
        id: 'tx_jan_light',
        description: 'Despesa Leve Jan',
        category: 'Outros',
        type: 'expense',
        status: 'estimated',
        paymentMethod: 'pix',
        totalAmount: 1500,
        estimatedAmount: 1500,
        dueDate: '2027-01-10',
        competenceMonth: '2027-01',
        createdByUid: 'u1',
        paidByUid: 'u1',
        visibleToUids: ['u1'],
        userImpact: { u1: 1500 },
        splitType: 'mine',
        allocations: [{ type: 'PLATFORM_USER', uid: 'u1', name: 'Você', amount: 1500, reimbursed: false }],
        createdAt: Date.now(),
      },
    ]

    const customProfile: UserProfile = {
      ...me,
      monthlyIncome: 3000,
    }

    const projections = projectFutureMonths({
      all: customTxs,
      bills: [],
      profile: customProfile,
      uid: 'u1',
      startMonthKey: '2026-11',
      count: 3,
      cards: [],
      closures: [],
    })

    const mNov = projections[0]
    expect(mNov.monthKey).toBe('2026-11')
    expect(mNov.projectedIncome).toBe(3000)
    expect(mNov.projectedExpense).toBe(4000)
    expect(mNov.projectedFreeBalance).toBe(-1000)

    const mDec = projections[1]
    expect(mDec.monthKey).toBe('2026-12')
    expect(mDec.previousMonthDeficit).toBe(1000)
    expect(mDec.projectedIncome).toBe(3000)
    expect(mDec.projectedExpense).toBe(1500)
    // (3000 - 1000) - 1500 = +500
    expect(mDec.projectedFreeBalance).toBe(500)

    const mJan = projections[2]
    expect(mJan.monthKey).toBe('2027-01')
    expect(mJan.previousMonthDeficit).toBe(0)
    expect(mJan.carryoverAmount).toBe(0)
    expect(mJan.projectedFreeBalance).toBe(1500)
  })

  it('inherits accumulated deficit when computing summary on a future month without closure', () => {
    // Transação em 2026-10 deixando déficit de R$ 700
    const txPast: Transaction = {
      id: 'tx_oct_def',
      description: 'Déficit de Outubro',
      category: 'Outros',
      type: 'expense',
      status: 'paid',
      paymentMethod: 'pix',
      totalAmount: 700,
      actualAmount: 700,
      dueDate: '2026-10-15',
      competenceMonth: '2026-10',
      createdByUid: 'u1',
      paidByUid: 'u1',
      visibleToUids: ['u1'],
      userImpact: { u1: 700 },
      splitType: 'mine',
      allocations: [{ type: 'PLATFORM_USER', uid: 'u1', name: 'Você', amount: 700, reimbursed: true }],
      createdAt: Date.now(),
    }

    // Navegando para mês futuro distante 2027-05 onde não há receita nem despesa
    const summaryFuture = computeSummary([txPast], 'u1', '2027-05', [], [])

    expect(summaryFuture.monthState).toBe('future')
    expect(summaryFuture.previousMonthDeficit).toBe(700)
    expect(summaryFuture.freeBalance).toBe(-700)
  })
})

describe('resolveNextAcceptanceStatus & friendShareOf', () => {
  const baseAcceptedTx: Transaction = {
    id: 'tx_shared_1',
    description: 'iFood sorvete',
    category: 'Alimentação',
    type: 'expense',
    status: 'pending',
    paymentMethod: 'credit_card',
    cardId: 'card_nubank_leticia',
    totalAmount: 35.16,
    actualAmount: 35.16,
    dueDate: '2026-10-20',
    competenceMonth: '2026-10',
    createdByUid: 'uid_leticia',
    paidByUid: 'uid_leticia',
    visibleToUids: ['uid_leticia', 'uid_kayo'],
    userImpact: { uid_leticia: 17.58, uid_kayo: 17.58 },
    splitType: 'friend',
    assignedFriendUid: 'uid_kayo',
    assignedFriendName: 'Kayo',
    friendSharePct: 50,
    acceptanceStatus: 'accepted',
    respondedAt: 1760000000000,
    allocations: [
      { type: 'PLATFORM_USER', uid: 'uid_leticia', name: 'Leticia', amount: 17.58, reimbursed: true },
      { type: 'PLATFORM_USER', uid: 'uid_kayo', name: 'Kayo', amount: 17.58, reimbursed: false },
    ],
    createdAt: 1759000000000,
  }

  it('preserves accepted status and respondedAt when status changes to paid without altering friend share', () => {
    const res = resolveNextAcceptanceStatus(baseAcceptedTx, {
      ...baseAcceptedTx,
      status: 'paid',
    })
    expect(res.acceptanceStatus).toBe('accepted')
    expect(res.respondedAt).toBe(1760000000000)
  })

  it('resets acceptanceStatus to pending_approval when friend monetary share changes', () => {
    const res = resolveNextAcceptanceStatus(baseAcceptedTx, {
      ...baseAcceptedTx,
      totalAmount: 50,
      allocations: [
        { type: 'PLATFORM_USER', uid: 'uid_leticia', name: 'Leticia', amount: 25, reimbursed: true },
        { type: 'PLATFORM_USER', uid: 'uid_kayo', name: 'Kayo', amount: 25, reimbursed: false },
      ],
    })
    expect(res.acceptanceStatus).toBe('pending_approval')
    expect(res.respondedAt).toBeNull()
  })

  it('uses fallbackFriendUid when assignedFriendUid is missing on shared-card split', () => {
    const txWithoutAssignedUid: Transaction = {
      ...baseAcceptedTx,
      assignedFriendUid: null,
      splitType: 'split_50',
    }
    expect(friendShareOf(txWithoutAssignedUid, 'uid_kayo')).toBe(17.58)
    expect(friendPctOf(txWithoutAssignedUid, 'uid_kayo')).toBe(50)
  })

  it('inherits accepted status when creating a refund for an already accepted transaction', () => {
    const refund = createRefundForTransaction(baseAcceptedTx, 16.8, {
      uid: 'uid_leticia',
      displayName: 'Leticia',
    })
    expect(refund.acceptanceStatus).toBe('accepted')
    expect(refund.respondedAt).toBe(1760000000000)
  })

  it('correctly computes freeBalance with previous month closure carryover and investment redemptions parity', () => {
    const userUid = 'user_abc'
    const monthKey = '2026-11'
    const prevMonthKey = '2026-10'

    const txs: Transaction[] = [
      {
        id: 'tx_income_1',
        createdByUid: userUid,
        paidByUid: userUid,
        visibleToUids: [userUid],
        description: 'Salário',
        category: 'Renda',
        type: 'income',
        paymentMethod: 'pix',
        status: 'paid',
        totalAmount: 6921.15,
        dueDate: '2026-11-05',
        competenceMonth: monthKey,
        allocations: [],
        createdAt: 1762000000000,
      },
      {
        id: 'tx_expense_1',
        createdByUid: userUid,
        paidByUid: userUid,
        visibleToUids: [userUid],
        description: 'Aluguel & Contas',
        category: 'Moradia',
        type: 'expense',
        paymentMethod: 'bill',
        status: 'paid',
        totalAmount: 6085.80,
        dueDate: '2026-11-10',
        competenceMonth: monthKey,
        allocations: [],
        createdAt: 1762000000000,
      },
      {
        id: 'tx_expense_pending',
        createdByUid: userUid,
        paidByUid: userUid,
        visibleToUids: [userUid],
        description: 'Financiamentos & Dívidas',
        category: 'Financiamentos & Dívidas',
        type: 'expense',
        paymentMethod: 'bill',
        status: 'pending',
        totalAmount: 1227.17,
        dueDate: '2026-11-20',
        competenceMonth: monthKey,
        allocations: [],
        createdAt: 1762000000000,
      },
      {
        id: 'tx_invest_1',
        createdByUid: userUid,
        paidByUid: userUid,
        visibleToUids: [userUid],
        description: 'Aporte Tesouro',
        category: 'Investimentos',
        subcategory: 'Tesouro Direto',
        type: 'expense',
        paymentMethod: 'pix',
        status: 'paid',
        totalAmount: 3549.55,
        dueDate: '2026-11-15',
        competenceMonth: monthKey,
        allocations: [],
        createdAt: 1762000000000,
      },
      {
        id: 'tx_invest_resgate',
        createdByUid: userUid,
        paidByUid: userUid,
        visibleToUids: [userUid],
        description: 'Resgate CDB',
        category: 'Investimentos',
        subcategory: 'Resgate',
        type: 'income',
        paymentMethod: 'pix',
        status: 'paid',
        totalAmount: 450.45,
        dueDate: '2026-11-18',
        competenceMonth: monthKey,
        allocations: [],
        createdAt: 1762000000000,
      },
    ]

    const closures: MonthClosure[] = [
      {
        id: 'closure_oct',
        uid: userUid,
        closedMonth: prevMonthKey,
        targetMonth: monthKey,
        totalSurplus: 927.24,
        carryoverToNextMonth: 927.24,
        investedAmount: 0,
        cushionAmount: 0,
        closedAt: 1761900000000,
      },
    ]

    const summary = computeSummary(txs, userUid, monthKey, [], closures)
    expect(summary.carryoverAmount).toBe(927.24)
    // totalInvested should abate the resgate: 4000.00 - 450.45 = 3549.55
    expect(summary.totalInvested).toBe(3549.55)
    // freeBalance: income (6921.15 + 450.45) + carryover (927.24) - expenses (6085.80 + 1227.17 + 4000.00) = 535.87
    expect(summary.income).toBe(7371.6)
  })

  describe('canUserEditTransaction', () => {
    const myUid = 'user_me'
    const otherUid = 'user_other'

    it('returns false when currentUid is null or blank', () => {
      const tx = { id: 'tx_1', createdByUid: myUid } as Transaction
      expect(canUserEditTransaction(tx, null)).toBe(false)
      expect(canUserEditTransaction(tx, '')).toBe(false)
    })

    it('returns false for synthetic invoice transactions', () => {
      const invoiceTx = {
        id: 'invoice_card123_2026-03',
        createdByUid: myUid,
        paidByUid: myUid,
      } as Transaction
      expect(canUserEditTransaction(invoiceTx, myUid)).toBe(false)
    })

    it('returns true when user is creator or payer', () => {
      const tx1 = { id: 'tx_1', createdByUid: myUid, paidByUid: myUid } as Transaction
      expect(canUserEditTransaction(tx1, myUid)).toBe(true)

      const tx2 = { id: 'tx_2', createdByUid: '', paidByUid: myUid } as Transaction
      expect(canUserEditTransaction(tx2, myUid)).toBe(true)

      const tx3 = { id: 'tx_3_payer', createdByUid: otherUid, paidByUid: myUid } as Transaction
      expect(canUserEditTransaction(tx3, myUid)).toBe(true)
    })

    it('returns false when transaction was created and paid by another user', () => {
      const tx = { id: 'tx_4', createdByUid: otherUid, paidByUid: otherUid } as Transaction
      expect(canUserEditTransaction(tx, myUid)).toBe(false)
    })

    it('returns false when user is assigned friend in split', () => {
      const tx = {
        id: 'tx_5',
        createdByUid: otherUid,
        paidByUid: otherUid,
        assignedFriendUid: myUid,
      } as Transaction
      expect(canUserEditTransaction(tx, myUid)).toBe(false)
    })

    it('returns false when user is in allocations but did not create nor pay', () => {
      const tx = {
        id: 'tx_6',
        createdByUid: otherUid,
        paidByUid: otherUid,
        allocations: [{ type: 'PLATFORM_USER', uid: myUid, name: 'Kayo', amount: 50.0 }],
      } as Transaction
      expect(canUserEditTransaction(tx, myUid)).toBe(false)
    })
  })

  describe('computeIncomeConsumption', () => {
    it('includes carryoverAmount in effectiveIncome so commitment does not falsely exceed 100%', () => {
      const summary = {
        income: 6921.15,
        carryoverAmount: 927.24,
        previousMonthDeficit: 0,
        paid: 6085.8,
        committed: 1227.17,
        freeBalance: 535.42,
      } as any
      const metrics = computeIncomeConsumption(summary)
      // 6921.15 + 927.24 = 7848.39
      expect(metrics.effectiveIncome).toBe(7848.39)
      // 6085.80 / 7848.39 = 77.54% -> 78%
      expect(metrics.consumptionPct).toBe(78)
      // (6085.80 + 1227.17) / 7848.39 = 7312.97 / 7848.39 = 93.18% -> 93%
      expect(metrics.committedPct).toBe(93)
      // 535.42 / 7848.39 = 6.82% -> 7%
      expect(metrics.remainingPct).toBe(7)
      expect(metrics.statusBadge).toBe('No Limite')
      expect(metrics.statusColor).toBe('emerald')
    })

    it('subtracts previousMonthDeficit from effectiveIncome', () => {
      const summary = {
        income: 5000,
        carryoverAmount: 0,
        previousMonthDeficit: 1000,
        paid: 3600,
        committed: 400,
        freeBalance: 0,
      } as any
      const metrics = computeIncomeConsumption(summary)
      expect(metrics.effectiveIncome).toBe(4000)
      expect(metrics.consumptionPct).toBe(90)
      expect(metrics.committedPct).toBe(100)
      expect(metrics.remainingPct).toBe(0)
      expect(metrics.statusBadge).toBe('Atenção')
      expect(metrics.statusColor).toBe('amber')
    })
  })
})

