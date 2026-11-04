import { useState, useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, monthLabel } from '@/lib/utils'
import { suggestCategoryBudgets } from '@/lib/finance'
import { getCategoryDetail } from '@/types'
import { ManageBudgetsModal } from '@/components/modals/ManageBudgetsModal'
import {
  Target,
  Sparkles,
  Sliders,
  AlertTriangle,
  Flame,
  ChevronDown,
  ChevronUp,
  MessageSquare,
  CheckCircle2,
  PlusCircle,
} from 'lucide-react'

type EnvelopeFilter = 'all' | 'alerts' | 'variable' | 'fixed'

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
  const [isCardCollapsed, setIsCardCollapsed] = useState(true)
  const [activeFilter, setActiveFilter] = useState<EnvelopeFilter>('all')

  const {
    items,
    totalBudgeted,
    rawTotalBudgeted,
    expectedIncome: reportExpectedIncome,
    isCappedToIncome,
    incomeUsagePercentage,
    unallocatedIncome,
    totalSpent,
    totalRemaining,
    overallPercentage,
    status,
    topOverspentCategory,
  } = categoryBudgetReport

  const expectedIncome =
    reportExpectedIncome && reportExpectedIncome > 0
      ? reportExpectedIncome
      : summary?.income && summary.income > 0
        ? summary.income
        : (profile?.monthlyIncome || 0)

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
      expectedIncome,
    )
    if (Object.keys(suggestion.suggestedBudgets).length > 0) {
      await setCategoryBudgets(suggestion.suggestedBudgets, month)
    } else {
      openConfig()
    }
  }

  const incomeAllocPct =
    incomeUsagePercentage !== undefined
      ? Math.round(incomeUsagePercentage)
      : expectedIncome > 0
        ? Math.round((totalBudgeted / expectedIncome) * 100)
        : 0

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
                    title={`Teto original (${formatBRL(rawTotalBudgeted || totalBudgeted)}) calibrado automaticamente para não exceder a entrada prevista de ${monthLabel(month)} (${formatBRL(expectedIncome)}).`}
                    className="text-[9px] px-1.5 py-0 font-mono border-0 shrink-0 bg-cyan-500/15 text-cyan-300"
                  >
                    Calibrado à Entrada do Mês
                  </Badge>
                )}
              </div>
              <p className="text-[11px] text-slate-400">
                {totalBudgeted > 0 ? (
                  <span>
                    Entrada Prevista: <strong className="text-slate-200 font-mono">{formatBRL(expectedIncome)}</strong>
                    {' • '}
                    Orçado: <strong className="text-emerald-300 font-mono">{formatBRL(totalBudgeted)}</strong>
                    {expectedIncome > 0 && (
                      <span className="text-slate-500 font-mono"> ({incomeAllocPct}% da entrada)</span>
                    )}
                  </span>
                ) : (
                  'Defina tetos calibrados pela entrada prevista do mês com alertas e compensações IA.'
                )}
              </p>
            </div>
          </div>

          {/* Inline compact progress when collapsed */}
          {isCardCollapsed && totalBudgeted > 0 && (
            <div className="hidden xl:flex items-center gap-3 px-3 py-1 rounded-xl bg-slate-950/60 border border-white/[0.06] text-xs">
              <div className="flex items-center gap-1.5">
                <span className="text-slate-400">Consumido:</span>
                <strong className="font-mono text-slate-100">{formatBRL(totalSpent)}</strong>
                <span className="text-slate-500 font-mono">/ {formatBRL(totalBudgeted)}</span>
              </div>
              <div className="w-24 bg-slate-800 rounded-full h-1.5 overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-300 ${
                    overallPercentage >= 100
                      ? 'bg-rose-500'
                      : overallPercentage >= 80
                        ? 'bg-amber-500'
                        : 'bg-emerald-500'
                  }`}
                  style={{ width: `${Math.min(100, overallPercentage)}%` }}
                />
              </div>
              <span className={`text-[11px] font-mono font-bold ${
                totalRemaining >= 0 ? 'text-emerald-400' : 'text-rose-400'
              }`}>
                {Math.round(overallPercentage)}% ({totalRemaining >= 0 ? `+${formatBRL(totalRemaining)} livre` : `-${formatBRL(Math.abs(totalRemaining))}`})
              </span>
            </div>
          )}

          {/* Botões de Ação */}
          <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-auto flex-wrap">
            {topOverspentCategory && (
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
              Analisar no Chat
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

        {/* Corpo expansível do Card */}
        {!isCardCollapsed && (
          <>
            {/* Painel Resumo de Execução do Orçamento vs Entrada Prevista */}
        {totalBudgeted > 0 && (
          <div className="p-3 rounded-xl bg-slate-950/55 border border-white/[0.06] space-y-2.5">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[11px]">
              <div className="p-2 rounded-lg bg-slate-900/60 border border-white/[0.04]">
                <span className="text-[10px] text-slate-400 block">Entrada Prevista</span>
                <strong className="font-mono text-xs text-slate-100">
                  {formatBRL(expectedIncome)}
                </strong>
              </div>
              <div className="p-2 rounded-lg bg-slate-900/60 border border-white/[0.04]">
                <span className="text-[10px] text-slate-400 block">Teto Orçado ({incomeAllocPct}%)</span>
                <strong className="font-mono text-xs text-emerald-300">
                  {formatBRL(totalBudgeted)}
                </strong>
              </div>
              <div className="p-2 rounded-lg bg-slate-900/60 border border-white/[0.04]">
                <span className="text-[10px] text-slate-400 block">Consumido ({Math.round(overallPercentage)}%)</span>
                <strong className="font-mono text-xs text-slate-100">
                  {formatBRL(totalSpent)}
                </strong>
              </div>
              <div className="p-2 rounded-lg bg-slate-900/60 border border-white/[0.04]">
                <span className="text-[10px] text-slate-400 block">
                  {totalRemaining >= 0 ? 'Folga nos Envelopes' : 'Excesso nos Envelopes'}
                </span>
                <strong
                  className={`font-mono text-xs ${
                    totalRemaining >= 0 ? 'text-emerald-400' : 'text-rose-400'
                  }`}
                >
                  {totalRemaining >= 0
                    ? `+${formatBRL(totalRemaining)}`
                    : `-${formatBRL(Math.abs(totalRemaining))}`}
                </strong>
              </div>
            </div>

            <div className="space-y-1">
              <div className="flex items-center justify-between text-[10px] text-slate-400">
                <span>Progresso de consumo dos envelopes</span>
                <span className="font-mono">
                  {Math.round(overallPercentage)}% usado
                  {unallocatedIncome && unallocatedIncome > 0
                    ? ` • Margem livre não orçada: ${formatBRL(unallocatedIncome)}`
                    : ''}
                </span>
              </div>
              <div className="w-full bg-slate-950 rounded-full h-2 overflow-hidden border border-white/[0.05]">
                <div
                  className={`h-full transition-all duration-500 rounded-full ${
                    overallPercentage >= 100
                      ? 'bg-rose-500'
                      : overallPercentage >= 80
                        ? 'bg-amber-500'
                        : 'bg-emerald-500'
                  }`}
                  style={{ width: `${Math.min(100, Math.max(0, overallPercentage))}%` }}
                />
              </div>
            </div>
          </div>
        )}

        {/* Alerta Preventivo de IA quando houver categoria em perigo */}
        {topOverspentCategory && (
          <div className="p-2.5 rounded-xl bg-purple-950/30 border border-purple-500/30 flex items-center justify-between gap-2.5 text-xs text-purple-200">
            <div className="flex items-center gap-2 min-w-0">
              <Sparkles className="size-3.5 text-purple-400 shrink-0" />
              <span className="truncate">
                {topOverspentCategory.status === 'danger'
                  ? `"${topOverspentCategory.category}" ultrapassou o teto em ${formatBRL(Math.abs(topOverspentCategory.remaining))}.`
                  : `Projeção de estouro em "${topOverspentCategory.category}" (${formatBRL(topOverspentCategory.projectedSpent)} até o fim do mês).`}
              </span>
            </div>
            <button
              type="button"
              onClick={openRebalance}
              className="font-bold underline text-purple-300 hover:text-white shrink-0 cursor-pointer"
            >
              Rebalancear
            </button>
          </div>
        )}

        {/* Filtros Rápidos e Grade de Envelopes */}
        {items.length > 0 ? (
          <div className="space-y-2.5">
            {items.length > 2 && (
              <div className="flex items-center justify-between gap-2 flex-wrap">
                <div className="flex items-center gap-1.5 flex-wrap">
                  {(
                    [
                      { id: 'all', label: `Todos (${items.length})` },
                      { id: 'alerts', label: `Em Alerta (${alertsCount})` },
                      { id: 'variable', label: 'Variáveis' },
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
                  Clique em qualquer envelope para ajustar o teto
                </span>
              </div>
            )}

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
                      title={`Clique para ajustar o teto de ${item.category}`}
                      className="p-3 rounded-xl bg-slate-950/60 border border-white/[0.06] hover:border-emerald-500/30 transition-all flex flex-col justify-between gap-2 cursor-pointer group"
                    >
                      <div>
                        {/* Top linha: Categoria + Badge de Ritmo */}
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
                              Definir teto
                            </Badge>
                          ) : item.burnRateStatus === 'critical' ? (
                            <Badge
                              variant="outline"
                              className="text-[8px] px-1 py-0 border-0 bg-rose-500/15 text-rose-300 font-mono shrink-0 flex items-center gap-0.5"
                            >
                              <Flame className="size-2.5" />
                              {item.spent > item.limit ? 'Estourado' : 'Crítico'}
                            </Badge>
                          ) : item.burnRateStatus === 'fast' ? (
                            <Badge
                              variant="outline"
                              className="text-[8px] px-1 py-0 border-0 bg-amber-500/15 text-amber-300 font-mono shrink-0 flex items-center gap-0.5"
                            >
                              <AlertTriangle className="size-2.5" />
                              Acelerado
                            </Badge>
                          ) : item.isFixedCategory ? (
                            <Badge
                              variant="outline"
                              className="text-[8px] px-1 py-0 border-0 bg-cyan-500/15 text-cyan-300 font-mono shrink-0"
                            >
                              Fixo Mensal
                            </Badge>
                          ) : (
                            <Badge
                              variant="outline"
                              className="text-[8px] px-1 py-0 border-0 bg-emerald-500/15 text-emerald-300 font-mono shrink-0 flex items-center gap-0.5"
                            >
                              <CheckCircle2 className="size-2.5" />
                              No Ritmo
                            </Badge>
                          )}
                        </div>

                        {/* Valores */}
                        <div className="flex items-baseline justify-between text-xs">
                          <span className="font-mono font-bold text-white">
                            {formatBRL(item.spent)}
                          </span>
                          <span className="text-[10px] text-slate-400 font-mono">
                            {item.limit > 0 ? `/ ${formatBRL(item.limit)}` : 'Sem teto definido'}
                          </span>
                        </div>
                      </div>

                      {/* Barra de Progresso individual com marcador de ritmo do mês */}
                      <div className="space-y-1">
                        <div className="relative w-full bg-slate-900 rounded-full h-1.5 overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all duration-300 ${
                              isOver ? 'bg-rose-500' : isWarn ? 'bg-amber-500' : 'bg-emerald-500'
                            }`}
                            style={{ width: `${Math.min(100, Math.max(0, item.percentage))}%` }}
                          />
                          {!item.isFixedCategory && item.limit > 0 && calendarPacePct > 5 && calendarPacePct < 95 && (
                            <div
                              title={`Tempo decorrido do mês: ${calendarPacePct}%`}
                              className="absolute top-0 bottom-0 w-0.5 bg-white/40"
                              style={{ left: `${calendarPacePct}%` }}
                            />
                          )}
                        </div>

                        {/* Rodapé informativo realista */}
                        <div className="flex items-center justify-between text-[9px] text-slate-400 pt-0.5 gap-1">
                          <span className="font-mono truncate">
                            {item.limit <= 0
                              ? 'Toque para orçar'
                              : !item.isFixedCategory && item.remaining > 0 && item.daysRemaining > 0
                                ? `Meta: ${formatBRL(item.recommendedDailyRemaining)}/dia`
                                : item.isFixedCategory
                                  ? `Custo fixo (${Math.round(item.percentage)}%)`
                                  : `${Math.round(item.percentage)}% consumido`}
                          </span>
                          {item.limit <= 0 ? (
                            <span className="text-amber-400 font-medium shrink-0">Sem teto</span>
                          ) : item.remaining > 0 ? (
                            <span className="text-emerald-400 font-medium shrink-0 font-mono">
                              +{formatBRL(item.remaining)}
                            </span>
                          ) : item.remaining < 0 ? (
                            <span className="text-rose-400 font-medium shrink-0 font-mono">
                              -{formatBRL(Math.abs(item.remaining))}
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

            {/* Botão de expansão se houver mais de 4 */}
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
                      Ver todos os {filteredItems.length} envelopes
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="p-4 rounded-xl bg-slate-950/40 border border-white/[0.04] text-center space-y-2.5">
            <p className="text-xs text-slate-400">
              Nenhum envelope definido para este mês. Gere tetos automáticos em 1 clique calibrados pela sua entrada prevista de{' '}
              <strong className="text-slate-200 font-mono">{formatBRL(expectedIncome)}</strong>!
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
