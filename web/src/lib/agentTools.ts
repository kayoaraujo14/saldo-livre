/**
 * Fachada principal e orquestrador das ferramentas do Copiloto IA Gemini.
 * Módulos especializados residem em `web/src/lib/agent/`.
 */
import type { AgentEnv, ToolExecutor, ToolName, ToolResult, UserRule } from './agent/types'
import {
  editar,
  excluir,
  expandirParcelamentos,
  registrar,
  registrarLote,
} from './agent/transactionTools'
import { atualizar } from './agent/billTools'
import { consultar } from './agent/queryTools'
import { relatorio } from './agent/projectionTools'
import {
  gerenciarAmizades,
  gerenciarCartoes,
  gerenciarContasRecorrentes,
} from './agent/managementTools'
import { gerenciarRegras } from './agent/rules'
import { gerarGrafico } from './agent/chartTools'

// Re-exporta tipos, regras e helpers para manter paridade e compatibilidade 100%
export type { AgentEnv, UserRule }
export {
  addLearnedRule,
  getLearnedRules,
  removeLearnedRule,
  saveLearnedRules,
} from './agent/rules'
export {
  cleanBaseDescription,
  INCOME_KEYWORDS_REGEX,
  matchesSearch,
  money,
  parseMonthKey,
  SPLIT_MAP,
  SYNONYMS,
} from './agent/common'

export function createToolExecutor(getEnv: () => AgentEnv): ToolExecutor {
  return async (name: ToolName, args: Record<string, unknown>): Promise<ToolResult> => {
    const env = getEnv()
    switch (name) {
      case 'registrarTransacao':
        return registrar(env, args)
      case 'registrarTransacoesLote':
        return registrarLote(env, args)
      case 'editarTransacoes':
        return editar(env, args)
      case 'excluirTransacoes':
        return excluir(env, args)
      case 'expandirParcelamentos':
        return expandirParcelamentos(env, args)
      case 'atualizarContaPrevista':
        return atualizar(env, args)
      case 'obterRelatorioEProjecaoMensal':
        return relatorio(env, args)
      case 'gerenciarCartoes':
        return gerenciarCartoes(env, args)
      case 'gerenciarContasRecorrentes':
        return gerenciarContasRecorrentes(env, args)
      case 'gerenciarAmizades':
        return gerenciarAmizades(env, args)
      case 'gerenciarRegrasEMemoria':
        return gerenciarRegras(env, args)
      case 'consultarTransacoes':
        return consultar(env, args)
      case 'gerarGraficoFinanceiro':
        return gerarGrafico(env, args)
      default:
        return { response: { ok: false, erro: `Ferramenta desconhecida: ${name}` } }
    }
  }
}
