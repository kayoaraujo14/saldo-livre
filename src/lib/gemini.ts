/**
 * Agente Financeiro — Google Gemini (gemini-2.5-flash) com Function Calling.
 * As ferramentas manipulam diretamente o estado/Firestore através de um
 * `ToolExecutor` injetado pelo FinanceContext.
 */
import { GoogleGenAI, Type, type Content, type FunctionDeclaration, type Part } from '@google/genai'
import type { ActionCardData, ChatMessage } from '@/types'
import { CATEGORIES } from '@/types'

export const GEMINI_MODEL = 'gemini-2.5-flash'
const LS_KEY = 'saldo-livre:gemini-api-key'

export const envGeminiKey = (import.meta.env.VITE_GEMINI_API_KEY as string | undefined) || null

export function getGeminiApiKey(): string | null {
  return envGeminiKey || localStorage.getItem(LS_KEY)
}
export function setGeminiApiKey(key: string | null) {
  if (key) localStorage.setItem(LS_KEY, key.trim())
  else localStorage.removeItem(LS_KEY)
}

export type ToolName = 'registrarTransacao' | 'atualizarContaPrevista' | 'obterRelatorioEProjecaoMensal'
export interface ToolResult {
  response: Record<string, unknown>
  action?: ActionCardData
}
export type ToolExecutor = (name: ToolName, args: Record<string, unknown>) => Promise<ToolResult>

export const functionDeclarations: FunctionDeclaration[] = [
  {
    name: 'registrarTransacao',
    description:
      'Registra uma nova despesa ou receita no mês, aplicando automaticamente as regras de divisão (allocations), impacto no Saldo Livre de cada usuário (userImpact) e visibilidade (visibleToUids).',
    parameters: {
      type: Type.OBJECT,
      properties: {
        descricao: { type: Type.STRING, description: 'Descrição curta e capitalizada. Ex: "Jantar", "Farmácia".' },
        valorTotal: { type: Type.NUMBER, description: 'Valor cheio da compra em reais.' },
        categoria: { type: Type.STRING, enum: [...CATEGORIES], description: 'Categoria. Use "Cartão Terceiros" para gastos de terceiros externos.' },
        metodoPagamento: { type: Type.STRING, enum: ['pix', 'credit_card', 'debit', 'bill'] },
        status: {
          type: Type.STRING,
          enum: ['estimated', 'pending', 'paid'],
          description: 'estimated = previsão; pending = valor real a pagar (ex: compra no cartão ainda na fatura); paid = já pago.',
        },
        data: { type: Type.STRING, description: 'Data no formato YYYY-MM-DD. Padrão: hoje.' },
        tipoDivisao: {
          type: Type.STRING,
          enum: ['MEU', 'CASAL_50_50', 'PARCEIRO', 'TERCEIRO'],
          description: 'MEU = 100% do usuário; CASAL_50_50 = metade para cada; PARCEIRO = 100% do(a) parceiro(a); TERCEIRO = pessoa externa sem conta.',
        },
        nomeTerceiro: { type: Type.STRING, description: 'Obrigatório quando tipoDivisao = TERCEIRO. Ex: "Mãe".' },
        tipo: { type: Type.STRING, enum: ['expense', 'income'], description: 'Padrão: expense.' },
        cartao: { type: Type.STRING, description: 'Opcional: nome ou dono do cartão (ex: "Nubank", "cartão da parceira").' },
      },
      required: ['descricao', 'valorTotal', 'tipoDivisao'],
    },
  },
  {
    name: 'atualizarContaPrevista',
    description:
      'Localiza uma conta estimada/a pagar do mês (ex: Luz, Água, Financiamento), preenche o valor real (actualAmount), atualiza o status e retorna a diferença exata no Saldo Livre.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        buscaDescricao: { type: Type.STRING, description: 'Trecho da descrição. Ex: "luz", "água", "financiamento".' },
        valorReal: { type: Type.NUMBER, description: 'Valor real da conta em reais.' },
        novoStatus: { type: Type.STRING, enum: ['pending', 'paid'], description: 'pending se a conta apenas chegou; paid se já foi paga.' },
        dataVencimento: { type: Type.STRING, description: 'Opcional: novo vencimento YYYY-MM-DD.' },
      },
      required: ['buscaDescricao', 'valorReal', 'novoStatus'],
    },
  },
  {
    name: 'obterRelatorioEProjecaoMensal',
    description:
      'Retorna os totais atualizados do mês do usuário: Saldo Livre Real, Limite Diário Seguro, dias restantes, gastos por categoria, contas pendentes/estimadas, reservas e valores a receber de terceiros/parceiro(a).',
    parameters: { type: Type.OBJECT, properties: {} },
  },
]

export function buildSystemInstruction(ctx: {
  userName: string
  partnerName: string | null
  today: string
  monthLabel: string
  cards: string[]
}) {
  return `Você é o "Copiloto Saldo Livre", um agente financeiro pessoal brasileiro, direto e acolhedor. Responda SEMPRE em português do Brasil.
Usuário logado: ${ctx.userName}. Parceiro(a) vinculado(a): ${ctx.partnerName ?? 'nenhum'}.
Hoje é ${ctx.today}. Mês em análise: ${ctx.monthLabel}.
Cartões disponíveis: ${ctx.cards.join('; ') || 'nenhum'}.

REGRAS:
- O foco é o Saldo Livre Real do Mês = Receitas − (Já Pago + Comprometido), e o Limite Diário Seguro = Saldo Livre / dias restantes.
- Para registrar gastos/receitas use "registrarTransacao". Para contas que chegaram/foram pagas (luz, água, financiamento, fatura...) use "atualizarContaPrevista".
- Para qualquer pergunta de planejamento, análise ou "quanto posso gastar", chame "obterRelatorioEProjecaoMensal" ANTES de responder. Nunca invente números.
- "Dividido 50/50" ou "casal" → CASAL_50_50. Pessoas sem conta (mãe, pai, irmão, amigo) → TERCEIRO com nomeTerceiro capitalizado. Compras no cartão de crédito ainda não pagas → status "pending".
- Datas: "dia 22" refere-se ao mês em análise. Formato YYYY-MM-DD.
- Após executar uma ação, confirme em 1–3 frases com o impacto no Saldo Livre e no limite diário. Use **negrito** para valores e tabelas Markdown para relatórios.
- Valores em reais no formato R$ 1.234,56.`
}

function toContents(history: ChatMessage[]): Content[] {
  return history
    .filter((m) => !m.pending && !m.error && m.content.trim())
    .slice(-14)
    .map((m) => ({ role: m.role === 'user' ? 'user' : 'model', parts: [{ text: m.content }] }))
}

export async function runGeminiAgent(opts: {
  apiKey: string
  history: ChatMessage[]
  userMessage: string
  systemInstruction: string
  execute: ToolExecutor
}): Promise<{ text: string; actions: ActionCardData[] }> {
  const ai = new GoogleGenAI({ apiKey: opts.apiKey })
  const contents: Content[] = [...toContents(opts.history), { role: 'user', parts: [{ text: opts.userMessage }] }]
  const actions: ActionCardData[] = []

  for (let step = 0; step < 6; step++) {
    const res = await ai.models.generateContent({
      model: GEMINI_MODEL,
      contents,
      config: {
        systemInstruction: opts.systemInstruction,
        tools: [{ functionDeclarations }],
        temperature: 0.3,
      },
    })

    const calls = res.functionCalls
    if (!calls || calls.length === 0) {
      return { text: res.text ?? 'Pronto!', actions }
    }

    const modelContent = res.candidates?.[0]?.content
    contents.push(modelContent ?? { role: 'model', parts: calls.map((fc) => ({ functionCall: fc })) })

    const responseParts: Part[] = []
    for (const fc of calls) {
      let result: ToolResult
      try {
        result = await opts.execute(fc.name as ToolName, (fc.args ?? {}) as Record<string, unknown>)
      } catch (e) {
        result = { response: { ok: false, erro: (e as Error).message } }
      }
      if (result.action) actions.push(result.action)
      responseParts.push({ functionResponse: { id: fc.id, name: fc.name, response: result.response } })
    }
    contents.push({ role: 'user', parts: responseParts })
  }
  return { text: 'Executei as ações solicitadas.', actions }
}
