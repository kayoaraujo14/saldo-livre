/**
 * recurringBills.ts
 * Motor de domínio e regras de negócio para contas recorrentes no Backend (Cloud Functions).
 * Garante paridade contratual com web/src/lib/finance.ts e Android FinanceEngine.kt.
 */

export interface Allocation {
  type: 'PLATFORM_USER' | 'EXTERNAL_CONTACT'
  uid?: string
  name: string
  amount: number
  reimbursed?: boolean
}

export interface RecurringBillData {
  ownerUid?: string
  title?: string
  description?: string
  category?: string
  subcategory?: string | null
  dueDay?: number | string
  amount?: number | string
  defaultAmount?: number | string
  estimationRule?: 'fixed' | 'last_month' | 'average_3' | 'manual'
  active?: boolean
  splitType?: 'mine' | 'split_50' | 'partner' | 'friend' | 'custom'
  paymentMethod?: 'pix' | 'bill' | 'credit_card' | 'cash' | 'transfer' | 'debit'
  cardId?: string | null
  type?: 'expense' | 'income' | 'investment'
  startMonth?: string | null
  endMonth?: string | null
  visibleToUids?: string[]
}

export interface AutoLaunchedTransactionPayload {
  description: string
  category: string
  subcategory: string | null
  type: 'expense' | 'income' | 'investment'
  paymentMethod: 'pix' | 'bill' | 'credit_card' | 'cash' | 'transfer' | 'debit'
  status: 'estimated' | 'pending' | 'paid' | 'reconciled'
  estimatedAmount: number
  actualAmount: number | null
  totalAmount: number
  amount: number
  date: string
  dueDate: string
  competenceMonth: string
  month: string
  createdByUid: string
  paidByUid: string
  visibleToUids: string[]
  splitType: string
  recurringBillId: string
  cardId: string | null
  allocations: Allocation[]
  userImpact: Record<string, number>
  isAutoLaunched: boolean
}

/**
 * Arredonda para 2 casas decimais com precisão
 */
export function round2(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100
}

/**
 * Retorna a chave do mês no formato YYYY-MM
 */
export function getMonthKey(date: Date = new Date()): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}`
}

/**
 * Desloca a chave do mês por N meses (positivo ou negativo)
 */
export function shiftMonth(monthKey: string, delta: number): string {
  const [yStr, mStr] = monthKey.split('-')
  const y = parseInt(yStr, 10)
  const m = parseInt(mStr, 10) - 1 + delta
  const date = new Date(Date.UTC(y, m, 1))
  const newY = date.getUTCFullYear()
  const newM = String(date.getUTCMonth() + 1).padStart(2, '0')
  return `${newY}-${newM}`
}

/**
 * Calcula a data de vencimento no formato YYYY-MM-DD ajustada ao limite de dias do mês
 */
export function dateInMonth(monthKey: string, rawDueDay?: number | string): string {
  const [yStr, mStr] = monthKey.split('-')
  const y = parseInt(yStr, 10)
  const m = parseInt(mStr, 10)
  // Último dia do mês
  const lastDay = new Date(Date.UTC(y, m, 0)).getUTCDate()
  const parsedDay = Number(rawDueDay) || 1
  const clampedDay = Math.min(Math.max(1, parsedDay), lastDay)
  return `${monthKey}-${String(clampedDay).padStart(2, '0')}`
}

/**
 * Verifica se a conta recorrente está ativa e dentro da janela de competência (startMonth e endMonth)
 */
export function isBillEligibleForMonth(
  bill: { active?: boolean; startMonth?: string | null; endMonth?: string | null },
  monthKey: string
): boolean {
  if (bill.active === false) return false
  if (bill.startMonth && monthKey < bill.startMonth) return false
  if (bill.endMonth && monthKey > bill.endMonth) return false
  return true
}

/**
 * Constrói o payload completo de Transação para autolançamento da conta recorrente,
 * com 100% de paridade com o modelo canônico de Transaction (Web e Android).
 */
export function buildAutoLaunchTransaction(args: {
  bill: RecurringBillData
  billId: string
  currentMonth: string
  prevTxAmount?: number | null
}): AutoLaunchedTransactionPayload {
  const { bill, billId, currentMonth, prevTxAmount } = args
  const ownerUid = bill.ownerUid || ''

  // 1. Resolução do valor (respeitando regra last_month quando disponível)
  let baseAmount = Number(bill.defaultAmount ?? bill.amount) || 0
  if (bill.estimationRule === 'last_month' && prevTxAmount != null && prevTxAmount > 0) {
    baseAmount = prevTxAmount
  }
  const amount = round2(baseAmount)

  // 2. Data de vencimento com clamping
  const dueDate = dateInMonth(currentMonth, bill.dueDay)

  // 3. Método de pagamento e status
  const isIncome = bill.type === 'income'
  const defaultMethod = isIncome ? 'pix' : bill.cardId ? 'credit_card' : 'bill'
  const paymentMethod = bill.paymentMethod || defaultMethod
  const isCardBill = paymentMethod === 'credit_card' && Boolean(bill.cardId)
  const status = isCardBill ? 'pending' : 'estimated'
  const actualAmount = isCardBill ? amount : null

  // 4. Divisões, alocações e userImpact
  const rawVisible = Array.isArray(bill.visibleToUids) ? bill.visibleToUids : []
  const visibleToUids: string[] = rawVisible.length > 0 ? [...rawVisible] : [ownerUid]
  if (ownerUid && !visibleToUids.includes(ownerUid)) {
    visibleToUids.unshift(ownerUid)
  }

  const splitType = bill.splitType || 'mine'
  let allocations: Allocation[] = []
  const userImpact: Record<string, number> = {}

  if (splitType === 'split_50' && visibleToUids.length >= 2) {
    const half = round2(amount / 2)
    const partnerUid = visibleToUids.find((u) => u !== ownerUid) || visibleToUids[1]
    const partnerHalf = round2(amount - half)

    allocations = [
      {
        type: 'PLATFORM_USER',
        uid: ownerUid,
        name: 'Você',
        amount: half,
        reimbursed: true,
      },
      {
        type: 'PLATFORM_USER',
        uid: partnerUid,
        name: 'Parceiro',
        amount: partnerHalf,
        reimbursed: false,
      },
    ]
    userImpact[ownerUid] = half
    userImpact[partnerUid] = partnerHalf
  } else {
    allocations = [
      {
        type: 'PLATFORM_USER',
        uid: ownerUid,
        name: 'Você',
        amount,
        reimbursed: true,
      },
    ]
    userImpact[ownerUid] = amount
  }

  return {
    description: bill.description || bill.title || 'Conta Recorrente',
    category: isIncome ? (bill.category || 'Receita') : (bill.category || 'Outros'),
    subcategory: bill.subcategory || null,
    type: bill.type || 'expense',
    paymentMethod,
    status,
    estimatedAmount: amount,
    actualAmount,
    totalAmount: amount,
    amount,
    date: dueDate,
    dueDate,
    competenceMonth: currentMonth,
    month: currentMonth,
    createdByUid: ownerUid,
    paidByUid: ownerUid,
    visibleToUids,
    splitType,
    recurringBillId: billId,
    cardId: bill.cardId || null,
    allocations,
    userImpact,
    isAutoLaunched: true,
  }
}

/**
 * Calcula os limites de corte (Date e epoch ms numérico) para expurgo de activity_logs
 */
export function getActivityLogCutoff(
  now: Date = new Date(),
  retentionDays = 180
): { cutoffDate: Date; cutoffMs: number } {
  const cutoffDate = new Date(now.getTime())
  cutoffDate.setDate(cutoffDate.getDate() - retentionDays)
  return {
    cutoffDate,
    cutoffMs: cutoffDate.getTime(),
  }
}

/**
 * Verifica se um valor de timestamp de activity_log (number epoch ms ou Date/Timestamp) expirou
 */
export function isActivityLogExpired(
  rawTimestamp: unknown,
  cutoffMs: number
): boolean {
  if (typeof rawTimestamp === 'number' && Number.isFinite(rawTimestamp)) {
    return rawTimestamp < cutoffMs
  }
  if (rawTimestamp instanceof Date) {
    return rawTimestamp.getTime() < cutoffMs
  }
  if (
    rawTimestamp &&
    typeof rawTimestamp === 'object' &&
    'toMillis' in rawTimestamp &&
    typeof (rawTimestamp as { toMillis: () => number }).toMillis === 'function'
  ) {
    return (rawTimestamp as { toMillis: () => number }).toMillis() < cutoffMs
  }
  return false
}
