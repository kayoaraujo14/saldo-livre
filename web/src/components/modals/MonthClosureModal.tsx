import { useState, useMemo } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, monthLabel, round2 } from '@/lib/utils'
import {
  TrendingUp,
  PiggyBank,
  CheckCircle2,
  Sparkles,
  Check,
} from 'lucide-react'

interface MonthClosureModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

const INVESTMENT_BOXES = [
  'Reserva de Emergência',
  'Renda Fixa (CDB / Tesouro / LCI / LCA)',
  'Ações & Fundos Imobiliários (FIIs)',
  'Criptomoedas & Ativos Digitais',
  'Outros Investimentos',
]

export function MonthClosureModal({ open, onOpenChange }: MonthClosureModalProps) {
  const { summary, month, saveMonthClosure, me } = useFinance()

  const closedMonth = summary?.pendingClosureMonth || ''
  const totalSurplus = summary?.pendingClosureSurplus || 0

  const [mode, setMode] = useState<'keep_in_account' | 'invest_reserve' | 'custom_split'>('keep_in_account')
  const [selectedSubcategory, setSelectedSubcategory] = useState<string>('Reserva de Emergência')
  const [carryoverInput, setCarryoverInput] = useState<string>('')
  const [investedInput, setInvestedInput] = useState<string>('')
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false)

  const carryoverVal = useMemo(() => {
    if (mode === 'keep_in_account') return totalSurplus
    if (mode === 'invest_reserve') return 0
    return parseFloat(carryoverInput.replace(',', '.')) || 0
  }, [mode, totalSurplus, carryoverInput])

  const investedVal = useMemo(() => {
    if (mode === 'invest_reserve') return totalSurplus
    if (mode === 'keep_in_account') return 0
    return parseFloat(investedInput.replace(',', '.')) || 0
  }, [mode, totalSurplus, investedInput])

  const allocatedSum = round2(carryoverVal + investedVal)
  const remaining = round2(totalSurplus - allocatedSum)
  const isExact = Math.abs(remaining) < 0.01

  if (!open || totalSurplus <= 0 || !closedMonth) return null

  const handleConfirm = async () => {
    if (!isExact || !me || isSubmitting) return
    setIsSubmitting(true)
    try {
      await saveMonthClosure({
        id: `cls_${closedMonth}_${me.uid}`,
        uid: me.uid,
        closedMonth,
        targetMonth: month,
        totalSurplus,
        carryoverToNextMonth: carryoverVal,
        investedAmount: investedVal,
        cushionAmount: 0,
        investmentSubcategory: investedVal > 0 ? selectedSubcategory : null,
        closedAt: Date.now(),
      })
      onOpenChange(false)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg bg-slate-950 border border-emerald-500/30 text-slate-100 shadow-2xl p-6 rounded-2xl animate-in fade-in zoom-in-95">
        <DialogHeader className="space-y-2">
          <div className="flex items-center gap-2.5 text-emerald-400">
            <span className="p-2 rounded-xl bg-emerald-500/15 border border-emerald-500/30">
              <Sparkles className="size-5" />
            </span>
            <DialogTitle className="text-xl font-bold tracking-tight text-white">
              Fechamento de Mês
            </DialogTitle>
          </div>
          <DialogDescription className="text-sm text-slate-300 leading-relaxed">
            Em <strong className="text-white">{monthLabel(closedMonth)}</strong> sobraram{' '}
            <strong className="text-emerald-400 font-semibold">{formatBRL(totalSurplus)}</strong>!
            Como você prefere destinar essa sobra?
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3.5 my-3">
          {/* Opção 1: Deixar na Conta Corrente */}
          <button
            type="button"
            onClick={() => setMode('keep_in_account')}
            className={`w-full text-left p-4 rounded-xl border transition-all cursor-pointer flex items-start gap-3.5 ${
              mode === 'keep_in_account'
                ? 'bg-emerald-500/15 border-emerald-500/60 shadow-md ring-1 ring-emerald-500/30'
                : 'bg-slate-900/60 border-white/5 hover:border-white/15'
            }`}
          >
            <div className={`p-2 rounded-lg shrink-0 mt-0.5 ${mode === 'keep_in_account' ? 'bg-cyan-500/20 text-cyan-300' : 'bg-slate-800 text-slate-400'}`}>
              <TrendingUp className="size-5" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2">
                <span className={`text-sm font-bold ${mode === 'keep_in_account' ? 'text-white' : 'text-slate-200'}`}>
                  Deixar na Conta Corrente
                </span>
                {mode === 'keep_in_account' && (
                  <span className="text-xs font-bold text-emerald-400 font-mono">
                    {formatBRL(totalSurplus)}
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-400 mt-1 leading-normal">
                Soma ao seu Saldo Livre de <strong className="text-slate-300">{monthLabel(month)}</strong> para continuar disponível para compras e pagamentos do dia a dia.
              </p>
            </div>
          </button>

          {/* Opção 2: Guardar em Investimentos / Reserva */}
          <button
            type="button"
            onClick={() => setMode('invest_reserve')}
            className={`w-full text-left p-4 rounded-xl border transition-all cursor-pointer flex items-start gap-3.5 ${
              mode === 'invest_reserve'
                ? 'bg-emerald-500/15 border-emerald-500/60 shadow-md ring-1 ring-emerald-500/30'
                : 'bg-slate-900/60 border-white/5 hover:border-white/15'
            }`}
          >
            <div className={`p-2 rounded-lg shrink-0 mt-0.5 ${mode === 'invest_reserve' ? 'bg-emerald-500/20 text-emerald-300' : 'bg-slate-800 text-slate-400'}`}>
              <PiggyBank className="size-5" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2">
                <span className={`text-sm font-bold ${mode === 'invest_reserve' ? 'text-white' : 'text-slate-200'}`}>
                  Guardar em Investimentos
                </span>
                {mode === 'invest_reserve' && (
                  <span className="text-xs font-bold text-emerald-400 font-mono">
                    {formatBRL(totalSurplus)}
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-400 mt-1 leading-normal">
                Protege o valor guardando diretamente em uma Caixinha ou Aplicação financeira, sem inflar o saldo diário.
              </p>
            </div>
          </button>

          {/* Seletor de Caixinha quando guardar em investimento */}
          {(mode === 'invest_reserve' || (mode === 'custom_split' && investedVal > 0)) && (
            <div className="p-3.5 rounded-xl bg-slate-900/80 border border-white/10 space-y-2 animate-in fade-in slide-in-from-top-1">
              <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
                Caixinha de Destino do Aporte:
              </span>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                {INVESTMENT_BOXES.map((box) => {
                  const isSelected = selectedSubcategory === box
                  return (
                    <button
                      key={box}
                      type="button"
                      onClick={() => setSelectedSubcategory(box)}
                      className={`text-left text-xs px-2.5 py-2 rounded-lg border transition-all flex items-center justify-between gap-1.5 cursor-pointer ${
                        isSelected
                          ? 'bg-emerald-500/20 border-emerald-500/50 text-emerald-200 font-semibold'
                          : 'bg-slate-800/50 border-white/5 text-slate-300 hover:bg-slate-800'
                      }`}
                    >
                      <span className="truncate">{box}</span>
                      {isSelected && <Check className="size-3.5 text-emerald-400 shrink-0" />}
                    </button>
                  )
                })}
              </div>
            </div>
          )}

          {/* Toggle para Divisão Personalizada */}
          <div className="pt-1 flex justify-center">
            <button
              type="button"
              onClick={() => {
                if (mode === 'custom_split') {
                  setMode('keep_in_account')
                } else {
                  const half = round2(totalSurplus / 2)
                  const otherHalf = round2(totalSurplus - half)
                  setCarryoverInput(half.toFixed(2).replace('.', ','))
                  setInvestedInput(otherHalf.toFixed(2).replace('.', ','))
                  setMode('custom_split')
                }
              }}
              className="text-xs text-slate-400 hover:text-amber-300 transition-colors font-medium cursor-pointer underline decoration-dotted"
            >
              {mode === 'custom_split'
                ? '← Voltar às opções simples'
                : 'Prefiro dividir os valores (Personalizado)'}
            </button>
          </div>

          {/* Entradas de Divisão Personalizada */}
          {mode === 'custom_split' && (
            <div className="p-3.5 rounded-xl bg-slate-900/80 border border-white/10 space-y-3 animate-in fade-in slide-in-from-top-1">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-[11px] font-bold text-cyan-300 uppercase block mb-1">
                    Conta Corrente (R$):
                  </label>
                  <input
                    type="text"
                    value={carryoverInput}
                    onChange={(e) => setCarryoverInput(e.target.value)}
                    placeholder="0,00"
                    className="w-full bg-slate-950 border border-white/15 focus:border-cyan-400 rounded-lg px-3 py-1.5 text-xs text-white outline-none font-mono"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-bold text-emerald-300 uppercase block mb-1">
                    Guardar / Investir (R$):
                  </label>
                  <input
                    type="text"
                    value={investedInput}
                    onChange={(e) => setInvestedInput(e.target.value)}
                    placeholder="0,00"
                    className="w-full bg-slate-950 border border-white/15 focus:border-emerald-400 rounded-lg px-3 py-1.5 text-xs text-white outline-none font-mono"
                  />
                </div>
              </div>
              <div className="flex items-center justify-between text-xs pt-1 border-t border-white/5">
                <span className="text-slate-400">Total alocado: {formatBRL(allocatedSum)}</span>
                <span className={isExact ? 'text-emerald-400 font-bold' : 'text-amber-400 font-bold'}>
                  {isExact ? '✅ 100% distribuído' : remaining > 0 ? `Faltam ${formatBRL(remaining)}` : `Excesso de ${formatBRL(Math.abs(remaining))}`}
                </span>
              </div>
            </div>
          )}
        </div>

        <DialogFooter className="flex flex-col sm:flex-row gap-2 sm:gap-0 sm:justify-between pt-2">
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            className="text-slate-400 hover:text-white text-xs cursor-pointer"
          >
            Decidir mais tarde
          </Button>

          <Button
            type="button"
            disabled={!isExact || isSubmitting}
            onClick={handleConfirm}
            className="bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs px-5 h-9 rounded-xl shadow-lg cursor-pointer disabled:opacity-40"
          >
            <CheckCircle2 className="size-4 mr-1.5" />
            {isSubmitting ? 'Salvando...' : 'Confirmar Fechamento'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
