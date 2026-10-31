/**
 * Motor de faturas de cartão (puro, sem dependências de UI/Firebase).
 *
 * Uma "fatura" é derivada: todas as transações de um cartão (`cardId`)
 * com o mesmo mês de competência (`competenceMonth` = mês de vencimento).
 * Não há coleção nova no Firestore — o acompanhamento é 100% calculado.
 */
import type { Card, Transaction } from '@/types'
import { clampPct, effectiveAmount } from './finance'
import { daysInMonth, formatBRL, round2, shiftMonth, todayKey } from './utils'

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

export type PredictiveInvoiceAlert =
  | {
    kind: 'anomaly'
    category: string
    pctIncrease: number
    currentAmount: number
    previousAvgAmount: number
    headline: string
    detail: string
  }
  | {
    kind: 'new_subscription'
    serviceName: string
    amount: number
    headline: string
    detail: string
  }
  | {
    kind: 'projection'
    projectedTotal: number
    dailyAverage: number
    daysPassed: number
    daysTotal: number
    daysRemaining: number
    headline: string
    detail: string
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
  /** Motor de alerta preditivo calculado */
  predictiveAlert?: PredictiveInvoiceAlert | null
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
        !t.reserveCardId,
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

  const isOwner = !card.ownerUid || card.ownerUid === uid
  const rawOwnerName = card.ownerName || 'Titular'

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
    const shares = getTransactionParticipantShares(t, uid, card, all)
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
  if (credits > 0) {
    const targetKey = isOwner ? 'Você (Minha parte)' : rawOwnerName
    if (participantMap.has(targetKey)) {
      const cur = participantMap.get(targetKey)!
      cur.amount = round2(Math.max(0, cur.amount - credits))
    } else if (participantMap.has('Você (Minha parte)')) {
      const cur = participantMap.get('Você (Minha parte)')!
      cur.amount = round2(Math.max(0, cur.amount - credits))
    }
  }

  const myShare = round2(participantMap.get('Você (Minha parte)')?.amount ?? 0)
  const othersShare = round2(Math.max(0, total - myShare))

  const splitBreakdown: InvoiceParticipantShare[] = [...participantMap.values()]
    .filter((p) => p.amount > 0.001)
    .map((p) => ({
      ...p,
      pct: total > 0 ? Math.round((p.amount / total) * 100) : 0,
    }))
    .sort((a, b) => (b.isMe ? 1 : 0) - (a.isMe ? 1 : 0) || b.amount - a.amount)
    .sort((a, b) => (b.isMe ? 1 : 0) - (a.isMe ? 1 : 0) || b.amount - a.amount)

  // Maior gasto da fatura
  const topExpense =
    expenses.length > 0 ? [...expenses].sort((a, b) => effectiveAmount(b) - effectiveAmount(a))[0] : null

  // Comprometimento futuro em parcelas deste cartão
  const futureItems = (all || []).filter(
    (t) =>
      t.cardId === card.id &&
      t.competenceMonth > monthKey &&
      !t.reserveCardId,
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
 * O usuário atual/visualizador é indexado pela chave especial "__me__" (que se tornará "Você (Minha parte)").
 * O titular do cartão (se o visualizador for um convidado) é indexado pelo nome do titular.
 * Outros participantes são indexados por seus respectivos nomes.
 * A soma de todos os valores no mapa é GARANTIDA de ser igual a round2(effectiveAmount(tx)).
 */
export function getTransactionParticipantShares(
  tx: Transaction,
  uid: string,
  card?: Card | null,
  _all: Transaction[] = []
): Map<string, number> {
  if (tx.type !== 'expense') return new Map()
  const total = round2(effectiveAmount(tx))
  if (Math.abs(total) <= 0.0001) return new Map()

  const shares = new Map<string, number>()

  const ownerUid = card?.ownerUid || tx.paidByUid || tx.createdByUid
  const rawOwnerName = card?.ownerName || 'Titular'
  const isViewerOwner = uid === ownerUid
  const ownerDisplayName = isViewerOwner ? '__me__' : rawOwnerName

  // 1. Alocações explícitas
  const validAllocs = (tx.allocations || []).filter((a) => Math.abs(a.amount || 0) > 0.0001)
  if (validAllocs.length > 0) {
    for (const a of validAllocs) {
      const amt = round2(a.amount || 0)
      const isMyUid = Boolean(a.uid && a.uid === uid)
      const isOwnerUid = Boolean(a.uid && a.uid === ownerUid)
      const norm = (a.name || '').trim().toLowerCase()
      const isMeName =
        norm === 'você' ||
        norm === 'voce' ||
        norm === 'meu' ||
        norm === 'minha parte' ||
        norm === 'você (minha parte)' ||
        norm === 'voce (minha parte)'

      let key = a.name?.trim() || 'Outro'
      if (isMyUid) key = '__me__'
      else if (isOwnerUid) key = ownerDisplayName
      else if (isMeName) key = ownerDisplayName

      shares.set(key, round2((shares.get(key) ?? 0) + amt))
    }
  } else if (tx.splitType === 'split_50') {
    // 2. Divisão 50/50 implícita
    const half = round2(total / 2)
    const otherHalf = round2(total - half)
    shares.set(ownerDisplayName, round2((shares.get(ownerDisplayName) ?? 0) + half))
    const partnerLabel = tx.externalName?.trim() || 'Parceiro(a)'
    const partnerKey = tx.assignedFriendUid === uid ? '__me__' : partnerLabel
    shares.set(partnerKey, round2((shares.get(partnerKey) ?? 0) + otherHalf))
  } else if (tx.splitType === 'partner') {
    // 3. 100% Parceiro(a)
    const partnerLabel = tx.externalName?.trim() || 'Parceiro(a)'
    const partnerKey = tx.assignedFriendUid === uid ? '__me__' : partnerLabel
    shares.set(partnerKey, total)
  } else if (tx.assignedFriendName || tx.assignedFriendUid || tx.splitType === 'friend') {
    // 4. Amigo atribuído
    const friendUid = tx.assignedFriendUid
    const friendName = tx.assignedFriendName?.trim() || 'Amigo'
    const pct = tx.friendSharePct && tx.friendSharePct > 0 ? clampPct(tx.friendSharePct) : 50
    const friendAmount = round2((total * pct) / 100)
    const buyerAmount = round2(total - friendAmount)

    const isFriendViewer = Boolean(friendUid && friendUid === uid)
    const friendKey = isFriendViewer ? '__me__' : friendName
    const buyerKey = ownerDisplayName

    if (Math.abs(friendAmount) > 0.0001) {
      shares.set(friendKey, round2((shares.get(friendKey) ?? 0) + friendAmount))
    }
    if (Math.abs(buyerAmount) > 0.0001) {
      shares.set(buyerKey, round2((shares.get(buyerKey) ?? 0) + buyerAmount))
    }
  } else if (tx.externalName || tx.splitType === 'external') {
    // 5. Terceiro / Externo
    const extName = tx.externalName?.trim() || 'Terceiro'
    shares.set(extName, total)
  } else {
    // 6. 100% Meu (despesa própria do titular do cartão)
    shares.set(ownerDisplayName, total)
  }

  // 7. Reconciliação estrita de centavos:
  let sumShares = 0
  for (const v of shares.values()) {
    sumShares = round2(sumShares + v)
  }
  const diff = round2(total - sumShares)
  if (Math.abs(diff) > 0.0001) {
    const primaryKey = ownerDisplayName
    const cur = shares.get(primaryKey) || 0
    shares.set(primaryKey, round2(cur + diff))
  }

  return shares
}

/**
 * Calcula o valor atribuível a um participante específico em uma transação da fatura.
 */
export function participantAmountIn(
  tx: Transaction,
  participant: InvoiceParticipantShare,
  uid: string,
  card?: Card | null
): number {
  if (tx.type !== 'expense') return 0
  const shares = getTransactionParticipantShares(tx, uid, card)

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
  uid: string,
  card?: Card | null
): InvoiceCategorySlice[] {
  const participantExpenses: Array<{ tx: Transaction; share: number }> = []

  for (const tx of expenses) {
    const share = participantAmountIn(tx, participant, uid, card)
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

export interface ConsolidatedInvoiceMeta {
  isConsolidatedInvoice: true
  card: Card
  invoice: CardInvoice
  totalInvoiceAmount: number
  myShare: number
  othersShare: number
  itemsCount: number
  closingDate: string
  dueDate: string
  invoiceStatus: InvoiceStatus
  isOwner: boolean
}

export type ConsolidatedTransaction = Transaction & {
  invoiceMeta?: ConsolidatedInvoiceMeta
}

export function createConsolidatedInvoiceTx(
  invoice: CardInvoice,
  uid: string,
  monthKey: string
): ConsolidatedTransaction {
  const card = invoice.card
  const isOwner = card.ownerUid === uid
  const myShare = invoice.myShare
  const status = invoice.status === 'paid' ? 'paid' : (invoice.status === 'open' ? 'estimated' : 'pending')

  const desc = `Fatura ${card.cardName}`

  const meta: ConsolidatedInvoiceMeta = {
    isConsolidatedInvoice: true,
    card,
    invoice,
    totalInvoiceAmount: invoice.total,
    myShare: invoice.myShare,
    othersShare: invoice.othersShare,
    itemsCount: invoice.itemsCount,
    closingDate: invoice.closingDate,
    dueDate: invoice.dueDate,
    invoiceStatus: invoice.status,
    isOwner,
  }

  return {
    id: `invoice_${card.id}_${monthKey}`,
    createdByUid: card.ownerUid,
    paidByUid: card.ownerUid,
    cardId: card.id,
    description: desc,
    category: 'Outros',
    subcategory: `Vencimento dia ${card.dueDay}`,
    type: 'expense',
    paymentMethod: 'credit_card',
    status,
    estimatedAmount: status === 'paid' ? null : myShare,
    actualAmount: status === 'paid' ? myShare : null,
    totalAmount: myShare,
    dueDate: invoice.dueDate,
    competenceMonth: monthKey,
    visibleToUids: [uid],
    userImpact: { [uid]: myShare },
    allocations: [
      {
        type: 'PLATFORM_USER',
        uid,
        name: isOwner ? 'Você (Minha parte)' : (invoice.splitBreakdown.find((p) => p.isMe)?.name || 'Minha parte'),
        amount: myShare,
        reimbursed: false,
      },
      ...(isOwner && invoice.othersShare > 0
        ? [
          {
            type: 'EXTERNAL' as const,
            name: 'Terceiros / Amigos',
            amount: invoice.othersShare,
            reimbursed: false,
          },
        ]
        : []),
    ],
    invoiceMeta: meta,
  }
}

export function consolidateMonthTransactions(
  monthTransactions: Transaction[],
  allTransactions: Transaction[],
  cards: Card[],
  uid: string,
  monthKey: string,
  enabled: boolean = true
): ConsolidatedTransaction[] {
  if (!enabled || !uid) return monthTransactions as ConsolidatedTransaction[]

  // Transações não vinculadas a cartão
  const nonCardTxs = (monthTransactions || []).filter(
    (t) => t.paymentMethod !== 'credit_card' || !t.cardId || Boolean(t.reserveCardId)
  )

  // Conjunto de cartões relevantes
  const relevantCardMap = new Map<string, Card>()
  for (const c of cards || []) {
    if (c.ownerUid === uid || (c.sharedWithUids || []).includes(uid)) {
      relevantCardMap.set(c.id, c)
    }
  }

  // Também inclui cartões referenciados em monthTransactions caso algum não esteja no array cards
  for (const t of monthTransactions || []) {
    if (t.paymentMethod === 'credit_card' && t.cardId && !relevantCardMap.has(t.cardId)) {
      const found = (cards || []).find((c) => c.id === t.cardId)
      if (found) relevantCardMap.set(found.id, found)
    }
  }

  const invoiceTxs: ConsolidatedTransaction[] = []

  for (const card of relevantCardMap.values()) {
    const inv = buildCardInvoice(card, monthKey, allTransactions, uid)
    // Se não há compras nesta fatura para o usuário, não gera linha de extrato
    if (inv.itemsCount === 0) continue

    // Se o usuário é apenas convidado e não tem cota nesta fatura, pula
    if (card.ownerUid !== uid && inv.myShare <= 0) continue

    invoiceTxs.push(createConsolidatedInvoiceTx(inv, uid, monthKey))
  }

  return [...nonCardTxs, ...invoiceTxs]
}

/**
 * Motor de Alertas Preditivos:
 * Calcula dinamicamente um dos 3 insights preditivos para a fatura:
 * 1. Alerta de Anomalia (aumento significativo em categorias de gastos vs histórico)
 * 2. Nova Assinatura detectada no ciclo atual (serviço recorrente inédito no cartão)
 * 3. Projeção estimada de fechamento (baseada na velocidade de gasto diário e dias restantes)
 */
export function computePredictiveAlert(
  invoice: CardInvoice,
  all: Transaction[],
  history: CardInvoice[],
  today: string = todayKey()
): PredictiveInvoiceAlert {
  const card = invoice.card
  const monthKey = invoice.monthKey

  // 1. Detectar anomalia de gastos em relação aos ciclos anteriores
  const pastInvoices = history.filter((it) => it.monthKey < monthKey && it.grossExpenses > 0)
  if (pastInvoices.length > 0) {
    const anomalies: { category: string; pct: number; pastAvg: number }[] = []
    for (const catSlice of invoice.byCategory) {
      if (catSlice.amount < 40) continue
      const pastAmounts = pastInvoices
        .map((past) => past.byCategory.find((c) => c.category.toLowerCase() === catSlice.category.toLowerCase())?.amount ?? 0)
        .filter((a) => a > 0)
      const pastAvg = pastAmounts.length > 0 ? pastAmounts.reduce((s, v) => s + v, 0) / pastAmounts.length : 0
      if (pastAvg >= 25 && catSlice.amount > pastAvg) {
        const pct = Math.round(((catSlice.amount - pastAvg) / pastAvg) * 100)
        if (pct >= 25) {
          anomalies.push({ category: catSlice.category, pct, pastAvg })
        }
      }
    }
    anomalies.sort((a, b) => b.pct - a.pct)
    const topAnomaly = anomalies[0]
    if (topAnomaly) {
      const { category, pct, pastAvg } = topAnomaly
      const currentAmount = invoice.byCategory.find((c) => c.category.toLowerCase() === category.toLowerCase())?.amount ?? 0
      return {
        kind: 'anomaly',
        category,
        pctIncrease: pct,
        currentAmount,
        previousAvgAmount: round2(pastAvg),
        headline: `Seus gastos em ${category} subiram +${pct}%`,
        detail: `Neste ciclo foram ${formatBRL(currentAmount)} vs média de ${formatBRL(pastAvg)} dos ciclos anteriores.`,
      }
    }
  }

  // 2. Detectar novas assinaturas / serviços recorrentes detectados pela primeira vez
  const knownServices = [
    'netflix', 'spotify', 'amazon', 'prime', 'disney', 'hbo', 'max',
    'youtube', 'icloud', 'apple', 'chatgpt', 'openai', 'deezer',
    'crunchyroll', 'globo', 'globoplay', 'gympass', 'totalpass',
    'smart fit', 'alura', 'uber one', 'meli+', 'star+'
  ]
  const expenses = invoice.items.filter((t) => t.type === 'expense')
  const newSub = expenses.find((tx) => {
    const descLower = tx.description.toLowerCase()
    const isCandidate =
      knownServices.some((s) => descLower.includes(s)) ||
      Boolean(tx.recurringBillId) ||
      tx.category.toLowerCase().includes('assinatura') ||
      tx.category.toLowerCase().includes('streaming')

    if (!isCandidate) return false

    // Verifica se essa descrição já apareceu em faturas passadas deste cartão
    const existedInPast = all.some((pastTx) => {
      if (pastTx.cardId !== card.id) return false
      if (pastTx.competenceMonth >= monthKey) return false
      if (pastTx.type !== 'expense') return false
      const pastLower = pastTx.description.toLowerCase()
      return pastLower.includes(descLower) || descLower.includes(pastLower)
    })
    return !existedInPast
  })
  if (newSub) {
    const subAmount = effectiveAmount(newSub)
    return {
      kind: 'new_subscription',
      serviceName: newSub.description,
      amount: subAmount,
      headline: `Nova assinatura detectada: ${newSub.description}`,
      detail: `Detectamos uma nova cobrança recorrente de ${formatBRL(subAmount)} neste ciclo.`,
    }
  }

  // 3. Projeção estimada de fechamento (baseada no gasto médio diário)
  const closingMonth = card.dueDay < card.closingDay ? shiftMonth(monthKey, -1) : monthKey
  const prevClosingMonth = shiftMonth(closingMonth, -1)
  const prevClosingDate = clampDay(prevClosingMonth, card.closingDay)
  const cycleTotalDays = Math.max(1, diffDays(prevClosingDate, invoice.closingDate))
  const cyclePassedDays = Math.min(cycleTotalDays, Math.max(1, diffDays(prevClosingDate, today)))
  const daysRemaining = Math.max(0, cycleTotalDays - cyclePassedDays)

  const dailyAvg = invoice.total > 0 ? round2(invoice.total / cyclePassedDays) : 0
  const projected = daysRemaining > 0 && dailyAvg > 0 ? round2(invoice.total + dailyAvg * daysRemaining) : invoice.total

  let headline = ''
  let detail = ''
  if (invoice.total <= 0) {
    headline = 'Fatura sem lançamentos'
    detail = 'Nenhum gasto registrado neste ciclo até o momento.'
  } else if (daysRemaining > 0) {
    headline = `Fechamento estimado: ${formatBRL(projected)}`
    detail = `Média de ${formatBRL(dailyAvg)}/dia (${cyclePassedDays} de ${cycleTotalDays} dias corridos · restam ${daysRemaining} dias).`
  } else {
    headline = `Fatura fechada: ${formatBRL(invoice.total)}`
    detail = `Média diária final de ${formatBRL(dailyAvg)}/dia durante o ciclo de ${cycleTotalDays} dias.`
  }

  return {
    kind: 'projection',
    projectedTotal: projected,
    dailyAverage: dailyAvg,
    daysPassed: cyclePassedDays,
    daysTotal: cycleTotalDays,
    daysRemaining,
    headline,
    detail,
  }
}
