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
import { RefreshCw, Plus, Trash2, Calendar, Split, Sparkles, Wallet, Check, Edit2, Clock } from 'lucide-react'

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
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="p-2 rounded-xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
                <RefreshCw className="size-5" />
              </div>
              <div>
                <DialogTitle>Moldes de Entradas e Contas Recorrentes</DialogTitle>
                <DialogDescription>
                  Configure previsões automáticas de salários, rendas e contas mensais
                </DialogDescription>
              </div>
            </div>
            {!editingBill && (
              <Button size="sm" onClick={handleOpenNew} className="bg-amber-600 hover:bg-amber-500 text-white gap-1.5 text-xs">
                <Plus className="size-4" /> Novo Molde
              </Button>
            )}
          </div>
        </DialogHeader>

        {/* 1. Base Salary Card */}
        <div className="p-3.5 rounded-xl bg-emerald-950/20 border border-emerald-500/30 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-emerald-500/15 text-emerald-400 border border-emerald-500/20">
              <Wallet className="size-4" />
            </div>
            <div>
              <span className="text-xs text-slate-400 block font-medium">Renda Base / Salário Principal</span>
              {editingIncome ? (
                <div className="flex items-center gap-1.5 mt-1">
                  <Input
                    type="number"
                    step="0.01"
                    value={incomeVal}
                    onChange={(e) => setIncomeVal(e.target.value)}
                    className="h-7 w-28 text-xs bg-slate-900 border-slate-700 font-mono"
                  />
                  <Button size="sm" onClick={handleSaveSalary} className="h-7 px-2 text-xs bg-emerald-600 hover:bg-emerald-500 text-white gap-1">
                    <Check className="size-3" /> Salvar
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => setEditingIncome(false)} className="h-7 px-2 text-xs text-slate-400">
                    ✕
                  </Button>
                </div>
              ) : (
                <span className="text-sm font-bold text-emerald-400">
                  {formatBRL(profile?.monthlyIncome || 0)} / mês
                </span>
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
              className="text-xs h-7 gap-1 border-slate-700 text-slate-300"
            >
              <Edit2 className="size-3" /> Alterar Renda
            </Button>
          )}
        </div>

        {/* 2. Bill Editor Form */}
        {editingBill ? (
          <div className="space-y-4 py-2 border border-slate-800 rounded-xl p-4 bg-slate-900/50">
            <div className="flex items-center justify-between">
              <h4 className="text-sm font-semibold text-slate-200">
                {editingBill.id.startsWith('new_') ? 'Novo Molde Recorrente' : `Editar ${editingBill.description}`}
              </h4>

              {/* Toggle Income vs Expense */}
              <div className="flex p-0.5 rounded-lg bg-slate-800 border border-slate-700 text-xs">
                <button
                  type="button"
                  onClick={() => {
                    setBillType('expense')
                    if (category === 'Receita') setCategory('Moradia')
                  }}
                  className={`py-1 px-2.5 rounded-md font-medium cursor-pointer transition-all ${
                    billType === 'expense' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30' : 'text-slate-400'
                  }`}
                >
                  💸 Despesa
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setBillType('income')
                    setCategory('Receita')
                  }}
                  className={`py-1 px-2.5 rounded-md font-medium cursor-pointer transition-all ${
                    billType === 'income' ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' : 'text-slate-400'
                  }`}
                >
                  💰 Entrada / Receita
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1.5 sm:col-span-2">
                <label className="text-xs font-medium text-slate-300">
                  {billType === 'income' ? 'Descrição da Entrada (Ex: Freelance Fixo, Rendimento)' : 'Descrição da Conta (Ex: Conta de Luz, Aluguel)'}
                </label>
                <Input
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder={billType === 'income' ? 'Nome da receita' : 'Nome da despesa'}
                  className="bg-slate-900 border-slate-700"
                />
              </div>

              {billType === 'expense' && (
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-300">Categoria</label>
                  <Select value={category} onValueChange={(v) => setCategory(v as Category)}>
                    <SelectTrigger className="bg-slate-900 border-slate-700">
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
                </div>
              )}

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">
                  {billType === 'income' ? 'Dia do Recebimento' : 'Dia de Vencimento'}
                </label>
                <Input
                  type="number"
                  min={1}
                  max={31}
                  value={dueDay}
                  onChange={(e) => setDueDay(Number(e.target.value))}
                  className="bg-slate-900 border-slate-700"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Valor Base (R$)</label>
                <Input
                  type="number"
                  step="0.01"
                  min={0}
                  value={defaultAmount}
                  onChange={(e) => setDefaultAmount(Number(e.target.value))}
                  className="bg-slate-900 border-slate-700 font-mono"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Regra de Estimativa</label>
                <Select value={estimationRule} onValueChange={(v) => setEstimationRule(v as EstimationRule)}>
                  <SelectTrigger className="bg-slate-900 border-slate-700">
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
                  <label className="text-xs font-medium text-slate-300">Divisão Padrão</label>
                  <Select value={splitType} onValueChange={(v) => setSplitType(v as SplitType)}>
                    <SelectTrigger className="bg-slate-900 border-slate-700">
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
                <label className="text-xs font-medium text-slate-300">Meio de Pagamento / Crédito</label>
                <Select value={paymentMethod} onValueChange={(v) => setPaymentMethod(v as PaymentMethod)}>
                  <SelectTrigger className="bg-slate-900 border-slate-700">
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

              {/* Feature 1: Duração & Fim da Recorrência */}
              <div className="sm:col-span-2 p-3 rounded-xl bg-slate-900 border border-slate-800 space-y-2.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-semibold text-slate-200 flex items-center gap-1.5">
                    <Clock className="size-3.5 text-cyan-400" />
                    <span>Duração & Término da Recorrência</span>
                  </label>
                  <span className="text-[10px] text-slate-400">Previsão e cálculo até a data X</span>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setDurationMode('indefinite')}
                    className={`py-1.5 px-2 rounded-lg text-xs font-medium cursor-pointer transition-all border ${
                      durationMode === 'indefinite'
                        ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    🔄 Contínua (Indeterminada)
                  </button>

                  <button
                    type="button"
                    onClick={() => setDurationMode('installments')}
                    className={`py-1.5 px-2 rounded-lg text-xs font-medium cursor-pointer transition-all border ${
                      durationMode === 'installments'
                        ? 'bg-cyan-500/15 text-cyan-300 border-cyan-500/30'
                        : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                    }`}
                  >
                    📅 Prazo Fixo / Parcelada
                  </button>
                </div>

                {durationMode === 'installments' && (
                  <div className="pt-2 border-t border-slate-800/80 grid grid-cols-1 sm:grid-cols-2 gap-2 animate-in fade-in duration-200">
                    <div className="space-y-1">
                      <label className="text-[11px] text-slate-400">Mês Inicial</label>
                      <Input
                        type="month"
                        value={startMonth}
                        onChange={(e) => setStartMonth(e.target.value)}
                        className="h-8 text-xs bg-slate-950 border-slate-700"
                      />
                    </div>

                    <div className="space-y-1">
                      <label className="text-[11px] text-slate-400">Quantidade de Meses / Parcelas</label>
                      <Select
                        value={String(installmentsCount)}
                        onValueChange={(v) => setInstallmentsCount(Number(v))}
                      >
                        <SelectTrigger className="h-8 text-xs bg-slate-950 border-slate-700">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {[2, 3, 4, 5, 6, 8, 10, 12, 18, 24, 36].map((n) => (
                            <SelectItem key={n} value={String(n)}>
                              {n} parcelas ({n} meses)
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="sm:col-span-2 p-2 rounded-lg bg-cyan-950/40 border border-cyan-800/40 text-[11px] text-cyan-300">
                      💡 <strong>Previsão Ativa:</strong> Comprometerá o saldo a partir de <strong>{monthLabel(startMonth)}</strong> até <strong>{monthLabel(shiftMonth(startMonth, Math.max(1, installmentsCount) - 1))}</strong>. Após esse mês, não gerará despesas na projeção futura.
                    </div>
                  </div>
                )}
              </div>

              <div className="sm:col-span-2 flex items-center justify-between p-3 rounded-lg bg-slate-800/60 border border-slate-700/60">
                <span className="text-xs text-slate-200">Molde Ativo (gerar automaticamente nos meses futuros)</span>
                <input
                  type="checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="size-4 accent-amber-500 rounded cursor-pointer"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" size="sm" onClick={() => setEditingBill(null)}>
                Cancelar
              </Button>
              <Button size="sm" onClick={handleSave} className="bg-amber-600 hover:bg-amber-500 text-white">
                Salvar Molde
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-3 py-1">
            {/* Filter Tabs */}
            <div className="flex items-center gap-1.5 border-b border-slate-800 pb-2">
              <button
                type="button"
                onClick={() => setActiveTab('all')}
                className={`py-1 px-2.5 rounded-lg text-xs font-medium cursor-pointer transition-all ${
                  activeTab === 'all' ? 'bg-slate-800 text-white border border-slate-700' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                Todos ({recurring.filter(b => b.ownerUid === me?.uid).length})
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('income')}
                className={`py-1 px-2.5 rounded-lg text-xs font-medium cursor-pointer transition-all flex items-center gap-1 ${
                  activeTab === 'income' ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                💰 Entradas ({recurring.filter(b => b.ownerUid === me?.uid && b.type === 'income').length})
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('expense')}
                className={`py-1 px-2.5 rounded-lg text-xs font-medium cursor-pointer transition-all flex items-center gap-1 ${
                  activeTab === 'expense' ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                💸 Despesas ({recurring.filter(b => b.ownerUid === me?.uid && b.type !== 'income').length})
              </button>
            </div>

            {/* List */}
            <div className="space-y-2.5 max-h-[48vh] overflow-y-auto pr-1">
              {myBills.length === 0 ? (
                <div className="text-center py-8 text-slate-400 text-sm">
                  Nenhum molde nesta categoria. Clique em "Novo Molde" para cadastrar.
                </div>
              ) : (
                myBills.map((bill) => {
                  const isIncome = bill.type === 'income'
                  return (
                    <div
                      key={bill.id}
                      className={`p-3.5 rounded-xl border transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
                        isIncome
                          ? 'border-emerald-500/30 bg-emerald-950/15 hover:border-emerald-500/50'
                          : 'border-slate-800 bg-slate-900/60 hover:border-slate-700'
                      }`}
                    >
                      <div className="space-y-1">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-semibold text-slate-100 text-sm">{bill.description}</span>
                          {isIncome ? (
                            <Badge variant="outline" className="text-[10px] bg-emerald-950/40 text-emerald-300 border-emerald-800">
                              💰 Entrada
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="text-[10px] bg-slate-800 text-slate-300">
                              {bill.category}
                            </Badge>
                          )}
                          <Badge
                            variant="outline"
                            className="text-[10px] bg-amber-950/40 text-amber-300 border-amber-800/60 gap-1"
                          >
                            <Sparkles className="size-3" />
                            {ESTIMATION_LABELS[bill.estimationRule]}
                          </Badge>
                          {bill.splitType === 'split_50' && (
                            <Badge variant="outline" className="text-[10px] bg-violet-950/50 text-violet-300 border-violet-800 gap-1">
                              <Split className="size-3" /> 50/50
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
                          <span className="flex items-center gap-1">
                            <Calendar className="size-3.5 text-slate-500" />
                            {isIncome ? `Entra todo dia ${bill.dueDay}` : `Vence todo dia ${bill.dueDay}`}
                          </span>
                          <span>·</span>
                          <span className={`font-semibold ${isIncome ? 'text-emerald-400' : 'text-slate-300'}`}>
                            {isIncome ? `+${formatBRL(bill.defaultAmount)}` : formatBRL(bill.defaultAmount)}
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 self-end sm:self-center">
                        <Button variant="outline" size="sm" onClick={() => handleEdit(bill)} className="text-xs h-7 px-2.5">
                          Editar
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => handleDelete(bill.id)}
                          className="text-slate-400 hover:text-rose-400 h-7 w-7"
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

        <DialogFooter>
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
