import { useState, useEffect, useRef } from 'react'

export interface NetworkStatus {
  isOnline: boolean
  justReconnected: boolean
}

/**
 * Hook reativo para monitorar o status de conectividade de rede do usuário.
 * Detecta instantaneamente quando a conexão cai ou retorna, expondo um flag
 * transitório `justReconnected` para feedback visual gracioso.
 */
export function useNetworkStatus(): NetworkStatus {
  const [isOnline, setIsOnline] = useState<boolean>(() => {
    if (typeof navigator !== 'undefined' && typeof navigator.onLine === 'boolean') {
      return navigator.onLine
    }
    return true
  })
  const [justReconnected, setJustReconnected] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const wasOfflineRef = useRef(!isOnline)

  useEffect(() => {
    if (typeof window === 'undefined') return

    const handleOnline = () => {
      setIsOnline(true)
      if (wasOfflineRef.current) {
        setJustReconnected(true)
        if (timerRef.current) clearTimeout(timerRef.current)
        timerRef.current = setTimeout(() => {
          setJustReconnected(false)
        }, 3500)
      }
      wasOfflineRef.current = false
    }

    const handleOffline = () => {
      setIsOnline(false)
      wasOfflineRef.current = true
      setJustReconnected(false)
      if (timerRef.current) clearTimeout(timerRef.current)
    }

    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [])

  return { isOnline, justReconnected }
}
