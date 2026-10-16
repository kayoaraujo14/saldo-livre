import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import type { Card } from '@/types'
import { CreditCard, Plus, Trash2, Users, Tag, Calendar } from 'lucide-react'

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
    setShareWithPartner(Boolean(partner))
    setThirdPartiesInput('Mãe')
    setColor('violet')
  }

  const handleEdit = (card: Card) => {
    setEditingCard(card)
    setCardName(card.cardName)
    setClosingDay(card.closingDay)
    setDueDay(card.dueDay)
    setShareWithPartner(card.sharedWithUsers.length > 0)
    setThirdPartiesInput(card.frequentThirdParties.join(', '))
    setColor(card.color || 'violet')
  }

  const handleSave = async () => {
    if (!editingCard || !me || !cardName.trim()) return

    const parties = thirdPartiesInput
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)

    const sharedWithUsers = shareWithPartner && partner ? [{ uid: partner.uid, email: '', name: partner.name }] : []

    const toSave: Card = {
      ...editingCard,
      cardName: cardName.trim(),
      closingDay: Number(closingDay),
      dueDay: Number(dueDay),
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
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="p-2 rounded-xl bg-violet-500/10 text-violet-400 border border-violet-500/20">
                <CreditCard className="size-5" />
              </div>
              <div>
                <DialogTitle>Cartões de Crédito Compartilhados</DialogTitle>
                <DialogDescription>
                  Separa compras compartilhadas e compras de terceiros da fatura principal
                </DialogDescription>
              </div>
            </div>
            {!editingCard && (
              <Button size="sm" onClick={handleOpenNew} className="bg-violet-600 hover:bg-violet-500 text-white gap-1.5">
                <Plus className="size-4" /> Novo Cartão
              </Button>
            )}
          </div>
        </DialogHeader>

        {editingCard ? (
          <div className="space-y-4 py-2 border border-slate-800 rounded-xl p-4 bg-slate-900/50">
            <h4 className="text-sm font-semibold text-slate-200">
              {editingCard.id.startsWith('new_') ? 'Cadastrar Novo Cartão' : `Editar ${editingCard.cardName}`}
            </h4>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1.5 sm:col-span-2">
                <label className="text-xs font-medium text-slate-300">Nome do Cartão (Ex: Nubank Ultravioleta)</label>
                <Input
                  value={cardName}
                  onChange={(e) => setCardName(e.target.value)}
                  placeholder="Nome do cartão"
                  className="bg-slate-900 border-slate-700"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Dia de Fechamento</label>
                <Input
                  type="number"
                  min={1}
                  max={31}
                  value={closingDay}
                  onChange={(e) => setClosingDay(Number(e.target.value))}
                  className="bg-slate-900 border-slate-700"
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Dia de Vencimento</label>
                <Input
                  type="number"
                  min={1}
                  max={31}
                  value={dueDay}
                  onChange={(e) => setDueDay(Number(e.target.value))}
                  className="bg-slate-900 border-slate-700"
                />
              </div>

              <div className="space-y-1.5 sm:col-span-2">
                <label className="text-xs font-medium text-slate-300">
                  Terceiros Frequentes (separados por vírgula)
                </label>
                <Input
                  value={thirdPartiesInput}
                  onChange={(e) => setThirdPartiesInput(e.target.value)}
                  placeholder="Mãe, Irmão, Amigo"
                  className="bg-slate-900 border-slate-700"
                />
                <p className="text-[11px] text-slate-400">
                  Gastos com estes nomes geram linha neutra no saldo e entram em "A Receber de Terceiros".
                </p>
              </div>

              {partner && (
                <div className="sm:col-span-2 flex items-center justify-between p-3 rounded-lg bg-slate-800/60 border border-slate-700/60">
                  <div className="flex items-center gap-2">
                    <Users className="size-4 text-violet-400" />
                    <span className="text-xs text-slate-200">
                      Compartilhar com <strong>{partner.name}</strong>
                    </span>
                  </div>
                  <input
                    type="checkbox"
                    checked={shareWithPartner}
                    onChange={(e) => setShareWithPartner(e.target.checked)}
                    className="size-4 accent-violet-600 rounded cursor-pointer"
                  />
                </div>
              )}

              <div className="sm:col-span-2 space-y-1.5">
                <label className="text-xs font-medium text-slate-300">Cor do Cartão</label>
                <div className="flex gap-2">
                  {['violet', 'orange', 'slate', 'emerald', 'cyan', 'rose'].map((c) => (
                    <button
                      key={c}
                      type="button"
                      onClick={() => setColor(c)}
                      className={`size-7 rounded-full border-2 transition-transform cursor-pointer ${
                        color === c ? 'scale-110 border-white ring-2 ring-violet-500/50' : 'border-transparent opacity-70 hover:opacity-100'
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

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" size="sm" onClick={() => setEditingCard(null)}>
                Cancelar
              </Button>
              <Button size="sm" onClick={handleSave} className="bg-violet-600 hover:bg-violet-500 text-white">
                Salvar Cartão
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-3 py-2 max-h-[60vh] overflow-y-auto pr-1">
            {cards.length === 0 ? (
              <div className="text-center py-8 text-slate-400 text-sm">
                Nenhum cartão cadastrado ainda. Clique em "Novo Cartão" para começar.
              </div>
            ) : (
              cards.map((card) => {
                const isOwner = card.ownerUid === me?.uid
                const isShared = card.sharedWithUsers.length > 0 || card.sharedWithUids.length > 0
                return (
                  <div
                    key={card.id}
                    className="p-4 rounded-xl border border-slate-800 bg-slate-900/60 hover:border-slate-700 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                  >
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-slate-100 text-sm">{card.cardName}</span>
                        {isOwner ? (
                          <Badge variant="outline" className="text-[10px] bg-slate-800 text-slate-300">
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
                        <span className="flex items-center gap-1">
                          <Calendar className="size-3.5 text-slate-500" />
                          Fecha dia {card.closingDay} · Vence dia {card.dueDay}
                        </span>
                        {card.frequentThirdParties?.length > 0 && (
                          <span className="flex items-center gap-1">
                            <Tag className="size-3.5 text-slate-500" />
                            Terceiros: {card.frequentThirdParties.join(', ')}
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center">
                      <Button variant="outline" size="sm" onClick={() => handleEdit(card)} className="text-xs h-8">
                        Editar
                      </Button>
                      {isOwner && (
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => handleDelete(card.id)}
                          className="text-slate-400 hover:text-rose-400 h-8 w-8"
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      )}
                    </div>
                  </div>
                )
              })
            )}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
