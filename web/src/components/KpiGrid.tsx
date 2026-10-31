import { useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { NumberTicker } from '@/components/ui/NumberTicker'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, monthLabel, shiftMonth, todayKey, round2 } from '@/lib/utils'
import { buildCardInvoice, invoiceDates } from '@/lib/invoices'
import { impactFor } from '@/lib/finance'
import {
  Wallet,
  TrendingUp,
  PieChart,
  CalendarClock,
  ShoppingBag,
  Shield,
} from 'lucide-react'

interface KpiGridProps {
  onOpenReceivables?: () => void
}

export function KpiGrid({ onOpenReceivables }: KpiGridProps) {
  const { summary, month, deleteMonthClosure, transactions, cards, me } = useFinance()

  // 1. Consumo da Renda (Sem repetição do valor da renda que já está no Hero Card)
  const consumptionPct = summary && summary.income > 0
    ? Math.round((summary.paid / summary.income) * 100)
    : 0
  const consumptionColor = consumptionPct > 100 ? 'rose' : consumptionPct > 80 ? 'amber' : 'emerald'
  const consumptionBadge = consumptionPct > 100 ? 'Alerta' : consumptionPct > 80 ? 'Atenção' : 'No Limite'

  // Formatação da data final da janela de 7 dias
  const formattedEndDate = useMemo(() => {
    const now = new Date()
    const nextWeekDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 7)
    return `${String(nextWeekDate.getDate()).padStart(2, '0')}/${String(nextWeekDate.getMonth() + 1).padStart(2, '0')}`
  }, [])

  // 2. Radar 7 Dias (Sem repetições triplas de 'sem contas')
  const radarData = useMemo(() => {
    if (!transactions) return { total: 0, count: 0, urgencyBadge: 'Sem pendências', subtitle: `Até ${formattedEndDate} sem contas`, isUrgent: false }

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
      ? 'Sem pendências'
      : minDays !== null && minDays <= 1
        ? 'Vence em breve!'
        : `${count} a vencer`
    const subtitle = count === 0
      ? `Até ${formattedEndDate} sem contas`
      : `Vencendo até ${formattedEndDate}`

    return { total: round2(total), count, urgencyBadge, subtitle, isUrgent }
  }, [transactions, cards, month, me?.uid, formattedEndDate])

  // 3. Maior Gasto (Top Categoria do Mês - Substitui o Termômetro Diário)
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
    const validCategories = summary.byCategory.filter((c) => c.amount > 0)
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
  }, [summary?.byCategory, summary?.paid, summary?.committed])

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

  if (!summary) return null

  const isPositiveBalance = summary.freeBalance >= 0
  const incomeRemainingPct = summary.income > 0 ? Math.round((summary.freeBalance / summary.income) * 100) : 0
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
              className={`text-[9px] px-1.5 py-0 font-mono border-0 shrink-0 ${incomeRemainingPct > 0
                ? 'bg-emerald-500/15 text-emerald-300'
                : 'bg-rose-500/15 text-rose-300'
                }`}
            >
              {pctSign} livre
            </Badge>
          </div>

          {/* Saldo Principal com Number Ticker */}
          <div className="my-1">
            <NumberTicker
              value={summary.freeBalance}
              className={`text-2xl sm:text-3xl font-extrabold tracking-tight drop-shadow-xs font-mono ${isPositiveBalance ? 'text-emerald-400' : 'text-rose-400'
                }`}
            />
          </div>

          {/* Contexto de Receitas e Despesas (e Sobra/Déficit) */}
          <div className="flex items-center gap-1.5 flex-wrap text-[11px] text-slate-400">
            <span>Receitas: <strong className="text-slate-300 font-semibold">{formatBRL(summary.income)}</strong></span>
            <span>• Despesas: <strong className="text-slate-300 font-semibold">{formatBRL(summary.paid + summary.committed)}</strong></span>
            {summary.carryoverAmount && summary.carryoverAmount > 0 ? (
              <span className="font-semibold text-cyan-400 font-mono">
                • +{formatBRL(summary.carryoverAmount)} sobra
              </span>
            ) : summary.previousMonthDeficit && summary.previousMonthDeficit > 0 ? (
              <span className="font-semibold text-rose-400 font-mono">
                • -{formatBRL(summary.previousMonthDeficit)} déficit
              </span>
            ) : null}
          </div>

          {/* Colchão de Proteção (se houver) */}
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
              className="flex items-center gap-1 text-[10px] text-amber-400 hover:text-amber-300 transition-colors font-medium mt-1.5 text-left cursor-pointer"
            >
              <Shield className="size-3 shrink-0" />
              <span className="underline decoration-dotted truncate">Colchão de Proteção: {formatBRL(summary.cushionAmount)}</span>
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
              className={`text-[9px] px-1.5 py-0 border-0 shrink-0 ${consumptionColor === 'rose'
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
          <div className="text-base sm:text-lg font-extrabold tracking-tight font-mono text-slate-100">
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
          <span className="truncate min-w-0 text-slate-400">
            Gasto: <strong className="text-slate-300 font-semibold">{formatBRL(summary.paid)}</strong>
          </span>
          <span className="truncate min-w-0 text-slate-500 hidden sm:inline">
            pagos no mês
          </span>
        </div>
      </div>

      {/* 3. KPI 2: Radar 7 Dias */}
      <div
        onClick={onOpenReceivables}
        className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm cursor-pointer group"
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
              className={`text-[9px] px-1.5 py-0 border-0 shrink-0 ${radarData.isUrgent
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
          <div className="text-base sm:text-lg font-extrabold tracking-tight font-mono text-slate-100">
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
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm">
        <div>
          {/* Top Row: Ícone à esquerda + Badge à direita */}
          <div className="flex items-center justify-between gap-1 mb-1.5">
            <div className="p-1.5 rounded-lg shrink-0 bg-purple-500/15 text-purple-300">
              <ShoppingBag className="size-3.5" />
            </div>

            <Badge
              variant="outline"
              className="text-[9px] px-1.5 py-0 border-0 shrink-0 bg-purple-500/15 text-purple-300 font-mono"
            >
              {topCategoryData.badgeText}
            </Badge>
          </div>

          {/* Título com largura total */}
          <div className="text-[11px] font-semibold text-slate-400 mb-0.5 truncate">
            Maior Gasto
          </div>

          {/* Valor Principal: valor financeiro da maior categoria */}
          <div className="text-base sm:text-lg font-extrabold tracking-tight font-mono text-purple-400">
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
      <div className="col-span-1 lg:col-span-2 rounded-2xl p-2.5 sm:p-3 flex flex-col justify-between transition-all bg-slate-900/60 backdrop-blur-md border border-white/[0.08] hover:border-white/[0.14] shadow-sm">
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
              className={`text-[9px] px-1.5 py-0 border-0 shrink-0 ${investmentData.isTargetReached ? 'bg-emerald-500/15 text-emerald-300' : 'bg-cyan-500/15 text-cyan-300'
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
          <div className="flex items-center justify-between gap-1.5">
            <span
              className={`text-base sm:text-lg font-extrabold tracking-tight font-mono truncate ${investmentData.isTargetReached ? 'text-emerald-400' : 'text-cyan-300'
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
          <span className="truncate min-w-0 text-slate-400">
            Aportado: <strong className="text-slate-300 font-semibold">{formatBRL(investmentData.totalInvested)}</strong>
          </span>
        </div>
      </div>
    </div>
  )
}
