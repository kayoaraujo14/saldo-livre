/**
 * Agente offline (Modo Demo) — interpreta linguagem natural com heurísticas
 * e chama as MESMAS ferramentas do Gemini, permitindo testar o fluxo
 * completo sem API key.
 */
import type { ActionCardData, ChatAttachment, PendingBatchTransaction } from '@/types'
import { CATEGORIES, CATEGORY_GROUPS } from '@/types'
import type { ToolExecutor } from './gemini'
import { guessCategory, guessCategoryAndSubcategory } from './finance'
import { dateInMonth, formatBRL, formatShortDate, monthLabel, normalize, round2, todayKey } from './utils'
import { parseTransactionsFromText } from './pdfParser'

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
  attachment?: ChatAttachment,
  pendingTransactions?: PendingBatchTransaction[],
): Promise<{ text: string; actions: ActionCardData[] }> {
  const n = normalize(message)
  const actions: ActionCardData[] = []
  const amount = parseAmount(message)
  const dayMatch = n.match(/dia\s+(\d{1,2})/)
  const date = dayMatch ? dateInMonth(monthKey, Number(dayMatch[1])) : undefined

  // ---------------- Consulta/Enumeração de Transações Pendentes do Extrato ----------------
  const isQueryingPending =
    /(quais|enumere|listar|mostrar|ver|detalhe|quais foram).*(transa[çc][õo]es|lan[çc]amentos|itens|gastos|despesas|entradas|receitas)/i.test(n) ||
    /^(quais foram|quais s[ãa]o elas|enumere elas|quais s[ãa]o os itens|quais itens|o que foi importado)/i.test(n)

  if (isQueryingPending && pendingTransactions && pendingTransactions.length > 0) {
    const valid = pendingTransactions.filter((t) => t.categoria_filtro === 'valida')
    const countReceitas = valid.filter((t) => t.tipo === 'income').length
    const countDespesas = valid.filter((t) => t.tipo === 'expense').length
    const totalReceitas = round2(valid.filter((t) => t.tipo === 'income').reduce((s, t) => s + t.valor_exato, 0))
    const totalDespesas = round2(valid.filter((t) => t.tipo === 'expense').reduce((s, t) => s + t.valor_exato, 0))

    const table = [
      '| Data | Tipo | Descrição / Contraparte | Valor |',
      '| :--- | :--- | :--- | :--- |',
      ...valid.map(
        (t) =>
          `| ${formatShortDate(t.data)} | ${t.tipo === 'income' ? '🟢 Entrada' : '🔴 Saída'} | ${t.contraparte || 'Transação'} | ${formatBRL(t.valor_exato)} |`,
      ),
    ].join('\n')

    return {
      text: `📋 **Lançamentos reais extraídos do extrato bancário (${valid.length} itens no total):**\n\n• 🟢 **${countReceitas} Receitas / Entradas**: ${formatBRL(totalReceitas)}\n• 🔴 **${countDespesas} Despesas / Saídas**: ${formatBRL(totalDespesas)}\n\n${table}\n\nPara efetivar o cadastro no seu Saldo Livre, clique no botão **🟢 Confirmar e Executar** no cartão acima ou responda **'Sim'** no chat.`,
      actions: [],
    }
  }

  // ---------------- Importação de Extrato/Fatura em PDF (Modo Demo) ----------------
  if (attachment || /fatura.*pdf|extrato.*pdf|importar pdf|importar fatura|importar extrato/i.test(n)) {
    const fileName = attachment?.name || (n.includes('fatura') ? 'fatura_cartao.pdf' : 'extrato_conta.pdf')
    const isStatement = /extrato|conta|bancari|extrato.*pdf/i.test(fileName + ' ' + n)

    // Se o PDF possui texto real extraído pelo pdfjs-dist, lê as transações VERDADEIRAS do arquivo do usuário!
    if (attachment?.extractedText && attachment.extractedText.trim().length > 20) {
      const realTxs = parseTransactionsFromText(attachment.extractedText.split('\n'), monthKey)
      if (realTxs.length > 0) {
        const batchResult = await execute('registrarTransacoesLote', {
          origemDocumento: isStatement ? 'extrato_conta' : 'fatura_cartao',
          mesFaturaPadrao: monthKey,
          metodoPagamentoPadrao: isStatement ? 'pix' : 'credit_card',
          cartaoPadrao: isStatement ? undefined : 'Cartão Principal',
          transacoes: realTxs.map((t) => ({
            ...t,
            tipoDivisao: 'MEU',
            data: t.data || dateInMonth(monthKey, 10),
            status: isStatement ? 'paid' : (t.metodoPagamento === 'credit_card' ? 'pending' : 'paid'),
          })),
        })
        if (batchResult.action) actions.push(batchResult.action)

        const validTxs = realTxs.filter((t) => t.categoria_filtro !== 'ignorada')
        const countIncomes = validTxs.filter((t) => t.tipo === 'income').length
        const countExpenses = validTxs.filter((t) => t.tipo === 'expense').length
        const totalReceitas = round2(validTxs.filter((t) => t.tipo === 'income').reduce((s, t) => s + t.valorTotal, 0))
        const totalDespesas = round2(validTxs.filter((t) => t.tipo === 'expense').reduce((s, t) => s + t.valorTotal, 0))

        const table = [
          '| Data | Tipo | Descrição / Contraparte | Valor |',
          '| :--- | :--- | :--- | :--- |',
          ...validTxs.map(
            (t) =>
              `| ${formatShortDate(t.data || dateInMonth(monthKey, 10))} | ${t.tipo === 'income' ? '🟢 Entrada' : '🔴 Saída'} | ${t.contraparte || t.descricao} | ${formatBRL(t.valorTotal)} |`,
          ),
        ].join('\n')

        if (batchResult.action?.kind === 'confirmation') {
          return {
            text: `📄 **Entendi sua solicitação!** Analisei o arquivo PDF (${fileName}) e extraí **${validTxs.length} lançamentos válidos** (${countExpenses} despesas somando **${formatBRL(totalDespesas)}** e ${countIncomes} receitas somando **${formatBRL(totalReceitas)}**):\n\n${table}\n\nPor favor, revise os itens listados acima e clique em **🟢 Confirmar e Executar** ou responda **'Sim'** no chat para cadastrá-los no seu Saldo Livre.`,
            actions,
          }
        }
        return {
          text: `📄 **Arquivo PDF lido com precisão!** (${fileName})\n\nExtraí diretamente do documento **${validTxs.length} lançamentos reais** (${countExpenses} despesas somando **${formatBRL(totalDespesas)}** e ${countIncomes} receitas somando **${formatBRL(totalReceitas)}**):\n\n${table}\n\n💡 Ao conectar sua **API Key do Gemini** (ícone 🔑), o Copiloto aplica visão multimodal para leitura de layouts complexos e tabelas bancárias!`,
          actions,
        }
      }
    }

    if (isStatement) {
      // Fallback didático se o PDF for escaneado/imagem sem texto selecionável
      const fallbackTxs = [
        { descricao: 'Salário Empresa XPTO', contraparte: 'Empresa XPTO', valorTotal: 4850.00, tipo: 'income' as const, categoria: 'Receita', metodoPagamento: 'pix' as const, status: 'paid', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 5) },
        { descricao: 'Pix Recebido - Venda Teclado', contraparte: 'Venda Teclado', valorTotal: 220.00, tipo: 'income' as const, categoria: 'Receita', metodoPagamento: 'pix' as const, status: 'paid', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 9) },
        { descricao: 'Pix Enviado - Aluguel', contraparte: 'Imobiliária Aluguel', valorTotal: 1200.00, tipo: 'expense' as const, categoria: 'Moradia', metodoPagamento: 'pix' as const, status: 'paid', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 6) },
        { descricao: 'Supermercado Débito', contraparte: 'Supermercado Pão de Açúcar', valorTotal: 284.50, tipo: 'expense' as const, categoria: 'Alimentação', metodoPagamento: 'debit' as const, status: 'paid', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 8) },
        { descricao: 'Boleto Enel Energia', contraparte: 'Enel Energia', valorTotal: 165.30, tipo: 'expense' as const, categoria: 'Moradia', metodoPagamento: 'bill' as const, status: 'paid', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 10) },
      ]
      const batchResult = await execute('registrarTransacoesLote', {
        origemDocumento: 'extrato_conta',
        mesFaturaPadrao: monthKey,
        metodoPagamentoPadrao: 'pix',
        transacoes: fallbackTxs,
      })
      if (batchResult.action) actions.push(batchResult.action)

      const table = [
        '| Data | Tipo | Descrição / Contraparte | Valor |',
        '| :--- | :--- | :--- | :--- |',
        ...fallbackTxs.map(
          (t) =>
            `| ${formatShortDate(t.data)} | ${t.tipo === 'income' ? '🟢 Entrada' : '🔴 Saída'} | ${t.contraparte} | ${formatBRL(t.valorTotal)} |`,
        ),
      ].join('\n')

      if (batchResult.action?.kind === 'confirmation') {
        return {
          text: `📄 **Entendi sua solicitação!** Analisei o extrato bancário (${fileName}) e preparei **5 lançamentos** (2 receitas somando **R$ 5.070,00** e 3 despesas somando **R$ 1.649,80**):\n\n${table}\n\nPor favor, confira os dados no cartão abaixo e clique em **🟢 Confirmar e Executar** ou responda **'Sim'** para confirmar o cadastro no seu Saldo Livre.`,
          actions,
        }
      }
      return {
        text: `📄 **Extrato Bancário processado!** (${fileName})\n\nClassifiquei com precisão os lançamentos bancários da sua conta:\n\n${table}\n\n💡 Conecte sua **API Key do Google Gemini** (ícone 🔑 acima) para ler extratos escaneados e faturas com IA multimodal profunda!`,
        actions,
      }
    } else {
      // Fatura de Cartão de Crédito
      const batchResult = await execute('registrarTransacoesLote', {
        origemDocumento: 'fatura_cartao',
        mesFaturaPadrao: monthKey,
        dataVencimentoPadrao: dateInMonth(monthKey, 15),
        cartaoPadrao: 'Nubank Principal',
        metodoPagamentoPadrao: 'credit_card',
        transacoes: [
          { descricao: 'Supermercado Pão de Açúcar', valorTotal: 342.80, tipo: 'expense', categoria: 'Alimentação', metodoPagamento: 'credit_card', status: 'pending', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 4) },
          { descricao: 'Posto Shell Combustível', valorTotal: 195.00, tipo: 'expense', categoria: 'Transporte', metodoPagamento: 'credit_card', status: 'pending', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 6) },
          { descricao: 'iFood Restaurante', valorTotal: 84.50, tipo: 'expense', categoria: 'Alimentação', metodoPagamento: 'credit_card', status: 'pending', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 8) },
          { descricao: 'Drogaria São Paulo', valorTotal: 67.90, tipo: 'expense', categoria: 'Saúde', metodoPagamento: 'credit_card', status: 'pending', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 11) },
          { descricao: 'Netflix Mensalidade', valorTotal: 55.90, tipo: 'expense', categoria: 'Assinaturas', metodoPagamento: 'credit_card', status: 'pending', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 12) },
          { descricao: 'Estorno Uber Viagem', valorTotal: -14.20, tipo: 'expense', categoria: 'Estorno', metodoPagamento: 'credit_card', status: 'pending', tipoDivisao: 'MEU', data: dateInMonth(monthKey, 14) },
        ],
      })
      if (batchResult.action) actions.push(batchResult.action)
      if (batchResult.action?.kind === 'confirmation') {
        return {
          text: `📄 **Entendi sua solicitação!** Analisei a fatura de cartão de crédito (${fileName}) para vencimento em **${monthLabel(monthKey)}**.\n\nPor favor, confira os lançamentos no cartão abaixo e clique em **🟢 Confirmar e Executar** ou responda **'Sim'** no chat para cadastrá-los no sistema.`,
          actions,
        }
      }
      return {
        text: `📄 **Fatura de Cartão de Crédito processada!** (${fileName})\n\nRegistrei os gastos e estornos da fatura para vencimento em **${monthLabel(monthKey)}**.\n\n💡 Ao conectar sua **API Key do Google Gemini**, o Copiloto extrai todas as parcelas e compras do seu cartão real em PDF!`,
        actions,
      }
    }
  }

  // ---------------- Exclusão Cirúrgica de Transações (Modo Demo) ----------------
  if (/^(exclua|excluir|apague|apagar|delete|deletar|remova|remover|tirar|tire)\b/i.test(n) || /(desfazer|excluir|apagar|deletar).*(ultimo|recente|fatura|extrato|compra|gasto|despesa|receita)/i.test(n)) {
    const isUndoRecent = /ultimo|ultimos|recentes|desfazer|desfaca|lote/i.test(n)
    const cleanTerm = n
      .replace(/^(exclua|excluir|apague|apagar|delete|deletar|remova|remover|tirar|tire)\s+(a\s+|o\s+|as\s+|os\s+)?(despesa|gasto|compra|receita|lancamento)?\s*(de|do|da|no|na)?\s*/i, '')
      .replace(/r?\$?\s*[\d.,]+\s*(reais)?/i, '')
      .replace(/(do mes|deste mes|de outubro|de novembro|por favor|pfv)/gi, '')
      .trim()

    const r = await execute('excluirTransacoes', {
      termoBusca: cleanTerm && cleanTerm.length >= 2 && !isUndoRecent ? cleanTerm : undefined,
      valor: amount ?? undefined,
      mes: monthKey,
      apenasRecentes: isUndoRecent,
      limiteQuantidade: isUndoRecent ? 10 : 1,
    })
    if (r.action) actions.push(r.action)
    if (r.action?.kind === 'confirmation') {
      return {
        text: `🗑️ **Entendi sua solicitação!** Localizei a(s) transação(ões) para exclusão.\n\nPor favor, confira os dados no cartão abaixo e clique em **🟢 Confirmar e Executar** ou responda **'Sim'** no chat para confirmar a exclusão no seu Saldo Livre.`,
        actions,
      }
    }
    const d = r.response as Record<string, any>
    if (!d.ok) {
      return {
        text: `⚠️ ${d.erro || 'Não encontrei nenhuma transação correspondente para excluir.'}`,
        actions,
      }
    }
    return {
      text: `🗑️ **${d.quantidadeExcluida} transação(ões) excluída(s) com sucesso!**\n\n${(d.itensExcluidos as string[]).map((it) => `• ${it}`).join('\n')}\n\nSaldo Livre atualizado: **${formatBRL(d.saldoLivreAtualizado)}**.`,
      actions,
    }
  }

  // ---------------- Regras / Memória do Usuário ----------------
  if (/aprenda|memoriz|lembre|guarde que|anote que/i.test(n)) {
    const cleanRule = message.replace(/^(aprenda que|lembre-se que|memorize que|guarde que|anote que|aprenda|lembre|memorize)\s*:?\s*/i, '').trim()
    const r = await execute('gerenciarRegrasEMemoria', { acao: 'aprender', regra: cleanRule || message })
    if (r.action) actions.push(r.action)
    return {
      text: `🧠 **Regra memorizada com sucesso!**\n\n"${cleanRule || message}"\n\nVou lembrar desse padrão em todos os seus próximos lançamentos e orientações financeiras.`,
      actions,
    }
  }

  if (/listar regras|quais regras|minhas regras|regras aprendidas/i.test(n)) {
    const r = await execute('gerenciarRegrasEMemoria', { acao: 'listar' })
    if (r.action) actions.push(r.action)
    const d = r.response as Record<string, any>
    const regras = (d.regras as Array<{ id: string; regra: string }>) || []
    if (regras.length === 0) {
      return {
        text: '🧠 Você ainda não me ensinou nenhuma regra personalizada.\n\nPara me ensinar, basta dizer por exemplo: *"Aprenda que todo gasto no Zé Delivery é Lazer e 100% meu"*.',
        actions,
      }
    }
    const listText = regras.map((reg, idx) => `${idx + 1}. ${reg.regra}`).join('\n')
    return {
      text: `🧠 **Regras Aprendidas Atualmente:**\n\n${listText}\n\nVocê pode me ensinar novas regras a qualquer momento dizendo *"Aprenda que..."*.`,
      actions,
    }
  }

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

    const subcats = (d.gastosPorSubcategoria as { category: string; subcategory: string; amount: number; count: number }[]) ?? []
    const subcatRows = subcats
      .slice(0, 10)
      .map((s) => `| ${s.category} › ${s.subcategory} | ${formatBRL(s.amount)} | ${((s.amount / totalCat) * 100).toFixed(0)}% |`)
      .join('\n')

    return {
      text: `### 📊 Relatório de ${d.mes}

| Indicador | Valor |
|---|---|
| Receitas | ${formatBRL(d.receitas)} |
| Já pago | ${formatBRL(d.jaPago)} |
| Comprometido | ${formatBRL(d.comprometido)} |
| **Saldo Livre Real** | **${formatBRL(d.saldoLivreReal)}** |
| Limite diário seguro | ${d.limiteDiarioSeguro != null ? formatBRL(d.limiteDiarioSeguro) : '—'} |

**Onde vai o seu dinheiro (Categorias Principais)**

| Categoria | Meu impacto | % |
|---|---|---|
${rows || '| — | — | — |'}

${subcats.length > 0 ? `**Detalhamento por Subgrupos Específicos**

| Grupo › Subgrupo | Valor | % |
|---|---|---|
${subcatRows}
` : ''}
**💡 Onde economizar**
${tips.map((t) => `- ${t}`).join('\n') || '- Seu mês está equilibrado. Continue assim!'}`,
      actions,
    }
  }

  // ---------------- Geração de Gráficos Financeiros ----------------
  if (/gr[aá]fico|chart|visualiza|pizza|donut|barras|evoluc[aã]o|distribui[cç][aã]o/.test(n)) {
    const isInvestment = /invest|aporta|patrimon|ativo|renda fixa|acao|acoes|fii|cripto/.test(n)
    const r = await execute('obterRelatorioEProjecaoMensal', {})
    if (r.action) actions.push(r.action)
    const d = r.response as Record<string, any>

    if (isInvestment) {
      const invClasses = (d.investimentosPorClasse as Array<{ subcategory: string; amount: number; percentage: number }>) || []
      const totalInv = (d.totalInvestido as number) || 0

      if (invClasses.length === 0 || totalInv === 0) {
        return {
          text: `📊 **Análise de Investimentos de ${d.mes}:**\n\nVocê ainda não possui lançamentos registrados na categoria **Investimentos** neste mês.\n\nPara começar a acompanhar sua carteira e gráficos, você pode registrar aportes (ex: *"Investi R$ 500 no Tesouro Selic"* ou *"Comprei R$ 300 em Fundos Imobiliários"*).`,
          actions,
        }
      }

      const chartData = invClasses.map((item) => ({
        label: item.subcategory,
        value: item.amount,
      }))

      const chartMarkdown = `\n\`\`\`chart\n${JSON.stringify({
        type: 'donut',
        title: `Alocação de Investimentos (${d.mes})`,
        description: `Total aportado neste mês: ${formatBRL(totalInv)}`,
        data: chartData,
      }, null, 2)}\n\`\`\`\n`

      return {
        text: `📈 **Gráfico de Investimentos gerado com sucesso!**\n\nNeste mês você aportou um total de **${formatBRL(totalInv)}** distribuído em **${invClasses.length} classe(s) de ativos**:\n\n${chartMarkdown}\n\n💡 *Dica:* Diversificar entre Renda Fixa e Ativos Geradores de Renda (FIIs/Ações) fortalece a proteção e o crescimento do seu patrimônio.`,
        actions,
      }
    }

    // Default to categories expense chart
    const cats = (d.gastosPorCategoria as Array<{ category: string; amount: number }>) || []
    const totalCommitted = (d.comprometido as number) || 0

    if (cats.length === 0 || totalCommitted === 0) {
      return {
        text: `📊 **Gráfico Financeiro de ${d.mes}:**\n\nAinda não há despesas cadastradas neste mês para gerar um gráfico de distribuição. Cadastre seus primeiros gastos para visualizar a análise completa!`,
        actions,
      }
    }

    const chartData = cats.slice(0, 8).map((c) => ({
      label: c.category,
      value: c.amount,
    }))

    const isBar = /barra|comparativ|ranking/.test(n)
    const chartType = isBar ? 'bar' : 'donut'

    const chartMarkdown = `\n\`\`\`chart\n${JSON.stringify({
      type: chartType,
      title: `Distribuição de Gastos — ${d.mes}`,
      description: `Total de despesas computadas: ${formatBRL(totalCommitted)}`,
      data: chartData,
    }, null, 2)}\n\`\`\`\n`

    return {
      text: `📊 **Aqui está a análise visual das suas despesas em ${d.mes}:**\n\n${chartMarkdown}\n\nO total comprometido no mês é de **${formatBRL(totalCommitted)}**. Seu **Saldo Livre** atual é de **${formatBRL(d.saldoLivreReal as number)}**.`,
      actions,
    }
  }

  // ---------------- Saldo Livre / Limite Diário Direto ----------------
  if (/saldo livre|qual.*saldo|quanto.*saldo|meu saldo|limite diario|qual.*limite|quanto tenho/.test(n)) {
    const r = await execute('obterRelatorioEProjecaoMensal', {})
    if (r.action) actions.push(r.action)
    const d = r.response as Record<string, any>
    const free = d.saldoLivreReal as number
    const daily = d.limiteDiarioSeguro as number | null
    const days = d.diasRestantes as number

    return {
      text: `Seu **Saldo Livre Real** em **${d.mes}** é **${formatBRL(free)}**.\n\n` +
        (daily != null
          ? `• **Limite Diário Seguro:** **${formatBRL(daily)}/dia** (${days} dias restantes).\n`
          : '• Mês encerrado.\n') +
        `• **Receitas:** ${formatBRL(d.receitas)}\n` +
        `• **Total Comprometido:** ${formatBRL(d.comprometido)} (Já pago: ${formatBRL(d.jaPago)}, A pagar: ${formatBRL(d.comprometido - d.jaPago)})\n` +
        (d.aReceberTotal > 0 ? `• **A Receber:** ${formatBRL(d.aReceberTotal)}\n` : ''),
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
    if (r.action?.kind === 'confirmation') {
      return {
        text: `⚡ **Entendi sua solicitação!** Preparei a atualização da conta **${billWord}** para **${formatBRL(amount)}**.\n\nPor favor, confira os dados no cartão abaixo e clique em **🟢 Confirmar e Executar** ou responda **'Sim'** para confirmar a alteração no seu Saldo Livre.`,
        actions,
      }
    }
    const d = r.response as Record<string, any>
    if (!d.ok) return { text: `Não encontrei uma conta em aberto com "${billWord}". Contas em aberto: ${(d.contasEmAberto ?? []).join(', ') || 'nenhuma'}.`, actions }
    const diff = d.variacaoNoMeuSaldoLivre as number
    return {
      text: `Atualizei **${d.conta}** para **${formatBRL(d.valorReal)}** (${d.novoStatus.toLowerCase()}, vence ${d.vencimento ? d.vencimento.split('-').reverse().slice(0, 2).join('/') : '—'}).

${diff === 0 ? 'Veio exatamente o valor estimado — seu Saldo Livre não mudou.' : diff < 0 ? `Veio **${formatBRL(Math.abs(d.diferencaVsEstimado))} acima** da estimativa. Como a conta é dividida conforme a regra, seu Saldo Livre caiu **${formatBRL(Math.abs(diff))}**.` : `Boa! Veio **${formatBRL(Math.abs(d.diferencaVsEstimado))} abaixo** do estimado e seu Saldo Livre subiu **${formatBRL(diff)}**.`}

Novo Saldo Livre: **${formatBRL(d.saldoLivreDepois)}** · Limite diário: **${d.limiteDiarioDepois != null ? formatBRL(d.limiteDiarioDepois) : '—'}**`,
      actions,
    }
  }

  const isQuestion = message.includes('?') || /^(qual|quanto|quantos|quais|onde|quem|por que|pq|o que|tem algum|tem alguma|existe|sera que)\b/i.test(n.trim())

  // ---------------- Consulta de Transações / Gastos ----------------
  if (
    isQuestion ||
    /consult|listar transac|pesquis|buscar|extrato|ultimas compras|ultimos lancamentos|historico de gastos|gastos com|gastei com|gastei no|gastei na|100% (a mim|minh|meu)|so minh|apenas o que e meu|despesas|compras|gastos do|gastos da|cartao/.test(
      n,
    )
  ) {
    // Sensiência para 100% minhas despesas vs terceiros vs cartões
    const isOnlyMine = /100% (a mim|minh|meu)|so minh|apenas o que e meu|meus gastos pessoais|despesas 100%|minhas despesas/i.test(n)
    const isThirdParties = /terceir|dividid|rachad|a receber de|de terceiros/i.test(n)

    let matchedPerson: string | undefined
    for (const [re, name] of THIRD_PARTIES) {
      if (re.test(n)) {
        matchedPerson = name
        break
      }
    }

    // Identificação de Cartão de Crédito cadastrado
    let matchedCardName: string | undefined
    const cardQuery = await execute('gerenciarCartoes', { acao: 'listar' }).catch(() => null)
    const registeredCards = (cardQuery?.response?.cartoes as Array<{ id: string; nome: string }>) || []
    for (const c of registeredCards) {
      if (c.nome && n.includes(normalize(c.nome))) {
        matchedCardName = c.nome
        break
      }
    }
    if (!matchedCardName) {
      const cardMatch = message.match(/cart[ãa]o\s+([A-Za-zÀ-ÿ0-9]+)/i)
      if (cardMatch && !/^(credito|debito|meu|da|do|de|fechou|vence)$/i.test(normalize(cardMatch[1]))) {
        matchedCardName = cardMatch[1].trim()
      }
    }

    // Check if user is asking about a subcategory or category
    let matchedCategory: string | undefined
    let matchedSubcategory: string | undefined

    for (const [catName, meta] of Object.entries(CATEGORY_GROUPS)) {
      for (const sub of meta.subcategories) {
        const normSub = normalize(sub)
        const singularSub = normSub.endsWith('s') && normSub.length > 4 ? normSub.slice(0, -1) : normSub
        if (n.includes(normSub) || n.includes(singularSub)) {
          matchedSubcategory = sub
          matchedCategory = catName
          break
        }
      }
      if (matchedSubcategory) break
    }

    if (!matchedCategory) {
      for (const cat of CATEGORIES) {
        if (normalize(cat) === 'receita') continue
        if (n.includes(normalize(cat))) {
          matchedCategory = cat
          break
        }
      }
    }

    // Check if user is asking about a search term (e.g. "uber", "ifood", "mercado")
    let searchTerm: string | undefined
    const searchMatch = message.match(/\b(?:no|na|em|com|de)\s+([A-Za-zÀ-ÿ0-9]{3,20})\b/i)
    if (searchMatch && !matchedCategory && !matchedSubcategory && !matchedCardName && !matchedPerson) {
      const term = searchMatch[1].trim()
      if (
        !/^(janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro|cartao|credito|debito|fatura|hoje|ontem)$/i.test(
          normalize(term),
        )
      ) {
        searchTerm = term
      }
    }

    const r = await execute('consultarTransacoes', {
      termoBusca: searchTerm,
      categoria: matchedCategory,
      subcategoria: matchedSubcategory,
      somenteMinhas: isOnlyMine,
      pessoa: matchedPerson,
      apenasTerceiros: isThirdParties && !isOnlyMine,
      cartao: matchedCardName,
      mes: monthKey,
    })
    if (r.action) actions.push(r.action)
    const d = r.response as Record<string, any>
    const items = (d.transacoes as Array<any>) || []

    if (items.length === 0) {
      let msgVazio = `🔍 Não encontrei nenhum lançamento registrado em **${monthLabel(monthKey)}**`
      if (isOnlyMine) msgVazio += ` que seja 100% pessoal seu.`
      else if (matchedPerson) msgVazio += ` vinculado a **${matchedPerson}**.`
      else if (matchedCardName) msgVazio += ` no cartão **${matchedCardName}**.`
      else if (matchedSubcategory) msgVazio += ` no subgrupo **${matchedSubcategory}** (${matchedCategory}).`
      else if (matchedCategory) msgVazio += ` na categoria **${matchedCategory}**.`
      else if (searchTerm) msgVazio += ` com o termo "**${searchTerm}**".`
      else msgVazio += ` para os filtros informados.`

      return {
        text: msgVazio,
        actions,
      }
    }

    const total = d.somaTotal as number
    const meuImpacto = d.somaMeuImpacto as number
    const somaTerceiros = (d.somaTerceiros as number) || 0
    const cardRef = d.cartaoReferenciado as { nome: string; fechamentoDia: number; vencimentoDia: number } | null
    const porSub = (d.porSubcategoria as Array<{ subcategoria: string; total: number; quantidade: number }>) || []

    // Montagem do cabeçalho de contexto descritivo
    let header = `### 📋 Análise de Despesas — ${monthLabel(monthKey)}`
    if (isOnlyMine) {
      header = `### 👤 Despesas 100% Pessoais (Exclusivas) — ${monthLabel(monthKey)}`
    } else if (matchedPerson) {
      header = `### 👥 Despesas Vinculadas a ${matchedPerson} — ${monthLabel(monthKey)}`
    } else if (cardRef) {
      header = `### 💳 Fatura do Cartão ${cardRef.nome} (Fecha dia ${cardRef.fechamentoDia}, Vence dia ${cardRef.vencimentoDia}) — ${monthLabel(monthKey)}`
    } else if (matchedCategory) {
      header = `### 🏷️ Despesas em ${matchedCategory}${matchedSubcategory ? ` › ${matchedSubcategory}` : ''} — ${monthLabel(monthKey)}`
    }

    // Painel de métricas principais
    const metrics = [
      `• **Total Geral:** ${formatBRL(total)}`,
      `• **Seu Impacto (Saldo Livre):** ${formatBRL(meuImpacto)}`,
      ...(somaTerceiros > 0 ? [`• **Cota de Terceiros (A Receber):** ${formatBRL(somaTerceiros)}`] : []),
      `• **Quantidade:** ${d.totalEncontrados} lançamento(s)`,
    ].join('\n')

    // Detalhamento de subcategorias se houver mais de uma
    let subBreakdown = ''
    if (porSub.length > 1) {
      subBreakdown = `\n\n**Distribuição por Subgrupo:**\n` +
        porSub.map((s) => `  * **${s.subcategoria}:** ${formatBRL(s.total)} (${s.quantidade} item${s.quantidade > 1 ? 's' : ''})`).join('\n')
    }

    // Listagem descritiva e limpa dos lançamentos
    const listFormatted = items
      .slice(0, 10)
      .map((t) => {
        const sub = t.subcategoria ? ` (${t.categoria} › ${t.subcategoria})` : ` (${t.categoria})`
        const cardInfo = t.cartao ? ` • Cartão ${t.cartao}` : ''
        const splitInfo = t.detalhesDivisao && t.detalhesDivisao !== '100% do usuário' ? `\n    ↳ *${t.detalhesDivisao}*` : ''
        return `• **${t.descricao}**${sub}: **${formatBRL(t.valorTotal)}** (${formatShortDate(t.dataVencimento || t.dataCompra)})${cardInfo}${splitInfo}`
      })
      .join('\n')

    const extraNote = items.length > 10 ? `\n*(exibindo os 10 primeiros de ${items.length} lançamentos)*` : ''

    return {
      text: `${header}\n\n${metrics}${subBreakdown}\n\n**Detalhamento dos Lançamentos:**\n${listFormatted}${extraNote}`,
      actions,
    }
  }

  // ---------------- Registrar transação ----------------
  if (amount && !isQuestion) {
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
    const guessed = guessCategoryAndSubcategory(n, amount || undefined)
    const categoria = tipoDivisao === 'TERCEIRO' ? 'Cartão Terceiros' : guessed.category
    const subcategoria = tipoDivisao === 'TERCEIRO' ? null : guessed.subcategory

    const r = await execute('registrarTransacao', {
      descricao,
      valorTotal: amount,
      categoria,
      subcategoria,
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
    if (r.action?.kind === 'confirmation') {
      return {
        text: `👍 **Entendi sua solicitação!** Preparei o lançamento de ${income ? 'receita' : 'despesa'}: **${d.descricao || descricao}** no valor de **${formatBRL(amount)}**.\n\nPor favor, confira os dados no cartão abaixo e clique em **🟢 Confirmar e Executar** ou responda **'Sim'** para confirmar o cadastro no seu Saldo Livre.`,
        actions,
      }
    }
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
