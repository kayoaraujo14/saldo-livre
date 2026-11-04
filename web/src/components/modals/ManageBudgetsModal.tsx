import { useState, useMemo, useEffect } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { useFinance } from '@/store/FinanceContext'
import { EXPENSE_CATEGORIES, getCategoryDetail } from '@/types'
import type { SmartBudgetPlan } from '@/types'
import { formatBRL, monthLabel, round2, shiftMonth } from '@/lib/utils'
import {
  suggestCategoryBudgets,
  suggestBudgetRebalance,
  normalizeBudgetCategory,
  FIXED_MONTHLY_CATEGORIES,
} from '@/lib/finance'
import { generateSmartBudgetPlan } from '@/lib/gemini'
import {
  Target,
  Sparkles,
  ArrowRight,
  CheckCircle2,
  RotateCcw,
  Sliders,
  AlertTriangle,
  Scale,
  Loader2,
  Plus,
  Minus,
} from 'lucide-react'

interface ManageBudgetsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialTab?: 'budgets' | 'rebalance'
}

export function ManageBudgetsModal({
  open,
  onOpenChange,
  initialTab = 'budgets',
}: ManageBudgetsModalProps) {
  const {
    profile,
    summary,
    month,
    transactions,
    me,
    categoryBudgetReport,
    setCategoryBudgets,
    rebalanceCategoryBudgets,
  } = useFinance()

  const [activeTab, setActiveTab] = useState<'budgets' | 'rebalance'>(initialTab)
  const [budgetsState, setBudgetsState] = useState<Record<string, string>>({})
  const [isSaving, setIsSaving] = useState(false)
  const [rebalanceSuccessMsg, setRebalanceSuccessMsg] = useState<string | null>(null)
  const [geminiPlan, setGeminiPlan] = useState<SmartBudgetPlan | null>(null)
  const [isLoadingGemini, setIsLoadingGemini] = useState(false)

  const expectedIncome = useMemo(() => {
    if (summary?.income && summary.income > 0) return summary.income
    return profile?.monthlyIncome || 0
  }, [summary?.income, profile?.monthlyIncome])

  const carryoverAmount = summary?.carryoverAmount || 0
  const previousMonthDeficit = summary?.previousMonthDeficit || 0

  const totalAvailableIncome = useMemo(() => {
    return round2(Math.max(0, expectedIncome + carryoverAmount - previousMonthDeficit))
  }, [expectedIncome, carryoverAmount, previousMonthDeficit])

  // Inicializa o formulário com os valores atuais calibrados quando o modal abrir
  const currentBudgets = useMemo(() => {
    return profile?.monthlyCategoryBudgets?.[month] || profile?.categoryBudgets || {}
  }, [profile?.monthlyCategoryBudgets, profile?.categoryBudgets, month])

  const previousMonth = useMemo(() => shiftMonth(month, -1), [month])
  const previousMonthBudgets = useMemo(() => {
    return profile?.monthlyCategoryBudgets?.[previousMonth] || null
  }, [profile?.monthlyCategoryBudgets, previousMonth])

  // Mapa detalhado de gastos pagos e comprometidos a vencer
  const committedByCategory = useMemo(() => {
    const spentMap: Record<string, number> = {}
    const pendingMap: Record<string, number> = {}
    const totalCommittedMap: Record<string, number> = {}

    for (const item of categoryBudgetReport.items) {
      const canon = normalizeBudgetCategory(item.category)
      spentMap[canon] = item.spentAmount
      pendingMap[canon] = item.pendingAmount
      totalCommittedMap[canon] = item.committedAmount
    }
    return { spentMap, pendingMap, totalCommittedMap }
  }, [categoryBudgetReport.items])

  useEffect(() => {
    if (open) {
      setActiveTab(initialTab)
      const initial: Record<string, string> = {}
      for (const item of categoryBudgetReport.items) {
        if (item.limit > 0) {
          const canon = normalizeBudgetCategory(item.category)
          initial[canon] = String(round2((Number(initial[canon]) || 0) + item.limit))
        }
      }
      if (Object.keys(initial).length === 0) {
        for (const [rawCat, val] of Object.entries(currentBudgets)) {
          if (val && val > 0) {
            const canon = normalizeBudgetCategory(rawCat)
            initial[canon] = String(round2((Number(initial[canon]) || 0) + val))
          }
        }
      }
      setBudgetsState(initial)
      setRebalanceSuccessMsg(null)
      setGeminiPlan(null)
    }
  }, [open, initialTab, categoryBudgetReport.items, currentBudgets])

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen)
  }

  // Sugestão da IA calibrada pela capacidade disponível do mês selecionado
  const aiSuggestion = useMemo(() => {
    if (!open) return null
    return suggestCategoryBudgets(
      transactions,
      me?.uid || '',
      month,
      expectedIncome,
      carryoverAmount,
      previousMonthDeficit,
    )
  }, [open, transactions, me?.uid, month, expectedIncome, carryoverAmount, previousMonthDeficit])

  // Mapa de gastos reais do mês por categoria canônica
  const currentMonthSpentMap = useMemo(() => {
    const map = new Map<string, number>()
    for (const item of categoryBudgetReport.items) {
      const canon = normalizeBudgetCategory(item.category)
      map.set(canon, round2((map.get(canon) || 0) + item.spentAmount))
    }
    const totalInvestedThisMonth = summary?.totalInvested || 0
    if (totalInvestedThisMonth > 0) {
      map.set('Investimentos', round2(Math.max(map.get('Investimentos') || 0, totalInvestedThisMonth)))
    }
    return map
  }, [categoryBudgetReport.items, summary?.totalInvested])

  // Oportunidades de rebalanceamento
  const rebalanceInfo = useMemo(() => {
    return suggestBudgetRebalance(categoryBudgetReport)
  }, [categoryBudgetReport])

  // Gera plano de orçamento com Gemini
  const handleGenerateGeminiPlan = async () => {
    setIsLoadingGemini(true)
    try {
      const existingLimitsObj: Record<string, number> = {}
      for (const [cat, v] of Object.entries(budgetsState)) {
        const num = parseFloat(String(v).replace(',', '.'))
        if (!isNaN(num) && num > 0) existingLimitsObj[cat] = num
      }

      const plan = await generateSmartBudgetPlan({
        monthlyIncome: totalAvailableIncome,
        carryoverAmount,
        spentByCategory: committedByCategory.spentMap,
        pendingByCategory: committedByCategory.pendingMap,
        history3mAverages: aiSuggestion?.categoryAverages || {},
        existingLimits: existingLimitsObj,
      })
      setGeminiPlan(plan)
    } finally {
      setIsLoadingGemini(false)
    }
  }

  // Aplica as sugestões geradas pelo Gemini
  const handleApplyGeminiPlan = () => {
    if (!geminiPlan) return
    const nextState: Record<string, string> = { ...budgetsState }
    for (const alloc of geminiPlan.allocations) {
      const canon = normalizeBudgetCategory(alloc.category)
      nextState[canon] = String(alloc.suggestedLimit)
    }
    setBudgetsState(nextState)
  }

  // Preenche formulário com as sugestões determinísticas da IA
  const applyAiSuggestions = () => {
    if (!aiSuggestion) return
    const nextState: Record<string, string> = {}
    for (const [cat, limit] of Object.entries(aiSuggestion.suggestedBudgets)) {
      nextState[normalizeBudgetCategory(cat)] = String(limit)
    }
    setBudgetsState(nextState)
  }

  // Copia o orçamento do mês anterior
  const handleCopyFromPreviousMonth = () => {
    if (!previousMonthBudgets) return
    const nextState: Record<string, string> = {}
    for (const [cat, limit] of Object.entries(previousMonthBudgets)) {
      if (limit > 0) {
        nextState[normalizeBudgetCategory(cat)] = String(Math.ceil(limit / 10) * 10)
      }
    }
    setBudgetsState(nextState)
  }

  // Soma dos limites no formulário (sempre arredondada)
  const formTotal = useMemo(() => {
    let sum = 0
    for (const val of Object.values(budgetsState)) {
      const num = parseFloat(String(val).replace(',', '.'))
      if (!isNaN(num) && num > 0) sum += num
    }
    return Math.round(sum)
  }, [budgetsState])

  // Calibra proporcionalmente sem centavos quebrados
  const handleCalibrateToExpectedIncome = () => {
    if (totalAvailableIncome <= 0 || formTotal <= totalAvailableIncome || formTotal <= 0) return
    const maxCeiling = Math.floor(totalAvailableIncome / 10) * 10
    const ratio = maxCeiling / formTotal
    const nextState: Record<string, string> = {}
    let sumScaled = 0
    let maxCat = ''
    let maxVal = -1

    for (const [cat, val] of Object.entries(budgetsState)) {
      const num = parseFloat(String(val).replace(',', '.'))
      if (!isNaN(num) && num > 0) {
        const committed = committedByCategory.totalCommittedMap[cat] || 0
        const minFloor = Math.ceil(committed / 10) * 10
        const scaled = Math.max(minFloor, Math.floor((num * ratio) / 10) * 10)
        nextState[cat] = String(scaled)
        sumScaled += scaled
        if (scaled > maxVal) {
          maxVal = scaled
          maxCat = cat
        }
      }
    }
    const diff = Math.floor((maxCeiling - sumScaled) / 10) * 10
    if (maxCat && diff > 0) {
      nextState[maxCat] = String((parseFloat(nextState[maxCat]) || 0) + diff)
    }
    setBudgetsState(nextState)
  }

  // Limpa todos os limites
  const handleClearAll = () => {
    if (window.confirm('Deseja zerar todos os limites orçamentários definidos?')) {
      setBudgetsState({})
      setGeminiPlan(null)
    }
  }

  // Salva no perfil (garantindo que nunca gere centavos quebrados e respeite a capacidade disponível)
  const handleSave = async () => {
    setIsSaving(true)
    try {
      const rawSanitized: Record<string, number> = {}
      let rawSum = 0
      for (const [cat, val] of Object.entries(budgetsState)) {
        const num = parseFloat(String(val).replace(',', '.'))
        if (!isNaN(num) && num > 0) {
          const roundedClean = Math.ceil(num / 10) * 10
          rawSanitized[cat] = roundedClean
          rawSum += roundedClean
        }
      }

      const finalBudgets: Record<string, number> = {}
      if (totalAvailableIncome > 0 && rawSum > totalAvailableIncome && rawSum > 0) {
        const maxCeiling = Math.floor(totalAvailableIncome / 10) * 10
        const ratio = maxCeiling / rawSum
        let sumScaled = 0
        let maxCat = ''
        let maxVal = -1
        for (const [cat, num] of Object.entries(rawSanitized)) {
          const committed = committedByCategory.totalCommittedMap[cat] || 0
          const minFloor = Math.ceil(committed / 10) * 10
          const scaled = Math.max(minFloor, Math.floor((num * ratio) / 10) * 10)
          finalBudgets[cat] = scaled
          sumScaled += scaled
          if (scaled > maxVal) {
            maxVal = scaled
            maxCat = cat
          }
        }
        const diff = Math.floor((maxCeiling - sumScaled) / 10) * 10
        if (maxCat && diff > 0) {
          finalBudgets[maxCat] = finalBudgets[maxCat] + diff
        }
      } else {
        Object.assign(finalBudgets, rawSanitized)
      }

      await setCategoryBudgets(finalBudgets, month)
      onOpenChange(false)
    } finally {
      setIsSaving(false)
    }
  }

  // Executa uma compensação sugerida
  const handleApplySingleRebalance = async (
    fromCat: string,
    toCat: string,
    amount: number,
  ) => {
    await rebalanceCategoryBudgets(fromCat, toCat, amount, month)
    setRebalanceSuccessMsg(
      `Transferência de ${formatBRL(amount)} de "${fromCat}" para "${toCat}" concluída com sucesso!`,
    )
    setTimeout(() => setRebalanceSuccessMsg(null), 4000)
  }

  const isOverExpectedIncome = totalAvailableIncome > 0 && formTotal > totalAvailableIncome
  const allocationPct = totalAvailableIncome > 0 ? Math.round((formTotal / totalAvailableIncome) * 100) : 0
  const unallocatedMargin = totalAvailableIncome > 0 ? round2(totalAvailableIncome - formTotal) : 0

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-xl max-h-[88vh] flex flex-col p-0 gap-0 overflow-hidden bg-slate-950 border-white/[0.12]">
        {/* Header com Abas */}
        <DialogHeader className="p-5 pb-3 border-b border-white/[0.08] bg-slate-900/40">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-xl bg-emerald-500/15 text-emerald-400 border border-emerald-500/20">
                <Target className="size-5" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-white flex items-center gap-2">
                  Envelopes Orçamentários
                  <Badge variant="outline" className="text-[10px] bg-emerald-500/10 text-emerald-300 border-emerald-500/20">
                    {monthLabel(month)}
                  </Badge>
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400">
                  Calibre seus tetos usando a entrada prevista do mês e conte com a IA para rebalancear imprevistos.
                </DialogDescription>
              </div>
            </div>
          </div>

          {/* Abas */}
          <div className="flex items-center gap-2 mt-4 border-b border-white/[0.06] -mb-3 pb-2">
            <button
              type="button"
              onClick={() => setActiveTab('budgets')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                activeTab === 'budgets'
                  ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                  : 'text-slate-400 hover:text-white hover:bg-white/[0.04]'
              }`}
            >
              <Sliders className="size-3.5" />
              Configurar Tetos
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('rebalance')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                activeTab === 'rebalance'
                  ? 'bg-purple-500/20 text-purple-300 border border-purple-500/30'
                  : 'text-slate-400 hover:text-white hover:bg-white/[0.04]'
              }`}
            >
              <Sparkles className="size-3.5" />
              Rebalanceamento IA
              {rebalanceInfo.canRebalance && (
                <span className="size-2 rounded-full bg-purple-400 animate-pulse ml-0.5" />
              )}
            </button>
          </div>
        </DialogHeader>

        {/* Conteúdo scrollável */}
        <div className="p-5 overflow-y-auto overscroll-contain flex-1 space-y-4 text-xs">
          {activeTab === 'budgets' && (
            <>
              {/* Sticky Header com Renda do Mês, Total Alocado e Saldo Livre Não Alocado */}
              <div
                className={`sticky top-0 z-20 backdrop-blur-md rounded-xl p-3 border shadow-lg space-y-2.5 ${
                  isOverExpectedIncome
                    ? 'bg-rose-950/90 border-rose-500/40'
                    : 'bg-slate-900/90 border-white/[0.12]'
                }`}
              >
                <div className="grid grid-cols-3 gap-2 text-center sm:text-left">
                  <div>
                    <span className="text-[9px] uppercase tracking-wider font-bold text-slate-400 block">
                      Renda do Mês
                    </span>
                    <strong className="font-mono text-xs sm:text-sm text-white">
                      {formatBRL(totalAvailableIncome)}
                    </strong>
                    {carryoverAmount > 0 && (
                      <span className="text-[9px] text-emerald-400 block font-mono">
                        +{formatBRL(carryoverAmount)} sobra
                      </span>
                    )}
                  </div>

                  <div className="border-x border-white/10 px-2">
                    <span className="text-[9px] uppercase tracking-wider font-bold text-slate-400 block">
                      Total Alocado ({allocationPct}%)
                    </span>
                    <strong
                      className={`font-mono text-xs sm:text-sm ${
                        isOverExpectedIncome ? 'text-rose-400' : 'text-emerald-400'
                      }`}
                    >
                      {formatBRL(formTotal)}
                    </strong>
                  </div>

                  <div>
                    <span className="text-[9px] uppercase tracking-wider font-bold text-slate-400 block">
                      Saldo Não Alocado
                    </span>
                    <strong
                      className={`font-mono text-xs sm:text-sm ${
                        unallocatedMargin >= 0 ? 'text-emerald-300' : 'text-rose-400'
                      }`}
                    >
                      {unallocatedMargin >= 0
                        ? formatBRL(unallocatedMargin)
                        : `-${formatBRL(Math.abs(unallocatedMargin))}`}
                    </strong>
                  </div>
                </div>

                {/* Barra de Progresso de Alocação da Renda */}
                {totalAvailableIncome > 0 && (
                  <div className="space-y-1">
                    <div className="w-full bg-slate-950 rounded-full h-1.5 overflow-hidden border border-white/[0.06]">
                      <div
                        className={`h-full rounded-full transition-all duration-300 ${
                          isOverExpectedIncome
                            ? 'bg-rose-500'
                            : allocationPct > 85
                              ? 'bg-amber-500'
                              : 'bg-emerald-500'
                        }`}
                        style={{ width: `${Math.min(100, Math.max(0, allocationPct))}%` }}
                      />
                    </div>
                  </div>
                )}

                {isOverExpectedIncome && (
                  <div className="pt-1 flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-t border-rose-500/20 text-[11px] text-rose-200">
                    <div className="flex items-center gap-1.5">
                      <AlertTriangle className="size-3.5 text-rose-400 shrink-0" />
                      <span>
                        Orçamento supera a renda em {formatBRL(Math.abs(unallocatedMargin))}.
                      </span>
                    </div>
                    <Button
                      type="button"
                      size="sm"
                      onClick={handleCalibrateToExpectedIncome}
                      className="h-6 text-[10px] bg-rose-600 hover:bg-rose-500 text-white shrink-0 px-2"
                    >
                      <Scale className="size-3 mr-1" />
                      Calibrar à Renda ({formatBRL(totalAvailableIncome)})
                    </Button>
                  </div>
                )}
              </div>

              {/* Botão e Preview "✨ Preencher Automaticamente com Gemini" */}
              <div className="glass-card rounded-xl p-3.5 border border-purple-500/30 bg-purple-950/20 space-y-2.5">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2.5">
                  <div className="space-y-0.5">
                    <div className="flex items-center gap-1.5 font-bold text-purple-300 text-xs">
                      <Sparkles className="size-3.5 text-purple-400" />
                      ✨ Planejar Orçamento com Gemini AI
                    </div>
                    <p className="text-[11px] text-slate-300 leading-relaxed">
                      O Gemini analisa sua receita, gastos pagos e contas a vencer para calcular limites arredondados que blindam seus compromissos.
                    </p>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <Button
                      type="button"
                      size="sm"
                      disabled={isLoadingGemini}
                      onClick={handleGenerateGeminiPlan}
                      className="bg-purple-600 hover:bg-purple-500 text-white text-xs h-8 px-3 shadow-md"
                    >
                      {isLoadingGemini ? (
                        <>
                          <Loader2 className="size-3 mr-1.5 animate-spin" />
                          Consultando Gemini...
                        </>
                      ) : (
                        <>
                          <Sparkles className="size-3 mr-1.5 text-purple-200" />
                          Sugerir com Gemini
                        </>
                      )}
                    </Button>
                  </div>
                </div>

                {/* Preview do Plano Gerado pelo Gemini */}
                {geminiPlan && (
                  <div className="p-3 rounded-lg bg-slate-900/80 border border-purple-500/30 space-y-2 animate-fadeIn">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[10px] font-bold uppercase tracking-wider text-purple-300 flex items-center gap-1">
                        <Sparkles className="size-3 text-purple-400" />
                        Estratégia Recomendada pelo Gemini
                      </span>
                      <Button
                        type="button"
                        size="sm"
                        onClick={handleApplyGeminiPlan}
                        className="h-6 text-[11px] bg-emerald-600 hover:bg-emerald-500 text-white px-2.5"
                      >
                        <CheckCircle2 className="size-3 mr-1" />
                        Aplicar Sugestões da IA
                      </Button>
                    </div>
                    <p className="text-xs text-purple-100 italic bg-purple-950/40 p-2 rounded border border-purple-500/20 leading-relaxed">
                      "{geminiPlan.summaryInsight}"
                    </p>
                  </div>
                )}
              </div>

              {/* Botões Secundários: Copiar Mês Anterior e Zerar Tudo */}
              <div className="flex items-center justify-between gap-2 flex-wrap text-[11px]">
                <div className="flex items-center gap-2">
                  {previousMonthBudgets && Object.keys(previousMonthBudgets).length > 0 && (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={handleCopyFromPreviousMonth}
                      className="bg-slate-900/80 hover:bg-slate-800 text-slate-300 border-white/[0.1] text-xs h-7 px-2.5"
                      title={`Copiar os envelopes configurados em ${monthLabel(previousMonth)}`}
                    >
                      <RotateCcw className="size-3 mr-1" />
                      Copiar {monthLabel(previousMonth).split(' ')[0]}
                    </Button>
                  )}
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={applyAiSuggestions}
                    className="bg-slate-900/80 hover:bg-slate-800 text-emerald-300 border-emerald-500/30 text-xs h-7 px-2.5"
                  >
                    Usar Histórico 3M
                  </Button>
                </div>

                <button
                  type="button"
                  onClick={handleClearAll}
                  className="text-slate-400 hover:text-rose-400 transition-colors flex items-center gap-1 cursor-pointer text-xs"
                >
                  <RotateCcw className="size-3" />
                  Zerar Todos os Tetos
                </button>
              </div>

              {/* Lista de Categorias com Sliders, Mínimo Comprometido e Chips Rápidos */}
              <div className="space-y-3">
                {EXPENSE_CATEGORIES.map((cat) => {
                  const detail = getCategoryDetail(cat)
                  const canon = normalizeBudgetCategory(cat)
                  const currentValStr = budgetsState[canon] ?? budgetsState[cat] ?? ''
                  const currentValNum = parseFloat(currentValStr.replace(',', '.')) || 0

                  const committed = committedByCategory.totalCommittedMap[canon] || currentMonthSpentMap.get(canon) || 0
                  const spent = committedByCategory.spentMap[canon] || 0
                  const pending = committedByCategory.pendingMap[canon] || 0
                  const minFloor = Math.ceil(committed / 10) * 10

                  const avg = aiSuggestion?.categoryAverages[cat] || aiSuggestion?.categoryAverages[canon] || 0
                  const isFixed = FIXED_MONTHLY_CATEGORIES.has(cat) || FIXED_MONTHLY_CATEGORIES.has(canon)
                  const inputId = `budget-input-${cat.replace(/[^a-zA-Z0-9]/g, '-')}`

                  const geminiReason = geminiPlan?.allocations.find(
                    (a) => normalizeBudgetCategory(a.category) === canon,
                  )?.reason

                  const handleValChange = (newVal: number) => {
                    const cleanVal = Math.max(minFloor, Math.round(newVal / 10) * 10)
                    setBudgetsState((prev) => ({
                      ...prev,
                      [canon]: String(cleanVal),
                    }))
                  }

                  const sliderMax = Math.max(minFloor + 1000, 2000, Math.ceil(totalAvailableIncome / 10) * 10)

                  return (
                    <div
                      key={cat}
                      className="p-3 rounded-xl bg-slate-900/60 border border-white/[0.06] hover:border-white/[0.12] transition-colors space-y-2.5"
                    >
                      {/* Top Row: Categoria, Tipo e Input Numérico */}
                      <div className="flex items-center justify-between gap-2.5">
                        <div className="flex items-center gap-2 min-w-0">
                          <div
                            className="size-3 rounded-full shrink-0"
                            style={{ backgroundColor: detail.color || '#10b981' }}
                          />
                          <label
                            htmlFor={inputId}
                            className="font-semibold text-slate-200 truncate text-xs cursor-pointer"
                          >
                            {cat}
                          </label>
                          <span
                            className={`text-[9px] px-1.5 py-0 rounded font-mono shrink-0 ${
                              isFixed
                                ? 'bg-cyan-500/15 text-cyan-300'
                                : 'bg-slate-800 text-slate-400'
                            }`}
                          >
                            {isFixed ? 'Fixo' : 'Variável'}
                          </span>
                        </div>

                        {/* Campo de Valor com prefixo R$ */}
                        <div className="flex items-center gap-1.5 shrink-0 w-32">
                          <span className="text-slate-500 text-[11px] font-mono">R$</span>
                          <Input
                            id={inputId}
                            type="number"
                            step="10"
                            min={minFloor}
                            placeholder="0"
                            value={currentValStr}
                            onChange={(e) => {
                              const val = parseFloat(e.target.value.replace(',', '.'))
                              setBudgetsState((prev) => ({
                                ...prev,
                                [canon]: isNaN(val) ? '' : String(val),
                              }))
                            }}
                            className="h-7 text-xs font-mono text-right bg-slate-950/80 border-white/[0.1]"
                          />
                        </div>
                      </div>

                      {/* Informações de Mínimo Comprometido e Média Histórica */}
                      <div className="flex items-center justify-between text-[10px] text-slate-400 gap-2 flex-wrap">
                        <span title="Valor que já foi gasto ou já está comprometido para contas a vencer neste mês">
                          Mínimo Comprometido:{' '}
                          <strong className="text-slate-300 font-mono">{formatBRL(committed)}</strong>
                          {pending > 0 && (
                            <span className="text-amber-300/90 font-mono text-[9px] ml-1">
                              ({formatBRL(spent)} pagos + {formatBRL(pending)} a vencer)
                            </span>
                          )}
                        </span>
                        {avg > 0 && (
                          <span className="font-mono text-slate-400">
                            Média 3M: <strong className="text-slate-300">{formatBRL(avg)}</strong>
                          </span>
                        )}
                      </div>

                      {/* Slider Intuitivo com piso no Mínimo Comprometido */}
                      <div className="space-y-1">
                        <input
                          type="range"
                          min={minFloor}
                          max={sliderMax}
                          step={10}
                          value={currentValNum >= minFloor ? currentValNum : minFloor}
                          onChange={(e) => handleValChange(parseFloat(e.target.value))}
                          className="w-full accent-emerald-500 h-1.5 bg-slate-950 rounded-lg cursor-pointer"
                        />
                      </div>

                      {/* Chips de Ajuste Rápido: Média 3M, -R$ 50, +R$ 50 */}
                      <div className="flex items-center justify-between gap-1.5 pt-0.5 flex-wrap">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          {avg > 0 && (
                            <button
                              type="button"
                              onClick={() => handleValChange(Math.max(minFloor, Math.ceil(avg / 10) * 10))}
                              className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-[10px] font-mono text-slate-300 hover:text-white transition-colors cursor-pointer"
                            >
                              Média 3M ({formatBRL(Math.ceil(avg / 10) * 10)})
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => handleValChange(currentValNum - 50)}
                            className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-[10px] font-mono text-slate-300 hover:text-white transition-colors cursor-pointer flex items-center gap-0.5"
                          >
                            <Minus className="size-2.5" />
                            R$ 50
                          </button>
                          <button
                            type="button"
                            onClick={() => handleValChange(currentValNum + 50)}
                            className="px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-[10px] font-mono text-slate-300 hover:text-white transition-colors cursor-pointer flex items-center gap-0.5"
                          >
                            <Plus className="size-2.5" />
                            R$ 50
                          </button>
                        </div>

                        {committed > 0 && currentValNum > 0 && (
                          <span
                            className={`text-[10px] font-mono ${
                              currentValNum >= committed ? 'text-emerald-400' : 'text-rose-400'
                            }`}
                          >
                            Folga: {formatBRL(Math.max(0, currentValNum - committed))}
                          </span>
                        )}
                      </div>

                      {/* Justificativa da IA (Gemini Reason) se disponível */}
                      {geminiReason && (
                        <div className="text-[10px] text-purple-300 italic flex items-center gap-1.5 pt-1 border-t border-white/[0.04]">
                          <Sparkles className="size-3 text-purple-400 shrink-0" />
                          <span>{geminiReason}</span>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            </>
          )}

          {activeTab === 'rebalance' && (
            <div className="space-y-4">
              {/* Notificação de sucesso temporária */}
              {rebalanceSuccessMsg && (
                <div className="p-3 rounded-xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 flex items-center gap-2 text-xs">
                  <CheckCircle2 className="size-4 shrink-0 text-emerald-400" />
                  <span>{rebalanceSuccessMsg}</span>
                </div>
              )}

              {/* Diagnóstico da IA */}
              <div className="glass-card rounded-xl p-4 border border-purple-500/25 bg-purple-950/20 space-y-2">
                <div className="flex items-center gap-2 text-purple-300 font-bold text-xs">
                  <Sparkles className="size-4" />
                  Diagnóstico de Compensação Inteligente
                </div>
                <p className="text-[11px] text-slate-300 leading-relaxed">
                  Quando uma categoria estoura devido a um gasto imprevisto, o Copiloto IA encontra envelopes discricionários com sobra positiva para compensar o desvio sem aumentar seu orçamento total e sem comprometer seu Saldo Livre.
                </p>
                <div className="grid grid-cols-2 gap-2 pt-2 border-t border-white/[0.06] text-[11px]">
                  <div>
                    <span className="text-slate-400 block">Déficit em Categorias:</span>
                    <strong className="text-rose-400 font-mono text-sm">
                      {formatBRL(rebalanceInfo.deficitTotal)}
                    </strong>
                  </div>
                  <div>
                    <span className="text-slate-400 block">Sobra Disponível em Outros:</span>
                    <strong className="text-emerald-400 font-mono text-sm">
                      {formatBRL(rebalanceInfo.surplusTotal)}
                    </strong>
                  </div>
                </div>
              </div>

              {/* Lista de Sugestões de Transferência */}
              {rebalanceInfo.canRebalance ? (
                <div className="space-y-2.5">
                  <h4 className="text-[11px] font-bold text-slate-300 uppercase tracking-wider">
                    Transferências Sugeridas pela IA ({rebalanceInfo.suggestions.length})
                  </h4>

                  {rebalanceInfo.suggestions.map((s, idx) => (
                    <div
                      key={idx}
                      className="p-3.5 rounded-xl bg-slate-900/80 border border-white/[0.08] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3"
                    >
                      <div className="space-y-1.5 flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap text-xs">
                          <span className="font-semibold text-slate-300 truncate">
                            {s.compensateFrom}
                          </span>
                          <span className="text-slate-500">
                            (sobra {formatBRL(s.availableSurplus)})
                          </span>
                          <ArrowRight className="size-3 text-purple-400 shrink-0" />
                          <span className="font-semibold text-rose-300 truncate">
                            {s.overspentCategory}
                          </span>
                          <span className="text-rose-400/80">
                            (+{formatBRL(s.overspentAmount)} acima)
                          </span>
                        </div>
                        <p className="text-[10px] text-slate-400">
                          Transferir <strong className="text-purple-300 font-mono">{formatBRL(s.suggestedTransfer)}</strong> equilibra o envelope de {s.overspentCategory} sem alterar suas despesas totais.
                        </p>
                      </div>

                      <Button
                        type="button"
                        size="sm"
                        onClick={() =>
                          handleApplySingleRebalance(
                            s.compensateFrom,
                            s.overspentCategory,
                            s.suggestedTransfer,
                          )
                        }
                        className="bg-purple-600 hover:bg-purple-500 text-white text-xs shrink-0"
                      >
                        Compensar Agora
                      </Button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-8 space-y-2 text-slate-400">
                  <CheckCircle2 className="size-8 mx-auto text-emerald-400 opacity-80" />
                  <p className="font-semibold text-white text-xs">Nenhum rebalanceamento necessário</p>
                  <p className="text-[11px] text-slate-400 max-w-sm mx-auto">
                    {categoryBudgetReport.items.length === 0
                      ? 'Configure primeiro seus limites na aba "Configurar Tetos" para permitir que a IA monitore compensações.'
                      : 'Todos os seus envelopes orçamentários estão operando dentro do teto planejado.'}
                  </p>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-white/[0.08] bg-slate-900/60 flex items-center justify-between gap-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onOpenChange(false)}
            className="text-slate-400 hover:text-white text-xs"
          >
            Fechar
          </Button>
          {activeTab === 'budgets' && (
            <Button
              type="button"
              size="sm"
              disabled={isSaving}
              onClick={handleSave}
              className="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs px-4"
            >
              {isSaving
                ? 'Salvando...'
                : isOverExpectedIncome
                  ? `Salvar Calibrado (${formatBRL(totalAvailableIncome)})`
                  : 'Salvar Envelopes'}
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
