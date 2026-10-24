/**
 * Estado global: abstrai a fonte de dados (Modo Demo local x Firestore)
 * e expõe ações de alto nível que aplicam as regras de negócio.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
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
  RecurringBill,
  SplitType,
  Transaction,
  TransactionStatus,
  UserProfile,
} from '@/types'
import {
  amountPatch,
  buildSplit,
  calculateCardInvoiceDates,
  clampPct,
  computeSummary,
  friendPctOf,
  friendShareOf,
  generateMonthBills,
  nextStatus,
  resolvePartner,
  statusPatch,
  type Party,
} from '@/lib/finance'
import * as fb from '@/lib/firebase'
import { DEMO_CARDS, DEMO_RECURRING, DEMO_USERS, buildDemoTransactions } from '@/lib/mockData'
import { createToolExecutor, getLearnedRules, saveLearnedRules, addLearnedRule, removeLearnedRule, type UserRule } from '@/lib/agentTools'
import type { ToolExecutor } from '@/lib/gemini'
import { currentMonthKey, uid as makeId, shiftDateMonth, shiftMonth, round2, dateInMonth, daysInMonth, monthLabel, formatInstallmentDesc, stripInstallmentSuffix } from '@/lib/utils'

type Mode = 'demo' | 'firebase'

interface DemoState {
  users: UserProfile[]
  cards: Card[]
  recurring: RecurringBill[]
  transactions: Transaction[]
  friendships: Friendship[]
  monthClosures?: MonthClosure[]

}

const DEMO_KEY = 'saldo-livre:demo-state:v3'
const FORCE_DEMO_KEY = 'saldo-livre:force-demo'

function freshDemo(): DemoState {
  const M = currentMonthKey()
  const gId = 'demo_group_curso'
  const kayoParty: Party = { uid: DEMO_USERS[0].uid, name: 'Kayo' }
  const marinaParty: Party = { uid: DEMO_USERS[1].uid, name: 'Marina' }

  const split = buildSplit({
    splitType: 'friend',
    total: 120,
    me: kayoParty,
    partner: marinaParty,
    assignedFriend: marinaParty,
    friendSharePct: 50,
    payerUid: DEMO_USERS[0].uid,
    createdByUid: DEMO_USERS[0].uid,
  })

  const sampleInstallments: Transaction[] = [1, 2, 3].map((num) => ({
    id: `tx_demo_install_${num}`,
    createdByUid: DEMO_USERS[0].uid,
    paidByUid: DEMO_USERS[0].uid,
    cardId: 'card_nubank',
    description: `Curso Masterclass Design (${num}/3)`,
    category: 'Outros',
    type: 'expense',
    paymentMethod: 'credit_card',
    status: 'pending',
    estimatedAmount: null,
    actualAmount: 120,
    totalAmount: 120,
    dueDate: dateInMonth(shiftMonth(M, num - 1), 10),
    competenceMonth: shiftMonth(M, num - 1),
    visibleToUids: split.visibleToUids,
    userImpact: split.userImpact,
    allocations: split.allocations,
    assignedFriendUid: DEMO_USERS[1].uid,
    assignedFriendName: 'Marina Costa',
    friendSharePct: 50,
    acceptanceStatus: 'pending_approval',
    installmentGroupId: gId,
    installmentNumber: num,
    installmentsTotal: 3,
    createdAt: Date.now() - 3600000,
  }))

  return {
    users: DEMO_USERS,
    cards: DEMO_CARDS,
    recurring: DEMO_RECURRING,
    transactions: [...buildDemoTransactions(), ...sampleInstallments],
    friendships: [
      {
        id: 'demo_f1',
        requesterUid: DEMO_USERS[0].uid,
        requesterEmail: DEMO_USERS[0].email,
        requesterName: DEMO_USERS[0].displayName,
        recipientUid: DEMO_USERS[1].uid,
        recipientEmail: DEMO_USERS[1].email,
        recipientName: DEMO_USERS[1].displayName,
        status: 'accepted',
        createdAt: Date.now() - 86400000,
      },
    ],
  }
}

function loadDemo(): DemoState {
  const fresh = freshDemo()
  try {
    const raw = localStorage.getItem(DEMO_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return {
        users: Array.isArray(parsed?.users) && parsed.users.length ? parsed.users : fresh.users,
        cards: Array.isArray(parsed?.cards) ? parsed.cards : fresh.cards,
        recurring: Array.isArray(parsed?.recurring) ? parsed.recurring : fresh.recurring,
        transactions: Array.isArray(parsed?.transactions) ? parsed.transactions : fresh.transactions,
        friendships: Array.isArray(parsed?.friendships) ? parsed.friendships : fresh.friendships,
      }
    }
  } catch {
    /* ignora estado corrompido */
  }
  return fresh
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
  // Feature 2: Amigo
  assignedFriendUid?: string | null
  assignedFriendName?: string | null
  /** Percentual (1–100) da despesa cobrado do amigo */
  friendSharePct?: number | null
  customAllocations?: Allocation[]
  // Feature 3: Parcelamento
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

interface FinanceContextValue {
  mode: Mode
  firebaseAvailable: boolean
  authLoading: boolean
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
  lookupUserByEmail: (email: string) => Promise<UserProfile | null>
  monthClosures: MonthClosure[]
  saveMonthClosure: (closure: MonthClosure) => Promise<void>
  deleteMonthClosure: (closedMonth: string) => Promise<void>
  // Rede de Amigos (Feature 2)
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

const FinanceContext = createContext<FinanceContextValue | null>(null)

function firstName(name: string) {
  return name.split(' ')[0] || name
}

export function FinanceProvider({ children }: { children: ReactNode }) {
  const [forceDemo, setForceDemo] = useState(() => localStorage.getItem(FORCE_DEMO_KEY) === '1')
  const mode: Mode = fb.isFirebaseConfigured && !forceDemo ? 'firebase' : 'demo'
  const [month, setMonth] = useState(currentMonthKey())
  const [pendingBatchTransactions, setPendingBatchTransactions] = useState<PendingBatchTransaction[]>([])
  const pendingBatchRef = useRef(pendingBatchTransactions)
  pendingBatchRef.current = pendingBatchTransactions

  // ---------------- Modo Demo ----------------
  const [demo, setDemo] = useState<DemoState>(loadDemo)
  const demoRef = useRef(demo)
  const SESSION_UID_KEY = 'saldo-livre:session-uid'
  const [sessionUid, setSessionUid] = useState<string | null>(() => localStorage.getItem(SESSION_UID_KEY))

  const commitDemo = useCallback((updater: (s: DemoState) => DemoState) => {
    const next = updater(demoRef.current)
    demoRef.current = next
    setDemo(next)
  }, [])

  useEffect(() => {
    if (mode === 'demo') localStorage.setItem(DEMO_KEY, JSON.stringify(demo))
  }, [demo, mode])

  // ---------------- Firebase ----------------
  const [authUser, setAuthUser] = useState<User | null>(null)
  const [authLoading, setAuthLoading] = useState(mode === 'firebase')
  const [fbProfile, setFbProfile] = useState<UserProfile | null>(null)
  const [fbTx, setFbTx] = useState<Transaction[]>([])
  const fbTxRef = useRef<Transaction[]>([])
  const [fbCards, setFbCards] = useState<Card[]>([])
  const [fbRecurring, setFbRecurring] = useState<RecurringBill[]>([])
  const [fbFriendships, setFbFriendships] = useState<Friendship[]>([])
  const [fbRules, setFbRules] = useState<UserRule[]>([])
  const [fbMonthClosures, setFbMonthClosures] = useState<MonthClosure[]>([])

  useEffect(() => {
    if (mode !== 'firebase') {
      setAuthLoading(false)
      return
    }
    setAuthLoading(true)
    let unsub = () => {}
    try {
      unsub = fb.onAuthChange(async (u) => {
        setAuthUser(u)
        if (u) {
          try {
            setFbProfile(await fb.upsertUserProfile(u))
          } catch (e) {
            console.error('Falha ao sincronizar perfil do Firestore:', e)
          }
        } else {
          setFbProfile(null)
          setFbTx([])
          setFbCards([])
          setFbRecurring([])
          setFbFriendships([])
          setFbRules([])
        }
        setAuthLoading(false)
      })
    } catch (err) {
      console.warn('Falha ao inicializar observador do Firebase Auth:', err)
      setAuthLoading(false)
    }

    // Safety timeout: never let the screen hang indefinitely on loading
    const timer = setTimeout(() => {
      setAuthLoading(false)
    }, 3500)

    return () => {
      clearTimeout(timer)
      unsub()
    }
  }, [mode])

  useEffect(() => {
    if (mode !== 'firebase' || !authUser) return
    const uid = authUser.uid
    const unsubs = [
      fb.subscribeUserProfile(uid, (p) => p && setFbProfile(p)),
      fb.subscribeTransactions(uid, (txs) => {
        fbTxRef.current = txs
        setFbTx(txs)
      }),
      fb.subscribeCards(uid, setFbCards),
      fb.subscribeRecurring(uid, setFbRecurring),
      fb.subscribeFriendships(uid, setFbFriendships),
      fb.subscribeMonthClosures(uid, setFbMonthClosures),
      fb.subscribeAgentRules(uid, (rules) => {
        setFbRules(rules)
        saveLearnedRules(uid, rules)
      }),
    ]
    return () => unsubs.forEach((u) => u())
  }, [mode, authUser])

  // ---------------- Visão unificada ----------------
  const transactions = mode === 'demo' ? (demo?.transactions || []) : (fbTx || [])
  const cards = mode === 'demo' ? (demo?.cards || []) : (fbCards || [])
  const recurring = mode === 'demo' ? (demo?.recurring || []) : (fbRecurring || [])
  const friendships = mode === 'demo' ? (demo?.friendships || []) : (fbFriendships || [])
  const monthClosures = mode === 'demo' ? (demo?.monthClosures || []) : (fbMonthClosures || [])
  const currentUid = mode === 'demo' ? sessionUid : (authUser?.uid ?? null)

  const users: UserProfile[] = useMemo(() => {
    if (mode === 'demo') return demo?.users || []
    const map = new Map<string, UserProfile>()
    if (fbProfile) map.set(fbProfile.uid, fbProfile)
    const cardList = Array.isArray(fbCards) ? fbCards : []
    for (const c of cardList) {
      if (!c) continue
      const rawShared = Array.isArray(c.sharedWithUsers)
        ? c.sharedWithUsers
        : (c.sharedWithUsers && typeof c.sharedWithUsers === 'object'
            ? Object.values(c.sharedWithUsers)
            : [])
      for (const item of (rawShared as any[])) {
        if (!item) continue
        const uid = typeof item === 'string' ? item : item.uid
        if (!uid || map.has(uid)) continue
        const email = typeof item === 'object' && item.email ? item.email : ''
        const displayName = typeof item === 'object' && item.name ? item.name : 'Parceiro'
        map.set(uid, {
          uid,
          email,
          displayName,
          photoURL: null,
          monthlyIncome: 0,
        })
      }
    }
    const friendshipsList = Array.isArray(fbFriendships) ? fbFriendships : []
    for (const f of friendshipsList) {
      if (!f) continue
      if (f.requesterUid && !map.has(f.requesterUid)) {
        map.set(f.requesterUid, {
          uid: f.requesterUid,
          email: f.requesterEmail || '',
          displayName: f.requesterName || 'Amigo',
          photoURL: null,
          monthlyIncome: 0,
        })
      }
      if (f.recipientUid && !map.has(f.recipientUid)) {
        map.set(f.recipientUid, {
          uid: f.recipientUid,
          email: f.recipientEmail || '',
          displayName: f.recipientName || 'Amigo',
          photoURL: null,
          monthlyIncome: 0,
        })
      }
    }
    return [...map.values()]
  }, [mode, demo.users, fbProfile, fbCards, fbFriendships])

  const profile: UserProfile | null = useMemo(() => {
    if (!currentUid) return null
    if (mode === 'demo') return demo.users.find((u) => u.uid === currentUid) ?? null
    return fbProfile
  }, [currentUid, mode, demo.users, fbProfile])

  const me: Party | null = useMemo(() => {
    if (!currentUid) return null
    const name = profile?.displayName || authUser?.displayName || 'Usuário'
    return { uid: currentUid, name: firstName(name) }
  }, [currentUid, profile, authUser])

  const partnerParty: Party | null = useMemo(() => {
    if (!currentUid) return null
    return resolvePartner(currentUid, cards, users)
  }, [currentUid, cards, users])

  // ---------------- Amizades & Pendências (Feature 2) ----------------
  const friends: FriendItem[] = useMemo(() => {
    if (!currentUid) return []
    const list: FriendItem[] = []
    for (const f of friendships) {
      if (f.status !== 'accepted') continue
      if (f.requesterUid === currentUid) {
        list.push({ uid: f.recipientUid, name: f.recipientName, email: f.recipientEmail, friendshipId: f.id })
      } else if (f.recipientUid === currentUid) {
        list.push({ uid: f.requesterUid, name: f.requesterName, email: f.requesterEmail, friendshipId: f.id })
      }
    }
    return list
  }, [friendships, currentUid])

  const pendingFriendRequests = useMemo(() => {
    if (!currentUid) return []
    return friendships.filter((f) => f.recipientUid === currentUid && f.status === 'pending')
  }, [friendships, currentUid])

  const pendingChargeRequests = useMemo(() => {
    if (!currentUid) return []
    return transactions.filter(
      (t) => t.assignedFriendUid === currentUid && t.acceptanceStatus === 'pending_approval',
    )
  }, [transactions, currentUid])

  const pendingChargeGroups = useMemo((): PendingChargeGroup[] => {
    if (!currentUid) return []
    const rawPending = transactions.filter(
      (t) => t.assignedFriendUid === currentUid && t.acceptanceStatus === 'pending_approval',
    )

    const groups: PendingChargeGroup[] = []
    const seenGroups = new Set<string>()

    const txList = Array.isArray(rawPending) ? rawPending : []
    for (const tx of txList) {
      if (!tx) continue
      const gId = tx.installmentGroupId
      if (gId) {
        if (seenGroups.has(gId)) continue
        seenGroups.add(gId)

        const items = txList
          .filter((t) => t && t.installmentGroupId === gId)
          .sort((a, b) => (a.installmentNumber ?? 0) - (b.installmentNumber ?? 0))

        const primaryTx = items[0]
        if (!primaryTx) continue
        const baseDesc = stripInstallmentSuffix(primaryTx.description)
        const sender = users.find((u) => u.uid === primaryTx.createdByUid)
        const friendPct = friendPctOf(primaryTx)

        const totalAmount = round2(items.reduce((s, t) => s + (Number(t.totalAmount) || 0), 0))
        const friendShareAmount = round2(items.reduce((s, t) => s + friendShareOf(t), 0))

        const dates = items.map((t) => t.dueDate).filter(Boolean).sort()
        const firstDueDate = dates[0] || primaryTx.dueDate
        const lastDueDate = dates[dates.length - 1] || primaryTx.dueDate

        groups.push({
          id: gId,
          isInstallment: true,
          installmentGroupId: gId,
          installmentsCount: items.length,
          baseDescription: baseDesc,
          cardId: primaryTx.cardId,
          senderUid: primaryTx.createdByUid,
          senderName: sender?.displayName || 'Amigo',
          totalAmount,
          friendShareAmount,
          friendSharePct: friendPct,
          firstDueDate,
          lastDueDate,
          perInstallmentAmount: primaryTx.totalAmount,
          perInstallmentFriendShare: friendShareOf(primaryTx),
          items,
          primaryTx,
        })
      } else {
        const sender = users.find((u) => u.uid === tx.createdByUid)
        const friendPct = friendPctOf(tx)
        const friendShare = friendShareOf(tx)

        groups.push({
          id: tx.id,
          isInstallment: false,
          installmentGroupId: null,
          installmentsCount: 1,
          baseDescription: tx.description,
          cardId: tx.cardId,
          senderUid: tx.createdByUid,
          senderName: sender?.displayName || 'Amigo',
          totalAmount: tx.totalAmount,
          friendShareAmount: friendShare,
          friendSharePct: friendPct,
          firstDueDate: tx.dueDate,
          lastDueDate: tx.dueDate,
          perInstallmentAmount: tx.totalAmount,
          perInstallmentFriendShare: friendShare,
          items: [tx],
          primaryTx: tx,
        })
      }
    }

    return groups
  }, [transactions, currentUid, users])

  const sendFriendRequest = useCallback(
    async (email: string) => {
      const trimmed = email.trim().toLowerCase()
      if (!profile || !me) return { success: false, message: 'Usuário não conectado.' }
      if (trimmed === profile.email.toLowerCase()) {
        return { success: false, message: 'Você não pode adicionar o seu próprio e-mail.' }
      }

      const already = friendships.find(
        (f) =>
          (f.requesterEmail.toLowerCase() === trimmed && f.recipientUid === me.uid) ||
          (f.recipientEmail.toLowerCase() === trimmed && f.requesterUid === me.uid),
      )
      if (already) {
        if (already.status === 'accepted') return { success: false, message: 'Vocês já são amigos conectados!' }
        return { success: false, message: 'Já existe um convite pendente entre vocês.' }
      }

      if (mode === 'demo') {
        const found = demo.users.find((u) => u.email.toLowerCase() === trimmed)
        const recipientName = found ? found.displayName : trimmed.split('@')[0]
        const recipientUid = found ? found.uid : `demo_user_${Date.now()}`
        const newF: Friendship = {
          id: `f_${Date.now()}`,
          requesterUid: me.uid,
          requesterEmail: profile.email,
          requesterName: profile.displayName,
          recipientUid,
          recipientEmail: trimmed,
          recipientName,
          status: 'pending',
          createdAt: Date.now(),
        }
        commitDemo((s) => ({ ...s, friendships: [...(s.friendships || []), newF] }))
        return { success: true, message: `Convite enviado com sucesso para ${recipientName}!` }
      }

      // Firebase mode
      const recipient = await fb.lookupUserByEmail(trimmed)
      if (!recipient) {
        return {
          success: false,
          message: `Nenhum usuário cadastrado com o e-mail ${trimmed}. Peça para seu amigo fazer o primeiro login no Saldo Livre antes!`,
        }
      }

      await fb.fsCreateFriendship(profile, recipient)
      return { success: true, message: `Convite de amizade enviado para ${recipient.displayName}!` }
    },
    [profile, me, friendships, mode, demo.users, commitDemo],
  )

  const respondFriendRequest = useCallback(
    async (friendshipId: string, accept: boolean) => {
      if (mode === 'demo') {
        commitDemo((s) => ({
          ...s,
          friendships: (s.friendships || []).map((f) =>
            f.id === friendshipId ? { ...f, status: accept ? 'accepted' : 'rejected' } : f,
          ),
        }))
        return
      }
      await fb.fsUpdateFriendshipStatus(friendshipId, accept ? 'accepted' : 'rejected')
    },
    [mode, commitDemo],
  )

  const removeFriend = useCallback(
    async (friendshipId: string) => {
      if (mode === 'demo') {
        commitDemo((s) => ({
          ...s,
          friendships: (s.friendships || []).filter((f) => f.id !== friendshipId),
        }))
        return
      }
      await fb.fsDeleteFriendship(friendshipId)
    },
    [mode, commitDemo],
  )

  const respondChargeRequest = useCallback(
    async (tx: Transaction, accept: boolean) => {
      const status: 'accepted' | 'rejected' = accept ? 'accepted' : 'rejected'
      const source = mode === 'demo' ? demoRef.current.transactions : fbTxRef.current
      // Parcelas irmãs que este usuário pode ver (já carregadas via snapshot)
      const ids = tx.installmentGroupId
        ? source
            .filter(
              (t) =>
                t.installmentGroupId === tx.installmentGroupId &&
                t.assignedFriendUid === tx.assignedFriendUid &&
                (!currentUid || (t.visibleToUids || []).includes(currentUid)),
            )
            .map((t) => t.id)
        : []
      if (!ids.includes(tx.id)) ids.push(tx.id)

      if (mode === 'demo') {
        const set = new Set(ids)
        commitDemo((s) => ({
          ...s,
          transactions: s.transactions.map((t) =>
            set.has(t.id) ? { ...t, acceptanceStatus: status, respondedAt: Date.now() } : t,
          ),
        }))
        return
      }
      await fb.fsRespondChargeRequest(ids, accept)
    },
    [mode, commitDemo, currentUid],
  )

  // ---------------- Lançamentos do mês e resumo ----------------
  const monthTransactions = useMemo(() => {
    if (!currentUid) return []
    return transactions.filter((t) => t.competenceMonth === month && (t.visibleToUids || []).includes(currentUid))
  }, [transactions, month, currentUid])

  const summary = useMemo(() => {
    if (!currentUid) return null
    return computeSummary(transactions, currentUid, month, monthClosures)
  }, [transactions, currentUid, month, monthClosures])

  const saveMonthClosure = useCallback(
    async (closure: MonthClosure) => {
      if (mode === 'demo') {
        commitDemo((s) => {
          const list = (s.monthClosures || []).filter(
            (c) => !(c.closedMonth === closure.closedMonth && c.uid === closure.uid),
          )
          const updatedClosures = [...list, closure]
          let updatedTxs = s.transactions

          if (closure.investedAmount > 0) {
            const lastDay = daysInMonth(closure.closedMonth)
            const investDate = dateInMonth(closure.closedMonth, lastDay)
            const investTx: Transaction = {
              id: 'tx_invest_' + Math.random().toString(36).slice(2, 9),
              createdByUid: closure.uid,
              paidByUid: closure.uid,
              visibleToUids: [closure.uid],
              description: `Aporte Sobra ${monthLabel(closure.closedMonth)}`,
              category: 'Investimentos',
              subcategory: 'Reserva de Emergência',
              type: 'expense',
              paymentMethod: 'debit',
              status: 'paid',
              estimatedAmount: closure.investedAmount,
              actualAmount: closure.investedAmount,
              totalAmount: closure.investedAmount,
              userImpact: { [closure.uid]: closure.investedAmount },
              dueDate: investDate,
              competenceMonth: closure.closedMonth,
              allocations: [
                {
                  type: 'PLATFORM_USER',
                  uid: closure.uid,
                  name: 'Eu',
                  amount: closure.investedAmount,
                  reimbursed: false,
                },
              ],
              createdAt: Date.now(),
            }
            updatedTxs = [investTx, ...updatedTxs]
          }

          return { ...s, monthClosures: updatedClosures, transactions: updatedTxs }
        })
        return
      }

      if (currentUid) {
        await fb.fsSaveMonthClosure(currentUid, closure)
      }
    },
    [mode, commitDemo, currentUid],
  )

  const deleteMonthClosure = useCallback(
    async (closedMonth: string) => {
      if (mode === 'demo') {
        commitDemo((s) => ({
          ...s,
          monthClosures: (s.monthClosures || []).filter(
            (c) => !(c.closedMonth === closedMonth && c.uid === currentUid),
          ),
        }))
        return
      }

      if (currentUid) {
        await fb.fsDeleteMonthClosure(currentUid, closedMonth)
      }
    },
    [mode, commitDemo, currentUid],
  )

  // ---------------- Ações de sessão ----------------
  const signIn = useCallback(async () => {
    localStorage.removeItem(FORCE_DEMO_KEY)
    localStorage.removeItem(SESSION_UID_KEY)
    setForceDemo(false)
    setSessionUid(null)
    await fb.signInWithGoogle()
  }, [])

  const signOut = useCallback(async () => {
    localStorage.removeItem(FORCE_DEMO_KEY)
    localStorage.removeItem(SESSION_UID_KEY)
    setForceDemo(false)
    setSessionUid(null)
    if (fb.isFirebaseConfigured) {
      try {
        await fb.signOutUser()
      } catch (err) {
        console.warn('Erro ao encerrar sessão Firebase:', err)
      }
    }
  }, [])

  const loginAsDemo = useCallback(
    (uid: string) => {
      setSessionUid(uid)
      localStorage.setItem(SESSION_UID_KEY, uid)
      localStorage.setItem(FORCE_DEMO_KEY, '1')
      setForceDemo(true)
    },
    [],
  )

  const switchProfile = useCallback(
    (uid: string) => {
      if (mode === 'demo') {
        setSessionUid(uid)
        localStorage.setItem(SESSION_UID_KEY, uid)
      }
    },
    [mode],
  )

  const enterDemo = useCallback(() => {
    localStorage.setItem(FORCE_DEMO_KEY, '1')
    setForceDemo(true)
    if (!sessionUid && demo.users.length) {
      setSessionUid(demo.users[0].uid)
      localStorage.setItem(SESSION_UID_KEY, demo.users[0].uid)
    }
  }, [sessionUid, demo.users])

  const exitDemo = useCallback(() => {
    localStorage.removeItem(FORCE_DEMO_KEY)
    localStorage.removeItem(SESSION_UID_KEY)
    setForceDemo(false)
    setSessionUid(null)
    window.location.reload()
  }, [])

  const resetDemo = useCallback(() => {
    const f = freshDemo()
    demoRef.current = f
    setDemo(f)
    localStorage.setItem(DEMO_KEY, JSON.stringify(f))
  }, [])

  const clearDemoData = useCallback(() => {
    const emptyState: DemoState = {
      users: [
        {
          uid: 'user-me',
          email: 'usuario@app.local',
          displayName: 'Minha Conta',
          photoURL: null,
          monthlyIncome: 0,
        },
      ],
      cards: [],
      recurring: [],
      transactions: [],
      friendships: [],
    }
    demoRef.current = emptyState
    setDemo(emptyState)
    localStorage.setItem(DEMO_KEY, JSON.stringify(emptyState))
    setSessionUid('user-me')
    localStorage.setItem(SESSION_UID_KEY, 'user-me')
  }, [])

  // ---------------- Ações de transação ----------------
  const addTransaction = useCallback(
    async (tx: NewTransaction) => {
      if (mode === 'demo') {
        const id = makeId('tx')
        const withId: Transaction = { ...tx, id }
        commitDemo((s) => ({ ...s, transactions: [withId, ...s.transactions] }))
        return id
      }
      return fb.fsAddTransaction(tx)
    },
    [mode, commitDemo],
  )

  const addMany = useCallback(
    async (txs: NewTransaction[]) => {
      if (!txs.length) return
      if (mode === 'demo') {
        const withIds: Transaction[] = txs.map((t) => ({ ...t, id: makeId('tx') }))
        commitDemo((s) => ({ ...s, transactions: [...withIds, ...s.transactions] }))
        return
      }
      await fb.fsAddTransactions(txs)
    },
    [mode, commitDemo],
  )

  const updateTransaction = useCallback(
    async (id: string, patch: Partial<Transaction>) => {
      if (mode === 'demo') {
        commitDemo((s) => ({
          ...s,
          transactions: s.transactions.map((t) => (t.id === id ? { ...t, ...patch } : t)),
        }))
        return
      }
      await fb.fsUpdateTransaction(id, patch)
    },
    [mode, commitDemo],
  )

  const deleteTransaction = useCallback(
    async (id: string) => {
      if (mode === 'demo') {
        commitDemo((s) => ({ ...s, transactions: s.transactions.filter((t) => t.id !== id) }))
        return
      }
      await fb.fsDeleteTransaction(id)
    },
    [mode, commitDemo],
  )

  const deleteInstallmentGroup = useCallback(
    async (groupId: string): Promise<number> => {
      if (!groupId) return 0
      const source = mode === 'demo' ? demoRef.current.transactions : fbTxRef.current
      const targetIds = source.filter((t) => t.installmentGroupId === groupId).map((t) => t.id)
      if (!targetIds.length) return 0

      if (mode === 'demo') {
        const set = new Set(targetIds)
        commitDemo((s) => ({
          ...s,
          transactions: s.transactions.filter((t) => !set.has(t.id)),
        }))
        return targetIds.length
      }

      await fb.fsDeleteTransactionsBatch(targetIds)
      return targetIds.length
    },
    [mode, commitDemo],
  )

  const updateInstallmentGroup = useCallback(
    async (groupId: string, input: InstallmentGroupUpdateInput): Promise<number> => {
      if (!groupId || !me) return 0
      const source = mode === 'demo' ? demoRef.current.transactions : fbTxRef.current
      const groupTxs = source
        .filter((t) => t.installmentGroupId === groupId)
        .sort((a, b) => (a.installmentNumber ?? 0) - (b.installmentNumber ?? 0))

      if (!groupTxs.length) return 0

      const isFriend = input.splitType === 'friend'
      const assignedFriend: Party | null =
        isFriend && input.friend ? { uid: input.friend.uid, name: input.friend.name } : null

      const card = cards.find((c) => c.id === input.cardId)
      const payerUid = input.paymentMethod === 'credit_card' && card ? card.ownerUid : me.uid

      const updates: { id: string; patch: Partial<Transaction> }[] = []
      const newDay = input.date ? parseInt(input.date.slice(8, 10), 10) : null

      // Detecta se as parcelas sofreram corrupção de datas (todas colapsadas no mesmo mês)
      const monthsSet = new Set(groupTxs.map((t) => t.competenceMonth || t.dueDate.slice(0, 7)))
      const isCorruptedSameMonth = groupTxs.length > 1 && monthsSet.size === 1
      const firstNum = groupTxs[0].installmentNumber ?? 1
      const baseMonth = groupTxs[0].competenceMonth || groupTxs[0].dueDate.slice(0, 7) || currentMonthKey()

      for (let idx = 0; idx < groupTxs.length; idx++) {
        const t = groupTxs[idx]
        const num = t.installmentNumber ?? idx + 1
        const total = t.installmentsTotal ?? groupTxs.length

        const curAmount =
          input.amount !== undefined && input.amount > 0 ? round2(input.amount) : t.totalAmount

        const newDesc = input.description ? formatInstallmentDesc(input.description, num, total) : t.description

        // Se estavam corrompidas no mesmo mês, redistribui cada uma para seu respectivo mês cronológico
        let targetMonth = t.competenceMonth || t.dueDate.slice(0, 7) || currentMonthKey()
        if (isCorruptedSameMonth) {
          targetMonth = shiftMonth(baseMonth, num - firstNum)
        }

        let newDueDate = t.dueDate
        if (newDay && !isNaN(newDay)) {
          newDueDate = dateInMonth(targetMonth, newDay)
        } else if (isCorruptedSameMonth) {
          const origDay = parseInt(t.dueDate.slice(8, 10), 10) || 10
          newDueDate = dateInMonth(targetMonth, origDay)
        }

        const friendSharePct = isFriend ? clampPct(input.friend?.pct ?? t.friendSharePct ?? 100) : null

        const split = buildSplit({
          splitType: input.splitType ?? 'mine',
          total: curAmount,
          me,
          partner: partnerParty,
          payerUid,
          createdByUid: t.createdByUid,
          externalName: input.externalName,
          assignedFriend,
          friendSharePct,
          customAllocations: input.customAllocations,
          acceptanceStatus: isFriend ? 'pending_approval' : null,
          previous: t.allocations,
        })

        const patch: Partial<Transaction> = {
          description: newDesc,
          category: input.category ?? t.category,
          subcategory: input.subcategory !== undefined ? input.subcategory : (t.subcategory ?? null),
          paymentMethod: input.paymentMethod ?? t.paymentMethod,
          cardId: input.paymentMethod === 'credit_card' ? (input.cardId ?? null) : null,
          status: input.status ?? t.status,
          dueDate: newDueDate,
          competenceMonth: targetMonth,
          estimatedAmount: (input.status ?? t.status) === 'estimated' ? curAmount : null,
          actualAmount: (input.status ?? t.status) === 'estimated' ? null : curAmount,
          totalAmount: curAmount,
          allocations: split.allocations,
          userImpact: split.userImpact,
          visibleToUids: split.visibleToUids,
          assignedFriendUid: assignedFriend?.uid ?? null,
          assignedFriendName: assignedFriend?.name ?? null,
          friendSharePct,
          acceptanceStatus: isFriend ? 'pending_approval' : null,
        }

        updates.push({ id: t.id, patch })
      }

      if (mode === 'demo') {
        const updateMap = new Map(updates.map((u) => [u.id, u.patch]))
        commitDemo((s) => ({
          ...s,
          transactions: s.transactions.map((t) => {
            const patch = updateMap.get(t.id)
            return patch ? { ...t, ...patch } : t
          }),
        }))
        return updates.length
      }

      await fb.fsUpdateTransactionsBatch(updates)
      return updates.length
    },
    [mode, commitDemo, me, partnerParty, cards],
  )

  // Auto-repara qualquer grupo de parcelas cujas datas foram acidentalmente colapsadas no mesmo mês
  useEffect(() => {
    if (!transactions.length) return
    const groups = new Map<string, Transaction[]>()
    for (const tx of transactions) {
      if (tx.installmentGroupId && (tx.installmentsTotal ?? 0) > 1) {
        const list = groups.get(tx.installmentGroupId) || []
        list.push(tx)
        groups.set(tx.installmentGroupId, list)
      }
    }

    for (const [, list] of groups) {
      if (list.length > 1) {
        const months = new Set(list.map((t) => t.competenceMonth || (t.dueDate ? t.dueDate.slice(0, 7) : '')))
        if (months.size === 1) {
          const sorted = [...list].sort((a, b) => (a.installmentNumber ?? 0) - (b.installmentNumber ?? 0))
          const first = sorted[0]
          if (!first) continue
          const firstNum = first.installmentNumber ?? 1
          const baseMonth = first.competenceMonth || (first.dueDate ? first.dueDate.slice(0, 7) : '') || currentMonthKey()

          for (const t of sorted) {
            const num = t.installmentNumber ?? 1
            const targetMonth = shiftMonth(baseMonth, num - firstNum)
            const origDay = parseInt(t.dueDate.slice(8, 10), 10) || 10
            const newDueDate = dateInMonth(targetMonth, origDay)

            if (t.competenceMonth !== targetMonth || t.dueDate !== newDueDate) {
              updateTransaction(t.id, {
                competenceMonth: targetMonth,
                dueDate: newDueDate,
              })
            }
          }
        }
      }
    }
  }, [transactions, updateTransaction])

  const cycleStatus = useCallback(
    async (tx: Transaction) => {
      const next = nextStatus(tx.status)
      await updateTransaction(tx.id, statusPatch(tx, next))
    },
    [updateTransaction],
  )

  const setStatus = useCallback(
    (tx: Transaction, status: TransactionStatus) => updateTransaction(tx.id, statusPatch(tx, status)),
    [updateTransaction],
  )

  const changeSplit = useCallback(
    async (
      tx: Transaction,
      splitType: SplitType,
      externalName?: string,
      friend?: FriendSplitInput,
      customAllocations?: Allocation[],
    ) => {
      if (!me) return
      const isFriend = splitType === 'friend'
      // Detecta amigo em divisões friend ou custom
      let assignedFriend: Party | null = null
      if (friend) {
        assignedFriend = { uid: friend.uid, name: friend.name }
      } else if (customAllocations) {
        const fa = customAllocations.find(
          (a) => a.type === 'PLATFORM_USER' && a.uid && a.uid !== me.uid && (!partnerParty || a.uid !== partnerParty.uid)
        )
        if (fa && fa.uid) assignedFriend = { uid: fa.uid, name: fa.name }
      } else if (isFriend && tx.assignedFriendUid && tx.assignedFriendName) {
        assignedFriend = { uid: tx.assignedFriendUid, name: tx.assignedFriendName }
      }

      // Sem amigo definido não há como cobrar no modo friend específico
      if (isFriend && !assignedFriend) return

      let pct = isFriend ? clampPct(friend?.pct ?? tx.friendSharePct ?? 100) : null
      if (assignedFriend && !pct && customAllocations && Math.abs(tx.totalAmount) > 0) {
        const fa = customAllocations.find((a) => a.uid === assignedFriend!.uid)
        if (fa) pct = clampPct(round2((fa.amount / Math.abs(tx.totalAmount)) * 100))
      }
      const split = buildSplit({
        splitType,
        total: tx.totalAmount,
        me,
        partner: partnerParty,
        payerUid: tx.paidByUid,
        createdByUid: tx.createdByUid,
        externalName,
        assignedFriend,
        friendSharePct: pct,
        customAllocations,
        acceptanceStatus: tx.acceptanceStatus,
        previous: tx.allocations,
      })

      // Se o amigo ou o percentual mudou, o amigo precisa aprovar os novos termos
      const termsChanged =
        assignedFriend &&
        (tx.assignedFriendUid !== assignedFriend.uid || clampPct(tx.friendSharePct ?? 100) !== pct)

      await updateTransaction(tx.id, {
        allocations: split.allocations,
        userImpact: split.userImpact,
        visibleToUids: split.visibleToUids,
        assignedFriendUid: assignedFriend?.uid ?? null,
        assignedFriendName: assignedFriend?.name ?? null,
        friendSharePct: pct,
        acceptanceStatus: assignedFriend
          ? termsChanged || !tx.acceptanceStatus
            ? 'pending_approval'
            : tx.acceptanceStatus
          : null,
      })
    },
    [me, partnerParty, updateTransaction],
  )

  const changeAmount = useCallback(
    (tx: Transaction, values: { estimatedAmount?: number | null; actualAmount?: number | null }) =>
      updateTransaction(tx.id, amountPatch(tx, values)),
    [updateTransaction],
  )

  const markReimbursed = useCallback(
    async (txId: string, idx: number, value: boolean) => {
      const tx = transactions.find((t) => t.id === txId)
      if (!tx) return
      const allocations = (tx.allocations || []).map((a, i) => (i === idx ? { ...a, reimbursed: value } : a))
      await updateTransaction(txId, { allocations })
    },
    [transactions, updateTransaction],
  )

  const createManual = useCallback(
    async (input: ManualTransactionInput) => {
      if (!me) throw new Error('Sem usuário conectado')
      const card = cards.find((c) => c.id === input.cardId)
      const payerUid = input.paymentMethod === 'credit_card' && card ? card.ownerUid : me.uid
      const isReserve = !!input.reserveCardId

      // Feature 3: Compra parcelada
      const isInstallment = Boolean(input.isInstallment && input.installmentsCount && input.installmentsCount > 1)
      const installmentsTotal = isInstallment ? Number(input.installmentsCount) : 1

      // Feature 2: Amigo atribuído (suporta tanto friend quanto custom)
      let assignedFriend: Party | null = null
      if (input.assignedFriendUid && input.assignedFriendName) {
        assignedFriend = { uid: input.assignedFriendUid, name: input.assignedFriendName }
      } else if (input.customAllocations) {
        const fa = input.customAllocations.find(
          (a) => a.type === 'PLATFORM_USER' && a.uid && a.uid !== me.uid && (!partnerParty || a.uid !== partnerParty.uid)
        )
        if (fa && fa.uid) assignedFriend = { uid: fa.uid, name: fa.name }
      }

      if (input.splitType === 'friend' && !assignedFriend) {
        throw new Error('Selecione um amigo da sua rede para enviar a cobrança.')
      }

      let friendSharePct = input.friendSharePct != null ? clampPct(input.friendSharePct) : null
      if (assignedFriend && friendSharePct == null) {
        if (input.customAllocations && Math.abs(input.amount) > 0) {
          const fa = input.customAllocations.find((a) => a.uid === assignedFriend!.uid)
          if (fa) friendSharePct = clampPct(round2((fa.amount / Math.abs(input.amount)) * 100))
        }
        if (friendSharePct == null) friendSharePct = 100
      }

      const initialStatus = assignedFriend ? ('pending_approval' as const) : undefined

      // Cálculo de valor das parcelas
      let perInstallmentAmount = input.amount
      if (isInstallment) {
        if (input.installmentMode === 'total') {
          perInstallmentAmount = round2(input.amount / installmentsTotal)
        } else {
          perInstallmentAmount = round2(input.amount)
        }
      }

      const installmentGroupId = isInstallment ? makeId('inst') : null
      const txsToCreate: NewTransaction[] = []

      for (let i = 1; i <= installmentsTotal; i++) {
        let curAmount = perInstallmentAmount
        if (isInstallment && input.installmentMode === 'total' && i === installmentsTotal) {
          curAmount = round2(input.amount - perInstallmentAmount * (installmentsTotal - 1))
        }

        const curDate = isInstallment ? shiftDateMonth(input.date, i - 1) : input.date
        let curDueDate = curDate
        let curCompetenceMonth = curDate.slice(0, 7)

        if (input.paymentMethod === 'credit_card' && card) {
          const cardDates = calculateCardInvoiceDates(curDate, card)
          curDueDate = cardDates.dueDate
          curCompetenceMonth = cardDates.competenceMonth
        }
        const curDesc = isInstallment
          ? `${input.description} (${i}/${installmentsTotal})`
          : input.description

        const split = buildSplit({
          splitType: input.type === 'income' ? 'mine' : input.splitType,
          total: curAmount,
          me,
          partner: partnerParty,
          payerUid,
          createdByUid: me.uid,
          externalName: input.externalName,
          assignedFriend,
          friendSharePct,
          customAllocations: input.customAllocations,
          acceptanceStatus: initialStatus,
        })

        txsToCreate.push({
          createdByUid: me.uid,
          paidByUid: isReserve ? me.uid : payerUid,
          cardId: input.paymentMethod === 'credit_card' ? (card?.id ?? null) : null,
          description: curDesc,
          category: input.type === 'income' ? 'Receita' : input.category,
          subcategory: input.subcategory || null,
          type: input.type,
          paymentMethod: input.paymentMethod,
          status: input.status,
          estimatedAmount: input.status === 'estimated' ? curAmount : null,
          actualAmount: input.status === 'estimated' ? null : curAmount,
          totalAmount: curAmount,
          dueDate: curDueDate,
          competenceMonth: curCompetenceMonth,
          visibleToUids: isReserve ? [me.uid] : split.visibleToUids,
          userImpact: split.userImpact,
          allocations: isReserve ? split.allocations.map((a) => ({ ...a, reimbursed: true })) : split.allocations,
          recurringBillId: null,
          reserveCardId: input.reserveCardId ?? null,
          createdAt: Date.now() + i,
          assignedFriendUid: assignedFriend?.uid ?? null,
          assignedFriendName: assignedFriend?.name ?? null,
          friendSharePct,
          acceptanceStatus: initialStatus,
          installmentGroupId,
          installmentNumber: isInstallment ? i : null,
          installmentsTotal: isInstallment ? installmentsTotal : null,
          splitType: input.type === 'income' ? 'mine' : input.splitType,
        })
      }

      if (txsToCreate.length === 1) {
        return addTransaction(txsToCreate[0])
      } else {
        await addMany(txsToCreate)
        return txsToCreate[0].installmentGroupId || makeId('tx')
      }
    },
    [me, cards, partnerParty, addTransaction, addMany],
  )

  const generateBills = useCallback(async () => {
    if (!me) return { created: 0, skipped: 0 }
    const { created, skipped } = generateMonthBills({
      bills: recurring,
      all: transactions,
      monthKey: month,
      me,
      partner: partnerParty,
      profile,
    })
    await addMany(created)
    return { created: created.length, skipped }
  }, [me, recurring, transactions, month, partnerParty, profile, addMany])

  const saveCard = useCallback(
    async (card: Card) => {
      const sharedWithUsers = Array.isArray(card.sharedWithUsers) ? card.sharedWithUsers : []
      const normalized: Card = {
        ...card,
        sharedWithUsers,
        sharedWithUids: sharedWithUsers.map((u) => u.uid),
      }
      if (mode === 'demo') {
        commitDemo((s) => {
          const exists = s.cards.some((c) => c.id === card.id)
          const withId = exists ? normalized : { ...normalized, id: card.id.startsWith('new_') ? makeId('card') : card.id }
          return { ...s, cards: exists ? s.cards.map((c) => (c.id === card.id ? withId : c)) : [...s.cards, withId] }
        })
        return
      }
      await fb.fsSaveCard(normalized)
    },
    [mode, commitDemo],
  )

  const deleteCard = useCallback(
    async (id: string) => {
      if (mode === 'demo') return commitDemo((s) => ({ ...s, cards: s.cards.filter((c) => c.id !== id) }))
      await fb.fsDeleteCard(id)
    },
    [mode, commitDemo],
  )

  const saveRecurring = useCallback(
    async (bill: RecurringBill) => {
      if (mode === 'demo') {
        commitDemo((s) => {
          const exists = s.recurring.some((b) => b.id === bill.id)
          const withId = exists ? bill : { ...bill, id: bill.id.startsWith('new_') ? makeId('bill') : bill.id }
          return { ...s, recurring: exists ? s.recurring.map((b) => (b.id === bill.id ? withId : b)) : [...s.recurring, withId] }
        })
        return
      }
      await fb.fsSaveRecurring(bill)
    },
    [mode, commitDemo],
  )

  const deleteRecurring = useCallback(
    async (id: string) => {
      if (mode === 'demo') return commitDemo((s) => ({ ...s, recurring: s.recurring.filter((b) => b.id !== id) }))
      await fb.fsDeleteRecurring(id)
    },
    [mode, commitDemo],
  )

  const updateProfile = useCallback(
    async (patch: Partial<UserProfile>) => {
      if (!profile) return
      if (mode === 'demo') {
        commitDemo((s) => ({
          ...s,
          users: s.users.map((u) => (u.uid === profile.uid ? { ...u, ...patch } : u)),
        }))
        return
      }
      await fb.fsUpdateUserProfile(profile.uid, patch)
    },
    [profile, mode, commitDemo],
  )

  const lookupUserByEmail = useCallback(async (email: string) => {
    return fb.lookupUserByEmail(email)
  }, [])

  // ---------------- Agente (refs para sempre ler o estado mais recente) ----------------
  const agentEnvRef = useRef<{ me: Party | null; partner: Party | null; month: string; cards: Card[]; recurring: RecurringBill[]; friends: FriendItem[] }>({
    me: null,
    partner: null,
    month,
    cards,
    recurring: [],
    friends: [],
  })
  agentEnvRef.current = { me, partner: partnerParty, month, cards, recurring, friends }

  const executeTool = useMemo(
    () =>
      createToolExecutor(() => {
        const env = agentEnvRef.current
        if (!env.me) throw new Error('Nenhum usuário ativo')
        return {
          me: env.me,
          partner: env.partner,
          monthKey: env.month,
          getCards: () => (mode === 'demo' ? demoRef.current.cards : agentEnvRef.current.cards),
          getTransactions: () => (mode === 'demo' ? demoRef.current.transactions : fbTxRef.current),
          getRecurring: () => (mode === 'demo' ? demoRef.current.recurring : agentEnvRef.current.recurring),
          getFriends: () => friends,
          getPendingTransactions: () => pendingBatchRef.current,
          setPendingTransactions: (txs) => {
            pendingBatchRef.current = txs
            setPendingBatchTransactions(txs)
          },
          lookupUserByEmail,
    monthClosures,
    saveMonthClosure,
    deleteMonthClosure,
          addTransaction,
          addMany,
          updateTransaction,
          deleteTransaction,
          addCard: async (c) => {
             const id = c.id || makeId()
             const cardToSave = { ...c, id } as Card
             if (mode === 'demo') {
                commitDemo(s => ({ ...s, cards: [...s.cards, cardToSave] }))
             } else {
                await fb.fsSaveCard(cardToSave)
             }
             return id
          },
          updateCard: async (id, patch) => {
             if (mode === 'demo') {
                commitDemo(s => ({ ...s, cards: s.cards.map(x => x.id === id ? { ...x, ...patch } : x) }))
             } else {
                const existing = agentEnvRef.current.cards.find(x => x.id === id)
                if (existing) await fb.fsSaveCard({ ...existing, ...patch } as Card)
             }
          },
          deleteCard: async (id) => {
             if (mode === 'demo') {
                commitDemo(s => ({ ...s, cards: s.cards.filter(x => x.id !== id) }))
             } else {
                await fb.fsDeleteCard(id)
             }
          },
          addRecurring: async (r) => {
             const id = r.id || makeId()
             const toSave = { ...r, id } as RecurringBill
             if (mode === 'demo') {
                commitDemo(s => ({ ...s, recurring: [...s.recurring, toSave] }))
             } else {
                await fb.fsSaveRecurring(toSave)
             }
             return id
          },
          updateRecurring: async (id, patch) => {
             if (mode === 'demo') {
                commitDemo(s => ({ ...s, recurring: s.recurring.map(x => x.id === id ? { ...x, ...patch } : x) }))
             } else {
                const existing = agentEnvRef.current.recurring.find(x => x.id === id)
                if (existing) await fb.fsSaveRecurring({ ...existing, ...patch } as RecurringBill)
             }
          },
          deleteRecurring: async (id) => {
             if (mode === 'demo') {
                commitDemo(s => ({ ...s, recurring: s.recurring.filter(x => x.id !== id) }))
             } else {
                await fb.fsDeleteRecurring(id)
             }
          },
          addFriend: async (email) => {
             await sendFriendRequest(email)
          },
          respondFriend: async (id, accept) => {
             await respondFriendRequest(id, accept)
          }
        }
      }),
    [mode, friends, lookupUserByEmail, addTransaction, addMany, updateTransaction, deleteTransaction, commitDemo, sendFriendRequest, respondFriendRequest],
  )

  const rules = useMemo(() => {
    if (mode === 'demo') {
      return sessionUid ? getLearnedRules(sessionUid) : []
    }
    return fbRules
  }, [mode, sessionUid, fbRules])

  const addRule = useCallback(
    async (ruleText: string, category = 'Geral') => {
      const uid = currentUid || 'guest'
      const newRule = addLearnedRule(uid, ruleText, category)
      if (mode === 'firebase' && authUser) {
        await fb.fsSaveAgentRule(authUser.uid, newRule)
        setFbRules((prev) => [newRule, ...prev.filter((r) => r.id !== newRule.id)])
      }
      return newRule
    },
    [currentUid, mode, authUser],
  )

  const deleteRule = useCallback(
    async (ruleId: string) => {
      const uid = currentUid || 'guest'
      removeLearnedRule(uid, ruleId)
      if (mode === 'firebase' && authUser) {
        await fb.fsDeleteAgentRule(authUser.uid, ruleId)
        setFbRules((prev) => prev.filter((r) => r.id !== ruleId))
      }
    },
    [currentUid, mode, authUser],
  )

  const value: FinanceContextValue = {
    mode,
    firebaseAvailable: fb.isFirebaseConfigured,
    isFirebaseConfigured: fb.isFirebaseConfigured,
    authLoading,
    isAuthenticated: mode === 'demo' ? Boolean(sessionUid) : Boolean(authUser),
    authUser,
    users,
    profile,
    me,
    partner: partnerParty,
    cards,
    recurring,
    transactions,
    monthTransactions,
    summary,
    month,
    setMonth,
    signIn,
    signOut,
    loginAsDemo,
    switchProfile,
    enterDemo,
    exitDemo,
    resetDemo,
    clearDemoData,
    addTransaction,
    addMany,
    createManual,
    updateTransaction,
    deleteTransaction,
    deleteInstallmentGroup,
    updateInstallmentGroup,
    cycleStatus,
    setStatus,
    changeSplit,
    changeAmount,
    markReimbursed,
    generateBills,
    saveCard,
    deleteCard,
    saveRecurring,
    deleteRecurring,
    updateProfile,
    lookupUserByEmail,
    monthClosures,
    saveMonthClosure,
    deleteMonthClosure,
    friendships,
    friends,
    pendingFriendRequests,
    pendingChargeRequests,
    pendingChargeGroups,
    sendFriendRequest,
    respondFriendRequest,
    removeFriend,
    respondChargeRequest,
    executeTool,
    pendingBatchTransactions,
    setPendingBatchTransactions,
    rules,
    addRule,
    deleteRule,
  }

  return <FinanceContext.Provider value={value}>{children}</FinanceContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useFinance() {
  const ctx = useContext(FinanceContext)
  if (!ctx) throw new Error('useFinance deve ser usado dentro de <FinanceProvider>')
  return ctx
}
