import { useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { NumberTicker } from '@/components/ui/NumberTicker'
import { Skeleton } from '@/components/ui/skeleton'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, monthLabel, shiftMonth, todayKey, nextWeekKey, round2 } from '@/lib/utils'
import { buildCardInvoice, invoiceDates } from '@/lib/invoices'
import {
  impactFor,
  calculateFinancialHealthScore,
  resolveEmergencyReserveBalance,
  computeIncomeConsumption,
} from '@/lib/finance'
import {
  Wallet,
  TrendingUp,
  PieChart,
  CalendarClock,
  ShoppingBag,
  RotateCcw,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'

interface KpiGridProps {
  onOpenReceivables?: () => void
  onOpenInvestments?: () => void
  onOpenFinancialHealth?: () => void
}

export function KpiGrid({
  onOpenReceivables,
  onOpenInvestments,
  onOpenFinancialHealth,
}: KpiGridProps) {
  const {
    summary,
    month,
    deleteMonthClosure,
    transactions,
    cards,
    me,
    profile,
    categoryBudgetReport,
    isLoading,
  } = useFinance()

  // 1. Consumo da Renda (Considera receitas do mês + sobra do mês anterior - déficit anterior)
  const incomeConsumption = useMemo(() => computeIncomeConsumption(summary), [summary])
  const consumptionPct = incomeConsumption.consumptionPct
  const committedPct = incomeConsumption.committedPct
  const consumptionColor = incomeConsumption.statusColor
  const consumptionBadge = incomeConsumption.statusBadge

  // Formatação da data final da janela de 7 dias
  const formattedEndDate = useMemo(() => nextWeekKey(7).label, [])

  // 2. Radar 7 Dias (Sem repetições triplas de 'sem contas')
  const radarData = useMemo(() => {
    if (!transactions) return { total: 0, count: 0, urgencyBadge: 'Sem pendências', subtitle: `Até ${formattedEndDate} sem contas`, isUrgent: false }

    const today = todayKey()
    const nextWeekStr = nextWeekKey(7).dateStr

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
      ? 'Sem pendências'
      : minDays !== null && minDays <= 1
        ? 'Vence em breve!'
        : `${count} a vencer`
    const subtitle = count === 0
      ? `Até ${formattedEndDate} sem contas`
      : `Vencendo até ${formattedEndDate}`

    return { total: round2(total), count, urgencyBadge, subtitle, isUrgent }
  }, [transactions, cards, month, me?.uid, formattedEndDate])

  // 3. Maior Gasto (Top Categoria do Mês - Paridade 100% Web & Android)
  const topCategoryData = useMemo(() => {
    if (!summary || !summary.byCategory || summary.byCategory.length === 0) {
      return {
        name: 'Sem despesas',
        amount: 0,
        percentage: 0,
        badgeText: 'Sem gastos',
        subtitle: 'Nenhum gasto registrado',
      }
    }
    const nonInvestmentCategories = summary.byCategory.filter(
      (c) => c.amount > 0 && !c.category.toLowerCase().includes('investimento'),
    )
    const validCategories =
      nonInvestmentCategories.length > 0
        ? nonInvestmentCategories
        : summary.byCategory.filter((c) => c.amount > 0)
    const top = validCategories[0]
    if (!top) {
      return {
        name: 'Sem despesas',
        amount: 0,
        percentage: 0,
        badgeText: 'Sem gastos',
        subtitle: 'Nenhum gasto registrado',
      }
    }
    const totalExpenses = summary.paid + summary.committed
    const catPct = totalExpenses > 0 ? Math.round((top.amount / totalExpenses) * 100) : 0
    return {
      name: top.category,
      amount: top.amount,
      percentage: catPct,
      badgeText: `${catPct}% do total`,
      subtitle: 'Principal despesa do mês',
    }
  }, [summary])

  // 4. Saúde de Investimentos (Sem repetições de Meta 20% vs 0% vs R$ 0)
  const investmentData = useMemo(() => {
    const totalInvested = summary?.totalInvested || 0
    const income = summary?.income || 0
    const incomePercentage = income > 0 ? Math.round((totalInvested / income) * 100) : 0
    const isTargetReached = incomePercentage >= 20
    const progressRatio = Math.min(1, incomePercentage / 20)
    const badgeText = totalInvested <= 0
      ? 'Pendente'
      : isTargetReached
        ? 'Meta OK'
        : `${incomePercentage}% / 20%`

    return { totalInvested, incomePercentage, isTargetReached, progressRatio, badgeText }
  }, [summary?.totalInvested, summary?.income])

  // 5. Termômetro de Saúde Financeira (Score 0 a 100) integrado com os 4 pilares
  const healthScore = useMemo(() => {
    const reserveBalance = resolveEmergencyReserveBalance(profile, summary)
    return calculateFinancialHealthScore(summary, reserveBalance, categoryBudgetReport)
  }, [profile, summary, categoryBudgetReport])

  if (!summary || isLoading) {
    return <KpiGridSkeleton />
  }

  const isPositiveBalance = summary.freeBalance >= 0
  const incomeRemainingPct = incomeConsumption.remainingPct
  const pctSign = incomeRemainingPct > 0 ? `+${incomeRemainingPct}%` : `${incomeRemainingPct}%`

  return (
    <div className="grid grid-cols-2 lg:grid-cols-12 gap-2.5 sm:gap-3">
      {/* 1. HERO COMPACT: Saldo Livre Real */}
      <div
        className={`col-span-2 lg:col-span-4 rounded-2xl relative overflow-hidden transition-all duration-300 p-3 sm:p-3.5 flex flex-col justify-between ${isPositiveBalance
          ? 'bg-gradient-to-br from-emerald-950/45 via-emerald-950/20 to-slate-900 border border-emerald-500/25 shadow-[0_8px_30px_-6px_rgba(16,185,129,0.25)] hover:shadow-[0_8px_34px_-4px_rgba(16,185,129,0.35)]'
          : 'bg-gradient-to-br from-rose-950/45 via-rose-950/20 to-slate-900 border border-rose-500/25 shadow-[0_8px_30px_-6px_rgba(244,63,94,0.25)] hover:shadow-[0_8px_34px_-4px_rgba(244,63,94,0.35)]'
          }`}
      >
        <div>
          {/* Top Row: Ícone + Título e Badge de % Livre */}
          <div className="flex items-center justify-between gap-1 mb-1.5">
            <div className="flex items-center gap-1.5 min-w-0">
              <div
                className={`p-1.5 rounded-lg shrink-0 ${isPositiveBalance
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
              className={`text-[9px] px-1.5 py-0 font-mono border-0 shrink-0 whitespace-nowrap ${incomeRemainingPct > 0
                ? 'bg-emerald-500/15 text-emerald-300'
                : 'bg-rose-500/15 text-rose-300'
                }`}
            >
              {pctSign} livre
            </Badge>
          </div>

          {/* Saldo Principal com Number Ticker */}
          <div className="my-1 min-w-0 overflow-hidden">
            <NumberTicker
              value={summary.freeBalance}
              className={`text-2xl sm:text-3xl font-extrabold tracking-tight drop-shadow-xs font-mono truncate block ${isPositiveBalance ? 'text-emerald-400' : 'text-rose-400'
                }`}
            />
          </div>

          {/* Contexto de Receitas e Despesas (e Sobra/Déficit) */}
          <div className="flex items-center gap-1.5 flex-wrap text-[11px] text-slate-400 min-w-0">
            <span className="whitespace-nowrap">Receitas: <strong className="text-slate-300 font-semibold">{formatBRL(summary.income)}</strong></span>
            <span className="whitespace-nowrap">• Despesas: <strong className="text-slate-300 font-semibold">{formatBRL(summary.paid + summary.committed)}</strong></span>
            {summary.carryoverAmount && summary.carryoverAmount > 0 ? (
              <span className="font-semibold text-cyan-400 font-mono whitespace-nowrap">
                • +{formatBRL(summary.carryoverAmount)} sobra
              </span>
            ) : summary.previousMonthDeficit && summary.previousMonthDeficit > 0 ? (
              <span className="font-semibold text-rose-400 font-mono whitespace-nowrap">
                • -{formatBRL(summary.previousMonthDeficit)} déficit
              </span>
            ) : null}
          </div>

          {/* Fechamento do Mês Anterior (Badge permanente para Desfazer a qualquer momento) */}
          {summary.previousMonthClosure || (summary.carryoverAmount && summary.carryoverAmount > 0) ? (
            <button
              type="button"
              onClick={() => {
                const prev = summary.previousMonthClosure?.closedMonth || summary.pendingClosureMonth || shiftMonth(month, -1)
                const prevLabel = monthLabel(prev)
                const carry = summary.previousMonthClosure?.carryoverToNextMonth ?? summary.carryoverAmount ?? 0
                const invest = summary.previousMonthClosure?.investedAmount ?? 0
                const sub = summary.previousMonthClosure?.investmentSubcategory || 'Investimentos'
                const detail = [
                  carry > 0 ? `+${formatBRL(carry)} na Conta` : '',
                  invest > 0 ? `+${formatBRL(invest)} em ${sub}` : ''
                ].filter(Boolean).join(' e ')

                if (window.confirm(`Deseja desfazer o fechamento de ${prevLabel}${detail ? ` (${detail})` : ''} e reabrir o mês para edição?`)) {
                  deleteMonthClosure(prev)
                }
              }}
              title="Clique para desfazer fechamento do mês anterior"
              className="flex items-center gap-1.5 text-[11px] text-cyan-400 hover:text-cyan-300 transition-colors font-medium mt-1.5 text-left cursor-pointer group min-w-0 max-w-full"
            >
              <RotateCcw className="size-3 shrink-0 group-hover:-rotate-45 transition-transform" />
              <span className="underline decoration-dotted truncate min-w-0">
                Fechamento de {monthLabel(summary.previousMonthClosure?.closedMonth || shiftMonth(month, -1))}: {summary.carryoverAmount && summary.carryoverAmount > 0 ? `+${formatBRL(summary.carryoverAmount)} na conta` : 'Desfazer'}
              </span>
            </button>
          ) : null}
        </div>
      </div>

      {/* 2. KPI 1: Consumo da Renda */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm">
        <div>
          {/* Top Row: Ícone à esquerda + Badge semântico à direita (sem duplicar %) */}
          <div className="flex items-center justify-between gap-1 mb-1.5">
            <div
              className={`p-1.5 rounded-lg shrink-0 ${consumptionColor === 'rose'
                ? 'bg-rose-500/15 text-rose-300'
                : consumptionColor === 'amber'
                  ? 'bg-amber-500/15 text-amber-300'
                  : 'bg-emerald-500/15 text-emerald-300'
                }`}
            >
              <PieChart className="size-3.5" />
            </div>

            <Badge
              variant="outline"
              className={`text-[9px] px-1.5 py-0 border-0 shrink-0 whitespace-nowrap ${consumptionColor === 'rose'
                ? 'bg-rose-500/15 text-rose-300'
                : consumptionColor === 'amber'
                  ? 'bg-amber-500/15 text-amber-300'
                  : 'bg-emerald-500/15 text-emerald-300'
                }`}
            >
              {consumptionBadge}
            </Badge>
          </div>

          {/* Título com largura total */}
          <div className="text-[11px] font-semibold text-slate-400 mb-0.5 truncate">
            Consumo da Renda
          </div>

          {/* Valor Principal */}
          <div className="text-base sm:text-lg font-extrabold tracking-tight font-mono text-slate-100 truncate">
            {consumptionPct}% Consumido
          </div>

          {/* Barra de progresso arredondada com fundo translúcido (15% de opacidade) */}
          <div className="w-full bg-white/15 h-1.5 rounded-full overflow-hidden mt-1.5">
            <div
              className={`h-full rounded-full transition-all duration-500 ${consumptionColor === 'rose'
                ? 'bg-rose-500'
                : consumptionColor === 'amber'
                  ? 'bg-amber-500'
                  : 'bg-emerald-500'
                }`}
              style={{ width: `${Math.min(consumptionPct, 100)}%` }}
            />
          </div>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-1.5 min-w-0 gap-1 border-t border-white/[0.04]">
          <span className="truncate min-w-0 text-slate-400 whitespace-nowrap">
            Pago: <strong className="text-slate-300 font-semibold">{formatBRL(summary.paid)}</strong>
          </span>
          <span className="truncate min-w-0 text-slate-500 font-mono whitespace-nowrap shrink-0">
            Comprom.: {committedPct}%
          </span>
        </div>
      </div>

      {/* 3. KPI 2: Radar 7 Dias */}
      <div
        onClick={onOpenReceivables}
        className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm cursor-pointer group min-w-0"
      >
        <div>
          {/* Top Row: Ícone à esquerda + Badge à direita */}
          <div className="flex items-center justify-between gap-1 mb-1.5">
            <div
              className={`p-1.5 rounded-lg shrink-0 ${radarData.isUrgent
                ? 'bg-rose-500/15 text-rose-300'
                : radarData.total > 0
                  ? 'bg-amber-500/15 text-amber-300'
                  : 'bg-emerald-500/15 text-emerald-300'
                }`}
            >
              <CalendarClock className="size-3.5" />
            </div>

            <Badge
              variant="outline"
              className={`text-[9px] px-1.5 py-0 border-0 shrink-0 whitespace-nowrap ${radarData.isUrgent
                ? 'bg-rose-500/15 text-rose-300'
                : radarData.total > 0
                  ? 'bg-amber-500/15 text-amber-300'
                  : 'bg-emerald-500/15 text-emerald-300'
                }`}
            >
              {radarData.urgencyBadge}
            </Badge>
          </div>

          {/* Título com largura total */}
          <div className="text-[11px] font-semibold text-slate-400 mb-0.5 truncate">
            Radar 7 Dias
          </div>

          {/* Valor Principal com NumberTicker */}
          <div className="text-base sm:text-lg font-extrabold tracking-tight font-mono text-slate-100 truncate">
            <NumberTicker value={radarData.total} />
          </div>

          <div className={`text-[10px] font-medium mt-1 truncate ${radarData.isUrgent ? 'text-rose-400' : radarData.total > 0 ? 'text-amber-400' : 'text-emerald-400'}`}>
            {radarData.count === 0 ? 'Próx. vencimento livre' : `${radarData.count} conta${radarData.count > 1 ? 's' : ''} a vencer`}
          </div>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-1.5 min-w-0 gap-1 border-t border-white/[0.04]">
          <span className="truncate min-w-0 text-slate-400">
            {radarData.subtitle}
          </span>
        </div>
      </div>

      {/* 4. KPI 3: Maior Gasto (Top Categoria do Mês) */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm min-w-0">
        <div>
          {/* Top Row: Ícone à esquerda + Badge à direita */}
          <div className="flex items-center justify-between gap-1 mb-1.5">
            <div className="p-1.5 rounded-lg shrink-0 bg-purple-500/15 text-purple-300">
              <ShoppingBag className="size-3.5" />
            </div>

            <Badge
              variant="outline"
              className="text-[9px] px-1.5 py-0 border-0 shrink-0 bg-purple-500/15 text-purple-300 font-mono whitespace-nowrap"
            >
              {topCategoryData.badgeText}
            </Badge>
          </div>

          {/* Título com largura total */}
          <div className="text-[11px] font-semibold text-slate-400 mb-0.5 truncate">
            Maior Gasto
          </div>

          {/* Valor Principal: valor financeiro da maior categoria */}
          <div className="text-base sm:text-lg font-extrabold tracking-tight font-mono text-purple-400 truncate">
            <NumberTicker value={topCategoryData.amount} />
          </div>

          <div className="text-[11px] text-purple-300 font-semibold mt-1 truncate">
            {topCategoryData.name}
          </div>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-1.5 min-w-0 gap-1 border-t border-white/[0.04]">
          <span className="truncate min-w-0 text-slate-400">
            {topCategoryData.subtitle}
          </span>
        </div>
      </div>

      {/* 5. KPI 4: Saúde de Investimentos */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm min-w-0">
        <div>
          {/* Top Row: Ícone à esquerda + Badge à direita */}
          <div className="flex items-center justify-between gap-1 mb-1.5">
            <div
              className={`p-1.5 rounded-lg shrink-0 ${investmentData.isTargetReached ? 'bg-emerald-500/15 text-emerald-300' : 'bg-cyan-500/15 text-cyan-300'
                }`}
            >
              <TrendingUp className="size-3.5" />
            </div>

            <Badge
              variant="outline"
              className={`text-[9px] px-1.5 py-0 border-0 shrink-0 whitespace-nowrap ${investmentData.isTargetReached ? 'bg-emerald-500/15 text-emerald-300' : 'bg-cyan-500/15 text-cyan-300'
                }`}
            >
              {investmentData.badgeText}
            </Badge>
          </div>

          {/* Título com largura total */}
          <div className="text-[11px] font-semibold text-slate-400 mb-0.5 truncate">
            Investimentos
          </div>

          {/* Valor Principal + Mini Circular Progress */}
          <div className="flex items-center justify-between gap-1.5 min-w-0">
            <span
              className={`text-base sm:text-lg font-extrabold tracking-tight font-mono truncate min-w-0 ${investmentData.isTargetReached ? 'text-emerald-400' : 'text-cyan-300'
                }`}
            >
              {investmentData.incomePercentage}% da Renda
            </span>

            {/* Mini circular progress indicator */}
            <div className="relative size-6 shrink-0 flex items-center justify-center">
              <svg className="size-full -rotate-90" viewBox="0 0 32 32">
                <circle
                  cx="16"
                  cy="16"
                  r="12"
                  className="stroke-white/15"
                  strokeWidth="3.5"
                  fill="none"
                />
                <circle
                  cx="16"
                  cy="16"
                  r="12"
                  className={`${investmentData.isTargetReached ? 'stroke-emerald-400' : 'stroke-cyan-400'
                    } transition-all duration-500`}
                  strokeWidth="3.5"
                  strokeDasharray={2 * Math.PI * 12}
                  strokeDashoffset={2 * Math.PI * 12 * (1 - investmentData.progressRatio)}
                  strokeLinecap="round"
                  fill="none"
                />
              </svg>
            </div>
          </div>

          <div className="text-[10px] text-slate-400 mt-1 truncate">
            Regra 50/30/20 (Meta 20%)
          </div>
        </div>

        <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 mt-1.5 min-w-0 gap-1 border-t border-white/[0.04]">
          <span className="truncate min-w-0 text-slate-400 whitespace-nowrap">
            Aportado: <strong className="text-slate-300 font-semibold">{formatBRL(investmentData.totalInvested)}</strong>
          </span>
        </div>
      </div>

      {/* 6. Termômetro de Saúde Financeira (Score 0 a 100 - 4 Pilares) */}
      <div
        onClick={onOpenFinancialHealth ?? onOpenInvestments}
        title="Clique para abrir o diagnóstico completo dos 4 pilares de Saúde Financeira"
        className="col-span-2 lg:col-span-12 rounded-2xl p-2.5 sm:p-3 bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-emerald-500/30 transition-all shadow-sm cursor-pointer group min-w-0"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 sm:gap-4 min-w-0">
          {/* Lado Esquerdo: Ícone + Título + Score Circular e Classificação */}
          <div className="flex items-center gap-3 min-w-0 flex-1">
            {/* Medidor Circular Compacto com Score */}
            <div className="relative size-10 sm:size-11 shrink-0 flex items-center justify-center">
              <svg className="size-full -rotate-90" viewBox="0 0 36 36">
                <circle
                  cx="18"
                  cy="18"
                  r="14"
                  className="stroke-white/10"
                  strokeWidth="3.5"
                  fill="none"
                />
                <circle
                  cx="18"
                  cy="18"
                  r="14"
                  className={`${
                    healthScore.classification === 'excellent'
                      ? 'stroke-emerald-400'
                      : healthScore.classification === 'healthy'
                        ? 'stroke-cyan-400'
                        : healthScore.classification === 'warning'
                          ? 'stroke-amber-400'
                          : 'stroke-rose-400'
                  } transition-all duration-700`}
                  strokeWidth="3.5"
                  strokeDasharray={2 * Math.PI * 14}
                  strokeDashoffset={2 * Math.PI * 14 * (1 - healthScore.score / 100)}
                  strokeLinecap="round"
                  fill="none"
                />
              </svg>
              <div className="absolute inset-0 flex items-center justify-center font-mono font-extrabold text-xs sm:text-sm text-slate-100">
                {healthScore.score}
              </div>
            </div>

            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 flex-wrap min-w-0">
                <span className="text-[11px] font-bold text-slate-300 uppercase tracking-wider truncate flex items-center gap-1.5 group-hover:text-emerald-300 transition-colors">
                  <ShieldCheck className="size-3.5 text-emerald-400 inline shrink-0" />
                  Saúde Financeira
                </span>
                <Badge
                  variant="outline"
                  className={`text-[9px] px-1.5 py-0 border-0 font-semibold shrink-0 whitespace-nowrap ${
                    healthScore.classification === 'excellent'
                      ? 'bg-emerald-500/15 text-emerald-300'
                      : healthScore.classification === 'healthy'
                        ? 'bg-cyan-500/15 text-cyan-300'
                        : healthScore.classification === 'warning'
                          ? 'bg-amber-500/15 text-amber-300'
                          : 'bg-rose-500/15 text-rose-300'
                  }`}
                >
                  {healthScore.label}
                </Badge>
                <span className="text-[9px] text-emerald-400/80 hidden md:inline-flex items-center gap-1 font-medium whitespace-nowrap shrink-0">
                  <Sparkles className="size-2.5" />
                  Ver Diagnóstico & Dicas IA
                </span>
              </div>
              <p className="text-[10px] text-slate-400 line-clamp-2 max-w-xl mt-0.5">
                {healthScore.actionableTips?.[0] || healthScore.description}
              </p>
            </div>
          </div>

          {/* Lado Direito: Os 4 Pilares Fundamentais (Soma Exata: 100 pts) */}
          <div className="grid grid-cols-4 gap-2 sm:flex sm:items-center sm:gap-4 text-center sm:text-right shrink-0 pt-1.5 sm:pt-0 border-t sm:border-t-0 border-white/[0.04]">
            <div className="flex flex-col sm:items-end min-w-0">
              <span className="text-[9px] uppercase tracking-wider text-slate-500 truncate">Poupança</span>
              <span className="text-[11px] font-mono font-bold text-slate-200 whitespace-nowrap">
                {healthScore.savingsScore}<span className="text-slate-500 text-[9px]">/30</span>
                <span className="text-[9px] text-slate-400 ml-1 hidden sm:inline">({healthScore.savingsRatePercentage}%)</span>
              </span>
            </div>

            <div className="flex flex-col sm:items-end min-w-0">
              <span className="text-[9px] uppercase tracking-wider text-slate-500 truncate">Custo Vida</span>
              <span className="text-[11px] font-mono font-bold text-slate-200 whitespace-nowrap">
                {healthScore.spendingLimitScore}<span className="text-slate-500 text-[9px]">/30</span>
                <span className="text-[9px] text-slate-400 ml-1 hidden sm:inline">({healthScore.spendingRatioPercentage}%)</span>
              </span>
            </div>

            <div className="flex flex-col sm:items-end min-w-0">
              <span className="text-[9px] uppercase tracking-wider text-slate-500 truncate">Reserva</span>
              <span className="text-[11px] font-mono font-bold text-slate-200 whitespace-nowrap">
                {healthScore.emergencyReserveScore}<span className="text-slate-500 text-[9px]">/25</span>
                <span className="text-[9px] text-slate-400 ml-1 hidden sm:inline">({healthScore.emergencyCoverageMonths}m)</span>
              </span>
            </div>

            <div className="flex flex-col sm:items-end min-w-0">
              <span className="text-[9px] uppercase tracking-wider text-slate-500 truncate">Envelopes</span>
              <span
                className={`text-[11px] font-mono font-bold whitespace-nowrap ${
                  (healthScore.budgetOverspentCount ?? 0) > 0
                    ? 'text-rose-400'
                    : (healthScore.budgetConfiguredCount ?? 0) > 0
                      ? 'text-emerald-400'
                      : 'text-slate-400'
                }`}
              >
                {healthScore.envelopeScore}<span className="text-slate-500 text-[9px]">/15</span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/**
 * Esqueleto de carregamento completo do KpiGrid com efeito Shimmer.
 * Preserva as dimensões e proporções exatas da grade para garantir CLS (Cumulative Layout Shift) zero.
 */
export function KpiGridSkeleton() {
  return (
    <div
      role="status"
      aria-label="Carregando indicadores financeiros"
      className="grid grid-cols-2 lg:grid-cols-12 gap-2.5 sm:gap-3 select-none"
    >
      {/* 1. Hero Saldo Livre Skeleton */}
      <div className="col-span-2 lg:col-span-4 rounded-2xl p-3 sm:p-3.5 flex flex-col justify-between bg-slate-900/60 backdrop-blur-md border border-white/[0.08] min-h-[148px]">
        <div>
          <div className="flex items-center justify-between gap-1 mb-2">
            <div className="flex items-center gap-1.5">
              <Skeleton className="size-6 rounded-lg" />
              <Skeleton className="h-3.5 w-24" />
            </div>
            <Skeleton className="h-4 w-16 rounded-full" />
          </div>
          <Skeleton className="h-8 w-44 mt-1 mb-2.5" />
          <Skeleton className="h-1.5 w-full rounded-full" />
        </div>
        <div className="flex items-center gap-2 mt-2 pt-1 border-t border-white/[0.04]">
          <Skeleton className="h-3 w-28" />
          <Skeleton className="h-3 w-28" />
        </div>
      </div>

      {/* 2. Consumo da Renda Skeleton */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between bg-slate-900/60 backdrop-blur-md border border-white/[0.08] min-h-[148px]">
        <div>
          <div className="flex items-center justify-between gap-1 mb-2">
            <Skeleton className="size-6 rounded-lg" />
            <Skeleton className="h-4 w-12 rounded-full" />
          </div>
          <Skeleton className="h-3 w-24 mb-1.5" />
          <Skeleton className="h-6 w-28 mb-2" />
          <Skeleton className="h-1.5 w-full rounded-full" />
        </div>
        <div className="flex items-center justify-between pt-1 mt-1.5 border-t border-white/[0.04]">
          <Skeleton className="h-2.5 w-16" />
          <Skeleton className="h-2.5 w-12" />
        </div>
      </div>

      {/* 3. Radar 7 Dias Skeleton */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between bg-slate-900/60 backdrop-blur-md border border-white/[0.08] min-h-[148px]">
        <div>
          <div className="flex items-center justify-between gap-1 mb-2">
            <Skeleton className="size-6 rounded-lg" />
            <Skeleton className="h-4 w-14 rounded-full" />
          </div>
          <Skeleton className="h-3 w-20 mb-1.5" />
          <Skeleton className="h-6 w-24 mb-1" />
          <Skeleton className="h-2.5 w-28 mt-1" />
        </div>
        <div className="pt-1 mt-1.5 border-t border-white/[0.04]">
          <Skeleton className="h-2.5 w-24" />
        </div>
      </div>

      {/* 4. Maior Despesa Skeleton */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between bg-slate-900/60 backdrop-blur-md border border-white/[0.08] min-h-[148px]">
        <div>
          <div className="flex items-center justify-between gap-1 mb-2">
            <Skeleton className="size-6 rounded-lg" />
            <Skeleton className="h-4 w-14 rounded-full" />
          </div>
          <Skeleton className="h-3 w-20 mb-1.5" />
          <Skeleton className="h-6 w-24 mb-1" />
          <Skeleton className="h-2.5 w-28 mt-1" />
        </div>
        <div className="pt-1 mt-1.5 border-t border-white/[0.04]">
          <Skeleton className="h-2.5 w-20" />
        </div>
      </div>

      {/* 5. Investimentos Skeleton */}
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between bg-slate-900/60 backdrop-blur-md border border-white/[0.08] min-h-[148px]">
        <div>
          <div className="flex items-center justify-between gap-1 mb-2">
            <Skeleton className="size-6 rounded-lg" />
            <Skeleton className="h-4 w-12 rounded-full" />
          </div>
          <Skeleton className="h-3 w-22 mb-1.5" />
          <div className="flex items-center justify-between">
            <Skeleton className="h-6 w-24" />
            <Skeleton className="size-5 rounded-full" />
          </div>
          <Skeleton className="h-2.5 w-28 mt-1" />
        </div>
        <div className="pt-1 mt-1.5 border-t border-white/[0.04]">
          <Skeleton className="h-2.5 w-24" />
        </div>
      </div>

      {/* 6. Termômetro de Saúde Financeira Skeleton */}
      <div className="col-span-2 lg:col-span-12 rounded-2xl p-2.5 sm:p-3 bg-slate-900/60 backdrop-blur-md border border-white/[0.08] min-h-[58px]">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 sm:gap-4">
          <div className="flex items-center gap-3 flex-1">
            <Skeleton className="size-10 rounded-full shrink-0" />
            <div className="space-y-1.5 flex-1">
              <Skeleton className="h-3.5 w-40" />
              <Skeleton className="h-2.5 w-56" />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Skeleton className="h-7 w-28 rounded-lg" />
            <Skeleton className="h-7 w-20 rounded-lg" />
          </div>
        </div>
      </div>
    </div>
  )
}
