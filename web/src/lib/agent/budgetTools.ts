import type { AgentEnv, ToolResult } from './types'
import { computeCategoryBudgets, suggestCategoryBudgets, suggestBudgetRebalance } from '../finance'
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

  if (acao === 'consultar') {
    const report = computeCategoryBudgets(txs, currentBudgets, monthKey, refDate, uid)
    const rebalance = suggestBudgetRebalance(report)

    const itensFormatados = report.items.map((i) => ({
      categoria: i.category,
      tetoMensal: i.limit > 0 ? formatBRL(i.limit) : 'Sem teto definido',
      gastoReal: formatBRL(i.spent),
      saldoRestante:
        i.remaining >= 0
          ? `+${formatBRL(i.remaining)}`
          : `-${formatBRL(Math.abs(i.remaining))} (ESTOURADO)`,
      porcentagemGasta: `${i.percentage}%`,
      ritmoGastoDiario: `${formatBRL(i.dailyPace)}/dia`,
      gastoProjetadoFimDoMes: formatBRL(i.projectedSpent),
      statusRitmo:
        i.burnRateStatus === 'critical'
          ? 'CRÍTICO'
          : i.burnRateStatus === 'fast'
            ? 'ACELERADO'
            : 'NORMAL',
      gastoDiarioRecomendado:
        i.remaining > 0 ? `${formatBRL(i.recommendedDailyRemaining)}/dia` : 'R$ 0,00/dia',
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

    return {
      response: {
        ok: true,
        mensagem:
          limite > 0
            ? `Teto de "${categoria}" definido em ${formatBRL(limite)} para o mês.`
            : `Teto de "${categoria}" removido.`,
        categoria,
        novoTeto: formatBRL(limite),
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
    }
  }

  if (acao === 'sugerir_tetos') {
    const monthlyIncome = profile?.monthlyIncome || 0
    const suggestion = suggestCategoryBudgets(txs, uid, monthKey, monthlyIncome)

    return {
      response: {
        ok: true,
        totalSugerido: formatBRL(suggestion.totalSuggested),
        coberturaRenda: `${suggestion.incomeCoveragePct}% da renda de ${formatBRL(monthlyIncome)}`,
        tetosRecomendados: Object.entries(suggestion.suggestedBudgets).map(([cat, lim]) => ({
          categoria: cat,
          mediaHistorica3Meses: formatBRL(suggestion.categoryAverages[cat] || 0),
          tetoSugeridoComMargem10Pct: formatBRL(lim),
        })),
        orientacao:
          'Para aplicar qualquer desses tetos, você pode pedir ex: "Defina o teto de Alimentação em R$ 1.200".',
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
    const report = computeCategoryBudgets(txs, currentBudgets, monthKey, refDate, uid)
    const envelope = report.items.find((i) => i.category === categoria)

    const tetoAtual = envelope?.limit || 0
    const gastoAtual = envelope?.spent || 0
    const sobraAtual = envelope?.remaining || 0
    const sobraAposCompra = round2(sobraAtual - valorParcela)

    const cabeNoEnvelope = tetoAtual === 0 || sobraAposCompra >= 0
    const deficitProjetado = sobraAposCompra < 0 ? Math.abs(sobraAposCompra) : 0

    return {
      response: {
        ok: true,
        veredito: cabeNoEnvelope
          ? `SIM, cabe no seu orçamento! A parcela de ${formatBRL(valorParcela)}${parcelas > 1 ? ` (em ${parcelas}x)` : ' à vista'} cabe no seu envelope de "${categoria}".`
          : `ATENÇÃO: A compra ultrapassará seu envelope de "${categoria}" em ${formatBRL(deficitProjetado)}.`,
        detalhes: {
          valorTotal: formatBRL(valorCompra),
          parcelas: `${parcelas}x de ${formatBRL(valorParcela)}`,
          categoria,
          tetoDoEnvelope: tetoAtual > 0 ? formatBRL(tetoAtual) : 'Sem teto definido',
          gastoAtual: formatBRL(gastoAtual),
          sobraAtual: formatBRL(sobraAtual),
          sobraAposCompra: formatBRL(sobraAposCompra),
        },
        recomendacaoIA: !cabeNoEnvelope
          ? `Para realizar essa compra sem prejudicar seu Saldo Livre, você pode rebalancear ${formatBRL(deficitProjetado)} de outra categoria com folga ou negociar um desconto.`
          : 'Compra aprovada dentro do seu planejamento orçamentário.',
      },
    }
  }

  return { response: { ok: false, erro: `Ação inválida para envelopes: ${acao}` } }
}
