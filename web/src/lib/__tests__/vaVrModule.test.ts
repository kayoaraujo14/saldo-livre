import { describe, it, expect } from 'vitest'
import {
  ACTIVE_CATEGORIES,
  EXPENSE_CATEGORIES,
  INCOME_CATEGORIES,
  getBudgetNature,
  isVaVrCategory,
  isVaVrTransaction,
  resolveStrictCategory,
  type Card,
  type RecurringBill,
  type Transaction,
  type UserProfile,
} from '@/types'
import {
  NON_ENVELOPE_CATEGORIES,
  calculateFinancialHealthScore,
  computeAccountCashFlow,
  computeCategoryBudgets,
  computeSummary,
  guessCategoryAndSubcategory,
  projectFutureMonths,
  suggestCategoryBudgets,
} from '../finance'

const MY_UID = 'u1'
const PARTNER_UID = 'u2'

const me: UserProfile = {
  uid: MY_UID,
  displayName: 'Kayo',
  email: 'kayo@test.com',
  photoURL: null,
  monthlyIncome: 5000,
}


function makeTx(overrides: Partial<Transaction> = {}): Transaction {
  const total = overrides.totalAmount ?? overrides.actualAmount ?? 100
  return {
    id: 'tx_' + Math.random().toString(36).slice(2, 8),
    createdByUid: MY_UID,
    paidByUid: MY_UID,
    description: 'Lançamento Teste',
    category: 'Alimentação',
    subcategory: 'Supermercado',
    type: 'expense',
    paymentMethod: 'pix',
    status: 'paid',
    estimatedAmount: null,
    actualAmount: total,
    totalAmount: total,
    dueDate: '2099-01-10',
    competenceMonth: '2099-01',
    splitType: 'mine',
    allocations: [{ type: 'PLATFORM_USER', uid: MY_UID, name: 'Kayo', amount: total, reimbursed: true }],
    userImpact: { [MY_UID]: total },
    visibleToUids: [MY_UID],
    createdAt: 1,
    ...overrides,
  }
}

describe('Módulo VA/VR (Vale-Alimentação / Vale-Refeição)', () => {
  it('1. Isola 100% entradas, saídas e contas recorrentes de VA/VR do Saldo Livre bancário, fluxo de caixa e projeções futuras', () => {
    const txs: Transaction[] = [
      makeTx({
        id: 'sal',
        description: 'Salário CLT',
        category: 'Salário & Remuneração',
        subcategory: 'Salário Principal',
        type: 'income',
        totalAmount: 5000,
        actualAmount: 5000,
      }),
      makeTx({
        id: 'rent',
        description: 'Aluguel',
        category: 'Moradia',
        subcategory: 'Aluguel',
        type: 'expense',
        totalAmount: 1500,
        actualAmount: 1500,
      }),
      makeTx({
        id: 'vr_recarga',
        description: 'Recarga Alelo VA/VR',
        category: 'VA/VR',
        subcategory: 'Recarga VA/VR',
        type: 'income',
        paymentMethod: 'pix',
        totalAmount: 1200,
        actualAmount: 1200,
      }),
      makeTx({
        id: 'vr_mercado',
        description: 'Supermercado Pão de Açúcar',
        category: 'VA/VR',
        subcategory: 'Supermercado',
        type: 'expense',
        paymentMethod: 'debit',
        totalAmount: 450,
        actualAmount: 450,
      }),
      // Transação legada sob 'Benefícios' + subcategoria VA/VR também pertence ao módulo isolado
      makeTx({
        id: 'vr_legado',
        description: 'Lanche VR',
        category: 'Benefícios' as Transaction['category'],
        subcategory: 'Vale Refeição (VR)',
        type: 'expense',
        paymentMethod: 'debit',
        status: 'pending',
        totalAmount: 50,
        actualAmount: 50,
      }),
    ]

    const vaVrRecurringBill: RecurringBill = {
      id: 'rec_vr',
      ownerUid: MY_UID,
      description: 'Recarga Mensal Flash VA/VR',
      defaultAmount: 1100,
      dueDay: 1,
      category: 'VA/VR',
      subcategory: 'Recarga VA/VR',
      type: 'income',
      paymentMethod: 'pix',
      estimationRule: 'fixed',
      active: true,
      startMonth: '2099-02',
    }

    const summary = computeSummary(txs, MY_UID, '2099-01', [], [], [vaVrRecurringBill])
    expect(summary.income).toBe(5000)
    expect(summary.paid).toBe(1500)
    expect(summary.committed).toBe(0)
    expect(summary.freeBalance).toBe(3500)

    const cashFlow = computeAccountCashFlow(txs, MY_UID, '2099-01', summary, [], '2099-01-15')
    expect(cashFlow.accountIncomesReceived).toBe(5000)
    expect(cashFlow.accountExpensesPaid).toBe(1500)
    expect(cashFlow.currentBankBalance).toBe(3500)

    const proj = projectFutureMonths({
      all: txs,
      bills: [vaVrRecurringBill],
      profile: { ...me, monthlyIncome: 0 },
      uid: MY_UID,
      startMonthKey: '2099-01',
      count: 3,
      cards: [] as Card[],
    })
    expect(proj[0].projectedIncome).toBe(5000)
    expect(proj[0].projectedExpense).toBe(1500)
    expect(proj[0].projectedFreeBalance).toBe(3500)
    // Fevereiro (2099-02) não deve somar recarga VA/VR recorrente de R$ 1.100 na renda da conta corrente
    expect(proj[1].projectedIncome).toBe(0)
  })

  it('2 & 3. Separa Disponível Agora (paid) de Projetado no Mês (pending/estimated + recorrentes VA/VR não lançadas) e calcula Limite Diário VA/VR', () => {
    const txs: Transaction[] = [
      makeTx({
        id: 'vr_in_paid',
        description: 'Recarga Pluxee Paga',
        category: 'VA/VR',
        subcategory: 'Recarga VA/VR',
        type: 'income',
        status: 'paid',
        totalAmount: 1000,
        actualAmount: 1000,
      }),
      makeTx({
        id: 'vr_in_pending',
        description: 'Bônus Alimentação Previsto',
        category: 'VA/VR',
        subcategory: 'Vale Alimentação (VA)',
        type: 'income',
        status: 'pending',
        totalAmount: 200,
        actualAmount: 200,
      }),
      makeTx({
        id: 'vr_out_paid',
        description: 'Almoço Restaurante',
        category: 'VA/VR',
        subcategory: 'Restaurante',
        type: 'expense',
        status: 'paid',
        totalAmount: 380,
        actualAmount: 380,
      }),
      makeTx({
        id: 'vr_out_pending',
        description: 'Mercado Fim de Semana',
        category: 'VA/VR',
        subcategory: 'Supermercado',
        type: 'expense',
        status: 'pending',
        totalAmount: 120,
        actualAmount: 120,
      }),
    ]

    const unlaunchedBill: RecurringBill = {
      id: 'rec_vr_in',
      ownerUid: MY_UID,
      description: 'Complemento VR Mensal',
      defaultAmount: 100,
      dueDay: 20,
      category: 'VA/VR',
      subcategory: 'Vale Refeição (VR)',
      type: 'income',
      paymentMethod: 'pix',
      estimationRule: 'fixed',
      active: true,
      startMonth: '2099-01',
    }

    const summary = computeSummary(txs, MY_UID, '2099-01', [], [], [unlaunchedBill])
    expect(summary.vaVrCarryover).toBe(0)
    expect(summary.vaVrIncomePaid).toBe(1000)
    // Pending real (200) + conta recorrente VA/VR não lançada (100)
    expect(summary.vaVrIncomePending).toBe(300)
    expect(summary.vaVrIncome).toBe(1300)
    expect(summary.vaVrSpentPaid).toBe(380)
    expect(summary.vaVrSpentPending).toBe(120)
    expect(summary.vaVrSpent).toBe(500)
    // Disponível Agora = carryover (0) + pago entrada (1000) - pago gasto (380) = 620
    expect(summary.vaVrAvailableBalance).toBe(620)
    // Projetado = carryover (0) + total entrada (1300) - total gasto (500) = 800
    expect(summary.vaVrProjectedBalance).toBe(800)
    // 2099-01 (futuro) tem 31 dias restantes -> 620 / 31 = 20
    expect(summary.daysRemaining).toBe(31)
    expect(summary.vaVrDailyLimit).toBe(20)
    expect(summary.vaVrBySubcategory?.reduce((acc, s) => acc + s.count, 0)).toBe(2)
  })

  it('4. Faz rollover cronológico automático (independente de MonthClosure) e permite Saldo VA/VR negativo isolado sem tocar na conta corrente', () => {
    const txs: Transaction[] = [
      // Mês anterior positivo (saldo +300) sem MonthClosure cadastrado
      makeTx({
        id: 'm0_in',
        category: 'VA/VR',
        subcategory: 'Recarga VA/VR',
        type: 'income',
        dueDate: '2098-11-05',
        competenceMonth: '2098-11',
        totalAmount: 800,
        actualAmount: 800,
      }),
      makeTx({
        id: 'm0_out',
        category: 'VA/VR',
        subcategory: 'Supermercado',
        type: 'expense',
        dueDate: '2098-11-20',
        competenceMonth: '2098-11',
        totalAmount: 500,
        actualAmount: 500,
      }),
      // Segundo mês (+300 anterior + 400 recarga - 250 gasto = +450 carryover para 2099-01)
      makeTx({
        id: 'm1_in',
        category: 'VA/VR',
        subcategory: 'Recarga VA/VR',
        type: 'income',
        dueDate: '2098-12-05',
        competenceMonth: '2098-12',
        totalAmount: 400,
        actualAmount: 400,
      }),
      makeTx({
        id: 'm1_out',
        category: 'VA/VR',
        subcategory: 'Restaurante',
        type: 'expense',
        dueDate: '2098-12-18',
        competenceMonth: '2098-12',
        totalAmount: 250,
        actualAmount: 250,
      }),
      // Mês atual (2099-01): estoura VA/VR (450 carryover - 600 gasto = -150) mas conta bancária tem +4000 intactos
      makeTx({
        id: 'm2_sal',
        category: 'Salário & Remuneração',
        subcategory: 'Salário Principal',
        type: 'income',
        dueDate: '2099-01-05',
        competenceMonth: '2099-01',
        totalAmount: 5000,
        actualAmount: 5000,
      }),
      makeTx({
        id: 'm2_conta',
        category: 'Moradia',
        subcategory: 'Aluguel',
        type: 'expense',
        dueDate: '2099-01-10',
        competenceMonth: '2099-01',
        totalAmount: 1000,
        actualAmount: 1000,
      }),
      makeTx({
        id: 'm2_vr_estouro',
        category: 'VA/VR',
        subcategory: 'Supermercado',
        type: 'expense',
        dueDate: '2099-01-12',
        competenceMonth: '2099-01',
        totalAmount: 600,
        actualAmount: 600,
      }),
    ]

    const janSummary = computeSummary(txs, MY_UID, '2099-01', [], [], [])
    expect(janSummary.vaVrCarryover).toBe(450)
    expect(janSummary.vaVrAvailableBalance).toBe(-150)
    expect(janSummary.vaVrDailyLimit).toBe(0)
    // Conta corrente principal permanece 100% blindada do estouro de -150 do VA/VR
    expect(janSummary.freeBalance).toBe(4000)

    // Mês seguinte após mês negativo parte de 0 no carryover (não acumula dívida bancária)
    const febSummary = computeSummary(txs, MY_UID, '2099-02', [], [], [])
    expect(febSummary.vaVrCarryover).toBe(0)
  })

  it('5. Rateio no VA/VR desconta 100% do pote do pagador e gera receivable na conta principal pela cota do parceiro/terceiro', () => {
    const splitPurchase = makeTx({
      id: 'vr_split_200',
      createdByUid: MY_UID,
      paidByUid: MY_UID,
      description: 'Compras do Mês Carrefour (VA)',
      category: 'VA/VR',
      subcategory: 'Supermercado',
      type: 'expense',
      paymentMethod: 'debit',
      status: 'paid',
      totalAmount: 200,
      actualAmount: 200,
      splitType: '50_50',
      allocations: [
        { type: 'PLATFORM_USER', uid: MY_UID, name: 'Kayo', amount: 100, reimbursed: true },
        { type: 'PLATFORM_USER', uid: PARTNER_UID, name: 'Ana', amount: 100, reimbursed: false },
      ],
      userImpact: { [MY_UID]: 100, [PARTNER_UID]: 100 },
      visibleToUids: [MY_UID, PARTNER_UID],
    })

    const payerSummary = computeSummary([splitPurchase], MY_UID, '2099-01', [], [], [])
    // Pagador desembolsou R$ 200 integrais no cartão VA/VR
    expect(payerSummary.vaVrSpent).toBe(200)
    expect(payerSummary.vaVrAvailableBalance).toBe(-200)
    // Recebe R$ 100 de reembolso do parceiro na conta principal
    expect(payerSummary.receivableFromPartner).toBe(100)
    expect(payerSummary.receivableTotal).toBe(100)

    const partnerSummary = computeSummary([splitPurchase], PARTNER_UID, '2099-01', [], [], [])
    // Parceiro não passou o próprio cartão VA/VR
    expect(partnerSummary.vaVrSpent).toBe(0)
    expect(partnerSummary.payableToOthers).toBe(100)
  })

  it('6. Mantém VA/VR como auto-orçado: excluído dos envelopes tradicionais, sugestões e score de custo de vida', () => {
    expect(NON_ENVELOPE_CATEGORIES).toContain('VA/VR')
    expect(getBudgetNature('VA/VR')).toBe('need')
    expect(getBudgetNature('VA/VR', 'Recarga VA/VR')).toBe('income')
    expect(ACTIVE_CATEGORIES).toContain('VA/VR')
    expect(EXPENSE_CATEGORIES).toContain('VA/VR')
    expect(INCOME_CATEGORIES).toContain('VA/VR')

    const txs: Transaction[] = [
      makeTx({
        id: 's_sal',
        category: 'Salário & Remuneração',
        subcategory: 'Salário Principal',
        type: 'income',
        dueDate: '2026-10-05',
        competenceMonth: '2026-10',
        totalAmount: 5000,
        actualAmount: 5000,
      }),
      makeTx({
        id: 's_alim',
        category: 'Alimentação',
        subcategory: 'Supermercado',
        type: 'expense',
        dueDate: '2026-10-12',
        competenceMonth: '2026-10',
        totalAmount: 600,
        actualAmount: 600,
      }),
      makeTx({
        id: 's_vavr_huge',
        category: 'VA/VR',
        subcategory: 'Supermercado',
        type: 'expense',
        dueDate: '2026-10-15',
        competenceMonth: '2026-10',
        totalAmount: 4000,
        actualAmount: 4000,
      }),
    ]

    const report = computeCategoryBudgets(
      txs,
      { Alimentação: 1000, 'VA/VR': 500 } as Record<string, number>,
      '2026-10',
      '2026-10-20',
      MY_UID,
      txs,
      5000,
    )
    expect(report.items.some((i) => i.category === 'VA/VR')).toBe(false)
    expect(report.totalSpent).toBe(600)

    const suggested = suggestCategoryBudgets(txs, MY_UID, '2026-11', 5000)
    expect(suggested.suggestedBudgets['VA/VR']).toBeUndefined()

    const janSummary = computeSummary(
      txs.map((t) => ({ ...t, competenceMonth: '2099-01', dueDate: '2099-01-15' })),
      MY_UID,
      '2099-01',
      [],
      [],
      [],
    )
    // Mesmo gastando R$ 4.000 no VA/VR, o custo de vida da saúde financeira considera só os R$ 600 da conta corrente
    const health = calculateFinancialHealthScore(janSummary, 3000, report)
    expect(health.monthlyLivingCost).toBe(600)
    expect(health.spendingRatioPercentage).toBe(12)
  })

  it('7. Classifica marcas de benefício e resolve categorias duais de VA/VR no classificador e validação estrita', () => {
    expect(isVaVrCategory('VA/VR')).toBe(true)
    expect(isVaVrCategory('Benefícios', 'Vale Refeição (VR)')).toBe(true)
    expect(isVaVrTransaction({ category: 'VA/VR', subcategory: 'Padaria' })).toBe(true)

    const brands = [
      { text: 'Recarga Benefícios Pluxee mensal', type: 'income' as const, expectedCat: 'VA/VR', expectedSub: 'Recarga VA/VR' },
      { text: 'Recarga Alelo VA mensal', type: 'income' as const, expectedCat: 'VA/VR', expectedSub: 'Vale Alimentação (VA)' },
      { text: 'Crédito Caju Vale Refeição', type: 'income' as const, expectedCat: 'VA/VR', expectedSub: 'Vale Refeição (VR)' },
      { text: 'Restaurante Pluxee almoço', type: 'expense' as const, expectedCat: 'VA/VR', expectedSub: 'Restaurante' },
      { text: 'Supermercado iFood Benefícios', type: 'expense' as const, expectedCat: 'VA/VR', expectedSub: 'Supermercado' },
      { text: 'Ticket Padaria café', type: 'expense' as const, expectedCat: 'VA/VR', expectedSub: 'Padaria' },
      { text: 'Compra Flash Benefícios', type: 'expense' as const, expectedCat: 'VA/VR' },
      { text: 'Almoço Sodexo Express', type: 'expense' as const, expectedCat: 'VA/VR' },
    ]

    for (const sample of brands) {
      const guess = guessCategoryAndSubcategory(sample.text, undefined, sample.type)
      expect(guess.category).toBe(sample.expectedCat ?? 'VA/VR')
      if (sample.expectedSub) {
        expect(guess.subcategory).toBe(sample.expectedSub)
      }
    }

    const strictExp = resolveStrictCategory('VA/VR', 'Supermercado', 'expense')
    expect(strictExp).toEqual({ category: 'VA/VR', subcategory: 'Supermercado' })

    const strictInc = resolveStrictCategory('VA/VR', 'Recarga VA/VR', 'income')
    expect(strictInc).toEqual({ category: 'VA/VR', subcategory: 'Recarga VA/VR' })

    const strictAlias = resolveStrictCategory('Vale Alimentação', 'Vale Alimentação (VA)', 'income')
    expect(strictAlias).toEqual({ category: 'VA/VR', subcategory: 'Vale Alimentação (VA)' })
  })
})
