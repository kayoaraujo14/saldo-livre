import { describe, it, expect } from 'vitest'
import {
  resolveStrictCategory,
  ACTIVE_CATEGORIES,
  INCOME_CATEGORIES,
  type Card,
  type PendingBatchTransaction,
  type Transaction,
} from '@/types'
import { createToolExecutor, type AgentEnv } from '../agentTools'

describe('Validação Estrita de Categorias no Chat (Web)', () => {
  describe('resolveStrictCategory - Mecanismo Central', () => {
    it('rejeita categorias arbitrárias e inventadas, aplicando fallback inviolável para Outros (despesa)', () => {
      const res = resolveStrictCategory('Viagem Fantasma', null, 'expense')
      expect(res.category).toBe('Outros')
      expect(ACTIVE_CATEGORIES).toContain(res.category)
    })

    it('rejeita categorias arbitrárias para receita, aplicando fallback para Outras Receitas', () => {
      const res = resolveStrictCategory('Pix Mágico Não Mapeado', null, 'income')
      expect(res.category).toBe('Outras Receitas')
      expect(INCOME_CATEGORIES).toContain(res.category)
    })

    it('resolve reversamente subcategoria passada como categoria principal (Supermercado -> Alimentação)', () => {
      const res = resolveStrictCategory('Supermercado', null, 'expense')
      expect(res.category).toBe('Alimentação')
      expect(res.subcategory).toBe('Supermercado')
    })

    it('resolve reversamente subcategoria com alias (Farmácia -> Saúde › Farmácia & Remédios)', () => {
      const res = resolveStrictCategory('Farmácia', null, 'expense')
      expect(res.category).toBe('Saúde')
      expect(res.subcategory).toBe('Farmácia & Remédios')
    })

    it('resolve reversamente transporte e apps (Uber -> Transporte › Aplicativos (Uber/99))', () => {
      const res = resolveStrictCategory('Uber', null, 'expense')
      expect(res.category).toBe('Transporte')
      expect(res.subcategory).toBe('Aplicativos (Uber/99)')
    })

    it('converte categorias legadas para versões ativas oficiais modernas', () => {
      expect(resolveStrictCategory('Lazer', null, 'expense').category).toBe('Lazer & Entretenimento')
      expect(resolveStrictCategory('Assinaturas', null, 'expense').category).toBe('Assinaturas & Serviços')
      expect(resolveStrictCategory('Compras', null, 'expense').category).toBe('Compras & Estilo de Vida')
      expect(resolveStrictCategory('Educação', null, 'expense').category).toBe('Educação & Desenvolvimento')
      expect(resolveStrictCategory('Financiamentos & Empréstimos', null, 'expense').category).toBe('Financiamentos & Dívidas')
      expect(resolveStrictCategory('Receita', null, 'income').category).toBe('Outras Receitas')
      expect(resolveStrictCategory('Cartão Terceiros', null, 'expense').category).toBe('Outros')
      expect(resolveStrictCategory('Estorno', null, 'expense').category).toBe('Outros')
    })

    it('normaliza nomes sem acentos e case-insensitive', () => {
      expect(resolveStrictCategory('alimentacao', null, 'expense').category).toBe('Alimentação')
      expect(resolveStrictCategory('SAUDE', null, 'expense').category).toBe('Saúde')
      expect(resolveStrictCategory('  moradia  ', null, 'expense').category).toBe('Moradia')
    })

    it('normaliza subcategoria informada para o nome canônico do grupo', () => {
      const res = resolveStrictCategory('Alimentação', 'mercado', 'expense')
      expect(res.category).toBe('Alimentação')
      expect(res.subcategory).toBe('Supermercado')
    })

    it('anula subcategoria arbitrária ou inexistente passada junto com categoria válida', () => {
      const res = resolveStrictCategory('Alimentação', 'SubAlimentacaoInventada', 'expense')
      expect(res.category).toBe('Alimentação')
      expect(res.subcategory).toBeNull()
    })

    it('anula subcategoria quando há mismatch cruzado de grupos (ex: IPVA sob Alimentação)', () => {
      const res = resolveStrictCategory('Alimentação', 'IPVA', 'expense')
      expect(res.category).toBe('Alimentação')
      expect(res.subcategory).toBeNull()
    })

    it('anula subcategoria arbitrária em fallback para Outros', () => {
      const res = resolveStrictCategory('Xurupita', 'SubXurupita', 'expense')
      expect(res.category).toBe('Outros')
      expect(res.subcategory).toBeNull()
    })

    it('preserva subcategoria oficial válida pertencente ao grupo de fallback Outros', () => {
      const res = resolveStrictCategory('Xurupita', 'Imprevistos', 'expense')
      expect(res.category).toBe('Outros')
      expect(res.subcategory).toBe('Imprevistos')
    })

    it('utiliza classificador heurístico via descrição se a categoria não foi informada', () => {
      const res = resolveStrictCategory('', null, 'expense', 'Almoço no restaurante Outback', () => ({
        category: 'Alimentação',
        subcategory: 'Restaurante',
      }))
      expect(res.category).toBe('Alimentação')
      expect(res.subcategory).toBe('Restaurante')
    })
  })

  describe('Integração com Ferramentas do Agente (createToolExecutor)', () => {
    function setupTestExecutor() {
      const savedTransactions: Transaction[] = []
      let pendingTxs: PendingBatchTransaction[] = []

      const dummyCard: Card = {
        id: 'card_nubank',
        ownerUid: 'user_123',
        ownerName: 'Usuario Teste',
        cardName: 'Nubank',
        dueDay: 10,
        closingDay: 3,
        sharedWithUsers: [],
        sharedWithUids: [],
        frequentThirdParties: [],
      }

      const mockEnv: Partial<AgentEnv> = {
        getTransactions: () => savedTransactions,
        getCards: () => [dummyCard],
        getFriends: () => [],
        me: { uid: 'user_123', name: 'Usuario Teste' },
        partner: null,
        monthKey: '2026-11',
        requireConfirmation: false,
        getPendingTransactions: () => pendingTxs,
        setPendingTransactions: (txs) => {
          pendingTxs = txs
        },
        addTransaction: async (data) => {
          const id = 'tx_' + Math.random().toString(36).slice(2, 9)
          const newTx = { ...data, id, createdAt: Date.now() } as Transaction
          savedTransactions.push(newTx)
          return id
        },
        updateTransaction: async (id, patch) => {
          const idx = savedTransactions.findIndex((t) => t.id === id)
          if (idx >= 0) {
            savedTransactions[idx] = { ...savedTransactions[idx], ...patch }
          }
        },
        deleteTransaction: async (id) => {
          const idx = savedTransactions.findIndex((t) => t.id === id)
          if (idx >= 0) savedTransactions.splice(idx, 1)
        },
      }

      const executor = createToolExecutor(() => mockEnv as AgentEnv)
      return { executor, savedTransactions, getPendingTxs: () => pendingTxs }
    }

    it('registrarTransacao: impede categoria fantasma e salva com categoria segura oficial', async () => {
      const { executor, savedTransactions } = setupTestExecutor()

      const result = await executor('registrarTransacao', {
        descricao: 'Compra teste',
        valorTotal: 100,
        categoria: 'Categoria Inexistente Maluca',
        metodoPagamento: 'pix',
        __confirmed: true,
      })

      expect(result.response.ok).toBe(true)
      expect(savedTransactions.length).toBe(1)
      expect(savedTransactions[0].category).toBe('Outros')
      expect(ACTIVE_CATEGORIES).toContain(savedTransactions[0].category)
    })

    it('registrarTransacao: converte subcategoria enviada no campo categoria (Supermercado)', async () => {
      const { executor, savedTransactions } = setupTestExecutor()

      const result = await executor('registrarTransacao', {
        descricao: 'Compras Pão de Açúcar',
        valorTotal: 250,
        categoria: 'Supermercado',
        metodoPagamento: 'pix',
        __confirmed: true,
      })

      expect(result.response.ok).toBe(true)
      expect(savedTransactions.length).toBe(1)
      expect(savedTransactions[0].category).toBe('Alimentação')
      expect(savedTransactions[0].subcategory).toBe('Supermercado')
    })

    it('registrarTransacoesLote: sanitiza itens em lote contra categorias fantasmas', async () => {
      const { executor, savedTransactions } = setupTestExecutor()

      const result = await executor('registrarTransacoesLote', {
        origemDocumento: 'fatura_cartao',
        mesFaturaPadrao: '2026-11',
        cartaoPadrao: 'Nubank',
        transacoes: [
          {
            descricao: 'Item A',
            valorTotal: 50,
            tipo: 'expense',
            categoria: 'Categoria Fantasma',
          },
          {
            descricao: 'Item B',
            valorTotal: 80,
            tipo: 'expense',
            categoria: 'Farmácia',
          },
          {
            descricao: 'Item C',
            valorTotal: 120,
            tipo: 'expense',
            categoria: 'Lazer',
          },
        ],
        __confirmed: true,
      })

      expect(result.response.ok).toBe(true)
      expect(savedTransactions.length).toBe(3)
      expect(savedTransactions[0].category).toBe('Outros')
      expect(savedTransactions[1].category).toBe('Saúde')
      expect(savedTransactions[1].subcategory).toBe('Farmácia & Remédios')
      expect(savedTransactions[2].category).toBe('Lazer & Entretenimento')
    })

    it('editarTransacoes: valida novaCategoria estritamente antes de persistir alteração', async () => {
      const { executor, savedTransactions } = setupTestExecutor()

      await executor('registrarTransacao', {
        descricao: 'Gasto Teste',
        valorTotal: 50,
        categoria: 'Outros',
        metodoPagamento: 'pix',
        __confirmed: true,
      })
      const txId = savedTransactions[0].id

      // Edita com subcategoria informada como novaCategoria
      const resSub = await executor('editarTransacoes', {
        ids: [txId],
        novaCategoria: 'Farmácia',
        __confirmed: true,
      })
      expect(resSub.response.ok).toBe(true)
      expect(savedTransactions[0].category).toBe('Saúde')
      expect(savedTransactions[0].subcategory).toBe('Farmácia & Remédios')

      // Edita com categoria inexistente -> fallback para Outros
      const resInvalida = await executor('editarTransacoes', {
        ids: [txId],
        novaCategoria: 'Categoria Alucinada',
        __confirmed: true,
      })
      expect(resInvalida.response.ok).toBe(true)
      expect(savedTransactions[0].category).toBe('Outros')

      // Edita apenas novaSubcategoria (sem passar novaCategoria) com normalização
      savedTransactions[0].category = 'Alimentação'
      savedTransactions[0].subcategory = null
      const resApenasSub = await executor('editarTransacoes', {
        ids: [txId],
        novaSubcategoria: 'mercado',
        __confirmed: true,
      })
      expect(resApenasSub.response.ok).toBe(true)
      expect(savedTransactions[0].category).toBe('Alimentação')
      expect(savedTransactions[0].subcategory).toBe('Supermercado')

      // Edita novaSubcategoria inválida -> anula subcategoria
      const resSubInvalida = await executor('editarTransacoes', {
        ids: [txId],
        novaSubcategoria: 'SubcategoriaMaluca',
        __confirmed: true,
      })
      expect(resSubInvalida.response.ok).toBe(true)
      expect(savedTransactions[0].category).toBe('Alimentação')
      expect(savedTransactions[0].subcategory).toBeNull()

      // Edita tipo para income -> define categoria moderna Outras Receitas (não a legada Receita)
      const resTipoReceita = await executor('editarTransacoes', {
        ids: [txId],
        novoTipo: 'income',
        __confirmed: true,
      })
      expect(resTipoReceita.response.ok).toBe(true)
      expect(savedTransactions[0].type).toBe('income')
      expect(savedTransactions[0].category).toBe('Outras Receitas')
    })
  })
})
