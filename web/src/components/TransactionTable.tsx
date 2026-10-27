import { useState, useMemo } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/overlays'
import { useFinance } from '@/store/FinanceContext'
import {
  EXPENSE_CATEGORIES,
  INCOME_CATEGORIES,
  LEGACY_CATEGORIES,
  getCategoryDetail,
  type Category,
  type SplitType,
  type Transaction,
} from '@/types'
import {
  canUserEditTransaction,
  effectiveAmount,
  impactFor,
  detectSplit,
  getReserveInfo,
  friendPctOf,
  friendShareOf,
  getThirdPartyInfo,
  isSharedCard,
  getTxPersons,
  isMyExpenseTx,
  isPureMineTx,
  isSharedWithMeTx,
} from '@/lib/finance'
import { formatBRL, formatNubankDateHeader, formatShortDate, round2 } from '@/lib/utils'
import {
  Search,
  RefreshCw,
  CreditCard,
  Shield,
  Landmark,
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
  X,
  Lock,
  Receipt,
  Calendar,
} from 'lucide-react'
import { DeleteInstallmentDialog } from './modals/DeleteInstallmentDialog'
import { MobileTransactionCard } from './mobile/MobileTransactionCard'
import { consolidateMonthTransactions, type ConsolidatedTransaction } from '@/lib/invoices'

type TabFilter = 'all' | 'income' | 'expense' | 'my_expenses' | 'refund' | 'pending_estimated' | 'paid' | 'shared_card' | 'third_party' | 'friends_installments'

interface ColumnFilters {
  status: ('paid' | 'pending' | 'estimated')[]
  categories: string[]
  subcategories: string[]
  splits: string[]
  persons: string[]
  dateStart: string
  dateEnd: string
  specificDays: string[]
  amountMin: string
  amountMax: string
  amountType: 'all' | 'income' | 'expense' | 'refund'
  sortBy: 'date_asc' | 'date_desc' | 'amount_asc' | 'amount_desc' | null
}

const INITIAL_COLUMN_FILTERS: ColumnFilters = {
  status: [],
  categories: [],
  subcategories: [],
  splits: [],
  persons: [],
  dateStart: '',
  dateEnd: '',
  specificDays: [],
  amountMin: '',
  amountMax: '',
  amountType: 'all',
  sortBy: null,
}

interface TransactionTableProps {
  onEditTx: (tx: Transaction) => void
  onRefundTx?: (tx: Transaction) => void
  onToggleExpand?: () => void
  isExpanded?: boolean
  onOpenInvoices?: (cardId?: string) => void
}

export function TransactionTable({
  onEditTx,
  onRefundTx,
  onToggleExpand,
  isExpanded,
  onOpenInvoices,
}: TransactionTableProps) {
  const {
    summary,
    monthTransactions,
    transactions,
    month,
    cards,
    me,
    partner,
    cycleStatus,
    setStatus,
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
  const [consolidateInvoices, setConsolidateInvoices] = useState(true)
  const [columnFilters, setColumnFilters] = useState<ColumnFilters>(INITIAL_COLUMN_FILTERS)
  const [categorySearchQuery, setCategorySearchQuery] = useState('')
  const [personSearchQuery, setPersonSearchQuery] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editDesc, setEditDesc] = useState('')
  const [editDate, setEditDate] = useState('')
  const [editAmount, setEditAmount] = useState('')
  const [deleteGroupTx, setDeleteGroupTx] = useState<Transaction | null>(null)

  const currentUid = me?.uid ?? ''

  // Lista base de transações: consolida faturas de cartão no dia do vencimento quando ativado
  const effectiveTransactions = useMemo(() => {
    return consolidateMonthTransactions(
      monthTransactions,
      transactions,
      cards,
      currentUid,
      month,
      consolidateInvoices,
    )
  }, [monthTransactions, transactions, cards, currentUid, month, consolidateInvoices])

  // Unique options and counts for Excel popovers
  const availableCategories = useMemo(() => {
    const map = new Map<string, number>()
    effectiveTransactions.forEach((tx) => {
      const isLegacy =
        (LEGACY_CATEGORIES as readonly string[]).includes(tx.category) &&
        !(EXPENSE_CATEGORIES as readonly string[]).includes(tx.category) &&
        !(INCOME_CATEGORIES as readonly string[]).includes(tx.category)
      if (!isLegacy && tx.category) {
        map.set(tx.category, (map.get(tx.category) || 0) + 1)
      }
    })
    return Array.from(map.entries()).sort((a, b) => b[1] - a[1])
  }, [effectiveTransactions])

  const availableSplits = useMemo(() => {
    const map = new Map<string, number>()
    effectiveTransactions.forEach((tx) => {
      const s = detectSplit(tx, currentUid)
      map.set(s, (map.get(s) || 0) + 1)
    })
    return Array.from(map.entries())
  }, [effectiveTransactions, currentUid])

  const availablePersons = useMemo(() => {
    let myAllCount = 0
    let myAllTotal = 0
    let myPureCount = 0
    let myPureTotal = 0
    let mySharedCount = 0
    let mySharedTotal = 0

    const othersMap = new Map<string, { count: number; totalAmount: number; role: string }>()

    effectiveTransactions.forEach((tx) => {
      const myImp = Math.abs(impactFor(tx, currentUid, effectiveTransactions))

      if (isMyExpenseTx(tx, currentUid, effectiveTransactions)) {
        myAllCount++
        myAllTotal += myImp
        if (isPureMineTx(tx, currentUid, effectiveTransactions)) {
          myPureCount++
          myPureTotal += myImp
        } else {
          mySharedCount++
          mySharedTotal += myImp
        }
      }

      const pList = getTxPersons(tx, currentUid, partner?.name, effectiveTransactions)
      pList.forEach((p) => {
        if (p.role !== 'me' && p.name !== '100% Meu') {
          const prev = othersMap.get(p.name) || { count: 0, totalAmount: 0, role: p.role }
          othersMap.set(p.name, {
            count: prev.count + 1,
            totalAmount: round2(prev.totalAmount + p.amount),
            role: p.role,
          })
        }
      })
    })

    const result: { name: string; count: number; totalAmount: number; role: string }[] = []

    if (myAllCount > 0) {
      result.push({
        name: 'Meus Gastos (Todos)',
        count: myAllCount,
        totalAmount: round2(myAllTotal),
        role: 'me_all',
      })
    }
    if (myPureCount > 0) {
      result.push({
        name: '100% Meu (Exclusivo)',
        count: myPureCount,
        totalAmount: round2(myPureTotal),
        role: 'me_pure',
      })
    }
    if (mySharedCount > 0) {
      result.push({
        name: 'Meus Rateados',
        count: mySharedCount,
        totalAmount: round2(mySharedTotal),
        role: 'me_shared',
      })
    }

    const sortedOthers = Array.from(othersMap.entries())
      .map(([name, data]) => ({ name, ...data }))
      .sort((a, b) => {
        if (a.role === 'partner' && b.role !== 'partner') return -1
        if (b.role === 'partner' && a.role !== 'partner') return 1
        return a.name.localeCompare(b.name)
      })

    return [...result, ...sortedOthers]
  }, [effectiveTransactions, currentUid, partner?.name])

  const availableDays = useMemo(() => {
    const map = new Map<string, number>()
    effectiveTransactions.forEach((tx) => {
      const d = (tx.dueDate || tx.competenceMonth || '').slice(8, 10)
      if (d) {
        map.set(d, (map.get(d) || 0) + 1)
      }
    })
    return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0]))
  }, [effectiveTransactions])

  const statusCounts = useMemo(() => {
    let paid = 0
    let pending = 0
    let estimated = 0
    effectiveTransactions.forEach((tx) => {
      if (tx.status === 'paid') paid++
      else if (tx.status === 'pending') pending++
      else if (tx.status === 'estimated') estimated++
    })
    return { paid, pending, estimated }
  }, [effectiveTransactions])
  // Filter and sort list by transaction date and excel column filters
  const filteredTxs = useMemo(() => {
    const list = effectiveTransactions.filter((tx: ConsolidatedTransaction) => {
      // 1. Tab filter
      if (activeTab === 'income') {
        if (tx.type !== 'income') return false
      } else if (activeTab === 'expense') {
        if (tx.type === 'income') return false
      } else if (activeTab === 'my_expenses') {
        if (tx.type === 'income') return false
        if (!isMyExpenseTx(tx, currentUid, effectiveTransactions)) return false
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

      // 2. Global search filter
      if (search.trim()) {
        const q = search.toLowerCase()
        const matchesDesc = tx.description.toLowerCase().includes(q)
        const matchesCat = tx.category.toLowerCase().includes(q)
        const matchesSubcat = tx.subcategory?.toLowerCase().includes(q)
        const matchesAlloc = (tx.allocations || []).some((a) => a.name.toLowerCase().includes(q))
        
        // Se for fatura consolidada, também pesquisa nos itens individuais da fatura
        const matchesInvoiceItems = tx.invoiceMeta?.invoice.items.some((item) =>
          item.description.toLowerCase().includes(q) ||
          item.category.toLowerCase().includes(q) ||
          item.subcategory?.toLowerCase().includes(q)
        )

        if (!matchesDesc && !matchesCat && !matchesSubcat && !matchesAlloc && !matchesInvoiceItems) return false
      }

      // 3. Excel Column Filter: Status
      if (columnFilters.status.length > 0) {
        if (!columnFilters.status.includes(tx.status)) return false
      }

      // 4. Excel Column Filter: Date
      const txDate = tx.dueDate || tx.competenceMonth || ''
      if (columnFilters.dateStart && txDate < columnFilters.dateStart) return false
      if (columnFilters.dateEnd && txDate > columnFilters.dateEnd) return false
      if (columnFilters.specificDays.length > 0) {
        const day = txDate.slice(8, 10)
        if (!columnFilters.specificDays.includes(day)) return false
      }

      // 5. Excel Column Filter: Category & Subcategory
      if (columnFilters.categories.length > 0) {
        const matchesDirect = columnFilters.categories.includes(tx.category)
        const matchesInvoice = tx.invoiceMeta?.invoice.items.some((item) => columnFilters.categories.includes(item.category))
        if (!matchesDirect && !matchesInvoice) return false
      }
      if (columnFilters.subcategories.length > 0) {
        const matchesDirect = tx.subcategory && columnFilters.subcategories.includes(tx.subcategory)
        const matchesInvoice = tx.invoiceMeta?.invoice.items.some((item) => item.subcategory && columnFilters.subcategories.includes(item.subcategory))
        if (!matchesDirect && !matchesInvoice) return false
      }

      // 6. Excel Column Filter: Split & Person / Responsável
      if (columnFilters.splits.length > 0) {
        const sp = detectSplit(tx, currentUid)
        if (!columnFilters.splits.includes(sp)) return false
      }
      if (columnFilters.persons.length > 0) {
        const hasMatchingPerson = columnFilters.persons.some((pName) => {
          if (pName === 'Meus Gastos (Todos)' || pName === 'Todos os Meus') {
            return isMyExpenseTx(tx, currentUid, effectiveTransactions)
          }
          if (pName === '100% Meu (Exclusivo)' || pName === '100% Meu') {
            return isPureMineTx(tx, currentUid, effectiveTransactions)
          }
          if (pName === 'Meus Rateados' || pName === 'Rateados (Minha Cota)') {
            return isSharedWithMeTx(tx, currentUid, effectiveTransactions)
          }
          const pList = getTxPersons(tx, currentUid, partner?.name, effectiveTransactions)
          return pList.some((p) => p.name === pName)
        })
        if (!hasMatchingPerson) return false
      }

      // 7. Excel Column Filter: Amount Type & Range
      const eff = effectiveAmount(tx)
      const myImpact = impactFor(tx, currentUid, effectiveTransactions)
      const isRef = tx.category === 'Estorno' || eff < 0 || tx.totalAmount < 0

      if (columnFilters.amountType === 'income' && tx.type !== 'income') return false
      if (columnFilters.amountType === 'expense' && (tx.type === 'income' || isRef)) return false
      if (columnFilters.amountType === 'refund' && !isRef) return false

      if (columnFilters.amountMin) {
        const minVal = parseFloat(columnFilters.amountMin.replace(',', '.'))
        if (!isNaN(minVal) && Math.abs(myImpact) < minVal) return false
      }
      if (columnFilters.amountMax) {
        const maxVal = parseFloat(columnFilters.amountMax.replace(',', '.'))
        if (!isNaN(maxVal) && Math.abs(myImpact) > maxVal) return false
      }

      return true
    })

    // Sort order
    return [...list].sort((a, b) => {
      // Custom Excel sort by amount if selected
      if (columnFilters.sortBy === 'amount_asc') {
        const impA = impactFor(a, currentUid, effectiveTransactions)
        const impB = impactFor(b, currentUid, effectiveTransactions)
        return impA - impB
      }
      if (columnFilters.sortBy === 'amount_desc') {
        const impA = impactFor(a, currentUid, effectiveTransactions)
        const impB = impactFor(b, currentUid, effectiveTransactions)
        return impB - impA
      }

      // Chronological date sort
      const dateA = a.dueDate || a.competenceMonth || ''
      const dateB = b.dueDate || b.competenceMonth || ''
      const cmp = dateA.localeCompare(dateB)
      const effectiveSort = columnFilters.sortBy === 'date_desc' ? 'desc' : columnFilters.sortBy === 'date_asc' ? 'asc' : sortOrder

      if (cmp !== 0) {
        return effectiveSort === 'asc' ? cmp : -cmp
      }
      if (a.type !== b.type) {
        return a.type === 'income' ? -1 : 1
      }
      return (b.createdAt || 0) - (a.createdAt || 0)
    })
  }, [effectiveTransactions, activeTab, search, cards, sortOrder, columnFilters, currentUid, partner?.name])

  // Real-time Analytical Summary of filtered items
  const filteredSummary = useMemo(() => {
    let income = 0
    let expense = 0

    filteredTxs.forEach((tx) => {
      const myImpact = impactFor(tx, currentUid, effectiveTransactions)
      const eff = effectiveAmount(tx)
      const isRef = tx.category === 'Estorno' || eff < 0 || tx.totalAmount < 0

      if (tx.type === 'income') {
        income += myImpact
      } else if (isRef) {
        income += Math.abs(myImpact)
      } else {
        expense += myImpact
      }
    })

    return {
      income,
      expense,
      net: income - expense,
    }
  }, [filteredTxs, currentUid, effectiveTransactions])

  // Total dinâmico exato pertencente à(s) pessoa(s) ou terceiro(s) filtrado(s)
  const filteredPersonTotal = useMemo(() => {
    if (columnFilters.persons.length === 0) return 0
    let total = 0
    filteredTxs.forEach((tx) => {
      const myImp = Math.abs(impactFor(tx, currentUid, effectiveTransactions))
      if (columnFilters.persons.includes('Meus Gastos (Todos)') || columnFilters.persons.includes('Todos os Meus')) {
        if (isMyExpenseTx(tx, currentUid, effectiveTransactions)) {
          total += myImp
          return
        }
      }
      if (columnFilters.persons.includes('100% Meu (Exclusivo)') || columnFilters.persons.includes('100% Meu')) {
        if (isPureMineTx(tx, currentUid, effectiveTransactions)) {
          total += myImp
          return
        }
      }
      if (columnFilters.persons.includes('Meus Rateados') || columnFilters.persons.includes('Rateados (Minha Cota)')) {
        if (isSharedWithMeTx(tx, currentUid, effectiveTransactions)) {
          total += myImp
          return
        }
      }
      const pList = getTxPersons(tx, currentUid, partner?.name, effectiveTransactions)
      pList.forEach((p) => {
        if (columnFilters.persons.includes(p.name)) {
          total += p.amount
        }
      })
    })
    return round2(total)
  }, [filteredTxs, columnFilters.persons, currentUid, partner?.name, effectiveTransactions])

  // Count if any column filter is active
  const isAnyFilterActive = useMemo(() => {
    return (
      columnFilters.status.length > 0 ||
      columnFilters.categories.length > 0 ||
      columnFilters.subcategories.length > 0 ||
      columnFilters.splits.length > 0 ||
      columnFilters.persons.length > 0 ||
      Boolean(columnFilters.dateStart || columnFilters.dateEnd || columnFilters.specificDays.length > 0) ||
      Boolean(columnFilters.amountMin || columnFilters.amountMax || columnFilters.amountType !== 'all' || columnFilters.sortBy)
    )
  }, [columnFilters])

  const mobileGroupedTxs = useMemo(() => {
    const map = new Map<string, Transaction[]>()
    filteredTxs.forEach((tx) => {
      const key = tx.dueDate || tx.competenceMonth || 'Sem data'
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(tx)
    })
    return Array.from(map.entries()).map(([dateKey, items]) => {
      let dayExpenses = 0
      let dayIncomes = 0
      items.forEach((tx) => {
        const imp = Math.abs(impactFor(tx, currentUid, monthTransactions))
        if (tx.type === 'income' || tx.category === 'Estorno') {
          dayIncomes += imp
        } else {
          dayExpenses += imp
        }
      })
      const isNetIncome = dayIncomes > dayExpenses
      const netAmount = isNetIncome ? dayIncomes - dayExpenses : dayExpenses - dayIncomes
      const totalAmount = dayExpenses === 0 && dayIncomes > 0 ? dayIncomes : dayIncomes === 0 ? dayExpenses : netAmount
      return { dateKey, items, totalAmount, isNetIncome }
    })
  }, [filteredTxs, currentUid, monthTransactions])

  // Handlers for toggling specific filter items
  const toggleStatusFilter = (st: 'paid' | 'pending' | 'estimated') => {
    setColumnFilters((prev) => ({
      ...prev,
      status: prev.status.includes(st) ? prev.status.filter((s) => s !== st) : [...prev.status, st],
    }))
  }

  const toggleCategoryFilter = (cat: string) => {
    setColumnFilters((prev) => ({
      ...prev,
      categories: prev.categories.includes(cat) ? prev.categories.filter((c) => c !== cat) : [...prev.categories, cat],
    }))
  }

  const toggleSplitFilter = (sp: string) => {
    setColumnFilters((prev) => ({
      ...prev,
      splits: prev.splits.includes(sp) ? prev.splits.filter((s) => s !== sp) : [...prev.splits, sp],
    }))
  }

  const togglePersonFilter = (pName: string) => {
    setColumnFilters((prev) => ({
      ...prev,
      persons: prev.persons.includes(pName)
        ? prev.persons.filter((p) => p !== pName)
        : [...prev.persons, pName],
    }))
  }

  const toggleDayFilter = (day: string) => {
    setColumnFilters((prev) => ({
      ...prev,
      specificDays: prev.specificDays.includes(day)
        ? prev.specificDays.filter((d) => d !== day)
        : [...prev.specificDays, day],
    }))
  }

  const clearAllFilters = () => {
    setColumnFilters(INITIAL_COLUMN_FILTERS)
    setCategorySearchQuery('')
    setPersonSearchQuery('')
    setSearch('')
  }



  // Count badges
  const counts = useMemo(() => {
    const txs = Array.isArray(effectiveTransactions) ? effectiveTransactions : []
    const cardsList = Array.isArray(cards) ? cards : []
    return {
      all: txs.length,
      income: txs.filter((t: Transaction) => t && t.type === 'income').length,
      expense: txs.filter((t: Transaction) => t && t.type !== 'income').length,
      myExpenses: txs.filter((t: Transaction) => t && isMyExpenseTx(t, currentUid, txs)).length,
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
  }, [effectiveTransactions, cards, currentUid])

  const handlePayConsolidatedInvoice = async (invoiceTx: ConsolidatedTransaction) => {
    if (!invoiceTx.invoiceMeta) return
    const { invoice, card } = invoiceTx.invoiceMeta
    if (card.ownerUid !== currentUid) return
    const unpaidItems = invoice.items.filter((t) => t.type === 'expense' && t.status !== 'paid')
    if (unpaidItems.length === 0) return
    if (confirm(`Confirmar o pagamento da fatura ${card.cardName} no valor de ${formatBRL(invoice.openAmount)}? Todos os ${unpaidItems.length} lançamentos vinculados serão marcados como pagos.`)) {
      await Promise.all(unpaidItems.map((item) => setStatus(item, 'paid')))
    }
  }

  const startEdit = (tx: Transaction) => {
    if (!canUserEditTransaction(tx, currentUid)) return
    setEditingId(tx.id)
    setEditDesc(tx.description)
    setEditDate(tx.dueDate)
    setEditAmount(String(effectiveAmount(tx)))
  }

  const saveInlineEdit = async (tx: Transaction) => {
    if (!canUserEditTransaction(tx, currentUid)) {
      setEditingId(null)
      return
    }
    const num = parseFloat(editAmount.replace(',', '.'))
    if (!editDesc.trim() || isNaN(num) || num <= 0) {
      setEditingId(null)
      return
    }

    const hasFriend = Boolean(tx.assignedFriendUid)
    const patch: Partial<Transaction> = {
      description: editDesc.trim(),
      dueDate: editDate,
    }
    if (hasFriend) {
      patch.acceptanceStatus = 'pending_approval'
      patch.respondedAt = null
    }

    await updateTransaction(tx.id, patch)

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

            {/* Toggle de Faturas Consolidadas no Extrato */}
            <button
              type="button"
              onClick={() => setConsolidateInvoices((prev) => !prev)}
              className={`h-8 px-2.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all border cursor-pointer shrink-0 ${
                consolidateInvoices
                  ? 'bg-sky-500/20 text-sky-300 border-sky-500/40 shadow-xs hover:bg-sky-500/30'
                  : 'bg-slate-950/70 text-slate-400 border-slate-800 hover:text-slate-200'
              }`}
              title={
                consolidateInvoices
                  ? 'Faturas consolidadas ativadas: Exibe 1 único lançamento consolidado por cartão no vencimento. Clique para ver compras individuais.'
                  : 'Exibindo compras individuais no extrato. Clique para consolidar em 1 lançamento por fatura.'
              }
            >
              <CreditCard className="size-3.5" />
              <span className="hidden sm:inline">Faturas Consolidadas</span>
              <span className="sm:hidden">Faturas</span>
            </button>

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

          <button
            type="button"
            onClick={() => setActiveTab('my_expenses')}
            className={`px-3 py-1 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 ${
              activeTab === 'my_expenses'
                ? 'bg-emerald-500/25 text-emerald-200 border border-emerald-500/40 font-semibold shadow-xs'
                : 'text-slate-400 hover:text-emerald-300 hover:bg-slate-800/50'
            }`}
            title="Todos os meus gastos (100% pessoais e rateados com minha participação)"
          >
            <span>👤 Meus Gastos ({counts.myExpenses})</span>
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

      {/* 2. Barra de Filtros Ativos (quando houver filtros aplicados) */}
      {isAnyFilterActive && (
        <div className="px-4 py-2 bg-emerald-950/20 border-b border-emerald-500/20 flex flex-wrap items-center gap-1.5 text-xs animate-in fade-in">
          <div className="flex items-center gap-1.5 text-emerald-400 font-semibold text-[11px] shrink-0 mr-1">
            <Filter className="size-3.5 fill-emerald-400/20" />
            <span>Filtros ativos:</span>
          </div>

          {/* Status Pills */}
          {columnFilters.status.length > 0 && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-900/90 border border-slate-700/80 text-[11px] text-slate-200">
              <span className="text-slate-400">Status:</span>
              <span className="text-emerald-300 font-medium">
                {columnFilters.status
                  .map((s) => (s === 'paid' ? 'Pago' : s === 'pending' ? 'Pendente' : 'Estimado'))
                  .join(', ')}
              </span>
              <button
                type="button"
                onClick={() => setColumnFilters((prev) => ({ ...prev, status: [] }))}
                className="hover:text-rose-400 cursor-pointer ml-0.5"
                title="Remover filtro de status"
              >
                <X className="size-3" />
              </button>
            </span>
          )}

          {/* Date Pills */}
          {(columnFilters.dateStart || columnFilters.dateEnd || columnFilters.specificDays.length > 0) && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-900/90 border border-slate-700/80 text-[11px] text-slate-200">
              <span className="text-slate-400">Data:</span>
              <span className="text-emerald-300 font-medium">
                {columnFilters.dateStart && columnFilters.dateEnd
                  ? `${formatShortDate(columnFilters.dateStart)} a ${formatShortDate(columnFilters.dateEnd)}`
                  : columnFilters.dateStart
                    ? `A partir de ${formatShortDate(columnFilters.dateStart)}`
                    : columnFilters.dateEnd
                      ? `Até ${formatShortDate(columnFilters.dateEnd)}`
                      : `Dias: ${columnFilters.specificDays.join(', ')}`}
              </span>
              <button
                type="button"
                onClick={() => setColumnFilters((prev) => ({ ...prev, dateStart: '', dateEnd: '', specificDays: [] }))}
                className="hover:text-rose-400 cursor-pointer ml-0.5"
                title="Remover filtro de data"
              >
                <X className="size-3" />
              </button>
            </span>
          )}

          {/* Category Pills */}
          {columnFilters.categories.length > 0 && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-900/90 border border-slate-700/80 text-[11px] text-slate-200">
              <span className="text-slate-400">Cat:</span>
              <span className="text-emerald-300 font-medium">{columnFilters.categories.join(', ')}</span>
              <button
                type="button"
                onClick={() => setColumnFilters((prev) => ({ ...prev, categories: [] }))}
                className="hover:text-rose-400 cursor-pointer ml-0.5"
                title="Remover filtro de categoria"
              >
                <X className="size-3" />
              </button>
            </span>
          )}

          {/* Subcategory Pills */}
          {columnFilters.subcategories.length > 0 && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-900/90 border border-slate-700/80 text-[11px] text-slate-200">
              <span className="text-slate-400">Sub:</span>
              <span className="text-emerald-300 font-medium">{columnFilters.subcategories.join(', ')}</span>
              <button
                type="button"
                onClick={() => setColumnFilters((prev) => ({ ...prev, subcategories: [] }))}
                className="hover:text-rose-400 cursor-pointer ml-0.5"
                title="Remover filtro de subcategoria"
              >
                <X className="size-3" />
              </button>
            </span>
          )}

          {/* Division Pills */}
          {columnFilters.splits.length > 0 && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-900/90 border border-slate-700/80 text-[11px] text-slate-200">
              <span className="text-slate-400">Divisão:</span>
              <span className="text-emerald-300 font-medium">
                {columnFilters.splits
                  .map((sp) =>
                    sp === 'mine'
                      ? '100% Meu'
                      : sp === 'split_50'
                        ? '50/50'
                        : sp === 'partner'
                          ? partner?.name || 'Parceiro'
                          : sp === 'external'
                            ? 'Terceiro'
                            : sp === 'friend'
                              ? 'Amigo'
                              : 'Personalizado'
                  )
                  .join(', ')}
              </span>
              <button
                type="button"
                onClick={() => setColumnFilters((prev) => ({ ...prev, splits: [] }))}
                className="hover:text-rose-400 cursor-pointer ml-0.5"
                title="Remover filtro de divisão"
              >
                <X className="size-3" />
              </button>
            </span>
          )}

          {/* Person / Responsável Pills */}
          {columnFilters.persons.length > 0 && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-amber-500/15 border border-amber-500/40 text-[11px] text-amber-200">
              <span className="text-amber-400/80">Pessoa:</span>
              <span className="text-white font-semibold">
                {columnFilters.persons.join(', ')}
              </span>
              <button
                type="button"
                onClick={() => setColumnFilters((prev) => ({ ...prev, persons: [] }))}
                className="hover:text-rose-400 cursor-pointer ml-0.5"
                title="Remover filtro de pessoa"
              >
                <X className="size-3" />
              </button>
            </span>
          )}

          {/* Amount / Type Pills */}
          {(columnFilters.amountMin || columnFilters.amountMax || columnFilters.amountType !== 'all') && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-900/90 border border-slate-700/80 text-[11px] text-slate-200">
              <span className="text-slate-400">Valor:</span>
              <span className="text-emerald-300 font-medium">
                {columnFilters.amountType === 'income'
                  ? 'Entradas '
                  : columnFilters.amountType === 'expense'
                    ? 'Despesas '
                    : columnFilters.amountType === 'refund'
                      ? 'Estornos '
                      : ''}
                {columnFilters.amountMin ? `≥ R$ ${columnFilters.amountMin} ` : ''}
                {columnFilters.amountMax ? `≤ R$ ${columnFilters.amountMax}` : ''}
              </span>
              <button
                type="button"
                onClick={() =>
                  setColumnFilters((prev) => ({
                    ...prev,
                    amountMin: '',
                    amountMax: '',
                    amountType: 'all',
                  }))
                }
                className="hover:text-rose-400 cursor-pointer ml-0.5"
                title="Remover filtro de valor"
              >
                <X className="size-3" />
              </button>
            </span>
          )}

          {/* Sort Pill */}
          {columnFilters.sortBy && (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-900/90 border border-slate-700/80 text-[11px] text-slate-200">
              <span className="text-slate-400">Ordem:</span>
              <span className="text-emerald-300 font-medium">
                {columnFilters.sortBy === 'amount_desc'
                  ? 'Maior Valor'
                  : columnFilters.sortBy === 'amount_asc'
                    ? 'Menor Valor'
                    : columnFilters.sortBy === 'date_desc'
                      ? 'Mais Recentes'
                      : 'Mais Antigos'}
              </span>
              <button
                type="button"
                onClick={() => setColumnFilters((prev) => ({ ...prev, sortBy: null }))}
                className="hover:text-rose-400 cursor-pointer ml-0.5"
                title="Restaurar ordenação padrão"
              >
                <X className="size-3" />
              </button>
            </span>
          )}

          <button
            type="button"
            onClick={clearAllFilters}
            className="text-[11px] text-emerald-400 hover:text-emerald-300 hover:underline cursor-pointer ml-auto font-semibold shrink-0"
          >
            Limpar todos os filtros
          </button>
        </div>
      )}

      {/* Mobile Transaction Cards Feed (Visible on screens < md) */}
      <div className="md:hidden flex-1 overflow-y-auto p-3 space-y-2.5">
        {Boolean(summary?.carryoverAmount || summary?.previousMonthDeficit) && (
          <div className={`p-3 rounded-xl border flex items-center justify-between ${
            summary?.previousMonthDeficit 
              ? 'bg-rose-950/20 border-rose-500/30' 
              : 'bg-emerald-950/20 border-emerald-500/30'
          }`}>
            <div className="flex items-center gap-2.5">
              <div className={`p-2 rounded-lg ${summary?.previousMonthDeficit ? 'bg-rose-500/20 text-rose-300' : 'bg-emerald-500/20 text-emerald-300'}`}>
                <Landmark className="size-4" />
              </div>
              <div>
                <p className="text-xs font-bold text-slate-200">
                  {summary?.previousMonthDeficit ? 'Déficit Anterior Acumulado' : 'Saldo Inicial do Mês'}
                </p>
                <p className="text-[10px] text-slate-400">
                  {summary?.previousMonthDeficit ? 'Débito herdado do período anterior' : 'Sobra transferida do mês anterior'}
                </p>
              </div>
            </div>
            <span className={`text-sm font-mono font-bold ${summary?.previousMonthDeficit ? 'text-rose-400' : 'text-emerald-400'}`}>
              {summary?.previousMonthDeficit ? `-${formatBRL(summary.previousMonthDeficit)}` : `+${formatBRL(summary?.carryoverAmount || 0)}`}
            </span>
          </div>
        )}
        {filteredTxs.length === 0 ? (
          <div className="text-center py-12 text-slate-400">
            <Filter className="size-8 mx-auto mb-2 opacity-30" />
            <p className="font-medium text-slate-300">Nenhuma transação encontrada</p>
            <p className="text-[11px] text-slate-500 mt-1">Tente ajustar os filtros ou adicione uma nova transação.</p>
          </div>
        ) : (
          mobileGroupedTxs.map(({ dateKey, items, totalAmount, isNetIncome }) => {
            const hdr = formatNubankDateHeader(dateKey)
            return (
              <div key={dateKey} className="space-y-1.5">
                <div className="flex items-center justify-between px-2 pt-2 text-[11px]">
                  <div className="flex items-center gap-1.5 font-bold uppercase tracking-wider text-slate-300">
                    <span className={hdr.isToday ? 'text-cyan-400' : hdr.isYesterday ? 'text-amber-400' : 'text-slate-200'}>
                      {hdr.title}
                    </span>
                    {hdr.subtitle && <span className="font-normal text-slate-500">· {hdr.subtitle}</span>}
                  </div>
                  <span className={`font-semibold tabular-nums text-[11px] ${isNetIncome ? 'text-emerald-400' : 'text-slate-400'}`}>
                    {isNetIncome ? `+ ${formatBRL(totalAmount)}` : formatBRL(totalAmount)}
                  </span>
                </div>
                <div className="space-y-1.5">
                  {items.map((tx: Transaction) => (
                    <MobileTransactionCard
                      key={tx.id}
                      tx={tx}
                      currentUid={currentUid}
                      allTransactions={effectiveTransactions}
                      cards={cards}
                      onEdit={(targetTx) => {
                        if (targetTx.id.startsWith('invoice_') && targetTx.cardId) {
                          onOpenInvoices?.(targetTx.cardId)
                        } else {
                          onEditTx(targetTx)
                        }
                      }}
                      onRefund={onRefundTx}
                      onDelete={(targetTx) => {
                        if (targetTx.installmentGroupId) {
                          setDeleteGroupTx(targetTx)
                        } else {
                          deleteTransaction(targetTx.id)
                        }
                      }}
                      onCycleStatus={cycleStatus}
                    />
                  ))}
                </div>
              </div>
            )
          })
        )}
      </div>

      {/* Main Table (Visible on md and above) */}
      <div className="hidden md:block flex-1 overflow-x-auto overflow-y-auto">
        <table className="w-full text-left text-xs border-collapse">
          <thead className="sticky top-0 bg-[#0f172a] text-slate-400 font-semibold uppercase text-[10px] tracking-wider border-b border-white/[0.06] z-10 select-none">
            <tr>
              {/* 1. Status Column */}
              <th className="py-2 px-2.5 w-28">
                <div className="flex items-center justify-between gap-1">
                  <span>Status</span>
                  <Popover>
                    <PopoverTrigger asChild>
                      <button
                        type="button"
                        className={`p-1 rounded cursor-pointer transition-colors ${
                          columnFilters.status.length > 0
                            ? 'text-emerald-400 bg-emerald-500/20 border border-emerald-500/40'
                            : 'text-slate-400 hover:text-white hover:bg-slate-800'
                        }`}
                        title="Filtrar por Status (estilo Excel)"
                      >
                        <Filter className={`size-3 ${columnFilters.status.length > 0 ? 'fill-emerald-400' : ''}`} />
                      </button>
                    </PopoverTrigger>
                    <PopoverContent align="start" className="w-56 bg-slate-950 border border-slate-800 shadow-2xl p-2.5 text-xs text-slate-200">
                      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] mb-2 font-sans">
                        <span className="font-bold text-[11px] text-white">Filtrar Status</span>
                        <div className="flex items-center gap-1.5 text-[10px]">
                          <button
                            type="button"
                            onClick={() => setColumnFilters((p) => ({ ...p, status: ['paid', 'pending', 'estimated'] }))}
                            className="text-emerald-400 hover:underline cursor-pointer"
                          >
                            Todos
                          </button>
                          <span>·</span>
                          <button
                            type="button"
                            onClick={() => setColumnFilters((p) => ({ ...p, status: [] }))}
                            className="text-slate-400 hover:text-white cursor-pointer"
                          >
                            Limpar
                          </button>
                        </div>
                      </div>
                      <div className="space-y-1">
                        {(['paid', 'pending', 'estimated'] as const).map((st) => {
                          const isChecked = columnFilters.status.includes(st)
                          const label = st === 'paid' ? 'Pago' : st === 'pending' ? 'Pendente' : 'Estimado'
                          const count = statusCounts[st]
                          const dotColor = st === 'paid' ? 'bg-emerald-400' : st === 'pending' ? 'bg-amber-400' : 'bg-cyan-400'
                          return (
                            <label
                              key={st}
                              onClick={() => toggleStatusFilter(st)}
                              className="flex items-center justify-between p-1.5 rounded-lg hover:bg-slate-900 cursor-pointer"
                            >
                              <div className="flex items-center gap-2">
                                <input
                                  type="checkbox"
                                  checked={isChecked}
                                  onChange={() => {}}
                                  className="rounded border-slate-700 bg-slate-900 text-emerald-500 focus:ring-emerald-500/30 size-3.5 cursor-pointer"
                                />
                                <span className={`size-2 rounded-full ${dotColor}`} />
                                <span>{label}</span>
                              </div>
                              <Badge variant="outline" className="text-[9px] px-1 py-0 font-mono text-slate-400">
                                {count}
                              </Badge>
                            </label>
                          )
                        })}
                      </div>
                    </PopoverContent>
                  </Popover>
                </div>
              </th>

              {/* 2. Date Column */}
              <th className="py-2 px-2.5 w-28">
                <div className="flex items-center justify-between gap-1">
                  <div
                    onClick={() => setSortOrder((prev) => (prev === 'asc' ? 'desc' : 'asc'))}
                    className="flex items-center gap-1 cursor-pointer hover:text-white transition-colors"
                    title="Alternar ordem cronológica"
                  >
                    <span>Data</span>
                    {sortOrder === 'asc' ? (
                      <ArrowUp className="size-3 text-emerald-400" />
                    ) : (
                      <ArrowDown className="size-3 text-emerald-400" />
                    )}
                  </div>
                  <Popover>
                    <PopoverTrigger asChild>
                      <button
                        type="button"
                        className={`p-1 rounded cursor-pointer transition-colors ${
                          columnFilters.dateStart || columnFilters.dateEnd || columnFilters.specificDays.length > 0
                            ? 'text-emerald-400 bg-emerald-500/20 border border-emerald-500/40'
                            : 'text-slate-400 hover:text-white hover:bg-slate-800'
                        }`}
                        title="Filtrar por Data ou Intervalo"
                      >
                        <Filter className={`size-3 ${columnFilters.dateStart || columnFilters.dateEnd || columnFilters.specificDays.length > 0 ? 'fill-emerald-400' : ''}`} />
                      </button>
                    </PopoverTrigger>
                    <PopoverContent align="start" className="w-64 bg-slate-950 border border-slate-800 shadow-2xl p-2.5 text-xs text-slate-200">
                      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] mb-2 font-sans">
                        <span className="font-bold text-[11px] text-white">Filtrar por Data</span>
                        <button
                          type="button"
                          onClick={() => setColumnFilters((p) => ({ ...p, dateStart: '', dateEnd: '', specificDays: [] }))}
                          className="text-[10px] text-slate-400 hover:text-white cursor-pointer"
                        >
                          Limpar
                        </button>
                      </div>

                      {/* Ordenação rápida */}
                      <div className="grid grid-cols-2 gap-1 mb-2.5 font-sans">
                        <button
                          type="button"
                          onClick={() => {
                            setSortOrder('asc')
                            setColumnFilters((p) => ({ ...p, sortBy: 'date_asc' }))
                          }}
                          className={`p-1.5 rounded-lg border text-[10px] font-medium text-center cursor-pointer ${
                            sortOrder === 'asc' && columnFilters.sortBy !== 'amount_desc' && columnFilters.sortBy !== 'amount_asc'
                              ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                              : 'bg-slate-900 border-slate-800 text-slate-300 hover:bg-slate-850'
                          }`}
                        >
                          1 → 31 (Antigos)
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setSortOrder('desc')
                            setColumnFilters((p) => ({ ...p, sortBy: 'date_desc' }))
                          }}
                          className={`p-1.5 rounded-lg border text-[10px] font-medium text-center cursor-pointer ${
                            sortOrder === 'desc' && columnFilters.sortBy !== 'amount_desc' && columnFilters.sortBy !== 'amount_asc'
                              ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                              : 'bg-slate-900 border-slate-800 text-slate-300 hover:bg-slate-850'
                          }`}
                        >
                          31 → 1 (Recentes)
                        </button>
                      </div>

                      {/* Intervalo de datas */}
                      <div className="space-y-1.5 mb-2.5 font-sans">
                        <span className="text-[10px] font-semibold text-slate-400">Intervalo de Vencimento:</span>
                        <div className="grid grid-cols-2 gap-1">
                          <div>
                            <span className="text-[9px] text-slate-500 block">De:</span>
                            <Input
                              type="date"
                              value={columnFilters.dateStart}
                              onChange={(e) => setColumnFilters((p) => ({ ...p, dateStart: e.target.value }))}
                              className="h-7 text-[10px] bg-slate-900 border-slate-700 p-1"
                            />
                          </div>
                          <div>
                            <span className="text-[9px] text-slate-500 block">Até:</span>
                            <Input
                              type="date"
                              value={columnFilters.dateEnd}
                              onChange={(e) => setColumnFilters((p) => ({ ...p, dateEnd: e.target.value }))}
                              className="h-7 text-[10px] bg-slate-900 border-slate-700 p-1"
                            />
                          </div>
                        </div>
                      </div>

                      {/* Dias específicos presentes no mês */}
                      {availableDays.length > 0 && (
                        <div>
                          <span className="text-[10px] font-semibold text-slate-400 block mb-1">Dias com Lançamentos:</span>
                          <div className="max-h-28 overflow-y-auto grid grid-cols-3 gap-1 p-1 bg-slate-900/60 rounded-lg border border-slate-800">
                            {availableDays.map(([day, count]) => {
                              const isChecked = columnFilters.specificDays.includes(day)
                              return (
                                <button
                                  key={day}
                                  type="button"
                                  onClick={() => toggleDayFilter(day)}
                                  className={`px-1.5 py-0.5 rounded text-[10px] font-mono flex items-center justify-between cursor-pointer border ${
                                    isChecked
                                      ? 'bg-emerald-500/25 border-emerald-500/40 text-emerald-300'
                                      : 'border-transparent text-slate-400 hover:bg-slate-800 hover:text-white'
                                  }`}
                                >
                                  <span>Dia {day}</span>
                                  <span className="text-[9px] opacity-70">({count})</span>
                                </button>
                              )
                            })}
                          </div>
                        </div>
                      )}
                    </PopoverContent>
                  </Popover>
                </div>
              </th>

              {/* 3. Description Column */}
              <th className="py-2 px-2.5 min-w-[150px]">
                <div className="flex items-center justify-between gap-1">
                  <span>Descrição</span>
                  <Popover>
                    <PopoverTrigger asChild>
                      <button
                        type="button"
                        className={`p-1 rounded cursor-pointer transition-colors ${
                          search.trim()
                            ? 'text-emerald-400 bg-emerald-500/20 border border-emerald-500/40'
                            : 'text-slate-400 hover:text-white hover:bg-slate-800'
                        }`}
                        title="Busca rápida por descrição"
                      >
                        <Search className="size-3" />
                      </button>
                    </PopoverTrigger>
                    <PopoverContent align="start" className="w-60 bg-slate-950 border border-slate-800 shadow-2xl p-2.5 text-xs text-slate-200">
                      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] mb-2 font-sans">
                        <span className="font-bold text-[11px] text-white">Buscar na Coluna</span>
                        {search && (
                          <button
                            type="button"
                            onClick={() => setSearch('')}
                            className="text-[10px] text-slate-400 hover:text-white cursor-pointer"
                          >
                            Limpar
                          </button>
                        )}
                      </div>
                      <Input
                        autoFocus
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="Digite o texto do lançamento..."
                        className="h-8 text-xs bg-slate-900 border-slate-700"
                      />
                    </PopoverContent>
                  </Popover>
                </div>
              </th>

              {/* 4. Category Column */}
              <th className="py-2 px-2.5 w-32">
                <div className="flex items-center justify-between gap-1">
                  <span>Categoria</span>
                  <Popover>
                    <PopoverTrigger asChild>
                      <button
                        type="button"
                        className={`p-1 rounded cursor-pointer transition-colors ${
                          columnFilters.categories.length > 0 || columnFilters.subcategories.length > 0
                            ? 'text-emerald-400 bg-emerald-500/20 border border-emerald-500/40'
                            : 'text-slate-400 hover:text-white hover:bg-slate-800'
                        }`}
                        title="Filtrar por Categoria (estilo Excel)"
                      >
                        <Filter className={`size-3 ${columnFilters.categories.length > 0 || columnFilters.subcategories.length > 0 ? 'fill-emerald-400' : ''}`} />
                      </button>
                    </PopoverTrigger>
                    <PopoverContent align="start" className="w-64 bg-slate-950 border border-slate-800 shadow-2xl p-2.5 text-xs text-slate-200">
                      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] mb-2 font-sans">
                        <span className="font-bold text-[11px] text-white">Filtrar Categorias</span>
                        <div className="flex items-center gap-1.5 text-[10px]">
                          <button
                            type="button"
                            onClick={() => setColumnFilters((p) => ({ ...p, categories: availableCategories.map(([c]) => c), subcategories: [] }))}
                            className="text-emerald-400 hover:underline cursor-pointer"
                          >
                            Todas
                          </button>
                          <span>·</span>
                          <button
                            type="button"
                            onClick={() => setColumnFilters((p) => ({ ...p, categories: [], subcategories: [] }))}
                            className="text-slate-400 hover:text-white cursor-pointer"
                          >
                            Limpar
                          </button>
                        </div>
                      </div>

                      {/* Campo de busca interna para filtrar opções de categoria */}
                      <div className="mb-2">
                        <Input
                          value={categorySearchQuery}
                          onChange={(e) => setCategorySearchQuery(e.target.value)}
                          placeholder="Pesquisar categoria..."
                          className="h-7 text-[11px] bg-slate-900 border-slate-700"
                        />
                      </div>

                      <div className="max-h-56 overflow-y-auto space-y-0.5 pr-0.5">
                        {availableCategories
                          .filter(([cat]) => cat.toLowerCase().includes(categorySearchQuery.toLowerCase()))
                          .map(([cat, count]) => {
                            const isChecked = columnFilters.categories.includes(cat)
                            const detail = getCategoryDetail(cat)
                            return (
                              <label
                                key={cat}
                                onClick={() => toggleCategoryFilter(cat)}
                                className="flex items-center justify-between p-1.5 rounded-lg hover:bg-slate-900 cursor-pointer"
                              >
                                <div className="flex items-center gap-2 truncate min-w-0">
                                  <input
                                    type="checkbox"
                                    checked={isChecked}
                                    onChange={() => {}}
                                    className="rounded border-slate-700 bg-slate-900 text-emerald-500 focus:ring-emerald-500/30 size-3.5 cursor-pointer shrink-0"
                                  />
                                  <span
                                    className="size-2 rounded-full shrink-0"
                                    style={{ backgroundColor: detail?.color || '#94a3b8' }}
                                  />
                                  <span className="truncate">{cat}</span>
                                </div>
                                <Badge variant="outline" className="text-[9px] px-1 py-0 font-mono text-slate-400 shrink-0">
                                  {count}
                                </Badge>
                              </label>
                            )
                          })}
                      </div>
                    </PopoverContent>
                  </Popover>
                </div>
              </th>

              {/* 5. Division / Responsible Column */}
              <th className="py-2 px-2.5 w-44">
                <div className="flex items-center justify-between gap-1">
                  <span>Divisão / Responsável</span>
                  <Popover>
                    <PopoverTrigger asChild>
                      <button
                        type="button"
                        className={`p-1 rounded cursor-pointer transition-colors ${
                          columnFilters.splits.length > 0 || columnFilters.persons.length > 0
                            ? 'text-emerald-400 bg-emerald-500/20 border border-emerald-500/40'
                            : 'text-slate-400 hover:text-white hover:bg-slate-800'
                        }`}
                        title="Filtrar por Responsável, Terceiros ou Divisão"
                      >
                        <Filter className={`size-3 ${columnFilters.splits.length > 0 || columnFilters.persons.length > 0 ? 'fill-emerald-400' : ''}`} />
                      </button>
                    </PopoverTrigger>
                    <PopoverContent align="start" className="w-72 bg-slate-950 border border-slate-800 shadow-2xl p-2.5 text-xs text-slate-200">
                      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] mb-2 font-sans">
                        <span className="font-bold text-[11px] text-white">Filtrar Responsável & Terceiros</span>
                        <div className="flex items-center gap-1.5 text-[10px]">
                          <button
                            type="button"
                            onClick={() => setColumnFilters((p) => ({ ...p, persons: availablePersons.map((ap) => ap.name), splits: [] }))}
                            className="text-emerald-400 hover:underline cursor-pointer"
                          >
                            Todos
                          </button>
                          <span>·</span>
                          <button
                            type="button"
                            onClick={() => setColumnFilters((p) => ({ ...p, persons: [], splits: [] }))}
                            className="text-slate-400 hover:text-white cursor-pointer"
                          >
                            Limpar
                          </button>
                        </div>
                      </div>

                      {/* Busca rápida de pessoas/terceiros */}
                      {availablePersons.length > 4 && (
                        <div className="relative mb-2">
                          <Search className="size-3 absolute left-2 top-1/2 -translate-y-1/2 text-slate-400" />
                          <Input
                            value={personSearchQuery}
                            onChange={(e) => setPersonSearchQuery(e.target.value)}
                            placeholder="Buscar pessoa ou terceiro..."
                            className="h-7 text-[11px] pl-6 bg-slate-900 border-slate-700"
                          />
                        </div>
                      )}

                      {/* Lista de Pessoas e Terceiros Individuais */}
                      <div className="space-y-0.5 max-h-48 overflow-y-auto mb-2 pr-1">
                        <div className="text-[10px] uppercase font-bold text-slate-400 px-1 py-0.5 tracking-wider">
                          Pessoas & Terceiros
                        </div>
                        {availablePersons
                          .filter((p) => !personSearchQuery || p.name.toLowerCase().includes(personSearchQuery.toLowerCase()))
                          .map((p) => {
                            const isChecked = columnFilters.persons.includes(p.name)
                            const emoji =
                              p.role === 'me_all' || p.role === 'me_pure' || p.name === '100% Meu'
                                ? '👤'
                                : p.role === 'me_shared'
                                  ? '🤝'
                                  : p.role === 'partner'
                                    ? '🔄'
                                    : p.role === 'friend'
                                      ? '🧑‍🤝‍🧑'
                                      : '🏷️'
                            return (
                              <label
                                key={p.name}
                                onClick={() => togglePersonFilter(p.name)}
                                className="flex items-center justify-between p-1.5 rounded-lg hover:bg-slate-900 cursor-pointer transition-colors"
                              >
                                <div className="flex items-center gap-2 truncate min-w-0">
                                  <input
                                    type="checkbox"
                                    checked={isChecked}
                                    onChange={() => {}}
                                    className="rounded border-slate-700 bg-slate-900 text-emerald-500 focus:ring-emerald-500/30 size-3.5 cursor-pointer shrink-0"
                                  />
                                  <span className="truncate text-slate-200">
                                    {emoji} {p.name}
                                  </span>
                                </div>
                                <div className="flex items-center gap-1.5 shrink-0 ml-1">
                                  <span className="text-[10px] text-emerald-400 font-mono font-medium">
                                    {formatBRL(p.totalAmount)}
                                  </span>
                                  <Badge variant="outline" className="text-[9px] px-1 py-0 font-mono text-slate-400">
                                    {p.count}
                                  </Badge>
                                </div>
                              </label>
                            )
                          })}
                      </div>

                      {/* Tipos Gerais de Divisão */}
                      <div className="pt-1.5 border-t border-white/[0.08]">
                        <div className="text-[10px] uppercase font-bold text-slate-400 px-1 py-0.5 tracking-wider mb-1">
                          Modos de Rateio
                        </div>
                        <div className="space-y-0.5 max-h-32 overflow-y-auto pr-1">
                          {availableSplits.map(([sp, count]) => {
                            const isChecked = columnFilters.splits.includes(sp)
                            const label =
                              sp === 'mine'
                                ? '👤 100% Meu'
                                : sp === 'split_50'
                                  ? '🤝 50/50 Dividido'
                                  : sp === 'partner'
                                    ? `🔄 100% ${partner?.name || 'Parceiro'}`
                                    : sp === 'external'
                                      ? '🏷️ Terceiro Externo'
                                      : sp === 'friend'
                                        ? '🧑‍🤝‍🧑 Amigo Cobrado'
                                        : '⚖️ Personalizado'
                            return (
                              <label
                                key={sp}
                                onClick={() => toggleSplitFilter(sp)}
                                className="flex items-center justify-between p-1.5 rounded-lg hover:bg-slate-900 cursor-pointer"
                              >
                                <div className="flex items-center gap-2 truncate">
                                  <input
                                    type="checkbox"
                                    checked={isChecked}
                                    onChange={() => {}}
                                    className="rounded border-slate-700 bg-slate-900 text-emerald-500 focus:ring-emerald-500/30 size-3.5 cursor-pointer shrink-0"
                                  />
                                  <span className="truncate">{label}</span>
                                </div>
                                <Badge variant="outline" className="text-[9px] px-1 py-0 font-mono text-slate-400 shrink-0">
                                  {count}
                                </Badge>
                              </label>
                            )
                          })}
                        </div>
                      </div>
                    </PopoverContent>
                  </Popover>
                </div>
              </th>

              {/* 6. Amount & Impact Column */}
              <th className="py-2 px-2.5 w-40 text-right">
                <div className="flex items-center justify-end gap-1">
                  <span>Valor & Impacto</span>
                  <Popover>
                    <PopoverTrigger asChild>
                      <button
                        type="button"
                        className={`p-1 rounded cursor-pointer transition-colors ${
                          columnFilters.amountMin || columnFilters.amountMax || columnFilters.amountType !== 'all' || columnFilters.sortBy?.startsWith('amount')
                            ? 'text-emerald-400 bg-emerald-500/20 border border-emerald-500/40'
                            : 'text-slate-400 hover:text-white hover:bg-slate-800'
                        }`}
                        title="Filtrar por Faixa de Valor ou Ordenar por Impacto"
                      >
                        <Filter className={`size-3 ${columnFilters.amountMin || columnFilters.amountMax || columnFilters.amountType !== 'all' || columnFilters.sortBy?.startsWith('amount') ? 'fill-emerald-400' : ''}`} />
                      </button>
                    </PopoverTrigger>
                    <PopoverContent align="end" className="w-64 bg-slate-950 border border-slate-800 shadow-2xl p-2.5 text-xs text-slate-200">
                      <div className="flex items-center justify-between pb-2 border-b border-white/[0.08] mb-2 font-sans">
                        <span className="font-bold text-[11px] text-white">Filtrar por Valor</span>
                        <button
                          type="button"
                          onClick={() => setColumnFilters((p) => ({ ...p, amountMin: '', amountMax: '', amountType: 'all', sortBy: null }))}
                          className="text-[10px] text-slate-400 hover:text-white cursor-pointer"
                        >
                          Limpar
                        </button>
                      </div>

                      {/* Ordenação por valor */}
                      <div className="grid grid-cols-2 gap-1 mb-2.5 font-sans">
                        <button
                          type="button"
                          onClick={() => setColumnFilters((p) => ({ ...p, sortBy: p.sortBy === 'amount_desc' ? null : 'amount_desc' }))}
                          className={`p-1.5 rounded-lg border text-[10px] font-medium text-center cursor-pointer ${
                            columnFilters.sortBy === 'amount_desc'
                              ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                              : 'bg-slate-900 border-slate-800 text-slate-300 hover:bg-slate-850'
                          }`}
                        >
                          Maior → Menor
                        </button>
                        <button
                          type="button"
                          onClick={() => setColumnFilters((p) => ({ ...p, sortBy: p.sortBy === 'amount_asc' ? null : 'amount_asc' }))}
                          className={`p-1.5 rounded-lg border text-[10px] font-medium text-center cursor-pointer ${
                            columnFilters.sortBy === 'amount_asc'
                              ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                              : 'bg-slate-900 border-slate-800 text-slate-300 hover:bg-slate-850'
                          }`}
                        >
                          Menor → Maior
                        </button>
                      </div>

                      {/* Tipo de fluxo */}
                      <div className="space-y-1 mb-2.5 font-sans">
                        <span className="text-[10px] font-semibold text-slate-400">Tipo de Fluxo:</span>
                        <div className="grid grid-cols-2 gap-1">
                          {(['all', 'income', 'expense', 'refund'] as const).map((tp) => (
                            <button
                              key={tp}
                              type="button"
                              onClick={() => setColumnFilters((p) => ({ ...p, amountType: tp }))}
                              className={`px-1.5 py-1 rounded text-[10px] font-medium text-center cursor-pointer border ${
                                columnFilters.amountType === tp
                                  ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
                              }`}
                            >
                              {tp === 'all' ? 'Todos' : tp === 'income' ? 'Entradas (+)' : tp === 'expense' ? 'Despesas (−)' : 'Estornos'}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* Faixa Min / Max */}
                      <div className="space-y-1 font-sans">
                        <span className="text-[10px] font-semibold text-slate-400">Faixa de Valor (R$):</span>
                        <div className="grid grid-cols-2 gap-1.5">
                          <div>
                            <span className="text-[9px] text-slate-500 block">Mínimo:</span>
                            <Input
                              type="number"
                              step="0.01"
                              placeholder="0,00"
                              value={columnFilters.amountMin}
                              onChange={(e) => setColumnFilters((p) => ({ ...p, amountMin: e.target.value }))}
                              className="h-7 text-xs bg-slate-900 border-slate-700 p-1"
                            />
                          </div>
                          <div>
                            <span className="text-[9px] text-slate-500 block">Máximo:</span>
                            <Input
                              type="number"
                              step="0.01"
                              placeholder="9999,99"
                              value={columnFilters.amountMax}
                              onChange={(e) => setColumnFilters((p) => ({ ...p, amountMax: e.target.value }))}
                              className="h-7 text-xs bg-slate-900 border-slate-700 p-1"
                            />
                          </div>
                        </div>
                      </div>
                    </PopoverContent>
                  </Popover>
                </div>
              </th>

              {/* 7. Action Column */}
              <th className="py-2 px-1.5 w-14 text-center">Ações</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/[0.04]">
            {Boolean(summary?.carryoverAmount || summary?.previousMonthDeficit) && (
              <tr className={summary?.previousMonthDeficit ? "bg-rose-950/20 hover:bg-rose-950/30 transition-colors" : "bg-emerald-950/20 hover:bg-emerald-950/30 transition-colors"}>
                <td className="py-2.5 px-3 text-center">
                  <span className="text-[10px] font-mono text-slate-400">01</span>
                </td>
                <td className="py-2.5 px-3">
                  <div className="flex items-center gap-2">
                    <div className={`p-1 rounded-md ${summary?.previousMonthDeficit ? 'bg-rose-500/20 text-rose-300' : 'bg-emerald-500/20 text-emerald-300'}`}>
                      <Landmark className="size-3.5" />
                    </div>
                    <div>
                      <p className="text-xs font-bold text-slate-200">
                        {summary?.previousMonthDeficit ? 'Déficit Anterior Acumulado' : 'Saldo Inicial do Mês'}
                      </p>
                      <p className="text-[10px] text-slate-400">
                        {summary?.previousMonthDeficit ? 'Débito acumulado de meses anteriores' : 'Sobra transferida do mês anterior'}
                      </p>
                    </div>
                  </div>
                </td>
                <td className="py-2.5 px-2">
                  <span className="text-[10px] text-slate-400 font-medium">Saldo de Abertura</span>
                </td>
                <td className="py-2.5 px-2">
                  <span className="text-[10px] text-slate-500">—</span>
                </td>
                <td className="py-2.5 px-2">
                  <span className="text-[10px] text-slate-500">Conta Corrente</span>
                </td>
                <td className="py-2.5 px-3 text-right">
                  <span className={`text-xs font-mono font-bold ${summary?.previousMonthDeficit ? 'text-rose-400' : 'text-emerald-400'}`}>
                    {summary?.previousMonthDeficit ? `-${formatBRL(summary.previousMonthDeficit)}` : `+${formatBRL(summary?.carryoverAmount || 0)}`}
                  </span>
                </td>
                <td className="py-2.5 px-1.5 text-center">
                  <span className="text-[9px] px-1.5 py-0.5 rounded bg-white/5 text-slate-400 font-medium">Fixo</span>
                </td>
              </tr>
            )}
            {filteredTxs.length === 0 ? (
              <tr>
                <td colSpan={7} className="text-center py-12 text-slate-400">
                  <Filter className="size-8 mx-auto mb-2 opacity-30" />
                  <p className="font-medium text-slate-300">Nenhuma transação encontrada</p>
                  <p className="text-[11px] text-slate-500 mt-1">Tente ajustar os filtros ou adicione uma nova transação.</p>
                </td>
              </tr>
            ) : (
              filteredTxs.map((tx: ConsolidatedTransaction) => {
                if (tx.invoiceMeta) {
                  const invMeta = tx.invoiceMeta
                  const isOwner = invMeta.isOwner
                  return (
                    <tr
                      key={tx.id}
                      className="hover:bg-slate-800/40 transition-colors bg-sky-950/15 border-l-2 border-l-sky-500 group select-none"
                    >
                      {/* 1. Status da Fatura */}
                      <td className="py-2.5 px-3 align-middle">
                        <button
                          type="button"
                          onClick={() => {
                            if (isOwner && tx.status !== 'paid') {
                              handlePayConsolidatedInvoice(tx)
                            } else {
                              onOpenInvoices?.(invMeta.card.id)
                            }
                          }}
                          title={
                            isOwner
                              ? tx.status === 'paid'
                                ? 'Fatura quitada'
                                : 'Clique para marcar a fatura como paga'
                              : 'Fatura de cartão compartilhado (Modo leitura)'
                          }
                          className={isOwner && tx.status !== 'paid' ? 'cursor-pointer hover:scale-105 active:scale-95 transition-transform' : 'cursor-pointer'}
                        >
                          {tx.status === 'paid' ? (
                            <Badge variant="outline" className="text-[10px] bg-emerald-500/15 text-emerald-300 border-emerald-500/40 gap-1 font-semibold">
                              <CheckCircle2 className="size-2.5 text-emerald-400" />
                              Fatura Paga
                            </Badge>
                          ) : invMeta.invoiceStatus === 'closed' ? (
                            <Badge variant="outline" className="text-[10px] bg-amber-500/15 text-amber-300 border-amber-500/40 gap-1 font-semibold">
                              <Clock className="size-2.5 text-amber-400" />
                              Fatura Fechada
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-[10px] bg-sky-500/15 text-sky-300 border-sky-500/40 gap-1 font-semibold">
                              <span className="size-1.5 rounded-full bg-sky-400 animate-pulse" />
                              Fatura Aberta
                            </Badge>
                          )}
                        </button>
                      </td>

                      {/* 2. Data de Vencimento */}
                      <td className="py-2.5 px-3 text-slate-200 font-mono text-xs whitespace-nowrap align-middle font-semibold">
                        <div className="flex items-center gap-1.5">
                          <Calendar className="size-3 text-slate-500 shrink-0" />
                          <span>{formatShortDate(tx.dueDate)}</span>
                        </div>
                      </td>

                      {/* 3. Descrição & Metadata Badges */}
                      <td className="py-2.5 px-3 align-middle">
                        <div className="flex items-center gap-2.5">
                          <div
                            className="size-7 rounded-lg flex items-center justify-center shrink-0 border"
                            style={{
                              backgroundColor: `${invMeta.card.color ? '#8b5cf6' : '#0284c7'}20`,
                              borderColor: `${invMeta.card.color ? '#8b5cf6' : '#0284c7'}40`,
                            }}
                          >
                            <CreditCard className="size-3.5 text-sky-400" />
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="text-xs font-bold text-white tracking-wide">
                                {tx.description}
                              </span>
                              <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700 font-mono">
                                {invMeta.itemsCount} {invMeta.itemsCount === 1 ? 'compra' : 'compras'} no ciclo
                              </span>
                              <button
                                type="button"
                                onClick={() => onOpenInvoices?.(invMeta.card.id)}
                                className="text-[10px] text-sky-400 hover:text-sky-300 hover:underline flex items-center gap-1 cursor-pointer font-medium"
                              >
                                <span>Ver compras</span>
                                <Receipt className="size-2.5" />
                              </button>
                            </div>

                            {/* Detalhes de Rateio e Terceiros */}
                            {invMeta.othersShare > 0 && (
                              <p className="text-[10px] text-slate-400 mt-0.5">
                                Sua parte: <strong className="text-emerald-400">{formatBRL(invMeta.myShare)}</strong> · Outros: <strong className="text-amber-400">{formatBRL(invMeta.othersShare)}</strong> (Total fatura: {formatBRL(invMeta.totalInvoiceAmount)})
                              </p>
                            )}
                            {!isOwner && (
                              <p className="text-[10px] text-indigo-300 mt-0.5">
                                Cartão compartilhado por <strong>{invMeta.card.ownerName}</strong>
                              </p>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* 4. Categoria */}
                      <td className="py-2.5 px-3 align-middle">
                        <Badge variant="outline" className="text-[10px] bg-slate-800/80 text-slate-300 border-slate-700">
                          Cartão de Crédito
                        </Badge>
                      </td>

                      {/* 5. Responsável / Divisão */}
                      <td className="py-2.5 px-3 align-middle text-xs">
                        {isOwner ? (
                          invMeta.othersShare > 0 ? (
                            <span className="text-[10px] font-medium text-amber-300">Rateado com Terceiros</span>
                          ) : (
                            <span className="text-[10px] font-medium text-slate-300">100% Meu</span>
                          )
                        ) : (
                          <span className="text-[10px] font-medium text-indigo-300">Minha Cota</span>
                        )}
                      </td>

                      {/* 6. Valor (Minha parte!) */}
                      <td className="py-2.5 px-3 align-middle text-right">
                        <div className="font-mono font-bold text-xs text-white">
                          {formatBRL(tx.totalAmount)}
                        </div>
                        {invMeta.othersShare > 0 && (
                          <div className="text-[9px] text-slate-500 font-mono">
                            total: {formatBRL(invMeta.totalInvoiceAmount)}
                          </div>
                        )}
                      </td>

                      {/* 7. Ações */}
                      <td className="py-2.5 px-3 align-middle text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => onOpenInvoices?.(invMeta.card.id)}
                            className="text-slate-400 hover:text-white h-7 w-7 rounded-lg cursor-pointer"
                            title="Abrir fatura detalhada"
                          >
                            <Receipt className="size-3.5 text-sky-400" />
                          </Button>
                          {isOwner && tx.status !== 'paid' && (
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => handlePayConsolidatedInvoice(tx)}
                              className="text-slate-400 hover:text-emerald-400 h-7 w-7 rounded-lg cursor-pointer"
                              title="Pagar fatura"
                            >
                              <CheckCircle2 className="size-3.5 text-emerald-400" />
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                }

                const isEditing = editingId === tx.id
                const canEdit = canUserEditTransaction(tx, currentUid)
                const effAmount = effectiveAmount(tx)
                const myImpact = impactFor(tx, currentUid, effectiveTransactions)
                const split = detectSplit(tx, currentUid)
                const isRefund = tx.category === 'Estorno' || effAmount < 0 || tx.totalAmount < 0
                const card = cards.find((c) => c.id === tx.cardId)
                const isReserve = Boolean(tx.reserveCardId)
                const reserveInfo = isReserve ? getReserveInfo(tx, effectiveTransactions) : null
                const thirdParty = getThirdPartyInfo(tx, currentUid, partner?.name)
                const txPersons = getTxPersons(tx, currentUid, partner?.name, effectiveTransactions)

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
                        onClick={() => { if (canEdit) cycleStatus(tx) }}
                        title={canEdit ? "Clique para alternar: Estimado → Pendente → Pago" : "Lançamento recebido por rateio (somente leitura)"}
                        className={canEdit ? "cursor-pointer transition-transform hover:scale-105 active:scale-95" : "cursor-default opacity-85"}
                      >
                        {tx.status === 'estimated' ? (
                          <Badge variant="outline" className="text-[10px] bg-cyan-500/10 text-cyan-300 border-cyan-500/30 gap-1 font-medium">
                            <span className="size-1.5 rounded-full bg-cyan-400 animate-pulse" />
                            Estimado
                          </Badge>
                        ) : tx.status === 'pending' ? (
                          <Badge variant="outline" className="text-[10px] bg-amber-500/10 text-amber-300 border-amber-500/30 gap-1 font-medium">
                            <span className="size-1.5 rounded-full bg-amber-400" />
                            Pendente
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
                              onDoubleClick={() => { if (canEdit) startEdit(tx) }}
                              className={`font-medium text-slate-200 ${canEdit ? 'hover:text-white cursor-pointer' : 'cursor-default'}`}
                              title={canEdit ? "Duplo clique para editar" : undefined}
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
                                {(card.cardName || 'Cartão').split(' ')[0]}
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
                                  if (canEdit) onEditTx(tx)
                                }}
                                className={canEdit ? "cursor-pointer" : "cursor-default"}
                                title={canEdit ? `Parcelamento conectado (${tx.installmentNumber}/${tx.installmentsTotal}). Clique para editar a série ou a parcela.` : `Parcelamento conectado (${tx.installmentNumber}/${tx.installmentsTotal})`}
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
                          disabled={!canEdit}
                          value={tx.category}
                          onValueChange={(cat) => {
                            const newCat = cat as Category
                            const newSub = getCategoryDetail(newCat)?.subcategories?.[0] || null
                            if (tx.installmentGroupId) {
                              updateInstallmentGroup(tx.installmentGroupId, {
                                category: newCat,
                                subcategory: newSub,
                              })
                            } else {
                              const patch: Partial<Transaction> = {
                                category: newCat,
                                subcategory: newSub,
                              }
                              if (tx.assignedFriendUid) {
                                patch.acceptanceStatus = 'pending_approval'
                                patch.respondedAt = null
                              }
                              updateTransaction(tx.id, patch)
                            }
                          }}
                        >
                          <SelectTrigger size="inline" className="text-slate-300">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {(tx.type === 'income' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES).map((c) => (
                              <SelectItem key={c} value={c}>
                                {c}
                              </SelectItem>
                            ))}
                            {(LEGACY_CATEGORIES as readonly string[]).includes(tx.category) &&
                              !(tx.type === 'income' ? (INCOME_CATEGORIES as readonly string[]) : (EXPENSE_CATEGORIES as readonly string[])).includes(tx.category) && (
                                <SelectItem key={tx.category} value={tx.category}>
                                  {tx.category} (Legada)
                                </SelectItem>
                              )}
                          </SelectContent>
                        </Select>
                        {(getCategoryDetail(tx.category)?.subcategories?.length ?? 0) > 0 ? (
                          <Select
                            disabled={!canEdit}
                            value={tx.subcategory || ''}
                            onValueChange={(sub) => {
                              const newSub = sub || null
                              if (tx.installmentGroupId) {
                                updateInstallmentGroup(tx.installmentGroupId, {
                                  subcategory: newSub,
                                })
                              } else {
                                const patch: Partial<Transaction> = {
                                  subcategory: newSub,
                                }
                                if (tx.assignedFriendUid) {
                                  patch.acceptanceStatus = 'pending_approval'
                                  patch.respondedAt = null
                                }
                                updateTransaction(tx.id, patch)
                              }
                            }}
                          >
                            <SelectTrigger size="inline" className="h-5 px-1 py-0 text-[10px] text-emerald-400/90 hover:text-emerald-300 font-medium border-transparent bg-transparent hover:bg-slate-800/60 max-w-[130px] justify-start gap-1">
                              <span className="truncate">› {tx.subcategory || 'Sem subgrupo'}</span>
                            </SelectTrigger>
                            <SelectContent>
                              {getCategoryDetail(tx.category).subcategories.map((sub) => (
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
                    {/* 5. Division / Responsible */}
                    <td className="py-2 px-3 align-middle">
                      {tx.type === 'income' ? (
                        <div className="flex flex-col gap-1">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <Badge variant="outline" className="text-[10px] py-0.5 px-2 bg-emerald-950/40 text-emerald-300 border-emerald-800/60 font-medium">
                              💰 {tx.category ? `${tx.category}${tx.subcategory ? ` › ${tx.subcategory}` : ''}` : (tx.subcategory ? `Entrada › ${tx.subcategory}` : 'Receita Pessoal')}
                            </Badge>
                            {thirdParty && (
                              <Badge variant="outline" className="text-[10px] py-0.5 px-1.5 bg-sky-500/15 text-sky-300 border-sky-500/35 font-medium">
                                De: {thirdParty.name}
                              </Badge>
                            )}
                          </div>
                          {card && (
                            <span
                              className="inline-flex items-center gap-1 text-[10px] text-slate-400 font-medium px-1.5 py-0.5 rounded bg-slate-900/60 border border-white/[0.06] w-fit shadow-2xs"
                              title={`Cartão vinculado: ${card.cardName}`}
                            >
                              <CreditCard className="size-2.5 text-slate-400" />
                              <span className="truncate max-w-[120px]">{card.cardName}</span>
                            </span>
                          )}
                        </div>
                      ) : (
                        <div className="flex flex-col gap-1 min-w-[130px]">
                          <Select
                            disabled={!canEdit}
                            value={split}
                            onValueChange={(val) => {
                              if (val === 'custom') {
                                if (canEdit) onEditTx(tx)
                              } else {
                                changeSplit(tx, val as SplitType)
                              }
                            }}
                          >
                            <SelectTrigger
                              size="inline"
                              className="h-auto py-1 px-1.5 text-left flex items-center justify-between gap-1.5 hover:bg-slate-800/50 rounded-lg border border-transparent hover:border-slate-700/60 transition-colors w-full"
                            >
                              <div className="flex flex-col gap-1 items-start text-left">
                                {(() => {
                                  // Case A: Multi-person custom allocations or rateio (SHARED_50_50 / allocations)
                                  if (txPersons.length > 1) {
                                    return (
                                      <div className="flex flex-wrap gap-1 max-w-[240px]">
                                        {txPersons.map((p, idx) => (
                                          <span
                                            key={idx}
                                            className="text-[9px] px-1.5 py-0.5 rounded bg-slate-800/90 border border-slate-700/80 text-slate-200 font-medium inline-flex items-center gap-1 shadow-2xs"
                                            title={`${p.name}: ${formatBRL(p.amount)}`}
                                          >
                                            <span className="text-slate-400 truncate max-w-[65px]">
                                              {p.name === '100% Meu' ? 'Meu' : p.name}:
                                            </span>
                                            <strong className="text-emerald-400 font-mono font-bold">
                                              {formatBRL(p.amount)}
                                            </strong>
                                          </span>
                                        ))}
                                      </div>
                                    )
                                  }

                                  const single = txPersons[0] || { name: '100% Meu', role: 'me', amount: effAmount }

                                  // Case B: Third Party
                                  if (
                                    single.role === 'third_party' ||
                                    tx.splitType === 'external' ||
                                    (tx.splitType as string) === 'THIRD_PARTY' ||
                                    tx.thirdPartyName
                                  ) {
                                    const tpName = single.name !== '100% Meu' ? single.name : (tx.thirdPartyName || tx.externalName || 'Terceiro')
                                    return (
                                      <Badge
                                        variant="outline"
                                        className="text-[10px] py-0.5 px-2 bg-amber-500/15 text-amber-200 border-amber-500/40 font-semibold gap-1 inline-flex items-center shadow-2xs"
                                        title={`Despesa de Terceiro: ${tpName}`}
                                      >
                                        <span>🏷️ Terceiro: <strong className="text-white font-bold">{tpName}</strong></span>
                                      </Badge>
                                    )
                                  }

                                  // Case C: Friend
                                  if (
                                    single.role === 'friend' ||
                                    tx.splitType === 'friend' ||
                                    tx.assignedFriendUid ||
                                    tx.targetFriendName
                                  ) {
                                    const fName = single.name !== '100% Meu' ? single.name : (tx.assignedFriendName || tx.targetFriendName || 'Amigo')
                                    return (
                                      <Badge
                                        variant="outline"
                                        className="text-[10px] py-0.5 px-2 bg-violet-500/15 text-violet-200 border-violet-500/40 font-semibold gap-1 inline-flex items-center shadow-2xs"
                                        title={`Despesa de Amigo: ${fName}`}
                                      >
                                        <span>🤝 Amigo: <strong className="text-white font-bold">{fName}</strong> ({friendPctOf(tx)}%)</span>
                                      </Badge>
                                    )
                                  }

                                  // Case D: Partner
                                  if (single.role === 'partner' || tx.splitType === 'partner') {
                                    const pName = single.name !== '100% Meu' ? single.name : (partner?.name || 'Parceiro(a)')
                                    return (
                                      <Badge
                                        variant="outline"
                                        className="text-[10px] py-0.5 px-2 bg-indigo-500/15 text-indigo-200 border-indigo-500/40 font-semibold gap-1 inline-flex items-center shadow-2xs"
                                        title={`100% Parceiro: ${pName}`}
                                      >
                                        <span>🔄 100% <strong className="text-white font-bold">{pName}</strong></span>
                                      </Badge>
                                    )
                                  }

                                  // Case E: 100% Meu (default fallback - high contrast)
                                  return (
                                    <span className="text-[11px] text-slate-200 font-medium flex items-center gap-1 px-1">
                                      👤 100% Meu
                                    </span>
                                  )
                                })()}
                              </div>
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="mine">👤 100% Meu</SelectItem>
                              {partner && <SelectItem value="split_50">🤝 50/50 ({partner.name})</SelectItem>}
                              {partner && <SelectItem value="partner">🔄 100% {partner.name}</SelectItem>}
                              <SelectItem value="external">
                                🏷️ {thirdParty?.name || tx.thirdPartyName ? `Terceiro: ${thirdParty?.name || tx.thirdPartyName}` : 'Terceiro Externo'}
                              </SelectItem>
                              {(tx.assignedFriendUid || tx.assignedFriendName || tx.targetFriendName) && (
                                <SelectItem value="friend">
                                  🤝 {tx.assignedFriendName || tx.targetFriendName || 'Amigo'} ({friendPctOf(tx)}%)
                                </SelectItem>
                              )}
                              <SelectItem value="custom">
                                ⚖️ Multi-pessoas / Específico...
                              </SelectItem>
                            </SelectContent>
                          </Select>

                          {/* Subtle Card Name Badge when paymentMethod === 'CARD' or cardId is linked */}
                          {card && (
                            <div className="flex items-center gap-1 px-1 mt-0.5">
                              <span
                                className="inline-flex items-center gap-1 text-[10px] text-slate-400 font-medium px-1.5 py-0.5 rounded bg-slate-900/80 border border-white/[0.06] hover:border-slate-700 transition-colors shadow-2xs"
                                title={`Cartão vinculado: ${card.cardName} (${card.ownerName})`}
                              >
                                <CreditCard className="size-2.5 text-slate-400" />
                                <span className="truncate max-w-[120px]">{card.cardName}</span>
                              </span>
                              {tx.paidByUid !== currentUid && (
                                <span className="text-[9px] text-amber-400/90 font-medium truncate max-w-[110px]" title={`Fatura de ${card.ownerName}`}>
                                  ({card.ownerName})
                                </span>
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
                                  tx.type === 'income' ? 'text-emerald-400' : isRefund ? 'text-cyan-400' : 'text-slate-100'
                                }`}
                              >
                                {tx.type === 'income' ? `+${formatBRL(myImpact)}` : isRefund ? `+${formatBRL(Math.abs(myImpact))}` : `−${formatBRL(myImpact)}`}
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

                          {/* Parcela específica de pessoa/terceiro quando filtro estiver ativo */}
                          {columnFilters.persons.length > 0 && (() => {
                            const matchingPersons = txPersons.filter((p) => columnFilters.persons.includes(p.name))
                            if (matchingPersons.length === 0) return null
                            return (
                              <div className="text-[10px] text-amber-300 font-semibold font-mono flex items-center justify-end gap-1 mt-0.5">
                                <span className="text-[9px] uppercase font-sans text-amber-400/80">Parcela:</span>
                                <span>
                                  {matchingPersons
                                    .map((p) => `${matchingPersons.length > 1 ? `${p.name}: ` : ''}${formatBRL(p.amount)}`)
                                    .join(' | ')}
                                </span>
                              </div>
                            )
                          })()}

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
                      ) : !canEdit ? (
                        <div className="flex items-center justify-center p-1" title="Lançamento recebido por rateio. Apenas quem lançou pode editar ou excluir.">
                          <Lock className="size-3.5 text-slate-500" />
                        </div>
                      ) : (
                        <div className="flex items-center justify-center gap-1 opacity-40 group-hover:opacity-100 transition-opacity">
                          {onRefundTx && tx.type === 'expense' && !isRefund && (
                            <button
                              type="button"
                              onClick={() => onRefundTx(tx)}
                              className="p-1 rounded text-cyan-400 hover:text-cyan-200 hover:bg-cyan-950/40 transition-colors cursor-pointer"
                              title="Cadastrar estorno rateado deste lançamento"
                            >
                              <RotateCcw className="size-3.5" />
                            </button>
                          )}
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

      {/* 3. Rodapé Dinâmico de Somatório (Sticky Footer) */}
      <div className="sticky bottom-0 bg-[#090d16]/95 backdrop-blur-md border-t border-white/[0.08] px-3 sm:px-4 py-2.5 flex flex-wrap items-center justify-between gap-2 text-xs z-20 shrink-0">
        <div className="flex items-center gap-2 text-slate-400">
          <span>
            Exibindo <strong className="text-white font-mono">{filteredTxs.length}</strong> de <span className="font-mono">{monthTransactions.length}</span> lançamentos
          </span>
          {isAnyFilterActive && (
            <span className="text-[10px] font-semibold text-emerald-400 bg-emerald-500/15 px-1.5 py-0.5 rounded border border-emerald-500/30">
              Filtro ativo
            </span>
          )}
        </div>

        <div className="flex items-center gap-3 sm:gap-4 font-mono text-[11px] sm:text-xs ml-auto">
          {columnFilters.persons.length > 0 && (
            <div className="flex items-center gap-1.5 pl-2 sm:pl-3 border-l border-amber-500/40 bg-amber-500/10 px-2 sm:px-3 py-1 rounded-lg text-amber-300 animate-in fade-in">
              <span className="font-sans text-[10px] uppercase font-bold tracking-wider text-amber-400">
                Cobrança {columnFilters.persons.join(', ')}:
              </span>
              <span className="font-bold text-amber-200 text-xs sm:text-sm font-mono">
                {formatBRL(filteredPersonTotal)}
              </span>
            </div>
          )}

          <div className="flex items-center gap-1.5">
            <span className="text-slate-400 font-sans text-[10px] uppercase">Entradas:</span>
            <span className="text-emerald-400 font-bold">+{formatBRL(filteredSummary.income)}</span>
          </div>

          <div className="flex items-center gap-1.5">
            <span className="text-slate-400 font-sans text-[10px] uppercase">Despesas:</span>
            <span className="text-rose-400 font-bold">−{formatBRL(filteredSummary.expense)}</span>
          </div>

          <div className="flex items-center gap-1.5 pl-2 sm:pl-3 border-l border-white/10">
            <span className="text-slate-400 font-sans text-[10px] uppercase">Saldo Seleção:</span>
            <span className={`font-bold ${filteredSummary.net >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
              {filteredSummary.net >= 0 ? `+${formatBRL(filteredSummary.net)}` : formatBRL(filteredSummary.net)}
            </span>
          </div>
        </div>
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
