import type { ErrorInfo } from 'react'

export interface ClientErrorTelemetry {
  message: string
  stack?: string
  componentStack?: string
  timestamp: string
  url: string
  userAgent: string
}

/**
 * Registra erros de renderização da aplicação Web no sessionStorage
 * e emite logs estruturados para monitoramento centralizado de telemetria.
 */
export function logClientError(error: Error, errorInfo?: ErrorInfo): ClientErrorTelemetry {
  const payload: ClientErrorTelemetry = {
    message: error.message || String(error),
    stack: error.stack ? error.stack.slice(0, 2000) : undefined,
    componentStack: errorInfo?.componentStack ? errorInfo.componentStack.slice(0, 2000) : undefined,
    timestamp: new Date().toISOString(),
    url: typeof window !== 'undefined' ? window.location.href : '',
    userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
  }
  try {
    const raw = sessionStorage.getItem('saldo-livre:client-errors')
    const list = raw ? JSON.parse(raw) : []
    list.push(payload)
    sessionStorage.setItem('saldo-livre:client-errors', JSON.stringify(list.slice(-10)))
    console.error('[Telemetry] ErrorBoundary capturou falha na renderização:', payload)
  } catch {
    // Fail-safe defensivo para jamais quebrar o fluxo de captura
  }
  return payload
}
