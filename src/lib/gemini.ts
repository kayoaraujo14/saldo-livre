/**
 * Agente Financeiro — Google Gemini (gemini-3.5-flash-lite) com Function Calling.
 * As ferramentas manipulam diretamente o estado/Firestore através de um
 * `ToolExecutor` injetado pelo FinanceContext.
 */
import { GoogleGenAI, Type, type Content, type FunctionDeclaration, type Part } from '@google/genai'
import type { ActionCardData, ChatMessage } from '@/types'
import { CATEGORIES } from '@/types'

export const GEMINI_MODEL = 'gemini-3.5-flash-lite'
export const GEMINI_MODELS = [
  { id: 'gemini-3.5-flash-lite', label: 'Gemini 3.5 Flash-Lite (Padrão — Maior cota RPD e ultrarrápido)' },
  { id: 'gemini-3.8-flash', label: 'Gemini 3.8 Flash' },
  { id: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash' },
  { id: 'gemini-1.5-flash', label: 'Gemini 1.5 Flash' },
] as const

const LS_KEY = 'saldo-livre:gemini-api-key'
const LS_MODEL_KEY = 'saldo-livre:gemini-model'
const LS_MIGRATION_KEY = 'saldo-livre:model-migrated-v3.5-lite'

export const envGeminiKey = (import.meta.env.VITE_GEMINI_API_KEY as string | undefined) || null
export const envGeminiModel = (import.meta.env.VITE_GEMINI_MODEL as string | undefined) || null

export function getGeminiApiKey(): string | null {
  return envGeminiKey || localStorage.getItem(LS_KEY)
}

export function setGeminiApiKey(key: string | null) {
  if (key) localStorage.setItem(LS_KEY, key.trim())
  else localStorage.removeItem(LS_KEY)
}

export function getSelectedGeminiModel(): string {
  // Migra automaticamente para o Gemini 3.5 Flash-Lite garantindo maior cota diária (RPD)
  const migrated = localStorage.getItem(LS_MIGRATION_KEY)
  if (!migrated) {
    localStorage.setItem(LS_MIGRATION_KEY, 'true')
    localStorage.setItem(LS_MODEL_KEY, envGeminiModel || GEMINI_MODEL)
    return envGeminiModel || GEMINI_MODEL
  }

  const saved = localStorage.getItem(LS_MODEL_KEY)
  if (!saved || saved === 'gemini-2.0-flash') {
    return envGeminiModel || GEMINI_MODEL
  }
  return saved
}

export function setSelectedGeminiModel(model: string) {
  localStorage.setItem(LS_MODEL_KEY, model)
}

/**
 * Formata erros retornados pela API do Google Gemini em mensagens claras e amigáveis,
 * identificando sobrecargas temporárias (503/alta demanda) e limites de requisição (429).
 */
export function formatGeminiErrorMessage(err: unknown): string {
  if (!err) return 'Erro desconhecido ao comunicar com a inteligência artificial.'
  const raw = (err as Error)?.message || String(err)

  try {
    const parsed = JSON.parse(raw)
    const errObj = parsed.error || parsed
    if (
      errObj.code === 503 ||
      errObj.status === 'UNAVAILABLE' ||
      errObj.message?.toLowerCase().includes('high demand') ||
      errObj.message?.toLowerCase().includes('spikes in demand') ||
      errObj.message?.toLowerCase().includes('unavailable')
    ) {
      return 'O modelo do Gemini está enfrentando alta demanda temporária nos servidores do Google (503). Por favor, aguarde alguns segundos e tente novamente.'
    }
    if (errObj.code === 429 || errObj.status === 'RESOURCE_EXHAUSTED') {
      return 'Limite de requisições por minuto (RPM) atingido temporariamente no Google AI Studio. Aguarde alguns segundos.'
    }
    if (errObj.message) {
      return errObj.message
    }
  } catch {
    const lower = raw.toLowerCase()
    if (lower.includes('503') || lower.includes('high demand') || lower.includes('unavailable') || lower.includes('spikes in demand')) {
      return 'O modelo do Gemini está enfrentando alta demanda temporária nos servidores do Google (503). Por favor, aguarde alguns segundos e tente novamente.'
    }
    if (lower.includes('429') || lower.includes('resource_exhausted')) {
      return 'Limite de requisições temporariamente atingido no Google AI Studio. Aguarde alguns instantes.'
    }
  }

  return raw
}

/**
 * Valida a conexão com o Google Gemini realizando uma chamada leve de teste.
 * Possui retry e fallback automático caso o modelo padrão esteja em pico de demanda temporária (503).
 */
export async function testGeminiConnection(
  key: string,
  model = getSelectedGeminiModel(),
): Promise<{ ok: boolean; message: string; modelUsed?: string }> {
  const trimmed = key.trim()
  if (!trimmed) {
    return { ok: false, message: 'Chave da API não fornecida.' }
  }

  const ai = new GoogleGenAI({ apiKey: trimmed })
  const candidates = [
    model,
    'gemini-3.5-flash-lite',
    'gemini-3.8-flash',
    'gemini-2.5-flash',
    'gemini-1.5-flash',
  ].filter((m, idx, arr) => arr.indexOf(m) === idx)

  let lastErr: unknown = null

  for (const candidate of candidates) {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const res = await ai.models.generateContent({
          model: candidate,
          contents: 'Olá! Responda apenas "OK" para teste de conexão.',
        })
        if (res.text) {
          if (candidate !== model) {
            setSelectedGeminiModel(candidate)
          }
          return {
            ok: true,
            message: `Conexão estabelecida com sucesso com o Google Gemini (${candidate})!`,
            modelUsed: candidate,
          }
        }
      } catch (err) {
        lastErr = err
        const errStr = ((err as Error)?.message || String(err)).toLowerCase()
        const isTransient =
          errStr.includes('503') ||
          errStr.includes('unavailable') ||
          errStr.includes('high demand') ||
          errStr.includes('429')

        if (attempt === 0 && isTransient) {
          await new Promise((r) => setTimeout(r, 1000))
          continue
        }
        break
      }
    }
  }

  return { ok: false, message: formatGeminiErrorMessage(lastErr) }
}

export type ToolName =
  | 'registrarTransacao'
  | 'registrarTransacoesLote'
  | 'editarTransacoes'
  | 'excluirTransacoes'
  | 'expandirParcelamentos'
  | 'atualizarContaPrevista'
  | 'obterRelatorioEProjecaoMensal'

export interface ToolResult {
  response: Record<string, unknown>
  action?: ActionCardData
}
export type ToolExecutor = (name: ToolName, args: Record<string, unknown>) => Promise<ToolResult>

export const functionDeclarations: FunctionDeclaration[] = [
  {
    name: 'registrarTransacao',
    description:
      'Registra uma nova despesa ou receita, calculando automaticamente as regras de divisão, impacto no Saldo Livre e o mês de competência correto da fatura do cartão ou vencimento.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        descricao: { type: Type.STRING, description: 'Descrição da despesa ou receita. Ex: "Decolar", "Amazon", "Uber".' },
        valorTotal: { type: Type.NUMBER, description: 'Valor da compra em reais (aceita valores negativos para estornos/reembolsos na fatura).' },
        categoria: { type: Type.STRING, enum: [...CATEGORIES], description: 'Categoria da despesa. Use "Cartão Terceiros" para terceiros sem conta.' },
        metodoPagamento: { type: Type.STRING, enum: ['pix', 'credit_card', 'debit', 'bill'], description: 'Padrão: credit_card.' },
        status: {
          type: Type.STRING,
          enum: ['estimated', 'pending', 'paid'],
          description: 'pending para compras no cartão de crédito na fatura; paid se já pago; estimated para previsão.',
        },
        data: { type: Type.STRING, description: 'Data da compra no formato YYYY-MM-DD. Padrão: hoje.' },
        mesFatura: {
          type: Type.STRING,
          description: 'Mês de competência (YYYY-MM) que comprometerá o Saldo Livre. Ex: "2026-11". Essencial para cartão cuja fatura já fechou ou vence no mês seguinte!',
        },
        dataVencimento: {
          type: Type.STRING,
          description: 'Data de vencimento da fatura/conta (YYYY-MM-DD). Ex: "2026-11-14".',
        },
        tipoDivisao: {
          type: Type.STRING,
          enum: ['MEU', 'CASAL_50_50', 'PARCEIRO', 'AMIGO', 'TERCEIRO', 'PERSONALIZADA'],
          description: 'MEU = 100% do usuário; CASAL_50_50 = 50% para cada; PARCEIRO = 100% do parceiro; AMIGO = amigo/namorado cadastrado na plataforma; TERCEIRO = pessoa sem conta (ex: Mãe); PERSONALIZADA = divisão por porcentagem ou valor exato.',
        },
        nomeAmigo: { type: Type.STRING, description: 'Nome do amigo na plataforma quando tipoDivisao = AMIGO ou PERSONALIZADA. Ex: "Kayo".' },
        emailAmigo: { type: Type.STRING, description: 'Email do amigo cadastrado na plataforma quando tipoDivisao = AMIGO ou PERSONALIZADA. Ex: "kayoaraujo2002@gmail.com".' },
        porcentagemAmigo: { type: Type.NUMBER, description: 'Porcentagem (1 a 100) que o amigo pagará (ex: 40 para 40%, 50 para 50%). Padrão se for 100% dele: 100.' },
        valorAmigo: { type: Type.NUMBER, description: 'Valor exato em reais que o amigo pagará (ex: 60.00). O sistema calcula a porcentagem automaticamente!' },
        porcentagemMinha: { type: Type.NUMBER, description: 'Porcentagem (1 a 100) que o usuário logado pagará (ex: 60 para 60%).' },
        valorMeu: { type: Type.NUMBER, description: 'Valor exato em reais que o usuário logado pagará.' },
        nomeTerceiro: { type: Type.STRING, description: 'Nome do terceiro sem conta quando tipoDivisao = TERCEIRO. Ex: "Mãe".' },
        divisoesPersonalizadas: {
          type: Type.ARRAY,
          description: 'Lista de participantes quando a divisão envolver mais de 2 pessoas ou valores específicos para cada um.',
          items: {
            type: Type.OBJECT,
            properties: {
              nome: { type: Type.STRING, description: 'Nome da pessoa (ex: "Você", "Kayo", "Mãe").' },
              email: { type: Type.STRING, description: 'Email se for amigo na plataforma.' },
              porcentagem: { type: Type.NUMBER, description: 'Porcentagem da conta (ex: 40).' },
              valor: { type: Type.NUMBER, description: 'Valor em reais (ex: 60.00).' },
            },
            required: ['nome'],
          },
        },
        tipo: { type: Type.STRING, enum: ['expense', 'income'], description: 'Padrão: expense.' },
        cartao: { type: Type.STRING, description: 'Opcional: nome ou titular do cartão.' },
        parcelaAtual: { type: Type.NUMBER, description: 'Número da parcela atual (ex: 2 para "2/6").' },
        totalParcelas: { type: Type.NUMBER, description: 'Total de parcelas (ex: 6 para "2/6").' },
      },
      required: ['descricao', 'valorTotal', 'tipoDivisao'],
    },
  },
  {
    name: 'registrarTransacoesLote',
    description:
      'Registra múltiplas despesas/receitas em lote (de uma única vez). Ideal quando o usuário cola uma lista com vários gastos de fatura de cartão de crédito.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        mesFaturaPadrao: { type: Type.STRING, description: 'Mês de competência padrão para todas as despesas do lote (YYYY-MM). Ex: "2026-11".' },
        dataVencimentoPadrao: { type: Type.STRING, description: 'Data de vencimento padrão da fatura para todas as despesas (YYYY-MM-DD). Ex: "2026-11-14".' },
        cartaoPadrao: { type: Type.STRING, description: 'Nome do cartão padrão para o lote.' },
        transacoes: {
          type: Type.ARRAY,
          description: 'Lista de despesas a serem cadastradas.',
          items: {
            type: Type.OBJECT,
            properties: {
              descricao: { type: Type.STRING, description: 'Nome/descrição do gasto.' },
              valorTotal: { type: Type.NUMBER, description: 'Valor em reais (negativo se for estorno/crédito).' },
              categoria: { type: Type.STRING, enum: [...CATEGORIES] },
              tipoDivisao: { type: Type.STRING, enum: ['MEU', 'CASAL_50_50', 'PARCEIRO', 'AMIGO', 'TERCEIRO', 'PERSONALIZADA'] },
              nomeAmigo: { type: Type.STRING },
              emailAmigo: { type: Type.STRING },
              porcentagemAmigo: { type: Type.NUMBER },
              valorAmigo: { type: Type.NUMBER },
              porcentagemMinha: { type: Type.NUMBER },
              valorMeu: { type: Type.NUMBER },
              nomeTerceiro: { type: Type.STRING },
              divisoesPersonalizadas: {
                type: Type.ARRAY,
                items: {
                  type: Type.OBJECT,
                  properties: {
                    nome: { type: Type.STRING },
                    email: { type: Type.STRING },
                    porcentagem: { type: Type.NUMBER },
                    valor: { type: Type.NUMBER },
                  },
                  required: ['nome'],
                },
              },
              parcelaAtual: { type: Type.NUMBER },
              totalParcelas: { type: Type.NUMBER },
              data: { type: Type.STRING },
              mesFatura: { type: Type.STRING },
              dataVencimento: { type: Type.STRING },
            },
            required: ['descricao', 'valorTotal', 'tipoDivisao'],
          },
        },
      },
      required: ['transacoes'],
    },
  },
  {
    name: 'editarTransacoes',
    description:
      'Permite editar e corrigir transações existentes, inclusive em lote (ex: mover despesas para o mês de fatura correto [YYYY-MM], alterar vencimento, reatribuir para amigo na plataforma ou alterar valores/descrições).',
    parameters: {
      type: Type.OBJECT,
      properties: {
        ids: { type: Type.ARRAY, items: { type: Type.STRING }, description: 'IDs das transações a editar (opcional).' },
        termoBusca: { type: Type.STRING, description: 'Filtra transações pela descrição (ex: "KAYO", "DECOLAR", "99FOOD").' },
        mesOrigem: { type: Type.STRING, description: 'Mês atual das transações a serem filtradas (YYYY-MM). Ex: "2026-10".' },
        novoMesFatura: { type: Type.STRING, description: 'Novo mês de competência da fatura (YYYY-MM). Ex: "2026-11" para mover para novembro!' },
        novaDataVencimento: { type: Type.STRING, description: 'Nova data de vencimento da fatura (YYYY-MM-DD). Ex: "2026-11-14".' },
        novoValor: { type: Type.NUMBER, description: 'Novo valor corrigido da compra.' },
        novaDescricao: { type: Type.STRING, description: 'Nova descrição.' },
        novaCategoria: { type: Type.STRING, enum: [...CATEGORIES] },
        novoStatus: { type: Type.STRING, enum: ['estimated', 'pending', 'paid'] },
        novoTipoDivisao: { type: Type.STRING, enum: ['MEU', 'CASAL_50_50', 'PARCEIRO', 'AMIGO', 'TERCEIRO'] },
        novoAmigoEmail: { type: Type.STRING, description: 'Atribui a despesa a um amigo pelo email na plataforma. Ex: "kayoaraujo2002@gmail.com".' },
        novoNomeTerceiro: { type: Type.STRING, description: 'Nome do terceiro externo se for o caso (ex: "Mãe").' },
        limiteQuantidade: { type: Type.NUMBER, description: 'Máximo de transações a alterar.' },
      },
    },
  },
  {
    name: 'excluirTransacoes',
    description:
      'Exclui uma ou múltiplas transações, permitindo exclusão individual por ID ou em lote por termo de busca, mês ou lote recente.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        ids: { type: Type.ARRAY, items: { type: Type.STRING }, description: 'Lista de IDs específicos a excluir.' },
        termoBusca: { type: Type.STRING, description: 'Exclui transações cuja descrição corresponda à busca (ex: "99FOOD", "DECOLAR").' },
        mes: { type: Type.STRING, description: 'Filtra pelo mês de competência (YYYY-MM). Ex: "2026-10".' },
        tudoDoMes: { type: Type.BOOLEAN, description: 'Se true, exclui todas as transações do mês informado.' },
        apenasRecentes: { type: Type.BOOLEAN, description: 'Se true, exclui as últimas transações cadastradas recentemente.' },
        limiteQuantidade: { type: Type.NUMBER, description: 'Quantidade máxima de itens a excluir (padrão: 50).' },
      },
    },
  },
  {
    name: 'expandirParcelamentos',
    description:
      'Varre as compras parceladas cadastradas no mês informado (padrão: 2026-11) e gera automaticamente todas as parcelas passadas (marcadas como pagas) e parcelas futuras (como pendentes) que estejam faltando, comprometendo cada mês correspondente de forma contínua no orçamento.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        mes: {
          type: Type.STRING,
          description: 'Mês de competência das compras parceladas cadastradas (YYYY-MM). Ex: "2026-11". Padrão: 2026-11.',
        },
      },
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
  monthKey: string
  cards: Array<{ id: string; name: string; owner: string; closingDay: number; dueDay: number }>
  friends: Array<{ uid: string; name: string; email: string }>
}) {
  const cardsDesc = ctx.cards
    .map((c) => `${c.name} (titular: ${c.owner}, fecha dia ${c.closingDay}, vence dia ${c.dueDay})`)
    .join('; ') || 'nenhum'

  const friendsDesc = ctx.friends
    .map((f) => `${f.name} (email: ${f.email}, uid: ${f.uid})`)
    .join('; ') || 'nenhum'

  return `Você é o "Copiloto Saldo Livre", um agente financeiro pessoal brasileiro, ultra-preciso, direto e acolhedor. Responda SEMPRE em português do Brasil.

CONTEXTO ATUAL:
- Usuário logado: ${ctx.userName}.
- Parceiro(a) vinculado(a): ${ctx.partnerName ?? 'nenhum'}.
- Hoje é: ${ctx.today}.
- Mês em análise na tela: ${ctx.monthLabel} (${ctx.monthKey}).
- Cartões cadastrados: ${cardsDesc}.
- Amigos na plataforma: ${friendsDesc}.

REGRAS DE FUNCIONAMENTO:

1. CARTÕES DE CRÉDITO, FATURA E MÊS DE COMPETÊNCIA:
- Compras no cartão de crédito comprometem a renda do MÊS DE VENCIMENTO DA FATURA (competenceMonth), e NÃO a data em que a compra foi feita!
- Se o usuário disser que os gastos virão para a fatura com vencimento em determinado dia/mês (ex: "vence 14/11" ou "fatura de novembro"):
  Defina obrigatoriamente: dataVencimento = "2026-11-14" e mesFatura = "2026-11"! O orçamento que deve ser comprometido é novembro, e não outubro!
- Se a compra foi feita após o dia de fechamento (closingDay) do cartão, a fatura fecha no ciclo seguinte e compromete o mês de vencimento (dueDay).

2. AMIGOS NA REDE E DIVISÃO DE CONTAS (PORCENTAGEM, VALORES ESPECÍFICOS E MULTI-PESSOAS):
- Se o usuário disser que um gasto é de um amigo na plataforma (ex: "KAYO", "kayoaraujo2002@gmail.com"):
  * Identifique o amigo (emailAmigo = "kayoaraujo2002@gmail.com", nomeAmigo = "Kayo").
  * Se for 100% do amigo: use tipoDivisao = "AMIGO" (ou porcentagemAmigo = 100).
  * Se o usuário disser que dividiu com o amigo em porcentagem ou valor (ex: "almoço R$ 150 onde Kayo paga 40% e eu 60%", "Kayo paga R$ 50", "dividi com Kayo meio a meio"):
    Preencha porcentagemAmigo: 40 (ou valorAmigo: 50). O sistema calcula os valores exatos, aloca a cota correta de cada um, gera a cobrança com aceite mútuo para o amigo e desconta do Saldo Livre do usuário apenas a parte dele!
  * Se a despesa for dividida entre mais de 2 pessoas (ex: "Eu, Kayo e minha Mãe"):
    Preencha divisoesPersonalizadas com cada participante (nome, email se amigo, porcentagem ou valor).
- Se for pessoa sem conta na plataforma (ex: "Mãe", "Pai", "Irmão"):
  Use tipoDivisao = "TERCEIRO" com nomeTerceiro = "Mãe".

3. ESTORNOS E REEMBOLSOS (VALORES NEGATIVOS):
- Se o gasto for negativo (ex: "99FOOD -R$ 8,40" ou "UBER -R$ 4,09"), defina obrigatoriamente categoria = "Estorno" e envie o valor negativo (ex: -8.40). Trata-se de estorno/crédito na fatura que alivia o Saldo Livre.

4. COMPRAS PARCELADAS:
- Notações como "9/12", "2/6" ou "10/12" indicam compras parceladas. Preencha parcelaAtual e totalParcelas (ex: parcelaAtual: 9, totalParcelas: 12).
- O sistema aloca AUTOMATICAMENTE toda a série de parcelas no banco de dados pelos meses correspondentes (passadas como pagas, atual e futuras como pendentes).
- IMPORTANTE: Confirme a operação de forma simples e direta no mês atual. NÃO fique gerando previsões ou projeções detalhadas de meses futuros ao final da resposta a menos que o usuário peça especificamente.
- Se o usuário pedir para gerar, expandir ou sincronizar as parcelas passadas e futuras de compras já existentes:
  Chame a ferramenta "expandirParcelamentos" com mes: "2026-11"!

5. REGISTRO EM LOTE:
- Quando o usuário enviar uma lista com vários gastos de uma vez, use SEMPRE a ferramenta "registrarTransacoesLote". Isso processa todas as despesas em uma única chamada rápida!

6. EDIÇÃO E EXCLUSÃO (INCLUSIVE EM LOTE):
- O usuário pode pedir para alterar ou excluir despesas já cadastradas a qualquer momento.
- Se o usuário pedir para mover despesas cadastradas por engano em outubro para a fatura de novembro:
  Chame "editarTransacoes" com mesOrigem: "2026-10", novoMesFatura: "2026-11" e novaDataVencimento: "2026-11-14"!
- Se o usuário pedir para reatribuir gastos para o Kayo:
  Chame "editarTransacoes" com termoBusca: "KAYO", novoTipoDivisao: "AMIGO", novoAmigoEmail: "kayoaraujo2002@gmail.com".
- Se o usuário pedir para excluir despesas duplicadas ou incorretas:
  Chame "excluirTransacoes" com termoBusca, mes ou apenasRecentes!

7. RESPOSTAS DIRETAS, CIRÚRGICAS E SEM PROJEÇÕES NÃO SOLICITADAS:
- Seja ultra-útil, inteligente e direto ao ponto. Aprenda com as preferências do usuário.
- Confirme a operação realizada e o novo Saldo Livre do mês em questão.
- NÃO adicione parágrafos de projeção do futuro ao final das respostas (ex: "Para os próximos meses...", "Sua projeção futura é..."). Responda estritamente ao que foi pedido! Projeções só devem ser apresentadas se o usuário fizer uma pergunta explícita sobre o futuro ou pedir um planejamento/relatório.`
}


/**
 * Constrói o histórico de mensagens garantindo as regras rígidas da API do Gemini:
 * 1. O primeiro turno DEVE ser do papel 'user' (descarta mensagem de boas-vindas do assistente).
 * 2. Os turnos devem alternar rigorosamente entre 'user' e 'model'.
 * 3. A mensagem atual do usuário é incluída como o turno final de 'user'.
 */
export function buildChatContents(history: ChatMessage[], newUserText: string): Content[] {
  const filtered = history.filter(
    (m) => m.id !== 'msg_welcome' && !m.pending && !m.error && m.content && m.content.trim(),
  )

  const turns: Content[] = []
  for (const m of filtered.slice(-12)) {
    const role: 'user' | 'model' = m.role === 'user' ? 'user' : 'model'
    if (turns.length === 0 && role !== 'user') continue

    const lastTurn = turns[turns.length - 1]
    if (lastTurn && lastTurn.role === role) {
      if (!lastTurn.parts) lastTurn.parts = []
      lastTurn.parts.push({ text: m.content })
    } else {
      turns.push({ role, parts: [{ text: m.content }] })
    }
  }

  const lastTurn = turns[turns.length - 1]
  if (lastTurn && lastTurn.role === 'user') {
    if (!lastTurn.parts) lastTurn.parts = []
    lastTurn.parts.push({ text: newUserText })
  } else {
    turns.push({ role: 'user', parts: [{ text: newUserText }] })
  }

  return turns
}

async function callGeminiWithResilience(params: {
  ai: GoogleGenAI
  model: string
  contents: Content[]
  systemInstruction: string
  tools: Array<{ functionDeclarations: FunctionDeclaration[] }>
  onModelChanged?: (newModel: string) => void
}): Promise<{ res: any; activeModel: string }> {
  const { ai, contents, systemInstruction, tools, onModelChanged } = params
  let currentModel = params.model

  const fallbackPool = [
    'gemini-3.5-flash-lite',
    'gemini-3.8-flash',
    'gemini-2.5-flash',
    'gemini-1.5-flash',
  ]

  const modelsToTry = [
    currentModel,
    ...fallbackPool.filter((m) => m !== currentModel),
  ]

  let lastError: unknown = null

  for (let mIdx = 0; mIdx < modelsToTry.length; mIdx++) {
    const candidate = modelsToTry[mIdx]

    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const res = await ai.models.generateContent({
          model: candidate,
          contents,
          config: {
            systemInstruction,
            tools,
            temperature: 0.3,
          },
        })

        if (!res) {
          throw new Error(`Nenhuma resposta retornada pelo modelo ${candidate}.`)
        }

        if (candidate !== currentModel) {
          currentModel = candidate
          onModelChanged?.(candidate)
          setSelectedGeminiModel(candidate)
        }

        return { res, activeModel: currentModel }
      } catch (err) {
        lastError = err
        const errStr = ((err as Error)?.message || String(err)).toLowerCase()
        const isTransient =
          errStr.includes('503') ||
          errStr.includes('unavailable') ||
          errStr.includes('high demand') ||
          errStr.includes('spikes in demand') ||
          errStr.includes('429') ||
          errStr.includes('resource_exhausted') ||
          errStr.includes('quota')

        // Se for erro transitório de sobrecarga (503 / 429), aguarda 1.2s antes de tentar novamente o mesmo modelo
        if (attempt === 0 && isTransient) {
          await new Promise((r) => setTimeout(r, 1200))
          continue
        }

        // Se falhou na 2ª tentativa ou for erro permanente, avança para o próximo modelo do pool
        break
      }
    }
  }

  throw lastError
}

export async function runGeminiAgent(opts: {
  apiKey: string
  history: ChatMessage[]
  userMessage: string
  systemInstruction: string
  execute: ToolExecutor
  model?: string
  onModelChanged?: (newModel: string) => void
}): Promise<{ text: string; actions: ActionCardData[] }> {
  const ai = new GoogleGenAI({ apiKey: opts.apiKey })
  const contents: Content[] = buildChatContents(opts.history, opts.userMessage)
  const actions: ActionCardData[] = []
  let activeModel = opts.model || getSelectedGeminiModel() || GEMINI_MODEL

  for (let step = 0; step < 6; step++) {
    let res: any
    try {
      const outcome = await callGeminiWithResilience({
        ai,
        model: activeModel,
        contents,
        systemInstruction: opts.systemInstruction,
        tools: [{ functionDeclarations }],
        onModelChanged: (m) => {
          activeModel = m
          opts.onModelChanged?.(m)
        },
      })
      res = outcome.res
      activeModel = outcome.activeModel
    } catch (err) {
      // Se ferramentas já foram executadas com sucesso no Firestore/estado, NUNCA lance exceção assustando o usuário!
      // Retorne a confirmação segura com as ações que já foram devidamente gravadas.
      if (actions.length > 0) {
        const linesSummary = actions
          .map((act) => {
            const details = act.lines.map((l) => `${l.label}: **${l.value}**`).join(' • ')
            return `• **${act.title}**: ${details}`
          })
          .join('\n')

        return {
          text: `✅ **Lançamento registrado com sucesso!**\n\n${linesSummary}\n\n*(Nota: O Google Gemini apresentou oscilação temporária de alta demanda [503] ao gerar o texto de resposta, mas seu lançamento foi salvo e computado normalmente no seu Saldo Livre.)*`,
          actions,
        }
      }
      throw err
    }

    const calls = res.functionCalls
    if (!calls || calls.length === 0) {
      return { text: res.text ?? 'Pronto!', actions }
    }

    const modelContent = res.candidates?.[0]?.content
    contents.push(modelContent ?? { role: 'model', parts: calls.map((fc: any) => ({ functionCall: fc })) })

    const responseParts: Part[] = []
    for (const fc of calls) {
      let result: ToolResult
      try {
        result = await opts.execute(fc.name as ToolName, (fc.args ?? {}) as Record<string, unknown>)
      } catch (e) {
        result = { response: { ok: false, erro: (e as Error).message } }
      }
      if (result.action) actions.push(result.action)
      responseParts.push({
        functionResponse: {
          ...(fc.id ? { id: fc.id } : {}),
          name: fc.name,
          response: result.response,
        },
      })
    }
    contents.push({ role: 'user', parts: responseParts })
  }
  return { text: 'Executei as ações solicitadas.', actions }
}

