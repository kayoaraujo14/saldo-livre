import { useState, useEffect } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFinance } from '@/store/FinanceContext'
import { CATEGORIES, CATEGORY_GROUPS, type Allocation, type Category, type PaymentMethod, type SplitType, type Transaction, type TransactionStatus } from '@/types'
import { clampPct, effectiveAmount, friendPctOf, guessCategoryAndSubcategory } from '@/lib/finance'
import { todayKey, formatBRL, stripInstallmentSuffix, round2 } from '@/lib/utils'
import {
  CreditCard,
  Users2,
  Layers,
  Trash2,
  RefreshCw,
  RotateCcw,
  UserPlus,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  ShieldCheck,
  Calendar,
  Sparkles,
  ReceiptText,
} from 'lucide-react'
import { DeleteInstallmentDialog } from './DeleteInstallmentDialog'

export interface CustomParticipantRow {
  id: string
  type: 'me' | 'partner' | 'friend' | 'external'
  uid?: string
  name: string
  amount: number
  percentage: number
}

const PARTICIPANT_COLORS = [
  'bg-emerald-400',
  'bg-violet-400',
  'bg-sky-400',
  'bg-amber-400',
  'bg-rose-400',
  'bg-teal-400',
  'bg-indigo-400',
]

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
  const [subcategory, setSubcategory] = useState<string>('Supermercado')
  const [date, setDate] = useState(todayKey())
  const [type, setType] = useState<'expense' | 'income'>('expense')
  const [status, setStatus] = useState<TransactionStatus>('pending')
  const [splitType, setSplitType] = useState<SplitType>('mine')
  const [externalName, setExternalName] = useState('Mãe')
  const [customParticipants, setCustomParticipants] = useState<CustomParticipantRow[]>([])
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('credit_card')
  const [cardId, setCardId] = useState<string>('')
  const [isReserve, setIsReserve] = useState(false)

  // Parcelamento
  const [isInstallment, setIsInstallment] = useState(false)
  const [installmentsCount, setInstallmentsCount] = useState(3)
  const [installmentMode, setInstallmentMode] = useState<'total' | 'per_installment'>('total')

  // Conexão de Parcelas: Editar todas de uma vez ou apenas esta
  const [editScope, setEditScope] = useState<'all' | 'single'>('all')
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)

  const getInitialCustomParticipants = (tot: number): CustomParticipantRow[] => {
    const curTot = tot > 0 ? tot : 0
    const half = round2(curTot / 2)
    const secondHalf = round2(curTot - half)

    const list: CustomParticipantRow[] = [
      { id: 'me', type: 'me', uid: me?.uid, name: me?.name || 'Você', amount: half, percentage: 50 },
    ]

    if (friends.length > 0) {
      list.push({ id: `fr_${friends[0].uid}`, type: 'friend', uid: friends[0].uid, name: friends[0].name, amount: secondHalf, percentage: 50 })
    } else if (partner) {
      list.push({ id: 'partner', type: 'partner', uid: partner.uid, name: partner.name, amount: secondHalf, percentage: 50 })
    } else {
      list.push({ id: 'ext_mae', type: 'external', name: 'Mãe', amount: secondHalf, percentage: 50 })
    }

    return list
  }

  useEffect(() => {
    setErrorMessage(null)
    setIsSubmitting(false)

    if (editingTx) {
      const isGrp = Boolean(editingTx.installmentGroupId)
      setDescription(isGrp ? stripInstallmentSuffix(editingTx.description) : editingTx.description)
      setAmount(String(Math.abs(effectiveAmount(editingTx))))
      setCategory(editingTx.category)
      setSubcategory(editingTx.subcategory || CATEGORY_GROUPS[editingTx.category]?.subcategories?.[0] || '')
      setDate(editingTx.dueDate)
      setType(editingTx.type)
      setStatus(editingTx.status)
      setPaymentMethod(editingTx.paymentMethod)
      setCardId(editingTx.cardId || '')
      setIsReserve(Boolean(editingTx.reserveCardId))
      setIsInstallment(isGrp)
      setEditScope('all')

      const allocs = editingTx.allocations || []
      if (editingTx.splitType === 'custom' || allocs.length > 2) {
        setSplitType('custom')
        const total = Math.abs(effectiveAmount(editingTx)) || 1
        const rows: CustomParticipantRow[] = allocs.map((a, i) => {
          const isMe = a.uid === me?.uid
          const isPart = partner && a.uid === partner.uid
          const isFr = friends.some((f) => f.uid === a.uid)
          const pct = round2((a.amount / total) * 100)
          return {
            id: `p_${i}_${a.uid || a.name}`,
            type: isMe ? 'me' : isPart ? 'partner' : isFr ? 'friend' : (a.type === 'PLATFORM_USER' ? 'friend' : 'external'),
            uid: a.uid,
            name: a.name,
            amount: a.amount,
            percentage: pct,
          }
        })
        setCustomParticipants(rows)
      } else if (editingTx.assignedFriendUid) {
        setSplitType('custom')
        const total = Math.abs(effectiveAmount(editingTx)) || 1
        const pct = friendPctOf(editingTx)
        const friendVal = round2((total * pct) / 100)
        const myVal = round2(total - friendVal)
        const rows: CustomParticipantRow[] = [
          { id: 'me', type: 'me', uid: me?.uid, name: me?.name || 'Você', amount: myVal, percentage: 100 - pct },
          { id: `fr_${editingTx.assignedFriendUid}`, type: 'friend', uid: editingTx.assignedFriendUid, name: editingTx.assignedFriendName || 'Amigo', amount: friendVal, percentage: pct },
        ]
        setCustomParticipants(rows)
      } else {
        const hasExternal = allocs.some((a) => a.type === 'EXTERNAL')
        if (hasExternal) {
          setSplitType('external')
          const ext = allocs.find((a) => a.type === 'EXTERNAL')
          if (ext) setExternalName(ext.name)
        } else if (allocs.length > 1) {
          setSplitType('split_50')
        } else if (allocs[0]?.uid === me?.uid) {
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
      setSubcategory('Supermercado')
      setDate(todayKey())
      setType('expense')
      setStatus('pending')
      setSplitType('mine')
      setExternalName('Mãe')
      setCustomParticipants([])
      setPaymentMethod('credit_card')
      setCardId(cards[0]?.id || '')
      setIsReserve(false)
      setIsInstallment(false)
      setInstallmentsCount(3)
      setInstallmentMode('total')
      setEditScope('all')
    }
  }, [editingTx, open, cards, me, friends, partner])

  const handleAmountChange = (newAmountStr: string) => {
    setAmount(newAmountStr)
    const newTot = Math.abs(parseFloat(newAmountStr.replace(',', '.')))
    if (!isNaN(newTot) && newTot > 0 && customParticipants.length > 0) {
      setCustomParticipants((prev) =>
        prev.map((p) => ({
          ...p,
          amount: round2((newTot * p.percentage) / 100),
        })),
      )
    }
  }

  const handleSplitTypeChange = (newType: SplitType) => {
    setSplitType(newType)
    if (newType === 'custom' && customParticipants.length === 0) {
      const num = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
      setCustomParticipants(getInitialCustomParticipants(num))
    }
  }

  const handleParticipantPctChange = (id: string, newPctRaw: number) => {
    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
    const pct = Math.max(0, Math.min(100, isNaN(newPctRaw) ? 0 : newPctRaw))
    setCustomParticipants((prev) =>
      prev.map((p) => {
        if (p.id !== id) return p
        return {
          ...p,
          percentage: pct,
          amount: round2((numTotal * pct) / 100),
        }
      }),
    )
  }

  const handleParticipantAmountChange = (id: string, newAmtRaw: number) => {
    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
    const amt = Math.max(0, isNaN(newAmtRaw) ? 0 : newAmtRaw)
    setCustomParticipants((prev) =>
      prev.map((p) => {
        if (p.id !== id) return p
        const pct = numTotal > 0 ? round2((amt / numTotal) * 100) : p.percentage
        return {
          ...p,
          amount: round2(amt),
          percentage: pct,
        }
      }),
    )
  }

  const handleAddParticipant = (partType: 'partner' | 'friend' | 'external', friendUid?: string) => {
    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
    let newP: CustomParticipantRow
    if (partType === 'partner' && partner) {
      newP = {
        id: `partner_${Date.now()}`,
        type: 'partner',
        uid: partner.uid,
        name: partner.name,
        amount: 0,
        percentage: 0,
      }
    } else if (partType === 'friend' && friendUid) {
      const fr = friends.find((f) => f.uid === friendUid)
      newP = {
        id: `fr_${friendUid}_${Date.now()}`,
        type: 'friend',
        uid: friendUid,
        name: fr?.name || 'Amigo',
        amount: 0,
        percentage: 0,
      }
    } else {
      newP = {
        id: `ext_${Date.now()}`,
        type: 'external',
        name: 'Mãe',
        amount: 0,
        percentage: 0,
      }
    }

    setCustomParticipants((prev) => {
      const updated = [...prev, newP]
      const count = updated.length
      const eachPct = round2(100 / count)
      return updated.map((p, idx) => {
        const pct = idx === count - 1 ? round2(100 - eachPct * (count - 1)) : eachPct
        return {
          ...p,
          percentage: pct,
          amount: round2((numTotal * pct) / 100),
        }
      })
    })
  }

  const handleRemoveParticipant = (id: string) => {
    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
    setCustomParticipants((prev) => {
      const filtered = prev.filter((p) => p.id !== id)
      if (filtered.length === 0) return prev
      const count = filtered.length
      const eachPct = round2(100 / count)
      return filtered.map((p, idx) => {
        const pct = idx === count - 1 ? round2(100 - eachPct * (count - 1)) : eachPct
        return {
          ...p,
          percentage: pct,
          amount: round2((numTotal * pct) / 100),
        }
      })
    })
  }

  const handleDistributeEqually = () => {
    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
    const count = customParticipants.length
    if (count === 0) return
    const eachPct = round2(100 / count)
    setCustomParticipants((prev) =>
      prev.map((p, idx) => {
        const pct = idx === count - 1 ? round2(100 - eachPct * (count - 1)) : eachPct
        return {
          ...p,
          percentage: pct,
          amount: round2((numTotal * pct) / 100),
        }
      }),
    )
  }

  const handleAdjustDiffToMe = () => {
    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
    const othersAlloc = customParticipants
      .filter((p) => p.type !== 'me')
      .reduce((s, p) => s + p.amount, 0)
    const myAmt = Math.max(0, round2(numTotal - othersAlloc))
    const myPct = numTotal > 0 ? round2((myAmt / numTotal) * 100) : 100

    setCustomParticipants((prev) =>
      prev.map((p) => {
        if (p.type === 'me') {
          return { ...p, amount: myAmt, percentage: myPct }
        }
        return p
      }),
    )
  }

  const handleSetParticipantPctPreset = (id: string, pct: number) => {
    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
    setCustomParticipants((prev) => {
      const target = prev.find((p) => p.id === id)
      if (!target) return prev

      if (target.type !== 'me') {
        const othersAlloc = prev
          .filter((p) => p.id !== id && p.type !== 'me')
          .reduce((s, p) => s + p.percentage, 0)
        const clampedTargetPct = Math.min(100 - othersAlloc, Math.max(0, pct))
        const remainingForMe = Math.max(0, round2(100 - othersAlloc - clampedTargetPct))

        return prev.map((p) => {
          if (p.id === id) {
            return {
              ...p,
              percentage: clampedTargetPct,
              amount: round2((numTotal * clampedTargetPct) / 100),
            }
          }
          if (p.type === 'me') {
            return {
              ...p,
              percentage: remainingForMe,
              amount: round2((numTotal * remainingForMe) / 100),
            }
          }
          return p
        })
      } else {
        return prev.map((p) => {
          if (p.id === id) {
            return {
              ...p,
              percentage: pct,
              amount: round2((numTotal * pct) / 100),
            }
          }
          return p
        })
      }
    })
  }

  const handleQuickFriendPreset = (friendUid: string, pct: number) => {
    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
    const fr = friends.find((f) => f.uid === friendUid)
    if (!fr) return

    const friendShare = round2((numTotal * pct) / 100)
    const myShare = round2(numTotal - friendShare)

    setCustomParticipants([
      { id: 'me', type: 'me', uid: me?.uid, name: me?.name || 'Você', amount: myShare, percentage: 100 - pct },
      { id: `fr_${friendUid}`, type: 'friend', uid: friendUid, name: fr.name, amount: friendShare, percentage: pct },
    ])
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorMessage(null)
    const rawVal = parseFloat(amount.replace(',', '.'))
    if (isNaN(rawVal) || rawVal === 0 || !description.trim()) {
      setErrorMessage('Por favor, informe a descrição e um valor válido.')
      return
    }

    const isEstorno = category === 'Estorno'
    const numericAmount = isEstorno ? -Math.abs(rawVal) : Math.abs(rawVal)
    const posTotal = Math.abs(numericAmount)

    setIsSubmitting(true)

    try {
      const isCustom = splitType === 'custom' || splitType === 'friend'
      let customAllocations: Allocation[] | undefined = undefined
      let assignedFriendUid: string | undefined = undefined
      let assignedFriendName: string | undefined = undefined
      let friendSharePctNum: number | undefined = undefined

      if (isCustom) {
        let participantsToUse = customParticipants
        if (participantsToUse.length === 0) {
          participantsToUse = getInitialCustomParticipants(posTotal)
          setCustomParticipants(participantsToUse)
        }

        const totalPct = participantsToUse.reduce((s, p) => s + p.percentage, 0)
        if (Math.abs(totalPct - 100) > 0.5 && participantsToUse.some((p) => p.type === 'me')) {
          const othersPct = participantsToUse
            .filter((p) => p.type !== 'me')
            .reduce((s, p) => s + p.percentage, 0)
          const myClampedPct = Math.max(0, Math.min(100, round2(100 - othersPct)))
          participantsToUse = participantsToUse.map((p) =>
            p.type === 'me'
              ? { ...p, percentage: myClampedPct, amount: round2((posTotal * myClampedPct) / 100) }
              : p,
          )
        }

        customAllocations = participantsToUse.map((p) => {
          const item: Allocation = {
            type: p.type === 'external' ? 'EXTERNAL' : 'PLATFORM_USER',
            name: p.name.trim() || 'Participante',
            amount: round2((p.percentage * posTotal) / 100),
            reimbursed:
              p.uid ===
              (paymentMethod === 'credit_card' && cardId
                ? cards.find((c) => c.id === cardId)?.ownerUid || me?.uid
                : me?.uid),
          }
          if (p.type !== 'external' && p.uid) {
            item.uid = p.uid
          }
          return item
        })

        const friendPart = participantsToUse.find(
          (p) => p.type === 'friend' || (p.uid && p.uid !== me?.uid && (!partner || p.uid !== partner.uid)),
        )
        if (friendPart && friendPart.uid) {
          assignedFriendUid = friendPart.uid
          assignedFriendName = friendPart.name
          friendSharePctNum = clampPct(Math.round(friendPart.percentage))
        }
      }

      if (editingTx) {
        if (editingTx.installmentGroupId && editScope === 'all') {
          await updateInstallmentGroup(editingTx.installmentGroupId, {
            description: description.trim(),
            amount: numericAmount,
            category,
            subcategory: subcategory || null,
            date: date !== editingTx.dueDate ? date : undefined,
            status,
            paymentMethod,
            cardId: paymentMethod === 'credit_card' ? (cardId || null) : null,
            splitType: isCustom ? 'custom' : splitType,
            externalName: splitType === 'external' ? externalName : undefined,
            friend:
              assignedFriendUid && assignedFriendName
                ? { uid: assignedFriendUid, name: assignedFriendName, pct: friendSharePctNum ?? 100 }
                : undefined,
            customAllocations,
          })
        } else {
          await updateTransaction(editingTx.id, {
            description: description.trim(),
            category,
            subcategory: subcategory || null,
            dueDate: date,
            paymentMethod,
            cardId: paymentMethod === 'credit_card' ? (cardId || null) : null,
            splitType: isCustom ? 'custom' : splitType,
          })
          await changeAmount(editingTx, {
            actualAmount: status === 'estimated' ? null : numericAmount,
            estimatedAmount: status === 'estimated' ? numericAmount : editingTx.estimatedAmount,
          })
          await changeSplit(
            editingTx,
            isCustom ? 'custom' : splitType,
            splitType === 'external' ? externalName : undefined,
            assignedFriendUid && assignedFriendName
              ? { uid: assignedFriendUid, name: assignedFriendName, pct: friendSharePctNum ?? 100 }
              : undefined,
            customAllocations,
          )
        }
      } else {
        await createManual({
          description: description.trim(),
          amount: numericAmount,
          category,
          subcategory: subcategory || null,
          date,
          type,
          status,
          splitType: isCustom ? 'custom' : splitType,
          externalName: splitType === 'external' ? externalName : undefined,
          paymentMethod,
          cardId: paymentMethod === 'credit_card' ? (cardId || null) : null,
          reserveCardId: isReserve && cardId ? cardId : null,
          assignedFriendUid,
          assignedFriendName,
          friendSharePct: friendSharePctNum,
          customAllocations,
          isInstallment: type === 'expense' && isInstallment,
          installmentsCount,
          installmentMode,
        })
      }

      onOpenChange(false)
    } catch (err) {
      console.error('Erro ao salvar despesa:', err)
      setErrorMessage(
        err instanceof Error ? err.message : 'Erro ao salvar despesa. Verifique os dados e tente novamente.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const isEstornoCat = category === 'Estorno'
  const isIncomeType = type === 'income'

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl w-full max-h-[92vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader className="space-y-1 pb-1 border-b border-white/[0.06]">
            <DialogTitle className="text-base sm:text-lg font-bold text-white flex items-center gap-2">
              <div className="size-8 rounded-xl bg-gradient-to-tr from-emerald-500/20 to-teal-500/20 border border-emerald-500/30 text-emerald-400 flex items-center justify-center">
                <Sparkles className="size-4" />
              </div>
              <span>{editingTx ? 'Editar Lançamento' : 'Novo Lançamento'}</span>
            </DialogTitle>
            <DialogDescription className="text-xs text-slate-400">
              {editingTx
                ? 'Atualize descrição, valores, cartão ou divisão de participantes'
                : 'Cadastre receitas, compras individuais, parceladas ou compartilhadas com amigos'}
            </DialogDescription>
          </DialogHeader>

          {/* Conexão de Parcelas: Banner de Escopo de Edição */}
          {editingTx?.installmentGroupId && (
            <div className="p-3.5 rounded-xl bg-violet-950/40 border border-violet-500/30 space-y-2.5">
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
                  Parcela {editingTx.installmentNumber || 1}/{editingTx.installmentsTotal || '?'}
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
                    className={`p-2.5 rounded-xl text-left text-xs transition-all border cursor-pointer ${
                      editScope === 'all'
                        ? 'bg-violet-600/30 border-violet-400 text-white font-semibold shadow-sm'
                        : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <div className="flex items-center gap-1.5 font-bold text-xs text-violet-300">
                      <RefreshCw className="size-3 shrink-0" />
                      <span>Em TODAS as {editingTx.installmentsTotal || 'todas'} Parcelas</span>
                    </div>
                    <p className="text-[10px] text-slate-400 font-normal mt-0.5 leading-tight">
                      Sincroniza descrição, valor e divisão em toda a série do cartão
                    </p>
                  </button>

                  <button
                    type="button"
                    onClick={() => setEditScope('single')}
                    className={`p-2.5 rounded-xl text-left text-xs transition-all border cursor-pointer ${
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

          {/* 1. SELETOR DE TIPO (DESPESA vs RECEITA) & CAMPO HERO DE VALOR */}
          <div className="space-y-3 p-3.5 sm:p-4 rounded-2xl bg-slate-950/60 border border-white/[0.07]">
            {/* Segmented Type Switcher */}
            <div className="flex p-1 bg-slate-900/90 border border-slate-800 rounded-xl gap-1">
              <button
                type="button"
                onClick={() => {
                  setType('expense')
                  if (status === 'estimated') setStatus('pending')
                }}
                className={`flex-1 py-2 px-3 rounded-lg text-xs font-bold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
                  type === 'expense'
                    ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <span>💸 Despesa / Saída</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setType('income')
                  setCategory('Receita')
                  setSplitType('mine')
                  setIsInstallment(false)
                  setStatus('paid')
                }}
                className={`flex-1 py-2 px-3 rounded-lg text-xs font-bold cursor-pointer transition-all flex items-center justify-center gap-1.5 ${
                  type === 'income'
                    ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <span>💰 Receita / Entrada</span>
              </button>
            </div>

            {/* Hero Value Input (Clean Financial Display) */}
            <div className="pt-1">
              <label className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 block mb-1">
                {isInstallment && installmentMode === 'total' ? 'Valor Total da Compra' : 'Valor do Lançamento'}
              </label>
              <div
                className={`flex items-center px-4 py-2.5 rounded-xl border transition-all ${
                  isIncomeType
                    ? 'bg-emerald-950/20 border-emerald-500/30 focus-within:border-emerald-400 focus-within:ring-2 focus-within:ring-emerald-500/20'
                    : isEstornoCat
                      ? 'bg-cyan-950/20 border-cyan-500/30 focus-within:border-cyan-400'
                      : 'bg-slate-900/80 border-slate-700/80 focus-within:border-emerald-500/50 focus-within:ring-2 focus-within:ring-emerald-500/20'
                }`}
              >
                <span className="text-xl sm:text-2xl font-bold font-mono text-slate-400 mr-2 select-none">
                  {isIncomeType ? '+ R$' : isEstornoCat ? '🔄 R$' : 'R$'}
                </span>
                <input
                  type="text"
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => handleAmountChange(e.target.value)}
                  placeholder="0,00"
                  className="w-full bg-transparent border-none outline-none text-2xl sm:text-3xl font-black font-mono text-white tracking-tight placeholder:text-slate-600"
                  required
                  autoFocus
                />
              </div>
            </div>

            {/* Estorno Information Pill */}
            {category === 'Estorno' && (
              <div className="p-2.5 rounded-xl bg-emerald-950/30 border border-emerald-500/30 flex items-center gap-2 text-xs text-emerald-300 animate-in fade-in">
                <RotateCcw className="size-4 shrink-0 text-emerald-400" />
                <span>
                  <strong>Estorno na Fatura:</strong> abaterá o valor no fechamento do cartão de crédito.
                </span>
              </div>
            )}
          </div>

          {/* 2. DADOS PRINCIPAIS (DESCRIÇÃO, DATA, CATEGORIA E STATUS) */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            {/* Descrição */}
            <div className="sm:col-span-2 space-y-1.5">
              <label className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                <ReceiptText className="size-3.5 text-slate-400" />
                <span>Descrição da Transação</span>
              </label>
              <Input
                value={description}
                onChange={(e) => {
                  const val = e.target.value
                  setDescription(val)
                  if (!editingTx && val.trim().length >= 3) {
                    const guess = guessCategoryAndSubcategory(val)
                    if (type === 'expense') {
                      setCategory(guess.category)
                      const subs = CATEGORY_GROUPS[guess.category]?.subcategories || []
                      setSubcategory(guess.subcategory || subs[0] || '')
                    }
                  }
                }}
                placeholder={isIncomeType ? 'Ex: Salário, Rendimentos, Pix de Amigo...' : 'Ex: Supermercado, Aluguel, Amazon Prime, Uber...'}
                className="h-10 text-xs sm:text-sm bg-slate-900/90 border-slate-700/80 rounded-xl focus-visible:ring-emerald-500/30"
                required
              />
            </div>

            {/* Data */}
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-300 flex items-center gap-1.5">
                <Calendar className="size-3.5 text-slate-400" />
                <span>{isIncomeType ? 'Data de Recebimento' : 'Data de Vencimento'}</span>
              </label>
              <Input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="h-10 text-xs bg-slate-900/90 border-slate-700/80 rounded-xl"
                required
              />
            </div>

            {/* Categoria / Subcategoria de Receita e Forma de Recebimento */}
            {isIncomeType ? (
              <>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Tipo de Receita</label>
                  <Select
                    value={subcategory || 'Salário Principal'}
                    onValueChange={(v) => setSubcategory(v)}
                  >
                    <SelectTrigger className="h-10 text-xs bg-slate-900/90 border-slate-700/80 rounded-xl">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {CATEGORY_GROUPS.Receita.subcategories.map((sub) => (
                        <SelectItem key={sub} value={sub}>
                          {sub}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Forma de Recebimento</label>
                  <Select value={paymentMethod} onValueChange={(v) => setPaymentMethod(v as PaymentMethod)}>
                    <SelectTrigger className="h-10 text-xs bg-slate-900/90 border-slate-700/80 rounded-xl">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="pix">⚡ Pix</SelectItem>
                      <SelectItem value="debit">🏦 Conta Corrente / TED</SelectItem>
                      <SelectItem value="bill">📄 Boleto / Outro</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </>
            ) : (
              <>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Categoria Principal</label>
                  <Select
                    value={category}
                    onValueChange={(v) => {
                      const newCat = v as Category
                      setCategory(newCat)
                      const subs = CATEGORY_GROUPS[newCat]?.subcategories || []
                      setSubcategory(subs[0] || '')
                    }}
                  >
                    <SelectTrigger className="h-10 text-xs bg-slate-900/90 border-slate-700/80 rounded-xl">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {CATEGORIES.filter((c) => c !== 'Receita').map((c) => (
                        <SelectItem key={c} value={c}>
                          {c === 'Estorno' ? '🔄 Estorno / Crédito na Fatura' : c}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Subcategoria</label>
                  <Select
                    value={subcategory || (CATEGORY_GROUPS[category]?.subcategories?.[0] ?? '')}
                    onValueChange={(v) => setSubcategory(v)}
                  >
                    <SelectTrigger className="h-10 text-xs bg-slate-900/90 border-slate-700/80 rounded-xl">
                      <SelectValue placeholder="Selecione um subgrupo" />
                    </SelectTrigger>
                    <SelectContent>
                      {(CATEGORY_GROUPS[category]?.subcategories || []).map((sub) => (
                        <SelectItem key={sub} value={sub}>
                          {sub}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </>
            )}

            {/* Status do Lançamento */}
            <div className="space-y-1.5 sm:col-span-2">
              <label className="text-xs font-semibold text-slate-300">Status do Lançamento</label>
              <div className="grid grid-cols-3 gap-2">
                <button
                  type="button"
                  onClick={() => setStatus('pending')}
                  className={`py-2 px-2.5 rounded-xl text-xs font-semibold border transition-all cursor-pointer text-center ${
                    status === 'pending'
                      ? 'bg-amber-500/20 text-amber-300 border-amber-500/50 shadow-sm'
                      : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-slate-200'
                  }`}
                >
                  🟠 Pendente
                </button>
                <button
                  type="button"
                  onClick={() => setStatus('paid')}
                  className={`py-2 px-2.5 rounded-xl text-xs font-semibold border transition-all cursor-pointer text-center ${
                    status === 'paid'
                      ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/50 shadow-sm'
                      : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-slate-200'
                  }`}
                >
                  🟢 Pago
                </button>
                <button
                  type="button"
                  onClick={() => setStatus('estimated')}
                  className={`py-2 px-2.5 rounded-xl text-xs font-semibold border transition-all cursor-pointer text-center ${
                    status === 'estimated'
                      ? 'bg-cyan-500/20 text-cyan-300 border-cyan-500/50 shadow-sm'
                      : 'bg-slate-900/60 border-slate-800 text-slate-400 hover:text-slate-200'
                  }`}
                >
                  🟡 Estimado
                </button>
              </div>
            </div>
          </div>

          {/* 3. PAGAMENTO E CARTÃO (SE DESPESA) */}
          {!isIncomeType && (
            <div className="space-y-3 p-3.5 rounded-2xl bg-slate-950/60 border border-white/[0.07]">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold text-slate-300">Método de Pagamento</label>
                  <Select value={paymentMethod} onValueChange={(v) => setPaymentMethod(v as PaymentMethod)}>
                    <SelectTrigger className="h-10 text-xs bg-slate-900/90 border-slate-700/80 rounded-xl">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="credit_card">💳 Cartão de Crédito</SelectItem>
                      <SelectItem value="pix">⚡ Pix</SelectItem>
                      <SelectItem value="debit">🏦 Débito em Conta</SelectItem>
                      <SelectItem value="bill">📄 Boleto Bancário</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                {paymentMethod === 'credit_card' && (
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-slate-300">Cartão Utilizado</label>
                    {cards.length === 0 ? (
                      <div className="p-2.5 rounded-xl bg-amber-500/10 border border-amber-500/25 text-amber-300 text-xs flex items-center gap-2">
                        <span>Nenhum cartão cadastrado. Cadastre um cartão no menu para vincular faturas e vencimentos.</span>
                      </div>
                    ) : (
                      <Select value={cardId} onValueChange={setCardId}>
                        <SelectTrigger className="h-10 text-xs bg-slate-900/90 border-slate-700/80 rounded-xl">
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
                    )}
                  </div>
                )}
              </div>

              {/* Compra Parcelada Toggle */}
              {paymentMethod === 'credit_card' && !editingTx && (
                <div className="pt-2 border-t border-slate-800/80 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <CreditCard className="size-4 text-violet-400" />
                      <div>
                        <span className="text-xs font-bold text-white block">Compra Parcelada?</span>
                        <span className="text-[10px] text-slate-400">Gera repetições automáticas nos meses seguintes</span>
                      </div>
                    </div>
                    <input
                      type="checkbox"
                      checked={isInstallment}
                      onChange={(e) => setIsInstallment(e.target.checked)}
                      className="size-5 accent-emerald-500 rounded cursor-pointer"
                    />
                  </div>

                  {isInstallment && (
                    <div className="p-3 rounded-xl bg-violet-950/20 border border-violet-500/30 space-y-2.5 animate-in fade-in">
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                        <div className="space-y-1">
                          <label className="text-[11px] font-semibold text-slate-300">Número de Parcelas</label>
                          <Select
                            value={String(installmentsCount)}
                            onValueChange={(v) => setInstallmentsCount(Number(v))}
                          >
                            <SelectTrigger className="h-8 text-xs bg-slate-900 border-slate-700 rounded-lg">
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
                          <label className="text-[11px] font-semibold text-slate-300">O valor digitado acima é:</label>
                          <div className="flex gap-1 pt-0.5">
                            <button
                              type="button"
                              onClick={() => setInstallmentMode('total')}
                              className={`flex-1 py-1.5 px-2 text-xs rounded-lg border font-semibold cursor-pointer transition-all ${
                                installmentMode === 'total'
                                  ? 'bg-violet-500/25 text-violet-300 border-violet-500/40'
                                  : 'bg-slate-900 text-slate-400 border-slate-800'
                              }`}
                            >
                              Valor Total
                            </button>
                            <button
                              type="button"
                              onClick={() => setInstallmentMode('per_installment')}
                              className={`flex-1 py-1.5 px-2 text-xs rounded-lg border font-semibold cursor-pointer transition-all ${
                                installmentMode === 'per_installment'
                                  ? 'bg-violet-500/25 text-violet-300 border-violet-500/40'
                                  : 'bg-slate-900 text-slate-400 border-slate-800'
                              }`}
                            >
                              Por Parcela
                            </button>
                          </div>
                        </div>
                      </div>

                      {parseFloat(amount.replace(',', '.')) > 0 && (
                        <p className="text-xs text-violet-300 font-medium">
                          ✓ Serão geradas <strong>{installmentsCount} parcelas</strong> de{' '}
                          <strong className="text-white font-mono">
                            {formatBRL(
                              installmentMode === 'total'
                                ? parseFloat(amount.replace(',', '.')) / installmentsCount
                                : parseFloat(amount.replace(',', '.')),
                            )}
                          </strong>{' '}
                          cada.
                        </p>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* 4. DIVISÃO INTELIGENTE COM AMIGOS E PARTICIPANTES (SE DESPESA) */}
          {!isIncomeType && (
            <div className="space-y-3 p-3.5 rounded-2xl bg-slate-950/60 border border-white/[0.07]">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-300 flex items-center justify-between">
                  <span>Quem Paga / Divisão</span>
                  <span className="text-[10px] text-slate-400 font-normal">
                    {splitType === 'mine' ? '👤 100% Meu' : splitType === 'split_50' ? '🤝 50/50' : '👥 Personalizada'}
                  </span>
                </label>
                <Select
                  value={splitType === 'friend' ? 'custom' : splitType}
                  onValueChange={(v) => handleSplitTypeChange(v as SplitType)}
                >
                  <SelectTrigger className="h-10 text-xs bg-slate-900/90 border-slate-700/80 rounded-xl">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="mine">👤 100% Meu (Desconta do meu Saldo Livre)</SelectItem>
                    {partner && <SelectItem value="split_50">🤝 Dividido 50/50 com {partner.name}</SelectItem>}
                    {partner && <SelectItem value="partner">🔄 100% {partner.name} (Neutro no meu Saldo)</SelectItem>}
                    <SelectItem value="custom">🧑‍🤝‍🧑 Dividir com Amigos / Multi-pessoas (% ou R$)</SelectItem>
                    <SelectItem value="external">🏷️ Terceiro Externo não cadastrado</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {splitType === 'external' && (
                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-slate-300">Nome do Terceiro</label>
                  <Input
                    value={externalName}
                    onChange={(e) => setExternalName(e.target.value)}
                    placeholder="Ex: Mãe, Irmão, Carlos..."
                    className="h-10 text-xs bg-slate-900 border-slate-700 rounded-xl"
                  />
                </div>
              )}

              {/* Painel de Divisão Personalizada Multi-Pessoas */}
              {(splitType === 'custom' || splitType === 'friend') && (
                <div className="space-y-3 p-3.5 rounded-xl bg-slate-900/80 border border-slate-700/70 shadow-sm animate-in fade-in">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-2 border-b border-slate-800">
                    <div>
                      <span className="text-xs font-bold text-white flex items-center gap-1.5">
                        <Users2 className="size-3.5 text-violet-400" />
                        <span>Participantes da Divisão</span>
                      </span>
                      <p className="text-[11px] text-slate-400">
                        Ajuste em porcentagem (%) ou valor exato (R$) para cada amigo.
                      </p>
                    </div>

                    <div className="flex items-center gap-1 shrink-0 flex-wrap">
                      {friends.length > 0 && (
                        <button
                          type="button"
                          onClick={() => handleQuickFriendPreset(friends[0].uid, 50)}
                          className="px-2 py-1 text-[10px] font-semibold rounded-lg bg-violet-950/70 hover:bg-violet-900/70 text-violet-300 border border-violet-700/60 cursor-pointer"
                        >
                          50/50 {(friends[0].name || 'Amigo').split(' ')[0]}
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={handleDistributeEqually}
                        className="px-2 py-1 text-[10px] font-semibold rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 cursor-pointer"
                      >
                        ⚖️ Igual
                      </button>
                      <button
                        type="button"
                        onClick={handleAdjustDiffToMe}
                        className="px-2 py-1 text-[10px] font-semibold rounded-lg bg-emerald-950/60 hover:bg-emerald-900/60 text-emerald-300 border border-emerald-700/60 cursor-pointer"
                      >
                        ⚡ Diferença em Mim
                      </button>
                    </div>
                  </div>

                  {/* Barra de Progresso de Distribuição */}
                  {(() => {
                    const numTotal = Math.abs(parseFloat(amount.replace(',', '.'))) || 0
                    const totalAllocated = customParticipants.reduce((s, p) => s + p.amount, 0)
                    const diff = round2(numTotal - totalAllocated)
                    const isBalanced = numTotal > 0 && Math.abs(diff) <= 0.05

                    return (
                      <div className="p-2.5 rounded-lg bg-slate-950 border border-slate-800/80 space-y-1.5">
                        <div className="flex items-center justify-between text-xs">
                          <span className="text-slate-400">
                            Total: <strong className="text-white font-mono">{formatBRL(numTotal)}</strong>
                          </span>
                          <div>
                            {isBalanced ? (
                              <Badge variant="outline" className="text-[10px] bg-emerald-500/10 text-emerald-300 border-emerald-500/30 gap-1 font-semibold">
                                <CheckCircle2 className="size-2.5" /> 100% Equilibrado
                              </Badge>
                            ) : diff > 0 ? (
                              <Badge variant="outline" className="text-[10px] bg-amber-500/10 text-amber-300 border-amber-500/30 gap-1 font-semibold">
                                <AlertTriangle className="size-2.5" /> Restam {formatBRL(diff)}
                              </Badge>
                            ) : (
                              <Badge variant="outline" className="text-[10px] bg-rose-500/10 text-rose-300 border-rose-500/30 gap-1 font-semibold">
                                <AlertTriangle className="size-2.5" /> Excedeu {formatBRL(Math.abs(diff))}
                              </Badge>
                            )}
                          </div>
                        </div>

                        {/* Barra multicolorida */}
                        <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden flex">
                          {customParticipants.map((p, idx) => (
                            <div
                              key={p.id}
                              className={`h-full ${PARTICIPANT_COLORS[idx % PARTICIPANT_COLORS.length]} transition-all`}
                              style={{ width: `${Math.min(100, Math.max(0, p.percentage))}%` }}
                            />
                          ))}
                        </div>
                      </div>
                    )
                  })()}

                  {/* Linhas de Cada Participante */}
                  <div className="space-y-2">
                    {customParticipants.map((p, idx) => {
                      const color = PARTICIPANT_COLORS[idx % PARTICIPANT_COLORS.length]
                      return (
                        <div
                          key={p.id}
                          className="p-2.5 rounded-xl bg-slate-950/70 border border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-2"
                        >
                          <div className="flex items-center gap-2 flex-1 min-w-0">
                            <span className={`size-2.5 rounded-full ${color} shrink-0`} />
                            {p.type === 'external' ? (
                              <Input
                                value={p.name}
                                onChange={(e) => {
                                  const val = e.target.value
                                  setCustomParticipants((prev) =>
                                    prev.map((item) => (item.id === p.id ? { ...item, name: val } : item)),
                                  )
                                }}
                                placeholder="Nome do terceiro"
                                className="h-7 text-xs bg-slate-900 border-slate-700 flex-1 max-w-[140px]"
                              />
                            ) : (
                              <span className="text-xs font-semibold text-slate-200 truncate">
                                {p.name}
                              </span>
                            )}
                            <Badge variant="outline" className="text-[9px] py-0 px-1 border border-slate-700 text-slate-300">
                              {p.type === 'me'
                                ? 'Você'
                                : p.type === 'partner'
                                  ? 'Parceiro(a)'
                                  : p.type === 'friend'
                                    ? 'Amigo(a)'
                                    : 'Terceiro'}
                            </Badge>
                          </div>

                          <div className="flex items-center gap-2 shrink-0">
                            {/* Preset pills */}
                            <div className="flex items-center gap-1">
                              {[100, 50, 25].map((pctPreset) => (
                                <button
                                  key={pctPreset}
                                  type="button"
                                  onClick={() => handleSetParticipantPctPreset(p.id, pctPreset)}
                                  className={`px-1.5 py-0.5 text-[9px] font-semibold rounded border transition-all cursor-pointer ${
                                    Math.round(p.percentage) === pctPreset
                                      ? 'bg-violet-500/30 text-violet-300 border-violet-500/60 font-bold'
                                      : 'bg-slate-900 text-slate-400 border-slate-800'
                                  }`}
                                >
                                  {pctPreset}%
                                </button>
                              ))}
                            </div>

                            {/* % Input */}
                            <div className="relative w-16">
                              <Input
                                type="number"
                                min={0}
                                max={100}
                                step={0.5}
                                value={p.percentage}
                                onChange={(e) => handleParticipantPctChange(p.id, parseFloat(e.target.value))}
                                className="h-7 text-xs bg-slate-900 border-slate-700 pr-4 font-mono text-right"
                              />
                              <span className="absolute right-1 top-1/2 -translate-y-1/2 text-[10px] text-slate-500 font-bold">
                                %
                              </span>
                            </div>

                            {/* R$ Input */}
                            <div className="relative w-24">
                              <Input
                                type="number"
                                min={0}
                                step={0.01}
                                value={p.amount}
                                onChange={(e) => handleParticipantAmountChange(p.id, parseFloat(e.target.value))}
                                className="h-7 text-xs bg-slate-900 border-slate-700 pl-5 font-mono text-right"
                              />
                              <span className="absolute left-1 top-1/2 -translate-y-1/2 text-[10px] text-slate-500 font-bold">
                                R$
                              </span>
                            </div>

                            {p.type !== 'me' && customParticipants.length > 1 && (
                              <button
                                type="button"
                                onClick={() => handleRemoveParticipant(p.id)}
                                className="p-1 rounded text-slate-500 hover:text-rose-400 transition-colors cursor-pointer"
                                title="Remover"
                              >
                                <Trash2 className="size-3.5" />
                              </button>
                            )}
                          </div>
                        </div>
                      )
                    })}
                  </div>

                  {/* Botões de Adicionar Participante */}
                  <div className="pt-2 border-t border-slate-800 flex items-center gap-1.5 flex-wrap">
                    <span className="text-[10px] text-slate-400 font-medium mr-1 flex items-center gap-1">
                      <UserPlus className="size-3 text-slate-500" /> + Adicionar:
                    </span>

                    {partner && !customParticipants.some((p) => p.type === 'partner') && (
                      <button
                        type="button"
                        onClick={() => handleAddParticipant('partner')}
                        className="px-2 py-1 text-[10px] font-semibold rounded-lg bg-sky-950/60 hover:bg-sky-900/60 text-sky-300 border border-sky-700/60 cursor-pointer"
                      >
                        🤝 {partner.name}
                      </button>
                    )}

                    {friends
                      .filter((f) => !customParticipants.some((p) => p.uid === f.uid))
                      .map((f) => (
                        <button
                          key={f.uid}
                          type="button"
                          onClick={() => handleAddParticipant('friend', f.uid)}
                          className="px-2 py-1 text-[10px] font-semibold rounded-lg bg-violet-950/60 hover:bg-violet-900/60 text-violet-300 border border-violet-700/60 cursor-pointer"
                        >
                          🧑‍🤝‍🧑 {f.name}
                        </button>
                      ))}

                    <button
                      type="button"
                      onClick={() => handleAddParticipant('external')}
                      className="px-2 py-1 text-[10px] font-semibold rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 cursor-pointer"
                    >
                      🏷️ + Terceiro
                    </button>
                  </div>

                  {/* Aviso de Aceite Mútuo de Amigos */}
                  {customParticipants.some((p) => p.type === 'friend') && (
                    <div className="p-2.5 rounded-xl bg-violet-950/40 border border-violet-500/30 flex items-start gap-2 text-xs text-violet-200">
                      <ShieldCheck className="size-4 text-violet-400 shrink-0 mt-0.5" />
                      <p className="text-[11px] text-violet-300/80 leading-relaxed">
                        A despesa será lançada no seu cartão e uma solicitação de aprovação será enviada automaticamente para{' '}
                        <strong>
                          {customParticipants
                            .filter((p) => p.type === 'friend')
                            .map((p) => p.name)
                            .join(', ')}
                        </strong>.
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* 5. REFLEXO NO SALDO LIVRE (LIVE IMPACT PREVIEW) */}
          {parseFloat(amount.replace(',', '.')) > 0 && (() => {
            const rawVal = parseFloat(amount.replace(',', '.'))
            if (isIncomeType) {
              return (
                <div className="p-3 rounded-2xl bg-emerald-950/30 border border-emerald-500/25 flex items-center justify-between text-xs animate-in fade-in">
                  <div className="flex items-center gap-2">
                    <span className="size-2 rounded-full bg-emerald-400 animate-ping" />
                    <span className="text-slate-300 font-medium">Impacto no Saldo Livre Real:</span>
                  </div>
                  <strong className="text-sm font-bold font-mono text-emerald-400">
                    +{formatBRL(rawVal)}
                  </strong>
                </div>
              )
            }

            const isCustom = splitType === 'custom' || splitType === 'friend'
            let myVal = rawVal
            let othersVal = 0

            if (isCustom && customParticipants.length > 0) {
              const myRow = customParticipants.find((p) => p.type === 'me')
              myVal = myRow ? myRow.amount : 0
              othersVal = customParticipants
                .filter((p) => p.type !== 'me')
                .reduce((s, p) => s + p.amount, 0)
            } else if (splitType === 'split_50') {
              myVal = rawVal / 2
              othersVal = rawVal / 2
            } else if (splitType === 'partner') {
              myVal = 0
              othersVal = rawVal
            } else if (splitType === 'external') {
              myVal = 0
              othersVal = rawVal
            }

            return (
              <div className="p-3 rounded-2xl bg-slate-950/70 border border-white/[0.07] space-y-2 text-xs">
                <span className="font-semibold text-slate-300 block">
                  Resumo do Impacto Financeiro:
                </span>
                <div className="grid grid-cols-2 gap-2 text-[11px]">
                  <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800">
                    <span className="text-slate-400 block mb-0.5">Seu Saldo Livre:</span>
                    <strong className={`text-xs font-mono font-bold ${myVal > 0 ? 'text-rose-400' : 'text-slate-400'}`}>
                      {myVal > 0 ? `−${formatBRL(myVal)}` : 'R$ 0,00 (Neutro)'}
                    </strong>
                  </div>

                  <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800">
                    <span className="text-slate-400 block mb-0.5">A Receber / Amigos:</span>
                    <strong className="text-xs font-mono font-bold text-emerald-400">
                      {othersVal > 0 ? `+${formatBRL(othersVal)}` : 'R$ 0,00'}
                    </strong>
                  </div>
                </div>
              </div>
            )
          })()}

          {/* Mensagem de Erro (se houver) */}
          {errorMessage && (
            <div className="p-3 rounded-xl bg-rose-950/50 border border-rose-500/40 text-xs text-rose-300 flex items-start gap-2 animate-in fade-in">
              <AlertTriangle className="size-4 text-rose-400 shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Footer com Ações */}
          <DialogFooter className="flex-col-reverse sm:flex-row items-center justify-between gap-2 pt-3 border-t border-white/[0.06]">
            {editingTx && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setDeleteConfirmOpen(true)}
                className="text-xs text-rose-400 hover:text-rose-300 hover:bg-rose-500/10 gap-1.5 cursor-pointer w-full sm:w-auto sm:mr-auto"
                title="Excluir lançamento"
              >
                <Trash2 className="size-3.5" />
                <span>Excluir Lançamento</span>
              </Button>
            )}

            <div className="flex items-center gap-2 w-full sm:w-auto sm:ml-auto">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => onOpenChange(false)}
                className="flex-1 sm:flex-initial h-9 text-xs border-slate-800 text-slate-300 hover:text-white rounded-xl"
              >
                Cancelar
              </Button>
              <Button
                type="submit"
                size="sm"
                disabled={isSubmitting}
                className="flex-1 sm:flex-initial h-9 text-xs bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-xl shadow-md shadow-emerald-950/40 disabled:opacity-50"
              >
                {isSubmitting ? (
                  <span className="flex items-center gap-1.5">
                    <Loader2 className="size-3.5 animate-spin" />
                    Salvando...
                  </span>
                ) : editingTx ? (
                  editingTx.installmentGroupId && editScope === 'all'
                    ? `Salvar em Todas as ${editingTx.installmentsTotal || ''} Parcelas`
                    : 'Atualizar Lançamento'
                ) : isInstallment ? (
                  `Criar ${installmentsCount} Parcelas`
                ) : (
                  'Salvar Lançamento'
                )}
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
