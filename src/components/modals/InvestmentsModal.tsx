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
    const needsCats = new Set(['Moradia', 'Alimentação', 'Saúde', 'Transporte', 'Educação'])
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
      <DialogContent className="max-w-4xl max-h-[92dvh] overflow-y-auto bg-[#0a0f1d]/98 border border-white/[0.1] text-slate-100 p-4 sm:p-6 rounded-3xl shadow-2xl backdrop-blur-2xl">
        <DialogHeader className="pb-3 border-b border-white/[0.08]">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="size-11 rounded-2xl bg-gradient-to-tr from-purple-600 to-indigo-500 text-white flex items-center justify-center shadow-lg shadow-purple-900/40">
                <TrendingUp className="size-6" />
              </div>
              <div>
                <DialogTitle className="text-lg font-bold text-white flex items-center gap-2">
                  <span>Gestão de Investimentos & Patrimônio</span>
                  <Badge variant="outline" className="text-[10px] bg-purple-500/15 text-purple-300 border-purple-500/30">
                    {monthLabel(month)}
                  </Badge>
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400 mt-0.5">
                  Controle seus aportes mensais, histórico patrimonial acumulado e simulador de rentabilidade.
                </DialogDescription>
              </div>
            </div>

            <Button
              onClick={() => {
                onOpenChange(false)
                onOpenNewTx()
              }}
              size="sm"
              className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs h-9 px-3.5 gap-1.5 rounded-xl cursor-pointer shadow-md shadow-emerald-950/60 shrink-0"
            >
              <Plus className="size-4" />
              <span>Novo Aporte</span>
            </Button>
          </div>
        </DialogHeader>

        {/* 4 KPI Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 py-3">
          {/* Card 1: Aportes deste Mês */}
          <div className="p-3.5 rounded-2xl bg-slate-900/70 border border-white/[0.08] relative overflow-hidden group hover:border-emerald-500/30 transition-all">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-1.5">
              <span className="font-medium">Aporte do Mês</span>
              <Wallet className="size-4 text-emerald-400" />
            </div>
            <div className="text-base sm:text-xl font-bold text-white">
              {formatBRL(totalInvestedThisMonth)}
            </div>
            <div className="text-[11px] text-emerald-400 font-medium mt-1 flex items-center gap-1">
              <ArrowUpRight className="size-3" />
              <span>{savingsRate.toFixed(1)}% da renda</span>
            </div>
          </div>

          {/* Card 2: Patrimônio Acumulado */}
          <div className="p-3.5 rounded-2xl bg-slate-900/70 border border-white/[0.08] relative overflow-hidden group hover:border-purple-500/30 transition-all">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-1.5">
              <span className="font-medium">Total Aportado</span>
              <PiggyBank className="size-4 text-purple-400" />
            </div>
            <div className="text-base sm:text-xl font-bold text-white">
              {formatBRL(totalInvestedAllTime)}
            </div>
            <div className="text-[11px] text-slate-400 font-medium mt-1">
              {allInvestments.length} aporte(s) registrados
            </div>
          </div>

          {/* Card 3: Meta Mensal */}
          <div className="p-3.5 rounded-2xl bg-slate-900/70 border border-white/[0.08] relative overflow-hidden group hover:border-cyan-500/30 transition-all">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-1">
              <span className="font-medium">Meta Mensal</span>
              <Target className="size-4 text-cyan-400" />
            </div>
            {isEditingTarget ? (
              <div className="flex items-center gap-1.5 mt-1">
                <input
                  type="number"
                  value={targetInput}
                  onChange={(e) => setTargetInput(e.target.value)}
                  className="w-20 px-1.5 py-0.5 text-xs bg-slate-950 border border-white/20 rounded text-white"
                />
                <button
                  type="button"
                  onClick={handleSaveTarget}
                  className="text-[10px] bg-cyan-600 hover:bg-cyan-500 text-white px-2 py-1 rounded"
                >
                  Salvar
                </button>
              </div>
            ) : (
              <div
                onClick={() => setIsEditingTarget(true)}
                className="text-base sm:text-xl font-bold text-white cursor-pointer hover:text-cyan-300 transition-colors"
                title="Clique para editar meta"
              >
                {formatBRL(monthlyTarget)}
              </div>
            )}
            <div className="mt-1.5">
              <div className="flex justify-between text-[10px] text-slate-400 mb-0.5">
                <span>{targetProgress.toFixed(0)}% atingido</span>
              </div>
              <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-cyan-500 rounded-full transition-all duration-500"
                  style={{ width: `${targetProgress}%` }}
                />
              </div>
            </div>
          </div>

          {/* Card 4: Classes de Ativos */}
          <div className="p-3.5 rounded-2xl bg-slate-900/70 border border-white/[0.08] relative overflow-hidden group hover:border-amber-500/30 transition-all">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-1.5">
              <span className="font-medium">Classes Ativas</span>
              <Layers className="size-4 text-amber-400" />
            </div>
            <div className="text-base sm:text-xl font-bold text-white">
              {subcategoryBreakdown.length} classe(s)
            </div>
            <div className="text-[11px] text-slate-400 font-medium mt-1 truncate">
              {subcategoryBreakdown.length > 0
                ? subcategoryBreakdown.map((s) => s.subcategory.split(' ')[0]).join(', ')
                : 'Nenhum ativo no mês'}
            </div>
          </div>
        </div>

        {/* Tab Buttons */}
        <div className="flex items-center gap-1.5 border-b border-white/[0.08] pb-2 text-xs overflow-x-auto">
          <button
            type="button"
            onClick={() => setActiveTab('overview')}
            className={`px-3 py-1.5 rounded-xl font-semibold transition-all flex items-center gap-1.5 cursor-pointer shrink-0 ${
              activeTab === 'overview'
                ? 'bg-purple-600 text-white shadow-md'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
          >
            <PieChart className="size-3.5" />
            <span>Alocação do Mês</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('calculator')}
            className={`px-3 py-1.5 rounded-xl font-semibold transition-all flex items-center gap-1.5 cursor-pointer shrink-0 ${
              activeTab === 'calculator'
                ? 'bg-purple-600 text-white shadow-md'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
          >
            <Calculator className="size-3.5" />
            <span>Simulador Juros Compostos</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('rule503020')}
            className={`px-3 py-1.5 rounded-xl font-semibold transition-all flex items-center gap-1.5 cursor-pointer shrink-0 ${
              activeTab === 'rule503020'
                ? 'bg-purple-600 text-white shadow-md'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
          >
            <Sparkles className="size-3.5" />
            <span>Regra 50 / 30 / 20</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('history')}
            className={`px-3 py-1.5 rounded-xl font-semibold transition-all flex items-center gap-1.5 cursor-pointer shrink-0 ${
              activeTab === 'history'
                ? 'bg-purple-600 text-white shadow-md'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
          >
            <Calendar className="size-3.5" />
            <span>Histórico de Aportes</span>
          </button>
        </div>

        {/* Tab 1: Overview */}
        {activeTab === 'overview' && (
          <div className="space-y-4 pt-3 animate-in fade-in">
            {subcategoryBreakdown.length === 0 ? (
              <div className="p-8 text-center rounded-2xl bg-slate-900/50 border border-dashed border-white/[0.1] space-y-3">
                <div className="size-12 rounded-2xl bg-purple-500/10 text-purple-400 flex items-center justify-center mx-auto">
                  <PiggyBank className="size-6" />
                </div>
                <h4 className="text-sm font-bold text-white">Nenhum aporte registrado em {monthLabel(month)}</h4>
                <p className="text-xs text-slate-400 max-w-md mx-auto">
                  Registre seus investimentos do mês com a categoria <strong>Investimentos</strong> e selecione a subcategoria desejada (Renda Fixa, Ações, FIIs, etc.).
                </p>
                <Button
                  onClick={() => {
                    onOpenChange(false)
                    onOpenNewTx()
                  }}
                  className="bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs h-8 px-4 rounded-xl cursor-pointer"
                >
                  <Plus className="size-3.5 mr-1" />
                  Registrar Primeiro Aporte
                </Button>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Breakdown List */}
                <div className="p-4 rounded-2xl bg-slate-900/60 border border-white/[0.08] space-y-3">
                  <h4 className="text-xs font-bold text-white tracking-tight flex items-center gap-1.5">
                    <Layers className="size-3.5 text-purple-400" />
                    <span>Distribuição de Classes no Mês</span>
                  </h4>

                  <div className="space-y-2.5">
                    {subcategoryBreakdown.map((item, idx) => {
                      const color = INVESTMENT_SUB_COLORS[item.subcategory] || '#10b981'
                      return (
                        <div key={idx} className="p-2.5 rounded-xl bg-slate-950/60 border border-white/[0.04]">
                          <div className="flex items-center justify-between text-xs mb-1">
                            <div className="flex items-center gap-2">
                              <span
                                className="size-2.5 rounded-full"
                                style={{ backgroundColor: color }}
                              />
                              <span className="font-semibold text-slate-200">{item.subcategory}</span>
                            </div>
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-white">{formatBRL(item.amount)}</span>
                              <span className="text-[10px] text-slate-400 font-mono">
                                ({item.percentage.toFixed(1)}%)
                              </span>
                            </div>
                          </div>

                          <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
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

                {/* Month Transactions List */}
                <div className="p-4 rounded-2xl bg-slate-900/60 border border-white/[0.08] space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-bold text-white tracking-tight flex items-center gap-1.5">
                      <Wallet className="size-3.5 text-emerald-400" />
                      <span>Aportes Realizados em {monthLabel(month)}</span>
                    </h4>
                    <span className="text-[10px] text-slate-400">
                      {monthInvestments.length} lançamento(s)
                    </span>
                  </div>

                  <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                    {monthInvestments.map((tx) => (
                      <div
                        key={tx.id}
                        className="p-2.5 rounded-xl bg-slate-950/60 border border-white/[0.04] flex items-center justify-between gap-2"
                      >
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-white truncate">{tx.description}</p>
                          <div className="flex items-center gap-2 text-[10px] text-slate-400 mt-0.5">
                            <span>{formatShortDate(tx.dueDate)}</span>
                            <span>·</span>
                            <span className="text-purple-300">{tx.subcategory || 'Investimentos'}</span>
                          </div>
                        </div>
                        <div className="text-right shrink-0">
                          <span className="text-xs font-bold text-emerald-400">
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

        {/* Tab 2: Compound Interest Calculator */}
        {activeTab === 'calculator' && (
          <div className="space-y-4 pt-3 animate-in fade-in">
            <div className="p-4 rounded-2xl bg-slate-900/60 border border-white/[0.08]">
              <div className="flex items-center justify-between mb-3">
                <h4 className="text-xs font-bold text-white tracking-tight flex items-center gap-1.5">
                  <Calculator className="size-4 text-purple-400" />
                  <span>Simulador de Futuro & Bola de Neve</span>
                </h4>
                <span className="text-[10px] text-purple-300">Juros compostos reinvestidos</span>
              </div>

              {/* Inputs Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div>
                  <label className="text-[11px] font-medium text-slate-400 block mb-1">Aporte Inicial</label>
                  <div className="relative">
                    <span className="absolute left-2.5 top-1.5 text-xs text-slate-500">R$</span>
                    <input
                      type="number"
                      value={initialAmount}
                      onChange={(e) => setInitialAmount(Math.max(0, parseFloat(e.target.value) || 0))}
                      className="w-full pl-8 pr-2 py-1.5 bg-slate-950/80 border border-white/[0.1] rounded-xl text-xs text-white"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-[11px] font-medium text-slate-400 block mb-1">Aporte Mensal</label>
                  <div className="relative">
                    <span className="absolute left-2.5 top-1.5 text-xs text-slate-500">R$</span>
                    <input
                      type="number"
                      value={monthlyContribution}
                      onChange={(e) => setMonthlyContribution(Math.max(0, parseFloat(e.target.value) || 0))}
                      className="w-full pl-8 pr-2 py-1.5 bg-slate-950/80 border border-white/[0.1] rounded-xl text-xs text-white"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-[11px] font-medium text-slate-400 block mb-1">Taxa de Juros (% a.a.)</label>
                  <div className="relative">
                    <span className="absolute right-2.5 top-1.5 text-xs text-slate-500">%</span>
                    <input
                      type="number"
                      step="0.25"
                      value={annualRate}
                      onChange={(e) => setAnnualRate(Math.max(0, parseFloat(e.target.value) || 0))}
                      className="w-full px-2.5 py-1.5 bg-slate-950/80 border border-white/[0.1] rounded-xl text-xs text-white"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-[11px] font-medium text-slate-400 block mb-1">Prazo (Anos)</label>
                  <div className="relative">
                    <span className="absolute right-2.5 top-1.5 text-xs text-slate-500">anos</span>
                    <input
                      type="number"
                      min="1"
                      max="50"
                      value={years}
                      onChange={(e) => setYears(Math.max(1, parseInt(e.target.value) || 1))}
                      className="w-full px-2.5 py-1.5 bg-slate-950/80 border border-white/[0.1] rounded-xl text-xs text-white"
                    />
                  </div>
                </div>
              </div>

              {/* Presets */}
              <div className="flex flex-wrap items-center gap-1.5 mt-3 pt-2 border-t border-white/[0.06] text-[10px]">
                <span className="text-slate-500 font-medium mr-1">Benchmarks:</span>
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
                    className="px-2 py-0.5 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-slate-300 transition-colors cursor-pointer"
                  >
                    {bench.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Results Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div className="p-3.5 rounded-2xl bg-gradient-to-br from-emerald-950/40 to-slate-900/80 border border-emerald-500/30">
                <span className="text-xs text-emerald-300 font-medium block">Valor Bruto Final</span>
                <span className="text-xl sm:text-2xl font-bold text-white mt-1 block">
                  {formatBRL(simulation.futureValue)}
                </span>
                <span className="text-[10px] text-emerald-400 font-medium">
                  Patrimônio total após {years} anos
                </span>
              </div>

              <div className="p-3.5 rounded-2xl bg-slate-900/60 border border-white/[0.08]">
                <span className="text-xs text-slate-400 font-medium block">Seu Esforço (Total Aportado)</span>
                <span className="text-lg sm:text-xl font-bold text-white mt-1 block">
                  {formatBRL(simulation.totalInvestedBolso)}
                </span>
                <span className="text-[10px] text-slate-400 font-medium">
                  {((simulation.totalInvestedBolso / simulation.futureValue) * 100).toFixed(0)}% do patrimônio final
                </span>
              </div>

              <div className="p-3.5 rounded-2xl bg-gradient-to-br from-purple-950/40 to-slate-900/80 border border-purple-500/30">
                <span className="text-xs text-purple-300 font-medium block">Ganho em Juros (Bola de Neve)</span>
                <span className="text-lg sm:text-xl font-bold text-purple-300 mt-1 block">
                  {formatBRL(simulation.totalInterest)}
                </span>
                <span className="text-[10px] text-purple-400 font-medium">
                  {((simulation.totalInterest / simulation.futureValue) * 100).toFixed(0)}% veio de rentabilidade
                </span>
              </div>
            </div>

            {/* Timeline Bars */}
            <div className="p-4 rounded-2xl bg-slate-900/60 border border-white/[0.08] space-y-2.5">
              <h5 className="text-xs font-bold text-white tracking-tight">Evolução do Patrimônio Ano a Ano</h5>
              <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                {simulation.yearlyData.map((d) => {
                  const maxVal = simulation.futureValue || 1
                  const investedPct = (d.invested / maxVal) * 100
                  const interestPct = (d.interest / maxVal) * 100

                  return (
                    <div key={d.year} className="text-xs">
                      <div className="flex justify-between text-slate-300 mb-1">
                        <span className="font-semibold text-slate-400">Ano {d.year}</span>
                        <div className="flex items-center gap-3">
                          <span className="text-slate-400 text-[11px]">Aportado: {formatBRL(d.invested)}</span>
                          <span className="font-bold text-white">{formatBRL(d.total)}</span>
                        </div>
                      </div>
                      <div className="h-2 w-full bg-slate-800 rounded-full flex overflow-hidden">
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

        {/* Tab 3: 50/30/20 Rule */}
        {activeTab === 'rule503020' && (
          <div className="space-y-4 pt-3 animate-in fade-in">
            <div className="p-4 rounded-2xl bg-slate-900/60 border border-white/[0.08] space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h4 className="text-xs font-bold text-white tracking-tight flex items-center gap-1.5">
                    <Sparkles className="size-4 text-amber-400" />
                    <span>Diagnóstico Financeiro: Regra dos 50 / 30 / 20</span>
                  </h4>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    O modelo de planejamento financeiro mais consagrado do mundo, comparado com seus gastos deste mês.
                  </p>
                </div>
                <div className="text-right">
                  <span className="text-[10px] text-slate-400 block">Renda do Mês</span>
                  <span className="text-sm font-bold text-emerald-400">{formatBRL(rule503020.income)}</span>
                </div>
              </div>

              {/* 3 Pillars */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {/* 50% Necessidades */}
                <div className="p-3.5 rounded-2xl bg-slate-950/70 border border-white/[0.06] space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-300">50% Essenciais</span>
                    <span className="text-[11px] text-slate-500">Meta: {formatBRL(rule503020.targetNeeds)}</span>
                  </div>
                  <div className="text-base font-bold text-white">
                    {formatBRL(rule503020.realNeeds)}
                  </div>
                  <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full ${rule503020.needsPct > 55 ? 'bg-rose-500' : 'bg-indigo-500'}`}
                      style={{ width: `${Math.min(100, rule503020.needsPct * 2)}%` }}
                    />
                  </div>
                  <span className="text-[10px] text-slate-400 block">
                    {rule503020.needsPct.toFixed(1)}% da sua renda atual
                  </span>
                </div>

                {/* 30% Estilo de Vida */}
                <div className="p-3.5 rounded-2xl bg-slate-950/70 border border-white/[0.06] space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-300">30% Desejos & Lazer</span>
                    <span className="text-[11px] text-slate-500">Meta: {formatBRL(rule503020.targetWants)}</span>
                  </div>
                  <div className="text-base font-bold text-white">
                    {formatBRL(rule503020.realWants)}
                  </div>
                  <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full ${rule503020.wantsPct > 35 ? 'bg-amber-500' : 'bg-cyan-500'}`}
                      style={{ width: `${Math.min(100, (rule503020.wantsPct / 30) * 100)}%` }}
                    />
                  </div>
                  <span className="text-[10px] text-slate-400 block">
                    {rule503020.wantsPct.toFixed(1)}% da sua renda atual
                  </span>
                </div>

                {/* 20% Investimentos */}
                <div className="p-3.5 rounded-2xl bg-slate-950/70 border border-purple-500/30 space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-purple-300">20% Futuro / Aportes</span>
                    <span className="text-[11px] text-slate-500">Meta: {formatBRL(rule503020.targetInvest)}</span>
                  </div>
                  <div className="text-base font-bold text-white">
                    {formatBRL(rule503020.realInvest)}
                  </div>
                  <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-emerald-400 rounded-full"
                      style={{ width: `${Math.min(100, (rule503020.investPct / 20) * 100)}%` }}
                    />
                  </div>
                  <span className="text-[10px] text-emerald-400 block font-medium">
                    {rule503020.investPct.toFixed(1)}% investidos (
                    {rule503020.investPct >= 20 ? 'Meta atingida!' : `faltam ${formatBRL(Math.max(0, rule503020.targetInvest - rule503020.realInvest))}`}
                    )
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 4: History */}
        {activeTab === 'history' && (
          <div className="space-y-3 pt-3 animate-in fade-in">
            {/* Filter pills */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-[11px]">
              <span className="text-slate-500 font-medium mr-1">Filtrar por:</span>
              <button
                type="button"
                onClick={() => setHistoryFilter('all')}
                className={`px-2.5 py-1 rounded-lg cursor-pointer transition-colors ${
                  historyFilter === 'all'
                    ? 'bg-purple-600 text-white font-bold'
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
                    className={`px-2.5 py-1 rounded-lg cursor-pointer transition-colors truncate max-w-[160px] ${
                      historyFilter === sub
                        ? 'bg-purple-600 text-white font-bold'
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
              <div className="p-8 text-center text-slate-500 text-xs">
                Nenhum aporte encontrado com este filtro.
              </div>
            ) : (
              <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
                {filteredHistory.map((tx) => (
                  <div
                    key={tx.id}
                    className="p-3 rounded-2xl bg-slate-900/60 border border-white/[0.06] flex items-center justify-between gap-3 hover:border-purple-500/30 transition-all"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="size-9 rounded-xl bg-purple-500/15 border border-purple-500/30 text-purple-300 flex items-center justify-center shrink-0">
                        <TrendingUp className="size-4" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-xs font-semibold text-white truncate">{tx.description}</p>
                        <div className="flex items-center gap-2 text-[10px] text-slate-400 mt-0.5 flex-wrap">
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
                      <span className="text-xs font-bold text-emerald-400">
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
