import { useMemo } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL, monthLabel } from '@/lib/utils'
import {
  calculateFinancialHealthScore,
  resolveEmergencyReserveBalance,
} from '@/lib/finance'
import {
  ShieldCheck,
  TrendingUp,
  Home,
  Target,
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  Sliders,
} from 'lucide-react'

interface FinancialHealthModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onOpenInvestments?: () => void
  onOpenBudgets?: () => void
}

export function FinancialHealthModal({
  open,
  onOpenChange,
  onOpenInvestments,
  onOpenBudgets,
}: FinancialHealthModalProps) {
  const { summary, profile, month, categoryBudgetReport } = useFinance()

  const reserveBalance = useMemo(
    () => resolveEmergencyReserveBalance(profile, summary),
    [profile, summary],
  )

  const health = useMemo(
    () => calculateFinancialHealthScore(summary, reserveBalance, categoryBudgetReport),
    [summary, reserveBalance, categoryBudgetReport],
  )

  const handleAskCopilot = () => {
    onOpenChange(false)
    window.dispatchEvent(
      new CustomEvent('saldo-livre:ask-copilot', {
        detail: {
          prompt:
            'Faça um diagnóstico completo da minha Saúde Financeira (4 pilares) e dos meus Envelopes Orçamentários deste mês, indicando um plano prático para subir meu Score.',
        },
      }),
    )
  }

  const toneColor =
    health.classification === 'excellent'
      ? 'emerald'
      : health.classification === 'healthy'
        ? 'cyan'
        : health.classification === 'warning'
          ? 'amber'
          : 'rose'

  const livingCost = health.monthlyLivingCost ?? (summary ? summary.paid + summary.committed : 0)
  const targetReserve6m = livingCost * 6
  const configuredEnvelopes = health.budgetConfiguredCount ?? 0
  const overspentEnvelopes = health.budgetOverspentCount ?? 0
  const adherencePct = health.budgetAdherenceScore ?? 100

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[90vh] flex flex-col p-0 overflow-hidden bg-slate-950 border-white/10 text-slate-100">
        {/* Header */}
        <DialogHeader className="p-5 pb-4 border-b border-white/[0.08] bg-slate-900/60 shrink-0">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-emerald-500/15 text-emerald-400 border border-emerald-500/25">
                <ShieldCheck className="size-5" />
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-white flex items-center gap-2">
                  Diagnóstico de Saúde Financeira
                  <Badge
                    variant="outline"
                    className={`text-[10px] px-2 py-0 border-0 font-semibold ${
                      toneColor === 'emerald'
                        ? 'bg-emerald-500/15 text-emerald-300'
                        : toneColor === 'cyan'
                          ? 'bg-cyan-500/15 text-cyan-300'
                          : toneColor === 'amber'
                            ? 'bg-amber-500/15 text-amber-300'
                            : 'bg-rose-500/15 text-rose-300'
                    }`}
                  >
                    {health.label}
                  </Badge>
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400 mt-0.5">
                  Avaliação integrada dos 4 pilares em {monthLabel(month)}
                </DialogDescription>
              </div>
            </div>
          </div>
        </DialogHeader>

        {/* Body */}
        <div className="flex-1 overflow-y-auto overscroll-contain p-5 space-y-4">
          {/* Hero Score Card */}
          <div className="p-4 rounded-2xl bg-slate-900/80 border border-white/[0.08] flex flex-col sm:flex-row items-center gap-4">
            <div className="relative size-20 shrink-0 flex items-center justify-center">
              <svg className="size-full -rotate-90" viewBox="0 0 36 36">
                <circle
                  cx="18"
                  cy="18"
                  r="15"
                  className="stroke-white/10"
                  strokeWidth="3.5"
                  fill="none"
                />
                <circle
                  cx="18"
                  cy="18"
                  r="15"
                  className={`${
                    toneColor === 'emerald'
                      ? 'stroke-emerald-400'
                      : toneColor === 'cyan'
                        ? 'stroke-cyan-400'
                        : toneColor === 'amber'
                          ? 'stroke-amber-400'
                          : 'stroke-rose-400'
                  } transition-all duration-700`}
                  strokeWidth="3.5"
                  strokeDasharray={2 * Math.PI * 15}
                  strokeDashoffset={2 * Math.PI * 15 * (1 - health.score / 100)}
                  strokeLinecap="round"
                  fill="none"
                />
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center">
                <span className="font-mono font-extrabold text-xl text-white leading-none">
                  {health.score}
                </span>
                <span className="text-[9px] text-slate-400 font-mono">/100</span>
              </div>
            </div>

            <div className="flex-1 text-center sm:text-left space-y-1.5">
              <div className="text-sm font-bold text-white">
                Status: {health.label} ({health.score} pontos)
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">{health.description}</p>
              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2 pt-1">
                <Button
                  type="button"
                  size="sm"
                  onClick={handleAskCopilot}
                  className="h-7 text-xs bg-emerald-600 hover:bg-emerald-500 text-white px-3"
                >
                  <Sparkles className="size-3.5 mr-1.5" />
                  Analisar Plano no Copiloto IA ✨
                </Button>
                {onOpenBudgets && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      onOpenChange(false)
                      onOpenBudgets()
                    }}
                    className="h-7 text-xs bg-slate-800 hover:bg-slate-700 text-slate-200 border-white/10 px-2.5"
                  >
                    <Sliders className="size-3 mr-1.5 text-emerald-400" />
                    Envelopes
                  </Button>
                )}
                {onOpenInvestments && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      onOpenChange(false)
                      onOpenInvestments()
                    }}
                    className="h-7 text-xs bg-slate-800 hover:bg-slate-700 text-slate-200 border-white/10 px-2.5"
                  >
                    <TrendingUp className="size-3 mr-1.5 text-cyan-400" />
                    Carteira & Reserva
                  </Button>
                )}
              </div>
            </div>
          </div>

          {/* Resumo Executivo do Mês */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <div className="p-2.5 rounded-xl bg-slate-900/50 border border-white/[0.06]">
              <div className="text-[10px] text-slate-400">Entrada Prevista</div>
              <div className="text-xs font-mono font-bold text-slate-100 mt-0.5">
                {formatBRL(summary?.income || 0)}
              </div>
            </div>
            <div className="p-2.5 rounded-xl bg-slate-900/50 border border-white/[0.06]">
              <div className="text-[10px] text-slate-400">Custo de Vida Líquido</div>
              <div className="text-xs font-mono font-bold text-cyan-300 mt-0.5">
                {formatBRL(livingCost)}
              </div>
            </div>
            <div className="p-2.5 rounded-xl bg-slate-900/50 border border-white/[0.06]">
              <div className="text-[10px] text-slate-400">Investido no Mês</div>
              <div className="text-xs font-mono font-bold text-emerald-400 mt-0.5">
                {formatBRL(summary?.totalInvested || 0)}
              </div>
            </div>
            <div className="p-2.5 rounded-xl bg-slate-900/50 border border-white/[0.06]">
              <div className="text-[10px] text-slate-400">Saldo Livre Real</div>
              <div
                className={`text-xs font-mono font-bold mt-0.5 ${
                  (summary?.freeBalance || 0) >= 0 ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                {formatBRL(summary?.freeBalance || 0)}
              </div>
            </div>
          </div>

          {/* Grid dos 4 Pilares */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Pilar 1: Poupança & Margem */}
            <div className="p-3.5 rounded-xl bg-slate-900/60 border border-white/[0.07] space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                  <TrendingUp className="size-3.5 text-emerald-400" />
                  1. Poupança & Margem
                </span>
                <span className="font-mono text-xs font-extrabold text-emerald-400">
                  {health.savingsScore}/35 pts
                </span>
              </div>
              <div className="w-full bg-slate-950 rounded-full h-1.5 overflow-hidden">
                <div
                  className="bg-emerald-500 h-full rounded-full transition-all"
                  style={{ width: `${Math.min(100, (health.savingsScore / 35) * 100)}%` }}
                />
              </div>
              <div className="text-[11px] text-slate-400 space-y-0.5">
                <div>
                  Investido no mês:{' '}
                  <strong className="text-slate-200 font-mono">
                    {formatBRL(summary?.totalInvested || 0)} ({health.savingsRatePercentage}%)
                  </strong>
                </div>
                <div>
                  Potencial c/ Saldo Livre:{' '}
                  <strong className="text-emerald-300 font-mono">
                    {health.potentialSavingsRatePercentage ?? health.savingsRatePercentage}% da renda
                  </strong>{' '}
                  (Meta: 20%)
                </div>
              </div>
            </div>

            {/* Pilar 2: Custo de Vida Real */}
            <div className="p-3.5 rounded-xl bg-slate-900/60 border border-white/[0.07] space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                  <Home className="size-3.5 text-cyan-400" />
                  2. Custo de Vida Real
                </span>
                <span className="font-mono text-xs font-extrabold text-cyan-400">
                  {health.spendingLimitScore}/35 pts
                </span>
              </div>
              <div className="w-full bg-slate-950 rounded-full h-1.5 overflow-hidden">
                <div
                  className="bg-cyan-500 h-full rounded-full transition-all"
                  style={{ width: `${Math.min(100, (health.spendingLimitScore / 35) * 100)}%` }}
                />
              </div>
              <div className="text-[11px] text-slate-400 space-y-0.5">
                <div>
                  Custo líquido (sem aportes):{' '}
                  <strong className="text-slate-200 font-mono">
                    {formatBRL(livingCost)} ({health.spendingRatioPercentage}%)
                  </strong>
                </div>
                <div>
                  Teto saudável recomendado:{' '}
                  <strong className="text-cyan-300 font-mono">Até 70% da renda</strong>
                </div>
              </div>
            </div>

            {/* Pilar 3: Reserva de Emergência */}
            <div className="p-3.5 rounded-xl bg-slate-900/60 border border-white/[0.07] space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                  <ShieldCheck className="size-3.5 text-amber-400" />
                  3. Reserva de Emergência
                </span>
                <span className="font-mono text-xs font-extrabold text-amber-400">
                  {health.emergencyReserveScore}/30 pts
                </span>
              </div>
              <div className="w-full bg-slate-950 rounded-full h-1.5 overflow-hidden">
                <div
                  className="bg-amber-500 h-full rounded-full transition-all"
                  style={{ width: `${Math.min(100, (health.emergencyReserveScore / 30) * 100)}%` }}
                />
              </div>
              <div className="text-[11px] text-slate-400 space-y-0.5">
                <div>
                  Reserva acumulada:{' '}
                  <strong className="text-slate-200 font-mono">
                    {formatBRL(reserveBalance)} ({health.emergencyCoverageMonths} meses)
                  </strong>
                </div>
                <div>
                  Meta de blindagem (6 meses):{' '}
                  <strong className="text-amber-300 font-mono">{formatBRL(targetReserve6m)}</strong>
                </div>
              </div>
            </div>

            {/* Pilar 4: Disciplina nos Envelopes */}
            <div className="p-3.5 rounded-xl bg-slate-900/60 border border-white/[0.07] space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                  <Target className="size-3.5 text-purple-400" />
                  4. Disciplina nos Envelopes
                </span>
                <span
                  className={`font-mono text-xs font-extrabold ${
                    overspentEnvelopes === 0 && configuredEnvelopes > 0
                      ? 'text-emerald-400'
                      : overspentEnvelopes > 0
                        ? 'text-rose-400'
                        : 'text-slate-400'
                  }`}
                >
                  {configuredEnvelopes > 0
                    ? overspentEnvelopes === 0
                      ? '+4 pts Bônus'
                      : `-${Math.min(8, overspentEnvelopes * 3)} pts`
                    : 'Não configurado'}
                </span>
              </div>
              <div className="w-full bg-slate-950 rounded-full h-1.5 overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all ${
                    overspentEnvelopes > 0 ? 'bg-rose-500' : 'bg-purple-500'
                  }`}
                  style={{ width: `${configuredEnvelopes > 0 ? adherencePct : 0}%` }}
                />
              </div>
              <div className="text-[11px] text-slate-400 space-y-0.5">
                <div>
                  Aderência orçamentária:{' '}
                  <strong className="text-slate-200 font-mono">
                    {configuredEnvelopes > 0 ? `${adherencePct}%` : '—'}
                  </strong>{' '}
                  ({configuredEnvelopes} categorias)
                </div>
                <div>
                  Status:{' '}
                  <strong className="text-purple-300">
                    {configuredEnvelopes === 0
                      ? 'Configure tetos para ganhar até +4 pts'
                      : overspentEnvelopes === 0
                        ? 'Todos os envelopes dentro do teto!'
                        : `${overspentEnvelopes} envelope(s) acima do teto`}
                  </strong>
                </div>
              </div>
            </div>
          </div>

          {/* Alavancas Práticas para Subir o Score */}
          <div className="p-4 rounded-2xl bg-gradient-to-br from-emerald-950/35 via-slate-900/90 to-slate-900 border border-emerald-500/25 space-y-2.5">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold uppercase tracking-wider text-emerald-300 flex items-center gap-1.5">
                <Sparkles className="size-3.5 text-emerald-400" />
                Alavancas Práticas para Subir seu Score
              </h4>
              <span className="text-[10px] text-slate-400">Coach IA</span>
            </div>

            <div className="space-y-2">
              {(health.actionableTips || []).map((tip, idx) => (
                <div
                  key={idx}
                  className="p-2.5 rounded-xl bg-slate-950/70 border border-white/[0.06] flex items-start gap-2.5 text-xs text-slate-200"
                >
                  {tip.includes('Parabéns') ? (
                    <CheckCircle2 className="size-4 text-emerald-400 shrink-0 mt-0.5" />
                  ) : (
                    <AlertTriangle className="size-4 text-amber-400 shrink-0 mt-0.5" />
                  )}
                  <span className="leading-relaxed">{tip}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-white/[0.08] bg-slate-900/60 flex items-center justify-between gap-2 shrink-0">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onOpenChange(false)}
            className="text-xs text-slate-400 hover:text-white"
          >
            Fechar
          </Button>
          <Button
            type="button"
            size="sm"
            onClick={handleAskCopilot}
            className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs"
          >
            <Sparkles className="size-3.5 mr-1.5" />
            Simular Melhorias no Chat IA
            <ArrowRight className="size-3.5 ml-1.5" />
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
