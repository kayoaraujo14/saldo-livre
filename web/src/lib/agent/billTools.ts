import type { Transaction, TransactionStatus } from '@/types'
import {
  amountPatch,
  canUserEditTransaction,
  computeSummary,
  effectiveAmount,
} from '../finance'
import { formatShortDate, monthLabel, round2, uid } from '../utils'
import { matchesSearch, money } from './common'
import type { AgentEnv, ToolResult } from './types'

export async function atualizar(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
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
  if (!canUserEditTransaction(tx, env.me.uid)) {
    return {
      response: {
        ok: false,
        erro: `Não foi possível atualizar: a conta "${tx.description}" foi criada por outra pessoa. Apenas quem lançou pode alterá-la.`,
      },
    }
  }
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

