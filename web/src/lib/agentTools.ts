/**
 * Implementação das ferramentas do agente (executadas pelo motor do Google Gemini).
 */
import type { Allocation, Card, Category, NewTransaction, PaymentMethod, PendingBatchTransaction, RecurringBill, SplitType, Transaction, TransactionStatus, UserProfile } from '@/types'
import { CATEGORIES } from '@/types'
import { extractCounterparty } from './pdfParser'
import {
  amountPatch,
  buildSplit,
  calculateCardInvoiceDates,
  clampPct,
  computeSummary,
  detectSplit,
  effectiveAmount,
  getReserveInfo,
  guessCategoryAndSubcategory,
  impactFor,
  listReceivables,
  pickCard,
  SPLIT_LABELS,
  type Party,
} from './finance'
import type { ToolExecutor, ToolName, ToolResult } from './gemini'
import {
  currentMonthKey,
  dateInMonth,
  formatBRL,
  formatInstallmentDesc,
  formatShortDate,
  monthLabel,
  normalize,
  round2,
  shiftDateMonth,
  shiftMonth,
  stripInstallmentSuffix,
  todayKey,
  uid,
} from './utils'
import { fsDeleteAgentRule, fsSaveAgentRule } from './firebase'

export interface AgentEnv {
  getTransactions: () => Transaction[]
  getCards: () => Card[]
  getRecurring?: () => RecurringBill[]
  getFriends?: () => Array<{ uid: string; name: string; email?: string }>
  getPendingTransactions?: () => PendingBatchTransaction[] | undefined
  setPendingTransactions?: (txs: PendingBatchTransaction[]) => void
  lookupUserByEmail?: (email: string) => Promise<UserProfile | null>
  me: Party
  partner: Party | null
  monthKey: string
  requireConfirmation?: boolean
  addTransaction: (tx: NewTransaction) => Promise<string>
  addMany?: (txs: NewTransaction[]) => Promise<void>
  updateTransaction: (id: string, patch: Partial<Transaction>) => Promise<void>
  deleteTransaction: (id: string) => Promise<void>
  addCard?: (card: Partial<Card>) => Promise<string>
  updateCard?: (id: string, patch: Partial<Card>) => Promise<void>
  deleteCard?: (id: string) => Promise<void>
  addRecurring?: (bill: Partial<RecurringBill>) => Promise<string>
  updateRecurring?: (id: string, patch: Partial<RecurringBill>) => Promise<void>
  deleteRecurring?: (id: string) => Promise<void>
  addFriend?: (email: string) => Promise<void>
  respondFriend?: (id: string, accept: boolean) => Promise<void>
}

const SPLIT_MAP: Record<string, SplitType> = {
  MEU: 'mine',
  CASAL_50_50: 'split_50',
  PARCEIRO: 'partner',
  AMIGO: 'friend',
  TERCEIRO: 'external',
  PERSONALIZADA: 'custom',
  CUSTOM: 'custom',
}

const SYNONYMS: Record<string, string[]> = {
  luz: ['luz', 'energia', 'enel', 'cemig', 'light', 'copel'],
  agua: ['agua', 'sabesp', 'saneamento'],
  financiamento: ['financiamento', 'carro', 'parcela do carro'],
  internet: ['internet', 'fibra', 'wifi'],
  aluguel: ['aluguel'],
}

export const INCOME_KEYWORDS_REGEX =
  /(salario|salário|folha|remunera|pro-?labore|recebido|recebimento|pix recebido|ted recebida|doc recebido|transf(erência|erencia|\.)? recebida|ted de |pix de |deposito|depósito|rendimento|rend\s*aplic|juros|provento|dividendo|jcp|restitui|cashback|credito|crédito|cred\s+em\s+conta|cred\s+tef|cred\s+pix|entrada|ted\s+remet|dep\s+dinheiro|\+)/i

function matchesSearch(description: string, search: string) {
  const d = normalize(description)
  const s = normalize(search)
  if (d.includes(s)) return true
  for (const [key, list] of Object.entries(SYNONYMS)) {
    if (list.some((w) => s.includes(w)) && (d.includes(key) || list.some((w) => d.includes(w)))) return true
  }
  return s.split(/\s+/).filter((w) => w.length > 2).some((w) => d.includes(w))
}

function money(n: number) {
  return formatBRL(n)
}

function cleanBaseDescription(desc: string): string {
  return stripInstallmentSuffix(desc.replace(/\s*\(?\b\d+\/\d+\b\)?\s*$/i, '')).trim()
}

export function createToolExecutor(getEnv: () => AgentEnv): ToolExecutor {
  return async (name: ToolName, args: Record<string, unknown>): Promise<ToolResult> => {
    const env = getEnv()
    switch (name) {
      case 'registrarTransacao':
        return registrar(env, args)
      case 'registrarTransacoesLote':
        return registrarLote(env, args)
      case 'editarTransacoes':
        return editar(env, args)
      case 'excluirTransacoes':
        return excluir(env, args)
      case 'expandirParcelamentos':
        return expandirParcelamentos(env, args)
      case 'atualizarContaPrevista':
        return atualizar(env, args)
      case 'obterRelatorioEProjecaoMensal':
        return relatorio(env)
      case 'gerenciarCartoes':
        return gerenciarCartoes(env, args)
      case 'gerenciarContasRecorrentes':
        return gerenciarContasRecorrentes(env, args)
      case 'gerenciarAmizades':
        return gerenciarAmizades(env, args)
      case 'gerenciarRegrasEMemoria':
        return gerenciarRegras(env, args)
      case 'consultarTransacoes':
        return consultar(env, args)
      case 'gerarGraficoFinanceiro':
        return gerarGrafico(env, args)
      default:
        return { response: { ok: false, erro: `Ferramenta desconhecida: ${name}` } }
    }
  }
}

async function registrar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const rawTotal = Number(args.valorTotal)
  if (isNaN(rawTotal) || rawTotal === 0) return { response: { ok: false, erro: 'valorTotal inválido' } }
  const total = round2(rawTotal)

  const description = String(args.descricao ?? 'Lançamento').trim()

  // Reconhecimento inteligente de receitas/entradas
  const isBankStatementDoc = args.origemDocumento === 'extrato_conta' || args.ehExtrato === true
  const isExplicitIncome = args.tipo === 'income' || args.categoria === 'Receita'
  const isIncome =
    isExplicitIncome ||
    INCOME_KEYWORDS_REGEX.test(description)

  const type = isIncome ? 'income' : (args.tipo === 'income' ? 'income' : 'expense')
  let splitType: SplitType = SPLIT_MAP[String(args.tipoDivisao ?? 'MEU')] ?? 'mine'
  if (type === 'income') splitType = 'mine'

  let method: PaymentMethod
  if (type === 'income') {
    method = 'pix'
  } else if (isBankStatementDoc) {
    if (/pix/i.test(description)) method = 'pix'
    else if (/boleto|energia|luz|agua|água|internet|condominio|tributo|darf/i.test(description)) method = 'bill'
    else method = 'debit'
  } else if (['pix', 'credit_card', 'debit', 'bill'].includes(String(args.metodoPagamento))) {
    method = args.metodoPagamento as PaymentMethod
  } else if (/pix/i.test(description)) {
    method = 'pix'
  } else if (/debito|débito/i.test(description)) {
    method = 'debit'
  } else if (/boleto|energia|luz|agua|água|internet|condominio|tributo|darf/i.test(description)) {
    method = 'bill'
  } else {
    method = 'credit_card'
  }

  // Receitas ou transações de extrato bancário NUNCA devem ser vinculadas a cartão de crédito
  if (type === 'income' && method === 'credit_card') {
    method = 'pix'
  }
  if (isBankStatementDoc && method === 'credit_card') {
    method = 'debit'
  }

  const guessed = guessCategoryAndSubcategory(description, total, type)
  let category: Category = (CATEGORIES as readonly string[]).includes(String(args.categoria))
    ? (args.categoria as Category)
    : guessed.category
  if (total < 0 && category === 'Outros') category = 'Estorno'
  if (splitType === 'external' && category === 'Outros') category = 'Cartão Terceiros'
  if (type === 'income' && category === 'Outros') category = 'Outras Receitas'

  let subcategory: string | null = null
  if (typeof args.subcategoria === 'string' && args.subcategoria.trim()) {
    subcategory = args.subcategoria.trim()
  } else if (category === guessed.category && guessed.subcategory) {
    subcategory = guessed.subcategory
  } else if (category === 'Receita') {
    subcategory = guessed.subcategory || 'Outras Receitas'
  }

  const date = /^\d{4}-\d{2}-\d{2}$/.test(String(args.data)) ? String(args.data) : todayKey()
  const status = (['estimated', 'pending', 'paid'].includes(String(args.status))
    ? args.status
    : method === 'credit_card'
      ? 'pending'
      : 'paid') as TransactionStatus

  const cards = env.getCards()
  const card = method === 'credit_card' ? pickCard(env.me.uid, cards, splitType, args.cartao as string | undefined) : undefined
  const payerUid = card?.ownerUid ?? env.me.uid

  // Identificação e atribuição de amigo na plataforma
  const emailAmigo = typeof args.emailAmigo === 'string' ? args.emailAmigo.trim().toLowerCase() : ''
  const nomeAmigo = typeof args.nomeAmigo === 'string' ? args.nomeAmigo.trim() : ''

  let assignedFriend: Party | null = null
  const friendsList = env.getFriends ? env.getFriends() : []

  if (emailAmigo) {
    const byEmail = friendsList.find((f) => f.email?.toLowerCase() === emailAmigo)
    if (byEmail) {
      assignedFriend = { uid: byEmail.uid, name: byEmail.name }
    } else if (env.partner && 'email' in (env.partner as any) && (env.partner as any).email?.toLowerCase() === emailAmigo) {
      assignedFriend = env.partner
    }
  }

  if (!assignedFriend && nomeAmigo) {
    const byName = friendsList.find((f) => normalize(f.name).includes(normalize(nomeAmigo)))
    if (byName) {
      assignedFriend = { uid: byName.uid, name: byName.name }
    } else if (env.partner && normalize(env.partner.name).includes(normalize(nomeAmigo))) {
      assignedFriend = env.partner
    }
  }

  if (!assignedFriend && (nomeAmigo || emailAmigo || splitType === 'friend')) {
    const resolvedName = nomeAmigo || (emailAmigo ? emailAmigo.split('@')[0] : 'Amigo')
    assignedFriend = {
      uid: `usr_amigo_${normalize(resolvedName).replace(/[^a-z0-9]/g, '_')}`,
      name: resolvedName,
    }
  }

  // Cálculo da porcentagem do amigo ou divisão personalizada
  let friendSharePct = 100
  if (args.porcentagemAmigo != null && !isNaN(Number(args.porcentagemAmigo))) {
    friendSharePct = clampPct(Number(args.porcentagemAmigo))
  } else if (args.valorAmigo != null && !isNaN(Number(args.valorAmigo)) && total > 0) {
    friendSharePct = clampPct(round2((Number(args.valorAmigo) / Math.abs(total)) * 100))
  } else if (args.porcentagemMinha != null && !isNaN(Number(args.porcentagemMinha))) {
    friendSharePct = clampPct(100 - Number(args.porcentagemMinha))
  } else if (args.valorMeu != null && !isNaN(Number(args.valorMeu)) && total > 0) {
    friendSharePct = clampPct(round2(((Math.abs(total) - Number(args.valorMeu)) / Math.abs(total)) * 100))
  }

  let customAllocations: Allocation[] | undefined = undefined

  if (Array.isArray(args.divisoesPersonalizadas) && args.divisoesPersonalizadas.length > 0) {
    splitType = 'custom'
    const divList = args.divisoesPersonalizadas as Array<{ nome?: string; email?: string; valor?: number; porcentagem?: number }>
    customAllocations = divList.map((d) => {
      let pAmount = 0
      if (d.valor != null && !isNaN(Number(d.valor))) {
        pAmount = round2(Number(d.valor))
      } else if (d.porcentagem != null && !isNaN(Number(d.porcentagem))) {
        pAmount = round2((total * Number(d.porcentagem)) / 100)
      }
      const pName = String(d.nome ?? 'Participante').trim()
      const pEmail = String(d.email ?? '').trim().toLowerCase()

      if (normalize(pName) === 'eu' || normalize(pName) === 'voce' || (pEmail && env.me.email && pEmail === env.me.email.toLowerCase())) {
        return {
          type: 'PLATFORM_USER' as const,
          uid: env.me.uid,
          name: env.me.name || 'Você',
          amount: pAmount,
          reimbursed: true,
        }
      }

      const foundFriend = friendsList.find((f) => (pEmail && f.email?.toLowerCase() === pEmail) || normalize(f.name) === normalize(pName))
      if (foundFriend) {
        if (!assignedFriend) {
          assignedFriend = { uid: foundFriend.uid, name: foundFriend.name }
          friendSharePct = total > 0 ? clampPct(round2((pAmount / total) * 100)) : 100
        }
        return {
          type: 'PLATFORM_USER' as const,
          uid: foundFriend.uid,
          name: foundFriend.name,
          amount: pAmount,
          reimbursed: false,
        }
      }

      if (env.partner && normalize(env.partner.name) === normalize(pName)) {
        return {
          type: 'PLATFORM_USER' as const,
          uid: env.partner.uid,
          name: env.partner.name,
          amount: pAmount,
          reimbursed: false,
        }
      }

      return {
        type: 'EXTERNAL' as const,
        name: pName,
        amount: pAmount,
        reimbursed: false,
      }
    })
  } else if (assignedFriend) {
    if (friendSharePct < 100) {
      splitType = 'custom'
      const friendVal = round2((total * friendSharePct) / 100)
      const myVal = round2(total - friendVal)
      customAllocations = [
        {
          type: 'PLATFORM_USER',
          uid: env.me.uid,
          name: env.me.name || 'Você',
          amount: myVal,
          reimbursed: true,
        },
        {
          type: 'PLATFORM_USER',
          uid: assignedFriend.uid,
          name: assignedFriend.name,
          amount: friendVal,
          reimbursed: false,
        },
      ]
    } else {
      splitType = 'friend'
    }
  }

  // 1. Detecta parcelaAtual e totalParcelas via parâmetros explícitos ou via regex na descrição (ex: "2/6", "10/12")
  let parcelaAtual = Number(args.parcelaAtual)
  let totalParcelas = Number(args.totalParcelas)

  if (isNaN(parcelaAtual) || isNaN(totalParcelas) || totalParcelas <= 1) {
    const match = description.match(/\b(\d{1,2})\/(\d{1,2})\b/)
    if (match) {
      const cur = parseInt(match[1], 10)
      const tot = parseInt(match[2], 10)
      if (cur >= 1 && tot >= cur && tot > 1) {
        parcelaAtual = cur
        totalParcelas = tot
      }
    }
  }

  const isInstallment = !isNaN(parcelaAtual) && !isNaN(totalParcelas) && totalParcelas > 1
  const cleanBaseDesc = cleanBaseDescription(description)

  // Cálculo da data de vencimento da fatura e mês de competência que comprometerá a renda
  const explicitMonth = typeof args.mesFatura === 'string' && /^\d{4}-\d{2}$/.test(args.mesFatura)
    ? args.mesFatura
    : null
  const explicitDueDate = typeof args.dataVencimento === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.dataVencimento)
    ? args.dataVencimento
    : null

  let finalDueDate = date
  let competenceMonth = date.slice(0, 7)

  if (method === 'credit_card') {
    const invoiceDates = calculateCardInvoiceDates(date, card, explicitDueDate, explicitMonth)
    // Para compras no cartão, a data do lançamento deve ser a data da compra (date),
    // e competenceMonth define a qual fatura ele pertence.
    finalDueDate = date
    competenceMonth = invoiceDates.competenceMonth
  } else if (explicitDueDate) {
    finalDueDate = explicitDueDate
    competenceMonth = explicitMonth || explicitDueDate.slice(0, 7)
  } else if (explicitMonth) {
    competenceMonth = explicitMonth
    finalDueDate = `${explicitMonth}-${date.slice(8, 10)}`
  }

  const before = computeSummary(env.getTransactions(), env.me.uid, competenceMonth)
  const finalDescription = isInstallment
    ? formatInstallmentDesc(cleanBaseDesc, parcelaAtual, totalParcelas)
    : (cleanBaseDesc || description)

  // Split para cálculo do impacto e divisão
  const splitForCurrent = buildSplit({
    splitType,
    total,
    me: env.me,
    partner: env.partner,
    payerUid,
    createdByUid: env.me.uid,
    externalName: args.nomeTerceiro as string | undefined,
    assignedFriend,
    friendSharePct: assignedFriend ? friendSharePct : 100,
    customAllocations,
  })

  const myImpact = splitForCurrent.userImpact[env.me.uid] ?? 0
  const receivable = payerUid === env.me.uid ? round2(total - myImpact) : 0
  const label = SPLIT_LABELS[splitForCurrent.splitType]

  const firstMonth = isInstallment ? shiftMonth(competenceMonth, 1 - parcelaAtual) : competenceMonth
  const lastMonth = isInstallment ? shiftMonth(competenceMonth, totalParcelas - parcelaAtual) : competenceMonth
  const pastCount = isInstallment ? parcelaAtual - 1 : 0
  const futureCount = isInstallment ? totalParcelas - parcelaAtual : 0

  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false

  // Se ainda não foi confirmado pelo usuário, gera pedido de confirmação sem alterar o banco de dados
  if (isPendingConfirmation) {
    const netDiff = type === 'income' ? total : -myImpact
    const estimatedAfter = round2(before.freeBalance + netDiff)
    const actionTitle = `${type === 'income' ? 'Receita' : 'Despesa'}: ${finalDescription}`

    const confirmationLines = [
      { label: 'Valor da parcela', value: money(total) },
      ...(isInstallment
        ? [{ label: 'Parcelamento', value: `Parcela ${parcelaAtual} de ${totalParcelas} (${money(total)})` }]
        : []),
      { label: 'Categoria', value: subcategory ? `${category} › ${subcategory}` : category },
      { label: 'Mês da Fatura', value: monthLabel(competenceMonth) },
      { label: method === 'credit_card' ? 'Data da Compra' : 'Vencimento', value: formatShortDate(finalDueDate) },
      {
        label: 'Divisão',
        value: assignedFriend
          ? friendSharePct < 100
            ? `🧑‍🤝‍🧑 ${assignedFriend.name} (${friendSharePct}%) / Você (${100 - friendSharePct}%)`
            : `🧑‍🤝‍🧑 100% Amigo (${assignedFriend.name})`
          : splitForCurrent.splitType === 'custom' && customAllocations
            ? `👥 Multi-pessoas (${customAllocations.length}x)`
            : splitForCurrent.splitType === 'external'
              ? `🏷️ ${splitForCurrent.allocations[0]?.name || 'Terceiro'}`
              : `${label?.emoji ?? '👤'} ${label?.label ?? 'Meu'}`,
      },
      ...(card ? [{ label: 'Cartão', value: card.cardName || 'Cartão' }] : []),
      {
        label: type === 'income' ? 'Entrada no Saldo Livre' : 'Impacto no Saldo Livre',
        value: type === 'income' ? `+${money(total)}` : myImpact > 0 ? `−${money(myImpact)}` : myImpact < 0 ? `+${money(Math.abs(myImpact))}` : 'R$ 0,00 (Neutro)',
        tone: type === 'income' || myImpact < 0 ? ('positive' as const) : myImpact > 0 ? ('negative' as const) : ('neutral' as const),
      },
      ...(receivable > 0 ? [{ label: assignedFriend ? `A receber (${assignedFriend.name})` : 'A receber', value: money(receivable), tone: 'positive' as const }] : []),
      { label: 'Saldo Livre em ' + monthLabel(competenceMonth), value: `${money(before.freeBalance)} → ${money(estimatedAfter)}` },
    ]

    return {
      response: {
        ok: true,
        aguardandoConfirmacao: true,
        mensagem: `Entendi que você deseja cadastrar uma ${type === 'income' ? 'receita' : 'despesa'}: "${finalDescription}" no valor de ${money(total)}${card ? ` no cartão ${card.cardName}` : ''} realizada em ${formatShortDate(date)} para a fatura de ${monthLabel(competenceMonth)}. Por favor, confirme se está correto para lançar no sistema.`,
        descricao: finalDescription,
        valorParcela: total,
        tipo: type,
        categoria: category,
        subcategoria: subcategory,
        ehParcelamento: isInstallment,
        parcelaAtual: isInstallment ? parcelaAtual : 1,
        totalParcelas: isInstallment ? totalParcelas : 1,
        mesCompetenciaFatura: competenceMonth,
        dataVencimento: finalDueDate,
        cartao: card ? `${card.cardName} (titular: ${card.ownerName})` : null,
        divisao: label?.label ?? splitForCurrent.splitType,
        meuImpactoNoSaldoLivre: myImpact,
        aReceberGerado: receivable,
        saldoLivreAtual: before.freeBalance,
      },
      action: {
        kind: 'confirmation',
        title: `Confirmar ${actionTitle}`,
        lines: confirmationLines,
        pendingAction: {
          id: uid('act'),
          toolName: 'registrarTransacao',
          args: { ...args, __confirmed: true },
          title: actionTitle,
          status: 'pending',
          createdAt: Date.now(),
          lines: confirmationLines,
        },
      },
    }
  }

  let id = ''

  if (isInstallment) {
    // Aloca a série completa de parcelas (anteriores como pagas, atual e futuras como a pagar/pending)
    const installmentGroupId = uid('inst')
    const txsToCreate: NewTransaction[] = []

    for (let i = 1; i <= totalParcelas; i++) {
      const delta = i - parcelaAtual
      const instMonth = shiftMonth(competenceMonth, delta)
      const instDueDate = shiftDateMonth(finalDueDate, delta)
      const instDesc = formatInstallmentDesc(cleanBaseDesc, i, totalParcelas)
      const isPast = i < parcelaAtual
      const isCurrent = i === parcelaAtual
      const instStatus: TransactionStatus = isPast ? 'paid' : (isCurrent ? status : 'pending')

      const instSplit = buildSplit({
        splitType,
        total,
        me: env.me,
        partner: env.partner,
        payerUid,
        createdByUid: env.me.uid,
        externalName: args.nomeTerceiro as string | undefined,
        assignedFriend,
        friendSharePct: assignedFriend ? friendSharePct : 100,
        customAllocations,
      })

      txsToCreate.push({
        createdByUid: env.me.uid,
        paidByUid: payerUid,
        cardId: card?.id ?? null,
        description: instDesc,
        category,
        subcategory,
        type,
        paymentMethod: method,
        status: instStatus,
        estimatedAmount: instStatus === 'estimated' ? total : null,
        actualAmount: instStatus === 'estimated' ? null : total,
        totalAmount: total,
        dueDate: instDueDate,
        competenceMonth: instMonth,
        visibleToUids: instSplit.visibleToUids,
        userImpact: instSplit.userImpact,
        allocations: isPast
          ? instSplit.allocations.map((a) => ({ ...a, reimbursed: true }))
          : instSplit.allocations,
        recurringBillId: null,
        reserveCardId: null,
        createdAt: Date.now() + i,
        assignedFriendUid: assignedFriend?.uid ?? null,
        assignedFriendName: assignedFriend?.name ?? null,
        acceptanceStatus: assignedFriend ? (isPast ? 'accepted' : 'pending_approval') : null,
        friendSharePct: assignedFriend ? friendSharePct : null,
        installmentGroupId,
        installmentNumber: i,
        installmentsTotal: totalParcelas,
        splitType,
      })
    }

    if (env.addMany) {
      await env.addMany(txsToCreate)
    } else {
      for (const t of txsToCreate) await env.addTransaction(t)
    }

    id = installmentGroupId
  } else {
    // Transação individual comum (à vista / parcela única)
    const split = buildSplit({
      splitType,
      total,
      me: env.me,
      partner: env.partner,
      payerUid,
      createdByUid: env.me.uid,
      externalName: args.nomeTerceiro as string | undefined,
      assignedFriend,
      friendSharePct: assignedFriend ? friendSharePct : 100,
      customAllocations,
    })

    const tx: NewTransaction = {
      createdByUid: env.me.uid,
      paidByUid: payerUid,
      cardId: card?.id ?? null,
      description: cleanBaseDesc || description,
      category,
      subcategory,
      type,
      paymentMethod: method,
      status,
      estimatedAmount: status === 'estimated' ? total : null,
      actualAmount: status === 'estimated' ? null : total,
      totalAmount: total,
      dueDate: finalDueDate,
      competenceMonth,
      visibleToUids: split.visibleToUids,
      userImpact: split.userImpact,
      allocations: split.allocations,
      recurringBillId: null,
      reserveCardId: null,
      createdAt: Date.now(),
      assignedFriendUid: assignedFriend?.uid ?? null,
      assignedFriendName: assignedFriend?.name ?? null,
      acceptanceStatus: assignedFriend ? 'pending_approval' : null,
      friendSharePct: assignedFriend ? friendSharePct : null,
      installmentGroupId: null,
      installmentNumber: null,
      installmentsTotal: null,
      splitType,
    }

    id = await env.addTransaction(tx)
  }

  const after = computeSummary(env.getTransactions(), env.me.uid, competenceMonth)

  return {
    response: {
      ok: true,
      id,
      descricao: finalDescription,
      valorParcela: total,
      ehParcelamento: isInstallment,
      parcelaAtual: isInstallment ? parcelaAtual : 1,
      totalParcelas: isInstallment ? totalParcelas : 1,
      parcelasAnterioresPagas: pastCount,
      parcelasFuturasComprometidas: futureCount,
      periodoParcelamento: isInstallment ? `${monthLabel(firstMonth)} até ${monthLabel(lastMonth)}` : monthLabel(competenceMonth),
      categoria: category,
      subcategoria: subcategory,
      mesCompetenciaFatura: competenceMonth,
      dataVencimento: finalDueDate,
      cartao: card ? `${card.cardName} (titular: ${card.ownerName})` : null,
      divisao: label?.label ?? splitForCurrent.splitType,
      amigoAtribuido: assignedFriend?.name ?? null,
      meuImpactoNoSaldoLivre: myImpact,
      aReceberGerado: receivable,
      saldoLivreAntes: before.freeBalance,
      saldoLivreDepois: after.freeBalance,
      limiteDiarioDepois: after.dailyLimit,
      diasRestantes: after.daysRemaining,
    },
    action: {
      kind: 'created',
      title: `${type === 'income' ? 'Receita' : 'Despesa'} registrada: ${finalDescription}`,
      lines: [
        { label: 'Valor da parcela', value: money(total) },
        ...(isInstallment
          ? [{ label: 'Parcelamento', value: `Parcela ${parcelaAtual} de ${totalParcelas} (${money(total)})` }]
          : []),
        { label: 'Categoria', value: subcategory ? `${category} › ${subcategory}` : category },
        { label: 'Mês da Fatura', value: monthLabel(competenceMonth) },
        { label: method === 'credit_card' ? 'Data da Compra' : 'Vencimento', value: formatShortDate(finalDueDate) },
        {
          label: 'Divisão',
          value: assignedFriend
            ? friendSharePct < 100
              ? `🧑‍🤝‍🧑 ${assignedFriend.name} (${friendSharePct}%) / Você (${100 - friendSharePct}%)`
              : `🧑‍🤝‍🧑 100% Amigo (${assignedFriend.name})`
            : splitForCurrent.splitType === 'custom' && customAllocations
              ? `👥 Multi-pessoas (${customAllocations.length}x)`
              : splitForCurrent.splitType === 'external'
                ? `🏷️ ${splitForCurrent.allocations[0]?.name || 'Terceiro'}`
                : `${label?.emoji ?? '👤'} ${label?.label ?? 'Meu'}`,
        },
        ...(card ? [{ label: 'Cartão', value: card.cardName || 'Cartão' }] : []),
        {
          label: type === 'income' ? 'Entrada no Saldo Livre' : 'Impacto no Saldo Livre',
          value: type === 'income' ? `+${money(total)}` : myImpact > 0 ? `−${money(myImpact)}` : myImpact < 0 ? `+${money(Math.abs(myImpact))}` : 'R$ 0,00 (Neutro)',
          tone: type === 'income' || myImpact < 0 ? ('positive' as const) : myImpact > 0 ? ('negative' as const) : ('neutral' as const),
        },
        ...(receivable > 0 ? [{ label: assignedFriend ? `A receber (${assignedFriend.name})` : 'A receber', value: money(receivable), tone: 'positive' as const }] : []),
        { label: 'Saldo Livre em ' + monthLabel(competenceMonth), value: `${money(before.freeBalance)} → ${money(after.freeBalance)}` },
      ],
    },
  }
}

async function registrarLote(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const transacoesRaw = Array.isArray(args.transacoes) ? args.transacoes : []
  if (transacoesRaw.length === 0) {
    return { response: { ok: false, erro: 'Nenhuma transação informada para registro em lote.' } }
  }

  const origemDoc = typeof args.origemDocumento === 'string' ? args.origemDocumento : undefined
  const defaultMes = typeof args.mesFaturaPadrao === 'string' ? args.mesFaturaPadrao : undefined
  const defaultVenc = typeof args.dataVencimentoPadrao === 'string' ? args.dataVencimentoPadrao : undefined
  const defaultCartao = typeof args.cartaoPadrao === 'string' ? args.cartaoPadrao : undefined
  const defaultMetodo = typeof args.metodoPagamentoPadrao === 'string'
    ? args.metodoPagamentoPadrao
    : origemDoc === 'extrato_conta'
      ? 'pix'
      : origemDoc === 'fatura_cartao'
        ? 'credit_card'
        : undefined

  const isBankStatement =
    origemDoc === 'extrato_conta' ||
    (!defaultCartao && (
      transacoesRaw.some((t) => t.tipo === 'income' || INCOME_KEYWORDS_REGEX.test(String(t.descricao || '')))
    ))

  let targetMonth = defaultMes || env.monthKey
  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false

  if (isPendingConfirmation) {
    const pendingTransactionsList: PendingBatchTransaction[] = []

    for (const item of transacoesRaw) {
      const desc = String(item.descricao ?? 'Item').trim()
      const val = Math.abs(Number(item.valorTotal ?? item.valor ?? 0))
      const isExplicitIncome = item.tipo === 'income' || item.categoria === 'Receita'
      const looksLikeIncome = isExplicitIncome || INCOME_KEYWORDS_REGEX.test(desc)
      const resolvedTipo: 'income' | 'expense' = looksLikeIncome ? 'income' : (item.tipo === 'income' ? 'income' : 'expense')

      const isRdb = /\b(aplica[çc][ãa]o|resgate)\s+rdb\b/i.test(desc) || /\brdb\b/i.test(desc) || /\bguardar\s+dinheiro\b/i.test(desc)
      const isInvoicePayment = /pagamento (recebido|de fatura|efetuado)/i.test(desc) || /saldo anterior/i.test(desc)
      const filterCat: 'valida' | 'ignorada' = item.categoria_filtro === 'ignorada' || isRdb || isInvoicePayment ? 'ignorada' : 'valida'
      const motivoIgn = item.motivoIgnorado || (isRdb ? 'Aplicação / Resgate RDB' : isInvoicePayment ? 'Pagamento de Fatura' : undefined)

      const contraparte = typeof item.contraparte === 'string' && item.contraparte.trim()
        ? item.contraparte.trim()
        : extractCounterparty(desc)

      const txDate = (typeof item.data === 'string' && item.data.trim()) || defaultVenc || `${targetMonth}-01`

      const guessed = guessCategoryAndSubcategory(desc, val, resolvedTipo)
      const resolvedCategory = typeof item.categoria === 'string' ? item.categoria : guessed.category
      const resolvedSubcat = typeof item.subcategoria === 'string' && item.subcategoria.trim()
        ? item.subcategoria.trim()
        : (resolvedCategory === guessed.category ? guessed.subcategory : (resolvedTipo === 'income' ? 'Outras Receitas' : undefined))

      pendingTransactionsList.push({
        data: txDate,
        tipo: resolvedTipo,
        valor_exato: round2(val),
        contraparte,
        categoria_filtro: filterCat,
        categoria: resolvedCategory,
        subcategoria: resolvedSubcat || undefined,
        metodoPagamento: (typeof item.metodoPagamento === 'string' ? item.metodoPagamento : defaultMetodo) as any,
        motivoIgnorado: motivoIgn,
      })
    }

    env.setPendingTransactions?.(pendingTransactionsList)

    const validTxs = pendingTransactionsList.filter((t) => t.categoria_filtro === 'valida')
    const countReceitas = validTxs.filter((t) => t.tipo === 'income').length
    const countDespesas = validTxs.filter((t) => t.tipo === 'expense').length
    const previewReceitas = round2(validTxs.filter((t) => t.tipo === 'income').reduce((s, t) => s + t.valor_exato, 0))
    const previewDespesas = round2(validTxs.filter((t) => t.tipo === 'expense').reduce((s, t) => s + t.valor_exato, 0))
    const previewImpacto = round2(previewReceitas - previewDespesas)

    const tabelaMarkdown = [
      '| Data | Tipo | Descrição / Contraparte | Valor |',
      '| :--- | :--- | :--- | :--- |',
      ...validTxs.map((t) => `| ${formatShortDate(t.data)} | ${t.tipo === 'income' ? '🟢 Entrada' : '🔴 Saída'} | ${t.contraparte} | ${money(t.valor_exato)} |`),
    ].join('\n')

    const summaryBefore = computeSummary(env.getTransactions(), env.me.uid, targetMonth)
    const isAllIncome = countReceitas > 0 && countDespesas === 0
    const isMixed = countReceitas > 0 && countDespesas > 0

    const previewTitle = isAllIncome
      ? `Lote de ${countReceitas} receita${countReceitas > 1 ? 's' : ''}`
      : isMixed
        ? `Lote de ${validTxs.length} lançamentos (${countDespesas} despesas e ${countReceitas} receitas)`
        : `Lote de ${validTxs.length} despesa${validTxs.length > 1 ? 's' : ''}`

    const confirmationLines = [
      {
        label: 'Qtd. Lançamentos Válidos',
        value: isMixed
          ? `${validTxs.length} itens (${countDespesas} despesas, ${countReceitas} receitas)`
          : `${validTxs.length} itens`,
      },
      ...(previewReceitas > 0 ? [{ label: 'Total de Receitas (Entradas)', value: money(previewReceitas), tone: 'positive' as const }] : []),
      ...(previewDespesas > 0 ? [{ label: isBankStatement ? 'Total de Despesas (Saídas)' : 'Total da Fatura (Cartão)', value: money(previewDespesas), tone: 'negative' as const }] : []),
      {
        label: 'Impacto Líquido Previsto',
        value: previewImpacto > 0 ? `+${money(previewImpacto)}` : previewImpacto < 0 ? `−${money(Math.abs(previewImpacto))}` : 'R$ 0,00 (Neutro)',
        tone: previewImpacto > 0 ? ('positive' as const) : previewImpacto < 0 ? ('negative' as const) : ('neutral' as const),
      },
      { label: 'Mês de Competência', value: monthLabel(targetMonth) },
      { label: 'Saldo Livre Atual em ' + monthLabel(targetMonth), value: money(summaryBefore.freeBalance) },
    ]

    const mensagemConfirmacao = `Entendi sua solicitação! Analisei o extrato e preparei a importação de **${validTxs.length} lançamentos** (${countReceitas} receitas somando **${money(previewReceitas)}** e ${countDespesas} despesas somando **${money(previewDespesas)}**) para **${monthLabel(targetMonth)}**:\n\n${tabelaMarkdown}\n\nPor favor, revise os itens acima no cartão e clique em **🟢 Confirmar e Executar** ou responda **'Sim'** no chat para cadastrá-los no seu Saldo Livre.`

    return {
      response: {
        ok: true,
        aguardandoConfirmacao: true,
        mensagem: mensagemConfirmacao,
        quantidadeTotal: validTxs.length,
        quantidadeReceitas: countReceitas,
        quantidadeDespesas: countDespesas,
        totalReceitas: previewReceitas,
        totalDespesas: previewDespesas,
        meuImpactoTotal: previewImpacto,
        mesCompetencia: targetMonth,
        pending_transactions: pendingTransactionsList,
        transacoesValidadas: validTxs,
        tabelaMarkdown,
      },
      action: {
        kind: 'confirmation',
        title: `Confirmar ${previewTitle}`,
        lines: confirmationLines,
        pending_transactions: pendingTransactionsList,
        pendingAction: {
          id: uid('act'),
          toolName: 'registrarTransacoesLote',
          args: { ...args, __confirmed: true, pending_transactions: pendingTransactionsList },
          title: previewTitle,
          status: 'pending',
          createdAt: Date.now(),
          lines: confirmationLines,
          pending_transactions: pendingTransactionsList,
        },
      },
    }
  }

  const results: any[] = []
  let totalReceitas = 0
  let totalDespesas = 0
  let receitasCount = 0
  let despesasCount = 0
  let meuImpactoTotal = 0
  let aReceberTotal = 0
  let totalParcelasGeradas = 0

  for (const item of transacoesRaw) {
    const desc = String(item.descricao ?? '')
    const itemValor = Math.abs(Number(item.valorTotal ?? 0))

    const isRdb = /\b(aplica[çc][ãa]o|resgate)\s+rdb\b/i.test(desc) || /\brdb\b/i.test(desc) || /\bguardar\s+dinheiro\b/i.test(desc)
    const isInvoicePayment = /pagamento (recebido|de fatura|efetuado)/i.test(desc) || /saldo anterior/i.test(desc)
    if (item.categoria_filtro === 'ignorada' || isRdb || isInvoicePayment) {
      continue
    }

    // Detecção inteligente de receitas/entradas
    const isExplicitIncome = item.tipo === 'income' || item.categoria === 'Receita'
    const looksLikeIncome =
      isExplicitIncome ||
      INCOME_KEYWORDS_REGEX.test(desc)

    const resolvedTipo = looksLikeIncome ? 'income' : (item.tipo === 'income' ? 'income' : 'expense')

    // Detecção de forma de pagamento
    let resolvedMetodo: PaymentMethod
    if (resolvedTipo === 'income') {
      resolvedMetodo = 'pix'
    } else if (isBankStatement) {
      if (/pix/i.test(desc)) resolvedMetodo = 'pix'
      else if (/debito|débito/i.test(desc)) resolvedMetodo = 'debit'
      else if (/boleto|energia|luz|agua|água|internet|condominio|tributo|darf/i.test(desc)) resolvedMetodo = 'bill'
      else resolvedMetodo = (item.metodoPagamento && ['pix', 'debit', 'bill'].includes(item.metodoPagamento)) ? item.metodoPagamento : 'debit'
    } else if (item.metodoPagamento && ['pix', 'credit_card', 'debit', 'bill'].includes(item.metodoPagamento)) {
      resolvedMetodo = item.metodoPagamento
    } else if (defaultMetodo && ['pix', 'credit_card', 'debit', 'bill'].includes(defaultMetodo)) {
      resolvedMetodo = defaultMetodo as PaymentMethod
    } else if (defaultCartao || item.cartao) {
      resolvedMetodo = 'credit_card'
    } else if (/pix/i.test(desc)) {
      resolvedMetodo = 'pix'
    } else if (/debito|débito/i.test(desc)) {
      resolvedMetodo = 'debit'
    } else if (/boleto/i.test(desc)) {
      resolvedMetodo = 'bill'
    } else {
      resolvedMetodo = 'credit_card'
    }

    // Se for extrato de conta corrente, NUNCA permite cartão de crédito!
    if (isBankStatement && resolvedMetodo === 'credit_card') {
      resolvedMetodo = 'debit'
    }

    // Status: se não for cartão de crédito, status padrão é 'paid' (já debitado/creditado na conta)
    const resolvedStatus = resolvedMetodo === 'credit_card' ? (item.status || 'pending') : 'paid'

    // Cartão só existe se o método for cartão de crédito E não for extrato bancário
    const resolvedCartao = (!isBankStatement && resolvedMetodo === 'credit_card') ? (item.cartao || defaultCartao) : undefined

    const merged = {
      ...item,
      tipo: resolvedTipo,
      metodoPagamento: resolvedMetodo,
      status: resolvedStatus,
      cartao: resolvedCartao,
      mesFatura: item.mesFatura || defaultMes,
      dataVencimento: item.dataVencimento || (resolvedMetodo === 'credit_card' ? defaultVenc : item.data),
      __confirmed: true,
    }

    const single = await registrar(env, merged)
    if (single.response.ok) {
      const resp = single.response as any
      results.push(resp)
      const val = Math.abs(resp.valorParcela || resp.valorTotal || itemValor || 0)

      if (resolvedTipo === 'income') {
        totalReceitas += val
        receitasCount++
        meuImpactoTotal += (resp.meuImpactoNoSaldoLivre || val)
      } else {
        totalDespesas += val
        despesasCount++
        meuImpactoTotal -= (resp.meuImpactoNoSaldoLivre || val)
      }

      aReceberTotal += resp.aReceberGerado || 0
      totalParcelasGeradas += (resp.totalParcelas || 1)
      if (resp.mesCompetenciaFatura) targetMonth = resp.mesCompetenciaFatura
    }
  }

  env.setPendingTransactions?.([])

  const summaryAfter = computeSummary(env.getTransactions(), env.me.uid, targetMonth)

  const isAllIncome = receitasCount > 0 && despesasCount === 0
  const isMixed = receitasCount > 0 && despesasCount > 0

  const title = isAllIncome
    ? `Lote de ${receitasCount} receita${receitasCount > 1 ? 's' : ''} registrado com sucesso!`
    : isMixed
      ? `Lote de ${results.length} lançamentos (${despesasCount} despesa${despesasCount > 1 ? 's' : ''} e ${receitasCount} receita${receitasCount > 1 ? 's' : ''}) registrado com sucesso!`
      : `Lote de ${results.length} despesa${results.length > 1 ? 's' : ''} registrado com sucesso!`

  return {
    response: {
      ok: true,
      quantidadeRegistrada: results.length,
      quantidadeReceitas: receitasCount,
      quantidadeDespesas: despesasCount,
      totalReceitas: round2(totalReceitas),
      totalDespesas: round2(totalDespesas),
      totalParcelasCriadas: totalParcelasGeradas,
      totalValor: round2(totalReceitas + totalDespesas),
      meuImpactoTotal: round2(meuImpactoTotal),
      aReceberTotal: round2(aReceberTotal),
      mesCompetencia: targetMonth,
      saldoLivreDepois: summaryAfter.freeBalance,
      itens: results,
    },
    action: {
      kind: 'created',
      title,
      lines: [
        {
          label: 'Qtd. Lançamentos',
          value: isMixed
            ? `${results.length} itens (${despesasCount} despesas, ${receitasCount} receitas)`
            : `${results.length} itens`,
        },
        ...(totalReceitas > 0 ? [{ label: 'Total de Receitas (Entradas)', value: money(totalReceitas), tone: 'positive' as const }] : []),
        ...(totalDespesas > 0 ? [{ label: isBankStatement ? 'Total de Despesas (Saídas)' : 'Total da Fatura (Cartão)', value: money(totalDespesas), tone: 'negative' as const }] : []),
        {
          label: 'Impacto Líquido no Saldo Livre',
          value: meuImpactoTotal > 0 ? `+${money(meuImpactoTotal)}` : meuImpactoTotal < 0 ? `−${money(Math.abs(meuImpactoTotal))}` : 'R$ 0,00 (Neutro)',
          tone: meuImpactoTotal > 0 ? 'positive' : meuImpactoTotal < 0 ? 'negative' : 'neutral',
        },
        ...(aReceberTotal > 0 ? [{ label: 'A Receber', value: money(aReceberTotal), tone: 'positive' as const }] : []),
        { label: 'Mês de Competência', value: monthLabel(targetMonth) },
        { label: 'Saldo Livre em ' + monthLabel(targetMonth), value: money(summaryAfter.freeBalance) },
      ],
    },
  }
}

async function expandirParcelamentos(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const all = env.getTransactions()
  const targetMonth = typeof args.mes === 'string' && /^\d{4}-\d{2}$/.test(args.mes)
    ? args.mes
    : '2026-11'

  // Procura transações do mês alvo que sejam compras parceladas
  const candidates = all.filter((t) => {
    if (t.competenceMonth !== targetMonth) return false
    if (t.installmentsTotal && t.installmentsTotal > 1) return true
    return /\b(\d{1,2})\/(\d{1,2})\b/.test(t.description)
  })

  if (candidates.length === 0) {
    return {
      response: {
        ok: false,
        erro: `Nenhuma compra parcelada encontrada no mês de ${monthLabel(targetMonth)}.`,
      },
    }
  }

  const txsToCreate: NewTransaction[] = []
  const expandedSummary: string[] = []
  let countCreated = 0

  for (const tx of candidates) {
    let curNum = tx.installmentNumber
    let totNum = tx.installmentsTotal

    if (!curNum || !totNum) {
      const match = tx.description.match(/\b(\d{1,2})\/(\d{1,2})\b/)
      if (match) {
        curNum = parseInt(match[1], 10)
        totNum = parseInt(match[2], 10)
      }
    }

    if (!curNum || !totNum || totNum <= 1) continue

    const cleanDesc = cleanBaseDescription(tx.description)
    const groupId = tx.installmentGroupId || uid('inst')

    // Garante que a transação atual tem o groupId correto
    if (!tx.installmentGroupId || !tx.installmentNumber || !tx.installmentsTotal) {
      await env.updateTransaction(tx.id, {
        installmentGroupId: groupId,
        installmentNumber: curNum,
        installmentsTotal: totNum,
        description: formatInstallmentDesc(cleanDesc, curNum, totNum),
      })
    }

    // Verifica quais números de parcelas já existem no banco para esta compra
    const existingForGroup = all.filter(
      (t) =>
        t.installmentGroupId === groupId ||
        (cleanBaseDescription(t.description) === cleanDesc && t.installmentsTotal === totNum),
    )
    const existingNumbers = new Set<number>(
      existingForGroup.map((t) => t.installmentNumber).filter((n): n is number => Boolean(n)),
    )
    existingNumbers.add(curNum)

    let createdForThis = 0

    for (let i = 1; i <= totNum; i++) {
      if (existingNumbers.has(i)) continue

      const delta = i - curNum
      const instMonth = shiftMonth(tx.competenceMonth, delta)
      const instDueDate = shiftDateMonth(tx.dueDate, delta)
      const isPast = i < curNum
      const instStatus: TransactionStatus = isPast ? 'paid' : 'pending'

      txsToCreate.push({
        createdByUid: tx.createdByUid,
        paidByUid: tx.paidByUid,
        cardId: tx.cardId,
        description: formatInstallmentDesc(cleanDesc, i, totNum),
        category: tx.category,
        type: tx.type,
        paymentMethod: tx.paymentMethod,
        status: instStatus,
        estimatedAmount: null,
        actualAmount: tx.totalAmount,
        totalAmount: tx.totalAmount,
        dueDate: instDueDate,
        competenceMonth: instMonth,
        visibleToUids: tx.visibleToUids,
        userImpact: tx.userImpact,
        allocations: isPast
          ? tx.allocations.map((a) => ({ ...a, reimbursed: true }))
          : tx.allocations.map((a) => ({ ...a, reimbursed: false })),
        recurringBillId: null,
        reserveCardId: null,
        createdAt: Date.now() + i,
        assignedFriendUid: tx.assignedFriendUid,
        assignedFriendName: tx.assignedFriendName,
        acceptanceStatus: tx.assignedFriendUid ? (isPast ? 'accepted' : 'pending_approval') : null,
        friendSharePct: tx.friendSharePct,
        installmentGroupId: groupId,
        installmentNumber: i,
        installmentsTotal: totNum,
      })
      existingNumbers.add(i)
      createdForThis++
      countCreated++
    }

    if (createdForThis > 0) {
      expandedSummary.push(`${cleanDesc} (${totNum}x de ${money(tx.totalAmount)}: ${createdForThis} parcelas geradas)`)
    }
  }

  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
  if (isPendingConfirmation) {
    const title = `Expandir ${expandedSummary.length} compra(s) parcelada(s)`
    const confirmationLines = [
      { label: 'Parcelas a criar', value: `${countCreated} novas parcelas` },
      { label: 'Compras encontradas', value: `${expandedSummary.length} série(s) completa(s)` },
      { label: 'Mês de Referência', value: monthLabel(targetMonth) },
      { label: 'Histórico', value: 'Aloca parcelas anteriores como pagas e futuras como pendentes' },
      { label: 'Saldo Livre em ' + monthLabel(targetMonth), value: money(computeSummary(env.getTransactions(), env.me.uid, targetMonth).freeBalance) },
    ]

    return {
      response: {
        ok: true,
        aguardandoConfirmacao: true,
        mensagem: `Entendi que você deseja expandir ${expandedSummary.length} série(s) de compras parceladas, gerando ${countCreated} parcelas passadas e futuras para ${monthLabel(targetMonth)}. Por favor, confirme no botão abaixo se está correto.`,
        quantidadeParcelas: countCreated,
        compras: expandedSummary,
      },
      action: {
        kind: 'confirmation',
        title: `Confirmar ${title}`,
        lines: confirmationLines,
        pendingAction: {
          id: uid('act'),
          toolName: 'expandirParcelamentos',
          args: { ...args, __confirmed: true },
          title,
          status: 'pending',
          createdAt: Date.now(),
          lines: confirmationLines,
        },
      },
    }
  }

  if (txsToCreate.length > 0) {
    if (env.addMany) {
      await env.addMany(txsToCreate)
    } else {
      for (const t of txsToCreate) await env.addTransaction(t)
    }
  }

  const after = computeSummary(env.getTransactions(), env.me.uid, targetMonth)

  return {
    response: {
      ok: true,
      quantidadeParcelasGeradas: countCreated,
      resumo: expandedSummary,
      saldoLivreNovembro: after.freeBalance,
    },
    action: {
      kind: 'updated',
      title: `${countCreated} parcelas passadas e futuras geradas com sucesso!`,
      lines: [
        { label: 'Parcelas criadas', value: `${countCreated} novas parcelas` },
        { label: 'Compras expandidas', value: `${expandedSummary.length} série(s) completa(s)` },
        { label: 'Status anteriores', value: 'Marcadas como pagas (histórico)' },
        { label: 'Status futuras', value: 'Alocadas e comprometendo meses seguintes' },
        { label: 'Saldo Livre em ' + monthLabel(targetMonth), value: money(after.freeBalance) },
      ],
    },
  }
}

async function excluir(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const all = env.getTransactions()
  const ids = Array.isArray(args.ids) ? args.ids.map(String).filter(Boolean) : []
  const termo = typeof args.termoBusca === 'string' ? args.termoBusca.trim() : ''
  const valor = typeof args.valor === 'number' && !isNaN(args.valor) ? Math.abs(args.valor) : undefined
  const mes = typeof args.mes === 'string' && /^\d{4}-\d{2}$/.test(args.mes) ? args.mes : ''
  const apenasRecentes = Boolean(args.apenasRecentes)
  const tudoDoMes = Boolean(args.tudoDoMes)
  const emLote = Boolean(args.emLote) || Boolean(args.todas)

  // SEGURANÇA: Se não for exclusão em lote explicitada pelo usuário, o limite padrão é SEMPRE 1!
  // Isso impede que um comando como "exclua a compra do mercado" apague 50 itens em lote!
  const limite = typeof args.limiteQuantidade === 'number'
    ? args.limiteQuantidade
    : (tudoDoMes || apenasRecentes || emLote) ? 50 : 1

  let targets: Transaction[] = []

  if (ids.length > 0) {
    targets = all.filter((t) => ids.includes(t.id)).slice(0, limite)
  } else if (tudoDoMes && mes) {
    targets = all.filter((t) => t.competenceMonth === mes).slice(0, limite)
  } else if (apenasRecentes) {
    const sorted = [...all].sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    targets = sorted.slice(0, limite)
  } else if (termo || valor !== undefined || mes) {
    // Busca cirúrgica com verificação de valor e eliminação de stop words
    const candidates = all.filter((t) => {
      if (mes && t.competenceMonth !== mes) return false

      if (valor !== undefined) {
        const tVal = Math.abs(effectiveAmount(t))
        if (Math.abs(tVal - valor) > 0.05) return false
      }

      if (termo) {
        const normDesc = normalize(t.description)
        const normTerm = normalize(termo)
        if (normDesc.includes(normTerm)) return true

        const stopWords = new Set(['compra', 'compras', 'despesa', 'despesas', 'gasto', 'gastos', 'lancamento', 'lancamentos', 'valor', 'reais', 'de', 'do', 'da', 'no', 'na', 'em', 'para', 'com', 'o', 'a', 'os', 'as', 'pago', 'pix'])
        const words = normTerm.split(/\s+/).filter((w) => w.length >= 3 && !stopWords.has(w))
        if (words.length > 0) {
          // TODAS as palavras significativas precisam bater (não apenas uma)
          return words.every((w) => normDesc.includes(w))
        }
        return false
      }

      return true
    })

    // Ordena pelo mais recente para que limite 1 afete a última transação correspondente
    candidates.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    targets = candidates.slice(0, limite)
  }

  if (targets.length === 0) {
    return {
      response: { ok: false, erro: 'Nenhuma transação encontrada com os filtros informados para exclusão.' },
    }
  }

  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
  const totalDeletedValue = targets.reduce((sum, t) => sum + effectiveAmount(t), 0)
  const deletedDescriptions = targets.map((t) => t.description)
  const affectedMonth = targets[0]?.competenceMonth || env.monthKey

  if (isPendingConfirmation) {
    const title = `Excluir: ${targets.length === 1 ? targets[0].description : `${targets.length} lançamentos`}`
    const confirmationLines = [
      { label: 'Ação', value: 'Exclusão de Lançamento(s)' },
      { label: 'Quantidade', value: `${targets.length} item(ns)` },
      { label: 'Itens a Excluir', value: deletedDescriptions.slice(0, 4).join(', ') + (deletedDescriptions.length > 4 ? ` (+${deletedDescriptions.length - 4})` : '') },
      { label: 'Valor a Remover', value: money(totalDeletedValue), tone: 'positive' as const },
      { label: 'Mês Afetado', value: monthLabel(affectedMonth) },
      { label: 'Saldo Livre Atual', value: money(computeSummary(all, env.me.uid, affectedMonth).freeBalance) },
    ]

    return {
      response: {
        ok: true,
        aguardandoConfirmacao: true,
        mensagem: `Entendi que você deseja excluir ${targets.length === 1 ? `o lançamento "${targets[0].description}" no valor de ${money(totalDeletedValue)}` : `${targets.length} lançamentos totalizando ${money(totalDeletedValue)}`}. Por favor, confirme no botão abaixo se está correto para excluir do sistema.`,
        quantidadeExcluir: targets.length,
        valorTotal: round2(totalDeletedValue),
        itens: deletedDescriptions,
      },
      action: {
        kind: 'confirmation',
        title: `Confirmar ${title}`,
        lines: confirmationLines,
        pendingAction: {
          id: uid('act'),
          toolName: 'excluirTransacoes',
          args: { ...args, ids: targets.map((t) => t.id), __confirmed: true },
          title,
          status: 'pending',
          createdAt: Date.now(),
          lines: confirmationLines,
        },
      },
    }
  }

  for (const t of targets) {
    await env.deleteTransaction(t.id)
  }

  const nextAll = env.getTransactions().filter((t) => !targets.some((x) => x.id === t.id))
  const after = computeSummary(nextAll, env.me.uid, affectedMonth)

  return {
    response: {
      ok: true,
      quantidadeExcluida: targets.length,
      valorTotalExcluido: round2(totalDeletedValue),
      itensExcluidos: deletedDescriptions,
      saldoLivreAtualizado: after.freeBalance,
    },
    action: {
      kind: 'updated',
      title: `${targets.length} transaç${targets.length > 1 ? 'ões excluídas' : 'ão excluída'} com sucesso`,
      lines: [
        { label: 'Quantidade', value: `${targets.length} item(ns)` },
        { label: 'Valor Removido', value: money(totalDeletedValue), tone: 'positive' },
        { label: 'Itens', value: deletedDescriptions.slice(0, 4).join(', ') + (deletedDescriptions.length > 4 ? ` (+${deletedDescriptions.length - 4})` : '') },
        { label: 'Saldo Livre em ' + monthLabel(affectedMonth), value: money(after.freeBalance) },
      ],
    },
  }
}

async function editar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const all = env.getTransactions()
  const ids = Array.isArray(args.ids) ? args.ids.map(String) : []
  const termo = typeof args.termoBusca === 'string' ? args.termoBusca.trim() : ''
  const mesOrigem = typeof args.mesOrigem === 'string' && /^\d{4}-\d{2}$/.test(args.mesOrigem) ? args.mesOrigem : ''
  const limite = typeof args.limiteQuantidade === 'number' ? args.limiteQuantidade : 50

  let targets: Transaction[] = []

  if (ids.length > 0) {
    targets = all.filter((t) => ids.includes(t.id))
  } else if (termo || mesOrigem) {
    targets = all.filter((t) => {
      if (mesOrigem && t.competenceMonth !== mesOrigem) return false
      if (termo && !matchesSearch(t.description, termo)) return false
      return true
    }).slice(0, limite)
  }

  if (targets.length === 0) {
    return {
      response: { ok: false, erro: 'Nenhuma transação encontrada para edição com os critérios informados.' },
    }
  }

  // Se alguma das transações pertencer a um grupo de parcelamento, inclui as parcelas irmãs do mesmo grupo
  const installmentGroupIds = new Set(targets.map((t) => t.installmentGroupId).filter(Boolean) as string[])
  if (installmentGroupIds.size > 0) {
    const siblings = all.filter(
      (t) => t.installmentGroupId && installmentGroupIds.has(t.installmentGroupId) && !targets.some((x) => x.id === t.id)
    )
    if (siblings.length > 0) {
      targets = [...targets, ...siblings]
    }
  }

  const novoMesFatura = typeof args.novoMesFatura === 'string' && /^\d{4}-\d{2}$/.test(args.novoMesFatura)
    ? args.novoMesFatura
    : undefined
  const novaDataVencimento = typeof args.novaDataVencimento === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.novaDataVencimento)
    ? args.novaDataVencimento
    : undefined
  const novoValor = typeof args.novoValor === 'number' ? round2(args.novoValor) : undefined
  const novaDescricao = typeof args.novaDescricao === 'string' ? args.novaDescricao.trim() : undefined
  const novaCategoria = (CATEGORIES as readonly string[]).includes(String(args.novaCategoria))
    ? (args.novaCategoria as Category)
    : undefined
  const novaSubcategoria = typeof args.novaSubcategoria === 'string' ? args.novaSubcategoria.trim() : undefined
  const novoStatus = (['estimated', 'pending', 'paid'].includes(String(args.novoStatus))
    ? args.novoStatus
    : undefined) as TransactionStatus | undefined
  const novoTipo = args.novoTipo === 'income' ? 'income' : args.novoTipo === 'expense' ? 'expense' : undefined
  const novoMetodoPagamento = ['pix', 'credit_card', 'debit', 'bill'].includes(String(args.novoMetodoPagamento))
    ? (args.novoMetodoPagamento as PaymentMethod)
    : undefined

  const novoTipoDivisao = SPLIT_MAP[String(args.novoTipoDivisao)]
  const novoAmigoEmail = typeof args.novoAmigoEmail === 'string' ? args.novoAmigoEmail.trim().toLowerCase() : ''
  const novoNomeTerceiro = typeof args.novoNomeTerceiro === 'string' ? args.novoNomeTerceiro.trim() : ''

  const isOnlyCategorySubcat = Boolean(novaCategoria || novaSubcategoria !== undefined) && !args.moverFatura
  const hasInstallments = targets.some((t) => Boolean(t.installmentGroupId))

  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
  if (isPendingConfirmation) {
    const title = `Editar: ${targets.length === 1 ? targets[0].description : `${targets.length} lançamentos`}`
    const confirmationLines = [
      { label: 'Quantidade', value: `${targets.length} item(ns)` },
      { label: 'Itens a Alterar', value: targets.map((t) => t.description).slice(0, 3).join(', ') + (targets.length > 3 ? ` (+${targets.length - 3})` : '') },
      ...(novoMesFatura && !(hasInstallments && isOnlyCategorySubcat) ? [{ label: 'Novo Mês da Fatura', value: monthLabel(novoMesFatura) }] : []),
      ...(novaDataVencimento && !(hasInstallments && isOnlyCategorySubcat) ? [{ label: 'Novo Vencimento', value: formatShortDate(novaDataVencimento) }] : []),
      ...(novaDescricao ? [{ label: 'Nova Descrição', value: novaDescricao }] : []),
      ...(novoValor !== undefined ? [{ label: 'Novo Valor', value: money(novoValor) }] : []),
      ...(novaCategoria ? [{ label: 'Nova Categoria', value: novaCategoria }] : []),
      ...(novaSubcategoria !== undefined ? [{ label: 'Nova Subcategoria', value: novaSubcategoria || 'Nenhuma' }] : []),
      ...(novoStatus ? [{ label: 'Novo Status', value: novoStatus === 'paid' ? 'Pago' : 'Pendente' }] : []),
    ]

    return {
      response: {
        ok: true,
        aguardandoConfirmacao: true,
        mensagem: `Entendi que você deseja alterar ${targets.length === 1 ? `a transação "${targets[0].description}"` : `${targets.length} transações`}. Por favor, confirme no botão abaixo se está correto para salvar as edições.`,
        quantidadeAlterar: targets.length,
      },
      action: {
        kind: 'confirmation',
        title: `Confirmar ${title}`,
        lines: confirmationLines,
        pendingAction: {
          id: uid('act'),
          toolName: 'editarTransacoes',
          args: { ...args, ids: targets.map((t) => t.id), __confirmed: true },
          title,
          status: 'pending',
          createdAt: Date.now(),
          lines: confirmationLines,
        },
      },
    }
  }

  const updatedIds: string[] = []

  for (const tx of targets) {
    const patch: Partial<Transaction> = {}

    const isInstallment = Boolean(tx.installmentGroupId)

    if (isInstallment) {
      // Encontra todas as parcelas deste grupo para verificar integridade cronológica
      const groupTxs = all
        .filter((t) => t.installmentGroupId === tx.installmentGroupId)
        .sort((a, b) => (a.installmentNumber ?? 0) - (b.installmentNumber ?? 0))

      const firstTx = groupTxs[0] || tx
      const firstNum = firstTx.installmentNumber ?? 1
      const num = tx.installmentNumber ?? 1
      const total = tx.installmentsTotal ?? groupTxs.length

      // Detecta se as parcelas estavam corrompidas (todas colapsadas no mesmo mês)
      const monthsSet = new Set(groupTxs.map((t) => t.competenceMonth || t.dueDate.slice(0, 7)))
      const isCorruptedSameMonth = groupTxs.length > 1 && monthsSet.size === 1
      const baseMonth = firstTx.competenceMonth || firstTx.dueDate.slice(0, 7) || currentMonthKey()

      // Determina mês de competência da parcela
      let targetMonth = tx.competenceMonth || tx.dueDate.slice(0, 7) || currentMonthKey()
      if (isCorruptedSameMonth) {
        // Auto-repara cronologicamente
        targetMonth = shiftMonth(baseMonth, num - firstNum)
      } else if (novoMesFatura && !isOnlyCategorySubcat) {
        // Se explicitamente pediu para mover início do parcelamento
        targetMonth = shiftMonth(novoMesFatura, num - firstNum)
      }

      // Determina data de vencimento da parcela
      const origDay = parseInt(tx.dueDate.slice(8, 10), 10) || 10
      let newDueDate = tx.dueDate
      if (novaDataVencimento && !isOnlyCategorySubcat) {
        const newDay = parseInt(novaDataVencimento.slice(8, 10), 10) || origDay
        newDueDate = dateInMonth(targetMonth, newDay)
      } else if (isCorruptedSameMonth || targetMonth !== tx.competenceMonth) {
        newDueDate = dateInMonth(targetMonth, origDay)
      }

      patch.competenceMonth = targetMonth
      patch.dueDate = newDueDate

      if (novaDescricao) {
        patch.description = formatInstallmentDesc(novaDescricao, num, total)
      }
    } else {
      if (novoMesFatura) {
        patch.competenceMonth = novoMesFatura
      }
      if (novaDataVencimento) {
        patch.dueDate = novaDataVencimento
        if (!novoMesFatura) patch.competenceMonth = novaDataVencimento.slice(0, 7)
      }
      if (novaDescricao) {
        patch.description = novaDescricao
      }
    }

    if (novaCategoria) {
      patch.category = novaCategoria
    }
    if (novaSubcategoria !== undefined) {
      patch.subcategory = novaSubcategoria || null
    }
    if (novoTipo) {
      patch.type = novoTipo
      if (novoTipo === 'income') {
        patch.category = 'Receita'
        patch.paymentMethod = 'pix'
        patch.cardId = null
        patch.status = 'paid'
      }
    }
    if (novoMetodoPagamento) {
      patch.paymentMethod = novoMetodoPagamento
      if (novoMetodoPagamento !== 'credit_card') {
        patch.cardId = null
      }
    }
    if (novoStatus) {
      patch.status = novoStatus
      if (novoStatus === 'estimated') {
        patch.estimatedAmount = novoValor ?? tx.totalAmount
        patch.actualAmount = null
      } else {
        patch.actualAmount = novoValor ?? tx.totalAmount
      }
    }

    if (novoValor !== undefined) {
      patch.totalAmount = novoValor
      if (tx.status === 'estimated') patch.estimatedAmount = novoValor
      else patch.actualAmount = novoValor
    }

    if (novoTipoDivisao || novoAmigoEmail || novoNomeTerceiro || novoValor !== undefined) {
      const curTotal = novoValor ?? tx.totalAmount
      let splitType = novoTipoDivisao ?? detectSplit(tx, env.me.uid)
      let assignedFriend = tx.assignedFriendUid && tx.assignedFriendName ? { uid: tx.assignedFriendUid, name: tx.assignedFriendName } : null

      if (novoAmigoEmail) {
        const friendsList = env.getFriends ? env.getFriends() : []
        const f = friendsList.find((fr) => fr.email?.toLowerCase() === novoAmigoEmail)
        if (f) {
          assignedFriend = { uid: f.uid, name: f.name }
          splitType = 'friend'
        } else {
          assignedFriend = { uid: `usr_amigo_${normalize(novoAmigoEmail).replace(/[^a-z0-9]/g, '_')}`, name: novoAmigoEmail.split('@')[0] }
          splitType = 'friend'
        }
      }

      const split = buildSplit({
        splitType,
        total: curTotal,
        me: env.me,
        partner: env.partner,
        payerUid: tx.paidByUid || env.me.uid,
        createdByUid: tx.createdByUid || env.me.uid,
        externalName: novoNomeTerceiro || (splitType === 'external' ? tx.allocations[0]?.name : undefined),
        assignedFriend,
        friendSharePct: 100,
      })

      patch.allocations = split.allocations
      patch.userImpact = split.userImpact
      patch.visibleToUids = split.visibleToUids
      if (assignedFriend) {
        patch.assignedFriendUid = assignedFriend.uid
        patch.assignedFriendName = assignedFriend.name
        patch.acceptanceStatus = 'pending_approval'
      }
    }

    await env.updateTransaction(tx.id, patch)
    updatedIds.push(tx.id)
  }

  const targetMonth = novoMesFatura && !hasInstallments ? novoMesFatura : (targets[0]?.competenceMonth || env.monthKey)
  const summaryAfter = computeSummary(env.getTransactions(), env.me.uid, targetMonth)

  return {
    response: {
      ok: true,
      quantidadeEditada: updatedIds.length,
      mesCompetencia: targetMonth,
      saldoLivreAtualizado: summaryAfter.freeBalance,
    },
    action: {
      kind: 'updated',
      title: `${updatedIds.length} transaç${updatedIds.length > 1 ? 'ões atualizadas' : 'ão atualizada'}`,
      lines: [
        { label: 'Editadas', value: `${updatedIds.length} item(ns)` },
        ...(novoMesFatura && !(hasInstallments && isOnlyCategorySubcat) ? [{ label: 'Novo Mês da Fatura', value: monthLabel(novoMesFatura) }] : []),
        ...(novaDataVencimento && !(hasInstallments && isOnlyCategorySubcat) ? [{ label: 'Novo Vencimento', value: novaDataVencimento }] : []),
        { label: 'Saldo Livre em ' + monthLabel(targetMonth), value: money(summaryAfter.freeBalance) },
      ],
    },
  }
}

async function atualizar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const search = String(args.buscaDescricao ?? '')
  const value = round2(Number(args.valorReal))
  const newStatus = (args.novoStatus === 'paid' ? 'paid' : 'pending') as TransactionStatus
  const newDueDate = typeof args.dataVencimento === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.dataVencimento)
    ? args.dataVencimento
    : undefined

  if (!search) return { response: { ok: false, erro: 'Informe o nome da conta a ser atualizada (ex: "luz").' } }
  if (!value || value <= 0) return { response: { ok: false, erro: 'Informe um valorReal positivo.' } }

  const all = env.getTransactions()
  const month = env.monthKey
  const candidates = all.filter(
    (t) =>
      t.competenceMonth === month &&
      t.type === 'expense' &&
      matchesSearch(t.description, search),
  )

  if (candidates.length === 0) {
    return {
      response: {
        ok: false,
        erro: `Não encontrei nenhuma conta com a descrição "${search}" no mês de ${monthLabel(month)}.`,
      },
    }
  }

  const tx = candidates[0]
  const patch = {
    ...amountPatch(tx, { actualAmount: value, estimatedAmount: newStatus === 'estimated' ? value : null }),
    status: newStatus,
    ...(newDueDate ? { dueDate: newDueDate } : {}),
  }

  const before = computeSummary(all, env.me.uid, tx.competenceMonth)
  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false

  if (isPendingConfirmation) {
    const title = `Atualizar Conta: ${tx.description}`
    const fakeUpdated: Transaction = { ...tx, ...patch }
    const fakeNextAll = all.map((t) => (t.id === tx.id ? fakeUpdated : t))
    const fakeAfter = computeSummary(fakeNextAll, env.me.uid, tx.competenceMonth)
    const diffSaldo = round2(fakeAfter.freeBalance - before.freeBalance)

    const confirmationLines = [
      { label: 'Conta', value: tx.description },
      { label: 'Valor Atual → Novo Valor', value: `${money(effectiveAmount(tx))} → ${money(value)}` },
      { label: 'Impacto Previsto no Saldo', value: diffSaldo >= 0 ? `+${money(diffSaldo)}` : `−${money(Math.abs(diffSaldo))}`, tone: diffSaldo >= 0 ? ('positive' as const) : ('negative' as const) },
      { label: 'Novo Status', value: newStatus === 'paid' ? 'Pago' : 'Pendente' },
      ...(newDueDate ? [{ label: 'Novo Vencimento', value: formatShortDate(newDueDate) }] : []),
      { label: 'Saldo Livre em ' + monthLabel(tx.competenceMonth), value: `${money(before.freeBalance)} → ${money(fakeAfter.freeBalance)}` },
    ]

    return {
      response: {
        ok: true,
        aguardandoConfirmacao: true,
        mensagem: `Entendi que você deseja atualizar a conta "${tx.description}" para o valor real de ${money(value)} (${newStatus === 'paid' ? 'Pago' : 'Pendente'}). Por favor, confirme no botão abaixo se está correto para atualizar no seu Saldo Livre.`,
        conta: tx.description,
        valorReal: value,
        status: newStatus,
      },
      action: {
        kind: 'confirmation',
        title: `Confirmar ${title}`,
        lines: confirmationLines,
        pendingAction: {
          id: uid('act'),
          toolName: 'atualizarContaPrevista',
          args: { ...args, __confirmed: true },
          title,
          status: 'pending',
          createdAt: Date.now(),
          lines: confirmationLines,
        },
      },
    }
  }

  await env.updateTransaction(tx.id, patch)

  const updated: Transaction = { ...tx, ...patch }
  const nextAll = all.map((t) => (t.id === tx.id ? updated : t))
  const after = computeSummary(nextAll, env.me.uid, tx.competenceMonth)

  const diffSaldo = round2(after.freeBalance - before.freeBalance)

  return {
    response: {
      ok: true,
      descricao: tx.description,
      valorAntigo: effectiveAmount(tx),
      valorReal: value,
      diferencaNoSaldoLivre: diffSaldo,
      novoStatus: newStatus,
      vencimento: updated.dueDate,
      saldoLivreAntes: before.freeBalance,
      saldoLivreDepois: after.freeBalance,
      limiteDiarioDepois: after.dailyLimit,
    },
    action: {
      kind: 'updated',
      title: `Conta atualizada: ${tx.description}`,
      lines: [
        { label: 'Valor', value: `${money(effectiveAmount(tx))} → ${money(value)}` },
        { label: 'Impacto no Saldo', value: diffSaldo >= 0 ? `+${money(diffSaldo)}` : `−${money(Math.abs(diffSaldo))}`, tone: diffSaldo >= 0 ? 'positive' : 'negative' },
        { label: 'Status', value: newStatus === 'paid' ? 'Pago' : 'Pendente' },
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

function parseMonthKey(raw: unknown, referenceMonth = todayKey()): string {
  if (typeof raw !== 'string' || !raw.trim()) return ''
  const trimmed = raw.trim().toLowerCase()
  if (/^\d{4}-\d{2}$/.test(trimmed)) return trimmed
  if (trimmed === 'all' || trimmed === 'todos' || trimmed === 'ano' || trimmed === 'tudo') return ''

  const yearMatch = trimmed.match(/\b(20\d{2})\b/)
  const year = yearMatch ? yearMatch[1] : referenceMonth.slice(0, 4)

  const monthMap: Record<string, string> = {
    janeiro: '01', jan: '01',
    fevereiro: '02', fev: '02',
    marco: '03', março: '03', mar: '03',
    abril: '04', abr: '04',
    maio: '05', mai: '05',
    junho: '06', jun: '06',
    julho: '07', jul: '07',
    agosto: '08', ago: '08',
    setembro: '09', set: '09',
    outubro: '10', out: '10',
    novembro: '11', nov: '11',
    dezembro: '12', dez: '12',
  }

  for (const [name, mm] of Object.entries(monthMap)) {
    if (trimmed.includes(name)) {
      return `${year}-${mm}`
    }
  }

  const numMatch = trimmed.match(/^([1-9]|1[0-2])$/)
  if (numMatch) {
    return `${year}-${numMatch[1].padStart(2, '0')}`
  }

  return ''
}

async function consultar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const all = env.getTransactions()
  const termo = typeof args.termoBusca === 'string' ? args.termoBusca.trim() : ''
  const categoria = typeof args.categoria === 'string' ? args.categoria.trim() : ''
  const subcategoria = typeof args.subcategoria === 'string' ? args.subcategoria.trim() : ''
  const mes = parseMonthKey(args.mes)
  const dataInicio = typeof args.dataInicio === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.dataInicio) ? args.dataInicio.trim() : ''
  const dataFim = typeof args.dataFim === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.dataFim) ? args.dataFim.trim() : ''
  const somenteMinhas = args.somenteMinhas === true || args.apenasMinhas === true
  const pessoa = typeof args.pessoa === 'string' ? args.pessoa.trim() : typeof args.terceiro === 'string' ? args.terceiro.trim() : ''
  const apenasTerceiros = args.apenasTerceiros === true || args.terceiros === true || /^(terceiro|terceiros|amigos|amigo)$/i.test(pessoa)
  const cartaoFiltro = typeof args.cartao === 'string' ? args.cartao.trim() : ''
  const tipoFiltro = typeof args.tipo === 'string' ? args.tipo.trim().toLowerCase() : ''
  const status = typeof args.status === 'string' ? args.status : ''
  const limite = typeof args.limite === 'number' && args.limite > 0 ? args.limite : 25

  const cards = env.getCards()
  const cardsMap = new Map(cards.map((c) => [c.id, c]))
  let matchedCard: Card | undefined
  if (cartaoFiltro) {
    const normCartao = normalize(cartaoFiltro)
    matchedCard = cards.find((c) => normalize(c.cardName).includes(normCartao) || c.id === cartaoFiltro)
  }

  const pendingTxs = env.getPendingTransactions?.()
  if (pendingTxs && pendingTxs.length > 0 && (args.pendentes === true || /pendent|extrato|lote|importad/i.test(termo))) {
    const validPending = pendingTxs.filter((t) => t.categoria_filtro === 'valida')
    const somaTotal = validPending.reduce((acc, t) => acc + t.valor_exato, 0)
    return {
      response: {
        ok: true,
        pendentes: true,
        totalEncontrados: validPending.length,
        somaTotal: round2(somaTotal),
        transacoes: validPending,
      },
      action: {
        kind: 'report',
        title: `Lançamentos Pendentes do Extrato (${validPending.length} itens)`,
        lines: [
          { label: 'Status', value: 'Aguardando Confirmação' },
          { label: 'Total Itens', value: `${validPending.length} transações` },
          ...(validPending.slice(0, 3).map((t) => ({ label: t.contraparte || t.data, value: money(t.valor_exato) }))),
        ],
      },
    }
  }

  const filtered = all.filter((t) => {
    // 1. Tipo (despesa vs receita)
    if (tipoFiltro && tipoFiltro !== 'all') {
      if (t.type !== tipoFiltro) return false
    } else if (somenteMinhas || pessoa || apenasTerceiros || cartaoFiltro || categoria) {
      // Por padrão em análises de gastos e faturas, filtra despesas a menos que o usuário peça receitas
      if (t.type !== 'expense' && categoria !== 'Receita') return false
    }

    // 2. Mês de competência
    if (mes && t.competenceMonth !== mes) return false

    // 3. Intervalo de datas (dataInicio / dataFim)
    const tDate = t.dueDate || (t as any).date || ''
    if (dataInicio && tDate && tDate < dataInicio) return false
    if (dataFim && tDate && tDate > dataFim) return false

    // 4. Cartão de crédito cadastrado
    if (cartaoFiltro) {
      if (matchedCard) {
        if (t.cardId !== matchedCard.id && !matchesSearch(t.description, matchedCard.cardName)) return false
      } else {
        if (!matchesSearch(t.description, cartaoFiltro)) return false
      }
    }

    // 5. Categoria e Subcategoria
    if (categoria && t.category !== categoria) return false
    if (subcategoria) {
      if (t.subcategory) {
        if (!normalize(t.subcategory).includes(normalize(subcategoria))) return false
      } else {
        if (!matchesSearch(t.description, subcategoria)) return false
      }
    }

    // 6. Status
    if (status && t.status !== status) return false

    // 7. Termo de busca livre
    if (termo && !matchesSearch(t.description, termo) && !(t.subcategory && matchesSearch(t.subcategory, termo))) return false

    // 8. FILTRO CRÍTICO: SOMENTE 100% MEU (SEM DIVISÕES NEM TERCEIROS)
    if (somenteMinhas) {
      if (t.category === 'Cartão Terceiros') return false
      if (t.splitType && t.splitType !== 'mine') return false
      if (t.assignedFriendUid || t.assignedFriendName) return false
      if (t.allocations && t.allocations.some((a) => a.uid !== env.me.uid && a.amount > 0)) return false
      const myImp = impactFor(t, env.me.uid, all)
      if (Math.abs(myImp - t.totalAmount) > 0.01) return false
    }

    // 9. FILTRO CRÍTICO: PESSOA ESPECÍFICA (TERCEIRO X OU Y / AMIGO)
    if (pessoa && !/^(terceiro|terceiros|amigos|amigo)$/i.test(pessoa)) {
      const normPessoa = normalize(pessoa)
      const matchesFriendName = t.assignedFriendName && normalize(t.assignedFriendName).includes(normPessoa)
      const matchesAllocations = t.allocations && t.allocations.some((a) => normalize(a.name).includes(normPessoa))
      const matchesDesc = matchesSearch(t.description, pessoa)
      const isThirdPartyCat = t.category === 'Cartão Terceiros' && matchesSearch(t.description, pessoa)

      if (!matchesFriendName && !matchesAllocations && !matchesDesc && !isThirdPartyCat) {
        return false
      }
    }

    // 10. FILTRO CRÍTICO: APENAS TERCEIROS / DIVIDIDOS (NÃO 100% MEU)
    if (apenasTerceiros) {
      const isThirdPartyCat = t.category === 'Cartão Terceiros'
      const isSharedSplit = t.splitType && t.splitType !== 'mine'
      const hasFriend = Boolean(t.assignedFriendUid || t.assignedFriendName)
      const hasOtherAllocations = t.allocations && t.allocations.some((a) => a.uid !== env.me.uid && a.amount > 0)
      const myImp = impactFor(t, env.me.uid, all)
      const hasThirdShare = t.totalAmount - myImp > 0.01

      if (!isThirdPartyCat && !isSharedSplit && !hasFriend && !hasOtherAllocations && !hasThirdShare) {
        return false
      }
    }

    return true
  })

  // Ordena por data de vencimento / createdAt mais recente primeiro
  filtered.sort((a, b) => (b.dueDate || '').localeCompare(a.dueDate || '') || (b.createdAt || 0) - (a.createdAt || 0))

  const targets = filtered.slice(0, limite)
  const somaTotal = filtered.reduce((acc, t) => acc + (t.totalAmount || 0), 0)
  const somaMeuImpacto = filtered.reduce((acc, t) => acc + impactFor(t, env.me.uid, all), 0)
  const somaTerceiros = Math.max(0, somaTotal - somaMeuImpacto)

  // Agrupamento por subcategoria para relatórios analíticos ricos
  const subcategoryTotals: Record<string, { total: number; count: number; category: string }> = {}
  for (const t of filtered) {
    const sub = t.subcategory || 'Geral'
    if (!subcategoryTotals[sub]) {
      subcategoryTotals[sub] = { total: 0, count: 0, category: t.category }
    }
    subcategoryTotals[sub].total += t.totalAmount
    subcategoryTotals[sub].count += 1
  }
  const porSubcategoria = Object.entries(subcategoryTotals)
    .map(([sub, data]) => ({
      subcategoria: sub,
      categoria: data.category,
      total: round2(data.total),
      quantidade: data.count,
    }))
    .sort((a, b) => b.total - a.total)

  // Agrupamento por pessoa / participante
  const personTotals: Record<string, { total: number; userShare: number; personShare: number; count: number }> = {}
  for (const t of filtered) {
    const myImp = impactFor(t, env.me.uid, all)
    const thirdImp = Math.max(0, t.totalAmount - myImp)
    let pName =
      t.assignedFriendName ||
      t.allocations?.find((a) => a.uid !== env.me.uid)?.name ||
      (t.category === 'Cartão Terceiros' ? 'Terceiro' : null)
    if (!pName && t.splitType === 'split_50') pName = 'Parceiro(a)'
    if (!pName && t.splitType === 'partner') pName = 'Parceiro(a)'
    if (pName) {
      if (!personTotals[pName]) {
        personTotals[pName] = { total: 0, userShare: 0, personShare: 0, count: 0 }
      }
      personTotals[pName].total += t.totalAmount
      personTotals[pName].userShare += myImp
      personTotals[pName].personShare += thirdImp
      personTotals[pName].count += 1
    }
  }
  const porPessoa = Object.entries(personTotals)
    .map(([pName, data]) => ({
      pessoa: pName,
      total: round2(data.total),
      cotaUsuario: round2(data.userShare),
      cotaPessoa: round2(data.personShare),
      quantidade: data.count,
    }))
    .sort((a, b) => b.total - a.total)

  // Agrupamento por cartão
  const cardTotals: Record<string, { total: number; count: number }> = {}
  for (const t of filtered) {
    const cName = t.cardId ? cardsMap.get(t.cardId)?.cardName : null
    if (cName) {
      cardTotals[cName] = cardTotals[cName] || { total: 0, count: 0 }
      cardTotals[cName].total += t.totalAmount
      cardTotals[cName].count += 1
    }
  }
  const porCartao = Object.entries(cardTotals)
    .map(([cName, data]) => ({
      cartao: cName,
      total: round2(data.total),
      quantidade: data.count,
    }))
    .sort((a, b) => b.total - a.total)

  const items = targets.map((t) => {
    const cardObj = t.cardId ? cardsMap.get(t.cardId) : undefined
    const myImpact = round2(impactFor(t, env.me.uid, all))
    const thirdPartyImpact = round2(Math.max(0, t.totalAmount - myImpact))

    let detalheDivisao = '100% do usuário'
    if (t.splitType === 'split_50') {
      detalheDivisao = `Dividido 50/50 (Você: ${money(myImpact)} | Parceiro: ${money(thirdPartyImpact)})`
    } else if (t.splitType === 'friend' || t.assignedFriendName) {
      const friend = t.assignedFriendName || 'Amigo'
      const pct = t.friendSharePct != null ? `${t.friendSharePct}%` : '100%'
      detalheDivisao = `Amigo: ${friend} (${pct}) • Cota dele(a): ${money(thirdPartyImpact)} • Sua cota: ${money(myImpact)}`
    } else if (t.splitType === 'partner') {
      detalheDivisao = `100% do Parceiro(a) (${money(t.totalAmount)})`
    } else if (t.splitType === 'external' || t.category === 'Cartão Terceiros') {
      const who = t.allocations?.find((a) => a.uid !== env.me.uid)?.name || 'Terceiro'
      detalheDivisao = `Terceiro (${who}) • Cota dele(a): ${money(thirdPartyImpact)}`
    } else if (t.allocations && t.allocations.length > 1) {
      detalheDivisao = t.allocations.map((a) => `${a.name}: ${money(a.amount)}`).join(' | ')
    }

    return {
      id: t.id,
      descricao: t.description,
      valorTotal: t.totalAmount,
      meuImpacto: myImpact,
      impactoTerceiros: thirdPartyImpact,
      dataVencimento: t.dueDate,
      dataCompra: (t as any).date || t.dueDate,
      mesCompetencia: t.competenceMonth,
      categoria: t.category,
      subcategoria: t.subcategory || null,
      cartao: cardObj ? cardObj.cardName : null,
      metodoPagamento: t.paymentMethod,
      status: t.status,
      tipo: t.type,
      divisao: t.splitType || 'mine',
      nomeAmigo: t.assignedFriendName || null,
      detalhesDivisao: detalheDivisao,
    }
  })

  // Título e linhas do ActionCard dinâmicos e elegantes
  let reportTitle = `Consulta: ${filtered.length} lançamento(s)`
  if (somenteMinhas) {
    reportTitle = `Despesas 100% Pessoais (${filtered.length} itens)`
  } else if (pessoa) {
    reportTitle = `Despesas vinculadas a ${pessoa} (${filtered.length} itens)`
  } else if (apenasTerceiros) {
    reportTitle = `Despesas de Terceiros e Divisões (${filtered.length} itens)`
  } else if (matchedCard) {
    reportTitle = `Fatura Cartão ${matchedCard.cardName} (${filtered.length} compras)`
  } else if (categoria) {
    reportTitle = `Despesas em ${categoria}${subcategoria ? ` › ${subcategoria}` : ''} (${filtered.length} itens)`
  } else if (termo) {
    reportTitle = `${termo} (${filtered.length} itens)`
  }

  const lines = [
    { label: 'Total Encontrado', value: `${filtered.length} lançamento(s)` },
    ...(termo ? [{ label: 'Termo de Busca', value: termo }] : []),
    { label: 'Soma Total', value: money(somaTotal) },
    { label: 'Seu Impacto (Saldo Livre)', value: money(somaMeuImpacto), tone: somaMeuImpacto > 0 ? ('negative' as const) : ('neutral' as const) },
    ...(somaTerceiros > 0 ? [{ label: 'Cota de Terceiros / A Receber', value: money(somaTerceiros), tone: 'positive' as const }] : []),
    ...(matchedCard ? [{ label: 'Cartão Referenciado', value: `${matchedCard.cardName} (Fecha dia ${matchedCard.closingDay}, Vence dia ${matchedCard.dueDay})` }] : []),
    ...(pessoa ? [{ label: 'Pessoa / Terceiro', value: pessoa }] : []),
    ...(categoria ? [{ label: 'Categoria', value: categoria }] : []),
    ...(subcategoria ? [{ label: 'Subcategoria', value: subcategoria }] : []),
    ...(mes ? [{ label: 'Mês de Competência', value: monthLabel(mes) }] : []),
    ...(dataInicio || dataFim ? [{ label: 'Período', value: `${dataInicio || 'início'} até ${dataFim || 'hoje'}` }] : []),
    ...(targets.length > 0
      ? [
          {
            label: 'Principais Itens',
            value:
              targets.slice(0, 3).map((t) => `${t.description} (${money(t.totalAmount)})`).join(', ') +
              (targets.length > 3 ? ` (+${targets.length - 3})` : ''),
          },
        ]
      : []),
  ]

  const cartaoReferenciado = matchedCard
    ? {
        id: matchedCard.id,
        nome: matchedCard.cardName,
        titular: matchedCard.ownerName,
        fechamentoDia: matchedCard.closingDay,
        vencimentoDia: matchedCard.dueDay,
      }
    : null

  return {
    response: {
      ok: true,
      totalEncontrados: filtered.length,
      somaTotal: round2(somaTotal),
      somaMeuImpacto: round2(somaMeuImpacto),
      somaTerceiros: round2(somaTerceiros),
      cartaoReferenciado,
      porSubcategoria,
      porPessoa,
      porCartao,
      exibindoQuantidade: items.length,
      transacoes: items,
    },
    action: {
      kind: 'report',
      title: reportTitle,
      lines,
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
      gastosPorSubcategoria: s.bySubcategory || [],
      totalInvestido: s.totalInvested || 0,
      totalInvestidoAcumulado: s.totalInvestedAccumulated || 0,
      investimentosPorClasse: s.byInvestmentSubcategory || [],
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
      title: `Resumo de ${monthLabel(env.monthKey)}`,
      lines: [
        { label: 'Saldo Livre Real', value: money(s.freeBalance), tone: s.freeBalance >= 0 ? 'positive' : 'negative' },
        { label: 'Limite diário', value: s.dailyLimit != null ? `${money(s.dailyLimit)} × ${s.daysRemaining}d` : 'Mês encerrado' },
        { label: 'Comprometido', value: money(s.committed) },
        { label: 'A receber', value: money(s.receivableTotal), tone: 'positive' },
      ],
    },
  }
}

async function gerenciarCartoes(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const acao = String(args.acao || 'listar')
  if (acao === 'listar') {
    const cartoes = env.getCards().map((c) => ({
      id: c.id,
      nome: c.cardName,
      titular: c.ownerName,
      diaFechamento: c.closingDay,
      diaVencimento: c.dueDay
    }))
    return {
      response: { ok: true, cartoes },
      action: {
        kind: 'report',
        title: 'Cartões Listados',
        lines: [{ label: 'Cartões Encontrados', value: cartoes.length.toString() }],
      },
    }
  }

  if (!env.addCard || !env.updateCard || !env.deleteCard) {
    return { response: { ok: false, erro: 'As funções de cartão não estão injetadas neste ambiente.' } }
  }

  if (acao === 'adicionar') {
    const nome = String(args.nome || '')
    const f = Number(args.fechamento || 1)
    const v = Number(args.vencimento || 10)
    if (!nome) return { response: { ok: false, erro: 'Nome é obrigatório para adicionar' } }

    const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
    if (isPendingConfirmation) {
      const title = `Adicionar Cartão: ${nome}`
      const confirmationLines = [
        { label: 'Nome do Cartão', value: nome },
        { label: 'Dia de Fechamento', value: `Dia ${f}` },
        { label: 'Dia de Vencimento', value: `Dia ${v}` },
      ]
      return {
        response: {
          ok: true,
          aguardandoConfirmacao: true,
          mensagem: `Entendi que você deseja adicionar o cartão "${nome}" (fecha dia ${f}, vence dia ${v}). Por favor, confirme no botão abaixo.`,
        },
        action: {
          kind: 'confirmation',
          title: `Confirmar ${title}`,
          lines: confirmationLines,
          pendingAction: {
            id: uid('act'),
            toolName: 'gerenciarCartoes',
            args: { ...args, __confirmed: true },
            title,
            status: 'pending',
            createdAt: Date.now(),
            lines: confirmationLines,
          },
        },
      }
    }

    const id = await env.addCard({ cardName: nome, closingDay: f, dueDay: v, ownerName: env.me.name, ownerUid: env.me.uid, sharedWithUids: [], sharedWithUsers: [], frequentThirdParties: [] })
    return {
      response: { ok: true, id },
      action: { kind: 'created', title: 'Cartão Adicionado', lines: [{ label: 'Cartão', value: nome }] },
    }
  }

  const id = String(args.id || '')
  if (!id) return { response: { ok: false, erro: 'ID obrigatório para edição ou exclusão.' } }

  if (acao === 'editar') {
    const patch: Partial<Card> = {}
    if (args.nome) patch.cardName = String(args.nome)
    if (args.fechamento) patch.closingDay = Number(args.fechamento)
    if (args.vencimento) patch.dueDay = Number(args.vencimento)
    await env.updateCard(id, patch)
    return {
      response: { ok: true },
      action: { kind: 'updated', title: 'Cartão Editado', lines: [{ label: 'ID', value: id }] },
    }
  }

  if (acao === 'excluir') {
    await env.deleteCard(id)
    return {
      response: { ok: true },
      action: { kind: 'updated', title: 'Cartão Excluído', lines: [{ label: 'ID', value: id }] },
    }
  }
  return { response: { ok: false, erro: 'Ação inválida' } }
}

async function gerenciarContasRecorrentes(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const acao = String(args.acao || 'listar')
  if (acao === 'listar') {
    const contas = (env.getRecurring?.() || []).map((c) => ({
      id: c.id,
      descricao: c.description,
      valorBase: c.defaultAmount,
      diaVencimento: c.dueDay,
      ativo: c.active,
      categoria: c.category
    }))
    return {
      response: { ok: true, contas },
      action: {
        kind: 'report',
        title: 'Contas Recorrentes Listadas',
        lines: [{ label: 'Contas Encontradas', value: contas.length.toString() }],
      },
    }
  }

  if (!env.addRecurring || !env.updateRecurring || !env.deleteRecurring) {
    return { response: { ok: false, erro: 'As funções de conta recorrente não estão disponíveis.' } }
  }

  if (acao === 'adicionar') {
    const desc = String(args.descricao || '')
    const valor = Number(args.valor || 0)
    if (!desc || valor <= 0) return { response: { ok: false, erro: 'Descrição e valor são obrigatórios.' } }
    const guessed = guessCategoryAndSubcategory(desc, valor)
    const cat = (args.categoria as Category) || guessed.category || 'Outros'
    const subcat = typeof args.subcategoria === 'string' && args.subcategoria.trim()
      ? args.subcategoria.trim()
      : (cat === guessed.category ? guessed.subcategory : null)
    const dueDay = Number(args.diaVencimento || 10)

    const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
    if (isPendingConfirmation) {
      const title = `Adicionar Assinatura: ${desc}`
      const confirmationLines = [
        { label: 'Descrição', value: desc },
        { label: 'Valor Mensal', value: money(valor) },
        { label: 'Dia de Vencimento', value: `Todo dia ${dueDay}` },
        { label: 'Categoria', value: subcat ? `${cat} › ${subcat}` : cat },
      ]
      return {
        response: {
          ok: true,
          aguardandoConfirmacao: true,
          mensagem: `Entendi que você deseja cadastrar a assinatura/conta recorrente "${desc}" no valor de ${money(valor)} (vencimento todo dia ${dueDay}). Por favor, confirme no botão abaixo.`,
        },
        action: {
          kind: 'confirmation',
          title: `Confirmar ${title}`,
          lines: confirmationLines,
          pendingAction: {
            id: uid('act'),
            toolName: 'gerenciarContasRecorrentes',
            args: { ...args, __confirmed: true },
            title,
            status: 'pending',
            createdAt: Date.now(),
            lines: confirmationLines,
          },
        },
      }
    }
    const id = await env.addRecurring({
      description: desc,
      defaultAmount: valor,
      dueDay,
      category: cat,
      subcategory: subcat,
      active: true,
      ownerUid: env.me.uid,
      estimationRule: 'fixed',
      type: 'expense'
    })
    return {
      response: { ok: true, id },
      action: { kind: 'created', title: 'Assinatura/Conta Recorrente Criada', lines: [{ label: 'Descrição', value: desc }] },
    }
  }

  const id = String(args.id || '')
  if (!id) return { response: { ok: false, erro: 'ID obrigatório.' } }

  if (acao === 'pausar' || acao === 'ativar') {
    const active = acao === 'ativar'
    await env.updateRecurring(id, { active })
    return {
      response: { ok: true },
      action: { kind: 'updated', title: active ? 'Conta Ativada' : 'Conta Pausada', lines: [{ label: 'ID', value: id }] },
    }
  }

  if (acao === 'excluir') {
    await env.deleteRecurring(id)
    return {
      response: { ok: true },
      action: { kind: 'updated', title: 'Conta Excluída', lines: [{ label: 'ID', value: id }] },
    }
  }
  return { response: { ok: false, erro: 'Ação inválida' } }
}

async function gerenciarAmizades(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const acao = String(args.acao || 'listar')
  if (acao === 'listar') {
    const amigos = (env.getFriends?.() || []).map(f => ({ id: f.uid, nome: f.name, email: f.email }))
    return {
      response: { ok: true, amigos },
      action: {
        kind: 'report',
        title: 'Amigos Listados',
        lines: [{ label: 'Amigos Encontrados', value: amigos.length.toString() }],
      },
    }
  }

  if (!env.addFriend || !env.respondFriend) {
    return { response: { ok: false, erro: 'Funções de amizade indisponíveis.' } }
  }

  if (acao === 'adicionar') {
    const email = String(args.email || '')
    if (!email) return { response: { ok: false, erro: 'Email obrigatório' } }
    await env.addFriend(email)
    return {
      response: { ok: true },
      action: { kind: 'created', title: 'Convite Enviado', lines: [{ label: 'Para', value: email }] },
    }
  }

  const id = String(args.id || '')
  if (!id) return { response: { ok: false, erro: 'ID do pedido obrigatório' } }

  if (acao === 'aceitar' || acao === 'recusar') {
    const aceitar = acao === 'aceitar'
    await env.respondFriend(id, aceitar)
    return {
      response: { ok: true },
      action: { kind: 'updated', title: aceitar ? 'Pedido Aceito' : 'Pedido Recusado', lines: [{ label: 'ID', value: id }] },
    }
  }

  return { response: { ok: false, erro: 'Ação inválida' } }
}

export interface UserRule {
  id: string
  rule: string
  category?: string
  createdAt: number
}

const USER_RULES_KEY_PREFIX = 'saldo_livre_user_rules_'

export function getLearnedRules(uid: string): UserRule[] {
  try {
    const key = `${USER_RULES_KEY_PREFIX}${uid || 'guest'}`
    const raw = localStorage.getItem(key)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveLearnedRules(uid: string, rules: UserRule[]) {
  try {
    const key = `${USER_RULES_KEY_PREFIX}${uid || 'guest'}`
    localStorage.setItem(key, JSON.stringify(rules))
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('saldo_livre_rules_updated', { detail: { uid } }))
    }
  } catch {
    // ignore
  }
}

export function addLearnedRule(uid: string, ruleText: string, category = 'Geral'): UserRule {
  const existing = getLearnedRules(uid)
  const trimmed = ruleText.trim()
  const newRule: UserRule = {
    id: `rule_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
    rule: trimmed,
    category,
    createdAt: Date.now(),
  }
  const updated = [newRule, ...existing.filter((r) => r.rule.toLowerCase() !== trimmed.toLowerCase())]
  saveLearnedRules(uid, updated)

  // Persiste no banco de dados Firestore se usuário autenticado
  if (uid && uid !== 'guest') {
    fsSaveAgentRule(uid, newRule).catch((err) => {
      console.warn('Erro ao persistir nova regra no Firestore:', err)
    })
  }

  return newRule
}

export function removeLearnedRule(uid: string, idOrSearch: string): boolean {
  const existing = getLearnedRules(uid)
  const target = idOrSearch.trim().toLowerCase()
  const removedRules = existing.filter((r) => r.id === idOrSearch || r.rule.toLowerCase().includes(target))
  const filtered = existing.filter((r) => r.id !== idOrSearch && !r.rule.toLowerCase().includes(target))
  if (filtered.length !== existing.length) {
    saveLearnedRules(uid, filtered)

    // Remove do banco de dados Firestore
    if (uid && uid !== 'guest') {
      for (const r of removedRules) {
        fsDeleteAgentRule(uid, r.id).catch((err) => {
          console.warn('Erro ao excluir regra do Firestore:', err)
        })
      }
    }

    return true
  }
  return false
}

async function gerenciarRegras(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const acao = String(args.acao || 'listar')
  const userUid = env.me?.uid || 'guest'

  if (acao === 'listar') {
    const regras = getLearnedRules(userUid)
    return {
      response: { ok: true, regras: regras.map((r) => ({ id: r.id, regra: r.rule, categoria: r.category })) },
      action: {
        kind: 'report',
        title: 'Regras Aprendidas do Usuário',
        lines: [
          { label: 'Total de Regras', value: regras.length.toString() },
          ...(regras.slice(0, 3).map((r, i) => ({ label: `Regra ${i + 1}`, value: r.rule }))),
        ],
      },
    }
  }

  if (acao === 'aprender') {
    const texto = String(args.regra || '').trim()
    if (!texto) {
      return { response: { ok: false, erro: 'Texto da regra não informado.' } }
    }
    const novaRegra = addLearnedRule(userUid, texto)
    return {
      response: { ok: true, id: novaRegra.id, regra: novaRegra.rule },
      action: {
        kind: 'created',
        title: '🧠 Nova Regra Memorizada!',
        lines: [
          { label: 'Regra Fixada', value: novaRegra.rule },
          { label: 'Status', value: 'Ativa em futuros cálculos e lançamentos' },
        ],
      },
    }
  }

  if (acao === 'remover') {
    const termoOuId = String(args.id || args.regra || '').trim()
    if (!termoOuId) {
      return { response: { ok: false, erro: 'Informe o ID ou trecho da regra a ser removida.' } }
    }
    const ok = removeLearnedRule(userUid, termoOuId)
    return {
      response: { ok, mensagem: ok ? 'Regra esquecida com sucesso.' : 'Regra não encontrada.' },
      action: {
        kind: 'updated',
        title: ok ? 'Regra Removida da Memória' : 'Regra Não Encontrada',
        lines: [{ label: 'Referência', value: termoOuId }],
      },
    }
  }

  return { response: { ok: false, erro: 'Ação inválida para gerenciarRegrasEMemoria.' } }
}

async function gerarGrafico(_env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const tipo = String(args.tipo || 'donut').toLowerCase()
  const titulo = String(args.titulo || 'Gráfico Financeiro')
  const descricao = args.descricao ? String(args.descricao) : undefined
  const dados = Array.isArray(args.dados) ? args.dados : []

  const chartPayload = {
    type: ['donut', 'pie', 'bar', 'line'].includes(tipo) ? tipo : 'donut',
    title: titulo,
    description: descricao,
    data: dados,
  }

  const chartBlock = `\n\`\`\`chart\n${JSON.stringify(chartPayload, null, 2)}\n\`\`\`\n`

  return {
    response: {
      ok: true,
      tipo,
      titulo,
      descricao,
      dados,
      graficoMarkdown: chartBlock,
    },
    action: {
      kind: 'report',
      title: `📊 ${titulo}`,
      lines: [
        { label: 'Tipo', value: tipo.toUpperCase() },
        { label: 'Itens mapeados', value: `${dados.length} séries/categorias` },
      ],
    },
  }
}


