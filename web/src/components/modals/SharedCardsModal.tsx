import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import type { Card } from '@/types'
import { formatBRL } from '@/lib/utils'
import { CreditCard, Plus, Trash2, Users, Tag, Calendar, Edit2, Sparkles, ShieldCheck } from 'lucide-react'

interface SharedCardsModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function SharedCardsModal({ open, onOpenChange }: SharedCardsModalProps) {
  const { cards, saveCard, deleteCard, me, partner } = useFinance()
  const [editingCard, setEditingCard] = useState<Card | null>(null)
  const [cardName, setCardName] = useState('')
  const [closingDay, setClosingDay] = useState(3)
  const [dueDay, setDueDay] = useState(10)
  const [limit, setLimit] = useState<number | string>(5000)
  const [shareWithPartner, setShareWithPartner] = useState(true)
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
      sharedWithUsers: partner ? [{ uid: partner.uid, email: '', name: partner.name }] : [],
      sharedWithUids: partner ? [partner.uid] : [],
      frequentThirdParties: ['Mãe'],
      color: 'violet',
    })
    setCardName('')
    setClosingDay(5)
    setDueDay(12)
    setLimit(5000)
    setShareWithPartner(Boolean(partner))
    setThirdPartiesInput('Mãe')
    setColor('violet')
  }

  const handleEdit = (card: Card) => {
    setEditingCard(card)
    setCardName(card.cardName)
    setClosingDay(card.closingDay)
    setDueDay(card.dueDay)
    setLimit(card.limit ?? 5000)
    setShareWithPartner((card.sharedWithUsers?.length ?? 0) > 0)
    setThirdPartiesInput((card.frequentThirdParties || []).join(', '))
    setColor(card.color || 'violet')
  }

  const handleSave = async () => {
    if (!editingCard || !me || !cardName.trim()) return

    const parties = thirdPartiesInput
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)

    const sharedWithUsers = shareWithPartner && partner ? [{ uid: partner.uid, email: '', name: partner.name }] : []

    const parsedLimit = Number(limit) > 0 ? Number(limit) : null

    const toSave: Card = {
      ...editingCard,
      cardName: cardName.trim(),
      closingDay: Number(closingDay),
      dueDay: Number(dueDay),
      limit: parsedLimit,
      sharedWithUsers,
      sharedWithUids: sharedWithUsers.map((u) => u.uid),
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
                  Gerencie limites, datas de fechamento e cartões compartilhados
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
                  Terceiros Frequentes (separados por vírgula)
                </label>
                <Input
                  value={thirdPartiesInput}
                  onChange={(e) => setThirdPartiesInput(e.target.value)}
                  placeholder="Mãe, Irmão, Carlos"
                  className="h-10 text-xs bg-slate-900 border-slate-700/80 rounded-xl"
                />
                <p className="text-[11px] text-slate-400">
                  Gastos lançados para estes terceiros entram no cálculo de <strong>A Receber</strong> sem impactar negativamente seu saldo pessoal.
                </p>
              </div>

              {partner && (
                <div className="sm:col-span-2 flex items-center justify-between p-3.5 rounded-xl bg-slate-900/80 border border-slate-800">
                  <div className="flex items-center gap-2.5">
                    <Users className="size-4 text-violet-400" />
                    <div>
                      <span className="text-xs font-semibold text-slate-200 block">
                        Compartilhar cartão com {partner.name}
                      </span>
                      <span className="text-[11px] text-slate-400">
                        Permite que seu parceiro selecione este cartão ao lançar despesas
                      </span>
                    </div>
                  </div>
                  <input
                    type="checkbox"
                    checked={shareWithPartner}
                    onChange={(e) => setShareWithPartner(e.target.checked)}
                    className="size-5 accent-emerald-500 rounded cursor-pointer"
                  />
                </div>
              )}

              <div className="sm:col-span-2 space-y-2">
                <label className="text-xs font-semibold text-slate-300 block">Cor de Identificação</label>
                <div className="flex gap-2.5 items-center">
                  {['violet', 'orange', 'slate', 'emerald', 'cyan', 'rose'].map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      className={`size-8 rounded-full border-2 transition-transform cursor-pointer ${
                        color === c ? 'scale-110 border-white ring-2 ring-emerald-500/40 shadow-md' : 'border-transparent opacity-60 hover:opacity-100'
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
                const isShared = (card.sharedWithUsers?.length ?? 0) > 0 || (card.sharedWithUids?.length ?? 0) > 0
                return (
                  <div
                    key={card.id}
                    className="p-4 rounded-2xl border border-white/[0.06] bg-slate-950/70 hover:border-slate-700 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-md"
                  >
                    <div className="space-y-2">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-bold text-slate-100 text-sm">{card.cardName}</span>
                        {isOwner ? (
                          <Badge variant="outline" className="text-[10px] bg-slate-800 text-slate-300 border-slate-700">
                            Meu Cartão
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="text-[10px] bg-violet-950/60 text-violet-300 border-violet-800">
                            Titular: {card.ownerName}
                          </Badge>
                        )}
                        {isShared && (
                          <Badge variant="outline" className="text-[10px] bg-sky-950/50 text-sky-300 border-sky-800 gap-1">
                            <Users className="size-3" /> Compartilhado
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
                        {card.frequentThirdParties?.length > 0 && (
                          <span className="flex items-center gap-1.5">
                            <Tag className="size-3.5 text-slate-500" />
                            Terceiros: <strong className="text-slate-200">{card.frequentThirdParties.join(', ')}</strong>
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleEdit(card)}
                        className="text-xs h-8 rounded-lg border-slate-800 hover:text-white"
                      >
                        <Edit2 className="size-3 mr-1" />
                        <span>Editar</span>
                      </Button>
                      {isOwner && (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => handleDelete(card.id)}
                          className="text-slate-400 hover:text-rose-400 h-8 w-8 rounded-lg cursor-pointer"
                          title="Excluir Cartão"
                        >
                          <Trash2 className="size-3.5" />
                        </Button>
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
