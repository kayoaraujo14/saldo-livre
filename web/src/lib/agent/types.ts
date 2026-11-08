import type {
  Card,
  MonthClosure,
  NewTransaction,
  PendingBatchTransaction,
  RecurringBill,
  Transaction,
  UserProfile,
  PublicUserProfile,
} from '@/types'
import type { Party } from '../finance'
import type { ToolExecutor, ToolName, ToolResult } from '../gemini'

export interface AgentEnv {
  getTransactions: () => Transaction[]
  getCards: () => Card[]
  getRecurring?: () => RecurringBill[]
  getFriends?: () => Array<{ uid: string; name: string; email?: string }>
  getProfile?: () => UserProfile | null
  updateProfile?: (patch: Partial<UserProfile>) => Promise<void>
  getClosures?: () => MonthClosure[]
  getPendingTransactions?: () => PendingBatchTransaction[] | undefined
  setPendingTransactions?: (txs: PendingBatchTransaction[]) => void
  lookupUserByEmail?: (email: string) => Promise<PublicUserProfile | UserProfile | null>
  me: Party
  partner: Party | null
  monthKey: string
  requireConfirmation?: boolean
  addTransaction: (tx: NewTransaction) => Promise<string>
  addMany?: (txs: NewTransaction[]) => Promise<void>
  updateTransaction: (id: string, patch: Partial<Transaction>) => Promise<void>
  deleteTransaction: (id: string) => Promise<void>
  addCard?: (card: Partial<Card>) => Promise<string>
  updateCard?: (id: string, patch: Partial<Card>) => Promise<void>
  deleteCard?: (id: string) => Promise<void>
  addRecurring?: (bill: Partial<RecurringBill>) => Promise<string>
  updateRecurring?: (id: string, patch: Partial<RecurringBill>) => Promise<void>
  deleteRecurring?: (id: string) => Promise<void>
  addFriend?: (email: string) => Promise<void>
  respondFriend?: (id: string, accept: boolean) => Promise<void>
}

export interface UserRule {
  id: string
  rule: string
  category?: string
  createdAt: number
}

export type { ToolExecutor, ToolName, ToolResult }

