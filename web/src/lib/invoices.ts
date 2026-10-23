/**
 * Motor de faturas de cartão (puro, sem dependências de UI/Firebase).
 *
 * Uma "fatura" é derivada: todas as transações de um cartão (`cardId`)
 * com o mesmo mês de competência (`competenceMonth` = mês de vencimento).
 * Não há coleção nova no Firestore — o acompanhamento é 100% calculado.
 */
import type { Card, Transaction } from '@/types'
import { effectiveAmount, impactFor } from './finance'
import { daysInMonth, round2, shiftMonth, todayKey } from './utils'

export type InvoiceStatus = 'empty' | 'open' | 'closed' | 'overdue' | 'paid'

export interface InvoiceCategorySlice {
  category: string
  amount: number
  pct: number
}

export interface CardInvoice {
  card: Card
  monthKey: string
  closingDate: string // YYYY-MM-DD
  dueDate: string // YYYY-MM-DD
  status: InvoiceStatus
  /** Valor bruto da fatura (compras - créditos/estornos) */
  total: number
  /** Soma dos lançamentos de despesa */
  grossExpenses: number
  /** Créditos/estornos lançados no cartão */
  credits: number
  /** Já quitado (itens com status `paid`) */
  paidAmount: number
  /** Ainda em aberto */
  openAmount: number
  /** Parte que impacta o meu Saldo Livre */
  myShare: number
  /** Parte de terceiros/amigos/parceiro(a) */
  othersShare: number
  /** Limite configurado (quando existir) */
  limit: number | null
  limitUsagePct: number | null
  daysToDue: number
  daysToClose: number
  itemsCount: number
  installmentsCount: number
  items: Transaction[]
  byCategory: InvoiceCategorySlice[]
}

export const INVOICE_STATUS_META: Record<InvoiceStatus, { label: string; tone: string; dot: string }> = {
  empty: { label: 'Sem lançamentos', tone: 'text-slate-400 bg-slate-500/10 border-slate-500/30', dot: 'bg-slate-400' },
  open: { label: 'Aberta', tone: 'text-sky-300 bg-sky-500/10 border-sky-500/30', dot: 'bg-sky-400' },
  closed: { label: 'Fechada', tone: 'text-amber-300 bg-amber-500/10 border-amber-500/30', dot: 'bg-amber-400' },
  overdue: { label: 'Vencida', tone: 'text-rose-300 bg-rose-500/10 border-rose-500/30', dot: 'bg-rose-400' },
  paid: { label: 'Paga', tone: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/30', dot: 'bg-emerald-400' },
}

function clampDay(monthKey: string, day: number) {
  const d = Math.min(Math.max(1, day || 1), daysInMonth(monthKey))
  return `${monthKey}-${String(d).padStart(2, '0')}`
}

function diffDays(fromKey: string, toKey: string) {
  const a = new Date(`${fromKey}T00:00:00`)
  const b = new Date(`${toKey}T00:00:00`)
  return Math.round((b.getTime() - a.getTime()) / 86_400_000)
}

/** Datas de fechamento e vencimento da fatura cujo vencimento cai em `monthKey`. */
export function invoiceDates(card: Card, monthKey: string) {
  const dueDate = clampDay(monthKey, card.dueDay)
  // Mesmo critério de `calculateCardInvoiceDates`: se vence antes do dia de
  // fechamento, o fechamento ocorreu no mês anterior ao vencimento.
  const closingMonth = card.dueDay < card.closingDay ? shiftMonth(monthKey, -1) : monthKey
  const closingDate = clampDay(closingMonth, card.closingDay)
  return { closingDate, dueDate }
}

export function buildCardInvoice(
  card: Card,
  monthKey: string,
  all: Transaction[],
  uid: string,
  today: string = todayKey(),
): CardInvoice {
  const { closingDate, dueDate } = invoiceDates(card, monthKey)

  const items = (all || [])
    .filter(
      (t) =>
        t.cardId === card.id &&
        t.competenceMonth === monthKey &&
        !t.reserveCardId &&
        (t.visibleToUids || []).includes(uid),
    )
    .sort((a, b) => (b.dueDate || '').localeCompare(a.dueDate || '') || (b.createdAt ?? 0) - (a.createdAt ?? 0))

  const expenses = items.filter((t) => t.type === 'expense')
  const creditsItems = items.filter((t) => t.type === 'income')

  const grossExpenses = round2(expenses.reduce((s, t) => s + effectiveAmount(t), 0))
  const credits = round2(creditsItems.reduce((s, t) => s + effectiveAmount(t), 0))
  const total = round2(Math.max(0, grossExpenses - credits))
  const paidAmount = round2(
    Math.min(total, expenses.filter((t) => t.status === 'paid').reduce((s, t) => s + effectiveAmount(t), 0)),
  )
  const openAmount = round2(Math.max(0, total - paidAmount))
  const myShare = round2(Math.max(0, expenses.reduce((s, t) => s + impactFor(t, uid, all), 0) - credits))
  const othersShare = round2(Math.max(0, total - myShare))

  const cat = new Map<string, number>()
  for (const t of expenses) cat.set(t.category, (cat.get(t.category) ?? 0) + effectiveAmount(t))
  const byCategory = [...cat.entries()]
    .map(([category, amount]) => ({
      category,
      amount: round2(amount),
      pct: grossExpenses > 0 ? Math.round((amount / grossExpenses) * 100) : 0,
    }))
    .sort((a, b) => b.amount - a.amount)

  let status: InvoiceStatus
  if (expenses.length === 0) status = 'empty'
  else if (expenses.every((t) => t.status === 'paid')) status = 'paid'
  else if (today < closingDate) status = 'open'
  else if (today <= dueDate) status = 'closed'
  else status = 'overdue'

  const limit = card.limit && card.limit > 0 ? card.limit : null

  return {
    card,
    monthKey,
    closingDate,
    dueDate,
    status,
    total,
    grossExpenses,
    credits,
    paidAmount,
    openAmount,
    myShare,
    othersShare,
    limit,
    limitUsagePct: limit ? Math.min(100, Math.round((total / limit) * 100)) : null,
    daysToDue: diffDays(today, dueDate),
    daysToClose: diffDays(today, closingDate),
    itemsCount: items.length,
    installmentsCount: expenses.filter((t) => t.installmentsTotal && t.installmentsTotal > 1).length,
    items,
    byCategory,
  }
}

/** Histórico das últimas `months` faturas (inclusive `monthKey`), da mais antiga para a mais recente. */
export function buildInvoiceHistory(card: Card, monthKey: string, all: Transaction[], uid: string, months = 6, forward = 0) {
  const out: CardInvoice[] = []
  for (let i = months - 1; i >= -forward; i--) {
    out.push(buildCardInvoice(card, shiftMonth(monthKey, -i), all, uid))
  }
  return out
}

/** Texto amigável de prazo: "vence em 3 dias", "venceu há 2 dias"... */
export function dueCountdownLabel(inv: CardInvoice) {
  if (inv.status === 'paid') return 'Fatura quitada'
  if (inv.status === 'empty') return 'Nenhuma compra neste ciclo'
  if (inv.status === 'open') {
    if (inv.daysToClose === 0) return 'Fecha hoje'
    return `Fecha em ${inv.daysToClose} dia${inv.daysToClose === 1 ? '' : 's'}`
  }
  if (inv.daysToDue === 0) return 'Vence hoje'
  if (inv.daysToDue > 0) return `Vence em ${inv.daysToDue} dia${inv.daysToDue === 1 ? '' : 's'}`
  const late = Math.abs(inv.daysToDue)
  return `Venceu há ${late} dia${late === 1 ? '' : 's'}`
}
