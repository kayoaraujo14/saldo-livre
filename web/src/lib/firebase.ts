/**
 * Integração Firebase (Auth Google SSO + Cloud Firestore).
 * Se as variáveis VITE_FIREBASE_* não estiverem definidas, o app roda em
 * Modo Demo (estado local) e nada deste módulo é inicializado.
 */
import { initializeApp, type FirebaseApp } from 'firebase/app'
import {
  GoogleAuthProvider,
  getAuth,
  onAuthStateChanged,
  signInWithPopup,
  signOut,
  type Auth,
  type User,
} from 'firebase/auth'
import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  limit,
  onSnapshot,
  query,
  setDoc,
  updateDoc,
  where,
  writeBatch,
  type Firestore,
} from 'firebase/firestore'
import type { Card, ChatSession, Friendship, NewTransaction, RecurringBill, SharedUser, Transaction, UserLearnedRule, UserProfile } from '@/types'
import { stripUndefined } from './utils'

const FIREBASE_CONFIG_STORAGE_KEY = 'saldo-livre:firebase-config'

export function getStoredFirebaseConfig(): Record<string, string> | null {
  try {
    const raw = localStorage.getItem(FIREBASE_CONFIG_STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function saveFirebaseConfig(cfg: Record<string, string>) {
  localStorage.setItem(FIREBASE_CONFIG_STORAGE_KEY, JSON.stringify(cfg))
  // Clear any forced demo mode flag so production firebase mode activates
  localStorage.removeItem('saldo-livre:force-demo')
  localStorage.removeItem('saldo-livre:session-uid')
  window.location.reload()
}

export function clearFirebaseConfig() {
  localStorage.removeItem(FIREBASE_CONFIG_STORAGE_KEY)
  window.location.reload()
}

export const DEFAULT_FIREBASE_CONFIG = {
  apiKey: 'AIzaSyD6R12Yi9dVUQWiVkYCUplPh0-6Dsr-mhg',
  authDomain: 'saldo-livre-c3ca3.firebaseapp.com',
  projectId: 'saldo-livre-c3ca3',
  storageBucket: 'saldo-livre-c3ca3.firebasestorage.app',
  messagingSenderId: '251257095844',
  appId: '1:251257095844:web:f53db2159f9f201f560831',
}

function resolveFirebaseConfig() {
  const env = import.meta.env
  const apiKey = (env.VITE_FIREBASE_API_KEY as string) || ''
  const projectId = (env.VITE_FIREBASE_PROJECT_ID as string) || ''
  if (apiKey && apiKey !== 'true' && projectId && projectId !== 'true') {
    return {
      apiKey,
      authDomain: (env.VITE_FIREBASE_AUTH_DOMAIN as string) || `${projectId}.firebaseapp.com`,
      projectId,
      storageBucket: (env.VITE_FIREBASE_STORAGE_BUCKET as string) || `${projectId}.firebasestorage.app`,
      messagingSenderId: (env.VITE_FIREBASE_MESSAGING_SENDER_ID as string) || '251257095844',
      appId: (env.VITE_FIREBASE_APP_ID as string) || '',
    }
  }
  const stored = getStoredFirebaseConfig()
  if (stored?.apiKey && stored?.projectId) {
    return {
      apiKey: stored.apiKey,
      authDomain: stored.authDomain || `${stored.projectId}.firebaseapp.com`,
      projectId: stored.projectId,
      storageBucket: stored.storageBucket || `${stored.projectId}.firebasestorage.app`,
      messagingSenderId: stored.messagingSenderId,
      appId: stored.appId || '',
    }
  }
  return DEFAULT_FIREBASE_CONFIG
}

export const firebaseConfig = resolveFirebaseConfig()

export const isFirebaseConfigured = Boolean(
  firebaseConfig?.apiKey && firebaseConfig?.projectId,
)

let app: FirebaseApp | null = null
let auth: Auth | null = null
let db: Firestore | null = null

function ensure() {
  if (!isFirebaseConfigured || !firebaseConfig) throw new Error('Firebase não configurado (Modo Demo ativo).')
  if (!app) {
    app = initializeApp(firebaseConfig as Record<string, string>)
    auth = getAuth(app)
    db = getFirestore(app)
  }
  return { auth: auth!, db: db! }
}

export const COLLECTIONS = {
  users: 'users',
  cards: 'cards',
  recurring: 'recurring_bills',
  transactions: 'transactions',
  friendships: 'friendships',
  agentRules: 'agent_rules',
  chatSessions: 'chat_sessions',
} as const

// ------------------------------------------------------------------
// Auth
// ------------------------------------------------------------------
export function onAuthChange(cb: (user: User | null) => void) {
  return onAuthStateChanged(ensure().auth, cb)
}

export async function signInWithGoogle() {
  const provider = new GoogleAuthProvider()
  provider.setCustomParameters({ prompt: 'select_account' })
  const cred = await signInWithPopup(ensure().auth, provider)
  return cred.user
}

export function signOutUser() {
  return signOut(ensure().auth)
}

// ------------------------------------------------------------------
// Users
// ------------------------------------------------------------------
export async function upsertUserProfile(user: User): Promise<UserProfile> {
  const { db } = ensure()
  const ref = doc(db, COLLECTIONS.users, user.uid)
  const snap = await getDoc(ref)
  const base: UserProfile = {
    uid: user.uid,
    email: user.email ?? '',
    displayName: user.displayName ?? user.email ?? 'Usuário',
    photoURL: user.photoURL,
    monthlyIncome: 0,
  }
  if (!snap.exists()) {
    await setDoc(ref, base)
    return base
  }
  const existing = snap.data() as UserProfile
  const merged = { ...existing, email: base.email, displayName: existing.displayName || base.displayName, photoURL: base.photoURL }
  await setDoc(ref, merged, { merge: true })
  return merged
}

export function subscribeUserProfile(uid: string, cb: (u: UserProfile | null) => void) {
  try {
    return onSnapshot(
      doc(ensure().db, COLLECTIONS.users, uid),
      (s) => cb(s.exists() ? (s.data() as UserProfile) : null),
      (err) => console.warn('Erro ao sincronizar perfil do Firestore:', err)
    )
  } catch (err) {
    console.warn('Falha ao iniciar listener de perfil:', err)
    return () => {}
  }
}

export function updateUserProfile(uid: string, patch: Partial<UserProfile>) {
  return updateDoc(doc(ensure().db, COLLECTIONS.users, uid), stripUndefined(patch))
}
export const fsUpdateUserProfile = updateUserProfile

export async function findUserByEmail(email: string): Promise<UserProfile | null> {
  const q = query(collection(ensure().db, COLLECTIONS.users), where('email', '==', email.trim().toLowerCase()), limit(1))
  const res = await getDocs(q)
  return res.empty ? null : (res.docs[0].data() as UserProfile)
}
export const lookupUserByEmail = findUserByEmail

// ------------------------------------------------------------------
// Subscriptions (tempo real) com Normalizadores Defensivos
// ------------------------------------------------------------------
export function normalizeCard(id: string, raw: any): Card {
  const data = raw || {}
  const rawSharedUsers = Array.isArray(data.sharedWithUsers)
    ? data.sharedWithUsers
    : (data.sharedWithUsers && typeof data.sharedWithUsers === 'object'
        ? Object.values(data.sharedWithUsers)
        : [])
  const sharedWithUsers = rawSharedUsers.map((u: any) => {
    if (typeof u === 'string') return { uid: u, name: 'Parceiro', email: '' }
    if (u && typeof u === 'object') return { uid: u.uid || '', name: u.name || 'Parceiro', email: u.email || '' }
    return null
  }).filter(Boolean) as SharedUser[]
  const rawSharedUids = Array.isArray(data.sharedWithUids) ? data.sharedWithUids : []
  const sharedWithUids = rawSharedUids.length > 0
    ? rawSharedUids.filter((x: any) => typeof x === 'string')
    : sharedWithUsers.map(u => u.uid).filter(Boolean)

  return {
    id,
    cardName: data.cardName || 'Cartão',
    closingDay: Number(data.closingDay) || 1,
    dueDay: Number(data.dueDay) || 10,
    ownerUid: data.ownerUid || '',
    ownerName: data.ownerName || '',
    sharedWithUids,
    sharedWithUsers,
    color: data.color || 'emerald',
    frequentThirdParties: Array.isArray(data.frequentThirdParties) ? data.frequentThirdParties : [],
  }
}

export function normalizeTransaction(id: string, raw: any): Transaction {
  const data = raw || {}
  return {
    id,
    createdByUid: data.createdByUid || '',
    paidByUid: data.paidByUid || data.createdByUid || '',
    cardId: data.cardId || null,
    reserveCardId: data.reserveCardId || null,
    recurringBillId: data.recurringBillId || null,
    description: data.description || '',
    category: data.category || 'Outros',
    subcategory: data.subcategory || null,
    type: data.type || 'expense',
    paymentMethod: data.paymentMethod || 'credit_card',
    status: data.status || 'confirmed',
    estimatedAmount: data.estimatedAmount ?? null,
    actualAmount: data.actualAmount ?? null,
    totalAmount: Number(data.totalAmount) || 0,
    dueDate: data.dueDate || '',
    competenceMonth: data.competenceMonth || '',
    visibleToUids: Array.isArray(data.visibleToUids) ? data.visibleToUids : [],
    userImpact: (data.userImpact && typeof data.userImpact === 'object' && !Array.isArray(data.userImpact)) ? data.userImpact : {},
    allocations: Array.isArray(data.allocations) ? data.allocations : [],
    assignedFriendUid: data.assignedFriendUid || null,
    assignedFriendName: data.assignedFriendName || null,
    friendSharePct: data.friendSharePct ?? null,
    acceptanceStatus: data.acceptanceStatus || null,
    respondedAt: data.respondedAt ?? null,
    installmentGroupId: data.installmentGroupId || null,
    installmentNumber: data.installmentNumber ?? null,
    installmentsTotal: data.installmentsTotal ?? null,
    splitType: data.splitType || null,
    createdAt: Number(data.createdAt) || Date.now(),
  }
}

export function normalizeRecurringBill(id: string, raw: any): RecurringBill {
  const data = raw || {}
  return {
    id,
    ownerUid: data.ownerUid || '',
    description: data.description || '',
    category: data.category || 'Outros',
    subcategory: data.subcategory || null,
    dueDay: Number(data.dueDay) || 1,
    defaultAmount: Number(data.defaultAmount) || Number(data.estimatedAmount) || 0,
    estimationRule: data.estimationRule || 'fixed',
    active: data.active !== false,
    splitType: data.splitType,
    paymentMethod: data.paymentMethod,
    type: data.type,
    startMonth: data.startMonth,
    endMonth: data.endMonth,
    installmentsCount: data.installmentsCount,
  }
}

export function subscribeTransactions(uid: string, cb: (txs: Transaction[]) => void) {
  try {
    // array-contains sem outros filtros → não exige índice composto.
    const q = query(collection(ensure().db, COLLECTIONS.transactions), where('visibleToUids', 'array-contains', uid))
    return onSnapshot(
      q,
      (snap) => cb(snap.docs.map((d) => normalizeTransaction(d.id, d.data()))),
      (err) => console.warn('Erro ao sincronizar transações do Firestore:', err)
    )
  } catch (err) {
    console.warn('Falha ao iniciar listener de transações:', err)
    return () => {}
  }
}

export function subscribeCards(uid: string, cb: (cards: Card[]) => void) {
  try {
    const { db } = ensure()
    let owned: Card[] = []
    let shared: Card[] = []
    const emit = () => {
      const map = new Map<string, Card>()
      for (const c of [...owned, ...shared]) map.set(c.id, c)
      cb([...map.values()])
    }
    const u1 = onSnapshot(
      query(collection(db, COLLECTIONS.cards), where('ownerUid', '==', uid)),
      (s) => {
        owned = s.docs.map((d) => normalizeCard(d.id, d.data()))
        emit()
      },
      (err) => console.warn('Erro ao sincronizar cartões próprios:', err)
    )
    const u2 = onSnapshot(
      query(collection(db, COLLECTIONS.cards), where('sharedWithUids', 'array-contains', uid)),
      (s) => {
        shared = s.docs.map((d) => normalizeCard(d.id, d.data()))
        emit()
      },
      (err) => console.warn('Erro ao sincronizar cartões compartilhados:', err)
    )
    return () => {
      u1()
      u2()
    }
  } catch (err) {
    console.warn('Falha ao iniciar listener de cartões:', err)
    return () => {}
  }
}

export function subscribeRecurring(uid: string, cb: (bills: RecurringBill[]) => void) {
  try {
    const q = query(collection(ensure().db, COLLECTIONS.recurring), where('ownerUid', '==', uid))
    return onSnapshot(
      q,
      (s) => cb(s.docs.map((d) => normalizeRecurringBill(d.id, d.data()))),
      (err) => console.warn('Erro ao sincronizar despesas recorrentes:', err)
    )
  } catch (err) {
    console.warn('Falha ao iniciar listener de despesas recorrentes:', err)
    return () => {}
  }
}

// ------------------------------------------------------------------
// Mutations
// ------------------------------------------------------------------
export async function fsAddTransaction(tx: NewTransaction) {
  const ref = await addDoc(collection(ensure().db, COLLECTIONS.transactions), stripUndefined(tx))
  return ref.id
}

export async function fsAddTransactions(txs: NewTransaction[]) {
  const { db } = ensure()
  const batch = writeBatch(db)
  for (const tx of txs) batch.set(doc(collection(db, COLLECTIONS.transactions)), stripUndefined(tx))
  await batch.commit()
}

export function fsUpdateTransaction(id: string, patch: Partial<Transaction>) {
  const { id: _omit, ...rest } = patch
  void _omit
  return updateDoc(doc(ensure().db, COLLECTIONS.transactions, id), stripUndefined(rest))
}

export function fsDeleteTransaction(id: string) {
  return deleteDoc(doc(ensure().db, COLLECTIONS.transactions, id))
}

export async function fsUpdateTransactionsBatch(updates: { id: string; patch: Partial<Transaction> }[]) {
  const { db } = ensure()
  const valid = updates.filter((u) => u.id)
  for (let i = 0; i < valid.length; i += 450) {
    const batch = writeBatch(db)
    for (const { id, patch } of valid.slice(i, i + 450)) {
      const { id: _omit, ...rest } = patch
      void _omit
      batch.update(doc(db, COLLECTIONS.transactions, id), stripUndefined(rest))
    }
    await batch.commit()
  }
}

export async function fsDeleteTransactionsBatch(ids: string[]) {
  const { db } = ensure()
  const valid = [...new Set(ids)].filter(Boolean)
  for (let i = 0; i < valid.length; i += 450) {
    const batch = writeBatch(db)
    for (const id of valid.slice(i, i + 450)) {
      batch.delete(doc(db, COLLECTIONS.transactions, id))
    }
    await batch.commit()
  }
}

export async function fsSaveCard(card: Card) {
  const { db } = ensure()
  const { id, ...data } = card
  const payload = {
    ...data,
    sharedWithUsers: Array.isArray(data.sharedWithUsers) ? data.sharedWithUsers : [],
    sharedWithUids: Array.isArray(data.sharedWithUids)
      ? data.sharedWithUids
      : (Array.isArray(data.sharedWithUsers) ? data.sharedWithUsers.map((u: any) => u.uid) : []),
    frequentThirdParties: Array.isArray(data.frequentThirdParties) ? data.frequentThirdParties : [],
  }
  if (id && !id.startsWith('new_')) return setDoc(doc(db, COLLECTIONS.cards, id), stripUndefined(payload))
  await addDoc(collection(db, COLLECTIONS.cards), stripUndefined(payload))
}

export function fsDeleteCard(id: string) {
  return deleteDoc(doc(ensure().db, COLLECTIONS.cards, id))
}

export async function fsSaveRecurring(bill: RecurringBill) {
  const { db } = ensure()
  const { id, ...data } = bill
  if (id && !id.startsWith('new_')) return setDoc(doc(db, COLLECTIONS.recurring, id), stripUndefined(data))
  await addDoc(collection(db, COLLECTIONS.recurring), stripUndefined(data))
}

export function fsDeleteRecurring(id: string) {
  return deleteDoc(doc(ensure().db, COLLECTIONS.recurring, id))
}

// ------------------------------------------------------------------
// Rede de Amizades
// ------------------------------------------------------------------
export function subscribeFriendships(uid: string, cb: (fs: Friendship[]) => void) {
  try {
    const { db } = ensure()
    let list1: Friendship[] = []
    let list2: Friendship[] = []

    const emit = () => {
      const map = new Map<string, Friendship>()
      for (const f of list1) map.set(f.id, f)
      for (const f of list2) map.set(f.id, f)
      cb([...map.values()])
    }

    const u1 = onSnapshot(
      query(collection(db, COLLECTIONS.friendships), where('requesterUid', '==', uid)),
      (s) => {
        list1 = s.docs.map((d) => {
          const data = d.data() || {}
          return {
            id: d.id,
            requesterUid: data.requesterUid || '',
            requesterEmail: data.requesterEmail || '',
            requesterName: data.requesterName || 'Amigo',
            recipientUid: data.recipientUid || '',
            recipientEmail: data.recipientEmail || '',
            recipientName: data.recipientName || 'Amigo',
            status: data.status || 'pending',
            createdAt: Number(data.createdAt) || Date.now(),
          }
        })
        emit()
      },
      (err) => console.warn('Erro ao sincronizar convites de amizade enviados:', err)
    )

    const u2 = onSnapshot(
      query(collection(db, COLLECTIONS.friendships), where('recipientUid', '==', uid)),
      (s) => {
        list2 = s.docs.map((d) => {
          const data = d.data() || {}
          return {
            id: d.id,
            requesterUid: data.requesterUid || '',
            requesterEmail: data.requesterEmail || '',
            requesterName: data.requesterName || 'Amigo',
            recipientUid: data.recipientUid || '',
            recipientEmail: data.recipientEmail || '',
            recipientName: data.recipientName || 'Amigo',
            status: data.status || 'pending',
            createdAt: Number(data.createdAt) || Date.now(),
          }
        })
        emit()
      },
      (err) => console.warn('Erro ao sincronizar convites de amizade recebidos:', err)
    )

    return () => {
      u1()
      u2()
    }
  } catch (err) {
    console.warn('Falha ao iniciar listener de amizades:', err)
    return () => {}
  }
}

export async function fsCreateFriendship(
  requester: UserProfile,
  recipient: UserProfile,
) {
  const { db } = ensure()
  const docRef = await addDoc(collection(db, COLLECTIONS.friendships), {
    requesterUid: requester.uid,
    requesterEmail: requester.email,
    requesterName: requester.displayName,
    recipientUid: recipient.uid,
    recipientEmail: recipient.email,
    recipientName: recipient.displayName,
    status: 'pending',
    createdAt: Date.now(),
  })
  return docRef.id
}

export function fsUpdateFriendshipStatus(id: string, status: 'accepted' | 'rejected') {
  return updateDoc(doc(ensure().db, COLLECTIONS.friendships, id), { status })
}

export function fsDeleteFriendship(id: string) {
  return deleteDoc(doc(ensure().db, COLLECTIONS.friendships, id))
}

/**
 * Responde a uma cobrança feita por amigo.
 *
 * IMPORTANTE: não fazemos query por `installmentGroupId` aqui. As regras do
 * Firestore não funcionam como filtro — uma query sem
 * `where('visibleToUids', 'array-contains', uid)` é rejeitada com
 * permission-denied. Por isso recebemos os IDs (já conhecidos via snapshot
 * em tempo real) e atualizamos cada documento diretamente em lote.
 */
export async function fsRespondChargeRequest(txIds: string[], accept: boolean) {
  const { db } = ensure()
  const ids = [...new Set(txIds)].filter(Boolean)
  if (!ids.length) return
  const patch = { acceptanceStatus: accept ? 'accepted' : 'rejected', respondedAt: Date.now() }
  // writeBatch suporta até 500 operações
  for (let i = 0; i < ids.length; i += 450) {
    const batch = writeBatch(db)
    for (const id of ids.slice(i, i + 450)) batch.update(doc(db, COLLECTIONS.transactions, id), patch)
    await batch.commit()
  }
}

// ------------------------------------------------------------------
// Regras e Memória do Agente (Persistência no Firestore)
// ------------------------------------------------------------------
export function subscribeAgentRules(uid: string, cb: (rules: UserLearnedRule[]) => void) {
  if (!isFirebaseConfigured || !uid || uid === 'guest') return () => {}
  try {
    const { db } = ensure()
    const q = collection(db, COLLECTIONS.users, uid, COLLECTIONS.agentRules)
    return onSnapshot(
      q,
      (snap) => {
        const list = snap.docs.map((d) => ({ ...(d.data() as UserLearnedRule), id: d.id }))
        list.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
        cb(list)
      },
      (err) => {
        console.warn('Erro ao sincronizar regras do Firestore:', err)
      }
    )
  } catch (err) {
    console.warn('Falha ao inicializar listener de regras do Firestore:', err)
    return () => {}
  }
}

export async function fsSaveAgentRule(uid: string, rule: UserLearnedRule) {
  if (!isFirebaseConfigured || !uid || uid === 'guest') return
  try {
    const { db } = ensure()
    const ref = doc(db, COLLECTIONS.users, uid, COLLECTIONS.agentRules, rule.id)
    await setDoc(ref, stripUndefined({ ...rule, ownerUid: uid }), { merge: true })
  } catch (err) {
    console.warn('Erro ao salvar regra no Firestore:', err)
  }
}

export async function fsDeleteAgentRule(uid: string, ruleId: string) {
  if (!isFirebaseConfigured || !uid || uid === 'guest') return
  try {
    const { db } = ensure()
    const ref = doc(db, COLLECTIONS.users, uid, COLLECTIONS.agentRules, ruleId)
    await deleteDoc(ref)
  } catch (err) {
    console.warn('Erro ao excluir regra no Firestore:', err)
  }
}

// ------------------------------------------------------------------
// Histórico de Conversas e Sessões de Chat (Persistência no Firestore)
// ------------------------------------------------------------------
export function subscribeChatSessions(uid: string, cb: (sessions: ChatSession[]) => void) {
  if (!isFirebaseConfigured || !uid || uid === 'guest') return () => {}
  try {
    const { db } = ensure()
    const q = collection(db, COLLECTIONS.users, uid, COLLECTIONS.chatSessions)
    return onSnapshot(
      q,
      (snap) => {
        const list = snap.docs.map((d) => ({ ...(d.data() as ChatSession), id: d.id }))
        list.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
        cb(list)
      },
      (err) => {
        console.warn('Erro ao sincronizar sessões de chat do Firestore:', err)
      }
    )
  } catch (err) {
    console.warn('Falha ao inicializar listener de sessões de chat do Firestore:', err)
    return () => {}
  }
}

export async function fsSaveChatSession(uid: string, session: ChatSession) {
  if (!isFirebaseConfigured || !uid || uid === 'guest') return
  try {
    const { db } = ensure()
    const ref = doc(db, COLLECTIONS.users, uid, COLLECTIONS.chatSessions, session.id)
    // Remove dados binários pesados de anexos (base64) para garantir conformidade com limites do Firestore
    const sanitizedMessages = (session.messages || []).map((m) => {
      if (m.attachment?.base64) {
        const { base64: _omitted, ...safeAttachment } = m.attachment
        return { ...m, attachment: safeAttachment }
      }
      return m
    })
    const payload = stripUndefined({
      ...session,
      messages: sanitizedMessages,
      ownerUid: uid,
    })
    await setDoc(ref, payload, { merge: true })
  } catch (err) {
    console.warn('Erro ao salvar sessão de chat no Firestore:', err)
  }
}

export async function fsDeleteChatSession(uid: string, sessionId: string) {
  if (!isFirebaseConfigured || !uid || uid === 'guest') return
  try {
    const { db } = ensure()
    const ref = doc(db, COLLECTIONS.users, uid, COLLECTIONS.chatSessions, sessionId)
    await deleteDoc(ref)
  } catch (err) {
    console.warn('Erro ao excluir sessão de chat no Firestore:', err)
  }
}

