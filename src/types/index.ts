// ============================================================
// Modelo de dados (espelha as coleções do Cloud Firestore)
// ============================================================

export type TransactionType = 'income' | 'expense'
export type PaymentMethod = 'pix' | 'credit_card' | 'debit' | 'bill'
export type TransactionStatus = 'estimated' | 'pending' | 'paid'
export type EstimationRule = 'fixed' | 'last_month' | 'avg_3_months'

/** Tipo de divisão SEMPRE relativo a quem está visualizando/criando. */
export type SplitType = 'mine' | 'split_50' | 'partner' | 'friend' | 'external'

export const CATEGORIES = [
  'Moradia',
  'Alimentação',
  'Transporte',
  'Saúde',
  'Lazer',
  'Assinaturas',
  'Cartão Terceiros',
  'Receita',
  'Outros',
] as const
export type Category = (typeof CATEGORIES)[number]

/** Coleção `users` */
export interface UserProfile {
  uid: string
  email: string
  displayName: string
  photoURL: string | null
  monthlyIncome: number
}

export interface SharedUser {
  uid: string
  email: string
  name: string
}

/** Coleção `friendships` (Rede de Amizades) */
export interface Friendship {
  id: string
  requesterUid: string
  requesterEmail: string
  requesterName: string
  recipientUid: string
  recipientEmail: string
  recipientName: string
  status: 'pending' | 'accepted' | 'rejected'
  createdAt: number
}

/** Coleção `cards` */
export interface Card {
  id: string
  ownerUid: string
  ownerName: string
  cardName: string
  closingDay: number
  dueDay: number
  sharedWithUsers: SharedUser[]
  /** Campo derivado para permitir query `array-contains` no Firestore */
  sharedWithUids: string[]
  frequentThirdParties: string[]
  color?: string
}

/** Coleção `recurring_bills` */
export interface RecurringBill {
  id: string
  ownerUid: string
  description: string
  category: Category
  dueDay: number
  defaultAmount: number
  estimationRule: EstimationRule
  active: boolean
  /** Extensão opcional: divisão padrão ao gerar (ex: aluguel 50/50) */
  splitType?: SplitType
  paymentMethod?: PaymentMethod
  /** 'expense' (padrão) ou 'income' para receitas recorrentes (salário, aluguel recebido, freela) */
  type?: TransactionType
  /** Nova Feature 1: Limite e parcelamento em modelos recorrentes */
  startMonth?: string // YYYY-MM
  endMonth?: string // YYYY-MM (null/undefined se for contínua por prazo indeterminado)
  installmentsCount?: number // Quantidade total de parcelas / meses
}

export interface Allocation {
  type: 'PLATFORM_USER' | 'EXTERNAL'
  uid?: string
  name: string
  amount: number
  reimbursed: boolean
}

/** Coleção `transactions` */
export interface Transaction {
  id: string
  createdByUid: string
  paidByUid: string
  cardId?: string | null
  description: string
  category: Category
  type: TransactionType
  paymentMethod: PaymentMethod
  status: TransactionStatus
  estimatedAmount: number | null
  actualAmount: number | null
  totalAmount: number
  dueDate: string // YYYY-MM-DD
  competenceMonth: string // YYYY-MM
  visibleToUids: string[]
  userImpact: Record<string, number>
  allocations: Allocation[]
  /** Origem: molde recorrente que gerou esta linha */
  recurringBillId?: string | null
  /** Reserva de montante em um cartão (anti-duplicidade) */
  reserveCardId?: string | null
  createdAt?: number

  /** Nova Feature 2: Gasto de amigo no cartão com aceite prévio */
  assignedFriendUid?: string | null
  assignedFriendName?: string | null
  acceptanceStatus?: 'pending_approval' | 'accepted' | 'rejected' | null
  /** Percentual (1–100) da despesa que cabe ao amigo. Ausente = 100% (legado). */
  friendSharePct?: number | null
  respondedAt?: number | null

  installmentGroupId?: string | null
  installmentNumber?: number | null
  installmentsTotal?: number | null
}

export interface FriendSplitInput {
  uid: string
  name: string
  pct?: number | null
}

export interface InstallmentGroupUpdateInput {
  description: string
  amount?: number
  category?: Category
  paymentMethod?: PaymentMethod
  cardId?: string | null
  splitType?: SplitType
  externalName?: string
  friend?: FriendSplitInput
  date?: string
  status?: TransactionStatus
}

export type NewTransaction = Omit<Transaction, 'id'>

export interface MonthSummary {
  income: number
  paid: number
  committed: number
  committedEstimated: number
  committedPending: number
  freeBalance: number
  daysRemaining: number
  daysInMonth: number
  dailyLimit: number | null
  monthState: 'past' | 'current' | 'future'
  receivableTotal: number
  receivableFromPartner: number
  receivableFromExternal: number
  receivableFromFriends: number
  payableToOthers: number
  byCategory: { category: string; amount: number }[]
}

export interface FutureMonthProjection {
  monthKey: string // YYYY-MM
  label: string // Outubro 2026
  projectedIncome: number
  projectedExpense: number
  projectedFreeBalance: number
  daysInMonth: number
  projectedDailyLimit: number | null
  isGenerated: boolean
  incomeCount: number
  expenseCount: number
}

export interface ReceivableItem {
  transactionId: string
  allocationIndex: number
  description: string
  dueDate: string
  name: string
  type: 'PLATFORM_USER' | 'EXTERNAL'
  amount: number
  cardName?: string
  acceptanceStatus?: 'pending_approval' | 'accepted' | 'rejected' | null
  friendSharePct?: number | null
}

export interface ActionCardData {
  kind: 'created' | 'updated' | 'report' | 'error'
  title: string
  lines: { label: string; value: string; tone?: 'positive' | 'negative' | 'neutral' }[]
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  actions?: ActionCardData[]
  pending?: boolean
  error?: boolean
}
