import { describe, it, expect } from 'vitest'
import {
  ACTIVE_CATEGORIES,
  EXPENSE_CATEGORIES,
  INCOME_CATEGORIES,
  LEGACY_CATEGORIES,
  getBudgetNature,
  isVaVrCard,
  isVaVrCategory,
  isVaVrRecurringBill,
  isVaVrTransaction,
  resolveStrictCategory,
  type Card,
  type Category,
  type CategoryBudget,
  type MonthClosure,
  type RecurringBill,
  type Transaction,
  type UserProfile,
} from '@/types'
import {
  NON_ENVELOPE_CATEGORIES,
  calculateFinancialHealthScore,
  computeCategoryBudgets,
  computeSummary,
  guessCategoryAndSubcategory,
  projectFutureMonths,
  suggestCategoryBudgets,
  suggestPaymentMethod,
} from '../finance'
import { buildCardInvoice, consolidateMonthTransactions } from '../invoices'

const MY_UID = 'user_me'
const PARTNER_UID = 'user_partner'

function makeTx(
  overrides: Partial<Transaction> & { amount?: number },
): Transaction {
  const { amount = 100, ...rest } = overrides
  const paidBy = rest.paidByUid ?? MY_UID
  return {
    id: `tx_${Math.random().toString(36).slice(2, 9)}`,
    createdByUid: paidBy,
    paidByUid: paidBy,
    cardId: null,
    description: 'Lançamento Teste',
    category: 'Outros',
    subcategory: null,
    type: 'expense',
    paymentMethod: 'pix',
    status: 'paid',
    estimatedAmount: amount,
    actualAmount: amount,
    totalAmount: amount,
    dueDate: '2099-01-15',
    competenceMonth: '2099-01',
    visibleToUids: [MY_UID],
    userImpact: { [MY_UID]: amount },
    allocations: [],
    createdAt: Date.now(),
    ...rest,
  }
}

function makeCard(overrides: Partial<Card> = {}): Card {
  return {
    id: `card_${Math.random().toString(36).slice(2, 8)}`,
    ownerUid: MY_UID,
    ownerName: 'Titular',
    cardName: 'Cartão Padrão',
    closingDay: 1,
    dueDay: 10,
    sharedWithUsers: [],
    sharedWithUids: [],
    frequentThirdParties: [],
    cardType: 'credit',
    ...overrides,
  }
}

const VALE_CARD: Card = makeCard({
  id: 'card_vale_1',
  cardName: 'Caju Multi',
  cardType: 'va_vr',
  color: 'teal',
})

const CREDIT_CARD: Card = makeCard({
  id: 'card_cred_1',
  cardName: 'Nubank Violet',
  cardType: 'credit',
  color: 'violet',
})

describe('Módulo Cartão Pré-Pago Vale (VA/VR) via Método/Cartão', () => {
  it('1. Isola 100% entradas, saídas com categorias reais e recorrentes de Vale do Saldo Livre bancário, fluxo de caixa e projeções', () => {
    const txs: Transaction[] = [
      // Conta corrente principal: +5.000 salário, -1.500 aluguel -> Saldo Livre = +3.500
      makeTx({
        id: 'sal',
        description: 'Salário Empresa',
        category: 'Salário & Remuneração',
        type: 'income',
        paymentMethod: 'transfer',
        amount: 5000,
      }),
      makeTx({
        id: 'aluguel',
        description: 'Aluguel',
        category: 'Moradia',
        type: 'expense',
        paymentMethod: 'pix',
        amount: 1500,
      }),
      // Recarga no Vale via paymentMethod 'va_vr' usando categoria real 'Benefícios'
      makeTx({
        id: 'va_in',
        description: 'Recarga Caju Benefícios',
        category: 'Benefícios',
        subcategory: 'Vale Alimentação (VA)',
        type: 'income',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        amount: 1200,
      }),
      // Gasto no Vale com categoria real 'Alimentação'
      makeTx({
        id: 'va_out_food',
        description: 'Supermercado Extra',
        category: 'Alimentação',
        subcategory: 'Supermercado',
        type: 'expense',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        amount: 350,
      }),
      // Gasto no Vale em categoria diferente ('Transporte' — multi-benefícios via cartão pré-pago)
      makeTx({
        id: 'va_out_transp',
        description: 'Uber Mobilidade Flash',
        category: 'Transporte',
        subcategory: 'Apps (Uber / 99)',
        type: 'expense',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        amount: 50,
      }),
      // Transação legada sem paymentMethod 'va_vr' mas sob categoria legada 'VA/VR' continua compatível
      makeTx({
        id: 'va_legacy',
        description: 'Padaria Central Legado',
        category: 'VA/VR',
        subcategory: 'Padaria & Café',
        type: 'expense',
        paymentMethod: 'debit_card',
        amount: 40,
      }),
    ]

    const vaVrRecurringBill: RecurringBill = {
      id: 'bill_vavr',
      ownerUid: MY_UID,
      description: 'Recarga Mensal Flash VA/VR',
      category: 'Benefícios',
      subcategory: 'Vale Alimentação (VA)',
      dueDay: 1,
      defaultAmount: 1100,
      estimationRule: 'fixed',
      active: true,
      type: 'income',
      paymentMethod: 'va_vr',
      cardId: VALE_CARD.id,
      startMonth: '2099-02',
    }

    const cards = [VALE_CARD, CREDIT_CARD]
    const summary = computeSummary(txs, MY_UID, '2099-01', cards, [], [vaVrRecurringBill])

    // Conta corrente bancária intacta
    expect(summary.income).toBe(5000)
    expect(summary.paid).toBe(1500)
    expect(summary.committed).toBe(0)
    expect(summary.freeBalance).toBe(3500)

    // Módulo VA/VR isolado (1200 - 350 - 50 - 40 = 760)
    expect(summary.vaVrIncome).toBe(1200)
    expect(summary.vaVrSpent).toBe(440)
    expect(summary.vaVrAvailableBalance).toBe(760)
    expect(summary.vaVrProjectedBalance).toBe(760)

    // Projeções futuras da conta corrente ignoram o molde recorrente VA/VR
    const projections = projectFutureMonths({
      all: txs,
      bills: [vaVrRecurringBill],
      profile: { monthlyIncome: 5000 } as UserProfile,
      uid: MY_UID,
      startMonthKey: '2099-02',
      count: 2,
      cards,
    })

    const feb = projections.find((p) => p.monthKey === '2099-02')
    expect(feb).toBeDefined()
    expect(feb!.projectedIncome).toBe(5000)
    expect(feb!.expenseCount).toBe(0)
  })

  it('2 & 3. Separa Disponível Agora (paid) de Projetado no Mês (pending/estimated + recorrentes Vale) e calcula Limite Diário VA/VR', () => {
    const cards = [VALE_CARD]
    const txs: Transaction[] = [
      makeTx({
        id: 'va_in_paid',
        description: 'Recarga VA Confirmada',
        category: 'Benefícios',
        subcategory: 'Vale Alimentação (VA)',
        type: 'income',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        status: 'paid',
        amount: 1000,
      }),
      makeTx({
        id: 'va_in_pending',
        description: 'Recarga VR Programada',
        category: 'Benefícios',
        subcategory: 'Vale Refeição (VR)',
        type: 'income',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        status: 'pending',
        amount: 200,
      }),
      makeTx({
        id: 'va_out_paid',
        description: 'Mercado Pão de Açúcar',
        category: 'Alimentação',
        subcategory: 'Supermercado',
        type: 'expense',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        status: 'paid',
        amount: 380,
      }),
      makeTx({
        id: 'va_out_pending',
        description: 'Almoço Agendado',
        category: 'Alimentação',
        subcategory: 'Restaurante',
        type: 'expense',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        status: 'pending',
        amount: 120,
      }),
    ]

    const pendingBill: RecurringBill = {
      id: 'bill_va_extra',
      ownerUid: MY_UID,
      description: 'Complemento VR Mensal',
      category: 'Benefícios',
      subcategory: 'Vale Refeição (VR)',
      dueDay: 10,
      defaultAmount: 100,
      estimationRule: 'fixed',
      active: true,
      type: 'income',
      paymentMethod: 'va_vr',
      cardId: VALE_CARD.id,
    }

    const summary = computeSummary(txs, MY_UID, '2099-01', cards, [], [pendingBill])

    expect(summary.vaVrCarryover).toBe(0)
    expect(summary.vaVrIncomePaid).toBe(1000)
    // Pending real (200) + recorrente Vale não lançada (100)
    expect(summary.vaVrIncomePending).toBe(300)
    expect(summary.vaVrIncome).toBe(1300)
    expect(summary.vaVrSpentPaid).toBe(380)
    expect(summary.vaVrSpentPending).toBe(120)
    expect(summary.vaVrSpent).toBe(500)
    // Disponível Agora = 0 + 1000 - 380 = 620
    expect(summary.vaVrAvailableBalance).toBe(620)
    // Projetado no Mês = 0 + 1300 - 500 = 800
    expect(summary.vaVrProjectedBalance).toBe(800)
    // Janeiro tem 31 dias no futuro (2099-01): 620 / 31 = 20
    expect(summary.vaVrDailyLimit).toBe(20)
    expect(summary.vaVrBySubcategory?.reduce((acc, s) => acc + s.count, 0)).toBe(2)
  })

  it('4. Faz rollover cronológico automático (independente de MonthClosure) e permite Saldo Vale negativo sem tocar na conta corrente', () => {
    const cards = [VALE_CARD]
    const closure: MonthClosure = {
      monthKey: '2098-12',
      ownerUid: MY_UID,
      carryoverBalance: 900,
      closedAt: Date.now(),
    }

    const txs: Transaction[] = [
      // Dezembro 2098: +800 no Vale, -350 gasto -> sobra 450 para Jan/2099
      makeTx({
        id: 'dec_in',
        category: 'Benefícios',
        subcategory: 'Vale Alimentação (VA)',
        type: 'income',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        status: 'paid',
        competenceMonth: '2098-12',
        dueDate: '2098-12-02',
        amount: 800,
      }),
      makeTx({
        id: 'dec_out',
        category: 'Alimentação',
        subcategory: 'Supermercado',
        type: 'expense',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        status: 'paid',
        competenceMonth: '2098-12',
        dueDate: '2098-12-10',
        amount: 350,
      }),
      // Mês atual (2099-01): conta bancária tem +4000 intactos, Vale estoura (450 carryover - 600 = -150)
      makeTx({
        id: 'jan_sal',
        category: 'Salário & Remuneração',
        type: 'income',
        paymentMethod: 'transfer',
        status: 'paid',
        competenceMonth: '2099-01',
        dueDate: '2099-01-05',
        amount: 4000,
      }),
      makeTx({
        id: 'jan_va_out',
        category: 'Alimentação',
        subcategory: 'Supermercado',
        type: 'expense',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        status: 'paid',
        competenceMonth: '2099-01',
        dueDate: '2099-01-12',
        amount: 600,
      }),
    ]

    const janSummary = computeSummary(txs, MY_UID, '2099-01', cards, [closure])
    expect(janSummary.vaVrCarryover).toBe(450)
    expect(janSummary.vaVrAvailableBalance).toBe(-150)
    expect(janSummary.vaVrDailyLimit).toBe(0)
    // Conta corrente blindada do estouro negativo do cartão pré-pago
    expect(janSummary.freeBalance).toBe(4000)

    // Rollover negativo nunca zera: ou melhor, saldo disponível positivo transita e negativo é zerado ao virar competência
    const febSummary = computeSummary(txs, MY_UID, '2099-02', cards, [closure])
    expect(febSummary.vaVrCarryover).toBe(0)
  })

  it('5. Rateio no Vale desconta 100% do saldo pré-pago do pagador e gera receivable na conta principal pela cota do parceiro', () => {
    const cards = [VALE_CARD]
    const sharedTx = makeTx({
      id: 'va_split',
      description: 'Compra Jantar 50/50 no Vale',
      category: 'Alimentação',
      subcategory: 'Restaurante',
      type: 'expense',
      paymentMethod: 'va_vr',
      cardId: VALE_CARD.id,
      status: 'paid',
      paidByUid: MY_UID,
      visibleToUids: [MY_UID, PARTNER_UID],
      splitType: 'split_50',
      amount: 200,
      userImpact: { [MY_UID]: 100, [PARTNER_UID]: 100 },
      allocations: [
        { uid: MY_UID, type: 'PLATFORM_USER', name: 'Eu', amount: 100, reimbursed: false },
        { uid: PARTNER_UID, type: 'PLATFORM_USER', name: 'Parceiro', amount: 100, reimbursed: false },
      ],
    })

    const payerSummary = computeSummary([sharedTx], MY_UID, '2099-01', cards)
    // Pagador desembolsou R$ 200 integrais no cartão pré-pago Vale
    expect(payerSummary.vaVrSpent).toBe(200)
    expect(payerSummary.vaVrAvailableBalance).toBe(-200)
    // Mas gera R$ 100 a receber do parceiro normalmente na conta corrente
    expect(payerSummary.receivableFromPartner).toBe(100)
    expect(payerSummary.paid).toBe(0)

    const partnerSummary = computeSummary([sharedTx], PARTNER_UID, '2099-01', cards)
    // Parceiro não passou o próprio cartão Vale
    expect(partnerSummary.vaVrSpent).toBe(0)
    expect(partnerSummary.payableToOthers).toBe(100)
  })

  it('6. Remove VA/VR de categorias ativas, isola gastos pagos via Vale dos envelopes tradicionais (mesmo em Alimentação), score e faturas', () => {
    expect((EXPENSE_CATEGORIES as readonly string[]).includes('VA/VR')).toBe(false)
    expect((INCOME_CATEGORIES as readonly string[]).includes('VA/VR')).toBe(false)
    expect((ACTIVE_CATEGORIES as readonly string[]).includes('VA/VR')).toBe(false)
    expect(LEGACY_CATEGORIES).toContain('VA/VR')
    expect(NON_ENVELOPE_CATEGORIES).toContain('VA/VR')
    expect(getBudgetNature('VA/VR')).toBe('need')
    expect(getBudgetNature('VA/VR', 'Recarga VA/VR')).toBe('income')
    expect(isVaVrCard(VALE_CARD)).toBe(true)
    expect(isVaVrCard(CREDIT_CARD)).toBe(false)

    const cards = [VALE_CARD, CREDIT_CARD]
    const budgetTxList: Transaction[] = [
      makeTx({
        id: 's_sal',
        category: 'Salário & Remuneração',
        type: 'income',
        paymentMethod: 'transfer',
        amount: 6000,
      }),
      // Compra de R$ 2.000 em Alimentação paga no cartão Vale NÃO deve estourar o envelope tradicional de Alimentação!
      makeTx({
        id: 's_va_food',
        description: 'Supermercado no Vale',
        category: 'Alimentação',
        subcategory: 'Supermercado',
        type: 'expense',
        paymentMethod: 'va_vr',
        cardId: VALE_CARD.id,
        amount: 2000,
      }),
      // Compra de R$ 500 em Alimentação paga no PIX conta no envelope tradicional
      makeTx({
        id: 's_pix_food',
        description: 'Jantar no PIX',
        category: 'Alimentação',
        subcategory: 'Restaurante',
        type: 'expense',
        paymentMethod: 'pix',
        amount: 500,
      }),
    ]

    const summary = computeSummary(budgetTxList, MY_UID, '2099-01', cards)
    const budgets: Record<Category, number> | Record<string, number> = {
      Alimentação: 1000,
    } as Record<CategoryBudget['category'] & string, number>

    const report = computeCategoryBudgets(
      budgetTxList,
      budgets,
      '2099-01',
      '2099-01-15',
      MY_UID,
      budgetTxList,
      6000,
      0,
      0,
      [],
      cards,
    )

    const foodEnvelope = report.items.find((i) => i.category === 'Alimentação')
    expect(foodEnvelope).toBeDefined()
    expect(foodEnvelope!.spent).toBe(500)
    expect(report.items.some((i) => i.category === 'VA/VR')).toBe(false)

    // HealthScore considera apenas os R$ 500 de Alimentação na conta corrente (não os R$ 2.000 do Vale)
    const health = calculateFinancialHealthScore(summary, 0, report)
    expect(health.monthlyLivingCost).toBe(500)
    expect(health.spendingLimitScore).toBe(30)
    expect(health.envelopeScore).toBe(15)
    expect(health.score).toBe(45)

    // Sugestão de envelopes ignora transações pagas via Vale
    const pastTxs = budgetTxList.map((t) => ({
      ...t,
      competenceMonth: '2098-12',
      dueDate: '2098-12-10',
    }))
    const suggested = suggestCategoryBudgets(pastTxs, MY_UID, '2099-01', 6000, 0, 0, cards)
    expect(suggested.suggestedBudgets['VA/VR']).toBeUndefined()
    expect(suggested.categoryAverages['Alimentação']).toBe(500)
    expect(suggested.suggestedBudgets['Alimentação']).toBe(600)

    // Cartão tipo 'va_vr' NUNCA gera fatura pós-paga (buildCardInvoice retorna vazio & consolidateMonthTransactions preserva linhas fora de faturas)
    const valeInvoice = buildCardInvoice(VALE_CARD, '2099-01', budgetTxList, MY_UID, '2099-01-15')
    expect(valeInvoice.items).toHaveLength(0)
    expect(valeInvoice.total).toBe(0)
    expect(valeInvoice.grossExpenses).toBe(0)

    const consolidated = consolidateMonthTransactions(budgetTxList, budgetTxList, cards, MY_UID, '2099-01', true)
    expect(consolidated.some((t) => t.id === 's_va_food')).toBe(true)
    expect(consolidated.some((t) => t.cardId === VALE_CARD.id && t.id.startsWith('__invoice_'))).toBe(false)
  })

  it('7. Classificadores sugerem método de pagamento va_vr, mantêm categoria real (Alimentação, Transporte, Benefícios) e migram VA/VR legado', () => {
    expect(isVaVrCategory('VA/VR')).toBe(true)
    expect(isVaVrCategory('Benefícios', 'Vale Alimentação (VA)')).toBe(true)
    expect(isVaVrTransaction(makeTx({ paymentMethod: 'va_vr', category: 'Saúde' }), [VALE_CARD])).toBe(true)
    expect(isVaVrTransaction(makeTx({ paymentMethod: 'credit_card', cardId: VALE_CARD.id, category: 'Transporte' }), [VALE_CARD])).toBe(true)
    expect(
      isVaVrRecurringBill(
        {
          id: 'b_vavr',
          ownerUid: MY_UID,
          description: 'Vale',
          category: 'Benefícios',
          dueDay: 1,
          defaultAmount: 500,
          estimationRule: 'fixed',
          active: true,
          paymentMethod: 'va_vr',
        },
        [VALE_CARD],
      ),
    ).toBe(true)

    // Detecção de pagamento Vale vs não-benefícios
    expect(suggestPaymentMethod('Recarga Pluxee VA')).toBe('va_vr')
    expect(suggestPaymentMethod('Uber com VR Flash')).toBe('va_vr')
    expect(suggestPaymentMethod('Supermercado iFood Benefícios')).toBe('va_vr')
    expect(suggestPaymentMethod('Ticket show rock')).toBeNull()
    expect(suggestPaymentMethod('Flash drive USB 64gb')).toBeNull()

    // Classificação inteligente retorna categoria real (não 'VA/VR')
    const recarga = guessCategoryAndSubcategory('Recarga Benefícios Pluxee mensal', undefined, 'income')
    expect(recarga.category).toBe('Benefícios')
    expect(recarga.subcategory).toBe('Vale Alimentação (VA)')

    const supermercado = guessCategoryAndSubcategory('Supermercado iFood Benefícios', undefined, 'expense')
    expect(supermercado.category).toBe('Alimentação')
    expect(supermercado.subcategory).toBe('Supermercado')

    const uberFlash = guessCategoryAndSubcategory('Uber com VR Flash', undefined, 'expense')
    expect(uberFlash.category).toBe('Transporte')

    const almocoAlelo = guessCategoryAndSubcategory('Almoço Alelo Restaurante', undefined, 'expense')
    expect(almocoAlelo.category).toBe('Alimentação')
    expect(almocoAlelo.subcategory).toBe('Restaurante')

    // resolveStrictCategory migra categoria legada 'VA/VR' para categorias ativas válidas
    const strictExp = resolveStrictCategory('VA/VR', 'Supermercado', 'expense')
    expect(strictExp.category).toBe('Alimentação')
    expect(strictExp.subcategory).toBe('Supermercado')

    const strictInc = resolveStrictCategory('VA/VR', 'Recarga VA/VR', 'income')
    expect(strictInc.category).toBe('Benefícios')
    expect(strictInc.subcategory).toBe('Vale Alimentação (VA)')
  })
})
