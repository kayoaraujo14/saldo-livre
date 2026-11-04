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
  Sparkles,
  LogOut,
  CalendarRange,
  Users2,
  SlidersHorizontal,
  History,
  TrendingUp,
  Receipt,
  PieChart,
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
  onOpenBudgets?: () => void
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
  onOpenBudgets,
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
  const isDemo = mode === 'demo'
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
    <header className="border-b border-white/5 bg-slate-950/80 backdrop-blur-md sticky top-0 z-40 transition-all pt-[max(0.4rem,env(safe-area-inset-top,0px))]">
      <div className="max-w-[1720px] mx-auto px-3 sm:px-6 py-2 sm:py-2.5">
        <div className="flex items-center justify-between gap-2 sm:gap-4">
          {/* ============================================================ */}
          {/* 1. ZONA ESQUERDA: Logo / Marca & Seletor de Mês em Pill       */}
          {/* ============================================================ */}
          <div className="flex items-center gap-2 sm:gap-4 shrink-0 min-w-0">
            {/* Logo */}
            <div className="flex items-center gap-2 sm:gap-2.5 shrink-0">
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

            {/* Seletor de Mês (Pill elegante com < Mês > Hoje) */}
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
                  className="text-[9px] sm:text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-400 hover:bg-emerald-500/25 transition-colors cursor-pointer font-medium"
                >
                  Hoje
                </button>
              )}
            </div>
          </div>

          {/* ============================================================ */}
          {/* 2. ZONA CENTRO: Atalhos Contextuais (Faturas & Amigos)       */}
          {/* ============================================================ */}
          <div className="hidden md:flex items-center gap-1.5 bg-slate-900/40 border border-white/5 p-1 rounded-xl">
            {/* Atalho de Faturas Consolidadas */}
            {onOpenInvoices && (
              <Button
                variant="ghost"
                size="sm"
                onClick={onOpenInvoices}
                className="text-xs h-7 px-2.5 gap-1.5 text-slate-300 hover:text-cyan-300 hover:bg-slate-800/60 cursor-pointer rounded-lg transition-all"
                title="Acompanhamento consolidado de faturas por cartão"
              >
                <Receipt className="size-3.5 text-cyan-400" />
                <span>Faturas</span>
              </Button>
            )}

            {/* Atalho de Amigos & Compartilhamento */}
            <Button
              variant="ghost"
              size="sm"
              onClick={onOpenFriends}
              className={`text-xs h-7 px-2.5 gap-1.5 cursor-pointer rounded-lg transition-all ${
                totalAlerts > 0
                  ? 'text-amber-300 bg-amber-500/15 hover:bg-amber-500/25 font-semibold'
                  : 'text-slate-300 hover:text-violet-300 hover:bg-slate-800/60'
              }`}
              title="Rede de amigos, parceiro(a) e solicitações de divisão"
            >
              <Users2 className="size-3.5 text-violet-400" />
              <span>Amigos</span>
              {totalAlerts > 0 && (
                <span className="size-4 rounded-full bg-amber-500 text-slate-950 font-bold text-[10px] flex items-center justify-center animate-pulse">
                  {totalAlerts}
                </span>
              )}
            </Button>
          </div>

          {/* ============================================================ */}
          {/* 3. ZONA DIREITA: Novo Lançamento, Gestão/Menu & Perfil       */}
          {/* ============================================================ */}
          <div className="flex items-center gap-1.5 sm:gap-2.5 shrink-0 ml-auto md:ml-0">
            {/* Botão de Ação Primária: + Novo Lançamento */}
            <Button
              size="sm"
              onClick={onOpenNewTx}
              className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs h-7 sm:h-8 px-2.5 sm:px-3.5 gap-1.5 shadow-md shadow-emerald-950/60 cursor-pointer flex items-center rounded-xl transition-all active:scale-95 shrink-0"
              title="Adicionar novo lançamento (entrada ou despesa)"
            >
              <Plus className="size-3.5 stroke-[2.5]" />
              <span className="hidden sm:inline">Novo Lançamento</span>
              <span className="sm:hidden">Novo</span>
            </Button>

            {/* Menu Suspenso Consolidado: Gestão & Ferramentas */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="outline"
                  size="sm"
                  className="text-xs h-7 sm:h-8 gap-1.5 bg-slate-900/60 border-white/5 hover:border-white/10 hover:bg-slate-900 text-slate-300 cursor-pointer rounded-xl transition-all"
                  title="Acessar ferramentas de gestão financeira e planejamento"
                >
                  <SlidersHorizontal className="size-3.5 text-cyan-400" />
                  <span className="hidden sm:inline">Gestão & Ferramentas</span>
                  <span className="sm:hidden">Menu</span>
                  <ChevronDown className="size-3 text-slate-400" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-64 bg-slate-950 border border-slate-800 shadow-2xl p-1.5">
                {/* 1. Planejamento */}
                <DropdownMenuLabel className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-2 py-1">
                  Planejamento
                </DropdownMenuLabel>

                {onOpenBudgets && (
                  <DropdownMenuItem
                    onClick={onOpenBudgets}
                    className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                  >
                    <PieChart className="size-3.5 text-emerald-400" />
                    <div className="flex flex-col">
                      <span className="font-medium">Orçamentos por Categoria</span>
                      <span className="text-[10px] text-slate-400">Envelopes mensais e tetos de gastos</span>
                    </div>
                  </DropdownMenuItem>
                )}

                <DropdownMenuItem
                  onClick={onOpenProjection}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <CalendarRange className="size-3.5 text-cyan-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Projeção de Fluxo (6m)</span>
                    <span className="text-[10px] text-slate-400">Previsão e fluxo dos próximos meses</span>
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
                      <span className="text-[10px] text-slate-400">Aportes, classes de ativos e simulador</span>
                    </div>
                  </DropdownMenuItem>
                )}

                <DropdownMenuSeparator className="bg-slate-800 my-1" />

                {/* 2. Cadastros */}
                <DropdownMenuLabel className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-2 py-1">
                  Cadastros
                </DropdownMenuLabel>

                <DropdownMenuItem
                  onClick={onOpenCards}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <CreditCard className="size-3.5 text-violet-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Cartões de Crédito</span>
                    <span className="text-[10px] text-slate-400">Limites, fechamento e cartões</span>
                  </div>
                </DropdownMenuItem>

                <DropdownMenuItem
                  onClick={onOpenRecurring}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <RefreshCw className="size-3.5 text-amber-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Contas Fixas & Renda</span>
                    <span className="text-[10px] text-slate-400">Assinaturas, salários e contas mensais</span>
                  </div>
                </DropdownMenuItem>

                <DropdownMenuSeparator className="bg-slate-800 my-1" />

                {/* 3. Configurações & IA */}
                <DropdownMenuLabel className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider px-2 py-1">
                  Configurações & IA
                </DropdownMenuLabel>

                <DropdownMenuItem
                  onClick={onOpenApiKey}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <Sparkles className="size-3.5 text-emerald-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Copiloto Gemini IA</span>
                    <span className="text-[10px] text-emerald-400 font-mono">{modelLabel}</span>
                  </div>
                </DropdownMenuItem>

                <DropdownMenuItem
                  onClick={onOpenRecentActivity}
                  className="flex items-center gap-2.5 px-2.5 py-2 text-xs text-slate-200 hover:text-white hover:bg-slate-900 rounded-lg cursor-pointer"
                >
                  <History className="size-3.5 text-sky-400" />
                  <div className="flex flex-col">
                    <span className="font-medium">Histórico Recente</span>
                    <span className="text-[10px] text-slate-400">Últimas cadastradas ou recebidas</span>
                  </div>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>

            {/* Perfil do Usuário Moderno */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className="flex items-center gap-1.5 sm:gap-2 bg-slate-900/60 border border-white/5 h-7 sm:h-8 pl-1 sm:pl-1.5 pr-2 sm:pr-2.5 rounded-xl hover:border-white/10 hover:bg-slate-900 transition-all cursor-pointer outline-none shrink-0 group"
                  title="Perfil e conexões"
                >
                  <div
                    className={`size-5 sm:size-6 rounded-full flex items-center justify-center font-bold text-[9px] sm:text-[10px] shrink-0 ring-2 transition-all ${
                      isDemo
                        ? 'bg-amber-500/20 text-amber-300 ring-amber-500/40'
                        : 'bg-emerald-500/20 text-emerald-300 ring-emerald-500/40'
                    }`}
                  >
                    {profile?.displayName?.charAt(0) ?? 'U'}
                  </div>
                  <span className="text-xs font-semibold text-slate-200 hidden md:inline truncate max-w-[90px]">
                    {profile?.displayName?.split(' ')[0] ?? 'Usuário'}
                  </span>
                  <ChevronDown className="size-3 text-slate-400 group-hover:text-slate-200 transition-colors shrink-0" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-64 bg-slate-950 border border-slate-800 shadow-2xl p-1.5">
                <div className="px-2.5 py-2">
                  <p className="text-xs font-semibold text-white truncate">{profile?.displayName ?? 'Usuário'}</p>
                  <p className="text-[11px] text-slate-400 truncate">{profile?.email || 'Conta Pessoal'}</p>
                  {isDemo ? (
                    <div className="mt-2 flex items-center gap-1.5 text-[10px] text-amber-300 bg-amber-950/40 border border-amber-500/30 rounded-lg px-2 py-1">
                      <span className="size-1.5 rounded-full bg-amber-400 animate-pulse" />
                      <span className="font-medium">Modo Demonstração Ativo</span>
                    </div>
                  ) : (
                    <div className="mt-2 flex items-center gap-1.5 text-[10px] text-emerald-300 bg-emerald-950/40 border border-emerald-500/30 rounded-lg px-2 py-1">
                      <span className="size-1.5 rounded-full bg-emerald-400" />
                      <span className="font-medium">Nuvem Firebase Conectada</span>
                    </div>
                  )}
                </div>

                {isDemo && (
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

                {isDemo && users.length > 1 && (
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
                  className="flex items-center gap-2 px-2.5 py-2 text-xs text-rose-400 hover:text-rose-300 hover:bg-rose-500/10 rounded-lg cursor-pointer"
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
