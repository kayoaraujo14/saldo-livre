import type { AgentEnv, ToolResult } from './types'
import {
  computeCategoryBudgets,
  suggestCategoryBudgets,
  suggestBudgetRebalance,
  computeSummary,
  calculateFinancialHealthScore,
  resolveEmergencyReserveBalance,
} from '../finance'
import { todayKey, formatBRL, round2 } from '../utils'
import { normalizeCategory } from '@/types'

export async function gerenciarEnvelopesOrcamentarios(
  env: AgentEnv,
  args: Record<string, unknown>,
): Promise<ToolResult> {
  const acao = String(args.acao || 'consultar').trim()
  const profile = env.getProfile ? env.getProfile() : null
  const currentBudgets = { ...(profile?.categoryBudgets || {}) }
  const txs = env.getTransactions()
  const uid = env.me.uid
  const monthKey = env.monthKey
  const refDate = todayKey()
  const rawMonthSummary = computeSummary(txs, uid, monthKey, [], env.getCards ? env.getCards() : [])
  const expectedMonthIncome =
    rawMonthSummary.income > 0 ? rawMonthSummary.income : (profile?.monthlyIncome || 0)

  if (acao === 'consultar') {
    const report = computeCategoryBudgets(txs, currentBudgets, monthKey, refDate, uid, txs, expectedMonthIncome)
    const rebalance = suggestBudgetRebalance(report)

    const itensFormatados = report.items.map((i) => ({
      categoria: i.category,
      tipoEnvelope: i.isFixedCategory ? 'Fixo Mensal' : 'Variável (Ritmo Diário)',
      tetoMensal: i.limit > 0 ? formatBRL(i.limit) : 'Sem teto definido',
      gastoReal: formatBRL(i.spent),
      saldoRestante:
        i.remaining >= 0
          ? `+${formatBRL(i.remaining)}`
          : `-${formatBRL(Math.abs(i.remaining))} (ESTOURADO)`,
      porcentagemGasta: `${i.percentage}%`,
      ritmoGastoDiario: i.isFixedCategory ? 'Custo fixo mensal' : `${formatBRL(i.dailyPace)}/dia`,
      gastoProjetadoFimDoMes: formatBRL(i.projectedSpent),
      statusRitmo:
        i.burnRateStatus === 'critical'
          ? 'CRÍTICO'
          : i.burnRateStatus === 'fast'
            ? 'ACELERADO'
            : 'NORMAL',
      gastoDiarioRecomendado:
        i.isFixedCategory
          ? 'Parcela fixa'
          : i.remaining > 0
            ? `${formatBRL(i.recommendedDailyRemaining)}/dia`
            : 'R$ 0,00/dia',
    }))

    return {
      response: {
        ok: true,
        mes: monthKey,
        resumoGeral: {
          totalOrcado: formatBRL(report.totalBudgeted),
          totalGasto: formatBRL(report.totalSpent),
          saldoLivreOrcado: formatBRL(report.totalRemaining),
          percentualConsumidoGeral: `${report.overallPercentage}%`,
          statusGeral:
            report.status === 'danger'
              ? 'ATENÇÃO (Déficit em categorias)'
              : report.status === 'warning'
                ? 'ALERTA (Ritmo elevado)'
                : 'SAUDÁVEL (Dentro da meta)',
        },
        envelopes: itensFormatados,
        rebalanceamentoPossivel: rebalance.canRebalance,
        sugestoesRebalanceamento: rebalance.suggestions.map((s) => ({
          tirarDe: s.compensateFrom,
          disponivel: formatBRL(s.availableSurplus),
          transferirPara: s.overspentCategory,
          deficit: formatBRL(s.overspentAmount),
          valorSugerido: formatBRL(s.suggestedTransfer),
        })),
      },
      action: {
        kind: 'report',
        title: `Envelopes Orçamentários (${monthKey})`,
        lines: [
          { label: 'Total Orçado', value: formatBRL(report.totalBudgeted) },
          { label: 'Consumido', value: `${formatBRL(report.totalSpent)} (${report.overallPercentage}%)` },
          {
            label: 'Sobra nos Envelopes',
            value: formatBRL(report.totalRemaining),
            tone: report.totalRemaining >= 0 ? 'positive' : 'negative',
          },
          {
            label: 'Envelopes Configurados',
            value: `${report.items.filter((i) => i.limit > 0).length} categorias`,
          },
        ],
      },
    }
  }

  if (acao === 'definir_teto') {
    const catRaw = String(args.categoria || '').trim()
    const categoria = normalizeCategory(catRaw)
    const limite = round2(Number(args.limite) || 0)

    if (!catRaw || limite < 0) {
      return {
        response: {
          ok: false,
          erro: 'Informe o nome da categoria e um valor de teto válido (positivo).',
        },
      }
    }

    if (limite === 0) {
      delete currentBudgets[categoria]
    } else {
      currentBudgets[categoria] = limite
    }

    if (env.updateProfile) {
      await env.updateProfile({ categoryBudgets: currentBudgets })
    }

    const updatedReport = computeCategoryBudgets(txs, currentBudgets, monthKey, refDate, uid, txs, expectedMonthIncome)
    const item = updatedReport.items.find((i) => i.category === categoria)

    return {
      response: {
        ok: true,
        mensagem:
          limite > 0
            ? `Teto de "${categoria}" definido em ${formatBRL(limite)} para o mês.`
            : `Teto de "${categoria}" removido.`,
        categoria,
        novoTeto: formatBRL(limite),
        gastoAtual: formatBRL(item?.spent || 0),
        saldoRestante: formatBRL(item?.remaining || 0),
      },
      action: {
        kind: 'updated',
        title: limite > 0 ? `Envelope Atualizado: ${categoria}` : `Envelope Removido: ${categoria}`,
        lines: [
          { label: 'Categoria', value: categoria },
          { label: 'Novo Teto Mensal', value: limite > 0 ? formatBRL(limite) : 'Removido', tone: 'positive' },
          ...(item
            ? [
                { label: 'Gasto Atual no Mês', value: formatBRL(item.spent) },
                {
                  label: 'Saldo no Envelope',
                  value: formatBRL(item.remaining),
                  tone: (item.remaining >= 0 ? 'positive' : 'negative') as 'positive' | 'negative',
                },
              ]
            : []),
        ],
      },
    }
  }

  if (acao === 'rebalancear') {
    const fromRaw = String(args.categoriaOrigem || '').trim()
    const toRaw = String(args.categoriaDestino || '').trim()
    const categoriaOrigem = normalizeCategory(fromRaw)
    const categoriaDestino = normalizeCategory(toRaw)
    const valor = round2(Number(args.valorTransferencia) || 0)

    if (!fromRaw || !toRaw || valor <= 0) {
      return {
        response: {
          ok: false,
          erro: 'Informe categoriaOrigem, categoriaDestino e um valorTransferencia positivo.',
        },
      }
    }

    const fromCurrent = currentBudgets[categoriaOrigem] || 0
    const toCurrent = currentBudgets[categoriaDestino] || 0

    const newFrom = Math.max(0, round2(fromCurrent - valor))
    const newTo = round2(toCurrent + valor)

    currentBudgets[categoriaOrigem] = newFrom
    currentBudgets[categoriaDestino] = newTo

    if (env.updateProfile) {
      await env.updateProfile({ categoryBudgets: currentBudgets })
    }

    return {
      response: {
        ok: true,
        mensagem: `Rebalanceamento concluído: ${formatBRL(valor)} transferidos de "${categoriaOrigem}" (${formatBRL(fromCurrent)} ➔ ${formatBRL(newFrom)}) para "${categoriaDestino}" (${formatBRL(toCurrent)} ➔ ${formatBRL(newTo)}).`,
        categoriaOrigem: { nome: categoriaOrigem, novoTeto: formatBRL(newFrom) },
        categoriaDestino: { nome: categoriaDestino, novoTeto: formatBRL(newTo) },
        valorTransferido: formatBRL(valor),
      },
      action: {
        kind: 'updated',
        title: 'Rebalanceamento de Envelopes',
        lines: [
          { label: 'Valor Transferido', value: formatBRL(valor), tone: 'positive' },
          { label: `De: ${categoriaOrigem}`, value: `${formatBRL(fromCurrent)} ➔ ${formatBRL(newFrom)}` },
          { label: `Para: ${categoriaDestino}`, value: `${formatBRL(toCurrent)} ➔ ${formatBRL(newTo)}` },
        ],
      },
    }
  }

  if (acao === 'rebalancear_automatico') {
    const report = computeCategoryBudgets(txs, currentBudgets, monthKey, refDate, uid, txs, expectedMonthIncome)
    const rebalance = suggestBudgetRebalance(report)

    if (!rebalance.canRebalance || rebalance.suggestions.length === 0) {
      return {
        response: {
          ok: false,
          mensagem:
            'Não há categorias estouradas com folga disponível em outros envelopes para rebalanceamento automático neste momento.',
        },
      }
    }

    const transfers: Array<{ de: string; para: string; valor: string }> = []
    for (const s of rebalance.suggestions) {
      const fromCur = currentBudgets[s.compensateFrom] || 0
      const toCur = currentBudgets[s.overspentCategory] || 0
      currentBudgets[s.compensateFrom] = Math.max(0, round2(fromCur - s.suggestedTransfer))
      currentBudgets[s.overspentCategory] = round2(toCur + s.suggestedTransfer)
      transfers.push({
        de: s.compensateFrom,
        para: s.overspentCategory,
        valor: formatBRL(s.suggestedTransfer),
      })
    }

    if (env.updateProfile) {
      await env.updateProfile({ categoryBudgets: currentBudgets })
    }

    return {
      response: {
        ok: true,
        mensagem: `Rebalanceamento inteligente aplicado! ${transfers.length} transferência(s) realizada(s) para cobrir os envelopes estourados sem alterar seu orçamento total.`,
        transferenciasRealizadas: transfers,
      },
      action: {
        kind: 'updated',
        title: 'Rebalanceamento Inteligente IA Aplicado',
        lines: transfers.map((t) => ({
          label: `${t.de} ➔ ${t.para}`,
          value: t.valor,
          tone: 'positive' as const,
        })),
      },
    }
  }

  if (acao === 'sugerir_tetos') {
    const monthlyIncome = expectedMonthIncome
    const suggestion = suggestCategoryBudgets(txs, uid, monthKey, monthlyIncome)

    return {
      response: {
        ok: true,
        entradaPrevistaDoMes: formatBRL(monthlyIncome),
        totalSugerido: formatBRL(suggestion.totalSuggested),
        coberturaRenda: `${suggestion.incomeCoveragePct}% da entrada prevista de ${formatBRL(monthlyIncome)}`,
        tetosRecomendados: Object.entries(suggestion.suggestedBudgets).map(([cat, lim]) => ({
          categoria: cat,
          mediaHistorica3Meses: formatBRL(suggestion.categoryAverages[cat] || 0),
          tetoSugerido: formatBRL(lim),
        })),
        orientacao:
          'Para aplicar todos esses tetos de uma só vez, chame acao="aplicar_sugestao_ia" ou peça "Aplicar tetos sugeridos pela IA".',
      },
      action: {
        kind: 'report',
        title: 'Sugestão de Envelopes IA (Calibrado à Entrada)',
        lines: [
          { label: 'Entrada Prevista do Mês', value: formatBRL(monthlyIncome) },
          { label: 'Total Orçado Sugerido', value: formatBRL(suggestion.totalSuggested) },
          { label: 'Alocação da Entrada', value: `${suggestion.incomeCoveragePct}%` },
          ...Object.entries(suggestion.suggestedBudgets)
            .slice(0, 4)
            .map(([cat, lim]) => ({
              label: cat,
              value: formatBRL(lim),
            })),
        ],
      },
    }
  }

  if (acao === 'aplicar_sugestao_ia') {
    const monthlyIncome = expectedMonthIncome
    const suggestion = suggestCategoryBudgets(txs, uid, monthKey, monthlyIncome)
    const entries = Object.entries(suggestion.suggestedBudgets)

    if (entries.length === 0) {
      return {
        response: {
          ok: false,
          erro: 'Ainda não há histórico de despesas suficiente para sugerir tetos automaticamente.',
        },
      }
    }

    const calibratedBudgets: Record<string, number> = {}
    for (const [cat, lim] of entries) {
      calibratedBudgets[cat] = lim
    }

    if (env.updateProfile) {
      await env.updateProfile({ categoryBudgets: calibratedBudgets })
    }

    return {
      response: {
        ok: true,
        mensagem: `Apliquei os tetos inteligentes sugeridos pela IA para ${entries.length} categorias, totalizando ${formatBRL(suggestion.totalSuggested)} (${suggestion.incomeCoveragePct}% da entrada prevista do mês de ${formatBRL(monthlyIncome)}).`,
        tetosAplicados: entries.map(([cat, lim]) => ({ categoria: cat, teto: formatBRL(lim) })),
      },
      action: {
        kind: 'updated',
        title: `Envelopes IA Configurados (${entries.length} categorias)`,
        lines: [
          { label: 'Entrada Prevista do Mês', value: formatBRL(monthlyIncome) },
          { label: 'Orçamento Total', value: formatBRL(suggestion.totalSuggested), tone: 'positive' },
          { label: 'Alocação da Entrada', value: `${suggestion.incomeCoveragePct}%` },
          ...entries.slice(0, 4).map(([cat, lim]) => ({
            label: cat,
            value: formatBRL(lim),
          })),
        ],
      },
    }
  }

  if (acao === 'simular_compra') {
    const valorCompra = round2(Number(args.valorCompra) || 0)
    const parcelas = Math.max(1, parseInt(String(args.parcelas || 1), 10))
    const catRaw = String(args.categoria || 'Compras & Estilo de Vida').trim()
    const categoria = normalizeCategory(catRaw)

    if (valorCompra <= 0) {
      return { response: { ok: false, erro: 'Informe um valorCompra maior que zero.' } }
    }

    const valorParcela = round2(valorCompra / parcelas)
    const report = computeCategoryBudgets(txs, currentBudgets, monthKey, refDate, uid, txs, expectedMonthIncome)
    const envelope = report.items.find((i) => i.category === categoria)

    const tetoAtual = envelope?.limit || 0
    const gastoAtual = envelope?.spent || 0
    const sobraAtual = envelope?.remaining || 0

    const rawSummary = rawMonthSummary
    const effectiveFreeBalance =
      rawSummary.income > 0
        ? rawSummary.freeBalance
        : (profile?.monthlyIncome || 0) > 0
          ? round2((profile?.monthlyIncome || 0) - rawSummary.paid - rawSummary.committed)
          : sobraAtual

    const sobraAposCompra = round2(sobraAtual - valorParcela)
    const saldoLivreAposCompra = round2(effectiveFreeBalance - valorParcela)

    const cabeNoEnvelope = tetoAtual === 0 || sobraAposCompra >= 0
    const cabeNoSaldoLivre = saldoLivreAposCompra >= 0 || cabeNoEnvelope
    const aprovada = cabeNoEnvelope
    const deficitProjetado = sobraAposCompra < 0 ? Math.abs(sobraAposCompra) : 0

    return {
      response: {
        ok: true,
        veredito: aprovada
          ? `SIM, cabe no seu orçamento! A parcela de ${formatBRL(valorParcela)}${parcelas > 1 ? ` (em ${parcelas}x)` : ' à vista'} cabe no seu envelope de "${categoria}" e preserva seu Saldo Livre positivo (${formatBRL(saldoLivreAposCompra)}).`
          : !cabeNoEnvelope
            ? `ATENÇÃO: A compra ultrapassará seu envelope de "${categoria}" em ${formatBRL(deficitProjetado)}.`
            : `ATENÇÃO: Embora a categoria tenha espaço, a parcela de ${formatBRL(valorParcela)} deixará seu Saldo Livre do mês negativo em ${formatBRL(saldoLivreAposCompra)}.`,
        detalhes: {
          valorTotal: formatBRL(valorCompra),
          parcelas: `${parcelas}x de ${formatBRL(valorParcela)}`,
          categoria,
          tetoDoEnvelope: tetoAtual > 0 ? formatBRL(tetoAtual) : 'Sem teto definido',
          gastoAtual: formatBRL(gastoAtual),
          sobraAtual: formatBRL(sobraAtual),
          sobraAposCompra: tetoAtual > 0 ? formatBRL(sobraAposCompra) : 'Sem teto',
          saldoLivreAtual: formatBRL(effectiveFreeBalance),
          saldoLivreAposCompra: formatBRL(saldoLivreAposCompra),
        },
        recomendacaoIA: !cabeNoEnvelope
          ? `Para realizar essa compra sem prejudicar seu Saldo Livre, você pode rebalancear ${formatBRL(deficitProjetado)} de outra categoria com folga ou parcelar em mais vezes.`
          : 'Compra aprovada dentro do seu planejamento orçamentário.',
      },
      action: {
        kind: 'report',
        title: aprovada ? 'Simulação de Compra: Aprovada ✅' : 'Simulação de Compra: Alerta ⚠️',
        lines: [
          { label: 'Compra / Parcela', value: `${ parcelInfo(parcelas, valorParcela, valorCompra) }` },
          { label: 'Envelope', value: `${categoria} (${tetoAtual > 0 ? formatBRL(tetoAtual) : 'Sem teto'})` },
          {
            label: 'Sobra no Envelope Após',
            value: tetoAtual > 0 ? formatBRL(sobraAposCompra) : 'Livre',
            tone: cabeNoEnvelope ? 'positive' : 'negative',
          },
          {
            label: 'Saldo Livre Após',
            value: formatBRL(saldoLivreAposCompra),
            tone: cabeNoSaldoLivre ? 'positive' : 'negative',
          },
        ],
      },
    }
  }

  if (acao === 'diagnostico_saude_financeira') {
    const rawSummary = rawMonthSummary
    const summary =
      rawSummary.income === 0 && (profile?.monthlyIncome || 0) > 0
        ? {
            ...rawSummary,
            income: profile!.monthlyIncome!,
            freeBalance: round2(profile!.monthlyIncome! - rawSummary.paid - rawSummary.committed),
          }
        : rawSummary
    const report = computeCategoryBudgets(txs, currentBudgets, monthKey, refDate, uid, txs, summary.income)
    const rebalance = suggestBudgetRebalance(report)
    const reserveBalance = resolveEmergencyReserveBalance(profile, summary)
    const health = calculateFinancialHealthScore(summary, reserveBalance, report)

    return {
      response: {
        ok: true,
        mes: monthKey,
        scoreGeral: `${health.score}/100`,
        classificacao: health.label,
        descricao: health.description,
        pilares: {
          poupancaEInvestimentos: {
            nota: `${health.savingsScore}/30`,
            peso: '30%',
            taxaInvestidaReal: `${health.savingsRatePercentage}% da renda (${formatBRL(summary.totalInvested || 0)})`,
            taxaPoupancaPotencialComSaldoLivre: `${health.potentialSavingsRatePercentage ?? health.savingsRatePercentage}% da renda`,
          },
          controleCustoDeVida: {
            nota: `${health.spendingLimitScore}/30`,
            peso: '30%',
            custoDeVidaReal: `${formatBRL(health.monthlyLivingCost ?? summary.committed)} (${health.spendingRatioPercentage}% da renda, descontados aportes)`,
            metaRecomendada: 'Até 70% da renda líquida',
          },
          reservaDeEmergencia: {
            nota: `${health.emergencyReserveScore}/25`,
            peso: '25%',
            saldoReservaAtual: formatBRL(reserveBalance),
            coberturaEmMeses: `${health.emergencyCoverageMonths} meses do seu custo de vida`,
            metaIdeal6Meses: formatBRL(round2((health.monthlyLivingCost ?? summary.committed) * 6)),
          },
          disciplinaNosEnvelopes: {
            nota: `${health.envelopeScore}/15`,
            peso: '15%',
            aderencia: `${health.budgetAdherenceScore ?? 100}%`,
            envelopesConfigurados: health.budgetConfiguredCount ?? 0,
            envelopesEstourados: health.budgetOverspentCount ?? 0,
          },
        },
        dicasPraticasParaSubirScore: health.actionableTips || [],
        rebalanceamentoDisponivel: rebalance.canRebalance,
        sugestoesRebalanceamento: rebalance.suggestions,
      },
      action: {
        kind: 'report',
        title: `Saúde Financeira: ${health.score}/100 (${health.label})`,
        lines: [
          {
            label: 'Pilar Poupança (30 pts)',
            value: `${health.savingsScore}/30 (${health.savingsRatePercentage}% inv.)`,
            tone: health.savingsScore >= 20 ? 'positive' : health.savingsScore < 10 ? 'negative' : 'neutral',
          },
          {
            label: 'Pilar Custo de Vida (30 pts)',
            value: `${health.spendingLimitScore}/30 (${health.spendingRatioPercentage}% renda)`,
            tone: health.spendingLimitScore >= 20 ? 'positive' : health.spendingLimitScore < 10 ? 'negative' : 'neutral',
          },
          {
            label: 'Pilar Reserva (25 pts)',
            value: `${health.emergencyReserveScore}/25 (${health.emergencyCoverageMonths}m)`,
            tone: health.emergencyReserveScore >= 18 ? 'positive' : health.emergencyReserveScore < 10 ? 'negative' : 'neutral',
          },
          {
            label: 'Pilar Envelopes (15 pts)',
            value: `${health.envelopeScore}/15 (${health.budgetAdherenceScore ?? 100}% ad.)`,
            tone: health.envelopeScore >= 10 ? 'positive' : health.envelopeScore < 5 ? 'negative' : 'neutral',
          },
          {
            label: 'Aderência Envelopes',
            value:
              (health.budgetConfiguredCount ?? 0) > 0
                ? `${health.budgetAdherenceScore ?? 100}% (${health.budgetOverspentCount ?? 0} estouros)`
                : 'Não configurados',
            tone: (health.budgetOverspentCount ?? 0) === 0 ? 'positive' : 'negative',
          },
        ],
      },
    }
  }

  return { response: { ok: false, erro: `Ação inválida para envelopes: ${acao}` } }
}

function parcelInfo(parcelas: number, valorParcela: number, valorTotal: number): string {
  if (parcelas <= 1) return `${formatBRL(valorTotal)} à vista`
  return `${parcelas}x de ${formatBRL(valorParcela)} (${formatBRL(valorTotal)})`
}
