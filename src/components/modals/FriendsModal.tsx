import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import type { PendingChargeGroup } from '@/types'
import { friendShareOf } from '@/lib/finance'
import { formatBRL, formatShortDate } from '@/lib/utils'
import {
  Users2,
  UserPlus,
  CheckCircle2,
  XCircle,
  Clock,
  CreditCard,
  AlertTriangle,
  Trash2,
  Send,
  ShieldCheck,
  ArrowUpRight,
  ArrowDownLeft,
  Percent,
  Layers,
  ChevronDown,
  ChevronUp,
} from 'lucide-react'

interface FriendsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function FriendsModal({ open, onOpenChange }: FriendsModalProps) {
  const {
    friends,
    pendingFriendRequests,
    pendingChargeGroups,
    sendFriendRequest,
    respondFriendRequest,
    removeFriend,
    respondChargeRequest,
    cards,
    summary,
  } = useFinance()

  const [activeTab, setActiveTab] = useState<'approvals' | 'friends' | 'statement'>(
    pendingChargeGroups.length > 0 ? 'approvals' : 'friends',
  )
  const [inviteEmail, setInviteEmail] = useState('')
  const [sendingInvite, setSendingInvite] = useState(false)
  const [inviteFeedback, setInviteFeedback] = useState<{ text: string; success: boolean } | null>(null)
  const [actionFeedback, setActionFeedback] = useState<{ text: string; success: boolean } | null>(null)
  const [processingGroupId, setProcessingGroupId] = useState<string | null>(null)
  const [expandedGroupIds, setExpandedGroupIds] = useState<Record<string, boolean>>({})

  const toggleGroupExpand = (groupId: string) => {
    setExpandedGroupIds((prev) => ({ ...prev, [groupId]: !prev[groupId] }))
  }

  const handleSendInvite = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!inviteEmail.trim()) return
    setSendingInvite(true)
    setInviteFeedback(null)
    try {
      const res = await sendFriendRequest(inviteEmail.trim())
      setInviteFeedback({ text: res.message, success: res.success })
      if (res.success) setInviteEmail('')
    } finally {
      setSendingInvite(false)
    }
  }

  const handleChargeResponse = async (group: PendingChargeGroup, accept: boolean) => {
    setProcessingGroupId(group.id)
    setActionFeedback(null)
    try {
      await respondChargeRequest(group.primaryTx, accept)
      setActionFeedback({
        text: accept
          ? group.isInstallment
            ? `Compra parcelada "${group.baseDescription}" (${group.installmentsCount} parcelas de ${formatBRL(group.perInstallmentFriendShare)}) aceita com sucesso! Entrou no seu comprometimento mensal.`
            : `Gasto "${group.baseDescription}" aceito com sucesso! Entrou no seu comprometimento.`
          : group.isInstallment
            ? `Compra parcelada "${group.baseDescription}" recusada.`
            : `Gasto "${group.baseDescription}" recusado.`,
        success: accept,
      })
      setTimeout(() => setActionFeedback(null), 4500)
    } finally {
      setProcessingGroupId(null)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[85vh] flex flex-col p-6">
        <DialogHeader className="space-y-1">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-violet-500/10 text-violet-400 border border-violet-500/20">
              <Users2 className="size-5" />
            </div>
            <div>
              <DialogTitle className="text-base sm:text-lg">Rede de Amizades & Cobranças</DialogTitle>
              <DialogDescription className="text-xs">
                Controle de compras divididas ou feitas no cartão de amigos com aceite mútuo
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {/* Tab Navigation */}
        <div className="flex gap-2 p-1 bg-slate-900 border border-slate-800 rounded-xl mt-2">
          <button
            type="button"
            onClick={() => setActiveTab('approvals')}
            className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'approvals'
                ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Clock className="size-3.5" />
            <span>Aprovações Pendentes</span>
            {pendingChargeGroups.length > 0 && (
              <span className="size-4 rounded-full bg-amber-500 text-slate-950 text-[10px] font-bold flex items-center justify-center">
                {pendingChargeGroups.length}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('friends')}
            className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'friends'
                ? 'bg-violet-500/20 text-violet-300 border border-violet-500/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Users2 className="size-3.5" />
            <span>Meus Amigos ({friends.length})</span>
            {pendingFriendRequests.length > 0 && (
              <span className="size-4 rounded-full bg-violet-500 text-white text-[10px] font-bold flex items-center justify-center">
                {pendingFriendRequests.length}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('statement')}
            className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'statement'
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <CreditCard className="size-3.5" />
            <span>Balanço</span>
          </button>
        </div>

        {/* Action Feedback Banner */}
        {actionFeedback && (
          <div
            className={`p-2.5 rounded-lg text-xs font-medium flex items-center gap-2 animate-in fade-in ${
              actionFeedback.success
                ? 'bg-emerald-500/15 border border-emerald-500/30 text-emerald-300'
                : 'bg-rose-500/15 border border-rose-500/30 text-rose-300'
            }`}
          >
            {actionFeedback.success ? <CheckCircle2 className="size-4 shrink-0" /> : <XCircle className="size-4 shrink-0" />}
            <span>{actionFeedback.text}</span>
          </div>
        )}

        {/* Tab 1: Aprovações de Cobrança Pendentes */}
        {activeTab === 'approvals' && (
          <div className="py-3 space-y-3 overflow-y-auto pr-1 flex-1">
            {pendingChargeGroups.length === 0 ? (
              <div className="p-8 text-center rounded-xl bg-slate-900/40 border border-slate-800 space-y-2">
                <CheckCircle2 className="size-8 text-emerald-400/60 mx-auto" />
                <p className="text-sm font-medium text-slate-300">Nenhuma despesa pendente de aceite</p>
                <p className="text-xs text-slate-500 max-w-sm mx-auto">
                  Quando um amigo cadastrar uma compra feita no cartão dele para você pagar (integral ou dividida por cota), ela aparecerá aqui agrupada por compra para você aprovar antes de comprometer seu saldo.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/20 flex items-start gap-2 text-xs text-amber-300">
                  <AlertTriangle className="size-4 shrink-0 mt-0.5" />
                  <span>
                    Ao aceitar uma despesa ou compra parcelada, a sua <strong>cota atribuída</strong> entrará no cálculo das suas saídas comprometidas mês a mês, atualizando o seu Saldo Livre.
                  </span>
                </div>

                {pendingChargeGroups.map((group) => {
                  const card = cards.find((c) => c.id === group.cardId)
                  const isProcessing = processingGroupId === group.id
                  const isExpanded = Boolean(expandedGroupIds[group.id])

                  return (
                    <div
                      key={group.id}
                      className="p-4 rounded-xl border border-amber-500/30 bg-slate-900/70 space-y-3 shadow-lg"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="space-y-1 flex-1">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-semibold text-slate-100 text-sm">{group.baseDescription}</span>
                            {group.isInstallment && (
                              <Badge variant="outline" className="text-[10px] bg-violet-500/10 text-violet-300 border-violet-500/30 gap-1 font-medium">
                                <Layers className="size-2.5" />
                                Compra Parcelada ({group.installmentsCount}x)
                              </Badge>
                            )}
                            {group.friendSharePct < 100 ? (
                              <Badge variant="outline" className="text-[10px] bg-cyan-500/10 text-cyan-300 border-cyan-500/30 gap-1">
                                <Percent className="size-2.5" />
                                Sua Cota: {group.friendSharePct}%
                              </Badge>
                            ) : (
                              <Badge variant="outline" className="text-[10px] bg-amber-500/10 text-amber-300 border-amber-500/30">
                                Cota Integral (100%)
                              </Badge>
                            )}
                          </div>

                          <div className="text-xs text-slate-400 flex items-center gap-2 flex-wrap">
                            {group.isInstallment ? (
                              <span>Período: {formatShortDate(group.firstDueDate)} até {formatShortDate(group.lastDueDate)}</span>
                            ) : (
                              <span>Vencimento: {formatShortDate(group.firstDueDate)}</span>
                            )}
                            {card && (
                              <>
                                <span>•</span>
                                <span>Cartão: {card.cardName}</span>
                              </>
                            )}
                            {group.senderName && (
                              <>
                                <span>•</span>
                                <span className="text-amber-300">Enviado por: {group.senderName}</span>
                              </>
                            )}
                          </div>
                        </div>

                        <div className="text-right shrink-0">
                          <span className="text-base font-bold text-amber-300 block font-mono">
                            {formatBRL(group.friendShareAmount)}
                          </span>
                          {group.isInstallment ? (
                            <>
                              <span className="text-[10px] text-slate-300 font-medium block">
                                {group.installmentsCount}x de {formatBRL(group.perInstallmentFriendShare)}/mês
                              </span>
                              {group.friendSharePct < 100 && (
                                <span className="text-[10px] text-slate-500 block">
                                  ({group.friendSharePct}% do total de {formatBRL(group.totalAmount)})
                                </span>
                              )}
                            </>
                          ) : (
                            <span className="text-[10px] text-slate-500 block">
                              {group.friendSharePct < 100 ? `${group.friendSharePct}% de ${formatBRL(group.totalAmount)}` : '100% da compra'}
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Expandable installment breakdown if multi-installment */}
                      {group.isInstallment && (
                        <div className="pt-1">
                          <button
                            type="button"
                            onClick={() => toggleGroupExpand(group.id)}
                            className="flex items-center gap-1 text-[11px] text-violet-400 hover:text-violet-300 font-medium cursor-pointer transition-colors"
                          >
                            {isExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
                            <span>{isExpanded ? 'Ocultar cronograma das parcelas' : `Ver cronograma das ${group.installmentsCount} parcelas`}</span>
                          </button>

                          {isExpanded && (
                            <div className="mt-2 rounded-lg bg-slate-950/80 border border-slate-800 p-2.5 space-y-1">
                              <div className="grid grid-cols-3 text-[10px] font-semibold text-slate-400 px-2 pb-1 border-b border-slate-800">
                                <span>Parcela</span>
                                <span>Vencimento</span>
                                <span className="text-right">Sua Cota</span>
                              </div>
                              <div className="max-h-36 overflow-y-auto space-y-0.5 divide-y divide-slate-800/50">
                                {group.items.map((it) => (
                                  <div key={it.id} className="grid grid-cols-3 text-xs text-slate-300 px-2 py-1 items-center">
                                    <span className="font-mono text-slate-400">
                                      Parcela {it.installmentNumber || 1}/{group.installmentsCount}
                                    </span>
                                    <span className="text-slate-300">{formatShortDate(it.dueDate)}</span>
                                    <span className="text-right font-mono font-medium text-amber-300">
                                      {formatBRL(friendShareOf(it))}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      )}

                      <div className="flex items-center justify-between pt-2 border-t border-slate-800 gap-2 flex-wrap">
                        <span className="text-[11px] text-slate-400 flex-1 min-w-[200px]">
                          {group.isInstallment
                            ? `Compra parcelada em ${group.installmentsCount}x de ${formatBRL(group.perInstallmentAmount)}. Sua parte é de ${formatBRL(group.perInstallmentFriendShare)}/mês (${group.friendSharePct}%). Ao aceitar, todas as parcelas serão lançadas no seu mês a mês.`
                            : group.friendSharePct < 100
                              ? `Divisão: você arca com ${group.friendSharePct}% (${formatBRL(group.friendShareAmount)}) e ${group.senderName} com o restante.`
                              : 'Gasto no cartão para você pagar integralmente.'}
                        </span>

                        <div className="flex items-center gap-2 ml-auto shrink-0">
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={isProcessing}
                            onClick={() => handleChargeResponse(group, false)}
                            className="h-7 text-xs border-rose-500/30 text-rose-300 hover:bg-rose-500/10 hover:text-rose-200 cursor-pointer"
                          >
                            <XCircle className="size-3.5 mr-1" />
                            {group.isInstallment ? 'Recusar Compra' : 'Recusar'}
                          </Button>
                          <Button
                            size="sm"
                            disabled={isProcessing}
                            onClick={() => handleChargeResponse(group, true)}
                            className="h-7 text-xs bg-emerald-600 hover:bg-emerald-500 text-white font-medium cursor-pointer shadow-sm"
                          >
                            <CheckCircle2 className="size-3.5 mr-1" />
                            {isProcessing
                              ? 'Aceitando...'
                              : group.isInstallment
                                ? `Aceitar Compra (${formatBRL(group.friendShareAmount)})`
                                : `Aceitar (${formatBRL(group.friendShareAmount)})`}
                          </Button>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Meus Amigos */}
        {activeTab === 'friends' && (
          <div className="py-3 space-y-4 overflow-y-auto pr-1 flex-1">
            {/* Invite Form */}
            <form onSubmit={handleSendInvite} className="p-3.5 rounded-xl bg-slate-900/80 border border-slate-800 space-y-2">
              <label className="text-xs font-semibold text-slate-200 flex items-center gap-1.5">
                <UserPlus className="size-3.5 text-violet-400" />
                <span>Adicionar Novo Amigo por E-mail</span>
              </label>
              <div className="flex gap-2">
                <Input
                  type="email"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="Digite o e-mail Google do seu amigo..."
                  className="bg-slate-950 border-slate-700 text-xs h-9"
                  required
                />
                <Button
                  type="submit"
                  size="sm"
                  disabled={sendingInvite || !inviteEmail.trim()}
                  className="bg-violet-600 hover:bg-violet-500 text-white text-xs h-9 px-3 gap-1.5 cursor-pointer shrink-0"
                >
                  <Send className="size-3.5" />
                  <span>{sendingInvite ? 'Enviando...' : 'Convidar'}</span>
                </Button>
              </div>

              {inviteFeedback && (
                <p className={`text-xs ${inviteFeedback.success ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {inviteFeedback.text}
                </p>
              )}
            </form>

            {/* Pending Received Invites */}
            {pendingFriendRequests.length > 0 && (
              <div className="space-y-2">
                <span className="text-xs font-semibold text-amber-300 flex items-center gap-1.5">
                  <Clock className="size-3.5" />
                  <span>Convites Recebidos ({pendingFriendRequests.length})</span>
                </span>
                <div className="space-y-2">
                  {pendingFriendRequests.map((req) => (
                    <div
                      key={req.id}
                      className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-between gap-3"
                    >
                      <div>
                        <span className="text-xs font-semibold text-slate-100 block">{req.requesterName}</span>
                        <span className="text-[11px] text-slate-400">{req.requesterEmail}</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => respondFriendRequest(req.id, false)}
                          className="h-7 text-xs text-slate-400 hover:text-rose-300"
                        >
                          Recusar
                        </Button>
                        <Button
                          size="sm"
                          onClick={() => respondFriendRequest(req.id, true)}
                          className="h-7 text-xs bg-emerald-600 hover:bg-emerald-500 text-white font-medium"
                        >
                          Aceitar
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Friends List */}
            <div className="space-y-2">
              <span className="text-xs font-semibold text-slate-300">Amigos Conectados</span>
              {friends.length === 0 ? (
                <div className="p-6 text-center rounded-xl bg-slate-900/30 border border-slate-800 text-xs text-slate-500">
                  Nenhum amigo conectado ainda. Convide seus amigos pelo formulário acima!
                </div>
              ) : (
                <div className="space-y-2">
                  {friends.map((f) => (
                    <div
                      key={f.friendshipId}
                      className="p-3 rounded-xl bg-slate-900/60 border border-slate-800 flex items-center justify-between gap-3 hover:border-slate-700 transition-colors"
                    >
                      <div className="flex items-center gap-2.5">
                        <div className="size-8 rounded-full bg-violet-500/20 text-violet-300 border border-violet-500/30 flex items-center justify-center font-bold text-xs">
                          {f.name.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <span className="text-xs font-semibold text-slate-200 block">{f.name}</span>
                          <span className="text-[10px] text-slate-400">{f.email}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="text-[10px] bg-emerald-500/10 text-emerald-400 border-emerald-500/20 gap-1">
                          <ShieldCheck className="size-3" />
                          Amigo Conectado
                        </Badge>
                        <button
                          type="button"
                          onClick={() => {
                            if (confirm(`Deseja remover a conexão de amizade com ${f.name}?`)) {
                              removeFriend(f.friendshipId)
                            }
                          }}
                          className="p-1 rounded text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
                          title="Remover Amigo"
                        >
                          <Trash2 className="size-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Tab 3: Balanço de Amigos */}
        {activeTab === 'statement' && (
          <div className="py-3 space-y-3 overflow-y-auto pr-1 flex-1">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="p-3.5 rounded-xl bg-emerald-950/30 border border-emerald-500/30 flex flex-col justify-between">
                <div className="flex items-center gap-1.5 text-xs text-emerald-300 font-medium">
                  <ArrowDownLeft className="size-4" />
                  <span>A Receber de Amigos no Cartão:</span>
                </div>
                <span className="text-xl font-bold text-emerald-400 mt-2 font-mono">
                  {formatBRL(summary?.receivableFromFriends || 0)}
                </span>
                <span className="text-[10px] text-slate-400 mt-1">
                  Compras que você passou no seu cartão para amigos que já aceitaram pagar.
                </span>
              </div>

              <div className="p-3.5 rounded-xl bg-rose-950/30 border border-rose-500/30 flex flex-col justify-between">
                <div className="flex items-center gap-1.5 text-xs text-rose-300 font-medium">
                  <ArrowUpRight className="size-4" />
                  <span>A Pagar para Amigos / Terceiros:</span>
                </div>
                <span className="text-xl font-bold text-rose-400 mt-2 font-mono">
                  {formatBRL(summary?.payableToOthers || 0)}
                </span>
                <span className="text-[10px] text-slate-400 mt-1">
                  Compras feitas em cartões de amigos aceitas por você para pagar neste mês.
                </span>
              </div>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
