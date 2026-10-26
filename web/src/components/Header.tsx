import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/overlays'
import { useFinance } from '@/store/FinanceContext'
import { monthLabel, shortMonthLabel, shiftMonth, currentMonthKey } from '@/lib/utils'
import { getGeminiApiKey, getSelectedGeminiModel } from '@/lib/gemini'
import {
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Plus,
  CreditCard,
  RefreshCw,
  KeyRound,
  Sparkles,
  LogOut,
  CalendarRange,
  Users2,
  SlidersHorizontal,
  History,
  TrendingUp,
  Receipt,
} from 'lucide-react'

interface HeaderProps {
  onOpenNewTx: () => void
  onOpenCards: () => void
  onOpenRecurring: () => void
  onOpenApiKey: () => void
  onOpenProjection: () => void
  onOpenFriends: () => void
  onOpenRecentActivity: () => void
  onOpenInvestments?: () => void
  onOpenInvoices?: () => void
}

export function Header({
  onOpenNewTx,
  onOpenCards,
  onOpenRecurring,
  onOpenApiKey,
  onOpenProjection,
  onOpenFriends,
  onOpenRecentActivity,
  onOpenInvestments,
  onOpenInvoices,
}: HeaderProps) {
  const {
    mode,
    users,
    me,
    profile,
    month,
    setMonth,
    signOut,
    exitDemo,
    switchProfile,
    pendingFriendRequests,
    pendingChargeGroups,
  } = useFinance()

  const isCurrentMonth = month === currentMonthKey()
  const hasGeminiKey = Boolean(getGeminiApiKey())
  const activeModel = getSelectedGeminiModel()
  const modelLabel = hasGeminiKey
    ? activeModel.includes('3.5') && activeModel.includes('lite')
      ? 'Gemini 3.5 Lite'
      : activeModel.includes('3.5')
        ? 'Gemini 3.5 Flash'
        : activeModel.includes('flash')
          ? 'Gemini Flash'
          : activeModel.includes('pro')
            ? 'Gemini Pro'
            : 'Gemini Conectado'
    : 'Configurar Gemini'

  const totalAlerts = pendingFriendRequests.length + pendingChargeGroups.length

  const handlePrevMonth = () => setMonth(shiftMonth(month, -1))
  const handleNextMonth = () => setMonth(shiftMonth(month, 1))
  const handleToday = () => setMonth(currentMonthKey())

  return (
    <header className="border-b border-white/5 bg-slate-950/90 backdrop-blur-md sticky top-0 z-40 transition-all pt-[max(0.4rem,env(safe-area-inset-top,0px))]">
      <div className="max-w-[1720px] mx-auto px-3 sm:px-6 py-2 sm:py-2.5">
        <div className="flex items-center justify-between gap-2 sm:gap-3">
          {/* 1. Left: Brand & Month Navigator */}
          <div className="flex items-center gap-2 sm:gap-5 min-w-0 shrink">
            {/* Logo */}
            <div className="flex items-center gap-2 shrink-0">
              <div className="size-8 sm:size-9 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 shadow-[0_0_20px_-3px_rgba(16,185,129,0.5)] flex items-center justify-center shrink-0">
                <div className="size-full bg-[#0b101b] rounded-[10px] flex items-center justify-center">
                  <Sparkles className="size-3.5 sm:size-4 text-emerald-400" />
                </div>
              </div>
              <div className="hidden sm:block">
                <h1 className="font-bold text-sm sm:text-base tracking-tight text-white m-0 leading-tight">
                  Saldo Livre
                </h1>
                <p className="text-[10px] sm:text-[11px] text-slate-400 font-normal leading-tight hidden md:block">
                  Controle Pessoal & Compartilhado
                </p>
              </div>
            </div>

            {/* Month Navigator (Pill) */}
            <div className="flex items-center gap-0.5 sm:gap-1 bg-slate-900/60 border border-white/5 p-0.5 sm:p-1 rounded-xl shadow-inner shrink-0 transition-colors">
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={handlePrevMonth}
                className="size-6 sm:size-7 text-slate-400 hover:text-white hover:bg-slate-800/60 cursor-pointer p-0 transition-colors"
                title="Mês anterior"
              >
                <ChevronLeft className="size-3.5 sm:size-4" />
              </Button>
              <span className="text-xs font-semibold px-1 sm:px-2 min-w-[54px] sm:min-w-[110px] text-center text-slate-200">
                <span className="sm:hidden">{shortMonthLabel(month)}</span>
                <span className="hidden sm:inline">{monthLabel(month)}</span>
              </span>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={handleNextMonth}
                className="size-6 sm:size-7 text-slate-400 hover:text-white hover:bg-slate-800/60 cursor-pointer p-0 transition-colors"
                title="Próximo mês"
              >
                <ChevronRight className="size-3.5 sm:size-4" />
              </Button>
              {!isCurrentMonth && (
                <button
                  type="button"
                  onClick={handleToday}
                  className="text-[9px] sm:text-[10px] px-1 sm:px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-400 hover:bg-emerald-500/25 transition-colors cursor-pointer font-medium"
                >
                  Hoje
                </button>
              )}
            </div>
          </div>

          {/* 2. Right: Action Buttons & Menus */}
          <div className="flex items-center gap-1.5 sm:gap-2 shrink-0 ml-auto">
            {/* Botão de Adicionar Lançamento (Exclusivo no Cabeçalho - Entrada ou Despesa) */}
            <Button
              size="sm"
              onClick={onOpenNewTx}
              className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs h-7 sm:h-8 px-2.5 sm:px-3.5 gap-1.5 shadow-md shadow-emerald-950/60 cursor-pointer flex items-center rounded-xl transition-all active:scale-95 shrink-0"
              title="Adicionar lançamento (entrada ou despesa)"
            >
              <Plus className="size-3.5 stroke-[2.5]" />
              <span className="hidden sm:inline">Novo Lançamento</span>
              <span className="sm:hidden">Novo</span>
            </Button>

            {/* Friends Button (Desktop: visible with badge; Mobile: accessible via Menu & top alert banner) */}
            <Button
              variant="outline"
              size="sm"
              onClick={onOpenFriends}
              className={`text-xs h-8 gap-1.5 transition-all relative cursor-pointer rounded-xl hidden lg:flex ${
                totalAlerts > 0
                  ? 'bg-amber-500/15 border-amber-500/40 text-amber-300 hover:bg-amber-500/25 font-semibold'
                  : 'bg-slate-900/60 border-white/5 hover:border-white/10 hover:bg-slate-900 text-slate-300'
              }`}
              title="Rede de amigos, parceiro e aprovações no cartão"
            >
              <Users2 className="size-3.5 text-violet-400" />
              <span>Amigos</span>
              {totalAlerts > 0 && (
                <span className="size-4 rounded-full bg-amber-500 text-slate-950 font-bold text-[10px] flex items-center justify-center animate-pulse">
                  {totalAlerts}
                </span>
              )}
            </Button>

            {/* Invoices Quick Button (Desktop LG+) */}
            {onOpenInvoices && (
              <Button
                variant="outline"
                size="sm"
                onClick={onOpenInvoices}
                className="text-xs h-8 gap-1.5 bg-slate-900/60 border-white/5 hover:border-white/10 hover:bg-slate-900 text-slate-300 hover:text-cyan-300 cursor-pointer rounded-xl hidden lg:flex transition-all"
                title="Acompanhamento de faturas por cartão"
              >
                <Receipt className="size-3.5 text-cyan-400" />
                <span>Faturas</span>
              </Button>
            )}

            {/* Investments Quick Button (Desktop XL) */}
            {onOpenInvestments && (
              <Button
                variant="outline"
                size="sm"
                onClick={onOpenInvestments}
                className="text-xs h-8 gap-1.5 bg-slate-900/60 border-white/5 hover:border-white/10 hover:bg-slate-900 text-slate-300 hover:text-purple-300 cursor-pointer rounded-xl hidden xl:flex transition-all"
                title="Controle de Investimentos e Patrimônio"
              >
                <TrendingUp className="size-3.5 text-purple-400" />
                <span>Investimentos</span>
              </Button>
            )}

            {/* Consolidated "Recursos" Dropdown (Desktop: handy dropdown; Mobile: accessible via bottom Menu dock) */}
            <div className="hidden lg:block">
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    variant="outline"
                    size="sm"
                    className="text-xs h-8 gap-1.5 bg-slate-900/60 border-white/5 hover:border-white/10 hover:bg-slate-900 text-slate-300 cursor-pointer rounded-xl transition-all"
                    title="Acessar ferramentas, projeções e configurações de cartões"
                  >
                    <SlidersHorizontal className="size-3.5 text-cyan-400" />
                    <span>Recursos</span>
                    <ChevronDown className="size-3 text-slate-400" />
                  </Button>
                </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56 bg-slate-950 border border-slate-800 shadow-2xl p-1.5">
                <DropdownMenuLabel className="text-[11px] font-semibold text-slate-400 px-2 py-1">
                  Ferramentas Financeiras
                </DropdownMenuLabel>
                
                <DropdownMenuItem
                  onClick={onOpenProjection}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <CalendarRange className="size-3.5 text-cyan-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Projeção de Fluxo (6m)</span>
                    <span className="text-[10px] text-slate-500">Previsão dos próximos meses</span>
                  </div>
                </DropdownMenuItem>

                <DropdownMenuItem
                  onClick={onOpenRecentActivity}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <History className="size-3.5 text-sky-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Histórico Recente</span>
                    <span className="text-[10px] text-slate-500">Últimas cadastradas ou recebidas</span>
                  </div>
                </DropdownMenuItem>

                {onOpenInvestments && (
                  <DropdownMenuItem
                    onClick={onOpenInvestments}
                    className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                  >
                    <TrendingUp className="size-3.5 text-purple-400" />
                    <div className="flex flex-col">
                      <span className="font-medium">Investimentos & Patrimônio</span>
                      <span className="text-[10px] text-slate-500">Aportes, classes de ativos e simulador</span>
                    </div>
                  </DropdownMenuItem>
                )}

                <DropdownMenuSeparator className="bg-slate-800 my-1" />
                
                <DropdownMenuLabel className="text-[11px] font-semibold text-slate-400 px-2 py-1">
                  Cadastros & Automações
                </DropdownMenuLabel>

                <DropdownMenuItem
                  onClick={onOpenCards}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <CreditCard className="size-3.5 text-violet-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Cartões de Crédito</span>
                    <span className="text-[10px] text-slate-500">Limites, fechamento e cartões</span>
                  </div>
                </DropdownMenuItem>

                <DropdownMenuItem
                  onClick={onOpenRecurring}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <RefreshCw className="size-3.5 text-amber-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Contas Fixas & Renda</span>
                    <span className="text-[10px] text-slate-500">Assinaturas e renda fixa</span>
                  </div>
                </DropdownMenuItem>

                <DropdownMenuItem
                  onClick={onOpenApiKey}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <KeyRound className="size-3.5 text-emerald-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Chave Gemini IA</span>
                    <span className="text-[10px] text-emerald-400 font-mono">{modelLabel}</span>
                  </div>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            </div>

            {mode === 'demo' && (
              <button
                type="button"
                onClick={exitDemo}
                className="hidden lg:flex items-center gap-1.5 px-2.5 py-1 rounded-xl bg-amber-500/15 border border-amber-500/30 text-amber-300 text-xs font-semibold hover:bg-amber-500/25 transition-all cursor-pointer"
                title="Modo demonstração com dados simulados. Clique para conectar sua conta Google real"
              >
                <span className="size-2 rounded-full bg-amber-400 animate-pulse" />
                <span>Modo Demo</span>
                <span className="text-[10px] text-amber-200/80 font-normal">· Conectar Google</span>
              </button>
            )}

            {/* User Profile Dropdown Menu */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className="flex items-center gap-1 sm:gap-2 bg-slate-900/60 border border-white/5 h-7 sm:h-8 px-1.5 sm:px-2 rounded-xl hover:border-white/10 hover:bg-slate-900 transition-all cursor-pointer outline-none shrink-0"
                  title="Abrir opções de perfil e usuário"
                >
                  <div className="size-5 sm:size-6 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center justify-center font-bold text-[9px] sm:text-[10px] shrink-0">
                    {profile?.displayName?.charAt(0) ?? 'U'}
                  </div>
                  <span className="text-xs font-semibold text-slate-200 hidden md:inline truncate max-w-[100px]">
                    {profile?.displayName?.split(' ')[0] ?? 'Usuário'}
                  </span>
                  <ChevronDown className="size-3 text-slate-400 hidden sm:inline shrink-0" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-60 bg-slate-950 border border-slate-800 shadow-2xl p-1.5">
                <div className="px-2.5 py-2">
                  <p className="text-xs font-semibold text-white truncate">{profile?.displayName ?? 'Usuário'}</p>
                  <p className="text-[11px] text-slate-400 truncate">{profile?.email || 'Conta Pessoal'}</p>
                  {mode === 'demo' ? (
                    <div className="mt-1.5 flex items-center gap-1 text-[10px] text-amber-400 bg-amber-950/40 border border-amber-500/30 rounded px-1.5 py-0.5">
                      <span className="size-1.5 rounded-full bg-amber-400" />
                      <span>Modo Demonstração Ativo</span>
                    </div>
                  ) : (
                    <div className="mt-1.5 flex items-center gap-1 text-[10px] text-emerald-400 bg-emerald-950/40 border border-emerald-500/30 rounded px-1.5 py-0.5">
                      <span className="size-1.5 rounded-full bg-emerald-400" />
                      <span>Nuvem Firebase Conectada</span>
                    </div>
                  )}
                </div>

                {mode === 'demo' && (
                  <>
                    <DropdownMenuSeparator className="bg-slate-800 my-1" />
                    <DropdownMenuItem
                      onClick={exitDemo}
                      className="flex items-center gap-2 px-2.5 py-2 text-xs text-emerald-400 hover:text-emerald-300 hover:bg-emerald-500/10 rounded-lg cursor-pointer font-semibold"
                    >
                      <Sparkles className="size-3.5 text-emerald-400" />
                      <span>Conectar com Google (Firebase)</span>
                    </DropdownMenuItem>
                  </>
                )}

                {mode === 'demo' && users.length > 1 && (
                  <>
                    <DropdownMenuSeparator className="bg-slate-800 my-1" />
                    <DropdownMenuLabel className="text-[10px] font-semibold text-violet-300 uppercase px-2 py-1 flex items-center gap-1">
                      <Users2 className="size-3" /> Simular visualização:
                    </DropdownMenuLabel>
                    <div className="grid grid-cols-2 gap-1 p-1">
                      {users.map((u) => {
                        const active = u.uid === me?.uid
                        return (
                          <button
                            key={u.uid}
                            type="button"
                            onClick={() => switchProfile(u.uid)}
                            className={`px-2 py-1.5 rounded-lg text-xs font-semibold cursor-pointer transition-all text-center ${
                              active
                                ? 'bg-violet-600 text-white shadow-sm'
                                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                            }`}
                          >
                            {(u.displayName || 'Usuário').split(' ')[0]}
                          </button>
                        )
                      })}
                    </div>
                  </>
                )}

                <DropdownMenuSeparator className="bg-slate-800 my-1" />

                <DropdownMenuItem
                  onClick={signOut}
                  className="flex items-center gap-2 px-2.5 py-1.5 text-xs text-rose-400 hover:text-rose-300 hover:bg-rose-500/10 rounded-lg cursor-pointer"
                >
                  <LogOut className="size-3.5" />
                  <span>Sair da Conta</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </div>
    </header>
  )
}
