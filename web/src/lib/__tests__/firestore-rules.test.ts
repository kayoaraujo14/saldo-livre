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

  describe('1. Coleção /users/{userId} (Perfis de Usuário Privados)', () => {
    it('permite que o próprio usuário leia seu perfil privado (/users/{userId})', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'users', 'alice'), {
          displayName: 'Alice',
          monthlyIncome: 5000,
          emergencyReserveBalance: 20000,
        })
      })

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      await assertSucceeds(getDoc(doc(aliceDb, 'users', 'alice')))
    })

    it('bloqueia outro usuário autenticado (ex: Bob) de ler o perfil privado de outrem (/users/alice)', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'users', 'alice'), {
          displayName: 'Alice',
          monthlyIncome: 5000,
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      await assertFails(getDoc(doc(bobDb, 'users', 'alice')))
    })

    it('impede que usuários anônimos/não autenticados leiam perfis privados', async () => {
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

  describe('1.1 Coleção /public_profiles/{userId} (Perfis Públicos para Amizades)', () => {
    it('permite que qualquer usuário autenticado leia perfis públicos', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'public_profiles', 'alice'), {
          uid: 'alice',
          email: 'alice@example.com',
          displayName: 'Alice Silva',
          photoURL: 'https://example.com/alice.png',
          updatedAt: Date.now(),
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      await assertSucceeds(getDoc(doc(bobDb, 'public_profiles', 'alice')))
    })

    it('impede que usuários anônimos/não autenticados leiam perfis públicos', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'public_profiles', 'alice'), {
          uid: 'alice',
          displayName: 'Alice Silva',
        })
      })

      const unauthDb = testEnv.unauthenticatedContext().firestore()
      await assertFails(getDoc(doc(unauthDb, 'public_profiles', 'alice')))
    })

    it('permite que apenas o proprietário crie ou atualize seu perfil público', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobDb = testEnv.authenticatedContext('bob').firestore()

      // Alice (proprietária) cria seu perfil público com sucesso
      await assertSucceeds(
        setDoc(doc(aliceDb, 'public_profiles', 'alice'), {
          uid: 'alice',
          email: 'alice@example.com',
          displayName: 'Alice Silva',
          photoURL: 'https://example.com/avatar.jpg',
          updatedAt: Date.now(),
        })
      )

      // Alice atualiza seu perfil público com sucesso
      await assertSucceeds(
        updateDoc(doc(aliceDb, 'public_profiles', 'alice'), {
          displayName: 'Alice Santos',
        })
      )

      // Bob tenta criar o perfil de Alice -> falha
      await assertFails(
        setDoc(doc(bobDb, 'public_profiles', 'alice'), {
          uid: 'alice',
          displayName: 'Bob Hacking',
        })
      )

      // Bob tenta atualizar o perfil de Alice -> falha
      await assertFails(
        updateDoc(doc(bobDb, 'public_profiles', 'alice'), {
          displayName: 'Bob Overwrite',
        })
      )
    })

    it('bloqueia injeção de campos não autorizados (ex: monthlyIncome) em /public_profiles devido a hasOnly', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const aliceRef = doc(aliceDb, 'public_profiles', 'alice')

      // Tentativa de criar com campo não autorizado (monthlyIncome)
      await assertFails(
        setDoc(aliceRef, {
          uid: 'alice',
          displayName: 'Alice',
          monthlyIncome: 10000,
        })
      )

      // Criação inicial válida
      await assertSucceeds(
        setDoc(aliceRef, {
          uid: 'alice',
          displayName: 'Alice',
        })
      )

      // Tentativa de injetar campo não autorizado em update
      await assertFails(
        updateDoc(aliceRef, {
          monthlyIncome: 10000,
        })
      )
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

  describe('6. Coleção /friendships/{friendshipId} (Rede de Amizades e Anti-Spoofing)', () => {
    it('permite que o solicitante crie pedido de amizade válido', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const fRef = doc(aliceDb, 'friendships', 'friendship_alice_bob')

      await assertSucceeds(
        setDoc(fRef, {
          requesterUid: 'alice',
          recipientUid: 'bob',
          status: 'pending',
          createdAt: Date.now(),
        })
      )
    })

    it('impede criação com requesterUid forjado ou auto-amizade (recipientUid == requesterUid)', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const bobDb = testEnv.authenticatedContext('bob').firestore()

      // Bob tenta se passar por Alice no requesterUid
      await assertFails(
        setDoc(doc(bobDb, 'friendships', 'fake_request'), {
          requesterUid: 'alice',
          recipientUid: 'charlie',
          status: 'pending',
        })
      )

      // Bob tenta adicionar a si mesmo
      await assertFails(
        setDoc(doc(bobDb, 'friendships', 'self_request'), {
          requesterUid: 'bob',
          recipientUid: 'bob',
          status: 'pending',
        })
      )
    })

    it('permite leitura apenas por requester e recipient; bloqueia terceiros', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'friendships', 'f_alice_bob'), {
          requesterUid: 'alice',
          recipientUid: 'bob',
          status: 'pending',
        })
      })

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobDb = testEnv.authenticatedContext('bob').firestore()
      const eveDb = testEnv.authenticatedContext('eve').firestore()

      await assertSucceeds(getDoc(doc(aliceDb, 'friendships', 'f_alice_bob')))
      await assertSucceeds(getDoc(doc(bobDb, 'friendships', 'f_alice_bob')))
      await assertFails(getDoc(doc(eveDb, 'friendships', 'f_alice_bob')))
    })

    it('garante que requesterUid e recipientUid sejam estritamente imutáveis no update', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'friendships', 'f_mutable_test'), {
          requesterUid: 'alice',
          recipientUid: 'bob',
          status: 'pending',
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      const fRef = doc(bobDb, 'friendships', 'f_mutable_test')

      // Bob aceita o pedido -> PERMITIDO
      await assertSucceeds(updateDoc(fRef, { status: 'accepted' }))

      // Bob tenta adulterar o recipientUid para outra pessoa -> BLOQUEADO
      await assertFails(updateDoc(fRef, { recipientUid: 'eve' }))

      // Bob tenta adulterar o requesterUid -> BLOQUEADO
      await assertFails(updateDoc(fRef, { requesterUid: 'eve' }))
    })

    it('permite exclusão por requester ou recipient, bloqueando terceiros', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'friendships', 'f_del_test'), {
          requesterUid: 'alice',
          recipientUid: 'bob',
          status: 'accepted',
        })
      })

      const eveDb = testEnv.authenticatedContext('eve').firestore()
      const aliceDb = testEnv.authenticatedContext('alice').firestore()

      await assertFails(deleteDoc(doc(eveDb, 'friendships', 'f_del_test')))
      await assertSucceeds(deleteDoc(doc(aliceDb, 'friendships', 'f_del_test')))
    })
  })

  describe('7. Coleções Top-Level (/agent_rules, /chat_sessions, /month_closures) - Issue #57 Hardening', () => {
    it('bloqueia ataque de sobrescrita cruzada em /agent_rules (Update Bypass)', async () => {
      if (!isEmulatorRunning || !testEnv) return

      // Alice cria sua regra legítima
      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'agent_rules', 'rule_alice_top'), {
          ownerUid: 'alice',
          rule: 'Regra legítima de Alice',
          active: true,
        })
      })

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobDb = testEnv.authenticatedContext('bob').firestore()
      const targetRefForBob = doc(bobDb, 'agent_rules', 'rule_alice_top')
      const targetRefForAlice = doc(aliceDb, 'agent_rules', 'rule_alice_top')

      // Bob tenta sobrescrever a regra de Alice injetando seu próprio ownerUid no payload
      await assertFails(
        updateDoc(targetRefForBob, {
          ownerUid: 'bob',
          rule: 'Invasão por Bob',
        })
      )

      // Bob tenta alterar a regra mantendo ownerUid de Alice
      await assertFails(
        updateDoc(targetRefForBob, {
          rule: 'Invasão sutil',
        })
      )

      // Bob tenta ler ou deletar a regra de Alice
      await assertFails(getDoc(targetRefForBob))
      await assertFails(deleteDoc(targetRefForBob))

      // Alice pode atualizar legitimamente sua regra
      await assertSucceeds(
        updateDoc(targetRefForAlice, {
          rule: 'Regra atualizada pela Alice',
        })
      )

      // Alice tenta trocar ownerUid para outra pessoa -> BLOQUEADO (imutabilidade)
      await assertFails(
        updateDoc(targetRefForAlice, {
          ownerUid: 'charlie',
        })
      )
    })

    it('bloqueia ataque de sobrescrita cruzada em /chat_sessions', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'chat_sessions', 'sess_alice'), {
          ownerUid: 'alice',
          title: 'Conversa Secreta de Alice',
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobTargetRef = doc(bobDb, 'chat_sessions', 'sess_alice')
      const aliceTargetRef = doc(aliceDb, 'chat_sessions', 'sess_alice')

      // Bob tenta sobrescrever com seu próprio ownerUid
      await assertFails(
        updateDoc(bobTargetRef, {
          ownerUid: 'bob',
          title: 'Sessão Sequestrada',
        })
      )

      // Bob não pode ler nem excluir
      await assertFails(getDoc(bobTargetRef))
      await assertFails(deleteDoc(bobTargetRef))

      // Alice pode atualizar
      await assertSucceeds(updateDoc(aliceTargetRef, { title: 'Conversa Atualizada' }))

      // Alice não pode transferir ownership
      await assertFails(updateDoc(aliceTargetRef, { ownerUid: 'bob' }))
    })

    it('bloqueia ataque de sobrescrita cruzada em /month_closures', async () => {
      if (!isEmulatorRunning || !testEnv) return

      await testEnv.withSecurityRulesDisabled(async (context) => {
        await setDoc(doc(context.firestore(), 'month_closures', 'cls_alice_2026_04'), {
          uid: 'alice',
          closedMonth: '2026-04',
          totalSurplus: 1500,
        })
      })

      const bobDb = testEnv.authenticatedContext('bob').firestore()
      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobTargetRef = doc(bobDb, 'month_closures', 'cls_alice_2026_04')
      const aliceTargetRef = doc(aliceDb, 'month_closures', 'cls_alice_2026_04')

      // Bob tenta sobrescrever passando seu próprio uid
      await assertFails(
        updateDoc(bobTargetRef, {
          uid: 'bob',
          totalSurplus: 0,
        })
      )

      // Bob não pode ler nem excluir
      await assertFails(getDoc(bobTargetRef))
      await assertFails(deleteDoc(bobTargetRef))

      // Alice pode atualizar legitimamente
      await assertSucceeds(updateDoc(aliceTargetRef, { totalSurplus: 1800 }))

      // Alice não pode alterar o campo uid (imutabilidade)
      await assertFails(updateDoc(aliceTargetRef, { uid: 'charlie' }))
    })
  })

  describe('8. Subcoleções de Usuário (/users/{userId}/chat_sessions e month_closures)', () => {
    it('garante isolamento estrito de /chat_sessions e /month_closures sob /users/{userId}', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const bobDb = testEnv.authenticatedContext('bob').firestore()

      const aliceChatRef = doc(aliceDb, 'users', 'alice', 'chat_sessions', 'sess_sub_1')
      const aliceClosureRef = doc(aliceDb, 'users', 'alice', 'month_closures', 'cls_sub_1')

      await assertSucceeds(setDoc(aliceChatRef, { title: 'Chat Alice', updatedAt: Date.now() }))
      await assertSucceeds(setDoc(aliceClosureRef, { closedMonth: '2026-04', surplus: 500 }))

      // Bob não pode ler, escrever nem deletar as subcoleções de Alice
      const bobChatRef = doc(bobDb, 'users', 'alice', 'chat_sessions', 'sess_sub_1')
      const bobClosureRef = doc(bobDb, 'users', 'alice', 'month_closures', 'cls_sub_1')

      await assertFails(getDoc(bobChatRef))
      await assertFails(updateDoc(bobChatRef, { title: 'Hackeado' }))
      await assertFails(deleteDoc(bobChatRef))

      await assertFails(getDoc(bobClosureRef))
      await assertFails(updateDoc(bobClosureRef, { surplus: 0 }))
      await assertFails(deleteDoc(bobClosureRef))
    })
  })

  describe('9. Imutabilidade de Campos Críticos (/cards, /recurring_bills, /transactions)', () => {
    it('impede alteração do titular em /cards/{cardId}', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const cardRef = doc(aliceDb, 'cards', 'card_immut_test')

      await assertSucceeds(
        setDoc(cardRef, {
          ownerUid: 'alice',
          cardName: 'Cartão Alice',
        })
      )

      await assertFails(
        updateDoc(cardRef, {
          ownerUid: 'bob',
        })
      )
    })

    it('impede alteração do titular em /recurring_bills/{billId}', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const billRef = doc(aliceDb, 'recurring_bills', 'bill_immut_test')

      await assertSucceeds(
        setDoc(billRef, {
          ownerUid: 'alice',
          description: 'Internet',
          amount: 120,
        })
      )

      await assertFails(
        updateDoc(billRef, {
          ownerUid: 'bob',
        })
      )
    })

    it('impede alteração do createdByUid em /transactions/{txId} (anti-spoofing)', async () => {
      if (!isEmulatorRunning || !testEnv) return

      const aliceDb = testEnv.authenticatedContext('alice').firestore()
      const txRef = doc(aliceDb, 'transactions', 'tx_immut_test')

      await assertSucceeds(
        setDoc(txRef, {
          createdByUid: 'alice',
          paidByUid: 'alice',
          visibleToUids: ['alice'],
          totalAmount: 50,
          description: 'Café',
        })
      )

      // Alice tenta transferir a autoria para Bob -> BLOQUEADO
      await assertFails(
        updateDoc(txRef, {
          createdByUid: 'bob',
        })
      )
    })
  })
})


