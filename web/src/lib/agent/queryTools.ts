import type { Card } from '@/types'
import {
  estimateRecurring,
  findMatchingManualBill,
  impactFor,
  isMyExpenseTx,
} from '../finance'
import { currentMonthKey, dateInMonth, monthLabel, normalize, round2 } from '../utils'
import { matchesSearch, money, parseMonthKey } from './common'
import type { AgentEnv, ToolResult } from './types'

export async function consultar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const all = env.getTransactions()
  const termo = typeof args.termoBusca === 'string' ? args.termoBusca.trim() : ''
  const categoria = typeof args.categoria === 'string' ? args.categoria.trim() : ''
  const subcategoria = typeof args.subcategoria === 'string' ? args.subcategoria.trim() : ''
  const mes = parseMonthKey(args.mes)
  const dataInicio = typeof args.dataInicio === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.dataInicio) ? args.dataInicio.trim() : ''
  const dataFim = typeof args.dataFim === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(args.dataFim) ? args.dataFim.trim() : ''
  const somenteMinhas = args.somenteMinhas === true || args.apenasMinhas === true
  const todosMeusGastos = args.todosMeusGastos === true || args.meusGastos === true
  const pessoa = typeof args.pessoa === 'string' ? args.pessoa.trim() : typeof args.terceiro === 'string' ? args.terceiro.trim() : ''
  const apenasTerceiros = args.apenasTerceiros === true || args.terceiros === true || /^(terceiro|terceiros|amigos|amigo)$/i.test(pessoa)
  const cartaoFiltro = typeof args.cartao === 'string' ? args.cartao.trim() : ''
  const metodoPagamentoFiltro = typeof args.metodoPagamento === 'string' ? args.metodoPagamento.trim().toLowerCase() : ''
  const tipoFiltro = typeof args.tipo === 'string' ? args.tipo.trim().toLowerCase() : ''
  const status = typeof args.status === 'string' ? args.status : ''
  const incluirRecorrentesProjetadas =
    typeof args.incluirRecorrentesProjetadas === 'boolean'
      ? args.incluirRecorrentesProjetadas
      : Boolean(mes && mes >= currentMonthKey())
  const limite = typeof args.limite === 'number' && args.limite > 0 ? args.limite : 40

  const cards = env.getCards()
  const cardsMap = new Map(cards.map((c) => [c.id, c]))
  let matchedCard: Card | undefined
  if (cartaoFiltro) {
    const normCartao = normalize(cartaoFiltro)
    matchedCard = cards.find((c) => normalize(c.cardName).includes(normCartao) || c.id === cartaoFiltro)
  }

  const pendingTxs = env.getPendingTransactions?.()
  const isExplicitBatchSearch =
    args.pendentesExtrato === true ||
    args.loteExtrato === true ||
    /lote|extrato.*pendente|importad/i.test(termo)
  if (pendingTxs && pendingTxs.length > 0 && isExplicitBatchSearch) {
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

    // 6.5. Meio de Pagamento / Canal (Fatura vs Débito/Pix/Boleto)
    if (metodoPagamentoFiltro) {
      const isCard = t.paymentMethod === 'credit_card' || Boolean(t.cardId)
      if (metodoPagamentoFiltro === 'credit_card') {
        if (!isCard) return false
      } else if (metodoPagamentoFiltro === 'extrato_conta') {
        if (isCard) return false
      } else {
        if (isCard && metodoPagamentoFiltro !== 'credit_card') return false
        if (t.paymentMethod !== metodoPagamentoFiltro) return false
      }
    }

    // 7. Termo de busca livre
    if (termo && !matchesSearch(t.description, termo) && !(t.subcategory && matchesSearch(t.subcategory, termo))) return false

    // 7.5. FILTRO: TODOS OS MEUS GASTOS (100% MEUS + RATEADOS COM MINHA PARTICIPAÇÃO)
    if (todosMeusGastos) {
      if (!isMyExpenseTx(t, env.me.uid, all)) return false
    }

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

  // Se houver um mês filtrado e incluirRecorrentesProjetadas estiver ativo, inclui também as contas fixas não lançadas que atendam aos filtros
  const projectedRecurringItems: Array<{
    id: string
    descricao: string
    valorTotal: number
    meuImpacto: number
    impactoTerceiros: number
    dataVencimento: string
    dataCompra: string
    mesCompetencia: string
    categoria: string
    subcategoria: string | null
    cartao: string | null
    metodoPagamento: string
    canal: string
    status: string
    tipo: string
    divisao: string
    nomeAmigo: null
    detalhesDivisao: string
    ehContaFixaProjetada: boolean
  }> = []

  if (mes && incluirRecorrentesProjetadas && !status) {
    const monthTxs = all.filter((t) => t.competenceMonth === mes)
    const activeBills = (env.getRecurring?.() || []).filter((b) => b.active && b.ownerUid === env.me.uid)
    for (const bill of activeBills) {
      if (bill.startMonth && mes < bill.startMonth) continue
      if (bill.endMonth && mes > bill.endMonth) continue
      if (monthTxs.some((t) => t.recurringBillId === bill.id)) continue
      if (findMatchingManualBill(monthTxs, mes, bill)) continue

      const bType = bill.type ?? 'expense'
      if (tipoFiltro && tipoFiltro !== 'all' && bType !== tipoFiltro) continue
      if (!tipoFiltro && (somenteMinhas || pessoa || apenasTerceiros || cartaoFiltro || categoria) && bType !== 'expense' && categoria !== 'Receita') continue
      if (categoria && bill.category !== categoria) continue
      if (subcategoria && (!bill.subcategory || !normalize(bill.subcategory).includes(normalize(subcategoria)))) continue
      if (termo && !matchesSearch(bill.description, termo)) continue
      if (apenasTerceiros && bill.splitType !== 'split_50') continue
      if (somenteMinhas && bill.splitType === 'split_50') continue

      const effMethod = bill.paymentMethod ?? (bType === 'income' ? 'pix' : bill.cardId ? 'credit_card' : 'bill')
      const isCard = effMethod === 'credit_card' || Boolean(bill.cardId)
      const cardObj = bill.cardId ? cardsMap.get(bill.cardId) : undefined

      if (cartaoFiltro) {
        if (matchedCard) {
          if (bill.cardId !== matchedCard.id) continue
        } else if (!cardObj || !matchesSearch(cardObj.cardName, cartaoFiltro)) {
          continue
        }
      }
      if (metodoPagamentoFiltro) {
        if (metodoPagamentoFiltro === 'credit_card' && !isCard) continue
        if (metodoPagamentoFiltro === 'extrato_conta' && isCard) continue
        if (metodoPagamentoFiltro !== 'credit_card' && metodoPagamentoFiltro !== 'extrato_conta' && effMethod !== metodoPagamentoFiltro) continue
      }

      const { amount } = estimateRecurring(bill, all, mes)
      const myImp = bill.splitType === 'split_50' ? round2(amount / 2) : amount
      const thirdImp = round2(Math.max(0, amount - myImp))
      const dueStr = cardObj ? dateInMonth(mes, cardObj.dueDay) : dateInMonth(mes, bill.dueDay)

      projectedRecurringItems.push({
        id: `proj_${bill.id}_${mes}`,
        descricao: `${bill.description} (Conta Fixa Prevista)`,
        valorTotal: amount,
        meuImpacto: myImp,
        impactoTerceiros: thirdImp,
        dataVencimento: dueStr,
        dataCompra: dueStr,
        mesCompetencia: mes,
        categoria: bill.category,
        subcategoria: bill.subcategory || null,
        cartao: cardObj ? cardObj.cardName : null,
        metodoPagamento: isCard ? 'credit_card' : effMethod,
        canal: isCard ? `Fatura Cartão ${cardObj?.cardName || ''}`.trim() : effMethod === 'debit' ? 'Débito' : effMethod === 'bill' ? 'Boleto' : 'PIX',
        status: 'projected_recurring',
        tipo: bType,
        divisao: bill.splitType || 'mine',
        nomeAmigo: null,
        detalhesDivisao: bill.splitType === 'split_50' ? `Dividido 50/50 (Sua parte: ${money(myImp)})` : '100% do usuário',
        ehContaFixaProjetada: true,
      })
    }
  }

  // Ordena por data de vencimento / createdAt mais recente primeiro
  filtered.sort((a, b) => (b.dueDate || '').localeCompare(a.dueDate || '') || (b.createdAt || 0) - (a.createdAt || 0))

  const targets = filtered.slice(0, limite)
  const somaRealTotal = filtered.reduce((acc, t) => acc + (t.totalAmount || 0), 0)
  const somaRealImpacto = filtered.reduce((acc, t) => acc + impactFor(t, env.me.uid, all), 0)
  const somaRecorrentesTotal = projectedRecurringItems.reduce((acc, i) => acc + i.valorTotal, 0)
  const somaRecorrentesImpacto = projectedRecurringItems.reduce((acc, i) => acc + i.meuImpacto, 0)

  const somaTotal = somaRealTotal + somaRecorrentesTotal
  const somaMeuImpacto = somaRealImpacto + somaRecorrentesImpacto
  const somaTerceiros = Math.max(0, somaTotal - somaMeuImpacto)

  // Separação por Canal: Fatura de Cartão vs Extrato Conta Corrente (Pix, Débito, Boleto)
  let totalFaturasCartao = 0
  let totalExtratoConta = 0
  let totalPix = 0
  let totalDebito = 0
  let totalBoleto = 0

  for (const t of filtered) {
    const imp = impactFor(t, env.me.uid, all)
    const isCard = t.paymentMethod === 'credit_card' || Boolean(t.cardId)
    if (isCard) {
      totalFaturasCartao += imp
    } else {
      totalExtratoConta += imp
      if (t.paymentMethod === 'debit') totalDebito += imp
      else if (t.paymentMethod === 'bill') totalBoleto += imp
      else totalPix += imp
    }
  }
  for (const r of projectedRecurringItems) {
    if (r.metodoPagamento === 'credit_card') {
      totalFaturasCartao += r.meuImpacto
    } else {
      totalExtratoConta += r.meuImpacto
      if (r.metodoPagamento === 'debit') totalDebito += r.meuImpacto
      else if (r.metodoPagamento === 'bill') totalBoleto += r.meuImpacto
      else totalPix += r.meuImpacto
    }
  }

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

    const isCard = t.paymentMethod === 'credit_card' || Boolean(t.cardId)
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
      metodoPagamento: isCard ? 'credit_card' : t.paymentMethod,
      canal: isCard
        ? `Fatura Cartão ${cardObj?.cardName || ''}`.trim()
        : t.paymentMethod === 'debit'
          ? 'Débito'
          : t.paymentMethod === 'bill'
            ? 'Boleto'
            : 'PIX',
      status: t.status,
      tipo: t.type,
      divisao: t.splitType || 'mine',
      nomeAmigo: t.assignedFriendName || null,
      detalhesDivisao: detalheDivisao,
      ehContaFixaProjetada: false,
    }
  })

  const allItemsCombined = [...items, ...projectedRecurringItems]

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
      totalEncontrados: filtered.length + projectedRecurringItems.length,
      somaTotal: round2(somaTotal),
      somaMeuImpacto: round2(somaMeuImpacto),
      somaTerceiros: round2(somaTerceiros),
      resumoCanaisPagamento: {
        totalFaturasCartao: round2(totalFaturasCartao),
        totalExtratoConta: round2(totalExtratoConta),
        detalheExtrato: {
          pix: round2(totalPix),
          debito: round2(totalDebito),
          boleto: round2(totalBoleto),
        },
      },
      cartaoReferenciado,
      porSubcategoria,
      porPessoa,
      porCartao,
      exibindoQuantidade: allItemsCombined.length,
      transacoes: allItemsCombined,
    },
    action: {
      kind: 'report',
      title: reportTitle,
      lines,
    },
  }
}

