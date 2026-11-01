import { useState, useMemo } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { effectiveAmount } from '@/lib/finance'
import { formatBRL, formatShortDate, monthLabel, todayKey, round2 } from '@/lib/utils'
import { CATEGORY_GROUPS, getBudgetNature } from '@/types'
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
  ArrowDownLeft,
  Calendar,
  Layers,
  ShieldCheck,
  Flame,
  Edit3,
} from 'lucide-react'

interface InvestmentsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onOpenNewTx?: () => void
}

export interface InvestmentAssetDef {
  subcategory: string
  bucket: 'safe' | 'risk'
  color: string
  description: string
}

const ASSET_DEFINITIONS: InvestmentAssetDef[] = [
  {
    subcategory: 'Reserva de Emergência',
    bucket: 'safe',
    color: '#06b6d4',
    description: 'Liquidez diária para imprevistos e segurança familiar',
  },
  {
    subcategory: 'Renda Fixa (CDB / Tesouro / LCI / LCA)',
    bucket: 'safe',
    color: '#10b981',
    description: 'CDB 100%+ CDI, Tesouro Selic e títulos isentos de IR',
  },
  {
    subcategory: 'Previdência Privada',
    bucket: 'safe',
    color: '#ec4899',
    description: 'Aportes em PGBL e VGBL de longo prazo',
  },
  {
    subcategory: 'Ações & Fundos Imobiliários (FIIs)',
    bucket: 'risk',
    color: '#8b5cf6',
    description: 'Dividendos mensais e valorização na Bolsa B3',
  },
  {
    subcategory: 'Criptomoedas & Ativos Digitais',
    bucket: 'risk',
    color: '#f59e0b',
    description: 'Bitcoin, Ethereum e criptoativos descentralizados',
  },
  {
    subcategory: 'Fundos de Investimento',
    bucket: 'risk',
    color: '#3b82f6',
    description: 'Fundos multimercado, cambiais e ações globais',
  },
  {
    subcategory: 'Outros Investimentos',
    bucket: 'risk',
    color: '#94a3b8',
    description: 'Startups, colecionáveis e outros ativos patrimoniais',
  },
]

export function InvestmentsModal({ open, onOpenChange, onOpenNewTx: _onOpenNewTx }: InvestmentsModalProps) {
  const {
    summary,
    month,
    transactions,
    monthTransactions,
    profile,
    updateProfile,
    addTransaction,
    me,
  } = useFinance()

  const [activeTab, setActiveTab] = useState<'overview' | 'calculator' | 'rule503020' | 'history'>('overview')

  // Target storage (Firestore profile with fallback)
  const currentTarget = profile?.investmentMonthlyTarget ?? 1000
  const [targetInput, setTargetInput] = useState<string>(String(currentTarget))
  const [isEditingTarget, setIsEditingTarget] = useState(false)

  const handleSaveTarget = async () => {
    const num = Math.max(0, parseFloat(targetInput.replace(',', '.')) || 0)
    await updateProfile({ investmentMonthlyTarget: num })
    setIsEditingTarget(false)
  }

  // Quick Action Dialogs State
  const [quickActionType, setQuickActionType] = useState<'aporte' | 'resgate' | null>(null)
  const [selectedAssetForAction, setSelectedAssetForAction] = useState<string>('Reserva de Emergência')
  const [actionAmount, setActionAmount] = useState<string>('')
  const [actionDesc, setActionDesc] = useState<string>('')
  const [actionDate, setActionDate] = useState<string>(todayKey())
  const [isSubmittingAction, setIsSubmittingAction] = useState(false)

  // Edit Balance Dialog State
  const [editBalanceAsset, setEditBalanceAsset] = useState<string | null>(null)
  const [editBalanceInput, setEditBalanceInput] = useState<string>('')
  const [isSubmittingBalance, setIsSubmittingBalance] = useState(false)

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

  // Asset Cards & Balances computation
  const assetCards = useMemo(() => {
    const cloudBalances = profile?.investmentAssetBalances || {}

    return ASSET_DEFINITIONS.map((def) => {
      const allSubTxs = allInvestments.filter(
        (t) => (t.subcategory || 'Outros Investimentos') === def.subcategory
      )
      const monthSubTxs = monthInvestments.filter(
        (t) => (t.subcategory || 'Outros Investimentos') === def.subcategory
      )

      const subAportesAllTime = allSubTxs
        .filter((t) => t.type === 'expense')
        .reduce((sum, t) => sum + effectiveAmount(t), 0)

      const subResgatesAllTime = allSubTxs
        .filter((t) => t.type === 'income')
        .reduce((sum, t) => sum + effectiveAmount(t), 0)

      const totalInvestedAllTime = Math.max(0, round2(subAportesAllTime - subResgatesAllTime))

      const totalInvestedThisMonth = round2(
        monthSubTxs
          .filter((t) => t.type === 'expense')
          .reduce((sum, t) => sum + effectiveAmount(t), 0)
      )

      // Balance prioritizes user-edited balance in Firestore profile; defaults to total invested
      const userBalance = cloudBalances[def.subcategory]
      const currentBalance = typeof userBalance === 'number' ? userBalance : totalInvestedAllTime

      const profitOrLoss = round2(currentBalance - totalInvestedAllTime)
      const profitPercentage = totalInvestedAllTime > 0 ? round2((profitOrLoss / totalInvestedAllTime) * 100) : 0

      return {
        ...def,
        totalInvestedAllTime,
        totalInvestedThisMonth,
        currentBalance,
        profitOrLoss,
        profitPercentage,
      }
    })
  }, [allInvestments, monthInvestments, profile?.investmentAssetBalances])

  const safeReserveAssets = useMemo(() => assetCards.filter((a) => a.bucket === 'safe'), [assetCards])
  const riskAssets = useMemo(() => assetCards.filter((a) => a.bucket === 'risk'), [assetCards])

  // Metrics
  const totalCurrentPatrimony = useMemo(() => {
    return round2(assetCards.reduce((sum, a) => sum + a.currentBalance, 0))
  }, [assetCards])

  const totalInvestedThisMonth = summary?.totalInvested || 0
  const totalInvestedAllTime = summary?.totalInvestedAccumulated || 0
  const monthlyIncome = summary?.income || 0
  const savingsRate = monthlyIncome > 0 ? (totalInvestedThisMonth / monthlyIncome) * 100 : 0
  const targetProgress = currentTarget > 0 ? Math.min(100, (totalInvestedThisMonth / currentTarget) * 100) : 0

  const subcategoryBreakdown = summary?.byInvestmentSubcategory || []

  // Handlers for Quick Aporte / Resgate
  const handleOpenAction = (type: 'aporte' | 'resgate', subcat?: string) => {
    setQuickActionType(type)
    setSelectedAssetForAction(subcat || 'Reserva de Emergência')
    setActionAmount('')
    setActionDesc('')
    setActionDate(todayKey())
  }

  const handleSubmitAction = async (e: React.FormEvent) => {
    e.preventDefault()
    const amount = Math.max(0, parseFloat(actionAmount.replace(',', '.')) || 0)
    if (amount <= 0 || !quickActionType) return

    setIsSubmittingAction(true)
    try {
      const isAporte = quickActionType === 'aporte'
      const uid = me?.uid || 'user_default'
      const isoDate = actionDate || todayKey()
      const txMonth = isoDate.substring(0, 7)

      await addTransaction({
        createdByUid: uid,
        paidByUid: uid,
        description: actionDesc.trim() || `${isAporte ? 'Aporte' : 'Resgate'} - ${selectedAssetForAction}`,
        totalAmount: amount,
        estimatedAmount: amount,
        actualAmount: amount,
        category: 'Investimentos',
        subcategory: selectedAssetForAction,
        type: isAporte ? 'expense' : 'income',
        paymentMethod: 'pix',
        status: 'paid',
        dueDate: isoDate,
        competenceMonth: txMonth,
        splitType: 'mine',
        visibleToUids: [uid],
        allocations: [
          {
            type: 'PLATFORM_USER',
            uid,
            name: me?.name || 'Eu',
            amount,
            reimbursed: true,
          },
        ],
        userImpact: { [uid]: amount },
      })

      // Update asset balance in user profile
      const currentBalances = { ...(profile?.investmentAssetBalances || {}) }
      const existing = currentBalances[selectedAssetForAction] ?? (
        assetCards.find((a) => a.subcategory === selectedAssetForAction)?.currentBalance || 0
      )
      const newBal = isAporte
        ? round2(existing + amount)
        : Math.max(0, round2(existing - amount))

      currentBalances[selectedAssetForAction] = newBal
      await updateProfile({ investmentAssetBalances: currentBalances })

      setQuickActionType(null)
    } catch (err) {
      console.error('Erro ao registrar aporte/resgate:', err)
    } finally {
      setIsSubmittingAction(false)
    }
  }

  // Handlers for Edit Balance
  const handleOpenEditBalance = (subcat: string, currentBal: number) => {
    setEditBalanceAsset(subcat)
    setEditBalanceInput(String(currentBal))
  }

  const handleSubmitEditBalance = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editBalanceAsset) return
    const newBal = Math.max(0, parseFloat(editBalanceInput.replace(',', '.')) || 0)

    setIsSubmittingBalance(true)
    try {
      const currentBalances = { ...(profile?.investmentAssetBalances || {}) }
      currentBalances[editBalanceAsset] = round2(newBal)
      await updateProfile({ investmentAssetBalances: currentBalances })
      setEditBalanceAsset(null)
    } catch (err) {
      console.error('Erro ao atualizar saldo da caixinha:', err)
    } finally {
      setIsSubmittingBalance(false)
    }
  }

  // Compound Interest Calculator State
  const [initialAmount, setInitialAmount] = useState<number>(1000)
  const [monthlyContribution, setMonthlyContribution] = useState<number>(500)
  const [annualRate, setAnnualRate] = useState<number>(11.0)
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

    let realNeeds = 0
    let realWants = 0
    let realInvest = totalInvestedThisMonth

    const bySubs = summary?.bySubcategory || []
    const byCats = summary?.byCategory || []

    for (const item of bySubs) {
      if (item.category === 'Investimentos') continue
      const nature = getBudgetNature(item.category, item.subcategory)
      if (nature === 'need') {
        realNeeds += item.amount
      } else {
        realWants += item.amount
      }
    }

    for (const catItem of byCats) {
      if (catItem.category === 'Investimentos') continue
      const subSum = bySubs
        .filter((s) => s.category === catItem.category)
        .reduce((sum, s) => sum + s.amount, 0)
      const diff = Math.max(0, catItem.amount - subSum)
      if (diff > 0.01) {
        const catNature = getBudgetNature(catItem.category)
        if (catNature === 'need') realNeeds += diff
        else realWants += diff
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
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-w-5xl lg:max-w-6xl w-[96vw] max-h-[94dvh] overflow-y-auto bg-[#0a0f1d]/98 border border-white/[0.1] text-slate-100 p-5 sm:p-7 md:p-8 rounded-[2rem] shadow-2xl backdrop-blur-2xl">
          {/* Header */}
          <DialogHeader className="pb-4 border-b border-white/[0.08]">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex items-center gap-3.5 sm:gap-4">
                <div className="size-12 sm:size-14 rounded-2xl bg-gradient-to-tr from-purple-600 via-indigo-600 to-teal-400 text-white flex items-center justify-center shadow-xl shadow-purple-900/40 shrink-0">
                  <TrendingUp className="size-6 sm:size-7 stroke-[2.2]" />
                </div>
                <div>
                  <DialogTitle className="text-xl sm:text-2xl font-black text-white flex items-center gap-2.5 flex-wrap">
                    <span>Gestão de Investimentos & Caixinhas</span>
                    <Badge variant="outline" className="text-xs px-2.5 py-0.5 bg-purple-500/15 text-purple-300 border-purple-500/30 font-semibold">
                      {monthLabel(month)}
                    </Badge>
                  </DialogTitle>
                  <DialogDescription className="text-xs sm:text-sm text-slate-400 mt-1 leading-relaxed">
                    Acompanhe suas caixinhas, alocação de ativos, aportes, resgates e sincronize os rendimentos reais com 1 clique.
                  </DialogDescription>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <Button
                  onClick={() => handleOpenAction('resgate')}
                  variant="outline"
                  size="sm"
                  className="border-amber-500/40 text-amber-300 hover:bg-amber-500/10 font-bold text-xs h-10 px-3.5 rounded-xl gap-1.5 cursor-pointer transition-transform active:scale-95"
                >
                  <ArrowDownLeft className="size-4 text-amber-400" />
                  <span>Resgatar</span>
                </Button>

                <Button
                  onClick={() => handleOpenAction('aporte')}
                  size="sm"
                  className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs h-10 px-4 gap-1.5 rounded-xl cursor-pointer shadow-lg shadow-emerald-950/70 transition-transform active:scale-95"
                >
                  <Plus className="size-4 stroke-[2.5]" />
                  <span>Novo Aporte</span>
                </Button>
              </div>
            </div>
          </DialogHeader>

          {/* 4 KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 py-4 sm:py-5">
            {/* Card 1: Patrimônio Atual */}
            <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950 border border-white/[0.08] hover:border-purple-500/40 transition-all shadow-md group">
              <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm mb-2 font-medium">
                <span>Patrimônio Atual (Caixinhas)</span>
                <div className="size-8 rounded-lg bg-purple-500/15 text-purple-400 flex items-center justify-center">
                  <PiggyBank className="size-4" />
                </div>
              </div>
              <div className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                {formatBRL(totalCurrentPatrimony)}
              </div>
              <div className="text-xs text-purple-300 font-medium mt-2 flex items-center gap-1.5">
                <span>Histórico aportado: {formatBRL(totalInvestedAllTime)}</span>
              </div>
            </div>

            {/* Card 2: Aportes deste Mês */}
            <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950 border border-white/[0.08] hover:border-emerald-500/40 transition-all shadow-md group">
              <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm mb-2 font-medium">
                <span>Aporte Líquido do Mês</span>
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
                    className="text-xs bg-cyan-600 hover:bg-cyan-500 text-white font-bold px-2.5 py-1 rounded-lg cursor-pointer"
                  >
                    Salvar
                  </button>
                </div>
              ) : (
                <div
                  onClick={() => setIsEditingTarget(true)}
                  className="text-2xl sm:text-3xl font-extrabold text-white cursor-pointer hover:text-cyan-300 transition-colors tracking-tight flex items-center gap-2"
                  title="Clique para editar sua meta de investimento"
                >
                  <span>{formatBRL(currentTarget)}</span>
                  <Edit3 className="size-3.5 text-slate-500" />
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

            {/* Card 4: Diversificação Ativa */}
            <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950 border border-white/[0.08] hover:border-amber-500/40 transition-all shadow-md group">
              <div className="flex items-center justify-between text-slate-400 text-xs sm:text-sm mb-2 font-medium">
                <span>Diversificação Ativa</span>
                <div className="size-8 rounded-lg bg-amber-500/15 text-amber-400 flex items-center justify-center">
                  <Layers className="size-4" />
                </div>
              </div>
              <div className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                {assetCards.filter((a) => a.currentBalance > 0).length} caixinha(s)
              </div>
              <div className="text-xs text-amber-300 font-medium mt-2 truncate">
                {assetCards.filter((a) => a.currentBalance > 0).length > 0
                  ? assetCards
                      .filter((a) => a.currentBalance > 0)
                      .map((s) => s.subcategory.split(' ')[0])
                      .join(', ')
                  : 'Nenhuma caixinha com saldo positivo'}
              </div>
            </div>
          </div>

          {/* Tab Navigation */}
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
              <span>Caixinhas & Alocação</span>
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

          {/* TAB 1: OVERVIEW (Caixinhas & Reservas + Alocação do Mês) */}
          {activeTab === 'overview' && (
            <div className="space-y-6 pt-4 animate-in fade-in duration-200">
              {/* Seção 1: Caixinhas & Reservas Seguras */}
              <div className="space-y-3">
                <div className="flex items-center gap-2 text-sm font-bold text-slate-200">
                  <ShieldCheck className="size-4 text-emerald-400" />
                  <span>Caixinhas & Reservas Seguras</span>
                  <span className="text-xs text-slate-500 font-normal">
                    (Renda Fixa, Liquidez e Proteção)
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {safeReserveAssets.map((asset) => (
                    <div
                      key={asset.subcategory}
                      className="p-4 sm:p-5 rounded-2xl bg-slate-900/60 border border-white/[0.08] hover:border-emerald-500/40 transition-all flex flex-col justify-between gap-4 shadow-sm group"
                    >
                      <div>
                        <div className="flex items-center justify-between gap-2 mb-2">
                          <div className="flex items-center gap-2 min-w-0">
                            <span
                              className="size-3 rounded-full shrink-0 shadow-sm"
                              style={{ backgroundColor: asset.color }}
                            />
                            <h5 className="text-sm font-bold text-white truncate" title={asset.subcategory}>
                              {asset.subcategory}
                            </h5>
                          </div>
                          {asset.profitOrLoss !== 0 && (
                            <Badge
                              variant="outline"
                              className={`text-[10px] px-2 py-0.5 font-bold ${
                                asset.profitOrLoss > 0
                                  ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                                  : 'bg-rose-500/15 text-rose-300 border-rose-500/30'
                              }`}
                            >
                              {asset.profitOrLoss > 0 ? '+' : ''}
                              {formatBRL(asset.profitOrLoss)} ({asset.profitPercentage > 0 ? '+' : ''}
                              {asset.profitPercentage.toFixed(1)}%)
                            </Badge>
                          )}
                        </div>

                        <p className="text-xs text-slate-400 leading-relaxed line-clamp-2">
                          {asset.description}
                        </p>

                        <div className="mt-3.5 p-3 rounded-xl bg-slate-950/70 border border-white/[0.04]">
                          <div className="text-[11px] text-slate-400 font-medium">Saldo Atual na Caixinha</div>
                          <div className="text-xl font-black text-white mt-0.5">
                            {formatBRL(asset.currentBalance)}
                          </div>
                          <div className="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-white/[0.06]">
                            <span>Total Aportado: {formatBRL(asset.totalInvestedAllTime)}</span>
                            {asset.totalInvestedThisMonth > 0 && (
                              <span className="text-emerald-400 font-semibold">
                                +{formatBRL(asset.totalInvestedThisMonth)} no mês
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Botões Rápidos */}
                      <div className="grid grid-cols-3 gap-2 pt-2 border-t border-white/[0.06]">
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => handleOpenAction('aporte', asset.subcategory)}
                          className="h-8 text-xs font-bold text-emerald-400 hover:bg-emerald-500/15 hover:text-emerald-300 rounded-lg cursor-pointer px-1"
                        >
                          + Aporte
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => handleOpenAction('resgate', asset.subcategory)}
                          className="h-8 text-xs font-bold text-amber-400 hover:bg-amber-500/15 hover:text-amber-300 rounded-lg cursor-pointer px-1"
                        >
                          - Resgate
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => handleOpenEditBalance(asset.subcategory, asset.currentBalance)}
                          className="h-8 text-xs font-bold text-slate-300 hover:bg-white/[0.08] hover:text-white rounded-lg cursor-pointer px-1"
                        >
                          Ajustar
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Seção 2: Investimentos de Risco & Renda Variável */}
              <div className="space-y-3 pt-2">
                <div className="flex items-center gap-2 text-sm font-bold text-slate-200">
                  <Flame className="size-4 text-purple-400" />
                  <span>Investimentos de Risco & Renda Variável</span>
                  <span className="text-xs text-slate-500 font-normal">
                    (Ações, FIIs, Criptoativos e Fundos)
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                  {riskAssets.map((asset) => (
                    <div
                      key={asset.subcategory}
                      className="p-4 sm:p-5 rounded-2xl bg-slate-900/60 border border-white/[0.08] hover:border-purple-500/40 transition-all flex flex-col justify-between gap-4 shadow-sm group"
                    >
                      <div>
                        <div className="flex items-center justify-between gap-2 mb-2">
                          <div className="flex items-center gap-2 min-w-0">
                            <span
                              className="size-3 rounded-full shrink-0 shadow-sm"
                              style={{ backgroundColor: asset.color }}
                            />
                            <h5 className="text-sm font-bold text-white truncate" title={asset.subcategory}>
                              {asset.subcategory}
                            </h5>
                          </div>
                        </div>

                        {asset.profitOrLoss !== 0 && (
                          <div className="mb-2">
                            <Badge
                              variant="outline"
                              className={`text-[10px] px-2 py-0.5 font-bold ${
                                asset.profitOrLoss > 0
                                  ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                                  : 'bg-rose-500/15 text-rose-300 border-rose-500/30'
                              }`}
                            >
                              {asset.profitOrLoss > 0 ? '+' : ''}
                              {formatBRL(asset.profitOrLoss)} ({asset.profitPercentage > 0 ? '+' : ''}
                              {asset.profitPercentage.toFixed(1)}%)
                            </Badge>
                          </div>
                        )}

                        <p className="text-xs text-slate-400 leading-relaxed line-clamp-2">
                          {asset.description}
                        </p>

                        <div className="mt-3.5 p-3 rounded-xl bg-slate-950/70 border border-white/[0.04]">
                          <div className="text-[11px] text-slate-400 font-medium">Saldo Atual</div>
                          <div className="text-xl font-black text-white mt-0.5">
                            {formatBRL(asset.currentBalance)}
                          </div>
                          <div className="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-white/[0.06]">
                            <span>Aportado: {formatBRL(asset.totalInvestedAllTime)}</span>
                            {asset.totalInvestedThisMonth > 0 && (
                              <span className="text-purple-400 font-semibold">
                                +{formatBRL(asset.totalInvestedThisMonth)}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Botões Rápidos */}
                      <div className="grid grid-cols-3 gap-2 pt-2 border-t border-white/[0.06]">
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => handleOpenAction('aporte', asset.subcategory)}
                          className="h-8 text-xs font-bold text-emerald-400 hover:bg-emerald-500/15 hover:text-emerald-300 rounded-lg cursor-pointer px-1"
                        >
                          + Aporte
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => handleOpenAction('resgate', asset.subcategory)}
                          className="h-8 text-xs font-bold text-amber-400 hover:bg-amber-500/15 hover:text-amber-300 rounded-lg cursor-pointer px-1"
                        >
                          - Resgate
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => handleOpenEditBalance(asset.subcategory, asset.currentBalance)}
                          className="h-8 text-xs font-bold text-slate-300 hover:bg-white/[0.08] hover:text-white rounded-lg cursor-pointer px-1"
                        >
                          Ajustar
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Seção 3: Alocação do Mês & Histórico Mensal */}
              {subcategoryBreakdown.length > 0 && (
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 pt-3">
                  {/* Breakdown List */}
                  <div className="lg:col-span-7 p-5 sm:p-6 rounded-3xl bg-slate-900/60 border border-white/[0.08] space-y-4 shadow-md">
                    <div className="flex items-center justify-between">
                      <h4 className="text-sm font-bold text-white tracking-tight flex items-center gap-2">
                        <Layers className="size-4 text-purple-400" />
                        <span>Alocação dos Aportes em {monthLabel(month)}</span>
                      </h4>
                      <span className="text-xs text-slate-400 font-medium">
                        Total: {formatBRL(totalInvestedThisMonth)}
                      </span>
                    </div>

                    <div className="space-y-3">
                      {subcategoryBreakdown.map((item, idx) => {
                        const def = ASSET_DEFINITIONS.find((d) => d.subcategory === item.subcategory)
                        const color = def?.color || '#10b981'
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

                  {/* Lançamentos do Mês */}
                  <div className="lg:col-span-5 p-5 sm:p-6 rounded-3xl bg-slate-900/60 border border-white/[0.08] space-y-4 shadow-md">
                    <div className="flex items-center justify-between">
                      <h4 className="text-sm font-bold text-white tracking-tight flex items-center gap-2">
                        <Wallet className="size-4 text-emerald-400" />
                        <span>Aportes & Resgates do Mês</span>
                      </h4>
                      <span className="text-xs text-slate-400">
                        {monthInvestments.length} registro(s)
                      </span>
                    </div>

                    <div className="space-y-2.5 max-h-80 overflow-y-auto pr-1 custom-scrollbar">
                      {monthInvestments.map((tx) => {
                        const isIncome = tx.type === 'income'
                        return (
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
                              <span
                                className={`text-xs sm:text-sm font-bold ${
                                  isIncome ? 'text-amber-400' : 'text-emerald-400'
                                }`}
                              >
                                {isIncome ? '-' : '+'}
                                {formatBRL(effectiveAmount(tx))}
                              </span>
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* TAB 2: CALCULATOR */}
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

          {/* TAB 3: REGRA 50/30/20 */}
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

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
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

          {/* TAB 4: HISTÓRICO */}
          {activeTab === 'history' && (
            <div className="space-y-4 pt-4 animate-in fade-in duration-200">
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
                      {(sub || '').split(' ')[0]} ({count})
                    </button>
                  )
                })}
              </div>

              {filteredHistory.length === 0 ? (
                <div className="p-10 text-center text-slate-500 text-sm">
                  Nenhum aporte ou resgate encontrado com este filtro.
                </div>
              ) : (
                <div className="space-y-2.5 max-h-96 overflow-y-auto pr-1 custom-scrollbar">
                  {filteredHistory.map((tx) => {
                    const isIncome = tx.type === 'income'
                    return (
                      <div
                        key={tx.id}
                        className="p-3.5 sm:p-4 rounded-2xl bg-slate-900/60 border border-white/[0.06] flex items-center justify-between gap-4 hover:border-purple-500/30 transition-all shadow-sm"
                      >
                        <div className="flex items-center gap-3.5 min-w-0">
                          <div
                            className={`size-10 rounded-2xl border flex items-center justify-center shrink-0 ${
                              isIncome
                                ? 'bg-amber-500/15 border-amber-500/30 text-amber-300'
                                : 'bg-emerald-500/15 border-emerald-500/30 text-emerald-300'
                            }`}
                          >
                            {isIncome ? (
                              <ArrowDownLeft className="size-5" />
                            ) : (
                              <TrendingUp className="size-5" />
                            )}
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
                              <Badge
                                variant="outline"
                                className={`text-[10px] px-2 py-0 border-none font-bold ${
                                  isIncome ? 'bg-amber-500/20 text-amber-300' : 'bg-emerald-500/20 text-emerald-300'
                                }`}
                              >
                                {isIncome ? 'Resgate' : 'Aporte'}
                              </Badge>
                            </div>
                          </div>
                        </div>

                        <div className="text-right shrink-0">
                          <span
                            className={`text-xs sm:text-sm font-bold ${
                              isIncome ? 'text-amber-400' : 'text-emerald-400'
                            }`}
                          >
                            {isIncome ? '-' : '+'}
                            {formatBRL(effectiveAmount(tx))}
                          </span>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>

      {/* SUB-MODAL 1: Aporte ou Resgate Rápido */}
      {quickActionType && (
        <Dialog open={Boolean(quickActionType)} onOpenChange={(open) => !open && setQuickActionType(null)}>
          <DialogContent className="max-w-md w-[92vw] bg-[#0c1222] border border-white/[0.12] text-slate-100 p-6 rounded-3xl shadow-2xl z-[60]">
            <DialogHeader>
              <DialogTitle className="text-lg font-bold text-white flex items-center gap-2">
                {quickActionType === 'aporte' ? (
                  <>
                    <Plus className="size-5 text-emerald-400" />
                    <span>Novo Aporte Patrimonial</span>
                  </>
                ) : (
                  <>
                    <ArrowDownLeft className="size-5 text-amber-400" />
                    <span>Resgate de Caixinha / Ativo</span>
                  </>
                )}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400">
                {quickActionType === 'aporte'
                  ? 'Registra uma saída na sua conta corrente e adiciona o saldo à sua caixinha.'
                  : 'Registra uma entrada na sua conta corrente e deduz o saldo da caixinha selecionada.'}
              </DialogDescription>
            </DialogHeader>

            <form onSubmit={handleSubmitAction} className="space-y-4 py-2">
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1.5">Caixinha / Ativo</label>
                <select
                  value={selectedAssetForAction}
                  onChange={(e) => setSelectedAssetForAction(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-white/[0.12] rounded-xl text-xs sm:text-sm font-semibold text-white focus:outline-none focus:border-purple-500"
                >
                  {ASSET_DEFINITIONS.map((def) => (
                    <option key={def.subcategory} value={def.subcategory}>
                      {def.subcategory}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1.5">Valor (R$)</label>
                <div className="relative">
                  <span className="absolute left-3 top-2.5 text-sm text-slate-500 font-medium">R$</span>
                  <input
                    type="number"
                    step="0.01"
                    required
                    autoFocus
                    placeholder="0,00"
                    value={actionAmount}
                    onChange={(e) => setActionAmount(e.target.value)}
                    className="w-full pl-10 pr-3 py-2 bg-slate-950 border border-white/[0.12] rounded-xl text-base font-extrabold text-white focus:outline-none focus:border-purple-500"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1.5">Data do Lançamento</label>
                <input
                  type="date"
                  required
                  value={actionDate}
                  onChange={(e) => setActionDate(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-white/[0.12] rounded-xl text-xs sm:text-sm font-semibold text-white focus:outline-none focus:border-purple-500"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1.5">Descrição (Opcional)</label>
                <input
                  type="text"
                  placeholder={`Ex: ${quickActionType === 'aporte' ? 'Aporte mensal Nubank' : 'Resgate para emergência'}`}
                  value={actionDesc}
                  onChange={(e) => setActionDesc(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-950 border border-white/[0.12] rounded-xl text-xs sm:text-sm text-white focus:outline-none focus:border-purple-500"
                />
              </div>

              <DialogFooter className="pt-3 flex gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setQuickActionType(null)}
                  className="text-slate-400 hover:text-white"
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  disabled={isSubmittingAction}
                  className={`font-bold text-xs h-10 px-5 rounded-xl ${
                    quickActionType === 'aporte'
                      ? 'bg-emerald-600 hover:bg-emerald-500 text-white'
                      : 'bg-amber-600 hover:bg-amber-500 text-white'
                  }`}
                >
                  {isSubmittingAction ? (
                    'Salvando...'
                  ) : quickActionType === 'aporte' ? (
                    'Confirmar Aporte'
                  ) : (
                    'Confirmar Resgate'
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      )}

      {/* SUB-MODAL 2: Ajustar Saldo Real da Caixinha */}
      {editBalanceAsset && (
        <Dialog open={Boolean(editBalanceAsset)} onOpenChange={(open) => !open && setEditBalanceAsset(null)}>
          <DialogContent className="max-w-md w-[92vw] bg-[#0c1222] border border-white/[0.12] text-slate-100 p-6 rounded-3xl shadow-2xl z-[60]">
            <DialogHeader>
              <DialogTitle className="text-lg font-bold text-white flex items-center gap-2">
                <Edit3 className="size-5 text-purple-400" />
                <span>Ajustar Saldo Atual</span>
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400">
                Atualize o saldo de <strong>{editBalanceAsset}</strong> para sincronizar com o saldo real exibido no app da sua corretora ou banco.
              </DialogDescription>
            </DialogHeader>

            <form onSubmit={handleSubmitEditBalance} className="space-y-4 py-2">
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1.5">Novo Saldo Total (R$)</label>
                <div className="relative">
                  <span className="absolute left-3 top-2.5 text-sm text-slate-500 font-medium">R$</span>
                  <input
                    type="number"
                    step="0.01"
                    required
                    autoFocus
                    value={editBalanceInput}
                    onChange={(e) => setEditBalanceInput(e.target.value)}
                    className="w-full pl-10 pr-3 py-2 bg-slate-950 border border-white/[0.12] rounded-xl text-base font-extrabold text-white focus:outline-none focus:border-purple-500"
                  />
                </div>
              </div>

              {/* Informação do cálculo de rendimento */}
              {(() => {
                const asset = assetCards.find((a) => a.subcategory === editBalanceAsset)
                const totalAportado = asset?.totalInvestedAllTime || 0
                const num = parseFloat(editBalanceInput.replace(',', '.')) || 0
                const diff = round2(num - totalAportado)
                const pct = totalAportado > 0 ? round2((diff / totalAportado) * 100) : 0

                return (
                  <div className="p-3 rounded-xl bg-slate-950/80 border border-white/[0.06] text-xs space-y-1">
                    <div className="flex justify-between text-slate-400">
                      <span>Total Aportado Histórico:</span>
                      <span className="font-semibold text-slate-200">{formatBRL(totalAportado)}</span>
                    </div>
                    <div className="flex justify-between items-center pt-1 border-t border-white/[0.06]">
                      <span className="text-slate-400">Rendimento / Lucro Apurado:</span>
                      <span
                        className={`font-bold ${
                          diff >= 0 ? 'text-emerald-400' : 'text-rose-400'
                        }`}
                      >
                        {diff >= 0 ? '+' : ''}
                        {formatBRL(diff)} ({pct >= 0 ? '+' : ''}
                        {pct.toFixed(1)}%)
                      </span>
                    </div>
                  </div>
                )
              })()}

              <DialogFooter className="pt-3 flex gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => setEditBalanceAsset(null)}
                  className="text-slate-400 hover:text-white"
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  disabled={isSubmittingBalance}
                  className="bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs h-10 px-5 rounded-xl"
                >
                  {isSubmittingBalance ? 'Salvando...' : 'Atualizar Saldo'}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      )}
    </>
  )
}
