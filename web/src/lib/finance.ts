/**
 * Motor de regras financeiras (puro, sem dependências de UI/Firebase).
 * Todas as fórmulas dos KPIs, divisões (allocations/userImpact) e
 * estimativas de contas recorrentes vivem aqui.
 */
import type {
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
  LaunchTrigger,
  PendingRecurringCandidate,
} from '@/types'
import { currentMonthKey, dateInMonth, daysInMonth, monthLabel, normalize, round2, shiftMonth, todayKey } from './utils'
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
      return tx.userImpact?.[uid] ?? 0
    }
    // Pendente ou recusado: amigo ainda NÃO tem o saldo comprometido
    if (uid === tx.assignedFriendUid) return 0
    // Quem lançou tem apenas o seu próprio impacto (a cota do amigo é valor a receber em receivables)
    return tx.userImpact?.[uid] ?? 0
  }

  return tx.userImpact?.[uid] ?? 0
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
      inc += impact
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
  closures: MonthClosure[] = [],
  cards: Card[] = []
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
      income += impact
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

      if (tx.category === 'Investimentos') {
        totalInvested += impact
        const subName = tx.subcategory || 'Outros Investimentos'
        investSubcat.set(subName, (investSubcat.get(subName) ?? 0) + impact)
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

  // Total acumulado investido no histórico do usuário
  let totalInvestedAccumulated = 0
  for (const tx of all || []) {
    if (tx.category === 'Investimentos' && (tx.visibleToUids || []).includes(uid)) {
      const imp = impactFor(tx, uid, all)
      if (imp > 0) totalInvestedAccumulated += imp
    }
  }

  const committed = committedEstimated + committedPending
  const rawFreeBalance = round2(income - (paid + committed))
  const state = getMonthState(monthKey)

  let carryoverAmount = 0
  let previousMonthDeficit = 0
  let cushionAmount = 0
  let pendingClosureSurplus: number | null = null
  let pendingClosureMonth: string | null = null

  if (state !== 'future') {
    const prevMonthKey = shiftMonth(monthKey, -1)
    const prevClosure = (closures || []).find((c) => c.closedMonth === prevMonthKey && c.uid === uid)

    if (prevClosure) {
      carryoverAmount = round2(prevClosure.carryoverToNextMonth)
      cushionAmount = round2(prevClosure.cushionAmount)
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
  }

  const effectiveAvailable = income + carryoverAmount - previousMonthDeficit - cushionAmount
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
    daysRemaining,
    daysInMonth: daysInMonth(monthKey),
    dailyLimit: daysRemaining > 0 ? round2(freeBalance / daysRemaining) : null,
    monthState: state,
    receivableFromPartner: round2(receivableFromPartner),
    receivableFromExternal: round2(receivableFromExternal),
    receivableFromFriends: round2(receivableFromFriends),
    receivableTotal: round2(receivableFromPartner + receivableFromExternal + receivableFromFriends),
    payableToOthers: round2(payableToOthers),
    totalInvested: round2(totalInvested),
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

  // 2. Similaridade na descrição
  const descMatch = monthTxs.find((t) => {
    const txDesc = normalizeText(t.description)
    return txDesc.includes(normBill) || normBill.includes(txDesc)
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
    created.push({
      createdByUid: me.uid,
      paidByUid: me.uid,
      cardId: bill.cardId || null,
      description: bill.description,
      category: isIncome ? 'Receita' : bill.category,
      subcategory: bill.subcategory || null,
      type: bill.type ?? 'expense',
      paymentMethod: bill.paymentMethod ?? (isIncome ? 'pix' : bill.cardId ? 'credit_card' : 'bill'),
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
    const monthTxs = (all || []).filter((t) => t.competenceMonth === monthKey && (t.visibleToUids || []).includes(uid))

    let projectedIncome = 0
    let projectedExpense = 0
    let incomeCount = 0
    let expenseCount = 0

    // 1. Sempre inclui transações reais agendadas para aquele mês (ex: parcelas de compras com cartão ou contas já lançadas)
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

    // 2. Projeta o salário e as contas fixas ativas que ainda NÃO foram lançadas como transação neste mês
    const hasSalary =
      monthTxs.some((t) => t.recurringBillId === `salary_${uid}`) ||
      Boolean(findMatchingManualSalary(monthTxs, monthKey, profile?.monthlyIncome))
    if (!hasSalary && profile && profile.monthlyIncome > 0) {
      projectedIncome += profile.monthlyIncome
      incomeCount++
    }

    let ungeneratedCount = 0
    const activeBills = bills.filter((b) => b.active && b.ownerUid === uid)

    for (const bill of activeBills) {
      if (monthTxs.some((t) => t.recurringBillId === bill.id)) continue

      // Limite temporal de vigência / parcelas
      if (bill.startMonth && monthKey < bill.startMonth) continue
      if (bill.endMonth && monthKey > bill.endMonth) continue

      ungeneratedCount++
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

    const isGenerated = ungeneratedCount === 0 && (!profile || profile.monthlyIncome <= 0 || hasSalary)

    projectedIncome = round2(projectedIncome)
    projectedExpense = round2(projectedExpense)
    const projectedFreeBalance = round2(projectedIncome - projectedExpense)
    const projectedDailyLimit = dim > 0 ? round2(projectedFreeBalance / dim) : null

    projections.push({
      monthKey,
      label,
      daysInMonth: dim,
      projectedIncome,
      projectedExpense,
      projectedFreeBalance,
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
