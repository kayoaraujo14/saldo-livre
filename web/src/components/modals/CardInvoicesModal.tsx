import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useFinance } from '@/store/FinanceContext'
import { effectiveAmount } from '@/lib/finance'
import {
  buildCardInvoice,
  buildInvoiceHistory,
  dueCountdownLabel,
  INVOICE_STATUS_META,
  type CardInvoice,
} from '@/lib/invoices'
import { formatBRL, formatShortDate, monthLabel, shiftMonth, shortMonthLabel } from '@/lib/utils'
import { CATEGORY_GROUPS, type Category, type Transaction } from '@/types'
import {
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
  Users,
  Wallet,
} from 'lucide-react'

interface CardInvoicesModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onEditTx?: (tx: Transaction) => void
}

/** Paleta estática (Tailwind precisa das classes literais para o JIT). */
const CARD_THEME: Record<string, { gradient: string; ring: string; bar: string; text: string }> = {
  violet: { gradient: 'from-violet-600/40 via-violet-900/30', ring: 'ring-violet-500/60', bar: 'bg-violet-500', text: 'text-violet-300' },
  orange: { gradient: 'from-orange-500/40 via-orange-900/30', ring: 'ring-orange-500/60', bar: 'bg-orange-500', text: 'text-orange-300' },
  slate: { gradient: 'from-slate-500/40 via-slate-800/30', ring: 'ring-slate-400/60', bar: 'bg-slate-400', text: 'text-slate-300' },
  emerald: { gradient: 'from-emerald-500/40 via-emerald-900/30', ring: 'ring-emerald-500/60', bar: 'bg-emerald-500', text: 'text-emerald-300' },
  cyan: { gradient: 'from-cyan-500/40 via-cyan-900/30', ring: 'ring-cyan-500/60', bar: 'bg-cyan-500', text: 'text-cyan-300' },
  rose: { gradient: 'from-rose-500/40 via-rose-900/30', ring: 'ring-rose-500/60', bar: 'bg-rose-500', text: 'text-rose-300' },
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

export function CardInvoicesModal({ open, onOpenChange, onEditTx }: CardInvoicesModalProps) {
  const { cards, transactions, me, month, setStatus } = useFinance()
  const uid = me?.uid ?? ''

  const [viewMonth, setViewMonth] = useState(month)
  const [selectedCardId, setSelectedCardId] = useState<string | null>(null)
  const [confirmPay, setConfirmPay] = useState(false)
  const [paying, setPaying] = useState(false)
  const [itemFilter, setItemFilter] = useState<'all' | 'open' | 'paid'>('all')

  // Sincroniza com o mês global sempre que o modal é aberto
  useEffect(() => {
    if (open) {
      setViewMonth(month)
      setConfirmPay(false)
    }
  }, [open, month])

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

  const visibleItems = useMemo(() => {
    if (!selected) return []
    if (itemFilter === 'open') return selected.items.filter((t) => t.status !== 'paid')
    if (itemFilter === 'paid') return selected.items.filter((t) => t.status === 'paid')
    return selected.items
  }, [selected, itemFilter])

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
            Acompanhe fechamento, vencimento, composição e pagamento de cada fatura.
          </DialogDescription>
        </DialogHeader>

        {/* Navegação de mês */}
        <div className="flex items-center justify-between gap-2 rounded-2xl border border-white/[0.08] bg-slate-900/50 p-1.5">
          <button
            type="button"
            onClick={() => setViewMonth((m) => shiftMonth(m, -1))}
            className="p-2 rounded-xl hover:bg-slate-800 text-slate-300 cursor-pointer"
            aria-label="Fatura anterior"
          >
            <ChevronLeft className="size-4" />
          </button>
          <div className="text-center">
            <p className="text-[10px] uppercase tracking-wider text-slate-500">Faturas com vencimento em</p>
            <p className="text-sm font-bold text-white capitalize">{monthLabel(viewMonth)}</p>
          </div>
          <button
            type="button"
            onClick={() => setViewMonth((m) => shiftMonth(m, 1))}
            className="p-2 rounded-xl hover:bg-slate-800 text-slate-300 cursor-pointer"
            aria-label="Próxima fatura"
          >
            <ChevronRight className="size-4" />
          </button>
        </div>

        {cards.length === 0 ? (
          <div className="py-16 text-center space-y-2">
            <CreditCard className="size-10 mx-auto text-slate-600" />
            <p className="text-sm text-slate-300 font-semibold">Nenhum cartão cadastrado</p>
            <p className="text-xs text-slate-500">Cadastre um cartão em "Cartões de Crédito" para acompanhar as faturas.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* KPIs consolidados */}
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

            {/* Seletor de cartões */}
            <div className="flex gap-2.5 overflow-x-auto pb-1 -mx-1 px-1 snap-x">
              {invoices.map((inv) => {
                const t = themeOf(inv.card.color)
                const active = inv.card.id === effectiveCardId
                return (
                  <button
                    key={inv.card.id}
                    type="button"
                    onClick={() => {
                      setSelectedCardId(inv.card.id)
                      setConfirmPay(false)
                    }}
                    className={`snap-start shrink-0 w-[210px] text-left rounded-2xl p-3.5 border border-white/[0.08] bg-gradient-to-br ${t.gradient} to-slate-950 transition-all cursor-pointer ${
                      active ? `ring-2 ${t.ring} scale-[1.01]` : 'opacity-75 hover:opacity-100'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-sm font-bold text-white truncate">{inv.card.cardName}</span>
                      <StatusPill inv={inv} />
                    </div>
                    <p className="mt-3 text-lg font-extrabold text-white tabular-nums">{formatBRL(inv.total)}</p>
                    <p className="text-[11px] text-slate-300">
                      Vence {formatShortDate(inv.dueDate)} · {inv.itemsCount} itens
                    </p>
                  </button>
                )
              })}
            </div>

            {selected && (
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
                {/* Coluna esquerda: resumo + histórico + categorias */}
                <div className="lg:col-span-5 space-y-4">
                  <section className={`rounded-2xl p-4 border border-white/[0.08] bg-gradient-to-br ${theme.gradient} to-slate-950 space-y-3`}>
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="text-[11px] text-slate-300">Fatura {selected.card.cardName}</p>
                        <p className="text-3xl font-extrabold text-white tabular-nums">{formatBRL(selected.total)}</p>
                        <p className={`text-xs font-semibold ${selected.status === 'overdue' ? 'text-rose-300' : 'text-slate-200'}`}>
                          {dueCountdownLabel(selected)}
                        </p>
                      </div>
                      <StatusPill inv={selected} />
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <InfoBox label="Fechamento" value={formatShortDate(selected.closingDate)} />
                      <InfoBox label="Vencimento" value={formatShortDate(selected.dueDate)} />
                      <InfoBox label="Minha parte" value={formatBRL(selected.myShare)} />
                      <InfoBox label="De terceiros" value={formatBRL(selected.othersShare)} />
                    </div>

                    {/* Progresso de pagamento */}
                    <div className="space-y-1">
                      <div className="flex justify-between text-[11px] text-slate-300">
                        <span>Pago {formatBRL(selected.paidAmount)}</span>
                        <span>Em aberto {formatBRL(selected.openAmount)}</span>
                      </div>
                      <div className="h-2 rounded-full bg-slate-900/80 overflow-hidden">
                        <div
                          className="h-full bg-emerald-500 transition-all"
                          style={{ width: `${selected.total > 0 ? (selected.paidAmount / selected.total) * 100 : 0}%` }}
                        />
                      </div>
                    </div>

                    {selected.limit != null && (
                      <div className="space-y-1">
                        <div className="flex justify-between text-[11px] text-slate-300">
                          <span>Uso do limite ({selected.limitUsagePct}%)</span>
                          <span>Disponível {formatBRL(Math.max(0, selected.limit - selected.total))}</span>
                        </div>
                        <div className="h-1.5 rounded-full bg-slate-900/80 overflow-hidden">
                          <div
                            className={`h-full transition-all ${
                              (selected.limitUsagePct ?? 0) >= 90 ? 'bg-rose-500' : (selected.limitUsagePct ?? 0) >= 70 ? 'bg-amber-500' : theme.bar
                            }`}
                            style={{ width: `${selected.limitUsagePct ?? 0}%` }}
                          />
                        </div>
                      </div>
                    )}

                    {selected.credits > 0 && (
                      <p className="text-[11px] text-teal-300">Inclui {formatBRL(selected.credits)} em créditos/estornos.</p>
                    )}

                    {/* Ação de pagamento */}
                    {selected.openAmount > 0 &&
                      (confirmPay ? (
                        <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-3 space-y-2">
                          <p className="text-xs text-emerald-200">
                            Marcar {selected.items.filter((t) => t.type === 'expense' && t.status !== 'paid').length} lançamento(s) como
                            pagos, totalizando <strong>{formatBRL(selected.openAmount)}</strong>?
                          </p>
                          <div className="flex gap-2 justify-end">
                            <Button size="sm" variant="outline" className="h-8 text-xs cursor-pointer" onClick={() => setConfirmPay(false)} disabled={paying}>
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
                          className="w-full h-9 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs rounded-xl cursor-pointer"
                        >
                          <CheckCircle2 className="size-4 mr-1.5" />
                          Pagar fatura ({formatBRL(selected.openAmount)})
                        </Button>
                      ))}
                  </section>

                  {/* Histórico */}
                  <section className="rounded-2xl p-4 border border-white/[0.08] bg-slate-900/40 space-y-3">
                    <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                      <Layers className="size-3.5 text-cyan-400" /> Evolução das faturas
                    </h4>
                    <div className="flex items-end gap-1.5 h-32">
                      {history.map((h) => {
                        const isCurrent = h.monthKey === viewMonth
                        const pct = (h.total / historyMax) * 100
                        return (
                          <button
                            key={h.monthKey}
                            type="button"
                            onClick={() => setViewMonth(h.monthKey)}
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
                    <p className="text-[10px] text-slate-500">Barras tracejadas = faturas futuras já comprometidas (parcelas).</p>
                  </section>

                  {/* Categorias */}
                  {selected.byCategory.length > 0 && (
                    <section className="rounded-2xl p-4 border border-white/[0.08] bg-slate-900/40 space-y-2.5">
                      <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                        <PieChart className="size-3.5 text-violet-400" /> Para onde foi o dinheiro
                      </h4>
                      {selected.byCategory.slice(0, 6).map((c) => {
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

                {/* Coluna direita: itens da fatura */}
                <section className="lg:col-span-7 rounded-2xl p-4 border border-white/[0.08] bg-slate-900/40 space-y-3 min-h-0">
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                      <ListChecks className="size-3.5 text-emerald-400" /> Lançamentos da fatura
                      <span className="text-slate-500 font-normal">({selected.itemsCount})</span>
                    </h4>
                    <div className="flex gap-1 rounded-xl bg-slate-950/60 p-1 border border-white/[0.06]">
                      {(
                        [
                          ['all', 'Todos'],
                          ['open', 'Em aberto'],
                          ['paid', 'Pagos'],
                        ] as const
                      ).map(([key, label]) => (
                        <button
                          key={key}
                          type="button"
                          onClick={() => setItemFilter(key)}
                          className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold cursor-pointer transition-colors ${
                            itemFilter === key ? 'bg-slate-700 text-white' : 'text-slate-400 hover:text-slate-200'
                          }`}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {visibleItems.length === 0 ? (
                    <p className="text-xs text-slate-500 text-center py-10">Nenhum lançamento para este filtro.</p>
                  ) : (
                    <ul className="divide-y divide-white/[0.05] max-h-[60dvh] overflow-y-auto pr-1">
                      {visibleItems.map((tx) => {
                        const isCredit = tx.type === 'income'
                        const st = ITEM_STATUS_LABEL[tx.status]
                        const mine = tx.userImpact?.[uid]
                        return (
                          <li key={tx.id}>
                            <button
                              type="button"
                              onClick={() => onEditTx?.(tx)}
                              className="w-full py-2.5 flex items-center gap-3 text-left hover:bg-slate-800/30 rounded-lg px-1.5 cursor-pointer"
                            >
                              <span
                                className="size-2 rounded-full shrink-0"
                                style={{ backgroundColor: CATEGORY_GROUPS[tx.category]?.color ?? '#64748b' }}
                              />
                              <div className="min-w-0 flex-1">
                                <p className="text-xs font-semibold text-slate-100 truncate">
                                  {tx.description}
                                  {tx.installmentsTotal && tx.installmentsTotal > 1 && (
                                    <span className="ml-1.5 text-[10px] text-cyan-300 font-medium">
                                      {tx.installmentNumber}/{tx.installmentsTotal}
                                    </span>
                                  )}
                                </p>
                                <p className="text-[10px] text-slate-500 truncate">
                                  {tx.category}
                                  {tx.subcategory ? ` › ${tx.subcategory}` : ''}
                                  {mine != null && !isCredit && Math.abs(mine - effectiveAmount(tx)) > 0.01 && (
                                    <> · minha parte {formatBRL(mine)}</>
                                  )}
                                </p>
                              </div>
                              <div className="text-right shrink-0 space-y-0.5">
                                <p className={`text-xs font-bold tabular-nums ${isCredit ? 'text-teal-300' : 'text-white'}`}>
                                  {isCredit ? '− ' : ''}
                                  {formatBRL(effectiveAmount(tx))}
                                </p>
                                <span className={`inline-block rounded-full border px-1.5 text-[9px] font-semibold ${st.tone}`}>{st.label}</span>
                              </div>
                            </button>
                          </li>
                        )
                      })}
                    </ul>
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

function InfoBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-slate-950/50 border border-white/[0.06] px-2.5 py-1.5">
      <p className="text-[10px] text-slate-400">{label}</p>
      <p className="text-xs font-bold text-white tabular-nums">{value}</p>
    </div>
  )
}
