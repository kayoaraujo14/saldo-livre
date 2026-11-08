import type { User } from 'firebase/auth'
import type {
  Allocation,
  Card,
  Friendship,
  FriendSplitInput,
  InstallmentGroupUpdateInput,
  MonthClosure,
  MonthSummary,
  NewTransaction,
  PendingBatchTransaction,
  PendingChargeGroup,
  PendingRecurringCandidate,
  RecurringBill,
  SplitType,
  Transaction,
  TransactionStatus,
  UserProfile,
  PublicUserProfile,
  CategoryBudgetReport,
} from '@/types'
import type { Party } from '@/lib/finance'
import type { ToolExecutor } from '@/lib/gemini'
import type { UserRule } from '@/lib/agentTools'

export type Mode = 'demo' | 'firebase'

export interface DemoState {
  users: UserProfile[]
  cards: Card[]
  recurring: RecurringBill[]
  transactions: Transaction[]
  friendships: Friendship[]
  monthClosures?: MonthClosure[]
}

export interface ManualTransactionInput {
  description: string
  amount: number
  category: Transaction['category']
  subcategory?: string | null
  date: string
  type: Transaction['type']
  status: TransactionStatus
  splitType: SplitType
  externalName?: string
  paymentMethod: Transaction['paymentMethod']
  cardId?: string | null
  reserveCardId?: string | null
  assignedFriendUid?: string | null
  assignedFriendName?: string | null
  friendSharePct?: number | null
  customAllocations?: Allocation[]
  isInstallment?: boolean
  installmentsCount?: number
  installmentMode?: 'total' | 'per_installment'
}

export interface FriendItem {
  uid: string
  name: string
  email: string
  friendshipId: string
}

export interface AutoLaunchedNotice {
  date: string
  createdCount: number
  bills: Array<{ id: string; description: string; amount: number; type: string; dueDay: number }>
}

export interface FinanceContextValue {
  autoLaunchedNotice: AutoLaunchedNotice | null
  dismissAutoLaunchNotice: () => void
  pendingRecurringLaunches: PendingRecurringCandidate[]
  confirmLaunchRecurring: (
    candidate: PendingRecurringCandidate,
    options?: { asPaid?: boolean; customAmount?: number },
  ) => Promise<void>
  confirmLaunchAllRecurring: (candidates?: PendingRecurringCandidate[], asPaid?: boolean) => Promise<void>
  launchBillForCurrentMonth: (
    bill: RecurringBill,
    options?: { asPaid?: boolean; customAmount?: number },
  ) => Promise<void>
  linkManualTransactionToRecurring: (candidateId: string, manualTxId: string) => Promise<void>
  skipRecurringForMonth: (candidateId: string) => void
  mode: Mode
  firebaseAvailable: boolean
  authLoading: boolean
  isLoading: boolean
  isAuthenticated: boolean
  authUser: User | null
  // dados
  users: UserProfile[]
  profile: UserProfile | null
  me: Party | null
  partner: Party | null
  cards: Card[]
  recurring: RecurringBill[]
  transactions: Transaction[]
  monthTransactions: Transaction[]
  summary: MonthSummary | null
  month: string
  setMonth: (m: string) => void
  isFirebaseConfigured: boolean
  // sessão
  signIn: () => Promise<void>
  signOut: () => Promise<void>
  loginAsDemo: (uid: string) => void
  switchProfile: (uid: string) => void
  enterDemo: () => void
  exitDemo: () => void
  resetDemo: () => void
  clearDemoData: () => void
  // transações
  addTransaction: (tx: NewTransaction) => Promise<string>
  addMany: (txs: NewTransaction[]) => Promise<void>
  createManual: (input: ManualTransactionInput) => Promise<string>
  updateTransaction: (id: string, patch: Partial<Transaction>) => Promise<void>
  deleteTransaction: (id: string) => Promise<void>
  deleteInstallmentGroup: (groupId: string) => Promise<number>
  deleteFutureInstallments: (groupId: string, fromInstallmentNumber: number) => Promise<number>
  refundFullInstallmentGroup: (groupId: string, targetMonth?: string) => Promise<{ refundedCount: number; refundAmount: number }>
  anticipateInstallments: (groupId: string, count: number, targetMonth: string, discount?: number) => Promise<{ count: number; discount: number }>
  updateInstallmentGroup: (groupId: string, input: InstallmentGroupUpdateInput) => Promise<number>
  cycleStatus: (tx: Transaction) => Promise<void>
  setStatus: (tx: Transaction, status: TransactionStatus) => Promise<void>
  changeSplit: (
    tx: Transaction,
    splitType: SplitType,
    externalName?: string,
    friend?: FriendSplitInput,
    customAllocations?: Allocation[],
  ) => Promise<void>
  changeAmount: (tx: Transaction, values: { estimatedAmount?: number | null; actualAmount?: number | null }) => Promise<void>
  markReimbursed: (txId: string, allocationIndex: number, value: boolean) => Promise<void>
  generateBills: () => Promise<{ created: number; skipped: number }>
  // cartões / moldes / perfil
  saveCard: (card: Card) => Promise<void>
  deleteCard: (id: string) => Promise<void>
  saveRecurring: (bill: RecurringBill) => Promise<void>
  deleteRecurring: (id: string) => Promise<void>
  updateProfile: (patch: Partial<UserProfile>) => Promise<void>
  lookupUserByEmail: (email: string) => Promise<PublicUserProfile | UserProfile | null>
  // Envelopes Orçamentários
  categoryBudgetReport: CategoryBudgetReport
  setCategoryBudget: (category: string, limit: number, targetMonth?: string) => Promise<void>
  setCategoryBudgets: (budgets: Record<string, number>, targetMonth?: string) => Promise<void>
  rebalanceCategoryBudgets: (fromCategory: string, toCategory: string, amount: number, targetMonth?: string) => Promise<void>
  copyCategoryBudgetsFromMonth: (sourceMonth: string, targetMonth?: string) => Promise<void>
  monthClosures: MonthClosure[]
  saveMonthClosure: (closure: MonthClosure) => Promise<void>
  deleteMonthClosure: (closedMonth: string) => Promise<void>
  closureModalOpen: boolean
  setClosureModalOpen: (open: boolean) => void
  openMonthClosureModal: () => void
  // Rede de Amigos
  friendships: Friendship[]
  friends: FriendItem[]
  pendingFriendRequests: Friendship[]
  pendingChargeRequests: Transaction[]
  pendingChargeGroups: PendingChargeGroup[]
  sendFriendRequest: (email: string) => Promise<{ success: boolean; message: string }>
  respondFriendRequest: (friendshipId: string, accept: boolean) => Promise<void>
  removeFriend: (friendshipId: string) => Promise<void>
  respondChargeRequest: (tx: Transaction, accept: boolean) => Promise<void>
  // agente e regras
  executeTool: ToolExecutor
  pendingBatchTransactions: PendingBatchTransaction[]
  setPendingBatchTransactions: (txs: PendingBatchTransaction[]) => void
  rules: UserRule[]
  addRule: (text: string, category?: string) => Promise<UserRule>
  deleteRule: (id: string) => Promise<void>
}
