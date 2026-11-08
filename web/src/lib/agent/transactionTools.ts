import type {
  Allocation,
  Category,
  NewTransaction,
  PaymentMethod,
  PendingBatchTransaction,
  SplitType,
  Transaction,
  TransactionStatus,
} from '@/types'
import { INCOME_CATEGORIES, getCategoryDetail, resolveStrictCategory } from '@/types'
import { extractCounterparty } from '../pdfParser'
import { anticipateInstallments as calcAnticipateInstallments } from '../invoices'
import {
  buildSplit,
  calculateCardInvoiceDates,
  calculateInstallmentAmount,
  canUserEditTransaction,
  clampPct,
  computeSummary,
  detectSplit,
  effectiveAmount,
  calculateFullInstallmentRefund,
  fractionCustomAllocations,
  guessCategoryAndSubcategory,
  pickCard,
  SPLIT_LABELS,
  type Party,
} from '../finance'
import {
  currentMonthKey,
  dateInMonth,
  formatInstallmentDesc,
  stripInstallmentSuffix,
  formatShortDate,
  monthLabel,
  normalize,
  round2,
  shiftDateMonth,
  shiftMonth,
  todayKey,
  uid,
} from '../utils'
import {
  cleanBaseDescription,
  INCOME_KEYWORDS_REGEX,
  matchesSearch,
  money,
  SPLIT_MAP,
  sanitizeAmount,
  sanitizeText,
} from './common'

function getAgentSummary(env: AgentEnv, txs: Transaction[], monthKey: string) {
  const cards = env.getCards?.() || []
  const closures = env.getClosures?.() || []
  return computeSummary(txs, env.me.uid, monthKey, cards, closures)
}
import type { AgentEnv, ToolResult } from './types'

export async function registrar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const rawValorParcela = sanitizeAmount(args.valorParcela ?? args.installmentAmount)
  const rawValorTotal = sanitizeAmount(args.valorTotal ?? args.amount ?? args.valor ?? args.totalAmount)
  const inputAmount = rawValorTotal ?? rawValorParcela
  if (inputAmount === null || inputAmount === 0) {
    return {
      response: {
        ok: false,
        precisaClarificacao: true,
        campoFaltante: 'valorTotal',
        erro: 'Qual foi o valor dessa transação? (Deve ser um valor numérico válido diferente de zero)',
      },
    }
  }
  let total = inputAmount

  const description = sanitizeText(args.descricao ?? args.description ?? '', 300)
  if (!description || description.toLowerCase() === 'lançamento' || description.toLowerCase() === 'despesa' || description.toLowerCase() === 'gasto') {
    return {
      response: {
        ok: false,
        precisaClarificacao: true,
        campoFaltante: 'descricao',
        erro: 'Qual foi o estabelecimento ou a descrição dessa transação?',
      },
    }
  }

  // Reconhecimento inteligente de receitas/entradas
  const isBankStatementDoc = args.origemDocumento === 'extrato_conta' || args.ehExtrato === true
  const tipoStr = String(args.tipo ?? '').toLowerCase()
  const isExplicitIncome =
    tipoStr === 'income' ||
    tipoStr === 'receita' ||
    tipoStr === 'entrada' ||
    args.categoria === 'Receita' ||
    (INCOME_CATEGORIES as readonly string[]).includes(String(args.categoria))
  const isIncome =
    isExplicitIncome ||
    INCOME_KEYWORDS_REGEX.test(description)

  const type = isIncome ? 'income' : 'expense'
  let splitType: SplitType = SPLIT_MAP[String(args.tipoDivisao ?? args.splitType ?? 'MEU')] ?? 'mine'
  if (type === 'income') splitType = 'mine'

  const explicitMethod = args.metodoPagamento ?? args.paymentMethod
  let method: PaymentMethod | undefined
  if (type === 'income') {
    method = 'pix'
  } else if (isBankStatementDoc) {
    if (/pix/i.test(description)) method = 'pix'
    else if (/boleto|energia|luz|agua|água|internet|condominio|tributo|darf/i.test(description)) method = 'bill'
    else method = 'debit'
  } else if (['pix', 'credit_card', 'debit', 'bill'].includes(String(explicitMethod))) {
    method = explicitMethod as PaymentMethod
  } else if (/pix/i.test(description)) {
    method = 'pix'
  } else if (/debito|débito/i.test(description)) {
    method = 'debit'
  } else if (/boleto|energia|luz|agua|água|internet|condominio|tributo|darf/i.test(description)) {
    method = 'bill'
  } else if (/cart[aã]o|cr[eé]dito|fatura/i.test(description) || args.cartao || args.cardName) {
    method = 'credit_card'
  }

  if (!method) {
    return {
      response: {
        ok: false,
        precisaClarificacao: true,
        campoFaltante: 'metodoPagamento',
        erro: `Qual foi a forma de pagamento do lançamento de R$ ${total.toFixed(2)} (${description})? (Pix, Débito, Cartão de Crédito ou Boleto?)`,
      },
    }
  }

  // Receitas ou transações de extrato bancário NUNCA devem ser vinculadas a cartão de crédito
  if (type === 'income' && method === 'credit_card') {
    method = 'pix'
  }
  if (isBankStatementDoc && method === 'credit_card') {
    method = 'debit'
  }

  const strict = resolveStrictCategory(
    typeof args.categoria === 'string' ? args.categoria : undefined,
    typeof args.subcategoria === 'string' ? args.subcategoria : null,
    type,
    description,
    (d, t) => guessCategoryAndSubcategory(d, total, t)
  )
  let category: Category = strict.category
  let subcategory: string | null = strict.subcategory

  const isRefund =
    type === 'expense' &&
    (total < 0 ||
      category === 'Estorno' ||
      description.toLowerCase().startsWith('estorno') ||
      /crédito na fatura|credito na fatura|credito fatura/i.test(description))
  if (isRefund && type === 'expense' && total > 0) {
    total = -Math.abs(total)
  }
  if (isRefund) {
    category = 'Outros'
    subcategory = subcategory || 'Crédito na Fatura'
  }
  if (type === 'income' && (category === 'Outros' || category === 'Receita')) {
    category = 'Outras Receitas'
  }

  if (!subcategory && (category === 'Outras Receitas' || (INCOME_CATEGORIES as readonly string[]).includes(category))) {
    subcategory = getCategoryDetail(category)?.subcategories?.[0] || 'Outras Receitas'
  }

  const date = /^\d{4}-\d{2}-\d{2}$/.test(String(args.data)) ? String(args.data) : todayKey()
  const status = (['estimated', 'pending', 'paid'].includes(String(args.status))
    ? args.status
    : method === 'credit_card'
      ? 'pending'
      : 'paid') as TransactionStatus

  const cards = env.getCards()
  const cardHint = (args.cartao || args.cardName) as string | undefined
  if (method === 'credit_card' && !cardHint && cards.length > 1) {
    return {
      response: {
        ok: false,
        precisaClarificacao: true,
        campoFaltante: 'cartao',
        erro: `Você possui múltiplos cartões cadastrados (${cards.map((c) => c.cardName).join(', ')}). Em qual deles foi realizada a compra de R$ ${total.toFixed(2)} (${description})?`,
      },
    }
  }
  const card = method === 'credit_card' ? pickCard(env.me.uid, cards, splitType, cardHint) : undefined
  const payerUid = card?.ownerUid ?? env.me.uid

  // Identificação e atribuição de amigo na plataforma
  const emailAmigo = typeof args.emailAmigo === 'string'
    ? args.emailAmigo.trim().toLowerCase()
    : typeof args.friendEmail === 'string'
      ? args.friendEmail.trim().toLowerCase()
      : ''
  const nomeAmigo = typeof args.nomeAmigo === 'string'
    ? args.nomeAmigo.trim()
    : typeof args.friendName === 'string'
      ? args.friendName.trim()
      : ''

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

  if (splitType === 'friend' && !assignedFriend && !nomeAmigo && !emailAmigo) {
    return {
      response: {
        ok: false,
        precisaClarificacao: true,
        campoFaltante: 'divisao',
        erro: `Com quem você gostaria de dividir essa despesa de R$ ${total.toFixed(2)} (${description}) e qual a porcentagem ou valor de cada um?`,
      },
    }
  }

  if (splitType === 'external' && !args.nomeTerceiro && !args.thirdPartyName) {
    return {
      response: {
        ok: false,
        precisaClarificacao: true,
        campoFaltante: 'terceiro',
        erro: `Qual o nome da pessoa com quem você dividiu essa despesa de R$ ${total.toFixed(2)} (${description})?`,
      },
    }
  }

  if (!assignedFriend && (nomeAmigo || emailAmigo)) {
    const resolvedName = nomeAmigo || (emailAmigo ? emailAmigo.split('@')[0] : 'Amigo')
    assignedFriend = {
      uid: `usr_amigo_${normalize(resolvedName).replace(/[^a-z0-9]/g, '_')}`,
      name: resolvedName,
    }
  }

  // Cálculo da porcentagem do amigo ou divisão personalizada
  let friendSharePct = 100
  const rawPctAmigo = args.porcentagemAmigo ?? args.percentualAmigo ?? args.friendSharePct ?? args.friendPct
  if (rawPctAmigo != null && !isNaN(Number(rawPctAmigo))) {
    friendSharePct = clampPct(Number(rawPctAmigo))
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
          type: assignedFriend.uid.startsWith('usr_amigo_') ? 'EXTERNAL' : 'PLATFORM_USER',
          uid: assignedFriend.uid.startsWith('usr_amigo_') ? undefined : assignedFriend.uid,
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

  // Determina se a entrada foi por parcela (valorParcela) ou valor total (valorTotal)
  const isPerInstallment = Boolean(rawValorParcela && rawValorParcela > 0 && (!rawValorTotal || rawValorTotal <= 0))
  const purchaseTotal = isInstallment && isPerInstallment && rawValorParcela
    ? round2(rawValorParcela * totalParcelas)
    : inputAmount!
  const currentInstallmentAmt = isInstallment
    ? (isPerInstallment ? rawValorParcela! : calculateInstallmentAmount(purchaseTotal, parcelaAtual, totalParcelas, false))
    : purchaseTotal

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

  const before = getAgentSummary(env, env.getTransactions(), competenceMonth)
  const finalDescription = isInstallment
    ? formatInstallmentDesc(cleanBaseDesc, parcelaAtual, totalParcelas)
    : (cleanBaseDesc || description)

  const currentCustomAllocs = fractionCustomAllocations(
    customAllocations,
    parcelaAtual,
    totalParcelas,
    isPerInstallment
  )

  // Split para cálculo do impacto e divisão
  const splitForCurrent = buildSplit({
    splitType,
    total: currentInstallmentAmt,
    me: env.me,
    partner: env.partner,
    payerUid,
    createdByUid: env.me.uid,
    externalName: args.nomeTerceiro as string | undefined,
    assignedFriend,
    friendSharePct: assignedFriend ? friendSharePct : 100,
    customAllocations: currentCustomAllocs,
  })

  const myImpact = splitForCurrent.userImpact[env.me.uid] ?? 0
  const receivable = payerUid === env.me.uid ? round2(currentInstallmentAmt - myImpact) : 0
  const label = SPLIT_LABELS[splitForCurrent.splitType]

  const firstMonth = isInstallment ? shiftMonth(competenceMonth, 1 - parcelaAtual) : competenceMonth
  const lastMonth = isInstallment ? shiftMonth(competenceMonth, totalParcelas - parcelaAtual) : competenceMonth
  const pastCount = isInstallment ? parcelaAtual - 1 : 0
  const futureCount = isInstallment ? totalParcelas - parcelaAtual : 0

  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false

  // Se ainda não foi confirmado pelo usuário, gera pedido de confirmação sem alterar o banco de dados
  if (isPendingConfirmation) {
    const netDiff = type === 'income' ? currentInstallmentAmt : -myImpact
    const estimatedAfter = round2(before.freeBalance + netDiff)
    const actionKind = type === 'income' ? 'Receita' : isRefund ? 'Estorno / Reembolso' : 'Despesa'
    const actionTitle = `${actionKind}: ${finalDescription}`

    const confirmationLines = [
      { label: type === 'income' ? 'Valor da receita' : isRefund ? 'Valor do estorno' : isInstallment ? 'Valor da parcela' : 'Valor da despesa', value: money(Math.abs(currentInstallmentAmt)) },
      ...(isInstallment
        ? [{ label: 'Parcelamento', value: `Parcela ${parcelaAtual} de ${totalParcelas} (${money(Math.abs(currentInstallmentAmt))} cada • Total: ${money(Math.abs(purchaseTotal))})` }]
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
        value: type === 'income' ? `+${money(Math.abs(currentInstallmentAmt))}` : myImpact > 0 ? `−${money(myImpact)}` : myImpact < 0 ? `+${money(Math.abs(myImpact))}` : 'R$ 0,00 (Neutro)',
        tone: type === 'income' || myImpact < 0 ? ('positive' as const) : myImpact > 0 ? ('negative' as const) : ('neutral' as const),
      },
      ...(receivable > 0 ? [{ label: assignedFriend ? `A receber (${assignedFriend.name})` : 'A receber', value: money(receivable), tone: 'positive' as const }] : []),
      { label: 'Saldo Livre em ' + monthLabel(competenceMonth), value: `${money(before.freeBalance)} → ${money(estimatedAfter)}` },
    ]

    return {
      response: {
        ok: true,
        aguardandoConfirmacao: true,
        mensagem: `Entendi que você deseja cadastrar ${type === 'income' ? 'uma receita' : isRefund ? 'um estorno/crédito' : 'uma despesa'}: "${finalDescription}" no valor de ${money(Math.abs(currentInstallmentAmt))}${isInstallment ? ` (Parcela ${parcelaAtual} de ${totalParcelas} • Total: ${money(Math.abs(purchaseTotal))})` : ''}${card ? ` no cartão ${card.cardName}` : ''} realizada em ${formatShortDate(date)} para a fatura de ${monthLabel(competenceMonth)}. Por favor, confirme se está correto para lançar no sistema.`,
        descricao: finalDescription,
        valorParcela: currentInstallmentAmt,
        valorTotal: purchaseTotal,
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
      const curInstAmt = isPerInstallment
        ? rawValorParcela!
        : calculateInstallmentAmount(purchaseTotal, i, totalParcelas, false)
      const delta = i - parcelaAtual
      const instMonth = shiftMonth(competenceMonth, delta)
      const instDueDate = shiftDateMonth(finalDueDate, delta)
      const instDesc = formatInstallmentDesc(cleanBaseDesc, i, totalParcelas)
      const isPast = i < parcelaAtual
      const isCurrent = i === parcelaAtual
      const instStatus: TransactionStatus = isPast ? 'paid' : (isCurrent ? status : 'pending')

      const curCustomAllocs = fractionCustomAllocations(
        customAllocations,
        i,
        totalParcelas,
        isPerInstallment
      )

      const instSplit = buildSplit({
        splitType,
        total: curInstAmt,
        me: env.me,
        partner: env.partner,
        payerUid,
        createdByUid: env.me.uid,
        externalName: args.nomeTerceiro as string | undefined,
        assignedFriend,
        friendSharePct: assignedFriend ? friendSharePct : 100,
        customAllocations: curCustomAllocs,
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
        estimatedAmount: instStatus === 'estimated' ? curInstAmt : null,
        actualAmount: instStatus === 'estimated' ? null : curInstAmt,
        totalAmount: curInstAmt,
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
      total: currentInstallmentAmt,
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
      estimatedAmount: status === 'estimated' ? currentInstallmentAmt : null,
      actualAmount: status === 'estimated' ? null : currentInstallmentAmt,
      totalAmount: currentInstallmentAmt,
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

  const after = getAgentSummary(env, env.getTransactions(), competenceMonth)

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
      title: `${type === 'income' ? 'Receita' : isRefund ? 'Estorno' : 'Despesa'} registrada: ${finalDescription}`,
      lines: [
        { label: type === 'income' ? 'Valor da receita' : isRefund ? 'Valor do estorno' : 'Valor da parcela', value: money(Math.abs(total)) },
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

export async function registrarLote(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const transacoesRaw = Array.isArray(args.transacoes) ? args.transacoes : []
  if (transacoesRaw.length === 0) {
    return { response: { ok: false, erro: 'Nenhuma transação informada para registro em lote.' } }
  }

  const origemDoc = typeof args.origemDocumento === 'string' ? args.origemDocumento : undefined
  const defaultMes = typeof args.mesFaturaPadrao === 'string' ? args.mesFaturaPadrao : undefined
  const defaultVenc = typeof args.dataVencimentoPadrao === 'string' ? args.dataVencimentoPadrao : undefined
  const defaultCartao = typeof args.cartaoPadrao === 'string'
    ? args.cartaoPadrao
    : typeof args.cartao === 'string'
      ? args.cartao
      : typeof args.cardName === 'string'
        ? args.cardName
        : undefined
  const defaultMetodo = typeof args.metodoPagamentoPadrao === 'string'
    ? args.metodoPagamentoPadrao
    : origemDoc === 'extrato_conta'
      ? 'pix'
      : origemDoc === 'fatura_cartao'
        ? 'credit_card'
        : undefined

  const availableCards = env.getCards()
  const isCardInvoice = origemDoc === 'fatura_cartao' || defaultMetodo === 'credit_card'
  const hasBatchCardItems = isCardInvoice || transacoesRaw.some((t) => t.metodoPagamento === 'credit_card' || t.paymentMethod === 'credit_card')
  const hasBatchExplicitCard = defaultCartao || transacoesRaw.some((t) => t.cartao || t.cardName || t.cardId)
  if (hasBatchCardItems && !hasBatchExplicitCard && availableCards.length > 1) {
    return {
      response: {
        ok: false,
        precisaClarificacao: true,
        campoFaltante: 'cartao',
        erro: `Identifiquei compras de cartão de crédito no lote, mas você possui múltiplos cartões cadastrados (${availableCards.map((c) => c.cardName).join(', ')}). Em qual deles essas compras foram realizadas?`,
      },
    }
  }

  const resolvedDefaultCartao = defaultCartao || (
    isCardInvoice
      ? (availableCards.find((c) => c.ownerUid === env.me.uid)?.cardName || availableCards[0]?.cardName)
      : undefined
  )

  const isBankStatement =
    origemDoc === 'extrato_conta' ||
    (!isCardInvoice && !resolvedDefaultCartao && (
      transacoesRaw.some((t) => t.tipo === 'income' || INCOME_KEYWORDS_REGEX.test(String(t.descricao || '')))
    ))

  let targetMonth = defaultMes || env.monthKey
  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false

  if (isPendingConfirmation) {
    const pendingTransactionsList: PendingBatchTransaction[] = []

    for (const item of transacoesRaw) {
      const desc = String(item.descricao ?? item.description ?? item.contraparte ?? 'Item').trim()
      const val = Math.abs(Number(item.valorTotal ?? item.amount ?? item.valor_exato ?? item.valor ?? 0))
      const rawTipoStr = String(item.tipo ?? item.type ?? '').toLowerCase()
      const isExplicitIncome =
        rawTipoStr === 'income' ||
        rawTipoStr === 'receita' ||
        rawTipoStr === 'entrada' ||
        item.categoria === 'Receita' ||
        item.category === 'Receita' ||
        (INCOME_CATEGORIES as readonly string[]).includes(String(item.categoria ?? item.category))
      const looksLikeIncome = isExplicitIncome || INCOME_KEYWORDS_REGEX.test(desc)
      const resolvedTipo: 'income' | 'expense' = looksLikeIncome ? 'income' : 'expense'

      const isRdb = /\b(aplica[çc][ãa]o|resgate)\s+rdb\b/i.test(desc) || /\brdb\b/i.test(desc) || /\bguardar\s+dinheiro\b/i.test(desc)
      const isInvoicePayment = /pagamento (recebido|de fatura|efetuado)/i.test(desc) || /saldo anterior/i.test(desc)
      const filterCat: 'valida' | 'ignorada' = item.categoria_filtro === 'ignorada' || isRdb || isInvoicePayment ? 'ignorada' : 'valida'
      const motivoIgn = item.motivoIgnorado || (isRdb ? 'Aplicação / Resgate RDB' : isInvoicePayment ? 'Pagamento de Fatura' : undefined)

      const contraparte = typeof item.contraparte === 'string' && item.contraparte.trim()
        ? item.contraparte.trim()
        : extractCounterparty(desc)

      const txDate = (typeof item.data === 'string' && item.data.trim()) ||
        (typeof item.date === 'string' && item.date.trim()) ||
        defaultVenc || `${targetMonth}-01`

      const rawCat = typeof item.categoria === 'string'
        ? item.categoria
        : typeof item.category === 'string'
          ? item.category
          : undefined
      const rawSub = typeof item.subcategoria === 'string'
        ? item.subcategoria.trim()
        : typeof item.subcategory === 'string'
          ? item.subcategory.trim()
          : null
      const strict = resolveStrictCategory(
        rawCat,
        rawSub,
        resolvedTipo,
        desc,
        (d, t) => guessCategoryAndSubcategory(d, val, t)
      )
      const resolvedCategory = strict.category
      const resolvedSubcat = strict.subcategory || (resolvedTipo === 'income' ? 'Outras Receitas' : undefined)

      // Detecção inteligente de rateio / split no item
      let itemFriendName = item.nomeAmigo ?? item.amigo ?? item.friendName
      let itemFriendEmail = item.emailAmigo ?? item.friendEmail
      let itemFriendPct = item.porcentagemAmigo ?? item.percentualAmigo ?? item.friendSharePct ?? item.friendPct
      let itemSplitType = item.tipoDivisao ?? item.splitType
      let cleanDesc = desc

      if (!itemFriendName && !itemSplitType) {
        const pctMatch = desc.match(/[;,]?\s*(\d+(?:[.,]\d+)?)\s*%\s*(?:para|com|de)\s+([A-Za-zÀ-ÖØ-öø-ÿ\s]+?)(?:[;,.]|$)/i)
        if (pctMatch) {
          itemFriendPct = Number(pctMatch[1].replace(',', '.'))
          itemFriendName = pctMatch[2].trim()
          cleanDesc = desc.replace(pctMatch[0], '').replace(/[;,]?\s*R\$\s*\d+(?:[.,]\d+)?/gi, '').replace(/[;,]\s*$/, '').trim()
        } else {
          const divMatch = desc.match(/[;,]?\s*(?:dividid[oa]|divis[ãa]o|dividi|meio\s+a\s+meio|50\/50|50-50)\s*(?:com|para)\s+([A-Za-zÀ-ÖØ-öø-ÿ\s]+?)(?:[;,.]|$)/i)
          if (divMatch) {
            itemFriendPct = 50
            itemFriendName = divMatch[1].trim()
            cleanDesc = desc.replace(divMatch[0], '').replace(/[;,]\s*$/, '').trim()
          }
        }
      }
      cleanDesc = cleanDesc.replace(/[;,]?\s*R\$\s*\d+(?:[.,]\d+)?/gi, '').replace(/[;,]\s*$/, '').trim()

      const rawPayMethod = item.metodoPagamento ?? item.paymentMethod
      let resolvedItemMetodo: PaymentMethod
      if (resolvedTipo === 'income') {
        resolvedItemMetodo = 'pix'
      } else if (isBankStatement) {
        if (/pix/i.test(desc)) resolvedItemMetodo = 'pix'
        else if (/debito|débito/i.test(desc)) resolvedItemMetodo = 'debit'
        else if (/boleto|energia|luz|agua|água|internet|condominio|tributo|darf/i.test(desc)) resolvedItemMetodo = 'bill'
        else resolvedItemMetodo = (rawPayMethod && ['pix', 'debit', 'bill'].includes(rawPayMethod)) ? rawPayMethod : 'debit'
      } else if (rawPayMethod && ['pix', 'credit_card', 'debit', 'bill'].includes(rawPayMethod)) {
        resolvedItemMetodo = rawPayMethod
      } else if (defaultMetodo && ['pix', 'credit_card', 'debit', 'bill'].includes(defaultMetodo)) {
        resolvedItemMetodo = defaultMetodo as PaymentMethod
      } else if (resolvedDefaultCartao || item.cartao || item.cardName || item.cardId) {
        resolvedItemMetodo = 'credit_card'
      } else if (/pix/i.test(desc)) {
        resolvedItemMetodo = 'pix'
      } else {
        resolvedItemMetodo = 'credit_card'
      }

      const itemCardParam = item.cartao || item.cardName || item.cardId
      const fallbackCartao = availableCards.find((c) => c.ownerUid === env.me.uid)?.cardName || availableCards[0]?.cardName
      const resolvedItemCartao = (!isBankStatement && resolvedItemMetodo === 'credit_card')
        ? (itemCardParam || resolvedDefaultCartao || fallbackCartao)
        : undefined

      pendingTransactionsList.push({
        data: txDate,
        tipo: resolvedTipo,
        valor_exato: round2(val),
        contraparte: contraparte || cleanDesc,
        descricao: cleanDesc,
        categoria_filtro: filterCat,
        categoria: resolvedCategory,
        subcategoria: resolvedSubcat || undefined,
        metodoPagamento: resolvedItemMetodo,
        cartao: resolvedItemCartao,
        nomeAmigo: itemFriendName,
        emailAmigo: itemFriendEmail,
        porcentagemAmigo: itemFriendPct != null ? Number(itemFriendPct) : undefined,
        tipoDivisao: itemSplitType,
        motivoIgnorado: motivoIgn,
      })
    }

    env.setPendingTransactions?.(pendingTransactionsList)

    const validTxs = pendingTransactionsList.filter((t) => t.categoria_filtro === 'valida')
    const countReceitas = validTxs.filter((t) => t.tipo === 'income').length
    const countDespesas = validTxs.filter((t) => t.tipo === 'expense').length
    const previewReceitas = round2(validTxs.filter((t) => t.tipo === 'income').reduce((s, t) => s + t.valor_exato, 0))
    const previewDespesas = round2(
      validTxs.filter((t) => t.tipo === 'expense').reduce((s, t) => {
        const friendShare = (t.porcentagemAmigo != null && t.porcentagemAmigo > 0 && t.porcentagemAmigo <= 100)
          ? (t.valor_exato * (t.porcentagemAmigo / 100))
          : 0
        return s + (t.valor_exato - friendShare)
      }, 0)
    )
    const previewImpacto = round2(previewReceitas - previewDespesas)

    const tabelaMarkdown = [
      '| Data | Tipo | Descrição / Contraparte | Valor |',
      '| :--- | :--- | :--- | :--- |',
      ...validTxs.map((t) => `| ${formatShortDate(t.data)} | ${t.tipo === 'income' ? '🟢 Entrada' : '🔴 Saída'} | ${t.contraparte} | ${money(t.valor_exato)} |`),
    ].join('\n')

    const summaryBefore = getAgentSummary(env, env.getTransactions(), targetMonth)
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
    const desc = String(item.descricao ?? item.description ?? item.contraparte ?? '')
    const itemValor = Math.abs(Number(item.valorTotal ?? item.amount ?? item.valor_exato ?? item.valor ?? 0))

    const isRdb = /\b(aplica[çc][ãa]o|resgate)\s+rdb\b/i.test(desc) || /\brdb\b/i.test(desc) || /\bguardar\s+dinheiro\b/i.test(desc)
    const isInvoicePayment = /pagamento (recebido|de fatura|efetuado)/i.test(desc) || /saldo anterior/i.test(desc)
    if (item.categoria_filtro === 'ignorada' || isRdb || isInvoicePayment) {
      continue
    }

    // Detecção inteligente de receitas/entradas
    const rawTipoStr = String(item.tipo ?? item.type ?? '').toLowerCase()
    const isExplicitIncome =
      rawTipoStr === 'income' ||
      rawTipoStr === 'receita' ||
      rawTipoStr === 'entrada' ||
      item.categoria === 'Receita' ||
      item.category === 'Receita' ||
      (INCOME_CATEGORIES as readonly string[]).includes(String(item.categoria ?? item.category))
    const looksLikeIncome =
      isExplicitIncome ||
      INCOME_KEYWORDS_REGEX.test(desc)

    const resolvedTipo = looksLikeIncome ? 'income' : (item.tipo === 'income' ? 'income' : 'expense')

    // Detecção de forma de pagamento
    const rawPayMethod = item.metodoPagamento ?? item.paymentMethod
    let resolvedMetodo: PaymentMethod
    if (resolvedTipo === 'income') {
      resolvedMetodo = 'pix'
    } else if (isBankStatement) {
      if (/pix/i.test(desc)) resolvedMetodo = 'pix'
      else if (/debito|débito/i.test(desc)) resolvedMetodo = 'debit'
      else if (/boleto|energia|luz|agua|água|internet|condominio|tributo|darf/i.test(desc)) resolvedMetodo = 'bill'
      else resolvedMetodo = (rawPayMethod && ['pix', 'debit', 'bill'].includes(rawPayMethod)) ? rawPayMethod : 'debit'
    } else if (rawPayMethod && ['pix', 'credit_card', 'debit', 'bill'].includes(rawPayMethod)) {
      resolvedMetodo = rawPayMethod
    } else if (defaultMetodo && ['pix', 'credit_card', 'debit', 'bill'].includes(defaultMetodo)) {
      resolvedMetodo = defaultMetodo as PaymentMethod
    } else if (defaultCartao || item.cartao || item.cardName || item.cardId) {
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
    const itemCardParam = item.cartao || item.cardName || item.cardId
    const fallbackCartao = env.getCards().find((c) => c.ownerUid === env.me.uid)?.cardName || env.getCards()[0]?.cardName
    const resolvedCartao = (!isBankStatement && resolvedMetodo === 'credit_card')
      ? (itemCardParam || resolvedDefaultCartao || fallbackCartao)
      : undefined

    // Detecção inteligente de rateio / split no item
    let itemFriendName = item.nomeAmigo ?? item.amigo ?? item.friendName
    let itemFriendEmail = item.emailAmigo ?? item.friendEmail
    let itemFriendPct = item.porcentagemAmigo ?? item.percentualAmigo ?? item.friendSharePct ?? item.friendPct
    let itemSplitType = item.tipoDivisao ?? item.splitType
    let cleanDesc = desc

    if (!itemFriendName && !itemSplitType) {
      const pctMatch = desc.match(/[;,]?\s*(\d+(?:[.,]\d+)?)\s*%\s*(?:para|com|de)\s+([A-Za-zÀ-ÖØ-öø-ÿ\s]+?)(?:[;,.]|$)/i)
      if (pctMatch) {
        itemFriendPct = Number(pctMatch[1].replace(',', '.'))
        itemFriendName = pctMatch[2].trim()
        cleanDesc = desc.replace(pctMatch[0], '').replace(/[;,]?\s*R\$\s*\d+(?:[.,]\d+)?/gi, '').replace(/[;,]\s*$/, '').trim()
      } else {
        const divMatch = desc.match(/[;,]?\s*(?:dividid[oa]|divis[ãa]o|dividi|meio\s+a\s+meio|50\/50|50-50)\s*(?:com|para)\s+([A-Za-zÀ-ÖØ-öø-ÿ\s]+?)(?:[;,.]|$)/i)
        if (divMatch) {
          itemFriendPct = 50
          itemFriendName = divMatch[1].trim()
          cleanDesc = desc.replace(divMatch[0], '').replace(/[;,]\s*$/, '').trim()
        }
      }
    }
    cleanDesc = cleanDesc.replace(/[;,]?\s*R\$\s*\d+(?:[.,]\d+)?/gi, '').replace(/[;,]\s*$/, '').trim()

    const itemDate = item.data || item.date || defaultVenc || todayKey()
    const merged = {
      ...item,
      descricao: cleanDesc,
      valorTotal: itemValor,
      data: itemDate,
      tipo: resolvedTipo,
      metodoPagamento: resolvedMetodo,
      status: resolvedStatus,
      cartao: resolvedCartao,
      nomeAmigo: itemFriendName,
      emailAmigo: itemFriendEmail,
      porcentagemAmigo: itemFriendPct,
      tipoDivisao: itemSplitType,
      mesFatura: item.mesFatura || item.competenceMonth || defaultMes,
      dataVencimento: item.dataVencimento || (resolvedMetodo === 'credit_card' ? defaultVenc : itemDate),
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

  const summaryAfter = getAgentSummary(env, env.getTransactions(), targetMonth)

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

export async function expandirParcelamentos(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
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
      { label: 'Saldo Livre em ' + monthLabel(targetMonth), value: money(getAgentSummary(env, env.getTransactions(), targetMonth).freeBalance) },
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

  const after = getAgentSummary(env, env.getTransactions(), targetMonth)

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

export async function excluir(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
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

  const deleteFutureOnly =
    Boolean(args.deleteFutureOnly) ||
    Boolean(args.cancelFutureOnly) ||
    Boolean(args.excluirFuturas) ||
    Boolean(args.cancelarFuturas)

  const deleteAllInstallments =
    Boolean(args.deleteAllInstallments) ||
    Boolean(args.excluirTodasAsParcelas) ||
    Boolean(args.excluirParcelamento)

  if (deleteFutureOnly && targets.length > 0) {
    const primary = targets[0]
    if (primary.installmentGroupId) {
      const fromNum = primary.installmentNumber ?? 1
      targets = all.filter(
        (t) =>
          t.installmentGroupId === primary.installmentGroupId &&
          (t.installmentNumber ?? 1) >= fromNum &&
          t.status !== 'paid'
      )
    }
  } else if (deleteAllInstallments && targets.length > 0) {
    const groupIds = new Set(targets.map((t) => t.installmentGroupId).filter(Boolean) as string[])
    if (groupIds.size > 0) {
      targets = all.filter((t) => t.installmentGroupId && groupIds.has(t.installmentGroupId))
    }
  }

  if (targets.length === 0) {
    return {
      response: { ok: false, erro: 'Nenhuma transação encontrada com os filtros informados para exclusão.' },
    }
  }

  const uneditable = targets.filter((t) => !canUserEditTransaction(t, env.me.uid))
  if (uneditable.length > 0) {
    return {
      response: {
        ok: false,
        erro: `Você não tem permissão para excluir ${uneditable.length === 1 ? 'o lançamento' : 'os lançamentos'} "${uneditable.map((u) => u.description).join(', ')}", pois foram registrados por outra pessoa e recebidos por você via rateio.`,
      },
    }
  }

  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
  const totalDeletedValue = targets.reduce((sum, t) => sum + effectiveAmount(t), 0)
  const deletedDescriptions = targets.map((t) => t.description)
  const affectedMonth = targets[0]?.competenceMonth || env.monthKey
  const isGroupDelete = deleteAllInstallments && targets.some((t) => Boolean(t.installmentGroupId))

  if (isPendingConfirmation) {
    const title = isGroupDelete
      ? `Excluir Parcelamento Completo (${targets.length} parcelas)`
      : `Excluir: ${targets.length === 1 ? targets[0].description : `${targets.length} lançamentos`}`
    const confirmationLines = [
      { label: 'Ação', value: isGroupDelete ? 'Exclusão de Parcelamento Completo' : 'Exclusão de Lançamento(s)' },
      { label: 'Quantidade', value: `${targets.length} parcela(s)` },
      { label: 'Itens a Excluir', value: deletedDescriptions.slice(0, 4).join(', ') + (deletedDescriptions.length > 4 ? ` (+${deletedDescriptions.length - 4})` : '') },
      { label: 'Valor a Remover', value: money(totalDeletedValue), tone: 'positive' as const },
      { label: 'Mês Afetado', value: monthLabel(affectedMonth) },
      { label: 'Saldo Livre Atual', value: money(getAgentSummary(env, all, affectedMonth).freeBalance) },
    ]

    return {
      response: {
        ok: true,
        aguardandoConfirmacao: true,
        mensagem: isGroupDelete
          ? `Entendi que você deseja excluir o parcelamento completo de "${targets[0]?.description || 'compra parcelada'}" (${targets.length} parcelas no total de ${money(totalDeletedValue)}). Por favor, confirme no botão abaixo se está correto para excluir do sistema.`
          : `Entendi que você deseja excluir ${targets.length === 1 ? `o lançamento "${targets[0].description}" no valor de ${money(totalDeletedValue)}` : `${targets.length} lançamentos totalizando ${money(totalDeletedValue)}`}. Por favor, confirme no botão abaixo se está correto para excluir do sistema.`,
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
  const after = getAgentSummary(env, nextAll, affectedMonth)

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

export async function editar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const all = env.getTransactions()
  const ids = Array.isArray(args.ids) ? args.ids.map(String) : []
  const termo = typeof args.termoBusca === 'string' ? args.termoBusca.trim() : ''
  const termos = Array.isArray(args.termosBusca)
    ? (args.termosBusca as string[]).map(String).filter(Boolean)
    : termo ? [termo] : []
  const mesOrigem = typeof args.mesOrigem === 'string' && /^\d{4}-\d{2}$/.test(args.mesOrigem) ? args.mesOrigem : ''
  const dataOriginal = typeof args.dataOriginal === 'string' ? args.dataOriginal.trim()
    : typeof args.originDate === 'string' ? args.originDate.trim()
    : typeof args.dataAtual === 'string' ? args.dataAtual.trim()
    : ''
  const valorFiltro = typeof args.valorFiltro === 'number' ? args.valorFiltro
    : typeof args.filterAmount === 'number' ? args.filterAmount
    : typeof args.valor === 'number' ? args.valor
    : undefined
  const alterarTodos = args.alterarTodos === true || args.editAllMatches === true || args.editAll === true
  const limite = typeof args.limiteQuantidade === 'number' ? args.limiteQuantidade : 50

  let targets: Transaction[] = []

  if (ids.length > 0) {
    targets = all.filter((t) => ids.includes(t.id))
  } else if (termos.length > 0) {
    const selected: Transaction[] = []
    const selectedIds = new Set<string>()

    for (const tTerm of termos) {
      const candidates = all.filter((t) => {
        if (selectedIds.has(t.id)) return false
        if (mesOrigem && t.competenceMonth !== mesOrigem) return false
        if (dataOriginal && t.dueDate !== dataOriginal) return false
        if (valorFiltro != null && Math.abs(effectiveAmount(t) - valorFiltro) > 0.05) return false
        return matchesSearch(t.description, tTerm) || matchesSearch(t.category, tTerm) || (t.subcategory && matchesSearch(t.subcategory, tTerm))
      })

      if (alterarTodos) {
        for (const c of candidates) {
          selected.push(c)
          selectedIds.add(c.id)
        }
      } else {
        // Seleção cirúrgica: escolhe apenas o item mais recente correspondente a este termo específico
        const best = candidates.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0))[0]
        if (best) {
          selected.push(best)
          selectedIds.add(best.id)
        }
      }
    }
    targets = selected.slice(0, limite)
  } else if (mesOrigem || dataOriginal) {
    targets = all.filter((t) => {
      if (mesOrigem && t.competenceMonth !== mesOrigem) return false
      if (dataOriginal && t.dueDate !== dataOriginal) return false
      if (valorFiltro != null && Math.abs(effectiveAmount(t) - valorFiltro) > 0.05) return false
      return true
    }).slice(0, limite)
  }

  if (targets.length === 0) {
    return {
      response: { ok: false, erro: 'Nenhuma transação encontrada para edição com os critérios informados.' },
    }
  }

  const uneditable = targets.filter((t) => !canUserEditTransaction(t, env.me.uid))
  if (uneditable.length > 0) {
    return {
      response: {
        ok: false,
        error: `Não foi possível editar: o lançamento "${uneditable[0].description}" foi realizado por outra pessoa e você recebeu o rateio. Apenas quem lançou pode fazer alterações nele.`,
      },
      action: {
        kind: 'error',
        title: 'Edição não permitida',
        lines: [
          { label: 'Motivo', value: 'Lançamento de terceiro recebido via rateio', tone: 'negative' },
          { label: 'Item', value: uneditable[0].description },
        ],
      },
    }
  }

  const novoMesFatura = typeof args.novoMesFatura === 'string' && /^\d{4}-\d{2}$/.test(args.novoMesFatura)
    ? args.novoMesFatura
    : undefined
  const novaDataVencimento = typeof args.novaDataVencimento === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.novaDataVencimento)
    ? args.novaDataVencimento
    : undefined

  // Se alguma das transações pertencer a um grupo de parcelamento, inclui as parcelas irmãs do mesmo grupo
  // APENAS se NÃO estiver alterando dataVencimento ou mesFatura (alterar data é operação específica do lançamento)
  const isDateChange = Boolean(novaDataVencimento || novoMesFatura)
  const installmentGroupIds = new Set(targets.map((t) => t.installmentGroupId).filter(Boolean) as string[])
  if (installmentGroupIds.size > 0 && !isDateChange) {
    const siblings = all.filter(
      (t) => t.installmentGroupId && installmentGroupIds.has(t.installmentGroupId) && !targets.some((x) => x.id === t.id)
    )
    if (siblings.length > 0) {
      targets = [...targets, ...siblings]
    }
  }
  const novoValor = typeof args.novoValor === 'number' ? round2(args.novoValor) : undefined
  const novaDescricao = typeof args.novaDescricao === 'string' ? args.novaDescricao.trim() : undefined
  let novaCategoria: Category | undefined
  let novaSubcategoria: string | null | undefined = undefined
  const hasNovaCat = args.novaCategoria != null && String(args.novaCategoria).trim() !== ''
  const hasNovaSub = typeof args.novaSubcategoria === 'string' && args.novaSubcategoria.trim() !== ''

  if (hasNovaCat || hasNovaSub) {
    const targetType = targets[0]?.type || 'expense'
    const rawCatToResolve = hasNovaCat ? String(args.novaCategoria) : targets[0]?.category
    const rawSubToResolve = hasNovaSub ? String(args.novaSubcategoria).trim() : (hasNovaCat ? null : targets[0]?.subcategory)

    const strict = resolveStrictCategory(
      rawCatToResolve,
      rawSubToResolve,
      targetType,
      novaDescricao || targets[0]?.description,
      (d, t) => guessCategoryAndSubcategory(d, undefined, t)
    )
    novaCategoria = strict.category
    novaSubcategoria = strict.subcategory
  }
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
        patch.category = 'Outras Receitas'
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
  const summaryAfter = getAgentSummary(env, env.getTransactions(), targetMonth)

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

export async function anteciparParcelas(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const searchTerm = sanitizeText(args.termoBusca ?? args.descricao ?? '', 200)
  const groupId = typeof args.installmentGroupId === 'string' ? args.installmentGroupId.trim() : null
  const count = typeof args.quantidadeParcelas === 'number' ? Math.max(1, Math.round(args.quantidadeParcelas)) : 999
  const targetMonth = typeof args.mesDestino === 'string' && /^\d{4}-\d{2}$/.test(args.mesDestino)
    ? args.mesDestino
    : env.monthKey
  const discount = sanitizeAmount(args.desconto ?? args.discount) || 0

  const all = env.getTransactions()
  let matchedGroupTxs: Transaction[] = []
  if (groupId) {
    matchedGroupTxs = all.filter((t) => t.installmentGroupId === groupId)
  } else if (searchTerm) {
    const candidate = all.find(
      (t) => Boolean(t.installmentGroupId) && matchesSearch(t.description, searchTerm)
    )
    if (candidate && candidate.installmentGroupId) {
      matchedGroupTxs = all.filter((t) => t.installmentGroupId === candidate.installmentGroupId)
    }
  }

  if (matchedGroupTxs.length === 0) {
    return {
      response: {
        ok: false,
        erro: `Não encontrei nenhuma compra parcelada pendente correspondente a "${searchTerm}".`,
      },
    }
  }

  const baseTx = matchedGroupTxs[0]
  if (!canUserEditTransaction(baseTx, env.me.uid)) {
    return {
      response: {
        ok: false,
        erro: `Você não tem permissão para antecipar parcelas de uma compra feita por outra pessoa.`,
      },
    }
  }

  const { updatedTransactions, discountTransaction, anticipatedCount, totalAnticipatedAmount } = calcAnticipateInstallments(
    all,
    baseTx.installmentGroupId!,
    count,
    targetMonth,
    discount,
    todayKey(),
    { uid: env.me.uid, displayName: env.me.name }
  )

  if (anticipatedCount === 0) {
    return {
      response: {
        ok: false,
        erro: `Não há parcelas pendentes disponíveis para antecipar nesta compra para a fatura de ${monthLabel(targetMonth)}.`,
      },
    }
  }

  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
  const baseDesc = stripInstallmentSuffix(baseTx.description)

  if (isPendingConfirmation) {
    return {
      response: {
        ok: true,
        precisaConfirmacao: true,
        acao: {
          tipo: 'anteciparParcelas',
          title: `Antecipar ${anticipatedCount} ${anticipatedCount === 1 ? 'parcela' : 'parcelas'}: ${baseDesc}`,
          detalhes: [
            { label: 'Compra', value: baseDesc },
            { label: 'Parcelas a antecipar', value: `${anticipatedCount} parcelas finais` },
            { label: 'Valor bruto antecipado', value: money(totalAnticipatedAmount) },
            ...(discount > 0 ? [{ label: 'Desconto concedido', value: `−${money(discount)}` }] : []),
            { label: 'Fatura de destino', value: monthLabel(targetMonth) },
          ],
          mensagem: `Confirma a antecipação de ${anticipatedCount} parcelas da compra "${baseDesc}" (${money(totalAnticipatedAmount)}) para a fatura de ${monthLabel(targetMonth)}${discount > 0 ? ` com desconto de ${money(discount)}` : ''}?`,
        },
      },
    }
  }

  // Executa a antecipação
  for (const tx of updatedTransactions) {
    await env.updateTransaction(tx.id, {
      competenceMonth: tx.competenceMonth,
      anticipatedFromMonth: tx.anticipatedFromMonth,
      isAnticipated: true,
    })
  }

  if (discountTransaction) {
    const { id: _id, ...data } = discountTransaction
    await env.addTransaction(data)
  }

  return {
    response: {
      ok: true,
      mensagem: `🎉 **${anticipatedCount} ${anticipatedCount === 1 ? 'parcela antecipada' : 'parcelas antecipadas'} com sucesso!**\n\n• **Compra**: ${baseDesc}\n• **Total antecipado**: ${money(totalAnticipatedAmount)}\n• **Fatura de destino**: ${monthLabel(targetMonth)}${discount > 0 ? `\n• **Crédito de desconto**: −${money(discount)}` : ''}`,
    },
  }
}

export async function estornarCompraParcelada(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const searchTerm = sanitizeText(args.termoBusca ?? args.descricao ?? '', 200)
  const groupId = typeof args.installmentGroupId === 'string' ? args.installmentGroupId.trim() : null
  const targetMonth = typeof args.mesDestino === 'string' && /^\d{4}-\d{2}$/.test(args.mesDestino)
    ? args.mesDestino
    : env.monthKey

  const all = env.getTransactions()
  let matchedGroupTxs: Transaction[] = []
  if (groupId) {
    matchedGroupTxs = all.filter((t) => t.installmentGroupId === groupId)
  } else if (searchTerm) {
    const candidate = all.find(
      (t) => Boolean(t.installmentGroupId) && matchesSearch(t.description, searchTerm)
    )
    if (candidate && candidate.installmentGroupId) {
      matchedGroupTxs = all.filter((t) => t.installmentGroupId === candidate.installmentGroupId)
    }
  }

  if (matchedGroupTxs.length === 0) {
    return {
      response: {
        ok: false,
        erro: `Não encontrei nenhuma compra parcelada correspondente a "${searchTerm}" para estorno integral.`,
      },
    }
  }

  const baseTx = matchedGroupTxs[0]
  if (!canUserEditTransaction(baseTx, env.me.uid)) {
    return {
      response: {
        ok: false,
        erro: `Você não tem permissão para estornar uma compra feita por outra pessoa.`,
      },
    }
  }

  const { toDeleteIds, totalPaidAmount, refundTransaction } = calculateFullInstallmentRefund(
    matchedGroupTxs,
    { uid: env.me.uid, displayName: env.me.name },
    todayKey(),
    targetMonth
  )

  const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
  const baseDesc = stripInstallmentSuffix(baseTx.description)

  if (isPendingConfirmation) {
    return {
      response: {
        ok: true,
        precisaConfirmacao: true,
        acao: {
          tipo: 'estornarCompraParcelada',
          title: `Estorno Integral da Compra: ${baseDesc}`,
          detalhes: [
            { label: 'Compra', value: baseDesc },
            { label: 'Parcelas canceladas (futuras)', value: `${toDeleteIds.length} parcelas` },
            ...(totalPaidAmount > 0
              ? [
                  { label: 'Valor pago a estornar', value: money(totalPaidAmount) },
                  { label: 'Fatura de crédito', value: monthLabel(targetMonth) },
                ]
              : [{ label: 'Valor pago anterior', value: 'R$ 0,00 (nenhuma parcela paga ainda)' }]),
          ],
          mensagem: `Confirma o estorno integral da compra "${baseDesc}"? ${toDeleteIds.length} parcelas futuras serão canceladas${totalPaidAmount > 0 ? ` e um estorno de ${money(totalPaidAmount)} será lançado na fatura de ${monthLabel(targetMonth)} para reembolsar o valor já pago.` : '.'}`,
        },
      },
    }
  }

  // Executa o cancelamento e lançamento de estorno
  for (const id of toDeleteIds) {
    await env.deleteTransaction(id)
  }

  if (refundTransaction) {
    const { id: _id, ...data } = refundTransaction
    await env.addTransaction(data)
  }

  return {
    response: {
      ok: true,
      mensagem: `🔄 **Estorno integral de compra parcelada concluído!**\n\n• **Compra**: ${baseDesc}\n• **Parcelas canceladas**: ${toDeleteIds.length} pendentes\n${totalPaidAmount > 0 ? `• **Crédito de estorno gerado**: +${money(totalPaidAmount)} na fatura de ${monthLabel(targetMonth)}` : '• **Status**: todas as obrigações futuras foram removidas sem custos.'}`,
    },
  }
}
