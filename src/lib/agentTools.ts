/**
 * Implementação das ferramentas do agente (usada tanto pelo Gemini real
 * quanto pelo agente offline do Modo Demo).
 */
import type { Allocation, Card, Category, NewTransaction, PaymentMethod, SplitType, Transaction, TransactionStatus, UserProfile } from '@/types'
import { CATEGORIES } from '@/types'
import {
  amountPatch,
  buildSplit,
  calculateCardInvoiceDates,
  clampPct,
  computeSummary,
  detectSplit,
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
import {
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

export interface AgentEnv {
  getTransactions: () => Transaction[]
  getCards: () => Card[]
  getFriends?: () => Array<{ uid: string; name: string; email?: string }>
  lookupUserByEmail?: (email: string) => Promise<UserProfile | null>
  me: Party
  partner: Party | null
  monthKey: string
  addTransaction: (tx: NewTransaction) => Promise<string>
  addMany?: (txs: NewTransaction[]) => Promise<void>
  updateTransaction: (id: string, patch: Partial<Transaction>) => Promise<void>
  deleteTransaction: (id: string) => Promise<void>
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
      default:
        return { response: { ok: false, erro: `Ferramenta desconhecida: ${name}` } }
    }
  }
}

async function registrar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const rawTotal = Number(args.valorTotal)
  if (isNaN(rawTotal) || rawTotal === 0) return { response: { ok: false, erro: 'valorTotal inválido' } }
  const total = round2(rawTotal)

  const type = args.tipo === 'income' ? 'income' : 'expense'
  let splitType: SplitType = SPLIT_MAP[String(args.tipoDivisao ?? 'MEU')] ?? 'mine'
  if (type === 'income') splitType = 'mine'
  const method = (['pix', 'credit_card', 'debit', 'bill'].includes(String(args.metodoPagamento))
    ? args.metodoPagamento
    : 'credit_card') as PaymentMethod

  const description = String(args.descricao ?? 'Lançamento').trim()
  let category = (CATEGORIES as readonly string[]).includes(String(args.categoria))
    ? (args.categoria as Category)
    : guessCategory(description, total)
  if (total < 0 && category === 'Outros') category = 'Estorno'
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
    finalDueDate = invoiceDates.dueDate
    competenceMonth = invoiceDates.competenceMonth
  } else if (explicitDueDate) {
    finalDueDate = explicitDueDate
    competenceMonth = explicitMonth || explicitDueDate.slice(0, 7)
  } else if (explicitMonth) {
    competenceMonth = explicitMonth
    finalDueDate = `${explicitMonth}-${date.slice(8, 10)}`
  }

  const before = computeSummary(env.getTransactions(), env.me.uid, competenceMonth)

  let id = ''
  let finalDescription = cleanBaseDesc

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
    finalDescription = formatInstallmentDesc(cleanBaseDesc, parcelaAtual, totalParcelas)
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
    finalDescription = cleanBaseDesc || description
  }

  const after = computeSummary(env.getTransactions(), env.me.uid, competenceMonth)

  // Split para relatório da parcela atual
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
        { label: 'Mês da Fatura', value: monthLabel(competenceMonth) },
        { label: 'Vencimento', value: formatShortDate(finalDueDate) },
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
        ...(card ? [{ label: 'Cartão', value: card.cardName }] : []),
        {
          label: 'Meu impacto',
          value: myImpact > 0 ? `−${money(myImpact)}` : myImpact < 0 ? `+${money(Math.abs(myImpact))}` : 'R$ 0,00 (Neutro)',
          tone: myImpact > 0 ? 'negative' : myImpact < 0 ? 'positive' : 'neutral',
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

  const defaultMes = typeof args.mesFaturaPadrao === 'string' ? args.mesFaturaPadrao : undefined
  const defaultVenc = typeof args.dataVencimentoPadrao === 'string' ? args.dataVencimentoPadrao : undefined
  const defaultCartao = typeof args.cartaoPadrao === 'string' ? args.cartaoPadrao : undefined

  const results: any[] = []
  let totalValor = 0
  let meuImpactoTotal = 0
  let aReceberTotal = 0
  let totalParcelasGeradas = 0
  let targetMonth = defaultMes || env.monthKey

  for (const item of transacoesRaw) {
    const merged = {
      metodoPagamento: 'credit_card',
      ...item,
      mesFatura: item.mesFatura || defaultMes,
      dataVencimento: item.dataVencimento || defaultVenc,
      cartao: item.cartao || defaultCartao,
    }
    const single = await registrar(env, merged)
    if (single.response.ok) {
      const resp = single.response as any
      results.push(resp)
      totalValor += resp.valorParcela || resp.valorTotal || 0
      meuImpactoTotal += resp.meuImpactoNoSaldoLivre || 0
      aReceberTotal += resp.aReceberGerado || 0
      totalParcelasGeradas += (resp.totalParcelas || 1)
      if (resp.mesCompetenciaFatura) targetMonth = resp.mesCompetenciaFatura
    }
  }

  const summaryAfter = computeSummary(env.getTransactions(), env.me.uid, targetMonth)

  return {
    response: {
      ok: true,
      quantidadeRegistrada: results.length,
      totalParcelasCriadas: totalParcelasGeradas,
      totalValorFaturaAtual: round2(totalValor),
      meuImpactoTotal: round2(meuImpactoTotal),
      aReceberTotal: round2(aReceberTotal),
      mesCompetencia: targetMonth,
      saldoLivreDepois: summaryAfter.freeBalance,
      itens: results,
    },
    action: {
      kind: 'created',
      title: `Lote de ${results.length} despesas registrado com sucesso!`,
      lines: [
        { label: 'Qtd. Lançamentos', value: `${results.length} itens (${totalParcelasGeradas} parcelas geradas)` },
        { label: 'Total da Fatura', value: money(totalValor) },
        { label: 'Meu Impacto Pessoal', value: money(meuImpactoTotal), tone: meuImpactoTotal > 0 ? 'negative' : 'neutral' },
        ...(aReceberTotal > 0 ? [{ label: 'A Receber (Kayo/Mãe)', value: money(aReceberTotal), tone: 'positive' as const }] : []),
        { label: 'Mês da Fatura', value: monthLabel(targetMonth) },
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
  const ids = Array.isArray(args.ids) ? args.ids.map(String) : []
  const termo = typeof args.termoBusca === 'string' ? args.termoBusca.trim() : ''
  const mes = typeof args.mes === 'string' && /^\d{4}-\d{2}$/.test(args.mes) ? args.mes : ''
  const apenasRecentes = Boolean(args.apenasRecentes)
  const limite = typeof args.limiteQuantidade === 'number' ? args.limiteQuantidade : 50
  const tudoDoMes = Boolean(args.tudoDoMes)

  let targets: Transaction[] = []

  if (ids.length > 0) {
    targets = all.filter((t) => ids.includes(t.id))
  } else if (apenasRecentes) {
    const sorted = [...all].sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    targets = sorted.slice(0, limite)
  } else if (tudoDoMes && mes) {
    targets = all.filter((t) => t.competenceMonth === mes)
  } else if (termo || mes) {
    targets = all.filter((t) => {
      if (mes && t.competenceMonth !== mes) return false
      if (termo && !matchesSearch(t.description, termo)) return false
      return true
    }).slice(0, limite)
  }

  if (targets.length === 0) {
    return {
      response: { ok: false, erro: 'Nenhuma transação encontrada com os filtros informados para exclusão.' },
    }
  }

  let totalDeletedValue = 0
  const deletedDescriptions: string[] = []

  for (const t of targets) {
    await env.deleteTransaction(t.id)
    totalDeletedValue += effectiveAmount(t)
    deletedDescriptions.push(t.description)
  }

  const affectedMonth = targets[0]?.competenceMonth || env.monthKey
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
  const novoStatus = (['estimated', 'pending', 'paid'].includes(String(args.novoStatus))
    ? args.novoStatus
    : undefined) as TransactionStatus | undefined

  const novoTipoDivisao = SPLIT_MAP[String(args.novoTipoDivisao)]
  const novoAmigoEmail = typeof args.novoAmigoEmail === 'string' ? args.novoAmigoEmail.trim().toLowerCase() : ''
  const novoNomeTerceiro = typeof args.novoNomeTerceiro === 'string' ? args.novoNomeTerceiro.trim() : ''

  const updatedIds: string[] = []

  for (const tx of targets) {
    const patch: Partial<Transaction> = {}

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
    if (novaCategoria) {
      patch.category = novaCategoria
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

  const targetMonth = novoMesFatura || targets[0]?.competenceMonth || env.monthKey
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
        ...(novoMesFatura ? [{ label: 'Novo Mês da Fatura', value: monthLabel(novoMesFatura) }] : []),
        ...(novaDataVencimento ? [{ label: 'Novo Vencimento', value: novaDataVencimento }] : []),
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
        { label: 'Status', value: newStatus === 'paid' ? 'Pago' : 'A Pagar' },
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
