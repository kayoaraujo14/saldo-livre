import { useState, useEffect } from 'react'

const MOBILE_BREAKPOINT = 640

/**
 * Hook reativo para detecção de viewport mobile (< 640px).
 * Garante consistência com o breakpoint 'sm' do Tailwind CSS (640px),
 * adaptando-se instantaneamente a redimensionamentos e rotações de tela.
 */
export function useIsMobile(breakpoint = MOBILE_BREAKPOINT): boolean {
  const [isMobile, setIsMobile] = useState<boolean>(() => {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      return window.matchMedia(`(max-width: ${breakpoint - 1}px)`).matches
    }
    return false
  })

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return
    }

    const mql = window.matchMedia(`(max-width: ${breakpoint - 1}px)`)
    const onChange = (e: MediaQueryListEvent) => {
      setIsMobile(e.matches)
    }

    setIsMobile(mql.matches)

    if (typeof mql.addEventListener === 'function') {
      mql.addEventListener('change', onChange)
      return () => mql.removeEventListener('change', onChange)
    } else {
      // Fallback para navegadores com API legada
      mql.addListener(onChange)
      return () => mql.removeListener(onChange)
    }
  }, [breakpoint])

  return isMobile
}
