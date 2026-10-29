/**
 * Mock Data realista para o Modo Demo.
 * Dois perfis ligados por cartões compartilhados para testar o espelhamento:
 *  - Usuário A (Meu Perfil)       → demo-kayo
 *  - Usuário B (Perfil Namorada)  → demo-marina
 */
import type { Card, PaymentMethod, RecurringBill, SplitType, Transaction, TransactionStatus, Category, UserProfile } from '@/types'
import { buildSplit, type Party } from './finance'
import { currentMonthKey, dateInMonth, shiftMonth } from './utils'

export const DEMO_USERS: UserProfile[] = [
  {
    uid: 'demo-kayo',
    email: 'kayo@demo.app',
    displayName: 'Kayo Araújo',
    photoURL: null,
    monthlyIncome: 8500,
  },
  {
    uid: 'demo-marina',
    email: 'marina@demo.app',
    displayName: 'Marina Costa',
    photoURL: null,
    monthlyIncome: 6200,
  },
]

const A: Party = { uid: 'demo-kayo', name: 'Kayo' }
const B: Party = { uid: 'demo-marina', name: 'Marina' }

export const DEMO_CARDS: Card[] = [
  {
    id: 'card_nubank',
    ownerUid: A.uid,
    ownerName: 'Kayo',
    cardName: 'Nubank Ultravioleta',
    closingDay: 3,
    dueDay: 10,
    sharedWithUsers: [{ uid: B.uid, email: 'marina@demo.app', name: 'Marina' }],
    sharedWithUids: [B.uid],
    frequentThirdParties: ['Mãe', 'Irmão'],
    color: 'violet',
  },
  {
    id: 'card_inter',
    ownerUid: B.uid,
    ownerName: 'Marina',
    cardName: 'Inter Black',
    closingDay: 25,
    dueDay: 5,
    sharedWithUsers: [{ uid: A.uid, email: 'kayo@demo.app', name: 'Kayo' }],
    sharedWithUids: [A.uid],
    frequentThirdParties: ['Irmã'],
    color: 'orange',
  },
  {
    id: 'card_xp',
    ownerUid: A.uid,
    ownerName: 'Kayo',
    cardName: 'XP Visa Infinite',
    closingDay: 20,
    dueDay: 28,
    sharedWithUsers: [],
    sharedWithUids: [],
    frequentThirdParties: [],
    color: 'slate',
  },
]

export const DEMO_RECURRING: RecurringBill[] = [
  { id: 'rb_aluguel', ownerUid: A.uid, description: 'Aluguel', category: 'Moradia', dueDay: 10, defaultAmount: 2400, estimationRule: 'fixed', active: true, splitType: 'split_50' },
  { id: 'rb_luz', ownerUid: A.uid, description: 'Conta de Luz', category: 'Moradia', dueDay: 22, defaultAmount: 220, estimationRule: 'avg_3_months', active: true, splitType: 'split_50' },
  { id: 'rb_agua', ownerUid: A.uid, description: 'Conta de Água', category: 'Moradia', dueDay: 18, defaultAmount: 95, estimationRule: 'avg_3_months', active: true, splitType: 'split_50' },
  { id: 'rb_internet', ownerUid: A.uid, description: 'Internet Fibra 600MB', category: 'Moradia', dueDay: 15, defaultAmount: 119.9, estimationRule: 'fixed', active: true },
  { id: 'rb_financ', ownerUid: A.uid, description: 'Financiamento do Carro', category: 'Transporte', dueDay: 20, defaultAmount: 1350, estimationRule: 'last_month', active: true },
  { id: 'rb_academia', ownerUid: A.uid, description: 'Academia SmartFit', category: 'Saúde', dueDay: 5, defaultAmount: 129.9, estimationRule: 'fixed', active: true },
  { id: 'rb_spotify', ownerUid: A.uid, description: 'Spotify Família', category: 'Assinaturas', dueDay: 12, defaultAmount: 34.9, estimationRule: 'fixed', active: true, paymentMethod: 'credit_card' },
  { id: 'rb_b_celular', ownerUid: B.uid, description: 'Plano de Celular', category: 'Assinaturas', dueDay: 8, defaultAmount: 59.9, estimationRule: 'fixed', active: true },
  { id: 'rb_b_pos', ownerUid: B.uid, description: 'Pós-graduação', category: 'Outros', dueDay: 10, defaultAmount: 890, estimationRule: 'fixed', active: true },
  { id: 'rb_b_terapia', ownerUid: B.uid, description: 'Terapia', category: 'Saúde', dueDay: 25, defaultAmount: 600, estimationRule: 'last_month', active: true },
]

let seq = 0
interface MkArgs {
  by: Party
  payer?: Party
  desc: string
  cat: Category
  total: number
  month: string
  day: number
  status: TransactionStatus
  split?: SplitType
  partner?: Party
  external?: string
  method?: PaymentMethod
  card?: string
  type?: 'income' | 'expense'
  estimated?: number | null
  actual?: number | null
  rb?: string
  reserveCard?: string
  reimbursed?: boolean
}

function mk(a: MkArgs): Transaction {
  const payer = a.payer ?? a.by
  const split = buildSplit({
    splitType: a.split ?? 'mine',
    total: a.total,
    me: a.by,
    partner: a.partner ?? (a.by.uid === A.uid ? B : A),
    payerUid: payer.uid,
    createdByUid: a.by.uid,
    externalName: a.external,
  })
  if (a.reimbursed) split.allocations = split.allocations.map((x) => ({ ...x, reimbursed: true }))
  const isEstimatedRow = a.estimated !== undefined
  const cardObj = a.card ? DEMO_CARDS.find((c) => c.id === a.card) : undefined
  const visible = a.reserveCard
    ? [a.by.uid]
    : Array.from(
        new Set([
          ...split.visibleToUids,
          ...(cardObj ? [cardObj.ownerUid, ...(cardObj.sharedWithUids || [])] : []),
        ]),
      )
  return {
    id: `tx_demo_${++seq}`,
    createdByUid: a.by.uid,
    paidByUid: payer.uid,
    cardId: a.card ?? null,
    description: a.desc,
    category: a.cat,
    type: a.type ?? 'expense',
    paymentMethod: a.method ?? (a.card ? 'credit_card' : 'pix'),
    status: a.status,
    estimatedAmount: isEstimatedRow ? (a.estimated ?? null) : null,
    actualAmount: a.actual !== undefined ? a.actual : isEstimatedRow ? null : a.total,
    totalAmount: a.total,
    dueDate: dateInMonth(a.month, a.day),
    competenceMonth: a.month,
    visibleToUids: visible,
    userImpact: split.userImpact,
    allocations: split.allocations,
    recurringBillId: a.rb ?? null,
    reserveCardId: a.reserveCard ?? null,
    createdAt: Date.now() - (1000 - seq) * 60000,
  }
}

function history(month: string, luz: number, agua: number, financ: number, extras: Transaction[] = []) {
  return [
    mk({ by: A, desc: 'Salário', cat: 'Receita', type: 'income', total: 8500, month, day: 5, status: 'paid', estimated: 8500, actual: 8500, rb: `salary_${A.uid}` }),
    mk({ by: B, desc: 'Salário', cat: 'Receita', type: 'income', total: 6200, month, day: 5, status: 'paid', estimated: 6200, actual: 6200, rb: `salary_${B.uid}` }),
    mk({ by: A, desc: 'Aluguel', cat: 'Moradia', total: 2400, month, day: 10, status: 'paid', split: 'split_50', method: 'bill', estimated: 2400, actual: 2400, rb: 'rb_aluguel', reimbursed: true }),
    mk({ by: A, desc: 'Conta de Luz', cat: 'Moradia', total: luz, month, day: 22, status: 'paid', split: 'split_50', method: 'bill', estimated: 220, actual: luz, rb: 'rb_luz', reimbursed: true }),
    mk({ by: A, desc: 'Conta de Água', cat: 'Moradia', total: agua, month, day: 18, status: 'paid', split: 'split_50', method: 'bill', estimated: 95, actual: agua, rb: 'rb_agua', reimbursed: true }),
    mk({ by: A, desc: 'Financiamento do Carro', cat: 'Transporte', total: financ, month, day: 20, status: 'paid', method: 'bill', estimated: 1350, actual: financ, rb: 'rb_financ' }),
    mk({ by: A, desc: 'Internet Fibra 600MB', cat: 'Moradia', total: 119.9, month, day: 15, status: 'paid', method: 'bill', estimated: 119.9, actual: 119.9, rb: 'rb_internet' }),
    mk({ by: B, desc: 'Terapia', cat: 'Saúde', total: 600, month, day: 25, status: 'paid', estimated: 600, actual: 600, rb: 'rb_b_terapia' }),
    ...extras,
  ]
}

export function buildDemoTransactions(): Transaction[] {
  seq = 0
  const M = currentMonthKey()
  const M1 = shiftMonth(M, -1)
  const M2 = shiftMonth(M, -2)
  const M3 = shiftMonth(M, -3)

  const past = [
    ...history(M3, 198.4, 92.1, 1340.2, [
      mk({ by: A, desc: 'Supermercado Pão de Açúcar', cat: 'Alimentação', total: 512.4, month: M3, day: 6, status: 'paid', split: 'split_50', card: 'card_nubank', reimbursed: true }),
      mk({ by: A, desc: 'Viagem Paraty - Pousada', cat: 'Lazer', total: 1380, month: M3, day: 19, status: 'paid', split: 'split_50', card: 'card_xp', reimbursed: true }),
    ]),
    ...history(M2, 241.7, 101.35, 1351.8, [
      mk({ by: A, desc: 'Supermercado Extra', cat: 'Alimentação', total: 468.9, month: M2, day: 4, status: 'paid', split: 'split_50', card: 'card_nubank', reimbursed: true }),
      mk({ by: A, desc: 'Revisão do carro', cat: 'Transporte', total: 740, month: M2, day: 14, status: 'paid', card: 'card_xp' }),
    ]),
    ...history(M1, 254.9, 98.7, 1362.4, [
      mk({ by: A, desc: 'Supermercado Pão de Açúcar', cat: 'Alimentação', total: 530.15, month: M1, day: 5, status: 'paid', split: 'split_50', card: 'card_nubank', reimbursed: true }),
      mk({ by: A, desc: 'Show Coldplay (2 ingressos)', cat: 'Lazer', total: 1120, month: M1, day: 21, status: 'paid', split: 'split_50', card: 'card_nubank', reimbursed: true }),
      mk({ by: A, desc: 'iFood', cat: 'Alimentação', total: 286.4, month: M1, day: 28, status: 'paid', card: 'card_xp' }),
    ]),
  ]

  const current: Transaction[] = [
    // ---------- Receitas ----------
    mk({ by: A, desc: 'Salário', cat: 'Receita', type: 'income', total: 8500, month: M, day: 5, status: 'paid', estimated: 8500, actual: 8500, rb: `salary_${A.uid}` }),
    mk({ by: A, desc: 'Freela — Landing page', cat: 'Receita', type: 'income', total: 900, month: M, day: 25, status: 'pending' }),
    mk({ by: B, desc: 'Salário', cat: 'Receita', type: 'income', total: 6200, month: M, day: 5, status: 'paid', estimated: 6200, actual: 6200, rb: `salary_${B.uid}` }),

    // ---------- Contas recorrentes (A) ----------
    mk({ by: A, desc: 'Aluguel', cat: 'Moradia', total: 2400, month: M, day: 10, status: 'paid', split: 'split_50', method: 'bill', estimated: 2400, actual: 2400, rb: 'rb_aluguel', reimbursed: true }),
    mk({ by: A, desc: 'Conta de Luz', cat: 'Moradia', total: 231.67, month: M, day: 22, status: 'estimated', split: 'split_50', method: 'bill', estimated: 231.67, rb: 'rb_luz' }),
    mk({ by: A, desc: 'Conta de Água', cat: 'Moradia', total: 97.38, month: M, day: 18, status: 'estimated', split: 'split_50', method: 'bill', estimated: 97.38, rb: 'rb_agua' }),
    mk({ by: A, desc: 'Internet Fibra 600MB', cat: 'Moradia', total: 119.9, month: M, day: 15, status: 'paid', method: 'bill', estimated: 119.9, actual: 119.9, rb: 'rb_internet' }),
    mk({ by: A, desc: 'Financiamento do Carro', cat: 'Transporte', total: 1371.15, month: M, day: 20, status: 'pending', method: 'bill', estimated: 1362.4, actual: 1371.15, rb: 'rb_financ' }),
    mk({ by: A, desc: 'Academia SmartFit', cat: 'Saúde', total: 119.9, month: M, day: 5, status: 'paid', method: 'debit', estimated: 129.9, actual: 119.9, rb: 'rb_academia' }),

    // ---------- Cartão Nubank (A, compartilhado com B) ----------
    mk({ by: A, desc: 'Supermercado Pão de Açúcar', cat: 'Alimentação', total: 486.3, month: M, day: 3, status: 'pending', split: 'split_50', card: 'card_nubank' }),
    mk({ by: A, desc: 'Uber — aeroporto', cat: 'Transporte', total: 38.5, month: M, day: 7, status: 'paid', card: 'card_nubank' }),
    mk({ by: A, desc: 'Farmácia Drogasil', cat: 'Cartão Terceiros', total: 87.4, month: M, day: 9, status: 'pending', split: 'external', external: 'Mãe', card: 'card_nubank' }),
    mk({ by: A, desc: 'Tênis Nike', cat: 'Cartão Terceiros', total: 299.9, month: M, day: 11, status: 'pending', split: 'external', external: 'Irmão', card: 'card_nubank' }),
    mk({ by: B, payer: A, desc: 'Livraria Cultura', cat: 'Lazer', total: 112, month: M, day: 12, status: 'pending', split: 'mine', card: 'card_nubank' }),
    mk({ by: B, payer: A, desc: 'Cinema Cinépolis', cat: 'Lazer', total: 76, month: M, day: 13, status: 'pending', split: 'split_50', card: 'card_nubank' }),

    // ---------- Cartão Inter (B, compartilhado com A) ----------
    mk({ by: A, desc: 'Previsão Uso Cartão Parceira (Inter)', cat: 'Cartão Terceiros', total: 500, month: M, day: 5, status: 'estimated', estimated: 500, reserveCard: 'card_inter', reimbursed: true }),
    mk({ by: A, payer: B, desc: 'Posto Shell — gasolina', cat: 'Transporte', total: 120, month: M, day: 8, status: 'pending', card: 'card_inter' }),
    mk({ by: B, desc: 'Restaurante Outback', cat: 'Alimentação', total: 216.8, month: M, day: 10, status: 'pending', split: 'split_50', card: 'card_inter' }),
    mk({ by: B, desc: 'iFood', cat: 'Alimentação', total: 64.9, month: M, day: 14, status: 'pending', card: 'card_inter' }),

    // ---------- Cartão XP (A, individual) ----------
    mk({ by: A, desc: 'Fone JBL — Mercado Livre', cat: 'Lazer', total: 189, month: M, day: 6, status: 'pending', card: 'card_xp' }),
    mk({ by: A, desc: 'Parcela Notebook 4/10', cat: 'Outros', total: 420, month: M, day: 2, status: 'pending', card: 'card_xp' }),
    mk({ by: A, desc: 'iFood', cat: 'Alimentação', total: 72.8, month: M, day: 13, status: 'paid', card: 'card_xp' }),

    // ---------- Contas da B ----------
    mk({ by: B, desc: 'Plano de Celular', cat: 'Assinaturas', total: 59.9, month: M, day: 8, status: 'paid', method: 'bill', estimated: 59.9, actual: 59.9, rb: 'rb_b_celular' }),
    mk({ by: B, desc: 'Terapia', cat: 'Saúde', total: 600, month: M, day: 25, status: 'estimated', estimated: 600, rb: 'rb_b_terapia' }),
  ]

  return [...past, ...current]
}
