/**
 * Agente offline (Modo Demo) — interpreta linguagem natural com heurísticas
 * e chama as MESMAS ferramentas do Gemini, permitindo testar o fluxo
 * completo sem API key.
 */
import type { ActionCardData } from '@/types'
import type { ToolExecutor } from './gemini'
import { guessCategory } from './finance'
import { dateInMonth, formatBRL, normalize, todayKey } from './utils'

const THIRD_PARTIES: [RegExp, string][] = [
  [/\bmae\b|\bminha mae\b/, 'Mãe'],
  [/\bpai\b/, 'Pai'],
  [/\birmao\b/, 'Irmão'],
  [/\birma\b/, 'Irmã'],
  [/\bamig[oa]\b/, 'Amigo(a)'],
  [/\bsogr[ao]\b/, 'Sogro(a)'],
  [/\bavo\b|\bavó\b/, 'Avó'],
  [/\btia\b/, 'Tia'],
  [/\btio\b/, 'Tio'],
  [/\bprim[oa]\b/, 'Primo(a)'],
]

const BILL_WORDS = /(luz|energia|agua|internet|financiamento|aluguel|gas|condominio|fatura|boleto|terapia|celular|academia)/

export function parseAmount(text: string): number | null {
  const m =
    text.match(/r\$\s*([\d.]+(?:,\d{1,2})?|\d+(?:\.\d{1,2})?)/i) ??
    text.match(/([\d.]+(?:,\d{1,2})?)\s*(?:reais|conto|pila)/i) ??
    text.match(/\b(?:de|por|valor)\s+([\d.]+(?:,\d{1,2})?)\b/i)
  if (!m) return null
  let raw = m[1]
  if (raw.includes(',')) raw = raw.replace(/\./g, '').replace(',', '.')
  else if (/^\d{1,3}(\.\d{3})+$/.test(raw)) raw = raw.replace(/\./g, '')
  const n = Number(raw)
  return Number.isFinite(n) && n > 0 ? n : null
}

function capitalize(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

function extractDescription(original: string, n: string): string {
  const place = original.match(/\b(?:na|no|em|com)\s+(?:a\s+|o\s+)?([A-Za-zÀ-ÿ][\wÀ-ÿ'’ ]{2,30}?)(?=\s+(?:de|por|no|na|dividid|50|com|r\$|hoje|ontem)|[.,!?]|$)/i)
  const lead = original.match(/^([A-Za-zÀ-ÿ][\wÀ-ÿ ]{2,40}?)\s+(?:de|por)\s+r?\$?\s*\d/i)
  const bad = /cart[aã]o|cr[eé]dito|meu|minha|compartilhado|pix|d[eé]bito/i
  if (lead && !bad.test(lead[1]) && !/^(gastei|paguei|comprei|usei)/i.test(lead[1])) return capitalize(lead[1].trim())
  if (place && !bad.test(place[1])) return capitalize(place[1].trim())
  const cat = guessCategory(n)
  return cat === 'Outros' ? 'Compra' : cat
}

export async function runDemoAgent(
  message: string,
  execute: ToolExecutor,
  monthKey: string,
): Promise<{ text: string; actions: ActionCardData[] }> {
  const n = normalize(message)
  const actions: ActionCardData[] = []
  const amount = parseAmount(message)
  const dayMatch = n.match(/dia\s+(\d{1,2})/)
  const date = dayMatch ? dateInMonth(monthKey, Number(dayMatch[1])) : undefined

  // ---------------- Relatório / economia ----------------
  if (/relatorio|economiz|resumo|analise|onde .*gast|como estou|situacao/.test(n)) {
    const r = await execute('obterRelatorioEProjecaoMensal', {})
    if (r.action) actions.push(r.action)
    const d = r.response as Record<string, any>
    const cats = (d.gastosPorCategoria as { category: string; amount: number }[]) ?? []
    const totalCat = cats.reduce((s, c) => s + c.amount, 0) || 1
    const top = cats[0]
    const lazer = cats.find((c) => c.category === 'Lazer')
    const alim = cats.find((c) => c.category === 'Alimentação')
    const rows = cats
      .map((c) => `| ${c.category} | ${formatBRL(c.amount)} | ${((c.amount / totalCat) * 100).toFixed(0)}% |`)
      .join('\n')
    const tips: string[] = []
    if (top) tips.push(`**${top.category}** concentra ${((top.amount / totalCat) * 100).toFixed(0)}% do seu impacto — é a maior alavanca de economia.`)
    if (alim && alim.amount > 300) tips.push(`Alimentação está em **${formatBRL(alim.amount)}**. Trocar 2 pedidos de delivery por mercado pode liberar ~${formatBRL(alim.amount * 0.15)}.`)
    if (lazer && lazer.amount > 150) tips.push(`Lazer somou **${formatBRL(lazer.amount)}**; definir um teto semanal ajuda a proteger o limite diário.`)
    if (d.aReceberTotal > 0) tips.push(`Você tem **${formatBRL(d.aReceberTotal)}** a receber. Cobrar isso aumenta seu caixa real (não muda o Saldo Livre, que já desconsidera esses valores).`)
    if (d.comprometidoEstimado > 0) tips.push(`Ainda há **${formatBRL(d.comprometidoEstimado)}** só em estimativas — quando as contas reais chegarem, atualize para refinar o saldo.`)

    return {
      text: `### 📊 Relatório de ${d.mes}

| Indicador | Valor |
|---|---|
| Receitas | ${formatBRL(d.receitas)} |
| Já pago | ${formatBRL(d.jaPago)} |
| Comprometido | ${formatBRL(d.comprometido)} |
| **Saldo Livre Real** | **${formatBRL(d.saldoLivreReal)}** |
| Limite diário seguro | ${d.limiteDiarioSeguro != null ? formatBRL(d.limiteDiarioSeguro) : '—'} |

**Onde vai o seu dinheiro**

| Categoria | Meu impacto | % |
|---|---|---|
${rows || '| — | — | — |'}

**💡 Onde economizar**
${tips.map((t) => `- ${t}`).join('\n') || '- Seu mês está equilibrado. Continue assim!'}`,
      actions,
    }
  }

  // ---------------- Planejamento: quanto posso gastar ----------------
  if (/quanto .*(posso|consigo) gastar|fim de semana|fds|sem furar|posso gastar/.test(n)) {
    const r = await execute('obterRelatorioEProjecaoMensal', {})
    if (r.action) actions.push(r.action)
    const d = r.response as Record<string, any>
    const daily = d.limiteDiarioSeguro as number | null
    if (daily == null) return { text: 'Esse mês já foi encerrado — selecione o mês atual para planejar.', actions }
    const days = d.diasRestantes as number
    const free = d.saldoLivreReal as number
    const weekend = /fim de semana|fds|sabado|domingo/.test(n)
    const period = weekend ? 2 : 1
    const safe = Math.max(0, daily * period)
    const stretch = Math.max(0, daily * period * 1.5)
    const after = (spent: number) => (days - period > 0 ? (free - spent) / (days - period) : 0)
    return {
      text: `${free < 0 ? '⚠️ Seu Saldo Livre já está **negativo**. O ideal é segurar gastos variáveis até as receitas pendentes entrarem.\n\n' : ''}Seu **Saldo Livre Real** é **${formatBRL(free)}** para os **${days} dias** restantes → limite seguro de **${formatBRL(daily)}/dia**.

${weekend ? 'Para o **fim de semana (2 dias)**:' : 'Para **hoje**:'}

| Cenário | Gasto | Novo limite diário depois |
|---|---|---|
| 🟢 Seguro | ${formatBRL(safe)} | ${formatBRL(after(safe))} |
| 🟡 Esticado (+50%) | ${formatBRL(stretch)} | ${formatBRL(after(stretch))} |
| 🔴 Tudo livre do mês | ${formatBRL(Math.max(free, 0))} | ${formatBRL(0)} |

👉 Recomendo ficar em até **${formatBRL(safe)}** — assim você não compromete o resto do mês.`,
      actions,
    }
  }

  // ---------------- Atualizar conta prevista ----------------
  const billWord = n.match(BILL_WORDS)?.[1]
  if (billWord && amount && /(veio|chegou|deu|ficou|paguei|pago|quitei|atualiz|valor real)/.test(n)) {
    const paid = /(paguei|pago|quitei|ja paguei)/.test(n)
    const r = await execute('atualizarContaPrevista', {
      buscaDescricao: billWord,
      valorReal: amount,
      novoStatus: paid ? 'paid' : 'pending',
      dataVencimento: date,
    })
    if (r.action) actions.push(r.action)
    const d = r.response as Record<string, any>
    if (!d.ok) return { text: `Não encontrei uma conta em aberto com "${billWord}". Contas em aberto: ${(d.contasEmAberto ?? []).join(', ') || 'nenhuma'}.`, actions }
    const diff = d.variacaoNoMeuSaldoLivre as number
    return {
      text: `Atualizei **${d.conta}** para **${formatBRL(d.valorReal)}** (${d.novoStatus.toLowerCase()}, vence ${d.vencimento.split('-').reverse().slice(0, 2).join('/')}).

${diff === 0 ? 'Veio exatamente o valor estimado — seu Saldo Livre não mudou.' : diff < 0 ? `Veio **${formatBRL(Math.abs(d.diferencaVsEstimado))} acima** da estimativa. Como a conta é dividida conforme a regra, seu Saldo Livre caiu **${formatBRL(Math.abs(diff))}**.` : `Boa! Veio **${formatBRL(Math.abs(d.diferencaVsEstimado))} abaixo** do estimado e seu Saldo Livre subiu **${formatBRL(diff)}**.`}

Novo Saldo Livre: **${formatBRL(d.saldoLivreDepois)}** · Limite diário: **${d.limiteDiarioDepois != null ? formatBRL(d.limiteDiarioDepois) : '—'}**`,
      actions,
    }
  }

  // ---------------- Registrar transação ----------------
  if (amount) {
    let tipoDivisao = 'MEU'
    let nomeTerceiro: string | undefined
    for (const [re, name] of THIRD_PARTIES) {
      if (re.test(n)) {
        tipoDivisao = 'TERCEIRO'
        nomeTerceiro = name
        break
      }
    }
    if (tipoDivisao === 'MEU') {
      if (/50\/50|metade|dividid|rachad|casal/.test(n)) tipoDivisao = 'CASAL_50_50'
      else if (/(parceir|namorad|esposa|marido).*(usou|gastou|comprou)|100% (dela|dele|parceir)/.test(n)) tipoDivisao = 'PARCEIRO'
    }
    const income = /(recebi|salario|freela|entrou|receita|ganhei)/.test(n)
    const method = /cartao|credito/.test(n) ? 'credit_card' : /debito/.test(n) ? 'debit' : /boleto/.test(n) ? 'bill' : tipoDivisao === 'TERCEIRO' ? 'credit_card' : 'pix'
    const cardHint = /cartao (da|do) (parceir|namorad|esposa|marido)/.test(n) ? 'parceira' : undefined
    const descricao = income ? 'Receita extra' : extractDescription(message, n)
    const r = await execute('registrarTransacao', {
      descricao,
      valorTotal: amount,
      categoria: tipoDivisao === 'TERCEIRO' ? 'Cartão Terceiros' : guessCategory(n),
      metodoPagamento: method,
      status: /paguei|pago/.test(n) ? 'paid' : method === 'credit_card' ? 'pending' : 'paid',
      data: date ?? todayKey(),
      tipoDivisao,
      nomeTerceiro,
      tipo: income ? 'income' : 'expense',
      cartao: cardHint,
    })
    if (r.action) actions.push(r.action)
    const d = r.response as Record<string, any>
    if (!d.ok) return { text: `Não consegui registrar: ${d.erro}`, actions }
    const neutral = d.meuImpactoNoSaldoLivre === 0
    return {
      text: `Pronto! Registrei **${d.descricao}** de **${formatBRL(d.valorTotal)}**${d.cartao ? ` no ${d.cartao}` : ''} como _${d.divisao}_.

${neutral ? `É um lançamento **neutro** para você: não desconta nada do seu Saldo Livre${d.aReceberGerado > 0 ? ` e gerou **${formatBRL(d.aReceberGerado)} a receber**` : ''}.` : `Impacto no seu Saldo Livre: **−${formatBRL(d.meuImpactoNoSaldoLivre)}**${d.aReceberGerado > 0 ? ` · **${formatBRL(d.aReceberGerado)}** ficaram em "A Receber"` : ''}.`}

Saldo Livre: **${formatBRL(d.saldoLivreDepois)}** · Limite diário: **${d.limiteDiarioDepois != null ? formatBRL(d.limiteDiarioDepois) : '—'}** (${d.diasRestantes} dias)`,
      actions,
    }
  }

  return {
    text: `Estou no **Modo Demo** (sem chave do Gemini), então entendo comandos como:

- _"A conta de luz veio R$ 245 para o dia 22"_
- _"Jantar de R$ 160 no cartão compartilhado dividido 50/50"_
- _"Minha mãe usou R$ 120 do meu cartão na farmácia"_
- _"Quanto ainda posso gastar no fim de semana?"_
- _"Me dê um relatório de onde posso economizar"_

Configure sua **GEMINI_API_KEY** no ícone 🔑 para conversar livremente.`,
    actions,
  }
}
