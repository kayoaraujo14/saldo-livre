import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { listReceivables } from '@/lib/finance'
import { formatBRL, formatShortDate } from '@/lib/utils'
import { HandCoins, Check, User, ArrowUpRight, ArrowDownLeft, ShieldCheck, CreditCard } from 'lucide-react'

interface ReceivablesModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ReceivablesModal({ open, onOpenChange }: ReceivablesModalProps) {
  const { transactions, cards, me, month, markReimbursed } = useFinance()
  const [activeTab, setActiveTab] = useState<'receivable' | 'payable'>('receivable')

  if (!me) return null

  const { receivables, payables } = listReceivables(transactions, me.uid, month, cards)

  const totalReceivable = receivables.reduce((s, r) => s + r.amount, 0)
  const totalPayable = payables.reduce((s, p) => s + p.amount, 0)

  const handleSettle = async (txId: string, idx: number) => {
    await markReimbursed(txId, idx, true)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.06]">
          <div className="flex items-center gap-2.5">
            <div className="size-9 rounded-xl bg-gradient-to-tr from-cyan-500/20 to-emerald-500/20 border border-cyan-500/30 text-cyan-400 flex items-center justify-center shrink-0">
              <HandCoins className="size-5" />
            </div>
            <div>
              <DialogTitle className="text-base sm:text-lg font-bold text-white">
                Acertos & Reembolsos do Mês
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400">
                Gastos passados no seu cartão para terceiros ou divididos com o parceiro
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {/* Segmented Selector */}
        <div className="flex items-center gap-2 p-1 rounded-xl bg-slate-900/80 border border-slate-800">
          <button
            type="button"
            onClick={() => setActiveTab('receivable')}
            className={`flex-1 py-2 px-3 rounded-lg text-xs font-semibold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
              activeTab === 'receivable'
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <ArrowDownLeft className="size-3.5 text-emerald-400" />
            <span>A Receber ({formatBRL(totalReceivable)})</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('payable')}
            className={`flex-1 py-2 px-3 rounded-lg text-xs font-semibold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
              activeTab === 'payable'
                ? 'bg-orange-500/20 text-orange-300 border border-orange-500/30 shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <ArrowUpRight className="size-3.5 text-orange-400" />
            <span>Eu Devo ({formatBRL(totalPayable)})</span>
          </button>
        </div>

        {/* Content List */}
        <div className="py-1 space-y-2.5 max-h-[55vh] overflow-y-auto pr-1">
          {activeTab === 'receivable' ? (
            receivables.length === 0 ? (
              <div className="text-center py-12 text-slate-400 rounded-2xl bg-slate-950/60 border border-white/[0.06] space-y-2">
                <ShieldCheck className="size-8 mx-auto text-emerald-400/60" />
                <p className="text-sm font-semibold text-slate-200">Tudo em dia!</p>
                <p className="text-xs text-slate-500">Nenhum valor pendente de reembolso neste mês.</p>
              </div>
            ) : (
              receivables.map((item) => (
                <div
                  key={`${item.transactionId}_${item.allocationIndex}`}
                  className="p-4 rounded-2xl border border-white/[0.06] bg-slate-950/70 hover:border-slate-700 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-md"
                >
                  <div className="space-y-1.5">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-slate-100 text-sm">{item.description}</span>
                      <Badge
                        variant="outline"
                        className={
                          item.type === 'EXTERNAL'
                            ? 'bg-cyan-950/40 text-cyan-300 border-cyan-800 text-[10px]'
                            : 'bg-violet-950/40 text-violet-300 border-violet-800 text-[10px]'
                        }
                      >
                        <User className="size-2.5 mr-1" />
                        {item.name}
                      </Badge>
                    </div>

                    <div className="flex items-center gap-2 text-xs text-slate-400 flex-wrap">
                      <span>Vencimento: <strong className="text-slate-200">{formatShortDate(item.dueDate)}</strong></span>
                      {item.cardName && (
                        <>
                          <span>·</span>
                          <span className="flex items-center gap-1 text-slate-300">
                            <CreditCard className="size-3 text-slate-500" />
                            {item.cardName}
                          </span>
                        </>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-3 self-end sm:self-center shrink-0">
                    <span className="font-mono font-bold text-emerald-400 text-base whitespace-nowrap">
                      +{formatBRL(item.amount)}
                    </span>
                    <Button
                      size="sm"
                      onClick={() => handleSettle(item.transactionId, item.allocationIndex)}
                      className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs h-8 px-3 gap-1.5 rounded-xl font-semibold shadow-sm cursor-pointer"
                    >
                      <Check className="size-3.5" /> Dar Baixa
                    </Button>
                  </div>
                </div>
              ))
            )
          ) : payables.length === 0 ? (
            <div className="text-center py-12 text-slate-400 rounded-2xl bg-slate-950/60 border border-white/[0.06] space-y-2">
              <ShieldCheck className="size-8 mx-auto text-emerald-400/60" />
              <p className="text-sm font-semibold text-slate-200">Você não deve nada!</p>
              <p className="text-xs text-slate-500">Nenhum lançamento no cartão do parceiro pendente de acerto.</p>
            </div>
          ) : (
            payables.map((item) => (
              <div
                key={`${item.transactionId}_${item.allocationIndex}`}
                className="p-4 rounded-2xl border border-white/[0.06] bg-slate-950/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-md"
              >
                <div className="space-y-1.5">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-bold text-slate-100 text-sm">{item.description}</span>
                    <Badge variant="outline" className="bg-orange-950/40 text-orange-300 border-orange-800 text-[10px]">
                      Cartão de {item.name}
                    </Badge>
                  </div>
                  <div className="text-xs text-slate-400">
                    Vence <strong className="text-slate-200">{formatShortDate(item.dueDate)}</strong> {item.cardName && `· ${item.cardName}`}
                  </div>
                </div>

                <div className="flex items-center gap-3 self-end sm:self-center shrink-0">
                  <span className="font-mono font-bold text-orange-400 text-base whitespace-nowrap">
                    −{formatBRL(item.amount)}
                  </span>
                  <Badge variant="secondary" className="text-xs bg-slate-800 text-slate-300 border-slate-700">
                    Já deduzido do Saldo
                  </Badge>
                </div>
              </div>
            ))
          )}
        </div>

        <DialogFooter className="pt-2 border-t border-white/[0.06]">
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} className="rounded-xl text-xs h-9">
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
