/**
 * Módulo de Segurança e Sanitização de Parâmetros para o Copiloto Gemini (Web).
 * Protege contra Prompt Injections, estouros numéricos (NaN, Infinity), strings abusivas
 * e formatos corrompidos de datas/moedas.
 */

// eslint-disable-next-line no-control-regex
const CONTROL_CHARS_REGEX = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F]/g
const HTML_TAGS_REGEX = /<[^>]*>/g
const PROMPT_INJECTION_PATTERNS = [
  /\bignore\s+(all\s+)?previous\s+instructions\b/i,
  /\bdisregard\s+(all\s+)?earlier\s+instructions\b/i,
  /\bsystem\s+prompt\s*:/i,
  /\byou\s+are\s+now\s+(in\s+)?developer\s+mode\b/i,
  /\bforget\s+all\s+(prior|previous)\s+rules\b/i,
  /\bnew\s+instructions\s*:\s*delete\b/i,
  /\bbypass\s+all\s+safety\s+filters\b/i,
  /\bexfiltrate\s+(data|database|keys)\b/i,
]

export function detectPromptInjection(text: string): boolean {
  if (!text || typeof text !== 'string') return false
  return PROMPT_INJECTION_PATTERNS.some((pattern) => pattern.test(text))
}

export function sanitizeText(
  raw: unknown,
  maxLength = 300,
  fallback = '',
): string {
  if (raw == null) return fallback
  let str = String(raw).trim()

  // 1. Remove caracteres de controle invisíveis
  str = str.replace(CONTROL_CHARS_REGEX, '')

  // 2. Remove tags HTML / scripts
  str = str.replace(HTML_TAGS_REGEX, '')

  // 3. Detecta e neutraliza tentativas de prompt injection em extratos bancários
  if (detectPromptInjection(str)) {
    console.warn('[Security] Tentativa de prompt injection detectada e neutralizada:', str)
    str = str.replace(/ignore\s+previous\s+instructions/gi, '[neutralizado]')
    str = str.replace(/disregard\s+earlier\s+instructions/gi, '[neutralizado]')
    str = str.replace(/system\s+prompt\s*:/gi, '[neutralizado]:')
    str = str.replace(/developer\s+mode/gi, '[neutralizado]')
  }

  // 4. Normaliza espaços múltiplos
  str = str.replace(/\s+/g, ' ').trim()

  // 5. Trunca para limite seguro de caracteres
  if (str.length > maxLength) {
    str = str.slice(0, maxLength).trim()
  }

  return str || fallback
}

export interface AmountSanitizationOptions {
  min?: number
  max?: number
  allowNegative?: boolean
}

export function sanitizeAmount(
  raw: unknown,
  options: AmountSanitizationOptions = {},
): number | null {
  const { min = 0.01, max = 100_000_000.0, allowNegative = false } = options

  if (raw == null) return null

  let num: number
  if (typeof raw === 'number') {
    num = raw
  } else if (typeof raw === 'string') {
    const clean = raw.trim().replace(/\s/g, '').replace(',', '.')
    num = Number(clean)
  } else {
    return null
  }

  // Bloqueia NaN, Infinity e -Infinity
  if (!Number.isFinite(num)) {
    return null
  }

  // Verifica negatividade
  if (!allowNegative && num < 0) {
    return null
  }

  const absNum = Math.abs(num)
  if (absNum < min || absNum > max) {
    return null
  }

  // Arredonda para 2 casas decimais
  return Math.round(num * 100) / 100
}

export function sanitizePercentage(raw: unknown, min = 0, max = 100): number | null {
  if (raw == null) return null
  const num = typeof raw === 'number' ? raw : Number(String(raw).trim().replace(',', '.'))
  if (!Number.isFinite(num)) return null
  if (num < min || num > max) return null
  return Math.round(num * 100) / 100
}

export function sanitizeIsoDate(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  const clean = raw.trim()
  const match = clean.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match) return null

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])

  if (year < 2000 || year > 2099) return null
  if (month < 1 || month > 12) return null
  if (day < 1 || day > 31) return null

  return clean
}

export function sanitizeMonthKey(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  const clean = raw.trim()
  const match = clean.match(/^(\d{4})-(\d{2})$/)
  if (!match) return null

  const year = Number(match[1])
  const month = Number(match[2])

  if (year < 2000 || year > 2099) return null
  if (month < 1 || month > 12) return null

  return clean
}

export function escapeHtmlAndMarkdown(text: string): string {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/`/g, '&#96;')
}
