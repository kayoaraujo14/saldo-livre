import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, stripInstallmentSuffix } from '@/lib/utils'
import { effectiveAmount } from '@/lib/finance'
import type { Transaction } from '@/types'
import { AlertTriangle, Layers, Trash2, ArrowRight } from 'lucide-react'

interface DeleteInstallmentDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  tx: Transaction | null
  onDeleted?: () => void
}

export function DeleteInstallmentDialog({
  open,
  onOpenChange,
  tx,
  onDeleted,
}: DeleteInstallmentDialogProps) {
  const { transactions, deleteTransaction, deleteInstallmentGroup } = useFinance()
  const [loading, setLoading] = useState(false)

  if (!tx) return null

  const groupId = tx.installmentGroupId
  const isInstallment = Boolean(groupId)
  const groupTxs = isInstallment
    ? transactions.filter((t) => t.installmentGroupId === groupId)
    : [tx]
  const totalCount = groupTxs.length || tx.installmentsTotal || 1
  const groupTotal = groupTxs.reduce((s, t) => s + effectiveAmount(t), 0)
  const curAmount = effectiveAmount(tx)
  const baseDesc = stripInstallmentSuffix(tx.description)

  const handleDeleteSingle = async () => {
    setLoading(true)
    try {
      await deleteTransaction(tx.id)
      onOpenChange(false)
      onDeleted?.()
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteAll = async () => {
    if (!groupId) {
      await handleDeleteSingle()
      return
    }
    setLoading(true)
    try {
      await deleteInstallmentGroup(groupId)
      onOpenChange(false)
      onDeleted?.()
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-2 border-b border-white/[0.06]">
          <div className="flex items-center gap-2.5 text-rose-400">
            <div className="size-9 rounded-xl bg-gradient-to-tr from-rose-500/20 to-red-500/20 border border-rose-500/30 flex items-center justify-center shrink-0">
              <AlertTriangle className="size-5" />
            </div>
            <div>
              <DialogTitle className="text-base sm:text-lg font-bold text-white">
                Excluir Compra Parcelada
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400">
                Esta despesa faz parte de uma série parcelada conectada
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {/* Card de informações do parcelamento */}
        <div className="p-4 rounded-2xl bg-slate-950/70 border border-white/[0.08] space-y-3 shadow-md">
          <div className="flex items-start justify-between gap-2">
            <div>
              <span className="text-sm font-bold text-white block">{baseDesc}</span>
              <span className="text-xs text-slate-400">
                Parcela selecionada: <strong className="text-slate-200">{tx.installmentNumber || 1}</strong> de <strong className="text-slate-200">{tx.installmentsTotal || totalCount}</strong>
              </span>
            </div>
            <Badge
              variant="outline"
              className="text-[10px] bg-violet-500/10 text-violet-300 border-violet-500/30 gap-1 shrink-0 font-mono"
            >
              <Layers className="size-3" />
              {totalCount} parcelas
            </Badge>
          </div>

          <div className="grid grid-cols-2 gap-2 pt-2 border-t border-slate-800 text-xs">
            <div>
              <span className="text-[11px] text-slate-400 block">Valor desta parcela:</span>
              <span className="font-bold font-mono text-slate-200 text-sm">{formatBRL(curAmount)}</span>
            </div>
            <div className="text-right">
              <span className="text-[11px] text-slate-400 block">Total do parcelamento:</span>
              <span className="font-bold font-mono text-amber-400 text-sm">{formatBRL(groupTotal)}</span>
            </div>
          </div>
        </div>

        <div className="text-xs text-slate-300 space-y-2.5">
          <p className="font-semibold text-slate-200">Como você deseja realizar a exclusão?</p>
          <div className="space-y-2">
            {/* Opção 1: Excluir TODAS de uma vez */}
            <button
              type="button"
              disabled={loading}
              onClick={handleDeleteAll}
              className="w-full p-3.5 rounded-2xl border border-rose-500/30 bg-rose-500/10 hover:bg-rose-500/20 text-rose-200 text-left transition-all cursor-pointer flex items-center justify-between gap-3 group active:scale-[0.99] shadow-sm"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-1.5 font-bold text-xs text-rose-300">
                  <Trash2 className="size-3.5" />
                  <span>Excluir TODAS as {totalCount} parcelas</span>
                </div>
                <p className="text-[11px] text-rose-300/80 leading-relaxed">
                  Remove de uma só vez todos os lançamentos passados e futuros desta compra ({formatBRL(groupTotal)}).
                </p>
              </div>
              <ArrowRight className="size-4 text-rose-400 shrink-0 opacity-70 group-hover:opacity-100 group-hover:translate-x-0.5 transition-all" />
            </button>

            {/* Opção 2: Excluir apenas ESTA parcela */}
            <button
              type="button"
              disabled={loading}
              onClick={handleDeleteSingle}
              className="w-full p-3.5 rounded-2xl border border-slate-800 bg-slate-900/60 hover:bg-slate-800/80 text-slate-200 text-left transition-all cursor-pointer flex items-center justify-between gap-3 group active:scale-[0.99] shadow-sm"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-1.5 font-semibold text-xs text-slate-200">
                  <span>Excluir APENAS esta parcela ({tx.installmentNumber || 1}/{tx.installmentsTotal || totalCount})</span>
                </div>
                <p className="text-[11px] text-slate-400 leading-relaxed">
                  Mantém as outras parcelas intactas no histórico e nos meses futuros.
                </p>
              </div>
              <ArrowRight className="size-4 text-slate-400 shrink-0 opacity-70 group-hover:opacity-100 group-hover:translate-x-0.5 transition-all" />
            </button>
          </div>
        </div>

        <DialogFooter className="pt-2 border-t border-white/[0.06]">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={loading}
            onClick={() => onOpenChange(false)}
            className="w-full text-xs text-slate-400 hover:text-white rounded-xl h-9"
          >
            Cancelar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
