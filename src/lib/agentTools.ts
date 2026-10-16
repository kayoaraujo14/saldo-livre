/**
 * Implementação das ferramentas do agente (usada tanto pelo Gemini real
 * quanto pelo agente offline do Modo Demo).
 */
import type { Card, Category, NewTransaction, PaymentMethod, SplitType, Transaction, TransactionStatus } from '@/types'
import { CATEGORIES } from '@/types'
import {
  amountPatch,
  buildSplit,
  computeSummary,
  effectiveAmount,
  getReserveInfo,
  guessCategory,
  impactFor,
  listReceivables,
  pickCard,
  SPLIT_LABELS,
  type Party,
} from './finance'
import type { ToolExecutor, ToolName, ToolResult } from './gemini'
import { formatBRL, formatShortDate, monthLabel, normalize, round2, todayKey } from './utils'

export interface AgentEnv {
  getTransactions: () => Transaction[]
  getCards: () => Card[]
  me: Party
  partner: Party | null
  monthKey: string
  addTransaction: (tx: NewTransaction) => Promise<string>
  updateTransaction: (id: string, patch: Partial<Transaction>) => Promise<void>
}

const SPLIT_MAP: Record<string, SplitType> = {
  MEU: 'mine',
  CASAL_50_50: 'split_50',
  PARCEIRO: 'partner',
  TERCEIRO: 'external',
}

const SYNONYMS: Record<string, string[]> = {
  luz: ['luz', 'energia', 'enel', 'cemig', 'light', 'copel'],
  agua: ['agua', 'sabesp', 'saneamento'],
  financiamento: ['financiamento', 'carro', 'parcela do carro'],
  internet: ['internet', 'fibra', 'wifi'],
  aluguel: ['aluguel'],
}

function matchesSearch(description: string, search: string) {
  const d = normalize(description)
  const s = normalize(search)
  if (d.includes(s)) return true
  for (const [key, list] of Object.entries(SYNONYMS)) {
    if (list.some((w) => s.includes(w)) && (d.includes(key) || list.some((w) => d.includes(w)))) return true
  }
  return s.split(/\s+/).filter((w) => w.length > 3).some((w) => d.includes(w))
}

function money(n: number) {
  return formatBRL(n)
}

export function createToolExecutor(getEnv: () => AgentEnv): ToolExecutor {
  return async (name: ToolName, args: Record<string, unknown>): Promise<ToolResult> => {
    const env = getEnv()
    switch (name) {
      case 'registrarTransacao':
        return registrar(env, args)
      case 'atualizarContaPrevista':
        return atualizar(env, args)
      case 'obterRelatorioEProjecaoMensal':
        return relatorio(env)
      default:
        return { response: { ok: false, erro: `Ferramenta desconhecida: ${name}` } }
    }
  }
}

async function registrar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const total = round2(Number(args.valorTotal))
  if (!total || total <= 0) return { response: { ok: false, erro: 'valorTotal inválido' } }

  const type = args.tipo === 'income' ? 'income' : 'expense'
  let splitType: SplitType = SPLIT_MAP[String(args.tipoDivisao ?? 'MEU')] ?? 'mine'
  if (type === 'income') splitType = 'mine'
  const method = (['pix', 'credit_card', 'debit', 'bill'].includes(String(args.metodoPagamento))
    ? args.metodoPagamento
    : 'pix') as PaymentMethod
  const description = String(args.descricao ?? 'Lançamento').trim()
  let category = (CATEGORIES as readonly string[]).includes(String(args.categoria))
    ? (args.categoria as Category)
    : guessCategory(description)
  if (splitType === 'external' && category === 'Outros') category = 'Cartão Terceiros'
  if (type === 'income') category = 'Receita'

  const date = /^\d{4}-\d{2}-\d{2}$/.test(String(args.data)) ? String(args.data) : todayKey()
  const status = (['estimated', 'pending', 'paid'].includes(String(args.status))
    ? args.status
    : method === 'credit_card'
      ? 'pending'
      : 'paid') as TransactionStatus

  const cards = env.getCards()
  const card = method === 'credit_card' ? pickCard(env.me.uid, cards, splitType, args.cartao as string | undefined) : undefined
  const payerUid = card?.ownerUid ?? env.me.uid

  const split = buildSplit({
    splitType,
    total,
    me: env.me,
    partner: env.partner,
    payerUid,
    createdByUid: env.me.uid,
    externalName: args.nomeTerceiro as string | undefined,
  })

  const competenceMonth = date.slice(0, 7)
  const before = computeSummary(env.getTransactions(), env.me.uid, competenceMonth)

  const tx: NewTransaction = {
    createdByUid: env.me.uid,
    paidByUid: payerUid,
    cardId: card?.id ?? null,
    description,
    category,
    type,
    paymentMethod: method,
    status,
    estimatedAmount: status === 'estimated' ? total : null,
    actualAmount: status === 'estimated' ? null : total,
    totalAmount: total,
    dueDate: date,
    competenceMonth,
    visibleToUids: split.visibleToUids,
    userImpact: split.userImpact,
    allocations: split.allocations,
    recurringBillId: null,
    reserveCardId: null,
    createdAt: Date.now(),
  }
  const id = await env.addTransaction(tx)
  const after = computeSummary([...env.getTransactions().filter((t) => t.id !== id), { ...tx, id }], env.me.uid, competenceMonth)

  const myImpact = split.userImpact[env.me.uid] ?? 0
  const receivable = payerUid === env.me.uid ? round2(total - myImpact) : 0
  const label = SPLIT_LABELS[split.splitType]

  return {
    response: {
      ok: true,
      id,
      descricao: description,
      valorTotal: total,
      categoria: category,
      cartao: card ? `${card.cardName} (titular: ${card.ownerName})` : null,
      divisao: label.label,
      meuImpactoNoSaldoLivre: myImpact,
      aReceberGerado: receivable,
      saldoLivreAntes: before.freeBalance,
      saldoLivreDepois: after.freeBalance,
      limiteDiarioDepois: after.dailyLimit,
      diasRestantes: after.daysRemaining,
    },
    action: {
      kind: 'created',
      title: `${type === 'income' ? 'Receita' : 'Despesa'} registrada: ${description}`,
      lines: [
        { label: 'Valor total', value: money(total) },
        { label: 'Divisão', value: `${label.emoji} ${split.splitType === 'external' ? split.allocations[0].name : label.label}` },
        ...(card ? [{ label: 'Cartão', value: card.cardName }] : []),
        { label: 'Meu impacto', value: type === 'income' ? `+${money(myImpact)}` : `−${money(myImpact)}`, tone: type === 'income' ? 'positive' : myImpact > 0 ? 'negative' : 'neutral' },
        ...(receivable > 0 ? [{ label: 'A receber', value: money(receivable), tone: 'positive' as const }] : []),
        { label: 'Saldo Livre', value: `${money(before.freeBalance)} → ${money(after.freeBalance)}` },
      ],
    },
  }
}

async function atualizar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const search = String(args.buscaDescricao ?? '')
  const value = round2(Number(args.valorReal))
  const newStatus = (args.novoStatus === 'paid' ? 'paid' : 'pending') as TransactionStatus
  const all = env.getTransactions()

  const open = (month: string) =>
    all.filter(
      (t) =>
        t.competenceMonth === month &&
        t.visibleToUids.includes(env.me.uid) &&
        t.type === 'expense' &&
        (t.status === 'estimated' || t.status === 'pending'),
    )
  let candidates = open(env.monthKey).filter((t) => matchesSearch(t.description, search))
  if (!candidates.length) candidates = open(todayKey().slice(0, 7)).filter((t) => matchesSearch(t.description, search))
  candidates.sort((a, b) => Number(!!b.recurringBillId) - Number(!!a.recurringBillId) || (a.status === 'estimated' ? -1 : 1))
  const tx = candidates[0]

  if (!tx || !value) {
    const list = open(env.monthKey).map((t) => `${t.description} (${money(effectiveAmount(t))})`)
    return {
      response: { ok: false, erro: `Nenhuma conta estimada/a pagar encontrada para "${search}".`, contasEmAberto: list },
      action: { kind: 'error', title: `Conta "${search}" não encontrada`, lines: [{ label: 'Em aberto', value: String(list.length) }] },
    }
  }

  const before = computeSummary(all, env.me.uid, tx.competenceMonth)
  const oldImpact = impactFor(tx, env.me.uid, all)
  const estimated = tx.estimatedAmount ?? tx.totalAmount
  const patch: Partial<Transaction> = {
    ...amountPatch(tx, { actualAmount: value, estimatedAmount: tx.estimatedAmount ?? tx.totalAmount }),
    status: newStatus,
  }
  if (typeof args.dataVencimento === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.dataVencimento)) {
    patch.dueDate = args.dataVencimento
  }
  const updated = { ...tx, ...patch } as Transaction
  const nextAll = all.map((t) => (t.id === tx.id ? updated : t))
  const after = computeSummary(nextAll, env.me.uid, tx.competenceMonth)
  const newImpact = impactFor(updated, env.me.uid, nextAll)
  await env.updateTransaction(tx.id, patch)

  const diffTotal = round2(value - estimated)
  const diffSaldo = round2(after.freeBalance - before.freeBalance)

  return {
    response: {
      ok: true,
      conta: tx.description,
      valorEstimado: estimated,
      valorReal: value,
      diferencaVsEstimado: diffTotal,
      meuImpactoAntes: oldImpact,
      meuImpactoDepois: newImpact,
      variacaoNoMeuSaldoLivre: diffSaldo,
      novoStatus: newStatus === 'paid' ? 'Pago' : 'A Pagar (valor real)',
      vencimento: updated.dueDate,
      saldoLivreDepois: after.freeBalance,
      limiteDiarioDepois: after.dailyLimit,
    },
    action: {
      kind: 'updated',
      title: `${tx.description} atualizada`,
      lines: [
        { label: 'Estimado → Real', value: `${money(estimated)} → ${money(value)}` },
        {
          label: 'Diferença',
          value: diffTotal === 0 ? 'Igual à estimativa' : diffTotal > 0 ? `+${money(diffTotal)} vs estimado` : `${money(diffTotal)} economizado`,
          tone: diffTotal > 0 ? 'negative' : diffTotal < 0 ? 'positive' : 'neutral',
        },
        { label: 'Status', value: newStatus === 'paid' ? '🟢 Pago' : '🟠 A Pagar (Real)' },
        { label: 'Vencimento', value: formatShortDate(updated.dueDate) },
        {
          label: 'Saldo Livre',
          value: `${money(before.freeBalance)} → ${money(after.freeBalance)}`,
          tone: diffSaldo < 0 ? 'negative' : diffSaldo > 0 ? 'positive' : 'neutral',
        },
      ],
    },
  }
}

async function relatorio(env: AgentEnv): Promise<ToolResult> {
  const all = env.getTransactions()
  const s = computeSummary(all, env.me.uid, env.monthKey)
  const { receivables, payables } = listReceivables(all, env.me.uid, env.monthKey, env.getCards())
  const monthTxs = all.filter((t) => t.competenceMonth === env.monthKey && t.visibleToUids.includes(env.me.uid))
  const open = monthTxs
    .filter((t) => t.type === 'expense' && t.status !== 'paid' && impactFor(t, env.me.uid, all) > 0)
    .map((t) => ({
      descricao: t.description,
      status: t.status === 'estimated' ? 'Estimado' : 'A Pagar',
      vencimento: t.dueDate,
      meuImpacto: impactFor(t, env.me.uid, all),
    }))
  const reserves = monthTxs
    .filter((t) => t.reserveCardId && t.createdByUid === env.me.uid)
    .map((t) => ({ descricao: t.description, ...getReserveInfo(t, all) }))

  return {
    response: {
      ok: true,
      mes: monthLabel(env.monthKey),
      receitas: s.income,
      jaPago: s.paid,
      comprometido: s.committed,
      comprometidoEstimado: s.committedEstimated,
      comprometidoAPagarReal: s.committedPending,
      saldoLivreReal: s.freeBalance,
      diasRestantes: s.daysRemaining,
      limiteDiarioSeguro: s.dailyLimit,
      gastosPorCategoria: s.byCategory,
      contasEmAberto: open,
      reservasDeCartao: reserves,
      aReceberTotal: s.receivableTotal,
      aReceberDoParceiro: s.receivableFromPartner,
      aReceberDeTerceiros: s.receivableFromExternal,
      itensAReceber: receivables.map((r) => ({ de: r.name, descricao: r.description, valor: r.amount })),
      euDevoAoParceiro: s.payableToOthers,
      itensQueEuDevo: payables.map((r) => ({ descricao: r.description, valor: r.amount })),
    },
    action: {
      kind: 'report',
      title: `Projeção de ${monthLabel(env.monthKey)}`,
      lines: [
        { label: 'Saldo Livre Real', value: money(s.freeBalance), tone: s.freeBalance >= 0 ? 'positive' : 'negative' },
        { label: 'Limite diário', value: s.dailyLimit != null ? `${money(s.dailyLimit)} × ${s.daysRemaining}d` : 'Mês encerrado' },
        { label: 'Comprometido', value: money(s.committed) },
        { label: 'A receber', value: money(s.receivableTotal), tone: 'positive' },
      ],
    },
  }
}
