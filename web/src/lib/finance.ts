/**
 * Motor de regras financeiras (puro, sem dependências de UI/Firebase).
 * Todas as fórmulas dos KPIs, divisões (allocations/userImpact) e
 * estimativas de contas recorrentes vivem aqui.
 */
import type {
  AccountCashFlowSummary,
  Allocation,
  Card,
  Category,
  MonthClosure,
  MonthSummary,
  NewTransaction,
  ReceivableItem,
  RecurringBill,
  SplitType,
  Transaction,
  TransactionStatus,
  UserProfile,
  FutureMonthProjection,
  ProjectedCategoryItem,
  ProjectedMonthItem,
  LaunchTrigger,
  PendingRecurringCandidate,
  FinancialHealthScore,
  BudgetRebalanceSuggestion,
  CategoryBudgetBadgeLabel,
  CategoryBudgetPaceStatus,
  CategoryBudgetReport,
  CategoryBudgetStatus,
  BudgetInsight,
  BudgetInsightAction,
  SmartBudgetPlan,
  SmartBudgetAllocation,
} from '@/types'
import { INCOME_CATEGORIES, normalizeCategory } from '@/types'
import { currentMonthKey, dateInMonth, daysInMonth, formatBRL, monthLabel, normalize, round2, shiftDateMonth, shiftMonth, stripInstallmentSuffix, todayKey } from './utils'
import { buildCardInvoice } from './invoices'

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
  purchaseDate?: string | null,
  card?: Card | null,
  explicitDueDate?: string | null,
  explicitMonth?: string | null,
): { dueDate: string; competenceMonth: string } {
  const safePurchaseDate = typeof purchaseDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(purchaseDate.trim())
    ? purchaseDate.trim()
    : todayKey()

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
    const cleanMonth = safePurchaseDate.slice(0, 7)
    return { dueDate: safePurchaseDate, competenceMonth: cleanMonth }
  }

  const parts = safePurchaseDate.split('-')
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

/**
 * Calcula o valor de uma parcela específica (1-based index) absorvendo
 * a diferença residual de arredondamento de centavos na última parcela.
 */
export function calculateInstallmentAmount(
  total: number,
  installmentNumber: number,
  installmentsTotal: number,
  isPerInstallment: boolean = false
): number {
  if (installmentsTotal <= 1 || isPerInstallment) {
    return round2(total)
  }
  const perInstallment = round2(total / installmentsTotal)
  if (installmentNumber === installmentsTotal) {
    return round2(total - perInstallment * (installmentsTotal - 1))
  }
  return perInstallment
}

/**
 * Determina a lista de meses de competência para uma série de parcelas,
 * garantindo avanço determinístico estrito mês a mês a partir da 1ª parcela
 * e impedindo o colapso de faturas em meses com menos dias (ex: 31 de janeiro).
 */
export function calculateInstallmentCompetenceMonths(
  purchaseDate: string,
  card: Card | null | undefined,
  installmentsTotal: number,
  isCreditCard: boolean
): string[] {
  if (installmentsTotal <= 0) return []
  if (installmentsTotal === 1) {
    if (isCreditCard && card) {
      return [calculateCardInvoiceDates(purchaseDate, card).competenceMonth]
    }
    return [purchaseDate.slice(0, 7)]
  }

  const initialCompetenceMonth = isCreditCard && card
    ? calculateCardInvoiceDates(purchaseDate, card).competenceMonth
    : purchaseDate.slice(0, 7)

  const months: string[] = []
  for (let i = 1; i <= installmentsTotal; i++) {
    if (isCreditCard && card) {
      months.push(shiftMonth(initialCompetenceMonth, i - 1))
    } else {
      const curDate = shiftDateMonth(purchaseDate, i - 1)
      months.push(curDate.slice(0, 7))
    }
  }
  return months
}

/**
 * Fraciona alocações customizadas (rateio com amigos/participantes) para uma
 * parcela específica, absorvendo a diferença residual de centavos na última parcela.
 */
export function fractionCustomAllocations(
  allocations: Allocation[] | undefined,
  installmentNumber: number,
  installmentsTotal: number,
  isPerInstallment: boolean = false
): Allocation[] | undefined {
  if (!allocations || allocations.length === 0) return allocations
  if (installmentsTotal <= 1 || isPerInstallment) return allocations

  return allocations.map((a) => {
    const perInst = round2(a.amount / installmentsTotal)
    const partAmount = installmentNumber === installmentsTotal
      ? round2(a.amount - perInst * (installmentsTotal - 1))
      : perInst
    return { ...a, amount: partAmount }
  })
}


// ------------------------------------------------------------------
// D. Divisões: allocations → userImpact → visibleToUids
// ------------------------------------------------------------------
export function deriveImpact(allocations: Allocation[] = [], visibleToUids: string[] = []) {
  const impact: Record<string, number> = {}
  const rawVisible = Array.isArray(visibleToUids) ? visibleToUids : []
  for (const u of rawVisible) {
    if (u && typeof u === 'string') impact[u] = 0
  }
  const rawAllocs = Array.isArray(allocations)
    ? allocations
    : (allocations && typeof allocations === 'object'
        ? Object.values(allocations)
        : [])
  for (const a of (rawAllocs as any[])) {
    if (a && typeof a === 'object' && a.type === 'PLATFORM_USER' && a.uid) {
      impact[a.uid] = round2((impact[a.uid] ?? 0) + (Number(a.amount) || 0))
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

/** Valor que cabe ao amigo numa cobrança de amigo (fallback: cálculo por porcentagem ou total). */
export function friendShareOf(tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'>) {
  if (!tx || !tx.assignedFriendUid) return 0
  const rawAllocs = Array.isArray(tx.allocations)
    ? tx.allocations
    : (tx.allocations && typeof tx.allocations === 'object'
        ? Object.values(tx.allocations)
        : [])
  const allocs = rawAllocs.filter((a: any) => a && typeof a === 'object' && a.uid === tx.assignedFriendUid)
  if (allocs.length > 0) {
    return round2(allocs.reduce((s: number, a: any) => s + (Number(a.amount) || 0), 0))
  }
  // Fallback se não houver allocation explícita com o UID do amigo:
  const total = Number(tx.totalAmount) || 0
  const pct = tx.friendSharePct != null ? clampPct(tx.friendSharePct) : 50
  return round2((total * pct) / 100)
}

export function friendPctOf(tx: Pick<Transaction, 'assignedFriendUid' | 'allocations' | 'totalAmount' | 'friendSharePct'>) {
  if (!tx) return 100
  if (tx.friendSharePct != null) return clampPct(tx.friendSharePct)
  const total = Math.abs(Number(tx.totalAmount) || 0)
  if (!total) return 100
  return clampPct((Math.abs(friendShareOf(tx)) / total) * 100)
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
          Math.abs(myAmount) > 0.001
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
          const signedAmount = total < 0 ? -Math.abs(a.amount) : Math.abs(a.amount)
          const item: Allocation = {
            type: a.type,
            name: a.name.trim() || 'Participante',
            amount: round2(signedAmount),
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

/**
 * Reescala as divisões quando o valor efetivo muda ou quando um estorno é criado a partir de uma compra.
 * Garante proporcionalidade estrita: a soma das cotas de todos os participantes é EXATAMENTE 100% de newTotal.
 */
export function rescaleSplit(tx: Transaction, newTotal: number, viewerUid?: string) {
  const roundedTotal = round2(newTotal)
  const allocs = tx.allocations || []
  let allocations: Allocation[] = []

  // Caso 1: Já existem allocations com 2 ou mais participantes com soma condizente
  const allocSum = allocs.reduce((s, a) => s + (Number(a.amount) || 0), 0)
  const hasMultipleValidAllocs = allocs.length > 1 && Math.abs(allocSum) > 0.001

  if (hasMultipleValidAllocs) {
    const ratio = roundedTotal / allocSum
    let acc = 0
    allocations = allocs.map((a, i) => {
      if (i === allocs.length - 1) return { ...a, amount: round2(roundedTotal - acc) }
      const amount = round2(a.amount * ratio)
      acc += amount
      return { ...a, amount }
    })
  } else if (tx.assignedFriendUid || tx.splitType === 'friend') {
    // Caso 2: Divisão com Amigo (pode estar sem allocations completas ou com alocação legada de 100%)
    const pct = tx.friendSharePct != null ? clampPct(tx.friendSharePct) : 50
    const friendAmount = round2((roundedTotal * pct) / 100)
    const myAmount = round2(roundedTotal - friendAmount)
    const fName = tx.assignedFriendName?.trim() || 'Amigo'
    const myUid = viewerUid || tx.createdByUid || tx.paidByUid

    allocations = []
    if (Math.abs(myAmount) > 0.0001) {
      allocations.push({
        type: 'PLATFORM_USER',
        uid: myUid,
        name: 'Você',
        amount: myAmount,
        reimbursed: true,
      })
    }
    allocations.push({
      type: tx.assignedFriendUid ? 'PLATFORM_USER' : 'EXTERNAL',
      uid: tx.assignedFriendUid || undefined,
      name: fName,
      amount: friendAmount,
      reimbursed: false,
    })
  } else if (tx.splitType === 'split_50' || (tx.splitType as string) === 'SHARED_50_50') {
    // Caso 3: Divisão 50/50 (Casal / Parceiro)
    const half = round2(roundedTotal / 2)
    const otherHalf = round2(roundedTotal - half)
    const myUid = viewerUid || tx.createdByUid || tx.paidByUid
    const pName = tx.externalName?.trim() || 'Parceiro(a)'

    allocations = [
      {
        type: 'PLATFORM_USER',
        uid: myUid,
        name: 'Você',
        amount: half,
        reimbursed: true,
      },
      {
        type: 'EXTERNAL',
        name: pName,
        amount: otherHalf,
        reimbursed: false,
      },
    ]
  } else if (tx.splitType === 'partner') {
    // Caso 4: 100% Parceiro(a)
    allocations = [
      {
        type: 'EXTERNAL',
        name: tx.externalName?.trim() || 'Parceiro(a)',
        amount: roundedTotal,
        reimbursed: false,
      },
    ]
  } else if (tx.splitType === 'external' || tx.externalName) {
    // Caso 5: Terceiro Externo
    allocations = [
      {
        type: 'EXTERNAL',
        name: tx.externalName?.trim() || 'Terceiro',
        amount: roundedTotal,
        reimbursed: false,
      },
    ]
  } else if (allocs.length > 0) {
    // Caso 6: Allocations existentes (ex: 1 allocation de 100% Meu)
    const ratio = Math.abs(allocSum) > 0.001 ? roundedTotal / allocSum : 1
    let acc = 0
    allocations = allocs.map((a, i) => {
      if (i === allocs.length - 1) return { ...a, amount: round2(roundedTotal - acc) }
      const amount = round2(a.amount * ratio)
      acc += amount
      return { ...a, amount }
    })
  } else {
    // Caso 7: Padrão 100% Meu
    const myUid = viewerUid || tx.createdByUid || tx.paidByUid
    allocations = [
      {
        type: 'PLATFORM_USER',
        uid: myUid,
        name: 'Você',
        amount: roundedTotal,
        reimbursed: true,
      },
    ]
  }

  const visibleUids = new Set<string>(tx.visibleToUids || [])
  for (const a of allocations) {
    if (a.uid) visibleUids.add(a.uid)
  }

  return {
    totalAmount: roundedTotal,
    allocations,
    userImpact: deriveImpact(allocations, [...visibleUids]),
    visibleToUids: [...visibleUids],
  }
}

/**
 * Cria uma transação de estorno vinculada a uma compra existente no cartão de crédito.
 * Replica exatamente a categoria, subcategoria, cartão, participantes e proporções de rateio,
 * gerando valores negativos que abatem a fatura e o Saldo Livre.
 */
export function createRefundForTransaction(
  origTx: Transaction,
  refundAmount: number,
  currentUser: { uid: string; displayName?: string },
  todayIso: string = todayKey()
): Transaction {
  const absAmount = round2(Math.abs(refundAmount))
  const signedTotal = -absAmount

  const rescaled = rescaleSplit(origTx, signedTotal, currentUser.uid)
  const cleanDesc = origTx.description.replace(/^Estorno:\s*/i, '').trim()

  const friendAlloc = origTx.assignedFriendUid
    ? rescaled.allocations.find((a) => a.uid === origTx.assignedFriendUid)
    : null
  const friendSharePct = friendAlloc && Math.abs(signedTotal) > 0
    ? round2((Math.abs(friendAlloc.amount) / Math.abs(signedTotal)) * 100)
    : (origTx.friendSharePct != null ? clampPct(origTx.friendSharePct) : null)

  return {
    id: `tx_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    createdByUid: currentUser.uid,
    paidByUid: origTx.paidByUid || currentUser.uid,
    cardId: origTx.cardId,
    description: `Estorno: ${cleanDesc}`,
    category: origTx.category,
    subcategory: origTx.subcategory || 'Crédito na Fatura',
    type: 'expense',
    paymentMethod: origTx.paymentMethod || 'credit_card',
    status: 'pending',
    estimatedAmount: null,
    actualAmount: signedTotal,
    totalAmount: signedTotal,
    dueDate: todayIso || origTx.dueDate,
    competenceMonth: origTx.competenceMonth,
    visibleToUids: rescaled.visibleToUids || [currentUser.uid],
    userImpact: rescaled.userImpact,
    allocations: rescaled.allocations,
    splitType: origTx.splitType || (origTx.assignedFriendUid ? 'friend' : 'mine'),
    assignedFriendUid: origTx.assignedFriendUid,
    assignedFriendName: origTx.assignedFriendName,
    acceptanceStatus: origTx.assignedFriendUid ? 'pending_approval' : null,
    friendSharePct,
    externalName: origTx.externalName,
    refundedTransactionId: origTx.id,
    createdAt: Date.now(),
  }
}

/** Detecta o tipo de divisão do ponto de vista de quem está olhando. */
export function detectSplit(tx: Transaction, viewerUid: string): SplitType {
  const allocs = tx.allocations || []
  if (tx.splitType === 'custom') return 'custom'
  if (allocs.length > 2) return 'custom'
  if (tx.assignedFriendUid || tx.assignedFriendName || tx.targetFriendName || tx.splitType === 'friend') return 'friend'
  if (
    tx.thirdPartyName ||
    tx.externalName ||
    tx.splitType === 'external' ||
    (tx.splitType as string) === 'THIRD_PARTY' ||
    allocs.some((a) => a.type === 'EXTERNAL')
  ) {
    if (allocs.length <= 1) return 'external'
    return 'custom'
  }
  const users = allocs.filter((a) => a.type === 'PLATFORM_USER')
  const mine = users.some((a) => a.uid === viewerUid)
  const others = users.some((a) => a.uid !== viewerUid)
  if (mine && others) {
    const myAlloc = users.find((a) => a.uid === viewerUid)?.amount ?? 0
    const otherAlloc = users.find((a) => a.uid !== viewerUid)?.amount ?? 0
    if (Math.abs(myAlloc - otherAlloc) > 0.05) return 'custom'
    return 'split_50'
  }
  if (tx.splitType === 'split_50' || (tx.splitType as string) === 'SHARED_50_50') return 'split_50'
  if (tx.splitType === 'partner') return 'partner'
  if (mine) return 'mine'
  return 'partner'
}

/**
 * Determina se o usuário logado tem permissão para editar ou excluir a transação.
 * Um lançamento feito por outra pessoa NÃO pode ser editado nem excluído por quem
 * recebeu o rateio (amigo atribuído ou participante em rateio).
 */
export function canUserEditTransaction(tx: Transaction, currentUid?: string | null): boolean {
  if (!currentUid) return false

  // Se o usuário logado é o criador ou o pagador, tem permissão total
  const isCreator = tx.createdByUid === currentUid || (!tx.createdByUid && tx.paidByUid === currentUid)
  if (isCreator) return true

  // Quem recebeu o rateio como amigo atribuído NÃO pode editar
  if (tx.assignedFriendUid === currentUid) return false

  // Se o usuário faz parte das alocações mas não criou o lançamento
  if (tx.allocations?.some((a) => a.uid === currentUid)) return false

  // Se outra pessoa criou explicitamente, o usuário atual não pode editar
  if (tx.createdByUid && tx.createdByUid !== currentUid) return false

  return true
}

export const SPLIT_LABELS: Record<SplitType, { label: string; emoji: string; short: string }> = {
  mine: { label: '100% Meu', emoji: '👤', short: 'Meu' },
  split_50: { label: '50/50 (Casal)', emoji: '🤝', short: '50/50' },
  SHARED_50_50: { label: '50/50 (Casal)', emoji: '🤝', short: '50/50' },
  partner: { label: '100% Parceiro(a)', emoji: '🔄', short: 'Parceiro(a)' },
  friend: { label: '🧑‍🤝‍🧑 Amigo da Rede', emoji: '🧑‍🤝‍🧑', short: 'Amigo' },
  external: { label: 'Terceiro Externo', emoji: '🏷️', short: 'Terceiro' },
  THIRD_PARTY: { label: 'Terceiro Externo', emoji: '🏷️', short: 'Terceiro' },
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
  // 0. Nome de terceiro explícito
  const explicitThird = tx.thirdPartyName || tx.externalName
  if (explicitThird) {
    return {
      name: explicitThird,
      label: 'Terceiro',
      type: 'external',
    }
  }

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

  // 2. Amigo atribuído (assignedFriend ou targetFriendName)
  const explicitFriend = tx.assignedFriendName || tx.targetFriendName
  if (explicitFriend) {
    if (tx.assignedFriendUid === currentUid) {
      const sender = tx.paidByUid !== currentUid && partnerName ? partnerName : explicitFriend
      return {
        name: sender,
        label: 'Recebido de',
        type: 'sender',
      }
    }
    return {
      name: explicitFriend,
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
  if (tx.splitType === 'external' || (tx.splitType as string) === 'THIRD_PARTY') {
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

  if ((tx.splitType === 'split_50' || (tx.splitType as string) === 'SHARED_50_50') && partnerName) {
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

export interface TxPersonInvolvement {
  name: string
  role: 'me' | 'partner' | 'friend' | 'third_party'
  amount: number
}

/**
 * Extrai todos os participantes/responsáveis individuais presentes num lançamento
 * com seus respectivos valores para exibição e somatórios de cobrança.
 */
export function getTxPersons(
  tx: Transaction,
  currentUid: string,
  partnerName?: string,
  allTransactions: Transaction[] = [],
): TxPersonInvolvement[] {
  const result: TxPersonInvolvement[] = []
  const effAmount = Math.abs(effectiveAmount(tx))
  const myImpact = Math.abs(impactFor(tx, currentUid, allTransactions))

  const rawThirdPartyName = tx.thirdPartyName || tx.externalName
  const rawFriendName = tx.assignedFriendName || tx.targetFriendName

  // 1. Caso com allocations explícitas
  if (Array.isArray(tx.allocations) && tx.allocations.length > 0) {
    const isSoleUser = tx.allocations.length === 1 && !tx.assignedFriendUid
    for (const alloc of tx.allocations) {
      if (!alloc) continue
      const allocAmount = Math.abs(Number(alloc.amount) || 0)
      if (alloc.type === 'EXTERNAL') {
        const name = alloc.name || rawThirdPartyName || 'Terceiro'
        result.push({ name, role: 'third_party', amount: allocAmount })
      } else if (alloc.uid === currentUid || alloc.name?.toLowerCase() === 'você' || alloc.name?.toLowerCase() === 'meu') {
        const name = isSoleUser ? '100% Meu' : 'Você (Minha parte)'
        result.push({ name, role: 'me', amount: allocAmount })
      } else if (partnerName && (alloc.name === partnerName || alloc.uid === partnerName)) {
        result.push({ name: partnerName, role: 'partner', amount: allocAmount })
      } else {
        const name = alloc.name || rawFriendName || 'Amigo'
        result.push({ name, role: 'friend', amount: allocAmount })
      }
    }
  } else {
    // 2. Sem allocations explícitas - checa campos diretos
    if (rawThirdPartyName || tx.splitType === 'external' || (tx.splitType as string) === 'THIRD_PARTY') {
      const name = rawThirdPartyName || 'Terceiro Externo'
      result.push({ name, role: 'third_party', amount: effAmount })
      if (myImpact > 0 && myImpact < effAmount) {
        result.push({ name: 'Você (Minha parte)', role: 'me', amount: myImpact })
      }
    } else if (rawFriendName || tx.splitType === 'friend' || tx.assignedFriendUid) {
      const name = rawFriendName || 'Amigo'
      const friendAmt = Math.abs(friendShareOf(tx)) || round2(effAmount / 2)
      result.push({ name, role: 'friend', amount: friendAmt })
      const myPart = round2(Math.max(0, effAmount - friendAmt))
      if (myPart > 0) {
        result.push({ name: 'Você (Minha parte)', role: 'me', amount: myPart })
      }
    } else if (tx.splitType === 'split_50' || (tx.splitType as string) === 'SHARED_50_50') {
      const pName = partnerName || 'Parceiro(a)'
      const half = round2(effAmount / 2)
      result.push({ name: pName, role: 'partner', amount: half })
      result.push({ name: 'Você (Minha parte)', role: 'me', amount: round2(effAmount - half) })
    } else if (tx.splitType === 'partner') {
      const pName = partnerName || 'Parceiro(a)'
      result.push({ name: pName, role: 'partner', amount: effAmount })
    } else {
      // 100% Meu
      result.push({ name: '100% Meu', role: 'me', amount: effAmount })
    }
  }

  // Garantia absoluta: nenhuma linha fica sem responsável
  if (result.length === 0) {
    result.push({ name: '100% Meu', role: 'me', amount: effAmount })
  }

  return result
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
      const ownImpact = tx.userImpact?.[uid]
      if (typeof ownImpact === 'number') return ownImpact
      if (uid === tx.createdByUid || uid === tx.paidByUid) {
        return round2(effectiveAmount(tx) - friendShare)
      }
      return 0
    }
    // Pendente ou recusado: amigo ainda NÃO tem o saldo comprometido
    if (uid === tx.assignedFriendUid) return 0
    // Quem lançou tem apenas o seu próprio impacto (a cota do amigo é valor a receber em receivables)
    const ownImpact = tx.userImpact?.[uid]
    if (typeof ownImpact === 'number') return ownImpact
    return uid === tx.createdByUid || uid === tx.paidByUid
      ? round2(effectiveAmount(tx) - friendShare)
      : 0
  }

  const directImpact = tx.userImpact?.[uid]
  if (typeof directImpact === 'number') {
    return directImpact
  }

  // Fallbacks de paridade Web/Android para documentos legados sem userImpact explícito:
  if (Array.isArray(tx.allocations) && tx.allocations.length > 0) {
    const userAllocs = tx.allocations.filter(
      (a) =>
        a &&
        a.type === 'PLATFORM_USER' &&
        (a.uid === uid || a.name?.toLowerCase() === 'você' || a.name?.toLowerCase() === 'meu'),
    )
    if (userAllocs.length > 0) {
      return round2(userAllocs.reduce((s, a) => s + (Number(a.amount) || 0), 0))
    }
    return 0
  }

  if (tx.splitType === 'external' || (tx.splitType as string) === 'THIRD_PARTY') {
    return 0
  }

  if (tx.splitType === 'split_50' || (tx.splitType as string) === 'SHARED_50_50') {
    if ((tx.visibleToUids || []).includes(uid) || uid === tx.createdByUid || uid === tx.paidByUid) {
      return round2(effectiveAmount(tx) / 2)
    }
    return 0
  }

  if (tx.splitType === 'partner') {
    if (uid === tx.createdByUid || uid === tx.paidByUid) return 0
    if ((tx.visibleToUids || []).includes(uid)) return effectiveAmount(tx)
    return 0
  }

  if (tx.splitType === 'mine') {
    if (uid === tx.createdByUid || uid === tx.paidByUid) return effectiveAmount(tx)
    return 0
  }

  return uid === tx.createdByUid || uid === tx.paidByUid ? effectiveAmount(tx) : 0
}

/**
 * Verifica se a transação representa um gasto do usuário (impacta o Saldo Livre ou tem sua participação).
 * Inclui gastos 100% pessoais e lançamentos rateados (50/50, amigos, divisões personalizadas).
 */
export function isMyExpenseTx(tx: Transaction, uid: string, all: Transaction[] = []): boolean {
  if (tx.type === 'income') return false

  const myImp = Math.abs(impactFor(tx, uid, all))
  if (myImp > 0.001) return true

  // Alocação explícita onde o usuário tem valor > 0
  if (
    tx.allocations &&
    tx.allocations.some(
      (a) => (a.uid === uid || a.name?.toLowerCase() === 'você' || a.name?.toLowerCase() === 'meu' || a.name?.toLowerCase() === '100% meu') && Number(a.amount) > 0,
    )
  ) {
    return true
  }

  // Divisão 50/50 que envolve o usuário
  if (tx.splitType === 'split_50' || (tx.splitType as string) === 'SHARED_50_50') {
    if (!tx.visibleToUids || tx.visibleToUids.includes(uid) || tx.createdByUid === uid || tx.paidByUid === uid) {
      return true
    }
  }

  // 100% Meu
  if (tx.splitType === 'mine') {
    if (!tx.visibleToUids || tx.visibleToUids.includes(uid) || tx.createdByUid === uid || tx.paidByUid === uid) {
      return true
    }
  }

  // Amigo onde o usuário tem cota
  if (tx.splitType === 'friend' || tx.assignedFriendUid) {
    if (uid === tx.assignedFriendUid && tx.acceptanceStatus === 'accepted') return true
    if ((tx.createdByUid === uid || tx.paidByUid === uid) && friendShareOf(tx) < Math.abs(effectiveAmount(tx))) {
      return true
    }
  }

  // Criado ou pago pelo usuário (desde que não seja 100% do parceiro ou terceiro puro)
  if (tx.createdByUid === uid || tx.paidByUid === uid) {
    if (tx.splitType === 'partner') return false
    if (tx.splitType === 'external' || (tx.splitType as string) === 'THIRD_PARTY') return false
    return true
  }

  return false
}

/**
 * Verifica se a transação é 100% exclusiva do usuário (sem divisão com parceiro, amigos ou terceiros).
 */
export function isPureMineTx(tx: Transaction, uid: string, all: Transaction[] = []): boolean {
  if (!isMyExpenseTx(tx, uid, all)) return false

  if (tx.splitType === 'split_50' || (tx.splitType as string) === 'SHARED_50_50') return false
  if (tx.splitType === 'partner') return false
  if (tx.assignedFriendUid || tx.assignedFriendName || tx.targetFriendName || tx.splitType === 'friend') return false
  if (tx.thirdPartyName || tx.externalName || tx.splitType === 'external' || (tx.splitType as string) === 'THIRD_PARTY') return false

  if (tx.allocations && tx.allocations.length > 1) {
    const hasOthers = tx.allocations.some(
      (a) =>
        a.uid !== uid &&
        a.name?.toLowerCase() !== 'você' &&
        a.name?.toLowerCase() !== 'meu' &&
        a.name?.toLowerCase() !== '100% meu' &&
        Number(a.amount) > 0,
    )
    if (hasOthers) return false
  }

  const myImp = Math.abs(impactFor(tx, uid, all))
  const eff = Math.abs(effectiveAmount(tx))
  if (eff > 0 && Math.abs(myImp - eff) > 0.05) return false

  return true
}

/**
 * Verifica se a transação é um gasto do usuário que foi rateado/compartilhado com parceiro, amigos ou terceiros.
 */
export function isSharedWithMeTx(tx: Transaction, uid: string, all: Transaction[] = []): boolean {
  return isMyExpenseTx(tx, uid, all) && !isPureMineTx(tx, uid, all)
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

export function computeMonthRawBalance(all: Transaction[], uid: string, monthKey: string): number {
  const monthTxs = (all || []).filter((t) => t.competenceMonth === monthKey && (t.visibleToUids || []).includes(uid))
  let inc = 0
  let exp = 0
  for (const tx of monthTxs) {
    const impact = impactFor(tx, uid, all)
    if (tx.type === 'income') {
      inc += Math.abs(impact)
    } else {
      exp += impact
    }
  }
  return round2(inc - exp)
}

export function computeSummary(
  all: Transaction[],
  uid: string,
  monthKey: string,
  cards: Card[] = [],
  closures: MonthClosure[] = []
): MonthSummary {
  const monthTxs = (all || []).filter((t) => t.competenceMonth === monthKey && (t.visibleToUids || []).includes(uid))
  let income = 0
  let paid = 0
  let committedEstimated = 0
  let committedPending = 0
  let receivableFromPartner = 0
  let receivableFromExternal = 0
  let receivableFromFriends = 0
  let payableToOthers = 0
  let totalInvested = 0
  let grossInvested = 0
  const cat = new Map<string, number>()
  const subcat = new Map<string, { category: string; subcategory: string; amount: number; count: number }>()
  const investSubcat = new Map<string, number>()

  const cardMap = new Map<string, Card>()
  for (const c of cards || []) {
    if (c?.id) cardMap.set(c.id, c)
  }

  const nonCardTxs = monthTxs.filter((t) => !t.cardId || !cardMap.has(t.cardId))

  // 1. Não-cartões (Pix, Boleto, Salário, etc.)
  for (const tx of nonCardTxs) {
    const impact = impactFor(tx, uid, all)
    if (tx.type === 'income') {
      income += Math.abs(impact)
      continue
    }
    if (tx.status === 'paid') paid += impact
    else if (tx.status === 'estimated') committedEstimated += impact
    else committedPending += impact
  }

  // 2. Faturas de Cartão (comprometimento consolidado exato: myShare abatido de estornos/créditos)
  const processedCardIds = new Set<string>()
  const monthCardIds = Array.from(
    new Set([...monthTxs.map((t) => t.cardId).filter(Boolean), ...(cards || []).map((c) => c.id)])
  ) as string[]
  for (const cardId of monthCardIds) {
    const card = cardMap.get(cardId)
    if (!card || processedCardIds.has(cardId)) continue
    processedCardIds.add(cardId)

    const invoice = buildCardInvoice(card, monthKey, all, uid)
    const myShare = invoice.myShare
    if (myShare > 0) {
      const paidRatio = invoice.total > 0 ? Math.min(1, invoice.paidAmount / invoice.total) : 0
      const paidPart = invoice.status === 'paid' ? myShare : round2(Math.min(myShare, paidRatio * myShare))
      const openPart = round2(Math.max(0, myShare - paidPart))

      paid += paidPart
      if (invoice.status === 'open') {
        committedEstimated += openPart
      } else {
        committedPending += openPart
      }
    }
  }

  // 3. Categorias, Investimentos e A Receber / A Pagar (processa todas as despesas do mês)
  for (const tx of monthTxs) {
    const impact = impactFor(tx, uid, all)
    if (tx.type === 'expense' && impact > 0) {
      cat.set(tx.category, (cat.get(tx.category) ?? 0) + impact)

      const isAporte = tx.category === 'Investimentos' || /investimento/i.test(tx.category)
      if (isAporte) {
        grossInvested += impact
        totalInvested += impact
        const subName = tx.subcategory || 'Outros Investimentos'
        investSubcat.set(subName, (investSubcat.get(subName) ?? 0) + impact)
        if (tx.category !== 'Investimentos') {
          cat.set('Investimentos', (cat.get('Investimentos') ?? 0) + impact)
        }
      }

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
    } else if (tx.type === 'income' && impact > 0) {
      // Abate resgates de investimentos do total investido no mês
      const isResgate =
        tx.category === 'Investimentos' ||
        /investimento/i.test(tx.category) ||
        (tx.category === 'Rendimentos & Investimentos' && Boolean(tx.subcategory && /resgate/i.test(tx.subcategory)))
      if (isResgate) {
        totalInvested -= impact
        const subName = tx.subcategory || 'Outros Investimentos'
        investSubcat.set(subName, (investSubcat.get(subName) ?? 0) - impact)
      }
    }

    const rawAllocs = Array.isArray(tx.allocations)
      ? tx.allocations
      : (tx.allocations && typeof tx.allocations === 'object'
          ? Object.values(tx.allocations)
          : [])
    const allocs: any[] = (rawAllocs as any[]).filter((a: any) => a && typeof a === 'object')
    const friendAlloc = tx.assignedFriendUid ? allocs.find((a: any) => a.uid === tx.assignedFriendUid) : undefined
    const friendOpen = tx.acceptanceStatus === 'accepted' && !(friendAlloc?.reimbursed ?? false)

    if (tx.paidByUid === uid) {
      if (tx.assignedFriendUid && tx.assignedFriendUid !== uid && friendOpen) {
        receivableFromFriends += friendShareOf(tx)
      }
      for (const a of allocs) {
        if (a.reimbursed) continue
        if (a.type === 'EXTERNAL') receivableFromExternal += (Number(a.amount) || 0)
        else if (a.uid !== uid && a.uid !== tx.assignedFriendUid) receivableFromPartner += (Number(a.amount) || 0)
      }
    } else {
      if (tx.assignedFriendUid === uid && friendOpen) {
        payableToOthers += friendShareOf(tx)
      }
      for (const a of allocs) {
        if (a.type === 'PLATFORM_USER' && a.uid === uid && !a.reimbursed && !tx.assignedFriendUid) payableToOthers += (Number(a.amount) || 0)
      }
    }
  }

  // Total acumulado investido no histórico do usuário (aportes - resgates)
  let totalInvestedAccumulated = 0
  for (const tx of all || []) {
    if ((tx.visibleToUids || []).includes(uid)) {
      const isAporte = (tx.category === 'Investimentos' || /investimento/i.test(tx.category)) && tx.type === 'expense'
      const isResgate =
        ((tx.category === 'Investimentos' || /investimento/i.test(tx.category)) && tx.type === 'income') ||
        (tx.category === 'Rendimentos & Investimentos' && Boolean(tx.subcategory && /resgate/i.test(tx.subcategory)))
      const imp = impactFor(tx, uid, all)
      if (isAporte && imp > 0) {
        totalInvestedAccumulated += imp
      } else if (isResgate && imp > 0) {
        totalInvestedAccumulated -= imp
      }
    }
  }
  totalInvestedAccumulated = Math.max(0, round2(totalInvestedAccumulated))

  const committed = committedEstimated + committedPending
  const rawFreeBalance = round2(income - (paid + committed))
  const state = getMonthState(monthKey)

  let carryoverAmount = 0
  let previousMonthDeficit = 0
  let cushionAmount = 0
  let pendingClosureSurplus: number | null = null
  let pendingClosureMonth: string | null = null
  let prevClosureObj: MonthClosure | null = null

  const prevMonthKey = shiftMonth(monthKey, -1)
  const prevClosure = (closures || []).find(
    (c) => (c.closedMonth === prevMonthKey || c.targetMonth === monthKey) && c.uid === uid,
  )

  if (prevClosure) {
    prevClosureObj = prevClosure
    carryoverAmount = round2(prevClosure.carryoverToNextMonth || 0)
    cushionAmount = round2(prevClosure.cushionAmount || 0)
  } else {
    const pastMonths = Array.from(
      new Set(
        (all || [])
          .filter((t) => (t.visibleToUids || []).includes(uid) && t.competenceMonth < monthKey)
          .map((t) => t.competenceMonth)
      )
    ).sort()

    if (pastMonths.length > 0) {
        const startMonth = pastMonths[0]
        const endMonth = prevMonthKey
        let runningBalance = 0
        let currentIter = startMonth

        while (currentIter <= endMonth) {
          const closure = (closures || []).find((c) => c.closedMonth === currentIter && c.uid === uid)
          const raw = computeMonthRawBalance(all, uid, currentIter)
          const finalBalance = runningBalance + raw

          if (closure) {
            runningBalance = closure.carryoverToNextMonth
          } else {
            runningBalance = finalBalance
          }

          currentIter = shiftMonth(currentIter, 1)
        }

        if (runningBalance < 0) {
          previousMonthDeficit = round2(Math.abs(runningBalance))
        } else if (runningBalance > 0 && state === 'current') {
          pendingClosureSurplus = round2(runningBalance)
          pendingClosureMonth = prevMonthKey
        }
      }
    }

  const effectiveAvailable = income + carryoverAmount - previousMonthDeficit
  const freeBalance = round2(effectiveAvailable - (paid + committed))
  const daysRemaining = getDaysRemaining(monthKey)
  const thisMonthClosure = (closures || []).find((c) => c.closedMonth === monthKey && c.uid === uid) || null

  return {
    income: round2(income),
    paid: round2(paid),
    committed: round2(committed),
    committedEstimated: round2(committedEstimated),
    committedPending: round2(committedPending),
    freeBalance: round2(freeBalance),
    rawFreeBalance,
    cushionAmount,
    carryoverAmount,
    previousMonthDeficit,
    pendingClosureSurplus,
    pendingClosureMonth,
    closure: thisMonthClosure,
    previousMonthClosure: prevClosureObj,
    daysRemaining,
    daysInMonth: daysInMonth(monthKey),
    dailyLimit: daysRemaining > 0 ? round2(freeBalance / daysRemaining) : null,
    monthState: state,
    receivableFromPartner: round2(receivableFromPartner),
    receivableFromExternal: round2(receivableFromExternal),
    receivableFromFriends: round2(receivableFromFriends),
    receivableTotal: round2(receivableFromPartner + receivableFromExternal + receivableFromFriends),
    payableToOthers: round2(payableToOthers),
    totalInvested: round2(Math.max(grossInvested, totalInvested)),
    totalInvestedAccumulated: round2(totalInvestedAccumulated),
    byInvestmentSubcategory: [...investSubcat.entries()]
      .map(([subcategory, amount]) => ({
        subcategory,
        amount: round2(amount),
        percentage: totalInvested > 0 ? Math.round((amount / totalInvested) * 100) : 0,
      }))
      .sort((a, b) => b.amount - a.amount),
    byCategory: [...cat.entries()]
      .map(([category, amount]) => ({ category, amount: round2(amount) }))
      .sort((a, b) => b.amount - a.amount),
    bySubcategory: [...subcat.values()]
      .map((item) => ({ ...item, amount: round2(item.amount) }))
      .sort((a, b) => b.amount - a.amount),
  }
}

/**
 * Calcula o fluxo de caixa concreto da conta corrente / extrato bancário até a data de hoje,
 * distinguindo com rigor o saldo imediatamente disponível em conta do Saldo Livre Projetado do mês.
 */
export function computeAccountCashFlow(
  all: Transaction[],
  uid: string,
  monthKey: string,
  summary: MonthSummary,
  cards: Card[] = [],
  today: string = todayKey(),
): AccountCashFlowSummary {
  const prevMonth = shiftMonth(monthKey, -1)
  const prevMonthLabel = monthLabel(prevMonth)

  const previousMonthsSurplus = (summary.carryoverAmount ?? 0) > 0
    ? (summary.carryoverAmount ?? 0)
    : round2((summary.pendingClosureSurplus ?? 0) - (summary.previousMonthDeficit ?? 0))

  const monthTxs = (all || []).filter((t) => t.competenceMonth === monthKey && (t.visibleToUids || []).includes(uid))

  // Apenas transações da conta bancária / extrato (exclui compras feitas em cartão de crédito)
  const accountTxs = monthTxs.filter((t) => !t.cardId && t.paymentMethod !== 'credit_card')

  const accountIncomesReceived = round2(
    accountTxs
      .filter((t) => t.type === 'income' && t.status === 'paid' && t.dueDate <= today)
      .reduce((sum, t) => sum + Math.abs(impactFor(t, uid, all)), 0),
  )

  const accountExpensesPaid = round2(
    accountTxs
      .filter((t) => t.type === 'expense' && t.status === 'paid' && t.dueDate <= today)
      .reduce((sum, t) => sum + Math.abs(impactFor(t, uid, all)), 0),
  )

  const paidInvoicesAmount = round2(
    (cards || []).reduce((sum, card) => {
      const invoice = buildCardInvoice(card, monthKey, all, uid, today)
      if (invoice.status === 'paid' && invoice.dueDate <= today) {
        return sum + invoice.myShare
      }
      return sum
    }, 0),
  )

  const totalOutflowSoFar = round2(accountExpensesPaid + paidInvoicesAmount)
  const currentBankBalance = round2(previousMonthsSurplus + accountIncomesReceived - totalOutflowSoFar)

  const futureIncomesRemaining = round2(Math.max(0, summary.income - accountIncomesReceived))
  const futureCommittedRemaining = round2(Math.max(0, summary.committed - totalOutflowSoFar))

  return {
    previousMonthsSurplus,
    previousMonthLabel: prevMonthLabel,
    accountIncomesReceived,
    accountExpensesPaid,
    paidInvoicesAmount,
    totalOutflowSoFar,
    currentBankBalance,
    futureIncomesRemaining,
    futureCommittedRemaining,
    projectedFreeBalance: summary.freeBalance,
    dailyLimit: summary.dailyLimit,
  }
}

export function listReceivables(all: Transaction[], uid: string, monthKey: string, cards: Card[]) {
  const items: ReceivableItem[] = []
  const payables: ReceivableItem[] = []
  for (const tx of all || []) {
    if (tx.competenceMonth !== monthKey || tx.type !== 'expense' || !(tx.visibleToUids || []).includes(uid)) continue
    const cardName = cards?.find((c) => c.id === tx.cardId)?.cardName

    const allocs = tx.allocations || []
    // Amigo atribuído no cartão
    if (tx.assignedFriendUid) {
      const idx = Math.max(0, allocs.findIndex((a) => a.uid === tx.assignedFriendUid))
      if (allocs[idx]?.reimbursed && allocs[idx]?.uid === tx.assignedFriendUid) continue
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

    allocs.forEach((a, i) => {
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

export function computeLaunchDate(dueDay: number, monthKey: string, trigger: LaunchTrigger = 'day_before'): string {
  const [yearStr, monthStr] = monthKey.split('-')
  const year = parseInt(yearStr, 10)
  const month = parseInt(monthStr, 10)
  const dim = daysInMonth(monthKey)
  const clampedDueDay = Math.min(Math.max(1, dueDay), dim)

  if (trigger === 'first_day_of_month') {
    return `${monthKey}-01`
  }

  const dateObj = new Date(year, month - 1, clampedDueDay)

  if (trigger === 'day_before') {
    dateObj.setDate(dateObj.getDate() - 1)
  } else if (trigger === 'day_after') {
    dateObj.setDate(dateObj.getDate() + 1)
  }

  const y = dateObj.getFullYear()
  const m = String(dateObj.getMonth() + 1).padStart(2, '0')
  const d = String(dateObj.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function normalizeText(str: string): string {
  return str
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
}

export function findMatchingManualSalary(
  allTxs: Transaction[],
  monthKey: string,
  expectedAmount?: number,
): Transaction | null {
  const salaryWords = ['salario', 'remuneracao', 'holerite', 'pro labore', 'pro-labore', 'renda fixa', 'salario base']
  const monthIncomes = (allTxs || []).filter((t) => t.competenceMonth === monthKey && t.type === 'income')

  // 1. Verifica se já possui recurringBillId iniciando com salary_
  const explicit = monthIncomes.find((t) => t.recurringBillId?.startsWith('salary_'))
  if (explicit) return explicit

  // 2. Busca por descrição contendo palavras-chave de salário
  const descMatch = monthIncomes.find((t) => {
    const desc = normalizeText(t.description)
    return salaryWords.some((word) => desc.includes(word))
  })
  if (descMatch) return descMatch

  // 3. Busca por valor exato se expectedAmount estiver definido
  if (expectedAmount && expectedAmount > 0) {
    const amountMatch = monthIncomes.find((t) => Math.abs(t.totalAmount - expectedAmount) < 0.01)
    if (amountMatch) return amountMatch
  }

  return null
}

export function findMatchingManualBill(
  allTxs: Transaction[],
  monthKey: string,
  bill: RecurringBill,
): Transaction | null {
  const normBill = normalizeText(bill.description)
  const targetType = bill.type ?? 'expense'
  const monthTxs = (allTxs || []).filter((t) => t.competenceMonth === monthKey && t.type === targetType)

  // 1. Match explícito
  const explicit = monthTxs.find((t) => t.recurringBillId === bill.id)
  if (explicit) return explicit

  // 2. Similaridade na descrição (evitando falso-positivo para strings curtas)
  const descMatch = monthTxs.find((t) => {
    const txDesc = normalizeText(t.description)
    if (!txDesc || !normBill) return false
    if (txDesc === normBill) return true
    if (normBill.length >= 4 && txDesc.includes(normBill)) return true
    if (txDesc.length >= 4 && normBill.includes(txDesc)) return true
    return false
  })
  return descMatch || null
}

export function getPendingRecurringLaunches(args: {
  bills: RecurringBill[]
  all: Transaction[]
  monthKey: string
  todayStr: string
  me: Party
  profile: UserProfile | null
  resolvedIds?: string[]
}): PendingRecurringCandidate[] {
  const { bills, all, monthKey, todayStr, me, profile, resolvedIds = [] } = args
  const resolvedSet = new Set(resolvedIds)
  const candidates: PendingRecurringCandidate[] = []

  // 1. Salário Base (se profile configurado)
  if (profile && profile.monthlyIncome > 0) {
    const salaryId = `salary_${me.uid}`
    const alreadyResolved = resolvedSet.has(salaryId)
    const hasExplicitTx = (all || []).some((t) => t.recurringBillId === salaryId && t.competenceMonth === monthKey)

    if (!alreadyResolved && !hasExplicitTx) {
      const salaryLaunchDate = `${monthKey}-01`
      if (todayStr >= salaryLaunchDate) {
        const matched = findMatchingManualSalary(all, monthKey, profile.monthlyIncome)
        // Se ainda não foi explicitamente vinculado
        if (!matched || matched.recurringBillId !== salaryId) {
          candidates.push({
            id: salaryId,
            isSalary: true,
            description: 'Salário Base',
            category: 'Receita',
            subcategory: null,
            amount: profile.monthlyIncome,
            type: 'income',
            paymentMethod: 'pix',
            cardId: null,
            dueDay: 5,
            dueDate: dateInMonth(monthKey, 5),
            launchDate: salaryLaunchDate,
            matchedManualTx: matched,
          })
        }
      }
    }
  }

  // 2. Contas Fixas e Assinaturas ativas do usuário
  const activeBills = (bills || []).filter((b) => b.active && b.ownerUid === me.uid)
  for (const bill of activeBills) {
    if (bill.startMonth && monthKey < bill.startMonth) continue
    if (bill.endMonth && monthKey > bill.endMonth) continue

    if (resolvedSet.has(bill.id)) continue

    const hasExplicitTx = (all || []).some((t) => t.recurringBillId === bill.id && t.competenceMonth === monthKey)
    if (hasExplicitTx) continue

    const trigger = bill.launchTrigger || 'day_before'
    const launchDate = computeLaunchDate(bill.dueDay, monthKey, trigger)

    if (todayStr >= launchDate) {
      const { amount } = estimateRecurring(bill, all, monthKey)
      const matched = findMatchingManualBill(all, monthKey, bill)
      if (!matched || matched.recurringBillId !== bill.id) {
        candidates.push({
          id: bill.id,
          bill,
          isSalary: false,
          description: bill.description,
          category: bill.category,
          subcategory: bill.subcategory || null,
          amount,
          type: bill.type ?? 'expense',
          paymentMethod: bill.paymentMethod ?? (bill.type === 'income' ? 'pix' : bill.cardId ? 'credit_card' : 'bill'),
          cardId: bill.cardId || null,
          dueDay: bill.dueDay,
          dueDate: dateInMonth(monthKey, bill.dueDay),
          launchDate,
          matchedManualTx: matched,
        })
      }
    }
  }

  return candidates
}

export interface AutoLaunchResult {
  created: NewTransaction[]
  skipped: number
  launchedBillIds: string[]
}

export function autoLaunchDueBills(args: {
  bills: RecurringBill[]
  all: Transaction[]
  monthKey: string
  todayStr: string
  me: Party
  partner: Party | null
  profile: UserProfile | null
  resolvedIds?: string[]
}): AutoLaunchResult {
  const { bills, all, monthKey, todayStr, me, partner, profile, resolvedIds = [] } = args
  const candidates = getPendingRecurringLaunches({
    bills,
    all,
    monthKey,
    todayStr,
    me,
    profile,
    resolvedIds,
  })

  const created: NewTransaction[] = []
  const launchedBillIds: string[] = []

  for (const cand of candidates) {
    // IMPORTANTE: Se encontrou um lançamento manual similar (especialmente salário ou conta existente),
    // NUNCA cria silenciosamente uma duplicidade!
    if (cand.matchedManualTx) {
      continue
    }

    const split = buildSplit({
      splitType: cand.bill?.splitType ?? 'mine',
      total: cand.amount,
      me,
      partner: cand.isSalary ? null : partner,
      payerUid: me.uid,
    })

    created.push({
      createdByUid: me.uid,
      paidByUid: me.uid,
      cardId: cand.cardId || null,
      description: cand.description,
      category: cand.category,
      subcategory: cand.subcategory || null,
      type: cand.type,
      paymentMethod: cand.paymentMethod,
      status: 'estimated',
      estimatedAmount: cand.amount,
      actualAmount: null,
      totalAmount: cand.amount,
      dueDate: cand.dueDate,
      competenceMonth: monthKey,
      recurringBillId: cand.id,
      allocations: split.allocations,
      userImpact: split.userImpact,
      visibleToUids: split.visibleToUids,
      createdAt: Date.now(),
    })
    launchedBillIds.push(cand.id)
  }

  const skipped = bills.length - launchedBillIds.length

  return { created, skipped, launchedBillIds }
}

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
        description: 'Salário Base',
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
    // Limite de meses da recorrência/parcelas
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
    const effPaymentMethod = bill.paymentMethod ?? (isIncome ? 'pix' : bill.cardId ? 'credit_card' : 'bill')
    const isCardBill = effPaymentMethod === 'credit_card' && Boolean(bill.cardId)
    created.push({
      createdByUid: me.uid,
      paidByUid: me.uid,
      cardId: bill.cardId || null,
      description: bill.description,
      category: isIncome ? 'Receita' : bill.category,
      subcategory: bill.subcategory || null,
      type: bill.type ?? 'expense',
      paymentMethod: effPaymentMethod,
      status: isCardBill ? 'pending' : 'estimated',
      estimatedAmount: amount,
      actualAmount: isCardBill ? amount : null,
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
  cards?: Card[]
  closures?: MonthClosure[]
}): FutureMonthProjection[] {
  const { all, bills, profile, uid, startMonthKey, count = 12, cards = [], closures = [] } = args
  const projections: FutureMonthProjection[] = []
  const cardsMap = new Map(cards.map((c) => [c.id, c]))

  for (let i = 0; i < count; i++) {
    const monthKey = shiftMonth(startMonthKey, i)
    const prevMonthKey = shiftMonth(monthKey, -1)
    const label = monthLabel(monthKey)
    const dim = daysInMonth(monthKey)

    let carryoverAmount = 0
    let previousMonthDeficit = 0

    // Verifica se houve fechamento prévio com injeção de sobra no saldo livre
    const prevClosure = closures.find(
      (c) => (c.closedMonth === prevMonthKey || c.targetMonth === monthKey) && c.uid === uid,
    )

    if (prevClosure) {
      carryoverAmount = round2(prevClosure.carryoverToNextMonth || 0)
    } else if (i === 0) {
      // Se não há fechamento explícito do mês anterior e estamos no primeiro mês da projeção,
      // calcula eventual déficit herdado de meses anteriores
      const pastMonths = Array.from(
        new Set(
          (all || [])
            .filter((t) => (t.visibleToUids || []).includes(uid) && t.competenceMonth < monthKey)
            .map((t) => t.competenceMonth),
        ),
      ).sort()

      if (pastMonths.length > 0) {
        let runningBalance = 0
        let currentIter = pastMonths[0]
        while (currentIter <= prevMonthKey) {
          const closure = closures.find(
            (c) => (c.closedMonth === currentIter || c.targetMonth === shiftMonth(currentIter, 1)) && c.uid === uid,
          )
          const raw = computeMonthRawBalance(all, uid, currentIter)
          if (closure) {
            runningBalance = closure.carryoverToNextMonth
          } else {
            runningBalance = runningBalance + raw
          }
          currentIter = shiftMonth(currentIter, 1)
        }
        if (runningBalance < 0) {
          previousMonthDeficit = round2(Math.abs(runningBalance))
        }
      }
    } else if ((projections[i - 1]?.projectedFreeBalance ?? 0) < 0) {
      previousMonthDeficit = round2(Math.abs(projections[i - 1].projectedFreeBalance))
    }

    // Check existing transactions for that month visible to uid
    const monthTxs = (all || []).filter(
      (t) => t.competenceMonth === monthKey && (t.visibleToUids || []).includes(uid) && !t.reserveCardId,
    )

    let projectedIncome = 0
    let projectedExpense = 0
    let invoiceExpense = 0
    let fixedBillsExpense = 0
    let variableAccountExpense = 0
    let incomeCount = 0
    let expenseCount = 0

    const catMap = new Map<
      string,
      { amount: number; invoiceAmount: number; accountAmount: number; fixedBillAmount: number; count: number }
    >()
    const items: ProjectedMonthItem[] = []

    const addCat = (
      category: string,
      impact: number,
      bucket: 'invoice' | 'account' | 'fixed_bill',
    ) => {
      if (impact <= 0) return
      const cur = catMap.get(category) ?? {
        amount: 0,
        invoiceAmount: 0,
        accountAmount: 0,
        fixedBillAmount: 0,
        count: 0,
      }
      cur.amount = round2(cur.amount + impact)
      if (bucket === 'invoice') cur.invoiceAmount = round2(cur.invoiceAmount + impact)
      else if (bucket === 'fixed_bill') cur.fixedBillAmount = round2(cur.fixedBillAmount + impact)
      else cur.accountAmount = round2(cur.accountAmount + impact)
      cur.count += 1
      catMap.set(category, cur)
    }

    // 1. Transações reais já lançadas/agendadas para aquele mês (parcelas de cartão, compras na fatura, Pix/débito/boleto, contas já geradas)
    for (const t of monthTxs) {
      const impact = impactFor(t, uid, all)
      const isCard = t.paymentMethod === 'credit_card' || Boolean(t.cardId)
      const cardObj = t.cardId ? cardsMap.get(t.cardId) : null

      if (t.type === 'income') {
        if (isCard) {
          // Estornos/créditos no cartão reduzem a fatura
          projectedExpense = Math.max(0, projectedExpense - Math.abs(impact))
          invoiceExpense = Math.max(0, invoiceExpense - Math.abs(impact))
        } else if (impact > 0) {
          projectedIncome += Math.abs(impact)
          incomeCount++
          items.push({
            id: t.id,
            description: t.description,
            amount: effectiveAmount(t),
            myImpact: impact,
            category: t.category,
            subcategory: t.subcategory || null,
            dueDate: t.dueDate,
            type: 'income',
            paymentMethod: t.paymentMethod,
            cardId: t.cardId || null,
            cardName: cardObj?.cardName || null,
            origin: 'income',
            status: t.status,
            isRecurringProjection: false,
          })
        }
      } else if (impact !== 0) {
        projectedExpense += impact
        expenseCount++

        const isFixedTx = Boolean(t.recurringBillId) || t.status === 'estimated'
        if (isCard) {
          invoiceExpense += impact
          addCat(t.category, impact, 'invoice')
        } else if (isFixedTx) {
          fixedBillsExpense += impact
          addCat(t.category, impact, 'fixed_bill')
        } else {
          variableAccountExpense += impact
          addCat(t.category, impact, 'account')
        }

        const instInfo =
          t.installmentsTotal && t.installmentsTotal > 1
            ? `${t.installmentNumber || 1}/${t.installmentsTotal}`
            : null

        items.push({
          id: t.id,
          description: t.description,
          amount: effectiveAmount(t),
          myImpact: impact,
          category: t.category,
          subcategory: t.subcategory || null,
          dueDate: t.dueDate,
          type: 'expense',
          paymentMethod: isCard ? 'credit_card' : t.paymentMethod,
          cardId: t.cardId || null,
          cardName: cardObj?.cardName || null,
          origin: isCard ? 'invoice' : isFixedTx ? 'fixed_bill' : 'debit_pix',
          status: t.status,
          isRecurringProjection: false,
          installmentInfo: instInfo,
        })
      }
    }

    // 2. Projeta o salário e as contas fixas ativas que ainda NÃO foram lançadas como transação neste mês
    const hasSalary =
      monthTxs.some((t) => t.recurringBillId === `salary_${uid}`) ||
      Boolean(findMatchingManualSalary(monthTxs, monthKey, profile?.monthlyIncome))
    if (!hasSalary && profile && profile.monthlyIncome > 0) {
      projectedIncome += profile.monthlyIncome
      incomeCount++
      items.push({
        id: `proj_salary_${uid}_${monthKey}`,
        description: 'Salário Base',
        amount: profile.monthlyIncome,
        myImpact: profile.monthlyIncome,
        category: 'Salário & Remuneração',
        subcategory: 'Salário Principal',
        dueDate: dateInMonth(monthKey, 5),
        type: 'income',
        paymentMethod: 'pix',
        cardId: null,
        cardName: null,
        origin: 'income',
        status: 'projected_recurring',
        isRecurringProjection: true,
      })
    }

    let ungeneratedCount = 0
    const activeBills = (bills || []).filter((b) => b.active && b.ownerUid === uid)

    for (const bill of activeBills) {
      if (monthTxs.some((t) => t.recurringBillId === bill.id)) continue
      if (findMatchingManualBill(monthTxs, monthKey, bill)) continue

      // Limite temporal de vigência / parcelas
      if (bill.startMonth && monthKey < bill.startMonth) continue
      if (bill.endMonth && monthKey > bill.endMonth) continue

      ungeneratedCount++
      const { amount } = estimateRecurring(bill, all, monthKey)
      const myFraction = bill.splitType === 'split_50' ? round2(amount / 2) : amount
      const effMethod =
        bill.paymentMethod ?? (bill.type === 'income' ? 'pix' : bill.cardId ? 'credit_card' : 'bill')
      const isCardBill = effMethod === 'credit_card' || Boolean(bill.cardId)
      const cardObj = bill.cardId ? cardsMap.get(bill.cardId) : null
      const dueStr = cardObj ? dateInMonth(monthKey, cardObj.dueDay) : dateInMonth(monthKey, bill.dueDay)

      if (bill.type === 'income') {
        projectedIncome += myFraction
        incomeCount++
        items.push({
          id: `proj_bill_${bill.id}_${monthKey}`,
          description: bill.description,
          amount,
          myImpact: myFraction,
          category: bill.category || 'Receita',
          subcategory: bill.subcategory || null,
          dueDate: dueStr,
          type: 'income',
          paymentMethod: effMethod,
          cardId: bill.cardId || null,
          cardName: cardObj?.cardName || null,
          origin: 'income',
          status: 'projected_recurring',
          isRecurringProjection: true,
        })
      } else {
        projectedExpense += myFraction
        expenseCount++
        if (isCardBill) {
          invoiceExpense += myFraction
          addCat(bill.category, myFraction, 'invoice')
        } else {
          fixedBillsExpense += myFraction
          addCat(bill.category, myFraction, 'fixed_bill')
        }

        items.push({
          id: `proj_bill_${bill.id}_${monthKey}`,
          description: bill.description,
          amount,
          myImpact: myFraction,
          category: bill.category,
          subcategory: bill.subcategory || null,
          dueDate: dueStr,
          type: 'expense',
          paymentMethod: isCardBill ? 'credit_card' : effMethod,
          cardId: bill.cardId || null,
          cardName: cardObj?.cardName || null,
          origin: isCardBill ? 'invoice' : 'fixed_bill',
          status: 'projected_recurring',
          isRecurringProjection: true,
        })
      }
    }

    if (carryoverAmount > 0) {
      incomeCount++
      items.push({
        id: `proj_carryover_${monthKey}`,
        description: 'Sobra Injetada do Mês Anterior',
        amount: carryoverAmount,
        myImpact: carryoverAmount,
        category: 'Saldo Remanescente',
        subcategory: 'Fechamento de Mês',
        dueDate: dateInMonth(monthKey, 1),
        type: 'income',
        paymentMethod: 'pix',
        cardId: null,
        cardName: null,
        origin: 'income',
        status: 'paid',
        isRecurringProjection: false,
      })
    }

    const isGenerated = ungeneratedCount === 0 && (!profile || profile.monthlyIncome <= 0 || hasSalary)

    projectedIncome = round2(projectedIncome)
    projectedExpense = round2(projectedExpense)
    invoiceExpense = round2(invoiceExpense)
    fixedBillsExpense = round2(fixedBillsExpense)
    variableAccountExpense = round2(variableAccountExpense)
    const projectedAvailable = round2(projectedIncome + carryoverAmount - previousMonthDeficit)
    const projectedFreeBalance = round2(projectedAvailable - projectedExpense)
    const projectedDailyLimit = dim > 0 ? round2(projectedFreeBalance / dim) : null

    const byCategory: ProjectedCategoryItem[] = [...catMap.entries()]
      .map(([category, data]) => ({
        category,
        amount: data.amount,
        invoiceAmount: data.invoiceAmount,
        accountAmount: data.accountAmount,
        fixedBillAmount: data.fixedBillAmount,
        count: data.count,
      }))
      .sort((a, b) => b.amount - a.amount)

    items.sort((a, b) => (a.dueDate || '').localeCompare(b.dueDate || ''))

    projections.push({
      monthKey,
      label,
      daysInMonth: dim,
      projectedIncome,
      projectedExpense,
      carryoverAmount,
      previousMonthDeficit,
      projectedFreeBalance,
      projectedDailyLimit,
      isGenerated,
      incomeCount,
      expenseCount,
      invoiceExpense,
      fixedBillsExpense,
      variableAccountExpense,
      byCategory,
      items,
    })
  }

  return projections
}

// ------------------------------------------------------------------
// Parceiro(a) e cartões
// ------------------------------------------------------------------
export function resolvePartner(meUid: string, cards: Card[], users: UserProfile[]): Party | null {
  const cardList = Array.isArray(cards) ? cards : []
  const userList = Array.isArray(users) ? users : []
  for (const c of cardList) {
    if (!c) continue
    const sharedWithUsers = Array.isArray(c.sharedWithUsers)
      ? c.sharedWithUsers
      : (c.sharedWithUsers && typeof c.sharedWithUsers === 'object'
          ? Object.values(c.sharedWithUsers)
          : [])
    const sharedWithUids = Array.isArray(c.sharedWithUids) ? c.sharedWithUids : []
    if (c.ownerUid === meUid && sharedWithUsers.length) {
      const s: any = sharedWithUsers[0]
      if (s) {
        const sUid = typeof s === 'string' ? s : s.uid
        const sName = typeof s === 'object' && s.name ? s.name : 'Parceiro'
        const matched = userList.find((u) => u && u.uid === sUid)
        return { uid: sUid || '', name: matched?.displayName || sName }
      }
    }
    if (c.ownerUid !== meUid && sharedWithUids.includes(meUid)) {
      const matched = userList.find((u) => u && u.uid === c.ownerUid)
      return { uid: c.ownerUid || '', name: matched?.displayName || c.ownerName || 'Parceiro' }
    }
  }
  return null
}

export function accessibleCards(meUid: string, cards: Card[]) {
  return cards.filter((c) => c.ownerUid === meUid || (Array.isArray(c.sharedWithUids) && c.sharedWithUids.includes(meUid)))
}

export function isSharedCard(card: Card | undefined) {
  if (!card) return false
  const usersCount = Array.isArray(card.sharedWithUsers) ? card.sharedWithUsers.length : 0
  const uidsCount = Array.isArray(card.sharedWithUids) ? card.sharedWithUids.length : 0
  return usersCount > 0 || uidsCount > 0
}

/** Escolhe o cartão mais adequado para um lançamento. */
export function pickCard(meUid: string, cards: Card[], splitType: SplitType, hint?: string) {
  const mine = accessibleCards(meUid, cards || [])
  if (hint && typeof hint === 'string') {
    const h = normalize(hint)
    const byName = mine.find((c) => {
      const cName = c?.cardName ? normalize(c.cardName) : ''
      return cName && (cName.includes(h) || h.includes(cName))
    })
    if (byName) return byName
    if (/parceir|namorad|dela|dele|marido|esposa/.test(h)) {
      const other = mine.find((c) => c.ownerUid !== meUid)
      if (other) return other
    }
  }
  const ownShared = mine.find((c) => c.ownerUid === meUid && isSharedCard(c))
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

const INCOME_SUBCATEGORY_HINTS: [Category, string, RegExp][] = [
  ['Salário & Remuneração', 'Salário Principal', /salario|salário|folha de pag|holerite|pro-?labore|empresa.*sal/i],
  ['Salário & Remuneração', 'Adiantamento / Quinzenal', /adiantamento|quinzena/i],
  ['Salário & Remuneração', '13º Salário', /13o|13º|decimo terceiro|décimo terceiro/i],
  ['Salário & Remuneração', 'Férias', /ferias|férias/i],
  ['Salário & Remuneração', 'Bônus & PLR', /plr|bonus|bônus|participacao nos lucros|participação nos lucros/i],
  ['Benefícios', 'Vale Alimentação (VA)', /vale alimentacao|vale alimentação|\bva\b|alelo alimentacao|sodexo alimentacao|ticket alimentacao/i],
  ['Benefícios', 'Vale Refeição (VR)', /vale refeicao|vale refeição|\bvr\b|alelo refeicao|sodexo refeicao|ticket refeicao/i],
  ['Benefícios', 'Auxílio Mobilidade / Combustível', /mobilidade|auxilio combustivel|auxílio combustível|vale transporte/i],
  ['Freelance & Serviços', 'Projetos Freelance', /freela|freelance|projeto|prestacao de servico|prestação de serviço|servico prestado/i],
  ['Freelance & Serviços', 'Faturamento MEI / PJ', /faturamento mei|nota fiscal emitida|nf-e emitida|cliente pj|honorarios|honorários/i],
  ['Rendimentos & Investimentos', 'Dividendos & JCP', /dividendo|jcp|juros sobre capital/i],
  ['Rendimentos & Investimentos', 'Rendimentos de FIIs', /rendimento fii|fii.*rendimento|hglg|mxrf/i],
  ['Rendimentos & Investimentos', 'Rendimentos de Renda Fixa', /rendimento cdb|rendimento lci|rendimento lca|rendimento tesouro/i],
  ['Rendimentos & Investimentos', 'Resgates de Aplicação', /resgate|resgate investimento|resgate cdb|resgate rdb/i],
  ['Reembolsos & Acertos', 'Acerto de Amigo / Cartão', /acerto|divisao de conta|pagamento de amigo|reembolso amigo/i],
  ['Reembolsos & Acertos', 'Reembolso de Empresa', /reembolso empresa|despesa corporativa reembolsada|km rodado/i],
  ['Reembolsos & Acertos', 'Cashback & Estornos Recebidos', /cashback|estorno recebido/i],
  ['Vendas & Desapegos', 'Venda de Eletrônicos & Bens', /venda olx|venda enjoei|venda mercado livre|desapego/i],
  ['Outras Receitas', 'Pix Recebido', /pix recebido|ted recebida|doc recebido|deposito|depósito|transferencia recebida/i],
  ['Outras Receitas', 'Restituição de Imposto de Renda', /restituicao|restituição|restituicao irpf/i],
]

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
  ['Transporte', 'Seguro Auto & Moto', /seguro auto|seguro carro|seguro moto|porto seguro auto|tokio marine/i],
  ['Transporte', 'Manutenção & Revisão', /mecanico|mecânico|oficina|pneu|troca de oleo|troca de óleo|alinhamento|balanceamento/i],

  // Moradia
  ['Moradia', 'Aluguel & Condomínio', /aluguel|condominio|condomínio|locacao|locação|imobiliaria/i],
  ['Moradia', 'Energia Elétrica', /luz|energia|enel|cpfl|cemig|coelba|light|eletropaulo|energisa/i],
  ['Moradia', 'Água & Saneamento', /agua|água|sabesp|sanepar|copasa|cedae|caesb/i],
  ['Moradia', 'Internet & TV a Cabo', /internet|fibra|claro|vivo|oi |tim fibra|brisanet|net virtua/i],
  ['Moradia', 'Gás', /gas|gás|ultragaz|liquigas|comgas/i],
  ['Moradia', 'Manutenção & Reparos', /encanador|eletricista|pintor|marceneiro|reparo casa|manutencao predial/i],

  // Saúde
  ['Saúde', 'Farmácia & Remédios', /farmacia|farmácia|drogaria|drogasil|droga raia|pacheco|sao paulo|são paulo|panvel|remedio|remédio|medicamento/i],
  ['Saúde', 'Consultas & Exames', /consulta|exame|laboratorio|laboratório|dentista|oftalmo|medico|médico|clinica|clínica|fleury|lavoisier/i],
  ['Saúde', 'Plano de Saúde & Dental', /unimed|amil|bradesco saude|sulamerica saude|notredame|odontoprev/i],
  ['Saúde', 'Academia & Treinos', /academia|smartfit|smart fit|bluefit|bio ritmo|musculacao|musculação|pilates|crossfit|natacao|natação/i],
  ['Saúde', 'Terapia & Psicologia', /terapia|psicolog|psiquiatra|sessao terapia/i],

  // Cuidados Pessoais & Estética
  ['Cuidados Pessoais & Estética', 'Cabelereiro & Barbearia', /barbearia|barbeiro|cabelereiro|cabeleireiro|salao de beleza|salão de beleza|corte cabelo/i],
  ['Cuidados Pessoais & Estética', 'Cosméticos & Maquiagem', /sephora|boticario|o boticário|natura|quem disse berenice|maquiagem|cosmetico|cosmético/i],
  ['Cuidados Pessoais & Estética', 'Skincare & Perfumaria', /skincare|protetor solar|perfume|perfumaria/i],
  ['Cuidados Pessoais & Estética', 'Tratamentos & Estética', /estetica|estética|depilacao|depilação|massagem|manicure|pedicure|spa/i],
  ['Cuidados Pessoais & Estética', 'Higiene Pessoal', /shampoo|sabonete|pasta de dente|desodorante|higiene/i],

  // Assinaturas & Serviços
  ['Assinaturas & Serviços', 'Streaming de Vídeo (Netflix/Max/Disney)', /netflix|disney|hbo|max|prime video|amazon prime|youtube premium|globoplay|telecine/i],
  ['Assinaturas & Serviços', 'Streaming de Música (Spotify/Apple)', /spotify|deezer|apple music|tidal|amazon music/i],
  ['Assinaturas & Serviços', 'Softwares, IA & Nuvem (iCloud/ChatGPT)', /chatgpt|openai|icloud|google one|google storage|github|adobe|canva|microsoft|dropbox/i],
  ['Assinaturas & Serviços', 'Telefonia Celular & Planos', /plano celular|controle vivo|claro celular|tim celular|recarga celular/i],

  // Família & Dependentes
  ['Família & Dependentes', 'Fraldas & Itens de Bebê', /fralda|pampers|huggies|lenco umedecido|mamadeira|chupeta/i],
  ['Família & Dependentes', 'Escola & Creche', /creche|mensalidade escolar|bercario|berçário|colegio infantil/i],
  ['Família & Dependentes', 'Pediatra & Saúde Infantil', /pediatra|vacina infantil/i],
  ['Família & Dependentes', 'Brinquedos & Atividades', /ri happy|brinquedo|brinquedos|lego/i],

  // Pets
  ['Pets', 'Ração & Petiscos', /pet|petz|cobasi|racao|ração|petisco/i],
  ['Pets', 'Veterinário & Medicamentos', /veterinario|veterinário|vacina pet/i],
  ['Pets', 'Banho, Tosa & Cuidados', /banho e tosa|tosa pet/i],

  // Compras & Estilo de Vida
  ['Compras & Estilo de Vida', 'Presentes', /presente|presentes|lembrancinha|lembranca|lembrança|aniversario|aniversário|floricultura|flores|cesta|cacau show|kopenhagen|chocolates/i],
  ['Compras & Estilo de Vida', 'Roupas & Acessórios', /zara|renner|riachuelo|c&a|shein|roupa|vestuario|vestuário|acessorio|acessório|nike|adidas|lingerie|joia|bijuteria/i],
  ['Compras & Estilo de Vida', 'Calçados', /calcado|calçado|tenis|tênis|sapato|sandalia|sandália|havaianas|arezzo|anacapri|vans|olympikus/i],
  ['Compras & Estilo de Vida', 'Eletrônicos & Informática', /kabum|pichau|terabyte|apple|samsung|dell|mercado livre|amazon|shopee|magalu|celular|smartphone|notebook|computador/i],
  ['Compras & Estilo de Vida', 'Casa & Decoração', /decoracao|decoração|cama|mesa|banho|tok&stok|tok e stok|etna|camicado|mobly|leroy|marabraz|utilidade|moveis|móveis/i],

  // Educação & Desenvolvimento
  ['Educação & Desenvolvimento', 'Cursos Online & Certificações', /curso|udemy|alura|hotmart/i],
  ['Educação & Desenvolvimento', 'Livros & E-books', /livro|livraria|saraiva|leitura|kindle/i],
  ['Educação & Desenvolvimento', 'Faculdade & Pós-Graduação', /faculdade|pos-graduacao|pós-graduação|puc|fiap|fgv|estacio/i],

  // Lazer & Entretenimento
  ['Lazer & Entretenimento', 'Cinema, Teatro & Shows', /cinema|cinemark|ingresso|sympla|eventim|show|teatro/i],
  ['Lazer & Entretenimento', 'Viagens & Hospedagem', /viagem|hotel|airbnb|booking|decolar|passagem aerea|latam|gol|azul/i],
  ['Lazer & Entretenimento', 'Jogos & Games', /playstation|xbox|nintendo|steam|steamgames|epic games|riot games/i],

  // Impostos & Tributos
  ['Impostos & Tributos', 'IPVA & Licenciamento', /ipva|licenciamento|detran|taxa detran/i],
  ['Impostos & Tributos', 'IPTU', /iptu|taxa de lixo prefeitura/i],
  ['Impostos & Tributos', 'Imposto de Renda (IRPF)', /darf irpf|imposto de renda|declaracao irpf/i],
  ['Impostos & Tributos', 'DARFs & Tributos de Renda', /darf|receita federal|simples nacional|tributo federal/i],

  // Trabalho / PJ / Carreira
  ['Trabalho / PJ / Carreira', 'DAS MEI & Contabilidade', /das mei|pgmei|contabilizei|contabilidade/i],
  ['Trabalho / PJ / Carreira', 'Coworking & Escritório', /coworking|wework|aluguel sala comercial/i],
  ['Trabalho / PJ / Carreira', 'Equipamentos & Ferramentas de Trabalho', /monitor trabalho|cadeira ergonomica|teclado mecanico trabalho/i],

  // Investimentos
  ['Investimentos', 'Renda Fixa (CDB / Tesouro / LCI / LCA)', /cdb|tesouro|selic|lci|lca|ipca|renda fixa|debênture|debenture|cri|cra|nuinvest|inter invest/i],
  ['Investimentos', 'Ações & Fundos Imobiliários (FIIs)', /acoes|ações|fii|fiis|hglg|mxrf|knip|xpml|bova11|ivvb11|smal11|itub|petr|vale|bbas|wege/i],
  ['Investimentos', 'Criptomoedas & Ativos Digitais', /bitcoin|btc|ethereum|eth|solana|binance|mercado bitcoin|crypto|cripto/i],
  ['Investimentos', 'Reserva de Emergência', /reserva de emergencia|guardar dinheiro|cofrinho|reserva financeira/i],
  ['Investimentos', 'Previdência Privada', /previdencia|previdência|pgbl|vgbl/i],
  ['Investimentos', 'Fundos de Investimento', /fundo de investimento|fundo multimercado|fundo cambial/i],
  ['Investimentos', 'Outros Investimentos', /investimento|aporte|aplicacao|aplicação|corretora|xp investimentos|rico|clear|btg/i],

  // Financiamentos & Dívidas
  ['Financiamentos & Dívidas', 'Financiamento Imobiliário', /financiamento habitacional|habitacional|caixa habitacional|financ(?:iamento)? imobil|parcela (?:do |de )?(?:ape|apê|apartamento|imovel|imóvel|casa)|prestacao (?:ape|casa|imovel)|credito imobiliario|crédito imobiliário/i],
  ['Financiamentos & Dívidas', 'Financiamento Veicular', /financiamento (?:auto|veiculo|veículo|carro|moto)|parcela (?:do |de )?(?:carro|veiculo|veículo|moto)|consorcio (?:auto|carro|moto)|consórcio (?:auto|carro|moto)|bv financeira|pan auto|itau veiculos|santander auto/i],
  ['Financiamentos & Dívidas', 'Empréstimo Pessoal', /emprestimo pessoal|empréstimo pessoal|emprestimo nubank|emprestimo inter|emprestimo c6|emprestimo mercado pago|credito pessoal|crédito pessoal/i],
  ['Financiamentos & Dívidas', 'Empréstimo Consignado', /consignado|inss consignado|emprestimo folha|desconto em folha emprestimo|siape/i],
  ['Financiamentos & Dívidas', 'Amortização de Financiamento', /amortizacao|amortização|amortizar|reducao de prazo|redução de prazo|abatimento saldo devedor/i],
  ['Financiamentos & Dívidas', 'Crédito Estudantil (FIES/Pravaler)', /fies|pravaler|credito estudantil|crédito estudantil|financiamento estudantil/i],
  ['Financiamentos & Dívidas', 'Renegociação de Dívidas', /renegociacao|renegociação|acordo serasa|acordo divida|acordo dívida|recuperacao de credito|desenrola/i],
  ['Financiamentos & Dívidas', 'Juros & Tarifas de Empréstimo', /iof emprestimo|tarifa operacao credito|tac emprestimo|juros emprestimo|juros financ/i],

  // Estorno (Legado / Modificador)
  ['Estorno', 'Crédito na Fatura', /estorno|reembolso|devolucao|devolução|credito fatura|crédito fatura|cashback/i],
]

const CATEGORY_HINTS: [Category, RegExp][] = [
  ['Estorno', /estorno|reembolso|devolucao|devolução|credito fatura|crédito fatura|credito na fatura|credit voucher|cashback/i],
  ['Financiamentos & Dívidas', /financiamento|emprestimo|empréstimo|consignado|amortizacao|amortização|fies|pravaler|renegociacao|renegociação|parcela habitacional|parcela auto|parcela carro|parcela moto|credito pessoal|crédito pessoal/i],
  ['Investimentos', /investimento|aporte|cdb|tesouro|selic|lci|lca|fii|fiis|acoes|ações|cripto|bitcoin|btc|eth|corretora|nuinvest|binance/i],
  ['Cuidados Pessoais & Estética', /barbearia|barbeiro|salao de beleza|salão|cabeleireiro|manicure|estetica|estética|cosmetico|perfume|skincare/i],
  ['Impostos & Tributos', /ipva|iptu|irpf|darf|tributo|licenciamento/i],
  ['Família & Dependentes', /fralda|creche|bercario|pediatra|filho|escola infantil/i],
  ['Trabalho / PJ / Carreira', /coworking|das mei|contabilidade mei|despesa corporativa/i],
  ['Moradia', /aluguel|condom|luz|energia|agua|gas|internet|casa|apartamento/i],
  ['Alimentação', /jantar|almoco|cafe|mercado|supermercado|ifood|restaurante|lanche|pizza|padaria|comida|feira/i],
  ['Transporte', /uber|99|gasolina|combustivel|onibus|metro|estacionamento|carro|pedagio/i],
  ['Saúde', /farmacia|remedio|medic|consulta|exame|dentista|academia|plano de saude|drogaria/i],
  ['Lazer & Entretenimento', /cinema|show|viagem|bar|festa|passeio|jogo|teatro/i],
  ['Assinaturas & Serviços', /netflix|spotify|prime|disney|hbo|assinatura|youtube|icloud|google one/i],
  ['Educação & Desenvolvimento', /curso|faculdade|escola|livro|udemy|alura/i],
  ['Compras & Estilo de Vida', /roupa|tenis|shopping|eletronico|celular|magazine|presente|lembranc/i],
  ['Pets', /pet|racao|veterinario|cobasi/i],
]

export function guessCategoryAndSubcategory(text: string, amount?: number, type?: 'income' | 'expense'): CategoryGuess {
  if (typeof amount === 'number' && amount < 0) {
    return { category: 'Estorno', subcategory: 'Crédito na Fatura' }
  }
  const n = normalize(text)

  // Se o contexto for expressamente receita, priorizar hints de receita
  if (type === 'income') {
    for (const [cat, sub, re] of INCOME_SUBCATEGORY_HINTS) {
      if (re.test(n)) return { category: cat, subcategory: sub }
    }
    return { category: 'Salário & Remuneração', subcategory: 'Salário Principal' }
  }

  for (const [cat, sub, re] of SUBCATEGORY_HINTS) {
    if (re.test(n)) return { category: cat, subcategory: sub }
  }
  for (const [cat, re] of CATEGORY_HINTS) {
    if (re.test(n)) return { category: cat }
  }

  // Se parecer receita mesmo sem tipo informado
  for (const [cat, sub, re] of INCOME_SUBCATEGORY_HINTS) {
    if (re.test(n)) return { category: cat, subcategory: sub }
  }

  return { category: 'Outros' }
}

export function guessCategory(text: string, amount?: number, type?: 'income' | 'expense'): Category {
  return guessCategoryAndSubcategory(text, amount, type).category
}

/** Categorias canônicas (padrão dos lançamentos) com despesa fixa/recorrente mensal. */
export const FIXED_MONTHLY_CATEGORIES = new Set<string>([
  'Moradia',
  'Assinaturas & Serviços',
  'Educação & Desenvolvimento',
  'Financiamentos & Dívidas',
  'Impostos & Tributos',
])

/** Categorias que nunca geram envelope de despesa: entradas, patrimônio e legados de crédito/terceiros. */
export const NON_ENVELOPE_CATEGORIES = new Set<string>([
  'Investimentos',
  ...INCOME_CATEGORIES,
  'Receita',
  'Estorno',
  'Cartão Terceiros',
])

/**
 * Normaliza a categoria de um envelope para o padrão das categorias dos lançamentos
 * (fonte única: `normalizeCategory`). Legados de crédito/terceiros são preservados
 * para continuarem fora dos envelopes.
 */
export function normalizeBudgetCategory(category: string): string {
  const trimmed = (category || '').trim()
  if (trimmed === 'Estorno' || trimmed === 'Cartão Terceiros' || trimmed === 'Receita') return trimmed
  return normalizeCategory(trimmed)
}

/**
 * Calcula o Score do Termômetro de Saúde Financeira (0 a 100).
 * Soma 4 pilares da metodologia Saldo Livre:
 * 1. Poupança & Investimentos (até 30 pts)
 * 2. Controle de Custo de Vida / Consumo da Renda (até 30 pts)
 * 3. Cobertura da Reserva de Emergência (até 25 pts)
 * 4. Aderência aos Envelopes Orçamentários (até 15 pts)
 */

export function resolveEmergencyReserveBalance(
  profile: UserProfile | null | undefined,
  summary: MonthSummary | null | undefined,
): number {
  const balances = profile?.investmentAssetBalances
  let reserveBalance = 0
  if (balances && typeof balances === 'object') {
    const exactReserve = balances['Reserva de Emergência']
    if (typeof exactReserve === 'number' && exactReserve > 0) {
      reserveBalance = exactReserve
    } else {
      const reserveKey = Object.keys(balances).find((k) => k.toLowerCase().includes('reserva'))
      const keyVal = reserveKey ? balances[reserveKey] : undefined
      if (typeof keyVal === 'number' && keyVal > 0) {
        reserveBalance = keyVal
      } else {
        const values = Object.values(balances).filter((v): v is number => typeof v === 'number' && v > 0)
        if (values.length > 0) reserveBalance = values.reduce((sum, v) => sum + v, 0)
      }
    }
  }
  if (reserveBalance <= 0 && typeof profile?.emergencyReserveBalance === 'number' && profile.emergencyReserveBalance > 0) {
    reserveBalance = profile.emergencyReserveBalance
  }
  if (reserveBalance <= 0) {
    reserveBalance = summary?.totalInvestedAccumulated || 0
  }
  return round2(Math.max(0, reserveBalance))
}

export function calculateFinancialHealthScore(
  summary: MonthSummary | null | undefined,
  emergencyReserveBalance: number = 0,
  budgetReport?: CategoryBudgetReport | null,
): FinancialHealthScore {
  if (!summary) {
    return {
      score: 50,
      classification: 'warning',
      label: 'Atenção',
      description: 'Aguardando dados financeiros para calcular o score.',
      savingsScore: 0,
      spendingLimitScore: 0,
      emergencyReserveScore: 0,
      envelopeScore: 0,
      savingsRatePercentage: 0,
      spendingRatioPercentage: 0,
      emergencyCoverageMonths: 0,
      budgetAdherenceScore: 0,
      budgetConfiguredCount: 0,
      budgetOverspentCount: 0,
      monthlyLivingCost: 0,
      potentialSavingsRatePercentage: 0,
      actionableTips: ['Cadastre suas receitas e despesas do mês para liberar o diagnóstico completo.'],
    }
  }

  const rawIncome = summary.income || 0
  const carryover = summary.carryoverAmount || 0
  const deficit = summary.previousMonthDeficit || 0
  const effectiveAvailable = round2(Math.max(0, rawIncome + carryover - deficit))
  const income = effectiveAvailable
  const rawTotalOutflow = (summary.paid || 0) > 0 || (summary.committed || 0) > 0
    ? (summary.paid === summary.committed && !summary.committedEstimated && !summary.committedPending
        ? summary.committed
        : (summary.paid || 0) + (summary.committed || 0))
    : (Array.isArray(summary.byCategory) ? summary.byCategory.reduce((acc, c) => acc + (c.amount || 0), 0) : 0)
  const totalInvested = Math.max(0, summary.totalInvested || 0)
  const positiveFreeBalance = Math.max(0, summary.freeBalance || 0)

  // Desconta os aportes em Investimentos do custo de vida real para nunca penalizar quem investe
  const investedInByCategory = Array.isArray(summary.byCategory)
    ? (summary.byCategory.find((c) => c.category === 'Investimentos' || /investimento/i.test(c.category))?.amount || 0)
    : 0
  const totalExpenses = Math.max(0, round2(rawTotalOutflow - investedInByCategory))

  // 1. Pilar de Poupança & Investimento (0 a 30 pontos) — Meta >= 20% da renda
  let savingsScore = 0
  let savingsRatePercentage = 0
  let potentialSavingsRatePercentage = 0
  if (income > 0) {
    const rate = totalInvested / income
    savingsRatePercentage = Math.round(rate * 100)
    potentialSavingsRatePercentage = Math.min(
      100,
      Math.round(((totalInvested + positiveFreeBalance) / income) * 100),
    )

    if (totalInvested <= 0 || rate <= 0) {
      savingsScore = 0
    } else if (rate >= 0.20) {
      savingsScore = 30
    } else {
      const baseInvestedPts = Math.round((rate / 0.20) * 25)
      // Reconhece a margem de Saldo Livre positivo projetado como capacidade real de aporte (até +5 pts se já investiu)
      const freeMarginRate = positiveFreeBalance / income
      const marginBonusPts = freeMarginRate > 0 ? Math.min(5, Math.round((freeMarginRate / 0.20) * 5)) : 0
      savingsScore = Math.min(30, baseInvestedPts + marginBonusPts)
    }
  } else if (totalInvested > 0) {
    savingsScore = 15
    savingsRatePercentage = 100
    potentialSavingsRatePercentage = 100
  }

  // 2. Pilar de Controle de Custo de Vida (0 a 30 pontos) — Despesas vs. Renda
  let spendingLimitScore = 0
  let spendingRatioPercentage = 0
  if (income > 0) {
    const ratio = totalExpenses / income
    spendingRatioPercentage = Math.round(ratio * 100)
    if (ratio <= 0.70) {
      spendingLimitScore = 30
    } else if (ratio <= 1.00) {
      spendingLimitScore = Math.max(8, Math.round(30 - ((ratio - 0.70) / 0.30) * 22))
    } else if (ratio <= 1.10) {
      spendingLimitScore = 4
    } else {
      spendingLimitScore = 0
    }
  } else if (totalExpenses === 0) {
    spendingLimitScore = 15
    spendingRatioPercentage = 0
  } else {
    spendingLimitScore = 0
    spendingRatioPercentage = 100
  }

  // 3. Pilar de Reserva de Emergência (0 a 25 pontos) — Meta 3 a 6 meses de proteção
  let emergencyReserveScore = 0
  const baselineCost = totalExpenses > 0
    ? totalExpenses
    : (income > 0 ? income * 0.8 : 1000)

  const coverageMonths = emergencyReserveBalance > 0
    ? round2(emergencyReserveBalance / baselineCost)
    : 0

  if (coverageMonths >= 6.0) {
    emergencyReserveScore = 25
  } else if (coverageMonths >= 3.0) {
    emergencyReserveScore = Math.round(18 + ((coverageMonths - 3.0) / 3.0) * 7)
  } else if (coverageMonths >= 1.0) {
    emergencyReserveScore = Math.round(8 + ((coverageMonths - 1.0) / 2.0) * 10)
  } else if (coverageMonths > 0) {
    emergencyReserveScore = Math.min(8, Math.round(coverageMonths * 8))
  }

  // 4. Pilar de Controle de Envelopes Orçamentários (0 a 15 pontos)
  let budgetAdherenceScore = 0
  let budgetConfiguredCount = 0
  let budgetOverspentCount = 0
  let envelopeScore = 0

  if (budgetReport && budgetReport.totalBudgeted > 0) {
    const configuredItems = budgetReport.items.filter(
      (i) => i.limit > 0 && !/investimento/i.test(i.category),
    )
    budgetConfiguredCount = configuredItems.length
    const overspentItems = configuredItems.filter(
      (i) => (i.spentAmount + (i.pendingAmount || 0)) - i.limit >= 0.01,
    )
    budgetOverspentCount = overspentItems.length

    if (budgetConfiguredCount > 0) {
      if (budgetOverspentCount === 0) {
        envelopeScore = 15
        budgetAdherenceScore = 100
      } else {
        const compliantCount = budgetConfiguredCount - budgetOverspentCount
        const complianceRatio = compliantCount / budgetConfiguredCount
        const isGlobalOver = (budgetReport.totalSpent + (budgetReport.totalPending || 0)) - budgetReport.totalBudgeted >= 0.01
        const globalOverPenalty = isGlobalOver ? 3 : 0
        envelopeScore = Math.max(0, Math.min(15, Math.round(complianceRatio * 15) - globalOverPenalty))
        budgetAdherenceScore = Math.round((envelopeScore / 15) * 100)
      }
    } else {
      envelopeScore = 15
      budgetAdherenceScore = 100
    }
  } else {
    // Sem envelopes configurados: infere a disciplina orçamentária pelo comprometimento da renda
    if (spendingRatioPercentage <= 70) {
      envelopeScore = 15
      budgetAdherenceScore = 100
    } else if (spendingRatioPercentage >= 100) {
      envelopeScore = 0
      budgetAdherenceScore = 0
    } else {
      const ratio = (100 - spendingRatioPercentage) / 30
      envelopeScore = Math.max(0, Math.min(15, Math.round(ratio * 15)))
      budgetAdherenceScore = Math.round((envelopeScore / 15) * 100)
    }
  }

  // Pontuação Total (0 a 100 pontos — soma exata e transparente dos 4 pilares)
  const totalScore = Math.min(
    100,
    Math.max(0, savingsScore + spendingLimitScore + emergencyReserveScore + envelopeScore),
  )

  let classification: FinancialHealthScore['classification'] = 'critical'
  let label = 'Crítico'
  let description = 'Despesas acima da renda ou ausência de margem e reservas.'

  if (totalScore >= 80) {
    classification = 'excellent'
    label = 'Excelente'
    description = 'Finanças blindadas com excelente taxa de poupança, controle e reserva sólida.'
  } else if (totalScore >= 60) {
    classification = 'healthy'
    label = 'Saudável'
    description = 'Bom ritmo financeiro com despesas controladas e reservas ativas.'
  } else if (totalScore >= 40) {
    classification = 'warning'
    label = 'Atenção'
    description = 'Gastos próximos do limite ou reserva de emergência em formação.'
  }

  // Geração de dicas práticas e acionáveis (Actionable Coach)
  const actionableTips: string[] = []

  if (budgetConfiguredCount > 0 && budgetOverspentCount > 0 && budgetReport?.topOverspentCategory) {
    const topOver = budgetReport.topOverspentCategory
    const excess = round2(Math.max(0, (topOver.spentAmount + (topOver.pendingAmount || 0)) - topOver.limit))
    actionableTips.push(
      `📦 Envelope de ${topOver.category} excedeu o teto em R$ ${excess.toFixed(2).replace('.', ',')}. Reequilibrar seus envelopes recupera +${15 - envelopeScore} pts no Score.`,
    )
  } else if (budgetConfiguredCount === 0 && income > 0) {
    actionableTips.push(
      '📦 Ative os Envelopes Orçamentários com IA para blindar suas categorias variáveis e ganhar até +7 pts extras no Score.',
    )
  }

  if (income > 0 && savingsRatePercentage < 20) {
    const idealInvest = round2(income * 0.20)
    const missingInvest = round2(Math.max(0, idealInvest - totalInvested))
    const potentialGain = Math.max(1, 30 - savingsScore)
    if (positiveFreeBalance >= missingInvest && missingInvest > 0) {
      actionableTips.push(
        `📈 Você tem Saldo Livre suficiente para aportar mais R$ ${missingInvest.toFixed(2).replace('.', ',')} (atingindo 20% da renda) e ganhar +${potentialGain} pts.`,
      )
    } else if (missingInvest > 0) {
      actionableTips.push(
        `📈 Aportar na categoria Investimentos (meta ideal: R$ ${idealInvest.toFixed(2).replace('.', ',')}/mês) pode elevar seu Score em até +${potentialGain} pts.`,
      )
    }
  }

  if (income > 0 && spendingRatioPercentage > 70) {
    const targetExpense = round2(income * 0.70)
    const excessExpense = round2(Math.max(0, totalExpenses - targetExpense))
    const potentialGain = Math.max(1, 30 - spendingLimitScore)
    actionableTips.push(
      `🎯 Seu custo de vida consome ${spendingRatioPercentage}% da renda. Reduzir R$ ${excessExpense.toFixed(2).replace('.', ',')} em gastos variáveis destrava +${potentialGain} pts.`,
    )
  }

  if (coverageMonths < 6.0) {
    const nextTargetMonths = coverageMonths < 1 ? 1 : coverageMonths < 3 ? 3 : 6
    const neededAmount = round2(Math.max(0, baselineCost * nextTargetMonths - emergencyReserveBalance))
    if (neededAmount > 0) {
      actionableTips.push(
        `🛡️ Sua Reserva cobre ${coverageMonths}m do seu custo de vida. Acumular mais R$ ${neededAmount.toFixed(2).replace('.', ',')} atinge o marco de ${nextTargetMonths} ${nextTargetMonths === 1 ? 'mês' : 'meses'} de proteção.`,
      )
    }
  }

  if (actionableTips.length === 0) {
    actionableTips.push(
      '✨ Parabéns! Você atingiu excelência nos 4 pilares (Poupança, Custo de Vida, Envelopes e Reserva de Emergência).',
    )
  }

  return {
    score: totalScore,
    classification,
    label,
    description,
    savingsScore,
    spendingLimitScore,
    emergencyReserveScore,
    envelopeScore,
    savingsRatePercentage,
    spendingRatioPercentage,
    emergencyCoverageMonths: coverageMonths,
    budgetAdherenceScore,
    budgetConfiguredCount,
    budgetOverspentCount,
    monthlyLivingCost: round2(baselineCost),
    potentialSavingsRatePercentage,
    actionableTips,
  }
}

// ------------------------------------------------------------------
// Envelopes Orçamentários Inteligentes & Análise de Gastos (IA-Ready)
// ------------------------------------------------------------------

/**
 * Calcula o consumo, ritmo diário, projeção de fechamento e alertas de estouro
 * para envelopes orçamentários por categoria, calibrando automaticamente o teto
 * caso a soma dos envelopes ultrapasse a entrada prevista do mês (expectedIncome).
 */
export function computeCategoryBudgets(
  categorySpendData: Array<{ category: string; amount: number }> | Transaction[],
  categoryBudgets: Record<string, number> = {},
  monthKey: string = currentMonthKey(),
  referenceDate: string = todayKey(),
  uid?: string,
  allTransactions?: Transaction[],
  expectedIncome: number = 0,
  carryoverAmount: number = 0,
  previousMonthDeficit: number = 0,
  recurringBills?: RecurringBill[],
): CategoryBudgetReport {
  const rawBudgetsInput = categoryBudgets || {}
  const resolveKey = (rawCat: string): string => {
    const trimmed = (rawCat || '').trim()
    if (!trimmed) return ''
    if (trimmed in rawBudgetsInput && (Number(rawBudgetsInput[trimmed]) || 0) > 0) {
      return trimmed
    }
    const canon = normalizeBudgetCategory(trimmed)
    if (canon in rawBudgetsInput && (Number(rawBudgetsInput[canon]) || 0) > 0) {
      return canon
    }
    for (const k of Object.keys(rawBudgetsInput)) {
      if ((Number(rawBudgetsInput[k]) || 0) > 0 && normalizeBudgetCategory(k) === canon) {
        return k
      }
    }
    return canon
  }

  // 1. Extrai ou computa mapa de gastos pagos (spentAmountMap) e comprometidos a vencer (pendingAmountMap)
  const spentAmountMap = new Map<string, number>()
  const pendingAmountMap = new Map<string, number>()

  const txList = Array.isArray(allTransactions) && allTransactions.length > 0
    ? allTransactions
    : (Array.isArray(categorySpendData) && categorySpendData.length > 0 && typeof (categorySpendData[0] as any)?.totalAmount === 'number'
      ? (categorySpendData as Transaction[])
      : [])

  if (txList.length > 0 && uid) {
    for (const tx of txList) {
      const m = tx.competenceMonth || (tx.dueDate || '').slice(0, 7)
      if (m === monthKey && (tx.visibleToUids || []).includes(uid)) {
        if (tx.type === 'expense') {
          const impact = impactFor(tx, uid, txList)
          if (impact > 0 && tx.category) {
            const key = resolveKey(tx.category)
            if (tx.status === 'paid') {
              spentAmountMap.set(key, round2((spentAmountMap.get(key) || 0) + impact))
            } else {
              pendingAmountMap.set(key, round2((pendingAmountMap.get(key) || 0) + impact))
            }
          }
        }
      }
    }
  } else if (Array.isArray(categorySpendData) && categorySpendData.length > 0) {
    // É uma lista { category, amount } (ex: summary.byCategory)
    for (const item of categorySpendData as Array<{ category: string; amount: number }>) {
      if (item.category) {
        const key = resolveKey(item.category)
        spentAmountMap.set(key, round2((spentAmountMap.get(key) || 0) + (Number(item.amount) || 0)))
      }
    }
  }

  // Contas fixas e recorrentes ativas a vencer no mês que ainda não geraram lançamento
  if (uid && Array.isArray(recurringBills) && recurringBills.length > 0) {
    const activeBills = recurringBills.filter((b) => b.active && b.ownerUid === uid && b.type !== 'income')
    for (const bill of activeBills) {
      if (bill.startMonth && monthKey < bill.startMonth) continue
      if (bill.endMonth && monthKey > bill.endMonth) continue

      const hasTx = txList.some((t) => t.recurringBillId === bill.id && (t.competenceMonth === monthKey || (t.dueDate || '').startsWith(monthKey)))
      const hasManual = findMatchingManualBill(txList, monthKey, bill)
      if (!hasTx && !hasManual) {
        const estimated = estimateRecurring(bill, txList, monthKey).amount
        if (estimated > 0 && bill.category) {
          const key = resolveKey(bill.category)
          pendingAmountMap.set(key, round2((pendingAmountMap.get(key) || 0) + estimated))
        }
      }
    }
  }

  // Reconcilia aportes de investimento no mês caso não capturados
  let investMonthSpent = 0
  if (uid && txList.length > 0) {
    for (const tx of txList) {
      const m = tx.competenceMonth || (tx.dueDate || '').slice(0, 7)
      if (m === monthKey && (tx.visibleToUids || []).includes(uid)) {
        const isAporte = (tx.category === 'Investimentos' || /investimento/i.test(tx.category)) && tx.type === 'expense'
        if (isAporte) {
          const impact = impactFor(tx, uid, txList)
          if (impact > 0) investMonthSpent += impact
        }
      }
    }
  }
  if (investMonthSpent > 0) {
    const existing = spentAmountMap.get('Investimentos') || 0
    spentAmountMap.set('Investimentos', round2(Math.max(existing, investMonthSpent)))
  }

  // Consolida o mapa de orçamentos brutos sob as mesmas chaves reconciliadas
  const rawLimitsMap = new Map<string, number>()
  for (const [cat, val] of Object.entries(rawBudgetsInput)) {
    const num = round2(Math.max(0, Number(val) || 0))
    if (num > 0) {
      const key = resolveKey(cat)
      rawLimitsMap.set(key, round2((rawLimitsMap.get(key) || 0) + num))
    }
  }

  // Exclui Investimentos do cálculo do total orçado de despesas
  let rawExpenseBudgeted = 0
  for (const [k, v] of rawLimitsMap.entries()) {
    if (!/investimento/i.test(k)) {
      rawExpenseBudgeted += v
    }
  }
  rawExpenseBudgeted = round2(rawExpenseBudgeted)

  const rawTotalBudgeted = rawExpenseBudgeted
  const validExpectedIncome = round2(Math.max(0, Number(expectedIncome) || 0))
  const validCarryover = round2(Math.max(0, Number(carryoverAmount) || 0))
  const validDeficit = round2(Math.max(0, Number(previousMonthDeficit) || 0))
  const totalAvailableIncome = round2(Math.max(0, validExpectedIncome + validCarryover - validDeficit))
  const isCappedToIncome = totalAvailableIncome > 0 && rawTotalBudgeted > totalAvailableIncome

  // Calibração proporcional sem centavos quebrados (arredonda em múltiplos de 10)
  const effectiveLimitsMap = new Map<string, number>()
  if (isCappedToIncome && rawTotalBudgeted > 0) {
    const ratio = totalAvailableIncome / rawTotalBudgeted
    let sumScaled = 0
    let maxKey = ''
    let maxVal = -1
    for (const [k, rawLim] of rawLimitsMap.entries()) {
      if (/investimento/i.test(k)) {
        effectiveLimitsMap.set(k, rawLim)
        continue
      }
      let scaled = Math.floor((rawLim * ratio) / 10) * 10
      // Nunca zera um envelope configurado só por causa do arredondamento em múltiplos de 10
      if (scaled <= 0 && rawLim > 0) scaled = Math.max(1, Math.floor(rawLim * ratio))
      effectiveLimitsMap.set(k, scaled)
      sumScaled += scaled
      if (scaled > maxVal) {
        maxVal = scaled
        maxKey = k
      }
    }
    const diff = Math.floor((totalAvailableIncome - sumScaled) / 10) * 10
    if (maxKey && diff >= 10) {
      effectiveLimitsMap.set(maxKey, (effectiveLimitsMap.get(maxKey) || 0) + diff)
    }
  } else {
    for (const [k, rawLim] of rawLimitsMap.entries()) {
      effectiveLimitsMap.set(k, rawLim)
    }
  }

  // 2. Parâmetros temporais para ritmo e projeção
  const totalDays = daysInMonth(monthKey)
  let currentDay = totalDays

  if (referenceDate.startsWith(monthKey)) {
    const dayParsed = parseInt(referenceDate.split('-')[2], 10)
    currentDay = Math.min(totalDays, Math.max(1, isNaN(dayParsed) ? 1 : dayParsed))
  } else if (referenceDate < monthKey) {
    currentDay = 1
  }

  const daysRemaining = Math.max(0, totalDays - currentDay)

  // 3. Monta lista de categorias a avaliar
  const allCategoriesSet = new Set<string>([...effectiveLimitsMap.keys(), ...spentAmountMap.keys(), ...pendingAmountMap.keys()])

  const items: CategoryBudgetStatus[] = []
  let totalBudgeted = 0
  let totalSpent = 0
  let totalPending = 0
  let criticalCount = 0
  let warningCount = 0

  for (const category of allCategoriesSet) {
    const rawLimit = round2(Math.max(0, rawLimitsMap.get(category) || 0))
    const limit = round2(Math.max(0, effectiveLimitsMap.get(category) || 0))
    const spentAmount = round2(Math.max(0, spentAmountMap.get(category) || 0))
    const pendingAmount = round2(Math.max(0, pendingAmountMap.get(category) || 0))
    const committedAmount = round2(spentAmount + pendingAmount)
    const spent = committedAmount

    const canonCat = normalizeBudgetCategory(category)
    const isInvestmentCategory = category === 'Investimentos' || /investimento/i.test(category)
    const isFixedCategory = FIXED_MONTHLY_CATEGORIES.has(category) || FIXED_MONTHLY_CATEGORIES.has(canonCat)

    // Categorias patrimoniais/entradas não geram envelopes a menos que explicitamente configuradas
    if (limit <= 0 && (NON_ENVELOPE_CATEGORIES.has(category) || NON_ENVELOPE_CATEGORIES.has(canonCat))) continue
    if (limit <= 0 && committedAmount <= 0) continue

    const remaining = round2(limit - committedAmount)
    const realRemaining = round2(Math.max(0, limit - committedAmount))
    const percentage = limit > 0 ? round2((spentAmount / limit) * 100) : (spentAmount > 0 ? 100 : 0)
    const pendingPercentage = limit > 0 ? round2((pendingAmount / limit) * 100) : 0
    const totalPercentage = limit > 0 ? round2((committedAmount / limit) * 100) : (committedAmount > 0 ? 100 : 0)

    const isPaidOverspent = limit > 0 && (spentAmount - limit >= 0.01)
    const isCommittedOverspent = limit > 0 && (committedAmount - limit >= 0.01)
    const isExactLimit = limit > 0 && Math.abs(committedAmount - limit) < 0.01

    let status: 'ok' | 'warning' | 'danger' = 'ok'
    let badgeLabel: CategoryBudgetBadgeLabel = 'Sob Controle'

    if (isInvestmentCategory) {
      status = 'ok'
      badgeLabel = 'Sob Controle'
    } else if (limit <= 0) {
      status = committedAmount > 0 ? 'warning' : 'ok'
      badgeLabel = 'Definir Teto'
      if (status === 'warning') warningCount++
    } else if (isPaidOverspent) {
      status = 'danger'
      badgeLabel = 'Estourado'
      criticalCount++
    } else if (isCommittedOverspent) {
      status = 'danger'
      badgeLabel = 'Vai Estourar'
      criticalCount++
    } else if (isExactLimit) {
      if (spentAmount === 0 && pendingAmount > 0) {
        status = 'ok'
        badgeLabel = 'Previsto no Teto'
      } else {
        status = isFixedCategory ? 'ok' : 'warning'
        badgeLabel = isFixedCategory ? 'Quitado / Dentro do Previsto' : 'No Limite'
        if (status === 'warning') warningCount++
      }
    } else if (isFixedCategory) {
      status = 'ok'
      badgeLabel = 'Fixo em Dia'
    } else {
      // Gastos variáveis
      if (spentAmount === 0 && pendingAmount > 0 && committedAmount <= limit + 0.01) {
        status = 'ok'
        badgeLabel = 'Previsto no Teto'
      } else if (totalPercentage >= 90) {
        status = 'warning'
        badgeLabel = 'Quase no Limite'
        warningCount++
      } else if (totalPercentage >= 80) {
        status = 'warning'
        badgeLabel = (currentDay > 7 && ((spent / currentDay) * totalDays) > limit) ? 'Ritmo Rápido' : 'Quase no Limite'
        warningCount++
      } else if (currentDay <= 7) {
        if (totalPercentage > 65) {
          status = 'ok'
          badgeLabel = 'Ritmo Rápido'
        } else {
          status = 'ok'
          badgeLabel = 'Sob Controle'
        }
      } else {
        const dailyPaceCalc = currentDay > 0 ? spent / currentDay : 0
        const projectedCalc = dailyPaceCalc * totalDays
        if (projectedCalc >= limit * 0.95) {
          status = 'ok'
          badgeLabel = 'Ritmo Rápido'
        } else {
          status = 'ok'
          badgeLabel = 'Sob Controle'
        }
      }
    }

    const dailyPace = currentDay > 0 ? round2(spent / currentDay) : 0
    const projectedSpent = isFixedCategory && limit > 0
      ? round2(Math.max(committedAmount, limit))
      : round2(dailyPace * totalDays)
    const projectedPercentage = limit > 0 ? round2((projectedSpent / limit) * 100) : (projectedSpent > 0 ? 100 : 0)
    const recommendedDailyRemaining = realRemaining > 0 && daysRemaining > 0 ? round2(realRemaining / daysRemaining) : 0

    let burnRateStatus: CategoryBudgetPaceStatus = 'normal'
    if (isInvestmentCategory || isFixedCategory) {
      burnRateStatus = (committedAmount - limit >= 0.01) ? 'critical' : 'normal'
    } else if (currentDay <= 7) {
      if (committedAmount - limit >= 0.01) burnRateStatus = 'critical'
      else if (totalPercentage >= 90) burnRateStatus = 'critical'
      else if (totalPercentage > 65) burnRateStatus = 'fast'
      else burnRateStatus = 'normal'
    } else {
      if (committedAmount - limit >= 0.01 || projectedPercentage >= 115) {
        burnRateStatus = 'critical'
      } else if (projectedPercentage >= 95 || totalPercentage >= 90) {
        burnRateStatus = 'fast'
      } else {
        burnRateStatus = 'normal'
      }
    }

    if (limit > 0 && !isInvestmentCategory) {
      totalBudgeted += limit
      totalSpent += spentAmount
      totalPending += pendingAmount
    }

    items.push({
      category,
      limit,
      rawLimit,
      spent,
      spentAmount,
      pendingAmount,
      committedAmount,
      remaining,
      realRemaining,
      percentage,
      pendingPercentage,
      totalPercentage,
      status,
      badgeLabel,
      dailyPace,
      projectedSpent,
      projectedPercentage,
      recommendedDailyRemaining,
      burnRateStatus,
      daysRemaining,
      currentDay,
      totalDays,
      isFixedCategory,
    })
  }

  // Ordena: perigo primeiro, depois alerta, depois por valor comprometido decrescente
  items.sort((a, b) => {
    const priority = { danger: 3, warning: 2, ok: 1 }
    const pDiff = priority[b.status] - priority[a.status]
    if (pDiff !== 0) return pDiff
    return b.committedAmount - a.committedAmount
  })

  totalBudgeted = round2(totalBudgeted)
  totalSpent = round2(totalSpent)
  totalPending = round2(totalPending)
  const totalCommitted = round2(totalSpent + totalPending)
  const totalRemaining = round2(totalBudgeted - totalCommitted)
  const totalRealRemaining = round2(Math.max(0, totalBudgeted - totalCommitted))
  const overallPercentage = totalBudgeted > 0 ? round2((totalSpent / totalBudgeted) * 100) : 0

  let overallStatus: 'ok' | 'warning' | 'danger' = 'ok'
  if (totalCommitted > totalBudgeted + 0.01 || criticalCount > 0) overallStatus = 'danger'
  else if (overallPercentage >= 80 || warningCount > 0) overallStatus = 'warning'

  // Top categoria estourada só existe se committedAmount exceder limit em >= 0.01 (sem falsos estouros de R$ 0,00)
  const topOverspentCategory = items.find(
    (i) => !/investimento/i.test(i.category) && (i.status === 'danger' || (i.committedAmount - i.limit >= 0.01)),
  )

  const incomeUsagePercentage = totalAvailableIncome > 0 ? round2((totalBudgeted / totalAvailableIncome) * 100) : 0
  const unallocatedIncome = totalAvailableIncome > 0 ? round2(Math.max(0, totalAvailableIncome - totalBudgeted)) : 0

  const investmentTarget = rawLimitsMap.get('Investimentos') || 0
  const investmentSpent = spentAmountMap.get('Investimentos') || 0
  const investmentPercentage = investmentTarget > 0 ? round2((investmentSpent / investmentTarget) * 100) : 0
  // Meta 50/30/20: 20% da renda prevista, independente do teto de envelope (que pode ter sido reduzido pela calibragem)
  const investmentGoal = round2(Math.max(0, validExpectedIncome) * 0.2)

  return {
    items,
    totalBudgeted,
    rawTotalBudgeted,
    expectedIncome: validExpectedIncome,
    carryoverAmount: validCarryover,
    previousMonthDeficit: validDeficit,
    totalAvailableIncome,
    isCappedToIncome,
    incomeUsagePercentage,
    unallocatedIncome,
    totalSpent,
    totalPending,
    totalCommitted,
    totalRemaining,
    totalRealRemaining,
    overallPercentage,
    status: overallStatus,
    criticalCount,
    warningCount,
    topOverspentCategory,
    investmentTarget,
    investmentSpent,
    investmentPercentage,
    investmentGoal,
  }
}

/**
 * Gera até 3 insights acionáveis e determinísticos (sem rede) sobre os envelopes.
 * Espelha `buildBudgetInsights` do Android. Ordenados por severidade (perigo > atenção > info).
 */
export function buildBudgetInsights(report: CategoryBudgetReport): BudgetInsight[] {
  const insights: BudgetInsight[] = []
  const severityRank: Record<BudgetInsight['severity'], number> = { info: 0, attention: 1, danger: 2 }

  if (report.items.length === 0 || report.totalBudgeted <= 0) {
    return [
      {
        id: 'setup',
        severity: 'info',
        title: 'Crie seus envelopes',
        message:
          'Defina um teto por categoria para saber quanto ainda pode gastar. A IA monta um plano com base no seu histórico.',
        action: { type: 'apply_plan', label: 'Montar com IA' },
      },
    ]
  }

  const rebalance = suggestBudgetRebalance(report)

  // 1) Estouros reais (>= R$ 1,00)
  report.items
    .filter((i) => i.limit > 0 && i.committedAmount - i.limit >= 1)
    .sort((a, b) => b.committedAmount - b.limit - (a.committedAmount - a.limit))
    .slice(0, 2)
    .forEach((item) => {
      const excess = round2(item.committedAmount - item.limit)
      const transfer = rebalance.suggestions.find((s) => s.overspentCategory === item.category)
      const raise = Math.ceil(excess / 10) * 10
      const action: BudgetInsightAction =
        transfer && transfer.suggestedTransfer > 0
          ? {
              type: 'rebalance',
              label: `Cobrir com ${transfer.compensateFrom}`,
              category: item.category,
              amount: transfer.suggestedTransfer,
              fromCategory: transfer.compensateFrom,
            }
          : {
              type: 'adjust_limit',
              label: `Subir teto em ${formatBRL(raise)}`,
              category: item.category,
              amount: raise,
            }
      insights.push({
        id: `over_${item.category}`,
        severity: 'danger',
        title: `${item.category} passou do teto`,
        message: `Comprometido ${formatBRL(item.committedAmount)} de ${formatBRL(item.limit)} (${formatBRL(excess)} acima).`,
        action,
      })
    })

  // 2) Ritmo de gasto: variáveis perto do teto ou projetadas para estourar
  report.items
    .filter(
      (i) =>
        i.limit > 0 &&
        !i.isFixedCategory &&
        i.committedAmount - i.limit < 1 &&
        i.spentAmount > 0 &&
        (i.totalPercentage >= 85 || i.projectedSpent > i.limit + 1),
    )
    .sort((a, b) => b.totalPercentage - a.totalPercentage)
    .slice(0, 1)
    .forEach((item) => {
      const daily = item.daysRemaining > 0 ? item.realRemaining / item.daysRemaining : item.realRemaining
      insights.push({
        id: `pace_${item.category}`,
        severity: 'attention',
        title: `${item.category} está acelerado`,
        message: `Restam ${formatBRL(Math.max(0, item.realRemaining))} até o fim do mês, cerca de ${formatBRL(Math.max(0, daily))} por dia.`,
        action: {
          type: 'ask_copilot',
          label: 'Pedir dica à IA',
          category: item.category,
          prompt: `Meu envelope de ${item.category} está em ${Math.round(item.totalPercentage)}% do teto. Como posso segurar os gastos até o fim do mês?`,
        },
      })
    })

  // 3) Gastos sem envelope (itens sem teto que já têm valor comprometido)
  const unbudgeted = report.items
    .filter((i) => i.limit <= 0 && i.committedAmount > 0 && !/investimento/i.test(i.category))
    .sort((a, b) => b.committedAmount - a.committedAmount)
  if (unbudgeted.length > 0) {
    const unb = unbudgeted[0]
    const suggestedLimit = Math.ceil(unb.committedAmount / 50) * 50
    insights.push({
      id: `unbudgeted_${unb.category}`,
      severity: 'attention',
      title: `${unb.category} está sem teto`,
      message: `Você já tem ${formatBRL(unb.committedAmount)} nessa categoria e nenhum envelope para acompanhar.`,
      action: {
        type: 'adjust_limit',
        label: `Criar teto de ${formatBRL(suggestedLimit)}`,
        category: unb.category,
        amount: suggestedLimit,
      },
    })
  }

  // 4) Meta de aporte 50/30/20
  const goal = report.investmentGoal ?? 0
  const invested = report.investmentSpent ?? 0
  const goalGap = goal - invested
  if (goal > 0 && goalGap > 0 && invested < goal * 0.5) {
    insights.push({
      id: 'invest_goal',
      severity: 'info',
      title: `Meta de aporte: faltam ${formatBRL(goalGap)}`,
      message: `Pela regra 50/30/20, separe 20% da renda (${formatBRL(goal)}) para o seu futuro.`,
      action: {
        type: 'ask_copilot',
        label: 'Como atingir?',
        prompt: `Quero atingir minha meta de aporte de ${formatBRL(goal)} neste mês. Já investi ${formatBRL(invested)}. Qual o melhor caminho?`,
      },
    })
  }

  if (insights.length === 0) {
    insights.push({
      id: 'all_good',
      severity: 'info',
      title: 'Tudo sob controle',
      message: `Você ainda pode gastar ${formatBRL(Math.max(0, report.totalRealRemaining))} dentro dos tetos até o fim do mês.`,
    })
  }

  return insights.sort((a, b) => severityRank[b.severity] - severityRank[a.severity]).slice(0, 3)
}

/**
 * Analisa as transações dos últimos 3 meses e do mês selecionado para sugerir tetos
 * orçamentários realistas, garantindo que o orçamento total sugerido NUNCA ultrapasse
 * a entrada prevista daquele mês (expectedIncome).
 */
export function suggestCategoryBudgets(
  allTransactions: Transaction[],
  uid: string,
  referenceMonth: string = currentMonthKey(),
  monthlyIncome: number = 0,
  carryoverAmount: number = 0,
  previousMonthDeficit: number = 0,
): {
  suggestedBudgets: Record<string, number>
  totalSuggested: number
  incomeCoveragePct: number
  categoryAverages: Record<string, number>
  expectedIncome?: number
  isCappedToIncome?: boolean
} {
  const previousMonths = [
    shiftMonth(referenceMonth, -1),
    shiftMonth(referenceMonth, -2),
    shiftMonth(referenceMonth, -3),
  ]

  const monthCategoryTotals = new Map<string, Map<string, number>>()
  for (const m of previousMonths) {
    monthCategoryTotals.set(m, new Map())
  }

  const all = Array.isArray(allTransactions) ? allTransactions : []
  let matchingCount = 0
  let referenceMonthIncome = 0
  let referenceMonthInvested = 0
  const curMonthExpenseMap = new Map<string, number>()

  for (const tx of all) {
    const m = tx.competenceMonth || (tx.dueDate || '').slice(0, 7)
    const impact = impactFor(tx, uid, all)

    if (m === referenceMonth) {
      if (tx.type === 'income' && Math.abs(impact) > 0) {
        const isResgate =
          tx.category === 'Investimentos' ||
          (tx.category === 'Rendimentos & Investimentos' && Boolean(tx.subcategory && /resgate/i.test(tx.subcategory)))
        if (!isResgate) {
          referenceMonthIncome += Math.abs(impact)
        } else {
          referenceMonthInvested -= Math.abs(impact)
        }
      } else if (tx.type === 'expense' && impact > 0 && tx.category) {
        const canonCat = normalizeBudgetCategory(tx.category)
        if (canonCat === 'Investimentos') {
          referenceMonthInvested += impact
        } else {
          curMonthExpenseMap.set(canonCat, (curMonthExpenseMap.get(canonCat) || 0) + impact)
        }
      }
    }

    if (tx.type !== 'expense' || !previousMonths.includes(m)) continue
    if (impact <= 0 || !tx.category) continue

    const canonCat = normalizeBudgetCategory(tx.category)
    const catMap = monthCategoryTotals.get(m)!
    catMap.set(canonCat, (catMap.get(canonCat) || 0) + impact)
    matchingCount++
  }

  referenceMonthIncome = round2(Math.max(0, referenceMonthIncome))
  referenceMonthInvested = round2(Math.max(0, referenceMonthInvested))

  // Usa o que é esperado entrar no mês selecionado + sobra do mês anterior; se não houver receitas lançadas no mês, usa monthlyIncome passado
  const baseIncome = referenceMonthIncome > 0
    ? referenceMonthIncome
    : round2(Math.max(0, Number(monthlyIncome) || 0))
  const effectiveExpectedIncome = round2(Math.max(0, baseIncome + (carryoverAmount || 0) - (previousMonthDeficit || 0)))

  // Se não houver dados nos meses anteriores, analisa o próprio referenceMonth
  if (matchingCount === 0) {
    monthCategoryTotals.set(referenceMonth, curMonthExpenseMap)
  }

  const activeMonths = [...monthCategoryTotals.keys()].filter(
    (m) => (monthCategoryTotals.get(m)?.size || 0) > 0,
  )
  const divisor = Math.max(1, activeMonths.length)

  const sumMap = new Map<string, number>()
  for (const m of activeMonths) {
    const catMap = monthCategoryTotals.get(m)!
    for (const [cat, amt] of catMap.entries()) {
      sumMap.set(cat, (sumMap.get(cat) || 0) + amt)
    }
  }

  // Inclui categorias que já possuem gastos compromissados no mês atual mas não estavam no histórico
  for (const [cat, curAmt] of curMonthExpenseMap.entries()) {
    if (!sumMap.has(cat) && curAmt >= 20) {
      sumMap.set(cat, curAmt * divisor)
    }
  }

  const suggestedBudgets: Record<string, number> = {}
  const categoryAverages: Record<string, number> = {}
  let totalSuggested = 0

  for (const [cat, sum] of sumMap.entries()) {
    if (NON_ENVELOPE_CATEGORIES.has(cat)) continue
    const avg = round2(sum / divisor)
    categoryAverages[cat] = avg
    const curCommitted = curMonthExpenseMap.get(cat) || 0
    if (avg >= 20 || curCommitted > 0) {
      const isFixed = FIXED_MONTHLY_CATEGORIES.has(cat) || FIXED_MONTHLY_CATEGORIES.has(normalizeBudgetCategory(cat))
      const baseTarget = isFixed
        ? Math.max(avg, curCommitted)
        : Math.max(avg * 1.15, curCommitted * 1.20)
      const rawSuggested = isFixed
        ? Math.max(50, Math.ceil(baseTarget / 10) * 10)
        : Math.max(50, Math.ceil(baseTarget / 50) * 50)
      const committedCeil = isFixed
        ? Math.ceil(curCommitted / 10) * 10
        : Math.ceil(curCommitted / 50) * 50
      const suggested = Math.max(committedCeil, rawSuggested)
      suggestedBudgets[cat] = suggested
      totalSuggested += suggested
    }
  }

  // Se houver aportes de investimento no mês de referência e a categoria não estiver em suggestedBudgets
  if (referenceMonthInvested > 0 && !suggestedBudgets['Investimentos']) {
    const invSuggested = Math.max(50, Math.ceil(referenceMonthInvested / 10) * 10)
    suggestedBudgets['Investimentos'] = invSuggested
    categoryAverages['Investimentos'] = round2(referenceMonthInvested)
    totalSuggested += invSuggested
  }

  totalSuggested = round2(totalSuggested)
  let isCappedToIncome = false

  // Teto rigoroso: o orçamento sugerido NUNCA pode ultrapassar a entrada prevista daquele mês
  // e NUNCA pode ser menor do que o que a pessoa já gastou no mês selecionado.
  if (effectiveExpectedIncome > 0 && totalSuggested > 0) {
    const maxEnvelopeCeiling = Math.floor(effectiveExpectedIncome / 10) * 10

    if (totalSuggested > maxEnvelopeCeiling && maxEnvelopeCeiling > 0) {
      isCappedToIncome = true

      // 1. Calcula a soma dos pisos inegociáveis (o que já foi gasto no mês em cada categoria arredondado em múltiplos de 10)
      let sumFloors = 0
      const floorsMap: Record<string, number> = {}
      for (const cat of Object.keys(suggestedBudgets)) {
        const rawFloor = curMonthExpenseMap.get(cat) || (cat === 'Investimentos' ? referenceMonthInvested : 0)
        const floor = Math.ceil(rawFloor / 10) * 10
        floorsMap[cat] = floor
        sumFloors += floor
      }

      if (sumFloors >= maxEnvelopeCeiling) {
        // Cenário onde os gastos já realizados no mês já consom toda a renda prevista:
        // Mantém cada categoria exatamente no seu piso de segurança arredondado
        let newTotal = 0
        for (const cat of Object.keys(suggestedBudgets)) {
          const floor = floorsMap[cat] || 0
          suggestedBudgets[cat] = floor
          newTotal += floor
        }
        totalSuggested = newTotal
      } else {
        // Folga disponível para distribuir acima dos pisos gastos:
        const availableFlex = maxEnvelopeCeiling - sumFloors

        // Margem desejada pela IA acima dos pisos para cada categoria:
        let sumDesiredFlex = 0
        const desiredFlexMap: Record<string, number> = {}
        for (const cat of Object.keys(suggestedBudgets)) {
          const floor = floorsMap[cat] || 0
          const desired = Math.max(0, suggestedBudgets[cat] - floor)
          desiredFlexMap[cat] = desired
          sumDesiredFlex += desired
        }

        let newTotal = 0
        let largestFlexCat = ''
        let largestFlexVal = -1

        for (const cat of Object.keys(suggestedBudgets)) {
          const floor = floorsMap[cat] || 0
          const desired = desiredFlexMap[cat] || 0
          const scaledFlex = sumDesiredFlex > 0
            ? Math.floor(((desired / sumDesiredFlex) * availableFlex) / 10) * 10
            : 0
          const finalVal = floor + scaledFlex
          suggestedBudgets[cat] = finalVal
          newTotal += finalVal

          if (desired > largestFlexVal) {
            largestFlexVal = desired
            largestFlexCat = cat
          }
        }

        // Distribui o resíduo da dezena na categoria com maior folga flexível
        const diff = Math.floor((maxEnvelopeCeiling - newTotal) / 10) * 10
        if (largestFlexCat && diff > 0) {
          suggestedBudgets[largestFlexCat] = suggestedBudgets[largestFlexCat] + diff
          newTotal += diff
        }

        totalSuggested = newTotal
      }
    }
  }

  const incomeCoveragePct = effectiveExpectedIncome > 0
    ? round2((totalSuggested / effectiveExpectedIncome) * 100)
    : 0

  return {
    suggestedBudgets,
    totalSuggested,
    incomeCoveragePct,
    categoryAverages,
    expectedIncome: effectiveExpectedIncome,
    isCappedToIncome,
  }
}

/**
 * Detecta categorias estouradas e encontra categorias com saldo sobrando no mês para compensar.
 * Utiliza estritamente committedAmount (spent + pending) e realRemaining para nunca retirar
 * valores que já estão comprometidos para contas a vencer.
 */
export function suggestBudgetRebalance(
  report: CategoryBudgetReport,
): {
  canRebalance: boolean
  deficitTotal: number
  surplusTotal: number
  suggestions: BudgetRebalanceSuggestion[]
} {
  // Uma categoria só é elegível para socorro se tiver estouro real >= R$ 1,00
  const overspent = report.items.filter((i) => {
    if (i.limit <= 0 || /investimento/i.test(i.category)) return false
    const committed = i.committedAmount ?? i.spent
    return (committed - i.limit) >= 1.00
  })

  // Só retira de categorias variáveis que tenham folga real livre (realRemaining) >= R$ 10,00
  const surplus = report.items.filter((i) => {
    if (i.limit <= 0 || i.isFixedCategory || /investimento/i.test(i.category)) return false
    const freeSurplus = i.realRemaining !== undefined ? i.realRemaining : Math.max(0, i.limit - i.spent)
    return freeSurplus >= 10
  })

  const deficitTotal = round2(
    overspent.reduce((acc, i) => {
      const committed = i.committedAmount ?? i.spent
      return acc + Math.max(0, committed - i.limit)
    }, 0),
  )

  const surplusTotal = round2(
    surplus.reduce((acc, i) => {
      const freeSurplus = i.realRemaining !== undefined ? i.realRemaining : Math.max(0, i.limit - i.spent)
      return acc + freeSurplus
    }, 0),
  )

  const suggestions: BudgetRebalanceSuggestion[] = []

  if (overspent.length === 0 || surplus.length === 0) {
    return {
      canRebalance: false,
      deficitTotal,
      surplusTotal,
      suggestions: [],
    }
  }

  // Cópia dos saldos remanescentes livres para alocação gananciosa (greedy allocation)
  const remainingSurplusMap = new Map<string, number>()
  for (const s of surplus) {
    const freeSurplus = s.realRemaining !== undefined ? s.realRemaining : Math.max(0, s.limit - s.spent)
    remainingSurplusMap.set(s.category, freeSurplus)
  }

  for (const o of overspent) {
    const committed = o.committedAmount ?? o.spent
    let needed = round2(committed - o.limit)

    for (const s of surplus) {
      if (needed <= 0) break
      const avail = remainingSurplusMap.get(s.category) || 0
      if (avail <= 0) continue

      // Arredonda a transferência para valor limpo (múltiplo de R$ 5,00 ou inteiro)
      const rawTransfer = Math.min(needed, avail)
      const transfer = Math.max(5, Math.ceil(rawTransfer / 5) * 5)
      const safeTransfer = Math.min(transfer, avail)

      if (safeTransfer >= 5) {
        suggestions.push({
          overspentCategory: o.category,
          overspentAmount: round2(committed - o.limit),
          compensateFrom: s.category,
          availableSurplus: round2(avail),
          suggestedTransfer: round2(safeTransfer),
        })
        needed = round2(Math.max(0, needed - safeTransfer))
        remainingSurplusMap.set(s.category, round2(Math.max(0, avail - safeTransfer)))
      }
    }
  }

  return {
    canRebalance: suggestions.length > 0,
    deficitTotal,
    surplusTotal,
    suggestions,
  }
}

/**
 * Validador matemático determinístico pós-IA para envelopes orçamentários.
 * Garante que:
 * 1. O teto sugerido NUNCA seja inferior ao que já foi pago + contas a vencer (piso inegociável).
 * 2. A soma dos tetos de despesas NUNCA ultrapasse a renda líquida real disponível no mês.
 * 3. Se os gastos já comprometidos consumirem ou superarem toda a renda disponível,
 *    os tetos são travados estritamente nos pisos reais consumidos e um alerta de déficit é gerado.
 * 4. Toda folga flexível é distribuída proporcionalmente apenas em despesas variáveis em múltiplos de R$ 10.
 */
export function sanitizeAndValidateBudgetPlan(
  plan: SmartBudgetPlan,
  availableIncome: number,
  spentByCategory: Record<string, number> = {},
  pendingByCategory: Record<string, number> = {},
): SmartBudgetPlan {
  const maxAvailable = round2(Math.max(0, Number(availableIncome) || 0))
  const rawAllocs = Array.isArray(plan?.allocations) ? plan.allocations : []

  if (rawAllocs.length === 0) {
    return {
      summaryInsight: plan?.summaryInsight || 'Nenhuma alocação fornecida para calibrar.',
      allocations: [],
    }
  }

  interface ItemCalc {
    category: string
    reason: string
    isInvestment: boolean
    isFixed: boolean
    committed: number
    floor: number
    rawSuggested: number
  }

  const canonSpent: Record<string, number> = {}
  for (const [k, v] of Object.entries(spentByCategory || {})) {
    if (v > 0) {
      const c = normalizeBudgetCategory(k)
      canonSpent[c] = round2((canonSpent[c] || 0) + v)
    }
  }

  const canonPending: Record<string, number> = {}
  for (const [k, v] of Object.entries(pendingByCategory || {})) {
    if (v > 0) {
      const c = normalizeBudgetCategory(k)
      canonPending[c] = round2((canonPending[c] || 0) + v)
    }
  }

  // Deduplica e consolida alocações por categoria canônica
  const allocMap = new Map<string, { rawLimit: number; reason: string }>()
  for (const alloc of rawAllocs) {
    const rawCat = (alloc.category || '').trim()
    if (!rawCat || /rendimento|sal[áa]rio/i.test(rawCat)) continue
    const canonCat = normalizeBudgetCategory(rawCat)
    if (!canonCat) continue

    const prev = allocMap.get(canonCat)
    const curLimit = Number(alloc.suggestedLimit) || 0
    const curReason = (alloc.reason || '').trim()

    if (!prev) {
      allocMap.set(canonCat, { rawLimit: curLimit, reason: curReason })
    } else {
      const maxLimit = Math.max(prev.rawLimit, curLimit)
      const chosenReason = curReason && !/(?:sem|nenhum|zerad|n[aã]o)/i.test(curReason)
        ? curReason
        : prev.reason
      allocMap.set(canonCat, { rawLimit: maxLimit, reason: chosenReason })
    }
  }

  // Assegura que TODAS as categorias com gastos ou pendências comprometidas estejam presentes
  const allCommittedCategories = new Set([
    ...Object.keys(canonSpent),
    ...Object.keys(canonPending),
  ])
  for (const cat of allCommittedCategories) {
    if (!cat || /rendimento|sal[áa]rio/i.test(cat)) continue
    const canonCat = normalizeBudgetCategory(cat)
    const committed = round2((canonSpent[canonCat] || 0) + (canonPending[canonCat] || 0))
    if (committed > 0 && !allocMap.has(canonCat)) {
      const pending = canonPending[canonCat] || 0
      const minFloor = Math.ceil(committed / 10) * 10
      allocMap.set(canonCat, {
        rawLimit: minFloor,
        reason: pending > 0
          ? `Garante os ${formatBRL(committed)} já comprometidos no mês (${formatBRL(pending)} a vencer).`
          : `Garante os ${formatBRL(committed)} já pagos no mês com margem de segurança.`,
      })
    }
  }

  let totalFloorExpenses = 0
  const items: ItemCalc[] = []

  for (const [canonCat, data] of allocMap.entries()) {
    const isInv = /investimento/i.test(canonCat)
    const isFix = FIXED_MONTHLY_CATEGORIES.has(canonCat)
    const spent = Math.max(0, Number(canonSpent[canonCat]) || 0)
    const pending = Math.max(0, Number(canonPending[canonCat]) || 0)
    const committed = round2(spent + pending)

    // Piso: múltiplo de 10 cobrindo todo o comprometido (mínimo 0)
    const floor = isInv ? 0 : Math.ceil(committed / 10) * 10
    const rawSuggested = Math.max(floor, Math.ceil((data.rawLimit || floor) / 10) * 10)

    if (!isInv) {
      totalFloorExpenses += floor
    }

    let cleanReason = data.reason
    if (committed > 0) {
      const isHallucinated =
        data.rawLimit < committed ||
        /(?:sem|nenhum|zerad|n[aã]o h[aá]|n[aã]o possui)\s*(?:compromisso|gasto|despesa|conta|pend[eê]ncia|sa[ií]da|movimenta[cç][aã]o)?/i.test(cleanReason) ||
        !cleanReason
      if (isHallucinated) {
        cleanReason = pending > 0
          ? `Garante os ${formatBRL(committed)} já comprometidos no mês (${formatBRL(pending)} a vencer).`
          : `Garante os ${formatBRL(committed)} já pagos no mês com margem de segurança.`
      }
    } else if (!cleanReason) {
      cleanReason = isInv
        ? 'Meta de investimento e construção de patrimônio (50/30/20).'
        : 'Sem compromissos no momento, alocação preventiva de segurança.'
    }

    items.push({
      category: canonCat,
      reason: cleanReason,
      isInvestment: isInv,
      isFixed: isFix,
      committed,
      floor,
      rawSuggested,
    })
  }

  // 2. Se os pisos das despesas já consom ou superam a renda líquida disponível:
  if (totalFloorExpenses >= maxAvailable && maxAvailable > 0) {
    const calibratedAllocs: SmartBudgetAllocation[] = items.map((item) => ({
      category: item.category,
      suggestedLimit: item.isInvestment ? 0 : item.floor,
      reason: item.isInvestment
        ? 'Aportes pausados: gastos essenciais e compromissos consom toda a renda do mês.'
        : (item.committed > 0
            ? `Travado no piso real de ${formatBRL(item.committed)} já comprometido neste mês.`
            : 'Teto mínimo de segurança mantido.'),
    }))

    const deficit = round2(totalFloorExpenses - maxAvailable)
    const deficitMsg = deficit > 0
      ? `Atenção: Seus gastos e contas já comprometidos (${formatBRL(totalFloorExpenses)}) excedem a renda disponível (${formatBRL(maxAvailable)}) em ${formatBRL(deficit)}. Os envelopes foram travados estritamente no piso real já consumido para evitar endividamento adicional.`
      : `Atenção: Seus gastos já comprometidos consom exatamente 100% da sua renda disponível do mês (${formatBRL(maxAvailable)}). Os tetos foram travados nos pisos reais já consumidos sem margem flexível.`

    return {
      summaryInsight: deficitMsg,
      allocations: calibratedAllocs,
    }
  }

  // Se a renda disponível for 0 ou negativa:
  if (maxAvailable <= 0) {
    const calibratedAllocs: SmartBudgetAllocation[] = items.map((item) => ({
      category: item.category,
      suggestedLimit: item.floor,
      reason: item.floor > 0
        ? `Travado no gasto já realizado de ${formatBRL(item.committed)}.`
        : 'Sem renda disponível registrada para alocação.',
    }))
    return {
      summaryInsight: 'Renda líquida disponível não identificada ou zerada. Envelopes travados no piso dos gastos já ocorridos.',
      allocations: calibratedAllocs,
    }
  }

  // 3. Há folga disponível acima dos pisos de despesas (maxAvailable > totalFloorExpenses):
  const availableFlex = maxAvailable - totalFloorExpenses

  // Separa despesas de investimentos
  const expenseItems = items.filter((i) => !i.isInvestment)
  const investmentItem = items.find((i) => i.isInvestment)

  let targetInvestment = 0
  if (investmentItem) {
    const rawInv = investmentItem.rawSuggested
    const ideal20pct = Math.floor((maxAvailable * 0.20) / 10) * 10
    targetInvestment = Math.min(availableFlex, rawInv > 0 ? rawInv : ideal20pct)
    if (availableFlex - targetInvestment < 50 && expenseItems.some((e) => !e.isFixed)) {
      targetInvestment = Math.max(0, Math.floor((availableFlex * 0.5) / 10) * 10)
    }
  }

  const flexForExpenses = availableFlex - targetInvestment

  let sumDesiredFlex = 0
  for (const item of expenseItems) {
    if (!item.isFixed) {
      const desired = Math.max(0, item.rawSuggested - item.floor)
      sumDesiredFlex += desired
    }
  }

  let allocatedExpenseFlex = 0
  let largestFlexItem: ItemCalc | null = null
  let maxDesired = -1

  const calibratedAllocs: SmartBudgetAllocation[] = []

  for (const item of expenseItems) {
    let finalLimit = item.floor

    if (item.isFixed) {
      finalLimit = item.floor > 0 ? item.floor : Math.min(flexForExpenses, item.rawSuggested)
    } else {
      const desired = Math.max(0, item.rawSuggested - item.floor)
      const share = sumDesiredFlex > 0
        ? Math.floor(((desired / sumDesiredFlex) * flexForExpenses) / 10) * 10
        : (expenseItems.length > 0 ? Math.floor((flexForExpenses / expenseItems.length) / 10) * 10 : 0)

      finalLimit = item.floor + share
      allocatedExpenseFlex += share

      if (desired > maxDesired) {
        maxDesired = desired
        largestFlexItem = item
      }
    }

    calibratedAllocs.push({
      category: item.category,
      suggestedLimit: finalLimit,
      reason: item.reason || (item.isFixed ? 'Teto de custo fixo contratual.' : 'Calibrado com base no histórico e folga real.'),
    })
  }

  const expenseResidue = Math.floor((flexForExpenses - allocatedExpenseFlex) / 10) * 10
  if (largestFlexItem && expenseResidue > 0) {
    const targetAlloc = calibratedAllocs.find((a) => a.category === largestFlexItem!.category)
    if (targetAlloc) {
      targetAlloc.suggestedLimit += expenseResidue
      allocatedExpenseFlex += expenseResidue
    }
  }

  if (investmentItem) {
    const finalInvestment = Math.max(0, maxAvailable - (totalFloorExpenses + allocatedExpenseFlex))
    calibratedAllocs.push({
      category: investmentItem.category,
      suggestedLimit: finalInvestment,
      reason: finalInvestment > 0
        ? `Meta de aporte protegida (R$ ${formatBRL(finalInvestment)} garantidos no saldo disponível).`
        : 'Sem margem para investimentos este mês após cobrir compromissos essenciais.',
    })
  }

  return {
    summaryInsight: plan.summaryInsight || 'Plano de envelopes calibrado com sucesso respeitando 100% da sua renda líquida disponível.',
    allocations: calibratedAllocs,
  }
}

export interface FullInstallmentRefundResult {
  toDeleteIds: string[]
  totalPaidAmount: number
  refundTransaction: Transaction | null
}

/**
 * Calcula o estorno integral de uma compra parcelada:
 * - Cancela e lista para exclusão todas as parcelas futuras e pendentes (status !== 'paid').
 * - Se houver parcelas já pagas (status === 'paid'), gera um lançamento consolidado de crédito/estorno
 *   com o valor total já desembolsado pelo usuário na competência desejada.
 * - Se nenhuma parcela foi paga ainda, cancela e exclui todo o parcelamento sem necessidade de crédito residual.
 */
export function calculateFullInstallmentRefund(
  groupTxs: Transaction[],
  currentUser: { uid: string; displayName?: string },
  todayIso: string = todayKey(),
  targetCompetenceMonth?: string
): FullInstallmentRefundResult {
  if (!groupTxs || groupTxs.length === 0) {
    return { toDeleteIds: [], totalPaidAmount: 0, refundTransaction: null }
  }

  const paidTxs = groupTxs.filter((t) => t.status === 'paid')
  const futureTxs = groupTxs.filter((t) => t.status !== 'paid')

  // Se nenhuma parcela foi paga, cancela o grupo integralmente
  if (paidTxs.length === 0) {
    return {
      toDeleteIds: groupTxs.map((t) => t.id),
      totalPaidAmount: 0,
      refundTransaction: null,
    }
  }

  const toDeleteIds = futureTxs.map((t) => t.id)
  const totalPaidAmount = round2(paidTxs.reduce((sum, t) => sum + effectiveAmount(t), 0))

  let refundTransaction: Transaction | null = null
  if (totalPaidAmount > 0) {
    const baseTx = paidTxs[0]
    const cleanDesc = baseTx.description.replace(/^Estorno:\s*/i, '').trim()
    const baseRefund = createRefundForTransaction(baseTx, totalPaidAmount, currentUser, todayIso)
    refundTransaction = {
      ...baseRefund,
      description: `Estorno Integral: ${stripInstallmentSuffix(cleanDesc)}`,
      competenceMonth: targetCompetenceMonth || currentMonthKey(),
      installmentGroupId: baseTx.installmentGroupId,
    }
  }

  return {
    toDeleteIds,
    totalPaidAmount,
    refundTransaction,
  }
}
