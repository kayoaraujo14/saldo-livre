import { useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL } from '@/lib/utils'
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
} from 'lucide-react'

export function CategoryBudgetsCard() {
  const { categoryBudgetReport } = useFinance()
  const [modalOpen, setModalOpen] = useState(false)
  const [modalTab, setModalTab] = useState<'budgets' | 'rebalance'>('budgets')
  const [isExpanded, setIsExpanded] = useState(false)

  const {
    items,
    totalBudgeted,
    totalSpent,
    totalRemaining,
    overallPercentage,
    status,
    topOverspentCategory,
  } = categoryBudgetReport

  // Mostra os 4 primeiros ou todos se expandido
  const visibleItems = isExpanded ? items : items.slice(0, 4)

  const openConfig = () => {
    setModalTab('budgets')
    setModalOpen(true)
  }

  const openRebalance = () => {
    setModalTab('rebalance')
    setModalOpen(true)
  }

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
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-sm text-white flex items-center gap-1.5">
                  Envelopes Orçamentários
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
              </div>
              <p className="text-[11px] text-slate-400">
                {totalBudgeted > 0 ? (
                  <span>
                    Orçado: <strong className="text-slate-200 font-mono">{formatBRL(totalBudgeted)}</strong>
                    {' • '}
                    Gasto: <strong className="text-slate-200 font-mono">{formatBRL(totalSpent)}</strong>
                    {' '}
                    <span className="text-slate-500 font-mono">({Math.round(overallPercentage)}%)</span>
                    {' • '}
                    {totalRemaining >= 0 ? (
                      <span className="text-emerald-400 font-medium">Sobra: {formatBRL(totalRemaining)}</span>
                    ) : (
                      <span className="text-rose-400 font-medium">Excedido: {formatBRL(Math.abs(totalRemaining))}</span>
                    )}
                  </span>
                ) : (
                  'Defina tetos por categoria para receber alertas e compensações com IA.'
                )}
              </p>
            </div>
          </div>

          {/* Botões de Ação */}
          <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-auto">
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
              onClick={openConfig}
              className="h-7 text-xs bg-slate-800/80 hover:bg-slate-700/80 text-slate-300 border-white/[0.08] px-2.5"
            >
              <Sliders className="size-3 mr-1 text-slate-400" />
              {totalBudgeted > 0 ? 'Ajustar' : 'Configurar'}
            </Button>
          </div>
        </div>

        {/* Barra de Progresso Global do Orçamento */}
        {totalBudgeted > 0 && (
          <div className="space-y-1">
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
        )}

        {/* Alerta Preventivo de IA quando houver categoria em perigo */}
        {topOverspentCategory && (
          <div className="p-2.5 rounded-xl bg-purple-950/30 border border-purple-500/30 flex items-center justify-between gap-2.5 text-xs text-purple-200">
            <div className="flex items-center gap-2 min-w-0">
              <Sparkles className="size-3.5 text-purple-400 shrink-0" />
              <span className="truncate">
                {topOverspentCategory.status === 'danger'
                  ? `"${topOverspentCategory.category}" ultrapassou o teto em ${formatBRL(Math.abs(topOverspentCategory.remaining))}.`
                  : `Projeção de estouro em "${topOverspentCategory.category}" até o fim do mês.`}
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

        {/* Grade de Envelopes */}
        {items.length > 0 ? (
          <div className="space-y-2">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
              {visibleItems.map((item) => {
                const detail = getCategoryDetail(item.category)
                const isOver = item.status === 'danger'
                const isWarn = item.status === 'warning'

                return (
                  <div
                    key={item.category}
                    className="p-3 rounded-xl bg-slate-950/60 border border-white/[0.06] hover:border-white/[0.12] transition-colors flex flex-col justify-between gap-2"
                  >
                    <div>
                      {/* Top linha: Categoria + Badge de Ritmo */}
                      <div className="flex items-center justify-between gap-1 mb-1.5">
                        <div className="flex items-center gap-1.5 min-w-0">
                          <div
                            className="size-2 rounded-full shrink-0"
                            style={{ backgroundColor: detail.color || '#10b981' }}
                          />
                          <span className="font-semibold text-xs text-slate-200 truncate">
                            {item.category}
                          </span>
                        </div>

                        {item.burnRateStatus === 'critical' ? (
                          <Badge
                            variant="outline"
                            className="text-[8px] px-1 py-0 border-0 bg-rose-500/15 text-rose-300 font-mono shrink-0 flex items-center gap-0.5"
                          >
                            <Flame className="size-2.5" />
                            Crítico
                          </Badge>
                        ) : item.burnRateStatus === 'fast' ? (
                          <Badge
                            variant="outline"
                            className="text-[8px] px-1 py-0 border-0 bg-amber-500/15 text-amber-300 font-mono shrink-0 flex items-center gap-0.5"
                          >
                            <AlertTriangle className="size-2.5" />
                            Acelerado
                          </Badge>
                        ) : (
                          <Badge
                            variant="outline"
                            className="text-[8px] px-1 py-0 border-0 bg-emerald-500/15 text-emerald-300 font-mono shrink-0"
                          >
                            Normal
                          </Badge>
                        )}
                      </div>

                      {/* Valores */}
                      <div className="flex items-baseline justify-between text-xs">
                        <span className="font-mono font-bold text-white">
                          {formatBRL(item.spent)}
                        </span>
                        <span className="text-[10px] text-slate-400 font-mono">
                          {item.limit > 0 ? `/ ${formatBRL(item.limit)}` : 'Sem teto'}
                        </span>
                      </div>
                    </div>

                    {/* Barra de Progresso individual */}
                    <div className="space-y-1">
                      <div className="w-full bg-slate-900 rounded-full h-1.5 overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-300 ${
                            isOver ? 'bg-rose-500' : isWarn ? 'bg-amber-500' : 'bg-emerald-500'
                          }`}
                          style={{ width: `${Math.min(100, Math.max(0, item.percentage))}%` }}
                        />
                      </div>

                      {/* Rodapé informativo */}
                      <div className="flex items-center justify-between text-[9px] text-slate-400 pt-0.5">
                        <span className="font-mono">
                          {item.percentage}% gasto
                        </span>
                        {item.remaining > 0 ? (
                          <span className="text-emerald-400 font-medium">
                            +{formatBRL(item.remaining)}
                          </span>
                        ) : item.remaining < 0 ? (
                          <span className="text-rose-400 font-medium">
                            -{formatBRL(Math.abs(item.remaining))}
                          </span>
                        ) : (
                          <span className="text-slate-500">Esgotado</span>
                        )}
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>

            {/* Botão de expansão se houver mais de 4 */}
            {items.length > 4 && (
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
                      Ver todos os {items.length} envelopes
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="p-4 rounded-xl bg-slate-950/40 border border-white/[0.04] text-center space-y-2">
            <p className="text-xs text-slate-400">
              Nenhum envelope definido para este mês. Comece agora mesmo com uma sugestão automática baseada no seu histórico real de gastos!
            </p>
            <Button
              type="button"
              size="sm"
              onClick={openConfig}
              className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs h-8"
            >
              <Sparkles className="size-3.5 mr-1.5" />
              Sugerir Orçamento com IA
            </Button>
          </div>
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
