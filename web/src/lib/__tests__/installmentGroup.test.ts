import { describe, it, expect } from 'vitest'
import { stripInstallmentSuffix, formatInstallmentDesc } from '../utils'
import {
  buildSplit,
  calculateCardInvoiceDates,
  calculateInstallmentAmount,
  calculateInstallmentCompetenceMonths,
  fractionCustomAllocations,
} from '../finance'
import { Allocation, Card } from '@/types'

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

describe('Issue #77: Motor de Parcelamento - Não-Colapso na Virada de Mês, Fracionamento de Rateios e Resíduo de Centavos', () => {
  const cardNubank: Card = {
    id: 'c_nubank',
    name: 'Nubank',
    ownerUid: 'u_me',
    closingDay: 30,
    dueDay: 10,
    lastFourDigits: '1234',
    brand: 'Mastercard',
    color: '#8A05BE',
    type: 'credit',
    limit: 5000,
  }

  describe('1. Avanço Estrito de Competência (Não-Colapso na Virada de Mês)', () => {
    it('não deve colapsar parcelas subsequentes de compra no fim de janeiro com mês de fevereiro mais curto', () => {
      // Compra feita em 2026-01-31 com cartão fechando dia 30 e vencendo dia 10:
      // Parcela 1: 31/01 >= 30 -> fecha em fevereiro, vence em março (2026-03)
      // Parcela 2: avança determinísticamente para 2026-04 (não colapsa em 2026-03 mesmo com 28 dias em fev)
      // Parcela 3: 2026-05
      const months = calculateInstallmentCompetenceMonths('2026-01-31', cardNubank, 3, true)
      expect(months).toEqual(['2026-03', '2026-04', '2026-05'])
    })

    it('deve avançar corretamente para compras sem cartão (ex: boleto ou pix)', () => {
      // Compra em 2026-01-31 sem cartão:
      // Parcela 1: 2026-01
      // Parcela 2: 2026-02 (28 de fevereiro)
      // Parcela 3: 2026-03 (31 de março)
      const months = calculateInstallmentCompetenceMonths('2026-01-31', null, 3, false)
      expect(months).toEqual(['2026-01', '2026-02', '2026-03'])
    })

    it('retorna lista vazia ou única para 0 ou 1 parcela', () => {
      expect(calculateInstallmentCompetenceMonths('2026-01-31', cardNubank, 0, true)).toEqual([])
      expect(calculateInstallmentCompetenceMonths('2026-01-31', cardNubank, 1, true)).toEqual(['2026-03'])
      expect(calculateInstallmentCompetenceMonths('2026-01-31', null, 1, false)).toEqual(['2026-01'])
    })
  })

  describe('2. Fracionamento de Cotas de Rateio Customizado com Amigos', () => {
    it('deve fracionar cotas de rateio pelo número de parcelas sem multiplicar débitos', () => {
      const origAllocs: Allocation[] = [
        { type: 'PLATFORM_USER', uid: 'u_me', name: 'Eu', amount: 150, reimbursed: true },
        { type: 'PLATFORM_USER', uid: 'u_friend', name: 'Amigo', amount: 150, reimbursed: false },
      ]

      const p1 = fractionCustomAllocations(origAllocs, 1, 3, false)
      const p2 = fractionCustomAllocations(origAllocs, 2, 3, false)
      const p3 = fractionCustomAllocations(origAllocs, 3, 3, false)

      expect(p1?.find((a) => a.uid === 'u_friend')?.amount).toBe(50)
      expect(p2?.find((a) => a.uid === 'u_friend')?.amount).toBe(50)
      expect(p3?.find((a) => a.uid === 'u_friend')?.amount).toBe(50)
    })

    it('deve absorver resíduos de centavos em rateios com divisões ímpares na última parcela', () => {
      const origAllocs: Allocation[] = [
        { type: 'PLATFORM_USER', uid: 'u_friend', name: 'Amigo', amount: 100, reimbursed: false },
      ]

      const p1 = fractionCustomAllocations(origAllocs, 1, 3, false)
      const p2 = fractionCustomAllocations(origAllocs, 2, 3, false)
      const p3 = fractionCustomAllocations(origAllocs, 3, 3, false)

      const a1 = p1![0].amount
      const a2 = p2![0].amount
      const a3 = p3![0].amount

      expect(a1).toBe(33.33)
      expect(a2).toBe(33.33)
      expect(a3).toBe(33.34)
      expect(Number((a1 + a2 + a3).toFixed(2))).toBe(100.0)
    })

    it('não deve alterar alocações no modo per_installment', () => {
      const origAllocs: Allocation[] = [
        { type: 'PLATFORM_USER', uid: 'u_friend', name: 'Amigo', amount: 50, reimbursed: false },
      ]

      const p1 = fractionCustomAllocations(origAllocs, 1, 3, true)
      expect(p1![0].amount).toBe(50)
    })
  })

  describe('3. Arredondamento Residual de Centavos no Valor da Parcela', () => {
    it('deve absorver 1 centavo na última parcela para R$ 100 em 3x', () => {
      const p1 = calculateInstallmentAmount(100, 1, 3, false)
      const p2 = calculateInstallmentAmount(100, 2, 3, false)
      const p3 = calculateInstallmentAmount(100, 3, 3, false)

      expect(p1).toBe(33.33)
      expect(p2).toBe(33.33)
      expect(p3).toBe(33.34)
      expect(Number((p1 + p2 + p3).toFixed(2))).toBe(100.0)
    })

    it('deve absorver resíduo para R$ 100 em 6x', () => {
      const parts = [1, 2, 3, 4, 5, 6].map((i) => calculateInstallmentAmount(100, i, 6, false))
      expect(parts.slice(0, 5)).toEqual([16.67, 16.67, 16.67, 16.67, 16.67])
      expect(parts[5]).toBe(16.65)
      const total = parts.reduce((acc, v) => acc + v, 0)
      expect(Number(total.toFixed(2))).toBe(100.0)
    })

    it('suporta valores negativos com resíduo para estornos em 3x', () => {
      const p1 = calculateInstallmentAmount(-100, 1, 3, false)
      const p2 = calculateInstallmentAmount(-100, 2, 3, false)
      const p3 = calculateInstallmentAmount(-100, 3, 3, false)

      expect(p1).toBe(-33.33)
      expect(p2).toBe(-33.33)
      expect(p3).toBe(-33.34)
      expect(Number((p1 + p2 + p3).toFixed(2))).toBe(-100.0)
    })
  })
})
