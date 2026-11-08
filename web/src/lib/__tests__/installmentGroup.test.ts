import { describe, it, expect } from 'vitest'
import { stripInstallmentSuffix, formatInstallmentDesc } from '../utils'
import { buildSplit, calculateCardInvoiceDates } from '../finance'
import { Card } from '@/types'

describe('Issue #76: Motor de Parcelamento - Extinção de Sufixo, Isolamento de Status e Reembolsos', () => {
  describe('1. Extinção do Sufixo (X/Y) em Descrições', () => {
    it('deve remover sufixos no formato padrão (3/10)', () => {
      expect(stripInstallmentSuffix('Notebook Dell (3/10)')).toBe('Notebook Dell')
      expect(formatInstallmentDesc('Notebook Dell (3/10)', 3, 10)).toBe('Notebook Dell')
    })

    it('deve remover sufixos com espaçamentos variados ( 1 / 12 )', () => {
      expect(stripInstallmentSuffix('iPhone 15 Pro ( 1 / 12 )')).toBe('iPhone 15 Pro')
      expect(formatInstallmentDesc('iPhone 15 Pro ( 1 / 12 )', 1, 12)).toBe('iPhone 15 Pro')
    })

    it('deve remover sufixos no formato descritivo (2 de 6)', () => {
      expect(stripInstallmentSuffix('Curso de Inglês (02 de 06)')).toBe('Curso de Inglês')
      expect(formatInstallmentDesc('Curso de Inglês (02 de 06)', 2, 6)).toBe('Curso de Inglês')
    })

    it('não deve alterar descrições simples sem sufixo', () => {
      expect(stripInstallmentSuffix('Aluguel')).toBe('Aluguel')
      expect(formatInstallmentDesc('Aluguel', 1, 1)).toBe('Aluguel')
    })
  })

  describe('2. Preservação de Reembolsos de Amigos em Edições de Grupo', () => {
    it('deve preservar reimbursed: true da parcela anterior ao recalcular rateio', () => {
      const me = { uid: 'u_me', name: 'Eu' }
      const friend = { uid: 'u_friend', name: 'Amigo' }

      // Parcela 1 com reembolso já realizado
      const prevAllocations = [
        { type: 'PLATFORM_USER' as const, uid: 'u_me', name: 'Eu', amount: 50, reimbursed: true },
        { type: 'PLATFORM_USER' as const, uid: 'u_friend', name: 'Amigo', amount: 50, reimbursed: true },
      ]

      const splitResult = buildSplit({
        splitType: 'friend',
        total: 100,
        me,
        payerUid: 'u_me',
        assignedFriend: friend,
        friendSharePct: 50,
        previous: prevAllocations,
      })

      const friendAlloc = splitResult.allocations.find((a) => a.uid === 'u_friend')
      expect(friendAlloc?.reimbursed).toBe(true)
    })

    it('deve manter reimbursed: false se a parcela anterior ainda não foi reembolsada', () => {
      const me = { uid: 'u_me', name: 'Eu' }
      const friend = { uid: 'u_friend', name: 'Amigo' }

      const prevAllocations = [
        { type: 'PLATFORM_USER' as const, uid: 'u_me', name: 'Eu', amount: 50, reimbursed: true },
        { type: 'PLATFORM_USER' as const, uid: 'u_friend', name: 'Amigo', amount: 50, reimbursed: false },
      ]

      const splitResult = buildSplit({
        splitType: 'friend',
        total: 100,
        me,
        payerUid: 'u_me',
        assignedFriend: friend,
        friendSharePct: 50,
        previous: prevAllocations,
      })

      const friendAlloc = splitResult.allocations.find((a) => a.uid === 'u_friend')
      expect(friendAlloc?.reimbursed).toBe(false)
    })
  })

  describe('3. Recálculo de Ciclo e Fatura ao Mudar de Cartão', () => {
    const cardNubank: Card = {
      id: 'c_nubank',
      name: 'Nubank',
      ownerUid: 'u_me',
      closingDay: 25,
      dueDay: 5,
      lastFourDigits: '1234',
      brand: 'Mastercard',
      color: '#8A05BE',
      type: 'credit',
      limit: 5000,
    }

    const cardInter: Card = {
      id: 'c_inter',
      name: 'Inter',
      ownerUid: 'u_me',
      closingDay: 30,
      dueDay: 10,
      lastFourDigits: '5678',
      brand: 'Mastercard',
      color: '#FF7A00',
      type: 'credit',
      limit: 5000,
    }

    it('recalcula a competência quando a compra muda para um cartão com dia de fechamento posterior', () => {
      // Compra feita em 2026-01-28:
      // No Nubank (fecha 25, vence 5): 28 >= 25 -> fatura fecha em fevereiro, vence em março (2026-03)
      const datesNubank = calculateCardInvoiceDates('2026-01-28', cardNubank)
      expect(datesNubank.competenceMonth).toBe('2026-03')
      expect(datesNubank.dueDate).toBe('2026-03-05')

      // No Inter (fecha 30, vence 10): 28 < 30 -> fatura fecha em janeiro, vence em fevereiro (2026-02)
      const datesInter = calculateCardInvoiceDates('2026-01-28', cardInter)
      expect(datesInter.competenceMonth).toBe('2026-02')
      expect(datesInter.dueDate).toBe('2026-02-10')
    })
  })
})
