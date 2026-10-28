import { useState, useMemo } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { projectFutureMonths } from '@/lib/finance'
import { formatBRL, currentMonthKey } from '@/lib/utils'
import {
  CalendarRange,
  ArrowRight,
  Sparkles,
  CheckCircle2,
  TrendingUp,
  TrendingDown,
  CreditCard,
  Receipt,
  Zap,
  ChevronDown,
  ChevronUp,
  Layers,
} from 'lucide-react'

interface FutureProjectionModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelectMonth: (monthKey: string) => void
}

export function FutureProjectionModal({ open, onOpenChange, onSelectMonth }: FutureProjectionModalProps) {
  const { transactions, recurring, profile, me, month, cards } = useFinance()
  const [horizonCount, setHorizonCount] = useState<6 | 12>(12)
  const [expandedMonthKey, setExpandedMonthKey] = useState<string | null>(null)
  const [detailTab, setDetailTab] = useState<'items' | 'categories'>('items')

  const currentKey = currentMonthKey()

  const projections = useMemo(() => {
    if (!me) return []
    return projectFutureMonths({
      all: transactions,
      bills: recurring,
      profile,
      uid: me.uid,
      startMonthKey: currentKey,
      count: horizonCount,
      cards,
    })
  }, [transactions, recurring, profile, me, currentKey, horizonCount, cards])

  const summaryMetrics = useMemo(() => {
    if (projections.length === 0) {
      return { totalIncome: 0, totalExpense: 0, avgFreeBalance: 0, totalInvoices: 0, totalFixed: 0 }
    }
    const totalIncome = projections.reduce((s, p) => s + p.projectedIncome, 0)
    const totalExpense = projections.reduce((s, p) => s + p.projectedExpense, 0)
    const totalFree = projections.reduce((s, p) => s + p.projectedFreeBalance, 0)
    const totalInvoices = projections.reduce((s, p) => s + (p.invoiceExpense || 0), 0)
    const totalFixed = projections.reduce((s, p) => s + (p.fixedBillsExpense || 0), 0)
    return {
      totalIncome,
      totalExpense,
      avgFreeBalance: totalFree / projections.length,
      totalInvoices,
      totalFixed,
    }
  }, [projections])

  if (!me) return null

  const handleGoToMonth = (monthKey: string) => {
    onSelectMonth(monthKey)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-4xl w-full max-h-[92vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.06]">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="size-9 rounded-xl bg-gradient-to-tr from-cyan-500/20 to-blue-500/20 border border-cyan-500/30 text-cyan-400 flex items-center justify-center shrink-0">
                <CalendarRange className="size-5" />
              </div>
              <div className="min-w-0 flex-1">
                <DialogTitle className="text-base sm:text-lg font-bold text-white truncate">
                  Projeção de Fluxo de Caixa ({horizonCount} Meses)
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400 truncate">
                  Simulação unificada de Renda, Faturas de Cartão, Contas Fixas e Categorias futuras
                </DialogDescription>
              </div>
            </div>

            {/* Seletor de Horizonte 6M vs 12M */}
            <div className="flex items-center p-1 rounded-xl bg-slate-900 border border-slate-800 text-xs shrink-0">
              <button
                type="button"
                onClick={() => setHorizonCount(6)}
                className={`px-3 py-1 rounded-lg font-semibold cursor-pointer transition-all ${
                  horizonCount === 6
                    ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                6 Meses
              </button>
              <button
                type="button"
                onClick={() => setHorizonCount(12)}
                className={`px-3 py-1 rounded-lg font-semibold cursor-pointer transition-all ${
                  horizonCount === 12
                    ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                12 Meses
              </button>
            </div>
          </div>
        </DialogHeader>

        {/* Painel Executivo Acumulado */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 pt-1">
          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800/80">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
              Média Livre ({horizonCount}m)
            </span>
            <span
              className={`text-sm sm:text-base font-black font-mono mt-0.5 block ${
                summaryMetrics.avgFreeBalance >= 0 ? 'text-emerald-400' : 'text-rose-400'
              }`}
            >
              {formatBRL(summaryMetrics.avgFreeBalance)}/mês
            </span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800/80">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
              Entradas ({horizonCount}m)
            </span>
            <span className="text-sm sm:text-base font-bold font-mono text-emerald-400 mt-0.5 block">
              +{formatBRL(summaryMetrics.totalIncome)}
            </span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800/80">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
              Faturas Cartão ({horizonCount}m)
            </span>
            <span className="text-sm sm:text-base font-bold font-mono text-violet-300 mt-0.5 block">
              −{formatBRL(summaryMetrics.totalInvoices)}
            </span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800/80">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
              Contas Fixas ({horizonCount}m)
            </span>
            <span className="text-sm sm:text-base font-bold font-mono text-amber-300 mt-0.5 block">
              −{formatBRL(summaryMetrics.totalFixed)}
            </span>
          </div>
        </div>

        <div className="py-2 space-y-3 max-h-[58vh] overflow-y-auto pr-1">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            {projections.map((p) => {
              const isSelected = p.monthKey === month
              const isCurrent = p.monthKey === currentKey
              const isPositive = p.projectedFreeBalance >= 0
              const isExpanded = expandedMonthKey === p.monthKey
              const retentionPct =
                p.projectedIncome > 0
                  ? Math.max(0, Math.min(100, Math.round((p.projectedFreeBalance / p.projectedIncome) * 100)))
                  : 0

              const invAmt = p.invoiceExpense || 0
              const fixAmt = p.fixedBillsExpense || 0
              const varAmt = p.variableAccountExpense || 0

              return (
                <div
                  key={p.monthKey}
                  className={`p-4 rounded-2xl border transition-all flex flex-col justify-between gap-3 shadow-md ${
                    isExpanded ? 'md:col-span-2 ' : ''
                  }${
                    isSelected
                      ? 'border-emerald-500/50 bg-emerald-950/20 shadow-lg ring-1 ring-emerald-500/30'
                      : 'border-white/[0.06] bg-slate-950/70 hover:border-slate-700'
                  }`}
                >
                  <div className="space-y-2.5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-slate-100 text-sm capitalize">{p.label}</span>
                        {isCurrent && (
                          <Badge
                            variant="outline"
                            className="text-[10px] py-0 px-2 bg-emerald-950/60 text-emerald-300 border-emerald-800"
                          >
                            Mês Atual
                          </Badge>
                        )}
                      </div>

                      {p.isGenerated ? (
                        <span className="text-[11px] text-emerald-400 flex items-center gap-1 font-semibold">
                          <CheckCircle2 className="size-3.5" /> Lançamentos Gerados
                        </span>
                      ) : (
                        <span className="text-[11px] text-slate-400 flex items-center gap-1">
                          <Sparkles className="size-3 text-amber-400" /> Simulado
                        </span>
                      )}
                    </div>

                    {/* Breakdown In vs Out */}
                    <div className="grid grid-cols-2 gap-2 text-xs pt-1">
                      <div className="p-2.5 rounded-xl bg-slate-900/80 border border-slate-800 flex flex-col">
                        <span className="text-[11px] text-slate-400 flex items-center gap-1">
                          <TrendingUp className="size-3 text-emerald-400" />
                          <span>Entradas Previstas</span>
                        </span>
                        <span className="font-bold font-mono text-emerald-400 text-sm mt-1">
                          +{formatBRL(p.projectedIncome)}
                        </span>
                        <span className="text-[10px] text-slate-500 mt-0.5">{p.incomeCount} fonte(s)</span>
                      </div>

                      <div className="p-2.5 rounded-xl bg-slate-900/80 border border-slate-800 flex flex-col">
                        <span className="text-[11px] text-slate-400 flex items-center gap-1">
                          <TrendingDown className="size-3 text-rose-400" />
                          <span>Saídas Previstas</span>
                        </span>
                        <span className="font-bold font-mono text-slate-200 text-sm mt-1">
                          −{formatBRL(p.projectedExpense)}
                        </span>
                        <span className="text-[10px] text-slate-500 mt-0.5">{p.expenseCount} item(ns)</span>
                      </div>
                    </div>

                    {/* Decomposição por Origem: Faturas vs Contas Fixas vs Débito/Pix */}
                    <div className="grid grid-cols-3 gap-1.5 text-[11px]">
                      <div className="p-2 rounded-lg bg-violet-950/25 border border-violet-500/20">
                        <span className="text-[10px] text-violet-300 flex items-center gap-1 font-medium">
                          <CreditCard className="size-2.5" /> Faturas
                        </span>
                        <span className="font-mono font-bold text-slate-100 block mt-0.5">
                          {formatBRL(invAmt)}
                        </span>
                      </div>
                      <div className="p-2 rounded-lg bg-amber-950/25 border border-amber-500/20">
                        <span className="text-[10px] text-amber-300 flex items-center gap-1 font-medium">
                          <Receipt className="size-2.5" /> Fixas
                        </span>
                        <span className="font-mono font-bold text-slate-100 block mt-0.5">
                          {formatBRL(fixAmt)}
                        </span>
                      </div>
                      <div className="p-2 rounded-lg bg-cyan-950/25 border border-cyan-500/20">
                        <span className="text-[10px] text-cyan-300 flex items-center gap-1 font-medium">
                          <Zap className="size-2.5" /> Pix/Débito
                        </span>
                        <span className="font-mono font-bold text-slate-100 block mt-0.5">
                          {formatBRL(varAmt)}
                        </span>
                      </div>
                    </div>

                    {/* Projected Free Balance & Daily Limit */}
                    <div className="p-3 rounded-xl bg-slate-900/90 border border-slate-800/80 flex items-center justify-between">
                      <div>
                        <span className="text-[11px] font-medium text-slate-400 block">Saldo Livre Estimado:</span>
                        <span
                          className={`text-lg font-black tracking-tight font-mono ${
                            isPositive ? 'text-emerald-400' : 'text-rose-400'
                          }`}
                        >
                          {formatBRL(p.projectedFreeBalance)}
                        </span>
                      </div>

                      <div className="text-right">
                        <span className="text-[11px] font-medium text-slate-400 block">Gasto Seguro:</span>
                        <span className="text-xs font-bold text-cyan-300 font-mono">
                          {p.projectedDailyLimit != null ? `${formatBRL(p.projectedDailyLimit)}/dia` : '—'}
                        </span>
                      </div>
                    </div>

                    {/* Retention Progress Bar */}
                    <div className="space-y-1">
                      <div className="flex justify-between text-[10px] text-slate-400">
                        <span>Retenção de Saldo</span>
                        <span className="font-semibold text-slate-200">{retentionPct}% livre</span>
                      </div>
                      <div className="h-1.5 w-full bg-slate-900 rounded-full overflow-hidden border border-slate-800">
                        <div
                          className="h-full bg-emerald-500 rounded-full transition-all duration-300"
                          style={{ width: `${retentionPct}%` }}
                        />
                      </div>
                    </div>

                    {/* Raio-X Expandido: Itens & Categorias do Mês */}
                    {isExpanded && (
                      <div className="mt-3 pt-3 border-t border-white/[0.08] space-y-3 animate-in fade-in duration-200">
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-xs font-bold text-cyan-300 flex items-center gap-1.5">
                            <Layers className="size-3.5" />
                            <span>Raio-X de {p.label}</span>
                          </span>
                          <div className="flex p-0.5 rounded-lg bg-slate-900 border border-slate-800 text-[11px]">
                            <button
                              type="button"
                              onClick={() => setDetailTab('items')}
                              className={`px-2.5 py-1 rounded-md font-semibold cursor-pointer ${
                                detailTab === 'items'
                                  ? 'bg-slate-800 text-white'
                                  : 'text-slate-400 hover:text-slate-200'
                              }`}
                            >
                              Itens ({(p.items || []).length})
                            </button>
                            <button
                              type="button"
                              onClick={() => setDetailTab('categories')}
                              className={`px-2.5 py-1 rounded-md font-semibold cursor-pointer ${
                                detailTab === 'categories'
                                  ? 'bg-slate-800 text-white'
                                  : 'text-slate-400 hover:text-slate-200'
                              }`}
                            >
                              Categorias ({(p.byCategory || []).length})
                            </button>
                          </div>
                        </div>

                        {detailTab === 'items' ? (
                          <div className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
                            {(p.items || []).length === 0 ? (
                              <p className="text-xs text-slate-500 py-3 text-center">
                                Nenhum item previsto para este mês.
                              </p>
                            ) : (
                              (p.items || []).map((item) => (
                                <div
                                  key={item.id}
                                  className="p-2 rounded-lg bg-slate-900/70 border border-slate-800/80 flex items-center justify-between gap-2 text-xs"
                                >
                                  <div className="min-w-0">
                                    <div className="flex items-center gap-1.5 flex-wrap">
                                      <span className="font-medium text-slate-200 truncate">{item.description}</span>
                                      {item.origin === 'invoice' && (
                                        <Badge
                                          variant="outline"
                                          className="text-[9px] py-0 px-1.5 bg-violet-950/60 text-violet-300 border-violet-800"
                                        >
                                          💳 {item.cardName || 'Cartão'}
                                        </Badge>
                                      )}
                                      {item.origin === 'fixed_bill' && (
                                        <Badge
                                          variant="outline"
                                          className="text-[9px] py-0 px-1.5 bg-amber-950/60 text-amber-300 border-amber-800"
                                        >
                                          🏠 Conta Fixa
                                        </Badge>
                                      )}
                                      {item.origin === 'debit_pix' && (
                                        <Badge
                                          variant="outline"
                                          className="text-[9px] py-0 px-1.5 bg-cyan-950/60 text-cyan-300 border-cyan-800"
                                        >
                                          ⚡ {item.paymentMethod === 'debit' ? 'Débito' : 'Pix/Boleto'}
                                        </Badge>
                                      )}
                                      <span className="text-[10px] text-slate-400">{item.category}</span>
                                    </div>
                                  </div>
                                  <span
                                    className={`font-mono font-bold shrink-0 ${
                                      item.type === 'income' ? 'text-emerald-400' : 'text-slate-200'
                                    }`}
                                  >
                                    {item.type === 'income' ? '+' : '−'}
                                    {formatBRL(item.myImpact)}
                                  </span>
                                </div>
                              ))
                            )}
                          </div>
                        ) : (
                          <div className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
                            {(p.byCategory || []).length === 0 ? (
                              <p className="text-xs text-slate-500 py-3 text-center">
                                Nenhuma categoria de despesa neste mês.
                              </p>
                            ) : (
                              (p.byCategory || []).map((cat) => (
                                <div
                                  key={cat.category}
                                  className="p-2 rounded-lg bg-slate-900/70 border border-slate-800/80 flex items-center justify-between gap-2 text-xs"
                                >
                                  <div>
                                    <span className="font-semibold text-slate-200">{cat.category}</span>
                                    <div className="flex items-center gap-2 text-[10px] text-slate-400 mt-0.5">
                                      {cat.invoiceAmount > 0 && (
                                        <span className="text-violet-300">
                                          💳 Fatura: {formatBRL(cat.invoiceAmount)}
                                        </span>
                                      )}
                                      {(cat.fixedBillAmount + cat.accountAmount) > 0 && (
                                        <span className="text-amber-300">
                                          🏦 Extrato/Fixas: {formatBRL(cat.fixedBillAmount + cat.accountAmount)}
                                        </span>
                                      )}
                                    </div>
                                  </div>
                                  <span className="font-mono font-bold text-slate-100">
                                    {formatBRL(cat.amount)}
                                  </span>
                                </div>
                              ))
                            )}
                          </div>
                        )}
                      </div>
                    )}
                  </div>

                  {/* Month Actions */}
                  <div className="flex items-center justify-between pt-2.5 border-t border-white/[0.06] text-xs gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => setExpandedMonthKey(isExpanded ? null : p.monthKey)}
                      className="text-xs h-8 px-2.5 text-cyan-400 hover:text-cyan-300 hover:bg-cyan-950/30 gap-1 rounded-lg cursor-pointer"
                    >
                      {isExpanded ? (
                        <>
                          <ChevronUp className="size-3.5" />
                          <span>Ocultar Raio-X</span>
                        </>
                      ) : (
                        <>
                          <ChevronDown className="size-3.5" />
                          <span>Ver Itens & Categorias</span>
                        </>
                      )}
                    </Button>

                    <Button
                      size="sm"
                      onClick={() => handleGoToMonth(p.monthKey)}
                      className="text-xs h-8 ml-auto bg-slate-800 hover:bg-slate-700 text-slate-200 hover:text-white gap-1.5 rounded-lg px-3 cursor-pointer shadow-sm shrink-0"
                    >
                      <span>Abrir Mês</span>
                      <ArrowRight className="size-3" />
                    </Button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        <DialogFooter className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-2 border-t border-white/[0.06] w-full">
          <p className="text-[11px] text-slate-500 leading-tight">
            * As previsões somam automaticamente faturas de cartão, parcelas futuras, contas fixas e salário para até 12 meses.
          </p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => onOpenChange(false)}
            className="rounded-xl text-xs h-9 shrink-0 cursor-pointer"
          >
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
