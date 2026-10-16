import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { useFinance } from '@/store/FinanceContext'
import { monthLabel, shiftMonth, currentMonthKey } from '@/lib/utils'
import { getGeminiApiKey, getSelectedGeminiModel } from '@/lib/gemini'
import {
  ChevronLeft,
  ChevronRight,
  Zap,
  CreditCard,
  RefreshCw,
  KeyRound,
  Sparkles,
  LogOut,
  CalendarRange,
  Users2,
} from 'lucide-react'

interface HeaderProps {
  onOpenCards: () => void
  onOpenRecurring: () => void
  onOpenApiKey: () => void
  onOpenProjection: () => void
  onOpenFriends: () => void
}

export function Header({
  onOpenCards,
  onOpenRecurring,
  onOpenApiKey,
  onOpenProjection,
  onOpenFriends,
}: HeaderProps) {
  const {
    mode,
    users,
    me,
    profile,
    month,
    setMonth,
    generateBills,
    signOut,
    switchProfile,
    pendingFriendRequests,
    pendingChargeGroups,
  } = useFinance()

  const [generating, setGenerating] = useState(false)
  const [generateToast, setGenerateToast] = useState<{ created: number; skipped: number } | null>(null)

  const isCurrentMonth = month === currentMonthKey()
  const hasGeminiKey = Boolean(getGeminiApiKey())
  const activeModel = getSelectedGeminiModel()
  const modelLabel = hasGeminiKey
    ? activeModel.includes('3.5')
      ? 'Gemini 3.5 Lite'
      : activeModel.includes('3.8')
        ? 'Gemini 3.8 Flash'
        : activeModel.includes('2.5')
          ? 'Gemini 2.5 Flash'
          : 'Gemini Conectado'
    : 'Gemini Offline'

  const handlePrevMonth = () => setMonth(shiftMonth(month, -1))
  const handleNextMonth = () => setMonth(shiftMonth(month, 1))
  const handleToday = () => setMonth(currentMonthKey())

  const handleGenerate = async () => {
    setGenerating(true)
    try {
      const res = await generateBills()
      setGenerateToast(res)
      setTimeout(() => setGenerateToast(null), 3500)
    } finally {
      setGenerating(false)
    }
  }

  return (
    <header className="border-b border-white/[0.08] bg-[#090d16]/80 backdrop-blur-xl sticky top-0 z-40">
      <div className="max-w-[1720px] mx-auto px-4 sm:px-6 py-3">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          {/* Logo & Month Navigation */}
          <div className="flex items-center justify-between sm:justify-start gap-4 flex-wrap">
            <div className="flex items-center gap-2.5">
              <div className="size-9 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 shadow-[0_0_20px_-3px_rgba(16,185,129,0.5)] flex items-center justify-center">
                <div className="size-full bg-[#0b101b] rounded-[10px] flex items-center justify-center">
                  <Sparkles className="size-4 text-emerald-400" />
                </div>
              </div>
              <div>
                <h1 className="font-bold text-base tracking-tight text-white m-0">Saldo Livre</h1>
                <p className="text-[11px] text-slate-400 font-normal">Controle Pessoal & Compartilhado</p>
              </div>
            </div>

            {/* Month Navigator */}
            <div className="flex items-center gap-1 bg-slate-900/90 border border-slate-800 p-1 rounded-xl shadow-inner">
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={handlePrevMonth}
                className="size-7 text-slate-400 hover:text-white hover:bg-slate-800"
                title="Mês anterior"
              >
                <ChevronLeft className="size-4" />
              </Button>
              <span className="text-xs font-semibold px-2 min-w-[110px] text-center text-slate-200">
                {monthLabel(month)}
              </span>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={handleNextMonth}
                className="size-7 text-slate-400 hover:text-white hover:bg-slate-800"
                title="Próximo mês"
              >
                <ChevronRight className="size-4" />
              </Button>
              {!isCurrentMonth && (
                <button
                  type="button"
                  onClick={handleToday}
                  className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-400 hover:bg-emerald-500/25 transition-colors cursor-pointer"
                >
                  Hoje
                </button>
              )}
            </div>
          </div>

          {/* Actions & Profile */}
          <div className="flex items-center gap-2 flex-wrap justify-end">
            {/* Generate bills button */}
            <div className="relative">
              <Button
                size="sm"
                onClick={handleGenerate}
                disabled={generating}
                className="bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-xs h-8 gap-1.5 shadow-sm shadow-emerald-950"
              >
                <Zap className={`size-3.5 ${generating ? 'animate-spin' : 'text-amber-300'}`} />
                <span>{generating ? 'Gerando...' : '⚡ Gerar Contas do Mês'}</span>
              </Button>

              {generateToast && (
                <div className="absolute top-full mt-2 right-0 z-50 bg-slate-900 border border-emerald-500/40 text-emerald-300 px-3 py-1.5 rounded-lg text-xs shadow-2xl flex items-center gap-2 whitespace-nowrap animate-in fade-in slide-in-from-top-1">
                  <span>
                    ✓ {generateToast.created} conta(s) gerada(s) · {generateToast.skipped} já existiam
                  </span>
                </div>
              )}
            </div>

            {/* Future Cash Flow Projection Modal */}
            <Button
              variant="outline"
              size="sm"
              onClick={onOpenProjection}
              className="text-xs h-8 gap-1.5 bg-cyan-950/40 border-cyan-800/60 hover:border-cyan-600 text-cyan-300 font-medium"
              title="Ver projeção e fluxo de caixa dos próximos 6 meses"
            >
              <CalendarRange className="size-3.5 text-cyan-400" />
              <span>🔮 Projeção 6 Meses</span>
            </Button>

            {/* Friends / Amigos Modal */}
            <Button
              variant="outline"
              size="sm"
              onClick={onOpenFriends}
              className={`text-xs h-8 gap-1.5 transition-all relative ${
                pendingFriendRequests.length > 0 || pendingChargeGroups.length > 0
                  ? 'bg-amber-500/15 border-amber-500/40 text-amber-300 hover:bg-amber-500/25 font-semibold'
                  : 'bg-slate-900/60 border-slate-800 hover:border-slate-700 text-slate-300'
              }`}
              title="Rede de amigos e aprovações de cobranças no cartão"
            >
              <Users2 className="size-3.5 text-violet-400" />
              <span>Amigos</span>
              {pendingFriendRequests.length + pendingChargeGroups.length > 0 && (
                <span className="size-4 rounded-full bg-amber-500 text-slate-950 font-bold text-[10px] flex items-center justify-center animate-pulse">
                  {pendingFriendRequests.length + pendingChargeGroups.length}
                </span>
              )}
            </Button>

            {/* Quick config modals */}
            <Button
              variant="outline"
              size="sm"
              onClick={onOpenCards}
              className="text-xs h-8 gap-1.5 bg-slate-900/60 border-slate-800 hover:border-slate-700 text-slate-300"
            >
              <CreditCard className="size-3.5 text-violet-400" />
              <span className="hidden sm:inline">Cartões</span>
            </Button>

            <Button
              variant="outline"
              size="sm"
              onClick={onOpenRecurring}
              className="text-xs h-8 gap-1.5 bg-slate-900/60 border-slate-800 hover:border-slate-700 text-slate-300"
            >
              <RefreshCw className="size-3.5 text-amber-400" />
              <span className="hidden sm:inline">Moldes</span>
            </Button>

            <Button
              variant="outline"
              size="sm"
              onClick={onOpenApiKey}
              className="text-xs h-8 gap-1.5 bg-slate-900/60 border-slate-800 hover:border-slate-700 text-slate-300"
              title="Configurar chave da API Gemini"
            >
              <KeyRound className="size-3.5 text-emerald-400" />
              <span className="size-2 rounded-full bg-emerald-400" />
              <span className="hidden md:inline">{modelLabel}</span>
            </Button>

            {/* Current Logged-in User & Logout */}
            <div className="flex items-center gap-2 pl-2 border-l border-white/[0.08]">
              {mode === 'demo' && users.length > 1 && (
                <div className="flex items-center gap-1 bg-violet-950/40 border border-violet-500/30 p-1 rounded-xl">
                  <span className="text-[10px] text-violet-300 font-semibold px-1.5 flex items-center gap-1">
                    <Users2 className="size-3" /> Simular:
                  </span>
                  {users.map((u) => {
                    const active = u.uid === me?.uid
                    return (
                      <button
                        key={u.uid}
                        type="button"
                        onClick={() => switchProfile(u.uid)}
                        className={`px-2 py-0.5 rounded-lg text-xs font-semibold cursor-pointer transition-all ${
                          active
                            ? 'bg-violet-600 text-white shadow-sm'
                            : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                        }`}
                        title={`Alternar visualização para ${u.displayName}`}
                      >
                        {u.displayName.split(' ')[0]}
                      </button>
                    )
                  })}
                </div>
              )}

              <div className="flex items-center gap-2 bg-slate-900/90 border border-slate-800 py-1 px-2.5 rounded-xl">
                <div className="size-6 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center justify-center font-bold text-[10px]">
                  {profile?.displayName?.charAt(0) ?? 'U'}
                </div>
                <div className="flex flex-col text-left">
                  <span className="text-xs font-semibold text-slate-200 leading-tight">
                    {profile?.displayName ?? 'Usuário'}
                  </span>
                  <span className="text-[10px] text-slate-400 leading-tight">
                    {profile?.email || 'Conta Pessoal'}
                  </span>
                </div>
              </div>

              {/* Logout Button */}
              <Button
                variant="ghost"
                size="sm"
                onClick={signOut}
                className="h-8 px-2.5 text-xs text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 gap-1.5 rounded-xl cursor-pointer"
                title="Sair da conta e voltar para a tela de login"
              >
                <LogOut className="size-3.5" />
                <span className="hidden sm:inline">Sair</span>
              </Button>
            </div>
          </div>
        </div>
      </div>
    </header>
  )
}
