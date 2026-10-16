import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFinance } from '@/store/FinanceContext'
import { CATEGORIES, type Category, type EstimationRule, type PaymentMethod, type RecurringBill, type SplitType, type TransactionType } from '@/types'
import { ESTIMATION_LABELS } from '@/lib/finance'
import { formatBRL, currentMonthKey, shiftMonth, monthLabel } from '@/lib/utils'
import { RefreshCw, Plus, Trash2, Calendar, Split, Sparkles, Wallet, Check, Edit2, Clock, ArrowDownLeft, ArrowUpRight } from 'lucide-react'

interface RecurringBillsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function RecurringBillsModal({ open, onOpenChange }: RecurringBillsModalProps) {
  const { recurring, saveRecurring, deleteRecurring, me, partner, profile, updateProfile } = useFinance()
  const [editingBill, setEditingBill] = useState<RecurringBill | null>(null)
  const [activeTab, setActiveTab] = useState<'all' | 'income' | 'expense'>('all')

  // Base monthly income editor
  const [editingIncome, setEditingIncome] = useState(false)
  const [incomeVal, setIncomeVal] = useState(String(profile?.monthlyIncome || 0))

  const [billType, setBillType] = useState<TransactionType>('expense')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState<Category>('Moradia')
  const [dueDay, setDueDay] = useState(10)
  const [defaultAmount, setDefaultAmount] = useState(150)
  const [estimationRule, setEstimationRule] = useState<EstimationRule>('fixed')
  const [splitType, setSplitType] = useState<SplitType>('mine')
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('bill')
  const [active, setActive] = useState(true)

  // Feature 1: Duração e Parcelas
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
    setEditingBill({
      id: `new_${Date.now()}`,
      ownerUid: me.uid,
      description: '',
      category: 'Moradia',
      dueDay: 10,
      defaultAmount: 200,
      estimationRule: 'fixed',
      active: true,
      splitType: 'mine',
      paymentMethod: 'bill',
      type: 'expense',
    })
    setBillType('expense')
    setDescription('')
    setCategory('Moradia')
    setDueDay(10)
    setDefaultAmount(200)
    setEstimationRule('fixed')
    setSplitType('mine')
    setPaymentMethod('bill')
    setActive(true)
    setDurationMode('indefinite')
    setStartMonth(currentMonthKey())
    setInstallmentsCount(3)
  }

  const handleEdit = (bill: RecurringBill) => {
    setEditingBill(bill)
    setBillType(bill.type ?? 'expense')
    setDescription(bill.description)
    setCategory(bill.category)
    setDueDay(bill.dueDay)
    setDefaultAmount(bill.defaultAmount)
    setEstimationRule(bill.estimationRule)
    setSplitType(bill.splitType ?? 'mine')
    setPaymentMethod(bill.paymentMethod ?? (bill.type === 'income' ? 'pix' : 'bill'))
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

    const toSave: RecurringBill = {
      ...editingBill,
      description: description.trim(),
      category: billType === 'income' ? 'Receita' : category,
      dueDay: Number(dueDay),
      defaultAmount: Number(defaultAmount),
      estimationRule,
      splitType,
      paymentMethod,
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
    if (confirm('Tem certeza que deseja excluir este molde recorrente?')) {
      await deleteRecurring(id)
      if (editingBill?.id === id) setEditingBill(null)
    }
  }

  const myBills = recurring
    .filter((b) => b.ownerUid === me?.uid)
    .filter((b) => {
      if (activeTab === 'income') return b.type === 'income'
      if (activeTab === 'expense') return b.type !== 'income'
      return true
    })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.06]">
          <div className="flex items-center justify-between gap-3 flex-wrap">
            <div className="flex items-center gap-2.5">
              <div className="size-9 rounded-xl bg-gradient-to-tr from-amber-500/20 to-orange-500/20 border border-amber-500/30 text-amber-400 flex items-center justify-center shrink-0">
                <RefreshCw className="size-5" />
              </div>
              <div>
                <DialogTitle className="text-base sm:text-lg font-bold text-white">
                  Moldes & Contas Recorrentes
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400">
                  Previsões automáticas de salários, rendas e contas mensais
                </DialogDescription>
              </div>
            </div>

            {!editingBill && (
              <Button
                size="sm"
                onClick={handleOpenNew}
                className="bg-amber-600 hover:bg-amber-500 text-white font-semibold text-xs h-8 px-3 gap-1.5 rounded-xl cursor-pointer shadow-sm"
              >
                <Plus className="size-3.5" />
                <span>Novo Molde</span>
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
                    className="h-8 px-3 text-xs bg-emerald-600 hover:bg-emerald-500 text-white gap-1 rounded-lg font-semibold"
                  >
                    <Check className="size-3.5" /> Salvar
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setEditingIncome(false)}
                    className="h-8 px-2 text-xs text-slate-400 hover:text-white"
                  >
                    ✕
                  </Button>
                </div>
              ) : (
                <div className="flex items-baseline gap-2 mt-0.5">
                  <span className="text-lg sm:text-xl font-black text-emerald-400 tracking-tight font-mono">
                    {formatBRL(profile?.monthlyIncome || 0)}
                  </span>
                  <span className="text-xs text-slate-400">/ mês</span>
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

        {/* 2. Bill Editor Form */}
        {editingBill ? (
          <div className="space-y-4 py-2 border border-white/[0.08] rounded-2xl p-4 sm:p-5 bg-slate-950/70 animate-in fade-in">
            <div className="flex items-center justify-between gap-2 flex-wrap">
              <h4 className="text-sm font-bold text-white flex items-center gap-1.5">
                <Sparkles className="size-4 text-amber-400" />
                <span>{editingBill.id.startsWith('new_') ? 'Cadastrar Novo Molde' : `Editar ${editingBill.description}`}</span>
              </h4>

              {/* Toggle Income vs Expense */}
              <div className="flex p-1 rounded-xl bg-slate-900 border border-slate-800 text-xs gap-1">
                <button
                  type="button"
                  onClick={() => {
                    setBillType('expense')
                    if (category === 'Receita') setCategory('Moradia')
                  }}
                  className={`py-1.5 px-3 rounded-lg font-semibold cursor-pointer transition-all flex items-center gap-1.5 ${
                    billType === 'expense'
                      ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30 shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <ArrowUpRight className="size-3.5 text-amber-400" />
                  <span>Despesa</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setBillType('income')
                    setCategory('Receita')
                  }}
                  className={`py-1.5 px-3 rounded-lg font-semibold cursor-pointer transition-all flex items-center gap-1.5 ${
                    billType === 'income'
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <ArrowDownLeft className="size-3.5 text-emerald-400" />
                  <span>Entrada</span>
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div className="space-y-1.5 sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300">
                  {billType === 'income' ? 'Descrição da Entrada (Ex: Freelance Fixo, Rendimento)' : 'Descrição da Conta (Ex: Conta de Luz, Aluguel)'}
                </label>
                <Input
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder={billType === 'income' ? 'Ex: Consultoria Mensal' : 'Ex: Internet Fibra'}
                  className="h-10 text-xs sm:text-sm bg-slate-900 border-slate-700/80 rounded-xl"
                />
              </div>

              {billType === 'expense' && (
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Categoria</label>
                  <Select value={category} onValueChange={(v) => setCategory(v as Category)}>
                    <SelectTrigger className="h-10 bg-slate-900 border-slate-700/80 rounded-xl text-xs sm:text-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {CATEGORIES.filter((c) => c !== 'Receita').map((c) => (
                        <SelectItem key={c} value={c} className="text-xs">
                          {c}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

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
                <label className="text-xs font-semibold text-slate-300">Valor Base (R$)</label>
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
                <label className="text-xs font-semibold text-slate-300">Meio de Pagamento / Cobrança</label>
                <Select value={paymentMethod} onValueChange={(v) => setPaymentMethod(v as PaymentMethod)}>
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
                  Molde Ativo (gerar automaticamente nos meses futuros)
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
              <Button variant="outline" size="sm" onClick={() => setEditingBill(null)} className="h-9 text-xs rounded-xl">
                Cancelar
              </Button>
              <Button size="sm" onClick={handleSave} className="h-9 text-xs bg-amber-600 hover:bg-amber-500 text-white font-bold rounded-xl shadow-sm">
                Salvar Molde
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
                Todos ({recurring.filter(b => b.ownerUid === me?.uid).length})
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
                <span>Entradas ({recurring.filter(b => b.ownerUid === me?.uid && b.type === 'income').length})</span>
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
                <span>Despesas ({recurring.filter(b => b.ownerUid === me?.uid && b.type !== 'income').length})</span>
              </button>
            </div>

            {/* List */}
            <div className="space-y-2.5 max-h-[50vh] overflow-y-auto pr-1">
              {myBills.length === 0 ? (
                <div className="text-center py-10 text-slate-400 text-xs rounded-2xl bg-slate-950/60 border border-white/[0.06] space-y-2">
                  <RefreshCw className="size-8 mx-auto opacity-30 text-slate-500" />
                  <p className="font-semibold text-slate-300">Nenhum molde nesta categoria.</p>
                  <p className="text-slate-500">Clique em "Novo Molde" acima para automatizar seus lançamentos.</p>
                </div>
              ) : (
                myBills.map((bill) => {
                  const isIncome = bill.type === 'income'
                  return (
                    <div
                      key={bill.id}
                      className={`p-4 rounded-2xl border transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-md ${
                        isIncome
                          ? 'border-emerald-500/30 bg-emerald-950/20 hover:border-emerald-500/50'
                          : 'border-white/[0.06] bg-slate-950/70 hover:border-slate-700'
                      }`}
                    >
                      <div className="space-y-1.5">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-bold text-slate-100 text-sm">{bill.description}</span>
                          {isIncome ? (
                            <Badge variant="outline" className="text-[10px] bg-emerald-950/40 text-emerald-300 border-emerald-800">
                              💰 Entrada
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-[10px] bg-slate-800 text-slate-300 border-slate-700">
                              {bill.category}
                            </Badge>
                          )}
                          <Badge
                            variant="outline"
                            className="text-[10px] bg-amber-950/40 text-amber-300 border-amber-800/60 gap-1"
                          >
                            <Sparkles className="size-2.5" />
                            {ESTIMATION_LABELS[bill.estimationRule]}
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

                        <div className="flex items-center gap-3 text-xs text-slate-400">
                          <span className="flex items-center gap-1.5">
                            <Calendar className="size-3.5 text-slate-500" />
                            {isIncome ? `Recebe dia ${bill.dueDay}` : `Vence dia ${bill.dueDay}`}
                          </span>
                          <span>·</span>
                          <span className={`font-mono font-bold ${isIncome ? 'text-emerald-400' : 'text-slate-200'}`}>
                            {isIncome ? `+${formatBRL(bill.defaultAmount)}` : formatBRL(bill.defaultAmount)}
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleEdit(bill)}
                          className="text-xs h-8 rounded-lg border-slate-800 hover:text-white"
                        >
                          <Edit2 className="size-3 mr-1" />
                          <span>Editar</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => handleDelete(bill.id)}
                          className="text-slate-400 hover:text-rose-400 h-8 w-8 rounded-lg cursor-pointer"
                          title="Excluir Molde"
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
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} className="rounded-xl text-xs h-9">
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
