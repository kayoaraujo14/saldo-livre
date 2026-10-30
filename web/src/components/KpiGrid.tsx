import { useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, monthLabel, shiftMonth, todayKey, round2 } from '@/lib/utils'
import { buildCardInvoice, invoiceDates } from '@/lib/invoices'
import { impactFor } from '@/lib/finance'
import {
  Wallet,
  TrendingUp,
  PieChart,
  CalendarClock,
  Gauge,
  Shield,
} from 'lucide-react'

interface KpiGridProps {
  onOpenReceivables?: () => void
}

export function KpiGrid({ onOpenReceivables }: KpiGridProps) {
  const { summary, month, deleteMonthClosure, transactions, cards, me } = useFinance()

  // 1. Consumo da Renda
  const consumptionPct = summary && summary.income > 0
    ? Math.round((summary.paid / summary.income) * 100)
    : 0
  const consumptionColor = consumptionPct > 100 ? 'rose' : consumptionPct > 80 ? 'amber' : 'emerald'

  // 2. Radar 7 Dias
  const radarData = useMemo(() => {
    if (!transactions) return { total: 0, count: 0, urgencyBadge: 'Tudo em dia', subtitle: 'Nenhuma conta nos próx. 7 dias', isUrgent: false }

    const today = todayKey()
    const now = new Date()
    const nextWeekDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 7)
    const nextWeekStr = `${nextWeekDate.getFullYear()}-${String(nextWeekDate.getMonth() + 1).padStart(2, '0')}-${String(nextWeekDate.getDate()).padStart(2, '0')}`

    // Despesas avulsas não vinculadas a fatura de cartão
    const nonCardTxs = transactions.filter((tx) => {
      if (tx.type !== 'expense' || tx.status === 'paid') return false
      if (tx.category === 'Estorno' || (tx.subcategory && tx.subcategory.includes('Estorno'))) return false
      if (tx.cardId && tx.reserveCardId == null) return false
      return tx.dueDate >= today && tx.dueDate <= nextWeekStr
    })

    let total = nonCardTxs.reduce((sum, tx) => sum + impactFor(tx, me?.uid || '', transactions), 0)
    let count = nonCardTxs.length
    let minDays: number | null = null

    nonCardTxs.forEach((tx) => {
      const diffTime = new Date(tx.dueDate).getTime() - new Date(today).getTime()
      const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
      if (minDays === null || diffDays < minDays) minDays = diffDays
    })

    // Faturas de cartões que vencem na próxima semana
    const candidateMonths = Array.from(new Set([month, shiftMonth(month, 1)]))
    const userCards = (cards || []).filter(
      (c) => c.ownerUid === me?.uid || (c.sharedWithUids && c.sharedWithUids.includes(me?.uid || ''))
    )

    for (const m of candidateMonths) {
      for (const card of userCards) {
        const { dueDate: invDueDate } = invoiceDates(card, m)
        if (invDueDate >= today && invDueDate <= nextWeekStr) {
          const inv = buildCardInvoice(card, m, transactions, me?.uid || '')
          if (inv.status !== 'paid' && inv.items.length > 0 && inv.openAmount > 0) {
            const amountOwed = card.ownerUid === me?.uid ? inv.openAmount : inv.myShare
            if (amountOwed > 0) {
              total += amountOwed
              count += 1
              const diffTime = new Date(invDueDate).getTime() - new Date(today).getTime()
              const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
              if (minDays === null || diffDays < minDays) minDays = diffDays
            }
          }
        }
      }
    }

    const isUrgent = count > 0 && minDays !== null && minDays <= 1
    const urgencyBadge = count === 0
      ? 'Tudo em dia'
      : minDays !== null && minDays <= 1
        ? 'Vence em breve!'
        : `${count} a vencer`
    const subtitle = count === 0
      ? 'Nenhuma conta nos próx. 7 dias'
      : minDays === 0
        ? 'Atenção: vence hoje'
        : minDays === 1
          ? 'Atenção: vence amanhã'
          : `${count} conta${count > 1 ? 's' : ''} a pagar`

    return { total: round2(total), count, urgencyBadge, subtitle, isUrgent }
  }, [transactions, cards, month, me?.uid])

  // 3. Termômetro Diário
  const dailyGaugeData = useMemo(() => {
    if (!transactions) return { todaySpent: 0, dailyLimit: summary?.dailyLimit ?? null, difference: 0, isEconomy: true, performanceText: '' }

    const today = todayKey()
    const getLocalDateStr = (timestamp?: number): string | null => {
      if (!timestamp) return null
      const d = new Date(timestamp)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    }

    const todaySpent = transactions
      .filter((tx) => {
        if (tx.type !== 'expense') return false
        if (tx.category === 'Estorno' || (tx.subcategory && tx.subcategory.includes('Estorno'))) return false
        const isToday = tx.dueDate === today || getLocalDateStr(tx.createdAt) === today
        return isToday
      })
      .reduce((sum, tx) => sum + impactFor(tx, me?.uid || '', transactions), 0)

    const dailyLimit = summary?.dailyLimit ?? null
    let isEconomy = true
    let diff = 0
    let performanceText = ''

    if (dailyLimit != null && dailyLimit > 0) {
      diff = round2(dailyLimit - todaySpent)
      if (diff >= 0) {
        isEconomy = true
        performanceText = `Economia de ${formatBRL(diff)} hoje`
      } else {
        isEconomy = false
        const excess = round2(Math.abs(diff))
        performanceText = `Passou ${formatBRL(excess)}`
      }
    } else if (summary?.monthState === 'past') {
      performanceText = 'Mês encerrado'
    } else {
      performanceText = `${formatBRL(todaySpent)} hoje`
    }

    return { todaySpent: round2(todaySpent), dailyLimit, difference: diff, isEconomy, performanceText }
  }, [transactions, summary?.dailyLimit, summary?.monthState, me?.uid])

  // 4. Saúde de Investimentos
  const investmentData = useMemo(() => {
    const totalInvested = summary?.totalInvested || 0
    const income = summary?.income || 0
    const incomePercentage = income > 0 ? Math.round((totalInvested / income) * 100) : 0
    const isTargetReached = incomePercentage >= 20
    const progressRatio = Math.min(1, incomePercentage / 20)

    return { totalInvested, incomePercentage, isTargetReached, progressRatio }
  }, [summary?.totalInvested, summary?.income])

  if (!summary) return null

  const isPositiveBalance = summary.freeBalance >= 0
  const incomeRemainingPct = summary.income > 0 ? Math.round((summary.freeBalance / summary.income) * 100) : 0

  return (
    <div className="grid grid-cols-2 lg:grid-cols-12 gap-2.5 sm:gap-3">
      {/* 1. HERO COMPACT: Saldo Livre Real com Limite Diário integrado no rodapé */}
      <div
        className={`col-span-2 lg:col-span-4 rounded-2xl relative overflow-hidden transition-all duration-300 p-3 sm:p-3.5 flex flex-col justify-between ${
          isPositiveBalance
            ? 'bg-gradient-to-br from-emerald-950/45 via-emerald-950/20 to-slate-900 border border-emerald-500/25 shadow-[0_8px_30px_-6px_rgba(16,185,129,0.25)] hover:shadow-[0_8px_34px_-4px_rgba(16,185,129,0.35)]'
            : 'bg-gradient-to-br from-rose-950/45 via-rose-950/20 to-slate-900 border border-rose-500/25 shadow-[0_8px_30px_-6px_rgba(244,63,94,0.25)] hover:shadow-[0_8px_34px_-4px_rgba(244,63,94,0.35)]'
        }`}
      >
        <div>
          <div className="flex items-center justify-between gap-1 mb-1.5">
            <div className="flex items-center gap-1.5 min-w-0">
              <div
                className={`p-1.5 rounded-lg shrink-0 ${
                  isPositiveBalance
                    ? 'bg-emerald-500/15 text-emerald-300'
                    : 'bg-rose-500/15 text-rose-300'
                }`}
              >
                <Wallet className="size-3.5" />
              </div>
              <span className="text-[10px] font-bold uppercase tracking-wider text-slate-300 truncate">
                Saldo Livre Real
              </span>
            </div>

            <Badge
              variant="outline"
              className={`text-[9px] px-1.5 py-0 font-mono border-0 shrink-0 ${
                incomeRemainingPct > 0
                  ? 'bg-emerald-500/15 text-emerald-300'
                  : 'bg-rose-500/15 text-rose-300'
              }`}
            >
              {incomeRemainingPct > 0 ? `+${incomeRemainingPct}%` : `${incomeRemainingPct}%`}
            </Badge>
          </div>

          <div className="flex items-baseline justify-between gap-1 flex-wrap">
            <span
              className={`text-2xl sm:text-3xl font-extrabold tracking-tight drop-shadow-xs font-mono ${
                isPositiveBalance ? 'text-emerald-400' : 'text-rose-400'
              }`}
            >
              {formatBRL(summary.freeBalance)}
            </span>
            {summary.carryoverAmount && summary.carryoverAmount > 0 ? (
              <span className="text-[10px] font-semibold text-cyan-400 font-mono">
                +{formatBRL(summary.carryoverAmount)} sobra ant.
              </span>
            ) : summary.previousMonthDeficit && summary.previousMonthDeficit > 0 ? (
              <span className="text-[10px] font-semibold text-rose-400 font-mono">
                -{formatBRL(summary.previousMonthDeficit)} déficit ant.
              </span>
            ) : null}
          </div>

          {summary.cushionAmount && summary.cushionAmount > 0 ? (
            <button
              type="button"
              onClick={() => {
                const prev = summary.pendingClosureMonth || shiftMonth(month, -1)
                if (window.confirm(`Deseja desfazer o fechamento de ${monthLabel(prev)} e devolver ${formatBRL(summary.cushionAmount || 0)} ao Saldo Livre Real?`)) {
                  deleteMonthClosure(prev)
                }
              }}
              title="Clique para desfazer fechamento do mês anterior"
              className="flex items-center gap-1 text-[10px] text-amber-400 hover:text-amber-300 transition-colors font-medium mt-1 text-left cursor-pointer"
            >
              <Shield className="size-3 shrink-0" />
              <span className="underline decoration-dotted truncate">Colchão de Proteção: {formatBRL(summary.cushionAmount)}</span>
            </button>
          ) : null}
        </div>

        {/* Rodapé Integrado: Disponível por Dia */}
        <div className="mt-3 pt-2 border-t border-white/[0.08] flex items-center justify-between text-xs min-w-0 gap-2">
          <div className="flex items-center gap-1.5 min-w-0 truncate">
            <div className="p-1 rounded-md bg-cyan-500/15 text-cyan-300 shrink-0">
              <Shield className="size-3" />
            </div>
            <span className="text-[11px] font-medium text-slate-300 truncate">
              Disponível por Dia:
            </span>
            <span className="text-[10px] text-slate-400 truncate hidden sm:inline">
              {summary.monthState === 'past' ? '(Encerrado)' : `(restam ${summary.daysRemaining}d)`}
            </span>
          </div>
          <span className="font-bold font-mono text-cyan-300 text-xs sm:text-sm shrink-0">
            {summary.dailyLimit != null && summary.dailyLimit > 0 ? `${formatBRL(summary.dailyLimit)}/dia` : '—'}
          </span>
        </div>
      </div>

      {/* 2. KPI 1: Consumo da Renda */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm">
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <div
              className={`p-1.5 rounded-lg shrink-0 ${
                consumptionColor === 'rose'
                  ? 'bg-rose-500/15 text-rose-300'
                  : consumptionColor === 'amber'
                    ? 'bg-amber-500/15 text-amber-300'
                    : 'bg-emerald-500/15 text-emerald-300'
              }`}
            >
              <PieChart className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 truncate">
              Consumo da Renda
            </span>
          </div>
        </div>

        <div className="my-0.5">
          <span
            className={`text-base sm:text-lg font-bold tracking-tight font-mono ${
              consumptionColor === 'rose'
                ? 'text-rose-400'
                : consumptionColor === 'amber'
                  ? 'text-amber-400'
                  : 'text-emerald-400'
            }`}
          >
            {consumptionPct}% Consumido
          </span>
          {/* Barra de progresso arredondada com fundo translúcido (15% de opacidade) */}
          <div className="w-full bg-white/15 h-1.5 rounded-full overflow-hidden mt-1.5">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                consumptionColor === 'rose'
                  ? 'bg-rose-500'
                  : consumptionColor === 'amber'
                    ? 'bg-amber-500'
                    : 'bg-emerald-500'
              }`}
              style={{ width: `${Math.min(consumptionPct, 100)}%` }}
            />
          </div>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 min-w-0 gap-1 border-t border-white/[0.04]">
          <span className="truncate min-w-0 text-slate-400">
            Gasto: <strong className="text-slate-300 font-semibold">{formatBRL(summary.paid)}</strong>
          </span>
          <span className="truncate min-w-0 text-slate-400 hidden sm:inline">
            de {formatBRL(summary.income)}
          </span>
        </div>
      </div>

      {/* 3. KPI 2: Radar 7 Dias */}
      <div
        onClick={onOpenReceivables}
        className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm cursor-pointer group"
      >
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <div
              className={`p-1.5 rounded-lg shrink-0 ${
                radarData.isUrgent
                  ? 'bg-rose-500/15 text-rose-300'
                  : radarData.total > 0
                    ? 'bg-amber-500/15 text-amber-300'
                    : 'bg-emerald-500/15 text-emerald-300'
              }`}
            >
              <CalendarClock className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 truncate">
              Radar 7 Dias
            </span>
          </div>
          <Badge
            variant="outline"
            className={`text-[9px] px-1.5 py-0 border-0 shrink-0 ${
              radarData.isUrgent
                ? 'bg-rose-500/15 text-rose-300'
                : radarData.total > 0
                  ? 'bg-amber-500/15 text-amber-300'
                  : 'bg-emerald-500/15 text-emerald-300'
            }`}
          >
            {radarData.urgencyBadge}
          </Badge>
        </div>

        <div className="flex items-baseline gap-1 my-0.5">
          <span
            className={`text-base sm:text-lg font-bold tracking-tight font-mono ${
              radarData.isUrgent ? 'text-rose-400' : radarData.total > 0 ? 'text-amber-300' : 'text-emerald-400'
            }`}
          >
            {formatBRL(radarData.total)}
          </span>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 min-w-0 gap-1 border-t border-white/[0.04]">
          <span className="truncate min-w-0 text-slate-400">
            {radarData.subtitle}
          </span>
        </div>
      </div>

      {/* 4. KPI 3: Termômetro Diário */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm">
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <div
              className={`p-1.5 rounded-lg shrink-0 ${
                dailyGaugeData.isEconomy ? 'bg-emerald-500/15 text-emerald-300' : 'bg-amber-500/15 text-amber-300'
              }`}
            >
              <Gauge className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 truncate">
              Termômetro
            </span>
          </div>
          {dailyGaugeData.dailyLimit != null && dailyGaugeData.dailyLimit > 0 && (
            <Badge
              variant="outline"
              className={`text-[9px] px-1.5 py-0 border-0 shrink-0 ${
                dailyGaugeData.isEconomy ? 'bg-emerald-500/15 text-emerald-300' : 'bg-amber-500/15 text-amber-300'
              }`}
            >
              {dailyGaugeData.isEconomy ? 'Econômico' : 'Acima cota'}
            </Badge>
          )}
        </div>

        <div className="my-0.5">
          <span
            className={`text-base sm:text-lg font-bold tracking-tight font-mono ${
              dailyGaugeData.isEconomy ? 'text-emerald-400' : 'text-amber-400'
            }`}
          >
            {dailyGaugeData.performanceText}
          </span>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 min-w-0 gap-1 border-t border-white/[0.04]">
          <span className="truncate min-w-0 text-slate-400">
            Gasto hoje: <strong className="text-slate-300 font-semibold">{formatBRL(dailyGaugeData.todaySpent)}</strong>
          </span>
          {dailyGaugeData.dailyLimit != null && (
            <span className="text-[9px] text-slate-500 shrink-0 hidden sm:inline">
              Meta: {formatBRL(dailyGaugeData.dailyLimit)}
            </span>
          )}
        </div>
      </div>

      {/* 5. KPI 4: Saúde de Investimentos */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm">
        <div className="flex items-center justify-between gap-1 mb-1">
          <div className="flex items-center gap-1.5 min-w-0">
            <div
              className={`p-1.5 rounded-lg shrink-0 ${
                investmentData.isTargetReached ? 'bg-emerald-500/15 text-emerald-300' : 'bg-cyan-500/15 text-cyan-300'
              }`}
            >
              <TrendingUp className="size-3.5" />
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 truncate">
              Investimentos
            </span>
          </div>
          <Badge
            variant="outline"
            className={`text-[9px] px-1.5 py-0 border-0 shrink-0 ${
              investmentData.isTargetReached ? 'bg-emerald-500/15 text-emerald-300' : 'bg-cyan-500/15 text-cyan-300'
            }`}
          >
            {investmentData.isTargetReached ? 'Meta OK' : 'Meta 20%'}
          </Badge>
        </div>

        <div className="flex items-center justify-between gap-2 my-0.5">
          <div className="min-w-0 truncate">
            <span
              className={`text-base sm:text-lg font-bold tracking-tight font-mono ${
                investmentData.isTargetReached ? 'text-emerald-400' : 'text-cyan-300'
              }`}
            >
              {investmentData.incomePercentage}% da Renda
            </span>
          </div>
          {/* Mini circular progress indicator */}
          <div className="relative size-7 shrink-0 flex items-center justify-center">
            <svg className="size-full -rotate-90" viewBox="0 0 32 32">
              <circle
                cx="16"
                cy="16"
                r="12"
                className="stroke-white/15"
                strokeWidth="3"
                fill="none"
              />
              <circle
                cx="16"
                cy="16"
                r="12"
                className={`${
                  investmentData.isTargetReached ? 'stroke-emerald-400' : 'stroke-cyan-400'
                } transition-all duration-500`}
                strokeWidth="3"
                strokeDasharray={2 * Math.PI * 12}
                strokeDashoffset={2 * Math.PI * 12 * (1 - investmentData.progressRatio)}
                strokeLinecap="round"
                fill="none"
              />
            </svg>
          </div>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-0.5 min-w-0 gap-1 border-t border-white/[0.04]">
          <span className="truncate min-w-0 text-slate-400">
            Aportado: <strong className="text-slate-300 font-semibold">{formatBRL(investmentData.totalInvested)}</strong>
          </span>
          <span className="text-[9px] text-slate-500 shrink-0 hidden sm:inline">
            Regra 50/30/20
          </span>
        </div>
      </div>
    </div>
  )
}
