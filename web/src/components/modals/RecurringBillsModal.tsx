import { useState, useMemo } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFinance } from '@/store/FinanceContext'
import {
  CATEGORY_GROUPS,
  EXPENSE_CATEGORIES,
  INCOME_CATEGORIES,
  getCategoryDetail,
  type Category,
  type EstimationRule,
  type PaymentMethod,
  type RecurringBill,
  type SplitType,
  type TransactionType,
  type LaunchTrigger,
} from '@/types'
import { ESTIMATION_LABELS, estimateRecurring } from '@/lib/finance'
import { formatBRL, currentMonthKey, shiftMonth, monthLabel } from '@/lib/utils'
import {
  RefreshCw,
  Plus,
  Trash2,
  Calendar,
  Split,
  Sparkles,
  Wallet,
  Check,
  Edit2,
  Clock,
  ArrowDownLeft,
  ArrowUpRight,
  CreditCard,
  Zap,
} from 'lucide-react'

interface RecurringBillsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function RecurringBillsModal({ open, onOpenChange }: RecurringBillsModalProps) {
  const {
    recurring,
    saveRecurring,
    deleteRecurring,
    me,
    partner,
    profile,
    updateProfile,
    cards,
    transactions,
    monthTransactions,
    month,
    launchBillForCurrentMonth,
    setStatus,
  } = useFinance()
  const [editingBill, setEditingBill] = useState<RecurringBill | null>(null)
  const [activeTab, setActiveTab] = useState<'all' | 'income' | 'expense'>('all')
  const [busyBillId, setBusyBillId] = useState<string | null>(null)

  // Base monthly income editor
  const [editingIncome, setEditingIncome] = useState(false)
  const [incomeVal, setIncomeVal] = useState(String(profile?.monthlyIncome || 0))

  const [billType, setBillType] = useState<TransactionType>('expense')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState<Category>('Moradia')
  const [subcategory, setSubcategory] = useState<string | null>(null)
  const [dueDay, setDueDay] = useState(10)
  const [defaultAmount, setDefaultAmount] = useState(150)
  const [estimationRule, setEstimationRule] = useState<EstimationRule>('fixed')
  const [splitType, setSplitType] = useState<SplitType>('mine')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('bill')
  const [cardId, setCardId] = useState<string | null>(null)
  const [launchTrigger, setLaunchTrigger] = useState<LaunchTrigger>('day_before')
  const [active, setActive] = useState(true)

  // Duração e Parcelas
  const [durationMode, setDurationMode] = useState<'indefinite' | 'installments'>('indefinite')
  const [startMonth, setStartMonth] = useState(currentMonthKey())
  const [installmentsCount, setInstallmentsCount] = useState(3)

  const handleSaveSalary = async () => {
    const num = parseFloat(incomeVal.replace(',', '.'))
    if (!isNaN(num) && num >= 0) {
      await updateProfile({ monthlyIncome: num })
      setEditingIncome(false)
    }
  }

  const handleOpenNew = () => {
    if (!me) return
    const initialCat: Category = 'Moradia'
    const initialSub = CATEGORY_GROUPS[initialCat]?.subcategories?.[0] || null
    setEditingBill({
      id: `new_${Date.now()}`,
      ownerUid: me.uid,
      description: '',
      category: initialCat,
      subcategory: initialSub,
      dueDay: 10,
      defaultAmount: 200,
      estimationRule: 'fixed',
      active: true,
      splitType: 'mine',
      paymentMethod: 'bill',
      type: 'expense',
      launchTrigger: 'day_before',
      cardId: null,
    })
    setBillType('expense')
    setDescription('')
    setCategory(initialCat)
    setSubcategory(initialSub)
    setDueDay(10)
    setDefaultAmount(200)
    setEstimationRule('fixed')
    setSplitType('mine')
    setPaymentMethod('bill')
    setCardId(null)
    setLaunchTrigger('day_before')
    setActive(true)
    setDurationMode('indefinite')
    setStartMonth(currentMonthKey())
    setInstallmentsCount(3)
  }

  const handleEdit = (bill: RecurringBill) => {
    setEditingBill(bill)
    const bType = bill.type ?? 'expense'
    setBillType(bType)
    setDescription(bill.description)
    setCategory(bill.category)
    setSubcategory(bill.subcategory || null)
    setDueDay(bill.dueDay)
    setDefaultAmount(bill.defaultAmount)
    setEstimationRule(bill.estimationRule)
    setSplitType(bill.splitType ?? 'mine')
    setPaymentMethod(bill.paymentMethod ?? (bill.type === 'income' ? 'pix' : bill.cardId ? 'credit_card' : 'bill'))
    setCardId(bill.cardId || null)
    setLaunchTrigger(bill.launchTrigger || 'day_before')
    setActive(bill.active)
    if (bill.endMonth) {
      setDurationMode('installments')
      setStartMonth(bill.startMonth || currentMonthKey())
      setInstallmentsCount(bill.installmentsCount || 3)
    } else {
      setDurationMode('indefinite')
      setStartMonth(currentMonthKey())
      setInstallmentsCount(3)
    }
  }

  const handleSave = async () => {
    if (!editingBill || !me || !description.trim()) return

    let endMonthVal: string | undefined = undefined
    let installmentsVal: number | undefined = undefined

    if (durationMode === 'installments') {
      installmentsVal = Math.max(1, Number(installmentsCount))
      endMonthVal = shiftMonth(startMonth, installmentsVal - 1)
    }

    const effectiveCat = category
    const toSave: RecurringBill = {
      ...editingBill,
      description: description.trim(),
      category: effectiveCat,
      subcategory: subcategory || null,
      dueDay: Number(dueDay),
      defaultAmount: Number(defaultAmount),
      estimationRule,
      splitType,
      paymentMethod,
      cardId: paymentMethod === 'credit_card' ? cardId : null,
      launchTrigger,
      active,
      type: billType,
      startMonth: durationMode === 'installments' ? startMonth : undefined,
      endMonth: endMonthVal,
      installmentsCount: installmentsVal,
    }

    await saveRecurring(toSave)
    setEditingBill(null)
  }

  const handleDelete = async (id: string) => {
    if (confirm('Tem certeza que deseja excluir esta assinatura/conta fixa?')) {
      await deleteRecurring(id)
      if (editingBill?.id === id) setEditingBill(null)
    }
  }

  // Média dinâmica calculada se estimationRule for avg_3_months
  const liveEstimate = useMemo(() => {
    if (!editingBill || estimationRule === 'fixed') return null
    const dummyBill: RecurringBill = {
      ...editingBill,
      defaultAmount: Number(defaultAmount) || 0,
      estimationRule,
    }
    return estimateRecurring(dummyBill, transactions, month)
  }, [editingBill, defaultAmount, estimationRule, transactions, month])

  const myBills = recurring
    .filter((b) => b.ownerUid === me?.uid)
    .filter((b) => {
      if (activeTab === 'income') return b.type === 'income'
      if (activeTab === 'expense') return b.type !== 'income'
      return true
    })

  // Métricas executivas de Comprometimento Fixo no mês selecionado
  const fixedMetrics = useMemo(() => {
    const activeMyBills = recurring.filter(
      (b) =>
        b.active &&
        b.ownerUid === me?.uid &&
        (!b.startMonth || month >= b.startMonth) &&
        (!b.endMonth || month <= b.endMonth),
    )
    const baseSalary = profile?.monthlyIncome || 0
    let extraFixedIncome = 0
    let fixedCardExpense = 0
    let fixedAccountExpense = 0

    for (const b of activeMyBills) {
      const { amount } = estimateRecurring(b, transactions, month)
      const myShare = b.splitType === 'split_50' ? amount / 2 : amount
      if (b.type === 'income') {
        extraFixedIncome += myShare
      } else {
        const isCard = b.paymentMethod === 'credit_card' || Boolean(b.cardId)
        if (isCard) fixedCardExpense += myShare
        else fixedAccountExpense += myShare
      }
    }

    const totalFixedIncome = baseSalary + extraFixedIncome
    const totalFixedExpense = fixedCardExpense + fixedAccountExpense
    const baseFreeBalance = totalFixedIncome - totalFixedExpense
    const commitmentPct =
      totalFixedIncome > 0 ? Math.min(100, Math.round((totalFixedExpense / totalFixedIncome) * 100)) : 0

    return {
      totalFixedIncome,
      totalFixedExpense,
      fixedCardExpense,
      fixedAccountExpense,
      baseFreeBalance,
      commitmentPct,
    }
  }, [recurring, me?.uid, month, profile?.monthlyIncome, transactions])

  const get503020Badge = (cat: Category) => {
    const detail = CATEGORY_GROUPS[cat]
    if (!detail) return null
    if (detail.defaultNature === 'need') {
      return (
        <Badge variant="outline" className="text-[10px] bg-blue-950/40 text-blue-300 border-blue-800/60">
          Necessidade (50%)
        </Badge>
      )
    }
    if (detail.defaultNature === 'want') {
      return (
        <Badge variant="outline" className="text-[10px] bg-pink-950/40 text-pink-300 border-pink-800/60">
          Desejo (30%)
        </Badge>
      )
    }
    if (detail.defaultNature === 'investment') {
      return (
        <Badge variant="outline" className="text-[10px] bg-purple-950/40 text-purple-300 border-purple-800/60">
          Investimento (20%)
        </Badge>
      )
    }
    return null
  }

  const getTriggerLabel = (trigger?: LaunchTrigger) => {
    switch (trigger) {
      case 'on_due_date':
        return 'No dia do vencimento'
      case 'day_after':
        return '1 dia após o vencimento'
      case 'first_day_of_month':
        return 'No 1º dia do mês'
      case 'day_before':
      default:
        return '1 dia antes do vencimento'
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.06]">
          <div className="flex items-center justify-between gap-3 flex-wrap min-w-0">
            <div className="flex items-center gap-2.5 min-w-0 flex-1">
              <div className="size-9 rounded-xl bg-gradient-to-tr from-amber-500/20 to-orange-500/20 border border-amber-500/30 text-amber-400 flex items-center justify-center shrink-0">
                <RefreshCw className="size-5" />
              </div>
              <div className="min-w-0 flex-1">
                <DialogTitle className="text-base sm:text-lg font-bold text-white truncate">
                  Assinaturas & Contas Fixas
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400 truncate">
                  Lançamento automatizado de assinaturas, contas fixas e renda mensal
                </DialogDescription>
              </div>
            </div>

            {!editingBill && (
              <Button
                size="sm"
                onClick={handleOpenNew}
                className="bg-amber-600 hover:bg-amber-500 text-white font-semibold text-xs h-8 px-3 gap-1.5 rounded-xl cursor-pointer shadow-sm shrink-0"
              >
                <Plus className="size-3.5" />
                <span>Nova Conta Fixa</span>
              </Button>
            )}
          </div>
        </DialogHeader>

        {/* 1. Base Salary Card */}
        <div className="p-4 rounded-2xl bg-gradient-to-r from-emerald-950/30 via-slate-900/60 to-emerald-950/20 border border-emerald-500/25 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-md">
          <div className="flex items-center gap-3">
            <div className="size-10 rounded-xl bg-emerald-500/15 text-emerald-400 border border-emerald-500/25 flex items-center justify-center shrink-0">
              <Wallet className="size-5" />
            </div>
            <div>
              <span className="text-[11px] font-semibold tracking-wider uppercase text-emerald-400/90 block">
                Renda Base / Salário Principal
              </span>
              {editingIncome ? (
                <div className="flex items-center gap-2 mt-1.5">
                  <Input
                    type="number"
                    step="0.01"
                    value={incomeVal}
                    onChange={(e) => setIncomeVal(e.target.value)}
                    className="h-8 w-32 text-xs bg-slate-950 border-slate-700 font-mono rounded-lg"
                    placeholder="0.00"
                  />
                  <Button
                    size="sm"
                    onClick={handleSaveSalary}
                    className="h-8 px-3 text-xs bg-emerald-600 hover:bg-emerald-500 text-white gap-1 rounded-lg font-semibold cursor-pointer"
                  >
                    <Check className="size-3.5" /> Salvar
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setEditingIncome(false)}
                    className="h-8 px-2 text-xs text-slate-400 hover:text-white cursor-pointer"
                  >
                    ✕
                  </Button>
                </div>
              ) : (
                <div className="flex items-baseline gap-2 mt-0.5">
                  <span className="text-lg sm:text-xl font-black text-emerald-400 tracking-tight font-mono">
                    {formatBRL(profile?.monthlyIncome || 0)}
                  </span>
                  <span className="text-xs text-slate-400">/ mês · Lançado no 1º dia</span>
                </div>
              )}
            </div>
          </div>

          {!editingIncome && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setIncomeVal(String(profile?.monthlyIncome || 0))
                setEditingIncome(true)
              }}
              className="text-xs h-8 px-3 gap-1.5 border-slate-700/80 bg-slate-900/60 hover:bg-slate-800 text-slate-200 rounded-xl cursor-pointer self-start sm:self-auto"
            >
              <Edit2 className="size-3" />
              <span>Alterar Renda</span>
            </Button>
          )}
        </div>

        {/* 1.5. Painel de Comprometimento Fixo Mensal */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800/80">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
              Renda Fixa Total
            </span>
            <span className="text-sm font-bold font-mono text-emerald-400 mt-0.5 block">
              +{formatBRL(fixedMetrics.totalFixedIncome)}
            </span>
            <span className="text-[10px] text-slate-500">Salário + Recorrentes</span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800/80">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
              Fixas (Extrato/Boleto)
            </span>
            <span className="text-sm font-bold font-mono text-amber-300 mt-0.5 block">
              −{formatBRL(fixedMetrics.fixedAccountExpense)}
            </span>
            <span className="text-[10px] text-slate-500">Pix, Débito & Boletos</span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800/80">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
              Assinaturas Cartão
            </span>
            <span className="text-sm font-bold font-mono text-violet-300 mt-0.5 block">
              −{formatBRL(fixedMetrics.fixedCardExpense)}
            </span>
            <span className="text-[10px] text-slate-500">Caem na Fatura</span>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800/80">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 block">
              Comprometimento Fixo
            </span>
            <span
              className={`text-sm font-bold font-mono mt-0.5 block ${
                fixedMetrics.commitmentPct <= 60 ? 'text-cyan-400' : 'text-rose-400'
              }`}
            >
              {fixedMetrics.commitmentPct}% da renda
            </span>
            <span className="text-[10px] text-slate-500">
              Livre base: {formatBRL(fixedMetrics.baseFreeBalance)}
            </span>
          </div>
        </div>

        {/* 2. Bill Editor Form */}
        {editingBill ? (
          <div className="space-y-4 py-2 border border-white/[0.08] rounded-2xl p-4 sm:p-5 bg-slate-950/70 animate-in fade-in">
            <div className="flex items-center justify-between gap-2 flex-wrap">
              <h4 className="text-sm font-bold text-white flex items-center gap-1.5">
                <Sparkles className="size-4 text-amber-400" />
                <span>{editingBill.id.startsWith('new_') ? 'Cadastrar Nova Conta Fixa / Assinatura' : `Editar ${editingBill.description}`}</span>
              </h4>

              {/* Toggle Income vs Expense */}
              <div className="flex p-1 rounded-xl bg-slate-900 border border-slate-800 text-xs gap-1">
                <button
                  type="button"
                  onClick={() => {
                    setBillType('expense')
                    if ((INCOME_CATEGORIES as readonly string[]).includes(category) || category === 'Receita') {
                      setCategory('Moradia')
                      setSubcategory(getCategoryDetail('Moradia')?.subcategories?.[0] || null)
                    }
                  }}
                  className={`py-1.5 px-3 rounded-lg font-semibold cursor-pointer transition-all flex items-center gap-1.5 ${
                    billType === 'expense'
                      ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30 shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <ArrowUpRight className="size-3.5 text-amber-400" />
                  <span>Despesa / Assinatura</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setBillType('income')
                    setCategory('Salário & Remuneração')
                    setSubcategory(getCategoryDetail('Salário & Remuneração')?.subcategories?.[0] || null)
                  }}
                  className={`py-1.5 px-3 rounded-lg font-semibold cursor-pointer transition-all flex items-center gap-1.5 ${
                    billType === 'income'
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <ArrowDownLeft className="size-3.5 text-emerald-400" />
                  <span>Renda Recorrente</span>
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div className="space-y-1.5 sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300">
                  {billType === 'income' ? 'Descrição da Renda (Ex: Freelance Fixo, Aluguel Recebido)' : 'Descrição da Conta / Assinatura (Ex: Netflix, Conta de Luz, Aluguel)'}
                </label>
                <Input
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder={billType === 'income' ? 'Ex: Consultoria Mensal' : 'Ex: Spotify ou Aluguel'}
                  className="h-10 text-xs sm:text-sm bg-slate-900 border-slate-700/80 rounded-xl"
                />
              </div>

              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-slate-300">Categoria</label>
                  {get503020Badge(category)}
                </div>
                <Select
                  value={category}
                  onValueChange={(v) => {
                    const newCat = v as Category
                    setCategory(newCat)
                    setSubcategory(getCategoryDetail(newCat)?.subcategories?.[0] || null)
                  }}
                >
                  <SelectTrigger className="h-10 bg-slate-900 border-slate-700/80 rounded-xl text-xs sm:text-sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(billType === 'income' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES).map((c) => (
                      <SelectItem key={c} value={c} className="text-xs">
                        {c}
                      </SelectItem>
                    ))}
                    {category === 'Receita' && (
                      <SelectItem value="Receita" className="text-xs">Receita (Legada)</SelectItem>
                    )}
                  </SelectContent>
                </Select>
              </div>

              {/* Subcategoria */}
              {(() => {
                const availableSubs = getCategoryDetail(category)?.subcategories || []
                return (
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-300">Subcategoria</label>
                    <Select
                      value={subcategory || ''}
                      onValueChange={(v) => setSubcategory(v || null)}
                    >
                      <SelectTrigger className="h-10 bg-slate-900 border-slate-700/80 rounded-xl text-xs sm:text-sm">
                        <SelectValue placeholder="Selecione a subcategoria..." />
                      </SelectTrigger>
                      <SelectContent>
                        {availableSubs.map((s) => (
                          <SelectItem key={s} value={s} className="text-xs">
                            {s}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )
              })()}

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">
                  {billType === 'income' ? 'Dia do Recebimento' : 'Dia de Vencimento'}
                </label>
                <Input
                  type="number"
                  min={1}
                  max={31}
                  value={dueDay}
                  onChange={(e) => setDueDay(Number(e.target.value))}
                  className="h-10 bg-slate-900 border-slate-700/80 rounded-xl font-mono text-xs sm:text-sm"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Valor Base Estimado (R$)</label>
                <Input
                  type="number"
                  step="0.01"
                  min={0}
                  value={defaultAmount}
                  onChange={(e) => setDefaultAmount(Number(e.target.value))}
                  className="h-10 bg-slate-900 border-slate-700/80 rounded-xl font-mono text-xs sm:text-sm"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Regra de Estimativa</label>
                <Select value={estimationRule} onValueChange={(v) => setEstimationRule(v as EstimationRule)}>
                  <SelectTrigger className="h-10 bg-slate-900 border-slate-700/80 rounded-xl text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="fixed">📌 Valor Fixo Padrão</SelectItem>
                    <SelectItem value="last_month">📅 Último Mês Pago</SelectItem>
                    <SelectItem value="avg_3_months">📊 Média dos Últimos 3 Meses</SelectItem>
                  </SelectContent>
                </Select>
                {liveEstimate && estimationRule !== 'fixed' && (
                  <p className="text-[11px] text-cyan-300 mt-1">
                    💡 Valor calculado atual: <strong className="font-mono">{formatBRL(liveEstimate.amount)}</strong>
                  </p>
                )}
              </div>

              {billType === 'expense' && (
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Divisão Padrão</label>
                  <Select value={splitType} onValueChange={(v) => setSplitType(v as SplitType)}>
                    <SelectTrigger className="h-10 bg-slate-900 border-slate-700/80 rounded-xl text-xs">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="mine">👤 100% Meu</SelectItem>
                      {partner && <SelectItem value="split_50">🤝 Dividido 50/50 com {partner.name}</SelectItem>}
                    </SelectContent>
                  </Select>
                </div>
              )}

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Forma de Pagamento</label>
                <Select
                  value={paymentMethod}
                  onValueChange={(v) => {
                    const pm = v as PaymentMethod
                    setPaymentMethod(pm)
                    if (pm === 'credit_card' && cards.length > 0 && !cardId) {
                      setCardId(cards[0].id)
                    }
                  }}
                >
                  <SelectTrigger className="h-10 bg-slate-900 border-slate-700/80 rounded-xl text-xs">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="pix">⚡ Pix</SelectItem>
                    <SelectItem value="bill">📄 Boleto Bancário</SelectItem>
                    <SelectItem value="debit">🏦 Débito em Conta</SelectItem>
                    <SelectItem value="credit_card">💳 Cartão de Crédito</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {/* Seletor de Cartão se Cartão de Crédito estiver selecionado */}
              {paymentMethod === 'credit_card' && (
                <div className="space-y-1.5 animate-in fade-in duration-200">
                  <label className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                    <CreditCard className="size-3.5 text-violet-400" />
                    <span>Vincular ao Cartão de Crédito</span>
                  </label>
                  {cards.length === 0 ? (
                    <p className="text-xs text-amber-400/90 py-2">
                      Nenhum cartão cadastrado. Cadastre um cartão no menu principal.
                    </p>
                  ) : (
                    <Select value={cardId || ''} onValueChange={(v) => setCardId(v || null)}>
                      <SelectTrigger className="h-10 bg-slate-900 border-slate-700/80 rounded-xl text-xs">
                        <SelectValue placeholder="Selecione o cartão..." />
                      </SelectTrigger>
                      <SelectContent>
                        {cards.map((c) => (
                          <SelectItem key={c.id} value={c.id} className="text-xs">
                            💳 {c.cardName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                </div>
              )}

              {/* Configuração de Gatilho de Automação */}
              <div className="sm:col-span-2 p-4 rounded-xl bg-gradient-to-r from-amber-950/20 via-slate-900/80 to-amber-950/10 border border-amber-500/25 space-y-2.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold text-amber-300 flex items-center gap-1.5">
                    <Zap className="size-4 text-amber-400" />
                    <span>Gatilho de Lançamento Automático</span>
                  </label>
                  <Badge variant="outline" className="text-[10px] bg-amber-500/15 text-amber-300 border-amber-500/30">
                    Automação Inteligente
                  </Badge>
                </div>

                <p className="text-[11px] text-slate-400">
                  Quando a data de disparo for atingida, o Saldo Livre lançará esta conta no seu extrato e exibirá uma notificação na tela de início.
                </p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => setLaunchTrigger('day_before')}
                    className={`p-2.5 rounded-xl text-xs font-medium text-left cursor-pointer transition-all border ${
                      launchTrigger === 'day_before'
                        ? 'bg-amber-500/20 text-amber-200 border-amber-500/40 shadow-sm'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    <div className="font-bold flex items-center gap-1 text-white">
                      <span>⚡ 1 dia antes</span>
                      <span className="text-[10px] text-amber-400 font-normal">(Recomendado)</span>
                    </div>
                    <span className="text-[10px] text-slate-400 block mt-0.5">Lança no dia {Math.max(1, dueDay - 1)} para antecipar o pagamento</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setLaunchTrigger('on_due_date')}
                    className={`p-2.5 rounded-xl text-xs font-medium text-left cursor-pointer transition-all border ${
                      launchTrigger === 'on_due_date'
                        ? 'bg-amber-500/20 text-amber-200 border-amber-500/40 shadow-sm'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    <div className="font-bold text-white">📅 No dia do vencimento</div>
                    <span className="text-[10px] text-slate-400 block mt-0.5">Lança pontualmente no dia {dueDay}</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setLaunchTrigger('day_after')}
                    className={`p-2.5 rounded-xl text-xs font-medium text-left cursor-pointer transition-all border ${
                      launchTrigger === 'day_after'
                        ? 'bg-amber-500/20 text-amber-200 border-amber-500/40 shadow-sm'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    <div className="font-bold text-white">⏰ 1 dia após o vencimento</div>
                    <span className="text-[10px] text-slate-400 block mt-0.5">Lança no dia {dueDay + 1} para checar cobrança</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setLaunchTrigger('first_day_of_month')}
                    className={`p-2.5 rounded-xl text-xs font-medium text-left cursor-pointer transition-all border ${
                      launchTrigger === 'first_day_of_month'
                        ? 'bg-amber-500/20 text-amber-200 border-amber-500/40 shadow-sm'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    <div className="font-bold text-white">🗓️ No 1º dia do mês</div>
                    <span className="text-[10px] text-slate-400 block mt-0.5">Lança assim que o mês se inicia</span>
                  </button>
                </div>
              </div>

              {/* Duração & Fim da Recorrência */}
              <div className="sm:col-span-2 p-4 rounded-xl bg-slate-900/80 border border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                    <Clock className="size-4 text-cyan-400" />
                    <span>Duração & Término da Recorrência</span>
                  </label>
                  <span className="text-[10px] text-slate-400">Projeção e cálculo seguro</span>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setDurationMode('indefinite')}
                    className={`py-2 px-3 rounded-xl text-xs font-semibold cursor-pointer transition-all border ${
                      durationMode === 'indefinite'
                        ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30 shadow-sm'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    🔄 Contínua (Indeterminada)
                  </button>

                  <button
                    type="button"
                    onClick={() => setDurationMode('installments')}
                    className={`py-2 px-3 rounded-xl text-xs font-semibold cursor-pointer transition-all border ${
                      durationMode === 'installments'
                        ? 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30 shadow-sm'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    📅 Prazo Fixo / Parcelada
                  </button>
                </div>

                {durationMode === 'installments' && (
                  <div className="pt-2 border-t border-slate-800/80 grid grid-cols-1 sm:grid-cols-2 gap-3 animate-in fade-in duration-200">
                    <div className="space-y-1.5">
                      <label className="text-[11px] font-semibold text-slate-300">Mês Inicial</label>
                      <Input
                        type="month"
                        value={startMonth}
                        onChange={(e) => setStartMonth(e.target.value)}
                        className="h-9 text-xs bg-slate-950 border-slate-700 rounded-lg"
                      />
                    </div>

                    <div className="space-y-1.5">
                      <label className="text-[11px] font-semibold text-slate-300">Quantidade de Meses / Parcelas</label>
                      <Select
                        value={String(installmentsCount)}
                        onValueChange={(v) => setInstallmentsCount(Number(v))}
                      >
                        <SelectTrigger className="h-9 text-xs bg-slate-950 border-slate-700 rounded-lg">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {[2, 3, 4, 5, 6, 8, 10, 12, 18, 24, 36].map((n) => (
                            <SelectItem key={n} value={String(n)} className="text-xs">
                              {n} parcelas ({n} meses)
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="sm:col-span-2 p-3 rounded-xl bg-cyan-950/30 border border-cyan-800/40 text-xs text-cyan-300 leading-relaxed">
                      💡 <strong>Previsão Ativa:</strong> Comprometerá o saldo a partir de <strong className="text-white">{monthLabel(startMonth)}</strong> até <strong className="text-white">{monthLabel(shiftMonth(startMonth, Math.max(1, installmentsCount) - 1))}</strong>. Após esse mês, não gerará despesas na projeção futura.
                    </div>
                  </div>
                )}
              </div>

              <div className="sm:col-span-2 flex items-center justify-between p-3.5 rounded-xl bg-slate-900/80 border border-slate-800">
                <span className="text-xs font-semibold text-slate-200">
                  Conta Fixa Ativa (lançar automaticamente conforme o vencimento)
                </span>
                <input
                  type="checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="size-5 accent-amber-500 rounded cursor-pointer"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-800">
              <Button variant="outline" size="sm" onClick={() => setEditingBill(null)} className="h-9 text-xs rounded-xl cursor-pointer">
                Cancelar
              </Button>
              <Button size="sm" onClick={handleSave} className="h-9 text-xs bg-amber-600 hover:bg-amber-500 text-white font-bold rounded-xl shadow-sm cursor-pointer">
                Salvar Conta Fixa
              </Button>
            </div>
          </div>
        ) : (
          /* 3. List of Recurring Bills */
          <div className="space-y-3 py-1">
            {/* Filter Tabs */}
            <div className="flex items-center gap-1.5 p-1 rounded-xl bg-slate-900/80 border border-slate-800">
              <button
                type="button"
                onClick={() => setActiveTab('all')}
                className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold cursor-pointer transition-all text-center ${
                  activeTab === 'all'
                    ? 'bg-slate-800 text-white shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                Todas ({recurring.filter(b => b.ownerUid === me?.uid).length})
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('income')}
                className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
                  activeTab === 'income'
                    ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <ArrowDownLeft className="size-3.5 text-emerald-400" />
                <span>Rendas ({recurring.filter(b => b.ownerUid === me?.uid && b.type === 'income').length})</span>
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('expense')}
                className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
                  activeTab === 'expense'
                    ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <ArrowUpRight className="size-3.5 text-amber-400" />
                <span>Despesas & Assinaturas ({recurring.filter(b => b.ownerUid === me?.uid && b.type !== 'income').length})</span>
              </button>
            </div>

            {/* List */}
            <div className="space-y-2.5 max-h-[50vh] overflow-y-auto pr-1">
              {myBills.length === 0 ? (
                <div className="text-center py-10 text-slate-400 text-xs rounded-2xl bg-slate-950/60 border border-white/[0.06] space-y-2">
                  <RefreshCw className="size-8 mx-auto opacity-30 text-slate-500" />
                  <p className="font-semibold text-slate-300">Nenhuma conta fixa cadastrada nesta aba.</p>
                  <p className="text-slate-500">Clique em "Nova Conta Fixa" acima para programar seus lançamentos automáticos.</p>
                </div>
              ) : (
                myBills.map((bill) => {
                  const isIncome = bill.type === 'income'
                  const linkedCard = bill.cardId ? cards.find((c) => c.id === bill.cardId) : null
                  const monthTx = (monthTransactions || []).find((t) => t.recurringBillId === bill.id)
                  const isBusy = busyBillId === bill.id
                  const isCardMethod = bill.paymentMethod === 'credit_card' || Boolean(bill.cardId)
                  const { amount: estimatedMonthVal } = estimateRecurring(bill, transactions, month)

                  return (
                    <div
                      key={bill.id}
                      className={`p-4 rounded-2xl border transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-md ${
                        !bill.active
                          ? 'opacity-60 border-slate-800 bg-slate-950/40'
                          : isIncome
                            ? 'border-emerald-500/30 bg-emerald-950/20 hover:border-emerald-500/50'
                            : 'border-white/[0.06] bg-slate-950/70 hover:border-slate-700'
                      }`}
                    >
                      <div className="space-y-1.5 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-bold text-slate-100 text-sm truncate">{bill.description}</span>
                          {isIncome ? (
                            <Badge variant="outline" className="text-[10px] bg-emerald-950/40 text-emerald-300 border-emerald-800">
                              💰 Renda Fixa
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-[10px] bg-slate-800 text-slate-300 border-slate-700">
                              {bill.category}
                            </Badge>
                          )}
                          {/* Status no mês selecionado (Checklist do Mês) */}
                          {monthTx ? (
                            monthTx.status === 'paid' ? (
                              <Badge
                                variant="outline"
                                className="text-[10px] bg-emerald-500/15 text-emerald-300 border-emerald-500/40 gap-1"
                              >
                                <Check className="size-2.5" />
                                {isIncome ? 'Recebido em ' : 'Pago em '}
                                {monthLabel(month)}
                              </Badge>
                            ) : monthTx.paymentMethod === 'credit_card' || monthTx.cardId ? (
                              <Badge
                                variant="outline"
                                className="text-[10px] bg-violet-500/15 text-violet-300 border-violet-500/40 gap-1"
                              >
                                <CreditCard className="size-2.5" />
                                Na Fatura ({monthLabel(month)})
                              </Badge>
                            ) : (
                              <Badge
                                variant="outline"
                                className="text-[10px] bg-amber-500/15 text-amber-300 border-amber-500/40 gap-1"
                              >
                                ⏳ Previsto no Extrato ({monthLabel(month)})
                              </Badge>
                            )
                          ) : bill.active ? (
                            <Badge
                              variant="outline"
                              className="text-[10px] bg-slate-800/80 text-slate-400 border-slate-700 gap-1"
                            >
                              ⚪ A lançar em {monthLabel(month)}
                            </Badge>
                          ) : null}
                          {!isIncome && get503020Badge(bill.category)}
                          {bill.subcategory && (
                            <Badge variant="outline" className="text-[10px] bg-slate-800/80 text-slate-300 border-slate-700 font-medium">
                              › {bill.subcategory}
                            </Badge>
                          )}
                          {linkedCard && (
                            <Badge variant="outline" className="text-[10px] bg-violet-950/50 text-violet-300 border-violet-800 gap-1">
                              <CreditCard className="size-2.5" /> {linkedCard.cardName}
                            </Badge>
                          )}
                          <Badge
                            variant="outline"
                            className="text-[10px] bg-amber-500/10 text-amber-300 border-amber-500/30 gap-1"
                          >
                            <Zap className="size-2.5" />
                            {getTriggerLabel(bill.launchTrigger)}
                          </Badge>
                          {bill.splitType === 'split_50' && (
                            <Badge variant="outline" className="text-[10px] bg-violet-950/50 text-violet-300 border-violet-800 gap-1">
                              <Split className="size-2.5" /> 50/50
                            </Badge>
                          )}
                          {bill.endMonth ? (
                            <Badge variant="outline" className="text-[10px] bg-cyan-950/40 text-cyan-300 border-cyan-800 gap-1">
                              <Calendar className="size-2.5" />
                              Até {monthLabel(bill.endMonth)} {bill.installmentsCount ? `(${bill.installmentsCount}x)` : ''}
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-[10px] bg-slate-800/60 text-slate-400 border-slate-700/60">
                              Contínua
                            </Badge>
                          )}
                        </div>

                        <div className="flex items-center gap-3 text-xs text-slate-400 flex-wrap">
                          <span className="flex items-center gap-1.5">
                            <Calendar className="size-3.5 text-slate-500" />
                            {isIncome ? `Recebe dia ${bill.dueDay}` : `Vence dia ${bill.dueDay}`}
                          </span>
                          <span>·</span>
                          <span className={`font-mono font-bold ${isIncome ? 'text-emerald-400' : 'text-slate-200'}`}>
                            {isIncome ? `+${formatBRL(estimatedMonthVal)}` : formatBRL(estimatedMonthVal)}
                          </span>
                          <span>·</span>
                          <span className="text-[11px] text-slate-500">
                            {ESTIMATION_LABELS[bill.estimationRule]}
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 self-end sm:self-center shrink-0 flex-wrap">
                        {/* Ações Rápidas de Efetivação no Mês */}
                        {bill.active && !monthTx && (
                          <Button
                            size="sm"
                            disabled={isBusy}
                            onClick={async () => {
                              setBusyBillId(bill.id)
                              try {
                                await launchBillForCurrentMonth(bill, { asPaid: !isCardMethod })
                              } finally {
                                setBusyBillId(null)
                              }
                            }}
                            className={`text-xs h-8 rounded-lg cursor-pointer gap-1 ${
                              isCardMethod
                                ? 'bg-violet-600 hover:bg-violet-500 text-white'
                                : 'bg-emerald-600 hover:bg-emerald-500 text-white'
                            }`}
                            title={
                              isCardMethod
                                ? 'Lançar na fatura do cartão neste mês'
                                : isIncome
                                  ? 'Confirmar recebimento neste mês'
                                  : 'Confirmar pagamento neste mês'
                            }
                          >
                            <Check className="size-3" />
                            <span>
                              {isCardMethod ? 'Lançar Fatura' : isIncome ? 'Já Recebi' : 'Já Paguei'}
                            </span>
                          </Button>
                        )}
                        {monthTx && monthTx.status === 'estimated' && (
                          <Button
                            size="sm"
                            disabled={isBusy}
                            onClick={async () => {
                              setBusyBillId(bill.id)
                              try {
                                await setStatus(monthTx, 'paid')
                              } finally {
                                setBusyBillId(null)
                              }
                            }}
                            className="text-xs h-8 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white cursor-pointer gap-1"
                            title="Confirmar pagamento/recebimento deste mês"
                          >
                            <Check className="size-3" />
                            <span>{isIncome ? 'Confirmar Recebido' : 'Confirmar Pago'}</span>
                          </Button>
                        )}
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleEdit(bill)}
                          className="text-xs h-8 rounded-lg border-slate-800 hover:text-white cursor-pointer"
                        >
                          <Edit2 className="size-3 mr-1" />
                          <span>Editar</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => handleDelete(bill.id)}
                          className="text-slate-400 hover:text-rose-400 h-8 w-8 rounded-lg cursor-pointer"
                          title="Excluir Conta Fixa"
                        >
                          <Trash2 className="size-3.5" />
                        </Button>
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </div>
        )}

        <DialogFooter className="pt-2 border-t border-white/[0.06]">
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} className="rounded-xl text-xs h-9 cursor-pointer">
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
