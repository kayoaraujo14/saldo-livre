import { describe, it, expect } from 'vitest'
import {
  invoiceDates,
  buildCardInvoice,
  buildInvoiceHistory,
  dueCountdownLabel,
  getTransactionParticipantShares,
  participantAmountIn,
  calculateCategoryBreakdownForParticipant,
  createConsolidatedInvoiceTx,
  consolidateMonthTransactions,
  computePredictiveAlert,
} from '../invoices'
import type { Card, Transaction } from '@/types'

describe('invoices.ts - Motor de Faturas e Ciclos de Cartão', () => {
  const ownerUid = 'user_owner'
  const guestUid = 'user_guest'

  const sampleCard: Card = {
    id: 'card_nubank',
    ownerUid,
    ownerName: 'Kayo Titular',
    cardName: 'Nubank Ultravioleta',
    color: '#820AD1',
    closingDay: 20,
    dueDay: 27,
    limit: 5000,
    sharedWithUids: [guestUid],
  }

  const invertedCard: Card = {
    id: 'card_inter',
    ownerUid,
    ownerName: 'Kayo Titular',
    cardName: 'Inter Black',
    color: '#FF7A00',
    closingDay: 25,
    dueDay: 5, // Vence no mês seguinte (dueDay < closingDay)
    limit: 10000,
  }

  const buildExpense = (overrides: Partial<Transaction>): Transaction => ({
    id: `tx_${Math.random().toString(36).substring(2, 9)}`,
    createdByUid: ownerUid,
    paidByUid: ownerUid,
    cardId: sampleCard.id,
    description: 'Compra Teste',
    category: 'Alimentação',
    type: 'expense',
    paymentMethod: 'credit_card',
    status: 'pending',
    estimatedAmount: null,
    actualAmount: 100,
    totalAmount: 100,
    dueDate: '2026-10-27',
    competenceMonth: '2026-10',
    visibleToUids: [ownerUid],
    createdAt: Date.now(),
    ...overrides,
  })

  const buildIncomeCredit = (overrides: Partial<Transaction>): Transaction => ({
    id: `tx_credit_${Math.random().toString(36).substring(2, 9)}`,
    createdByUid: ownerUid,
    paidByUid: ownerUid,
    cardId: sampleCard.id,
    description: 'Estorno / Cashback',
    category: 'Outros',
    type: 'income',
    paymentMethod: 'credit_card',
    status: 'paid',
    estimatedAmount: null,
    actualAmount: 50,
    totalAmount: 50,
    dueDate: '2026-10-27',
    competenceMonth: '2026-10',
    visibleToUids: [ownerUid],
    createdAt: Date.now(),
    ...overrides,
  })

  describe('1. Ciclos de Corte e Datas da Fatura (invoiceDates)', () => {
    it('calcula corretamente quando vencimento e fechamento ocorrem no mesmo mês (dueDay >= closingDay)', () => {
      const dates = invoiceDates(sampleCard, '2026-10')
      expect(dates.closingDate).toBe('2026-10-20')
      expect(dates.dueDate).toBe('2026-10-27')
    })

    it('calcula ciclo invertido onde fechamento ocorreu no mês anterior (dueDay < closingDay)', () => {
      // Vence em 2026-11-05, mas fechou em 2026-10-25
      const dates = invoiceDates(invertedCard, '2026-11')
      expect(dates.closingDate).toBe('2026-10-25')
      expect(dates.dueDate).toBe('2026-11-05')
    })

    it('faz clamp seguro no final de meses curtos (ex: Fevereiro e meses de 30 dias)', () => {
      const endOfMonthCard: Card = {
        ...sampleCard,
        closingDay: 31,
        dueDay: 31,
      }
      // Fevereiro em ano não bissexto (2026) -> 28 dias
      const febDates = invoiceDates(endOfMonthCard, '2026-02')
      expect(febDates.closingDate).toBe('2026-02-28')
      expect(febDates.dueDate).toBe('2026-02-28')

      // Abril tem 30 dias
      const aprDates = invoiceDates(endOfMonthCard, '2026-04')
      expect(aprDates.closingDate).toBe('2026-04-30')
      expect(aprDates.dueDate).toBe('2026-04-30')
    })
  })

  describe('2. Ciclos de Vida e Transições de Status da Fatura', () => {
    it('retorna status "empty" quando não existem despesas no mês de competência', () => {
      const invoice = buildCardInvoice(sampleCard, '2026-10', [], ownerUid, '2026-10-15')
      expect(invoice.status).toBe('empty')
      expect(invoice.total).toBe(0)
      expect(invoice.itemsCount).toBe(0)
    })

    it('retorna status "open" quando a data atual é anterior à data de fechamento', () => {
      const tx = buildExpense({ actualAmount: 150 })
      const invoice = buildCardInvoice(sampleCard, '2026-10', [tx], ownerUid, '2026-10-10')
      expect(invoice.status).toBe('open')
      expect(invoice.openAmount).toBe(150)
      expect(invoice.paidAmount).toBe(0)
      expect(invoice.daysToClose).toBe(10)
    })

    it('retorna status "closed" quando a fatura já fechou mas ainda está no prazo de vencimento', () => {
      const tx = buildExpense({ actualAmount: 200 })
      // Fechou dia 20, hoje é 22, vence dia 27
      const invoice = buildCardInvoice(sampleCard, '2026-10', [tx], ownerUid, '2026-10-22')
      expect(invoice.status).toBe('closed')
      expect(invoice.daysToDue).toBe(5)
    })

    it('retorna status "overdue" quando passou da data de vencimento e não foi quitada', () => {
      const tx = buildExpense({ actualAmount: 300, status: 'pending' })
      // Venceu dia 27, hoje é 29
      const invoice = buildCardInvoice(sampleCard, '2026-10', [tx], ownerUid, '2026-10-29')
      expect(invoice.status).toBe('overdue')
      expect(invoice.daysToDue).toBe(-2)
    })

    it('retorna status "paid" quando todas as despesas da fatura estão quitadas', () => {
      const tx1 = buildExpense({ actualAmount: 100, status: 'paid' })
      const tx2 = buildExpense({ actualAmount: 250, status: 'paid' })
      const invoice = buildCardInvoice(sampleCard, '2026-10', [tx1, tx2], ownerUid, '2026-10-22')
      expect(invoice.status).toBe('paid')
      expect(invoice.paidAmount).toBe(350)
      expect(invoice.openAmount).toBe(0)
    })
  })

  describe('3. Faturas com Estornos, Créditos e Devoluções', () => {
    it('deduz créditos e estornos do total bruto da fatura', () => {
      const exp1 = buildExpense({ actualAmount: 400 })
      const exp2 = buildExpense({ actualAmount: 150 })
      const refund = buildIncomeCredit({ actualAmount: 100 }) // estorno de 100

      const invoice = buildCardInvoice(sampleCard, '2026-10', [exp1, exp2, refund], ownerUid)
      expect(invoice.grossExpenses).toBe(550)
      expect(invoice.credits).toBe(100)
      expect(invoice.total).toBe(450)
    })

    it('impede que o total da fatura fique negativo se o estorno superar as despesas', () => {
      const exp = buildExpense({ actualAmount: 80 })
      const refund = buildIncomeCredit({ actualAmount: 120 })

      const invoice = buildCardInvoice(sampleCard, '2026-10', [exp, refund], ownerUid)
      expect(invoice.grossExpenses).toBe(80)
      expect(invoice.credits).toBe(120)
      expect(invoice.total).toBe(0)
      expect(invoice.openAmount).toBe(0)
    })

    it('recalcula o rateio do titular subtraindo os créditos da sua cota', () => {
      const exp = buildExpense({ actualAmount: 300 })
      const refund = buildIncomeCredit({ actualAmount: 50 })

      const invoice = buildCardInvoice(sampleCard, '2026-10', [exp, refund], ownerUid)
      expect(invoice.total).toBe(250)
      expect(invoice.myShare).toBe(250)
    })

    it('limita paidAmount ao total da fatura mesmo se itens quitados superarem o total líquido após estorno', () => {
      const exp1 = buildExpense({ actualAmount: 200, status: 'paid' })
      const refund = buildIncomeCredit({ actualAmount: 50 }) // total líquido = 150

      const invoice = buildCardInvoice(sampleCard, '2026-10', [exp1, refund], ownerUid)
      expect(invoice.total).toBe(150)
      expect(invoice.paidAmount).toBe(150)
      expect(invoice.openAmount).toBe(0)
    })
  })

  describe('4. Rateios Mistos e Divisão de Despesas (getTransactionParticipantShares)', () => {
    it('rateia 100% para o titular quando splitType é padrão ou ausente', () => {
      const tx = buildExpense({ actualAmount: 120, splitType: 'mine' })
      const shares = getTransactionParticipantShares(tx, ownerUid, sampleCard)
      expect(shares.get('__me__')).toBe(120)
      expect(shares.size).toBe(1)
    })

    it('divide exatamente 50/50 com parceiro(a) e reconcilia centavos ímpares', () => {
      // 100.01 dividido por 2 -> 50.00 e 50.00 -> sobra 0.01 reconciliado para o titular
      const tx = buildExpense({ actualAmount: 100.01, splitType: 'split_50', externalName: 'Camila' })
      const shares = getTransactionParticipantShares(tx, ownerUid, sampleCard)
      expect(shares.get('__me__')).toBe(50.01)
      expect(shares.get('Camila')).toBe(50.0)
      expect(Number(((shares.get('__me__') ?? 0) + (shares.get('Camila') ?? 0)).toFixed(2))).toBe(100.01)
    })

    it('atribui 100% para o parceiro(a) quando splitType é partner', () => {
      const tx = buildExpense({ actualAmount: 85.5, splitType: 'partner', externalName: 'Amor' })
      const shares = getTransactionParticipantShares(tx, ownerUid, sampleCard)
      expect(shares.get('Amor')).toBe(85.5)
      expect(shares.has('__me__')).toBe(false)
    })

    it('divide proporcionalmente com amigo atribuído (ex: 30% amigo, 70% titular)', () => {
      const tx = buildExpense({
        actualAmount: 200,
        splitType: 'friend',
        assignedFriendName: 'Carlos',
        friendSharePct: 30,
      })
      const shares = getTransactionParticipantShares(tx, ownerUid, sampleCard)
      expect(shares.get('Carlos')).toBe(60) // 30%
      expect(shares.get('__me__')).toBe(140) // 70%
    })

    it('processa alocações explícitas customizadas com múltiplos participantes', () => {
      const tx = buildExpense({
        actualAmount: 150,
        allocations: [
          { type: 'PLATFORM_USER', uid: ownerUid, name: 'Você (Minha parte)', amount: 50, reimbursed: false },
          { type: 'EXTERNAL', name: 'Rodrigo', amount: 40, reimbursed: false },
          { type: 'EXTERNAL', name: 'Fernanda', amount: 60, reimbursed: false },
        ],
      })
      const shares = getTransactionParticipantShares(tx, ownerUid, sampleCard)
      expect(shares.get('__me__')).toBe(50)
      expect(shares.get('Rodrigo')).toBe(40)
      expect(shares.get('Fernanda')).toBe(60)
    })

    it('identifica corretamente a perspectiva de um amigo/convidado visualizando a fatura', () => {
      // O amigo convidado (guestUid) é o visualizador
      const tx = buildExpense({
        actualAmount: 100,
        splitType: 'friend',
        assignedFriendUid: guestUid,
        assignedFriendName: 'Convidado',
        friendSharePct: 50,
      })
      const shares = getTransactionParticipantShares(tx, guestUid, sampleCard)
      // Para o convidado, sua própria cota é indexada por '__me__'
      expect(shares.get('__me__')).toBe(50)
      // A cota do titular aparece com o nome do titular
      expect(shares.get('Kayo Titular')).toBe(50)
    })
  })

  describe('5. Limite do Cartão e Comprometimento Futuro de Parcelas', () => {
    it('calcula comprometimento futuro somando parcelas em meses posteriores', () => {
      const currentMonthTx = buildExpense({ competenceMonth: '2026-10', actualAmount: 500 })
      const futureTx1 = buildExpense({ competenceMonth: '2026-11', actualAmount: 300, installmentsTotal: 3 })
      const futureTx2 = buildExpense({ competenceMonth: '2026-12', actualAmount: 300, installmentsTotal: 3 })

      const invoice = buildCardInvoice(sampleCard, '2026-10', [currentMonthTx, futureTx1, futureTx2], ownerUid)
      expect(invoice.total).toBe(500)
      expect(invoice.futureInstallmentsTotal).toBe(600)
      expect(invoice.futureInstallmentsCount).toBe(2)
      expect(invoice.totalCommitted).toBe(1100) // 500 aberto + 600 futuro
      expect(invoice.availableLimit).toBe(3900) // 5000 - 1100
      expect(invoice.limitUsagePct).toBe(22) // round(1100/5000 * 100)
    })

    it('libera o comprometimento atual do limite se a fatura já estiver quitada (status paid)', () => {
      const currentPaidTx = buildExpense({ competenceMonth: '2026-10', actualAmount: 500, status: 'paid' })
      const futureTx = buildExpense({ competenceMonth: '2026-11', actualAmount: 200 })

      const invoice = buildCardInvoice(sampleCard, '2026-10', [currentPaidTx, futureTx], ownerUid)
      expect(invoice.status).toBe('paid')
      expect(invoice.openAmount).toBe(0)
      // Total committed deve considerar apenas as parcelas futuras
      expect(invoice.totalCommitted).toBe(200)
      expect(invoice.availableLimit).toBe(4800)
    })
  })

  describe('6. Melhor Dia de Compra e Labels Temporais', () => {
    it('retorna contagem regressiva adequada e mensagem de melhor dia antes do fechamento', () => {
      const tx = buildExpense({ actualAmount: 100 })
      const invoice = buildCardInvoice(sampleCard, '2026-10', [tx], ownerUid, '2026-10-15')
      expect(invoice.bestPurchaseDay).toBe(20)
      expect(invoice.isBestPurchaseTime).toBe(false)
      expect(invoice.bestPurchaseHint).toContain('A partir do dia 20, compras caem só na próxima fatura')
      expect(dueCountdownLabel(invoice)).toBe('Fecha em 5 dias')
    })

    it('ativa o indicador de melhor época de compra logo após o fechamento da fatura', () => {
      const tx = buildExpense({ actualAmount: 100 })
      // Fechou dia 20, hoje é dia 22 (dentro da janela de 5 dias pós-fechamento)
      const invoice = buildCardInvoice(sampleCard, '2026-10', [tx], ownerUid, '2026-10-22')
      expect(invoice.isBestPurchaseTime).toBe(true)
      expect(invoice.bestPurchaseHint).toContain('Melhor época de compra ativa!')
      expect(dueCountdownLabel(invoice)).toBe('Vence em 5 dias')
    })
  })

  describe('7. Consolidação de Faturas para o Extrato Mensal (consolidateMonthTransactions)', () => {
    it('agrupa lançamentos de cartão em uma única transação consolidada preservando lançamentos avulsos', () => {
      const cardTx1 = buildExpense({ actualAmount: 100, category: 'Mercado' })
      const cardTx2 = buildExpense({ actualAmount: 200, category: 'Farmácia' })
      const pixTx: Transaction = {
        id: 'tx_pix',
        createdByUid: ownerUid,
        paidByUid: ownerUid,
        description: 'Aluguel via Pix',
        category: 'Moradia',
        type: 'expense',
        paymentMethod: 'pix',
        status: 'pending',
        estimatedAmount: null,
        actualAmount: 1200,
        totalAmount: 1200,
        dueDate: '2026-10-05',
        competenceMonth: '2026-10',
        visibleToUids: [ownerUid],
        createdAt: Date.now(),
      }

      const all = [cardTx1, cardTx2, pixTx]
      const consolidated = consolidateMonthTransactions(all, all, [sampleCard], ownerUid, '2026-10', true)

      // Deve ter: 1 transação Pix + 1 fatura consolidada
      expect(consolidated.length).toBe(2)
      const invTx = consolidated.find((t) => t.invoiceMeta?.isConsolidatedInvoice)
      expect(invTx).toBeDefined()
      expect(invTx?.totalAmount).toBe(300)
      expect(invTx?.invoiceMeta?.itemsCount).toBe(2)
      expect(invTx?.invoiceMeta?.isOwner).toBe(true)

      const remainingPix = consolidated.find((t) => t.id === 'tx_pix')
      expect(remainingPix).toBeDefined()
    })

    it('não gera fatura consolidada se o usuário convidado não tiver cota de gastos na fatura', () => {
      // Gasto 100% do titular
      const cardTx = buildExpense({ actualAmount: 300, splitType: 'mine' })
      const consolidatedGuest = consolidateMonthTransactions(
        [cardTx],
        [cardTx],
        [sampleCard],
        guestUid, // convidado
        '2026-10',
        true
      )
      // O convidado não deve ver fatura consolidada pois myShare é 0 e ele não é titular
      expect(consolidatedGuest.length).toBe(0)
    })
  })

  describe('8. Motor de Alertas Preditivos (computePredictiveAlert)', () => {
    it('detecta anomalia de gastos quando categoria supera média passada em mais de 25%', () => {
      const pastInvoice1 = buildCardInvoice(
        sampleCard,
        '2026-08',
        [buildExpense({ actualAmount: 100, category: 'Restaurante', competenceMonth: '2026-08' })],
        ownerUid
      )
      const pastInvoice2 = buildCardInvoice(
        sampleCard,
        '2026-09',
        [buildExpense({ actualAmount: 100, category: 'Restaurante', competenceMonth: '2026-09' })],
        ownerUid
      )

      // No mês atual, restaurante subiu para 250 (+150% de aumento vs média de 100)
      const currentTx = buildExpense({ actualAmount: 250, category: 'Restaurante', competenceMonth: '2026-10' })
      const currentInvoice = buildCardInvoice(sampleCard, '2026-10', [currentTx], ownerUid)

      const alert = computePredictiveAlert(currentInvoice, [currentTx], [pastInvoice1, pastInvoice2, currentInvoice])
      expect(alert.kind).toBe('anomaly')
      if (alert.kind === 'anomaly') {
        expect(alert.category).toBe('Restaurante')
        expect(alert.pctIncrease).toBeGreaterThanOrEqual(100)
        expect(alert.currentAmount).toBe(250)
      }
    })

    it('detecta nova assinatura recorrente detectada pela primeira vez no cartão', () => {
      // Primeira vez que aparece uma cobrança 'Netflix'
      const netflixTx = buildExpense({
        description: 'Netflix Mensal',
        category: 'Entretenimento',
        actualAmount: 55.9,
        competenceMonth: '2026-10',
      })
      const currentInvoice = buildCardInvoice(sampleCard, '2026-10', [netflixTx], ownerUid)

      const alert = computePredictiveAlert(currentInvoice, [netflixTx], [currentInvoice])
      expect(alert.kind).toBe('new_subscription')
      if (alert.kind === 'new_subscription') {
        expect(alert.serviceName).toBe('Netflix Mensal')
        expect(alert.amount).toBe(55.9)
      }
    })

    it('projeta o valor de fechamento com base na média diária de gastos', () => {
      const tx = buildExpense({ actualAmount: 300, competenceMonth: '2026-10' })
      // Ciclo: fecha dia 20. Se hoje for dia 10 (passaram 20 dias desde o ciclo anterior), restam dias
      const currentInvoice = buildCardInvoice(sampleCard, '2026-10', [tx], ownerUid, '2026-10-10')
      const alert = computePredictiveAlert(currentInvoice, [tx], [currentInvoice], '2026-10-10')

      expect(alert.kind).toBe('projection')
      if (alert.kind === 'projection') {
        expect(alert.projectedTotal).toBeGreaterThanOrEqual(300)
        expect(alert.dailyAverage).toBeGreaterThan(0)
      }
    })
  })

  describe('9. Histórico de Faturas e Detalhamento por Participante', () => {
    it('buildInvoiceHistory gera sequência ordenada de meses', () => {
      const history = buildInvoiceHistory(sampleCard, '2026-10', [], ownerUid, 3)
      expect(history.length).toBe(3)
      expect(history[0].monthKey).toBe('2026-08')
      expect(history[1].monthKey).toBe('2026-09')
      expect(history[2].monthKey).toBe('2026-10')
    })

    it('recalcula distribuição de categorias individual para a cota de um participante', () => {
      const tx1 = buildExpense({
        actualAmount: 100,
        category: 'Mercado',
        allocations: [
          { type: 'PLATFORM_USER', uid: ownerUid, name: 'Você (Minha parte)', amount: 60, reimbursed: false },
          { type: 'EXTERNAL', name: 'Rodrigo', amount: 40, reimbursed: false },
        ],
      })
      const tx2 = buildExpense({
        actualAmount: 80,
        category: 'Cinema',
        allocations: [{ type: 'EXTERNAL', name: 'Rodrigo', amount: 80, reimbursed: false }],
      })

      const participantRodrigo = { name: 'Rodrigo', amount: 120, pct: 67, isMe: false }
      const slices = calculateCategoryBreakdownForParticipant([tx1, tx2], participantRodrigo, ownerUid, sampleCard)

      expect(slices.length).toBe(2)
      // Rodrigo tem 80 em Cinema e 40 em Mercado (total 120)
      const cinemaSlice = slices.find((s) => s.category === 'Cinema')
      const mercadoSlice = slices.find((s) => s.category === 'Mercado')

      expect(cinemaSlice?.amount).toBe(80)
      expect(cinemaSlice?.pct).toBe(67) // 80/120 = 66.7% -> 67%
      expect(mercadoSlice?.amount).toBe(40)
      expect(mercadoSlice?.pct).toBe(33) // 40/120 = 33.3% -> 33%
    })
  })
})
