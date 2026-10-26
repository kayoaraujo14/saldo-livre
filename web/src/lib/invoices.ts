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

export interface InvoiceParticipantShare {
  name: string
  amount: number
  pct: number
  isMe: boolean
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
  /** Divisão por participante da fatura */
  splitBreakdown: InvoiceParticipantShare[]
  /** Maior gasto individual desta fatura */
  topExpense: Transaction | null
  /** Comprometimento futuro deste cartão (soma de parcelas em meses futuros) */
  futureInstallmentsTotal: number
  futureInstallmentsCount: number
  availableLimit?: number | null
  totalCommitted?: number
  /** Melhor dia para compras neste cartão */
  bestPurchaseDay: number
  bestPurchaseDate: string
  isBestPurchaseTime: boolean
  bestPurchaseHint: string
  /** Progresso temporal do ciclo da fatura (0% a 100%) */
  cycleProgressPct: number
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

  // Rateio detalhado por participante baseado nas cotas reais de cada lançamento
  const participantMap = new Map<string, { name: string; amount: number; isMe: boolean }>()

  for (const t of expenses) {
    const shares = getTransactionParticipantShares(t, uid, all)
    for (const [key, amt] of shares.entries()) {
      if (Math.abs(amt) <= 0.0001) continue
      const isMe = key === '__me__'
      const name = isMe ? 'Você (Minha parte)' : key
      const existing = participantMap.get(name) ?? { name, amount: 0, isMe }
      existing.amount = round2(existing.amount + amt)
      participantMap.set(name, existing)
    }
  }

  // Se houver créditos e o titular tiver participação, abate proporcionalmente do titular
  if (credits > 0 && participantMap.has('Você (Minha parte)')) {
    const cur = participantMap.get('Você (Minha parte)')!
    cur.amount = round2(Math.max(0, cur.amount - credits))
  }

  const splitBreakdown: InvoiceParticipantShare[] = [...participantMap.values()]
    .filter((p) => p.amount > 0.001)
    .map((p) => ({
      ...p,
      pct: total > 0 ? Math.round((p.amount / total) * 100) : 0,
    }))
    .sort((a, b) => (b.isMe ? 1 : 0) - (a.isMe ? 1 : 0) || b.amount - a.amount)

  // Maior gasto da fatura
  const topExpense =
    expenses.length > 0 ? [...expenses].sort((a, b) => effectiveAmount(b) - effectiveAmount(a))[0] : null

  // Comprometimento futuro em parcelas deste cartão
  const futureItems = (all || []).filter(
    (t) =>
      t.cardId === card.id &&
      t.competenceMonth > monthKey &&
      !t.reserveCardId &&
      (t.visibleToUids || []).includes(uid),
  )
  const futureExpenses = futureItems.filter((t) => t.type === 'expense' && t.status !== 'paid')
  const futureCredits = futureItems.filter((t) => t.type === 'income').reduce((s, t) => s + effectiveAmount(t), 0)
  const futureInstallmentsTotal = round2(Math.max(0, futureExpenses.reduce((s, t) => s + effectiveAmount(t), 0) - futureCredits))
  const futureInstallmentsCount = futureExpenses.length

  // Melhor dia de compra
  const bestPurchaseDay = card.closingDay
  const bestPurchaseDate = closingDate
  const isBestPurchaseTime = today >= closingDate && diffDays(closingDate, today) <= 5
  const bestPurchaseHint =
    today < closingDate
      ? `A partir do dia ${bestPurchaseDay}, compras caem só na próxima fatura (até 40 dias para pagar).`
      : `Melhor época de compra ativa! Suas compras agora já caem na fatura do próximo ciclo.`

  // Progresso do ciclo de fatura
  const closingMonth = card.dueDay < card.closingDay ? shiftMonth(monthKey, -1) : monthKey
  const prevClosingMonth = shiftMonth(closingMonth, -1)
  const prevClosingDate = clampDay(prevClosingMonth, card.closingDay)
  const cycleTotalDays = Math.max(1, diffDays(prevClosingDate, closingDate))
  const cyclePassedDays = Math.max(0, diffDays(prevClosingDate, today))
  const cycleProgressPct = Math.min(100, Math.max(0, Math.round((cyclePassedDays / cycleTotalDays) * 100)))

  const currentCommitted = status === 'paid' ? 0 : openAmount
  const totalCommitted = round2(currentCommitted + futureInstallmentsTotal)
  const availableLimit = limit != null ? Math.max(0, round2(limit - totalCommitted)) : null

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
    limitUsagePct: limit ? Math.min(100, Math.round((totalCommitted / limit) * 100)) : null,
    availableLimit,
    totalCommitted,
    daysToDue: diffDays(today, dueDate),
    daysToClose: diffDays(today, closingDate),
    itemsCount: items.length,
    installmentsCount: expenses.filter((t) => t.installmentsTotal && t.installmentsTotal > 1).length,
    items,
    byCategory,
    splitBreakdown,
    topExpense,
    futureInstallmentsTotal,
    futureInstallmentsCount,
    bestPurchaseDay,
    bestPurchaseDate,
    isBestPurchaseTime,
    bestPurchaseHint,
    cycleProgressPct,
  }
}

/** Histórico das últimas `months` faturas (inclusive `monthKey`), da mais antiga para a mais recente. */
export function buildInvoiceHistory(
  card: Card,
  monthKey: string,
  all: Transaction[],
  uid: string,
  months = 6,
  forward = 0,
) {
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
    if (inv.daysToClose < 0) return 'Fechando ciclo'
    return `Fecha em ${inv.daysToClose} dia${inv.daysToClose === 1 ? '' : 's'}`
  }
  if (inv.daysToDue === 0) return 'Vence hoje'
  if (inv.daysToDue > 0) return `Vence em ${inv.daysToDue} dia${inv.daysToDue === 1 ? '' : 's'}`
  const late = Math.abs(inv.daysToDue)
  return `Venceu há ${late} dia${late === 1 ? '' : 's'}`
}

/**
 * Retorna o mapa de participantes e seus respectivos valores rateados para uma transação de despesa.
 * O titular/usuário atual é indexado pela chave especial "__me__".
 * Outros participantes são indexados por seus nomes.
 * A soma de todos os valores no mapa é GARANTIDA de ser igual a round2(effectiveAmount(tx)).
 */
export function getTransactionParticipantShares(
  tx: Transaction,
  uid: string,
  _all: Transaction[] = []
): Map<string, number> {
  if (tx.type !== 'expense') return new Map()
  const total = round2(effectiveAmount(tx))
  if (Math.abs(total) <= 0.0001) return new Map()

  const shares = new Map<string, number>()

  // 1. Alocações explícitas
  const validAllocs = (tx.allocations || []).filter((a) => Math.abs(a.amount || 0) > 0.0001)
  const othersAllocs = validAllocs.filter((a) => {
    const isMyUid = Boolean(a.uid && a.uid === uid)
    const norm = (a.name || '').trim().toLowerCase()
    const isMyName = (!a.uid || a.uid === uid) && (
      norm === 'você' || norm === 'voce' || norm === 'meu' || norm === 'minha parte' || norm === 'você (minha parte)' || norm === 'voce (minha parte)'
    )
    return !isMyUid && !isMyName
  })

  if (othersAllocs.length > 0) {
    let sumOthers = 0
    for (const a of othersAllocs) {
      const name = a.name?.trim() || 'Outro'
      const amt = round2(a.amount || 0)
      shares.set(name, round2((shares.get(name) ?? 0) + amt))
      sumOthers += amt
    }
    const myAllocs = validAllocs.filter((a) => !othersAllocs.includes(a))
    const myAllocSum = myAllocs.reduce((s, a) => s + (a.amount || 0), 0)
    const myAmount = Math.abs(myAllocSum) > 0.0001 ? round2(total - sumOthers) : round2(Math.max(0, total - sumOthers))
    if (Math.abs(myAmount) > 0.0001) {
      shares.set('__me__', myAmount)
    }
  } else if (tx.splitType === 'split_50') {
    // 2. Divisão 50/50 implícita
    const half = round2(total / 2)
    const otherHalf = round2(total - half)
    shares.set('__me__', half)
    const partnerName = tx.externalName?.trim() || 'Parceiro(a)'
    shares.set(partnerName, otherHalf)
  } else if (tx.splitType === 'partner') {
    // 3. 100% Parceiro(a)
    const partnerName = tx.externalName?.trim() || 'Parceiro(a)'
    shares.set(partnerName, total)
  } else if (Boolean(tx.assignedFriendName || tx.assignedFriendUid || tx.splitType === 'friend')) {
    // 4. Amigo atribuído
    const friendName = tx.assignedFriendName?.trim() || 'Amigo'
    let friendAmount = 0
    if (tx.friendSharePct && tx.friendSharePct > 0) {
      friendAmount = round2((total * tx.friendSharePct) / 100)
    } else if (tx.userImpact && uid in tx.userImpact && (tx.userImpact[uid] ?? 0) >= 0 && (tx.userImpact[uid] ?? 0) <= total) {
      friendAmount = round2(total - (tx.userImpact[uid] || 0))
    } else if (tx.assignedFriendUid && tx.userImpact && tx.assignedFriendUid in tx.userImpact) {
      friendAmount = round2(tx.userImpact[tx.assignedFriendUid] || total)
    } else {
      friendAmount = round2(total / 2)
    }
    friendAmount = Math.max(0, Math.min(total, friendAmount))
    const myAmount = round2(Math.max(0, total - friendAmount))
    if (Math.abs(myAmount) > 0.0001) shares.set('__me__', myAmount)
    shares.set(friendName, friendAmount)
  } else if (Boolean(tx.externalName || tx.splitType === 'external')) {
    // 5. Terceiro / Externo
    const extName = tx.externalName?.trim() || 'Terceiro'
    const extAmount = tx.splitType === 'external'
      ? total
      : (tx.userImpact && uid in tx.userImpact && (tx.userImpact[uid] ?? 0) >= 0 && (tx.userImpact[uid] ?? 0) <= total)
        ? round2(total - (tx.userImpact[uid] || 0))
        : total
    const myAmount = round2(Math.max(0, total - extAmount))
    if (Math.abs(myAmount) > 0.0001) shares.set('__me__', myAmount)
    shares.set(extName, extAmount)
  } else {
    // 6. 100% Meu
    shares.set('__me__', total)
  }

  // 7. Reconciliação estrita de centavos:
  // Garante com precisão absoluta que a soma de todas as cotas seja EXATAMENTE igual a 'total'.
  let sumShares = 0
  for (const v of shares.values()) {
    sumShares = round2(sumShares + v)
  }
  const diff = round2(total - sumShares)
  if (Math.abs(diff) > 0.0001) {
    if (shares.has('__me__')) {
      const curMe = shares.get('__me__') || 0
      const adjustedMe = round2(curMe + diff)
      if (adjustedMe > 0) {
        shares.set('__me__', adjustedMe)
      } else {
        shares.delete('__me__')
      }
    } else if (diff > 0) {
      shares.set('__me__', diff)
    } else {
      let largestKey: string | null = null
      let largestVal = -1
      for (const [k, v] of shares.entries()) {
        if (v > largestVal) {
          largestVal = v
          largestKey = k
        }
      }
      if (largestKey) {
        shares.set(largestKey, round2(Math.max(0, largestVal + diff)))
      }
    }
  }

  return shares
}

/**
 * Calcula o valor atribuível a um participante específico em uma transação da fatura.
 */
export function participantAmountIn(
  tx: Transaction,
  participant: InvoiceParticipantShare,
  uid: string
): number {
  if (tx.type !== 'expense') return 0
  const shares = getTransactionParticipantShares(tx, uid)

  if (participant.isMe) {
    return shares.get('__me__') ?? 0
  }

  const partName = participant.name.trim().toLowerCase()
  for (const [k, v] of shares.entries()) {
    const kNorm = k.trim().toLowerCase()
    if (
      kNorm === partName ||
      (partName === 'parceiro(a)' && (kNorm === 'parceiro' || kNorm === 'parceira' || kNorm === 'parceiro(a)'))
    ) {
      return v
    }
  }
  return 0
}

/**
 * Recalcula a distribuição de categorias exclusivamente para os gastos do participante.
 */
export function calculateCategoryBreakdownForParticipant(
  expenses: Transaction[],
  participant: InvoiceParticipantShare,
  uid: string
): InvoiceCategorySlice[] {
  const participantExpenses: Array<{ tx: Transaction; share: number }> = []

  for (const tx of expenses) {
    const share = participantAmountIn(tx, participant, uid)
    if (share > 0) {
      participantExpenses.push({ tx, share })
    }
  }

  const totalParticipantExpense = participantExpenses.reduce((s, p) => s + p.share, 0)

  const catMap = new Map<string, number>()
  for (const { tx, share } of participantExpenses) {
    catMap.set(tx.category, (catMap.get(tx.category) ?? 0) + share)
  }

  return [...catMap.entries()]
    .map(([category, amount]) => ({
      category,
      amount: round2(amount),
      pct: totalParticipantExpense > 0 ? Math.round((amount / totalParticipantExpense) * 100) : 0,
    }))
    .sort((a, b) => b.amount - a.amount)
}
