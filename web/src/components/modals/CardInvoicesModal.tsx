import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useFinance } from '@/store/FinanceContext'
import { effectiveAmount, canUserEditTransaction } from '@/lib/finance'
import {
  buildCardInvoice,
  buildInvoiceHistory,
  dueCountdownLabel,
  INVOICE_STATUS_META,
  participantAmountIn,
  calculateCategoryBreakdownForParticipant,
  type CardInvoice,
} from '@/lib/invoices'
import { formatBRL, formatNubankDateHeader, formatShortDate, monthLabel, shiftMonth, shortMonthLabel } from '@/lib/utils'
import { CATEGORY_GROUPS, type Category, type Transaction } from '@/types'
import {
  Calendar,
  CalendarClock,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CreditCard,
  Layers,
  ListChecks,
  Lock,
  PieChart,
  Receipt,
  Search,
  Sparkles,
  TrendingDown,
  Users,
  Wallet,
  Zap,
  RotateCcw,
} from 'lucide-react'

interface CardInvoicesModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onEditTx?: (tx: Transaction) => void
  onRefundTx?: (tx: Transaction) => void
  initialCardId?: string | null
  initialMonth?: string | null
}

/** Paleta estática alinhada com as cores dos cartões. */
const CARD_THEME: Record<string, { gradient: string; ring: string; bar: string; text: string; bgCard: string }> = {
  violet: {
    gradient: 'from-violet-600/40 via-violet-900/30',
    ring: 'ring-violet-500/60',
    bar: 'bg-violet-500',
    text: 'text-violet-300',
    bgCard: 'from-purple-900 via-indigo-950 to-slate-950',
  },
  orange: {
    gradient: 'from-orange-500/40 via-orange-900/30',
    ring: 'ring-orange-500/60',
    bar: 'bg-orange-500',
    text: 'text-orange-300',
    bgCard: 'from-orange-900 via-amber-950 to-slate-950',
  },
  slate: {
    gradient: 'from-slate-500/40 via-slate-800/30',
    ring: 'ring-slate-400/60',
    bar: 'bg-slate-400',
    text: 'text-slate-300',
    bgCard: 'from-slate-800 via-slate-900 to-slate-950',
  },
  emerald: {
    gradient: 'from-emerald-500/40 via-emerald-900/30',
    ring: 'ring-emerald-500/60',
    bar: 'bg-emerald-500',
    text: 'text-emerald-300',
    bgCard: 'from-emerald-900 via-teal-950 to-slate-950',
  },
  cyan: {
    gradient: 'from-cyan-500/40 via-cyan-900/30',
    ring: 'ring-cyan-500/60',
    bar: 'bg-cyan-500',
    text: 'text-cyan-300',
    bgCard: 'from-cyan-900 via-sky-950 to-slate-950',
  },
  rose: {
    gradient: 'from-rose-500/40 via-rose-900/30',
    ring: 'ring-rose-500/60',
    bar: 'bg-rose-500',
    text: 'text-rose-300',
    bgCard: 'from-rose-900 via-pink-950 to-slate-950',
  },
}
const themeOf = (color?: string) => CARD_THEME[color ?? 'violet'] ?? CARD_THEME.violet

const ITEM_STATUS_LABEL: Record<Transaction['status'], { label: string; tone: string }> = {
  estimated: { label: 'Estimado', tone: 'text-amber-300 bg-amber-500/10 border-amber-500/30' },
  pending: { label: 'Pendente', tone: 'text-orange-300 bg-orange-500/10 border-orange-500/30' },
  paid: { label: 'Pago', tone: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/30' },
}

function StatusPill({ inv }: { inv: CardInvoice }) {
  const meta = INVOICE_STATUS_META[inv.status]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px] font-semibold ${meta.tone}`}>
      <span className={`size-1.5 rounded-full ${meta.dot}`} />
      {meta.label}
    </span>
  )
}

export function CardInvoicesModal({
  open,
  onOpenChange,
  onEditTx,
  onRefundTx,
  initialCardId,
  initialMonth,
}: CardInvoicesModalProps) {
  const { cards, transactions, me, month, setStatus } = useFinance()
  const uid = me?.uid ?? ''

  const [viewMonth, setViewMonth] = useState(initialMonth || month)
  const [selectedCardId, setSelectedCardId] = useState<string | null>(initialCardId || null)
  const [selectedParticipantName, setSelectedParticipantName] = useState<string | null>(null)
  const [confirmPay, setConfirmPay] = useState(false)
  const [paying, setPaying] = useState(false)
  const [itemFilter, setItemFilter] = useState<'all' | 'third_party' | 'open' | 'paid' | 'installments'>('all')
  const [searchQuery, setSearchQuery] = useState('')

  // Sincroniza com o mês global ou inicial sempre que o modal é aberto
  useEffect(() => {
    if (open) {
      setViewMonth(initialMonth || month)
      if (initialCardId) setSelectedCardId(initialCardId)
      setConfirmPay(false)
      setSearchQuery('')
      setSelectedParticipantName(null)
    }
  }, [open, month, initialCardId, initialMonth])

  const invoices = useMemo(
    () => cards.map((c) => buildCardInvoice(c, viewMonth, transactions, uid)),
    [cards, viewMonth, transactions, uid],
  )

  const effectiveCardId = useMemo(() => {
    if (!invoices.length) return null
    if (selectedCardId && invoices.some((i) => i.card.id === selectedCardId)) return selectedCardId
    return (invoices.find((i) => i.itemsCount > 0) ?? invoices[0])?.card.id ?? null
  }, [invoices, selectedCardId])

  const selected = useMemo(
    () => invoices.find((i) => i.card.id === effectiveCardId) ?? null,
    [invoices, effectiveCardId],
  )

  const history = useMemo(
    () => (selected ? buildInvoiceHistory(selected.card, viewMonth, transactions, uid, 6, 2) : []),
    [selected, viewMonth, transactions, uid],
  )
  const historyMax = Math.max(1, ...history.map((h) => h.total))

  const totals = useMemo(() => {
    const total = invoices.reduce((s, i) => s + i.total, 0)
    const open = invoices.reduce((s, i) => s + i.openAmount, 0)
    const mine = invoices.reduce((s, i) => s + i.myShare, 0)
    const nextDue = invoices
      .filter((i) => i.openAmount > 0)
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0]
    return { total, open, mine, nextDue }
  }, [invoices])

  const hideKpis = Boolean(
    selected &&
      (invoices.length <= 1 ||
        (Math.abs(totals.open - selected.openAmount) < 0.01 && Math.abs(totals.total - selected.total) < 0.01)),
  )

  const activeCategorySlices = useMemo(() => {
    if (!selected) return []
    if (selectedParticipantName) {
      const part = selected.splitBreakdown.find((p) => p.name === selectedParticipantName)
      if (part) {
        const expenses = selected.items.filter((t) => t.type === 'expense')
        return calculateCategoryBreakdownForParticipant(expenses, part, uid, selected.card)
      }
    }
    return selected.byCategory
  }, [selected, selectedParticipantName, uid])

  const visibleItems = useMemo(() => {
    if (!selected) return []
    let list = selected.items

    if (selectedParticipantName) {
      const part = selected.splitBreakdown.find((p) => p.name === selectedParticipantName)
      if (part) {
        list = list.filter((t) => participantAmountIn(t, part, uid, selected.card) > 0)
      }
    }

    if (itemFilter === 'third_party') {
      list = list.filter((t) => Boolean(t.assignedFriendName || t.externalName || (t.allocations && t.allocations.length > 0)))
    } else if (itemFilter === 'open') {
      list = list.filter((t) => t.status !== 'paid')
    } else if (itemFilter === 'paid') {
      list = list.filter((t) => t.status === 'paid')
    } else if (itemFilter === 'installments') {
      list = list.filter((t) => (t.installmentsTotal ?? 1) > 1)
    }

    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim()
      list = list.filter(
        (t) =>
          t.description.toLowerCase().includes(q) ||
          t.category.toLowerCase().includes(q) ||
          (t.subcategory && t.subcategory.toLowerCase().includes(q)),
      )
    }
    return list
  }, [selected, selectedParticipantName, itemFilter, searchQuery, uid])

  const groupedInvoiceItems = useMemo(() => {
    const groups: { dateKey: string; items: Transaction[]; total: number }[] = []
    const map = new Map<string, Transaction[]>()
    for (const tx of visibleItems) {
      const key = tx.dueDate ? tx.dueDate.trim() : 'Sem data'
      if (!map.has(key)) {
        map.set(key, [])
      }
      map.get(key)!.push(tx)
    }
    for (const [dateKey, items] of map.entries()) {
      const total = items.reduce((sum, tx) => {
        const activePart = selectedParticipantName ? selected?.splitBreakdown.find((p) => p.name === selectedParticipantName) : null
        const partShare = activePart ? participantAmountIn(tx, activePart, uid, selected?.card) : null
        const hasPartFilter = Boolean(selectedParticipantName && partShare != null && partShare > 0)
        const amount = hasPartFilter ? partShare! : effectiveAmount(tx)
        return sum + (tx.type === 'income' ? -amount : amount)
      }, 0)
      groups.push({ dateKey, items, total })
    }
    return groups
  }, [visibleItems, selectedParticipantName, selected?.splitBreakdown, uid])

  const handlePayInvoice = async () => {
    if (!selected) return
    setPaying(true)
    try {
      const unpaid = selected.items.filter((t) => t.type === 'expense' && t.status !== 'paid')
      await Promise.all(unpaid.map((t) => setStatus(t, 'paid')))
      setConfirmPay(false)
    } finally {
      setPaying(false)
    }
  }

  const theme = themeOf(selected?.card.color)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-5xl lg:max-w-6xl w-[96vw] max-h-[94dvh] overflow-y-auto bg-[#0a0f1d]/98 border border-white/[0.1] text-slate-100 p-4 sm:p-6 md:p-7 rounded-[2rem] shadow-2xl backdrop-blur-2xl">
        <DialogHeader className="space-y-1">
          <DialogTitle className="flex items-center gap-2 text-lg sm:text-xl font-bold text-white">
            <Receipt className="size-5 text-cyan-400" />
            Faturas dos Cartões
          </DialogTitle>
          <DialogDescription className="text-xs text-slate-400">
            Acompanhamento inteligente de faturas, rateio de despesas, melhor data de compra e limites.
          </DialogDescription>
        </DialogHeader>

        {/* Navegação de mês */}
        <div className="flex items-center justify-between gap-2 rounded-2xl border border-white/[0.08] bg-slate-900/50 p-1.5">
          <button
            type="button"
            onClick={() => {
              setViewMonth((m) => shiftMonth(m, -1))
              setSelectedParticipantName(null)
            }}
            className="p-2 rounded-xl hover:bg-slate-800 text-slate-300 cursor-pointer transition-colors"
            aria-label="Fatura anterior"
          >
            <ChevronLeft className="size-4" />
          </button>
          <div className="text-center">
            <p className="text-[10px] uppercase tracking-wider text-slate-500 font-medium">Faturas com vencimento em</p>
            <p className="text-sm font-bold text-white capitalize">{monthLabel(viewMonth)}</p>
          </div>
          <button
            type="button"
            onClick={() => {
              setViewMonth((m) => shiftMonth(m, 1))
              setSelectedParticipantName(null)
            }}
            className="p-2 rounded-xl hover:bg-slate-800 text-slate-300 cursor-pointer transition-colors"
            aria-label="Próxima fatura"
          >
            <ChevronRight className="size-4" />
          </button>
        </div>

        {cards.length === 0 ? (
          <div className="py-16 text-center space-y-3">
            <div className="size-16 rounded-3xl bg-slate-900/90 border border-slate-800 flex items-center justify-center mx-auto text-slate-600">
              <CreditCard className="size-8" />
            </div>
            <p className="text-base text-slate-200 font-bold">Nenhum cartão cadastrado</p>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              Cadastre seus cartões de crédito na seção "Cartões de Crédito" para vincular às suas compras e acompanhar as faturas com inteligência.
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* KPIs consolidados (ocultados quando redundantes com o cartão único exibido) */}
            {!hideKpis && (
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5">
                <Kpi icon={<Wallet className="size-4 text-cyan-400" />} label="Total das faturas" value={formatBRL(totals.total)} />
                <Kpi icon={<Lock className="size-4 text-amber-400" />} label="Em aberto" value={formatBRL(totals.open)} tone="text-amber-300" />
                <Kpi icon={<Users className="size-4 text-emerald-400" />} label="Minha parte" value={formatBRL(totals.mine)} tone="text-emerald-300" />
                <Kpi
                  icon={<CalendarClock className="size-4 text-rose-400" />}
                  label="Próximo vencimento"
                  value={totals.nextDue ? formatShortDate(totals.nextDue.dueDate) : '—'}
                  hint={totals.nextDue ? totals.nextDue.card.cardName : 'Tudo em dia'}
                />
              </div>
            )}

            {/* Seletor de cartões em carrossel horizontal */}
            {invoices.length > 1 && (
              <div className="flex gap-2.5 overflow-x-auto pb-1 -mx-1 px-1 snap-x">
                {invoices.map((inv) => {
                  const t = themeOf(inv.card.color)
                  const active = inv.card.id === effectiveCardId
                  const formattedName = inv.card.cardName.trim()
                    ? inv.card.cardName.trim().replace(/^./, (c) => c.toUpperCase())
                    : 'Cartão'
                  const chipSubtitle =
                    inv.itemsCount === 0
                      ? 'Sem compras no ciclo'
                      : `Vence ${formatShortDate(inv.dueDate)} · ${inv.itemsCount} ${inv.itemsCount === 1 ? 'item' : 'itens'}`

                  return (
                    <button
                      key={inv.card.id}
                      type="button"
                      onClick={() => {
                        setSelectedCardId(inv.card.id)
                        setSelectedParticipantName(null)
                        setConfirmPay(false)
                      }}
                      className={`snap-start shrink-0 w-[220px] text-left rounded-2xl p-3.5 border transition-all cursor-pointer ${
                        active
                          ? `ring-2 ${t.ring} border-white/20 bg-gradient-to-br ${t.gradient} to-slate-950 scale-[1.01] shadow-lg`
                          : 'border-white/[0.08] bg-slate-900/40 opacity-75 hover:opacity-100 hover:border-white/20'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm font-bold text-white truncate">{formattedName}</span>
                        <StatusPill inv={inv} />
                      </div>
                      <p className="mt-3 text-lg font-extrabold text-white tabular-nums">{formatBRL(inv.total)}</p>
                      <div className="mt-1 flex items-center justify-between text-[11px] text-slate-300">
                        <span>{chipSubtitle}</span>
                      </div>
                    </button>
                  )
                })}
              </div>
            )}

            {selected && (
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
                {/* Coluna esquerda: Cartão Virtual Visual + Insights Inteligentes + Rateio */}
                <div className="lg:col-span-5 space-y-4">
                  {/* Cartão Virtual Interativo */}
                  <section
                    className={`relative rounded-3xl p-5 border border-white/15 bg-gradient-to-br ${theme.bgCard} text-white shadow-2xl overflow-hidden`}
                  >
                    {/* Linhas de fundo decorativas */}
                    <div className="absolute -top-12 -right-12 size-40 rounded-full bg-white/5 blur-2xl pointer-events-none" />
                    <div className="absolute -bottom-12 -left-12 size-40 rounded-full bg-white/5 blur-2xl pointer-events-none" />

                    <div className="flex items-start justify-between relative z-10">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs uppercase font-extrabold tracking-widest text-white/80">
                            {selected.card.cardName}
                          </span>
                          <span className="text-[10px] px-2 py-0.5 rounded-full bg-white/10 text-white/90 border border-white/20">
                            {selected.card.ownerName || 'Titular'}
                          </span>
                        </div>
                        <p className="mt-2 text-3xl font-black tabular-nums tracking-tight text-white drop-shadow-sm">
                          {formatBRL(selected.total)}
                        </p>
                        <p className={`text-xs font-semibold mt-0.5 ${selected.status === 'overdue' ? 'text-rose-300' : 'text-slate-200'}`}>
                          {dueCountdownLabel(selected)}
                        </p>
                      </div>

                      {/* Chip metálico simulado */}
                      <div className="flex flex-col items-end gap-1.5">
                        <div className="size-9 rounded-lg bg-gradient-to-tr from-amber-300 via-amber-400 to-amber-200 border border-amber-500/50 shadow-inner flex flex-col justify-between p-1.5 opacity-90">
                          <div className="h-0.5 w-full bg-amber-600/60 rounded" />
                          <div className="h-0.5 w-full bg-amber-600/60 rounded" />
                        </div>
                        <StatusPill inv={selected} />
                      </div>
                    </div>

                    {/* Datas de Fechamento e Vencimento */}
                    <div className="grid grid-cols-2 gap-2 mt-4 relative z-10">
                      <div className="rounded-xl bg-black/25 border border-white/10 p-2">
                        <p className="text-[10px] text-white/60">Fechamento</p>
                        <p className="text-xs font-bold text-white tabular-nums">{formatShortDate(selected.closingDate)}</p>
                      </div>
                      <div className="rounded-xl bg-black/25 border border-white/10 p-2">
                        <p className="text-[10px] text-white/60">Vencimento</p>
                        <p className="text-xs font-bold text-white tabular-nums">{formatShortDate(selected.dueDate)}</p>
                      </div>
                    </div>

                    {/* Progresso de pagamento da fatura */}
                    <div className="space-y-1 mt-4 relative z-10">
                      <div className="flex justify-between text-[11px] text-white/80">
                        <span>Pago: {formatBRL(selected.paidAmount)}</span>
                        <span>Em aberto: {formatBRL(selected.openAmount)}</span>
                      </div>
                      <div className="h-2 rounded-full bg-black/40 overflow-hidden">
                        <div
                          className="h-full bg-emerald-400 transition-all shadow-sm"
                          style={{ width: `${selected.total > 0 ? (selected.paidAmount / selected.total) * 100 : 0}%` }}
                        />
                      </div>
                    </div>

                    {/* Limite do Cartão */}
                    {selected.limit != null && (
                      <div className="space-y-1 mt-3 relative z-10">
                        <div className="flex justify-between text-[11px] text-white/80">
                          <span>Uso do limite ({selected.limitUsagePct}%)</span>
                          <span className={selected.availableLimit != null && selected.availableLimit <= 0 ? 'text-rose-300 font-bold' : ''}>
                            Disponível: {formatBRL(selected.availableLimit ?? Math.max(0, selected.limit - selected.total))}
                          </span>
                        </div>
                        <div className="h-1.5 rounded-full bg-black/40 overflow-hidden">
                          <div
                            className={`h-full transition-all ${
                              (selected.limitUsagePct ?? 0) >= 90 ? 'bg-rose-400' : (selected.limitUsagePct ?? 0) >= 70 ? 'bg-amber-400' : 'bg-cyan-400'
                            }`}
                            style={{ width: `${selected.limitUsagePct ?? 0}%` }}
                          />
                        </div>
                        {selected.futureInstallmentsTotal > 0 && (
                          <p className="text-[10px] text-white/70">
                            Inclui {formatBRL(selected.futureInstallmentsTotal)} em faturas futuras
                          </p>
                        )}
                      </div>
                    )}

                    {/* Ação de pagamento */}
                    {selected.openAmount > 0 && (
                      <div className="mt-4 pt-3 border-t border-white/15 relative z-10">
                        {selected.card.ownerUid !== uid ? (
                          <div className="p-3 rounded-xl bg-indigo-950/60 border border-indigo-400/30 text-indigo-200 text-xs flex items-center justify-between gap-2 backdrop-blur-md">
                            <div>
                              <span className="font-semibold block text-white">Cartão de {selected.card.ownerName}</span>
                              <span className="text-[11px] text-indigo-300">
                                Sua parte nesta fatura: <strong>{formatBRL(selected.myShare)}</strong>
                              </span>
                            </div>
                            <span className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-900/60 text-indigo-200 border border-indigo-700">
                              Somente Leitura
                            </span>
                          </div>
                        ) : confirmPay ? (
                          <div className="rounded-xl border border-emerald-400/40 bg-emerald-950/60 p-3 space-y-2 backdrop-blur-md">
                            <p className="text-xs text-emerald-200">
                              Marcar {selected.items.filter((t) => t.type === 'expense' && t.status !== 'paid').length} lançamento(s) como
                              pagos, totalizando <strong>{formatBRL(selected.openAmount)}</strong>?
                            </p>
                            <div className="flex gap-2 justify-end">
                              <Button
                                size="sm"
                                variant="outline"
                                className="h-8 text-xs border-white/20 text-white hover:bg-white/10 cursor-pointer"
                                onClick={() => setConfirmPay(false)}
                                disabled={paying}
                              >
                                Cancelar
                              </Button>
                              <Button
                                size="sm"
                                className="h-8 text-xs bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold cursor-pointer"
                                onClick={handlePayInvoice}
                                disabled={paying}
                              >
                                {paying ? 'Pagando...' : 'Confirmar pagamento'}
                              </Button>
                            </div>
                          </div>
                        ) : (
                          <Button
                            onClick={() => setConfirmPay(true)}
                            className="w-full h-10 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs rounded-xl cursor-pointer shadow-lg transition-transform active:scale-95"
                          >
                            <CheckCircle2 className="size-4 mr-1.5" />
                            Pagar fatura ({formatBRL(selected.openAmount)})
                          </Button>
                        )}
                      </div>
                    )}
                  </section>

                  {/* Card Inteligente: Melhor Dia de Compra & Ciclo */}
                  <section className="rounded-2xl p-4 border border-white/[0.08] bg-slate-900/50 space-y-3">
                    <div className="flex items-center justify-between">
                      <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                        <Sparkles className="size-3.5 text-amber-400" /> Inteligência de Compra
                      </h4>
                      {selected.isBestPurchaseTime ? (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30 flex items-center gap-1">
                          <Zap className="size-3" /> Melhor Época Ativa!
                        </span>
                      ) : (
                        <span className="text-[11px] font-semibold text-slate-400">
                          Dia {selected.bestPurchaseDay}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-300 leading-relaxed">
                      {selected.bestPurchaseHint}
                    </p>
                    <div className="space-y-1 pt-1 border-t border-white/[0.06]">
                      <div className="flex justify-between text-[11px] text-slate-400">
                        <span>Progresso do ciclo da fatura</span>
                        <span className="text-slate-200 font-semibold">{selected.cycleProgressPct}%</span>
                      </div>
                      <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
                        <div
                          className="h-full bg-gradient-to-r from-sky-500 to-indigo-500 rounded-full transition-all"
                          style={{ width: `${selected.cycleProgressPct}%` }}
                        />
                      </div>
                    </div>
                  </section>

                  {/* Card Inteligente: Divisão da Fatura (Quem Divide Comigo) */}
                  {selected.splitBreakdown.length > 0 && (
                    <section className="rounded-2xl p-4 border border-white/[0.08] bg-slate-900/50 space-y-2.5">
                      <div className="flex items-center justify-between">
                        <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                          <Users className="size-3.5 text-emerald-400" /> Divisão da Fatura
                        </h4>
                        <span className="text-[10px] text-slate-400">
                          {selectedParticipantName ? 'Clique para desmarcar' : 'Clique para filtrar'}
                        </span>
                      </div>
                      <div className="space-y-2">
                        {selected.splitBreakdown.map((part) => {
                          const isSelected = selectedParticipantName === part.name
                          return (
                            <button
                              key={part.name}
                              type="button"
                              onClick={() => setSelectedParticipantName(isSelected ? null : part.name)}
                              className={`w-full text-left p-2.5 rounded-xl transition-all cursor-pointer space-y-1.5 border ${
                                isSelected
                                  ? 'bg-emerald-950/50 border-emerald-500/60 ring-2 ring-emerald-500/30 shadow-md'
                                  : 'bg-slate-950/60 border-white/[0.05] hover:border-white/20 hover:bg-slate-900/60'
                              }`}
                            >
                              <div className="flex items-center justify-between text-xs">
                                <div className="flex items-center gap-1.5 min-w-0">
                                  <span className={`font-semibold truncate ${part.isMe ? 'text-emerald-300' : 'text-slate-200'}`}>
                                    {part.name}
                                  </span>
                                  {isSelected && (
                                    <span className="text-[9px] px-1.5 py-0.2 rounded-full bg-emerald-500/20 text-emerald-300 font-bold border border-emerald-500/40 shrink-0">
                                      Filtrando ✕
                                    </span>
                                  )}
                                </div>
                                <span className="font-bold tabular-nums text-white shrink-0 ml-2">
                                  {formatBRL(part.amount)} ({part.pct}%)
                                </span>
                              </div>
                              <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
                                <div
                                  className={`h-full rounded-full ${part.isMe ? 'bg-emerald-500' : 'bg-sky-500'}`}
                                  style={{ width: `${part.pct}%` }}
                                />
                              </div>
                            </button>
                          )
                        })}
                      </div>
                    </section>
                  )}

                  {/* Comprometimento Futuro & Maior Gasto */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    <div className="p-3 rounded-2xl border border-white/[0.08] bg-slate-900/40 space-y-1">
                      <p className="text-[10px] text-slate-400 flex items-center gap-1 font-semibold uppercase tracking-wider">
                        <Calendar className="size-3 text-cyan-400" /> Futuro Comprometido
                      </p>
                      <p className="text-base font-extrabold text-white tabular-nums">
                        {formatBRL(selected.futureInstallmentsTotal)}
                      </p>
                      <p className="text-[10px] text-slate-400">
                        {selected.futureInstallmentsCount} parcelas nos próximos meses
                      </p>
                    </div>

                    <div className="p-3 rounded-2xl border border-white/[0.08] bg-slate-900/40 space-y-1">
                      <p className="text-[10px] text-slate-400 flex items-center gap-1 font-semibold uppercase tracking-wider">
                        <TrendingDown className="size-3 text-rose-400" /> Maior Compra
                      </p>
                      <p className="text-base font-extrabold text-white tabular-nums truncate">
                        {selected.topExpense ? formatBRL(effectiveAmount(selected.topExpense)) : '—'}
                      </p>
                      <p className="text-[10px] text-slate-400 truncate">
                        {selected.topExpense?.description ?? 'Nenhuma despesa'}
                      </p>
                    </div>
                  </div>

                  {/* Histórico e Evolução */}
                  <section className="rounded-2xl p-4 border border-white/[0.08] bg-slate-900/40 space-y-3">
                    <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                      <Layers className="size-3.5 text-cyan-400" /> Evolução das faturas
                    </h4>
                    <div className="flex items-end gap-1.5 h-28">
                      {history.map((h) => {
                        const isCurrent = h.monthKey === viewMonth
                        const pct = (h.total / historyMax) * 100
                        return (
                          <button
                            key={h.monthKey}
                            type="button"
                            onClick={() => {
                              setViewMonth(h.monthKey)
                              setSelectedParticipantName(null)
                            }}
                            className="flex-1 h-full flex flex-col justify-end items-center gap-1 group cursor-pointer"
                            title={`${monthLabel(h.monthKey)}: ${formatBRL(h.total)}`}
                          >
                            <span className="text-[9px] text-slate-400 tabular-nums opacity-0 group-hover:opacity-100 transition-opacity">
                              {formatBRL(h.total, true)}
                            </span>
                            <div
                              className={`w-full rounded-t-md transition-all ${
                                isCurrent ? theme.bar : h.monthKey > viewMonth ? 'bg-slate-700/60 border border-dashed border-slate-500' : 'bg-slate-600/70 group-hover:bg-slate-500'
                              }`}
                              style={{ height: `${Math.max(pct, h.total > 0 ? 4 : 1)}%` }}
                            />
                            <span className={`text-[10px] capitalize ${isCurrent ? 'text-white font-bold' : 'text-slate-500'}`}>
                              {shortMonthLabel(h.monthKey)}
                            </span>
                          </button>
                        )
                      })}
                    </div>
                    <p className="text-[10px] text-slate-500">Barras tracejadas = faturas futuras já comprometidas por compras parceladas.</p>
                  </section>

                  {/* Categorias (dinâmicas por participante) */}
                  {activeCategorySlices.length > 0 && (
                    <section className="rounded-2xl p-4 border border-white/[0.08] bg-slate-900/40 space-y-2.5">
                      <div className="flex items-center justify-between">
                        <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                          <PieChart className="size-3.5 text-violet-400" />
                          <span>
                            {selectedParticipantName ? `Gastos de ${selectedParticipantName}` : 'Para onde foi o dinheiro'}
                          </span>
                        </h4>
                        {selectedParticipantName && (
                          <button
                            type="button"
                            onClick={() => setSelectedParticipantName(null)}
                            className="text-[10px] text-cyan-400 hover:underline cursor-pointer"
                          >
                            Ver geral
                          </button>
                        )}
                      </div>
                      {activeCategorySlices.slice(0, 6).map((c) => {
                        const color = CATEGORY_GROUPS[c.category as Category]?.color ?? '#64748b'
                        return (
                          <div key={c.category} className="space-y-1">
                            <div className="flex justify-between text-[11px]">
                              <span className="text-slate-300">{c.category}</span>
                              <span className="text-slate-400 tabular-nums">
                                {formatBRL(c.amount)} · {c.pct}%
                              </span>
                            </div>
                            <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
                              <div className="h-full rounded-full" style={{ width: `${c.pct}%`, backgroundColor: color }} />
                            </div>
                          </div>
                        )
                      })}
                    </section>
                  )}
                </div>

                {/* Coluna direita: Lista Inteligente de Lançamentos com Busca e Filtros */}
                <section className="lg:col-span-7 rounded-2xl p-4 sm:p-5 border border-white/[0.08] bg-slate-900/40 space-y-3.5 min-h-0 flex flex-col">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                    <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                      <ListChecks className="size-3.5 text-emerald-400" /> Lançamentos da Fatura
                      <span className="text-slate-500 font-normal">({selected.itemsCount})</span>
                    </h4>

                    {/* Filtros em abas */}
                    <div className="flex gap-1 rounded-xl bg-slate-950/60 p-1 border border-white/[0.06] overflow-x-auto">
                      {(
                        [
                          ['all', 'Todos'],
                          ['third_party', 'Terceiros'],
                          ['open', 'Em aberto'],
                          ['paid', 'Pagos'],
                          ['installments', 'Parcelados'],
                        ] as const
                      ).map(([key, label]) => (
                        <button
                          key={key}
                          type="button"
                          onClick={() => setItemFilter(key)}
                          className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold cursor-pointer transition-colors whitespace-nowrap ${
                            itemFilter === key ? 'bg-slate-700 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Banner de filtro ativo por participante */}
                  {selectedParticipantName && (
                    <div className="flex items-center justify-between p-2.5 rounded-xl bg-emerald-950/40 border border-emerald-500/30 text-xs text-emerald-200 animate-in fade-in">
                      <span className="flex items-center gap-1.5 truncate">
                        <span>Exibindo lançamentos de:</span>
                        <strong className="text-white font-semibold truncate">{selectedParticipantName}</strong>
                      </span>
                      <button
                        type="button"
                        onClick={() => setSelectedParticipantName(null)}
                        className="text-[11px] text-emerald-400 hover:text-white underline cursor-pointer ml-2 shrink-0 font-medium"
                      >
                        Limpar filtro ✕
                      </button>
                    </div>
                  )}

                  {/* Campo de Busca Rápida */}
                  <div className="relative">
                    <Search className="size-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
                    <Input
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder="Pesquisar lançamentos por descrição ou categoria..."
                      className="h-9 pl-9 text-xs bg-slate-950/70 border-white/[0.08] rounded-xl text-slate-200 placeholder:text-slate-500"
                    />
                  </div>

                  {visibleItems.length === 0 ? (
                    <div className="py-16 text-center space-y-2">
                      <p className="text-xs text-slate-400 font-medium">Nenhum lançamento encontrado para este filtro.</p>
                      {(searchQuery || selectedParticipantName || itemFilter !== 'all') && (
                        <button
                          type="button"
                          onClick={() => {
                            setSearchQuery('')
                            setSelectedParticipantName(null)
                            setItemFilter('all')
                          }}
                          className="text-[11px] text-cyan-400 hover:underline cursor-pointer"
                        >
                          Limpar todos os filtros
                        </button>
                      )}
                    </div>
                  ) : (
                    <div className="space-y-4 max-h-[65dvh] overflow-y-auto pr-1">
                      {groupedInvoiceItems.map((group) => {
                        const header = formatNubankDateHeader(group.dateKey)
                        return (
                          <div key={group.dateKey} className="space-y-1.5">
                            <div className="flex items-center justify-between px-2 pt-1.5 pb-0.5">
                              <div className="flex items-center gap-2">
                                <span className={`text-xs font-bold tracking-wide ${header.isToday ? 'text-cyan-400' : header.isYesterday ? 'text-amber-400' : 'text-slate-200'}`}>
                                  {header.title}
                                </span>
                                {header.subtitle && (
                                  <span className="text-[11px] text-slate-400">
                                    · {header.subtitle}
                                  </span>
                                )}
                              </div>
                              <span className="text-[11px] font-semibold text-slate-300 tabular-nums">
                                {formatBRL(group.total)}
                              </span>
                            </div>

                            <ul className="divide-y divide-white/[0.05] rounded-xl bg-slate-900/40 border border-white/[0.05] px-1">
                              {group.items.map((tx) => {
                                const isCredit = tx.type === 'income'
                                const st = ITEM_STATUS_LABEL[tx.status]
                                const mine = tx.userImpact?.[uid]
                                const activePart = selectedParticipantName ? selected.splitBreakdown.find((p) => p.name === selectedParticipantName) : null
                                const participantShare = activePart ? participantAmountIn(tx, activePart, uid, selected.card) : null
                                const hasParticipantFilter = Boolean(selectedParticipantName && participantShare != null && participantShare > 0)
                                const displayAmount = hasParticipantFilter ? participantShare! : effectiveAmount(tx)
                                const isPartial = hasParticipantFilter && Math.abs(displayAmount - effectiveAmount(tx)) > 0.01

                                const thirdParty =
                                  tx.assignedFriendName ||
                                  tx.externalName ||
                                  (tx.allocations && tx.allocations.find((a) => a.uid !== uid)?.name)

                                return (
                                  <li key={tx.id} className="flex items-center gap-1 group">
                                    <button
                                      type="button"
                                      onClick={() => onEditTx?.(tx)}
                                      className="flex-1 py-2.5 flex items-center gap-3 text-left hover:bg-slate-800/40 rounded-xl px-2 cursor-pointer transition-colors min-w-0"
                                    >
                                      <span
                                        className="size-2 rounded-full shrink-0"
                                        style={{ backgroundColor: CATEGORY_GROUPS[tx.category]?.color ?? '#64748b' }}
                                      />
                                      <div className="min-w-0 flex-1">
                                        <div className="flex items-center gap-1.5 flex-wrap">
                                          <span className="text-xs font-semibold text-slate-100 truncate">{tx.description}</span>
                                          {tx.installmentsTotal && tx.installmentsTotal > 1 && (
                                            <span className="text-[10px] px-2 py-0.5 rounded-full bg-cyan-500/15 text-cyan-300 font-bold border border-cyan-500/30">
                                              {tx.installmentNumber}/{tx.installmentsTotal}
                                            </span>
                                          )}
                                          {thirdParty && (
                                            <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-300 font-medium border border-amber-500/30 truncate max-w-[130px]">
                                              🏷️ {thirdParty}
                                            </span>
                                          )}
                                        </div>
                                        <p className="text-[10px] text-slate-400 truncate mt-0.5">
                                          {tx.category}
                                          {tx.subcategory ? ` › ${tx.subcategory}` : ''}
                                          {isPartial ? (
                                            <span className="text-emerald-400 font-medium"> · parte de {selectedParticipantName}</span>
                                          ) : (
                                            mine != null && !isCredit && Math.abs(mine - effectiveAmount(tx)) > 0.01 && (
                                              <> · minha parte <strong className="text-emerald-400">{formatBRL(mine)}</strong></>
                                            )
                                          )}
                                        </p>
                                      </div>
                                      <div className="text-right shrink-0 space-y-0.5">
                                        <p className={`text-xs font-bold tabular-nums ${isCredit ? 'text-teal-300' : hasParticipantFilter ? 'text-emerald-400' : 'text-white'}`}>
                                          {isCredit ? '− ' : ''}
                                          {formatBRL(displayAmount)}
                                        </p>
                                        {isPartial && (
                                          <p className="text-[10px] text-slate-500 tabular-nums">
                                            total {formatBRL(effectiveAmount(tx))}
                                          </p>
                                        )}
                                        <div className="flex items-center justify-end gap-1">
                                          {transactions.some((t) => t.refundedTransactionId === tx.id) && (
                                            <span
                                              className="inline-flex items-center gap-1 rounded-full border border-cyan-500/40 bg-cyan-950/60 text-cyan-300 px-1.5 py-0.5 text-[9px] font-semibold"
                                              title="Esta compra possui estorno vinculado"
                                            >
                                              <RotateCcw className="size-2.5" />
                                              Estornado
                                            </span>
                                          )}
                                          <span className={`inline-block rounded-full border px-1.5 text-[9px] font-semibold ${st.tone}`}>{st.label}</span>
                                        </div>
                                      </div>
                                    </button>
                                    {onRefundTx && tx.type === 'expense' && !isCredit && canUserEditTransaction(tx, uid) && (
                                      <button
                                        type="button"
                                        onClick={() => {
                                          onRefundTx(tx)
                                        }}
                                        className="opacity-70 group-hover:opacity-100 p-2 rounded-lg text-cyan-400 hover:text-cyan-200 hover:bg-cyan-950/60 transition-all cursor-pointer shrink-0"
                                        title="Registrar estorno desta compra"
                                      >
                                        <RotateCcw className="size-3.5" />
                                      </button>
                                    )}
                                  </li>
                                )
                              })}
                            </ul>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </section>
              </div>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

function Kpi({ icon, label, value, tone, hint }: { icon: ReactNode; label: string; value: string; tone?: string; hint?: string }) {
  return (
    <div className="rounded-2xl border border-white/[0.08] bg-slate-900/50 p-3">
      <div className="flex items-center gap-1.5 text-[11px] text-slate-400">
        {icon}
        {label}
      </div>
      <p className={`mt-1 text-base font-extrabold tabular-nums ${tone ?? 'text-white'}`}>{value}</p>
      {hint && <p className="text-[10px] text-slate-500 truncate">{hint}</p>}
    </div>
  )
}
