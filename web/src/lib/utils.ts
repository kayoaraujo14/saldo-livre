import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const brlCompact = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  maximumFractionDigits: 0,
})

export function formatBRL(value: number, compact = false) {
  const num = typeof value === 'number' && Number.isFinite(value) ? value : 0
  const v = Object.is(num, -0) ? 0 : num
  return compact ? brlCompact.format(v) : brl.format(v)
}

export function round2(n: number) {
  return Math.round((n + Number.EPSILON) * 100) / 100
}

export const MONTH_NAMES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
]

export function pad(n: number) {
  return String(n).padStart(2, '0')
}

export function toMonthKey(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}

export function toDateKey(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function todayKey() {
  return toDateKey(new Date())
}

export function currentMonthKey() {
  return toMonthKey(new Date())
}

export function shiftMonth(monthKey?: string | null, delta = 0) {
  const key = typeof monthKey === 'string' && monthKey.includes('-') ? monthKey.trim() : currentMonthKey()
  const [y, m] = key.split('-').map(Number)
  if (isNaN(y) || isNaN(m)) {
    const d = new Date()
    return toMonthKey(new Date(d.getFullYear(), d.getMonth() + delta, 1))
  }
  const d = new Date(y, m - 1 + delta, 1)
  return toMonthKey(d)
}

export function monthLabel(monthKey?: string | null) {
  if (!monthKey || typeof monthKey !== 'string' || !monthKey.includes('-')) {
    return 'Mês'
  }
  const [y, m] = monthKey.trim().split('-').map(Number)
  if (isNaN(y) || isNaN(m) || m < 1 || m > 12) return monthKey
  return `${MONTH_NAMES[m - 1]} ${y}`
}

const SHORT_MONTH_NAMES = [
  'Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun',
  'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez',
]

export function shortMonthLabel(monthKey?: string | null) {
  if (!monthKey || typeof monthKey !== 'string' || !monthKey.includes('-')) {
    return 'Mês'
  }
  const [y, m] = monthKey.trim().split('-').map(Number)
  if (isNaN(y) || isNaN(m) || m < 1 || m > 12) return monthKey
  const shortYear = String(y).slice(-2)
  return `${SHORT_MONTH_NAMES[m - 1]}/${shortYear}`
}

export function daysInMonth(monthKey?: string | null) {
  if (!monthKey || typeof monthKey !== 'string' || !monthKey.includes('-')) {
    return 30
  }
  const [y, m] = monthKey.trim().split('-').map(Number)
  if (isNaN(y) || isNaN(m)) return 30
  return new Date(y, m, 0).getDate()
}

/** Gera YYYY-MM-DD para um dia do mês, limitando ao último dia válido. */
export function dateInMonth(monthKey: string | null | undefined, day: number) {
  const safeMonthKey = typeof monthKey === 'string' && monthKey.includes('-') ? monthKey.trim() : currentMonthKey()
  const last = daysInMonth(safeMonthKey)
  return `${safeMonthKey}-${pad(Math.min(Math.max(1, day), last))}`
}

export function formatShortDate(dateKey?: string | null) {
  if (!dateKey || typeof dateKey !== 'string') return '—'
  const trimmed = dateKey.trim()
  if (trimmed.includes('-')) {
    const parts = trimmed.split('-')
    if (parts.length >= 3) {
      const [, m, d] = parts
      return `${d}/${m}`
    }
    if (parts.length === 2) {
      const [y, m] = parts
      return `${m}/${y}`
    }
  }
  if (trimmed.includes('/')) {
    const parts = trimmed.split('/')
    if (parts.length >= 3) {
      const [d, m] = parts
      return `${d}/${m}`
    }
    return trimmed
  }
  return trimmed || '—'
}

const PT_MONTH_ABBR = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ']
const PT_WEEKDAYS = ['domingo', 'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira', 'sexta-feira', 'sábado']

export interface NubankDateHeaderInfo {
  title: string
  subtitle?: string
  isToday: boolean
  isYesterday: boolean
}

/**
 * Formata uma data para o cabeçalho de agrupamento estilo Nubank (ex: "HOJE", "ONTEM", "25 OUT", "14 SET").
 */
export function formatNubankDateHeader(dateStr?: string | null): NubankDateHeaderInfo {
  if (!dateStr || typeof dateStr !== 'string') return { title: 'Sem data', isToday: false, isYesterday: false }
  const clean = dateStr.trim()
  if (!clean.includes('-')) {
    return { title: clean, isToday: false, isYesterday: false }
  }
  const parts = clean.split('-').map(Number)
  if (parts.length < 3 || isNaN(parts[0]) || isNaN(parts[1]) || isNaN(parts[2])) {
    return { title: clean, isToday: false, isYesterday: false }
  }
  const [y, m, d] = parts
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const target = new Date(y, m - 1, d)
  const diffDays = Math.round((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))

  const isToday = diffDays === 0
  const isYesterday = diffDays === -1
  const monthAbbr = PT_MONTH_ABBR[m - 1] || ''
  const dayStr = pad(d)
  const weekday = PT_WEEKDAYS[target.getDay()] || ''
  const yearSuffix = y !== today.getFullYear() ? ` ${y}` : ''

  if (isToday) {
    return {
      title: 'HOJE',
      subtitle: `${dayStr} ${monthAbbr}`,
      isToday: true,
      isYesterday: false,
    }
  }
  if (isYesterday) {
    return {
      title: 'ONTEM',
      subtitle: `${dayStr} ${monthAbbr}`,
      isToday: false,
      isYesterday: true,
    }
  }
  return {
    title: `${dayStr} ${monthAbbr}${yearSuffix}`,
    subtitle: weekday,
    isToday: false,
    isYesterday: false,
  }
}

/** Desloca uma data YYYY-MM-DD em N meses para a frente mantendo o dia */
export function shiftDateMonth(dateKey: string | null | undefined, deltaMonths: number) {
  const key = typeof dateKey === 'string' && dateKey.includes('-') ? dateKey.trim() : todayKey()
  const parts = key.split('-').map(Number)
  const y = parts[0]
  const m = parts[1]
  const d = parts[2] || 1
  if (isNaN(y) || isNaN(m)) return todayKey()
  const targetMonthKey = shiftMonth(`${y}-${pad(m)}`, deltaMonths)
  return dateInMonth(targetMonthKey, d)
}

export function uid(prefix = 'id') {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}${Date.now().toString(36).slice(-4)}`
}

export function initials(name?: string | null) {
  if (!name || typeof name !== 'string') return '?'
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('')
}

/** Remove chaves `undefined` profundamente (o Firestore rejeita undefined em qualquer nível). */
export function stripUndefined<T>(val: T): T {
  if (val === undefined || val === null) {
    return val
  }
  if (Array.isArray(val)) {
    return val.map((item) => stripUndefined(item)) as unknown as T
  }
  if (typeof val === 'object' && !(val instanceof Date)) {
    const entries = Object.entries(val as Record<string, unknown>)
      .filter(([, v]) => v !== undefined)
      .map(([k, v]) => [k, stripUndefined(v)])
    return Object.fromEntries(entries) as T
  }
  return val
}

export function normalize(s: string) {
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
}

/** Extrai a descrição base sem o sufixo numérico de parcela (ex: "Celular (1/10)" → "Celular") */
export function stripInstallmentSuffix(desc?: string): string {
  if (!desc) return ''
  return desc.replace(/\s*\(\d+\/\d+\)\s*$/, '').trim()
}

/** Formata a descrição da parcela com a numeração correspondente (ex: "Celular (3/10)") */
export function formatInstallmentDesc(baseDesc: string | undefined, num: number, total: number): string {
  const clean = stripInstallmentSuffix(baseDesc)
  return clean ? `${clean} (${num}/${total})` : `(${num}/${total})`
}

export function formatCreatedAt(timestamp?: number): string {
  if (!timestamp) return 'Data não registrada'
  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)

  const timeStr = date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

  if (diffMins < 1) return 'Agora há pouco'
  if (diffMins < 60) return `Há ${diffMins} min (${timeStr})`
  if (diffHours < 24 && date.getDate() === now.getDate() && date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear()) {
    return `Hoje às ${timeStr}`
  }

  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.getDate() === yesterday.getDate() && date.getMonth() === yesterday.getMonth() && date.getFullYear() === yesterday.getFullYear()) {
    return `Ontem às ${timeStr}`
  }

  const day = pad(date.getDate())
  const month = pad(date.getMonth() + 1)
  const year = date.getFullYear()
  return `${day}/${month}/${year} às ${timeStr}`
}

