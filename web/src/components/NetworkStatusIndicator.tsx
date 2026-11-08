import { WifiOff, Wifi } from 'lucide-react'
import { useNetworkStatus } from '@/hooks/useNetworkStatus'

interface NetworkStatusIndicatorProps {
  className?: string
}

/**
 * Indicador visual e reativo do status de conectividade da aplicação Web.
 * Exibe um banner sutil quando a conexão cai ("Modo Offline — Alterações salvas localmente")
 * e um feedback positivo temporário quando a conexão é restabelecida.
 */
export function NetworkStatusIndicator({ className = '' }: NetworkStatusIndicatorProps) {
  const { isOnline, justReconnected } = useNetworkStatus()

  if (isOnline && !justReconnected) {
    return null
  }

  if (justReconnected) {
    return (
      <aside
        aria-label="Status de Conexão"
        aria-live="polite"
        className={`flex items-center justify-center gap-2 px-3 py-1.5 text-xs font-medium rounded-full bg-emerald-950/90 text-emerald-300 border border-emerald-500/30 shadow-md backdrop-blur-sm transition-all duration-300 animate-in fade-in slide-in-from-top-2 ${className}`}
      >
        <Wifi className="size-3.5 text-emerald-400 shrink-0" />
        <span>Conexão restabelecida</span>
      </aside>
    )
  }

  return (
    <aside
      aria-label="Status de Conexão"
      aria-live="polite"
      className={`flex items-center justify-center gap-2 px-3.5 py-1.5 text-xs font-medium rounded-full bg-amber-950/90 text-amber-200 border border-amber-500/40 shadow-md backdrop-blur-sm transition-all duration-300 animate-in fade-in slide-in-from-top-2 ${className}`}
    >
      <span className="size-2 rounded-full bg-amber-400 animate-pulse shrink-0" />
      <WifiOff className="size-3.5 text-amber-400 shrink-0" />
      <span>Modo Offline — Alterações salvas localmente</span>
    </aside>
  )
}
