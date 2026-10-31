import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import type { Card, SharedUser } from '@/types'
import { formatBRL } from '@/lib/utils'
import {
  CreditCard,
  Plus,
  Trash2,
  Users,
  Tag,
  Calendar,
  Edit2,
  Sparkles,
  ShieldCheck,
  Eye,
  UserPlus,
  Search,
  AlertCircle,
  Check,
} from 'lucide-react'

interface SharedCardsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function SharedCardsModal({ open, onOpenChange }: SharedCardsModalProps) {
  const { cards, saveCard, deleteCard, me, partner, friends, lookupUserByEmail } = useFinance()
  const [editingCard, setEditingCard] = useState<Card | null>(null)
  const [cardName, setCardName] = useState('')
  const [closingDay, setClosingDay] = useState(3)
  const [dueDay, setDueDay] = useState(10)
  const [limit, setLimit] = useState<number | string>(5000)
  const [sharedUsers, setSharedUsers] = useState<SharedUser[]>([])
  const [selectedFriendUid, setSelectedFriendUid] = useState<string>('')
  const [inviteEmailInput, setInviteEmailInput] = useState<string>('')
  const [lookupFeedback, setLookupFeedback] = useState<{ text: string; success: boolean } | null>(null)
  const [isSearchingUser, setIsSearchingUser] = useState(false)
  const [thirdPartiesInput, setThirdPartiesInput] = useState('Mãe, Irmão')
  const [color, setColor] = useState('violet')

  const handleOpenNew = () => {
    if (!me) return
    setEditingCard({
      id: `new_${Date.now()}`,
      ownerUid: me.uid,
      ownerName: me.name,
      cardName: '',
      closingDay: 5,
      dueDay: 12,
      sharedWithUsers: [],
      sharedWithUids: [],
      frequentThirdParties: ['Mãe'],
      color: 'violet',
    })
    setCardName('')
    setClosingDay(5)
    setDueDay(12)
    setLimit(5000)
    setSharedUsers([])
    setSelectedFriendUid('')
    setInviteEmailInput('')
    setLookupFeedback(null)
    setThirdPartiesInput('Mãe')
    setColor('violet')
  }

  const handleEdit = (card: Card) => {
    if (card.ownerUid !== me?.uid) return
    setEditingCard(card)
    setCardName(card.cardName)
    setClosingDay(card.closingDay)
    setDueDay(card.dueDay)
    setLimit(card.limit ?? 5000)
    setSharedUsers(card.sharedWithUsers || [])
    setSelectedFriendUid('')
    setInviteEmailInput('')
    setLookupFeedback(null)
    setThirdPartiesInput((card.frequentThirdParties || []).join(', '))
    setColor(card.color || 'violet')
  }

  const handleAddFriend = () => {
    if (!selectedFriendUid) return
    const f = friends.find((item) => item.uid === selectedFriendUid)
    if (!f) return
    if (sharedUsers.some((u) => u.uid === f.uid)) {
      setLookupFeedback({ text: 'Este amigo já tem acesso a este cartão.', success: false })
      return
    }
    setSharedUsers((prev) => [...prev, { uid: f.uid, name: f.name, email: f.email }])
    setSelectedFriendUid('')
    setLookupFeedback({ text: `${f.name} adicionado ao cartão como visualizador.`, success: true })
    setTimeout(() => setLookupFeedback(null), 3000)
  }

  const handleAddPartner = () => {
    if (!partner) return
    if (sharedUsers.some((u) => u.uid === partner.uid)) {
      setLookupFeedback({ text: `${partner.name} já tem acesso a este cartão.`, success: false })
      return
    }
    setSharedUsers((prev) => [...prev, { uid: partner.uid, name: partner.name, email: '' }])
    setLookupFeedback({ text: `${partner.name} adicionado(a) ao cartão.`, success: true })
    setTimeout(() => setLookupFeedback(null), 3000)
  }

  const handleAddByEmail = async () => {
    const email = inviteEmailInput.trim().toLowerCase()
    if (!email) return
    if (email === me?.email?.toLowerCase()) {
      setLookupFeedback({ text: 'Você já é o titular deste cartão.', success: false })
      return
    }
    if (sharedUsers.some((u) => u.email.toLowerCase() === email)) {
      setLookupFeedback({ text: 'Este usuário já tem acesso ao cartão.', success: false })
      return
    }
    setIsSearchingUser(true)
    setLookupFeedback(null)
    try {
      const userProfile = await lookupUserByEmail(email)
      if (!userProfile) {
        setLookupFeedback({
          text: 'Nenhum usuário cadastrado com este e-mail na plataforma. Verifique a grafia.',
          success: false,
        })
        return
      }
      setSharedUsers((prev) => [
        ...prev,
        {
          uid: userProfile.uid,
          name: userProfile.displayName || userProfile.email.split('@')[0],
          email: userProfile.email,
        },
      ])
      setInviteEmailInput('')
      setLookupFeedback({
        text: `${userProfile.displayName || userProfile.email} convidado para visualizar o cartão!`,
        success: true,
      })
      setTimeout(() => setLookupFeedback(null), 3500)
    } catch {
      setLookupFeedback({ text: 'Erro ao buscar usuário. Tente novamente.', success: false })
    } finally {
      setIsSearchingUser(false)
    }
  }

  const handleRemoveSharedUser = (uidToRemove: string) => {
    setSharedUsers((prev) => prev.filter((u) => u.uid !== uidToRemove))
  }

  const handleSave = async () => {
    if (!editingCard || !me || !cardName.trim()) return

    const parties = thirdPartiesInput
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)

    const parsedLimit = Number(limit) > 0 ? Number(limit) : null

    const toSave: Card = {
      ...editingCard,
      cardName: cardName.trim(),
      closingDay: Number(closingDay),
      dueDay: Number(dueDay),
      limit: parsedLimit,
      sharedWithUsers: sharedUsers,
      sharedWithUids: sharedUsers.map((u) => u.uid),
      frequentThirdParties: parties,
      color,
    }

    await saveCard(toSave)
    setEditingCard(null)
  }

  const handleDelete = async (id: string) => {
    if (confirm('Tem certeza que deseja excluir este cartão?')) {
      await deleteCard(id)
      if (editingCard?.id === id) setEditingCard(null)
    }
  }

  const unsharedFriends = friends.filter((f) => !sharedUsers.some((u) => u.uid === f.uid))
  const partnerAlreadyShared = partner ? sharedUsers.some((u) => u.uid === partner.uid) : false

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-2 border-b border-white/[0.06]">
          <div className="flex items-center justify-between gap-3 flex-wrap min-w-0">
            <div className="flex items-center gap-2.5 min-w-0 flex-1">
              <div className="size-9 rounded-xl bg-gradient-to-tr from-sky-500/20 to-indigo-500/20 border border-sky-500/30 text-sky-400 flex items-center justify-center shrink-0">
                <CreditCard className="size-5" />
              </div>
              <div className="min-w-0 flex-1">
                <DialogTitle className="text-base sm:text-lg font-bold text-white truncate">
                  Cartões de Crédito
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400 truncate">
                  Gerencie faturas, limites e convide amigos para dividir o cartão
                </DialogDescription>
              </div>
            </div>

            {!editingCard && (
              <Button
                size="sm"
                onClick={handleOpenNew}
                className="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs h-8 px-3 gap-1.5 rounded-xl cursor-pointer shadow-sm shrink-0"
              >
                <Plus className="size-3.5" />
                <span>Novo Cartão</span>
              </Button>
            )}
          </div>
        </DialogHeader>

        {editingCard ? (
          /* Form de Edição / Cadastro de Cartão */
          <div className="space-y-4 py-2 border border-white/[0.08] rounded-2xl p-4 sm:p-5 bg-slate-950/70 animate-in fade-in">
            <div className="flex items-center justify-between">
              <h4 className="text-sm font-bold text-white flex items-center gap-1.5">
                <Sparkles className="size-4 text-emerald-400" />
                <span>{editingCard.id.startsWith('new_') ? 'Cadastrar Novo Cartão' : `Editar ${editingCard.cardName}`}</span>
              </h4>
              <Badge variant="outline" className="text-[10px] bg-slate-800 text-slate-300">
                Titular: {editingCard.ownerName}
              </Badge>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div className="space-y-1.5 sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300">Nome do Cartão (Ex: Nubank Ultravioleta)</label>
                <Input
                  value={cardName}
                  onChange={(e) => setCardName(e.target.value)}
                  placeholder="Nome do cartão"
                  className="h-10 text-xs sm:text-sm bg-slate-900 border-slate-700/80 rounded-xl"
                  required
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Dia de Fechamento da Fatura</label>
                <Input
                  type="number"
                  min={1}
                  max={31}
                  value={closingDay}
                  onChange={(e) => setClosingDay(Number(e.target.value))}
                  className="h-10 text-xs bg-slate-900 border-slate-700/80 rounded-xl font-mono"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300">Dia de Vencimento da Fatura</label>
                <Input
                  type="number"
                  min={1}
                  max={31}
                  value={dueDay}
                  onChange={(e) => setDueDay(Number(e.target.value))}
                  className="h-10 text-xs bg-slate-900 border-slate-700/80 rounded-xl font-mono"
                />
              </div>

              <div className="space-y-1.5 sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300">Limite de Crédito do Cartão (R$)</label>
                <Input
                  type="number"
                  min={0}
                  step={100}
                  value={limit}
                  onChange={(e) => setLimit(e.target.value)}
                  placeholder="Ex: 5000"
                  className="h-10 text-xs bg-slate-900 border-slate-700/80 rounded-xl font-mono"
                />
              </div>

              <div className="space-y-1.5 sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300">
                  Terceiros Externos Frequentes (separados por vírgula)
                </label>
                <Input
                  value={thirdPartiesInput}
                  onChange={(e) => setThirdPartiesInput(e.target.value)}
                  placeholder="Mãe, Irmão, Carlos"
                  className="h-10 text-xs bg-slate-900 border-slate-700/80 rounded-xl"
                />
                <p className="text-[11px] text-slate-400">
                  Terceiros externos não possuem conta no app. As compras deles aparecem apenas na fatura e em <strong>A Receber</strong> para cobrança manual.
                </p>
              </div>

              {/* SEÇÃO: Convidar Amigo da Plataforma para Dividir o Cartão */}
              <div className="sm:col-span-2 space-y-3 p-3.5 rounded-xl bg-slate-900/90 border border-slate-800">
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <div className="size-7 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center shrink-0">
                      <Users className="size-4" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-slate-200 block">
                        Dividir Cartão com Amigos da Plataforma
                      </span>
                      <span className="text-[11px] text-slate-400">
                        O amigo convidado terá visão do cartão no app dele (modo somente leitura). Ele verá suas compras e, no fechamento da fatura, receberá a parte dele no extrato.
                      </span>
                    </div>
                  </div>
                  <Badge variant="outline" className="text-[10px] bg-sky-950/40 text-sky-300 border-sky-800 shrink-0 gap-1">
                    <Eye className="size-3" /> Somente Leitura
                  </Badge>
                </div>

                {/* Usuários Já Compartilhados */}
                {sharedUsers.length > 0 ? (
                  <div className="space-y-2 pt-1">
                    <label className="text-[11px] font-semibold text-slate-300 block">
                      Pessoas com acesso ({sharedUsers.length}):
                    </label>
                    <div className="grid grid-cols-1 gap-2">
                      {sharedUsers.map((user) => (
                        <div
                          key={user.uid}
                          className="flex items-center justify-between p-2.5 rounded-lg bg-slate-950/80 border border-slate-800 text-xs"
                        >
                          <div className="flex items-center gap-2 min-w-0">
                            <div className="size-7 rounded-full bg-slate-800 border border-slate-700 text-slate-300 flex items-center justify-center font-bold text-[11px] shrink-0">
                              {user.name.slice(0, 1).toUpperCase()}
                            </div>
                            <div className="min-w-0">
                              <span className="font-semibold text-slate-200 truncate block">{user.name}</span>
                              {user.email && (
                                <span className="text-[10px] text-slate-400 truncate block">{user.email}</span>
                              )}
                            </div>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700">
                              Visualizador
                            </span>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => handleRemoveSharedUser(user.uid)}
                              className="text-slate-400 hover:text-rose-400 h-7 w-7 rounded-lg cursor-pointer"
                              title="Remover acesso"
                            >
                              <Trash2 className="size-3.5" />
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <p className="text-[11px] text-slate-500 italic">
                    Nenhum amigo convidado ainda. Selecione um amigo ou adicione por e-mail abaixo.
                  </p>
                )}

                {/* Opções de Adicionar Amigo */}
                <div className="space-y-2.5 pt-2 border-t border-slate-800/80">
                  {/* Seletor de amigos já aceitos na plataforma */}
                  {unsharedFriends.length > 0 && (
                    <div className="flex items-center gap-2">
                      <select
                        value={selectedFriendUid}
                        onChange={(e) => setSelectedFriendUid(e.target.value)}
                        className="flex-1 h-9 px-3 text-xs bg-slate-950 border border-slate-700 rounded-xl text-slate-200 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                      >
                        <option value="">Selecione um amigo da sua rede...</option>
                        {unsharedFriends.map((f) => (
                          <option key={f.uid} value={f.uid}>
                            {f.name} ({f.email})
                          </option>
                        ))}
                      </select>
                      <Button
                        type="button"
                        size="sm"
                        onClick={handleAddFriend}
                        disabled={!selectedFriendUid}
                        className="h-9 text-xs bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl gap-1 shrink-0"
                      >
                        <UserPlus className="size-3.5" />
                        <span>Adicionar</span>
                      </Button>
                    </div>
                  )}

                  {/* Adição rápida de parceiro(a) se configurado */}
                  {partner && !partnerAlreadyShared && (
                    <div>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={handleAddPartner}
                        className="text-xs h-8 border-violet-800 text-violet-300 hover:bg-violet-950/40 rounded-xl gap-1.5"
                      >
                        <Plus className="size-3" />
                        <span>Compartilhar com parceiro(a): {partner.name}</span>
                      </Button>
                    </div>
                  )}

                  {/* Buscar e convidar outro usuário da plataforma por e-mail */}
                  <div className="space-y-1">
                    <label className="text-[11px] font-semibold text-slate-400 block">
                      Ou convidar usuário cadastrado por e-mail:
                    </label>
                    <div className="flex items-center gap-2">
                      <Input
                        type="email"
                        value={inviteEmailInput}
                        onChange={(e) => setInviteEmailInput(e.target.value)}
                        placeholder="amigo@email.com"
                        className="h-9 text-xs bg-slate-950 border-slate-700/80 rounded-xl"
                      />
                      <Button
                        type="button"
                        size="sm"
                        onClick={handleAddByEmail}
                        disabled={!inviteEmailInput.trim() || isSearchingUser}
                        className="h-9 text-xs bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl gap-1.5 shrink-0"
                      >
                        {isSearchingUser ? (
                          <span className="animate-spin text-xs">⏳</span>
                        ) : (
                          <Search className="size-3.5" />
                        )}
                        <span>Convidar</span>
                      </Button>
                    </div>
                  </div>

                  {lookupFeedback && (
                    <div
                      className={`p-2 rounded-lg text-[11px] flex items-center gap-1.5 animate-in fade-in ${lookupFeedback.success
                          ? 'bg-emerald-950/40 text-emerald-300 border border-emerald-800/60'
                          : 'bg-rose-950/40 text-rose-300 border border-rose-800/60'
                        }`}
                    >
                      {lookupFeedback.success ? (
                        <Check className="size-3.5 text-emerald-400 shrink-0" />
                      ) : (
                        <AlertCircle className="size-3.5 text-rose-400 shrink-0" />
                      )}
                      <span>{lookupFeedback.text}</span>
                    </div>
                  )}
                </div>
              </div>

              <div className="sm:col-span-2 space-y-2">
                <label className="text-xs font-semibold text-slate-300 block">Cor de Identificação</label>
                <div className="flex gap-2.5 items-center">
                  {['violet', 'orange', 'slate', 'emerald', 'cyan', 'rose'].map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      className={`size-8 rounded-full border-2 transition-transform cursor-pointer ${color === c ? 'scale-110 border-white ring-2 ring-emerald-500/40 shadow-md' : 'border-transparent opacity-60 hover:opacity-100'
                        }`}
                      style={{
                        backgroundColor:
                          c === 'violet'
                            ? '#8b5cf6'
                            : c === 'orange'
                              ? '#f97316'
                              : c === 'slate'
                                ? '#475569'
                                : c === 'emerald'
                                  ? '#10b981'
                                  : c === 'cyan'
                                    ? '#06b6d4'
                                    : '#f43f5e',
                      }}
                    />
                  ))}
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-800">
              <Button variant="outline" size="sm" onClick={() => setEditingCard(null)} className="h-9 text-xs rounded-xl">
                Cancelar
              </Button>
              <Button size="sm" onClick={handleSave} className="h-9 text-xs bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-xl shadow-sm">
                Salvar Cartão
              </Button>
            </div>
          </div>
        ) : (
          /* Lista de Cartões Cadastrados */
          <div className="space-y-3 py-2 max-h-[60vh] overflow-y-auto pr-1">
            {cards.length === 0 ? (
              <div className="text-center py-10 text-slate-400 text-xs rounded-2xl bg-slate-950/60 border border-white/[0.06] space-y-2">
                <CreditCard className="size-8 mx-auto opacity-30 text-slate-500" />
                <p className="font-semibold text-slate-300">Nenhum cartão cadastrado ainda.</p>
                <p className="text-slate-500">Clique em "Novo Cartão" para começar a gerenciar seus cartões.</p>
              </div>
            ) : (
              cards.map((card) => {
                const isOwner = card.ownerUid === me?.uid
                const sharedCount = card.sharedWithUsers?.length ?? 0
                return (
                  <div
                    key={card.id}
                    className="relative overflow-hidden p-4 rounded-2xl border border-white/[0.06] bg-slate-950/70 hover:border-slate-700 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-md"
                  >
                    <CreditCard className="absolute -right-4 -bottom-4 size-28 text-white/[0.03] pointer-events-none rotate-12" />
                    <div className="space-y-2 relative z-10">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-bold text-slate-100 text-sm">{card.cardName}</span>
                        {isOwner ? (
                          <Badge variant="outline" className="text-[10px] bg-slate-800 text-slate-300 border-slate-700">
                            Meu Cartão
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="text-[10px] bg-indigo-950/60 text-indigo-300 border-indigo-800 gap-1">
                            <Eye className="size-3" /> Titular: {card.ownerName} (Somente Leitura)
                          </Badge>
                        )}
                        {sharedCount > 0 && isOwner && (
                          <Badge variant="outline" className="text-[10px] bg-sky-950/50 text-sky-300 border-sky-800 gap-1">
                            <Users className="size-3" /> Dividido com {sharedCount} {sharedCount === 1 ? 'amigo' : 'amigos'}
                          </Badge>
                        )}
                      </div>

                      <div className="flex flex-wrap items-center gap-3 text-xs text-slate-400">
                        <span className="flex items-center gap-1.5">
                          <Calendar className="size-3.5 text-slate-500" />
                          Fecha dia <strong className="text-slate-200">{card.closingDay}</strong> · Vence dia <strong className="text-slate-200">{card.dueDay}</strong>
                        </span>
                        {card.limit != null && card.limit > 0 && (
                          <span className="flex items-center gap-1.5">
                            <ShieldCheck className="size-3.5 text-emerald-400" />
                            Limite: <strong className="text-slate-200">{formatBRL(card.limit)}</strong>
                          </span>
                        )}
                        {card.sharedWithUsers?.length > 0 && isOwner && (
                          <span className="flex items-center gap-1.5 text-indigo-300">
                            <Users className="size-3.5" />
                            Amigos: <strong>{card.sharedWithUsers.map((u) => u.name).join(', ')}</strong>
                          </span>
                        )}
                        {card.frequentThirdParties?.length > 0 && (
                          <span className="flex items-center gap-1.5">
                            <Tag className="size-3.5 text-slate-500" />
                            Terceiros: <strong className="text-slate-200">{card.frequentThirdParties.join(', ')}</strong>
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                      {isOwner ? (
                        <>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleEdit(card)}
                            className="text-xs h-8 rounded-lg border-slate-800 hover:text-white"
                          >
                            <Edit2 className="size-3 mr-1" />
                            <span>Editar</span>
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => handleDelete(card.id)}
                            className="text-slate-400 hover:text-rose-400 h-8 w-8 rounded-lg cursor-pointer"
                            title="Excluir Cartão"
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </>
                      ) : (
                        <span className="text-[11px] px-2.5 py-1 rounded-lg bg-slate-900 border border-slate-800 text-slate-400">
                          Apenas Visualização
                        </span>
                      )}
                    </div>
                  </div>
                )
              })
            )}
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
