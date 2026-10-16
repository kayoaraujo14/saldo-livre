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
      <DialogContent className="sm:max-w-3xl">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
              <CalendarRange className="size-5" />
            </div>
            <div>
              <DialogTitle>Projeção e Fluxo de Caixa dos Próximos 6 Meses</DialogTitle>
              <DialogDescription>
                Simulação de Entradas, Contas Fixas e Saldo Livre futuro para planejar gastos com segurança
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="py-3 space-y-3 max-h-[65vh] overflow-y-auto pr-1">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
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
                  className={`p-4 rounded-xl border transition-all flex flex-col justify-between gap-3 ${
                    isSelected
                      ? 'border-emerald-500/50 bg-emerald-950/20 shadow-md ring-1 ring-emerald-500/30'
                      : 'border-slate-800 bg-slate-900/60 hover:border-slate-700/80'
                  }`}
                >
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5">
                        <span className="font-bold text-slate-100 text-sm">{p.label}</span>
                        {isCurrent && (
                          <Badge variant="outline" className="text-[10px] py-0 px-1.5 bg-emerald-950/60 text-emerald-300 border-emerald-800">
                            Mês Atual
                          </Badge>
                        )}
                      </div>

                      {p.isGenerated ? (
                        <span className="text-[10px] text-emerald-400 flex items-center gap-1 font-medium">
                          <CheckCircle2 className="size-3" /> Gerado
                        </span>
                      ) : (
                        <span className="text-[10px] text-slate-400 flex items-center gap-1">
                          <Sparkles className="size-3 text-amber-400" /> Projetado
                        </span>
                      )}
                    </div>

                    {/* Breakdown In vs Out */}
                    <div className="grid grid-cols-2 gap-2 text-xs pt-1">
                      <div className="p-2 rounded-lg bg-slate-950/60 border border-slate-800/80 flex flex-col">
                        <span className="text-[11px] text-slate-400">Entradas Previstas:</span>
                        <span className="font-bold text-emerald-400 text-sm mt-0.5">
                          +{formatBRL(p.projectedIncome)}
                        </span>
                        <span className="text-[10px] text-slate-500 mt-0.5">
                          {p.incomeCount} fonte(s)
                        </span>
                      </div>

                      <div className="p-2 rounded-lg bg-slate-950/60 border border-slate-800/80 flex flex-col">
                        <span className="text-[11px] text-slate-400">Saídas Previstas:</span>
                        <span className="font-bold text-slate-200 text-sm mt-0.5">
                          −{formatBRL(p.projectedExpense)}
                        </span>
                        <span className="text-[10px] text-slate-500 mt-0.5">
                          {p.expenseCount} conta(s)
                        </span>
                      </div>
                    </div>

                    {/* Projected Free Balance & Daily Limit */}
                    <div className="p-2.5 rounded-lg bg-slate-950/80 border border-slate-800/80 flex items-center justify-between">
                      <div>
                        <span className="text-[11px] text-slate-400 block">Saldo Livre Projetado:</span>
                        <span
                          className={`text-base font-extrabold tracking-tight ${
                            isPositive ? 'text-emerald-400' : 'text-rose-400'
                          }`}
                        >
                          {formatBRL(p.projectedFreeBalance)}
                        </span>
                      </div>

                      <div className="text-right">
                        <span className="text-[11px] text-slate-400 block">Limite Diário:</span>
                        <span className="text-xs font-bold text-cyan-300">
                          {p.projectedDailyLimit != null ? `${formatBRL(p.projectedDailyLimit)}/dia` : '—'}
                        </span>
                      </div>
                    </div>

                    {/* Retention Progress Bar */}
                    <div className="space-y-1">
                      <div className="flex justify-between text-[10px] text-slate-400">
                        <span>Retenção de Saldo</span>
                        <span>{retentionPct}% livre</span>
                      </div>
                      <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-emerald-500 rounded-full transition-all"
                          style={{ width: `${retentionPct}%` }}
                        />
                      </div>
                    </div>
                  </div>

                  {/* Month Actions */}
                  <div className="flex items-center justify-between pt-2 border-t border-white/[0.06] text-xs">
                    {!p.isGenerated && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleGenerateForMonth(p.monthKey)}
                        className="text-[11px] text-amber-400 hover:text-amber-300 hover:bg-amber-500/10 h-7 px-2 gap-1"
                      >
                        <Zap className="size-3" /> Gerar Lançamentos
                      </Button>
                    )}
                    {p.isGenerated && (
                      <span className="text-[11px] text-slate-500">Lançamentos ativos</span>
                    )}

                    <Button
                      size="sm"
                      onClick={() => handleGoToMonth(p.monthKey)}
                      className="text-xs h-7 ml-auto bg-slate-800 hover:bg-slate-700 text-slate-200 hover:text-white gap-1"
                    >
                      <span>Visualizar</span>
                      <ArrowRight className="size-3" />
                    </Button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        <DialogFooter className="flex items-center justify-between sm:justify-between w-full">
          <p className="text-[11px] text-slate-500">
            * As previsões usam seu salário cadastrado e seus moldes ativos de receitas e despesas.
          </p>
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
