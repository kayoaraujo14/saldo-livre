import { buildCardInvoice } from '../invoices'
import {
  computeSummary,
  effectiveAmount,
  getReserveInfo,
  impactFor,
  listReceivables,
  projectFutureMonths,
} from '../finance'
import { currentMonthKey, monthLabel, normalize, round2 } from '../utils'
import { money, parseMonthKey } from './common'
import type { AgentEnv, ToolResult } from './types'

export async function relatorio(env: AgentEnv, args?: Record<string, unknown>): Promise<ToolResult> {
  const all = env.getTransactions()
  const targetMonth = parseMonthKey(args?.mes) || env.monthKey
  const countRaw = typeof args?.quantidadeMeses === 'number' ? Math.round(args.quantidadeMeses) : 1
  const count = Math.min(12, Math.max(1, countRaw))
  const cartaoFiltro = typeof args?.cartao === 'string' ? args.cartao.trim() : ''
  const closures = env.getClosures?.() || []
  const cards = env.getCards()
  const s = computeSummary(all, env.me.uid, targetMonth, closures, cards)
  const { receivables, payables } = listReceivables(all, env.me.uid, targetMonth, cards)
  const monthTxs = all.filter((t) => t.competenceMonth === targetMonth && (t.visibleToUids || []).includes(env.me.uid))

  const isFutureMonth = targetMonth > currentMonthKey()
  const bills = env.getRecurring?.() || []
  const profile = env.getProfile?.() || null

  const projections12 = projectFutureMonths({
    all,
    bills,
    profile,
    uid: env.me.uid,
    startMonthKey: targetMonth,
    count,
    cards,
    closures,
  })
  const projectedTarget = projections12[0] || null

  // Detalhamento completo de Faturas por Cartão no targetMonth via buildCardInvoice
  const faturasPorCartao = cards
    .filter((c) => {
      if (!cartaoFiltro) return true
      return normalize(c.cardName).includes(normalize(cartaoFiltro)) || c.id === cartaoFiltro
    })
    .map((card) => {
      const inv = buildCardInvoice(card, targetMonth, all, env.me.uid)
      // Inclui assinaturas fixas vinculadas a este cartão que ainda não foram lançadas no mês
      const unlaunchedCardBills = (projectedTarget?.items || []).filter(
        (i) => i.isRecurringProjection && i.cardId === card.id && i.type === 'expense',
      )
      const unlaunchedTotal = round2(unlaunchedCardBills.reduce((acc, b) => acc + b.myImpact, 0))
      return {
        cartaoId: card.id,
        cartaoNome: card.cardName,
        titular: card.ownerName,
        dataFechamento: inv.closingDate,
        dataVencimento: inv.dueDate,
        statusFatura: inv.status,
        totalBrutoFatura: round2(inv.total + unlaunchedTotal),
        creditosEstornos: inv.credits,
        minhaParteFatura: round2(inv.myShare + unlaunchedTotal),
        parteTerceirosFatura: inv.othersShare,
        jaPagoFatura: inv.paidAmount,
        emAbertoFatura: round2(inv.openAmount + unlaunchedTotal),
        divisaoPorPessoa: inv.splitBreakdown,
        gastosPorCategoriaNaFatura: inv.byCategory,
        itensDaFatura: [
          ...inv.items.map((t) => ({
            id: t.id,
            descricao: t.description,
            valorTotal: effectiveAmount(t),
            meuImpacto: round2(impactFor(t, env.me.uid, all)),
            categoria: t.category,
            subcategoria: t.subcategory || null,
            vencimento: t.dueDate,
            status: t.status,
            parcela:
              t.installmentsTotal && t.installmentsTotal > 1
                ? `${t.installmentNumber || 1}/${t.installmentsTotal}`
                : null,
          })),
          ...unlaunchedCardBills.map((b) => ({
            id: b.id,
            descricao: `${b.description} (Assinatura Fixa Prevista)`,
            valorTotal: b.amount,
            meuImpacto: b.myImpact,
            categoria: b.category,
            subcategoria: b.subcategory || null,
            vencimento: b.dueDate,
            status: 'projected_recurring',
            parcela: null,
          })),
        ],
      }
    })
    .filter((f) => f.totalBrutoFatura > 0 || Boolean(cartaoFiltro))

  // Detalhamento de contas em aberto e compras de cartão pendentes
  const pendingCreditCardTxs = monthTxs
    .filter((t) => t.type === 'expense' && t.status === 'pending' && impactFor(t, env.me.uid, all) > 0)
    .map((t) => ({
      descricao: t.description,
      status: 'Pendente na Fatura do Cartão',
      vencimento: t.dueDate,
      categoria: t.category,
      subcategoria: t.subcategory || null,
      cartao: t.cardId ? cards.find((c) => c.id === t.cardId)?.cardName : undefined,
      meuImpacto: impactFor(t, env.me.uid, all),
    }))

  const estimatedTxs = monthTxs
    .filter((t) => t.type === 'expense' && t.status === 'estimated' && impactFor(t, env.me.uid, all) > 0)
    .map((t) => ({
      descricao: t.description,
      status: 'Estimado (Previsão Recorrente)',
      vencimento: t.dueDate,
      categoria: t.category,
      meuImpacto: impactFor(t, env.me.uid, all),
    }))

  const unlaunchedFixedItems = (projectedTarget?.items || [])
    .filter((i) => i.isRecurringProjection)
    .map((i) => ({
      descricao: i.description,
      tipo: i.type,
      categoria: i.category,
      subcategoria: i.subcategory || null,
      vencimento: i.dueDate,
      metodoPagamento: i.paymentMethod,
      cartao: i.cardName || null,
      meuImpacto: i.myImpact,
      status: 'Programado (Conta Fixa / Renda Ativa)',
    }))

  const open = monthTxs
    .filter((t) => t.type === 'expense' && t.status !== 'paid' && impactFor(t, env.me.uid, all) > 0)
    .map((t) => ({
      descricao: t.description,
      status: t.status === 'estimated' ? 'Estimado' : 'Pendente (A Pagar na Fatura)',
      vencimento: t.dueDate,
      categoria: t.category,
      meuImpacto: impactFor(t, env.me.uid, all),
    }))
  const reserves = monthTxs
    .filter((t) => t.reserveCardId && t.createdByUid === env.me.uid)
    .map((t) => ({ descricao: t.description, ...getReserveInfo(t, all) }))

  // Separação Canais de Pagamento no mês: Faturas de Cartão vs Débito/Pix/Boleto (Extrato)
  const totalFaturasCartao = projectedTarget ? (projectedTarget.invoiceExpense || 0) : s.committedPending
  const totalContasFixasForaCartao = projectedTarget ? (projectedTarget.fixedBillsExpense || 0) : s.committedEstimated
  const totalAvulsosExtrato = projectedTarget ? (projectedTarget.variableAccountExpense || 0) : 0
  const totalExtratoConta = round2(totalContasFixasForaCartao + totalAvulsosExtrato)

  const effectiveIncome = isFutureMonth && projectedTarget ? projectedTarget.projectedIncome : Math.max(s.income, projectedTarget?.projectedIncome || 0)
  const effectiveExpense = isFutureMonth && projectedTarget ? projectedTarget.projectedExpense : (s.paid + s.committed)
  const effectiveFreeBalance = isFutureMonth && projectedTarget ? projectedTarget.projectedFreeBalance : s.freeBalance
  const effectiveDailyLimit = isFutureMonth && projectedTarget ? projectedTarget.projectedDailyLimit : s.dailyLimit

  // Categorias unificadas (somando itens da fatura + extrato + contas fixas previstas)
  const gastosPorCategoriaDetalhado = projectedTarget?.byCategory || s.byCategory.map((c) => ({
    category: c.category,
    amount: c.amount,
    invoiceAmount: 0,
    accountAmount: c.amount,
    fixedBillAmount: 0,
    count: 1,
  }))

  return {
    response: {
      ok: true,
      mes: monthLabel(targetMonth),
      competenceMonth: targetMonth,
      ehProjecaoFutura: isFutureMonth,
      quantidadeMesesProjetados: count,
      receitas: effectiveIncome,
      jaPago: s.paid,
      comprasCartaoPendentes: s.committedPending,
      contasEstimadas: s.committedEstimated,
      totalComprometido: effectiveExpense,
      saldoLivrePrevisto: effectiveFreeBalance,
      saldoLivreReal: s.freeBalance,
      diasRestantes: s.daysRemaining,
      limiteDiarioSeguro: effectiveDailyLimit,
      resumoCanaisPagamento: {
        totalFaturasCartao: round2(totalFaturasCartao),
        totalExtratoConta: round2(totalExtratoConta),
        contasFixasBoletoPixDebito: round2(totalContasFixasForaCartao),
        gastosAvulsosPixDebito: round2(totalAvulsosExtrato),
      },
      faturasPorCartao,
      resumoFaturaCartao: {
        totalPendenteCartao: round2(totalFaturasCartao),
        quantidadeItensPendentes: pendingCreditCardTxs.length,
        itensPendentesFatura: pendingCreditCardTxs,
      },
      gastosPorCategoria: gastosPorCategoriaDetalhado.map((c) => ({
        category: c.category,
        amount: c.amount,
        noCartaoFatura: c.invoiceAmount,
        noExtratoPixDebito: round2(c.accountAmount + c.fixedBillAmount),
      })),
      gastosPorCategoriaDetalhado,
      gastosPorSubcategoria: s.bySubcategory || [],
      itensDoMes: projectedTarget?.items || [],
      contasFixasProgramadasNaoLancadas: unlaunchedFixedItems,
      projecaoFluxoMeses: projections12.map((p) => ({
        mesKey: p.monthKey,
        mesLabel: p.label,
        entradasPrevistas: p.projectedIncome,
        saidasPrevistas: p.projectedExpense,
        faturasCartao: p.invoiceExpense || 0,
        contasFixasExtrato: p.fixedBillsExpense || 0,
        avulsosPixDebito: p.variableAccountExpense || 0,
        saldoLivreProjetado: p.projectedFreeBalance,
        limiteDiarioSeguro: p.projectedDailyLimit,
        porCategoria: p.byCategory || [],
        quantidadeItens: (p.items || []).length,
      })),
      totalInvestido: s.totalInvested || 0,
      totalInvestidoAcumulado: s.totalInvestedAccumulated || 0,
      investimentosPorClasse: s.byInvestmentSubcategory || [],
      contasEmAberto: open,
      contasEstimadasLista: estimatedTxs,
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
      title:
        count > 1
          ? `Projeção de Fluxo (${count} Meses a partir de ${monthLabel(targetMonth)})`
          : isFutureMonth
            ? `Projeção de ${monthLabel(targetMonth)}`
            : `Resumo de ${monthLabel(targetMonth)}`,
      lines: [
        { label: isFutureMonth ? 'Saldo Livre Previsto' : 'Saldo Livre Real', value: money(effectiveFreeBalance), tone: effectiveFreeBalance >= 0 ? 'positive' : 'negative' },
        { label: 'Receitas Previstas', value: money(effectiveIncome), tone: 'positive' },
        { label: 'Faturas de Cartão', value: money(totalFaturasCartao) },
        { label: 'Extrato (Fixas + Pix/Débito)', value: money(totalExtratoConta) },
        { label: 'Total Comprometido', value: money(effectiveExpense) },
      ],
    },
  }
}

