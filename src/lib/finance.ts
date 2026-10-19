/**
 * Motor de regras financeiras (puro, sem dependências de UI/Firebase).
 * Todas as fórmulas dos KPIs, divisões (allocations/userImpact) e
 * estimativas de contas recorrentes vivem aqui.
 */
import type {
  Allocation,
  Card,
  Category,
  MonthSummary,
  NewTransaction,
  ReceivableItem,
  RecurringBill,
  SplitType,
  Transaction,
  TransactionStatus,
  UserProfile,
  FutureMonthProjection,
} from '@/types'
import { currentMonthKey, dateInMonth, daysInMonth, monthLabel, normalize, round2, shiftMonth, todayKey } from './utils'

export interface Party {
  uid: string
  name: string
  email?: string
}

// ------------------------------------------------------------------
// A. Valor efetivo
// ------------------------------------------------------------------
export function effectiveAmount(tx: Pick<Transaction, 'actualAmount' | 'estimatedAmount' | 'totalAmount'>) {
  return tx.actualAmount ?? tx.estimatedAmount ?? tx.totalAmount ?? 0
}

/**
 * Calcula a data de vencimento da fatura e o mês de competência (orçamento comprometido)
 * para compras no cartão de crédito, respeitando a data de fechamento e vencimento do cartão.
 */
export function calculateCardInvoiceDates(
  purchaseDate: string,
  card?: Card | null,
  explicitDueDate?: string | null,
  explicitMonth?: string | null,
): { dueDate: string; competenceMonth: string } {
  if (explicitDueDate && /^\d{4}-\d{2}-\d{2}$/.test(explicitDueDate.trim())) {
    const cleanDueDate = explicitDueDate.trim()
    const cleanMonth = explicitMonth && /^\d{4}-\d{2}$/.test(explicitMonth.trim())
      ? explicitMonth.trim()
      : cleanDueDate.slice(0, 7)
    return { dueDate: cleanDueDate, competenceMonth: cleanMonth }
  }

  if (explicitMonth && /^\d{4}-\d{2}$/.test(explicitMonth.trim())) {
    const cleanMonth = explicitMonth.trim()
    const dueDayNum = card?.dueDay ?? 10
    const cleanDueDate = `${cleanMonth}-${String(dueDayNum).padStart(2, '0')}`
    return { dueDate: cleanDueDate, competenceMonth: cleanMonth }
  }

  if (!card || !card.closingDay || !card.dueDay) {
    const cleanMonth = purchaseDate.slice(0, 7)
    return { dueDate: purchaseDate, competenceMonth: cleanMonth }
  }

  const parts = purchaseDate.split('-')
  let year = parseInt(parts[0], 10)
  let month = parseInt(parts[1], 10)
  const day = parseInt(parts[2], 10)

  if (isNaN(year) || isNaN(month) || isNaN(day)) {
    const today = todayKey()
    return { dueDate: today, competenceMonth: today.slice(0, 7) }
  }

  // Determina o mês de fechamento da fatura
  let closingMonth = month
  let closingYear = year

  if (day >= card.closingDay) {
    closingMonth += 1
    if (closingMonth > 12) {
      closingMonth = 1
      closingYear += 1
    }
  }

  // O vencimento será no próprio mês do fechamento se dueDay for >= closingDay (raro mas possível)
  // Mas na imensa maioria dos bancos (fecha 25, vence 5), o vencimento cai no mês SEGUINTE ao fechamento.
  let dueMonth = closingMonth
  let dueYear = closingYear

  if (card.dueDay < card.closingDay) {
    dueMonth += 1
    if (dueMonth > 12) {
      dueMonth = 1
      dueYear += 1
    }
  }

  const competenceMonth = `${dueYear}-${String(dueMonth).padStart(2, '0')}`
  const dueDate = `${competenceMonth}-${String(card.dueDay).padStart(2, '0')}`

  return { dueDate, competenceMonth }
}


// ------------------------------------------------------------------
// D. Divisões: allocations → userImpact → visibleToUids
// ------------------------------------------------------------------
export function deriveImpact(allocations: Allocation[], visibleToUids: string[]) {
  const impact: Record<string, number> = {}
  for (const u of visibleToUids) impact[u] = 0
  for (const a of allocations) {
    if (a.type === 'PLATFORM_USER' && a.uid) {
      // Quando alguém reembolsa, o impacto NÃO some, pois a pessoa que pagou a fatura
      // original já pagou pro banco de qualquer forma, e quem foi reembolsado já enviou o dinheiro.
      // O impacto deve sempre refletir o valor de cota de cada um na transação para o mês,
      // para bater o orçamento (Saldo Livre) de cada usuário com precisão bancária.
      impact[a.uid] = round2((impact[a.uid] ?? 0) + a.amount)
    }
  }
  return impact
}

export interface SplitInput {
  splitType: SplitType
  total: number
  me: Party
  partner: Party | null
  payerUid: string
  createdByUid?: string
  externalName?: string
  assignedFriend?: Party | null
  acceptanceStatus?: 'pending_approval' | 'accepted' | 'rejected' | null
  /** Percentual (1–100) cobrado do amigo. Padrão: 100 */
  friendSharePct?: number | null
  /** Divisão personalizada multi-pessoas com valores específicos */
  customAllocations?: Allocation[]
  /** Mantém flags de reembolso já marcadas ao reconstruir */
  previous?: Allocation[]
}

/** Normaliza o percentual do amigo para o intervalo (0, 100]. */
export function clampPct(pct: number | null | undefined) {
  const n = Number(pct)
  if (!Number.isFinite(n) || n <= 0) return 100
  return Math.min(100, round2(n))
}

/** Valor que cabe ao amigo numa cobrança de amigo (fallback: total, para dados legados). */
export function friendShareOf(tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount'>) {
  if (!tx.assignedFriendUid) return 0
  const allocs = tx.allocations.filter((a) => a.uid === tx.assignedFriendUid)
  if (!allocs.length) return tx.totalAmount
  return round2(allocs.reduce((s, a) => s + a.amount, 0))
}

export function friendPctOf(tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'>) {
  if (tx.friendSharePct) return clampPct(tx.friendSharePct)
  if (!tx.totalAmount) return 100
  return clampPct((friendShareOf(tx) / tx.totalAmount) * 100)
}

export function buildSplit(input: SplitInput) {
  const { total, me, partner, payerUid, assignedFriend } = input
  let splitType = input.splitType
  if ((splitType === 'split_50' || splitType === 'partner') && !partner) splitType = 'mine'

  const wasReimbursed = (key: string) =>
    input.previous?.find((a) => (a.uid ?? a.name) === key)?.reimbursed ?? false

  const userAlloc = (p: Party, amount: number): Allocation => ({
    type: 'PLATFORM_USER',
    uid: p.uid,
    name: p.name,
    amount: round2(amount),
    // quem paga a fatura não precisa "reembolsar" a si mesmo
    reimbursed: p.uid === payerUid ? true : wasReimbursed(p.uid),
  })

  let allocations: Allocation[] = []
  switch (splitType) {
    case 'mine':
      allocations = [userAlloc(me, total)]
      break
    case 'split_50': {
      const half = round2(total / 2)
      allocations = [userAlloc(me, half), userAlloc(partner!, round2(total - half))]
      break
    }
    case 'partner':
      allocations = [userAlloc(partner!, total)]
      break
    case 'friend': {
      if (assignedFriend) {
        const pct = clampPct(input.friendSharePct)
        const friendAmount = round2((total * pct) / 100)
        const myAmount = round2(total - friendAmount)
        allocations =
          myAmount > 0
            ? [userAlloc(me, myAmount), userAlloc(assignedFriend, friendAmount)]
            : [userAlloc(assignedFriend, total)]
      } else {
        allocations = [userAlloc(me, total)]
      }
      break
    }
    case 'external': {
      const name = input.externalName?.trim() || 'Terceiro'
      allocations = [
        { type: 'EXTERNAL', name, amount: round2(total), reimbursed: wasReimbursed(name) },
      ]
      break
    }
    case 'custom': {
      if (input.customAllocations && input.customAllocations.length > 0) {
        allocations = input.customAllocations.map((a) => {
          const item: Allocation = {
            type: a.type,
            name: a.name.trim() || 'Participante',
            amount: round2(a.amount),
            reimbursed: a.uid === payerUid ? true : wasReimbursed(a.uid ?? a.name),
          }
          if (a.uid) item.uid = a.uid
          return item
        })
      } else {
        allocations = [userAlloc(me, total)]
      }
      break
    }
  }

  const visible = new Set<string>([me.uid, payerUid])
  if (input.createdByUid) visible.add(input.createdByUid)
  if (assignedFriend?.uid) visible.add(assignedFriend.uid)
  for (const a of allocations) if (a.uid) visible.add(a.uid)
  const visibleToUids = [...visible]

  return { allocations, userImpact: deriveImpact(allocations, visibleToUids), visibleToUids, splitType }
}

/** Reescala as divisões quando o valor efetivo muda (ex: conta de luz chegou). */
export function rescaleSplit(tx: Transaction, newTotal: number) {
  const oldTotal = tx.allocations.reduce((s, a) => s + a.amount, 0)
  let allocations: Allocation[]
  if (tx.allocations.length === 0) {
    allocations = []
  } else if (oldTotal <= 0) {
    const each = round2(newTotal / tx.allocations.length)
    allocations = tx.allocations.map((a) => ({ ...a, amount: each }))
  } else {
    const ratio = newTotal / oldTotal
    let acc = 0
    allocations = tx.allocations.map((a, i) => {
      if (i === tx.allocations.length - 1) return { ...a, amount: round2(newTotal - acc) }
      const amount = round2(a.amount * ratio)
      acc += amount
      return { ...a, amount }
    })
  }
  return {
    totalAmount: round2(newTotal),
    allocations,
    userImpact: deriveImpact(allocations, tx.visibleToUids),
  }
}

/** Detecta o tipo de divisão do ponto de vista de quem está olhando. */
export function detectSplit(tx: Transaction, viewerUid: string): SplitType {
  if (tx.splitType === 'custom') return 'custom'
  if (tx.allocations.length > 2) return 'custom'
  if (tx.assignedFriendUid) return 'friend'
  if (tx.allocations.some((a) => a.type === 'EXTERNAL')) {
    if (tx.allocations.length === 1) return 'external'
    return 'custom'
  }
  const users = tx.allocations.filter((a) => a.type === 'PLATFORM_USER')
  const mine = users.some((a) => a.uid === viewerUid)
  const others = users.some((a) => a.uid !== viewerUid)
  if (mine && others) {
    const myAlloc = users.find((a) => a.uid === viewerUid)?.amount ?? 0
    const otherAlloc = users.find((a) => a.uid !== viewerUid)?.amount ?? 0
    if (Math.abs(myAlloc - otherAlloc) > 0.05) return 'custom'
    return 'split_50'
  }
  if (mine) return 'mine'
  return 'partner'
}

export const SPLIT_LABELS: Record<SplitType, { label: string; emoji: string; short: string }> = {
  mine: { label: '100% Meu', emoji: '👤', short: 'Meu' },
  split_50: { label: '50/50 (Casal)', emoji: '🤝', short: '50/50' },
  partner: { label: '100% Parceiro(a)', emoji: '🔄', short: 'Parceiro(a)' },
  friend: { label: '🧑‍🤝‍🧑 Amigo da Rede', emoji: '🧑‍🤝‍🧑', short: 'Amigo' },
  external: { label: 'Terceiro Externo', emoji: '🏷️', short: 'Terceiro' },
  custom: { label: 'Divisão Personalizada', emoji: '⚖️', short: 'Personalizada' },
}

export interface ThirdPartyInfo {
  name: string
  label: string
  type: 'external' | 'friend' | 'partner' | 'sender'
  amount?: number
  percentage?: number
}

/**
 * Identifica o terceiro ou pessoa envolvida na despesa/entrada para exibição direta na tabela.
 */
export function getThirdPartyInfo(
  tx: Transaction,
  currentUid: string,
  partnerName?: string,
): ThirdPartyInfo | null {
  // 1. Alocações externas (ex: "Mãe", "Irmão", "Cliente", etc.)
  const externalAllocs = tx.allocations?.filter((a) => a.type === 'EXTERNAL')
  if (externalAllocs && externalAllocs.length > 0) {
    const names = externalAllocs.map((a) => a.name).filter(Boolean)
    if (names.length > 0) {
      return {
        name: names.join(', '),
        label: 'Terceiro',
        type: 'external',
        amount: externalAllocs.reduce((s, a) => s + (a.amount || 0), 0),
      }
    }
  }

  // 2. Amigo atribuído (assignedFriend)
  if (tx.assignedFriendName) {
    if (tx.assignedFriendUid === currentUid) {
      const sender = tx.paidByUid !== currentUid && partnerName ? partnerName : (tx.assignedFriendName || 'Amigo')
      return {
        name: sender,
        label: 'Recebido de',
        type: 'sender',
      }
    }
    return {
      name: tx.assignedFriendName,
      label: 'Amigo',
      type: 'friend',
      percentage: tx.friendSharePct ?? undefined,
    }
  }

  // 3. Outros usuários da plataforma em divisões customizadas
  if (tx.allocations && tx.allocations.length > 0) {
    const otherUsers = tx.allocations.filter(
      (a) => a.uid && a.uid !== currentUid && a.name && a.name.toLowerCase() !== 'você'
    )
    if (otherUsers.length > 0) {
      const names = otherUsers.map((a) => a.name).filter(Boolean)
      const isPartner = partnerName && names.includes(partnerName)
      return {
        name: names.join(', '),
        label: isPartner ? 'Parceiro(a)' : 'Amigo',
        type: isPartner ? 'partner' : 'friend',
        amount: otherUsers.reduce((s, a) => s + (a.amount || 0), 0),
      }
    }
  }

  // 4. Tipo de divisão explícito
  if (tx.splitType === 'external') {
    return {
      name: 'Terceiro Externo',
      label: 'Terceiro',
      type: 'external',
    }
  }

  if (tx.splitType === 'partner' && partnerName) {
    return {
      name: partnerName,
      label: 'Parceiro(a)',
      type: 'partner',
    }
  }

  if (tx.splitType === 'split_50' && partnerName) {
    return {
      name: partnerName,
      label: '50/50',
      type: 'partner',
    }
  }

  // 5. Cartão compartilhado onde outro usuário pagou
  if (tx.paidByUid && tx.paidByUid !== currentUid && partnerName) {
    return {
      name: partnerName,
      label: 'Pago por',
      type: 'sender',
    }
  }

  return null
}

// ------------------------------------------------------------------
// D.5 Reserva de montante em cartão (anti-duplicidade)
// ------------------------------------------------------------------
export function getReserveInfo(tx: Transaction, all: Transaction[]) {
  if (!tx.reserveCardId) return null
  const reserve = effectiveAmount(tx)
  const consumed = round2(
    all
      .filter(
        (o) =>
          o.id !== tx.id &&
          !o.reserveCardId &&
          o.type === 'expense' &&
          o.cardId === tx.reserveCardId &&
          o.competenceMonth === tx.competenceMonth,
      )
      .reduce((s, o) => s + (o.userImpact[tx.createdByUid] ?? 0), 0),
  )
  const remaining = round2(Math.max(0, reserve - consumed))
  return { reserve, consumed, remaining, pct: reserve > 0 ? Math.min(100, (consumed / reserve) * 100) : 0 }
}

/** Impacto efetivo de uma transação no Saldo Livre de um usuário. */
export function impactFor(tx: Transaction, uid: string, all: Transaction[]) {
  if (tx.reserveCardId) {
    if (tx.createdByUid !== uid) return 0
    return getReserveInfo(tx, all)?.remaining ?? 0
  }

  // Feature 2: Cobrança atribuída a amigo (total ou percentual)
  if (tx.assignedFriendUid) {
    const friendShare = friendShareOf(tx)
    if (tx.acceptanceStatus === 'accepted') {
      // Amigo aceitou: cada um sofre apenas a sua parte
      if (uid === tx.assignedFriendUid) return friendShare
      return tx.userImpact[uid] ?? 0
    }
    // Pendente ou recusado: amigo ainda NÃO tem o saldo comprometido
    if (uid === tx.assignedFriendUid) return 0
    // Quem lançou provisiona também a parte do amigo até o aceite
    const own = tx.userImpact[uid] ?? 0
    return uid === tx.createdByUid ? round2(own + friendShare) : own
  }

  return tx.userImpact[uid] ?? 0
}

// ------------------------------------------------------------------
// B. KPIs do mês
// ------------------------------------------------------------------
export function getMonthState(monthKey: string): MonthSummary['monthState'] {
  const cur = currentMonthKey()
  if (monthKey === cur) return 'current'
  return monthKey < cur ? 'past' : 'future'
}

export function getDaysRemaining(monthKey: string) {
  const dim = daysInMonth(monthKey)
  const state = getMonthState(monthKey)
  if (state === 'past') return 0
  if (state === 'future') return dim
  return dim - new Date().getDate() + 1 // inclui o dia de hoje
}

export function computeSummary(all: Transaction[], uid: string, monthKey: string): MonthSummary {
  const monthTxs = all.filter((t) => t.competenceMonth === monthKey && t.visibleToUids.includes(uid))
  let income = 0
  let paid = 0
  let committedEstimated = 0
  let committedPending = 0
  let receivableFromPartner = 0
  let receivableFromExternal = 0
  let receivableFromFriends = 0
  let payableToOthers = 0
  const cat = new Map<string, number>()
  const subcat = new Map<string, { category: string; subcategory: string; amount: number; count: number }>()

  for (const tx of monthTxs) {
    const impact = impactFor(tx, uid, all)
    if (tx.type === 'income') {
      income += impact
      continue
    }
    if (tx.status === 'paid') paid += impact
    else if (tx.status === 'estimated') committedEstimated += impact
    else committedPending += impact

    if (impact > 0) {
      cat.set(tx.category, (cat.get(tx.category) ?? 0) + impact)
      if (tx.subcategory) {
        const subKey = `${tx.category} › ${tx.subcategory}`
        const existing = subcat.get(subKey)
        if (existing) {
          existing.amount += impact
          existing.count += 1
        } else {
          subcat.set(subKey, {
            category: tx.category,
            subcategory: tx.subcategory,
            amount: impact,
            count: 1,
          })
        }
      }
    }

    const friendAlloc = tx.assignedFriendUid ? tx.allocations.find((a) => a.uid === tx.assignedFriendUid) : undefined
    const friendOpen = tx.acceptanceStatus === 'accepted' && !(friendAlloc?.reimbursed ?? false)

    if (tx.paidByUid === uid) {
      if (tx.assignedFriendUid && tx.assignedFriendUid !== uid && friendOpen) {
        receivableFromFriends += friendShareOf(tx)
      }
      for (const a of tx.allocations) {
        if (a.reimbursed) continue
        if (a.type === 'EXTERNAL') receivableFromExternal += a.amount
        else if (a.uid !== uid && a.uid !== tx.assignedFriendUid) receivableFromPartner += a.amount
      }
    } else {
      if (tx.assignedFriendUid === uid && friendOpen) {
        payableToOthers += friendShareOf(tx)
      }
      for (const a of tx.allocations) {
        if (a.type === 'PLATFORM_USER' && a.uid === uid && !a.reimbursed && !tx.assignedFriendUid) payableToOthers += a.amount
      }
    }
  }

  const committed = committedEstimated + committedPending
  const freeBalance = income - (paid + committed)
  const daysRemaining = getDaysRemaining(monthKey)

  return {
    income: round2(income),
    paid: round2(paid),
    committed: round2(committed),
    committedEstimated: round2(committedEstimated),
    committedPending: round2(committedPending),
    freeBalance: round2(freeBalance),
    daysRemaining,
    daysInMonth: daysInMonth(monthKey),
    dailyLimit: daysRemaining > 0 ? round2(freeBalance / daysRemaining) : null,
    monthState: getMonthState(monthKey),
    receivableFromPartner: round2(receivableFromPartner),
    receivableFromExternal: round2(receivableFromExternal),
    receivableFromFriends: round2(receivableFromFriends),
    receivableTotal: round2(receivableFromPartner + receivableFromExternal + receivableFromFriends),
    payableToOthers: round2(payableToOthers),
    byCategory: [...cat.entries()]
      .map(([category, amount]) => ({ category, amount: round2(amount) }))
      .sort((a, b) => b.amount - a.amount),
    bySubcategory: [...subcat.values()]
      .map((item) => ({ ...item, amount: round2(item.amount) }))
      .sort((a, b) => b.amount - a.amount),
  }
}

export function listReceivables(all: Transaction[], uid: string, monthKey: string, cards: Card[]) {
  const items: ReceivableItem[] = []
  const payables: ReceivableItem[] = []
  for (const tx of all) {
    if (tx.competenceMonth !== monthKey || tx.type !== 'expense' || !tx.visibleToUids.includes(uid)) continue
    const cardName = cards.find((c) => c.id === tx.cardId)?.cardName

    // Amigo atribuído no cartão
    if (tx.assignedFriendUid) {
      const idx = Math.max(0, tx.allocations.findIndex((a) => a.uid === tx.assignedFriendUid))
      if (tx.allocations[idx]?.reimbursed && tx.allocations[idx]?.uid === tx.assignedFriendUid) continue
      const base = {
        transactionId: tx.id,
        allocationIndex: idx,
        description: tx.description,
        dueDate: tx.dueDate,
        type: 'PLATFORM_USER' as const,
        amount: friendShareOf(tx),
        cardName,
        acceptanceStatus: tx.acceptanceStatus,
        friendSharePct: friendPctOf(tx),
      }
      if (tx.paidByUid === uid && tx.assignedFriendUid !== uid) {
        items.push({ ...base, name: tx.assignedFriendName || 'Amigo' })
      } else if (tx.assignedFriendUid === uid && tx.paidByUid !== uid) {
        payables.push({ ...base, name: 'Dono do Cartão' })
      }
      continue
    }

    tx.allocations.forEach((a, i) => {
      if (a.reimbursed) return
      const base: ReceivableItem = {
        transactionId: tx.id,
        allocationIndex: i,
        description: tx.description,
        dueDate: tx.dueDate,
        name: a.name,
        type: a.type,
        amount: a.amount,
        cardName,
      }
      if (tx.paidByUid === uid && (a.type === 'EXTERNAL' || a.uid !== uid)) items.push(base)
      if (tx.paidByUid !== uid && a.type === 'PLATFORM_USER' && a.uid === uid) payables.push(base)
    })
  }
  return { receivables: items, payables }
}

// ------------------------------------------------------------------
// Status / valores
// ------------------------------------------------------------------
export const STATUS_ORDER: TransactionStatus[] = ['estimated', 'pending', 'paid']

export function nextStatus(s: TransactionStatus): TransactionStatus {
  return STATUS_ORDER[(STATUS_ORDER.indexOf(s) + 1) % STATUS_ORDER.length]
}

export function statusPatch(tx: Transaction, status: TransactionStatus): Partial<Transaction> {
  const patch: Partial<Transaction> = { status }
  if (status === 'paid' && tx.actualAmount == null) {
    patch.actualAmount = tx.estimatedAmount ?? tx.totalAmount
  }
  return patch
}

export function amountPatch(
  tx: Transaction,
  values: { estimatedAmount?: number | null; actualAmount?: number | null },
): Partial<Transaction> {
  const merged = {
    estimatedAmount: values.estimatedAmount !== undefined ? values.estimatedAmount : tx.estimatedAmount,
    actualAmount: values.actualAmount !== undefined ? values.actualAmount : tx.actualAmount,
  }
  const newTotal = merged.actualAmount ?? merged.estimatedAmount ?? tx.totalAmount
  return { ...merged, ...rescaleSplit(tx, newTotal) }
}

// ------------------------------------------------------------------
// C. Estimativa de contas recorrentes
// ------------------------------------------------------------------
export function estimateRecurring(bill: RecurringBill, all: Transaction[], monthKey: string) {
  const fromMonth = (offset: number) =>
    all.find((t) => t.recurringBillId === bill.id && t.competenceMonth === shiftMonth(monthKey, -offset))

  if (bill.estimationRule === 'last_month') {
    const prev = fromMonth(1)
    const v = prev ? (prev.actualAmount ?? prev.estimatedAmount) : null
    if (v != null) return { amount: round2(v), basis: 'Valor real do mês anterior' }
    return { amount: bill.defaultAmount, basis: 'Sem histórico — valor padrão' }
  }
  if (bill.estimationRule === 'avg_3_months') {
    const values = [1, 2, 3]
      .map(fromMonth)
      .filter((t): t is Transaction => !!t && t.actualAmount != null)
      .map((t) => t.actualAmount as number)
    if (values.length)
      return {
        amount: round2(values.reduce((s, v) => s + v, 0) / values.length),
        basis: `Média de ${values.length} ${values.length === 1 ? 'mês' : 'meses'} pagos`,
      }
    return { amount: bill.defaultAmount, basis: 'Sem histórico — valor padrão' }
  }
  return { amount: bill.defaultAmount, basis: 'Valor fixo' }
}

export const ESTIMATION_LABELS = {
  fixed: 'Valor fixo',
  last_month: 'Mês anterior',
  avg_3_months: 'Média 3 meses',
} as const

export function generateMonthBills(args: {
  bills: RecurringBill[]
  all: Transaction[]
  monthKey: string
  me: Party
  partner: Party | null
  profile: UserProfile | null
}) {
  const { bills, all, monthKey, me, partner, profile } = args
  const created: NewTransaction[] = []
  let skipped = 0

  // Receita mensal (salário) a partir do perfil
  if (profile && profile.monthlyIncome > 0) {
    const salaryId = `salary_${me.uid}`
    const exists = all.some((t) => t.recurringBillId === salaryId && t.competenceMonth === monthKey)
    if (!exists) {
      const split = buildSplit({ splitType: 'mine', total: profile.monthlyIncome, me, partner: null, payerUid: me.uid })
      created.push({
        createdByUid: me.uid,
        paidByUid: me.uid,
        description: 'Salário',
        category: 'Receita',
        type: 'income',
        paymentMethod: 'pix',
        status: 'estimated',
        estimatedAmount: profile.monthlyIncome,
        actualAmount: null,
        totalAmount: profile.monthlyIncome,
        dueDate: dateInMonth(monthKey, 5),
        competenceMonth: monthKey,
        recurringBillId: salaryId,
        allocations: split.allocations,
        userImpact: split.userImpact,
        visibleToUids: split.visibleToUids,
        createdAt: Date.now(),
      })
    } else skipped++
  }

  for (const bill of bills.filter((b) => b.active && b.ownerUid === me.uid)) {
    // Feature 1: Limite de meses da recorrência/parcelas
    if (bill.startMonth && monthKey < bill.startMonth) {
      skipped++
      continue
    }
    if (bill.endMonth && monthKey > bill.endMonth) {
      skipped++
      continue
    }

    const exists = all.some((t) => t.recurringBillId === bill.id && t.competenceMonth === monthKey)
    if (exists) {
      skipped++
      continue
    }
    const { amount } = estimateRecurring(bill, all, monthKey)
    const split = buildSplit({
      splitType: bill.splitType ?? 'mine',
      total: amount,
      me,
      partner,
      payerUid: me.uid,
    })
    const isIncome = bill.type === 'income'
    created.push({
      createdByUid: me.uid,
      paidByUid: me.uid,
      description: bill.description,
      category: isIncome ? 'Receita' : bill.category,
      type: bill.type ?? 'expense',
      paymentMethod: bill.paymentMethod ?? (isIncome ? 'pix' : 'bill'),
      status: 'estimated',
      estimatedAmount: amount,
      actualAmount: null,
      totalAmount: amount,
      dueDate: dateInMonth(monthKey, bill.dueDay),
      competenceMonth: monthKey,
      recurringBillId: bill.id,
      allocations: split.allocations,
      userImpact: split.userImpact,
      visibleToUids: split.visibleToUids,
      createdAt: Date.now(),
    })
  }
  return { created, skipped }
}

export function projectFutureMonths(args: {
  all: Transaction[]
  bills: RecurringBill[]
  profile: UserProfile | null
  uid: string
  startMonthKey: string
  count?: number
}): FutureMonthProjection[] {
  const { all, bills, profile, uid, startMonthKey, count = 6 } = args
  const projections: FutureMonthProjection[] = []

  for (let i = 0; i < count; i++) {
    const monthKey = shiftMonth(startMonthKey, i)
    const label = monthLabel(monthKey)
    const dim = daysInMonth(monthKey)

    // Check existing transactions for that month visible to uid
    const monthTxs = all.filter((t) => t.competenceMonth === monthKey && t.visibleToUids.includes(uid))
    const isGenerated = monthTxs.some((t) => Boolean(t.recurringBillId))

    let projectedIncome = 0
    let projectedExpense = 0
    let incomeCount = 0
    let expenseCount = 0

    // 1. Sempre inclui transações reais agendadas para aquele mês (ex: parcelas de compras com cartão)
    for (const t of monthTxs) {
      const impact = impactFor(t, uid, all)
      if (t.type === 'income') {
        projectedIncome += impact
        incomeCount++
      } else {
        projectedExpense += impact
        expenseCount++
      }
    }

    // 2. Se as contas do mês ainda não foram geradas, projeta o salário e os moldes ativos válidos
    if (!isGenerated) {
      const hasSalary = monthTxs.some((t) => t.recurringBillId === `salary_${uid}`)
      if (!hasSalary && profile && profile.monthlyIncome > 0) {
        projectedIncome += profile.monthlyIncome
        incomeCount++
      }

      for (const bill of bills.filter((b) => b.active && b.ownerUid === uid)) {
        if (monthTxs.some((t) => t.recurringBillId === bill.id)) continue

        // Feature 1: Limite temporal de recorrência / parcelamento
        if (bill.startMonth && monthKey < bill.startMonth) continue
        if (bill.endMonth && monthKey > bill.endMonth) continue

        const { amount } = estimateRecurring(bill, all, monthKey)
        const myFraction = bill.splitType === 'split_50' ? round2(amount / 2) : amount
        if (bill.type === 'income') {
          projectedIncome += myFraction
          incomeCount++
        } else {
          projectedExpense += myFraction
          expenseCount++
        }
      }
    }

    projectedIncome = round2(projectedIncome)
    projectedExpense = round2(projectedExpense)
    const projectedFreeBalance = round2(projectedIncome - projectedExpense)
    const projectedDailyLimit = dim > 0 ? round2(projectedFreeBalance / dim) : null

    projections.push({
      monthKey,
      label,
      projectedIncome,
      projectedExpense,
      projectedFreeBalance,
      daysInMonth: dim,
      projectedDailyLimit,
      isGenerated,
      incomeCount,
      expenseCount,
    })
  }

  return projections
}

// ------------------------------------------------------------------
// Parceiro(a) e cartões
// ------------------------------------------------------------------
export function resolvePartner(meUid: string, cards: Card[], users: UserProfile[]): Party | null {
  for (const c of cards) {
    if (c.ownerUid === meUid && c.sharedWithUsers.length) {
      const s = c.sharedWithUsers[0]
      return { uid: s.uid, name: users.find((u) => u.uid === s.uid)?.displayName ?? s.name }
    }
    if (c.ownerUid !== meUid && c.sharedWithUids.includes(meUid)) {
      return { uid: c.ownerUid, name: users.find((u) => u.uid === c.ownerUid)?.displayName ?? c.ownerName }
    }
  }
  return null
}

export function accessibleCards(meUid: string, cards: Card[]) {
  return cards.filter((c) => c.ownerUid === meUid || c.sharedWithUids.includes(meUid))
}

export function isSharedCard(card: Card | undefined) {
  return !!card && card.sharedWithUsers.length > 0
}

/** Escolhe o cartão mais adequado para um lançamento. */
export function pickCard(meUid: string, cards: Card[], splitType: SplitType, hint?: string) {
  const mine = accessibleCards(meUid, cards)
  if (hint) {
    const h = normalize(hint)
    const byName = mine.find((c) => normalize(c.cardName).includes(h) || h.includes(normalize(c.cardName)))
    if (byName) return byName
    if (/parceir|namorad|dela|dele|marido|esposa/.test(h)) {
      const other = mine.find((c) => c.ownerUid !== meUid)
      if (other) return other
    }
  }
  const ownShared = mine.find((c) => c.ownerUid === meUid && c.sharedWithUsers.length > 0)
  const own = mine.find((c) => c.ownerUid === meUid)
  if (splitType === 'split_50' || splitType === 'partner') return ownShared ?? own ?? mine[0]
  return own ?? mine[0]
}

// ------------------------------------------------------------------
// Heurística de categoria & subcategoria inteligente
// ------------------------------------------------------------------
export interface CategoryGuess {
  category: Category
  subcategory?: string
}

const SUBCATEGORY_HINTS: [Category, string, RegExp][] = [
  // Alimentação
  ['Alimentação', 'Supermercado', /mercado|supermercado|assai|assaí|carrefour|pao de acucar|pão de açúcar|extra|atacadao|atacadão|dia%|hortifruti|feira|sacolao|sacolão/i],
  ['Alimentação', 'Delivery & Lanches', /ifood|rappi|uber eats|lanche|burger|hamburguer|mc donalds|mcdonalds|burguer king|bk |subway|habib|delivery|habbib/i],
  ['Alimentação', 'Restaurante', /restaurante|outback|madero|churrascaria|coco bambu|fogao|fogão|parrilla|cantina|pizzaria|sushi|almoço|almoco|jantar/i],
  ['Alimentação', 'Padaria & Café', /padaria|panificadora|cafe|café|starbucks|confeitaria|doceria|doce/i],
  ['Alimentação', 'Bares & Bebidas', /bar|cerveja|chopp|adega|pub|bebidas|vinho/i],

  // Transporte
  ['Transporte', 'Combustível', /gasolina|combustivel|combustível|etanol|posto|ipiranga|shell|br distribuidora|abastec/i],
  ['Transporte', 'Aplicativos (Uber/99)', /uber|99|indrive|cabify|pop|corrida/i],
  ['Transporte', 'Estacionamento & Pedágio', /estacionamento|pedagio|pedágio|sem parar|veloe|conectcar|taggy|estapar/i],
  ['Transporte', 'Transporte Público (Metrô/Ônibus)', /onibus|ônibus|metro|metrô|bilhete unico|cptm|sptrans/i],

  // Moradia
  ['Moradia', 'Aluguel & Condomínio', /aluguel|condominio|condomínio|locacao|locação|imobiliaria/i],
  ['Moradia', 'Energia Elétrica', /luz|energia|enel|cpfl|cemig|coelba|light|eletropaulo|energisa/i],
  ['Moradia', 'Água & Saneamento', /agua|água|sabesp|sanepar|copasa|cedae|caesb/i],
  ['Moradia', 'Internet & TV a Cabo', /internet|fibra|claro|vivo|oi |tim fibra|brisanet|net virtua/i],
  ['Moradia', 'Gás', /gas|gás|ultragaz|liquigas|comgas/i],

  // Saúde
  ['Saúde', 'Farmácia & Remédios', /farmacia|farmácia|drogaria|drogasil|droga raia|pacheco|sao paulo|são paulo|panvel|remedio|remédio|medicamento/i],
  ['Saúde', 'Consultas & Exames', /consulta|exame|laboratorio|laboratório|dentista|oftalmo|medico|médico|clinica|clínica/i],
  ['Saúde', 'Academia & Treinos', /academia|smartfit|smart fit|bluefit|bio ritmo|musculacao|musculação|pilates|crossfit|natacao|natação/i],

  // Assinaturas
  ['Assinaturas', 'Streaming de Vídeo (Netflix/Max/Disney)', /netflix|disney|hbo|max|prime video|amazon prime|youtube premium|globoplay|telecine/i],
  ['Assinaturas', 'Streaming de Música (Spotify/Apple)', /spotify|deezer|apple music|tidal|amazon music/i],
  ['Assinaturas', 'Softwares, IA & Cloud (iCloud/ChatGPT)', /chatgpt|openai|icloud|google one|google storage|github|adobe|canva|microsoft|dropbox/i],

  // Pets
  ['Pets', 'Ração & Petiscos', /pet|petz|cobasi|racao|ração|petisco/i],
  ['Pets', 'Veterinário & Medicamentos', /veterinario|veterinário|vacina pet/i],

  // Compras
  ['Compras', 'Presentes', /presente|presentes|lembrancinha|lembranca|lembrança|aniversario|aniversário|floricultura|flores|cesta|cacau show|kopenhagen|chocolates|brinquedo|ri happy|dia das maes|dia dos pais|dia dos namorados|natal/i],
  ['Compras', 'Roupas & Acessórios', /zara|renner|riachuelo|c&a|shein|roupa|vestuario|vestuário|acessorio|acessório|nike|adidas|lingerie|joia|bijuteria/i],
  ['Compras', 'Calçados', /calcado|calçado|tenis|tênis|sapato|sandalia|sandália|havaianas|arezzo|anacapri|vans|olympikus/i],
  ['Compras', 'Eletrônicos & Informática', /kabum|pichau|terabyte|apple|samsung|dell|mercado livre|amazon|shopee|magalu|celular|smartphone|notebook|computador/i],
  ['Compras', 'Casa & Decoração', /decoracao|decoração|cama|mesa|banho|tok&stok|tok e stok|etna|camicado|mobly|leroy|marabraz|utilidade|moveis|móveis/i],

  // Educação
  ['Educação', 'Cursos Online & Certificações', /curso|udemy|alura|hotmart/i],
  ['Educação', 'Livros & E-books', /livro|livraria|saraiva|leitura|kindle/i],

  // Lazer
  ['Lazer', 'Cinema, Teatro & Shows', /cinema|cinemark|ingresso|sympla|eventim|show|teatro/i],
  ['Lazer', 'Viagens & Hospedagem', /viagem|hotel|airbnb|booking|decolar|passagem aerea|latam|gol|azul/i],

  // Receita
  ['Receita', 'Salário Principal', /salario|salário|folha|pro-?labore|empresa/i],
  ['Receita', 'Pix Recebido', /pix recebido|ted recebida|deposito|depósito/i],

  // Estorno
  ['Estorno', 'Crédito na Fatura', /estorno|reembolso|devolucao|devolução|credito fatura|crédito fatura|cashback/i],
]

const CATEGORY_HINTS: [Category, RegExp][] = [
  ['Estorno', /estorno|reembolso|devolucao|devolução|credito fatura|crédito fatura|credito na fatura|credit voucher|cashback/i],
  ['Moradia', /aluguel|condom|luz|energia|agua|gas|internet|financiamento|iptu|casa|apartamento/i],
  ['Alimentação', /jantar|almoco|cafe|mercado|supermercado|ifood|restaurante|lanche|pizza|padaria|comida|feira/i],
  ['Transporte', /uber|99|gasolina|combustivel|onibus|metro|estacionamento|carro|pedagio/i],
  ['Saúde', /farmacia|remedio|medic|consulta|exame|dentista|academia|plano de saude|drogaria/i],
  ['Lazer', /cinema|show|viagem|bar|festa|passeio|jogo|livro|teatro/i],
  ['Assinaturas', /netflix|spotify|prime|disney|hbo|assinatura|youtube|icloud|google one/i],
  ['Educação', /curso|faculdade|escola|livro|udemy|alura/i],
  ['Compras', /roupa|tenis|shopping|eletronico|celular|magazine|presente|lembranc|brinquedo/i],
  ['Pets', /pet|racao|veterinario|cobasi/i],
]

export function guessCategoryAndSubcategory(text: string, amount?: number): CategoryGuess {
  if (typeof amount === 'number' && amount < 0) {
    return { category: 'Estorno', subcategory: 'Crédito na Fatura' }
  }
  const n = normalize(text)
  for (const [cat, sub, re] of SUBCATEGORY_HINTS) {
    if (re.test(n)) return { category: cat, subcategory: sub }
  }
  for (const [cat, re] of CATEGORY_HINTS) {
    if (re.test(n)) return { category: cat }
  }
  return { category: 'Outros' }
}

export function guessCategory(text: string, amount?: number): Category {
  return guessCategoryAndSubcategory(text, amount).category
}
