import { useState, useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFinance } from '@/store/FinanceContext'
import {
  CATEGORIES,
  CATEGORY_GROUPS,
  type Category,
  type SplitType,
  type Transaction,
} from '@/types'
import {
  effectiveAmount,
  impactFor,
  detectSplit,
  getReserveInfo,
  friendPctOf,
  friendShareOf,
  getThirdPartyInfo,
  isSharedCard,
} from '@/lib/finance'
import { formatBRL, formatShortDate } from '@/lib/utils'
import {
  Search,
  RefreshCw,
  CreditCard,
  Shield,
  Trash2,
  Edit2,
  Filter,
  Clock,
  CheckCircle2,
  Layers,
  RotateCcw,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Maximize2,
  Minimize2,
  User,
} from 'lucide-react'
import { DeleteInstallmentDialog } from './modals/DeleteInstallmentDialog'
import { MobileTransactionCard } from './mobile/MobileTransactionCard'

type TabFilter = 'all' | 'income' | 'expense' | 'refund' | 'pending_estimated' | 'paid' | 'shared_card' | 'third_party' | 'friends_installments'

interface TransactionTableProps {
  onEditTx: (tx: Transaction) => void
  onToggleExpand?: () => void
  isExpanded?: boolean
}

export function TransactionTable({ onEditTx, onToggleExpand, isExpanded }: TransactionTableProps) {
  const {
    monthTransactions,
    cards,
    me,
    partner,
    cycleStatus,
    changeSplit,
    changeAmount,
    updateTransaction,
    updateInstallmentGroup,
    deleteTransaction,
    respondChargeRequest,
  } = useFinance()

  const [activeTab, setActiveTab] = useState<TabFilter>('all')
  const [search, setSearch] = useState('')
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editDesc, setEditDesc] = useState('')
  const [editDate, setEditDate] = useState('')
  const [editAmount, setEditAmount] = useState('')
  const [deleteGroupTx, setDeleteGroupTx] = useState<Transaction | null>(null)

  const currentUid = me?.uid ?? ''

  // Filter and sort list by transaction date
  const filteredTxs = useMemo(() => {
    const list = monthTransactions.filter((tx: Transaction) => {
      // Tab filter
      if (activeTab === 'income') {
        if (tx.type !== 'income') return false
      } else if (activeTab === 'expense') {
        if (tx.type === 'income') return false
      } else if (activeTab === 'refund') {
        const isRef = tx.category === 'Estorno' || tx.totalAmount < 0 || effectiveAmount(tx) < 0
        if (!isRef) return false
      } else if (activeTab === 'pending_estimated') {
        if (tx.status === 'paid') return false
      } else if (activeTab === 'paid') {
        if (tx.status !== 'paid') return false
      } else if (activeTab === 'shared_card') {
        const card = cards.find((c) => c.id === tx.cardId)
        const isShared = isSharedCard(card)
        if (!isShared && !tx.reserveCardId) return false
      } else if (activeTab === 'third_party') {
        const isThird = (tx.allocations || []).some((a) => a.type === 'EXTERNAL')
        if (!isThird) return false
      } else if (activeTab === 'friends_installments') {
        const isFriend = Boolean(tx.assignedFriendUid)
        const isInst = Boolean(tx.installmentGroupId)
        if (!isFriend && !isInst) return false
      }

      // Search filter
      if (search.trim()) {
        const q = search.toLowerCase()
        const matchesDesc = tx.description.toLowerCase().includes(q)
        const matchesCat = tx.category.toLowerCase().includes(q)
        const matchesSubcat = tx.subcategory?.toLowerCase().includes(q)
        const matchesAlloc = (tx.allocations || []).some((a) => a.name.toLowerCase().includes(q))
        if (!matchesDesc && !matchesCat && !matchesSubcat && !matchesAlloc) return false
      }

      return true
    })

    // Organize chronologically by transaction date
    return [...list].sort((a, b) => {
      const dateA = a.dueDate || a.competenceMonth || ''
      const dateB = b.dueDate || b.competenceMonth || ''
      const cmp = dateA.localeCompare(dateB)
      if (cmp !== 0) {
        return sortOrder === 'asc' ? cmp : -cmp
      }
      // If dates are identical, show income first, then expense
      if (a.type !== b.type) {
        return a.type === 'income' ? -1 : 1
      }
      return (b.createdAt || 0) - (a.createdAt || 0)
    })
  }, [monthTransactions, activeTab, search, cards, sortOrder])



  // Count badges
  const counts = useMemo(() => {
    const txs = Array.isArray(monthTransactions) ? monthTransactions : []
    const cardsList = Array.isArray(cards) ? cards : []
    return {
      all: txs.length,
      income: txs.filter((t: Transaction) => t && t.type === 'income').length,
      expense: txs.filter((t: Transaction) => t && t.type !== 'income').length,
      refund: txs.filter((t: Transaction) => t && (t.category === 'Estorno' || t.totalAmount < 0 || effectiveAmount(t) < 0)).length,
      pending: txs.filter((t: Transaction) => t && t.status !== 'paid').length,
      paid: txs.filter((t: Transaction) => t && t.status === 'paid').length,
      shared: txs.filter((t: Transaction) => {
        if (!t) return false
        const card = cardsList.find((c) => c && c.id === t.cardId)
        return isSharedCard(card) || t.reserveCardId
      }).length,
      third: txs.filter((t: Transaction) => (Array.isArray(t?.allocations) ? t.allocations : []).some((a) => a && a.type === 'EXTERNAL')).length,
      friends_installments: txs.filter((t: Transaction) => t && Boolean(t.assignedFriendUid || t.installmentGroupId)).length,
    }
  }, [monthTransactions, cards])

  const startEdit = (tx: Transaction) => {
    setEditingId(tx.id)
    setEditDesc(tx.description)
    setEditDate(tx.dueDate)
    setEditAmount(String(effectiveAmount(tx)))
  }

  const saveInlineEdit = async (tx: Transaction) => {
    const num = parseFloat(editAmount.replace(',', '.'))
    if (!editDesc.trim() || isNaN(num) || num <= 0) {
      setEditingId(null)
      return
    }

    await updateTransaction(tx.id, {
      description: editDesc.trim(),
      dueDate: editDate,
    })

    if (num !== effectiveAmount(tx)) {
      await changeAmount(tx, {
        actualAmount: tx.status === 'estimated' ? null : num,
        estimatedAmount: tx.status === 'estimated' ? num : tx.estimatedAmount,
      })
    }

    setEditingId(null)
  }



  return (
    <div className="glass-card rounded-2xl border border-white/[0.08] flex flex-col h-full overflow-hidden shadow-xl">
      {/* Table Header & Filter Tabs */}
      <div className="p-4 border-b border-white/[0.06] space-y-3 bg-slate-900/40">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <h2 className="text-base font-bold text-white tracking-tight m-0">Lançamentos do Mês</h2>
            <Badge variant="outline" className="text-xs font-mono bg-slate-800 text-slate-300">
              {filteredTxs.length}
            </Badge>
          </div>

          {/* Search Bar & Mobile Date Sort Toggle */}
          <div className="flex items-center gap-2 w-full sm:w-auto">
            <button
              type="button"
              onClick={() => setSortOrder((prev) => (prev === 'asc' ? 'desc' : 'asc'))}
              className="md:hidden h-8 px-2.5 rounded-lg bg-slate-950/70 border border-slate-800 hover:border-emerald-500/40 text-slate-300 hover:text-white text-xs flex items-center gap-1.5 transition-all cursor-pointer shrink-0"
              title="Clique para alternar ordenação por data"
            >
              <ArrowUpDown className="size-3 text-emerald-400" />
              <span className="text-[11px] font-medium">
                {sortOrder === 'asc' ? '1→31' : '31→1'}
              </span>
            </button>

            <div className="relative flex-1 sm:w-60">
              <Search className="size-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Buscar por descrição, categoria..."
                className="h-8 pl-8 text-xs bg-slate-950/70 border-slate-800 focus-visible:ring-emerald-500/40"
              />
            </div>

            {onToggleExpand && (
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={onToggleExpand}
                className={`size-8 text-slate-400 hover:text-white cursor-pointer hidden lg:flex rounded-lg border shrink-0 items-center justify-center transition-all ${
                  isExpanded
                    ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40 hover:bg-emerald-500/25 shadow-xs'
                    : 'bg-slate-950/70 border-slate-800 hover:border-slate-700 hover:bg-slate-800/60'
                }`}
                title={isExpanded ? 'Restaurar divisão da tela' : 'Maximizar tabela (tela cheia)'}
              >
                {isExpanded ? <Minimize2 className="size-3.5" /> : <Maximize2 className="size-3.5" />}
              </Button>
            )}
          </div>
        </div>

        {/* Filter Pills */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
          <button
            type="button"
            onClick={() => setActiveTab('all')}
            className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === 'all'
                ? 'bg-slate-700/80 text-white border border-slate-600'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
            }`}
          >
            <span>Todos</span>
            <span className="text-[10px] opacity-70">({counts.all})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('income')}
            className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === 'income'
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 font-semibold'
                : 'text-slate-400 hover:text-emerald-300 hover:bg-slate-800/50'
            }`}
          >
            <span>💰 Entradas ({counts.income})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('expense')}
            className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === 'expense'
                ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40 font-semibold'
                : 'text-slate-400 hover:text-rose-300 hover:bg-slate-800/50'
            }`}
          >
            <span>💸 Despesas ({counts.expense})</span>
          </button>

          {counts.refund > 0 && (
            <button
              type="button"
              onClick={() => setActiveTab('refund')}
              className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
                activeTab === 'refund'
                  ? 'bg-emerald-500/25 text-emerald-200 border border-emerald-500/40 font-semibold shadow-xs'
                  : 'text-slate-400 hover:text-emerald-300 hover:bg-slate-800/50'
              }`}
            >
              <span>🔄 Estornos ({counts.refund})</span>
            </button>
          )}

          <button
            type="button"
            onClick={() => setActiveTab('pending_estimated')}
            className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === 'pending_estimated'
                ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
            }`}
          >
            <span>⏳ Pendentes & Estimados</span>
            <span className="text-[10px] opacity-70">({counts.pending})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('paid')}
            className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === 'paid'
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
            }`}
          >
            <span>✅ Pagos</span>
            <span className="text-[10px] opacity-70">({counts.paid})</span>
          </button>

          {(counts.shared > 0 || activeTab === 'shared_card') && (
            <button
              type="button"
              onClick={() => setActiveTab('shared_card')}
              className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
                activeTab === 'shared_card'
                  ? 'bg-violet-500/20 text-violet-300 border border-violet-500/30'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
              }`}
            >
              <span>💳 Cartão Compartilhado</span>
              <span className="text-[10px] opacity-70">({counts.shared})</span>
            </button>
          )}

          {(counts.third > 0 || activeTab === 'third_party') && (
            <button
              type="button"
              onClick={() => setActiveTab('third_party')}
              className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
                activeTab === 'third_party'
                  ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
              }`}
            >
              <span>🏷️ Terceiros</span>
              <span className="text-[10px] opacity-70">({counts.third})</span>
            </button>
          )}

          {(counts.friends_installments > 0 || activeTab === 'friends_installments') && (
            <button
              type="button"
              onClick={() => setActiveTab('friends_installments')}
              className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
                activeTab === 'friends_installments'
                  ? 'bg-violet-500/25 text-violet-200 border border-violet-500/40 font-semibold shadow-sm'
                  : 'text-slate-400 hover:text-violet-300 hover:bg-slate-800/50'
              }`}
            >
              <span>🧑‍🤝‍🧑 Amigos / Parcelados</span>
              <span className="text-[10px] opacity-70">({counts.friends_installments})</span>
            </button>
          )}
        </div>
      </div>

      {/* Mobile Transaction Cards Feed (Visible on screens < md) */}
      <div className="md:hidden flex-1 overflow-y-auto p-3 space-y-2.5">
        {filteredTxs.length === 0 ? (
          <div className="text-center py-12 text-slate-400">
            <Filter className="size-8 mx-auto mb-2 opacity-30" />
            <p className="font-medium text-slate-300">Nenhuma transação encontrada</p>
            <p className="text-[11px] text-slate-500 mt-1">Tente ajustar os filtros ou adicione uma nova transação.</p>
          </div>
        ) : (
          filteredTxs.map((tx: Transaction) => (
            <MobileTransactionCard
              key={tx.id}
              tx={tx}
              currentUid={currentUid}
              allTransactions={monthTransactions}
              cards={cards}
              onEdit={onEditTx}
              onDelete={(targetTx) => {
                if (targetTx.installmentGroupId) {
                  setDeleteGroupTx(targetTx)
                } else {
                  deleteTransaction(targetTx.id)
                }
              }}
              onCycleStatus={cycleStatus}
            />
          ))
        )}
      </div>

      {/* Main Table (Visible on md and above) */}
      <div className="hidden md:block flex-1 overflow-x-auto overflow-y-auto">
        <table className="w-full text-left text-xs border-collapse">
          <thead className="sticky top-0 bg-[#0f172a] text-slate-400 font-semibold uppercase text-[10px] tracking-wider border-b border-white/[0.06] z-10">
            <tr>
              <th className="py-2 px-2.5 w-24">Status</th>
              <th
                onClick={() => setSortOrder((prev) => (prev === 'asc' ? 'desc' : 'asc'))}
                className="py-2 px-2.5 w-24 cursor-pointer select-none hover:text-white transition-colors group"
                title="Clique para alternar ordem cronológica (1→31 ou 31→1)"
              >
                <div className="flex items-center gap-1">
                  <span>Data</span>
                  {sortOrder === 'asc' ? (
                    <ArrowUp className="size-3 text-emerald-400 shrink-0" />
                  ) : (
                    <ArrowDown className="size-3 text-emerald-400 shrink-0" />
                  )}
                  <span className="text-[9px] font-mono text-emerald-300 bg-emerald-500/15 px-1 py-0.5 rounded">
                    {sortOrder === 'asc' ? '1→31' : '31→1'}
                  </span>
                </div>
              </th>
              <th className="py-2 px-2.5 min-w-[150px]">Descrição</th>
              <th className="py-2 px-2.5 w-28">Categoria</th>
              <th className="py-2 px-2.5 w-32">Divisão / Pagador</th>
              <th className="py-2 px-2.5 w-36 text-right">Valor & Meu Impacto</th>
              <th className="py-2 px-1.5 w-14 text-center">Ações</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/[0.04]">
            {filteredTxs.length === 0 ? (
              <tr>
                <td colSpan={7} className="text-center py-12 text-slate-400">
                  <Filter className="size-8 mx-auto mb-2 opacity-30" />
                  <p className="font-medium text-slate-300">Nenhuma transação encontrada</p>
                  <p className="text-[11px] text-slate-500 mt-1">Tente ajustar os filtros ou adicione uma nova transação.</p>
                </td>
              </tr>
            ) : (
              filteredTxs.map((tx: Transaction) => {
                const isEditing = editingId === tx.id
                const effAmount = effectiveAmount(tx)
                const myImpact = impactFor(tx, currentUid, monthTransactions)
                const split = detectSplit(tx, currentUid)
                const isRefund = tx.category === 'Estorno' || effAmount < 0 || tx.totalAmount < 0
                const card = cards.find((c) => c.id === tx.cardId)
                const isReserve = Boolean(tx.reserveCardId)
                const reserveInfo = isReserve ? getReserveInfo(tx, monthTransactions) : null
                const thirdParty = getThirdPartyInfo(tx, currentUid, partner?.name)

                // Estimation vs Actual comparison
                const hasComparison = tx.recurringBillId && tx.actualAmount != null && tx.estimatedAmount != null
                const diffEstimate = hasComparison ? tx.actualAmount! - tx.estimatedAmount! : 0

                return (
                  <tr
                    key={tx.id}
                    className="hover:bg-slate-800/30 transition-colors group"
                  >
                    {/* 1. Status Badge (Interactive Click to Cycle) */}
                    <td className="py-2 px-3 align-middle">
                      <button
                        type="button"
                        onClick={() => cycleStatus(tx)}
                        title={
                          tx.type === 'income'
                            ? 'Clique para alternar: Previsto → A Receber → Na Conta'
                            : 'Clique para alternar: Estimado → A Pagar → Pago'
                        }
                        className="cursor-pointer transition-transform hover:scale-105 active:scale-95"
                      >
                        {tx.type === 'income' ? (
                          tx.status === 'estimated' ? (
                            <Badge variant="outline" className="text-[10px] bg-amber-500/10 text-amber-300 border-amber-500/30 gap-1 font-medium">
                              <span className="size-1.5 rounded-full bg-amber-400 animate-pulse" />
                              🟡 Previsto
                            </Badge>
                          ) : tx.status === 'pending' ? (
                            <Badge variant="outline" className="text-[10px] bg-orange-500/10 text-orange-300 border-orange-500/30 gap-1 font-medium">
                              <span className="size-1.5 rounded-full bg-orange-400" />
                              🟠 A Receber
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-[10px] bg-emerald-500/10 text-emerald-300 border-emerald-500/30 gap-1 font-medium">
                              <span className="size-1.5 rounded-full bg-emerald-400" />
                              🟢 Na Conta
                            </Badge>
                          )
                        ) : tx.status === 'estimated' ? (
                          <Badge variant="outline" className="text-[10px] bg-amber-500/10 text-amber-300 border-amber-500/30 gap-1 font-medium">
                            <span className="size-1.5 rounded-full bg-amber-400 animate-pulse" />
                            Estimado
                          </Badge>
                        ) : tx.status === 'pending' ? (
                          <Badge variant="outline" className="text-[10px] bg-orange-500/10 text-orange-300 border-orange-500/30 gap-1 font-medium">
                            <span className="size-1.5 rounded-full bg-orange-400" />
                            A Pagar
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="text-[10px] bg-emerald-500/10 text-emerald-300 border-emerald-500/30 gap-1 font-medium">
                            <span className="size-1.5 rounded-full bg-emerald-400" />
                            Pago
                          </Badge>
                        )}
                      </button>
                    </td>

                    {/* 2. Date */}
                    <td className="py-2 px-3 text-slate-400 font-mono text-xs whitespace-nowrap align-middle">
                      {isEditing ? (
                        <Input
                          type="date"
                          value={editDate}
                          onChange={(e) => setEditDate(e.target.value)}
                          className="h-7 text-[11px] bg-slate-900 border-slate-700 w-28 p-1"
                        />
                      ) : (
                        <span>{formatShortDate(tx.dueDate)}</span>
                      )}
                    </td>

                    {/* 3. Description & Metadata Badges */}
                    <td className="py-2 px-3 align-middle">
                      {isEditing ? (
                        <Input
                          value={editDesc}
                          onChange={(e) => setEditDesc(e.target.value)}
                          className="h-7 text-xs bg-slate-900 border-slate-700"
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') saveInlineEdit(tx)
                            if (e.key === 'Escape') setEditingId(null)
                          }}
                        />
                      ) : (
                        <div className="flex flex-col gap-0.5">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span
                              onDoubleClick={() => startEdit(tx)}
                              className="font-medium text-slate-200 hover:text-white cursor-pointer"
                              title="Duplo clique para editar"
                            >
                              {tx.description}
                            </span>

                            {/* Third Party / Friend Badge (Visible directly in row!) */}
                            {thirdParty && (
                              <Badge
                                variant="outline"
                                className={`text-[10px] py-0 px-2 gap-1 font-semibold inline-flex items-center shadow-2xs ${
                                  thirdParty.type === 'external'
                                    ? 'bg-amber-500/15 text-amber-300 border-amber-500/40 hover:bg-amber-500/25'
                                    : thirdParty.type === 'friend'
                                      ? 'bg-violet-500/15 text-violet-300 border-violet-500/40 hover:bg-violet-500/25'
                                      : 'bg-cyan-500/15 text-cyan-300 border-cyan-500/40 hover:bg-cyan-500/25'
                                }`}
                                title={`${thirdParty.label}: ${thirdParty.name}`}
                              >
                                <User className="size-2.5 shrink-0" />
                                <span>
                                  {thirdParty.label}: <strong className="text-white font-bold">{thirdParty.name}</strong>
                                </span>
                              </Badge>
                            )}

                            {/* Estorno / Reembolso Tag */}
                            {isRefund && (
                              <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-emerald-500/15 text-emerald-300 border-emerald-500/40 gap-1 font-semibold shadow-xs">
                                <RotateCcw className="size-2.5 text-emerald-400" />
                                <span>Estorno / Crédito</span>
                              </Badge>
                            )}

                            {/* Recurring bill indicator */}
                            {tx.recurringBillId && (
                              <span title="Conta gerada a partir de molde recorrente">
                                <RefreshCw className="size-3 text-amber-400/80" />
                              </span>
                            )}

                            {/* Shared card badge */}
                            {card && (
                              <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-slate-800/80 text-slate-300 border-slate-700 gap-1">
                                <CreditCard className="size-2.5 text-violet-400" />
                                {card.cardName.split(' ')[0]}
                              </Badge>
                            )}

                            {/* Anti-duplicity reserve badge */}
                            {isReserve && (
                              <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-cyan-950/60 text-cyan-300 border-cyan-800 gap-1">
                                <Shield className="size-2.5" />
                                Reserva Cartão
                              </Badge>
                            )}

                            {/* Feature 3: Installment badge */}
                            {tx.installmentNumber && tx.installmentsTotal && (
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation()
                                  onEditTx(tx)
                                }}
                                className="cursor-pointer"
                                title={`Parcelamento conectado (${tx.installmentNumber}/${tx.installmentsTotal}). Clique para editar a série ou a parcela.`}
                              >
                                <Badge
                                  variant="outline"
                                  className="text-[9px] py-0 px-1.5 bg-violet-950/70 hover:bg-violet-900 text-violet-300 border-violet-800 hover:border-violet-600 font-mono gap-1 transition-colors"
                                >
                                  <Layers className="size-2.5" />
                                  <span>{tx.installmentNumber}/{tx.installmentsTotal}</span>
                                </Badge>
                              </button>
                            )}

                            {/* Feature 2: Friend charge badge & Quick Actions */}
                            {tx.assignedFriendUid && (
                              tx.assignedFriendUid === currentUid ? (
                                // Visão do amigo que foi cobrado
                                tx.acceptanceStatus === 'accepted' ? (
                                  <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-emerald-500/10 text-emerald-300 border-emerald-500/30 gap-1">
                                    <CheckCircle2 className="size-2.5" />
                                    Aceito por você ({friendPctOf(tx)}% = {formatBRL(friendShareOf(tx))})
                                  </Badge>
                                ) : tx.acceptanceStatus === 'rejected' ? (
                                  <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-rose-500/10 text-rose-300 border-rose-500/30 gap-1">
                                    ✕ Você recusou
                                  </Badge>
                                ) : (
                                  <div className="inline-flex items-center gap-1.5 bg-amber-500/15 border border-amber-500/30 rounded-md px-1.5 py-0.5 animate-pulse">
                                    <Clock className="size-2.5 text-amber-300" />
                                    <span className="text-[10px] text-amber-200 font-semibold">
                                      Sua cota: {friendPctOf(tx)}% ({formatBRL(friendShareOf(tx))})
                                    </span>
                                    <button
                                      type="button"
                                      onClick={(e) => {
                                        e.stopPropagation()
                                        respondChargeRequest(tx, true)
                                      }}
                                      className="px-1.5 py-0.5 text-[9px] font-bold bg-emerald-600 hover:bg-emerald-500 text-white rounded cursor-pointer transition-colors shadow-sm"
                                      title="Aceitar cobrança e incluir no seu Saldo Livre"
                                    >
                                      ✓ Aceitar
                                    </button>
                                    <button
                                      type="button"
                                      onClick={(e) => {
                                        e.stopPropagation()
                                        respondChargeRequest(tx, false)
                                      }}
                                      className="px-1.5 py-0.5 text-[9px] font-bold bg-rose-600/80 hover:bg-rose-500 text-white rounded cursor-pointer transition-colors"
                                      title="Recusar cobrança"
                                    >
                                      ✕ Recusar
                                    </button>
                                  </div>
                                )
                              ) : (
                                // Visão de quem lançou ou parceiro
                                tx.acceptanceStatus === 'accepted' ? (
                                  <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-emerald-500/10 text-emerald-300 border-emerald-500/30 gap-1">
                                    <CheckCircle2 className="size-2.5" />
                                    Aceito por {tx.assignedFriendName || 'Amigo'} ({friendPctOf(tx)}% = {formatBRL(friendShareOf(tx))})
                                  </Badge>
                                ) : tx.acceptanceStatus === 'rejected' ? (
                                  <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-rose-500/10 text-rose-300 border-rose-500/30 gap-1">
                                    ✕ Recusado por {tx.assignedFriendName || 'Amigo'}
                                  </Badge>
                                ) : (
                                  <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-amber-500/15 text-amber-300 border-amber-500/30 gap-1 animate-pulse">
                                    <Clock className="size-2.5" />
                                    Aguardando {tx.assignedFriendName || 'Amigo'} ({friendPctOf(tx)}% = {formatBRL(friendShareOf(tx))})
                                  </Badge>
                                )
                              )
                            )}
                          </div>

                          {/* Reserve progress bar */}
                          {reserveInfo && (
                            <div className="flex items-center gap-2 mt-1">
                              <div className="w-24 h-1.5 rounded-full bg-slate-800 overflow-hidden">
                                <div
                                  className="h-full bg-cyan-400 rounded-full"
                                  style={{ width: `${reserveInfo.pct}%` }}
                                />
                              </div>
                              <span className="text-[10px] text-slate-400">
                                Resta {formatBRL(reserveInfo.remaining)}
                              </span>
                            </div>
                          )}
                        </div>
                      )}
                    </td>

                    {/* 4. Category & Subcategory */}
                    <td className="py-2 px-3 align-middle">
                      <div className="flex flex-col gap-0.5">
                        <Select
                          value={tx.category}
                          onValueChange={(cat) => {
                            const newCat = cat as Category
                            const newSub = CATEGORY_GROUPS[newCat]?.subcategories?.[0] || null
                            if (tx.installmentGroupId) {
                              updateInstallmentGroup(tx.installmentGroupId, {
                                category: newCat,
                                subcategory: newSub,
                              })
                            } else {
                              updateTransaction(tx.id, {
                                category: newCat,
                                subcategory: newSub,
                              })
                            }
                          }}
                        >
                          <SelectTrigger size="inline" className="text-slate-300">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {CATEGORIES.map((c) => (
                              <SelectItem key={c} value={c}>
                                {c === 'Estorno' ? '🔄 Estorno (Crédito Cartão)' : c}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                        {(CATEGORY_GROUPS[tx.category]?.subcategories?.length ?? 0) > 0 ? (
                          <Select
                            value={tx.subcategory || ''}
                            onValueChange={(sub) => {
                              const newSub = sub || null
                              if (tx.installmentGroupId) {
                                updateInstallmentGroup(tx.installmentGroupId, {
                                  subcategory: newSub,
                                })
                              } else {
                                updateTransaction(tx.id, {
                                  subcategory: newSub,
                                })
                              }
                            }}
                          >
                            <SelectTrigger size="inline" className="h-5 px-1 py-0 text-[10px] text-emerald-400/90 hover:text-emerald-300 font-medium border-transparent bg-transparent hover:bg-slate-800/60 max-w-[130px] justify-start gap-1">
                              <span className="truncate">› {tx.subcategory || 'Sem subgrupo'}</span>
                            </SelectTrigger>
                            <SelectContent>
                              {CATEGORY_GROUPS[tx.category].subcategories.map((sub) => (
                                <SelectItem key={sub} value={sub} className="text-xs">
                                  {sub}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        ) : tx.subcategory ? (
                          <span className="text-[10px] text-emerald-400/90 font-medium px-1 truncate max-w-[130px]" title={tx.subcategory}>
                            › {tx.subcategory}
                          </span>
                        ) : null}
                      </div>
                    </td>

                    {/* 5. Division / Payer (Inline Select) */}
                    <td className="py-2 px-3 align-middle">
                      {tx.type === 'income' ? (
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <Badge variant="outline" className="text-[10px] py-0.5 px-2 bg-emerald-950/40 text-emerald-300 border-emerald-800/60 font-medium">
                            💰 {tx.subcategory ? `Receita › ${tx.subcategory}` : 'Receita Pessoal'}
                          </Badge>
                          {thirdParty && (
                            <Badge variant="outline" className="text-[10px] py-0.5 px-1.5 bg-sky-500/15 text-sky-300 border-sky-500/35 font-medium">
                              De: {thirdParty.name}
                            </Badge>
                          )}
                        </div>
                      ) : (
                        <div className="flex flex-col gap-1">
                          <Select
                            value={split}
                            onValueChange={(val) => {
                              if (val === 'custom') {
                                onEditTx(tx)
                              } else {
                                changeSplit(tx, val as SplitType)
                              }
                            }}
                          >
                            <SelectTrigger size="inline" className="text-slate-300">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="mine">👤 100% Meu</SelectItem>
                              {partner && <SelectItem value="split_50">🤝 50/50 ({partner.name})</SelectItem>}
                              {partner && <SelectItem value="partner">🔄 100% {partner.name}</SelectItem>}
                              <SelectItem value="external">
                                🏷️ {thirdParty?.name ? `Terceiro: ${thirdParty.name}` : 'Terceiro Externo'}
                              </SelectItem>
                              {tx.assignedFriendUid && (
                                <SelectItem value="friend">
                                  🧑‍🤝‍🧑 {tx.assignedFriendName || 'Amigo'} ({friendPctOf(tx)}%)
                                </SelectItem>
                              )}
                              <SelectItem value="custom">
                                ⚖️ Multi-pessoas / Específico...
                              </SelectItem>
                            </SelectContent>
                          </Select>

                          {/* Multi-participant custom allocations preview chips */}
                          {split === 'custom' && Array.isArray(tx.allocations) && tx.allocations.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-1 max-w-[220px]">
                              {tx.allocations.map((a, i) => (
                                <span
                                  key={i}
                                  className="text-[9px] px-1.5 py-0.5 rounded bg-slate-800/90 border border-slate-700/80 text-slate-300 font-medium inline-flex items-center gap-1 shadow-2xs"
                                  title={`${a.name || 'Participante'}: ${formatBRL(Math.abs(a.amount))}`}
                                >
                                  <span className="text-slate-400 truncate max-w-[65px]">
                                    {a.name || (a.uid === currentUid ? 'Você' : 'Outro')}:
                                  </span>
                                  <span className="text-emerald-400 font-mono font-semibold">
                                    {formatBRL(Math.abs(a.amount))}
                                  </span>
                                </span>
                              ))}
                            </div>
                          )}

                          {/* Who paid the bill vs Who benefits */}
                          {card && (
                            <div className="text-[10px] text-slate-400 flex items-center gap-1">
                              {tx.paidByUid === currentUid ? (
                                <span className="text-slate-400">Você paga a fatura</span>
                              ) : (
                                <span className="text-amber-400 font-medium">Fatura de {card.ownerName}</span>
                              )}
                            </div>
                          )}
                        </div>
                      )}
                    </td>

                    {/* 6. Total Amount & My Impact */}
                    <td className="py-2 px-3 text-right align-middle font-mono">
                      {isEditing ? (
                        <div className="flex items-center justify-end gap-1">
                          <Input
                            type="number"
                            step="0.01"
                            value={editAmount}
                            onChange={(e) => setEditAmount(e.target.value)}
                            className="h-7 text-xs bg-slate-900 border-slate-700 w-24 text-right p-1"
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') saveInlineEdit(tx)
                              if (e.key === 'Escape') setEditingId(null)
                            }}
                          />
                        </div>
                      ) : (
                        <div className="flex flex-col items-end gap-0.5">
                          {/* My Real Impact on Free Balance */}
                          <div className="flex items-center gap-1.5">
                            {isRefund ? (
                              <div className="flex flex-col items-end">
                                <span className="text-xs font-bold text-emerald-400 flex items-center gap-1">
                                  +{formatBRL(Math.abs(myImpact))}
                                </span>
                                <Badge variant="outline" className="text-[9px] py-0 px-1 bg-emerald-950/60 text-emerald-300 border-emerald-600/50">
                                  Crédito na fatura
                                </Badge>
                              </div>
                            ) : myImpact === 0 ? (
                              <span className="text-xs font-semibold text-slate-500">
                                R$ 0,00 <span className="text-[10px] font-normal text-slate-500">(Neutro)</span>
                              </span>
                            ) : (
                              <span
                                className={`text-xs font-bold ${
                                  tx.type === 'income' ? 'text-emerald-400' : 'text-slate-100'
                                }`}
                              >
                                {tx.type === 'income' ? `+${formatBRL(myImpact)}` : `−${formatBRL(myImpact)}`}
                              </span>
                            )}
                          </div>

                          {/* Total gross amount if split or external or refund */}
                          {isRefund ? (
                            Math.abs(effAmount) !== Math.abs(myImpact) && (
                              <span className="text-[10px] text-emerald-500/80">
                                Estorno total: −{formatBRL(Math.abs(effAmount))}
                              </span>
                            )
                          ) : (
                            effAmount !== myImpact && (
                              <span className="text-[10px] text-slate-400">
                                Total compra: {formatBRL(effAmount)}
                              </span>
                            )
                          )}

                          {/* Friend Pending / Accepted Tags */}
                          {tx.assignedFriendUid && tx.paidByUid === currentUid && tx.acceptanceStatus !== 'accepted' && (
                            <Badge variant="outline" className="text-[9px] py-0 px-1 bg-amber-950/40 text-amber-300 border-amber-800/60 font-sans">
                              {formatBRL(friendShareOf(tx))} ({friendPctOf(tx)}%) aguarda aceite
                            </Badge>
                          )}
                          {tx.assignedFriendUid && tx.assignedFriendUid === currentUid && tx.acceptanceStatus !== 'accepted' && (
                            <Badge variant="outline" className="text-[9px] py-0 px-1 bg-amber-950/40 text-amber-300 border-amber-800/60 font-sans">
                              Pendente de aceite ({formatBRL(friendShareOf(tx))})
                            </Badge>
                          )}

                          {/* Shared Card Receivables / Payables Tag */}
                          {!isRefund && !tx.assignedFriendUid && tx.paidByUid === currentUid && effAmount > myImpact && (
                            <Badge variant="outline" className="text-[9px] py-0 px-1 bg-emerald-950/40 text-emerald-300 border-emerald-800/60 font-sans">
                              +{formatBRL(effAmount - myImpact)} a receber
                            </Badge>
                          )}
                          {tx.assignedFriendUid && tx.paidByUid === currentUid && tx.acceptanceStatus === 'accepted' && (
                            <Badge variant="outline" className="text-[9px] py-0 px-1 bg-emerald-950/40 text-emerald-300 border-emerald-800/60 font-sans">
                              +{formatBRL(friendShareOf(tx))} a receber de {tx.assignedFriendName || 'Amigo'}
                            </Badge>
                          )}
                          {tx.paidByUid !== currentUid && myImpact > 0 && (
                            <Badge variant="outline" className="text-[9px] py-0 px-1 bg-orange-950/40 text-orange-300 border-orange-800/60 font-sans">
                              Você deve ao titular
                            </Badge>
                          )}

                          {/* Comparison Tag vs Estimate */}
                          {hasComparison && diffEstimate !== 0 && (
                            <div className="flex items-center gap-1 mt-0.5">
                              {diffEstimate > 0 ? (
                                <Badge
                                  variant="outline"
                                  className="text-[9px] py-0 px-1 bg-rose-950/40 text-rose-300 border-rose-800/60 font-mono"
                                >
                                  +{formatBRL(diffEstimate)} vs est.
                                </Badge>
                              ) : (
                                <Badge
                                  variant="outline"
                                  className="text-[9px] py-0 px-1 bg-emerald-950/40 text-emerald-300 border-emerald-800/60 font-mono"
                                >
                                  {formatBRL(diffEstimate)} economizado
                                </Badge>
                              )}
                            </div>
                          )}
                        </div>
                      )}
                    </td>

                    {/* 7. Actions */}
                    <td className="py-2 px-2 text-center align-middle">
                      {isEditing ? (
                        <div className="flex items-center justify-center gap-1">
                          <Button
                            size="icon-sm"
                            onClick={() => saveInlineEdit(tx)}
                            className="size-6 bg-emerald-600 hover:bg-emerald-500 text-white"
                            title="Salvar alterações"
                          >
                            ✓
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => setEditingId(null)}
                            className="size-6 text-slate-400 hover:text-white"
                            title="Cancelar"
                          >
                            ✕
                          </Button>
                        </div>
                      ) : (
                        <div className="flex items-center justify-center gap-1 opacity-40 group-hover:opacity-100 transition-opacity">
                          <button
                            type="button"
                            onClick={() => onEditTx(tx)}
                            className="p-1 rounded text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-colors cursor-pointer"
                            title="Editar detalhes completos"
                          >
                            <Edit2 className="size-3.5" />
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              if (tx.installmentGroupId) {
                                setDeleteGroupTx(tx)
                              } else {
                                if (confirm(`Excluir "${tx.description}"?`)) deleteTransaction(tx.id)
                              }
                            }}
                            className="p-1 rounded text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors cursor-pointer"
                            title={tx.installmentGroupId ? 'Opções de exclusão do parcelamento' : 'Excluir lançamento'}
                          >
                            <Trash2 className="size-3.5" />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>



      {/* Modal para confirmação de exclusão individual ou de todo o grupo de parcelas */}
      <DeleteInstallmentDialog
        open={Boolean(deleteGroupTx)}
        onOpenChange={(open) => !open && setDeleteGroupTx(null)}
        tx={deleteGroupTx}
      />
    </div>
  )
}
