import { useState, useEffect, useMemo } from 'react'
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
import { type Transaction } from '@/types'
import { createRefundForTransaction, effectiveAmount, rescaleSplit } from '@/lib/finance'
import { formatBRL, round2 } from '@/lib/utils'
import {
  RotateCcw,
  CreditCard,
  Calendar,
  Tag,
  AlertTriangle,
  Loader2,
  Users2,
  Sparkles,
} from 'lucide-react'

interface QuickRefundModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  transaction: Transaction | null
  onSuccess?: (createdRefund: Transaction) => void
}

export function QuickRefundModal({
  open,
  onOpenChange,
  transaction,
  onSuccess,
}: QuickRefundModalProps) {
  const { addTransaction, cards, me } = useFinance()

  const [refundAmountStr, setRefundAmountStr] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successNotice, setSuccessNotice] = useState<string | null>(null)

  const originalTotal = useMemo(() => {
    if (!transaction) return 0
    return Math.abs(effectiveAmount(transaction))
  }, [transaction])

  // Quando abre ou muda a transação, pré-preenche com o valor total original
  useEffect(() => {
    if (transaction && open) {
      setRefundAmountStr(originalTotal > 0 ? originalTotal.toFixed(2).replace('.', ',') : '')
      setErrorMessage(null)
      setSuccessNotice(null)
      setIsSubmitting(false)
    }
  }, [transaction, originalTotal, open])

  const parsedAmount = Math.abs(parseFloat(refundAmountStr.replace(',', '.'))) || 0
  const isFullRefund = Math.abs(parsedAmount - originalTotal) < 0.01
  const isOverAmount = parsedAmount > originalTotal + 0.001

  // Informações do cartão
  const card = cards.find((c) => c.id === transaction?.cardId)
  const cardName = card ? card.cardName : 'Cartão de Crédito'

  // Proporções de rateio calculadas diretamente do rescaleSplit para garantir proporção e soma exata de 100%
  const previewAllocations = useMemo(() => {
    if (!transaction) return []
    return rescaleSplit(transaction, originalTotal, me?.uid).allocations
  }, [transaction, originalTotal, me?.uid])

  const hasSplit = previewAllocations.length > 1 || transaction?.splitType === 'split_50' || Boolean(transaction?.assignedFriendUid)

  // Calcular impacto estimado em tempo real com base no valor digitado
  const refundPreview = useMemo(() => {
    if (!transaction || !me || parsedAmount <= 0) return null
    try {
      const draft = createRefundForTransaction(transaction, parsedAmount, me)
      const myImpact = draft.userImpact?.[me.uid] ?? -parsedAmount
      const reliefMyPocket = Math.abs(myImpact)
      const reliefOthers = Math.max(0, round2(parsedAmount - reliefMyPocket))
      return {
        reliefMyPocket,
        reliefOthers,
        allocations: draft.allocations || [],
      }
    } catch {
      return null
    }
  }, [transaction, parsedAmount, me])

  if (!transaction) return null

  const handleConfirmRefund = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)

    if (parsedAmount <= 0) {
      setErrorMessage('Informe um valor de estorno maior que zero.')
      return
    }

    if (parsedAmount > originalTotal * 1.5) {
      setErrorMessage(`O valor do estorno (${formatBRL(parsedAmount)}) excede muito a compra original (${formatBRL(originalTotal)}).`)
      return
    }

    if (!me) {
      setErrorMessage('Usuário não autenticado.')
      return
    }

    setIsSubmitting(true)
    try {
      const refundTx = createRefundForTransaction(transaction, parsedAmount, me)
      // Remove o id temporário para que o addTransaction gere o id final no Firestore
      const { id: _tempId, ...newTxPayload } = refundTx
      await addTransaction(newTxPayload)

      setSuccessNotice(`Estorno de ${formatBRL(parsedAmount)} registrado com sucesso!`)
      if (onSuccess) {
        onSuccess(refundTx)
      }

      setTimeout(() => {
        onOpenChange(false)
      }, 700)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Falha ao registrar estorno.'
      setErrorMessage(msg)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg w-full max-h-[92vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-cyan-500/20 rounded-2xl shadow-2xl">
        <form onSubmit={handleConfirmRefund} className="space-y-4">
          {/* Header */}
          <DialogHeader className="space-y-1 pb-2 border-b border-white/[0.06]">
            <DialogTitle className="text-base sm:text-lg font-bold text-white flex items-center gap-2">
              <div className="size-8 rounded-xl border border-cyan-500/30 bg-gradient-to-tr from-cyan-500/20 to-teal-500/20 flex items-center justify-center text-cyan-400">
                <RotateCcw className="size-4" />
              </div>
              <span>Registrar Estorno Desta Compra</span>
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-400">
              Vincule o estorno diretamente a esta transação. Categoria, fatura e divisão de participantes são replicadas automaticamente.
            </DialogDescription>
          </DialogHeader>

          {/* Card Resumo da Compra Original */}
          <div className="p-3.5 rounded-xl bg-slate-900/90 border border-slate-800 space-y-2.5">
            <div className="flex items-start justify-between gap-2">
              <div className="space-y-0.5">
                <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500 block">
                  Compra Original
                </span>
                <h4 className="text-sm font-bold text-white line-clamp-1">
                  {transaction.description}
                </h4>
              </div>
              <div className="text-right shrink-0">
                <span className="text-[10px] text-slate-400 block">Valor original</span>
                <span className="text-sm font-black font-mono text-white">
                  {formatBRL(originalTotal)}
                </span>
              </div>
            </div>

            {/* Metadados replicados */}
            <div className="flex flex-wrap items-center gap-2 pt-1 text-[11px] text-slate-300">
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-slate-800 text-slate-300 border border-slate-700">
                <Tag className="size-3 text-cyan-400" />
                {transaction.category}
                {transaction.subcategory ? ` • ${transaction.subcategory}` : ''}
              </span>

              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-slate-800 text-slate-300 border border-slate-700">
                <CreditCard className="size-3 text-cyan-400" />
                {cardName}
              </span>

              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-slate-800 text-slate-300 border border-slate-700">
                <Calendar className="size-3 text-cyan-400" />
                Fatura: {transaction.competenceMonth}
              </span>
            </div>

            {/* Participantes / Rateio que serão replicados */}
            {hasSplit && (
              <div className="pt-2 border-t border-slate-800/80 space-y-1">
                <div className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-400">
                  <Users2 className="size-3 text-cyan-400" />
                  <span>Rateio Original Replicado Automaticamente:</span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5 pt-0.5">
                  {previewAllocations.map((a, i) => {
                    const sharePct = originalTotal > 0 ? round2((Math.abs(a.amount) / originalTotal) * 100) : 0
                    const currentPartAmount = parsedAmount > 0 ? round2((parsedAmount * sharePct) / 100) : Math.abs(a.amount)
                    return (
                      <div
                        key={i}
                        className="flex items-center justify-between text-[11px] px-2 py-1 rounded-lg bg-slate-950/60 border border-white/[0.05]"
                      >
                        <span className="text-slate-300 font-medium truncate mr-2">{a.name}</span>
                        <span className="text-cyan-300 font-mono text-[10px] shrink-0 font-bold">
                          {sharePct}% ({formatBRL(currentPartAmount)})
                        </span>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
          </div>

          {/* Campo Pergunta: Qual o valor do estorno? */}
          <div className="space-y-2 p-3.5 rounded-xl bg-cyan-950/20 border border-cyan-500/30">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-cyan-200 flex items-center gap-1.5">
                <Sparkles className="size-3.5 text-cyan-400" />
                <span>Qual o valor do estorno?</span>
              </label>

              {!isFullRefund && (
                <button
                  type="button"
                  onClick={() => setRefundAmountStr(originalTotal.toFixed(2).replace('.', ','))}
                  className="text-[11px] text-cyan-400 hover:text-cyan-200 underline font-semibold cursor-pointer"
                >
                  Estorno Total ({formatBRL(originalTotal)})
                </button>
              )}
            </div>

            {/* Input Hero de Valor */}
            <div className="flex items-center px-4 py-2 rounded-xl bg-slate-900 border border-cyan-500/40 focus-within:border-cyan-400 focus-within:ring-2 focus-within:ring-cyan-500/20 transition-all">
              <span className="text-xl sm:text-2xl font-black font-mono text-cyan-400 mr-2 select-none">
                − R$
              </span>
              <input
                type="text"
                inputMode="decimal"
                value={refundAmountStr}
                onChange={(e) => setRefundAmountStr(e.target.value)}
                placeholder="0,00"
                className="w-full bg-transparent border-none outline-none text-2xl sm:text-3xl font-black font-mono text-white tracking-tight placeholder:text-slate-600"
                required
                autoFocus
              />
            </div>

            {/* Alerta se o valor for maior que o original */}
            {isOverAmount && (
              <p className="text-[11px] text-amber-300 flex items-center gap-1 pt-0.5">
                <AlertTriangle className="size-3 shrink-0" />
                <span>Atenção: o valor digitado é maior que o valor original da compra.</span>
              </p>
            )}

            {/* Preview do Impacto Financeiro Calculado */}
            {refundPreview && parsedAmount > 0 && (
              <div className="pt-2 border-t border-cyan-500/20 grid grid-cols-2 gap-2 text-[11px]">
                <div className="p-2 rounded-lg bg-slate-900/90 border border-cyan-800/40">
                  <span className="text-slate-400 block text-[10px] mb-0.5">Alívio no seu saldo:</span>
                  <strong className="text-xs font-mono font-bold text-cyan-300">
                    +{formatBRL(refundPreview.reliefMyPocket)}
                  </strong>
                </div>

                <div className="p-2 rounded-lg bg-slate-900/90 border border-slate-800">
                  <span className="text-slate-400 block text-[10px] mb-0.5">
                    {hasSplit ? 'Abatimento de outros participantes:' : 'Abatimento na fatura:'}
                  </span>
                  <strong className="text-xs font-mono font-bold text-amber-300">
                    {hasSplit
                      ? `−${formatBRL(refundPreview.reliefOthers)}`
                      : `−${formatBRL(parsedAmount)}`}
                  </strong>
                </div>
              </div>
            )}
          </div>

          {/* Mensagem de Erro / Sucesso */}
          {errorMessage && (
            <div className="p-3 rounded-xl bg-rose-950/50 border border-rose-500/40 text-xs text-rose-300 flex items-start gap-2 animate-in fade-in">
              <AlertTriangle className="size-4 text-rose-400 shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          {successNotice && (
            <div className="p-3 rounded-xl bg-emerald-950/50 border border-emerald-500/40 text-xs text-emerald-300 flex items-center gap-2 animate-in fade-in">
              <RotateCcw className="size-4 text-emerald-400 shrink-0" />
              <span>{successNotice}</span>
            </div>
          )}

          {/* Footer */}
          <DialogFooter className="flex-col-reverse sm:flex-row items-center justify-between gap-2 pt-3 border-t border-white/[0.06]">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={isSubmitting}
              onClick={() => onOpenChange(false)}
              className="flex-1 sm:flex-initial h-9 text-xs border-slate-800 text-slate-300 hover:text-white rounded-xl"
            >
              Cancelar
            </Button>

            <Button
              type="submit"
              size="sm"
              disabled={isSubmitting || parsedAmount <= 0}
              className="flex-1 sm:flex-initial h-9 text-xs text-white font-bold bg-cyan-600 hover:bg-cyan-500 shadow-md shadow-cyan-950/50 rounded-xl cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="size-3.5 animate-spin" />
                  <span>Registrando Estorno...</span>
                </>
              ) : (
                <>
                  <RotateCcw className="size-3.5" />
                  <span>Confirmar Estorno ({parsedAmount > 0 ? formatBRL(parsedAmount) : 'R$ 0,00'})</span>
                </>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
