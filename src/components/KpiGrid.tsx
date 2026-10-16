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
} from 'lucide-react'

interface KpiGridProps {
  onOpenReceivables: () => void
}

export function KpiGrid({
  onOpenReceivables,
}: KpiGridProps) {
  const { summary } = useFinance()

  if (!summary) return null

  const isPositiveBalance = summary.freeBalance >= 0
  const incomeRemainingPct = summary.income > 0 ? Math.round((summary.freeBalance / summary.income) * 100) : 0

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-3.5">
      {/* 1. HERO SECTION: Saldo Livre Real & Limite Diário (7 cols desktop) */}
      <Card
        className={`lg:col-span-7 xl:col-span-7 glass-card relative overflow-hidden transition-all duration-300 border ${
          isPositiveBalance
            ? 'border-emerald-500/30 hover:border-emerald-500/40 shadow-[0_12px_40px_-15px_rgba(16,185,129,0.2)]'
            : 'border-rose-500/30 hover:border-rose-500/40 shadow-[0_12px_40px_-15px_rgba(244,63,94,0.2)]'
        }`}
      >
        {/* Ambient atmospheric glow */}
        <div
          className={`absolute -top-16 -right-16 w-64 h-64 rounded-full blur-3xl pointer-events-none opacity-40 transition-colors ${
            isPositiveBalance ? 'bg-emerald-500/25' : 'bg-rose-500/25'
          }`}
        />

        <CardContent className="p-4 sm:p-5 flex flex-col justify-between h-full relative z-10 gap-4">
          {/* Top row: Label + Income Breakdown Tag */}
          <div className="flex items-center justify-between flex-wrap gap-2">
            <div className="flex items-center gap-2">
              <div
                className={`p-2 rounded-xl border ${
                  isPositiveBalance
                    ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-300'
                    : 'bg-rose-500/15 border-rose-500/30 text-rose-300'
                }`}
              >
                <Wallet className="size-4" />
              </div>
              <div>
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 block leading-tight">
                  Saldo Livre Disponível
                </span>
                <span className="text-[11px] text-slate-400">
                  Total após descontar contas e cartões do mês
                </span>
              </div>
            </div>

            <Badge
              variant="outline"
              className="text-xs px-2.5 py-1 rounded-lg bg-slate-900/80 border-slate-700/70 text-slate-300 flex items-center gap-1.5"
            >
              <span className="text-[11px] text-slate-400">Receitas:</span>
              <strong className="text-white">{formatBRL(summary.income)}</strong>
              <span className="text-[10px] text-emerald-400 font-mono">
                ({incomeRemainingPct > 0 ? `+${incomeRemainingPct}%` : `${incomeRemainingPct}%`})
              </span>
            </Badge>
          </div>

          {/* Center: Hero Free Balance display */}
          <div className="py-1">
            <div className="flex items-baseline gap-2">
              <span
                className={`text-3xl sm:text-4xl lg:text-5xl font-black tracking-tight drop-shadow-sm ${
                  isPositiveBalance ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                {formatBRL(summary.freeBalance)}
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1 font-medium">
              Receitas <span className="text-slate-300 font-semibold">{formatBRL(summary.income)}</span> − Comprometido <span className="text-amber-300 font-semibold">{formatBRL(summary.committed)}</span> − Já Pago <span className="text-slate-300 font-semibold">{formatBRL(summary.paid)}</span>
            </p>
          </div>

          {/* Inset Sub-Card: Daily Safe Limit Meter */}
          <div className="p-3 sm:p-3.5 rounded-xl bg-slate-950/60 border border-white/[0.06] flex items-center justify-between gap-2.5">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-xl bg-cyan-500/15 border border-cyan-500/30 text-cyan-300 shrink-0">
                <TrendingUp className="size-4" />
              </div>
              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs text-slate-400 font-medium">Disponível por Dia:</span>
                  <strong className="text-sm sm:text-base font-bold text-cyan-300 font-mono">
                    {summary.dailyLimit != null ? formatBRL(summary.dailyLimit) : '—'}
                  </strong>
                  <span className="text-[10px] text-slate-500">/ dia</span>
                </div>
                <span className="text-[11px] text-slate-500">
                  {summary.monthState === 'past'
                    ? 'Mês encerrado'
                    : `Restam ${summary.daysRemaining} dias para fechar o mês`}
                </span>
              </div>
            </div>

            {summary.monthState !== 'past' && summary.dailyLimit != null && (
              <Badge variant="outline" className="text-[10px] bg-cyan-950/40 text-cyan-300 border-cyan-800/60 font-mono hidden xs:flex">
                Gasto Seguro
              </Badge>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 2. SECONDARY SECTION: Comprometido, Já Pago e A Receber (5 cols desktop) */}
      <div className="lg:col-span-5 xl:col-span-5 grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-1 gap-2.5">
        {/* Card A: Comprometido a Pagar */}
        <Card className="glass-card border-slate-800/80 hover:border-amber-500/30 transition-all p-3.5 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <Clock className="size-3.5 text-amber-400" />
              Comprometido
            </span>
            <Badge variant="outline" className="text-[10px] bg-amber-500/10 text-amber-300 border-amber-500/30 py-0">
              A Pagar
            </Badge>
          </div>

          <div className="my-1">
            <span className="text-xl sm:text-2xl font-bold tracking-tight text-amber-300">
              {formatBRL(summary.committed)}
            </span>
          </div>

          <div className="flex items-center justify-between gap-1 text-[11px] pt-1.5 border-t border-white/[0.05]">
            <span className="text-amber-400/90 flex items-center gap-1">
              <span className="size-1.5 rounded-full bg-amber-400" />
              Estimado: <strong>{formatBRL(summary.committedEstimated)}</strong>
            </span>
            <span className="text-orange-400/90 flex items-center gap-1">
              <span className="size-1.5 rounded-full bg-orange-400" />
              Real: <strong>{formatBRL(summary.committedPending)}</strong>
            </span>
          </div>
        </Card>

        {/* Card B: Já Pago no Mês */}
        <Card className="glass-card border-slate-800/80 hover:border-emerald-500/30 transition-all p-3.5 flex flex-col justify-between">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <CheckCircle2 className="size-3.5 text-emerald-400" />
              Já Pago no Mês
            </span>
            <Badge variant="outline" className="text-[10px] bg-emerald-500/10 text-emerald-300 border-emerald-500/30 py-0">
              Liquidado
            </Badge>
          </div>

          <div className="my-1">
            <span className="text-xl sm:text-2xl font-bold tracking-tight text-slate-100">
              {formatBRL(summary.paid)}
            </span>
          </div>

          <div className="flex items-center justify-between text-[11px] text-slate-400 pt-1.5 border-t border-white/[0.05]">
            <span>Despesas já quitadas</span>
            <span className="text-emerald-400 font-medium">100% baixado</span>
          </div>
        </Card>

        {/* Card C: A Receber de Terceiros e Amigos */}
        <Card
          onClick={onOpenReceivables}
          className={`glass-card border-slate-800/80 transition-all p-3.5 flex flex-col justify-between cursor-pointer group ${
            summary.receivableTotal > 0 ? 'hover:border-violet-500/40 hover:bg-violet-950/15' : ''
          }`}
        >
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
              <HandCoins className="size-3.5 text-violet-400" />
              A Receber
            </span>
            <Badge variant="outline" className="text-[10px] bg-violet-500/15 text-violet-300 border-violet-500/40 py-0">
              Cartão / Amigos
            </Badge>
          </div>

          <div className="my-1 flex items-baseline justify-between">
            <span className="text-xl sm:text-2xl font-bold tracking-tight text-violet-300">
              {formatBRL(summary.receivableTotal)}
            </span>
            <span className="text-[10px] text-violet-400 font-medium group-hover:underline flex items-center gap-0.5">
              Ver lista <ArrowRight className="size-2.5 transition-transform group-hover:translate-x-0.5" />
            </span>
          </div>

          <div className="flex items-center justify-between text-[11px] text-slate-400 pt-1.5 border-t border-white/[0.05]">
            <div className="flex items-center gap-2 truncate">
              {summary.receivableFromPartner > 0 && (
                <span>Parceiro: <strong className="text-slate-200">{formatBRL(summary.receivableFromPartner)}</strong></span>
              )}
              {summary.receivableFromExternal > 0 && (
                <span>Terceiros: <strong className="text-slate-200">{formatBRL(summary.receivableFromExternal)}</strong></span>
              )}
              {summary.receivableTotal === 0 && <span className="text-slate-500">Nenhum valor pendente</span>}
            </div>
            <ArrowRight className="size-3 text-violet-400 group-hover:translate-x-0.5 transition-transform shrink-0" />
          </div>
        </Card>
      </div>
    </div>
  )
}
