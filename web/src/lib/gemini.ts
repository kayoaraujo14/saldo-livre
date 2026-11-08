/**
 * Agente Financeiro — Google Gemini (gemini-3.5-flash-lite) com Function Calling.
 * As ferramentas manipulam diretamente o estado/Firestore através de um
 * `ToolExecutor` injetado pelo FinanceContext.
 */
import { GoogleGenAI, Type, type Content, type FunctionDeclaration, type Part } from '@google/genai'
import type {
  AccountCashFlowSummary,
  ActionCardData,
  BudgetInsight,
  BudgetInsightAction,
  CategoryBudgetReport,
  ChatMessage,
  ChatAttachment,
  FinancialHealthScore,
  PendingBatchTransaction,
  SmartBudgetPlan,
  SmartBudgetAllocation,
  FinancialHealthAiDiagnosis,
} from '@/types'
import { ACTIVE_CATEGORIES, CATEGORIES } from '@/types'
import { sanitizeAndValidateBudgetPlan, normalizeBudgetCategory } from './finance'
import { round2 } from './utils'

export const GEMINI_MODEL = 'gemini-3.5-flash'
export const DEFAULT_MODELS_POOL = [
  'gemini-3.5-flash',
  'gemini-3.5-flash-lite',
  'gemini-flash-latest',
  'gemini-flash-lite-latest',
  'gemini-3.1-flash-lite',
  'gemini-pro-latest',
] as const

export const GEMINI_MODELS = [
  { id: 'gemini-3.5-flash', label: 'Gemini 3.5 Flash (Padrão — Mais Rápido e Atual)' },
  { id: 'gemini-3.5-flash-lite', label: 'Gemini 3.5 Flash-Lite (Alta Disponibilidade)' },
  { id: 'gemini-flash-latest', label: 'Gemini Flash Latest (Estável)' },
  { id: 'gemini-flash-lite-latest', label: 'Gemini Flash-Lite Latest (Ultra Leve)' },
  { id: 'gemini-3.1-flash-lite', label: 'Gemini 3.1 Flash-Lite' },
  { id: 'gemini-pro-latest', label: 'Gemini Pro Latest (Raciocínio Profundo)' },
  { id: 'gemini-3-flash', label: 'Gemini 3 Flash' },
  { id: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
  { id: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash' },
] as const

const LS_KEY = 'saldo-livre:gemini-api-key'
const LS_MODEL_KEY = 'saldo-livre:gemini-model'
const LS_MIGRATION_KEY = 'saldo-livre:model-migrated-v3-flash'

export const envGeminiKey = (import.meta.env.VITE_GEMINI_API_KEY as string | undefined) || null
export const envGeminiModel = (import.meta.env.VITE_GEMINI_MODEL as string | undefined) || null

export function getGeminiApiKeysRaw(): string {
  return (envGeminiKey || localStorage.getItem(LS_KEY) || '').trim()
}

export function getGeminiApiKeys(): string[] {
  const raw = getGeminiApiKeysRaw()
  if (!raw) return []
  return raw
    .split(/[,;\n]/)
    .map((k) => k.trim())
    .filter(Boolean)
}

export function getGeminiApiKey(): string | null {
  const keys = getGeminiApiKeys()
  return keys.length > 0 ? keys[0] : null
}

export function setGeminiApiKey(key: string | null) {
  if (key) localStorage.setItem(LS_KEY, key.trim())
  else localStorage.removeItem(LS_KEY)
}

export function getSelectedGeminiModel(): string {
  // Migra automaticamente para o Gemini 3.5 Flash garantindo o melhor equilíbrio entre velocidade e inteligência
  const migrated = localStorage.getItem(LS_MIGRATION_KEY)
  if (!migrated) {
    localStorage.setItem(LS_MIGRATION_KEY, 'true')
    localStorage.setItem(LS_MODEL_KEY, envGeminiModel || GEMINI_MODEL)
    return envGeminiModel || GEMINI_MODEL
  }

  const saved = localStorage.getItem(LS_MODEL_KEY)
  // Limpa modelos inexistentes ou depreciados do local storage
  if (!saved || saved === 'gemini-2.0-flash' || saved === 'gemini-1.5-flash') {
    return envGeminiModel || GEMINI_MODEL
  }
  return saved
}

export function setSelectedGeminiModel(model: string) {
  localStorage.setItem(LS_MODEL_KEY, model)
}

/**
 * Roteador de Modelos (Router Pattern):
 * Analisa a intenção da mensagem do usuário e eleva para o modelo Pro se for análise complexa,
 * ou mantém o modelo Flash para operações e cadastros rápidos.
 */
export function resolveTargetModel(userQuery: string, preferredModel: string): string {
  const analysisRegex =
    /(analis(?:e|ar)|relat(?:ório|orios)|auditori|diagn[óo]stico|dre|balan[çc]o|resumo detalhado|compara[çc][ãa]o|proje[çc][ãa]o)/i
  return analysisRegex.test(userQuery) ? 'gemini-pro-latest' : preferredModel
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
  const keys = key
    .split(/[,;\n]/)
    .map((k) => k.trim())
    .filter(Boolean)

  if (keys.length === 0) {
    return { ok: false, message: 'Chave da API não fornecida.' }
  }

  const testKey = keys[0]
  const ai = new GoogleGenAI({ apiKey: testKey })
  const candidates = [
    model,
    'gemini-3.5-flash',
    'gemini-3.5-flash-lite',
    'gemini-flash-latest',
    'gemini-flash-lite-latest',
    'gemini-3.1-flash-lite',
    'gemini-pro-latest',
    'gemini-3-flash',
    'gemini-2.5-pro',
    'gemini-2.5-flash',
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
          const suffix = keys.length > 1 ? ` (${keys.length} chaves prontas para Load Balancing)` : ''
          return {
            ok: true,
            message: `Conexão estabelecida com sucesso com o Google Gemini (${candidate})!${suffix}`,
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
  | 'consultarTransacoes'
  | 'expandirParcelamentos'
  | 'atualizarContaPrevista'
  | 'obterRelatorioEProjecaoMensal'
  | 'gerenciarCartoes'
  | 'gerenciarContasRecorrentes'
  | 'gerenciarAmizades'
  | 'gerenciarRegrasEMemoria'
  | 'gerarGraficoFinanceiro'
  | 'gerenciarEnvelopesOrcamentarios'

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
        valorTotal: { type: Type.NUMBER, description: 'Valor total da compra em reais (ou valor total do parcelamento). Se informado junto com totalParcelas, o sistema divide automaticamente entre as parcelas. Aceita valores negativos para estornos.' },
        valorParcela: { type: Type.NUMBER, description: 'Valor de cada parcela em reais quando a compra for parcelada (ex: R$ 50 para 3x de R$ 50).' },
        categoria: { type: Type.STRING, enum: [...ACTIVE_CATEGORIES], description: 'Categoria oficial da despesa ou receita (ex: Alimentação, Moradia, Transporte, Salário & Remuneração, etc.).' },
        subcategoria: { type: Type.STRING, description: 'Subcategoria detalhada da despesa (ex: "Supermercado", "Restaurante", "Delivery & Lanches", "Combustível", "Farmácia", "Aluguel & Condomínio", "Energia Elétrica", "Presentes", etc.).' },
        metodoPagamento: { type: Type.STRING, enum: ['pix', 'credit_card', 'debit', 'bill'], description: 'Padrão: credit_card.' },
        status: {
          type: Type.STRING,
          enum: ['estimated', 'pending', 'paid'],
          description: 'pending para compras no cartão de crédito na fatura; paid se já pago; estimated para previsão.',
        },
        data: { type: Type.STRING, description: 'Data da realização da compra no formato YYYY-MM-DD (ex: se disse "ontem", calcule a data de ontem; se "hoje", hoje). Padrão: hoje.' },
        mesFatura: {
          type: Type.STRING,
          description: 'Mês de competência (YYYY-MM) que comprometerá o Saldo Livre. Ex: "2026-11". Essencial para cartão cuja fatura já fechou ou vence no mês seguinte!',
        },
        dataVencimento: {
          type: Type.STRING,
          description: 'Opcional: Data de vencimento da fatura/conta (YYYY-MM-DD). Ex: "2026-11-14". Não confunda com a data da compra!',
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
      'Registra múltiplas despesas ou receitas/entradas em lote de faturas de cartão de crédito ou extratos de conta corrente.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        origemDocumento: {
          type: Type.STRING,
          enum: ['extrato_conta', 'fatura_cartao'],
          description:
            'OBRIGATÓRIO: "extrato_conta" se for extrato bancário de conta corrente/poupança (Pix, débitos, salários, etc.); "fatura_cartao" se for fatura de cartão de crédito.',
        },
        mesFaturaPadrao: {
          type: Type.STRING,
          description:
            'Mês de competência padrão (YYYY-MM). Para fatura de cartão: mês de vencimento (ex: "2026-11"). Para extrato de conta corrente: mês de ocorrência das transações (ex: "2026-10").',
        },
        dataVencimentoPadrao: {
          type: Type.STRING,
          description: 'Data de vencimento padrão (YYYY-MM-DD). Para fatura: vencimento da fatura.',
        },
        metodoPagamentoPadrao: {
          type: Type.STRING,
          enum: ['pix', 'credit_card', 'debit', 'bill'],
          description:
            'Método de pagamento padrão para o lote. "pix" ou "debit" para extratos bancários de conta corrente. "credit_card" SOMENTE para faturas de cartão de crédito.',
        },
        cartaoPadrao: {
          type: Type.STRING,
          description: 'Nome do cartão de crédito (se for fatura de cartão de crédito). Não preencher para extrato de conta!',
        },
        transacoes: {
          type: Type.ARRAY,
          description: 'Lista de despesas e receitas a serem cadastradas.',
          items: {
            type: Type.OBJECT,
            properties: {
              descricao: { type: Type.STRING, description: 'Nome/descrição do lançamento (ex: "Salário Empresa", "iFood", "Supermercado").' },
              valorTotal: { type: Type.NUMBER, description: 'Valor positivo em reais (se for estorno na fatura, use valor negativo).' },
              tipo: {
                type: Type.STRING,
                enum: ['expense', 'income'],
                description:
                  'CRÍTICO: "income" para RECEITAS/ENTRADAS/SALÁRIOS/PIX RECEBIDOS/DEPÓSITOS/RENDIMENTOS; "expense" para gastos/saídas/compras.',
              },
              metodoPagamento: {
                type: Type.STRING,
                enum: ['pix', 'credit_card', 'debit', 'bill'],
                description:
                  '"pix" para Pix enviado ou recebido; "debit" para débito em conta corrente; "bill" para boletos; "credit_card" SOMENTE se for compra no cartão de crédito.',
              },
              status: {
                type: Type.STRING,
                enum: ['paid', 'pending', 'estimated'],
                description:
                  '"paid" para extrato de conta corrente (já liquidado na conta); "pending" para compras no cartão na fatura a vencer.',
              },
              categoria: { type: Type.STRING, enum: [...ACTIVE_CATEGORIES], description: 'Categoria oficial da despesa ou receita (ex: Alimentação, Moradia, Salário & Remuneração, etc.).' },
              subcategoria: { type: Type.STRING, description: 'Subcategoria específica (ex: "Supermercado", "Restaurante", "Delivery & Lanches", "Combustível", "Farmácia", "Presentes", "Energia Elétrica", etc.).' },
              contraparte: { type: Type.STRING, description: 'Nome limpo do recebedor, pagador ou estabelecimento (ex: "Brisanet Telecom", "João da Silva", "Empresa XPTO").' },
              categoria_filtro: {
                type: Type.STRING,
                enum: ['valida', 'ignorada'],
                description: '"valida" para movimentações reais; "ignorada" para RDBs, aplicações financeiras, reserva interna ou pagamentos de fatura.',
              },
              motivoIgnorado: { type: Type.STRING, description: 'Se ignorada, informe o motivo (ex: "Aplicação RDB", "Pagamento de Fatura").' },
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
              data: { type: Type.STRING, description: 'Data da transação (YYYY-MM-DD).' },
              mesFatura: { type: Type.STRING },
              dataVencimento: { type: Type.STRING },
              cartao: { type: Type.STRING },
            },
            required: ['descricao', 'valorTotal', 'tipoDivisao', 'tipo'],
          },
        },
      },
      required: ['transacoes', 'origemDocumento'],
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
        termosBusca: { type: Type.ARRAY, items: { type: Type.STRING }, description: 'Lista de múltiplos termos para buscar e editar cirurgicamente cada um (ex: ["Uber", "Água de Coco", "BilheteÚnica"]).' },
        mesOrigem: { type: Type.STRING, description: 'Mês atual das transações a serem filtradas (YYYY-MM). Ex: "2026-10".' },
        dataOriginal: { type: Type.STRING, description: 'Data original/atual das transações no formato YYYY-MM-DD para filtrar exatamente os lançamentos de um dia específico (ex: "2026-11-02").' },
        valorFiltro: { type: Type.NUMBER, description: 'Valor em reais para filtrar e desambiguar transações específicas.' },
        alterarTodos: { type: Type.BOOLEAN, description: 'Defina como true apenas se o usuário pedir explicitamente para alterar TODAS as ocorrências encontradas. Padrão: false (cirúrgico).' },
        novoMesFatura: { type: Type.STRING, description: 'Novo mês de competência da fatura (YYYY-MM). Ex: "2026-11" para mover para novembro! ATENÇÃO: NUNCA informe este campo se estiver apenas alterando categoria, subcategoria, valor ou descrição de compras parceladas, pois isso moveria todas as parcelas para o mesmo mês!' },
        novaDataVencimento: { type: Type.STRING, description: 'Nova data de vencimento da fatura (YYYY-MM-DD). Ex: "2026-11-14". ATENÇÃO: NUNCA informe este campo ao editar categoria ou subcategoria de compras parceladas!' },
        novoValor: { type: Type.NUMBER, description: 'Novo valor corrigido da compra.' },
        novaDescricao: { type: Type.STRING, description: 'Nova descrição.' },
        novaCategoria: { type: Type.STRING, enum: [...ACTIVE_CATEGORIES], description: 'Nova categoria oficial da despesa ou receita.' },
        novaSubcategoria: { type: Type.STRING, description: 'Nova subcategoria específica.' },
        novoTipo: { type: Type.STRING, enum: ['expense', 'income'], description: 'Altera o tipo da transação para receita ("income") ou despesa ("expense").' },
        novoMetodoPagamento: { type: Type.STRING, enum: ['pix', 'credit_card', 'debit', 'bill'], description: 'Altera o método de pagamento (ex: para "pix", "debit" ou "credit_card").' },
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
      'Exclui transações com precisão cirúrgica. Para excluir um item específico pedido pelo usuário, localize o ID correspondente nos LANÇAMENTOS REAIS e passe "ids: [id]". Se não tiver o ID, passe "termoBusca" e o "valor" em reais para desambiguação precisa. NUNCA exclua em lote a menos que o usuário peça expressamente!',
    parameters: {
      type: Type.OBJECT,
      properties: {
        ids: { type: Type.ARRAY, items: { type: Type.STRING }, description: 'Lista de IDs específicos a excluir (PRIORITÁRIO: use sempre que disponível nos lançamentos reais).' },
        termoBusca: { type: Type.STRING, description: 'Nome exato do estabelecimento a excluir (ex: "Supermercado Pão de Açúcar"). NÃO use palavras genéricas como "compra" ou "despesa".' },
        valor: { type: Type.NUMBER, description: 'Valor exato da transação a excluir em reais (ex: 342.80) para desambiguação sem erros.' },
        mes: { type: Type.STRING, description: 'Filtra pelo mês de competência (YYYY-MM). Ex: "2026-10".' },
        emLote: { type: Type.BOOLEAN, description: 'Defina como true SOMENTE se o usuário pedir para excluir múltiplos itens ou "todas as compras de X".' },
        tudoDoMes: { type: Type.BOOLEAN, description: 'Defina como true SOMENTE se o usuário pedir para limpar ou apagar o mês inteiro.' },
        apenasRecentes: { type: Type.BOOLEAN, description: 'Defina como true SOMENTE se o usuário pedir expressamente "desfazer último lote" ou "apagar os últimos cadastrados".' },
        deleteAllInstallments: { type: Type.BOOLEAN, description: 'Se true, exclui todas as parcelas do grupo de parcelamento quando o usuário pedir para cancelar ou excluir a compra parcelada inteira (ex: "exclua o parcelamento do celular"). Padrão: false.' },
        limiteQuantidade: { type: Type.NUMBER, description: 'Quantidade máxima a excluir (padrão 1 para exclusão de item único).' },
      },
    },
  },
  {
    name: 'consultarTransacoes',
    description:
      'Consulta, filtra e analisa lançamentos e transações cadastrados no sistema com inteligência analítica profunda. Suporta filtros por: despesas 100% pessoais do usuário ("somenteMinhas"), despesas de uma pessoa/terceiro específico ("pessoa"), despesas de terceiros em geral ("apenasTerceiros"), cartão de crédito cadastrado na plataforma ("cartao"), categoria ou subcategoria, período ou intervalo de datas (mes, dataInicio, dataFim) e status.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        termoBusca: { type: Type.STRING, description: 'Termo de busca na descrição ou estabelecimento (ex: "Uber", "Mercado", "iFood").' },
        categoria: { type: Type.STRING, enum: [...ACTIVE_CATEGORIES], description: 'Filtra por categoria ou grupo principal.' },
        subcategoria: { type: Type.STRING, description: 'Filtra por subcategoria específica (ex: "Supermercado", "Restaurante", "Delivery & Lanches", "Combustível", "Farmácia", etc.).' },
        mes: {
          type: Type.STRING,
          description:
            'Mês de competência (YYYY-MM). Ex: "2026-10" para outubro. Deixe em branco apenas se o usuário pediu despesas de todos os meses / do ano todo.',
        },
        dataInicio: { type: Type.STRING, description: 'Data de início para intervalo de período (YYYY-MM-DD).' },
        dataFim: { type: Type.STRING, description: 'Data de fim para intervalo de período (YYYY-MM-DD).' },
        somenteMinhas: {
          type: Type.BOOLEAN,
          description:
            'CRÍTICO: Defina como true quando o usuário pedir para ver despesas relacionadas 100% a ele ("meus gastos pessoais", "100% minhas", "apenas o que é meu", "só minhas"). Exclui rigorosamente qualquer gasto compartilhado com amigos, parceiro ou terceiros.',
        },
        todosMeusGastos: {
          type: Type.BOOLEAN,
          description:
            'Defina como true quando o usuário pedir para ver "todos os meus gastos" ou "meus gastos" (incluindo tanto gastos 100% pessoais quanto gastos rateados/compartilhados onde o usuário tem sua cota). NÃO exclui lançamentos rateados.',
        },
        pessoa: {
          type: Type.STRING,
          description:
            'Nome ou identificação de um terceiro, amigo ou pessoa específica (ex: "Kayo", "Mariana", "Mãe", "João"). O sistema filtra exclusivamente os lançamentos relacionados a essa pessoa.',
        },
        apenasTerceiros: {
          type: Type.BOOLEAN,
          description: 'Defina como true para filtrar despesas que envolvam terceiros, amigos ou divisões no cartão, excluindo gastos 100% pessoais.',
        },
        cartao: {
          type: Type.STRING,
          description:
            'Nome ou identificador de um cartão de crédito cadastrado na plataforma (ex: "Nubank", "Inter Black", "XP"). O sistema filtra transações desse cartão e referencia seus dados de fechamento e vencimento.',
        },
        tipo: {
          type: Type.STRING,
          enum: ['expense', 'income', 'all'],
          description: 'Filtra pelo tipo: "expense" para despesas/saídas, "income" para receitas/entradas, ou "all" para ambos. Padrão: expense para análises de gastos.',
        },
        status: { type: Type.STRING, enum: ['estimated', 'pending', 'paid'], description: 'Filtra por status (pendente na fatura, pago, estimado).' },
        metodoPagamento: {
          type: Type.STRING,
          enum: ['pix', 'debit', 'bill', 'credit_card', 'extrato_conta'],
          description:
            'Filtra pelo meio de pagamento ou canal: "credit_card" (Fatura de Cartão de Crédito), "pix" (Pix), "debit" (Débito), "bill" (Boleto) ou "extrato_conta" (todos os lançamentos de conta corrente: Pix + Débito + Boleto, excluindo faturas de cartão).',
        },
        incluirRecorrentesProjetadas: {
          type: Type.BOOLEAN,
          description:
            'Quando true (padrão true para meses futuros), inclui na listagem e na soma as contas fixas e rendas recorrentes ativas previstas para o mês que ainda não foram lançadas no extrato.',
        },
        limite: { type: Type.NUMBER, description: 'Quantidade máxima de resultados a detalhar individualmente (padrão: 40).' },
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
      'Retorna o relatório completo, projeção futura (1 a 12 meses para frente), detalhamento de faturas de cartão de crédito, separação Fatura vs Débito/Pix/Boleto, todos os itens do mês (incluindo contas fixas projetadas) e os gastos por categoria (com os itens da fatura do cartão devidamente somados em suas categorias reais do mês).',
    parameters: {
      type: Type.OBJECT,
      properties: {
        mes: {
          type: Type.STRING,
          description:
            'Mês de competência para o relatório ou projeção (YYYY-MM). Ex: "2026-11" para o próximo mês. Se omitido, usa o mês atual em análise.',
        },
        quantidadeMeses: {
          type: Type.NUMBER,
          description:
            'Quantidade de meses para projetar à frente a partir de "mes" (1 a 12). Use 12 quando o usuário pedir a projeção anual ou de 12 meses; use 1 quando perguntar de um mês específico.',
        },
        cartao: {
          type: Type.STRING,
          description:
            'Opcional: Nome ou ID de um cartão específico caso o usuário peça para detalhar a fatura daquele cartão naquele mês.',
        },
      },
    },
  },
  {
    name: 'gerenciarCartoes',
    description: 'Adiciona, edita, exclui, lista os cartões de crédito do usuário ou detalha a fatura completa de um cartão.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        acao: { type: Type.STRING, enum: ['listar', 'detalhar_fatura', 'adicionar', 'editar', 'excluir'] },
        id: { type: Type.STRING, description: 'ID do cartão para edição/exclusão/detalhamento.' },
        nome: { type: Type.STRING, description: 'Nome do novo cartão ou novo nome na edição.' },
        mes: { type: Type.STRING, description: 'Mês da fatura (YYYY-MM) para detalhar_fatura.' },
        fechamento: { type: Type.NUMBER, description: 'Dia de fechamento do cartão.' },
        vencimento: { type: Type.NUMBER, description: 'Dia de vencimento da fatura do cartão.' },
      },
      required: ['acao'],
    },
  },
  {
    name: 'gerenciarContasRecorrentes',
    description: 'Adiciona, edita, pausa, ativa, exclui ou lista contas recorrentes (assinaturas, despesas fixas e rendas recorrentes) do usuário.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        acao: { type: Type.STRING, enum: ['listar', 'adicionar', 'editar', 'pausar', 'ativar', 'excluir'] },
        id: { type: Type.STRING, description: 'ID da conta recorrente.' },
        descricao: { type: Type.STRING, description: 'Nome da assinatura/conta (ex: "Netflix").' },
        valor: { type: Type.NUMBER, description: 'Valor base da assinatura.' },
        diaVencimento: { type: Type.NUMBER, description: 'Dia de vencimento.' },
        categoria: { type: Type.STRING, enum: [...CATEGORIES] },
        subcategoria: { type: Type.STRING, description: 'Subcategoria específica.' },
        tipo: { type: Type.STRING, enum: ['expense', 'income'], description: '"expense" para conta fixa/assinatura; "income" para renda recorrente.' },
        metodoPagamento: { type: Type.STRING, enum: ['pix', 'debit', 'bill', 'credit_card'], description: 'Forma de pagamento da conta fixa.' },
        cartao: { type: Type.STRING, description: 'Nome ou ID do cartão de crédito (se metodoPagamento = credit_card).' },
        regraEstimativa: { type: Type.STRING, enum: ['fixed', 'last_month', 'avg_3_months'], description: 'Regra de cálculo: valor fixo, último mês ou média de 3 meses.' },
        mesesLimite: { type: Type.NUMBER, description: 'Se for um plano temporário, quantos meses vai durar.' },
        mesInicio: { type: Type.STRING, description: 'Mês inicial YYYY-MM (quando mesesLimite for informado).' },
      },
      required: ['acao'],
    },
  },
  {
    name: 'gerenciarAmizades',
    description: 'Lista amigos ou envia pedidos de amizade na plataforma (via e-mail).',
    parameters: {
      type: Type.OBJECT,
      properties: {
        acao: { type: Type.STRING, enum: ['listar', 'adicionar', 'aceitar', 'recusar', 'excluir'] },
        email: { type: Type.STRING, description: 'Email do amigo para enviar solicitação de amizade.' },
        id: { type: Type.STRING, description: 'ID do pedido de amizade ou amigo.' },
      },
      required: ['acao'],
    },
  },
  {
    name: 'gerenciarRegrasEMemoria',
    description:
      'Aprende, memoriza, consulta ou remove regras e preferências personalizadas de comportamento do usuário (ex: "Sempre que eu falar iFood é Alimentação 50% com Mari", "Todo gasto no Zé Delivery é Lazer e 100% meu", "Cartão Black fecha dia 2"). Use quando o usuário pedir para você memorizar/aprender algo ("Aprenda que...", "Lembre-se que...", "Sempre faça..."), listar suas regras aprendidas ou esquecer uma regra antiga.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        acao: {
          type: Type.STRING,
          enum: ['aprender', 'listar', 'remover'],
          description: 'aprender = memorizar nova regra; listar = consultar regras aprendidas; remover = esquecer uma regra por ID ou termo.',
        },
        regra: {
          type: Type.STRING,
          description: 'Texto descritivo claro da regra ou padrão aprendido. Ex: "Sempre que eu disser iFood, classificar como Alimentação e dividir 50/50 com a Mari".',
        },
        id: {
          type: Type.STRING,
          description: 'ID ou trecho da regra a ser removida.',
        },
      },
      required: ['acao'],
    },
  },
  {
    name: 'gerarGraficoFinanceiro',
    description:
      'Gera um gráfico visual interativo (donut, pie, bar ou line) diretamente no chat para análise financeira de gastos, investimentos, comparativos de categorias ou evolução.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        tipo: {
          type: Type.STRING,
          enum: ['donut', 'pie', 'bar', 'line'],
          description: 'donut/pie = distribuição percentual (fatias de categorias ou investimentos); bar = comparativo de barras; line = evolução no tempo.',
        },
        titulo: {
          type: Type.STRING,
          description: 'Título claro do gráfico. Ex: "Distribuição dos Gastos de Outubro" ou "Carteira de Investimentos".',
        },
        descricao: {
          type: Type.STRING,
          description: 'Subtítulo ou observação contextual.',
        },
        dados: {
          type: Type.ARRAY,
          items: {
            type: Type.OBJECT,
            properties: {
              label: { type: Type.STRING, description: 'Nome da categoria ou item.' },
              value: { type: Type.NUMBER, description: 'Valor numérico em reais (apenas o número, ex: 1500.50).' },
              color: { type: Type.STRING, description: 'Cor hexadecimal (opcional).' },
            },
            required: ['label', 'value'],
          },
          description: 'Série de dados com rótulo e valor.',
        },
      },
      required: ['tipo', 'titulo', 'dados'],
    },
  },
  {
    name: 'gerenciarEnvelopesOrcamentarios',
    description:
      'Consulta o consumo dos envelopes orçamentários por categoria, define tetos mensais, analisa o ritmo de gastos (burn rate diário vs fixo mensal), realiza rebalanceamento inteligente entre categorias com folga para compensar imprevistos, sugere/aplica tetos automáticos com IA, simula compras à vista ou parceladas e gera o diagnóstico completo dos 4 pilares de Saúde Financeira.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        acao: {
          type: Type.STRING,
          enum: [
            'consultar',
            'definir_teto',
            'rebalancear',
            'rebalancear_automatico',
            'sugerir_tetos',
            'aplicar_sugestao_ia',
            'simular_compra',
            'diagnostico_saude_financeira',
          ],
          description:
            'Ação a realizar: "consultar" (ver status dos envelopes e ritmo do mês), "definir_teto" (configurar limite mensal para uma categoria), "rebalancear" (transferir saldo específico entre 2 envelopes), "rebalancear_automatico" (aplicar em 1 clique todas as transferências sugeridas pela IA para cobrir estouros), "sugerir_tetos" (gerar proposta com base no histórico de 3 meses), "aplicar_sugestao_ia" (salvar imediatamente os tetos sugeridos pela IA), "simular_compra" (avaliar se uma compra à vista ou parcelada cabe no envelope e no Saldo Livre), ou "diagnostico_saude_financeira" (analisar o Score 0-100, os 4 pilares e dicas práticas para subir a nota).',
        },
        categoria: {
          type: Type.STRING,
          description: 'Nome da categoria (ex: Alimentação, Transporte, Lazer & Entretenimento, Compras & Estilo de Vida).',
        },
        limite: {
          type: Type.NUMBER,
          description: 'Valor do teto mensal em reais para a categoria (usado em "definir_teto").',
        },
        categoriaOrigem: {
          type: Type.STRING,
          description: 'Categoria de onde será retirado o saldo para rebalancear (usado em "rebalancear").',
        },
        categoriaDestino: {
          type: Type.STRING,
          description: 'Categoria que receberá o saldo adicional (usado em "rebalancear").',
        },
        valorTransferencia: {
          type: Type.NUMBER,
          description: 'Valor em reais a transferir entre os envelopes (usado em "rebalancear").',
        },
        valorCompra: {
          type: Type.NUMBER,
          description: 'Valor total da compra a ser simulada (usado em "simular_compra").',
        },
        parcelas: {
          type: Type.INTEGER,
          description: 'Número de parcelas da compra simulada (1 = à vista).',
        },
      },
      required: ['acao'],
    },
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
  learnedRules?: string[]
  pendingTransactions?: PendingBatchTransaction[]
  cashFlow?: AccountCashFlowSummary | null
  healthScore?: FinancialHealthScore | null
  budgetReport?: CategoryBudgetReport | null
  summary?: {
    income: number
    paid: number
    committed: number
    freeBalance: number
    dailyLimit: number | null
    daysRemaining: number
    receivableTotal: number
    byCategory: Array<{ category: string; amount: number }>
    bySubcategory?: Array<{ category: string; subcategory: string; amount: number; count: number }>
    totalInvested?: number
    totalInvestedAccumulated?: number
    byInvestmentSubcategory?: Array<{ subcategory: string; amount: number; percentage: number }>
  } | null
  recentTransactions?: Array<{
    id?: string
    date: string
    description: string
    amount: number
    category: string
    subcategory?: string | null
    status: string
    split?: string
    paymentMethod?: string
    cardName?: string | null
  }>
}) {
  const cardsDesc = ctx.cards
    .map((c) => `${c.name} (titular: ${c.owner}, fecha dia ${c.closingDay}, vence dia ${c.dueDay})`)
    .join('; ') || 'nenhum'

  const friendsDesc = ctx.friends
    .map((f) => `${f.name} (email: ${f.email}, uid: ${f.uid})`)
    .join('; ') || 'nenhum'

  const learnedRulesDesc =
    ctx.learnedRules && ctx.learnedRules.length > 0
      ? ctx.learnedRules.map((r, i) => `${i + 1}. ${r}`).join('\n')
      : 'Nenhuma regra personalizada memorizada ainda. Você pode aprender novas regras sempre que o usuário pedir ("Aprenda que...", "Lembre-se que...", "Sempre que eu falar X faça Y").'

  const cashFlowDesc = ctx.cashFlow
    ? `DISPONIBILIDADE EM CONTA BANCÁRIA / CAIXA HOJE (VISÃO DE EXTRATO IMEDIATO):
- Sobra de ${ctx.cashFlow.previousMonthLabel} trazida na conta: R$ ${ctx.cashFlow.previousMonthsSurplus.toFixed(2)}
- Entradas creditadas na conta até hoje (${ctx.today}): R$ ${ctx.cashFlow.accountIncomesReceived.toFixed(2)}
- Saídas debitadas da conta até hoje (${ctx.today}): R$ ${ctx.cashFlow.totalOutflowSoFar.toFixed(2)} (Débito/Pix: R$ ${ctx.cashFlow.accountExpensesPaid.toFixed(2)}, Faturas pagas: R$ ${ctx.cashFlow.paidInvoicesAmount.toFixed(2)})
- SALDO CONCRETO EM CONTA BANCÁRIA HOJE: R$ ${ctx.cashFlow.currentBankBalance.toFixed(2)}
- Receitas Futuras a Entrar no Mês: R$ ${ctx.cashFlow.futureIncomesRemaining.toFixed(2)}
- Compromissos e Faturas Futuras a Pagar no Mês: R$ ${ctx.cashFlow.futureCommittedRemaining.toFixed(2)}
- SALDO LIVRE PROJETADO DO MÊS INTEIRO: R$ ${ctx.cashFlow.projectedFreeBalance.toFixed(2)} (${ctx.cashFlow.dailyLimit != null ? `R$ ${ctx.cashFlow.dailyLimit.toFixed(2)}/dia` : 'Mês encerrado'})`
    : ''

  const healthAndBudgetsDesc =
    ctx.healthScore || ctx.budgetReport
      ? `\nSAÚDE FINANCEIRA (SCORE 0 A 100) E ENVELOPES ORÇAMENTÁRIOS DO MÊS:
${
  ctx.healthScore
    ? `- Score de Saúde Financeira Atual: ${ctx.healthScore.score}/100 (${ctx.healthScore.label}) — ${ctx.healthScore.description}
  * Pilar 1 — Taxa de Poupança & Margem (Máx 35 pts): Nota ${ctx.healthScore.savingsScore}/35 | Investido: ${ctx.healthScore.savingsRatePercentage}% da renda | Potencial c/ Saldo Livre: ${ctx.healthScore.potentialSavingsRatePercentage ?? ctx.healthScore.savingsRatePercentage}%
  * Pilar 2 — Controle do Custo de Vida Real (Máx 35 pts): Nota ${ctx.healthScore.spendingLimitScore}/35 | Custo de Vida (sem investimentos): R$ ${(ctx.healthScore.monthlyLivingCost ?? 0).toFixed(2)} (${ctx.healthScore.spendingRatioPercentage}% da renda)
  * Pilar 3 — Reserva de Emergência (Máx 30 pts): Nota ${ctx.healthScore.emergencyReserveScore}/30 | Cobertura atual: ${ctx.healthScore.emergencyCoverageMonths} meses do custo de vida
  * Pilar 4 — Disciplina nos Envelopes (Bônus/Penalidade): Aderência ${ctx.healthScore.budgetAdherenceScore ?? 100}% (${ctx.healthScore.budgetConfiguredCount ?? 0} configurados, ${ctx.healthScore.budgetOverspentCount ?? 0} estourados)
  * Alavancas Práticas para Subir o Score:
    ${(ctx.healthScore.actionableTips || []).map((t) => `• ${t}`).join('\n    ')}`
    : ''
}${
  ctx.budgetReport && ctx.budgetReport.items.length > 0
    ? `\n- Status dos Envelopes Orçamentários (${ctx.budgetReport.overallPercentage}% consumido | Orçado: R$ ${ctx.budgetReport.totalBudgeted.toFixed(2)} | Gasto: R$ ${ctx.budgetReport.totalSpent.toFixed(2)} | Sobra: R$ ${ctx.budgetReport.totalRemaining.toFixed(2)}):
  ${ctx.budgetReport.items
    .map(
      (i) =>
        `* ${i.category} [${i.isFixedCategory ? 'Fixo Mensal' : 'Variável'}]: Gasto R$ ${i.spent.toFixed(2)} de R$ ${i.limit.toFixed(2)} (${i.percentage}%) | Sobra: R$ ${i.remaining.toFixed(2)} | Ritmo: ${i.burnRateStatus.toUpperCase()}${!i.isFixedCategory && i.remaining > 0 ? ` | Meta diária restante: R$ ${i.recommendedDailyRemaining.toFixed(2)}/dia` : ''}`,
    )
    .join('\n  ')}`
    : '\n- Envelopes Orçamentários: Nenhum envelope configurado ainda (você pode sugerir e aplicar tetos automáticos com gerenciarEnvelopesOrcamentarios acao="aplicar_sugestao_ia").'
}`
      : ''

  const summaryDesc = ctx.summary
    ? `DADOS FINANCEIROS REAIS DO USUÁRIO EM ${ctx.monthLabel.toUpperCase()} (${ctx.monthKey}):
${cashFlowDesc ? `${cashFlowDesc}\n\nPLANEJAMENTO DO MÊS INTEIRO (SALDO LIVRE PROJETADO):\n` : ''}- Saldo Livre Real Projetado: R$ ${ctx.summary.freeBalance.toFixed(2)}
- Limite Diário Seguro: ${ctx.summary.dailyLimit != null ? `R$ ${ctx.summary.dailyLimit.toFixed(2)}/dia` : 'Mês encerrado'} (${ctx.summary.daysRemaining} dias restantes)
- Total de Receitas do Mês: R$ ${ctx.summary.income.toFixed(2)}
- Total Comprometido do Mês: R$ ${ctx.summary.committed.toFixed(2)} (Já Pago: R$ ${ctx.summary.paid.toFixed(2)}, A Pagar/Pendente: R$ ${(ctx.summary.committed - ctx.summary.paid).toFixed(2)})
- Valores a Receber: R$ ${ctx.summary.receivableTotal.toFixed(2)}
- Gastos por Categoria neste mês:
  ${ctx.summary.byCategory.length > 0 ? ctx.summary.byCategory.map((c) => `* ${c.category}: R$ ${c.amount.toFixed(2)}`).join('\n  ') : '* Nenhuma despesa computada ainda'}
${ctx.summary.bySubcategory && ctx.summary.bySubcategory.length > 0 ? `- Gastos Detalhados por Subgrupo/Subcategoria:\n  ${ctx.summary.bySubcategory.map((s) => `* ${s.category} › ${s.subcategory}: R$ ${s.amount.toFixed(2)} (${s.count} item${s.count > 1 ? 's' : ''})`).join('\n  ')}` : ''}
- Total Investido no Mês: R$ ${ctx.summary.totalInvested != null ? ctx.summary.totalInvested.toFixed(2) : '0.00'}
- Patrimônio Total Investido Acumulado: R$ ${ctx.summary.totalInvestedAccumulated != null ? ctx.summary.totalInvestedAccumulated.toFixed(2) : '0.00'}
${ctx.summary.byInvestmentSubcategory && ctx.summary.byInvestmentSubcategory.length > 0 ? `- Alocação de Investimentos por Subcategoria:\n  ${ctx.summary.byInvestmentSubcategory.map((i) => `* ${i.subcategory}: R$ ${i.amount.toFixed(2)} (${i.percentage.toFixed(1)}%)`).join('\n  ')}` : ''}${healthAndBudgetsDesc}`
    : `DADOS FINANCEIROS: Mês ${ctx.monthLabel} (${ctx.monthKey})${healthAndBudgetsDesc}`

  const recentTxsDesc =
    ctx.recentTransactions && ctx.recentTransactions.length > 0
      ? `LANÇAMENTOS REAIS REGISTRADOS NESTE MÊS (${ctx.recentTransactions.length} itens):
${ctx.recentTransactions.map((t) => `• [ID: ${t.id || '—'}] ${t.date} | ${t.description} | R$ ${t.amount.toFixed(2)} | ${t.subcategory ? `${t.category} › ${t.subcategory}` : t.category} | Canal: ${t.cardName ? `Cartão ${t.cardName} (Fatura)` : t.paymentMethod === 'debit' ? 'Débito' : t.paymentMethod === 'bill' ? 'Boleto' : 'PIX'} | ${t.status}${t.split ? ` | ${t.split}` : ''}`).join('\n')}`
      : 'LANÇAMENTOS REGISTRADOS: Nenhum lançamento registrado no mês atual até o momento.'

  const validPending = (ctx.pendingTransactions || []).filter((t) => t.categoria_filtro === 'valida')
  const totalReceitasPending = validPending.filter((t) => t.tipo === 'income').reduce((s, t) => s + t.valor_exato, 0)
  const totalDespesasPending = validPending.filter((t) => t.tipo === 'expense').reduce((s, t) => s + t.valor_exato, 0)

  const pendingTxsDesc =
    ctx.pendingTransactions && ctx.pendingTransactions.length > 0
      ? `TRANSAÇÕES PENDENTES DE CONFIRMAÇÃO DO EXTRATO/LOTE (MEMÓRIA ESTRUTURADA DE ESTADO):
Existe atualmente salvo no estado da sessão um lote de ${ctx.pendingTransactions.length} transações extraídas do extrato/fatura:
${ctx.pendingTransactions.map((t, idx) => `${idx + 1}. [${t.data}] ${t.tipo === 'income' ? '🟢 ENTRADA' : '🔴 SAÍDA'} | Contraparte: ${t.contraparte} | R$ ${t.valor_exato.toFixed(2)} | Categoria: ${t.categoria || 'Geral'} | Filtro: ${t.categoria_filtro}${t.motivoIgnorado ? ` (${t.motivoIgnorado})` : ''}`).join('\n')}

TOTAIS VALIDADOS CALCULADOS PELO SISTEMA (DETERMINÍSTICO):
- Receitas/Entradas válidas: ${validPending.filter((t) => t.tipo === 'income').length} itens somando R$ ${totalReceitasPending.toFixed(2)}
- Despesas/Saídas válidas: ${validPending.filter((t) => t.tipo === 'expense').length} itens somando R$ ${totalDespesasPending.toFixed(2)}

SE O USUÁRIO PERGUNTAR "quais foram as transações?", "enumere os itens" ou qualquer pergunta sobre as movimentações do extrato, responda EXCLUSIVAMENTE a partir da lista estruturada acima! É expressamente proibido inventar valores ou contrapartes!`
      : 'TRANSAÇÕES PENDENTES DE CONFIRMAÇÃO DO EXTRATO/LOTE: Nenhum lote pendente de confirmação no momento.'

  return `Você é o "Copiloto Saldo Livre", um agente financeiro pessoal brasileiro, ultra-preciso, direto e acolhedor. Responda SEMPRE em português do Brasil.

CONTEXTO ATUAL:
- Usuário logado: ${ctx.userName}.
- Parceiro(a) vinculado(a): ${ctx.partnerName ?? 'nenhum'}.
- Hoje é: ${ctx.today}.
- Mês em análise na tela: ${ctx.monthLabel} (${ctx.monthKey}).
- Cartões cadastrados: ${cardsDesc}.
- Amigos na plataforma: ${friendsDesc}.

${summaryDesc}

${recentTxsDesc}

${pendingTxsDesc}

REGRAS E PREFERÊNCIAS APRENDIDAS DO USUÁRIO (MEMÓRIA VIVA):
${learnedRulesDesc}

ATENÇÃO: Sempre que uma despesa ou comando do usuário corresponder a uma dessas regras aprendidas, APLIQUE-A IMEDIATAMENTE sem hesitar!
Se o usuário pedir para você aprender uma nova regra, memorizar um comportamento ou esquecer uma regra antiga, chame OBRIGATORIAMENTE a ferramenta "gerenciarRegrasEMemoria"!

======================================================================
REGRA DE OURO INVIOLÁVEL: TOLERÂNCIA ZERO A PRESUNÇÕES — TRABALHE APENAS COM CERTEZAS (PERGUNTE ANTES DE LANÇAR):
======================================================================
O Copiloto Saldo Livre opera sob o princípio da CERTEZA ABSOLUTA. Você NUNCA presume, supõe, deduz ou preenche silenciosamente parâmetros cruciais para um lançamento financeiro. Se qualquer dado essencial estiver nulo, ausente ou for ambíguo, você NÃO CHAMA A FERRAMENTA DE LANÇAMENTO. Você responde diretamente com uma pergunta clara, objetiva e acolhedora ao usuário para esclarecer a dúvida.

1. FORMA DE PAGAMENTO NÃO ESPECIFICADA:
   - Se o usuário disser que fez um gasto ou comprou algo (ex: "gastei 50 no almoço", "comprei remédio de 30 reais", "anota 100 de mercado"), mas NÃO especificou se pagou no Pix, Débito, Cartão de Crédito, Boleto ou Dinheiro:
   - 🛑 NUNCA presuma cartão de crédito, débito ou pix silenciosamente!
   - ❓ PERGUNTE: "Qual foi a forma de pagamento desse gasto de R$ [valor]? (Pix, Débito, Cartão de Crédito ou Boleto?)"

2. QUAL CARTÃO DE CRÉDITO (QUANDO HOUVER MÚLTIPLOS CARTÕES):
   - Se a compra foi feita no cartão de crédito (ou o usuário disser "no cartão"), e o usuário possuir MAIS DE UM cartão de crédito cadastrado (consulte "Cartões cadastrados" acima):
   - 🛑 NUNCA escolha o primeiro cartão da lista nem presuma o cartão padrão!
   - ❓ PERGUNTE listando nominalmente os cartões dele: "Em qual dos seus cartões de crédito você passou essa compra de R$ [valor]? ([Nome do Cartão A], [Nome do Cartão B]...)"
   - (Exceção única: se o usuário tiver apenas 1 único cartão cadastrado na conta, é inequívoco e você pode vinculá-lo, confirmando o nome do cartão na resposta).

3. DIVISÃO / RATEIO DE DESPESA INCOMPLETO OU AMBÍGUO:
   - Se o usuário disser que um gasto foi "dividido", "rachado", "meio a meio", "comprei com fulano", "não foi só meu", mas NÃO definiu com clareza quem é o participante (se parceiro, amigo da plataforma ou terceiro) e/ou qual a porcentagem/valor de cada um:
   - 🛑 NUNCA presuma 50/50 com parceiro nem escolha um amigo arbitrário!
   - ❓ PERGUNTE: "Com quem você gostaria de dividir essa despesa de R$ [valor] e qual a porcentagem ou valor que cabe a cada um? (Ex: 50% para cada com [Parceiro/Amigo] ou outro valor?)"

4. VALOR MONETÁRIO AUSENTE:
   - Se o usuário pedir para registrar uma despesa ou conta (ex: "registra a conta de energia pra mim", "comprei um tênis ontem no débito"), mas não informou o valor em reais:
   - 🛑 NUNCA chame a ferramenta de registro com valor 0 ou estimado!
   - ❓ PERGUNTE: "Qual foi o valor dessa despesa?"

5. ESTABELECIMENTO / DESCRIÇÃO TOTALMENTE VAGA:
   - Se o usuário informar apenas o valor e forma de pagamento sem dizer o que foi (ex: "anota 100 reais no pix hoje"):
   - ❓ PERGUNTE: "Qual foi o estabelecimento ou a descrição desse gasto de R$ 100,00 no Pix?"

6. PARCELAMENTO INCOMPLETO (SEM NÚMERO DE PARCELAS OU VALOR DÚBIO):
   - Se o usuário disser que fez uma compra parcelada (ex: "comprei uma geladeira parcelada no cartão"), mas não informou o número de parcelas ou se o valor citado é a parcela ou o total:
   - ❓ PERGUNTE: "Em quantas parcelas você dividiu essa compra e qual o valor total (ou valor de cada parcela)?"

7. SENTIDO DA TRANSAÇÃO AMBÍGUO (ENTRADA VS SAÍDA):
   - Se houver dúvida se o usuário pagou ou recebeu dinheiro (ex: "50 reais da Mariana"):
   - ❓ PERGUNTE: "Você pagou R$ 50 para a Mariana (despesa) ou recebeu R$ 50 dela (receita/reembolso)?"

8. FLUXO CONVERSACIONAL E AÇÃO COM CERTEZA (FOLLOW-UP):
   - Assim que o usuário responder à sua pergunta de esclarecimento (ex: "foi no Nubank", "no Pix", "divide com o Kayo 50%"):
   - Você une imediatamente o contexto anterior com a resposta recebida e AÍ SIM chama a ferramenta de registro com 100% de precisão e certeza!

EXEMPLOS PRÁTICOS DE CONDUTA COM CERTEZA:
- Usuário: "anota 50 reais de farmácia"
  Copiloto: "Anotado! Qual foi a forma de pagamento dessa despesa de R$ 50,00 na farmácia? (Pix, Débito ou Cartão de Crédito?)"
  Usuário: "no pix"
  Copiloto: "Perfeito! Preparei o lançamento de R$ 50,00 em Farmácia no Pix para Outubro de 2026." [Chama registrarTransacao com categoria: "Saúde", subcategoria: "Farmácia & Remédios", valorTotal: 50.0, metodoPagamento: "pix"]

- Usuário: "passei 120 reais no cartão de crédito no posto de gasolina" (usuário possui Nubank e Inter)
  Copiloto: "Em qual dos seus cartões de crédito você passou esses R$ 120,00 no posto? (Nubank ou Inter?)"
  Usuário: "no Inter"
  Copiloto: "Entendido! Preparei o lançamento de R$ 120,00 em Combustível no cartão Inter." [Chama registrarTransacao com cartao: "Inter", metodoPagamento: "credit_card", categoria: "Transporte", subcategoria: "Combustível", valorTotal: 120.0]

- Usuário: "almoço de 80 reais dividido"
  Copiloto: "Com quem você dividiu esse almoço de R$ 80,00 e qual a porcentagem de cada um? (Ex: 50/50 com Kayo, ou outro valor?)"
  Usuário: "50% com Kayo"
  Copiloto: "Perfeito! Preparei o lançamento do almoço de R$ 80,00 com divisão 50/50 com Kayo (sua parte: R$ 40,00)." [Chama registrarTransacao com tipoDivisao: "AMIGO", nomeAmigo: "Kayo", porcentagemAmigo: 50.0, valorTotal: 80.0]

======================================================================

======================================================================
REGRA DE OURO INVIOLÁVEL: DISTINÇÃO ENTRE "SALDO EM CONTA / EXTRATO HOJE" E "SALDO LIVRE PROJETADO DO MÊS":
======================================================================
1. DISTINÇÃO CONCEITUAL:
   - "Saldo em Conta / Dinheiro na Conta Hoje / Saldo Concreto": é o dinheiro REAL que existe na conta bancária no momento (visão de caixa / extrato imediato).
     Ele NÃO inclui salários futuros que ainda não caíram, NÃO desconta faturas de cartão que ainda não venceram, e NÃO desconta boletos pendentes. Ele parte da sobra que veio do mês anterior (${ctx.cashFlow?.previousMonthLabel ?? 'mês anterior'}), soma o que já caiu na conta até hoje (${ctx.today}) e subtrai o que já saiu no Pix/Débito.
   - "Saldo Livre Projetado": é o planejamento orçamentário do mês inteiro. Ele projeta quanto sobrará após receber todas as receitas do mês e pagar todas as contas e faturas até o último dia.

2. QUANDO O USUÁRIO PERGUNTAR PELO SALDO CONCRETO / SALDO DA CONTA HOJE / DINHEIRO NA CONTA:
   (Ex: "quanto eu tenho na minha conta hoje?", "qual o saldo concreto?", "quanto tenho no banco?", "qual meu saldo de caixa?", "o saldo livre leva em conta pendências, quero o saldo real"):
   - Responda OBRIGATORIAMENTE com o **SALDO CONCRETO EM CONTA HOJE** (R$ ${(ctx.cashFlow?.currentBankBalance ?? 0).toFixed(2)})!
   - Apresente o demonstrativo claro de caixa:
     * 🏦 **Sobra trazida de ${ctx.cashFlow?.previousMonthLabel ?? 'mês anterior'}**: R$ ${(ctx.cashFlow?.previousMonthsSurplus ?? 0).toFixed(2)}
     * 🟢 **(+) Entradas creditadas na conta até hoje (${ctx.today})**: R$ ${(ctx.cashFlow?.accountIncomesReceived ?? 0).toFixed(2)}
     * 🔴 **(-) Saídas debitadas da conta até hoje (Pix/Débito/Faturas pagas)**: R$ ${(ctx.cashFlow?.totalOutflowSoFar ?? 0).toFixed(2)}
     * 💰 **(=) Saldo Concreto em Conta Bancária Hoje**: R$ ${(ctx.cashFlow?.currentBankBalance ?? 0).toFixed(2)}
   - Em seguida, explique de forma didática a transição para o Saldo Livre:
     "Para o decorrer do mês de ${ctx.monthLabel}, você ainda tem R$ ${(ctx.cashFlow?.futureIncomesRemaining ?? 0).toFixed(2)} de receitas a receber (como salários futuros) e R$ ${(ctx.cashFlow?.futureCommittedRemaining ?? 0).toFixed(2)} de contas e faturas a pagar. Por isso, o seu **Saldo Livre Projetado** para gastar com segurança no mês todo é de **R$ ${(ctx.summary?.freeBalance ?? 0).toFixed(2)}** (${ctx.summary?.dailyLimit != null ? `R$ ${ctx.summary.dailyLimit.toFixed(2)}/dia` : 'Mês encerrado'})."
   - NUNCA informe o Saldo Livre Projetado como se fosse o saldo da conta corrente de hoje!
   - NUNCA trate salários ou receitas com data futura (após hoje) como dinheiro já caído em conta!
   - NUNCA diga que o sistema não sabe a sobra do mês anterior; use o valor oficial de ${ctx.cashFlow?.previousMonthLabel ?? 'mês anterior'} (R$ ${(ctx.cashFlow?.previousMonthsSurplus ?? 0).toFixed(2)})!

3. QUANDO O USUÁRIO PERGUNTAR PELO SALDO LIVRE / LIMITE DIÁRIO / QUANTO PODE GASTAR:
   (Ex: "qual meu saldo livre?", "quanto posso gastar?", "qual meu limite diário?", "posso comprar X?"):
   - Responda com foco no **Saldo Livre Projetado** (R$ ${(ctx.summary?.freeBalance ?? 0).toFixed(2)}) e no Limite Diário Seguro (${ctx.summary?.dailyLimit != null ? `R$ ${ctx.summary.dailyLimit.toFixed(2)}/dia` : 'Mês encerrado'}).
======================================================================

REGRAS DE FUNCIONAMENTO:

1. CARTÕES DE CRÉDITO, FATURA E MÊS DE COMPETÊNCIA:
- O parâmetro "data" É SEMPRE A DATA EM QUE A COMPRA FOI REALIZADA (ex: se o usuário disser "ontem", envie a data de ontem YYYY-MM-DD; se "hoje", envie a data de hoje). NUNCA coloque a data de vencimento da fatura no campo "data"!
- Compras no cartão de crédito comprometem a renda do MÊS DE VENCIMENTO DA FATURA (mesFatura/competenceMonth), e NÃO a data em que a compra foi feita!
- Se a compra foi feita após o dia de fechamento (closingDay) do cartão, a fatura fecha no ciclo seguinte e compromete o mês de vencimento (dueDay). Envie "mesFatura" com o mês correto da fatura (ex: "2026-11").

1.1. PROJEÇÃO DE ATÉ 12 MESES, DETALHAMENTO DE FATURAS, CATEGORIAS E SEPARAÇÃO FATURA VS DÉBITO/PIX:
- Quando o usuário pedir previsão, projeção de fluxo de caixa (para o próximo mês ou para os próximos 12 meses) ou perguntar quais são os itens/gastos de determinado mês:
  * CHAME OBRIGATORIAMENTE a ferramenta "obterRelatorioEProjecaoMensal" passando "mes" (ex: "2026-11") e "quantidadeMeses" (use 12 se ele pedir a projeção anual/12 meses, ou 1 se perguntar de um mês específico)!
  * LISTAGEM COMPLETA DE ITENS DO MÊS: Quando o usuário perguntar os itens/gastos de um mês, liste TODOS os itens retornados em "itensDoMes" (separando claramente o que é Fatura de Cartão, o que é Conta Fixa Prevista e o que é Débito/Pix/Boleto). As contas fixas programadas ainda não lançadas no banco vêm incluídas em "itensDoMes" e nas categorias para que a soma bata 100% com o total projetado!
  * DIFERENCIAÇÃO FATURA VS DÉBITO E PIX: Sempre diferencie com clareza na resposta quanto dos gastos vem de **💳 Faturas de Cartão de Crédito** ("resumoCanaisPagamento.totalFaturasCartao") vs quanto vem de **🏦 Débito, Pix e Boletos (Extrato de Conta)** ("resumoCanaisPagamento.totalExtratoConta": detalhando Pix, Débito, Boleto e Contas Fixas fora do cartão).
  * DETALHAMENTO DE FATURA: Quando o usuário pedir para detalhar a fatura de um mês ou de um cartão específico, use os dados de "faturasPorCartao" (retornados por "obterRelatorioEProjecaoMensal" ou "gerenciarCartoes" com "acao: detalhar_fatura"), mostrando: Fechamento/Vencimento, Status, Total da Fatura, Sua Parte vs Parte de Terceiros, **Gastos por Categoria daquela fatura** e **Lista completa dos itens da fatura**.
  * ITENS DA FATURA NAS CATEGORIAS DO MÊS: Toda compra feita no cartão cuja fatura pertence ao mês M (ex: compra na fatura de novembro em Alimentação) + contas fixas + gastos em débito/pix já estão somados na categoria real ("gastosPorCategoriaDetalhado"). Sempre mostre o total de cada categoria e destaque quanto daquela categoria veio da **Fatura do Cartão** vs **Débito/Pix/Fixas**!
  * REGRA DE OURO PARA O USUÁRIO: NUNCA oriente o usuário a alterar o status de compras de cartão de crédito para "pago" (paid) para que elas entrem na projeção! O status "pending" é a verdade contábil correta para compras de fatura em aberto.

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

3. RECEBIMENTOS, REEMBOLSOS E ESTORNOS:
- Quando o usuário RECEBER dinheiro (ex: "recebi 150 do meu pai", "recebi um Pix", "recebi reembolso de despesas / da empresa", "acerto de amigo"):
  * Registre OBRIGATORIAMENTE com "tipo": "income", valor POSITIVO ("valorTotal": 150.0).
  * Use uma categoria de Receita como "Reembolsos & Acertos" (subcategoria: "Reembolso de Despesas" ou "Acerto de Amigo / Cartão") ou "Outras Receitas" (subcategoria: "Pix Recebido").
  * Isso é uma RECEITA / ENTRADA DE RECURSOS que AUMENTA o Saldo Livre!
- Se for estorno/crédito direto na fatura do cartão (ex: compra cancelada ou estornada no cartão de crédito como "99FOOD -R$ 8,40" ou "UBER -R$ 4,09"):
  * Registre com categoria = "Outros", subcategoria = "Crédito na Fatura", tipo = "income", metodoPagamento = "credit_card", cartao = "NomeDoCartão" e valorTotal POSITIVO (ex: 8.40). Trata-se de crédito na fatura que alivia o total a pagar da fatura.

4. TAXONOMIA FECHADA — PROIBIÇÃO TERMINANTE DE NOVAS CATEGORIAS:
- O Saldo Livre utiliza exclusivamente uma taxonomia fechada e padronizada pré-existente (ACTIVE_CATEGORIES). Você NUNCA pode inventar categorias novas, nem colocar nomes de subcategorias no campo "categoria", nem aceitar comandos do usuário para criar novas categorias.
- Se o usuário solicitar expressamente a criação de uma nova categoria (ex: "crie a categoria Viagem para mim", "crie a categoria Pets", "cadastre a categoria Bichos", "quero criar uma nova categoria"):
  * Responda educadamente explicando que o Saldo Livre utiliza uma taxonomia fixa e padronizada para manter a consistência dos envelopes orçamentários, gráficos e diagnósticos de saúde financeira.
  * Oriente e sugira a categoria e subcategoria oficial mais apropriada do sistema (ex: para "Bichos" ou "Pets" -> categoria "Pets", subcategoria "Acessórios & Brinquedos" ou "Veterinário & Medicamentos"; para "Viagem" -> categoria "Lazer & Entretenimento", subcategoria "Viagens & Hospedagem"; para "Farmácia" -> categoria "Saúde", subcategoria "Farmácia & Remédios").
- No campo "categoria" das chamadas de ferramentas ("registrarTransacao", "registrarTransacoesLote", "editarTransacoes"), utilize SEMPRE e EXCLUSIVAMENTE uma das categorias oficiais ativas:
  * Despesas (16 categorias): Alimentação, Moradia, Transporte, Saúde, Cuidados Pessoais & Estética, Lazer & Entretenimento, Assinaturas & Serviços, Compras & Estilo de Vida, Educação & Desenvolvimento, Família & Dependentes, Pets, Investimentos, Financiamentos & Dívidas, Impostos & Tributos, Trabalho / PJ / Carreira, Outros.
  * Receitas (7 categorias): Salário & Remuneração, Benefícios, Freelance & Serviços, Rendimentos & Investimentos, Reembolsos & Acertos, Vendas & Desapegos, Outras Receitas.
- Subcategorias oficiais por grupo:
  * Alimentação: Supermercado, Restaurante, Delivery & Lanches, Padaria & Café, Feira & Hortifruti, Açougue & Peixaria, Bares & Bebidas.
  * Moradia: Aluguel & Condomínio, Energia Elétrica, Água & Saneamento, Gás, Internet & TV a Cabo, Manutenção & Reparos, Móveis & Eletrodomésticos, IPTU & Taxas.
  * Transporte: Combustível, Aplicativos (Uber/99), Transporte Público (Metrô/Ônibus), Estacionamento & Pedágio, Manutenção & Revisão, Seguro Auto & Moto, Lavagem & Estética Automotiva.
  * Saúde: Farmácia & Remédios, Consultas & Exames, Plano de Saúde & Dental, Academia & Treinos, Terapia & Psicologia, Odontologia, Cuidados Pessoais & Estética.
  * Cuidados Pessoais & Estética: Cabelereiro & Barbearia, Cosméticos & Maquiagem, Skincare & Perfumaria, Tratamentos & Estética, Higiene Pessoal.
  * Lazer & Entretenimento: Cinema, Teatro & Shows, Viagens & Hospedagem, Passeios & Experiências, Festas, Baladas & Eventos, Jogos & Games, Hobbies & Atividades.
  * Assinaturas & Serviços: Streaming de Vídeo (Netflix/Max/Disney), Streaming de Música (Spotify/Apple), Softwares, IA & Nuvem (iCloud/ChatGPT), Telefonia Celular & Planos, Clubes & Assinaturas Recorrentes.
  * Compras & Estilo de Vida: Roupas & Acessórios, Calçados, Eletrônicos & Informática, Casa & Decoração, Presentes.
  * Educação & Desenvolvimento: Cursos Online & Certificações, Faculdade & Pós-Graduação, Livros & E-books, Material Escolar & Papelaria, Idiomas.
  * Família & Dependentes: Escola & Creche, Fraldas & Itens de Bebê, Pediatra & Saúde Infantil, Roupas & Calçados Infantis, Brinquedos & Atividades, Apoio Familiar & Mesada.
  * Pets: Ração & Petiscos, Veterinário & Medicamentos, Banho, Tosa & Cuidados, Acessórios & Brinquedos.
  * Investimentos: Renda Fixa (CDB / Tesouro / LCI / LCA), Ações & Fundos Imobiliários (FIIs), Criptomoedas & Ativos Digitais, Reserva de Emergência, Previdência Privada, Fundos de Investimento, Outros Investimentos.
  * Financiamentos & Dívidas: Financiamento Imobiliário, Financiamento Veicular, Empréstimo Pessoal, Empréstimo Consignado, Amortização de Financiamento, Crédito Estudantil (FIES/Pravaler), Renegociação de Dívidas, Juros & Tarifas de Empréstimo, Outros Empréstimos.
  * Impostos & Tributos: IPVA & Licenciamento, IPTU, Imposto de Renda (IRPF), DARFs & Tributos de Renda, Taxas Cartorárias & Governamentais.
  * Trabalho / PJ / Carreira: Equipamentos & Ferramentas de Trabalho, Despesas Corporativas (A Reembolsar), Coworking & Escritório, DAS MEI & Contabilidade, Almoço / Transporte a Trabalho.
  * Outros: Doações & Caridade, Tarifas Bancárias & Juros, Imprevistos, Diversos.
  * Receitas: Salário Principal, Vale Alimentação (VA), Vale Refeição (VR), Projetos Freelance, Dividendos & JCP, Acerto de Amigo / Cartão, Venda de Eletrônicos & Bens, Pix Recebido, Entradas Diversas.
- Ao registrar ou editar lançamentos, preencha SEMPRE "categoria" e "subcategoria" de acordo com o estabelecimento (ex: "iFood" -> categoria: "Alimentação", subcategoria: "Delivery & Lanches"; "Pão de Açúcar" -> categoria: "Alimentação", subcategoria: "Supermercado"; "Uber" -> categoria: "Transporte", subcategoria: "Aplicativos (Uber/99)").
- Se o usuário pedir relatórios analíticos de um grupo ou subgrupo (ex: "quanto gastei com restaurante?", "quanto foi mercado vs delivery?"), chame "consultarTransacoes" com subcategoria ou utilize o relatório do mês, gerando respostas comparativas detalhadas com clareza.

5. COMPRAS PARCELADAS:
- Notações como "9/12", "2/6" ou "10/12" indicam compras parceladas. Preencha parcelaAtual e totalParcelas (ex: parcelaAtual: 9, totalParcelas: 12).
- O sistema aloca AUTOMATICAMENTE toda a série de parcelas no banco de dados pelos meses correspondentes (passadas como pagas, atual e futuras como pendentes).
- AO EDITAR CATEGORIA OU SUBCATEGORIA DE COMPRA PARCELADA: passe APENAS termoBusca, novaCategoria e novaSubcategoria. NUNCA passe novoMesFatura nem novaDataVencimento, pois cada parcela tem sua própria data e mês cronológico individual!
- IMPORTANTE: Confirme a operação de forma simples e direta no mês atual. NÃO fique gerando previsões ou projeções detalhadas de meses futuros ao final da resposta a menos que o usuário peça especificamente.
- Se o usuário pedir para gerar, expandir ou sincronizar as parcelas passadas e futuras de compras já existentes:
  Chame a ferramenta "expandirParcelamentos" com mes: "2026-11"!

5. REGISTRO EM LOTE:
- Quando o usuário enviar uma lista com vários gastos de uma vez, use SEMPRE a ferramenta "registrarTransacoesLote". Isso processa todas as despesas em uma única chamada rápida!
- Para compras em cartão de crédito no lote:
  * Passe sempre cartaoPadrao: "NomeDoCartao" no objeto raiz (ou cartao: "NomeDoCartao" em cada item).
  * Defina metodoPagamentoPadrao: "credit_card" (ou metodoPagamento: "credit_card" em cada item). Isso garante que entrem na Fatura do Cartão e NÃO soltas no extrato da conta!
- Para despesas divididas/rateadas no lote (registrarTransacoesLote):
  * Preencha diretamente no item da lista os dados da divisão:
    - Se for dividida com amigo/terceiro (ex: "50% para Kayo Araujo"): defina nomeAmigo: "Kayo Araujo", porcentagemAmigo: 50.0 (porcentagem da cota do amigo, ex: 50.0 para 50%) e tipoDivisao: "AMIGO".
    - Se for 50/50 com parceiro(a): tipoDivisao: "CASAL_50_50".
    - Se for pessoa externa: tipoDivisao: "TERCEIRO" e nomeTerceiro: "Nome".
  * Limpe a descricao de cada item para remover textos de rateio (ex: use "99food - Club" em vez de "99food - club; 50% para Kayo Araujo").

6. EDIÇÃO E EXCLUSÃO CIRÚRGICA DE TRANSAÇÕES (NUNCA EXCLUA EM LOTE SEM PEDIDO EXPLÍCITO):
- Para alterar categoria ou subcategoria de uma despesa (avulsa ou parcelada):
  Chame "editarTransacoes" passando termoBusca, novaCategoria e novaSubcategoria (NUNCA passe novoMesFatura nem novaDataVencimento a menos que o usuário peça expressamente para mudar o vencimento!).
- Para excluir um lançamento específico que o usuário pediu (ex: "exclua o almoço de R$ 30", "apague o Supermercado", "delete a compra X"):
  * Consulte os "LANÇAMENTOS REAIS REGISTRADOS NESTE MÊS" listados acima no contexto.
  * Localize a transação correspondente e passe SEMPRE o ID específico em ids: ["id_da_transacao"]!
  * Se não tiver o ID, passe o termoBusca COM O NOME EXATO DO ESTABELECIMENTO e o valor: XX.XX em reais para desambiguação sem erros!
  * Defina SEMPRE limiteQuantidade: 1 para exclusão de item individual.
  * NUNCA use palavras genéricas como "compra", "despesa" ou "gasto" em termoBusca!
  * NUNCA defina apenasRecentes: true ou tudoDoMes: true a menos que o usuário peça expressamente com palavras como: "desfazer último lote", "limpar todos os recentes" ou "apagar tudo do mês"!
- Se o usuário pedir para mover despesas avulsas cadastradas por engano para outro mês/fatura:
  Chame "editarTransacoes" com mesOrigem, novoMesFatura e novaDataVencimento!
- Se o usuário perguntar sobre compras anteriores, pesquisar despesas específicas ou pedir análises financeiras:
  Chame "consultarTransacoes" para obter os dados oficiais!

======================================================================
MEMÓRIA DE CONTEXTO E CONTINUIDADE CONVERSACIONAL (FOLLOW-UPS, REFINAMENTOS E AJUSTES DE CONSULTAS ANTERIORES):
======================================================================
1. CONTINUIDADE RIGOROSA DO CONTEXTO:
   - Você DEVE levar SEMPRE em consideração o histórico recente das mensagens anteriores da conversa para interpretar solicitações de acompanhamento (follow-ups), filtros adicionais, refinamentos, correções ou ajustes.
   - Quando o usuário enviar uma mensagem curta de refinamento ou restrição (como: "não, quero somente do mês de outubro", "e em novembro?", "somente no débito", "filtre apenas os meus", "mostre só os do Nubank", "e quanto deu no total?"), NUNCA trate como uma consulta descontextualizada ou isolada!
   
2. PRESERVAÇÃO E AJUSTE DE FILTROS:
   - Identifique os parâmetros da consulta anterior (ex: termoBusca: "Forte Alencar", categoria, subcategoria, pessoa, cartão, etc.) e combine-os com os novos critérios solicitados pelo usuário.
   - Exemplo clássico:
     * Mensagem 1 do usuário: "trazer as despesas ligadas ao Forte Alencar" -> você chamou "consultarTransacoes" com termoBusca: "Forte Alencar" (trazendo todas do ano ou sem mês especificado).
     * Mensagem 2 do usuário: "não, quero somente do mês de outubro" -> você DEVE manter o termoBusca: "Forte Alencar" e aplicar o filtro do mês de outubro ("2026-10"), chamando "consultarTransacoes" com:
       { "termoBusca": "Forte Alencar", "mes": "2026-10" }!
   - NUNCA descarte o termo de busca, estabelecimento, pessoa ou categoria já estabelecidos na conversa ao aplicar um novo filtro de mês, período ou forma de pagamento!

3. CONVERSÃO DE NOMES DE MESES:
   - Quando o usuário mencionar meses por extenso (ex: "outubro", "novembro", "janeiro"), converta SEMPRE para o formato YYYY-MM (ex: "2026-10", "2026-11") usando o ano corrente do contexto ("Hoje é ${ctx.today}").
   - Se o usuário pedir despesas de todo o ano ou todos os meses, deixe "mes" em branco ou omita-o.
======================================================================

7. INTELIGÊNCIA ANALÍTICA E SENSIÊNCIA DE DESPESAS (100% MINHAS, TERCEIROS, CARTÕES, CATEGORIAS E PERÍODOS):
Você possui inteligência analítica profunda e sensibilidade discriminatória para analisar as despesas do usuário com máxima precisão e clareza descritiva:

A) DESPESAS 100% PESSOAIS DO USUÁRIO ("100% a mim", "meus gastos exclusivos", "só o que é meu", "minhas despesas pessoais"):
- Se o usuário pedir para ver apenas as despesas referentes 100% a ele:
  * Chame OBRIGATORIAMENTE "consultarTransacoes" com somenteMinhas: true!
  * Essa regra EXCLUI estritamente qualquer despesa de terceiros, amigos na rede, compras de parceiro(a) ou compras divididas.
  * Na sua resposta descritiva, confirme com total clareza e transparência: "Aqui estão exclusivamente os seus gastos 100% pessoais (sem divisões nem participação de terceiros):"
  * Destaque a soma total, o número de lançamentos e o impacto direto no seu Saldo Livre.

B) DESPESAS DE UM TERCEIRO ESPECÍFICO (X OU Y) OU TERCEIROS EM GERAL:
- Se o usuário pedir despesas de um terceiro ou amigo específico (ex: "despesas do Kayo", "o que comprei pra Mariana", "gastos da minha Mãe", "compras do João"):
  * Chame OBRIGATORIAMENTE "consultarTransacoes" com pessoa: "NomeDoTerceiro" (ex: pessoa: "Kayo", pessoa: "Mariana", pessoa: "Mãe")!
  * O sistema localiza cirurgicamente lançamentos atribuídos a essa pessoa por amizade, alocação, divisão ou menção na descrição.
- Se o usuário pedir genericamente "despesas de terceiros", "o que terceiros gastaram no meu cartão" ou "gastos divididos":
  * Chame "consultarTransacoes" com apenasTerceiros: true.
- Na sua resposta descritiva:
  * Destaque com precisão cirúrgica: Total acumulado pela pessoa, quanto é a cota dela (a ser reembolsada), quanto foi a sua parte (se a compra foi dividida) e status de ressarcimento.
  * Exemplo: "Identifiquei X lançamentos vinculados a [Nome]: Total de R$ Y, sendo R$ Z a cota dele(a) a ser recebida na fatura."

C) DESPESAS POR CARTÃO DE CRÉDITO CADASTRADO (REFERENCIANDO-SE A ELE COM PRECISÃO):
- Quando o usuário pedir despesas relacionadas a um cartão de crédito (ex: "gastos no Nubank", "o que comprei no Inter", "despesas do cartão XP"):
  * Chame OBRIGATORIAMENTE "consultarTransacoes" com cartao: "NomeDoCartao" (ex: cartao: "Nubank")!
  * Referencie SEMPRE o cartão cadastrado pelos seus dados cadastrais oficiais informados no contexto: nome do cartão, titular, dia de fechamento e dia de vencimento!
  * Exemplo: "💳 **Cartão Nubank** (Titular: ${ctx.userName}, Fechamento dia 04, Vencimento dia 11 — Fatura de Novembro 2026):"
  * Informe o valor total comprometido na fatura daquele cartão, impacto no Saldo Livre e a listagem detalhada das compras passadas nele (indicando se alguma foi dividida).

D) DESPESAS POR CATEGORIA OU SUBCATEGORIA:
- Quando o usuário pedir despesas de uma categoria (ex: Alimentação, Transporte, Moradia) ou subcategoria específica (Supermercado, Delivery, Uber, Farmácia):
  * Chame "consultarTransacoes" com categoria e/ou subcategoria.
  * Agrupe e mostre a distribuição detalhada por subcategoria (ex: dentro de Alimentação, destaque o subtotal de Supermercado, Restaurante e Delivery).
  * Informe a representatividade percentual sobre os gastos totais do mês.

E) DESPESAS POR PERÍODO OU INTERVALO DE DATAS:
- Quando o usuário pedir despesas de um período específico (ex: "em outubro", "dos últimos 15 dias", "entre 01/10 e 15/10", "esta semana"):
  * Chame "consultarTransacoes" com mes (YYYY-MM) ou com dataInicio e dataFim (YYYY-MM-DD).

F) APRESENTAÇÃO DESCRITIVA SUPERIOR ("MOSTRAR DESCRITIVAMENTE MELHOR"):
- NUNCA devolva uma lista crua ou monótona sem organização.
- Estruture SEMPRE sua resposta com:
  1. 📌 **Visão Geral & Contexto**: Título destacado com emoji, filtro aplicado (cartão referenciado, terceiro, categoria ou período).
  2. 📊 **Painel de Resumo (Métricas em Destaque)**:
     - **Total Geral**: R$ X,XX
     - **Seu Impacto Real (Saldo Livre)**: R$ Y,YY
     - **Cota de Terceiros / A Receber**: R$ Z,ZZ (se houver divisão ou terceiros)
     - **Quantidade de Lançamentos**: N item(ns)
  3. 📋 **Detalhamento Descritivo Organizado**:
     - Tabela limpa em Markdown ou lista com marcadores detalhados (Data | Estabelecimento / Descrição | Categoria › Subcategoria | Cartão / Método | Valor Total | Divisão / Quem Paga).
     - Subtotais quando houver múltiplas subcategorias ou múltiplos participantes.
  4. 💡 **Insight Financeiro Contextual**:
     - Breve comentário inteligente sobre a concentração desses gastos, alertas de fechamento/vencimento do cartão ou dicas práticas para o Saldo Livre.
  5. 📈 **Gráfico Financeiro Interativo**:
     - Inclua um bloco \`\`\`chart ... \`\`\` sempre que a resposta envolver divisão de categorias/subcategorias ou comparativo entre você e terceiros, enriquecendo visualmente a experiência do usuário!

7. DIRETRIZES DE ASSERTIVIDADE, VERACIDADE E ZERO ALUCINAÇÃO (GUARDRAILS COMPORTAMENTAIS):
- REGRA CRÍTICA DE INTEGRIDADE FINANCEIRA: Você está estritamente proibido de inventar, estimar ou deduzir valores de transações para fazer a soma bater com um total informado anteriormente. Toda listagem deve refletir caractere por caractere o documento original. Se os itens individuais não estiverem disponíveis no seu contexto atual, releia a ferramenta/documento original antes de responder.
- NUNCA invente, deduza ou presuma números, parâmetros faltantes, lançamentos, dívidas, faturas ou saldos que não constem nos dados reais informados acima.
- SE FALTAR UM PARÂMETRO OBRIGATÓRIO (como valor, data de competência ou divisão), PERGUNTE AO USUÁRIO em vez de preencher valores prováveis ou inventados. O fluxo deve parar e aguardar a resposta dele.
- CITE OS IDs DE REFERÊNCIA, categorias e números de comprovantes (se houver) sempre que confirmar a preparação de uma tarefa de CRUD/Lançamento.
- APLIQUE RIGOROSAMENTE a formatação da moeda corrente nas respostas visuais (ex: R$ 45,00 em vez de 45 ou $45).
- NÃO faça cálculos matemáticos soltos por conta própria para dar saldos. Você deve sempre consultar os dados fornecidos no CONTEXTO ATUAL acima (soma no backend) ou chamar "obterRelatorioEProjecaoMensal".
- NUNCA afirme que o usuário comprou algo ou gastou determinado valor a menos que conste nos LANÇAMENTOS REAIS acima ou seja retornado pela ferramenta "consultarTransacoes".
- Se o usuário perguntar algo sobre seu extrato, compras passadas ou se um pagamento foi feito, consulte os LANÇAMENTOS REAIS acima ou chame "consultarTransacoes". Se não encontrar, diga com sinceridade e exatidão: "Não encontrei nenhum lançamento de [X] registrado no mês de ${ctx.monthLabel}."
- Seja extremamente DIRETO, OBJETIVO e SUCINTO. Elimine rodeios, introduções prolixas, encerramentos repetitivos e sermões não solicitados.

8. MENSAGENS DE ÁUDIO E COMANDOS POR VOZ (MULTIMODAL):
- Quando o usuário gravar áudio ou enviar mensagem de voz (ou se houver áudio anexado):
  1. Transcreva com fidelidade logo no início da sua resposta:
     🎙️ **Áudio:** *"[texto exato falado pelo usuário]"*
  2. Em seguida, processe e execute imediatamente as transações, contas divididas, consultas ou despesas solicitadas na mensagem de áudio!
- Ao registrar, editar ou excluir despesas, confirme a operação em NO MÁXIMO 2 a 3 linhas objetivas.
- NÃO dê sermões ou lições financeiras a menos que o usuário explicitamente peça conselhos.

8. IMPORTAÇÃO DE FATURAS E EXTRATOS BANCÁRIOS EM PDF (ONBOARDING & GASTOS ANTERIORES):
- Se o usuário anexar um arquivo PDF (fatura de cartão ou extrato de conta bancária) ou colar dados de lançamentos:
  1. EXTRAÇÃO ANTES DA AGREGAÇÃO (CHAIN-OF-THOUGHT):
     - O cálculo de totais nunca deve ser feito pela LLM de cabeça. A LLM deve apenas extrair as linhas literais do documento, e o código da ferramenta "registrarTransacoesLote" deve somar os valores a partir da lista extraída.
     - Extraia rigorosamente cada linha com: data, descricao/contraparte, valorTotal, tipo ("income" ou "expense"), metodoPagamento e categoria.
     - IGNORE movimentações internas de reserva (Aplicações RDB, Resgates RDB, Guardar Dinheiro) e pagamentos de fatura de cartão já existentes, marcando-as como categoria_filtro: "ignorada" para que não distorçam os totais de receitas nem despesas.

  2. IDENTIFIQUE COM PRECISÃO O TIPO DE DOCUMENTO:
     A) SE FOR EXTRATO DE CONTA BANCÁRIA (CONTA CORRENTE / POUPANÇA / NUCONTA / INTER CONTA / ITAÚ EXTRATO / BRADESCO):
        - Defina obrigatoriamente: origemDocumento = "extrato_conta" e metodoPagamentoPadrao = "pix" ou "debit"!
        - TODAS as ENTRADAS / CRÉDITOS / VALORES POSITIVOS (identificados com +, C, Crédito, Pix Recebido, Salário, Depósito, TED Recebida, Transferência Recebida, Rendimento):
          * tipo: "income"
          * categoria: "Receita"
          * metodoPagamento: "pix" (ou "debit")
          * status: "paid"
          * NUNCA marque como despesa de cartão de crédito! Entradas AUMENTAM o Saldo Livre!
        - TODAS as SAÍDAS / DÉBITOS / GASTOS DE CONTA (identificados com -, D, Débito, Pix Enviado, Transferência Enviada, Compra Débito, Pagamento de Boleto):
          * tipo: "expense"
          * metodoPagamento: "pix" (para Pix), "debit" (para débito) ou "bill" (para boletos)
          * status: "paid"
          * NUNCA marque como cartão de crédito (credit_card) a menos que a linha do extrato diga expressamente "Compra Cartão de Crédito"!
        - mesFaturaPadrao: mês em que as transações ocorreram (ex: "2026-10").
        - dataVencimentoPadrao: a data de cada movimentação.

     B) SE FOR FATURA DE CARTÃO DE CRÉDITO (FATURA NUBANK, FATURA ITAÚ CARTÕES, C6, SANTANDER, BRADESCO, ETC.):
        - Defina obrigatoriamente: origemDocumento = "fatura_cartao" e metodoPagamentoPadrao = "credit_card"!
        - As compras no cartão: tipo = "expense", metodoPagamento = "credit_card", status = "pending".
        - Estornos/créditos de devolução: valorTotal negativo (ex: -15.20) e categoria = "Estorno".
        - ATENÇÃO: IGNORE linhas de "Pagamento de fatura recebido", "Pagamento efetuado" ou "Saldo anterior" para NÃO duplicar lançamentos nem somar como novo gasto de cartão!
        - mesFaturaPadrao: mês de vencimento da fatura (ex: "2026-11").

  3. TRANSPARÊNCIA IMEDIATA NA CONFIRMAÇÃO (PRIMEIRA RESPOSTA AO USUÁRIO):
     - Sempre que pedir para o usuário clicar em **🟢 Confirmar e Executar**, JÁ EXIBA DE IMEDIATO na sua mensagem de resposta a tabela completa e formatada em Markdown com TODAS as transações válidas que compõem aquele total (Data | Tipo | Descrição / Contraparte | Valor), evitando que o usuário aprove uma importação às cegas ou precise pedir a listagem em uma segunda mensagem!
     - Exemplo de formato obrigatório na resposta:
       | Data | Tipo | Descrição / Contraparte | Valor |
       | :--- | :--- | :--- | :--- |
       | 05/10/2026 | 🟢 Entrada | Salário Empresa XPTO | R$ 5.200,00 |
       | 06/10/2026 | 🔴 Saída | Pix enviado Brisanet | R$ 99,90 |
     - Destaque com clareza o total exato de receitas (entradas) e de despesas (saídas) calculados pela ferramenta.

  4. EXECUÇÃO EM LOTE:
     - Chame SEMPRE a ferramenta "registrarTransacoesLote" preenchendo origemDocumento, metodoPagamentoPadrao e a lista completa de transações com cada campo tipo, metodoPagamento, categoria, contraparte e categoria_filtro!

9. SAÚDE FINANCEIRA (4 PILARES) E ENVELOPES ORÇAMENTÁRIOS INTELIGENTES:
- O Saldo Livre integra **Saúde Financeira (Score 0 a 100)** e **Envelopes Orçamentários Inteligentes** em um motor único e realista:
  * **Envelopes Fixos vs. Variáveis:** Categorias fixas mensais (Moradia, Assinaturas, Educação, Financiamentos, Impostos) têm ritmo avaliado apenas pelo limite total do mês (sem falso alarme de ritmo diário no início do mês). Categorias variáveis (Alimentação, Lazer, Transporte, Compras, etc.) projetam o ritmo diário (burn rate) e calculam a meta diária restante.
  * **Sinergia dos 4 Pilares da Saúde Financeira:**
    1. Taxa de Poupança & Margem (35%): Avalia o percentual aportado em Investimentos + reconhece positivamente o Saldo Livre positivo projetado do mês.
    2. Controle do Custo de Vida Real (35%): Desconta aportes em Investimentos do gasto bruto, para que investir mais de 20% da renda nunca penalize o indicador de custo de vida.
    3. Reserva de Emergência (30%): Mede quantos meses do custo de vida real estão cobertos pela Reserva de Emergência (meta ideal: 6 meses).
    4. Disciplina nos Envelopes Orçamentários: Concede bônus (+4 pts) quando todos os envelopes estão sob controle e aplica penalidade quando há categorias estouradas.
- Quando o usuário perguntar sobre sua **Saúde Financeira**, como subir seu Score, análise dos pilares ou pedir um diagnóstico:
  * Chame "gerenciarEnvelopesOrcamentarios" com acao: "diagnostico_saude_financeira" e apresente um diagnóstico consultivo claro, citando as notas dos 4 pilares e as alavancas práticas com valores exatos em R$ e quantos pontos ele ganha no Score!
- Quando o usuário pedir para ver seus **Envelopes Orçamentários**, definir um teto (ex: "defina o teto de Alimentação em R$ 1.200"), rebalancear categorias ("reequilibrar orçamento" -> acao: "rebalancear_automatico"), sugerir/aplicar tetos com IA (acao: "sugerir_tetos" ou "aplicar_sugestao_ia") ou simular se uma compra cabe no orçamento ("posso comprar uma TV de R$ 2.400 em 6x?" -> acao: "simular_compra"):
  * Chame SEMPRE a ferramenta "gerenciarEnvelopesOrcamentarios" com a ação correspondente!

10. CONFIRMAÇÃO PRÉVIA OBRIGATÓRIA ANTES DE QUALQUER EXECUÇÃO NO SISTEMA (HUMAN-IN-THE-LOOP):
- NUNCA assuma que você executa as ações no banco diretamente. Antes de efetivar alterações no banco de dados, o backend (Firestore) é bloqueado e as ferramentas SEMPRE retornam "aguardandoConfirmacao: true" em um estado de DRAFT (rascunho).
- QUANDO A FERRAMENTA RETORNAR "aguardandoConfirmacao: true":
  * O seu papel é apenas preparar a ação e exibi-la para o usuário.
  * Demonstre com total clareza que você ENTENDEU a solicitação e cite os parâmetros montados.
  * Resuma o que você entendeu de forma direta:
    - Exemplo de despesa: "Entendi que você deseja registrar uma despesa de **R$ 45,00** na **Padaria** no débito para o mês de **Outubro 2026**."
  * Convide o usuário a revisar os detalhes no cartão visual e confirmar clicando no botão **🟢 Confirmar e Executar** (a execução real no banco ocorrerá apenas após esse clique explícito do usuário).
  * NUNCA afirme que a ação já foi salva ou gravada! Diga sempre que a transação foi "preparada/criada em modo rascunho" e está "aguardando a aprovação e confirmação explícita".

11. CRIAÇÃO DE GRÁFICOS FINANCEIROS INTERATIVOS (DATA VISUALIZATION):
Sempre que o usuário pedir uma análise visual, comparativo, projeção gráfica ou perguntar "faça um gráfico de...", "analise com gráfico", "mostre a evolução", "distribuição de gastos", "como estão meus investimentos", ou você achar relevante enriquecer a resposta com visualização, inclua em sua resposta um bloco de código markdown no formato:

\`\`\`chart
{
  "type": "donut",
  "title": "Distribuição dos Gastos de Outubro",
  "description": "Total analisado: R$ 3.450,00",
  "data": [
    { "label": "Moradia", "value": 1500.00, "color": "#6366f1" },
    { "label": "Alimentação", "value": 850.00, "color": "#10b981" },
    { "label": "Investimentos", "value": 600.00, "color": "#8b5cf6" },
    { "label": "Lazer", "value": 500.00, "color": "#f59e0b" }
  ]
}
\`\`\`

Tipos de gráficos suportados:
- "donut" (ou "pie"): excelente para distribuição percentual de categorias, divisão de classes de investimentos (Renda Fixa, Ações, FIIs, Cripto), ou proporção de gastos vs sobra.
- "bar": excelente para comparação entre categorias, orçamentos vs realizados, ou comparativo de gastos.
- "line": excelente para evolução temporal, patrimônio acumulado ou comportamento do saldo diário.
IMPORTANTE: Use sempre dados numéricos reais obtidos do contexto (NUNCA invente números). O valor deve ser um número simples (ex: 1500.50). Você também pode chamar a ferramenta "gerarGraficoFinanceiro".

EXEMPLOS PRÁTICOS E PADRÕES BANCÁRIOS BRASIL (FEW-SHOT):

Exemplo A1 — Extrato bancário de Conta Corrente (com Entradas e Saídas via Pix/Débito):
Entrada:
"Extrato Conta Nubank - Outubro 2026:
05 OUT - Transferência Recebida de Empresa XPTO R$ 5.200,00
06 OUT - Pix enviado para João da Silva R$ 150,00
08 OUT - Compra no Débito Padaria Pão Quente R$ 28,50
10 OUT - Pagamento de Boleto Enel Luz R$ 210,00"
Ação da IA:
Chamar "registrarTransacoesLote" com:
- origemDocumento: "extrato_conta"
- metodoPagamentoPadrao: "pix"
- mesFaturaPadrao: "2026-10"
- transacoes:
  * { descricao: "Salário Empresa XPTO", valorTotal: 5200.00, tipo: "income", categoria: "Receita", metodoPagamento: "pix", status: "paid", tipoDivisao: "MEU" }
  * { descricao: "Pix para João da Silva", valorTotal: 150.00, tipo: "expense", categoria: "Outros", metodoPagamento: "pix", status: "paid", tipoDivisao: "MEU" }
  * { descricao: "Padaria Pão Quente", valorTotal: 28.50, tipo: "expense", categoria: "Alimentação", metodoPagamento: "debit", status: "paid", tipoDivisao: "MEU" }
  * { descricao: "Enel Luz", valorTotal: 210.00, tipo: "expense", categoria: "Moradia", metodoPagamento: "bill", status: "paid", tipoDivisao: "MEU" }

Exemplo A2 — Fatura de Cartão de Crédito (Gastos e Estornos):
Entrada:
"Fatura Cartão Nubank Vencimento 14/11:
04 OUT - IFOOD *RESTAURANTE R$ 78,50 50/50 com a Mari
05 OUT - POSTO IPIRANGA R$ 180,00
08 OUT - ESTORNO UBER -R$ 15,20
11 OUT - FARMACIA RAIA R$ 52,90
12 OUT - PAGAMENTO DE FATURA RECEBIDO -R$ 2.450,00"
Ação da IA:
Chamar "registrarTransacoesLote" com:
- origemDocumento: "fatura_cartao"
- cartaoPadrao: "Nubank"
- mesFaturaPadrao: "2026-11"
- dataVencimentoPadrao: "2026-11-14"
- transacoes (ignora a linha de pagamento de fatura recebido):
  * { descricao: "iFood Restaurante", valorTotal: 78.50, tipo: "expense", categoria: "Alimentação", metodoPagamento: "credit_card", tipoDivisao: "CASAL_50_50" }
  * { descricao: "Posto Ipiranga", valorTotal: 180.00, tipo: "expense", categoria: "Transporte", metodoPagamento: "credit_card", tipoDivisao: "MEU" }
  * { descricao: "Estorno Uber", valorTotal: -15.20, tipo: "expense", categoria: "Estorno", metodoPagamento: "credit_card", tipoDivisao: "MEU" }
  * { descricao: "Farmacia Raia", valorTotal: 52.90, tipo: "expense", categoria: "Saúde", metodoPagamento: "credit_card", tipoDivisao: "MEU" }

Exemplo B — Compra parcelada com notação brasileira XX/YY:
Entrada: "Lança Magazine Luiza 03/10 R$ 189,90 na fatura de novembro"
Ação da IA:
Chamar "registrarTransacao" com:
- descricao: "Magazine Luiza"
- valorTotal: 189.90
- parcelaAtual: 3, totalParcelas: 10
- mesFatura: "2026-11"
- tipoDivisao: "MEU"

Exemplo C — Divisão com amigos ("rachar", "minha cota", "cota do amigo"):
Entrada: "Rachei a conta do bar de R$ 120 com o Kayo, ele paga 40 e eu o resto"
Ação da IA:
Chamar "registrarTransacao" com:
- descricao: "Conta do Bar"
- valorTotal: 120.00
- tipoDivisao: "AMIGO", nomeAmigo: "Kayo", emailAmigo: "kayoaraujo2002@gmail.com"
- valorAmigo: 40.00, valorMeu: 80.00 (ou porcentagens equivalentes)
- categoria: "Lazer"

Exemplo D — Empréstimo de cartão para terceiro ("Mãe", "Pai", parente):
Entrada: "Passei uma compra de R$ 340 no meu cartão pra minha mãe, ela vai me fazer um Pix"
Ação da IA:
Chamar "registrarTransacao" com:
- descricao: "Compra Mãe"
- valorTotal: 340.00
- tipoDivisao: "TERCEIRO", nomeTerceiro: "Mãe"
- categoria: "Cartão Terceiros"
- status: "pending", metodoPagamento: "credit_card"

Exemplo E — Comando de aprendizado de regras:
Entrada: "Aprenda que todo gasto no Zé Delivery é Lazer e 100% meu"
Ação da IA:
Chamar "gerenciarRegrasEMemoria" com:
- acao: "aprender"
- regra: "Todo gasto no Zé Delivery pertence à categoria Lazer e é 100% MEU (sem divisão)."
Respostas da IA:
"🧠 Perfeito! Já memorizei essa regra. A partir de agora, qualquer compra no Zé Delivery será lançada automaticamente como Lazer e 100% sua!"`
}

/**
 * Constrói o histórico de mensagens garantindo as regras rígidas da API do Gemini:
 * 1. O primeiro turno DEVE ser do papel 'user' (descarta mensagem de boas-vindas do assistente).
 * 2. Os turnos devem alternar rigorosamente entre 'user' e 'model'.
 * 3. A mensagem atual do usuário é incluída como o turno final de 'user'.
 */
export function buildChatContents(
  history: ChatMessage[],
  newUserText: string,
  newAttachment?: ChatAttachment,
): Content[] {
  const filtered = history.filter(
    (m) => m.id !== 'msg_welcome' && !m.pending && !m.error && m.content && m.content.trim(),
  )

  const turns: Content[] = []
  for (const m of filtered.slice(-20)) {
    const role: 'user' | 'model' = m.role === 'user' ? 'user' : 'model'
    if (turns.length === 0 && role !== 'user') continue

    const actionDetails =
      m.actions && m.actions.length > 0
        ? '\n' +
          m.actions
            .map(
              (a) =>
                `[Ação executada: ${a.title} - ${a.lines.map((l) => `${l.label}: ${l.value}`).join(', ')}]`,
            )
            .join('\n')
        : ''
    const pendingTxs =
      m.pending_transactions ||
      m.actions?.flatMap((a) => a.pending_transactions || (a.pendingAction?.args?.pending_transactions as any) || []).filter(Boolean)
    const pendingTxsDetail =
      pendingTxs && pendingTxs.length > 0
        ? '\n[Transações exatas extraídas salvas no estado (' +
          pendingTxs.length +
          ' itens):\n' +
          pendingTxs
            .map(
              (t: any, idx: number) =>
                `• ${idx + 1}. Data: ${t.data} | Tipo: ${t.tipo} | Valor: R$ ${Number(t.valor_exato || t.valorTotal || 0).toFixed(2)} | Contraparte: ${t.contraparte || t.descricao} | Filtro: ${t.categoria_filtro || 'valida'}`,
            )
            .join('\n') +
          '\n]'
        : ''
    const isAudio =
      m.attachment?.type?.startsWith('audio/') ||
      /\.(webm|mp4|m4a|wav|ogg|aac)$/i.test(m.attachment?.name || '')
    const attachmentNote = m.attachment
      ? isAudio
        ? `\n[Mensagem de áudio anexada: ${m.attachment.name}]`
        : `\n[Arquivo PDF anexado: ${m.attachment.name}]`
      : ''
    const text = m.content + attachmentNote + actionDetails + pendingTxsDetail

    const lastTurn = turns[turns.length - 1]
    if (lastTurn && lastTurn.role === role) {
      if (!lastTurn.parts) lastTurn.parts = []
      lastTurn.parts.push({ text })
    } else {
      turns.push({ role, parts: [{ text }] })
    }
  }

  // Preparar partes do turno atual do usuário
  let promptText = newUserText
  if (newAttachment?.extractedText && newAttachment.extractedText.trim().length > 0) {
    promptText += `\n\n--- TEXTO EXATO E VERBATIM EXTRAÍDO DO ARQUIVO PDF (${newAttachment.name}) ---\nUtilize rigorosamente as linhas, datas, nomes de estabelecimentos e valores abaixo para cadastrar com precisão milimétrica sem alucinar nenhum valor ou dado que não conste aqui:\n"""\n${newAttachment.extractedText.slice(0, 35000)}\n"""\n--- FIM DO TEXTO EXTRAÍDO DO PDF ---`
  }

  const userParts: Part[] = [{ text: promptText }]
  if (newAttachment?.base64) {
    const isAudio =
      newAttachment.type?.startsWith('audio/') ||
      /\.(webm|mp4|m4a|wav|ogg|aac)$/i.test(newAttachment.name || '')
    let detectedMime = newAttachment.type
    if (!detectedMime || detectedMime === 'application/pdf') {
      if (isAudio) {
        detectedMime = newAttachment.name.endsWith('.mp4') ? 'audio/mp4' : 'audio/webm'
      } else {
        detectedMime = 'application/pdf'
      }
    }
    userParts.push({
      inlineData: {
        mimeType: detectedMime,
        data: newAttachment.base64,
      },
    })
  }

  const lastTurn = turns[turns.length - 1]
  if (lastTurn && lastTurn.role === 'user') {
    if (!lastTurn.parts) lastTurn.parts = []
    lastTurn.parts.push(...userParts)
  } else {
    turns.push({ role: 'user', parts: userParts })
  }

  return turns
}

const rateLimitedKeysCooldown = new Map<string, number>()
let keyIndex = 0

async function callGeminiWithResilience(params: {
  apiKey?: string
  model: string
  contents: Content[]
  systemInstruction: string
  tools: Array<{ functionDeclarations: FunctionDeclaration[] }>
  onModelChanged?: (newModel: string) => void
  onStatusUpdate?: (status: string) => void
  maxRounds?: number
  userQuery?: string
}): Promise<{ res: any; activeModel: string }> {
  const { apiKey, contents, systemInstruction, tools, onModelChanged, onStatusUpdate } = params

  const rawKeys = apiKey
    ? apiKey.split(/[,;\n]/).map((k) => k.trim()).filter(Boolean)
    : []
  const keys = rawKeys.length > 0 ? rawKeys : getGeminiApiKeys()

  if (keys.length === 0) {
    throw new Error('Nenhuma chave de API do Google Gemini configurada.')
  }

  const initialModel = params.userQuery
    ? resolveTargetModel(params.userQuery, params.model)
    : params.model

  // Cascata completa de Modelos (paridade com Android GeminiLoadBalancer):
  const modelCandidates = [
    initialModel,
    ...DEFAULT_MODELS_POOL.filter((m) => m !== initialModel),
    'gemini-3-flash',
    'gemini-2.5-pro',
    'gemini-2.5-flash',
  ].filter((m, idx, arr) => arr.indexOf(m) === idx)

  let lastError: unknown = null
  const numKeys = keys.length
  const maxRounds = params.maxRounds ?? 3

  for (let round = 0; round < maxRounds; round++) {
    const startKeyIdx = (keyIndex++ & 0x7fffffff) % numKeys

    for (let kStep = 0; kStep < numKeys; kStep++) {
      const currentKeyIdx = (startKeyIdx + kStep) % numKeys
      const candidateKey = keys[currentKeyIdx]

      // Cooldown em caso de 429 se houver mais de uma chave disponível
      const cooldownUntil = rateLimitedKeysCooldown.get(candidateKey)
      const now = Date.now()
      if (numKeys > 1 && cooldownUntil && now < cooldownUntil) {
        continue
      }

      const ai = new GoogleGenAI({ apiKey: candidateKey })

      for (const candidateModel of modelCandidates) {
        for (let attempt = 0; attempt < 2; attempt++) {
          try {
            const modelLabel = candidateModel.replace('gemini-', '').replace('-latest', '')
            onStatusUpdate?.(
              round === 0
                ? `Consultando Gemini ${modelLabel}...`
                : `Tentando Gemini ${modelLabel} (tentativa ${round + 1})...`
            )

            const config: Record<string, unknown> = {
              systemInstruction,
              tools,
              temperature: 0.1,
              topP: 0.95,
              topK: 40,
            }

            const res = await ai.models.generateContent({
              model: candidateModel,
              contents,
              config,
            })

            if (!res) {
              throw new Error(`Nenhuma resposta retornada pelo modelo ${candidateModel}.`)
            }

            if (candidateModel !== initialModel) {
              onModelChanged?.(candidateModel)
              setSelectedGeminiModel(candidateModel)
            }

            return { res, activeModel: candidateModel }
          } catch (err) {
            lastError = err
            const errStr = ((err as Error)?.message || String(err)).toLowerCase()
            const isRateLimit =
              errStr.includes('429') ||
              errStr.includes('resource_exhausted') ||
              errStr.includes('quota')

            const isTransient =
              errStr.includes('503') ||
              errStr.includes('unavailable') ||
              errStr.includes('high demand') ||
              errStr.includes('spikes in demand') ||
              errStr.includes('timeout') ||
              errStr.includes('socket') ||
              errStr.includes('connect') ||
              errStr.includes('network')

            if (isRateLimit) {
              if (numKeys > 1) {
                rateLimitedKeysCooldown.set(candidateKey, Date.now() + 45_000)
                break // Pula para a próxima chave
              } else {
                await new Promise((r) => setTimeout(r, 2000))
                break
              }
            }

            // Se for erro transitório de sobrecarga (503), aguarda 1.5s antes de tentar novamente o mesmo modelo
            if (attempt === 0 && isTransient) {
              await new Promise((r) => setTimeout(r, 1500))
              continue
            }

            // Se falhou na 2ª tentativa ou for outro erro, avança para o próximo modelo do pool
            break
          }
        }
      }
    }

    if (round < maxRounds - 1) {
      const backoffMs = (round + 1) * 2000
      onStatusUpdate?.(`Aguardando servidores do Gemini (${round + 1}/${maxRounds})...`)
      await new Promise((r) => setTimeout(r, backoffMs))
    }
  }

  throw (
    lastError ||
    new Error('Não foi possível obter resposta dos servidores do Google Gemini após várias tentativas.')
  )
}

export async function runGeminiAgent(opts: {
  apiKey?: string
  history: ChatMessage[]
  userMessage: string
  attachment?: ChatAttachment
  systemInstruction: string
  execute: ToolExecutor
  model?: string
  onModelChanged?: (newModel: string) => void
  onStatusUpdate?: (status: string) => void
}): Promise<{ text: string; actions: ActionCardData[] }> {
  const contents: Content[] = buildChatContents(opts.history, opts.userMessage, opts.attachment)
  const actions: ActionCardData[] = []
  let activeModel = opts.model || getSelectedGeminiModel() || GEMINI_MODEL

  for (let step = 0; step < 6; step++) {
    let res: any
    try {
      const outcome = await callGeminiWithResilience({
        apiKey: opts.apiKey,
        model: activeModel,
        userQuery: opts.userMessage,
        contents,
        systemInstruction: opts.systemInstruction,
        tools: [{ functionDeclarations }],
        onModelChanged: (m) => {
          activeModel = m
          opts.onModelChanged?.(m)
        },
        onStatusUpdate: opts.onStatusUpdate,
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
    const results = await Promise.all(
      calls.map(async (fc: any) => {
        let result: ToolResult
        try {
          result = await opts.execute(fc.name as ToolName, (fc.args ?? {}) as Record<string, unknown>)
        } catch (e) {
          result = { response: { ok: false, erro: (e as Error).message } }
        }
        return { fc, result }
      })
    )

    for (const { fc, result } of results) {
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

/**
 * Transcreve áudio com extrema fidelidade em português do Brasil usando a API multimodal do Gemini.
 * Suporta gravações de celulares (mp4/aac no iOS Safari e webm/opus no Android Chrome).
 */
export async function transcribeAudioWithGemini(opts: {
  apiKey?: string
  audioBase64: string
  mimeType: string
}): Promise<string> {
  const providedKeys = opts.apiKey
    ? opts.apiKey.split(/[,;\n]/).map((k) => k.trim()).filter(Boolean)
    : []
  const keys = providedKeys.length > 0 ? providedKeys : getGeminiApiKeys()
  if (keys.length === 0) {
    throw new Error('Nenhuma chave de API configurada para transcrição.')
  }

  const models = [
    'gemini-3.5-flash',
    'gemini-flash-latest',
    'gemini-3.5-flash-lite',
    'gemini-flash-lite-latest',
    'gemini-3.1-flash-lite',
    'gemini-3-flash',
  ]
  let lastError: unknown = null

  let normalizedMime = opts.mimeType || 'audio/webm'
  if (normalizedMime.includes('webm')) normalizedMime = 'audio/webm'
  else if (normalizedMime.includes('mp4') || normalizedMime.includes('m4a')) normalizedMime = 'audio/mp4'
  else if (normalizedMime.includes('aac')) normalizedMime = 'audio/aac'
  else if (normalizedMime.includes('ogg')) normalizedMime = 'audio/ogg'
  else if (normalizedMime.includes('wav')) normalizedMime = 'audio/wav'

  for (const candidateKey of keys) {
    const ai = new GoogleGenAI({ apiKey: candidateKey })
    for (const model of models) {
      try {
        const res = await ai.models.generateContent({
          model,
          contents: [
            {
              role: 'user',
              parts: [
                {
                  text: 'Transcreva com fidelidade absoluta em português do Brasil o que foi falado neste áudio. Retorne EXCLUSIVAMENTE o texto transcrito, sem aspas, sem saudações, sem introduções e sem explicações.',
                },
                {
                  inlineData: {
                    mimeType: normalizedMime,
                    data: opts.audioBase64,
                  },
                },
              ],
            },
          ],
          config: {
            temperature: 0.1,
          },
        })

        const text = (res.text || '').trim().replace(/^["']|["']$/g, '')
        if (text) return text
      } catch (err) {
        lastError = err
        console.warn(`Falha na transcrição com modelo ${model}:`, err)
      }
    }
  }

  if (lastError) throw lastError
  return ''
}

// ------------------------------------------------------------------
// Gemini Smart Budget & Health Diagnosis
// ------------------------------------------------------------------

export interface SmartBudgetPlanParams {
  monthlyIncome: number
  carryoverAmount?: number
  spentByCategory: Record<string, number>
  pendingByCategory: Record<string, number>
  history3mAverages: Record<string, number>
  existingLimits?: Record<string, number>
}

/**
 * Sugere um plano de orçamento de envelopes inteligente utilizando o Gemini com structured JSON output.
 * NUNCA gera centavos quebrados (sempre inteiros múltiplos de R$ 10 ou R$ 50) e NUNCA sugere teto
 * menor do que o que já está comprometido (pago + contas a vencer).
 */
export async function generateSmartBudgetPlan(
  params: SmartBudgetPlanParams,
): Promise<SmartBudgetPlan> {
  const keys = getGeminiApiKeys()
  const income = Math.max(0, Number(params.monthlyIncome) || 0)

  // 1. Canonicaliza todas as categorias de entrada para evitar duplicação ou divergência entre aliases
  const canonicalSpent: Record<string, number> = {}
  for (const [k, v] of Object.entries(params.spentByCategory || {})) {
    if (v > 0) {
      const canon = normalizeBudgetCategory(k)
      canonicalSpent[canon] = round2((canonicalSpent[canon] || 0) + v)
    }
  }

  const canonicalPending: Record<string, number> = {}
  for (const [k, v] of Object.entries(params.pendingByCategory || {})) {
    if (v > 0) {
      const canon = normalizeBudgetCategory(k)
      canonicalPending[canon] = round2((canonicalPending[canon] || 0) + v)
    }
  }

  const canonicalHistory: Record<string, number> = {}
  for (const [k, v] of Object.entries(params.history3mAverages || {})) {
    if (v > 0) {
      const canon = normalizeBudgetCategory(k)
      canonicalHistory[canon] = round2(Math.max(canonicalHistory[canon] || 0, v))
    }
  }

  const canonicalLimits: Record<string, number> = {}
  for (const [k, v] of Object.entries(params.existingLimits || {})) {
    if (v > 0) {
      const canon = normalizeBudgetCategory(k)
      canonicalLimits[canon] = round2((canonicalLimits[canon] || 0) + v)
    }
  }

  const categoriesSet = new Set<string>([
    ...Object.keys(canonicalSpent),
    ...Object.keys(canonicalPending),
    ...Object.keys(canonicalHistory),
    ...Object.keys(canonicalLimits),
  ])

  const categoryContext = Array.from(categoriesSet)
    .filter((cat) => cat && !/rendimento|sal[áa]rio/i.test(cat))
    .map((cat) => {
      const spent = canonicalSpent[cat] || 0
      const pending = canonicalPending[cat] || 0
      const committed = round2(spent + pending)
      const histAvg = canonicalHistory[cat] || 0
      const curLim = canonicalLimits[cat] || 0
      return `- ${cat}: pago = R$ ${spent.toFixed(2)}, a vencer = R$ ${pending.toFixed(2)} (comprometido total = R$ ${committed.toFixed(2)}), média 3 meses = R$ ${histAvg.toFixed(2)}, teto atual = R$ ${curLim.toFixed(2)}`
    })
    .join('\n')

  const prompt = `Você é um planejador financeiro pessoal de elite e especialista na Regra 50/30/20.
Sua missão é sugerir um plano de orçamento mensal (envelopes) realista, disciplinado e seguro para o usuário.

DADOS DO MÊS:
- Receita líquida prevista: R$ ${income.toFixed(2)}
- Gastos e compromissos por categoria:
${categoryContext || 'Nenhum dado prévio.'}

REGRAS OBRIGATÓRIAS DE PLANEJAMENTO:
1. PISO INEGOCIÁVEL: Se uma categoria tiver gastos ou contas a vencer (comprometido total > 0), o "suggestedLimit" NUNCA pode ser menor do que o total comprometido (já pago + contas a vencer). Além disso, a justificativa ("reason") JAMAIS deve dizer que a categoria está sem compromissos ou zerada quando houver comprometimento! Só mencione "sem compromissos" se o comprometido for estritamente R$ 0,00.
2. A soma de todos os tetos de despesas sugeridos NÃO DEVE ultrapassar a receita prevista do mês (R$ ${income.toFixed(2)}). Se a receita for maior que os gastos, reserve pelo menos 15-20% para "Investimentos" (Futuro/Aportes).
3. "Investimentos" NÃO é despesa de consumo; é meta de aporte da regra 50/30/20.
4. ARREDONDAMENTO OBRIGATÓRIO: NUNCA sugira valores com centavos quebrados (como R$ 499,08 ou R$ 338,38). Todos os valores de "suggestedLimit" DEVEM ser inteiros múltiplos de R$ 10,00 ou R$ 50,00 (ex: 150, 400, 750, 1200).
5. Cada alocação deve ter uma justificativa curta ("reason") de 1 frase explicando a escolha com base nos dados reais de comprometimento e média.
6. Retorne estritamente um JSON no seguinte formato:
{
  "summaryInsight": "Resumo executivo curto de 1-2 frases explicando a estratégia adotada (ex: Regra 50/30/20 adaptada protegendo R$ 1.280 em contas a vencer com folga real garantida).",
  "allocations": [
    {
      "category": "Alimentação",
      "suggestedLimit": 700,
      "reason": "Baseado na sua média de R$ 620 com margem de segurança de 10% cobrindo o que já foi gasto."
    }
  ]
}`

  if (keys.length > 0) {
    for (const key of keys) {
      const ai = new GoogleGenAI({ apiKey: key })
      for (const model of ['gemini-3.5-flash', 'gemini-3.5-flash-lite', 'gemini-flash-latest']) {
        try {
          const res = await ai.models.generateContent({
            model,
            contents: prompt,
            config: {
              responseMimeType: 'application/json',
              temperature: 0.2,
            },
          })
          const text = (res.text || '').trim()
          if (text) {
            const parsed = JSON.parse(text) as SmartBudgetPlan
            if (parsed && Array.isArray(parsed.allocations) && parsed.allocations.length > 0) {
              return sanitizeAndValidateBudgetPlan(
                parsed,
                income,
                canonicalSpent,
                canonicalPending,
              )
            }
          }
        } catch (err) {
          console.warn(`Falha no Gemini Smart Budget com modelo ${model}:`, err)
        }
      }
    }
  }

  // Fallback determinístico inteligente
  const fallbackAllocations: SmartBudgetAllocation[] = []
  for (const cat of categoriesSet) {
    if (!cat || /rendimento|sal[áa]rio/i.test(cat)) continue
    const spent = canonicalSpent[cat] || 0
    const pending = canonicalPending[cat] || 0
    const committed = round2(spent + pending)
    const histAvg = canonicalHistory[cat] || 0
    const minFloor = Math.ceil(committed / 10) * 10
    const isFixed = /moradia|financiamento|assinatura/i.test(cat)
    const baseTarget = isFixed ? Math.max(histAvg, committed) : Math.max(histAvg * 1.1, committed)
    const limit = Math.max(50, minFloor, Math.ceil(baseTarget / 10) * 10)

    fallbackAllocations.push({
      category: cat,
      suggestedLimit: limit,
      reason: pending > 0
        ? `Protegendo R$ ${pending.toFixed(2)} em contas a vencer e cobrindo média de R$ ${histAvg.toFixed(2)}.`
        : committed > 0
          ? `Garante os R$ ${committed.toFixed(2)} já pagos no mês com margem de segurança.`
          : `Margem de segurança calculada sobre sua média histórica de R$ ${histAvg.toFixed(2)}.`,
    })
  }

  const totalPending = Object.values(canonicalPending).reduce((a, b) => a + b, 0)
  const rawFallback: SmartBudgetPlan = {
    summaryInsight: `Plano inteligente calculado com Regra 50/30/20 protegendo R$ ${totalPending.toFixed(2)} em contas a vencer com tetos arredondados.`,
    allocations: fallbackAllocations,
  }
  return sanitizeAndValidateBudgetPlan(
    rawFallback,
    income,
    canonicalSpent,
    canonicalPending,
  )
}

export interface FinancialHealthDiagnosisParams {
  score: number
  classification: string
  savingsScore: number
  spendingLimitScore: number
  emergencyReserveScore: number
  envelopeScore: number
  savingsRatePercentage: number
  spendingRatioPercentage: number
  emergencyCoverageMonths: number
  monthlyLivingCost: number
  monthlyIncome: number
  actionableTips?: string[]
}

/**
 * Gera um diagnóstico financeiro rápido e acionável com 3 recomendações práticas do Gemini.
 */
export async function generateFinancialHealthAiDiagnosis(
  params: FinancialHealthDiagnosisParams,
): Promise<FinancialHealthAiDiagnosis> {
  const keys = getGeminiApiKeys()
  const prompt = `Você é um consultor financeiro pessoal sênior de fintech.
Analise os 4 pilares do Score de Saúde Financeira do usuário (0 a 100 pontos):
- Score Total: ${params.score}/100 (${params.classification.toUpperCase()})
- Pilar 1: Poupança/Investimentos = ${params.savingsScore}/30 pts (Taxa: ${params.savingsRatePercentage}% da renda)
- Pilar 2: Custo de Vida / Comprometimento = ${params.spendingLimitScore}/30 pts (${params.spendingRatioPercentage}% da renda, Custo: R$ ${params.monthlyLivingCost.toFixed(2)})
- Pilar 3: Reserva de Emergência = ${params.emergencyReserveScore}/25 pts (Cobre ${params.emergencyCoverageMonths.toFixed(1)} meses de custo)
- Pilar 4: Controle de Envelopes = ${params.envelopeScore}/15 pts

Gere um diagnóstico ultra objetivo e um plano de ação estritamente no formato JSON:
{
  "overallAssessment": "1 a 2 frases avaliando a saúde financeira atual de forma direta e motivadora.",
  "actionPlan": [
    "Dica prática 1 com emoji, métrica exata em R$ e ganho estimado de pontos",
    "Dica prática 2 com emoji, métrica exata em R$ e ganho estimado de pontos",
    "Dica prática 3 com emoji, métrica exata em R$ e ganho estimado de pontos"
  ]
}`

  if (keys.length > 0) {
    for (const key of keys) {
      const ai = new GoogleGenAI({ apiKey: key })
      for (const model of ['gemini-3.5-flash', 'gemini-3.5-flash-lite', 'gemini-flash-latest']) {
        try {
          const res = await ai.models.generateContent({
            model,
            contents: prompt,
            config: {
              responseMimeType: 'application/json',
              temperature: 0.2,
            },
          })
          const text = (res.text || '').trim()
          if (text) {
            const parsed = JSON.parse(text) as FinancialHealthAiDiagnosis
            if (parsed && parsed.overallAssessment && Array.isArray(parsed.actionPlan)) {
              return parsed
            }
          }
        } catch (err) {
          console.warn(`Falha no Gemini Health Diagnosis com modelo ${model}:`, err)
        }
      }
    }
  }

  // Fallback determinístico caso offline
  const fallbackTips = params.actionableTips && params.actionableTips.length > 0
    ? params.actionableTips.slice(0, 3)
    : [
        params.savingsScore < 20
          ? '📈 Direcione pelo menos 20% da sua renda para a categoria Investimentos para elevar seu Score em até +15 pts.'
          : '📈 Mantenha sua constância de aportes mensais para consolidar o pilar de Poupança.',
        params.emergencyReserveScore < 20
          ? `🛡️ Aumente sua Reserva de Emergência para cobrir ao menos 3 meses do seu custo de vida (R$ ${(params.monthlyLivingCost * 3).toFixed(2)}).`
          : '🛡️ Sua reserva está em patamar seguro. Mantenha os recursos com liquidez diária.',
        params.envelopeScore < 12
          ? '📦 Reequilibre suas categorias variáveis que estão acima de 90% para recuperar pontos no controle de envelopes.'
          : '📦 Parabéns! Suas categorias orçadas estão disciplinadas dentro do teto.',
      ]

  return {
    overallAssessment: `Seu Score de Saúde Financeira é de ${params.score}/100 pontos (${params.classification}). O foco deve ser otimizar os pilares com maior potencial de ganho.`,
    actionPlan: fallbackTips,
  }
}


// ------------------------------------------------------------------
// Coach de Envelopes (insights acionáveis do Gemini)
// ------------------------------------------------------------------

/**
 * Converte a resposta JSON do Gemini em insights validados.
 * Ações que referenciem categoria inexistente ou valores fora do razoável são descartadas
 * (o texto do insight é mantido), para que a IA nunca dispare uma alteração perigosa.
 */
export function parseBudgetInsights(raw: string, validCategories: string[]): BudgetInsight[] {
  const MAX_INSIGHTS = 4
  const MAX_ADJUST_ABS = 5000
  let root: unknown
  try {
    root = JSON.parse(raw.replace(/```json|```/g, '').trim())
  } catch {
    return []
  }
  const list = (root as { insights?: unknown })?.insights
  if (!Array.isArray(list)) return []

  const canonical = new Map(validCategories.map((c) => [c.trim().toLowerCase(), c]))
  const rank: Record<BudgetInsight['severity'], number> = { info: 0, attention: 1, danger: 2 }
  const result: BudgetInsight[] = []

  list.forEach((entry, index) => {
    if (result.length >= MAX_INSIGHTS || !entry || typeof entry !== 'object') return
    const obj = entry as Record<string, unknown>
    const title = String(obj.title ?? '').trim()
    const message = String(obj.message ?? '').trim()
    if (!title || !message) return

    const sev = String(obj.severity ?? 'info').trim().toLowerCase()
    const severity: BudgetInsight['severity'] = sev === 'danger' ? 'danger' : sev === 'attention' ? 'attention' : 'info'

    let action: BudgetInsightAction | undefined
    const a = obj.action as Record<string, unknown> | undefined
    if (a && typeof a === 'object') {
      const type = String(a.type ?? 'none').trim().toLowerCase()
      if (type === 'adjust_limit') {
        const category = canonical.get(String(a.category ?? '').trim().toLowerCase())
        const amount = Number(a.amount)
        if (category && Number.isFinite(amount) && amount !== 0 && Math.abs(amount) <= MAX_ADJUST_ABS) {
          const rounded = Math.round(amount / 10) * 10
          if (rounded !== 0) {
            const label = String(a.label ?? '').trim() || (rounded > 0 ? `Subir teto em R$ ${rounded}` : `Reduzir teto em R$ ${Math.abs(rounded)}`)
            action = { type: 'adjust_limit', label: label.slice(0, 32), category, amount: rounded }
          }
        }
      } else if (type === 'ask_copilot') {
        const prompt = String(a.prompt ?? '').trim() || `Me ajude com: ${title}`
        action = { type: 'ask_copilot', label: (String(a.label ?? '').trim() || 'Perguntar à IA').slice(0, 32), prompt }
      }
    }

    result.push({
      id: `ai_${index}`,
      severity,
      title: title.slice(0, 70),
      message: message.slice(0, 220),
      action,
      fromAi: true,
    })
  })

  return result.sort((x, y) => rank[y.severity] - rank[x.severity])
}

/**
 * Coach de envelopes: o Gemini analisa o estado real dos tetos e devolve insights acionáveis.
 * Retorna null quando não há chave/resposta válida (o chamador mantém os insights locais).
 */
export async function generateBudgetCoachInsights(
  report: CategoryBudgetReport,
  health: Pick<
    FinancialHealthScore,
    'score' | 'savingsScore' | 'spendingLimitScore' | 'emergencyReserveScore' | 'envelopeScore'
  >,
): Promise<BudgetInsight[] | null> {
  const keys = getGeminiApiKeys()
  if (keys.length === 0 || report.items.length === 0) return null

  const brl = (v: number) => v.toFixed(2)
  const envelopes = report.items
    .map(
      (i) =>
        `- ${i.category}: teto R$ ${brl(i.limit)}, pago R$ ${brl(i.spentAmount)}, a vencer R$ ${brl(i.pendingAmount)}, uso ${Math.round(i.totalPercentage)}%, ${i.isFixedCategory ? 'FIXO' : 'VARIAVEL'}, status ${i.badgeLabel}, dias restantes ${i.daysRemaining}`,
    )
    .join('\n')

  const prompt = `Você é o coach de orçamento de um app de finanças pessoais brasileiro. Fale em português, tom direto e amigável, sem jargão.
Analise os envelopes do mês e devolva de 2 a 4 insights curtos e ACIONÁVEIS, priorizando o que mais impacta o usuário.

DADOS:
- Renda prevista: R$ ${brl(report.expectedIncome ?? 0)}
- Ainda pode gastar dentro dos tetos: R$ ${brl(report.totalRealRemaining)}
- Meta de aporte (20% da renda): R$ ${brl(report.investmentGoal ?? 0)} | já investido: R$ ${brl(report.investmentSpent ?? 0)}
- Score de saúde: ${health.score}/100 (poupança ${health.savingsScore}/30, custo de vida ${health.spendingLimitScore}/30, reserva ${health.emergencyReserveScore}/25, envelopes ${health.envelopeScore}/15)
- Envelopes:
${envelopes}

REGRAS:
1. Use apenas nomes de categoria EXATAMENTE como listados acima.
2. Nunca sugira reduzir um teto abaixo de (pago + a vencer). Valores em múltiplos de 10, sem centavos.
3. Cite valores reais em R$ e, quando houver, os dias restantes. Não invente números.
4. Categorias FIXAS não têm "ritmo"; não as trate como gasto acelerado.
5. Se estiver tudo bem, devolva 1 insight positivo e 1 oportunidade (ex: aporte).
6. "severity": danger (estouro), attention (risco), info (oportunidade/elogio).
7. "action.type": adjust_limit (amount em R$ a somar ao teto; negativo reduz), ask_copilot (com "prompt" para o chat) ou none.

Responda APENAS com JSON:
{"insights":[{"severity":"attention","title":"até 60 caracteres","message":"1 a 2 frases","action":{"type":"adjust_limit","category":"Lazer & Entretenimento","amount":50,"label":"Subir teto em R$ 50"}}]}`

  const validCategories = report.items.map((i) => i.category)
  for (const key of keys) {
    const ai = new GoogleGenAI({ apiKey: key })
    for (const model of ['gemini-3.5-flash', 'gemini-3.5-flash-lite', 'gemini-flash-latest']) {
      try {
        const res = await ai.models.generateContent({
          model,
          contents: prompt,
          config: { responseMimeType: 'application/json', temperature: 0.3 },
        })
        const text = (res.text || '').trim()
        if (text) {
          const parsed = parseBudgetInsights(text, validCategories)
          if (parsed.length > 0) return parsed
        }
      } catch (err) {
        console.warn(`Falha no Gemini Budget Coach com modelo ${model}:`, err)
      }
    }
  }
  return null
}
