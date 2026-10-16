import { useState, useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFinance } from '@/store/FinanceContext'
import {
  CATEGORIES,
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
} from '@/lib/finance'
import { formatBRL, formatShortDate, todayKey } from '@/lib/utils'
import {
  Search,
  Plus,
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
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'
import { DeleteInstallmentDialog } from './modals/DeleteInstallmentDialog'
import { MobileTransactionCard } from './mobile/MobileTransactionCard'

type TabFilter = 'all' | 'income' | 'expense' | 'refund' | 'pending_estimated' | 'paid' | 'shared_card' | 'third_party' | 'friends_installments'

interface TransactionTableProps {
  onOpenNewTx?: () => void
  onEditTx: (tx: Transaction) => void
}

export function TransactionTable({ onEditTx }: TransactionTableProps) {
  const {
    monthTransactions,
    cards,
    me,
    partner,
    cycleStatus,
    changeSplit,
    changeAmount,
    updateTransaction,
    deleteTransaction,
    createManual,
    respondChargeRequest,
  } = useFinance()

  const [activeTab, setActiveTab] = useState<TabFilter>('all')
  const [search, setSearch] = useState('')
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [pageSize, setPageSize] = useState<number>(30)
  const [currentPage, setCurrentPage] = useState<number>(1)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editDesc, setEditDesc] = useState('')
  const [editDate, setEditDate] = useState('')
  const [editAmount, setEditAmount] = useState('')
  const [deleteGroupTx, setDeleteGroupTx] = useState<Transaction | null>(null)

  // Quick inline add state
  const [quickType, setQuickType] = useState<'expense' | 'income'>('expense')
  const [quickDesc, setQuickDesc] = useState('')
  const [quickAmount, setQuickAmount] = useState('')
  const [quickCat, setQuickCat] = useState<Category>('Alimentação')
  const [quickSplit, setQuickSplit] = useState<SplitType>('mine')

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
        const isShared = card && (card.sharedWithUsers.length > 0 || card.sharedWithUids.length > 0)
        if (!isShared && !tx.reserveCardId) return false
      } else if (activeTab === 'third_party') {
        const isThird = tx.allocations.some((a) => a.type === 'EXTERNAL')
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
        const matchesAlloc = tx.allocations.some((a) => a.name.toLowerCase().includes(q))
        if (!matchesDesc && !matchesCat && !matchesAlloc) return false
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

  // Pagination calculation
  const totalPages = pageSize === 0 ? 1 : Math.max(1, Math.ceil(filteredTxs.length / pageSize))
  const startIdx = pageSize === 0 ? 0 : (currentPage - 1) * pageSize
  const endIdx = pageSize === 0 ? filteredTxs.length : startIdx + pageSize
  const pagedTxs = useMemo(() => {
    return pageSize === 0 ? filteredTxs : filteredTxs.slice(startIdx, endIdx)
  }, [filteredTxs, startIdx, endIdx, pageSize])

  // Count badges
  const counts = useMemo(() => {
    return {
      all: monthTransactions.length,
      income: monthTransactions.filter((t: Transaction) => t.type === 'income').length,
      expense: monthTransactions.filter((t: Transaction) => t.type !== 'income').length,
      refund: monthTransactions.filter((t: Transaction) => t.category === 'Estorno' || t.totalAmount < 0 || effectiveAmount(t) < 0).length,
      pending: monthTransactions.filter((t: Transaction) => t.status !== 'paid').length,
      paid: monthTransactions.filter((t: Transaction) => t.status === 'paid').length,
      shared: monthTransactions.filter((t: Transaction) => {
        const card = cards.find((c) => c.id === t.cardId)
        return (card && (card.sharedWithUsers.length > 0 || card.sharedWithUids.length > 0)) || t.reserveCardId
      }).length,
      third: monthTransactions.filter((t: Transaction) => t.allocations.some((a) => a.type === 'EXTERNAL')).length,
      friends_installments: monthTransactions.filter((t: Transaction) => Boolean(t.assignedFriendUid || t.installmentGroupId)).length,
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

  const handleQuickAdd = async (e: React.FormEvent) => {
    e.preventDefault()
    const num = parseFloat(quickAmount.replace(',', '.'))
    if (!quickDesc.trim() || isNaN(num) || num <= 0) return

    const isRefund = quickCat === 'Estorno'
    const finalAmount = isRefund ? -Math.abs(num) : num

    await createManual({
      description: quickDesc.trim(),
      amount: finalAmount,
      category: quickType === 'income' ? 'Receita' : quickCat,
      date: todayKey(),
      type: quickType,
      status: quickType === 'income' ? 'paid' : 'pending',
      splitType: quickType === 'income' ? 'mine' : quickSplit,
      paymentMethod: quickType === 'income' ? 'pix' : 'credit_card',
    })

    setQuickDesc('')
    setQuickAmount('')
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
          pagedTxs.map((tx: Transaction) => (
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
              <th className="py-2.5 px-3 w-28">Status</th>
              <th
                onClick={() => setSortOrder((prev) => (prev === 'asc' ? 'desc' : 'asc'))}
                className="py-2.5 px-3 w-28 cursor-pointer select-none hover:text-white transition-colors group"
                title="Clique para alternar ordem cronológica (1→31 ou 31→1)"
              >
                <div className="flex items-center gap-1.5">
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
              <th className="py-2.5 px-3 min-w-[200px]">Descrição</th>
              <th className="py-2.5 px-3 w-32">Categoria</th>
              <th className="py-2.5 px-3 w-40">Divisão / Pagador</th>
              <th className="py-2.5 px-3 w-44 text-right">Valor & Meu Impacto</th>
              <th className="py-2.5 px-2 w-16 text-center">Ações</th>
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
              pagedTxs.map((tx: Transaction) => {
                const isEditing = editingId === tx.id
                const effAmount = effectiveAmount(tx)
                const myImpact = impactFor(tx, currentUid, monthTransactions)
                const split = detectSplit(tx, currentUid)
                const isRefund = tx.category === 'Estorno' || effAmount < 0 || tx.totalAmount < 0
                const card = cards.find((c) => c.id === tx.cardId)
                const isReserve = Boolean(tx.reserveCardId)
                const reserveInfo = isReserve ? getReserveInfo(tx, monthTransactions) : null

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

                    {/* 4. Category (Inline Select) */}
                    <td className="py-2 px-3 align-middle">
                      <Select
                        value={tx.category}
                        onValueChange={(cat) => updateTransaction(tx.id, { category: cat as Category })}
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
                    </td>

                    {/* 5. Division / Payer (Inline Select) */}
                    <td className="py-2 px-3 align-middle">
                      {tx.type === 'income' ? (
                        <div className="flex items-center gap-1.5">
                          <Badge variant="outline" className="text-[10px] py-0.5 px-2 bg-emerald-950/40 text-emerald-300 border-emerald-800/60 font-medium">
                            💰 Receita Pessoal
                          </Badge>
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
                              <SelectItem value="external">🏷️ Terceiro Externo</SelectItem>
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
                          {split === 'custom' && tx.allocations && tx.allocations.length > 0 && (
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

      {/* Pagination & Count Toolbar */}
      {filteredTxs.length > 0 && (
        <div className="px-3.5 py-1.5 border-t border-white/[0.04] bg-slate-950/60 flex items-center justify-between text-xs text-slate-400 shrink-0">
          <div className="flex items-center gap-1.5 text-[11px]">
            <span>
              Mostrando <strong className="text-slate-200">{startIdx + 1}–{Math.min(endIdx, filteredTxs.length)}</strong> de{' '}
              <strong className="text-slate-200">{filteredTxs.length}</strong>
            </span>
          </div>

          <div className="flex items-center gap-3">
            {totalPages > 1 && (
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  disabled={currentPage <= 1}
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  className="size-5 text-slate-400 hover:text-white disabled:opacity-30 cursor-pointer"
                  title="Página anterior"
                >
                  <ChevronLeft className="size-3" />
                </Button>
                <span className="text-[10px] font-mono text-slate-300 px-1">
                  {currentPage} / {totalPages}
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  disabled={currentPage >= totalPages}
                  onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                  className="size-5 text-slate-400 hover:text-white disabled:opacity-30 cursor-pointer"
                  title="Próxima página"
                >
                  <ChevronRight className="size-3" />
                </Button>
              </div>
            )}

            {filteredTxs.length > 30 && (
              <div className="flex items-center gap-1 border-l border-slate-800/80 pl-2">
                <span className="text-[10px] text-slate-500">Por pág:</span>
                {[30, 60, 0].map((size) => (
                  <button
                    key={size}
                    type="button"
                    onClick={() => {
                      setPageSize(size)
                      setCurrentPage(1)
                    }}
                    className={`px-1.5 py-0.5 rounded text-[10px] font-mono transition-colors cursor-pointer ${
                      pageSize === size
                        ? 'bg-slate-800 text-emerald-400 font-bold border border-slate-700'
                        : 'text-slate-500 hover:text-slate-300'
                    }`}
                  >
                    {size === 0 ? 'Tudo' : size}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Footer: Quick Add Row (Desktop) */}
      <div className="hidden sm:block p-3 border-t border-white/[0.06] bg-slate-900/60">
        <form onSubmit={handleQuickAdd} className="flex items-center gap-2">
          {/* Quick type toggle */}
          <div className="flex items-center rounded-lg bg-slate-950 p-0.5 border border-slate-800 shrink-0">
            <button
              type="button"
              onClick={() => setQuickType('expense')}
              className={`px-2 py-1 rounded text-xs font-medium cursor-pointer transition-colors ${
                quickType === 'expense'
                  ? 'bg-rose-500/20 text-rose-300 font-semibold shadow-xs'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              💸 Despesa
            </button>
            <button
              type="button"
              onClick={() => {
                setQuickType('income')
                setQuickCat('Receita')
                setQuickSplit('mine')
              }}
              className={`px-2 py-1 rounded text-xs font-medium cursor-pointer transition-colors ${
                quickType === 'income'
                  ? 'bg-emerald-500/20 text-emerald-300 font-semibold shadow-xs'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              💰 Entrada
            </button>
          </div>

          <Input
            value={quickDesc}
            onChange={(e) => setQuickDesc(e.target.value)}
            placeholder={
              quickType === 'income'
                ? '+ Nova receita (ex: Pix recebido, Freela, Bônus)...'
                : '+ Nova despesa rápida (ex: Farmácia, Almoço)...'
            }
            className="h-8 text-xs bg-slate-950/70 border-slate-800 flex-1"
          />

          <Input
            type="number"
            step="0.01"
            min={0.01}
            value={quickAmount}
            onChange={(e) => setQuickAmount(e.target.value)}
            placeholder="R$ 0,00"
            className="h-8 text-xs bg-slate-950/70 border-slate-800 w-24 font-mono shrink-0"
          />

          {quickType === 'expense' ? (
            <>
              <Select value={quickCat} onValueChange={(c) => setQuickCat(c as Category)}>
                <SelectTrigger className="h-8 text-xs bg-slate-950/70 border-slate-800 w-28 shrink-0">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CATEGORIES.filter((c) => c !== 'Receita').map((c) => (
                    <SelectItem key={c} value={c}>
                      {c}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <Select value={quickSplit} onValueChange={(s) => setQuickSplit(s as SplitType)}>
                <SelectTrigger className="h-8 text-xs bg-slate-950/70 border-slate-800 w-28 shrink-0">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="mine">👤 Meu</SelectItem>
                  {partner && <SelectItem value="split_50">🤝 50/50</SelectItem>}
                  <SelectItem value="external">🏷️ Terceiro</SelectItem>
                </SelectContent>
              </Select>
            </>
          ) : (
            <Badge variant="outline" className="h-8 px-2.5 text-xs bg-emerald-500/10 text-emerald-300 border-emerald-500/30 flex items-center gap-1 font-mono shrink-0">
              💰 Receita (+Saldo)
            </Badge>
          )}

          <Button
            type="submit"
            size="sm"
            className="h-8 text-xs bg-emerald-600 hover:bg-emerald-500 text-white font-medium gap-1 px-3 shrink-0 cursor-pointer"
          >
            <Plus className="size-3.5" /> Adicionar
          </Button>
        </form>
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
