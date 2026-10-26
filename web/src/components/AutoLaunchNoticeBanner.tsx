import { useMemo } from 'react'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL } from '@/lib/utils'
import { Zap, Check, X, ArrowRight, CalendarClock } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface AutoLaunchNoticeBannerProps {
  onGoToTransactions?: () => void
}

export function AutoLaunchNoticeBanner({ onGoToTransactions }: AutoLaunchNoticeBannerProps) {
  const { autoLaunchedNotice, dismissAutoLaunchNotice, recurring, monthTransactions, month, me } = useFinance()

  // 2. Card informativo opcional: Próximos vencimentos nos próximos 3 dias que ainda não foram lançados
  const upcomingBills = useMemo(() => {
    const currentDay = new Date().getDate()
    return (recurring || [])
      .filter((b) => b.active && b.ownerUid === me?.uid)
      .filter((b) => {
        const diff = b.dueDay - currentDay
        const alreadyLaunched = (monthTransactions || []).some(
          (t) => t.recurringBillId === b.id && t.competenceMonth === month
        )
        return !alreadyLaunched && diff > 0 && diff <= 3
      })
  }, [recurring, me?.uid, monthTransactions, month])

  // 1. Banner para lançamentos efetuados automaticamente
  if (autoLaunchedNotice && autoLaunchedNotice.bills.length > 0) {
    const isSingle = autoLaunchedNotice.bills.length === 1
    const firstBill = autoLaunchedNotice.bills[0]

    return (
      <div className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-amber-950/40 via-slate-900/90 to-amber-950/30 border border-amber-500/30 p-3.5 sm:p-4 shadow-xl backdrop-blur-xl animate-in fade-in slide-in-from-top-2 duration-300">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3 min-w-0">
            <div className="size-9 sm:size-10 rounded-xl bg-gradient-to-tr from-amber-500/25 to-orange-500/25 border border-amber-500/40 text-amber-400 flex items-center justify-center shrink-0 mt-0.5 shadow-sm">
              <Zap className="size-4 sm:size-5" />
            </div>

            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-[10px] font-bold tracking-wider uppercase px-2 py-0.5 rounded-full bg-amber-500/15 border border-amber-500/30 text-amber-300">
                  ⚡ Lançamento Automático
                </span>
                <span className="text-xs text-slate-400">
                  {isSingle ? 'Conta fixa lançada no extrato' : `${autoLaunchedNotice.bills.length} contas lançadas`}
                </span>
              </div>

              <div className="mt-1">
                {isSingle ? (
                  <p className="text-xs sm:text-sm text-slate-200">
                    A conta <strong className="text-white font-semibold">{firstBill.description}</strong> no valor de{' '}
                    <strong className="text-amber-300 font-mono font-semibold">{formatBRL(firstBill.amount)}</strong> foi lançada automaticamente como estimada no seu extrato deste mês.
                  </p>
                ) : (
                  <p className="text-xs sm:text-sm text-slate-200">
                    Suas contas fixas programadas foram lançadas automaticamente no seu mês:{' '}
                    <span className="text-amber-200 font-medium">
                      {autoLaunchedNotice.bills.map((b) => `${b.description} (${formatBRL(b.amount)})`).join(', ')}
                    </span>
                    .
                  </p>
                )}
              </div>

              {/* Botões de Ação */}
              <div className="flex items-center gap-2 mt-3 flex-wrap">
                {onGoToTransactions && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={onGoToTransactions}
                    className="h-7 text-xs px-2.5 bg-amber-500/10 border-amber-500/30 text-amber-200 hover:bg-amber-500/20 hover:text-white rounded-lg cursor-pointer gap-1"
                  >
                    <span>Ver no Extrato</span>
                    <ArrowRight className="size-3" />
                  </Button>
                )}

                <Button
                  size="sm"
                  variant="ghost"
                  onClick={dismissAutoLaunchNotice}
                  className="h-7 text-xs px-2.5 text-slate-400 hover:text-white hover:bg-white/5 rounded-lg cursor-pointer gap-1"
                >
                  <Check className="size-3" />
                  <span>Entendido</span>
                </Button>
              </div>
            </div>
          </div>

          {/* Botão fechar */}
          <button
            type="button"
            onClick={dismissAutoLaunchNotice}
            className="text-slate-500 hover:text-slate-300 p-1 rounded-lg hover:bg-white/5 transition-colors cursor-pointer shrink-0"
            title="Dispensar aviso"
          >
            <X className="size-4" />
          </button>
        </div>
      </div>
    )
  }

  if (upcomingBills.length > 0) {
    const nextBill = upcomingBills[0]
    const daysLeft = nextBill.dueDay - new Date().getDate()

    return (
      <div className="rounded-xl bg-slate-900/60 border border-slate-800/80 px-3.5 py-2.5 flex items-center justify-between gap-3 text-xs">
        <div className="flex items-center gap-2.5 min-w-0">
          <CalendarClock className="size-4 text-cyan-400 shrink-0" />
          <p className="text-slate-300 truncate">
            Próximo compromisso programado:{' '}
            <strong className="text-white font-medium">{nextBill.description}</strong>{' '}
            <span className="text-slate-400">({daysLeft === 1 ? 'vence amanhã' : `vence em ${daysLeft} dias`} · {formatBRL(nextBill.defaultAmount)})</span>
          </p>
        </div>
        <span className="text-[11px] text-cyan-400 font-semibold shrink-0 hidden sm:inline">
          Lançamento automático ativo
        </span>
      </div>
    )
  }

  return null
}
