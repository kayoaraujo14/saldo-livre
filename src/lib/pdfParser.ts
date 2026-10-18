import * as pdfjsLib from 'pdfjs-dist'
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

// Configure worker URL for Vite
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorker

export interface ExtractedPdfData {
  text: string
  pageCount: number
  lines: string[]
}

/**
 * Extrai todo o texto legível de um arquivo PDF no navegador.
 */
export async function extractTextFromPdf(fileOrBase64: File | string): Promise<ExtractedPdfData> {
  let data: ArrayBuffer | Uint8Array

  if (typeof fileOrBase64 === 'string') {
    // Base64 string
    const binary = atob(fileOrBase64.replace(/\s/g, ''))
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i)
    }
    data = bytes.buffer
  } else {
    data = await fileOrBase64.arrayBuffer()
  }

  const loadingTask = pdfjsLib.getDocument({
    data,
    useSystemFonts: true,
  })

  const pdf = await loadingTask.promise
  const pageCount = pdf.numPages
  const allLines: string[] = []

  for (let pageNum = 1; pageNum <= pageCount; pageNum++) {
    const page = await pdf.getPage(pageNum)
    const textContent = await page.getTextContent()
    
    // Agrupa itens em linhas com base na coordenada Y
    const items = textContent.items as Array<{ str: string; transform: number[] }>
    let currentY: number | null = null
    let lineBuffer: string[] = []

    for (const item of items) {
      if (!item.str || !item.str.trim()) continue
      const y = Math.round(item.transform[5])
      if (currentY === null || Math.abs(y - currentY) > 3) {
        if (lineBuffer.length > 0) {
          allLines.push(lineBuffer.join(' ').trim())
          lineBuffer = []
        }
        currentY = y
      }
      lineBuffer.push(item.str.trim())
    }
    if (lineBuffer.length > 0) {
      allLines.push(lineBuffer.join(' ').trim())
    }
  }

  const fullText = allLines.join('\n')
  return {
    text: fullText,
    pageCount,
    lines: allLines,
  }
}

export interface ParsedPdfTransaction {
  data?: string
  descricao: string
  valorTotal: number
  tipo: 'income' | 'expense'
  metodoPagamento: 'pix' | 'credit_card' | 'debit' | 'bill'
  categoria?: string
  parcelaAtual?: number
  totalParcelas?: number
}

const MONTH_MAP: Record<string, string> = {
  jan: '01', fev: '02', mar: '03', abr: '04', mai: '05', jun: '06',
  jul: '07', ago: '08', set: '09', out: '10', nov: '11', dez: '12',
}

/**
 * Heurística avançada para interpretar linhas de faturas (Nubank, Itaú, Bradesco, Inter, C6, etc.)
 * e extratos bancários a partir do texto real do PDF.
 */
export function parseTransactionsFromText(lines: string[], monthKey: string): ParsedPdfTransaction[] {
  const transactions: ParsedPdfTransaction[] = []
  const year = monthKey.split('-')[0] || new Date().getFullYear().toString()

  const IGNORE_PATTERNS = [
    /pagamento (recebido|de fatura|efetuado)/i,
    /saldo anterior/i,
    /total a pagar/i,
    /limite disponivel/i,
    /data de vencimento/i,
    /demonstrativo/i,
  ]

  for (const line of lines) {
    if (line.length < 5) continue
    if (IGNORE_PATTERNS.some((p) => p.test(line))) continue

    // 1. Procura por valores em reais (ex: R$ 123,45 ou 123,45 ou -15,00)
    // Padrão brasileiro: 1.234,56 ou 123,45
    const valueMatch = line.match(/(?:R\$\s*)?(-?\s*\d{1,3}(?:\.\d{3})*,\d{2}|-?\s*\d+,\d{2})\b/)
    if (!valueMatch) continue

    const rawValStr = valueMatch[1].replace(/\s/g, '').replace(/\./g, '').replace(',', '.')
    let val = parseFloat(rawValStr)
    if (isNaN(val) || val === 0) continue

    // Verifica se tem sinal negativo na linha ou (C)/(D)
    const isCreditSignal = /\b(\+|c|crédito|credito|rec)\b/i.test(line) && !line.includes('cartao de credito')
    const isDebitSignal = /\b(-|d|débito|debito)\b/i.test(line)

    // 2. Procura por data (ex: 04/10, 04/10/2026, 04 OUT, 04 de Outubro)
    let txDate: string | undefined
    const slashDate = line.match(/\b(\d{1,2})\/(\d{1,2})(?:\/(\d{2,4}))?\b/)
    if (slashDate) {
      const d = slashDate[1].padStart(2, '0')
      const m = slashDate[2].padStart(2, '0')
      const y = slashDate[3] ? (slashDate[3].length === 2 ? `20${slashDate[3]}` : slashDate[3]) : year
      txDate = `${y}-${m}-${d}`
    } else {
      const textDate = line.match(/\b(\d{1,2})\s+(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)\b/i)
      if (textDate) {
        const d = textDate[1].padStart(2, '0')
        const m = MONTH_MAP[textDate[2].toLowerCase()] || '10'
        txDate = `${year}-${m}-${d}`
      }
    }

    // 3. Extrai descrição removendo a data e o valor da linha
    let desc = line
      .replace(/(?:R\$\s*)?(-?\s*\d{1,3}(?:\.\d{3})*,\d{2}|-?\s*\d+,\d{2})\b/g, '')
      .replace(/\b(\d{1,2})\/(\d{1,2})(?:\/(\d{2,4}))?\b/g, '')
      .replace(/\b(\d{1,2})\s+(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)\b/gi, '')
      .replace(/[-*#•]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()

    if (desc.length < 2) continue

    // 4. Parcela (ex: 2/6, 10/12)
    let parcelaAtual: number | undefined
    let totalParcelas: number | undefined
    const instMatch = line.match(/\b(\d{1,2})\/(\d{1,2})\b/)
    if (instMatch) {
      const cur = parseInt(instMatch[1], 10)
      const tot = parseInt(instMatch[2], 10)
      if (cur >= 1 && tot >= cur && tot <= 48) {
        parcelaAtual = cur
        totalParcelas = tot
      }
    }

    // 5. Tipo (income vs expense)
    const isIncome =
      !isDebitSignal &&
      (isCreditSignal ||
        /(salario|salário|remunera|folha|pro-?labore|recebido|recebimento|pix recebido|ted recebida|doc recebido|deposito|depósito|rendimento|provento|cashback|ted de |pix de )/i.test(desc))

    const absVal = Math.abs(val)
    const tipo = isIncome ? 'income' : 'expense'

    let metodoPagamento: 'pix' | 'credit_card' | 'debit' | 'bill' = 'credit_card'
    if (tipo === 'income') {
      metodoPagamento = 'pix'
    } else if (/pix/i.test(desc)) {
      metodoPagamento = 'pix'
    } else if (/debito|débito/i.test(desc)) {
      metodoPagamento = 'debit'
    } else if (/boleto|energia|luz|agua|água|condominio|tributo/i.test(desc)) {
      metodoPagamento = 'bill'
    }

    transactions.push({
      data: txDate,
      descricao: desc,
      valorTotal: absVal,
      tipo,
      metodoPagamento,
      parcelaAtual,
      totalParcelas,
    })
  }

  return transactions
}
