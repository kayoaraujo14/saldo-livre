import type { SplitType } from '@/types'
import { formatBRL, normalize, stripInstallmentSuffix, todayKey } from '../utils'

export const SPLIT_MAP: Record<string, SplitType> = {
  MEU: 'mine',
  CASAL_50_50: 'split_50',
  PARCEIRO: 'partner',
  AMIGO: 'friend',
  TERCEIRO: 'external',
  PERSONALIZADA: 'custom',
  CUSTOM: 'custom',
}

export const SYNONYMS: Record<string, string[]> = {
  luz: ['luz', 'energia', 'enel', 'cemig', 'light', 'copel'],
  agua: ['agua', 'sabesp', 'saneamento'],
  financiamento: ['financiamento', 'carro', 'parcela do carro'],
  internet: ['internet', 'fibra', 'wifi'],
  aluguel: ['aluguel'],
}

export const INCOME_KEYWORDS_REGEX =
  /(salario|salário|folha|remunera|pro-?labore|recebido|recebimento|pix recebido|ted recebida|doc recebido|transf(erência|erencia|\.)? recebida|ted de |pix de |deposito|depósito|rendimento|rend\s*aplic|juros|provento|dividendo|jcp|restitui|cashback|credito|crédito|cred\s+em\s+conta|cred\s+tef|cred\s+pix|entrada|ted\s+remet|dep\s+dinheiro|\+)/i

export function matchesSearch(description: string, search: string): boolean {
  const d = normalize(description)
  const s = normalize(search)
  if (d.includes(s)) return true
  for (const [key, list] of Object.entries(SYNONYMS)) {
    if (list.some((w) => s.includes(w)) && (d.includes(key) || list.some((w) => d.includes(w)))) return true
  }
  return s.split(/\s+/).filter((w) => w.length > 2).some((w) => d.includes(w))
}

export function money(n: number): string {
  return formatBRL(n)
}

export function cleanBaseDescription(desc: string): string {
  return stripInstallmentSuffix(desc.replace(/\s*\(?\b\d+\/\d+\b\)?\s*$/i, '')).trim()
}

export function parseMonthKey(raw: unknown, referenceMonth = todayKey()): string {
  if (typeof raw !== 'string' || !raw.trim()) return ''
  const trimmed = raw.trim().toLowerCase()
  if (/^\d{4}-\d{2}$/.test(trimmed)) return trimmed
  if (trimmed === 'all' || trimmed === 'todos' || trimmed === 'ano' || trimmed === 'tudo') return ''

  const yearMatch = trimmed.match(/\b(20\d{2})\b/)
  const year = yearMatch ? yearMatch[1] : referenceMonth.slice(0, 4)

  const monthMap: Record<string, string> = {
    janeiro: '01', jan: '01',
    fevereiro: '02', fev: '02',
    marco: '03', março: '03', mar: '03',
    abril: '04', abr: '04',
    maio: '05', mai: '05',
    junho: '06', jun: '06',
    julho: '07', jul: '07',
    agosto: '08', ago: '08',
    setembro: '09', set: '09',
    outubro: '10', out: '10',
    novembro: '11', nov: '11',
    dezembro: '12', dez: '12',
  }

  for (const [name, mm] of Object.entries(monthMap)) {
    if (trimmed.includes(name)) {
      return `${year}-${mm}`
    }
  }

  const numMatch = trimmed.match(/^([1-9]|1[0-2])$/)
  if (numMatch) {
    return `${year}-${numMatch[1].padStart(2, '0')}`
  }

  return ''
}

export {
  detectPromptInjection,
  escapeHtmlAndMarkdown,
  sanitizeAmount,
  sanitizeIsoDate,
  sanitizeMonthKey,
  sanitizePercentage,
  sanitizeText,
} from './security'

