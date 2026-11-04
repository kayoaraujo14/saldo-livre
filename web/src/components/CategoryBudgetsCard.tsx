import { useState, useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, monthLabel, round2 } from '@/lib/utils'
import { suggestCategoryBudgets } from '@/lib/finance'
import { getCategoryDetail } from '@/types'
import { ManageBudgetsModal } from '@/components/modals/ManageBudgetsModal'
import {
  Target,
  Sparkles,
  Sliders,
  ChevronDown,
  ChevronUp,
  MessageSquare,
  PlusCircle,
  TrendingUp,
  Plus,
  Minus,
} from 'lucide-react'

type EnvelopeFilter = 'alerts' | 'variable' | 'all' | 'fixed'

export function CategoryBudgetsCard() {
  const {
    categoryBudgetReport,
    summary,
    transactions,
    me,
    month,
    profile,
    setCategoryBudgets,
  } = useFinance()
  const [modalOpen, setModalOpen] = useState(false)
  const [modalTab, setModalTab] = useState<'budgets' | 'rebalance'>('budgets')
  const [isExpanded, setIsExpanded] = useState(false)
  const [isCardCollapsed, setIsCardCollapsed] = useState(false)
  const [activeFilter, setActiveFilter] = useState<EnvelopeFilter>('all')

  const {
    items,
    totalBudgeted,
    rawTotalBudgeted,
    expectedIncome: reportExpectedIncome,
    carryoverAmount = 0,
    totalAvailableIncome,
    isCappedToIncome,
    totalSpent,
    totalPending,
    totalRealRemaining,
    overallPercentage,
    status,
    topOverspentCategory,
    investmentTarget = 0,
    investmentSpent = 0,
    investmentPercentage = 0,
  } = categoryBudgetReport

  const rawIncome =
    reportExpectedIncome && reportExpectedIncome > 0
      ? reportExpectedIncome
      : summary?.income && summary.income > 0
        ? summary.income
        : (profile?.monthlyIncome || 0)

  const effectiveAvailable = totalAvailableIncome || round2(Math.max(0, rawIncome + carryoverAmount - (summary?.previousMonthDeficit || 0)))

  const filteredItems = useMemo(() => {
    if (activeFilter === 'alerts') {
      return items.filter(
        (i) => i.status !== 'ok' || i.burnRateStatus === 'critical' || i.burnRateStatus === 'fast',
      )
    }
    if (activeFilter === 'variable') {
      return items.filter((i) => !i.isFixedCategory)
    }
    if (activeFilter === 'fixed') {
      return items.filter((i) => Boolean(i.isFixedCategory))
    }
    return items
  }, [items, activeFilter])

  const alertsCount = useMemo(
    () =>
      items.filter(
        (i) => i.status !== 'ok' || i.burnRateStatus === 'critical' || i.burnRateStatus === 'fast',
      ).length,
    [items],
  )

  // Mostra os 4 primeiros ou todos se expandido
  const visibleItems = isExpanded ? filteredItems : filteredItems.slice(0, 4)

  const openConfig = () => {
    setModalTab('budgets')
    setModalOpen(true)
  }

  const openRebalance = () => {
    setModalTab('rebalance')
    setModalOpen(true)
  }

  const handleAskCopilot = () => {
    window.dispatchEvent(
      new CustomEvent('saldo-livre:ask-copilot', {
        detail: {
          prompt:
            'Analise o ritmo dos meus Envelopes Orçamentários deste mês, indique se há categorias em risco e sugira ajustes ou rebalanceamentos.',
        },
      }),
    )
  }

  const handleQuickApplyAiBudgets = async () => {
    const suggestion = suggestCategoryBudgets(
      transactions,
      me?.uid || '',
      month,
      rawIncome,
      carryoverAmount,
      summary?.previousMonthDeficit || 0,
    )
    if (Object.keys(suggestion.suggestedBudgets).length > 0) {
      await setCategoryBudgets(suggestion.suggestedBudgets, month)
    } else {
      openConfig()
    }
  }

  // Quick-edit de teto inline no próprio card sem abrir modal
  const handleQuickAdjustLimit = async (
    category: string,
    delta: number,
    e?: React.MouseEvent,
  ) => {
    if (e) e.stopPropagation()
    const targetItem = items.find((i) => i.category === category)
    if (!targetItem) return
    const currentLimit = targetItem.limit || 0
    const committed = targetItem.committedAmount || 0
    const minFloor = Math.ceil(committed / 10) * 10
    const nextLimit = Math.max(minFloor, Math.max(50, Math.round((currentLimit + delta) / 10) * 10))

    const nextBudgets: Record<string, number> = {}
    for (const it of items) {
      if (it.limit > 0) nextBudgets[it.category] = it.limit
    }
    nextBudgets[category] = nextLimit
    await setCategoryBudgets(nextBudgets, month)
  }

  // Banner roxo SÓ aparece se houver estouro real >= R$ 1,00
  const hasRealOverspend =
    Boolean(topOverspentCategory) &&
    (topOverspentCategory!.committedAmount - topOverspentCategory!.limit) >= 1.00

  return (
    <>
      <div className="glass-card rounded-2xl p-4 border border-white/[0.08] space-y-3.5 bg-slate-900/60 backdrop-blur-md">
        {/* Top Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-emerald-500/15 text-emerald-400 border border-emerald-500/20 shrink-0">
              <Target className="size-4" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h3 className="font-bold text-sm text-white flex items-center gap-1.5">
                  Envelopes Orçamentários
                  <span className="text-xs font-semibold text-emerald-400 font-sans">
                    • {monthLabel(month)}
                  </span>
                </h3>
                <Badge
                  variant="outline"
                  className={`text-[9px] px-1.5 py-0 font-mono border-0 shrink-0 ${
                    status === 'danger'
                      ? 'bg-rose-500/15 text-rose-300'
                      : status === 'warning'
                        ? 'bg-amber-500/15 text-amber-300'
                        : 'bg-emerald-500/15 text-emerald-300'
                  }`}
                >
                  {status === 'danger'
                    ? 'Atenção aos Tetos'
                    : status === 'warning'
                      ? 'Ritmo Acelerado'
                      : 'Dentro da Meta'}
                </Badge>
                {isCappedToIncome && (
                  <Badge
                    variant="outline"
                    title={`Teto original (${formatBRL(rawTotalBudgeted || totalBudgeted)}) calibrado automaticamente para não exceder a capacidade disponível de ${monthLabel(month)} (${formatBRL(effectiveAvailable)}).`}
                    className="text-[9px] px-1.5 py-0 font-mono border-0 shrink-0 bg-cyan-500/15 text-cyan-300"
                  >
                    Calibrado à Renda
                  </Badge>
                )}
              </div>
              <p className="text-[11px] text-slate-400">
                {totalBudgeted > 0 ? (
                  <span>
                    Renda Disponível: <strong className="text-slate-200 font-mono">{formatBRL(effectiveAvailable)}</strong>
                    {' • '}
                    Orçado: <strong className="text-emerald-300 font-mono">{formatBRL(totalBudgeted)}</strong>
                  </span>
                ) : (
                  'Planeje seus gastos por envelope com proteção de contas a vencer e IA nativa.'
                )}
              </p>
            </div>
          </div>

          {/* Botões de Ação */}
          <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-auto flex-wrap">
            {hasRealOverspend && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={openRebalance}
                className="h-7 text-xs bg-purple-500/15 hover:bg-purple-500/25 text-purple-300 border-purple-500/30 px-2.5"
              >
                <Sparkles className="size-3 mr-1 text-purple-400" />
                Compensar IA
              </Button>
            )}
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={handleAskCopilot}
              title="Analisar seus envelopes diretamente na conversa com o Copiloto IA"
              className="h-7 text-xs bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 border-emerald-500/25 px-2.5"
            >
              <MessageSquare className="size-3 mr-1 text-emerald-400" />
              Chat IA
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={openConfig}
              className="h-7 text-xs bg-slate-800/80 hover:bg-slate-700/80 text-slate-300 border-white/[0.08] px-2.5"
            >
              <Sliders className="size-3 mr-1 text-slate-400" />
              {totalBudgeted > 0 ? 'Ajustar Tetos' : 'Configurar'}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => setIsCardCollapsed((prev) => !prev)}
              title={isCardCollapsed ? 'Expandir envelopes detalhados' : 'Recolher envelopes'}
              className="h-7 px-2 text-xs text-slate-400 hover:text-white hover:bg-white/[0.06] cursor-pointer flex items-center gap-1 rounded-lg"
            >
              <span className="hidden sm:inline text-[11px] font-medium">{isCardCollapsed ? 'Ver Todos' : 'Recolher'}</span>
              {isCardCollapsed ? <ChevronDown className="size-3.5" /> : <ChevronUp className="size-3.5" />}
            </Button>
          </div>
        </div>

        {/* Corpo do Card */}
        {!isCardCollapsed && (
          <>
            {/* Resumo Compacto Integrado à Barra Segmentada (Substitui as 4 caixas repetitivas) */}
            {totalBudgeted > 0 && (
              <div className="p-3 rounded-xl bg-slate-950/60 border border-white/[0.06] space-y-2.5">
                <div className="flex flex-wrap items-center justify-between gap-3 text-xs">
                  <div className="flex items-center gap-3 sm:gap-4 flex-wrap">
                    <div>
                      <span className="text-[10px] text-slate-400 block uppercase tracking-wider font-semibold">Orçado Total</span>
                      <strong className="font-mono text-emerald-300 text-xs sm:text-sm">{formatBRL(totalBudgeted)}</strong>
                    </div>
                    <div className="h-5 w-px bg-white/10 hidden sm:block" />
                    <div>
                      <span className="text-[10px] text-slate-400 block uppercase tracking-wider font-semibold">Pago</span>
                      <strong className="font-mono text-slate-100 text-xs sm:text-sm">{formatBRL(totalSpent)}</strong>
                    </div>
                    <div className="h-5 w-px bg-white/10 hidden sm:block" />
                    <div>
                      <span className="text-[10px] text-slate-400 block uppercase tracking-wider font-semibold">A Vencer</span>
                      <strong className="font-mono text-amber-300 text-xs sm:text-sm">+{formatBRL(totalPending)}</strong>
                    </div>
                    <div className="h-5 w-px bg-white/10 hidden sm:block" />
                    <div>
                      <span className="text-[10px] text-slate-400 block uppercase tracking-wider font-semibold">Folga Real</span>
                      <strong className={`font-mono text-xs sm:text-sm ${totalRealRemaining >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                        {totalRealRemaining >= 0 ? `+${formatBRL(totalRealRemaining)}` : `-${formatBRL(Math.abs(totalRealRemaining))}`}
                      </strong>
                    </div>
                  </div>

                  {/* Pill Destacado de Investimentos (50/30/20) - Separado de consumo */}
                  {investmentTarget > 0 && (
                    <div
                      title="Meta de Aporte 50/30/20 (isolada das despesas de consumo dos envelopes)"
                      className="px-2.5 py-1 rounded-xl bg-indigo-500/10 border border-indigo-500/25 flex items-center gap-2 text-[11px] text-indigo-200"
                    >
                      <TrendingUp className="size-3.5 text-indigo-400 shrink-0" />
                      <div className="flex items-baseline gap-1.5 font-mono">
                        <span className="text-slate-400 text-[10px] font-sans">Aporte 20%:</span>
                        <strong className="text-indigo-200">{formatBRL(investmentSpent)}</strong>
                        <span className="text-slate-500 text-[10px]">/ {formatBRL(investmentTarget)}</span>
                        <span className="text-indigo-400 font-bold text-[10px]">({investmentPercentage}%)</span>
                      </div>
                    </div>
                  )}
                </div>

                {/* Barra Geral de Dupla Camada Segmentada */}
                <div className="space-y-1">
                  <div className="flex items-center justify-between text-[10px] text-slate-400 font-mono">
                    <span>
                      {Math.round(overallPercentage)}% consumido {totalPending > 0 && `(+${Math.round((totalPending / totalBudgeted) * 100)}% a vencer)`}
                    </span>
                    <span className={totalRealRemaining >= 0 ? 'text-emerald-400' : 'text-rose-400'}>
                      {totalRealRemaining >= 0 ? `${formatBRL(totalRealRemaining)} folga livre` : `${formatBRL(Math.abs(totalRealRemaining))} excedido`}
                    </span>
                  </div>
                  <div className="relative w-full bg-slate-950 rounded-full h-2.5 overflow-hidden flex border border-white/[0.08]">
                    {/* 1ª Camada Sólida (Pago) */}
                    <div
                      className={`h-full transition-all duration-500 ${
                        totalSpent > totalBudgeted
                          ? 'bg-rose-500'
                          : overallPercentage >= 80
                            ? 'bg-amber-500'
                            : 'bg-emerald-500'
                      }`}
                      style={{ width: `${Math.min(100, Math.max(0, overallPercentage))}%` }}
                      title={`Pago: ${formatBRL(totalSpent)} (${Math.round(overallPercentage)}%)`}
                    />
                    {/* 2ª Camada Hachurada (A Vencer) */}
                    {totalPending > 0 && totalBudgeted > 0 && (
                      <div
                        className="h-full opacity-70 bg-amber-400/60 bg-[repeating-linear-gradient(45deg,transparent,transparent_4px,rgba(245,158,11,0.5)_4px,rgba(245,158,11,0.5)_8px)]"
                        style={{
                          width: `${Math.min(
                            Math.max(0, 100 - overallPercentage),
                            (totalPending / totalBudgeted) * 100,
                          )}%`,
                        }}
                        title={`A Vencer: ${formatBRL(totalPending)} (${Math.round((totalPending / totalBudgeted) * 100)}%)`}
                      />
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* Banner de Rebalanceamento Inteligente de Excesso */}
            {hasRealOverspend && (
              <div className="p-3 rounded-xl bg-purple-950/35 border border-purple-500/35 flex items-center justify-between gap-3 text-xs text-purple-200">
                <div className="flex items-center gap-2.5 min-w-0">
                  <Sparkles className="size-4 text-purple-400 shrink-0" />
                  <span className="truncate">
                    "{topOverspentCategory!.category}" ultrapassou o teto em{' '}
                    <strong className="text-white font-mono">
                      {formatBRL(round2(topOverspentCategory!.committedAmount - topOverspentCategory!.limit))}
                    </strong>
                    . Deseja rebalancear com a folga real de outras categorias?
                  </span>
                </div>
                <button
                  type="button"
                  onClick={openRebalance}
                  className="px-3 py-1 rounded-lg bg-purple-500/25 hover:bg-purple-500/40 text-purple-200 font-bold border border-purple-500/40 shrink-0 transition-colors cursor-pointer text-[11px]"
                >
                  Rebalancear com IA ✨
                </button>
              </div>
            )}

            {/* Abas de Filtros Priorizando "Em Alerta" e "Gastos Variáveis" */}
            {items.length > 0 ? (
              <div className="space-y-2.5">
                {items.length > 2 && (
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      {(
                        [
                          { id: 'alerts', label: `Em Alerta (${alertsCount})` },
                          { id: 'variable', label: 'Gastos Variáveis' },
                          { id: 'all', label: `Todos (${items.length})` },
                          { id: 'fixed', label: 'Fixos Mensais' },
                        ] as const
                      ).map((tab) => (
                        <button
                          key={tab.id}
                          type="button"
                          onClick={() => setActiveFilter(tab.id)}
                          className={`px-2.5 py-1 rounded-lg text-[10px] font-semibold transition-all cursor-pointer ${
                            activeFilter === tab.id
                              ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                              : 'bg-slate-950/50 text-slate-400 border border-white/[0.05] hover:text-slate-200'
                          }`}
                        >
                          {tab.label}
                        </button>
                      ))}
                    </div>
                    <span className="text-[10px] text-slate-500 hidden sm:inline">
                      Ajuste rápido com -/+ R$ 50 ou clique no envelope
                    </span>
                  </div>
                )}

                {/* Grade de Envelopes com Barra de Dupla Camada e Quick-Edit */}
                {visibleItems.length > 0 ? (
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
                    {visibleItems.map((item) => {
                      const detail = getCategoryDetail(item.category)
                      const isOver = item.status === 'danger'
                      const isWarn = item.status === 'warning'
                      const calendarPacePct =
                        item.totalDays > 0
                          ? Math.min(100, Math.round((item.currentDay / item.totalDays) * 100))
                          : 0

                      return (
                        <div
                          key={item.category}
                          onClick={openConfig}
                          role="button"
                          tabIndex={0}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              e.preventDefault()
                              openConfig()
                            }
                          }}
                          title={`Clique para configurar o teto de ${item.category}`}
                          className="p-3 rounded-xl bg-slate-950/60 border border-white/[0.06] hover:border-emerald-500/30 transition-all flex flex-col justify-between gap-2.5 cursor-pointer group"
                        >
                          <div>
                            {/* Top linha: Categoria + Badge Unificado */}
                            <div className="flex items-center justify-between gap-1 mb-1.5">
                              <div className="flex items-center gap-1.5 min-w-0">
                                <div
                                  className="size-2.5 rounded-full shrink-0"
                                  style={{ backgroundColor: detail.color || '#10b981' }}
                                />
                                <span className="font-semibold text-xs text-slate-200 truncate group-hover:text-white transition-colors">
                                  {item.category}
                                </span>
                              </div>

                              {item.limit <= 0 ? (
                                <Badge
                                  variant="outline"
                                  className="text-[8px] px-1.5 py-0 border-0 bg-amber-500/15 text-amber-300 font-mono shrink-0 flex items-center gap-0.5"
                                >
                                  <PlusCircle className="size-2.5" />
                                  Definir Teto
                                </Badge>
                              ) : (
                                <Badge
                                  variant="outline"
                                  className={`text-[8px] px-1.5 py-0 border-0 font-mono shrink-0 ${
                                    item.badgeLabel === 'Estourado' || item.badgeLabel === 'Vai Estourar'
                                      ? 'bg-rose-500/15 text-rose-300'
                                      : item.badgeLabel === 'Quase no Limite'
                                        ? 'bg-orange-500/15 text-orange-300'
                                        : item.badgeLabel === 'Ritmo Rápido'
                                          ? 'bg-amber-500/15 text-amber-300'
                                          : item.badgeLabel === 'Fixo em Dia' || item.badgeLabel === 'Quitado / Dentro do Previsto'
                                            ? 'bg-cyan-500/15 text-cyan-300'
                                            : 'bg-emerald-500/15 text-emerald-300'
                                  }`}
                                >
                                  {item.badgeLabel}
                                </Badge>
                              )}
                            </div>

                            {/* Valores e Quick-Edit (-/+ R$ 50) */}
                            <div className="flex items-center justify-between text-xs pt-0.5">
                              <div>
                                <span className="font-mono font-bold text-white text-xs sm:text-sm">
                                  {formatBRL(item.spentAmount)}
                                </span>
                                {item.pendingAmount > 0 && (
                                  <span className="text-[10px] text-amber-300/90 font-mono ml-1" title="Contas a vencer">
                                    +{formatBRL(item.pendingAmount)}
                                  </span>
                                )}
                                <span className="text-[10px] text-slate-400 font-mono ml-1">
                                  {item.limit > 0 ? `/ ${formatBRL(item.limit)}` : ''}
                                </span>
                              </div>

                              {/* Controles de Quick-Edit */}
                              {item.limit > 0 && (
                                <div
                                  className="flex items-center gap-0.5 opacity-80 group-hover:opacity-100 transition-opacity"
                                  onClick={(e) => e.stopPropagation()}
                                >
                                  <button
                                    type="button"
                                    onClick={(e) => handleQuickAdjustLimit(item.category, -50, e)}
                                    title="Diminuir teto em R$ 50"
                                    className="p-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors cursor-pointer"
                                  >
                                    <Minus className="size-2.5" />
                                  </button>
                                  <button
                                    type="button"
                                    onClick={(e) => handleQuickAdjustLimit(item.category, 50, e)}
                                    title="Aumentar teto em R$ 50"
                                    className="p-1 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors cursor-pointer"
                                  >
                                    <Plus className="size-2.5" />
                                  </button>
                                </div>
                              )}
                            </div>
                          </div>

                          {/* Barra de Progresso de Dupla Camada (Sólida + Hachurada A Vencer) */}
                          <div className="space-y-1">
                            <div className="relative w-full bg-slate-900 rounded-full h-2 overflow-hidden flex border border-white/[0.04]">
                              {/* 1ª Camada Sólida (Pago) */}
                              <div
                                className={`h-full transition-all duration-300 ${
                                  isOver ? 'bg-rose-500' : isWarn ? 'bg-amber-500' : 'bg-emerald-500'
                                }`}
                                style={{ width: `${Math.min(100, Math.max(0, item.percentage))}%` }}
                                title={`Já pago: ${formatBRL(item.spentAmount)} (${item.percentage}%)`}
                              />
                              {/* 2ª Camada Hachurada Translúcida (A Vencer) */}
                              {item.pendingPercentage > 0 && (
                                <div
                                  className={`h-full transition-all duration-300 opacity-70 ${
                                    isOver
                                      ? 'bg-rose-400/60 bg-[repeating-linear-gradient(45deg,transparent,transparent_3px,rgba(244,63,94,0.4)_3px,rgba(244,63,94,0.4)_6px)]'
                                      : isWarn
                                        ? 'bg-amber-400/60 bg-[repeating-linear-gradient(45deg,transparent,transparent_3px,rgba(245,158,11,0.4)_3px,rgba(245,158,11,0.4)_6px)]'
                                        : 'bg-emerald-400/60 bg-[repeating-linear-gradient(45deg,transparent,transparent_3px,rgba(16,185,129,0.4)_3px,rgba(16,185,129,0.4)_6px)]'
                                  }`}
                                  style={{
                                    width: `${Math.min(
                                      Math.max(0, 100 - item.percentage),
                                      item.pendingPercentage,
                                    )}%`,
                                  }}
                                  title={`Comprometido a vencer: ${formatBRL(item.pendingAmount)} (${item.pendingPercentage}%)`}
                                />
                              )}
                              {/* Marcador do Dia Atual do Mês */}
                              {!item.isFixedCategory && item.limit > 0 && calendarPacePct > 5 && calendarPacePct < 95 && (
                                <div
                                  title={`Tempo decorrido do mês: ${calendarPacePct}%`}
                                  className="absolute top-0 bottom-0 w-0.5 bg-white/40 z-10"
                                  style={{ left: `${calendarPacePct}%` }}
                                />
                              )}
                            </div>

                            {/* Rodapé Informativo: "R$ X pagos + R$ Y a vencer • Folga real: +R$ Z" */}
                            <div className="flex items-center justify-between text-[9px] text-slate-400 pt-0.5 gap-1">
                              <span className="font-mono truncate">
                                {item.limit <= 0 ? (
                                  'Toque para orçar'
                                ) : item.pendingAmount > 0 ? (
                                  `${formatBRL(item.spentAmount)} pagos + ${formatBRL(item.pendingAmount)} a vencer`
                                ) : !item.isFixedCategory && item.realRemaining > 0 && item.daysRemaining > 0 ? (
                                  `Meta: ${formatBRL(item.recommendedDailyRemaining)}/dia`
                                ) : (
                                  `${Math.round(item.totalPercentage)}% comprometido`
                                )}
                              </span>
                              {item.limit <= 0 ? (
                                <span className="text-amber-400 font-medium shrink-0">Sem teto</span>
                              ) : item.realRemaining > 0 ? (
                                <span className="text-emerald-400 font-medium shrink-0 font-mono">
                                  +{formatBRL(item.realRemaining)}
                                </span>
                              ) : item.realRemaining < 0 || (item.committedAmount - item.limit >= 0.01) ? (
                                <span className="text-rose-400 font-medium shrink-0 font-mono">
                                  -{formatBRL(Math.abs(item.committedAmount - item.limit))}
                                </span>
                              ) : (
                                <span className="text-slate-500 shrink-0">No limite</span>
                              )}
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                ) : (
                  <div className="p-3 rounded-xl bg-slate-950/40 border border-white/[0.04] text-center text-xs text-slate-400">
                    Nenhum envelope encontrado para o filtro selecionado.
                  </div>
                )}

                {/* Botão de expansão com texto claro */}
                {filteredItems.length > 4 && (
                  <div className="flex justify-center pt-1">
                    <button
                      type="button"
                      onClick={() => setIsExpanded((prev) => !prev)}
                      className="text-[11px] text-slate-400 hover:text-white transition-colors flex items-center gap-1 cursor-pointer font-medium"
                    >
                      {isExpanded ? (
                        <>
                          <ChevronUp className="size-3" />
                          Recolher envelopes
                        </>
                      ) : (
                        <>
                          <ChevronDown className="size-3" />
                          Mostrar todas as {filteredItems.length} categorias
                        </>
                      )}
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="p-4 rounded-xl bg-slate-950/40 border border-white/[0.04] text-center space-y-2.5">
                <p className="text-xs text-slate-400">
                  Nenhum envelope definido para este mês. Gere tetos automáticos em 1 clique calibrados pela sua capacidade disponível de{' '}
                  <strong className="text-slate-200 font-mono">{formatBRL(effectiveAvailable)}</strong>!
                </p>
                <div className="flex items-center justify-center gap-2 flex-wrap">
                  <Button
                    type="button"
                    size="sm"
                    onClick={handleQuickApplyAiBudgets}
                    className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs h-8"
                  >
                    <Sparkles className="size-3.5 mr-1.5" />
                    Ativar Tetos com IA em 1 Clique
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={openConfig}
                    className="bg-slate-900 hover:bg-slate-800 text-slate-300 border-white/10 text-xs h-8"
                  >
                    <Sliders className="size-3.5 mr-1.5" />
                    Personalizar Manualmente
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* Modal de Gestão e Rebalanceamento */}
      <ManageBudgetsModal
        open={modalOpen}
        onOpenChange={setModalOpen}
        initialTab={modalTab}
      />
    </>
  )
}
