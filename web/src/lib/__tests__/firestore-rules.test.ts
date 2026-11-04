import { describe, it, beforeAll, afterAll, beforeEach } from 'vitest'
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing'
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
} from 'firebase/firestore'
import * as fs from 'fs'
import * as path from 'path'

const PROJECT_ID = 'saldo-livre-rules-test'
const rulesPath = path.resolve(__dirname, '../../../../firestore.rules')
const rules = fs.readFileSync(rulesPath, 'utf8')

const emulatorHost = process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080'

describe('firestore.rules - Blindagem e Isolamento de Privacidade', () => {
  let testEnv: RulesTestEnvironment | null = null
  let isEmulatorRunning = false

  beforeAll(async () => {
    try {
      testEnv = await initializeTestEnvironment({
        projectId: PROJECT_ID,
        firestore: {
          rules,
          host: emulatorHost.split(':')[0],
          port: Number(emulatorHost.split(':')[1] || 8080),
        },
      })
      isEmulatorRunning = true
    } catch {
      console.warn(
        `[AVISO] Emulador do Firestore não detectado em ${emulatorHost}. ` +
        `Para rodar suíte de regras ao vivo: npm run test:rules`
      )
      isEmulatorRunning = false
    }
  })

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup()
    }
  })

  beforeEach(async () => {
    if (testEnv && isEmulatorRunning) {
      await testEnv.clearFirestore()
    }
  })

  describe('1. Coleção /users/{userId} (Perfis de Usuário)', () => {
    it('permite que qualquer usuário autenticado leia perfis', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'users', 'alice'), { displayName: 'Alice' })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      await assertSucceeds(getDoc(doc(bobDb, 'users', 'alice')))
    })

    it('impede que usuários anônimos/não autenticados leiam perfis', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'users', 'alice'), { displayName: 'Alice' })
      })

      const unauthDb = testEnv.unauthenticatedContext().firestore()
      await assertFails(getDoc(doc(unauthDb, 'users', 'alice')))
    })

    it('permite que o próprio usuário edite seu perfil mas bloqueia terceiros', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobDb = testEnv.authenticatedContext('bob').firestore()

      await assertSucceeds(setDoc(doc(aliceDb, 'users', 'alice'), { displayName: 'Alice Silva' }))
      await assertFails(setDoc(doc(bobDb, 'users', 'alice'), { displayName: 'Bob Hacking Alice' }))
    })
  })

  describe('2. Coleção /cards/{cardId} (Cartões e Convidados)', () => {
    it('permite que o titular crie, leia, atualize e delete seu próprio cartão', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const cardRef = doc(aliceDb, 'cards', 'card_alice_1')

      await assertSucceeds(
        setDoc(cardRef, {
          ownerUid: 'alice',
          cardName: 'Nubank Alice',
          closingDay: 20,
          dueDay: 27,
          sharedWithUids: ['bob'],
        })
      )
      await assertSucceeds(updateDoc(cardRef, { cardName: 'Nubank Ultravioleta' }))
      await assertSucceeds(deleteDoc(cardRef))
    })

    it('impede que terceiros forjem cartões com ownerUid de outrem', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      await assertFails(
        setDoc(doc(bobDb, 'cards', 'fraud_card'), {
          ownerUid: 'alice',
          cardName: 'Cartão Fake',
        })
      )
    })

    it('permite que convidados (sharedWithUids) LEIAM o cartão, mas IMPEDE edição ou exclusão', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'cards', 'card_shared'), {
          ownerUid: 'alice',
          cardName: 'Cartão Casal',
          closingDay: 15,
          dueDay: 22,
          limit: 10000,
          sharedWithUids: ['bob'],
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      const sharedRef = doc(bobDb, 'cards', 'card_shared')

      await assertSucceeds(getDoc(sharedRef))
      await assertFails(updateDoc(sharedRef, { limit: 50000 }))
      await assertFails(deleteDoc(sharedRef))
    })

    it('impede que terceiros alheios leiam ou alterem cartões privados', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'cards', 'card_private'), {
          ownerUid: 'alice',
          cardName: 'Cartão Secreto',
          sharedWithUids: [],
        })
      })

      const eveDb = testEnv.authenticatedContext('eve').firestore()
      const privateRef = doc(eveDb, 'cards', 'card_private')

      await assertFails(getDoc(privateRef))
      await assertFails(updateDoc(privateRef, { cardName: 'Vandalismo' }))
      await assertFails(deleteDoc(privateRef))
    })
  })

  describe('3. Coleção /transactions/{txId} (Isolamento Estrito de Transações)', () => {
    it('permite leitura apenas por usuários envolvidos e bloqueia terceiros', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'transactions', 'tx_private'), {
          createdByUid: 'alice',
          paidByUid: 'alice',
          visibleToUids: ['alice'],
          totalAmount: 150,
          description: 'Consulta Médica Privada',
        })
      })

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const eveDb = testEnv.authenticatedContext('eve').firestore()

      await assertSucceeds(getDoc(doc(aliceDb, 'transactions', 'tx_private')))
      await assertFails(getDoc(doc(eveDb, 'transactions', 'tx_private')))
    })

    it('permite leitura da transação para o amigo atribuído no rateio', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'transactions', 'tx_split'), {
          createdByUid: 'alice',
          paidByUid: 'alice',
          assignedFriendUid: 'bob',
          visibleToUids: ['alice'],
          totalAmount: 200,
          description: 'Jantar Restaurante',
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      await assertSucceeds(getDoc(doc(bobDb, 'transactions', 'tx_split')))
    })

    it('amigo atribuído PODE aceitar/rejeitar rateio mas NÃO PODE alterar valores ou dados', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'transactions', 'tx_split'), {
          createdByUid: 'alice',
          paidByUid: 'alice',
          assignedFriendUid: 'bob',
          visibleToUids: ['alice', 'bob'],
          totalAmount: 200,
          description: 'Jantar',
          acceptanceStatus: 'pending',
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      const txRef = doc(bobDb, 'transactions', 'tx_split')

      // Bob tenta adulterar o valor -> BLOQUEADO!
      await assertFails(updateDoc(txRef, { totalAmount: 10 }))

      // Bob tenta alterar a descrição -> BLOQUEADO!
      await assertFails(updateDoc(txRef, { description: 'Alterado' }))

      // Bob apenas aceita o rateio -> PERMITIDO!
      await assertSucceeds(
        updateDoc(txRef, {
          acceptanceStatus: 'accepted',
          respondedAt: Date.now(),
        })
      )
    })

    it('apenas o criador original pode excluir uma transação', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'transactions', 'tx_del'), {
          createdByUid: 'alice',
          paidByUid: 'alice',
          visibleToUids: ['alice', 'bob'],
          assignedFriendUid: 'bob',
        })
      })

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobDb = testEnv.authenticatedContext('bob').firestore()

      await assertFails(deleteDoc(doc(bobDb, 'transactions', 'tx_del')))
      await assertSucceeds(deleteDoc(doc(aliceDb, 'transactions', 'tx_del')))
    })
  })

  describe('4. Coleção /recurring_bills e Memórias de IA /agent_rules', () => {
    it('isola moldes recorrentes estritamente para o dono', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobDb = testEnv.authenticatedContext('bob').firestore()

      await assertSucceeds(
        setDoc(doc(aliceDb, 'recurring_bills', 'bill_rent'), {
          ownerUid: 'alice',
          description: 'Aluguel',
          amount: 2500,
        })
      )

      await assertFails(getDoc(doc(bobDb, 'recurring_bills', 'bill_rent')))
      await assertFails(updateDoc(doc(bobDb, 'recurring_bills', 'bill_rent'), { amount: 100 }))
    })

    it('isola regras de aprendizado do agente (/users/{userId}/agent_rules) por usuário', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobDb = testEnv.authenticatedContext('bob').firestore()

      const aliceRuleRef = doc(aliceDb, 'users', 'alice', 'agent_rules', 'rule_uber')

      await assertSucceeds(
        setDoc(aliceRuleRef, {
          rule: 'Classificar Uber como Transporte',
          active: true,
        })
      )

      const bobTargetRef = doc(bobDb, 'users', 'alice', 'agent_rules', 'rule_uber')
      await assertFails(getDoc(bobTargetRef))
      await assertFails(setDoc(bobTargetRef, { rule: 'Regra adulterada', active: false }))
    })
  })

  describe('5. Coleção /users/{userId}/activity_logs (Logs de Atividades)', () => {
    it('permite que o próprio usuário crie, leia, atualize e delete seus logs de atividade', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const logRef = doc(aliceDb, 'users', 'alice', 'activity_logs', 'log_1')

      await assertSucceeds(
        setDoc(logRef, {
          title: 'Transação Criada',
          timestamp: Date.now(),
        })
      )
      await assertSucceeds(getDoc(logRef))
      await assertSucceeds(updateDoc(logRef, { title: 'Transação Atualizada' }))
      await assertSucceeds(deleteDoc(logRef))
    })

    it('impede que terceiros leiam ou manipulem os logs de outro usuário', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'users', 'alice', 'activity_logs', 'log_secret'), {
          title: 'Segredo de Alice',
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      const aliceLogRef = doc(bobDb, 'users', 'alice', 'activity_logs', 'log_secret')

      await assertFails(getDoc(aliceLogRef))
      await assertFails(setDoc(aliceLogRef, { title: 'Bob Espiando' }))
      await assertFails(updateDoc(aliceLogRef, { title: 'Bob Hackeando' }))
      await assertFails(deleteDoc(aliceLogRef))
    })
  })
})

