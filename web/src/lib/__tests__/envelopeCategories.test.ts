import { describe, it, expect } from 'vitest'
import { normalizeBudgetCategory, NON_ENVELOPE_CATEGORIES, FIXED_MONTHLY_CATEGORIES } from '../finance'
import { EXPENSE_CATEGORIES, INCOME_CATEGORIES, getCategoryDetail } from '@/types'

describe('categorização de envelopes segue o padrão dos lançamentos', () => {
  it('normaliza apelidos e legados para a categoria canônica', () => {
    expect(normalizeBudgetCategory('Lazer')).toBe('Lazer & Entretenimento')
    expect(normalizeBudgetCategory('lazer')).toBe('Lazer & Entretenimento')
    expect(normalizeBudgetCategory('Compras')).toBe('Compras & Estilo de Vida')
    expect(normalizeBudgetCategory('compras')).toBe('Compras & Estilo de Vida')
    expect(normalizeBudgetCategory('Assinaturas')).toBe('Assinaturas & Serviços')
    expect(normalizeBudgetCategory('Família')).toBe('Família & Dependentes')
    expect(normalizeBudgetCategory('Família & Pets')).toBe('Família & Dependentes')
    expect(normalizeBudgetCategory('Pets')).toBe('Pets')
    expect(normalizeBudgetCategory('pets')).toBe('Pets')
    expect(normalizeBudgetCategory('Financiamentos & Empréstimos')).toBe('Financiamentos & Dívidas')
    expect(normalizeBudgetCategory('financiamentos e empréstimos')).toBe('Financiamentos & Dívidas')
    expect(normalizeBudgetCategory('educacao')).toBe('Educação & Desenvolvimento')
    expect(normalizeBudgetCategory('Educação')).toBe('Educação & Desenvolvimento')
    expect(normalizeBudgetCategory('Alimentação')).toBe('Alimentação')
  })
  it('categorias de receita e legados de crédito nunca viram envelope', () => {
    for (const c of INCOME_CATEGORIES) expect(NON_ENVELOPE_CATEGORIES.has(c)).toBe(true)
    expect(normalizeBudgetCategory('Estorno')).toBe('Estorno')
    expect(NON_ENVELOPE_CATEGORIES.has('Estorno')).toBe(true)
  })
  it('categorias fixas são todas canônicas de despesa', () => {
    for (const c of FIXED_MONTHLY_CATEGORIES) expect((EXPENSE_CATEGORIES as readonly string[]).includes(c)).toBe(true)
  })
  it('getCategoryDetail resolve aliases', () => {
    expect(getCategoryDetail('Trabalho')).toBe(getCategoryDetail('Trabalho / PJ / Carreira'))
  })
})
