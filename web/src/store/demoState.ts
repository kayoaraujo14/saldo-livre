import type { DemoState } from './types'
import { DEMO_CARDS, DEMO_RECURRING, DEMO_USERS, buildDemoTransactions } from '@/lib/mockData'
import { buildSplit, type Party } from '@/lib/finance'
import { currentMonthKey, dateInMonth, shiftMonth } from '@/lib/utils'
import type { Transaction } from '@/types'

export const DEMO_KEY = 'saldo-livre:demo-state:v3'
export const FORCE_DEMO_KEY = 'saldo-livre:force-demo'

export function freshDemo(): DemoState {
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

export function loadDemo(): DemoState {
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
