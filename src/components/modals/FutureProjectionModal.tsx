import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { projectFutureMonths } from '@/lib/finance'
import { formatBRL, currentMonthKey } from '@/lib/utils'
import {
  CalendarRange,
  ArrowRight,
  Zap,
  Sparkles,
  CheckCircle2,
  TrendingUp,
  TrendingDown,
} from 'lucide-react'

interface FutureProjectionModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelectMonth: (monthKey: string) => void
}

export function FutureProjectionModal({ open, onOpenChange, onSelectMonth }: FutureProjectionModalProps) {
  const { transactions, recurring, profile, me, month, setMonth, generateBills } = useFinance()

  if (!me) return null

  const currentKey = currentMonthKey()
  const projections = projectFutureMonths({
    all: transactions,
    bills: recurring,
    profile,
    uid: me.uid,
    startMonthKey: currentKey,
    count: 6,
  })

  const handleGoToMonth = (monthKey: string) => {
    onSelectMonth(monthKey)
    onOpenChange(false)
  }

  const handleGenerateForMonth = async (monthKey: string) => {
    setMonth(monthKey)
    await generateBills()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.06]">
          <div className="flex items-center gap-2.5">
            <div className="size-9 rounded-xl bg-gradient-to-tr from-cyan-500/20 to-blue-500/20 border border-cyan-500/30 text-cyan-400 flex items-center justify-center shrink-0">
              <CalendarRange className="size-5" />
            </div>
            <div>
              <DialogTitle className="text-base sm:text-lg font-bold text-white">
                Projeção de Fluxo de Caixa (6 Meses)
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400">
                Simulação de Entradas, Contas Fixas e Saldo Livre futuro para planejar gastos com segurança
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="py-2 space-y-3 max-h-[62vh] overflow-y-auto pr-1">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            {projections.map((p) => {
              const isSelected = p.monthKey === month
              const isCurrent = p.monthKey === currentKey
              const isPositive = p.projectedFreeBalance >= 0
              const retentionPct =
                p.projectedIncome > 0
                  ? Math.max(0, Math.min(100, Math.round((p.projectedFreeBalance / p.projectedIncome) * 100)))
                  : 0

              return (
                <div
                  key={p.monthKey}
                  className={`p-4 rounded-2xl border transition-all flex flex-col justify-between gap-3 shadow-md ${
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
                          <Badge variant="outline" className="text-[10px] py-0 px-2 bg-emerald-950/60 text-emerald-300 border-emerald-800">
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
                        <span className="text-[10px] text-slate-500 mt-0.5">
                          {p.incomeCount} fonte(s)
                        </span>
                      </div>

                      <div className="p-2.5 rounded-xl bg-slate-900/80 border border-slate-800 flex flex-col">
                        <span className="text-[11px] text-slate-400 flex items-center gap-1">
                          <TrendingDown className="size-3 text-rose-400" />
                          <span>Saídas Previstas</span>
                        </span>
                        <span className="font-bold font-mono text-slate-200 text-sm mt-1">
                          −{formatBRL(p.projectedExpense)}
                        </span>
                        <span className="text-[10px] text-slate-500 mt-0.5">
                          {p.expenseCount} conta(s)
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
                  </div>

                  {/* Month Actions */}
                  <div className="flex items-center justify-between pt-2.5 border-t border-white/[0.06] text-xs gap-2">
                    {!p.isGenerated ? (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleGenerateForMonth(p.monthKey)}
                        className="text-[11px] text-amber-400 hover:text-amber-300 hover:bg-amber-500/10 h-8 px-2.5 gap-1 rounded-lg"
                      >
                        <Zap className="size-3" /> Gerar Lançamentos
                      </Button>
                    ) : (
                      <span className="text-[11px] text-slate-500 font-medium">Lançamentos sincronizados</span>
                    )}

                    <Button
                      size="sm"
                      onClick={() => handleGoToMonth(p.monthKey)}
                      className="text-xs h-8 ml-auto bg-slate-800 hover:bg-slate-700 text-slate-200 hover:text-white gap-1.5 rounded-lg px-3 cursor-pointer shadow-sm"
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
            * As previsões usam seu salário cadastrado e seus moldes ativos de receitas e despesas.
          </p>
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} className="rounded-xl text-xs h-9 shrink-0">
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
