import { useState, useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, monthLabel, round2 } from '@/lib/utils'
import {
  buildBudgetInsights,
  calculateFinancialHealthScore,
  resolveEmergencyReserveBalance,
} from '@/lib/finance'
import { generateBudgetCoachInsights } from '@/lib/gemini'
import type { BudgetInsight, BudgetInsightAction, CategoryBudgetStatus } from '@/types'
import { ManageBudgetsModal } from '@/components/modals/ManageBudgetsModal'
import {
  Target,
  Sparkles,
  Sliders,
  ChevronDown,
  ChevronUp,
  Info,
  Loader2,
  RefreshCw,
  TrendingUp,
} from 'lucide-react'

type EnvelopeFilter = 'alerts' | 'variable' | 'all' | 'fixed'
type Tone = 'ok' | 'warning' | 'danger' | 'muted'

/**
 * Paleta semântica ÚNICA da tela de orçamentos:
 * verde = dentro do teto · âmbar = atenção · vermelho = estourou · cinza = sem teto · roxo = IA.
 */
const TONE = {
  ok: { text: 'text-emerald-300', bg: 'bg-emerald-500/15', bar: 'bg-emerald-500', soft: 'bg-emerald-500/35' },
  warning: { text: 'text-amber-300', bg: 'bg-amber-500/15', bar: 'bg-amber-500', soft: 'bg-amber-500/35' },
  danger: { text: 'text-rose-300', bg: 'bg-rose-500/15', bar: 'bg-rose-500', soft: 'bg-rose-500/35' },
  muted: { text: 'text-slate-400', bg: 'bg-slate-500/15', bar: 'bg-slate-500', soft: 'bg-slate-500/35' },
} as const

function badgeTone(label: string): Tone {
  if (label === 'Estourado' || label === 'Vai Estourar') return 'danger'
  if (label === 'Quase no Limite' || label === 'No Limite' || label === 'Ritmo Rápido') return 'warning'
  if (label === 'Definir Teto') return 'muted'
  return 'ok'
}

/** Texto curto e autoexplicativo exibido no selo (os rótulos internos do motor continuam intactos). */
function badgeText(label: string): string {
  if (['Previsto no Teto', 'Fixo em Dia', 'Quitado / Dentro do Previsto', 'Sob Controle'].includes(label)) {
    return 'Dentro do teto'
  }
  if (label === 'Definir Teto') return 'Sem teto'
  return label
}

function severityRank(item: CategoryBudgetStatus): number {
  if (item.limit > 0 && item.committedAmount - item.limit >= 0.01) return 0
  if (item.status === 'danger') return 0
  if (item.status === 'warning') return 1
  return 2
}

const INSIGHT_COLOR: Record<BudgetInsight['severity'], string> = {
  danger: 'bg-rose-400',
  attention: 'bg-amber-400',
  info: 'bg-slate-500',
}

export function CategoryBudgetsCard() {
  const {
    categoryBudgetReport,
    summary,
    month,
    profile,
    setCategoryBudgets,
    rebalanceCategoryBudgets,
  } = useFinance()
  const [modalOpen, setModalOpen] = useState(false)
  const [modalTab, setModalTab] = useState<'budgets' | 'rebalance'>('budgets')
  const [modalAutoPlan, setModalAutoPlan] = useState(false)
  const [isExpanded, setIsExpanded] = useState(false)
  const [isCardCollapsed, setIsCardCollapsed] = useState(false)
  const [showHelp, setShowHelp] = useState(false)
  const [activeFilter, setActiveFilter] = useState<EnvelopeFilter>('all')
  const [expandedKey, setExpandedKey] = useState<string | null>(null)
  const [aiInsights, setAiInsights] = useState<{ month: string; items: BudgetInsight[] } | null>(null)
  const [isGeneratingInsights, setIsGeneratingInsights] = useState(false)

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
    status,
    investmentSpent = 0,
    investmentTarget = 0,
    investmentGoal = 0,
  } = categoryBudgetReport

  const rawIncome =
    reportExpectedIncome && reportExpectedIncome > 0
      ? reportExpectedIncome
      : summary?.income && summary.income > 0
        ? summary.income
        : profile?.monthlyIncome || 0

  const effectiveAvailable =
    totalAvailableIncome ||
    round2(Math.max(0, rawIncome + carryoverAmount - (summary?.previousMonthDeficit || 0)))

  const sortedItems = useMemo(
    () =>
      [...items].sort(
        (a, b) => severityRank(a) - severityRank(b) || b.totalPercentage - a.totalPercentage,
      ),
    [items],
  )

  const isAlert = (i: CategoryBudgetStatus) =>
    i.status !== 'ok' || i.burnRateStatus === 'critical' || i.burnRateStatus === 'fast'

  const filteredItems = useMemo(() => {
    if (activeFilter === 'alerts') return sortedItems.filter(isAlert)
    if (activeFilter === 'variable') {
      return sortedItems.filter((i) => !i.isFixedCategory && !/investimento/i.test(i.category))
    }
    if (activeFilter === 'fixed') return sortedItems.filter((i) => Boolean(i.isFixedCategory))
    return sortedItems
  }, [sortedItems, activeFilter])

  const alertsCount = useMemo(() => sortedItems.filter(isAlert).length, [sortedItems])
  const visibleItems = isExpanded ? filteredItems : filteredItems.slice(0, 4)

  // Insights: locais e instantâneos; se o Gemini já analisou este mês, ele assume o lugar
  const localInsights = useMemo(() => buildBudgetInsights(categoryBudgetReport), [categoryBudgetReport])
  const insights =
    aiInsights && aiInsights.month === month && aiInsights.items.length > 0 ? aiInsights.items : localInsights
  const insightsFromAi = insights.some((i) => i.fromAi)

  const openConfig = () => {
    setModalAutoPlan(false)
    setModalTab('budgets')
    setModalOpen(true)
  }

  const openAiPlan = () => {
    setModalAutoPlan(true)
    setModalTab('budgets')
    setModalOpen(true)
  }

  const askCopilot = (prompt: string) => {
    window.dispatchEvent(new CustomEvent('saldo-livre:ask-copilot', { detail: { prompt } }))
  }

  const handleGenerateAiInsights = async () => {
    if (isGeneratingInsights) return
    setIsGeneratingInsights(true)
    try {
      const reserveBalance = resolveEmergencyReserveBalance(profile, summary)
      const health = calculateFinancialHealthScore(summary, reserveBalance, categoryBudgetReport)
      const result = await generateBudgetCoachInsights(categoryBudgetReport, health)
      if (result) setAiInsights({ month, items: result })
    } finally {
      setIsGeneratingInsights(false)
    }
  }

  /** Ajuste de teto sempre respeitando o piso: nunca abaixo do que já está comprometido (pago + a vencer). */
  const adjustLimit = async (category: string, delta: number) => {
    const target = items.find((i) => i.category === category)
    const currentLimit = target?.limit || 0
    const committed = target?.committedAmount || 0
    const floor = Math.ceil(committed / 10) * 10
    const nextLimit = Math.max(floor, Math.max(50, Math.round((currentLimit + delta) / 10) * 10))

    const nextBudgets: Record<string, number> = {}
    for (const it of items) {
      if (it.limit > 0) nextBudgets[it.category] = it.limit
    }
    nextBudgets[category] = nextLimit
    await setCategoryBudgets(nextBudgets, month)
    setAiInsights(null)
  }

  const handleInsightAction = async (action: BudgetInsightAction) => {
    switch (action.type) {
      case 'adjust_limit':
        if (action.category) await adjustLimit(action.category, action.amount ?? 0)
        break
      case 'rebalance':
        if (action.fromCategory && action.category && action.amount) {
          await rebalanceCategoryBudgets(action.fromCategory, action.category, action.amount, month)
          setAiInsights(null)
        }
        break
      case 'apply_plan':
        openAiPlan()
        break
      case 'open_config':
        openConfig()
        break
      case 'ask_copilot':
        if (action.prompt) askCopilot(action.prompt)
        break
    }
  }

  const overallTone: Tone = status === 'danger' ? 'danger' : status === 'warning' ? 'warning' : 'ok'
  const spentPct = totalBudgeted > 0 ? Math.min(100, (totalSpent / totalBudgeted) * 100) : 0
  const pendingPct =
    totalBudgeted > 0 ? Math.min(Math.max(0, 100 - spentPct), (totalPending / totalBudgeted) * 100) : 0

  const goal = investmentGoal > 0 ? investmentGoal : investmentTarget
  const investPct = goal > 0 ? Math.round((investmentSpent / goal) * 100) : 0

  return (
    <>
      <div className="glass-card rounded-2xl p-4 border border-white/[0.08] space-y-3.5 bg-slate-900/60 backdrop-blur-md">
        {/* Cabeçalho */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-white/[0.06] text-slate-200 border border-white/10 shrink-0">
              <Target className="size-4" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <h3 className="font-bold text-sm text-white flex items-center gap-1.5">
                  Envelopes Orçamentários
                  <span className="text-xs font-semibold text-slate-400 font-sans">• {monthLabel(month)}</span>
                </h3>
                {totalBudgeted > 0 && (
                  <Badge
                    variant="outline"
                    className={`text-[9px] px-1.5 py-0 font-mono border-0 shrink-0 ${TONE[overallTone].bg} ${TONE[overallTone].text}`}
                  >
                    {status === 'danger' ? 'Atenção aos tetos' : status === 'warning' ? 'Ritmo acelerado' : 'Dentro da meta'}
                  </Badge>
                )}
                {isCappedToIncome && (
                  <Badge
                    variant="outline"
                    title={`Teto original (${formatBRL(rawTotalBudgeted || totalBudgeted)}) calibrado automaticamente para não exceder a capacidade disponível de ${monthLabel(month)} (${formatBRL(effectiveAvailable)}).`}
                    className="text-[9px] px-1.5 py-0 font-mono border-0 shrink-0 bg-slate-500/20 text-slate-300"
                  >
                    Calibrado à renda
                  </Badge>
                )}
              </div>
              <p className="text-[11px] text-slate-400">
                {totalBudgeted > 0
                  ? 'Um teto de gasto para cada categoria do mês'
                  : 'Defina tetos por categoria e acompanhe quanto ainda pode gastar'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-auto flex-wrap">
            <button
              type="button"
              onClick={() => setShowHelp((p) => !p)}
              title="Como funciona"
              aria-label="Como funciona"
              className={`p-1.5 rounded-lg cursor-pointer transition-colors ${showHelp ? 'text-white bg-white/10' : 'text-slate-500 hover:text-slate-200'}`}
            >
              <Info className="size-4" />
            </button>
            <Button
              type="button"
              size="sm"
              onClick={openAiPlan}
              className="h-8 text-xs bg-purple-500/80 hover:bg-purple-500 text-white px-3"
            >
              <Sparkles className="size-3.5 mr-1.5" />
              {totalBudgeted > 0 ? 'Planejar com IA' : 'Criar com IA'}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={openConfig}
              className="h-8 text-xs bg-slate-800/80 hover:bg-slate-700/80 text-slate-200 border-white/[0.12] px-3"
            >
              <Sliders className="size-3.5 mr-1.5 text-slate-400" />
              {totalBudgeted > 0 ? 'Ajustar tetos' : 'Definir tetos'}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => setIsCardCollapsed((prev) => !prev)}
              title={isCardCollapsed ? 'Expandir envelopes detalhados' : 'Recolher envelopes'}
              className="h-8 px-2 text-xs text-slate-400 hover:text-white hover:bg-white/[0.06] cursor-pointer flex items-center gap-1 rounded-lg"
            >
              <span className="hidden sm:inline text-[11px] font-medium">{isCardCollapsed ? 'Ver todos' : 'Recolher'}</span>
              {isCardCollapsed ? <ChevronDown className="size-3.5" /> : <ChevronUp className="size-3.5" />}
            </Button>
          </div>
        </div>

        {showHelp && (
          <div className="p-3 rounded-xl bg-white/[0.04] border border-white/[0.06] text-[11.5px] text-slate-300 space-y-1.5">
            <p className="font-bold text-white text-xs">Como ler esta tela</p>
            <p>• Envelope é o teto de gasto de uma categoria neste mês.</p>
            <p>• Barra cheia = já pago. Barra clara listrada = contas e faturas que ainda vão vencer.</p>
            <p>• “Ainda pode gastar” = tetos − pago − a vencer.</p>
            <p className="flex items-center gap-3 flex-wrap">
              <span className="flex items-center gap-1"><i className="size-2 rounded-full bg-emerald-400 inline-block" />ok</span>
              <span className="flex items-center gap-1"><i className="size-2 rounded-full bg-amber-400 inline-block" />atenção</span>
              <span className="flex items-center gap-1"><i className="size-2 rounded-full bg-rose-400 inline-block" />estourou</span>
              <span className="flex items-center gap-1"><i className="size-2 rounded-full bg-purple-400 inline-block" />IA</span>
            </p>
            <p>• Clique em uma categoria para ajustar o teto ou perguntar à IA.</p>
          </div>
        )}

        {!isCardCollapsed && (
          <>
            {/* Resumo: quanto ainda pode gastar */}
            {totalBudgeted > 0 && (
              <div className="p-3.5 rounded-xl bg-slate-950/60 border border-white/[0.06] space-y-3">
                <div className="flex flex-wrap items-start justify-between gap-3 text-xs">
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase tracking-wider font-semibold">Ainda pode gastar</span>
                    <strong className={`font-mono text-lg sm:text-xl font-extrabold ${totalRealRemaining > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {formatBRL(Math.max(0, totalRealRemaining))}
                    </strong>
                    <span className="text-[10.5px] text-slate-500 block">dentro dos tetos, até o fim do mês</span>
                  </div>
                  <div className="text-right">
                    <span className="text-[10px] text-slate-400 block uppercase tracking-wider font-semibold">Teto total</span>
                    <strong className="font-mono text-slate-100 text-sm sm:text-base">{formatBRL(totalBudgeted)}</strong>
                  </div>
                </div>

                <div className="space-y-1.5">
                  <div className="relative w-full bg-slate-950 rounded-full h-2.5 overflow-hidden flex border border-white/[0.08]">
                    <div
                      className={`h-full transition-all duration-500 ${TONE[overallTone].bar}`}
                      style={{ width: `${spentPct}%` }}
                      title={`Pago: ${formatBRL(totalSpent)}`}
                    />
                    {pendingPct > 0 && (
                      <div
                        className={`h-full ${TONE[overallTone].soft}`}
                        style={{ width: `${pendingPct}%` }}
                        title={`A vencer: ${formatBRL(totalPending)}`}
                      />
                    )}
                  </div>
                  <div className="flex items-center justify-between text-[11px] text-slate-400">
                    <div className="flex items-center gap-1.5">
                      <div className={`size-2 rounded-full ${TONE[overallTone].bar}`} />
                      <span>Pago <strong className="font-mono text-slate-200">{formatBRL(totalSpent)}</strong></span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <div className={`size-2 rounded-full border ${TONE[overallTone].soft} border-slate-400/60`} />
                      <span>A vencer <strong className="font-mono text-slate-200">{formatBRL(totalPending)}</strong></span>
                    </div>
                  </div>
                </div>

                {/* Meta de aporte (regra 50/30/20) isolada do consumo */}
                {(goal > 0 || investmentSpent > 0) && (
                  <div
                    title="Meta de aporte 50/30/20 (isolada das despesas de consumo dos envelopes)"
                    className="px-3 py-2.5 rounded-xl bg-slate-950/70 border border-sky-400/20 space-y-1.5"
                  >
                    <div className="flex items-center justify-between gap-2 text-[11.5px]">
                      <div className="flex items-center gap-1.5 min-w-0 text-sky-300 font-bold">
                        <TrendingUp className="size-3.5 shrink-0" />
                        <span className="truncate">Meta de aporte · 20% da renda</span>
                      </div>
                      <span className="font-mono font-bold text-sky-300 shrink-0 whitespace-nowrap">{investPct}%</span>
                    </div>
                    <div className="flex items-baseline gap-1.5">
                      <strong className="font-mono text-sm text-white">{formatBRL(investmentSpent)}</strong>
                      <span className="text-[11px] text-slate-400">de {formatBRL(goal)} neste mês</span>
                    </div>
                    <div className="w-full bg-slate-900 rounded-full h-1 overflow-hidden">
                      <div className="h-full bg-sky-400" style={{ width: `${Math.min(100, Math.max(0, investPct))}%` }} />
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Assistente IA: insights acionáveis */}
            {insights.length > 0 && (
              <div className="p-3 rounded-xl bg-purple-500/[0.07] border border-purple-400/25 space-y-2.5">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <Sparkles className="size-4 text-purple-400 shrink-0" />
                    <span className="text-[13px] font-bold text-white">Assistente IA</span>
                    <span className="text-[9.5px] font-bold text-purple-300 bg-purple-500/20 rounded px-1.5 py-0.5 whitespace-nowrap">
                      {insightsFromAi ? 'Gemini' : 'Dicas rápidas'}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={handleGenerateAiInsights}
                    disabled={isGeneratingInsights}
                    className="flex items-center gap-1.5 text-[11px] font-bold text-purple-300 hover:text-purple-200 disabled:opacity-60 cursor-pointer px-2 py-1 rounded-lg hover:bg-purple-500/10 whitespace-nowrap"
                  >
                    {isGeneratingInsights ? <Loader2 className="size-3 animate-spin" /> : <RefreshCw className="size-3" />}
                    {isGeneratingInsights ? 'Analisando…' : insightsFromAi ? 'Atualizar' : 'Analisar com Gemini'}
                  </button>
                </div>

                <div className="space-y-2.5">
                  {insights.map((insight) => (
                    <div key={insight.id} className="flex gap-2.5">
                      <div className={`w-[3px] rounded-full shrink-0 ${INSIGHT_COLOR[insight.severity]}`} />
                      <div className="min-w-0 space-y-0.5">
                        <p className="text-[13px] font-semibold text-slate-100">{insight.title}</p>
                        <p className="text-xs text-slate-400 leading-snug">{insight.message}</p>
                        {insight.action && (
                          <button
                            type="button"
                            onClick={() => handleInsightAction(insight.action!)}
                            className="mt-1.5 px-2.5 py-1.5 rounded-lg bg-purple-500/20 hover:bg-purple-500/30 border border-purple-400/35 text-purple-200 text-[11.5px] font-bold cursor-pointer transition-colors"
                          >
                            {insight.action.label}
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Lista de envelopes */}
            {items.length > 0 && (
              <div className="space-y-2.5">
                {items.length > 2 && (
                  <div className="flex items-center gap-1.5 flex-wrap">
                    {(
                      [
                        { id: 'all', label: `Todos (${items.length})` },
                        { id: 'alerts', label: `Em alerta (${alertsCount})` },
                        { id: 'variable', label: 'Variáveis' },
                        { id: 'fixed', label: 'Fixos' },
                      ] as const
                    ).map((tab) => (
                      <button
                        key={tab.id}
                        type="button"
                        onClick={() => setActiveFilter(tab.id)}
                        className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold transition-all cursor-pointer ${
                          activeFilter === tab.id
                            ? 'bg-white/[0.14] text-white border border-white/30'
                            : 'bg-slate-950/50 text-slate-400 border border-white/[0.06] hover:text-slate-200'
                        }`}
                      >
                        {tab.label}
                      </button>
                    ))}
                  </div>
                )}

                {visibleItems.length > 0 ? (
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5 items-start">
                    {visibleItems.map((item) => {
                      const tone = badgeTone(item.badgeLabel)
                      const isOpen = expandedKey === item.category
                      const isOver = item.limit > 0 && item.committedAmount - item.limit >= 0.01
                      const spentRatio = item.limit > 0 ? Math.min(100, (item.spentAmount / item.limit) * 100) : 0
                      const pendingRatio =
                        item.limit > 0
                          ? Math.min(Math.max(0, 100 - spentRatio), (item.pendingAmount / item.limit) * 100)
                          : 0
                      const detail =
                        item.pendingAmount > 0 && item.spentAmount <= 0
                          ? `Nada pago ainda · ${formatBRL(item.pendingAmount)} a vencer`
                          : item.pendingAmount > 0
                            ? `Pago ${formatBRL(item.spentAmount)} · A vencer ${formatBRL(item.pendingAmount)}`
                            : item.spentAmount > 0
                              ? `Pago ${formatBRL(item.spentAmount)}`
                              : 'Sem gastos neste mês'

                      return (
                        <div
                          key={item.category}
                          className={`rounded-xl bg-slate-950/60 border transition-all ${isOpen ? 'border-white/25' : 'border-white/[0.06] hover:border-white/15'}`}
                        >
                          <div
                            role="button"
                            tabIndex={0}
                            aria-expanded={isOpen}
                            onClick={() => setExpandedKey(isOpen ? null : item.category)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault()
                                setExpandedKey(isOpen ? null : item.category)
                              }
                            }}
                            title={`Clique para ajustar o teto de ${item.category}`}
                            className="p-3 flex flex-col gap-2 cursor-pointer"
                          >
                            <div className="flex items-center justify-between gap-2">
                              <span className="font-semibold text-xs text-slate-100 truncate">{item.category}</span>
                              <div className="flex items-center gap-1 shrink-0">
                                <Badge
                                  variant="outline"
                                  className={`text-[9px] px-1.5 py-0 border-0 font-mono whitespace-nowrap ${TONE[tone].bg} ${TONE[tone].text}`}
                                >
                                  {badgeText(item.badgeLabel)}
                                </Badge>
                                {isOpen ? <ChevronUp className="size-3.5 text-slate-500" /> : <ChevronDown className="size-3.5 text-slate-500" />}
                              </div>
                            </div>

                            <div className="flex items-end justify-between gap-2">
                              <div className="flex items-baseline gap-1 min-w-0">
                                <span className="font-mono font-extrabold text-white text-sm">{formatBRL(item.committedAmount)}</span>
                                <span className="text-[11px] text-slate-400 font-mono truncate">
                                  {item.limit > 0 ? `de ${formatBRL(item.limit)}` : '(sem teto)'}
                                </span>
                              </div>
                              {item.limit > 0 && (
                                <span className={`text-[11.5px] font-bold font-mono whitespace-nowrap ${TONE[tone].text}`}>
                                  {isOver
                                    ? `${formatBRL(item.committedAmount - item.limit)} acima`
                                    : `sobra ${formatBRL(item.realRemaining)}`}
                                </span>
                              )}
                            </div>

                            <div className="w-full bg-slate-900 rounded-full h-1.5 overflow-hidden flex border border-white/[0.04]">
                              <div className={`h-full transition-all duration-300 ${TONE[tone].bar}`} style={{ width: `${spentRatio}%` }} />
                              {pendingRatio > 0 && (
                                <div className={`h-full ${TONE[tone].soft}`} style={{ width: `${pendingRatio}%` }} />
                              )}
                            </div>

                            <span className="text-[10.5px] text-slate-500 truncate">{detail}</span>
                          </div>

                          {isOpen && (
                            <div className="px-3 pb-3 space-y-2">
                              <div className="p-2.5 rounded-lg bg-slate-950/80 border border-white/[0.05] space-y-2">
                                <p className="text-[11px] text-slate-400">
                                  {item.limit > 0 ? `Teto atual ${formatBRL(item.limit)} · ajuste rápido:` : 'Ainda sem teto · crie um:'}
                                </p>
                                <div className="grid grid-cols-4 gap-1.5">
                                  {[-100, -50, 50, 100].map((delta) => (
                                    <button
                                      key={delta}
                                      type="button"
                                      onClick={() => adjustLimit(item.category, delta)}
                                      className="py-1.5 rounded-md bg-white/[0.06] hover:bg-white/[0.12] border border-white/10 text-[11.5px] font-bold text-slate-100 cursor-pointer transition-colors"
                                    >
                                      {delta > 0 ? `+${delta}` : delta}
                                    </button>
                                  ))}
                                </div>
                                <div className="grid grid-cols-2 gap-1.5">
                                  <button
                                    type="button"
                                    onClick={openConfig}
                                    className="py-1.5 rounded-md bg-white/[0.06] hover:bg-white/[0.12] border border-white/10 text-[11.5px] font-bold text-slate-100 cursor-pointer transition-colors"
                                  >
                                    Editar valor
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() =>
                                      askCopilot(
                                        `Analise meu envelope de ${item.category}: teto ${formatBRL(item.limit)}, pago ${formatBRL(item.spentAmount)}, a vencer ${formatBRL(item.pendingAmount)}. O que posso fazer para fechar o mês dentro do orçamento?`,
                                      )
                                    }
                                    className="py-1.5 rounded-md bg-purple-500/15 hover:bg-purple-500/25 border border-purple-400/35 text-[11.5px] font-bold text-purple-300 cursor-pointer transition-colors flex items-center justify-center gap-1"
                                  >
                                    <Sparkles className="size-3" />
                                    Perguntar à IA
                                  </button>
                                </div>
                              </div>
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                ) : (
                  <div className="p-3 rounded-xl bg-slate-950/40 border border-white/[0.04] text-center text-xs text-slate-400">
                    Nenhuma categoria neste filtro.
                  </div>
                )}

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
                          Mostrar menos
                        </>
                      ) : (
                        <>
                          <ChevronDown className="size-3" />
                          Ver mais {filteredItems.length - 4} categorias
                        </>
                      )}
                    </button>
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </div>

      <ManageBudgetsModal
        open={modalOpen}
        onOpenChange={setModalOpen}
        initialTab={modalTab}
        autoGeneratePlan={modalAutoPlan}
      />
    </>
  )
}
