import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { listReceivables } from '@/lib/finance'
import { formatBRL, formatShortDate } from '@/lib/utils'
import { HandCoins, Check, User, ArrowUpRight, ArrowDownLeft, ShieldCheck } from 'lucide-react'

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
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
              <HandCoins className="size-5" />
            </div>
            <div>
              <DialogTitle>Acertos & Reembolsos do Mês</DialogTitle>
              <DialogDescription>
                Gastos passados no seu cartão para terceiros ou divididos com o parceiro
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="flex items-center gap-2 p-1 rounded-xl bg-slate-900 border border-slate-800">
          <button
            type="button"
            onClick={() => setActiveTab('receivable')}
            className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-medium transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
              activeTab === 'receivable' ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <ArrowDownLeft className="size-3.5 text-emerald-400" />
            A Receber ({formatBRL(totalReceivable)})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('payable')}
            className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-medium transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
              activeTab === 'payable' ? 'bg-orange-500/20 text-orange-300 border border-orange-500/30' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <ArrowUpRight className="size-3.5 text-orange-400" />
            Eu Devo ({formatBRL(totalPayable)})
          </button>
        </div>

        <div className="py-2 space-y-2.5 max-h-[55vh] overflow-y-auto pr-1">
          {activeTab === 'receivable' ? (
            receivables.length === 0 ? (
              <div className="text-center py-10 text-slate-400 space-y-2">
                <ShieldCheck className="size-8 mx-auto text-emerald-400/60" />
                <p className="text-sm font-medium text-slate-300">Tudo em dia!</p>
                <p className="text-xs text-slate-500">Nenhum valor pendente de reembolso neste mês.</p>
              </div>
            ) : (
              receivables.map((item) => (
                <div
                  key={`${item.transactionId}_${item.allocationIndex}`}
                  className="p-3.5 rounded-xl border border-slate-800 bg-slate-900/60 hover:border-slate-700/80 transition-all flex items-center justify-between gap-3"
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-100 text-sm">{item.description}</span>
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

                    <div className="flex items-center gap-2 text-xs text-slate-400">
                      <span>Vencimento: {formatShortDate(item.dueDate)}</span>
                      {item.cardName && (
                        <>
                          <span>·</span>
                          <span className="text-slate-300">{item.cardName}</span>
                        </>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <span className="font-semibold text-emerald-400 text-sm whitespace-nowrap">
                      +{formatBRL(item.amount)}
                    </span>
                    <Button
                      size="sm"
                      onClick={() => handleSettle(item.transactionId, item.allocationIndex)}
                      className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs h-8 px-2.5 gap-1 shadow-sm"
                    >
                      <Check className="size-3.5" /> Dar Baixa
                    </Button>
                  </div>
                </div>
              ))
            )
          ) : payables.length === 0 ? (
            <div className="text-center py-10 text-slate-400 space-y-2">
              <ShieldCheck className="size-8 mx-auto text-emerald-400/60" />
              <p className="text-sm font-medium text-slate-300">Você não deve nada!</p>
              <p className="text-xs text-slate-500">Nenhum lançamento no cartão do parceiro pendente de acerto.</p>
            </div>
          ) : (
            payables.map((item) => (
              <div
                key={`${item.transactionId}_${item.allocationIndex}`}
                className="p-3.5 rounded-xl border border-slate-800 bg-slate-900/60 flex items-center justify-between gap-3"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-slate-100 text-sm">{item.description}</span>
                    <Badge variant="outline" className="bg-orange-950/40 text-orange-300 border-orange-800 text-[10px]">
                      Cartão de {item.name}
                    </Badge>
                  </div>
                  <div className="text-xs text-slate-400">
                    Vence {formatShortDate(item.dueDate)} {item.cardName && `· ${item.cardName}`}
                  </div>
                </div>

                <div className="flex items-center gap-3">
                  <span className="font-semibold text-orange-400 text-sm whitespace-nowrap">
                    −{formatBRL(item.amount)}
                  </span>
                  <Badge variant="secondary" className="text-xs">
                    Já deduzido do Saldo
                  </Badge>
                </div>
              </div>
            ))
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
