import { Home, ReceiptText, Sparkles, Menu } from 'lucide-react'

export type MobileTab = 'home' | 'transactions' | 'chat' | 'menu'

interface MobileBottomNavProps {
  currentTab: MobileTab
  onSelectTab: (tab: MobileTab) => void
  unreadAlertsCount?: number
  txCount?: number
}

export function MobileBottomNav({
  currentTab,
  onSelectTab,
  unreadAlertsCount = 0,
  txCount = 0,
}: MobileBottomNavProps) {
  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 lg:hidden bg-[#090d16]/95 backdrop-blur-2xl border-t border-white/[0.08] px-3 pt-1.5 pb-[max(0.6rem,env(safe-area-inset-bottom,0px))] shadow-[0_-10px_30px_rgba(0,0,0,0.7)] select-none">
      <div className="max-w-md mx-auto grid grid-cols-4 gap-1">
        {/* 1. Início (Dashboard / Resumo) */}
        <button
          type="button"
          onClick={() => onSelectTab('home')}
          className={`flex flex-col items-center justify-center py-1 px-2 rounded-2xl transition-all cursor-pointer relative active:scale-95 ${
            currentTab === 'home'
              ? 'text-emerald-400 font-bold bg-emerald-500/10'
              : 'text-slate-400 hover:text-slate-200 font-medium'
          }`}
        >
          <Home className={`size-5 transition-transform ${currentTab === 'home' ? 'scale-110' : ''}`} />
          <span className="text-[10px] mt-0.5 tracking-tight">Início</span>
          {unreadAlertsCount > 0 && (
            <span className="absolute top-1 right-3 size-2 rounded-full bg-amber-400 animate-ping" />
          )}
        </button>

        {/* 2. Extrato (Lançamentos) */}
        <button
          type="button"
          onClick={() => onSelectTab('transactions')}
          className={`flex flex-col items-center justify-center py-1 px-2 rounded-2xl transition-all cursor-pointer relative active:scale-95 ${
            currentTab === 'transactions'
              ? 'text-emerald-400 font-bold bg-emerald-500/10'
              : 'text-slate-400 hover:text-slate-200 font-medium'
          }`}
        >
          <ReceiptText className={`size-5 transition-transform ${currentTab === 'transactions' ? 'scale-110' : ''}`} />
          <span className="text-[10px] mt-0.5 tracking-tight">Extrato</span>
          {txCount > 0 && (
            <span className="absolute top-0.5 right-2 px-1 py-0.2 bg-slate-800 border border-slate-700 text-slate-300 rounded-full text-[9px] font-mono">
              {txCount > 99 ? '99+' : txCount}
            </span>
          )}
        </button>

        {/* 3. Copiloto IA */}
        <button
          type="button"
          onClick={() => onSelectTab('chat')}
          className={`flex flex-col items-center justify-center py-1 px-2 rounded-2xl transition-all cursor-pointer relative active:scale-95 ${
            currentTab === 'chat'
              ? 'text-emerald-400 font-bold bg-emerald-500/10'
              : 'text-slate-400 hover:text-slate-200 font-medium'
          }`}
        >
          <Sparkles className={`size-5 transition-transform ${currentTab === 'chat' ? 'scale-110 text-emerald-400' : ''}`} />
          <span className="text-[10px] mt-0.5 tracking-tight">Copiloto</span>
        </button>

        {/* 4. Menu / Ferramentas */}
        <button
          type="button"
          onClick={() => onSelectTab('menu')}
          className={`flex flex-col items-center justify-center py-1 px-2 rounded-2xl transition-all cursor-pointer relative active:scale-95 ${
            currentTab === 'menu'
              ? 'text-emerald-400 font-bold bg-emerald-500/10'
              : 'text-slate-400 hover:text-slate-200 font-medium'
          }`}
        >
          <Menu className={`size-5 transition-transform ${currentTab === 'menu' ? 'scale-110' : ''}`} />
          <span className="text-[10px] mt-0.5 tracking-tight">Menu</span>
          {unreadAlertsCount > 0 && (
            <span className="absolute top-1 right-3 size-2 rounded-full bg-amber-400" />
          )}
        </button>
      </div>
    </nav>
  )
}
