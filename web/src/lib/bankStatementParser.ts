import { extractCounterparty } from '@/lib/pdfParser'
import type { Transaction } from '@/types'

export interface ParsedStatementTransaction {
  id: string
  fitId?: string
  date: string // YYYY-MM-DD
  description: string
  counterparty: string
  amount: number
  type: 'income' | 'expense'
  paymentMethod: 'pix' | 'credit_card' | 'debit' | 'bill'
  category: string
  subcategory?: string
  isIgnored: boolean
  ignoredReason?: string
  isDuplicate: boolean
  duplicateReason?: string
  isSelected: boolean
  installmentNumber?: number
  installmentsTotal?: number
}

const RDB_PATTERNS = [
  /\b(aplica[çc][ãa]o|resgate)\s+rdb\b/i,
  /\brdb\s+(resgate|aplica[çc][ãa]o|nubank)?\b/i,
  /\bguardar\s+dinheiro\b/i,
  /\bresgate\s+(de\s+investimento|di[áa]rio)\b/i,
  /\brendimento\s+l[íi]quido\s+rdb\b/i,
]

const INVOICE_PAYMENT_PATTERNS = [
  /pagamento\s+(recebido|de\s+fatura|efetuado)/i,
  /pagamento\s+de\s+fatura\s+recebido/i,
  /saldo\s+anterior/i,
  /total\s+a\s+pagar/i,
  /limite\s+disponivel/i,
  /data\s+de\s+vencimento/i,
  /demonstrativo/i,
]

const CATEGORY_HEURISTICS: Array<{
  category: string
  subcategory?: string
  regex: RegExp
}> = [
  // Alimentação
  { category: 'Alimentação', subcategory: 'Supermercado', regex: /mercado|supermercado|assai|assaí|carrefour|pao de acucar|pão de açúcar|extra|atacadao|atacadão|dia%|hortifruti|feira|sacolao|sacolão/i },
  { category: 'Alimentação', subcategory: 'Delivery & Lanches', regex: /ifood|rappi|uber\s*eats|lanche|burger|hamburguer|mc\s*donalds|mcdonalds|burguer\s*king|bk\b|subway|habib|delivery/i },
  { category: 'Alimentação', subcategory: 'Restaurante', regex: /restaurante|outback|madero|churrascaria|coco\s*bambu|fogao|fogão|parrilla|cantina|pizzaria|sushi|almoço|almoco|jantar/i },
  { category: 'Alimentação', subcategory: 'Padaria & Café', regex: /padaria|panificadora|cafe|café|starbucks|confeitaria|doceria|doce/i },
  { category: 'Alimentação', subcategory: 'Bares & Bebidas', regex: /bar\b|cerveja|chopp|adega|pub\b|bebidas|vinho/i },

  // Transporte
  { category: 'Transporte', subcategory: 'Combustível', regex: /gasolina|combustivel|combustível|etanol|posto|ipiranga|shell|br\s*distribuidora|abastec/i },
  { category: 'Transporte', subcategory: 'Aplicativos (Uber/99)', regex: /uber\b|99\b|indrive|cabify|pop\b|corrida/i },
  { category: 'Transporte', subcategory: 'Estacionamento & Pedágio', regex: /estacionamento|pedagio|pedágio|sem\s*parar|veloe|conectcar|taggy|estapar/i },
  { category: 'Transporte', subcategory: 'Transporte Público (Metrô/Ônibus)', regex: /onibus|ônibus|metro|metrô|bilhete\s*unico|cptm|sptrans/i },

  // Moradia
  { category: 'Moradia', subcategory: 'Aluguel & Condomínio', regex: /aluguel|condominio|condomínio|locacao|locação|imobiliaria/i },
  { category: 'Moradia', subcategory: 'Energia Elétrica', regex: /luz\b|energia|enel|cpfl|cemig|coelba|light|eletropaulo|energisa/i },
  { category: 'Moradia', subcategory: 'Água & Saneamento', regex: /agua|água|sabesp|sanepar|copasa|cedae|caesb/i },
  { category: 'Moradia', subcategory: 'Internet & TV a Cabo', regex: /internet|fibra|claro|vivo|oi\b|tim\s*fibra|brisanet|net\s*virtua/i },
  { category: 'Moradia', subcategory: 'Gás', regex: /gas\b|gás\b|ultragaz|liquigas|comgas/i },

  // Saúde
  { category: 'Saúde', subcategory: 'Farmácia & Remédios', regex: /farmacia|farmácia|drogaria|drogasil|droga\s*raia|pacheco|panvel|remedio|remédio|medicamento/i },
  { category: 'Saúde', subcategory: 'Consultas & Exames', regex: /consulta|exame|laboratorio|laboratório|dentista|oftalmo|medico|médico|clinica|clínica/i },
  { category: 'Saúde', subcategory: 'Academia & Treinos', regex: /academia|smartfit|smart\s*fit|bluefit|bio\s*ritmo|musculacao|musculação|pilates|crossfit/i },

  // Assinaturas
  { category: 'Assinaturas', subcategory: 'Streaming de Vídeo (Netflix/Max/Disney)', regex: /netflix|disney|hbo|max\b|prime\s*video|amazon\s*prime|youtube\s*premium|globoplay|telecine/i },
  { category: 'Assinaturas', subcategory: 'Streaming de Música (Spotify/Apple)', regex: /spotify|deezer|apple\s*music|tidal|amazon\s*music/i },
  { category: 'Assinaturas', subcategory: 'Softwares, IA & Cloud (iCloud/ChatGPT)', regex: /chatgpt|openai|icloud|google\s*one|google\s*storage|github|adobe|canva|microsoft|dropbox/i },

  // Compras
  { category: 'Compras', subcategory: 'Eletrônicos & Informática', regex: /kabum|pichau|terabyte|apple\b|samsung|dell|mercado\s*livre|amazon|shopee|magalu/i },
  { category: 'Compras', subcategory: 'Roupas & Acessórios', regex: /zara|renner|riachuelo|c&a|shein|roupa|vestuario|vestuário|acessorio|acessório|nike|adidas/i },
  { category: 'Compras', subcategory: 'Casa & Decoração', regex: /decoracao|decoração|cama|mesa|banho|tok&stok|etna|camicado|mobly|leroy|marabraz/i },
]

export function guessCategoryAndSubcategory(
  description: string,
  amount?: number
): { category: string; subcategory?: string } {
  for (const item of CATEGORY_HEURISTICS) {
    if (item.regex.test(description)) {
      return { category: item.category, subcategory: item.subcategory }
    }
  }

  if (amount !== undefined && amount > 800) {
    return { category: 'Compras', subcategory: 'Eletrônicos & Informática' }
  }

  return { category: 'Outros' }
}

/**
 * Normaliza textos de descrições para deduplicação sem falsos negativos causados por pontuação ou espaços
 */
export function normalizeDescriptionForDeduplication(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]/g, '')
}

/**
 * Parser para extratos bancários em formato OFX / QFX (SGML e XML)
 */
export function parseOfx(content: string, defaultMonthKey?: string): ParsedStatementTransaction[] {
  const items: ParsedStatementTransaction[] = []
  const year = defaultMonthKey ? defaultMonthKey.split('-')[0] : new Date().getFullYear().toString()

  // Procura blocos <STMTTRN>...</STMTTRN> ou SGML simples sem tag de fechamento
  const trnRegex = /<STMTTRN>([\s\S]*?)(?:<\/STMTTRN>|(?=<STMTTRN>|$))/gi
  let match: RegExpExecArray | null

  while ((match = trnRegex.exec(content)) !== null) {
    const block = match[1]

    // Valor da transação (<TRNAMT>)
    const amtMatch = block.match(/<TRNAMT>([-+]?\d+(?:\.\d+)?)/i)
    if (!amtMatch) continue
    const rawAmt = parseFloat(amtMatch[1])
    if (isNaN(rawAmt) || rawAmt === 0) continue

    // Data da transação (<DTPOSTED>) formato: YYYYMMDD...
    const dateMatch = block.match(/<DTPOSTED>(\d{4})(\d{2})(\d{2})/i)
    const txDate = dateMatch
      ? `${dateMatch[1]}-${dateMatch[2]}-${dateMatch[3]}`
      : `${year}-01-01`

    // FITID (Identificador único do banco)
    const fitIdMatch = block.match(/<FITID>([^<\r\n]+)/i)
    const fitId = fitIdMatch ? fitIdMatch[1].trim() : undefined

    // Descrição e Contraparte (<MEMO> ou <NAME>)
    const memoMatch = block.match(/<MEMO>([^<\r\n]+)/i)
    const nameMatch = block.match(/<NAME>([^<\r\n]+)/i)
    const rawDesc = (memoMatch ? memoMatch[1] : nameMatch ? nameMatch[1] : 'Transação').trim()
    const cleanCounterparty = extractCounterparty(rawDesc)

    const isIncome = rawAmt > 0
    const absAmount = Math.abs(rawAmt)

    // Detecção de RDB / Aplicação interna ou Pagamento de Fatura
    const isRdb = RDB_PATTERNS.some((p) => p.test(rawDesc))
    const isInvoicePayment = INVOICE_PAYMENT_PATTERNS.some((p) => p.test(rawDesc))
    const isIgnored = isRdb || isInvoicePayment
    const ignoredReason = isRdb
      ? 'RDB / Aplicação Financeira (reserva interna)'
      : isInvoicePayment
        ? 'Pagamento de Fatura / Saldo Anterior'
        : undefined

    // Detecção de parcelas (ex: 2/6 ou 10/12)
    const instMatch = rawDesc.match(/\b(\d{1,2})\/(\d{1,2})\b/)
    let installmentNumber: number | undefined
    let installmentsTotal: number | undefined
    if (instMatch) {
      const c = parseInt(instMatch[1], 10)
      const t = parseInt(instMatch[2], 10)
      if (c >= 1 && c <= t && t <= 48) {
        installmentNumber = c
        installmentsTotal = t
      }
    }

    // Inferência de método de pagamento
    let paymentMethod: 'pix' | 'credit_card' | 'debit' | 'bill' = 'credit_card'
    const lowerDesc = rawDesc.toLowerCase()
    if (isIncome || lowerDesc.includes('pix')) {
      paymentMethod = 'pix'
    } else if (lowerDesc.includes('debito') || lowerDesc.includes('débito')) {
      paymentMethod = 'debit'
    } else if (lowerDesc.includes('boleto')) {
      paymentMethod = 'bill'
    }

    const { category, subcategory } = isIncome
      ? { category: 'Receita', subcategory: undefined }
      : guessCategoryAndSubcategory(rawDesc, absAmount)

    items.push({
      id: `ofx_${fitId || Math.random().toString(36).substring(2, 10)}`,
      fitId,
      date: txDate,
      description: rawDesc,
      counterparty: cleanCounterparty,
      amount: absAmount,
      type: isIncome ? 'income' : 'expense',
      paymentMethod,
      category,
      subcategory,
      isIgnored,
      ignoredReason,
      isDuplicate: false,
      isSelected: !isIgnored,
      installmentNumber,
      installmentsTotal,
    })
  }

  return items
}

/**
 * Parser para extratos bancários em formato CSV (Nubank, Itaú, Inter, Bradesco, Santander, etc.)
 */
export function parseCsv(content: string, defaultMonthKey?: string): ParsedStatementTransaction[] {
  const items: ParsedStatementTransaction[] = []
  const lines = content.split(/\r?\n/).filter((l) => l.trim().length > 0)
  if (lines.length === 0) return []

  const year = defaultMonthKey ? defaultMonthKey.split('-')[0] : new Date().getFullYear().toString()

  // Identifica o delimitador mais provável da primeira linha (vírgula, ponto-e-vírgula ou tab)
  const firstLine = lines[0]
  const semiCount = (firstLine.match(/;/g) || []).length
  const commaCount = (firstLine.match(/,/g) || []).length
  const tabCount = (firstLine.match(/\t/g) || []).length

  let separator = ','
  if (semiCount > commaCount && semiCount > tabCount) separator = ';'
  else if (tabCount > commaCount && tabCount > semiCount) separator = '\t'

  const splitLine = (l: string): string[] => {
    // Parser simples de linha CSV respeitando aspas
    const result: string[] = []
    let current = ''
    let inQuotes = false

    for (let i = 0; i < l.length; i++) {
      const char = l[i]
      if (char === '"') {
        inQuotes = !inQuotes
      } else if (char === separator && !inQuotes) {
        result.push(current.trim().replace(/^"|"$/g, ''))
        current = ''
      } else {
        current += char
      }
    }
    result.push(current.trim().replace(/^"|"$/g, ''))
    return result
  }

  const headerTokens = splitLine(firstLine).map((t) => t.toLowerCase())
  const hasHeaders = headerTokens.some((t) =>
    t.includes('data') || t.includes('date') ||
    t.includes('valor') || t.includes('amount') ||
    t.includes('desc') || t.includes('title') ||
    t.includes('historico') || t.includes('histórico')
  )

  let dateIdx = -1
  let descIdx = -1
  let valIdx = -1
  let fitIdIdx = -1

  if (hasHeaders) {
    headerTokens.forEach((token, i) => {
      if (dateIdx === -1 && (token.includes('data') || token.includes('date'))) {
        dateIdx = i
      } else if (valIdx === -1 && (token.includes('valor') || token.includes('amount') || token.includes('quantia'))) {
        valIdx = i
      } else if (descIdx === -1 && (token.includes('desc') || token.includes('title') || token.includes('hist') || token.includes('estabelec') || token.includes('memo'))) {
        descIdx = i
      } else if (fitIdIdx === -1 && (token.includes('identificador') || token.includes('fitid') || token.includes('documento') || token.includes('id\b'))) {
        fitIdIdx = i
      }
    })
  }

  // Fallback padrão se cabeçalhos não mapeados
  if (dateIdx === -1) dateIdx = 0
  if (valIdx === -1) valIdx = 1
  if (descIdx === -1) descIdx = 2

  const startIndex = hasHeaders ? 1 : 0

  for (let i = startIndex; i < lines.length; i++) {
    const cols = splitLine(lines[i])
    if (cols.length < 2) continue

    const rawDate = cols[dateIdx] || ''
    const rawDesc = (cols[descIdx] || cols[1] || 'Transação').trim()
    const rawVal = cols[valIdx] || ''
    const fitId = fitIdIdx !== -1 && cols[fitIdIdx] ? cols[fitIdIdx].trim() : undefined

    if (!rawVal || !rawDate) continue

    // Normalização de Valor
    // Formatos: -123,45 / 1.234,56 / 1234.56 / 123,45 D / 123,45 C / (123,45)
    let isCredit = /\b(c|crédito|credito|\+)\b/i.test(rawVal)
    let isDebit = /\b(d|débito|debito|-)\b/i.test(rawVal) || rawVal.includes('(')

    let cleanVal = rawVal
      .replace(/[R$\s()CDcd+]/g, '')
      .trim()

    // Se tem vírgula como separador decimal brasileiro: "1.234,56" -> "1234.56"
    if (cleanVal.includes(',') && cleanVal.includes('.')) {
      cleanVal = cleanVal.replace(/\./g, '').replace(',', '.')
    } else if (cleanVal.includes(',')) {
      cleanVal = cleanVal.replace(',', '.')
    }

    let parsedAmount = parseFloat(cleanVal)
    if (isNaN(parsedAmount) || parsedAmount === 0) continue

    if (rawVal.includes('-')) {
      isDebit = true
      isCredit = false
    }

    const hasExplicitDebit = isDebit || rawVal.includes('-')
    const hasExplicitCredit = isCredit || rawVal.includes('+')
    const descIndicatesIncome = /recebid|recebimento|dep[oó]sito|sal[aá]rio|rendimento|ted recebida|doc recebido|estorno/i.test(rawDesc)

    let isIncome = false
    if (hasExplicitCredit) {
      isIncome = true
    } else if (hasExplicitDebit) {
      isIncome = false
    } else if (descIndicatesIncome) {
      isIncome = true
    } else {
      const fileHasNegativeValues = lines.some((l) => l.includes('-'))
      if (fileHasNegativeValues) {
        isIncome = parsedAmount > 0
      } else {
        isIncome = false
      }
    }
    const absAmount = Math.abs(parsedAmount)


    // Normalização de Data (DD/MM/YYYY ou YYYY-MM-DD ou DD-MM-YYYY)
    let txDate = `${year}-01-01`
    const brDateMatch = rawDate.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})/)
    const isoDateMatch = rawDate.match(/^(\d{4})[/-](\d{1,2})[/-](\d{1,2})/)

    if (isoDateMatch) {
      const y = isoDateMatch[1]
      const m = isoDateMatch[2].padStart(2, '0')
      const d = isoDateMatch[3].padStart(2, '0')
      txDate = `${y}-${m}-${d}`
    } else if (brDateMatch) {
      const d = brDateMatch[1].padStart(2, '0')
      const m = brDateMatch[2].padStart(2, '0')
      const y = brDateMatch[3].length === 2 ? `20${brDateMatch[3]}` : brDateMatch[3]
      txDate = `${y}-${m}-${d}`
    }

    const cleanCounterparty = extractCounterparty(rawDesc)

    // Detecção de RDB e Pagamentos de fatura
    const isRdb = RDB_PATTERNS.some((p) => p.test(rawDesc))
    const isInvoicePayment = INVOICE_PAYMENT_PATTERNS.some((p) => p.test(rawDesc))
    const isIgnored = isRdb || isInvoicePayment
    const ignoredReason = isRdb
      ? 'RDB / Aplicação Financeira (reserva interna)'
      : isInvoicePayment
        ? 'Pagamento de Fatura / Saldo Anterior'
        : undefined

    // Detecção de parcelas
    const instMatch = rawDesc.match(/\b(\d{1,2})\/(\d{1,2})\b/)
    let installmentNumber: number | undefined
    let installmentsTotal: number | undefined
    if (instMatch) {
      const c = parseInt(instMatch[1], 10)
      const t = parseInt(instMatch[2], 10)
      if (c >= 1 && c <= t && t <= 48) {
        installmentNumber = c
        installmentsTotal = t
      }
    }

    let paymentMethod: 'pix' | 'credit_card' | 'debit' | 'bill' = 'credit_card'
    const lowerDesc = rawDesc.toLowerCase()
    if (isIncome || lowerDesc.includes('pix')) {
      paymentMethod = 'pix'
    } else if (lowerDesc.includes('debito') || lowerDesc.includes('débito')) {
      paymentMethod = 'debit'
    } else if (lowerDesc.includes('boleto')) {
      paymentMethod = 'bill'
    }

    const { category, subcategory } = isIncome
      ? { category: 'Receita', subcategory: undefined }
      : guessCategoryAndSubcategory(rawDesc, absAmount)

    items.push({
      id: `csv_${fitId || Math.random().toString(36).substring(2, 10)}`,
      fitId,
      date: txDate,
      description: rawDesc,
      counterparty: cleanCounterparty,
      amount: absAmount,
      type: isIncome ? 'income' : 'expense',
      paymentMethod,
      category,
      subcategory,
      isIgnored,
      ignoredReason,
      isDuplicate: false,
      isSelected: !isIgnored,
      installmentNumber,
      installmentsTotal,
    })
  }

  return items
}

/**
 * Deduplica transações extraídas contra transações já existentes no banco de dados.
 * Transações duplicadas recebem `isDuplicate: true`, `isSelected: false` e um motivo explicativo.
 */
export function deduplicateTransactions(
  parsedItems: ParsedStatementTransaction[],
  existingTransactions: Transaction[]
): ParsedStatementTransaction[] {
  if (!existingTransactions || existingTransactions.length === 0) {
    return parsedItems
  }

  // Mapa de assinaturas exatas existentes
  // 1. Por fitId (se houver)
  const existingFitIds = new Set<string>()
  // 2. Por assinatura de dados: data + valor + contraparte/descrição normalizada
  const existingSignatures = new Map<string, Transaction>()

  for (const tx of existingTransactions) {
    const customTx = tx as unknown as Record<string, unknown>
    if (customTx.fitId) {
      existingFitIds.add(String(customTx.fitId))
    }
    const rawNotes = (customTx.notes as string) || ''
    const fitIdMatch = rawNotes.match(/FITID[:=]\s*([^\s;]+)/i)
    if (fitIdMatch) {
      existingFitIds.add(fitIdMatch[1])
    }

    const txAmount = tx.actualAmount ?? tx.estimatedAmount ?? tx.totalAmount ?? 0
    const absAmount = Math.abs(txAmount).toFixed(2)
    const txDate = tx.dueDate || ''
    const normDesc = normalizeDescriptionForDeduplication(tx.description || '')
    const sig = `${txDate}_${absAmount}_${normDesc}`
    existingSignatures.set(sig, tx)

    const counterparty = (customTx.counterparty as string) || tx.externalName || tx.thirdPartyName || tx.assignedFriendName
    if (counterparty) {
      const normCounter = normalizeDescriptionForDeduplication(counterparty)
      existingSignatures.set(`${txDate}_${absAmount}_${normCounter}`, tx)
    }
  }

  return parsedItems.map((item) => {
    // 1. Verifica por fitId
    if (item.fitId && existingFitIds.has(item.fitId)) {
      return {
        ...item,
        isDuplicate: true,
        isSelected: false,
        duplicateReason: `Transação já importada anteriormente (FITID: ${item.fitId})`,
      }
    }

    // 2. Verifica por assinatura composta (data + valor + texto)
    const absAmount = item.amount.toFixed(2)
    const normDesc = normalizeDescriptionForDeduplication(item.description)
    const normCounter = normalizeDescriptionForDeduplication(item.counterparty)

    const sigDesc = `${item.date}_${absAmount}_${normDesc}`
    const sigCounter = `${item.date}_${absAmount}_${normCounter}`

    const existingMatch = existingSignatures.get(sigDesc) || existingSignatures.get(sigCounter)

    if (existingMatch) {
      const txAmount = existingMatch.actualAmount ?? existingMatch.estimatedAmount ?? existingMatch.totalAmount ?? 0
      const txDate = existingMatch.dueDate || ''
      const formattedDate = txDate.split('-').reverse().slice(0, 2).join('/')
      return {
        ...item,
        isDuplicate: true,
        isSelected: false,
        duplicateReason: `Lançamento idêntico já cadastrado em ${formattedDate} (R$ ${txAmount.toFixed(2).replace('.', ',')})`,
      }
    }

    return item
  })
}

/**
 * Função Universal de Parsing e Deduplicação:
 * Detecta se o conteúdo é OFX ou CSV, executa o parser correspondente e deduplica contra o banco.
 */
export function parseUniversalStatement(
  content: string,
  fileName?: string,
  existingTransactions?: Transaction[],
  defaultMonthKey?: string
): ParsedStatementTransaction[] {
  const lowerName = (fileName || '').toLowerCase()
  const trimmed = content.trim()

  let parsed: ParsedStatementTransaction[] = []

  if (
    lowerName.endsWith('.ofx') ||
    lowerName.endsWith('.qfx') ||
    trimmed.includes('<OFX>') ||
    trimmed.includes('<STMTTRN>')
  ) {
    parsed = parseOfx(trimmed, defaultMonthKey)
  } else if (
    lowerName.endsWith('.csv') ||
    trimmed.includes(';') ||
    (trimmed.includes(',') && trimmed.split('\n').length > 1)
  ) {
    parsed = parseCsv(trimmed, defaultMonthKey)
  }

  if (existingTransactions && existingTransactions.length > 0) {
    return deduplicateTransactions(parsed, existingTransactions)
  }

  return parsed
}
