import { describe, it, expect } from 'vitest'
import {
  getMonthKey,
  shiftMonth,
  dateInMonth,
  isBillEligibleForMonth,
  buildAutoLaunchTransaction,
  getActivityLogCutoff,
  isActivityLogExpired,
  type RecurringBillData,
} from '../recurringBills.js'

describe('Motor de Contas Recorrentes do Backend (Cloud Functions)', () => {
  describe('Manipulação de Competência e Datas', () => {
    it('formata getMonthKey corretamente', () => {
      const fixedDate = new Date(Date.UTC(2026, 10, 8)) // Novembro = mês 10 (base 0)
      expect(getMonthKey(fixedDate)).toBe('2026-11')
    })

    it('desloca meses corretamente com shiftMonth', () => {
      expect(shiftMonth('2026-11', -1)).toBe('2026-10')
      expect(shiftMonth('2026-01', -1)).toBe('2025-12')
      expect(shiftMonth('2026-12', 1)).toBe('2027-01')
      expect(shiftMonth('2026-05', 3)).toBe('2026-08')
    })

    it('ajusta e trunca datas de vencimento no limite do mês (dateInMonth)', () => {
      // Novembro só tem 30 dias: dia 31 vira 30
      expect(dateInMonth('2026-11', 31)).toBe('2026-11-30')
      expect(dateInMonth('2026-11', 5)).toBe('2026-11-05')

      // Fevereiro de 2026 (ano comum) só tem 28 dias
      expect(dateInMonth('2026-02', 30)).toBe('2026-02-28')

      // Fevereiro de 2024 (ano bissexto) tem 29 dias
      expect(dateInMonth('2024-02', 31)).toBe('2024-02-29')

      // Entrada inválida ou 0 faz fallback seguro para o dia 01
      expect(dateInMonth('2026-11', 0)).toBe('2026-11-01')
    })
  })

  describe('Elegibilidade Temporal (isBillEligibleForMonth)', () => {
    it('rejeita contas desativadas (active: false)', () => {
      expect(isBillEligibleForMonth({ active: false }, '2026-11')).toBe(false)
    })

    it('rejeita contas cujo startMonth é futuro', () => {
      expect(isBillEligibleForMonth({ active: true, startMonth: '2026-12' }, '2026-11')).toBe(false)
      expect(isBillEligibleForMonth({ active: true, startMonth: '2026-11' }, '2026-11')).toBe(true)
      expect(isBillEligibleForMonth({ active: true, startMonth: '2026-10' }, '2026-11')).toBe(true)
    })

    it('rejeita contas cujo endMonth já expirou', () => {
      expect(isBillEligibleForMonth({ active: true, endMonth: '2026-10' }, '2026-11')).toBe(false)
      expect(isBillEligibleForMonth({ active: true, endMonth: '2026-11' }, '2026-11')).toBe(true)
      expect(isBillEligibleForMonth({ active: true, endMonth: '2026-12' }, '2026-11')).toBe(true)
    })
  })

  describe('Construção Canônica da Transação (buildAutoLaunchTransaction)', () => {
    const baseBill: RecurringBillData = {
      ownerUid: 'user-abc',
      description: 'Conta de Energia',
      category: 'Moradia',
      subcategory: 'Luz',
      dueDay: 15,
      defaultAmount: 250.75,
      estimationRule: 'fixed',
      active: true,
      splitType: 'mine',
      paymentMethod: 'bill',
    }

    it('gera payload com totalAmount, competenceMonth e allocations estritas', () => {
      const tx = buildAutoLaunchTransaction({
        bill: baseBill,
        billId: 'bill-123',
        currentMonth: '2026-11',
      })

      expect(tx.description).toBe('Conta de Energia')
      expect(tx.category).toBe('Moradia')
      expect(tx.subcategory).toBe('Luz')
      expect(tx.totalAmount).toBe(250.75)
      expect(tx.estimatedAmount).toBe(250.75)
      expect(tx.actualAmount).toBeNull()
      expect(tx.status).toBe('estimated')
      expect(tx.competenceMonth).toBe('2026-11')
      expect(tx.month).toBe('2026-11')
      expect(tx.dueDate).toBe('2026-11-15')
      expect(tx.createdByUid).toBe('user-abc')
      expect(tx.paidByUid).toBe('user-abc')
      expect(tx.recurringBillId).toBe('bill-123')
      expect(tx.isAutoLaunched).toBe(true)

      // Allocations e userImpact
      expect(tx.allocations).toHaveLength(1)
      expect(tx.allocations[0]).toEqual({
        type: 'PLATFORM_USER',
        uid: 'user-abc',
        name: 'Você',
        amount: 250.75,
        reimbursed: true,
      })
      expect(tx.userImpact['user-abc']).toBe(250.75)
    })

    it('configura status pending e actualAmount preenchido para contas em cartão de crédito', () => {
      const cardBill: RecurringBillData = {
        ...baseBill,
        description: 'Assinatura Streaming',
        category: 'Assinaturas & Serviços',
        paymentMethod: 'credit_card',
        cardId: 'card-master',
        defaultAmount: 59.9,
      }

      const tx = buildAutoLaunchTransaction({
        bill: cardBill,
        billId: 'bill-stream',
        currentMonth: '2026-11',
      })

      expect(tx.paymentMethod).toBe('credit_card')
      expect(tx.status).toBe('pending')
      expect(tx.estimatedAmount).toBe(59.9)
      expect(tx.actualAmount).toBe(59.9)
      expect(tx.cardId).toBe('card-master')
    })

    it('divide valores com precisão em divisão 50/50 entre participantes', () => {
      const sharedBill: RecurringBillData = {
        ...baseBill,
        description: 'Aluguel do Apartamento',
        defaultAmount: 3000.0,
        splitType: 'split_50',
        visibleToUids: ['user-abc', 'user-partner'],
      }

      const tx = buildAutoLaunchTransaction({
        bill: sharedBill,
        billId: 'bill-rent',
        currentMonth: '2026-11',
      })

      expect(tx.splitType).toBe('split_50')
      expect(tx.totalAmount).toBe(3000.0)
      expect(tx.allocations).toHaveLength(2)

      expect(tx.allocations[0]).toEqual({
        type: 'PLATFORM_USER',
        uid: 'user-abc',
        name: 'Você',
        amount: 1500.0,
        reimbursed: true,
      })
      expect(tx.allocations[1]).toEqual({
        type: 'PLATFORM_USER',
        uid: 'user-partner',
        name: 'Parceiro',
        amount: 1500.0,
        reimbursed: false,
      })

      expect(tx.userImpact['user-abc']).toBe(1500.0)
      expect(tx.userImpact['user-partner']).toBe(1500.0)
    })

    it('adota valor do mês anterior quando a regra for last_month e prevTxAmount existir', () => {
      const dynamicBill: RecurringBillData = {
        ...baseBill,
        description: 'Fatura de Água',
        defaultAmount: 100.0,
        estimationRule: 'last_month',
      }

      const txWithPrev = buildAutoLaunchTransaction({
        bill: dynamicBill,
        billId: 'bill-water',
        currentMonth: '2026-11',
        prevTxAmount: 184.32,
      })

      expect(txWithPrev.totalAmount).toBe(184.32)
      expect(txWithPrev.estimatedAmount).toBe(184.32)

      // Se prevTxAmount for nulo, faz fallback gracioso para o defaultAmount
      const txFallback = buildAutoLaunchTransaction({
        bill: dynamicBill,
        billId: 'bill-water',
        currentMonth: '2026-11',
        prevTxAmount: null,
      })

      expect(txFallback.totalAmount).toBe(100.0)
      expect(txFallback.estimatedAmount).toBe(100.0)
    })
  })

  describe('Retenção e TTL de activity_logs (timestamp numérico e Timestamp Firestore)', () => {
    it('calcula cutoffMs e avalia expiração para timestamps numéricos e objetos Date/Timestamp', () => {
      const now = new Date('2026-11-08T12:00:00.000Z')
      const { cutoffDate, cutoffMs } = getActivityLogCutoff(now, 180)

      expect(cutoffDate.getTime()).toBe(cutoffMs)
      expect(isActivityLogExpired(cutoffMs - 1000, cutoffMs)).toBe(true)
      expect(isActivityLogExpired(cutoffMs + 1000, cutoffMs)).toBe(false)
      expect(isActivityLogExpired(new Date(cutoffMs - 5000), cutoffMs)).toBe(true)
      expect(isActivityLogExpired({ toMillis: () => cutoffMs - 1 }, cutoffMs)).toBe(true)
      expect(isActivityLogExpired({ toMillis: () => cutoffMs + 1 }, cutoffMs)).toBe(false)
    })
  })
})
