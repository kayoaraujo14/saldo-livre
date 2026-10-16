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
import type { Card, Friendship, NewTransaction, RecurringBill, Transaction, UserProfile } from '@/types'
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

function resolveFirebaseConfig() {
  const env = import.meta.env
  if (env.VITE_FIREBASE_API_KEY && env.VITE_FIREBASE_PROJECT_ID) {
    return {
      apiKey: env.VITE_FIREBASE_API_KEY as string,
      authDomain: (env.VITE_FIREBASE_AUTH_DOMAIN as string) || `${env.VITE_FIREBASE_PROJECT_ID}.firebaseapp.com`,
      projectId: env.VITE_FIREBASE_PROJECT_ID as string,
      storageBucket: (env.VITE_FIREBASE_STORAGE_BUCKET as string) || `${env.VITE_FIREBASE_PROJECT_ID}.appspot.com`,
      messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID as string | undefined,
      appId: (env.VITE_FIREBASE_APP_ID as string) || '',
    }
  }
  const stored = getStoredFirebaseConfig()
  if (stored?.apiKey && stored?.projectId) {
    return {
      apiKey: stored.apiKey,
      authDomain: stored.authDomain || `${stored.projectId}.firebaseapp.com`,
      projectId: stored.projectId,
      storageBucket: stored.storageBucket || `${stored.projectId}.appspot.com`,
      messagingSenderId: stored.messagingSenderId,
      appId: stored.appId || '',
    }
  }
  return null
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
  return onSnapshot(doc(ensure().db, COLLECTIONS.users, uid), (s) => cb(s.exists() ? (s.data() as UserProfile) : null))
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
// Subscriptions (tempo real)
// ------------------------------------------------------------------
export function subscribeTransactions(uid: string, cb: (txs: Transaction[]) => void) {
  // array-contains sem outros filtros → não exige índice composto.
  const q = query(collection(ensure().db, COLLECTIONS.transactions), where('visibleToUids', 'array-contains', uid))
  return onSnapshot(q, (snap) => cb(snap.docs.map((d) => ({ ...(d.data() as NewTransaction), id: d.id }))))
}

export function subscribeCards(uid: string, cb: (cards: Card[]) => void) {
  const { db } = ensure()
  let owned: Card[] = []
  let shared: Card[] = []
  const emit = () => {
    const map = new Map<string, Card>()
    for (const c of [...owned, ...shared]) map.set(c.id, c)
    cb([...map.values()])
  }
  const u1 = onSnapshot(query(collection(db, COLLECTIONS.cards), where('ownerUid', '==', uid)), (s) => {
    owned = s.docs.map((d) => ({ ...(d.data() as Card), id: d.id }))
    emit()
  })
  const u2 = onSnapshot(query(collection(db, COLLECTIONS.cards), where('sharedWithUids', 'array-contains', uid)), (s) => {
    shared = s.docs.map((d) => ({ ...(d.data() as Card), id: d.id }))
    emit()
  })
  return () => {
    u1()
    u2()
  }
}

export function subscribeRecurring(uid: string, cb: (bills: RecurringBill[]) => void) {
  const q = query(collection(ensure().db, COLLECTIONS.recurring), where('ownerUid', '==', uid))
  return onSnapshot(q, (s) => cb(s.docs.map((d) => ({ ...(d.data() as RecurringBill), id: d.id }))))
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
  if (id && !id.startsWith('new_')) return setDoc(doc(db, COLLECTIONS.cards, id), stripUndefined(data))
  await addDoc(collection(db, COLLECTIONS.cards), stripUndefined(data))
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
  const { db } = ensure()
  let list1: Friendship[] = []
  let list2: Friendship[] = []

  const emit = () => {
    const map = new Map<string, Friendship>()
    for (const f of list1) map.set(f.id, f)
    for (const f of list2) map.set(f.id, f)
    cb([...map.values()])
  }

  const u1 = onSnapshot(query(collection(db, COLLECTIONS.friendships), where('requesterUid', '==', uid)), (s) => {
    list1 = s.docs.map((d) => ({ ...(d.data() as Friendship), id: d.id }))
    emit()
  })

  const u2 = onSnapshot(query(collection(db, COLLECTIONS.friendships), where('recipientUid', '==', uid)), (s) => {
    list2 = s.docs.map((d) => ({ ...(d.data() as Friendship), id: d.id }))
    emit()
  })

  return () => {
    u1()
    u2()
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
