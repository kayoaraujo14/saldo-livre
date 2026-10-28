import { useState } from 'react'
import { useFinance } from '@/store/FinanceContext'
import { formatBRL } from '@/lib/utils'
import {
  Zap,
  Check,
  CheckCheck,
  X,
  CalendarClock,
  Link2,
  CreditCard,
  Receipt,
  ArrowDownLeft,
  Sparkles,
  ArrowRight,
} from 'lucide-react'
import { Button } from '@/components/ui/button'

interface AutoLaunchNoticeBannerProps {
  onGoToTransactions?: () => void
}

export function AutoLaunchNoticeBanner({ onGoToTransactions }: AutoLaunchNoticeBannerProps) {
  const {
    recurring,
    monthTransactions,
    month,
    me,
    pendingRecurringLaunches,
    confirmLaunchRecurring,
    confirmLaunchAllRecurring,
    linkManualTransactionToRecurring,
    skipRecurringForMonth,
  } = useFinance()

  const [loadingActionId, setLoadingActionId] = useState<string | null>(null)

  // AÇÕES PARA PENDÊNCIAS DE RECORRENTES
  const handleConfirmSingle = async (
    candidate: (typeof pendingRecurringLaunches)[0],
    asPaid = false,
  ) => {
    setLoadingActionId(candidate.id)
    try {
      await confirmLaunchRecurring(candidate, { asPaid })
    } finally {
      setLoadingActionId(null)
    }
  }

  const handleLinkManual = async (candidateId: string, manualTxId: string) => {
    setLoadingActionId(candidateId)
    try {
      await linkManualTransactionToRecurring(candidateId, manualTxId)
    } finally {
      setLoadingActionId(null)
    }
  }

  const handleSkip = (candidateId: string) => {
    skipRecurringForMonth(candidateId)
  }

  const handleConfirmAll = async (asPaid = false) => {
    setLoadingActionId('all')
    try {
      // Confirma apenas os que não tem conflito de match manual, para segurança
      const safeToLaunch = pendingRecurringLaunches.filter((c) => !c.matchedManualTx)
      if (safeToLaunch.length > 0) {
        await confirmLaunchAllRecurring(safeToLaunch, asPaid)
      }
    } finally {
      setLoadingActionId(null)
    }
  }

  // Próximos vencimentos nos próximos 3 dias que ainda não viraram pendências
  const currentDay = new Date().getDate()
  const upcomingBills = (recurring || [])
    .filter((b) => b.active && b.ownerUid === me?.uid)
    .filter((b) => {
      const diff = b.dueDay - currentDay
      const alreadyLaunched = (monthTransactions || []).some(
        (t) => t.recurringBillId === b.id && t.competenceMonth === month,
      )
      const isAlreadyPending = pendingRecurringLaunches.some((c) => c.id === b.id)
      return !alreadyLaunched && !isAlreadyPending && diff > 0 && diff <= 3
    })

  return (
    <div className="space-y-3">
      {/* Central de Confirmação Inteligente de Recorrentes Prontas */}
      {pendingRecurringLaunches.length > 0 && (
        <div className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-amber-950/35 via-slate-900/90 to-cyan-950/30 border border-amber-500/30 p-3.5 sm:p-4 shadow-xl backdrop-blur-xl animate-in fade-in slide-in-from-top-2 duration-300">
          {/* Header do Card */}
          <div className="flex items-start justify-between gap-3 mb-3">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="size-8 rounded-lg bg-gradient-to-tr from-amber-500/20 to-orange-500/20 border border-amber-500/35 text-amber-400 flex items-center justify-center shrink-0">
                <Zap className="size-4" />
              </div>
              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <h3 className="text-sm font-semibold text-white tracking-tight">
                    Contas Fixas & Renda Programadas
                  </h3>
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-500/20 border border-amber-500/30 text-amber-300">
                    {pendingRecurringLaunches.length} pronta(s)
                  </span>
                </div>
                <p className="text-xs text-slate-400">
                  Valide seus compromissos antes de lançar no extrato.
                </p>
              </div>
            </div>

            {/* Ações do cabeçalho */}
            <div className="flex items-center gap-2 shrink-0">
              {onGoToTransactions && (
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={onGoToTransactions}
                  className="h-7 text-xs px-2 text-slate-400 hover:text-white rounded-lg gap-1 hidden sm:flex cursor-pointer"
                >
                  <span>Extrato</span>
                  <ArrowRight className="size-3" />
                </Button>
              )}
              {pendingRecurringLaunches.filter((c) => !c.matchedManualTx).length > 1 && (
                <div className="hidden sm:flex items-center gap-1.5">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={loadingActionId === 'all'}
                    onClick={() => handleConfirmAll(true)}
                    className="h-7 text-xs px-2.5 bg-emerald-500/15 border-emerald-500/35 text-emerald-300 hover:bg-emerald-500/25 rounded-lg gap-1 cursor-pointer"
                  >
                    <CheckCheck className="size-3.5" />
                    <span>Confirmar Tudo Pago</span>
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={loadingActionId === 'all'}
                    onClick={() => handleConfirmAll(false)}
                    className="h-7 text-xs px-2.5 bg-amber-500/10 border-amber-500/30 text-amber-300 hover:bg-amber-500/20 rounded-lg gap-1 cursor-pointer"
                  >
                    <span>Lançar Previsto</span>
                  </Button>
                </div>
              )}
            </div>
          </div>

          {/* Lista de itens pendentes */}
          <div className="space-y-2 mt-2">
            {pendingRecurringLaunches.map((item) => {
              const isLoading = loadingActionId === item.id
              const hasMatch = Boolean(item.matchedManualTx)

              return (
                <div
                  key={item.id}
                  className={`rounded-xl border p-2.5 sm:p-3 transition-colors ${
                    hasMatch
                      ? 'bg-amber-950/20 border-amber-500/30'
                      : 'bg-slate-950/40 border-slate-800/80 hover:border-slate-700'
                  }`}
                >
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                    {/* Informações da conta */}
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div
                        className={`size-8 rounded-lg flex items-center justify-center shrink-0 border ${
                          item.type === 'income'
                            ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
                            : item.cardId
                              ? 'bg-purple-500/10 border-purple-500/30 text-purple-400'
                              : 'bg-slate-800 border-slate-700 text-slate-300'
                        }`}
                      >
                        {item.type === 'income' ? (
                          <ArrowDownLeft className="size-4" />
                        ) : item.cardId ? (
                          <CreditCard className="size-4" />
                        ) : (
                          <Receipt className="size-4" />
                        )}
                      </div>

                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-xs sm:text-sm font-medium text-white truncate">
                            {item.description}
                          </span>
                          <span
                            className={`text-[10px] font-mono font-semibold px-1.5 py-0.2 rounded ${
                              item.type === 'income'
                                ? 'bg-emerald-500/15 text-emerald-300'
                                : 'bg-slate-800 text-slate-300'
                            }`}
                          >
                            {formatBRL(item.amount)}
                          </span>
                        </div>
                        <div className="flex items-center gap-1.5 text-[11px] text-slate-400 mt-0.5">
                          <span>Vencimento dia {item.dueDay}</span>
                          <span>•</span>
                          <span>{item.category}</span>
                        </div>
                      </div>
                    </div>

                    {/* Botões de Ação */}
                    <div className="flex items-center gap-1.5 self-end sm:self-center shrink-0">
                      {hasMatch && item.matchedManualTx ? (
                        <>
                          <Button
                            size="sm"
                            disabled={isLoading}
                            onClick={() => handleLinkManual(item.id, item.matchedManualTx!.id)}
                            className="h-7 text-xs px-2.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-semibold rounded-lg gap-1 cursor-pointer"
                            title="Vincular a conta fixa à transação manual já lançada"
                          >
                            <Link2 className="size-3" />
                            <span>Vincular ao Lançamento</span>
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={isLoading}
                            onClick={() => handleSkip(item.id)}
                            className="h-7 text-xs px-2 text-slate-400 hover:text-slate-200 rounded-lg cursor-pointer"
                            title="Dispensar sugestão"
                          >
                            <X className="size-3" />
                          </Button>
                        </>
                      ) : (
                        <>
                          {item.paymentMethod === 'credit_card' || item.cardId ? (
                            <Button
                              size="sm"
                              disabled={isLoading}
                              onClick={() => handleConfirmSingle(item, false)}
                              className="h-7 text-xs px-2.5 bg-violet-600 hover:bg-violet-500 text-white rounded-lg gap-1 cursor-pointer"
                              title="Lança na fatura do cartão vinculado como Pendente"
                            >
                              <CreditCard className="size-3" />
                              <span>Lançar na Fatura</span>
                            </Button>
                          ) : (
                            <>
                              <Button
                                size="sm"
                                disabled={isLoading}
                                onClick={() => handleConfirmSingle(item, true)}
                                className="h-7 text-xs px-2.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg gap-1 cursor-pointer"
                                title={
                                  item.type === 'income'
                                    ? 'Confirmar que já foi recebido'
                                    : 'Confirmar que já foi pago no extrato'
                                }
                              >
                                <Check className="size-3" />
                                <span>{item.type === 'income' ? 'Já Recebi' : 'Já Paguei'}</span>
                              </Button>
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={isLoading}
                                onClick={() => handleConfirmSingle(item, false)}
                                className="h-7 text-xs px-2 bg-amber-500/10 border-amber-500/30 text-amber-300 hover:bg-amber-500/20 rounded-lg cursor-pointer"
                                title="Lançar no extrato como Previsto (a pagar)"
                              >
                                <span>Previsto</span>
                              </Button>
                            </>
                          )}
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={isLoading}
                            onClick={() => handleSkip(item.id)}
                            className="h-7 text-xs px-2 bg-slate-800/80 border-slate-700 text-slate-300 hover:text-white rounded-lg cursor-pointer"
                            title="Marcar como já lançada manualmente ou pular este mês"
                          >
                            <span>Pular</span>
                          </Button>
                        </>
                      )}
                    </div>
                  </div>

                  {/* Aviso contextual caso tenha encontrado transação manual correspondente */}
                  {hasMatch && item.matchedManualTx && (
                    <div className="mt-2 pt-2 border-t border-amber-500/20 flex items-center gap-1.5 text-[11px] text-amber-200/90">
                      <Sparkles className="size-3 text-amber-400 shrink-0" />
                      <span>
                        Encontramos um lançamento manual similar:{' '}
                        <strong className="text-white font-medium">
                          &quot;{item.matchedManualTx.description}&quot;
                        </strong>{' '}
                        de{' '}
                        <strong className="text-amber-300 font-mono font-medium">
                          {formatBRL(item.matchedManualTx.totalAmount)}
                        </strong>{' '}
                        em {item.matchedManualTx.dueDate}.
                      </span>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Aviso sutil de Próximos Vencimentos (Informativo, não invasivo) */}
      {upcomingBills.length > 0 && pendingRecurringLaunches.length === 0 && (
        <div className="rounded-xl bg-slate-900/60 border border-slate-800/80 px-3.5 py-2.5 flex items-center justify-between gap-3 text-xs">
          <div className="flex items-center gap-2.5 min-w-0">
            <CalendarClock className="size-4 text-cyan-400 shrink-0" />
            <p className="text-slate-300 truncate">
              Próximo compromisso programado:{' '}
              <strong className="text-white font-medium">{upcomingBills[0].description}</strong>{' '}
              <span className="text-slate-400">
                (
                {upcomingBills[0].dueDay - currentDay === 1
                  ? 'vence amanhã'
                  : `vence em ${upcomingBills[0].dueDay - currentDay} dias`}{' '}
                · {formatBRL(upcomingBills[0].defaultAmount)})
              </span>
            </p>
          </div>
          <span className="text-[11px] text-cyan-400 font-semibold shrink-0 hidden sm:inline">
            Programação ativa
          </span>
        </div>
      )}
    </div>
  )
}
