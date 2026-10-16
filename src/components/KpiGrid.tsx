import { Card, CardContent } from '@/components/ui/card'
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
  Info,
} from 'lucide-react'

interface KpiGridProps {
  onOpenReceivables: () => void
  onOpenProjection?: () => void
  onOpenRecurring?: () => void
}

export function KpiGrid({ onOpenReceivables, onOpenProjection, onOpenRecurring }: KpiGridProps) {
  const { summary } = useFinance()

  if (!summary) return null

  const isPositiveBalance = summary.freeBalance >= 0

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3.5">
      {/* 1. Saldo Livre Real do Mês (HERO) */}
      <Card
        className={`glass-card relative overflow-hidden transition-all duration-300 ${
          isPositiveBalance
            ? 'border-emerald-500/30 hover:border-emerald-500/50 shadow-[0_10px_30px_-10px_rgba(16,185,129,0.25)]'
            : 'border-rose-500/30 hover:border-rose-500/50 shadow-[0_10px_30px_-10px_rgba(244,63,94,0.25)]'
        }`}
      >
        <div
          className={`absolute top-0 right-0 w-36 h-36 rounded-full blur-3xl pointer-events-none ${
            isPositiveBalance ? 'bg-emerald-500/15' : 'bg-rose-500/15'
          }`}
        />
        <CardContent className="p-4 flex flex-col justify-between h-full">
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                <Wallet className={`size-3.5 ${isPositiveBalance ? 'text-emerald-400' : 'text-rose-400'}`} />
                Saldo Livre Real
              </span>
              <span
                className="text-[11px] text-slate-500 cursor-help"
                title="Receitas − (Já Pago + Comprometido)"
              >
                <Info className="size-3.5" />
              </span>
            </div>

            <div className="flex items-baseline gap-1 my-1">
              <span
                className={`text-2xl sm:text-3xl font-extrabold tracking-tight ${
                  isPositiveBalance ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                {formatBRL(summary.freeBalance)}
              </span>
            </div>
          </div>

          <div className="text-[11px] text-slate-400 border-t border-white/[0.06] pt-2 mt-2 flex items-center justify-between">
            {onOpenRecurring ? (
              <button
                type="button"
                onClick={onOpenRecurring}
                className="hover:text-emerald-300 transition-colors cursor-pointer text-left flex items-center gap-1 group"
                title="Configurar Salário Base e Moldes Recorrentes"
              >
                <span>Receitas: <strong className="text-slate-200 underline decoration-slate-600 underline-offset-2 group-hover:text-emerald-300">{formatBRL(summary.income)}</strong></span>
              </button>
            ) : (
              <span>Receitas: <strong className="text-slate-200">{formatBRL(summary.income)}</strong></span>
            )}
            <span className="text-slate-500">Sobram {summary.income > 0 ? Math.round((summary.freeBalance / summary.income) * 100) : 0}%</span>
          </div>
        </CardContent>
      </Card>

      {/* 2. Disponível por Dia (Limite Diário Seguro) */}
      <Card className="glass-card border-slate-800/80 hover:border-slate-700/80 transition-all">
        <CardContent className="p-4 flex flex-col justify-between h-full">
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                <TrendingUp className="size-3.5 text-cyan-400" />
                Disponível por Dia
              </span>
              <Badge variant="outline" className="text-[10px] bg-cyan-950/40 text-cyan-300 border-cyan-800/60 py-0">
                {summary.daysRemaining} dias
              </Badge>
            </div>

            <div className="flex items-baseline gap-1 my-1">
              <span className="text-2xl font-bold tracking-tight text-cyan-300">
                {summary.dailyLimit != null ? formatBRL(summary.dailyLimit) : '—'}
              </span>
              <span className="text-xs text-slate-500 font-medium">/ dia</span>
            </div>
          </div>

          <div className="text-[11px] text-slate-400 border-t border-white/[0.06] pt-2 mt-2 flex items-center justify-between">
            <span>Meta diária segura</span>
            {onOpenProjection ? (
              <button
                type="button"
                onClick={onOpenProjection}
                className="text-cyan-400 hover:text-cyan-300 font-medium transition-colors cursor-pointer flex items-center gap-0.5"
                title="Ver projeção de fluxo dos próximos 6 meses"
              >
                <span>Projeção 6m →</span>
              </button>
            ) : (
              <span className="text-slate-300 font-medium">
                {summary.monthState === 'past' ? 'Mês fechado' : `${summary.daysRemaining}d restantes`}
              </span>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 3. Comprometido (A Pagar + Estimado) */}
      <Card className="glass-card border-slate-800/80 hover:border-slate-700/80 transition-all">
        <CardContent className="p-4 flex flex-col justify-between h-full">
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                <Clock className="size-3.5 text-amber-400" />
                Comprometido
              </span>
              <span className="text-[10px] text-slate-400 font-medium">A pagar</span>
            </div>

            <div className="flex items-baseline gap-1 my-1">
              <span className="text-2xl font-bold tracking-tight text-amber-300">
                {formatBRL(summary.committed)}
              </span>
            </div>
          </div>

          <div className="border-t border-white/[0.06] pt-2 mt-2 flex items-center justify-between gap-1 text-[11px]">
            <span className="flex items-center gap-1 text-amber-400">
              <span className="size-1.5 rounded-full bg-amber-400" />
              Estimado: <strong>{formatBRL(summary.committedEstimated)}</strong>
            </span>
            <span className="flex items-center gap-1 text-orange-400">
              <span className="size-1.5 rounded-full bg-orange-400" />
              Real: <strong>{formatBRL(summary.committedPending)}</strong>
            </span>
          </div>
        </CardContent>
      </Card>

      {/* 4. Já Pago no Mês */}
      <Card className="glass-card border-slate-800/80 hover:border-slate-700/80 transition-all">
        <CardContent className="p-4 flex flex-col justify-between h-full">
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                <CheckCircle2 className="size-3.5 text-emerald-400" />
                Já Pago no Mês
              </span>
              <span className="text-[10px] text-emerald-500 font-medium">Liquidado</span>
            </div>

            <div className="flex items-baseline gap-1 my-1">
              <span className="text-2xl font-bold tracking-tight text-slate-100">
                {formatBRL(summary.paid)}
              </span>
            </div>
          </div>

          <div className="text-[11px] text-slate-400 border-t border-white/[0.06] pt-2 mt-2 flex items-center justify-between">
            <span>Impacto quitado</span>
            <span className="text-emerald-400 font-medium">Tudo liquidado</span>
          </div>
        </CardContent>
      </Card>

      {/* 5. A Receber de Terceiros / Parceiro */}
      <Card
        className={`glass-card border-slate-800/80 transition-all cursor-pointer group ${
          summary.receivableTotal > 0 ? 'hover:border-violet-500/40' : ''
        }`}
        onClick={onOpenReceivables}
      >
        <CardContent className="p-4 flex flex-col justify-between h-full">
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                <HandCoins className="size-3.5 text-violet-400" />
                A Receber
              </span>
              <Badge variant="outline" className="text-[10px] bg-violet-950/40 text-violet-300 border-violet-800/60 py-0">
                Cartão / Terceiros
              </Badge>
            </div>

            <div className="flex items-baseline gap-1 my-1">
              <span className="text-2xl font-bold tracking-tight text-violet-300">
                {formatBRL(summary.receivableTotal)}
              </span>
            </div>
          </div>

          <div className="border-t border-white/[0.06] pt-2 mt-2 flex items-center justify-between text-[11px] text-slate-400">
            <div className="flex items-center gap-2">
              {summary.receivableFromPartner > 0 && (
                <span>Parceiro: <strong className="text-slate-200">{formatBRL(summary.receivableFromPartner)}</strong></span>
              )}
              {summary.receivableFromExternal > 0 && (
                <span>Terceiros: <strong className="text-slate-200">{formatBRL(summary.receivableFromExternal)}</strong></span>
              )}
              {summary.receivableTotal === 0 && <span>Sem pendências</span>}
            </div>
            <ArrowRight className="size-3 text-violet-400 group-hover:translate-x-0.5 transition-transform" />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
