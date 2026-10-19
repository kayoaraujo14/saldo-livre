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
  const v = Object.is(value, -0) ? 0 : value
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

export function shiftMonth(monthKey: string, delta: number) {
  const [y, m] = monthKey.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  return toMonthKey(d)
}

export function monthLabel(monthKey: string) {
  const [y, m] = monthKey.split('-').map(Number)
  return `${MONTH_NAMES[m - 1]} ${y}`
}

export function daysInMonth(monthKey: string) {
  const [y, m] = monthKey.split('-').map(Number)
  return new Date(y, m, 0).getDate()
}

/** Gera YYYY-MM-DD para um dia do mês, limitando ao último dia válido. */
export function dateInMonth(monthKey: string, day: number) {
  const last = daysInMonth(monthKey)
  return `${monthKey}-${pad(Math.min(Math.max(1, day), last))}`
}

export function formatShortDate(dateKey: string) {
  const [, m, d] = dateKey.split('-')
  return `${d}/${m}`
}

/** Desloca uma data YYYY-MM-DD em N meses para a frente mantendo o dia */
export function shiftDateMonth(dateKey: string, deltaMonths: number) {
  const [y, m, d] = dateKey.split('-').map(Number)
  const targetMonthKey = shiftMonth(`${y}-${pad(m)}`, deltaMonths)
  return dateInMonth(targetMonthKey, d)
}

export function uid(prefix = 'id') {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}${Date.now().toString(36).slice(-4)}`
}

export function initials(name: string) {
  return name
    .split(' ')
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

