import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL } from '@/lib/utils'
import {
  Wallet,
  TrendingUp,
  Clock,
  CheckCircle2,
  HandCoins,
  ArrowRight,
} from 'lucide-react'

interface KpiGridProps {
  onOpenReceivables: () => void
}

export function KpiGrid({ onOpenReceivables }: KpiGridProps) {
  const { summary } = useFinance()

  if (!summary) return null

  const isPositiveBalance = summary.freeBalance >= 0
  const incomeRemainingPct = summary.income > 0 ? Math.round((summary.freeBalance / summary.income) * 100) : 0

  return (
    <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-12 gap-2 sm:gap-2.5">
      {/* 1. HERO COMPACT: Saldo Livre Real (3 cols on desktop, 2 cols on mobile) */}
      <Card
        className={`col-span-2 lg:col-span-3 glass-card relative overflow-hidden transition-all duration-300 border p-2.5 sm:p-3 flex flex-col justify-between ${
          isPositiveBalance
            ? 'bg-gradient-to-br from-emerald-950/60 via-slate-900/90 to-slate-900 border-emerald-500/30 hover:border-emerald-500/50 shadow-lg shadow-emerald-950/20'
            : 'bg-gradient-to-br from-rose-950/60 via-slate-900/90 to-slate-900 border-rose-500/30 hover:border-rose-500/50 shadow-lg shadow-rose-950/20'
        }`}
      >
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5">
            <div
              className={`p-1.5 rounded-lg ${
                isPositiveBalance
                  ? 'bg-emerald-500/15 text-emerald-300'
                  : 'bg-rose-500/15 text-rose-300'
              }`}
            >
              <Wallet className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
              Saldo Livre
            </span>
          </div>

          <Badge
            variant="outline"
            className={`text-[9px] px-1.5 py-0 font-mono border-0 ${
              incomeRemainingPct > 0
                ? 'bg-emerald-500/15 text-emerald-300'
                : 'bg-rose-500/15 text-rose-300'
            }`}
          >
            {incomeRemainingPct > 0 ? `+${incomeRemainingPct}%` : `${incomeRemainingPct}%`}
          </Badge>
        </div>

        <div className="flex items-baseline justify-between gap-1">
          <span
            className={`text-xl sm:text-2xl font-extrabold tracking-tight drop-shadow-xs font-mono ${
              isPositiveBalance ? 'text-emerald-400' : 'text-rose-400'
            }`}
          >
            {formatBRL(summary.freeBalance)}
          </span>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 border-t border-white/5 min-w-0 gap-1">
          <span className="truncate min-w-0 text-slate-400">Rec: <strong className="text-slate-300 font-semibold">{formatBRL(summary.income)}</strong></span>
          <span className="truncate min-w-0 text-slate-400">Pago: <strong className="text-slate-300 font-semibold">{formatBRL(summary.paid)}</strong></span>
        </div>
      </Card>

      {/* 2. Limite Diário Seguro (2 cols desktop, 1 col mobile) */}
      <Card className="col-span-1 lg:col-span-2 glass-card border-white/5 hover:border-white/10 transition-colors p-2.5 sm:p-3 flex flex-col justify-between">
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <div className="p-1.5 rounded-lg bg-cyan-500/15 text-cyan-300 shrink-0">
              <TrendingUp className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 truncate">
              Por Dia
            </span>
          </div>
          <span className="text-[9px] text-cyan-400 font-mono font-medium hidden sm:inline shrink-0">
            Seguro
          </span>
        </div>

        <div className="flex items-baseline gap-1">
          <span className="text-base sm:text-lg font-bold tracking-tight text-cyan-300 font-mono">
            {summary.dailyLimit != null ? formatBRL(summary.dailyLimit) : '—'}
          </span>
          <span className="text-[10px] text-slate-400 font-normal">/dia</span>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 border-t border-white/5">
          <span className="truncate text-slate-400">
            {summary.monthState === 'past'
              ? 'Encerrado'
              : <>Restam <strong className="text-slate-300 font-semibold">{summary.daysRemaining} dias</strong></>}
          </span>
          <span className="text-[9px] text-slate-400 hidden sm:inline">ritmo</span>
        </div>
      </Card>

      {/* 3. Comprometido (2 cols desktop, 1 col mobile) */}
      <Card className="col-span-1 lg:col-span-2 glass-card border-white/5 hover:border-white/10 transition-colors p-2.5 sm:p-3 flex flex-col justify-between">
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <div className="p-1.5 rounded-lg bg-amber-500/15 text-amber-300 shrink-0">
              <Clock className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 truncate">
              A Pagar
            </span>
          </div>
          <Badge variant="outline" className="text-[9px] bg-amber-500/15 text-amber-300 border-0 py-0 px-1.5 hidden sm:inline shrink-0">
            Fatura
          </Badge>
        </div>

        <div className="flex items-baseline gap-1">
          <span className="text-base sm:text-lg font-bold tracking-tight text-amber-300 font-mono">
            {formatBRL(summary.committed)}
          </span>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 border-t border-white/5 min-w-0 gap-1">
          <span className="truncate min-w-0 text-slate-400">Est: <strong className="text-slate-300 font-semibold">{formatBRL(summary.committedEstimated)}</strong></span>
          <span className="truncate min-w-0 text-slate-400 hidden sm:inline">Real: <strong className="text-slate-300 font-semibold">{formatBRL(summary.committedPending)}</strong></span>
        </div>
      </Card>

      {/* 4. Já Pago no Mês (2 cols desktop, 1 col mobile) */}
      <Card className="col-span-1 lg:col-span-2 glass-card border-white/5 hover:border-white/10 transition-colors p-2.5 sm:p-3 flex flex-col justify-between">
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <div className="p-1.5 rounded-lg bg-emerald-500/15 text-emerald-300 shrink-0">
              <CheckCircle2 className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 truncate">
              Já Pago
            </span>
          </div>
          <Badge variant="outline" className="text-[9px] bg-emerald-500/15 text-emerald-300 border-0 py-0 px-1.5 hidden sm:inline shrink-0">
            Quitado
          </Badge>
        </div>

        <div className="flex items-baseline gap-1">
          <span className="text-base sm:text-lg font-bold tracking-tight text-slate-100 font-mono">
            {formatBRL(summary.paid)}
          </span>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 border-t border-white/5 min-w-0 gap-1">
          <span className="truncate min-w-0 text-emerald-400 font-semibold">100% baixado</span>
          <span className="text-slate-400 hidden sm:inline shrink-0">no mês</span>
        </div>
      </Card>

      {/* 5. A Receber de Terceiros e Amigos (3 cols desktop, 1 col mobile) */}
      <Card
        onClick={onOpenReceivables}
        className={`col-span-1 lg:col-span-3 glass-card border-white/5 hover:border-white/10 transition-colors p-2.5 sm:p-3 flex flex-col justify-between cursor-pointer group ${
          summary.receivableTotal > 0 ? 'hover:bg-purple-950/20' : ''
        }`}
      >
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <div className="p-1.5 rounded-lg bg-purple-500/15 text-purple-300 shrink-0">
              <HandCoins className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 truncate">
              A Receber
            </span>
          </div>
          <span className="text-[9px] text-purple-400 font-medium group-hover:underline flex items-center gap-0.5 shrink-0 ml-auto">
            <span className="hidden sm:inline">Ver detalhes</span> <ArrowRight className="size-2.5 transition-transform group-hover:translate-x-0.5" />
          </span>
        </div>

        <div className="flex items-baseline justify-between gap-1">
          <span className="text-base sm:text-lg font-bold tracking-tight text-purple-300 font-mono">
            {formatBRL(summary.receivableTotal)}
          </span>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 border-t border-white/5 min-w-0 gap-1">
          <div className="flex items-center gap-1.5 truncate min-w-0 flex-1">
            {summary.receivableFromPartner > 0 && (
              <span className="truncate text-slate-400">Parceiro: <strong className="text-slate-300 font-semibold">{formatBRL(summary.receivableFromPartner)}</strong></span>
            )}
            {summary.receivableFromExternal > 0 && (
              <span className="truncate text-slate-400">Terceiros: <strong className="text-slate-300 font-semibold">{formatBRL(summary.receivableFromExternal)}</strong></span>
            )}
            {summary.receivableTotal === 0 && <span className="text-slate-400 truncate">Nenhum valor a receber</span>}
          </div>
          <ArrowRight className="size-3 text-purple-400 group-hover:translate-x-0.5 transition-transform shrink-0 ml-auto" />
        </div>
      </Card>
    </div>
  )
}
