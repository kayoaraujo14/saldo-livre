import { useState, useMemo } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { useFinance } from '@/store/FinanceContext'
import { getThirdPartyInfo, impactFor, effectiveAmount } from '@/lib/finance'
import { formatBRL, formatShortDate, formatCreatedAt, monthLabel } from '@/lib/utils'
import type { Transaction } from '@/types'
import {
  History,
  Search,
  User,
  CreditCard,
  Calendar,
  Clock,
  ArrowUpRight,
  ArrowDownLeft,
  RotateCcw,
  ExternalLink,
  Edit2,
  Layers,
  Filter,
} from 'lucide-react'

interface RecentActivityModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onEditTx?: (tx: Transaction) => void
}

type ActivityFilter = 'all' | 'mine' | 'friends' | 'third_party' | 'income' | 'expense'

export function RecentActivityModal({
  open,
  onOpenChange,
  onEditTx,
}: RecentActivityModalProps) {
  const { transactions, cards, me, partner, setMonth } = useFinance()
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState<ActivityFilter>('all')
  const [displayCount, setDisplayCount] = useState(40)

  const currentUid = me?.uid ?? ''

  // Ordena estritamente pela data em que a transação foi cadastrada no sistema (createdAt descendente)
  const sortedTransactions = useMemo(() => {
    return [...transactions].sort((a, b) => {
      const timeA = a.createdAt ?? (a.dueDate ? new Date(a.dueDate).getTime() : 0)
      const timeB = b.createdAt ?? (b.dueDate ? new Date(b.dueDate).getTime() : 0)
      return timeB - timeA
    })
  }, [transactions])

  // Contadores para o cabeçalho
  const stats = useMemo(() => {
    const now = Date.now()
    const sevenDaysAgo = now - 7 * 86400000
    let last7DaysCount = 0
    let withThirdPartyCount = 0
    let friendsCount = 0

    for (const tx of transactions) {
      const time = tx.createdAt ?? 0
      if (time >= sevenDaysAgo) last7DaysCount++
      const third = getThirdPartyInfo(tx, currentUid, partner?.name)
      if (third) withThirdPartyCount++
      if (
        tx.assignedFriendUid === currentUid ||
        (tx.assignedFriendUid && tx.assignedFriendUid !== currentUid) ||
        (tx.createdByUid && tx.createdByUid !== currentUid)
      ) {
        friendsCount++
      }
    }

    return {
      total: transactions.length,
      last7DaysCount,
      withThirdPartyCount,
      friendsCount,
    }
  }, [transactions, currentUid, partner])

  // Filtra de acordo com a aba e busca
  const filteredList = useMemo(() => {
    return sortedTransactions.filter((tx) => {
      // 1. Busca por texto
      if (search.trim()) {
        const q = search.toLowerCase()
        const descMatch = tx.description.toLowerCase().includes(q)
        const catMatch = tx.category.toLowerCase().includes(q)
        const third = getThirdPartyInfo(tx, currentUid, partner?.name)
        const thirdMatch = third ? third.name.toLowerCase().includes(q) : false
        const card = cards.find((c) => c.id === tx.cardId)
        const cardMatch = card ? card.cardName.toLowerCase().includes(q) : false
        if (!descMatch && !catMatch && !thirdMatch && !cardMatch) return false
      }

      // 2. Filtro de abas
      if (filter === 'income') return tx.type === 'income'
      if (filter === 'expense') return tx.type === 'expense'
      if (filter === 'mine') {
        return tx.createdByUid === currentUid && !tx.assignedFriendUid
      }
      if (filter === 'friends') {
        return (
          tx.assignedFriendUid === currentUid ||
          (tx.assignedFriendUid && tx.assignedFriendUid !== currentUid) ||
          (tx.createdByUid && tx.createdByUid !== currentUid)
        )
      }
      if (filter === 'third_party') {
        const third = getThirdPartyInfo(tx, currentUid, partner?.name)
        return Boolean(third)
      }

      return true
    })
  }, [sortedTransactions, search, filter, currentUid, partner, cards])

  const visibleList = filteredList.slice(0, displayCount)

  const handleNavigateToMonth = (monthKey: string) => {
    setMonth(monthKey)
    onOpenChange(false)
  }

  const handleEdit = (tx: Transaction) => {
    if (onEditTx) {
      onEditTx(tx)
      onOpenChange(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-3xl w-full max-h-[92vh] flex flex-col p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl overflow-hidden">
        {/* Header */}
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.08] shrink-0">
          <div className="flex items-center justify-between gap-3 min-w-0">
            <div className="flex items-center gap-2.5 min-w-0 flex-1">
              <div className="size-10 rounded-xl bg-sky-500/15 border border-sky-500/30 text-sky-400 flex items-center justify-center shrink-0 shadow-[0_0_15px_-3px_rgba(56,189,248,0.3)]">
                <History className="size-5" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <DialogTitle className="text-base sm:text-lg font-bold text-white tracking-tight truncate">
                    Histórico de Lançamentos Recentes
                  </DialogTitle>
                  <Badge variant="outline" className="text-[10px] font-mono bg-sky-500/10 text-sky-300 border-sky-500/30 shrink-0">
                    {filteredList.length} encontrados
                  </Badge>
                </div>
                <DialogDescription className="text-xs text-slate-400 truncate">
                  Ordenado pela <strong>data de cadastro no sistema</strong>, independente do mês de realização ou vencimento.
                </DialogDescription>
              </div>
            </div>
          </div>

          {/* Quick Metrics Ribbon */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2.5">
            <div className="p-2 rounded-xl bg-slate-900/60 border border-slate-800 text-xs">
              <span className="text-[10px] text-slate-400 block font-medium">Total no Sistema</span>
              <span className="text-sm font-bold text-white font-mono">{stats.total}</span>
            </div>
            <div className="p-2 rounded-xl bg-sky-950/40 border border-sky-800/50 text-xs">
              <span className="text-[10px] text-sky-300 block font-medium">Últimos 7 dias</span>
              <span className="text-sm font-bold text-sky-200 font-mono">+{stats.last7DaysCount}</span>
            </div>
            <div className="p-2 rounded-xl bg-amber-950/40 border border-amber-800/50 text-xs">
              <span className="text-[10px] text-amber-300 block font-medium">Com Terceiros</span>
              <span className="text-sm font-bold text-amber-200 font-mono">{stats.withThirdPartyCount}</span>
            </div>
            <div className="p-2 rounded-xl bg-violet-950/40 border border-violet-800/50 text-xs">
              <span className="text-[10px] text-violet-300 block font-medium">Rede de Amigos</span>
              <span className="text-sm font-bold text-violet-200 font-mono">{stats.friendsCount}</span>
            </div>
          </div>
        </DialogHeader>

        {/* Search & Filter Toolbar */}
        <div className="space-y-2 py-3 border-b border-white/[0.06] shrink-0">
          <div className="relative">
            <Search className="size-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por descrição, terceiro, amigo, cartão, categoria..."
              className="h-9 pl-9 text-xs bg-slate-950/80 border-slate-800 focus-visible:ring-sky-500/40"
            />
          </div>

          {/* Filter Pills */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
            <button
              type="button"
              onClick={() => setFilter('all')}
              className={`px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer ${
                filter === 'all'
                  ? 'bg-sky-600 text-white shadow-xs font-semibold'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              Todos ({sortedTransactions.length})
            </button>
            <button
              type="button"
              onClick={() => setFilter('third_party')}
              className={`px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
                filter === 'third_party'
                  ? 'bg-amber-600 text-white shadow-xs font-semibold'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <User className="size-3 text-amber-400" />
              <span>Com Terceiros</span>
            </button>
            <button
              type="button"
              onClick={() => setFilter('friends')}
              className={`px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
                filter === 'friends'
                  ? 'bg-violet-600 text-white shadow-xs font-semibold'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <span>🧑‍🤝‍🧑 Amigos & Compartilhados</span>
            </button>
            <button
              type="button"
              onClick={() => setFilter('mine')}
              className={`px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer ${
                filter === 'mine'
                  ? 'bg-slate-700 text-white shadow-xs font-semibold'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              Individuais
            </button>
            <button
              type="button"
              onClick={() => setFilter('income')}
              className={`px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1 ${
                filter === 'income'
                  ? 'bg-emerald-600 text-white shadow-xs font-semibold'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <ArrowDownLeft className="size-3 text-emerald-400" />
              <span>Entradas</span>
            </button>
            <button
              type="button"
              onClick={() => setFilter('expense')}
              className={`px-2.5 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1 ${
                filter === 'expense'
                  ? 'bg-rose-600 text-white shadow-xs font-semibold'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <ArrowUpRight className="size-3 text-rose-400" />
              <span>Despesas</span>
            </button>
          </div>
        </div>

        {/* Transaction History List */}
        <div className="flex-1 overflow-y-auto pr-1 space-y-2.5 py-2">
          {visibleList.length === 0 ? (
            <div className="text-center py-12 text-slate-400">
              <Filter className="size-8 mx-auto mb-2 opacity-30 text-sky-400" />
              <p className="font-semibold text-slate-300 text-sm">Nenhuma transação encontrada</p>
              <p className="text-xs text-slate-500 mt-1">
                Tente ajustar os filtros ou o termo de busca digitado.
              </p>
            </div>
          ) : (
            visibleList.map((tx) => {
              const effAmount = effectiveAmount(tx)
              const myImpact = impactFor(tx, currentUid, transactions)
              const isIncome = tx.type === 'income'
              const isRefund = tx.category === 'Estorno' || effAmount < 0 || tx.totalAmount < 0
              const card = cards.find((c) => c.id === tx.cardId)
              const thirdParty = getThirdPartyInfo(tx, currentUid, partner?.name)

              return (
                <div
                  key={tx.id}
                  className="p-3 sm:p-3.5 rounded-xl bg-slate-900/50 hover:bg-slate-900/80 border border-white/[0.06] hover:border-sky-500/30 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 group"
                >
                  {/* Left Column: Icons & Details */}
                  <div className="flex items-start gap-3 min-w-0">
                    <div
                      className={`size-9 rounded-xl flex items-center justify-center shrink-0 border mt-0.5 ${
                        isIncome
                          ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-300'
                          : isRefund
                            ? 'bg-cyan-500/15 border-cyan-500/30 text-cyan-300'
                            : 'bg-slate-800/80 border-slate-700 text-slate-300'
                      }`}
                    >
                      {isIncome ? (
                        <ArrowDownLeft className="size-4 text-emerald-400" />
                      ) : isRefund ? (
                        <RotateCcw className="size-4 text-cyan-400" />
                      ) : (
                        <ArrowUpRight className="size-4 text-rose-400" />
                      )}
                    </div>

                    <div className="min-w-0 space-y-1">
                      {/* Description & Badges */}
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <span className="font-semibold text-sm text-slate-100 group-hover:text-white transition-colors truncate">
                          {tx.description}
                        </span>

                        {/* Third party badge */}
                        {thirdParty && (
                          <Badge
                            variant="outline"
                            className={`text-[10px] py-0 px-1.5 gap-1 font-semibold ${
                              thirdParty.type === 'external'
                                ? 'bg-amber-500/15 text-amber-300 border-amber-500/40'
                                : thirdParty.type === 'friend'
                                  ? 'bg-violet-500/15 text-violet-300 border-violet-500/40'
                                  : 'bg-cyan-500/15 text-cyan-300 border-cyan-500/40'
                            }`}
                          >
                            <User className="size-2.5" />
                            <span>
                              {thirdParty.label}: <strong className="text-white">{thirdParty.name}</strong>
                            </span>
                          </Badge>
                        )}

                        {/* Card Badge */}
                        {card && (
                          <Badge
                            variant="outline"
                            className="text-[10px] py-0 px-1.5 bg-slate-800/80 text-slate-300 border-slate-700 gap-1"
                          >
                            <CreditCard className="size-2.5 text-violet-400" />
                            <span>{card.cardName}</span>
                          </Badge>
                        )}

                        {/* Installment Badge */}
                        {tx.installmentNumber && tx.installmentsTotal && (
                          <Badge
                            variant="outline"
                            className="text-[10px] py-0 px-1.5 bg-violet-950/60 text-violet-300 border-violet-800 font-mono gap-1"
                          >
                            <Layers className="size-2.5" />
                            <span>
                              {tx.installmentNumber}/{tx.installmentsTotal}
                            </span>
                          </Badge>
                        )}
                      </div>

                      {/* Timestamps & Financial Dates Ribbon */}
                      <div className="flex items-center gap-2 flex-wrap text-xs text-slate-400">
                        {/* 1. Registration Date (CRUCIAL) */}
                        <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-sky-300 bg-sky-500/10 border border-sky-500/20 px-2 py-0.5 rounded-md">
                          <Clock className="size-3 text-sky-400" />
                          <span>Cadastrado: {formatCreatedAt(tx.createdAt)}</span>
                        </span>

                        {/* 2. Competence Month & Due Date */}
                        <span className="inline-flex items-center gap-1 text-[11px] text-slate-400 bg-slate-800/60 border border-slate-700/60 px-2 py-0.5 rounded-md">
                          <Calendar className="size-3 text-slate-500" />
                          <span>
                            Vencimento: {formatShortDate(tx.dueDate)} • {monthLabel(tx.competenceMonth)}
                          </span>
                        </span>

                        {/* Category */}
                        <span className="text-[10px] text-slate-500">
                          {tx.category}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Right Column: Values & Quick Actions */}
                  <div className="flex sm:flex-col items-center sm:items-end justify-between gap-2 shrink-0 border-t sm:border-t-0 border-white/[0.04] pt-2 sm:pt-0">
                    <div className="text-right">
                      <div className="font-mono text-sm font-extrabold text-white">
                        {isIncome ? (
                          <span className="text-emerald-400">+{formatBRL(Math.abs(effAmount))}</span>
                        ) : isRefund ? (
                          <span className="text-cyan-400">{formatBRL(effAmount)}</span>
                        ) : (
                          <span className="text-slate-100">−{formatBRL(Math.abs(effAmount))}</span>
                        )}
                      </div>

                      {/* Real Impact */}
                      {Math.abs(effAmount) !== Math.abs(myImpact) && (
                        <div className="text-[10px] text-slate-400 font-mono">
                          Meu impacto:{' '}
                          <span className="font-semibold text-slate-200">
                            {formatBRL(Math.abs(myImpact))}
                          </span>
                        </div>
                      )}
                    </div>

                    {/* Action Buttons */}
                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() => handleNavigateToMonth(tx.competenceMonth)}
                        className="h-7 px-2.5 rounded-lg bg-slate-800/90 hover:bg-slate-700 text-slate-300 hover:text-white text-[11px] font-medium flex items-center gap-1 transition-colors cursor-pointer border border-slate-700/60"
                        title={`Ir para o extrato de ${monthLabel(tx.competenceMonth)}`}
                      >
                        <ExternalLink className="size-3 text-sky-400" />
                        <span>Ver no Mês</span>
                      </button>

                      {onEditTx && (
                        <button
                          type="button"
                          onClick={() => handleEdit(tx)}
                          className="h-7 w-7 rounded-lg bg-slate-800/90 hover:bg-slate-700 text-slate-300 hover:text-white flex items-center justify-center transition-colors cursor-pointer border border-slate-700/60"
                          title="Editar esta transação"
                        >
                          <Edit2 className="size-3 text-slate-400" />
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              )
            })
          )}

          {/* Load More Button */}
          {visibleList.length < filteredList.length && (
            <div className="text-center pt-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setDisplayCount((prev) => prev + 40)}
                className="text-xs bg-slate-900 border-slate-800 hover:border-slate-700 text-slate-300 cursor-pointer"
              >
                Carregar mais transações ({filteredList.length - visibleList.length} restantes)
              </Button>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
