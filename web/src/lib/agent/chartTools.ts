import type { AgentEnv, ToolResult } from './types'

export async function gerarGrafico(_env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const tipo = String(args.tipo || 'donut').toLowerCase()
  const titulo = String(args.titulo || 'Gráfico Financeiro')
  const descricao = args.descricao ? String(args.descricao) : undefined
  const dados = Array.isArray(args.dados) ? args.dados : []

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

