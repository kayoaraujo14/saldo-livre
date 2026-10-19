import { useState, useMemo } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { effectiveAmount } from '@/lib/finance'
import { formatBRL, formatShortDate, monthLabel } from '@/lib/utils'
import { CATEGORY_GROUPS } from '@/types'
import {
  TrendingUp,
  PiggyBank,
  Wallet,
  Calculator,
  PieChart,
  Plus,
  Sparkles,
  Target,
  ArrowUpRight,
  Calendar,
  Layers,
} from 'lucide-react'

interface InvestmentsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onOpenNewTx: () => void
}

const INVESTMENT_SUB_COLORS: Record<string, string> = {
  'Renda Fixa (CDB, LCI, LCA)': '#10b981',
  'Tesouro Direto (Selic, IPCA)': '#06b6d4',
  'Ações (Bolsa de Valores)': '#8b5cf6',
  'Fundos Imobiliários (FIIs)': '#6366f1',
  'Criptomoedas & Bitcoin': '#f59e0b',
  'Previdência Privada': '#ec4899',
  'Outros Investimentos': '#94a3b8',
}

export function InvestmentsModal({ open, onOpenChange, onOpenNewTx }: InvestmentsModalProps) {
  const { summary, month, transactions, monthTransactions } = useFinance()

  const [activeTab, setActiveTab] = useState<'overview' | 'calculator' | 'rule503020' | 'history'>('overview')

  // Target storage (saved in localStorage per user)
  const [targetInput, setTargetInput] = useState<string>(() => {
    return localStorage.getItem('saldo_livre_investment_monthly_target') || '1000'
  })
  const [isEditingTarget, setIsEditingTarget] = useState(false)
  const monthlyTarget = Math.max(0, parseFloat(targetInput) || 0)

  const handleSaveTarget = () => {
    localStorage.setItem('saldo_livre_investment_monthly_target', String(monthlyTarget))
    setIsEditingTarget(false)
  }

  // Current month investment transactions
  const monthInvestments = useMemo(() => {
    return monthTransactions.filter((t) => t.category === 'Investimentos')
  }, [monthTransactions])

  // All-time investment transactions
  const allInvestments = useMemo(() => {
    return transactions
      .filter((t) => t.category === 'Investimentos')
      .sort((a, b) => b.dueDate.localeCompare(a.dueDate))
  }, [transactions])

  const [historyFilter, setHistoryFilter] = useState<string>('all')

  const filteredHistory = useMemo(() => {
    if (historyFilter === 'all') return allInvestments
    return allInvestments.filter((t) => (t.subcategory || 'Outros Investimentos') === historyFilter)
  }, [allInvestments, historyFilter])

  // Metrics
  const totalInvestedThisMonth = summary?.totalInvested || 0
  const totalInvestedAllTime = summary?.totalInvestedAccumulated || 0
  const monthlyIncome = summary?.income || 0
  const savingsRate = monthlyIncome > 0 ? (totalInvestedThisMonth / monthlyIncome) * 100 : 0
  const targetProgress = monthlyTarget > 0 ? Math.min(100, (totalInvestedThisMonth / monthlyTarget) * 100) : 0

  const subcategoryBreakdown = summary?.byInvestmentSubcategory || []

  // Compound Interest Calculator State
  const [initialAmount, setInitialAmount] = useState<number>(1000)
  const [monthlyContribution, setMonthlyContribution] = useState<number>(500)
  const [annualRate, setAnnualRate] = useState<number>(11.0) // 11% a.a.
  const [years, setYears] = useState<number>(10)

  const simulation = useMemo(() => {
    const rMonthly = Math.pow(1 + annualRate / 100, 1 / 12) - 1
    const totalMonths = years * 12
    let futureValue = initialAmount
    let totalInvestedBolso = initialAmount

    const yearlyData: { year: number; invested: number; total: number; interest: number }[] = []

    for (let m = 1; m <= totalMonths; m++) {
      futureValue = futureValue * (1 + rMonthly) + monthlyContribution
      totalInvestedBolso += monthlyContribution

      if (m % 12 === 0) {
        const y = m / 12
        yearlyData.push({
          year: y,
          invested: Math.round(totalInvestedBolso),
          total: Math.round(futureValue),
          interest: Math.round(futureValue - totalInvestedBolso),
        })
      }
    }

    const totalInterest = Math.max(0, futureValue - totalInvestedBolso)

    return {
      futureValue,
      totalInvestedBolso,
      totalInterest,
      yearlyData,
    }
  }, [initialAmount, monthlyContribution, annualRate, years])

  // 50/30/20 Rule computation
  const rule503020 = useMemo(() => {
    const income = summary?.income || 0
    const targetNeeds = income * 0.5
    const targetWants = income * 0.3
    const targetInvest = income * 0.2

    // Estimate needs from categories
    const needsCats = new Set(['Moradia', 'Alimentação', 'Saúde', 'Transporte', 'Educação', 'Financiamentos & Empréstimos'])
    let realNeeds = 0
    let realWants = 0
    let realInvest = totalInvestedThisMonth

    for (const item of (summary?.byCategory || [])) {
      if (item.category === 'Investimentos') continue
      if (needsCats.has(item.category)) {
        realNeeds += item.amount
      } else {
        realWants += item.amount
      }
    }

    return {
      income,
      targetNeeds,
      targetWants,
      targetInvest,
      realNeeds,
      realWants,
      realInvest,
      needsPct: income > 0 ? (realNeeds / income) * 100 : 0,
      wantsPct: income > 0 ? (realWants / income) * 100 : 0,
      investPct: income > 0 ? (realInvest / income) * 100 : 0,
    }
  }, [summary, totalInvestedThisMonth])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-5xl lg:max-w-6xl w-[96vw] max-h-[94dvh] overflow-y-auto bg-[#0a0f1d]/98 border border-white/[0.1] text-slate-100 p-5 sm:p-7 md:p-8 rounded-[2rem] shadow-2xl backdrop-blur-2xl">
        {/* Header com mais espaço e tipografia elegante */}
        <DialogHeader className="pb-4 border-b border-white/[0.08]">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-center gap-3.5 sm:gap-4">
              <div className="size-12 sm:size-14 rounded-2xl bg-gradient-to-tr from-purple-600 via-indigo-600 to-teal-400 text-white flex items-center justify-center shadow-xl shadow-purple-900/40 shrink-0">
                <TrendingUp className="size-6 sm:size-7 stroke-[2.2]" />
              </div>
              <div>
                <DialogTitle className="text-xl sm:text-2xl font-black text-white flex items-center gap-2.5 flex-wrap">
                  <span>Gestão de Investimentos & Patrimônio</span>
                  <Badge variant="outline" className="text-xs px-2.5 py-0.5 bg-purple-500/15 text-purple-300 border-purple-500/30 font-semibold">
                    {monthLabel(month)}
                  </Badge>
                </DialogTitle>
                <DialogDescription className="text-xs sm:text-sm text-slate-400 mt-1 leading-relaxed">
                  Painel analítico para acompanhar seus aportes mensais, diversificação por classes de ativos e simulador de rentabilidade futura.
                </DialogDescription>
              </div>
            </div>

            <Button
              onClick={() => {
                onOpenChange(false)
                onOpenNewTx()
              }}
              size="lg"
              className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs sm:text-sm h-10 sm:h-11 px-4 sm:px-5 gap-2 rounded-xl cursor-pointer shadow-lg shadow-emerald-950/70 shrink-0 transition-transform active:scale-95"
            >
              <Plus className="size-4 stroke-[2.5]" />
              <span>Novo Aporte</span>
            </Button>
          </div>
        </DialogHeader>

        {/* 4 KPI Cards Ampliados e Espaçosos */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 py-4 sm:py-5">
          {/* Card 1: Aportes deste Mês */}
          <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950 border border-white/[0.08] hover:border-emerald-500/40 transition-all shadow-md group">
            <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm mb-2 font-medium">
              <span>Aporte do Mês</span>
              <div className="size-8 rounded-lg bg-emerald-500/15 text-emerald-400 flex items-center justify-center">
                <Wallet className="size-4" />
              </div>
            </div>
            <div className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
              {formatBRL(totalInvestedThisMonth)}
            </div>
            <div className="text-xs text-emerald-400 font-semibold mt-2 flex items-center gap-1.5">
              <ArrowUpRight className="size-3.5" />
              <span>{savingsRate.toFixed(1)}% da renda do mês</span>
            </div>
          </div>

          {/* Card 2: Patrimônio Acumulado */}
          <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950 border border-white/[0.08] hover:border-purple-500/40 transition-all shadow-md group">
            <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm mb-2 font-medium">
              <span>Total Aportado (Histórico)</span>
              <div className="size-8 rounded-lg bg-purple-500/15 text-purple-400 flex items-center justify-center">
                <PiggyBank className="size-4" />
              </div>
            </div>
            <div className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
              {formatBRL(totalInvestedAllTime)}
            </div>
            <div className="text-xs text-purple-300 font-medium mt-2">
              {allInvestments.length} aporte(s) registrados na conta
            </div>
          </div>

          {/* Card 3: Meta Mensal */}
          <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950 border border-white/[0.08] hover:border-cyan-500/40 transition-all shadow-md group">
            <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm mb-1.5 font-medium">
              <span>Meta Mensal de Aporte</span>
              <div className="size-8 rounded-lg bg-cyan-500/15 text-cyan-400 flex items-center justify-center">
                <Target className="size-4" />
              </div>
            </div>
            {isEditingTarget ? (
              <div className="flex items-center gap-2 mt-2">
                <input
                  type="number"
                  value={targetInput}
                  onChange={(e) => setTargetInput(e.target.value)}
                  className="w-24 px-2 py-1 text-sm bg-slate-950 border border-cyan-500/40 rounded-lg text-white font-bold"
                />
                <button
                  type="button"
                  onClick={handleSaveTarget}
                  className="text-xs bg-cyan-600 hover:bg-cyan-500 text-white font-bold px-2.5 py-1 rounded-lg"
                >
                  Salvar
                </button>
              </div>
            ) : (
              <div
                onClick={() => setIsEditingTarget(true)}
                className="text-2xl sm:text-3xl font-extrabold text-white cursor-pointer hover:text-cyan-300 transition-colors tracking-tight"
                title="Clique para editar sua meta de investimento"
              >
                {formatBRL(monthlyTarget)}
              </div>
            )}
            <div className="mt-2.5">
              <div className="flex justify-between text-[11px] text-slate-400 mb-1">
                <span className="font-medium">{targetProgress.toFixed(0)}% da meta atingida</span>
              </div>
              <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden p-0.5">
                <div
                  className="h-full bg-gradient-to-r from-cyan-500 to-teal-400 rounded-full transition-all duration-500"
                  style={{ width: `${targetProgress}%` }}
                />
              </div>
            </div>
          </div>

          {/* Card 4: Classes de Ativos */}
          <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950 border border-white/[0.08] hover:border-amber-500/40 transition-all shadow-md group">
            <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm mb-2 font-medium">
              <span>Diversificação Ativa</span>
              <div className="size-8 rounded-lg bg-amber-500/15 text-amber-400 flex items-center justify-center">
                <Layers className="size-4" />
              </div>
            </div>
            <div className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
              {subcategoryBreakdown.length} classe(s)
            </div>
            <div className="text-xs text-amber-300 font-medium mt-2 truncate">
              {subcategoryBreakdown.length > 0
                ? subcategoryBreakdown.map((s) => s.subcategory.split(' ')[0]).join(', ')
                : 'Nenhum ativo alocado neste mês'}
            </div>
          </div>
        </div>

        {/* Tab Buttons Expandidos e Confortáveis */}
        <div className="flex items-center gap-2 border-b border-white/[0.08] pb-3 text-xs sm:text-sm overflow-x-auto scrollbar-none">
          <button
            type="button"
            onClick={() => setActiveTab('overview')}
            className={`px-4 sm:px-5 py-2.5 rounded-xl font-bold transition-all flex items-center gap-2 cursor-pointer shrink-0 ${
              activeTab === 'overview'
                ? 'bg-purple-600 text-white shadow-md shadow-purple-900/40'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
          >
            <PieChart className="size-4" />
            <span>Alocação do Mês</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('calculator')}
            className={`px-4 sm:px-5 py-2.5 rounded-xl font-bold transition-all flex items-center gap-2 cursor-pointer shrink-0 ${
              activeTab === 'calculator'
                ? 'bg-purple-600 text-white shadow-md shadow-purple-900/40'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
          >
            <Calculator className="size-4" />
            <span>Simulador Juros Compostos</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('rule503020')}
            className={`px-4 sm:px-5 py-2.5 rounded-xl font-bold transition-all flex items-center gap-2 cursor-pointer shrink-0 ${
              activeTab === 'rule503020'
                ? 'bg-purple-600 text-white shadow-md shadow-purple-900/40'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
          >
            <Sparkles className="size-4 text-amber-400" />
            <span>Regra 50 / 30 / 20</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('history')}
            className={`px-4 sm:px-5 py-2.5 rounded-xl font-bold transition-all flex items-center gap-2 cursor-pointer shrink-0 ${
              activeTab === 'history'
                ? 'bg-purple-600 text-white shadow-md shadow-purple-900/40'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
          >
            <Calendar className="size-4" />
            <span>Histórico de Aportes</span>
          </button>
        </div>

        {/* Tab 1: Overview com Layout Amplo */}
        {activeTab === 'overview' && (
          <div className="space-y-5 pt-4 animate-in fade-in duration-200">
            {subcategoryBreakdown.length === 0 ? (
              <div className="p-10 text-center rounded-3xl bg-slate-900/50 border border-dashed border-white/[0.1] space-y-4">
                <div className="size-14 rounded-2xl bg-purple-500/10 text-purple-400 flex items-center justify-center mx-auto">
                  <PiggyBank className="size-7" />
                </div>
                <h4 className="text-base font-bold text-white">Nenhum aporte registrado em {monthLabel(month)}</h4>
                <p className="text-xs sm:text-sm text-slate-400 max-w-lg mx-auto leading-relaxed">
                  Cadastre seus investimentos do mês utilizando a categoria <strong>Investimentos</strong> e escolha entre as subcategorias disponíveis (Renda Fixa, Ações, FIIs, Tesouro Selic, etc.).
                </p>
                <Button
                  onClick={() => {
                    onOpenChange(false)
                    onOpenNewTx()
                  }}
                  className="bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs sm:text-sm h-10 px-5 rounded-xl cursor-pointer shadow-lg"
                >
                  <Plus className="size-4 mr-1.5" />
                  Registrar Primeiro Aporte
                </Button>
              </div>
            ) : (
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
                {/* Breakdown List (7 colunas no desktop) */}
                <div className="lg:col-span-7 p-5 sm:p-6 rounded-3xl bg-slate-900/60 border border-white/[0.08] space-y-4 shadow-md">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-bold text-white tracking-tight flex items-center gap-2">
                      <Layers className="size-4 text-purple-400" />
                      <span>Alocação por Classe de Ativos</span>
                    </h4>
                    <span className="text-xs text-slate-400 font-medium">
                      Total: {formatBRL(totalInvestedThisMonth)}
                    </span>
                  </div>

                  <div className="space-y-3">
                    {subcategoryBreakdown.map((item, idx) => {
                      const color = INVESTMENT_SUB_COLORS[item.subcategory] || '#10b981'
                      return (
                        <div key={idx} className="p-3 sm:p-3.5 rounded-2xl bg-slate-950/70 border border-white/[0.06] hover:border-white/[0.12] transition-all">
                          <div className="flex items-center justify-between text-xs sm:text-sm mb-2">
                            <div className="flex items-center gap-2.5">
                              <span
                                className="size-3 rounded-full shrink-0 shadow-sm"
                                style={{ backgroundColor: color }}
                              />
                              <span className="font-semibold text-slate-200">{item.subcategory}</span>
                            </div>
                            <div className="flex items-center gap-3">
                              <span className="font-bold text-white">{formatBRL(item.amount)}</span>
                              <span className="text-xs text-slate-400 font-mono font-medium">
                                ({item.percentage.toFixed(1)}%)
                              </span>
                            </div>
                          </div>

                          <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden p-0.5">
                            <div
                              className="h-full rounded-full transition-all duration-500"
                              style={{ width: `${item.percentage}%`, backgroundColor: color }}
                            />
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>

                {/* Month Transactions List (5 colunas no desktop) */}
                <div className="lg:col-span-5 p-5 sm:p-6 rounded-3xl bg-slate-900/60 border border-white/[0.08] space-y-4 shadow-md">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-bold text-white tracking-tight flex items-center gap-2">
                      <Wallet className="size-4 text-emerald-400" />
                      <span>Lançamentos em {monthLabel(month)}</span>
                    </h4>
                    <span className="text-xs text-slate-400">
                      {monthInvestments.length} aporte(s)
                    </span>
                  </div>

                  <div className="space-y-2.5 max-h-80 overflow-y-auto pr-1 custom-scrollbar">
                    {monthInvestments.map((tx) => (
                      <div
                        key={tx.id}
                        className="p-3 rounded-2xl bg-slate-950/70 border border-white/[0.04] flex items-center justify-between gap-3 hover:border-purple-500/30 transition-all"
                      >
                        <div className="min-w-0">
                          <p className="text-xs sm:text-sm font-semibold text-white truncate">{tx.description}</p>
                          <div className="flex items-center gap-2 text-[11px] text-slate-400 mt-0.5">
                            <span>{formatShortDate(tx.dueDate)}</span>
                            <span>·</span>
                            <span className="text-purple-300 font-medium">{tx.subcategory || 'Investimentos'}</span>
                          </div>
                        </div>
                        <div className="text-right shrink-0">
                          <span className="text-xs sm:text-sm font-bold text-emerald-400">
                            {formatBRL(effectiveAmount(tx))}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Simulador de Juros Compostos Amplo e Confortável */}
        {activeTab === 'calculator' && (
          <div className="space-y-5 pt-4 animate-in fade-in duration-200">
            <div className="p-5 sm:p-6 rounded-3xl bg-slate-900/70 border border-white/[0.08] shadow-md space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-sm sm:text-base font-bold text-white tracking-tight flex items-center gap-2">
                    <Calculator className="size-5 text-purple-400" />
                    <span>Simulador de Futuro & Bola de Neve</span>
                  </h4>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Veja como a combinação de disciplina de aportes e juros compostos transforma pequenos valores em independência financeira.
                  </p>
                </div>
                <span className="text-xs text-purple-300 font-semibold hidden sm:inline">Juros compostos reinvestidos</span>
              </div>

              {/* Inputs Grid Confortável */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 pt-1">
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1.5">Aporte Inicial</label>
                  <div className="relative">
                    <span className="absolute left-3 top-2.5 text-sm text-slate-500 font-medium">R$</span>
                    <input
                      type="number"
                      value={initialAmount}
                      onChange={(e) => setInitialAmount(Math.max(0, parseFloat(e.target.value) || 0))}
                      className="w-full pl-9 pr-3 py-2 bg-slate-950/90 border border-white/[0.12] rounded-xl text-sm font-bold text-white focus:outline-none focus:border-purple-500/60"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1.5">Aporte Mensal</label>
                  <div className="relative">
                    <span className="absolute left-3 top-2.5 text-sm text-slate-500 font-medium">R$</span>
                    <input
                      type="number"
                      value={monthlyContribution}
                      onChange={(e) => setMonthlyContribution(Math.max(0, parseFloat(e.target.value) || 0))}
                      className="w-full pl-9 pr-3 py-2 bg-slate-950/90 border border-white/[0.12] rounded-xl text-sm font-bold text-white focus:outline-none focus:border-purple-500/60"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1.5">Taxa de Juros (% a.a.)</label>
                  <div className="relative">
                    <span className="absolute right-3 top-2.5 text-sm text-slate-500 font-medium">%</span>
                    <input
                      type="number"
                      step="0.25"
                      value={annualRate}
                      onChange={(e) => setAnnualRate(Math.max(0, parseFloat(e.target.value) || 0))}
                      className="w-full px-3 py-2 bg-slate-950/90 border border-white/[0.12] rounded-xl text-sm font-bold text-white focus:outline-none focus:border-purple-500/60"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1.5">Prazo de Acúmulo</label>
                  <div className="relative">
                    <span className="absolute right-3 top-2.5 text-sm text-slate-500 font-medium">anos</span>
                    <input
                      type="number"
                      min="1"
                      max="50"
                      value={years}
                      onChange={(e) => setYears(Math.max(1, parseInt(e.target.value) || 1))}
                      className="w-full px-3 py-2 bg-slate-950/90 border border-white/[0.12] rounded-xl text-sm font-bold text-white focus:outline-none focus:border-purple-500/60"
                    />
                  </div>
                </div>
              </div>

              {/* Presets de Mercado */}
              <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-white/[0.06] text-xs">
                <span className="text-slate-400 font-semibold mr-1">Benchmarks do Mercado:</span>
                {[
                  { label: 'CDI / Selic (10.75%)', rate: 10.75 },
                  { label: 'Tesouro IPCA+ (12%)', rate: 12.0 },
                  { label: 'Ações / FIIs (14%)', rate: 14.0 },
                  { label: 'Conservador (8%)', rate: 8.0 },
                ].map((bench, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => setAnnualRate(bench.rate)}
                    className="px-3 py-1 rounded-xl bg-slate-800/90 hover:bg-purple-900/40 hover:text-purple-300 text-slate-300 transition-colors cursor-pointer font-medium"
                  >
                    {bench.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Results Cards Espaçosos */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="p-5 sm:p-6 rounded-3xl bg-gradient-to-br from-emerald-950/50 via-slate-900/90 to-teal-950/30 border border-emerald-500/40 shadow-xl">
                <span className="text-xs sm:text-sm text-emerald-300 font-semibold block">Valor Bruto Final</span>
                <span className="text-2xl sm:text-3xl font-black text-white mt-1.5 block tracking-tight">
                  {formatBRL(simulation.futureValue)}
                </span>
                <span className="text-xs text-emerald-400 font-medium mt-1 block">
                  Patrimônio total acumulado em {years} anos
                </span>
              </div>

              <div className="p-5 sm:p-6 rounded-3xl bg-slate-900/70 border border-white/[0.08] shadow-md">
                <span className="text-xs sm:text-sm text-slate-400 font-semibold block">Seu Esforço (Total Aportado)</span>
                <span className="text-xl sm:text-2xl font-black text-white mt-1.5 block tracking-tight">
                  {formatBRL(simulation.totalInvestedBolso)}
                </span>
                <span className="text-xs text-slate-400 font-medium mt-1 block">
                  {((simulation.totalInvestedBolso / simulation.futureValue) * 100).toFixed(0)}% do valor final veio do seu bolso
                </span>
              </div>

              <div className="p-5 sm:p-6 rounded-3xl bg-gradient-to-br from-purple-950/50 via-slate-900/90 to-indigo-950/30 border border-purple-500/40 shadow-xl">
                <span className="text-xs sm:text-sm text-purple-300 font-semibold block">Ganho em Juros (Bola de Neve)</span>
                <span className="text-xl sm:text-2xl font-black text-purple-300 mt-1.5 block tracking-tight">
                  {formatBRL(simulation.totalInterest)}
                </span>
                <span className="text-xs text-purple-400 font-medium mt-1 block">
                  {((simulation.totalInterest / simulation.futureValue) * 100).toFixed(0)}% gerado pelo efeito dos juros compostos
                </span>
              </div>
            </div>

            {/* Timeline Bars com visualização ampla */}
            <div className="p-5 sm:p-6 rounded-3xl bg-slate-900/70 border border-white/[0.08] space-y-3.5 shadow-md">
              <h5 className="text-xs sm:text-sm font-bold text-white tracking-tight flex items-center justify-between">
                <span>Evolução do Patrimônio Ano a Ano</span>
                <div className="flex items-center gap-3 text-xs font-normal">
                  <span className="flex items-center gap-1.5 text-indigo-300">
                    <span className="size-2 rounded-full bg-indigo-500" /> Aportado
                  </span>
                  <span className="flex items-center gap-1.5 text-emerald-300">
                    <span className="size-2 rounded-full bg-emerald-400" /> Juros
                  </span>
                </div>
              </h5>
              <div className="space-y-2.5 max-h-64 overflow-y-auto pr-1 custom-scrollbar">
                {simulation.yearlyData.map((d) => {
                  const maxVal = simulation.futureValue || 1
                  const investedPct = (d.invested / maxVal) * 100
                  const interestPct = (d.interest / maxVal) * 100

                  return (
                    <div key={d.year} className="text-xs sm:text-sm p-2 rounded-xl bg-slate-950/50 border border-white/[0.04]">
                      <div className="flex justify-between text-slate-300 mb-1.5">
                        <span className="font-bold text-slate-300">Ano {d.year}</span>
                        <div className="flex items-center gap-3">
                          <span className="text-slate-400 text-xs">Aportado: {formatBRL(d.invested)}</span>
                          <span className="font-extrabold text-white">{formatBRL(d.total)}</span>
                        </div>
                      </div>
                      <div className="h-2.5 w-full bg-slate-800 rounded-full flex overflow-hidden">
                        <div
                          style={{ width: `${investedPct}%` }}
                          className="bg-indigo-500 h-full"
                          title="Aportado"
                        />
                        <div
                          style={{ width: `${interestPct}%` }}
                          className="bg-emerald-400 h-full"
                          title="Juros compostos"
                        />
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        )}

        {/* Tab 3: Regra 50/30/20 com Design Confortável */}
        {activeTab === 'rule503020' && (
          <div className="space-y-5 pt-4 animate-in fade-in duration-200">
            <div className="p-5 sm:p-6 rounded-3xl bg-slate-900/70 border border-white/[0.08] space-y-4 shadow-md">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div>
                  <h4 className="text-sm sm:text-base font-bold text-white tracking-tight flex items-center gap-2">
                    <Sparkles className="size-5 text-amber-400" />
                    <span>Diagnóstico Financeiro: Regra dos 50 / 30 / 20</span>
                  </h4>
                  <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                    Compara sua receita e despesas atuais com a regra mais recomendada por educadores financeiros globais.
                  </p>
                </div>
                <div className="text-left sm:text-right bg-slate-950/60 p-2.5 px-4 rounded-2xl border border-white/[0.06]">
                  <span className="text-[11px] text-slate-400 block font-medium">Renda Computada no Mês</span>
                  <span className="text-base font-bold text-emerald-400">{formatBRL(rule503020.income)}</span>
                </div>
              </div>

              {/* 3 Pillars Espaçosos */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                {/* 50% Necessidades */}
                <div className="p-4 sm:p-5 rounded-3xl bg-slate-950/80 border border-white/[0.08] space-y-3">
                  <div className="flex items-center justify-between text-xs sm:text-sm">
                    <span className="font-bold text-slate-200">50% Gastos Essenciais</span>
                    <span className="text-xs text-slate-400 font-mono">Teto: {formatBRL(rule503020.targetNeeds)}</span>
                  </div>
                  <div className="text-xl sm:text-2xl font-black text-white">
                    {formatBRL(rule503020.realNeeds)}
                  </div>
                  <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden p-0.5">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${rule503020.needsPct > 55 ? 'bg-rose-500' : 'bg-indigo-500'}`}
                      style={{ width: `${Math.min(100, rule503020.needsPct * 2)}%` }}
                    />
                  </div>
                  <span className="text-xs text-slate-400 block font-medium">
                    {rule503020.needsPct.toFixed(1)}% da sua renda atual (Moradia, Alimentação, Saúde)
                  </span>
                </div>

                {/* 30% Estilo de Vida */}
                <div className="p-4 sm:p-5 rounded-3xl bg-slate-950/80 border border-white/[0.08] space-y-3">
                  <div className="flex items-center justify-between text-xs sm:text-sm">
                    <span className="font-bold text-slate-200">30% Desejos & Lazer</span>
                    <span className="text-xs text-slate-400 font-mono">Teto: {formatBRL(rule503020.targetWants)}</span>
                  </div>
                  <div className="text-xl sm:text-2xl font-black text-white">
                    {formatBRL(rule503020.realWants)}
                  </div>
                  <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden p-0.5">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${rule503020.wantsPct > 35 ? 'bg-amber-500' : 'bg-cyan-500'}`}
                      style={{ width: `${Math.min(100, (rule503020.wantsPct / 30) * 100)}%` }}
                    />
                  </div>
                  <span className="text-xs text-slate-400 block font-medium">
                    {rule503020.wantsPct.toFixed(1)}% da sua renda atual (Lazer, Delivery, Compras)
                  </span>
                </div>

                {/* 20% Investimentos */}
                <div className="p-4 sm:p-5 rounded-3xl bg-slate-950/80 border border-purple-500/40 space-y-3 shadow-lg shadow-purple-950/20">
                  <div className="flex items-center justify-between text-xs sm:text-sm">
                    <span className="font-bold text-purple-300">20% Futuro / Investimentos</span>
                    <span className="text-xs text-purple-400/80 font-mono">Meta: {formatBRL(rule503020.targetInvest)}</span>
                  </div>
                  <div className="text-xl sm:text-2xl font-black text-white">
                    {formatBRL(rule503020.realInvest)}
                  </div>
                  <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden p-0.5">
                    <div
                      className="h-full bg-gradient-to-r from-emerald-500 to-teal-400 rounded-full transition-all duration-500"
                      style={{ width: `${Math.min(100, (rule503020.investPct / 20) * 100)}%` }}
                    />
                  </div>
                  <span className="text-xs text-emerald-400 block font-semibold">
                    {rule503020.investPct.toFixed(1)}% investidos (
                    {rule503020.investPct >= 20 ? 'Meta atingida! Parabéns!' : `faltam ${formatBRL(Math.max(0, rule503020.targetInvest - rule503020.realInvest))}`}
                    )
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 4: Histórico Amplo */}
        {activeTab === 'history' && (
          <div className="space-y-4 pt-4 animate-in fade-in duration-200">
            {/* Filter pills */}
            <div className="flex items-center gap-2 overflow-x-auto pb-1 text-xs sm:text-sm scrollbar-none">
              <span className="text-slate-400 font-semibold mr-1">Filtrar por:</span>
              <button
                type="button"
                onClick={() => setHistoryFilter('all')}
                className={`px-3.5 py-1.5 rounded-xl cursor-pointer transition-colors font-semibold ${
                  historyFilter === 'all'
                    ? 'bg-purple-600 text-white shadow-md'
                    : 'bg-slate-800/80 text-slate-300 hover:bg-slate-700'
                }`}
              >
                Todas ({allInvestments.length})
              </button>
              {CATEGORY_GROUPS['Investimentos']?.subcategories?.map((sub) => {
                const count = allInvestments.filter((t) => t.subcategory === sub).length
                if (count === 0) return null
                return (
                  <button
                    key={sub}
                    type="button"
                    onClick={() => setHistoryFilter(sub)}
                    className={`px-3.5 py-1.5 rounded-xl cursor-pointer transition-colors font-medium truncate max-w-[200px] ${
                      historyFilter === sub
                        ? 'bg-purple-600 text-white shadow-md'
                        : 'bg-slate-800/80 text-slate-300 hover:bg-slate-700'
                    }`}
                  >
                    {sub.split(' ')[0]} ({count})
                  </button>
                )
              })}
            </div>

            {/* Transactions list */}
            {filteredHistory.length === 0 ? (
              <div className="p-10 text-center text-slate-500 text-sm">
                Nenhum aporte encontrado com este filtro.
              </div>
            ) : (
              <div className="space-y-2.5 max-h-96 overflow-y-auto pr-1 custom-scrollbar">
                {filteredHistory.map((tx) => (
                  <div
                    key={tx.id}
                    className="p-3.5 sm:p-4 rounded-2xl bg-slate-900/60 border border-white/[0.06] flex items-center justify-between gap-4 hover:border-purple-500/30 transition-all shadow-sm"
                  >
                    <div className="flex items-center gap-3.5 min-w-0">
                      <div className="size-10 rounded-2xl bg-purple-500/15 border border-purple-500/30 text-purple-300 flex items-center justify-center shrink-0">
                        <TrendingUp className="size-5" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs sm:text-sm font-semibold text-white truncate">{tx.description}</p>
                        <div className="flex items-center gap-2 text-xs text-slate-400 mt-1 flex-wrap">
                          <span>{formatShortDate(tx.dueDate)}</span>
                          <span>·</span>
                          <span className="text-purple-300 font-medium">
                            {tx.subcategory || 'Outros Investimentos'}
                          </span>
                          <span>·</span>
                          <span className="capitalize">{tx.paymentMethod === 'pix' ? 'PIX' : tx.paymentMethod}</span>
                        </div>
                      </div>
                    </div>

                    <div className="text-right shrink-0">
                      <span className="text-xs sm:text-sm font-bold text-emerald-400">
                        {formatBRL(effectiveAmount(tx))}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
