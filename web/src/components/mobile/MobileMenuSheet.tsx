import { useFinance } from '@/store/FinanceContext'
import { getGeminiApiKey, getSelectedGeminiModel } from '@/lib/gemini'
import {
  Users2,
  CreditCard,
  RefreshCw,
  CalendarRange,
  Zap,
  KeyRound,
  LogOut,
  ChevronRight,
  ShieldCheck,
  Sparkles,
  Landmark,
  History,
  TrendingUp,
} from 'lucide-react'

interface MobileMenuSheetProps {
  onOpenCards: () => void
  onOpenRecurring: () => void
  onOpenApiKey: () => void
  onOpenProjection: () => void
  onOpenFriends: () => void
  onGenerateBills: () => void
  onOpenOpenFinance?: () => void
  onOpenRecentActivity?: () => void
  onOpenInvestments?: () => void
  generating?: boolean
}

export function MobileMenuSheet({
  onOpenCards,
  onOpenRecurring,
  onOpenApiKey,
  onOpenProjection,
  onOpenFriends,
  onGenerateBills,
  onOpenOpenFinance,
  onOpenRecentActivity,
  onOpenInvestments,
  generating,
}: MobileMenuSheetProps) {
  const {
    mode,
    users,
    me,
    profile,
    signOut,
    switchProfile,
    pendingFriendRequests,
    pendingChargeGroups,
  } = useFinance()

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

  const totalAlerts = pendingFriendRequests.length + pendingChargeGroups.length

  return (
    <div className="space-y-3.5 pb-24 animate-in fade-in duration-200">
      {/* 1. User Profile Card */}
      <div className="glass-card rounded-2xl p-4 border border-white/[0.08] flex items-center justify-between min-w-0">
        <div className="flex items-center gap-3 min-w-0 flex-1 mr-2">
          <div className="size-11 rounded-2xl bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center justify-center font-bold text-base shadow-sm shrink-0">
            {profile?.displayName?.charAt(0) ?? 'U'}
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="font-bold text-sm text-white truncate">{profile?.displayName ?? 'Usuário'}</h3>
            <p className="text-xs text-slate-400 truncate">{profile?.email || 'Conta Pessoal'}</p>
            <span className="inline-flex items-center gap-1 text-[10px] text-emerald-400 font-medium mt-0.5">
              <ShieldCheck className="size-3 shrink-0" /> Sessão Ativa
            </span>
          </div>
        </div>

        <button
          type="button"
          onClick={signOut}
          className="p-2 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 text-xs font-semibold flex items-center gap-1 cursor-pointer transition-colors shrink-0 ml-auto"
          title="Sair da conta"
        >
          <LogOut className="size-4" />
        </button>
      </div>

      {/* Demo Switcher if in demo mode */}
      {mode === 'demo' && users.length > 1 && (
        <div className="glass-card rounded-2xl p-3 border border-violet-500/20 bg-violet-950/20">
          <span className="text-[11px] font-semibold text-violet-300 block mb-2 flex items-center gap-1.5">
            <Users2 className="size-3.5 text-violet-400" /> Alternar Perfil Demo:
          </span>
          <div className="grid grid-cols-2 gap-2">
            {users.map((u) => {
              const active = u.uid === me?.uid
              return (
                <button
                  key={u.uid}
                  type="button"
                  onClick={() => switchProfile(u.uid)}
                  className={`py-2 px-3 rounded-xl text-xs font-semibold cursor-pointer transition-all text-center ${
                    active
                      ? 'bg-violet-600 text-white shadow-md'
                      : 'bg-slate-900/80 text-slate-300 hover:bg-slate-800 border border-slate-800'
                  }`}
                >
                  {u.displayName}
                </button>
              )
            })}
          </div>
        </div>
      )}

      {/* 3. Menu Navigation List */}
      <div className="glass-card rounded-2xl border border-white/[0.08] overflow-hidden divide-y divide-white/[0.05]">
        {/* Amigos & Aprovações */}
        <button
          type="button"
          onClick={onOpenFriends}
          className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
        >
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-violet-500/15 border border-violet-500/30 text-violet-300 flex items-center justify-center">
              <Users2 className="size-4" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-white">Amigos & Divisões</span>
                {totalAlerts > 0 && (
                  <span className="px-1.5 py-0.5 rounded-full bg-amber-500 text-slate-950 font-bold text-[10px] animate-pulse">
                    {totalAlerts} pendente{totalAlerts > 1 ? 's' : ''}
                  </span>
                )}
              </div>
              <span className="text-xs text-slate-400">Gerenciar conexões e aprovações de cartão</span>
            </div>
          </div>
          <ChevronRight className="size-4 text-slate-500" />
        </button>

        {/* Open Finance Brasil */}
        {onOpenOpenFinance && (
          <button
            type="button"
            onClick={onOpenOpenFinance}
            className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
          >
            <div className="flex items-center gap-3">
              <div className="size-9 rounded-xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 flex items-center justify-center">
                <Landmark className="size-4" />
              </div>
              <div>
                <span className="text-sm font-semibold text-white block">Open Finance Brasil</span>
                <span className="text-xs text-slate-400">Conectar contas bancárias e faturas</span>
              </div>
            </div>
            <ChevronRight className="size-4 text-slate-500" />
          </button>
        )}

        {/* Histórico Recente (Ordenado por Cadastro) */}
        {onOpenRecentActivity && (
          <button
            type="button"
            onClick={onOpenRecentActivity}
            className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
          >
            <div className="flex items-center gap-3">
              <div className="size-9 rounded-xl bg-sky-500/15 border border-sky-500/30 text-sky-300 flex items-center justify-center">
                <History className="size-4" />
              </div>
              <div>
                <span className="text-sm font-semibold text-white block">Histórico Recente</span>
                <span className="text-xs text-slate-400">Últimas cadastradas ou recebidas</span>
              </div>
            </div>
            <ChevronRight className="size-4 text-slate-500" />
          </button>
        )}

        {/* Investimentos & Patrimônio */}
        {onOpenInvestments && (
          <button
            type="button"
            onClick={onOpenInvestments}
            className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
          >
            <div className="flex items-center gap-3">
              <div className="size-9 rounded-xl bg-purple-500/15 border border-purple-500/30 text-purple-300 flex items-center justify-center">
                <TrendingUp className="size-4" />
              </div>
              <div>
                <span className="text-sm font-semibold text-white block">Investimentos & Patrimônio</span>
                <span className="text-xs text-slate-400">Aportes, classes de ativos e simulador</span>
              </div>
            </div>
            <ChevronRight className="size-4 text-slate-500" />
          </button>
        )}

        {/* Cartões de Crédito */}
        <button
          type="button"
          onClick={onOpenCards}
          className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
        >
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-sky-500/15 border border-sky-500/30 text-sky-300 flex items-center justify-center">
              <CreditCard className="size-4" />
            </div>
            <div>
              <span className="text-sm font-semibold text-white block">Cartões de Crédito</span>
              <span className="text-xs text-slate-400">Limites, faturas e cartões compartilhados</span>
            </div>
          </div>
          <ChevronRight className="size-4 text-slate-500" />
        </button>

        {/* Moldes Recorrentes & Salário */}
        <button
          type="button"
          onClick={onOpenRecurring}
          className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
        >
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-amber-500/15 border border-amber-500/30 text-amber-300 flex items-center justify-center">
              <RefreshCw className="size-4" />
            </div>
            <div>
              <span className="text-sm font-semibold text-white block">Moldes & Salário Base</span>
              <span className="text-xs text-slate-400">Contas fixas, assinaturas e receitas</span>
            </div>
          </div>
          <ChevronRight className="size-4 text-slate-500" />
        </button>

        {/* Projeção de Fluxo (6 Meses) */}
        <button
          type="button"
          onClick={onOpenProjection}
          className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
        >
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-cyan-500/15 border border-cyan-500/30 text-cyan-300 flex items-center justify-center">
              <CalendarRange className="size-4" />
            </div>
            <div>
              <span className="text-sm font-semibold text-white block">Projeção 6 Meses</span>
              <span className="text-xs text-slate-400">Fluxo futuro de parcelas e saldo livre</span>
            </div>
          </div>
          <ChevronRight className="size-4 text-slate-500" />
        </button>

        {/* Gerar Contas Fixas */}
        <button
          type="button"
          onClick={onGenerateBills}
          disabled={generating}
          className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
        >
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 flex items-center justify-center">
              <Zap className={`size-4 ${generating ? 'animate-spin' : ''}`} />
            </div>
            <div>
              <span className="text-sm font-semibold text-white block">
                {generating ? 'Gerando Contas...' : '⚡ Gerar Contas do Mês'}
              </span>
              <span className="text-xs text-slate-400">Importa moldes para o mês atual</span>
            </div>
          </div>
          <ChevronRight className="size-4 text-slate-500" />
        </button>

        {/* Configuração de Chave API Gemini */}
        <button
          type="button"
          onClick={onOpenApiKey}
          className="w-full p-4 flex items-center justify-between hover:bg-slate-800/40 transition-colors text-left cursor-pointer active:bg-slate-800/60"
        >
          <div className="flex items-center gap-3">
            <div className="size-9 rounded-xl bg-teal-500/15 border border-teal-500/30 text-teal-300 flex items-center justify-center">
              <KeyRound className="size-4" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-sm font-semibold text-white">Chave API Gemini</span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-400 font-mono">
                  {modelLabel}
                </span>
              </div>
              <span className="text-xs text-slate-400">Alterar chave ou modelo de IA</span>
            </div>
          </div>
          <ChevronRight className="size-4 text-slate-500" />
        </button>
      </div>

      {/* App version info */}
      <div className="text-center py-2 text-xs text-slate-400">
        <p className="font-semibold text-slate-400 flex items-center justify-center gap-1.5">
          <Sparkles className="size-3 text-emerald-400" /> Saldo Livre Web
        </p>
        <p className="text-[11px] text-slate-400 mt-0.5">Sincronizado em Nuvem · Gemini Flash Lite integrado</p>
      </div>
    </div>
  )
}
