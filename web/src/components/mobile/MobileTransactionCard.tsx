import { useState } from 'react'
import { type Transaction, type Card as CardType } from '@/types'
import { getCategoryDetail } from '@/types'
import { canUserEditTransaction, effectiveAmount, impactFor, getThirdPartyInfo } from '@/lib/finance'
import { formatBRL } from '@/lib/utils'
import {
  Edit2,
  Trash2,
  RotateCcw,
  CheckCircle2,
  Clock,
  Sparkles,
  Lock,
  CreditCard,
  ChevronDown,
  ChevronUp,
} from 'lucide-react'

interface MobileTransactionCardProps {
  tx: Transaction
  currentUid: string
  allTransactions: Transaction[]
  cards: CardType[]
  onEdit: (tx: Transaction) => void
  onRefund?: (tx: Transaction) => void
  onDelete: (tx: Transaction) => void
  onCycleStatus: (tx: Transaction) => void
}

export function MobileTransactionCard({
  tx,
  currentUid,
  allTransactions,
  cards,
  onEdit,
  onRefund,
  onDelete,
  onCycleStatus,
}: MobileTransactionCardProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  const effAmount = effectiveAmount(tx)
  const canEdit = canUserEditTransaction(tx, currentUid)
  const myImpact = impactFor(tx, currentUid, allTransactions)
  const isRefund = tx.category === 'Estorno' || effAmount < 0 || tx.totalAmount < 0
  const isIncome = tx.type === 'income'
  const card = cards.find((c) => c.id === tx.cardId)
  const thirdParty = getThirdPartyInfo(tx, currentUid)
  const catDetail = getCategoryDetail(tx.category)

  const isSharedAndDifferent = Math.abs(myImpact - effAmount) > 0.01

  if (tx.id.startsWith('invoice_')) {
    const isOwner = card?.ownerUid === currentUid
    return (
      <div
        onClick={() => onEdit(tx)}
        className={`group rounded-2xl p-3 border transition-all flex items-center justify-between gap-3 relative cursor-pointer ${tx.status === 'paid'
            ? 'bg-slate-900/80 border-emerald-500/30'
            : 'bg-slate-900/70 border-sky-500/25 hover:border-sky-500/40'
          }`}
      >
        <div className="flex items-center gap-3 min-w-0">
          <div className="size-9 rounded-xl bg-gradient-to-tr from-sky-500/20 to-indigo-500/20 border border-sky-500/30 text-sky-400 flex items-center justify-center shrink-0">
            <CreditCard className="size-4" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="text-xs font-bold text-white truncate">
                {tx.description}
              </span>
              {tx.status === 'paid' ? (
                <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-semibold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                  <CheckCircle2 className="size-2.5" /> Paga
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-semibold bg-amber-500/15 text-amber-300 border border-amber-500/30">
                  <Clock className="size-2.5" /> Fatura
                </span>
              )}
            </div>
            <p className="text-[10px] text-slate-400 truncate mt-0.5">
              <span>Vencimento dia {card?.dueDay || tx.dueDate.slice(8, 10)}</span>
              {isOwner ? (
                <span className="text-sky-300"> · Toque para ver compras</span>
              ) : (
                <span className="text-indigo-300"> · Cartão de {card?.ownerName}</span>
              )}
            </p>
          </div>
        </div>

        <div className="text-right shrink-0">
          <p className="text-xs font-bold text-white font-mono">
            {formatBRL(effAmount)}
          </p>
        </div>
      </div>
    )
  }

  const getStatusBadge = () => {
    if (tx.status === 'paid') {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-semibold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
          <CheckCircle2 className="size-2.5" /> Pago
        </span>
      )
    }

    if (tx.status === 'pending') {
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-semibold bg-amber-500/15 text-amber-300 border border-amber-500/30">
          <Clock className="size-2.5" /> Pendente
        </span>
      )
    }

    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-semibold bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
        <Sparkles className="size-2.5" /> Estimado
      </span>
    )
  }

  const thirdPartyName = thirdParty?.name || tx.assignedFriendName || tx.externalName

  return (
    <div
      className={`group rounded-2xl p-2.5 border transition-all flex flex-col relative ${tx.status === 'paid'
          ? 'bg-slate-900/70 border-emerald-500/25'
          : 'bg-slate-900/60 border-white/[0.08] hover:border-white/[0.16]'
        }`}
    >
      <div className="flex items-center gap-3 w-full">
        {/* 1. Indicador da Categoria Circular */}
        <div
          className="size-9 rounded-full shrink-0 flex items-center justify-center border"
          style={{
            backgroundColor: `${catDetail.color}20`,
            borderColor: `${catDetail.color}45`,
          }}
        >
          <span
            className="size-2.5 rounded-full"
            style={{ backgroundColor: catDetail.color }}
          />
        </div>

        {/* 2. Coluna Central de Conteúdo */}
        <div
          className="min-w-0 flex-1 cursor-pointer"
          onClick={() => onEdit(tx)}
        >
          {/* Linha Superior: Descrição + Tags de Parcela / Terceiro */}
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-xs font-semibold text-slate-100 truncate">
              {tx.description}
            </span>

            {tx.installmentsTotal && tx.installmentsTotal > 1 && (
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-cyan-500/15 text-cyan-300 font-bold border border-cyan-500/30">
                {tx.installmentNumber}/{tx.installmentsTotal}
              </span>
            )}

            {thirdPartyName && (
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 font-medium border border-amber-500/30 truncate max-w-[130px]">
                🏷️ {thirdPartyName}
              </span>
            )}
          </div>

          {/* Linha Inferior: Categoria › Subcategoria · Cartão / Minha Parte */}
          <p className="text-[10px] text-slate-400 truncate mt-0.5">
            <span>{tx.category}</span>
            {tx.subcategory && tx.subcategory !== tx.category && (
              <span className="text-slate-400"> › {tx.subcategory}</span>
            )}
            {card && (
              <span className="text-sky-300"> · 💳 {card.cardName}</span>
            )}
            {isSharedAndDifferent && (
              <span className="text-emerald-400 font-medium"> · minha parte: {formatBRL(myImpact)}</span>
            )}
          </p>
        </div>

        {/* 3. Coluna Direita: Valor e Status */}
        <div className="text-right shrink-0 space-y-1">
          <p
            className={`text-xs font-bold tabular-nums ${isRefund
                ? 'text-cyan-400'
                : isIncome
                  ? 'text-emerald-400'
                  : 'text-white'
              }`}
          >
            {isIncome ? `+ ${formatBRL(effAmount)}` : isRefund ? formatBRL(effAmount) : formatBRL(effAmount)}
          </p>

          <div className="flex items-center justify-end gap-1">
            {canEdit ? (
              <button
                type="button"
                onClick={() => onCycleStatus(tx)}
                className="cursor-pointer active:scale-95 transition-transform"
                title="Toque para alternar status"
              >
                {getStatusBadge()}
              </button>
            ) : (
              <span className="text-[9px] text-slate-500 flex items-center gap-0.5" title="Somente leitura">
                <Lock className="size-2.5" /> Leitura
              </span>
            )}

            <button
              type="button"
              onClick={() => setIsExpanded(!isExpanded)}
              className="p-1 rounded-full text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
              title={isExpanded ? 'Recolher detalhes' : 'Expandir detalhes'}
            >
              {isExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
            </button>

            {canEdit && (
              <div className="flex items-center opacity-0 group-hover:opacity-100 transition-opacity ml-1">
                {onRefund && tx.type === 'expense' && !isRefund && (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      onRefund(tx)
                    }}
                    className="p-1 rounded text-cyan-400 hover:text-cyan-200 hover:bg-cyan-950/40"
                    title="Estornar"
                  >
                    <RotateCcw className="size-3" />
                  </button>
                )}
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation()
                    onEdit(tx)
                  }}
                  className="p-1 rounded text-slate-400 hover:text-white hover:bg-slate-800"
                  title="Editar"
                >
                  <Edit2 className="size-3" />
                </button>
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation()
                    onDelete(tx)
                  }}
                  className="p-1 rounded text-slate-400 hover:text-rose-400 hover:bg-rose-500/10"
                  title="Excluir"
                >
                  <Trash2 className="size-3" />
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Painel Expansível com transição suave (Tailwind CSS) */}
      <div
        className={`overflow-hidden transition-all duration-300 ease-in-out ${isExpanded ? 'max-h-40 opacity-100 mt-2.5 pt-2 border-t border-white/[0.06]' : 'max-h-0 opacity-0'
          }`}
      >
        <div className="bg-slate-950/40 rounded-xl p-2.5 text-[11px] space-y-1.5 border border-white/[0.04]">
          <div className="flex justify-between text-slate-400">
            <span>Data de vencimento:</span>
            <span className="text-slate-200 font-medium">{tx.dueDate}</span>
          </div>
          {tx.paymentMethod && (
            <div className="flex justify-between text-slate-400">
              <span>Método de pagamento:</span>
              <span className="text-slate-200 font-medium capitalize">{tx.paymentMethod}</span>
            </div>
          )}
          {isSharedAndDifferent && (
            <div className="flex justify-between text-slate-400">
              <span>Valor total original:</span>
              <span className="text-amber-400 font-mono font-semibold">{formatBRL(effAmount)}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
