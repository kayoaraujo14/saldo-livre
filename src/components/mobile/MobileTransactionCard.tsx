import { Badge } from '@/components/ui/badge'
import { type Transaction, type Card as CardType } from '@/types'
import { effectiveAmount, impactFor, detectSplit, getThirdPartyInfo } from '@/lib/finance'
import { formatBRL, formatShortDate } from '@/lib/utils'
import {
  CreditCard,
  Edit2,
  Trash2,
  RotateCcw,
  CheckCircle2,
  Clock,
  Layers,
  ArrowDownLeft,
} from 'lucide-react'

interface MobileTransactionCardProps {
  tx: Transaction
  currentUid: string
  allTransactions: Transaction[]
  cards: CardType[]
  onEdit: (tx: Transaction) => void
  onDelete: (tx: Transaction) => void
  onCycleStatus: (tx: Transaction) => void
}

export function MobileTransactionCard({
  tx,
  currentUid,
  allTransactions,
  cards,
  onEdit,
  onDelete,
  onCycleStatus,
}: MobileTransactionCardProps) {
  const effAmount = effectiveAmount(tx)
  const myImpact = impactFor(tx, currentUid, allTransactions)
  const split = detectSplit(tx, currentUid)
  const isRefund = tx.category === 'Estorno' || effAmount < 0 || tx.totalAmount < 0
  const isIncome = tx.type === 'income'
  const card = cards.find((c) => c.id === tx.cardId)
  const isInstallment = Boolean(tx.installmentGroupId)
  const thirdParty = getThirdPartyInfo(tx, currentUid)

  // Status visual configurations
  const getStatusBadge = () => {
    if (isIncome) {
      if (tx.status === 'paid') {
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
            <CheckCircle2 className="size-2.5" /> Na Conta
          </span>
        )
      }
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
          <Clock className="size-2.5" /> A Receber
        </span>
      )
    }

    if (tx.status === 'paid') {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
          <CheckCircle2 className="size-2.5" /> Pago
        </span>
      )
    }

    if (tx.status === 'pending') {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-500/15 text-amber-300 border border-amber-500/30">
          <Clock className="size-2.5" /> A Pagar
        </span>
      )
    }

    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-800 text-slate-300 border border-slate-700">
        Estimado
      </span>
    )
  }

  return (
    <div
      className={`glass-card rounded-2xl p-3.5 border transition-all relative overflow-hidden ${
        isRefund
          ? 'border-emerald-500/30 bg-emerald-950/10'
          : isIncome
            ? 'border-emerald-500/20 bg-emerald-950/5'
            : 'border-white/[0.07] hover:border-white/[0.12]'
      }`}
    >
      {/* Top row: Category & Date + Status */}
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2">
          {isRefund ? (
            <Badge className="bg-emerald-500/20 text-emerald-300 border-emerald-500/40 text-[10px] font-bold px-2 py-0.5">
              <RotateCcw className="size-2.5 mr-1" /> Estorno / Reembolso
            </Badge>
          ) : isIncome ? (
            <Badge className="bg-emerald-500/15 text-emerald-300 border-emerald-500/30 text-[10px] font-bold px-2 py-0.5">
              <ArrowDownLeft className="size-2.5 mr-1" /> {tx.subcategory ? `Receita › ${tx.subcategory}` : 'Receita'}
            </Badge>
          ) : (
            <span className="text-[11px] font-medium text-slate-300 bg-slate-800/80 px-2 py-0.5 rounded-md border border-slate-700/60 inline-flex items-center gap-1">
              <span>{tx.category}</span>
              {tx.subcategory && (
                <span className="text-emerald-400 font-normal">
                  › {tx.subcategory}
                </span>
              )}
            </span>
          )}

          <span className="text-[11px] text-slate-500 font-mono">
            {formatShortDate(tx.dueDate || tx.competenceMonth)}
          </span>
        </div>

        {/* Status Pill (Tap to toggle) */}
        <button
          type="button"
          onClick={() => onCycleStatus(tx)}
          className="cursor-pointer active:scale-95 transition-transform"
          title="Toque para alternar status da despesa"
        >
          {getStatusBadge()}
        </button>
      </div>

      {/* Middle row: Description & Tags */}
      <div className="mb-2.5">
        <div className="flex items-start justify-between gap-2">
          <h4 className="text-sm font-bold text-white leading-snug break-words">
            {tx.description}
          </h4>

          {/* Amount */}
          <div className="text-right shrink-0">
            <span
              className={`text-base font-extrabold font-mono tracking-tight ${
                isRefund
                  ? 'text-emerald-400'
                  : isIncome
                    ? 'text-emerald-400'
                    : 'text-slate-100'
              }`}
            >
              {isIncome ? `+${formatBRL(effAmount)}` : isRefund ? formatBRL(effAmount) : `-${formatBRL(effAmount)}`}
            </span>
          </div>
        </div>

        {/* Tags / Meta Chips */}
        <div className="flex items-center gap-1.5 flex-wrap mt-1.5">
          {/* Installment Badge */}
          {isInstallment && (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-violet-500/15 border border-violet-500/30 text-violet-300 text-[10px] font-mono">
              <Layers className="size-2.5" /> Parcela
            </span>
          )}

          {/* Card Chip */}
          {card && (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-sky-500/10 border border-sky-500/25 text-sky-300 text-[10px]">
              <CreditCard className="size-2.5" /> {card.cardName}
            </span>
          )}

          {/* Split / Third Party Tag */}
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800/80 text-slate-300 border border-slate-700/60 font-medium">
            {split === 'split_50' && '🤝 50/50'}
            {split === 'partner' && '👤 Parceiro (100%)'}
            {split === 'mine' && '👤 Meu'}
            {split === 'external' && `🏷️ ${thirdParty?.name ? `Terceiro: ${thirdParty.name}` : 'Terceiro Externo'}`}
            {split === 'friend' && `🧑 ${tx.assignedFriendName || 'Amigo'}`}
            {split === 'custom' && `👥 Dividido (${tx.allocations?.length ?? 2}p)`}
          </span>
        </div>
      </div>

      {/* Bottom row: My Impact & Actions */}
      <div className="flex items-center justify-between pt-2 border-t border-white/[0.06] text-xs">
        <div className="text-[11px] text-slate-400">
          <span>Meu impacto: </span>
          <strong
            className={`font-semibold ${
              isRefund
                ? 'text-emerald-400'
                : isIncome
                  ? 'text-emerald-400'
                  : 'text-amber-300'
            }`}
          >
            {formatBRL(myImpact)}
          </strong>
        </div>

        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => onEdit(tx)}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
            title="Editar lançamento"
          >
            <Edit2 className="size-3.5" />
          </button>
          <button
            type="button"
            onClick={() => onDelete(tx)}
            className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
            title="Excluir lançamento"
          >
            <Trash2 className="size-3.5" />
          </button>
        </div>
      </div>
    </div>
  )
}
