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
      <DialogContent className="sm:max-w-2xl w-full max-h-[90vh] flex flex-col p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-2 border-b border-white/[0.06]">
          <div className="flex items-center gap-2.5">
            <div className="size-9 rounded-xl bg-gradient-to-tr from-violet-500/20 to-purple-500/20 border border-violet-500/30 text-violet-400 flex items-center justify-center shrink-0">
              <Users2 className="size-5" />
            </div>
            <div>
              <DialogTitle className="text-base sm:text-lg font-bold text-white flex items-center gap-2">
                <span>Rede de Amizades & Aprovações</span>
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400">
                Gerencie conexões de amigos e aprove cobranças compartilhadas no cartão
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        {/* Segmented Tab Navigation */}
        <div className="flex p-1 bg-slate-900/90 border border-slate-800 rounded-xl mt-3 gap-1">
          <button
            type="button"
            onClick={() => setActiveTab('approvals')}
            className={`flex-1 py-2 px-2.5 rounded-lg text-xs font-bold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'approvals'
                ? 'bg-amber-500/20 text-amber-300 border border-amber-500/40 shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Clock className="size-3.5" />
            <span>Aprovações</span>
            {pendingChargeGroups.length > 0 && (
              <span className="px-1.5 py-0.2 rounded-full bg-amber-500 text-slate-950 text-[10px] font-bold animate-pulse">
                {pendingChargeGroups.length}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('friends')}
            className={`flex-1 py-2 px-2.5 rounded-lg text-xs font-bold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'friends'
                ? 'bg-violet-500/20 text-violet-300 border border-violet-500/40 shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Users2 className="size-3.5" />
            <span>Amigos ({friends.length})</span>
            {pendingFriendRequests.length > 0 && (
              <span className="px-1.5 py-0.2 rounded-full bg-violet-500 text-white text-[10px] font-bold animate-pulse">
                {pendingFriendRequests.length}
              </span>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('statement')}
            className={`flex-1 py-2 px-2.5 rounded-lg text-xs font-bold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
              activeTab === 'statement'
                ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-sm'
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
            className={`p-3 rounded-xl text-xs font-medium flex items-center gap-2 mt-2 animate-in fade-in ${
              actionFeedback.success
                ? 'bg-emerald-500/15 border border-emerald-500/30 text-emerald-300'
                : 'bg-rose-500/15 border border-rose-500/30 text-rose-300'
            }`}
          >
            {actionFeedback.success ? <CheckCircle2 className="size-4 shrink-0" /> : <XCircle className="size-4 shrink-0" />}
            <span>{actionFeedback.text}</span>
          </div>
        )}

        {/* ============================================================== */}
        {/* TAB 1: APROVAÇÕES DE COBRANÇA PENDENTES                        */}
        {/* ============================================================== */}
        {activeTab === 'approvals' && (
          <div className="py-2 space-y-3 overflow-y-auto flex-1 pr-1">
            {pendingChargeGroups.length === 0 ? (
              <div className="p-8 text-center rounded-2xl bg-slate-950/60 border border-white/[0.06] space-y-2.5 my-2">
                <div className="size-12 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto">
                  <CheckCircle2 className="size-6" />
                </div>
                <h4 className="text-sm font-bold text-white">Nenhuma despesa pendente de aprovação</h4>
                <p className="text-xs text-slate-400 max-w-sm mx-auto leading-relaxed">
                  Quando um amigo cadastrar uma compra feita no cartão dele com a sua parte, ela aparecerá aqui agrupada para você revisar e aprovar antes de comprometer seu Saldo Livre.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-start gap-2.5 text-xs text-amber-300">
                  <AlertTriangle className="size-4 shrink-0 mt-0.5" />
                  <span className="leading-relaxed">
                    Ao aceitar, a sua <strong>cota atribuída</strong> entrará no cálculo das suas saídas comprometidas mês a mês, atualizando com precisão o seu Saldo Livre.
                  </span>
                </div>

                {pendingChargeGroups.map((group) => {
                  const card = cards.find((c) => c.id === group.cardId)
                  const isProcessing = processingGroupId === group.id
                  const isExpanded = Boolean(expandedGroupIds[group.id])

                  return (
                    <div
                      key={group.id}
                      className="p-4 rounded-2xl border border-amber-500/30 bg-slate-950/70 space-y-3 shadow-lg hover:border-amber-500/50 transition-all"
                    >
                      {/* Top Row: Description, Badges and Value */}
                      <div className="flex items-start justify-between gap-3">
                        <div className="space-y-1.5 flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <h4 className="font-bold text-slate-100 text-sm leading-tight">{group.baseDescription}</h4>
                            {group.isInstallment && (
                              <Badge variant="outline" className="text-[10px] bg-violet-500/15 text-violet-300 border-violet-500/30 gap-1 font-semibold">
                                <Layers className="size-2.5" />
                                {group.installmentsCount}x Parcelas
                              </Badge>
                            )}
                            {group.friendSharePct < 100 ? (
                              <Badge variant="outline" className="text-[10px] bg-cyan-500/15 text-cyan-300 border-cyan-500/30 gap-1 font-semibold">
                                <Percent className="size-2.5" />
                                Sua Cota: {group.friendSharePct}%
                              </Badge>
                            ) : (
                              <Badge variant="outline" className="text-[10px] bg-amber-500/15 text-amber-300 border-amber-500/30 font-semibold">
                                100% Para Você
                              </Badge>
                            )}
                          </div>

                          <div className="text-xs text-slate-400 flex items-center gap-2 flex-wrap">
                            {group.isInstallment ? (
                              <span>Período: <strong className="text-slate-300">{formatShortDate(group.firstDueDate)}</strong> até <strong className="text-slate-300">{formatShortDate(group.lastDueDate)}</strong></span>
                            ) : (
                              <span>Vencimento: <strong className="text-slate-300">{formatShortDate(group.firstDueDate)}</strong></span>
                            )}
                            {card && (
                              <>
                                <span>•</span>
                                <span className="inline-flex items-center gap-1 text-slate-300">
                                  <CreditCard className="size-3 text-sky-400" /> {card.cardName}
                                </span>
                              </>
                            )}
                            {group.senderName && (
                              <>
                                <span>•</span>
                                <span className="text-amber-300 font-medium">Por: {group.senderName}</span>
                              </>
                            )}
                          </div>
                        </div>

                        {/* Amount */}
                        <div className="text-right shrink-0">
                          <span className="text-lg sm:text-xl font-black text-amber-300 block font-mono">
                            {formatBRL(group.friendShareAmount)}
                          </span>
                          {group.isInstallment ? (
                            <>
                              <span className="text-[11px] text-slate-300 font-semibold block">
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
                              {group.friendSharePct < 100 ? `${group.friendSharePct}% do total` : 'Total da compra'}
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Expandable installment schedule */}
                      {group.isInstallment && (
                        <div className="pt-1">
                          <button
                            type="button"
                            onClick={() => toggleGroupExpand(group.id)}
                            className="flex items-center gap-1.5 text-xs text-violet-400 hover:text-violet-300 font-semibold cursor-pointer transition-colors"
                          >
                            {isExpanded ? <ChevronUp className="size-3.5" /> : <ChevronDown className="size-3.5" />}
                            <span>{isExpanded ? 'Ocultar cronograma' : `Ver cronograma das ${group.installmentsCount} parcelas`}</span>
                          </button>

                          {isExpanded && (
                            <div className="mt-2 rounded-xl bg-slate-950 border border-slate-800 p-3 space-y-1 animate-in fade-in">
                              <div className="grid grid-cols-3 text-[10px] font-bold uppercase tracking-wider text-slate-400 px-2 pb-1 border-b border-slate-800">
                                <span>Parcela</span>
                                <span>Vencimento</span>
                                <span className="text-right">Sua Cota</span>
                              </div>
                              <div className="max-h-36 overflow-y-auto space-y-0.5 divide-y divide-slate-800/40">
                                {group.items.map((it) => (
                                  <div key={it.id} className="grid grid-cols-3 text-xs text-slate-300 px-2 py-1.5 items-center">
                                    <span className="font-mono text-slate-400">
                                      {it.installmentNumber || 1}/{group.installmentsCount}
                                    </span>
                                    <span className="text-slate-300 font-mono">{formatShortDate(it.dueDate)}</span>
                                    <span className="text-right font-mono font-bold text-amber-300">
                                      {formatBRL(friendShareOf(it))}
                                    </span>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      )}

                      {/* Bottom Action Bar */}
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between pt-2.5 border-t border-slate-800/80 gap-3">
                        <span className="text-[11px] text-slate-400">
                          {group.isInstallment
                            ? `Ao aceitar, ${group.installmentsCount} parcelas serão incluídas automaticamente no seu Saldo Livre mensal.`
                            : 'Ao aceitar, este valor será incluído como compromisso a pagar no seu mês.'}
                        </span>

                        <div className="flex items-center gap-2 self-end sm:self-auto shrink-0">
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={isProcessing}
                            onClick={() => handleChargeResponse(group, false)}
                            className="h-8 text-xs border-rose-500/30 text-rose-300 hover:bg-rose-500/10 hover:text-rose-200 cursor-pointer rounded-lg"
                          >
                            <XCircle className="size-3.5 mr-1" />
                            <span>Recusar</span>
                          </Button>
                          <Button
                            size="sm"
                            disabled={isProcessing}
                            onClick={() => handleChargeResponse(group, true)}
                            className="h-8 text-xs bg-emerald-600 hover:bg-emerald-500 text-white font-bold cursor-pointer rounded-lg shadow-sm"
                          >
                            <CheckCircle2 className="size-3.5 mr-1" />
                            <span>
                              {isProcessing ? 'Aceitando...' : `Aceitar (${formatBRL(group.friendShareAmount)})`}
                            </span>
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

        {/* ============================================================== */}
        {/* TAB 2: MEUS AMIGOS                                             */}
        {/* ============================================================== */}
        {activeTab === 'friends' && (
          <div className="py-2 space-y-4 overflow-y-auto flex-1 pr-1">
            {/* Invite Form */}
            <form onSubmit={handleSendInvite} className="p-4 rounded-2xl bg-slate-950/60 border border-white/[0.07] space-y-2.5">
              <label className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                <UserPlus className="size-4 text-violet-400" />
                <span>Adicionar Novo Amigo por E-mail</span>
              </label>
              <div className="flex gap-2">
                <Input
                  type="email"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="Digite o e-mail da conta do seu amigo..."
                  className="bg-slate-900 border-slate-700/80 text-xs sm:text-sm h-10 rounded-xl"
                  required
                />
                <Button
                  type="submit"
                  size="sm"
                  disabled={sendingInvite || !inviteEmail.trim()}
                  className="bg-violet-600 hover:bg-violet-500 text-white text-xs font-semibold h-10 px-4 gap-1.5 cursor-pointer shrink-0 rounded-xl shadow-md"
                >
                  <Send className="size-3.5" />
                  <span>{sendingInvite ? 'Enviando...' : 'Convidar'}</span>
                </Button>
              </div>

              {inviteFeedback && (
                <p className={`text-xs font-medium pt-1 ${inviteFeedback.success ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {inviteFeedback.text}
                </p>
              )}
            </form>

            {/* Pending Received Invites */}
            {pendingFriendRequests.length > 0 && (
              <div className="space-y-2">
                <span className="text-xs font-bold text-amber-300 flex items-center gap-1.5">
                  <Clock className="size-3.5" />
                  <span>Convites Recebidos ({pendingFriendRequests.length})</span>
                </span>
                <div className="space-y-2">
                  {pendingFriendRequests.map((req) => (
                    <div
                      key={req.id}
                      className="p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/25 flex items-center justify-between gap-3"
                    >
                      <div>
                        <span className="text-xs font-bold text-slate-100 block">{req.requesterName}</span>
                        <span className="text-[11px] text-slate-400">{req.requesterEmail}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => respondFriendRequest(req.id, false)}
                          className="h-8 text-xs text-slate-400 hover:text-rose-300 rounded-lg cursor-pointer"
                        >
                          Recusar
                        </Button>
                        <Button
                          size="sm"
                          onClick={() => respondFriendRequest(req.id, true)}
                          className="h-8 text-xs bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-lg cursor-pointer"
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
              <span className="text-xs font-bold text-slate-300 block">Amigos Conectados ({friends.length})</span>
              {friends.length === 0 ? (
                <div className="p-8 text-center rounded-2xl bg-slate-950/60 border border-white/[0.06] text-xs text-slate-500 space-y-1">
                  <p className="font-semibold text-slate-400">Nenhum amigo conectado ainda.</p>
                  <p>Convide seus amigos pelo formulário acima para dividir despesas com facilidade.</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {friends.map((f) => (
                    <div
                      key={f.friendshipId}
                      className="p-3.5 rounded-xl bg-slate-950/70 border border-white/[0.06] flex items-center justify-between gap-3 hover:border-slate-700 transition-colors"
                    >
                      <div className="flex items-center gap-3">
                        <div className="size-9 rounded-xl bg-violet-500/20 text-violet-300 border border-violet-500/30 flex items-center justify-center font-bold text-xs shrink-0">
                          {f.name.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <span className="text-xs font-bold text-slate-100 block">{f.name}</span>
                          <span className="text-[11px] text-slate-400">{f.email}</span>
                        </div>
                      </div>

                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="text-[10px] bg-emerald-500/10 text-emerald-400 border-emerald-500/20 gap-1 hidden xs:inline-flex">
                          <ShieldCheck className="size-3" />
                          Conectado
                        </Badge>
                        <button
                          type="button"
                          onClick={() => {
                            if (confirm(`Deseja remover a conexão de amizade com ${f.name}?`)) {
                              removeFriend(f.friendshipId)
                            }
                          }}
                          className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer"
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

        {/* ============================================================== */}
        {/* TAB 3: BALANÇO & ACERTOS                                        */}
        {/* ============================================================== */}
        {activeTab === 'statement' && (
          <div className="py-2 space-y-3 overflow-y-auto flex-1 pr-1">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div className="p-4 rounded-2xl bg-emerald-950/25 border border-emerald-500/30 flex flex-col justify-between gap-3">
                <div className="flex items-center gap-2 text-xs text-emerald-300 font-semibold">
                  <div className="p-1.5 rounded-lg bg-emerald-500/15 border border-emerald-500/30">
                    <ArrowDownLeft className="size-4 text-emerald-400" />
                  </div>
                  <span>A Receber de Amigos</span>
                </div>
                <div>
                  <span className="text-2xl font-black text-emerald-400 font-mono">
                    {formatBRL(summary?.receivableFromFriends || 0)}
                  </span>
                  <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">
                    Compras no seu cartão para amigos que já aceitaram o comprometimento.
                  </p>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-rose-950/25 border border-rose-500/30 flex flex-col justify-between gap-3">
                <div className="flex items-center gap-2 text-xs text-rose-300 font-semibold">
                  <div className="p-1.5 rounded-lg bg-rose-500/15 border border-rose-500/30">
                    <ArrowUpRight className="size-4 text-rose-400" />
                  </div>
                  <span>A Pagar para Amigos</span>
                </div>
                <div>
                  <span className="text-2xl font-black text-rose-400 font-mono">
                    {formatBRL(summary?.payableToOthers || 0)}
                  </span>
                  <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">
                    Compras feitas em cartões de amigos aceitas por você para pagamento neste mês.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
