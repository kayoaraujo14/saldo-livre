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
import { formatBRL, monthLabel, round2 } from '@/lib/utils'
import {
  suggestCategoryBudgets,
  suggestBudgetRebalance,
  normalizeBudgetCategory,
  FIXED_MONTHLY_CATEGORIES,
} from '@/lib/finance'
import {
  Target,
  Sparkles,
  ArrowRight,
  CheckCircle2,
  RotateCcw,
  Sliders,
  AlertTriangle,
  Scale,
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

  const expectedIncome = useMemo(() => {
    if (summary?.income && summary.income > 0) return summary.income
    return profile?.monthlyIncome || 0
  }, [summary?.income, profile?.monthlyIncome])

  // Inicializa o formulário com os valores atuais calibrados quando o modal abrir
  const currentBudgets = useMemo(() => profile?.categoryBudgets || {}, [profile?.categoryBudgets])

  useEffect(() => {
    if (open) {
      setActiveTab(initialTab)
      const initial: Record<string, string> = {}
      // Usa os limites efetivos (já calibrados pela entrada prevista do mês se aplicável)
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
    }
  }, [open, initialTab, categoryBudgetReport.items, currentBudgets])

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen)
  }

  // Sugestão da IA calibrada pela entrada prevista do mês selecionado
  const aiSuggestion = useMemo(() => {
    if (!open) return null
    return suggestCategoryBudgets(transactions, me?.uid || '', month, expectedIncome)
  }, [open, transactions, me?.uid, month, expectedIncome])

  // Mapa de gastos reais do mês por categoria canônica
  const currentMonthSpentMap = useMemo(() => {
    const map = new Map<string, number>()
    for (const item of categoryBudgetReport.items) {
      const canon = normalizeBudgetCategory(item.category)
      map.set(canon, round2((map.get(canon) || 0) + item.spent))
    }
    return map
  }, [categoryBudgetReport.items])

  // Oportunidades de rebalanceamento
  const rebalanceInfo = useMemo(() => {
    return suggestBudgetRebalance(categoryBudgetReport)
  }, [categoryBudgetReport])

  // Preenche formulário com as sugestões da IA
  const applyAiSuggestions = () => {
    if (!aiSuggestion) return
    const nextState: Record<string, string> = {}
    for (const [cat, limit] of Object.entries(aiSuggestion.suggestedBudgets)) {
      nextState[normalizeBudgetCategory(cat)] = String(limit)
    }
    setBudgetsState(nextState)
  }

  // Soma dos limites no formulário
  const formTotal = useMemo(() => {
    let sum = 0
    for (const val of Object.values(budgetsState)) {
      const num = parseFloat(String(val).replace(',', '.'))
      if (!isNaN(num) && num > 0) sum += num
    }
    return round2(sum)
  }, [budgetsState])

  // Calibra proporcionalmente os valores digitados para caberem exatamente na entrada prevista do mês
  const handleCalibrateToExpectedIncome = () => {
    if (expectedIncome <= 0 || formTotal <= expectedIncome || formTotal <= 0) return
    const ratio = expectedIncome / formTotal
    const nextState: Record<string, string> = {}
    let sumScaled = 0
    let maxCat = ''
    let maxVal = -1

    for (const [cat, val] of Object.entries(budgetsState)) {
      const num = parseFloat(String(val).replace(',', '.'))
      if (!isNaN(num) && num > 0) {
        const scaled = round2(num * ratio)
        nextState[cat] = String(scaled)
        sumScaled = round2(sumScaled + scaled)
        if (scaled > maxVal) {
          maxVal = scaled
          maxCat = cat
        }
      }
    }
    const diff = round2(expectedIncome - sumScaled)
    if (maxCat && Math.abs(diff) >= 0.01) {
      const adjusted = round2(Math.max(0, (parseFloat(nextState[maxCat]) || 0) + diff))
      nextState[maxCat] = String(adjusted)
    }
    setBudgetsState(nextState)
  }

  // Limpa todos os limites
  const handleClearAll = () => {
    if (window.confirm('Deseja zerar todos os limites orçamentários definidos?')) {
      setBudgetsState({})
    }
  }

  // Salva no perfil (garantindo que nunca exceda a entrada prevista do mês se houver entrada prevista > 0)
  const handleSave = async () => {
    setIsSaving(true)
    try {
      const rawSanitized: Record<string, number> = {}
      let rawSum = 0
      for (const [cat, val] of Object.entries(budgetsState)) {
        const num = parseFloat(String(val).replace(',', '.'))
        if (!isNaN(num) && num > 0) {
          const rounded = round2(num)
          rawSanitized[cat] = rounded
          rawSum = round2(rawSum + rounded)
        }
      }

      const finalBudgets: Record<string, number> = {}
      if (expectedIncome > 0 && rawSum > expectedIncome && rawSum > 0) {
        const ratio = expectedIncome / rawSum
        let sumScaled = 0
        let maxCat = ''
        let maxVal = -1
        for (const [cat, num] of Object.entries(rawSanitized)) {
          const scaled = round2(num * ratio)
          finalBudgets[cat] = scaled
          sumScaled = round2(sumScaled + scaled)
          if (scaled > maxVal) {
            maxVal = scaled
            maxCat = cat
          }
        }
        const diff = round2(expectedIncome - sumScaled)
        if (maxCat && Math.abs(diff) >= 0.01) {
          finalBudgets[maxCat] = round2(Math.max(0, finalBudgets[maxCat] + diff))
        }
      } else {
        Object.assign(finalBudgets, rawSanitized)
      }

      await setCategoryBudgets(finalBudgets)
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
    await rebalanceCategoryBudgets(fromCat, toCat, amount)
    setRebalanceSuccessMsg(
      `Transferência de ${formatBRL(amount)} de "${fromCat}" para "${toCat}" concluída com sucesso!`,
    )
    setTimeout(() => setRebalanceSuccessMsg(null), 4000)
  }

  const isOverExpectedIncome = expectedIncome > 0 && formTotal > expectedIncome
  const allocationPct = expectedIncome > 0 ? Math.round((formTotal / expectedIncome) * 100) : 0
  const unallocatedMargin = expectedIncome > 0 ? round2(expectedIncome - formTotal) : 0

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
              {/* Termômetro de Alocação da Entrada Prevista do Mês */}
              <div
                className={`rounded-xl p-3.5 border space-y-2.5 ${
                  isOverExpectedIncome
                    ? 'bg-rose-950/25 border-rose-500/35'
                    : 'bg-slate-900/70 border-white/[0.08]'
                }`}
              >
                <div className="flex items-center justify-between gap-2 flex-wrap">
                  <div>
                    <span className="text-[10px] uppercase tracking-wider font-bold text-slate-400 block">
                      Entrada Prevista em {monthLabel(month)}
                    </span>
                    <strong className="font-mono text-sm text-white">
                      {formatBRL(expectedIncome)}
                    </strong>
                  </div>

                  <div className="text-right">
                    <span className="text-[10px] uppercase tracking-wider font-bold text-slate-400 block">
                      Total Orçado ({allocationPct}%)
                    </span>
                    <strong
                      className={`font-mono text-sm ${
                        isOverExpectedIncome ? 'text-rose-400' : 'text-emerald-400'
                      }`}
                    >
                      {formatBRL(formTotal)}
                    </strong>
                  </div>
                </div>

                {/* Barra de Alocação da Entrada */}
                {expectedIncome > 0 && (
                  <div className="space-y-1">
                    <div className="w-full bg-slate-950 rounded-full h-2 overflow-hidden border border-white/[0.06]">
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
                    <div className="flex items-center justify-between text-[10px] text-slate-400">
                      <span>
                        {unallocatedMargin >= 0 ? (
                          <>
                            Margem Livre p/ Poupança:{' '}
                            <strong className="text-emerald-300 font-mono">
                              {formatBRL(unallocatedMargin)}
                            </strong>
                          </>
                        ) : (
                          <span className="text-rose-300 font-medium">
                            Excede a entrada prevista em {formatBRL(Math.abs(unallocatedMargin))}
                          </span>
                        )}
                      </span>
                      <button
                        type="button"
                        onClick={handleClearAll}
                        className="text-slate-400 hover:text-rose-400 transition-colors flex items-center gap-1 cursor-pointer"
                      >
                        <RotateCcw className="size-3" />
                        Zerar Tudo
                      </button>
                    </div>
                  </div>
                )}

                {isOverExpectedIncome && (
                  <div className="pt-1 flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-t border-rose-500/20 text-[11px] text-rose-200">
                    <div className="flex items-center gap-1.5">
                      <AlertTriangle className="size-3.5 text-rose-400 shrink-0" />
                      <span>
                        O orçamento nunca deve superar a entrada prevista do mês ({formatBRL(expectedIncome)}).
                      </span>
                    </div>
                    <Button
                      type="button"
                      size="sm"
                      onClick={handleCalibrateToExpectedIncome}
                      className="h-7 text-[11px] bg-rose-600 hover:bg-rose-500 text-white shrink-0 px-2.5"
                    >
                      <Scale className="size-3 mr-1" />
                      Calibrar à Entrada ({formatBRL(expectedIncome)})
                    </Button>
                  </div>
                )}
              </div>

              {/* Banner da Sugestão com IA */}
              <div className="glass-card rounded-xl p-3.5 border border-emerald-500/25 bg-emerald-950/20 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                <div className="space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-emerald-300 text-xs">
                    <Sparkles className="size-3.5" />
                    Sugerir Orçamento com IA (Teto: {formatBRL(aiSuggestion?.totalSuggested || 0)})
                  </div>
                  <p className="text-[11px] text-slate-300 leading-relaxed">
                    Analisa seu histórico de 3 meses e os compromissos de {monthLabel(month)}, limitando o orçamento total à entrada prevista do mês ({aiSuggestion?.incomeCoveragePct || 0}% da entrada).
                  </p>
                </div>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={applyAiSuggestions}
                  className="shrink-0 bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border-emerald-500/40 text-xs h-8"
                >
                  <Sparkles className="size-3.5 mr-1.5" />
                  Preencher com IA
                </Button>
              </div>

              {/* Lista de Categorias com Inputs Acessíveis */}
              <div className="space-y-2">
                {EXPENSE_CATEGORIES.map((cat) => {
                  const detail = getCategoryDetail(cat)
                  const currentVal = budgetsState[cat] ?? ''
                  const avg = aiSuggestion?.categoryAverages[cat]
                  const suggestedVal = aiSuggestion?.suggestedBudgets[cat]
                  const spentNow = currentMonthSpentMap.get(cat) || 0
                  const isFixed = FIXED_MONTHLY_CATEGORIES.has(cat)
                  const inputId = `budget-input-${cat.replace(/[^a-zA-Z0-9]/g, '-')}`

                  return (
                    <div
                      key={cat}
                      className="flex items-center justify-between gap-3 p-2.5 rounded-xl bg-slate-900/60 border border-white/[0.06] hover:border-white/[0.12] transition-colors"
                    >
                      <div className="flex items-center gap-2.5 min-w-0 flex-1">
                        <div
                          className="size-3 rounded-full shrink-0"
                          style={{ backgroundColor: detail.color || '#10b981' }}
                        />
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <label
                              htmlFor={inputId}
                              className="font-semibold text-slate-200 truncate text-xs cursor-pointer"
                            >
                              {cat}
                            </label>
                            <span
                              className={`text-[9px] px-1.5 py-0 rounded font-mono ${
                                isFixed
                                  ? 'bg-cyan-500/15 text-cyan-300'
                                  : 'bg-slate-800 text-slate-400'
                              }`}
                            >
                              {isFixed ? 'Fixo' : 'Variável'}
                            </span>
                          </div>
                          <div className="flex items-center gap-2 flex-wrap text-[10px] text-slate-400 mt-0.5">
                            {spentNow > 0 && (
                              <span>
                                Gasto no mês: <strong className="text-slate-300 font-mono">{formatBRL(spentNow)}</strong>
                              </span>
                            )}
                            {avg && avg > 0 ? (
                              <span>
                                • Média 3M: <strong className="text-slate-300 font-mono">{formatBRL(avg)}</strong>
                              </span>
                            ) : null}
                            {suggestedVal && suggestedVal > 0 && String(suggestedVal) !== currentVal ? (
                              <button
                                type="button"
                                onClick={() =>
                                  setBudgetsState((prev) => ({
                                    ...prev,
                                    [cat]: String(suggestedVal),
                                  }))
                                }
                                className="text-emerald-400 hover:text-emerald-300 underline font-mono cursor-pointer"
                              >
                                Usar IA: {formatBRL(suggestedVal)}
                              </button>
                            ) : null}
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 shrink-0 w-36">
                        <span className="text-slate-500 text-[11px]">R$</span>
                        <Input
                          id={inputId}
                          type="number"
                          step="10"
                          min="0"
                          placeholder="0,00"
                          value={currentVal}
                          onChange={(e) =>
                            setBudgetsState((prev) => ({
                              ...prev,
                              [cat]: e.target.value,
                            }))
                          }
                          className="h-8 text-xs font-mono text-right bg-slate-950/80 border-white/[0.1]"
                        />
                      </div>
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
                  ? `Salvar Calibrado (${formatBRL(expectedIncome)})`
                  : 'Salvar Envelopes'}
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
