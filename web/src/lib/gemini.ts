/**
 * Agente Financeiro — Google Gemini (gemini-3.5-flash-lite) com Function Calling.
 * As ferramentas manipulam diretamente o estado/Firestore através de um
 * `ToolExecutor` injetado pelo FinanceContext.
 */
import { GoogleGenAI, Type, type Content, type FunctionDeclaration, type Part } from '@google/genai'
import type { ActionCardData, ChatMessage, ChatAttachment, PendingBatchTransaction } from '@/types'
import { CATEGORIES } from '@/types'

export const GEMINI_MODEL = 'gemini-3-flash'
export const GEMINI_MODELS = [
  { id: 'gemini-3-flash', label: 'Gemini 3 Flash (Padrão — Melhor Custo-Benefício)' },
  { id: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro (O Mais Inteligente — Limites rígidos)' },
  { id: 'gemini-3.5-flash-lite', label: 'Gemini 3.5 Flash-Lite (Mais Rápido / Alta Disponibilidade)' },
  { id: 'gemini-3.1-flash-lite', label: 'Gemini 3.1 Flash-Lite (Leve)' },
  { id: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash (Alternativa Estável)' },
] as const

const LS_KEY = 'saldo-livre:gemini-api-key'
const LS_MODEL_KEY = 'saldo-livre:gemini-model'
const LS_MIGRATION_KEY = 'saldo-livre:model-migrated-v3-flash'

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
  // Migra automaticamente para o Gemini 3 Flash garantindo melhor balanço entre performance e cota (RPD)
  const migrated = localStorage.getItem(LS_MIGRATION_KEY)
  if (!migrated) {
    localStorage.setItem(LS_MIGRATION_KEY, 'true')
    localStorage.setItem(LS_MODEL_KEY, envGeminiModel || GEMINI_MODEL)
    return envGeminiModel || GEMINI_MODEL
  }

  const saved = localStorage.getItem(LS_MODEL_KEY)
  // Limpa modelos fictícios ou depreciados do local storage
  if (!saved || saved === 'gemini-2.0-flash' || saved === 'gemini-3.8-flash' || saved === 'gemini-1.5-flash') {
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
    'gemini-3-flash',
    'gemini-2.5-pro',
    'gemini-2.5-flash',
    'gemini-3.5-flash-lite',
    'gemini-3.1-flash-lite',
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
  | 'consultarTransacoes'
  | 'expandirParcelamentos'
  | 'atualizarContaPrevista'
  | 'obterRelatorioEProjecaoMensal'
  | 'gerenciarCartoes'
  | 'gerenciarContasRecorrentes'
  | 'gerenciarAmizades'
  | 'gerenciarRegrasEMemoria'
  | 'gerarGraficoFinanceiro'

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
        subcategoria: { type: Type.STRING, description: 'Subcategoria detalhada da despesa (ex: "Supermercado", "Restaurante", "Delivery & Lanches", "Combustível", "Farmácia", "Aluguel & Condomínio", "Energia Elétrica", "Presentes", etc.).' },
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
              categoria: { type: Type.STRING, enum: [...CATEGORIES], description: 'Categoria. Para entradas e salários use "Receita".' },
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
        mesOrigem: { type: Type.STRING, description: 'Mês atual das transações a serem filtradas (YYYY-MM). Ex: "2026-10".' },
        novoMesFatura: { type: Type.STRING, description: 'Novo mês de competência da fatura (YYYY-MM). Ex: "2026-11" para mover para novembro! ATENÇÃO: NUNCA informe este campo se estiver apenas alterando categoria, subcategoria, valor ou descrição de compras parceladas, pois isso moveria todas as parcelas para o mesmo mês!' },
        novaDataVencimento: { type: Type.STRING, description: 'Nova data de vencimento da fatura (YYYY-MM-DD). Ex: "2026-11-14". ATENÇÃO: NUNCA informe este campo ao editar categoria ou subcategoria de compras parceladas!' },
        novoValor: { type: Type.NUMBER, description: 'Novo valor corrigido da compra.' },
        novaDescricao: { type: Type.STRING, description: 'Nova descrição.' },
        novaCategoria: { type: Type.STRING, enum: [...CATEGORIES] },
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
        categoria: { type: Type.STRING, enum: [...CATEGORIES], description: 'Filtra por categoria ou grupo principal.' },
        subcategoria: { type: Type.STRING, description: 'Filtra por subcategoria específica (ex: "Supermercado", "Restaurante", "Delivery & Lanches", "Combustível", "Farmácia", etc.).' },
        mes: { type: Type.STRING, description: 'Mês de competência (YYYY-MM). Padrão: mês atual em análise na tela.' },
        dataInicio: { type: Type.STRING, description: 'Data de início para intervalo de período (YYYY-MM-DD).' },
        dataFim: { type: Type.STRING, description: 'Data de fim para intervalo de período (YYYY-MM-DD).' },
        somenteMinhas: {
          type: Type.BOOLEAN,
          description:
            'CRÍTICO: Defina como true quando o usuário pedir para ver despesas relacionadas 100% a ele ("meus gastos pessoais", "100% minhas", "apenas o que é meu", "só minhas"). Exclui rigorosamente qualquer gasto compartilhado com amigos, parceiro ou terceiros.',
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
        limite: { type: Type.NUMBER, description: 'Quantidade máxima de resultados a detalhar individualmente (padrão: 25).' },
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
  {
    name: 'gerenciarCartoes',
    description: 'Adiciona, edita, exclui ou lista os cartões de crédito do usuário.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        acao: { type: Type.STRING, enum: ['listar', 'adicionar', 'editar', 'excluir'] },
        id: { type: Type.STRING, description: 'ID do cartão para edição/exclusão.' },
        nome: { type: Type.STRING, description: 'Nome do novo cartão ou novo nome na edição.' },
        fechamento: { type: Type.NUMBER, description: 'Dia de fechamento do cartão.' },
        vencimento: { type: Type.NUMBER, description: 'Dia de vencimento da fatura do cartão.' },
      },
      required: ['acao'],
    },
  },
  {
    name: 'gerenciarContasRecorrentes',
    description: 'Adiciona, pausa, ativa ou lista contas recorrentes (assinaturas e despesas fixas) do usuário.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        acao: { type: Type.STRING, enum: ['listar', 'adicionar', 'pausar', 'ativar', 'excluir'] },
        id: { type: Type.STRING, description: 'ID da conta recorrente.' },
        descricao: { type: Type.STRING, description: 'Nome da assinatura/conta (ex: "Netflix").' },
        valor: { type: Type.NUMBER, description: 'Valor base da assinatura.' },
        diaVencimento: { type: Type.NUMBER, description: 'Dia de vencimento.' },
        categoria: { type: Type.STRING, enum: [...CATEGORIES] },
        mesesLimite: { type: Type.NUMBER, description: 'Se for um plano temporário, quantos meses vai durar.' },
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

  const summaryDesc = ctx.summary
    ? `DADOS FINANCEIROS REAIS DO USUÁRIO EM ${ctx.monthLabel.toUpperCase()} (${ctx.monthKey}):
- Saldo Livre Real: R$ ${ctx.summary.freeBalance.toFixed(2)}
- Limite Diário Seguro: ${ctx.summary.dailyLimit != null ? `R$ ${ctx.summary.dailyLimit.toFixed(2)}/dia` : 'Mês encerrado'} (${ctx.summary.daysRemaining} dias restantes)
- Total de Receitas: R$ ${ctx.summary.income.toFixed(2)}
- Total Comprometido: R$ ${ctx.summary.committed.toFixed(2)} (Já Pago: R$ ${ctx.summary.paid.toFixed(2)}, A Pagar/Pendente: R$ ${(ctx.summary.committed - ctx.summary.paid).toFixed(2)})
- Valores a Receber: R$ ${ctx.summary.receivableTotal.toFixed(2)}
- Gastos por Categoria neste mês:
  ${ctx.summary.byCategory.length > 0 ? ctx.summary.byCategory.map((c) => `* ${c.category}: R$ ${c.amount.toFixed(2)}`).join('\n  ') : '* Nenhuma despesa computada ainda'}
${ctx.summary.bySubcategory && ctx.summary.bySubcategory.length > 0 ? `- Gastos Detalhados por Subgrupo/Subcategoria:\n  ${ctx.summary.bySubcategory.map((s) => `* ${s.category} › ${s.subcategory}: R$ ${s.amount.toFixed(2)} (${s.count} item${s.count > 1 ? 's' : ''})`).join('\n  ')}` : ''}
- Total Investido no Mês: R$ ${ctx.summary.totalInvested != null ? ctx.summary.totalInvested.toFixed(2) : '0.00'}
- Patrimônio Total Investido Acumulado: R$ ${ctx.summary.totalInvestedAccumulated != null ? ctx.summary.totalInvestedAccumulated.toFixed(2) : '0.00'}
${ctx.summary.byInvestmentSubcategory && ctx.summary.byInvestmentSubcategory.length > 0 ? `- Alocação de Investimentos por Subcategoria:\n  ${ctx.summary.byInvestmentSubcategory.map((i) => `* ${i.subcategory}: R$ ${i.amount.toFixed(2)} (${i.percentage.toFixed(1)}%)`).join('\n  ')}` : ''}`
    : `DADOS FINANCEIROS: Mês ${ctx.monthLabel} (${ctx.monthKey})`

  const recentTxsDesc =
    ctx.recentTransactions && ctx.recentTransactions.length > 0
      ? `LANÇAMENTOS REAIS REGISTRADOS NESTE MÊS (${ctx.recentTransactions.length} itens):
${ctx.recentTransactions.map((t) => `• [ID: ${t.id || '—'}] ${t.date} | ${t.description} | R$ ${t.amount.toFixed(2)} | ${t.subcategory ? `${t.category} › ${t.subcategory}` : t.category} | ${t.status}${t.split ? ` | ${t.split}` : ''}`).join('\n')}`
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

4. TAXONOMIA RICA E SUBGRUPOS ESPECÍFICOS (CATEGORIAS E SUBCATEGORIAS):
- O Saldo Livre conta com inteligência para categorização granular em grupos e subgrupos analíticos:
  * Alimentação: Supermercado, Restaurante, Delivery & Lanches, Padaria & Café, Feira & Hortifruti, Açougue & Peixaria, Bares & Bebidas.
  * Moradia: Aluguel & Condomínio, Energia Elétrica, Água & Saneamento, Gás, Internet & TV a Cabo, Manutenção & Reparos, Móveis & Eletrodomésticos, IPTU & Taxas.
  * Transporte: Combustível, Aplicativos (Uber/99), Transporte Público (Metrô/Ônibus), Estacionamento & Pedágio, Manutenção & Revisão, Seguro & IPVA, Lavagem & Estética Automotiva.
  * Saúde: Farmácia & Remédios, Consultas & Exames, Plano de Saúde & Dental, Academia & Treinos, Terapia & Psicologia, Cuidados Pessoais & Estética.
  * Lazer: Cinema, Teatro & Shows, Viagens & Hospedagem, Passeios & Experiências, Jogos & Games, Festas, Baladas & Eventos, Hobbies & Atividades.
  * Assinaturas: Streaming de Vídeo (Netflix/Max/Disney), Streaming de Música (Spotify/Apple), Softwares, IA & Cloud (iCloud/ChatGPT), Telefonia Celular, Clubes & Assinaturas Recorrentes.
  * Educação: Cursos Online & Certificações, Faculdade & Pós-Graduação, Livros & E-books, Material Escolar & Papelaria, Idiomas.
  * Compras: Roupas & Acessórios, Calçados, Eletrônicos & Informática, Casa & Decoração, Presentes.
  * Financiamentos & Empréstimos: Financiamento Imobiliário, Financiamento Veicular, Empréstimo Pessoal, Empréstimo Consignado, Amortização de Financiamento, Crédito Estudantil (FIES/Pravaler), Renegociação de Dívidas, Juros & Tarifas de Empréstimo, Outros Empréstimos.
  * Pets: Ração & Petiscos, Veterinário & Medicamentos, Banho, Tosa & Cuidados, Acessórios & Brinquedos.
  * Receita: Salário Principal, Adiantamento / 13º Salário, Freelance & Prestação de Serviços, Rendimentos & Investimentos, Reembolsos & Devoluções, Pix Recebido, Outras Receitas.
  * Cartão Terceiros: Compras de Amigos, Compras de Familiares, Outros Terceiros.
  * Estorno: Crédito na Fatura, Devolução / Cancelamento, Cashback Recebido.
  * Outros: Tarifas Bancárias & Juros, Impostos & Taxas Governamentais, Doações & Caridade, Diversos.
- Ao registrar ou editar lançamentos (individuais ou lote), preencha SEMPRE "categoria" e "subcategoria" de acordo com o estabelecimento (ex: "iFood" -> categoria: "Alimentação", subcategoria: "Delivery & Lanches"; "Pão de Açúcar" -> categoria: "Alimentação", subcategoria: "Supermercado"; "Uber" -> categoria: "Transporte", subcategoria: "Aplicativos (Uber/99)"; "Financiamento Habitacional / Caixa" -> categoria: "Financiamentos & Empréstimos", subcategoria: "Financiamento Imobiliário"; "Financiamento Carro" -> categoria: "Financiamentos & Empréstimos", subcategoria: "Financiamento Veicular"; "Empréstimo Pessoal Nubank" -> categoria: "Financiamentos & Empréstimos", subcategoria: "Empréstimo Pessoal"; "Amortização de Parcela" -> categoria: "Financiamentos & Empréstimos", subcategoria: "Amortização de Financiamento").
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
- descricao: "Magazine Luiza (3/10)"
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
  for (const m of filtered.slice(-12)) {
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

  // Cascata de Fallback Inteligente: do melhor custo/benefício/raciocínio para a maior velocidade/disponibilidade.
  // 1º tenta o modelo solicitado. Se falhar, tenta Pro (se ainda houver cota), senão cai para os Flash/Lite.
  const fallbackPool = [
    'gemini-3-flash',
    'gemini-2.5-pro',
    'gemini-2.5-flash',
    'gemini-3.5-flash-lite',
    'gemini-3.1-flash-lite',
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
            temperature: 0.1,
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
  attachment?: ChatAttachment
  systemInstruction: string
  execute: ToolExecutor
  model?: string
  onModelChanged?: (newModel: string) => void
}): Promise<{ text: string; actions: ActionCardData[] }> {
  const ai = new GoogleGenAI({ apiKey: opts.apiKey })
  const contents: Content[] = buildChatContents(opts.history, opts.userMessage, opts.attachment)
  const actions: ActionCardData[] = []
  let activeModel = opts.model || getSelectedGeminiModel() || GEMINI_MODEL

  // Implementação Padrão Router (Roteamento Dinâmico de Modelos)
  // Encaminha análises de DRE, auditorias complexas e diagnósticos de conciliação para o Gemini 2.5 Pro (quando configurado via Router).
  const analysisRegex = /(analis(?:e|ar)|relat(?:ório|orios)|auditori|diagn[óo]stico|dre|balan[çc]o|resumo detalhado|compara[çc][ãa]o)/i
  if (analysisRegex.test(opts.userMessage)) {
    // Força uso temporário do modelo mais inteligente para essa requisição complexa
    activeModel = 'gemini-2.5-pro'
  } else if (!opts.model && activeModel === 'gemini-2.5-pro') {
     // Se não pediu modelo e for normal CRUD, forçamos o flash para economizar as cotas do PRO.
     activeModel = 'gemini-3-flash'
  }

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

/**
 * Transcreve áudio com extrema fidelidade em português do Brasil usando a API multimodal do Gemini.
 * Suporta gravações de celulares (mp4/aac no iOS Safari e webm/opus no Android Chrome).
 */
export async function transcribeAudioWithGemini(opts: {
  apiKey: string
  audioBase64: string
  mimeType: string
}): Promise<string> {
  const ai = new GoogleGenAI({ apiKey: opts.apiKey })
  const models = ['gemini-3-flash', 'gemini-2.5-flash', 'gemini-3.5-flash-lite']
  let lastError: unknown = null

  let normalizedMime = opts.mimeType || 'audio/webm'
  if (normalizedMime.includes('webm')) normalizedMime = 'audio/webm'
  else if (normalizedMime.includes('mp4') || normalizedMime.includes('m4a')) normalizedMime = 'audio/mp4'
  else if (normalizedMime.includes('aac')) normalizedMime = 'audio/aac'
  else if (normalizedMime.includes('ogg')) normalizedMime = 'audio/ogg'
  else if (normalizedMime.includes('wav')) normalizedMime = 'audio/wav'

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

  if (lastError) throw lastError
  return ''
}

