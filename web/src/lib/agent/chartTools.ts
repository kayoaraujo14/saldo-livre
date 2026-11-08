import type { AgentEnv, ToolResult } from './types'
import { sanitizeAmount, sanitizeText } from './security'

export async function gerarGrafico(_env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const tipo = String(args.tipo || 'donut').toLowerCase().trim()
  const titulo = sanitizeText(args.titulo || 'Gráfico Financeiro', 100)
  const descricao = args.descricao ? sanitizeText(args.descricao, 200) : undefined
  const rawDados = Array.isArray(args.dados) ? args.dados : []

  const dados = rawDados
    .map((d: any, idx: number) => {
      if (!d || typeof d !== 'object') return null
      const label = sanitizeText(d.label ?? `Série ${idx + 1}`, 50)
      const val = sanitizeAmount(d.value ?? d.valor, { min: 0 }) ?? 0
      return {
        label,
        value: val,
        color: typeof d.color === 'string' ? sanitizeText(d.color, 30) : undefined,
        sublabel: typeof d.sublabel === 'string' ? sanitizeText(d.sublabel, 50) : undefined,
      }
    })
    .filter(Boolean)

  const chartPayload = {
    type: ['donut', 'pie', 'bar', 'line'].includes(tipo) ? tipo : 'donut',
    title: titulo,
    description: descricao,
    data: dados,
  }

  const chartBlock = `\n\`\`\`chart\n${JSON.stringify(chartPayload, null, 2)}\n\`\`\`\n`

  return {
    response: {
      ok: true,
      tipo,
      titulo,
      descricao,
      dados,
      graficoMarkdown: chartBlock,
    },
    action: {
      kind: 'report',
      title: `📊 ${titulo}`,
      lines: [
        { label: 'Tipo', value: tipo.toUpperCase() },
        { label: 'Itens mapeados', value: `${dados.length} séries/categorias` },
      ],
    },
  }
}

