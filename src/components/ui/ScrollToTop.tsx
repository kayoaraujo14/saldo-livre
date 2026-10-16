import { useState, useEffect } from 'react'
import { ArrowUp } from 'lucide-react'

export function ScrollToTop() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      setVisible(window.scrollY > 200)
    }

    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  if (!visible) return null

  return (
    <button
      type="button"
      onClick={scrollToTop}
      aria-label="Voltar ao topo da página"
      className="lg:hidden fixed bottom-20 sm:bottom-6 right-4 sm:right-6 z-30 p-2.5 sm:px-3.5 sm:py-2 rounded-full sm:rounded-xl bg-slate-900/90 hover:bg-emerald-600/90 text-slate-200 hover:text-white border border-slate-700/80 hover:border-emerald-500/50 shadow-2xl backdrop-blur-md transition-all duration-300 flex items-center gap-1.5 cursor-pointer hover:scale-105 active:scale-95 group animate-in fade-in slide-in-from-bottom-2"
    >
      <ArrowUp className="size-4 group-hover:-translate-y-0.5 transition-transform" />
      <span className="text-xs font-semibold hidden sm:inline">Topo</span>
    </button>
  )
}
