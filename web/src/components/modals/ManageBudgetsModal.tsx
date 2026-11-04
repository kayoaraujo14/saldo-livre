import { useState, useMemo } from 'react'
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
import { formatBRL, round2 } from '@/lib/utils'
import { suggestCategoryBudgets, suggestBudgetRebalance } from '@/lib/finance'
import {
  Target,
  Sparkles,
  ArrowRight,
  CheckCircle2,
  RotateCcw,
  Sliders,
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

  // Inicializa o formulário com os valores atuais do perfil quando o modal abrir
  const currentBudgets = useMemo(() => profile?.categoryBudgets || {}, [profile?.categoryBudgets])

  // Inicialização controlada ao abrir
  const handleOpenChange = (nextOpen: boolean) => {
    if (nextOpen) {
      const initial: Record<string, string> = {}
      for (const cat of EXPENSE_CATEGORIES) {
        if (currentBudgets[cat] && currentBudgets[cat] > 0) {
          initial[cat] = String(currentBudgets[cat])
        }
      }
      setBudgetsState(initial)
      setRebalanceSuccessMsg(null)
    }
    onOpenChange(nextOpen)
  }

  // Sugestão da IA
  const aiSuggestion = useMemo(() => {
    if (!open) return null
    return suggestCategoryBudgets(transactions, me?.uid || '', month, profile?.monthlyIncome || 0)
  }, [open, transactions, me?.uid, month, profile?.monthlyIncome])

  // Oportunidades de rebalanceamento
  const rebalanceInfo = useMemo(() => {
    return suggestBudgetRebalance(categoryBudgetReport)
  }, [categoryBudgetReport])

  // Preenche formulário com as sugestões da IA
  const applyAiSuggestions = () => {
    if (!aiSuggestion) return
    const nextState: Record<string, string> = {}
    for (const [cat, limit] of Object.entries(aiSuggestion.suggestedBudgets)) {
      nextState[cat] = String(limit)
    }
    setBudgetsState(nextState)
  }

  // Limpa todos os limites
  const handleClearAll = () => {
    if (window.confirm('Deseja zerar todos os limites orçamentários definidos?')) {
      setBudgetsState({})
    }
  }

  // Salva no perfil
  const handleSave = async () => {
    setIsSaving(true)
    try {
      const sanitized: Record<string, number> = {}
      for (const [cat, val] of Object.entries(budgetsState)) {
        const num = parseFloat(val.replace(',', '.'))
        if (!isNaN(num) && num > 0) {
          sanitized[cat] = round2(num)
        }
      }
      await setCategoryBudgets(sanitized)
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

  // Soma dos limites no formulário
  const formTotal = useMemo(() => {
    let sum = 0
    for (const val of Object.values(budgetsState)) {
      const num = parseFloat(val.replace(',', '.'))
      if (!isNaN(num) && num > 0) sum += num
    }
    return round2(sum)
  }, [budgetsState])

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
                    Copiloto IA
                  </Badge>
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400">
                  Defina tetos por categoria e conte com a IA para rebalancear imprevistos.
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
        <div className="p-5 overflow-y-auto flex-1 space-y-4 text-xs">
          {activeTab === 'budgets' && (
            <>
              {/* Banner da Sugestão com IA */}
              <div className="glass-card rounded-xl p-3.5 border border-emerald-500/25 bg-emerald-950/20 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                <div className="space-y-1">
                  <div className="flex items-center gap-1.5 font-bold text-emerald-300 text-xs">
                    <Sparkles className="size-3.5" />
                    Sugerir Orçamento com IA
                  </div>
                  <p className="text-[11px] text-slate-300 leading-relaxed">
                    A IA analisa a média dos seus últimos 3 meses e propõe tetos realistas com 10% de folga preventiva.
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

              {/* Barra de resumo de limites configurados */}
              <div className="flex items-center justify-between px-1 text-[11px] text-slate-400">
                <span>
                  Total Orçado: <strong className="text-white font-mono">{formatBRL(formTotal)}</strong>
                  {profile?.monthlyIncome ? (
                    <span className="text-slate-500 ml-1">
                      ({Math.round((formTotal / profile.monthlyIncome) * 100)}% da renda de {formatBRL(profile.monthlyIncome)})
                    </span>
                  ) : null}
                </span>
                <button
                  type="button"
                  onClick={handleClearAll}
                  className="text-slate-500 hover:text-rose-400 transition-colors flex items-center gap-1 cursor-pointer"
                >
                  <RotateCcw className="size-3" />
                  Zerar Tudo
                </button>
              </div>

              {/* Lista de Categorias com Inputs */}
              <div className="space-y-2">
                {EXPENSE_CATEGORIES.map((cat) => {
                  const detail = getCategoryDetail(cat)
                  const currentVal = budgetsState[cat] ?? ''
                  const avg = aiSuggestion?.categoryAverages[cat]

                  return (
                    <div
                      key={cat}
                      className="flex items-center justify-between gap-3 p-2.5 rounded-xl bg-slate-900/60 border border-white/[0.06] hover:border-white/[0.12] transition-colors"
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <div
                          className="size-3 rounded-full shrink-0"
                          style={{ backgroundColor: detail.color || '#10b981' }}
                        />
                        <div className="min-w-0">
                          <span className="font-semibold text-slate-200 block truncate text-xs">
                            {cat}
                          </span>
                          {avg && avg > 0 ? (
                            <span className="text-[10px] text-slate-400 block truncate">
                              Média recente: {formatBRL(avg)}
                            </span>
                          ) : null}
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 shrink-0 w-36">
                        <span className="text-slate-500 text-[11px]">R$</span>
                        <Input
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
                  Quando uma categoria estoura devido a um gasto imprevisto, o Copiloto IA encontra envelopes discricionários com sobra positiva para compensar o desvio sem comprometer seu Saldo Livre.
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
        <div className="p-4 border-t border-white/[0.08] bg-slate-900/60 flex items-center justify-end gap-2">
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
              {isSaving ? 'Salvando...' : 'Salvar Envelopes'}
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
