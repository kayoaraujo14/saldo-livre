import { isVaVrCard } from '@/types'
import type { Card, CardType, Category, EstimationRule, PaymentMethod, RecurringBill } from '@/types'
import { buildCardInvoice } from '../invoices'
import { effectiveAmount, guessCategoryAndSubcategory, impactFor, suggestPaymentMethod } from '../finance'
import { monthLabel, normalize, round2, shiftMonth, uid } from '../utils'
import { money, parseMonthKey } from './common'
import { sanitizeAmount, sanitizeText } from './security'
import type { AgentEnv, ToolResult } from './types'

export async function gerenciarCartoes(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const acao = String(args.acao || 'listar')
  const cards = env.getCards()
  const all = env.getTransactions()
  const targetMonth = parseMonthKey(args.mes) || env.monthKey

  if (acao === 'listar' || acao === 'detalhar_fatura') {
    const filtroNome = typeof args.nome === 'string' ? args.nome.trim() : ''
    const filtroId = typeof args.id === 'string' ? args.id.trim() : ''
    const selectedCards = cards.filter((c) => {
      if (filtroId && c.id === filtroId) return true
      if (filtroNome && normalize(c.cardName).includes(normalize(filtroNome))) return true
      return !filtroId && !filtroNome
    })

    const cartoes = selectedCards.map((c) => {
      const isVale = isVaVrCard(c)
      if (isVale) {
        return {
          id: c.id,
          nome: c.cardName,
          tipo: 'va_vr' as CardType,
          titular: c.ownerName,
          diaRecargaMensal: c.dueDay,
          limiteRecargaPadrao: c.limit ?? null,
          observacao: 'Cartão Vale / Pré-Pago (sem fatura pós-paga; saldo consolidado no módulo VA/VR)',
        }
      }
      const inv = buildCardInvoice(c, targetMonth, all, env.me.uid)
      return {
        id: c.id,
        nome: c.cardName,
        tipo: 'credit' as CardType,
        titular: c.ownerName,
        diaFechamento: c.closingDay,
        diaVencimento: c.dueDay,
        faturaMes: targetMonth,
        statusFatura: inv.status,
        totalFatura: inv.total,
        minhaParte: inv.myShare,
        parteTerceiros: inv.othersShare,
        gastosPorCategoria: inv.byCategory,
        divisaoParticipantes: inv.splitBreakdown,
        itensFatura: inv.items.map((t) => ({
          id: t.id,
          descricao: t.description,
          valor: effectiveAmount(t),
          meuImpacto: round2(impactFor(t, env.me.uid, all)),
          categoria: t.category,
          subcategoria: t.subcategory || null,
          vencimento: t.dueDate,
          status: t.status,
        })),
      }
    })
    return {
      response: { ok: true, mesCompetencia: targetMonth, cartoes },
      action: {
        kind: 'report',
        title: acao === 'detalhar_fatura' ? `Faturas de ${monthLabel(targetMonth)}` : 'Cartões Listados',
        lines: cartoes.map((c) =>
          c.tipo === 'va_vr'
            ? {
                label: `🍽️ ${c.nome} (Vale Pré-Pago • Recarga dia ${c.diaRecargaMensal})`,
                value: c.limiteRecargaPadrao ? `Ref. ${money(c.limiteRecargaPadrao)}` : 'Pré-Pago VA/VR',
              }
            : {
                label: `💳 ${c.nome} (Vence dia ${c.diaVencimento})`,
                value: `${money(c.minhaParte ?? 0)} (Total: ${money(c.totalFatura ?? 0)})`,
              }
        ),
      },
    }
  }

  if (!env.addCard || !env.updateCard || !env.deleteCard) {
    return { response: { ok: false, erro: 'As funções de cartão não estão injetadas neste ambiente.' } }
  }

  if (acao === 'adicionar') {
    const nome = sanitizeText(args.nome || '', 100)
    const rawType = typeof args.tipo === 'string' ? args.tipo.trim().toLowerCase() : typeof args.cardType === 'string' ? args.cardType.trim().toLowerCase() : ''
    const isVale = rawType === 'va_vr' || rawType === 'vale' || rawType === 'pre_pago' || suggestPaymentMethod(nome) === 'va_vr'
    const cardType: CardType = isVale ? 'va_vr' : 'credit'
    const f = isVale ? 1 : Math.min(31, Math.max(1, Math.floor(Number(args.fechamento) || 1)))
    const v = Math.min(31, Math.max(1, Math.floor(Number(args.vencimento) || (isVale ? 1 : 10))))
    const limitVal = typeof args.limite === 'number' && args.limite > 0 ? args.limite : undefined
    if (!nome) return { response: { ok: false, erro: 'Nome é obrigatório para adicionar' } }

    const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
    if (isPendingConfirmation) {
      const title = isVale ? `Adicionar Cartão Vale (VA/VR): ${nome}` : `Adicionar Cartão: ${nome}`
      const confirmationLines = isVale
        ? [
            { label: 'Nome do Vale', value: nome },
            { label: 'Tipo', value: 'Vale / Pré-Pago (VA/VR)' },
            { label: 'Dia da Recarga', value: `Todo dia ${v}` },
          ]
        : [
            { label: 'Nome do Cartão', value: nome },
            { label: 'Tipo', value: 'Cartão de Crédito' },
            { label: 'Dia de Fechamento', value: `Dia ${f}` },
            { label: 'Dia de Vencimento', value: `Dia ${v}` },
          ]
      return {
        response: {
          ok: true,
          aguardandoConfirmacao: true,
          mensagem: isVale
            ? `Entendi que você deseja adicionar o cartão Vale (Pré-Pago) "${nome}" (recarga dia ${v}). Por favor, confirme no botão abaixo.`
            : `Entendi que você deseja adicionar o cartão "${nome}" (fecha dia ${f}, vence dia ${v}). Por favor, confirme no botão abaixo.`,
        },
        action: {
          kind: 'confirmation',
          title: `Confirmar ${title}`,
          lines: confirmationLines,
          pendingAction: {
            id: uid('act'),
            toolName: 'gerenciarCartoes',
            args: { ...args, tipo: cardType, __confirmed: true },
            title,
            status: 'pending',
            createdAt: Date.now(),
            lines: confirmationLines,
          },
        },
      }
    }

    const id = await env.addCard({
      cardName: nome,
      cardType,
      closingDay: f,
      dueDay: v,
      ...(limitVal !== undefined ? { limit: limitVal } : {}),
      ownerName: env.me.name,
      ownerUid: env.me.uid,
      sharedWithUids: [],
      sharedWithUsers: [],
      frequentThirdParties: [],
    })
    return {
      response: { ok: true, id, tipo: cardType },
      action: { kind: 'created', title: isVale ? 'Cartão Vale Adicionado' : 'Cartão Adicionado', lines: [{ label: 'Cartão', value: nome }] },
    }
  }

  const id = String(args.id || '')
  if (!id) return { response: { ok: false, erro: 'ID obrigatório para edição ou exclusão.' } }

  if (acao === 'editar') {
    const patch: Partial<Card> = {}
    if (args.nome) patch.cardName = String(args.nome)
    if (args.fechamento) patch.closingDay = Number(args.fechamento)
    if (args.vencimento) patch.dueDay = Number(args.vencimento)
    if (args.tipo === 'va_vr' || args.tipo === 'credit' || args.cardType === 'va_vr' || args.cardType === 'credit') {
      patch.cardType = (args.tipo || args.cardType) as CardType
    }
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

export async function gerenciarContasRecorrentes(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const acao = String(args.acao || 'listar')
  const cards = env.getCards()
  if (acao === 'listar') {
    const contas = (env.getRecurring?.() || []).map((c) => {
      const cardObj = c.cardId ? cards.find((x) => x.id === c.cardId) : null
      return {
        id: c.id,
        descricao: c.description,
        valorBase: c.defaultAmount,
        diaVencimento: c.dueDay,
        ativo: c.active,
        tipo: c.type || 'expense',
        categoria: c.category,
        subcategoria: c.subcategory || null,
        metodoPagamento: c.paymentMethod || (cardObj?.cardType === 'va_vr' ? 'va_vr' : c.cardId ? 'credit_card' : 'bill'),
        cartao: cardObj?.cardName || null,
        regraEstimativa: c.estimationRule || 'fixed',
        mesInicio: c.startMonth || null,
        mesFim: c.endMonth || null,
        parcelas: c.installmentsCount || null,
      }
    })
    return {
      response: { ok: true, contas },
      action: {
        kind: 'report',
        title: 'Contas Fixas e Rendas Recorrentes',
        lines: [{ label: 'Contas Encontradas', value: contas.length.toString() }],
      },
    }
  }

  if (!env.addRecurring || !env.updateRecurring || !env.deleteRecurring) {
    return { response: { ok: false, erro: 'As funções de conta recorrente não estão disponíveis.' } }
  }

  if (acao === 'adicionar') {
    const desc = sanitizeText(args.descricao || '', 200)
    const sanitizedValor = sanitizeAmount(args.valor, { min: 0.01 })
    if (!desc || sanitizedValor === null || sanitizedValor <= 0) {
      return { response: { ok: false, erro: 'Descrição e valor positivo são obrigatórios.' } }
    }
    const valor = sanitizedValor
    const bType: 'expense' | 'income' = args.tipo === 'income' ? 'income' : 'expense'
    const guessed = guessCategoryAndSubcategory(desc, valor, bType)
    const suggestedPm = suggestPaymentMethod(desc)
    const cartaoStr = typeof args.cartao === 'string' ? args.cartao.trim() : ''
    let matchedCard = cartaoStr
      ? cards.find((c) => normalize(c.cardName).includes(normalize(cartaoStr)) || c.id === cartaoStr)
      : undefined
    const explicitPm = typeof args.metodoPagamento === 'string' ? (args.metodoPagamento as PaymentMethod) : undefined
    const isValeReq = explicitPm === 'va_vr' || (matchedCard ? isVaVrCard(matchedCard) : suggestedPm === 'va_vr')
    if (isValeReq && !matchedCard) {
      const valeCards = cards.filter((c) => isVaVrCard(c))
      if (valeCards.length === 1) matchedCard = valeCards[0]
    }
    const pm: PaymentMethod = isValeReq
      ? 'va_vr'
      : explicitPm || (matchedCard ? 'credit_card' : bType === 'income' ? 'pix' : 'bill')
    const cat = (args.categoria as Category) || (bType === 'income' ? (pm === 'va_vr' ? 'Benefícios' : 'Salário & Remuneração') : guessed.category || 'Outros')
    const subcat =
      typeof args.subcategoria === 'string' && args.subcategoria.trim()
        ? args.subcategoria.trim()
        : cat === guessed.category
          ? guessed.subcategory
          : null
    const dueDay = Number(args.diaVencimento || matchedCard?.dueDay || 10)
    const rule: EstimationRule = (args.regraEstimativa as EstimationRule) || 'fixed'
    const mesesLimite = typeof args.mesesLimite === 'number' && args.mesesLimite > 0 ? Math.round(args.mesesLimite) : undefined
    const startM = parseMonthKey(args.mesInicio) || (mesesLimite ? env.monthKey : undefined)
    const endM = mesesLimite && startM ? shiftMonth(startM, mesesLimite - 1) : undefined

    const isPendingConfirmation = args.__confirmed !== true && env.requireConfirmation !== false
    if (isPendingConfirmation) {
      const title = `${bType === 'income' ? 'Adicionar Renda Fixa' : 'Adicionar Conta Fixa'}: ${desc}`
      const confirmationLines = [
        { label: 'Descrição', value: desc },
        { label: 'Valor Mensal', value: money(valor) },
        { label: 'Dia', value: `Todo dia ${dueDay}` },
        { label: 'Categoria', value: subcat ? `${cat} › ${subcat}` : cat },
        { label: 'Canal', value: matchedCard ? (pm === 'va_vr' ? `Vale ${matchedCard.cardName}` : `Cartão ${matchedCard.cardName}`) : pm === 'va_vr' ? 'VALE (VA/VR)' : pm.toUpperCase() },
        ...(endM ? [{ label: 'Vigência', value: `${mesesLimite} meses (até ${monthLabel(endM)})` }] : []),
      ]
      return {
        response: {
          ok: true,
          aguardandoConfirmacao: true,
          mensagem: `Entendi que você deseja cadastrar "${desc}" no valor de ${money(valor)} (dia ${dueDay}). Por favor, confirme no botão abaixo.`,
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
      estimationRule: rule,
      type: bType,
      paymentMethod: pm,
      cardId: (pm === 'credit_card' || pm === 'va_vr') ? matchedCard?.id || null : null,
      startMonth: startM,
      endMonth: endM,
      installmentsCount: mesesLimite,
    })
    return {
      response: { ok: true, id },
      action: { kind: 'created', title: 'Conta/Renda Recorrente Criada', lines: [{ label: 'Descrição', value: desc }] },
    }
  }

  // Localiza por ID ou por descrição
  const rawId = String(args.id || '').trim()
  const rawDesc = String(args.descricao || '').trim()
  const allBills = env.getRecurring?.() || []
  const targetBill =
    allBills.find((b) => b.id === rawId) ||
    (rawDesc ? allBills.find((b) => normalize(b.description).includes(normalize(rawDesc))) : undefined)

  const id = targetBill?.id || rawId
  if (!id) return { response: { ok: false, erro: 'ID ou descrição da conta recorrente é obrigatório.' } }

  if (acao === 'editar') {
    const patch: Partial<RecurringBill> = {}
    if (typeof args.descricao === 'string' && args.descricao.trim() && targetBill && args.id) {
      patch.description = args.descricao.trim()
    }
    if (typeof args.valor === 'number' && args.valor > 0) patch.defaultAmount = args.valor
    if (typeof args.diaVencimento === 'number' && args.diaVencimento >= 1 && args.diaVencimento <= 31) {
      patch.dueDay = Math.round(args.diaVencimento)
    }
    if (typeof args.categoria === 'string' && args.categoria.trim()) patch.category = args.categoria as Category
    if (typeof args.subcategoria === 'string') patch.subcategory = args.subcategoria.trim() || null
    if (args.tipo === 'income' || args.tipo === 'expense') patch.type = args.tipo
    if (typeof args.metodoPagamento === 'string') patch.paymentMethod = args.metodoPagamento as PaymentMethod
    if (typeof args.regraEstimativa === 'string') patch.estimationRule = args.regraEstimativa as EstimationRule
    if (typeof args.cartao === 'string' && args.cartao.trim()) {
      const mc = cards.find((c) => normalize(c.cardName).includes(normalize(String(args.cartao))) || c.id === args.cartao)
      if (mc) {
        patch.cardId = mc.id
        patch.paymentMethod = isVaVrCard(mc) ? 'va_vr' : 'credit_card'
      }
    }
    if (typeof args.mesesLimite === 'number' && args.mesesLimite > 0) {
      const mLim = Math.round(args.mesesLimite)
      const sM = parseMonthKey(args.mesInicio) || targetBill?.startMonth || env.monthKey
      patch.startMonth = sM
      patch.endMonth = shiftMonth(sM, mLim - 1)
      patch.installmentsCount = mLim
    }
    await env.updateRecurring(id, patch)
    return {
      response: { ok: true, id, atualizado: patch },
      action: {
        kind: 'updated',
        title: 'Conta Fixa Atualizada',
        lines: [{ label: 'Conta', value: targetBill?.description || id }],
      },
    }
  }

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

export async function gerenciarAmizades(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const acao = String(args.acao || 'listar')
  if (acao === 'listar') {
    const amigos = (env.getFriends?.() || []).map((f) => ({ id: f.uid, nome: f.name, email: f.email }))
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

