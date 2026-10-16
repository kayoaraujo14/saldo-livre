import { useState, useEffect } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFinance } from '@/store/FinanceContext'
import { CATEGORIES, type Category, type PaymentMethod, type SplitType, type Transaction, type TransactionStatus } from '@/types'
import { clampPct, effectiveAmount, friendPctOf } from '@/lib/finance'
import { todayKey, formatBRL, stripInstallmentSuffix } from '@/lib/utils'
import { CreditCard, Users2, Percent, Layers, Trash2, RefreshCw } from 'lucide-react'
import { DeleteInstallmentDialog } from './DeleteInstallmentDialog'

interface TransactionModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  editingTx?: Transaction | null
}

export function TransactionModal({ open, onOpenChange, editingTx }: TransactionModalProps) {
  const {
    createManual,
    updateTransaction,
    updateInstallmentGroup,
    changeSplit,
    changeAmount,
    cards,
    me,
    partner,
    friends,
  } = useFinance()

  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [category, setCategory] = useState<Category>('Alimentação')
  const [date, setDate] = useState(todayKey())
  const [type, setType] = useState<'expense' | 'income'>('expense')
  const [status, setStatus] = useState<TransactionStatus>('pending')
  const [splitType, setSplitType] = useState<SplitType>('mine')
  const [externalName, setExternalName] = useState('Mãe')
  const [selectedFriendUid, setSelectedFriendUid] = useState<string>('')
  const [friendSharePct, setFriendSharePct] = useState<number>(100)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('credit_card')
  const [cardId, setCardId] = useState<string>('')
  const [isReserve, setIsReserve] = useState(false)

  // Feature 3: Parcelamento
  const [isInstallment, setIsInstallment] = useState(false)
  const [installmentsCount, setInstallmentsCount] = useState(3)
  const [installmentMode, setInstallmentMode] = useState<'total' | 'per_installment'>('total')

  // Conexão de Parcelas: Editar todas de uma vez ou apenas esta
  const [editScope, setEditScope] = useState<'all' | 'single'>('all')
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)

  useEffect(() => {
    if (editingTx) {
      const isGrp = Boolean(editingTx.installmentGroupId)
      setDescription(isGrp ? stripInstallmentSuffix(editingTx.description) : editingTx.description)
      setAmount(String(effectiveAmount(editingTx)))
      setCategory(editingTx.category)
      setDate(editingTx.dueDate)
      setType(editingTx.type)
      setStatus(editingTx.status)
      setPaymentMethod(editingTx.paymentMethod)
      setCardId(editingTx.cardId || '')
      setIsReserve(Boolean(editingTx.reserveCardId))
      setIsInstallment(isGrp)
      setEditScope('all') // Padrão: atualizar toda a série parcelada

      if (editingTx.assignedFriendUid) {
        setSplitType('friend')
        setSelectedFriendUid(editingTx.assignedFriendUid)
        setFriendSharePct(friendPctOf(editingTx))
      } else {
        const hasExternal = editingTx.allocations.some((a) => a.type === 'EXTERNAL')
        if (hasExternal) {
          setSplitType('external')
          const ext = editingTx.allocations.find((a) => a.type === 'EXTERNAL')
          if (ext) setExternalName(ext.name)
        } else if (editingTx.allocations.length > 1) {
          setSplitType('split_50')
        } else if (editingTx.allocations[0]?.uid === me?.uid) {
          setSplitType('mine')
        } else {
          setSplitType('partner')
        }
      }

      setInstallmentsCount(editingTx.installmentsTotal || 3)
    } else {
      setDescription('')
      setAmount('')
      setCategory('Alimentação')
      setDate(todayKey())
      setType('expense')
      setStatus('pending')
      setSplitType('mine')
      setExternalName('Mãe')
      setSelectedFriendUid(friends[0]?.uid || '')
      setFriendSharePct(100)
      setPaymentMethod('credit_card')
      setCardId(cards[0]?.id || '')
      setIsReserve(false)
      setIsInstallment(false)
      setInstallmentsCount(3)
      setInstallmentMode('total')
      setEditScope('all')
    }
  }, [editingTx, open, cards, me, friends])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const numericAmount = parseFloat(amount.replace(',', '.'))
    if (!numericAmount || numericAmount <= 0 || !description.trim()) return

    const chosenFriend = friends.find((f) => f.uid === selectedFriendUid)
    const validPct = clampPct(friendSharePct)

    if (editingTx) {
      if (editingTx.installmentGroupId && editScope === 'all') {
        // Conexão de Parcelas: Atualiza todas as parcelas do parcelamento de uma só vez
        await updateInstallmentGroup(editingTx.installmentGroupId, {
          description: description.trim(),
          amount: numericAmount,
          category,
          date,
          status,
          paymentMethod,
          cardId: paymentMethod === 'credit_card' ? (cardId || null) : null,
          splitType,
          externalName: splitType === 'external' ? externalName : undefined,
          friend: splitType === 'friend' && chosenFriend
            ? { uid: chosenFriend.uid, name: chosenFriend.name, pct: validPct }
            : undefined,
        })
      } else {
        // Atualiza apenas a parcela individual
        await updateTransaction(editingTx.id, {
          description: description.trim(),
          category,
          dueDate: date,
          paymentMethod,
          cardId: paymentMethod === 'credit_card' ? (cardId || null) : null,
        })
        await changeAmount(editingTx, {
          actualAmount: status === 'estimated' ? null : numericAmount,
          estimatedAmount: status === 'estimated' ? numericAmount : editingTx.estimatedAmount,
        })
        await changeSplit(
          editingTx,
          splitType,
          splitType === 'external' ? externalName : undefined,
          splitType === 'friend' && chosenFriend
            ? { uid: chosenFriend.uid, name: chosenFriend.name, pct: validPct }
            : undefined,
        )
      }
    } else {
      await createManual({
        description: description.trim(),
        amount: numericAmount,
        category,
        date,
        type,
        status,
        splitType,
        externalName: splitType === 'external' ? externalName : undefined,
        paymentMethod,
        cardId: paymentMethod === 'credit_card' ? (cardId || null) : null,
        reserveCardId: isReserve && cardId ? cardId : null,
        assignedFriendUid: splitType === 'friend' && chosenFriend ? chosenFriend.uid : undefined,
        assignedFriendName: splitType === 'friend' && chosenFriend ? chosenFriend.name : undefined,
        friendSharePct: splitType === 'friend' ? validPct : undefined,
        isInstallment: type === 'expense' && isInstallment,
        installmentsCount,
        installmentMode,
      })
    }

    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>{editingTx ? 'Editar Transação' : 'Nova Transação'}</DialogTitle>
            <DialogDescription>
              {editingTx ? 'Atualize os dados e a divisão do lançamento' : 'Adicione uma receita, despesa individual, parcelada ou para um amigo'}
            </DialogDescription>
          </DialogHeader>

          {/* Conexão de Parcelas: Banner de Escopo de Edição */}
          {editingTx?.installmentGroupId && (
            <div className="p-3.5 rounded-xl bg-violet-950/40 border border-violet-500/30 space-y-2.5 mt-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="size-6 rounded-lg bg-violet-500/20 text-violet-300 flex items-center justify-center shrink-0">
                    <Layers className="size-3.5" />
                  </div>
                  <div>
                    <span className="text-xs font-bold text-violet-200 block">
                      Compra Parcelada Conectada
                    </span>
                    <span className="text-[10px] text-slate-400">
                      Esta despesa faz parte de uma série em {editingTx.installmentsTotal || 'várias'} parcelas
                    </span>
                  </div>
                </div>
                <Badge variant="outline" className="text-[10px] bg-violet-500/10 text-violet-300 border-violet-500/30 font-mono">
                  {editingTx.installmentNumber || 1}/{editingTx.installmentsTotal || '?'}
                </Badge>
              </div>

              <div className="space-y-1.5 pt-1">
                <span className="text-[11px] font-medium text-slate-300 block">
                  Onde você deseja aplicar as alterações?
                </span>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setEditScope('all')}
                    className={`p-2.5 rounded-lg text-left text-xs transition-all border cursor-pointer ${
                      editScope === 'all'
                        ? 'bg-violet-600/30 border-violet-400 text-white font-semibold shadow-sm'
                        : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <div className="flex items-center gap-1.5 font-bold text-xs text-violet-300">
                      <RefreshCw className="size-3 shrink-0" />
                      <span>Em TODAS ({editingTx.installmentsTotal || 'todas'})</span>
                    </div>
                    <p className="text-[10px] text-slate-400 font-normal mt-0.5 leading-tight">
                      Atualiza descrição, valor, categoria, cartão e divisão em toda a série
                    </p>
                  </button>

                  <button
                    type="button"
                    onClick={() => setEditScope('single')}
                    className={`p-2.5 rounded-lg text-left text-xs transition-all border cursor-pointer ${
                      editScope === 'single'
                        ? 'bg-violet-600/30 border-violet-400 text-white font-semibold shadow-sm'
                        : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <div className="flex items-center gap-1.5 font-semibold text-xs text-slate-300">
                      <span>Apenas NESTA ({editingTx.installmentNumber || 1}/{editingTx.installmentsTotal || '?'})</span>
                    </div>
                    <p className="text-[10px] text-slate-400 font-normal mt-0.5 leading-tight">
                      Modifica apenas o lançamento deste mês selecionado
                    </p>
                  </button>
                </div>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 py-4">
            <div className="sm:col-span-2 flex gap-2 p-1 bg-slate-900 border border-slate-800 rounded-lg">
              <button
                type="button"
                onClick={() => {
                  setType('expense')
                  if (status === 'estimated') setStatus('pending')
                }}
                className={`flex-1 py-1.5 rounded-md text-xs font-semibold cursor-pointer transition-all ${
                  type === 'expense' ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30' : 'text-slate-400'
                }`}
              >
                💸 Despesa
              </button>
              <button
                type="button"
                onClick={() => {
                  setType('income')
                  setCategory('Receita')
                  setSplitType('mine')
                  setIsInstallment(false)
                }}
                className={`flex-1 py-1.5 rounded-md text-xs font-semibold cursor-pointer transition-all ${
                  type === 'income' ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' : 'text-slate-400'
                }`}
              >
                💰 Entrada (Receita)
              </button>
            </div>

            <div className="space-y-1.5 sm:col-span-2">
              <label className="text-xs font-medium text-slate-300">Descrição</label>
              <Input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={type === 'income' ? 'Ex: Salário, Freela, Venda...' : 'Ex: Supermercado, Aluguel, Tênis...'}
                className="bg-slate-900 border-slate-700"
                required
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-slate-300">
                {isInstallment && installmentMode === 'total' ? 'Valor Total da Compra (R$)' : 'Valor (R$)'}
              </label>
              <Input
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0,00"
                className="bg-slate-900 border-slate-700 font-mono text-base"
                required
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-medium text-slate-300">
                {type === 'income' ? 'Data de Recebimento' : 'Data de Vencimento'}
              </label>
              <Input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="bg-slate-900 border-slate-700"
                required
              />
            </div>

            {type === 'income' ? (
              <>
                <div className="space-y-1.5 sm:col-span-2">
                  <label className="text-xs font-medium text-slate-300">Forma de Recebimento</label>
                  <Select value={paymentMethod} onValueChange={(v) => setPaymentMethod(v as PaymentMethod)}>
                    <SelectTrigger className="bg-slate-900 border-slate-700">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="pix">⚡ Pix</SelectItem>
                      <SelectItem value="debit">🏦 Conta Corrente / TED</SelectItem>
                      <SelectItem value="bill">📄 Boleto / Outro</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {parseFloat(amount.replace(',', '.')) > 0 && (
                  <div className="sm:col-span-2 p-3 rounded-xl bg-emerald-950/30 border border-emerald-500/20 space-y-1 text-xs">
                    <span className="font-semibold text-emerald-300 flex items-center gap-1.5">
                      💰 Impacto no Saldo Livre:
                    </span>
                    <p className="text-slate-300 text-[11px]">
                      Esta entrada adicionará <strong className="text-emerald-400 font-mono">+R$ {parseFloat(amount.replace(',', '.')).toLocaleString('pt-BR', { minimumFractionDigits: 2 })}</strong> diretamente ao seu Saldo Livre Real do mês.
                    </p>
                  </div>
                )}
              </>
            ) : (
              <>
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-300">Categoria</label>
                  <Select value={category} onValueChange={(v) => setCategory(v as Category)}>
                    <SelectTrigger className="bg-slate-900 border-slate-700">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {CATEGORIES.filter((c) => c !== 'Receita').map((c) => (
                        <SelectItem key={c} value={c}>
                          {c}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-300">Status</label>
                  <Select value={status} onValueChange={(v) => setStatus(v as TransactionStatus)}>
                    <SelectTrigger className="bg-slate-900 border-slate-700">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="estimated">🟡 Estimado (Previsão)</SelectItem>
                      <SelectItem value="pending">🟠 A Pagar (Valor Real)</SelectItem>
                      <SelectItem value="paid">🟢 Já Pago</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5 sm:col-span-2">
                  <label className="text-xs font-medium text-slate-300">Quem Paga / Divisão</label>
                  <Select value={splitType} onValueChange={(v) => setSplitType(v as SplitType)}>
                    <SelectTrigger className="bg-slate-900 border-slate-700">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="mine">👤 100% Meu (Desconta do meu Saldo)</SelectItem>
                      {partner && <SelectItem value="split_50">🤝 Dividido 50/50 com {partner.name}</SelectItem>}
                      {partner && <SelectItem value="partner">🔄 100% {partner.name} (Neutro no meu Saldo)</SelectItem>}
                      <SelectItem value="friend">🧑‍🤝‍🧑 Amigo da Rede (Comprar para Amigo pagar)</SelectItem>
                      <SelectItem value="external">🏷️ Terceiro Externo não cadastrado</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {/* Feature 2: Seleção de Amigo & Divisão por Porcentagem */}
                {splitType === 'friend' && (
                  <div className="space-y-3 sm:col-span-2 p-3.5 rounded-xl bg-violet-950/30 border border-violet-500/30 animate-in fade-in duration-200">
                    <div className="flex items-center justify-between">
                      <label className="text-xs font-semibold text-violet-300 flex items-center gap-1.5">
                        <Users2 className="size-3.5" />
                        <span>Selecionar Amigo da sua Rede</span>
                      </label>
                      <span className="text-[10px] text-violet-400 font-medium">Aceite mútuo</span>
                    </div>

                    {friends.length === 0 ? (
                      <div className="text-xs text-slate-400 space-y-1">
                        <p>Você ainda não tem amigos conectados.</p>
                        <p className="text-[11px] text-slate-500">
                          Use o menu "Amigos" no topo para convidar amigos pelo e-mail Google antes de atribuir compras.
                        </p>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        <Select value={selectedFriendUid} onValueChange={setSelectedFriendUid}>
                          <SelectTrigger className="bg-slate-900 border-slate-700 text-xs">
                            <SelectValue placeholder="Escolha um amigo..." />
                          </SelectTrigger>
                          <SelectContent>
                            {friends.map((f) => (
                              <SelectItem key={f.uid} value={f.uid}>
                                {f.name} ({f.email})
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>

                        {/* Nova Feature: Divisão por Porcentagem Digitada */}
                        <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800 space-y-2">
                          <div className="flex items-center justify-between">
                            <label className="text-xs font-medium text-slate-300 flex items-center gap-1.5">
                              <Percent className="size-3 text-violet-400" />
                              <span>Porcentagem Cobrada do Amigo</span>
                            </label>
                            <span className="text-xs font-bold text-violet-300 font-mono">
                              {friendSharePct}%
                            </span>
                          </div>

                          <div className="flex items-center gap-2">
                            <div className="relative flex-1">
                              <Input
                                type="number"
                                min={1}
                                max={100}
                                step={1}
                                value={friendSharePct}
                                onChange={(e) => {
                                  const val = Number(e.target.value)
                                  if (!isNaN(val)) {
                                    setFriendSharePct(Math.min(100, Math.max(1, val)))
                                  }
                                }}
                                className="bg-slate-950 border-slate-700 h-8 text-xs font-mono pr-7"
                              />
                              <span className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-slate-500">
                                %
                              </span>
                            </div>

                            {/* Preset Buttons */}
                            <div className="flex gap-1 shrink-0">
                              {[25, 50, 75, 100].map((pct) => (
                                <button
                                  key={pct}
                                  type="button"
                                  onClick={() => setFriendSharePct(pct)}
                                  className={`px-2 py-1 text-[10px] font-semibold rounded-md border transition-all cursor-pointer ${
                                    friendSharePct === pct
                                      ? 'bg-violet-500/25 text-violet-300 border-violet-500/50'
                                      : 'bg-slate-950/60 text-slate-400 border-slate-800 hover:text-slate-200'
                                  }`}
                                >
                                  {pct === 100 ? '100% (Total)' : `${pct}%`}
                                </button>
                              ))}
                            </div>
                          </div>

                          {/* Visual Distribution Summary */}
                          {parseFloat(amount.replace(',', '.')) > 0 && (
                            <div className="pt-2 border-t border-slate-800/80 text-[11px] space-y-1.5">
                              <div className="flex items-center justify-between text-slate-300">
                                <span className="flex items-center gap-1">
                                  <span className="size-2 rounded-full bg-violet-400" />
                                  <span>Amigo ({friendSharePct}%):</span>
                                </span>
                                <span className="font-bold text-violet-300 font-mono">
                                  {formatBRL((parseFloat(amount.replace(',', '.')) * friendSharePct) / 100)}
                                </span>
                              </div>

                              <div className="flex items-center justify-between text-slate-300">
                                <span className="flex items-center gap-1">
                                  <span className="size-2 rounded-full bg-emerald-400" />
                                  <span>Sua Parte ({100 - friendSharePct}%):</span>
                                </span>
                                <span className="font-bold text-emerald-300 font-mono">
                                  {formatBRL((parseFloat(amount.replace(',', '.')) * (100 - friendSharePct)) / 100)}
                                </span>
                              </div>

                              {/* Progress bar */}
                              <div className="w-full h-1.5 rounded-full bg-slate-800 overflow-hidden flex">
                                <div
                                  className="h-full bg-violet-400 transition-all duration-200"
                                  style={{ width: `${friendSharePct}%` }}
                                />
                                <div
                                  className="h-full bg-emerald-400 transition-all duration-200"
                                  style={{ width: `${100 - friendSharePct}%` }}
                                />
                              </div>
                            </div>
                          )}
                        </div>

                        <p className="text-[11px] text-violet-300/80 leading-relaxed">
                          🛡️ <strong>Segurança e Aceite Prévio:</strong> O gasto será lançado no seu cartão de crédito, mas a cobrança só comprometerá o Saldo Livre de <strong>{friends.find(f => f.uid === selectedFriendUid)?.name || 'seu amigo'}</strong> após ele <strong>aceitar</strong> no aplicativo dele.
                        </p>
                      </div>
                    )}
                  </div>
                )}

                {splitType === 'external' && (
                  <div className="space-y-1.5 sm:col-span-2">
                    <label className="text-xs font-medium text-slate-300">Nome do Terceiro</label>
                    <Input
                      value={externalName}
                      onChange={(e) => setExternalName(e.target.value)}
                      placeholder="Ex: Mãe, Irmão, Carlos..."
                      className="bg-slate-900 border-slate-700"
                    />
                  </div>
                )}

                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-300">Método de Pagamento</label>
                  <Select value={paymentMethod} onValueChange={(v) => setPaymentMethod(v as PaymentMethod)}>
                    <SelectTrigger className="bg-slate-900 border-slate-700">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="credit_card">💳 Cartão de Crédito</SelectItem>
                      <SelectItem value="pix">⚡ Pix</SelectItem>
                      <SelectItem value="debit">🏦 Débito</SelectItem>
                      <SelectItem value="bill">📄 Boleto</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {paymentMethod === 'credit_card' && (
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-slate-300">Cartão Utilizado</label>
                    <Select value={cardId} onValueChange={setCardId}>
                      <SelectTrigger className="bg-slate-900 border-slate-700">
                        <SelectValue placeholder="Selecione o cartão" />
                      </SelectTrigger>
                      <SelectContent>
                        {cards.map((c) => (
                          <SelectItem key={c.id} value={c.id}>
                            {c.cardName} (Titular: {c.ownerName})
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {/* Feature 3: Compra Parcelada no Cartão de Crédito */}
                {!editingTx && (
                  <div className="sm:col-span-2 p-3 rounded-xl bg-slate-900 border border-slate-800 space-y-2.5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5">
                        <CreditCard className="size-3.5 text-violet-400" />
                        <span className="text-xs font-semibold text-slate-200">Compra Parcelada?</span>
                      </div>
                      <input
                        type="checkbox"
                        checked={isInstallment}
                        onChange={(e) => setIsInstallment(e.target.checked)}
                        className="size-4 accent-violet-500 rounded cursor-pointer"
                      />
                    </div>

                    {isInstallment && (
                      <div className="pt-2 border-t border-slate-800/80 space-y-2.5 animate-in fade-in duration-200">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                          <div className="space-y-1">
                            <label className="text-[11px] text-slate-400">Número de Parcelas</label>
                            <Select
                              value={String(installmentsCount)}
                              onValueChange={(v) => setInstallmentsCount(Number(v))}
                            >
                              <SelectTrigger className="h-8 text-xs bg-slate-950 border-slate-700">
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                {[2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 18, 24, 36, 48].map((n) => (
                                  <SelectItem key={n} value={String(n)}>
                                    {n}x parcelas ({n} meses)
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </div>

                          <div className="space-y-1">
                            <label className="text-[11px] text-slate-400">O valor digitado acima é:</label>
                            <div className="flex gap-1 pt-0.5">
                              <button
                                type="button"
                                onClick={() => setInstallmentMode('total')}
                                className={`flex-1 py-1 px-1.5 text-[10px] rounded border font-medium cursor-pointer transition-all ${
                                  installmentMode === 'total'
                                    ? 'bg-violet-500/20 text-violet-300 border-violet-500/30'
                                    : 'bg-slate-950 text-slate-400 border-slate-800'
                                }`}
                              >
                                Valor Total
                              </button>
                              <button
                                type="button"
                                onClick={() => setInstallmentMode('per_installment')}
                                className={`flex-1 py-1 px-1.5 text-[10px] rounded border font-medium cursor-pointer transition-all ${
                                  installmentMode === 'per_installment'
                                    ? 'bg-violet-500/20 text-violet-300 border-violet-500/30'
                                    : 'bg-slate-950 text-slate-400 border-slate-800'
                                }`}
                              >
                                Por Parcela
                              </button>
                            </div>
                          </div>
                        </div>

                        {parseFloat(amount.replace(',', '.')) > 0 && (
                          <div className="p-2.5 rounded-lg bg-violet-950/40 border border-violet-800/40 text-[11px] text-violet-200 space-y-1">
                            <span className="font-semibold text-violet-300">
                              🗓️ Repetição Automática nos Meses Seguintes:
                            </span>
                            <p className="text-slate-300">
                              {installmentMode === 'total'
                                ? `Serão criadas ${installmentsCount} parcelas de ${formatBRL(parseFloat(amount.replace(',', '.')) / installmentsCount)} (totalizando ${formatBRL(parseFloat(amount.replace(',', '.')))}).`
                                : `Serão criadas ${installmentsCount} parcelas de ${formatBRL(parseFloat(amount.replace(',', '.')))} (totalizando ${formatBRL(parseFloat(amount.replace(',', '.')) * installmentsCount)}).`}
                            </p>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {/* Live Impact Preview */}
                {parseFloat(amount.replace(',', '.')) > 0 && (() => {
                  const rawVal = parseFloat(amount.replace(',', '.'))
                  const friendVal = (rawVal * clampPct(friendSharePct)) / 100
                  const myVal = rawVal - friendVal

                  return (
                    <div className="sm:col-span-2 p-3 rounded-xl bg-slate-950/80 border border-slate-800 space-y-2 text-xs">
                      <span className="font-semibold text-slate-200">
                        Reflexo nos Saldos Individuais:
                      </span>
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-[11px]">
                        <div className="p-2 rounded-lg bg-slate-900 border border-slate-800 flex flex-col">
                          <span className="text-slate-400">Seu Saldo Livre:</span>
                          <span className={`font-bold mt-0.5 ${
                            splitType === 'friend'
                              ? myVal > 0 ? 'text-rose-400' : 'text-slate-400'
                              : splitType === 'partner' || splitType === 'external' ? 'text-slate-400' : 'text-rose-400'
                          }`}>
                            {splitType === 'friend'
                              ? myVal > 0
                                ? `−${formatBRL(myVal)} (Sua cota de ${100 - friendSharePct}%)`
                                : 'R$ 0,00 (100% do amigo após aceite)'
                              : splitType === 'partner' || splitType === 'external'
                                ? 'R$ 0,00 (Neutro)'
                                : splitType === 'split_50'
                                  ? `−R$ ${(rawVal / 2).toLocaleString('pt-BR', { minimumFractionDigits: 2 })} (50%)`
                                  : `−R$ ${rawVal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}`}
                          </span>
                        </div>

                        <div className="p-2 rounded-lg bg-slate-900 border border-slate-800 flex flex-col">
                          <span className="text-slate-400">
                            {splitType === 'friend'
                              ? `Cobrança para ${friends.find(f => f.uid === selectedFriendUid)?.name || 'Amigo'}:`
                              : splitType === 'external'
                                ? `A receber de ${externalName}:`
                                : partner ? `Saldo de ${partner.name}:` : 'Acerto:'}
                          </span>
                          <span className="font-bold text-emerald-400 mt-0.5">
                            {splitType === 'friend'
                              ? `+${formatBRL(friendVal)} (${friendSharePct}%) (Aguardando aceite)`
                              : splitType === 'external'
                                ? `+${formatBRL(rawVal)}`
                                : splitType === 'partner'
                                  ? `−${formatBRL(rawVal)}`
                                  : splitType === 'split_50'
                                    ? `−${formatBRL(rawVal / 2)}`
                                    : 'Sem pendências'}
                          </span>
                        </div>
                      </div>
                    </div>
                  )
                })()}
              </>
            )}
          </div>

          <DialogFooter className="flex-row items-center justify-between gap-2 pt-2">
            {editingTx && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setDeleteConfirmOpen(true)}
                className="text-xs text-rose-400 hover:text-rose-300 hover:bg-rose-500/10 gap-1.5 cursor-pointer mr-auto"
                title="Excluir despesa ou parcelamento"
              >
                <Trash2 className="size-3.5" />
                <span>Excluir</span>
              </Button>
            )}
            <div className="flex items-center gap-2 ml-auto">
              <Button type="button" variant="outline" size="sm" onClick={() => onOpenChange(false)}>
                Cancelar
              </Button>
              <Button type="submit" size="sm" className="bg-emerald-600 hover:bg-emerald-500 text-white font-medium">
                {editingTx
                  ? editingTx.installmentGroupId && editScope === 'all'
                    ? `Salvar em Todas as ${editingTx.installmentsTotal || ''} Parcelas`
                    : 'Atualizar Esta Parcela'
                  : isInstallment
                    ? `Criar ${installmentsCount} Parcelas`
                    : 'Criar Transação'}
              </Button>
            </div>
          </DialogFooter>
        </form>
      </DialogContent>

      {/* Modal para confirmação de exclusão individual ou de todo o grupo */}
      <DeleteInstallmentDialog
        open={deleteConfirmOpen}
        onOpenChange={setDeleteConfirmOpen}
        tx={editingTx || null}
        onDeleted={() => {
          setDeleteConfirmOpen(false)
          onOpenChange(false)
        }}
      />
    </Dialog>
  )
}
