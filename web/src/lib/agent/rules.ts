import type { AgentEnv, ToolResult, UserRule } from './types'
import { fsDeleteAgentRule, fsSaveAgentRule } from '../firebase'

export type { UserRule }

const USER_RULES_KEY_PREFIX = 'saldo_livre_user_rules_'

export function getLearnedRules(uid: string): UserRule[] {
  try {
    const key = `${USER_RULES_KEY_PREFIX}${uid || 'guest'}`
    const raw = localStorage.getItem(key)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveLearnedRules(uid: string, rules: UserRule[]) {
  try {
    const key = `${USER_RULES_KEY_PREFIX}${uid || 'guest'}`
    localStorage.setItem(key, JSON.stringify(rules))
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('saldo_livre_rules_updated', { detail: { uid } }))
    }
  } catch {
    // ignore
  }
}

export function addLearnedRule(uid: string, ruleText: string, category = 'Geral'): UserRule {
  const existing = getLearnedRules(uid)
  const trimmed = ruleText.trim()
  const newRule: UserRule = {
    id: `rule_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
    rule: trimmed,
    category,
    createdAt: Date.now(),
  }
  const updated = [newRule, ...existing.filter((r) => r.rule.toLowerCase() !== trimmed.toLowerCase())]
  saveLearnedRules(uid, updated)

  // Persiste no banco de dados Firestore se usuário autenticado
  if (uid && uid !== 'guest') {
    fsSaveAgentRule(uid, newRule).catch((err) => {
      console.warn('Erro ao persistir nova regra no Firestore:', err)
    })
  }

  return newRule
}

export function removeLearnedRule(uid: string, idOrSearch: string): boolean {
  const existing = getLearnedRules(uid)
  const target = idOrSearch.trim().toLowerCase()
  const removedRules = existing.filter((r) => r.id === idOrSearch || r.rule.toLowerCase().includes(target))
  const filtered = existing.filter((r) => r.id !== idOrSearch && !r.rule.toLowerCase().includes(target))
  if (filtered.length !== existing.length) {
    saveLearnedRules(uid, filtered)

    // Remove do banco de dados Firestore
    if (uid && uid !== 'guest') {
      for (const r of removedRules) {
        fsDeleteAgentRule(uid, r.id).catch((err) => {
          console.warn('Erro ao excluir regra do Firestore:', err)
        })
      }
    }

    return true
  }
  return false
}

export async function gerenciarRegras(env: AgentEnv, args: Record<string, unknown>): Promise<ToolResult> {
  const acao = String(args.acao || 'listar')
  const userUid = env.me?.uid || 'guest'

  if (acao === 'listar') {
    const regras = getLearnedRules(userUid)
    return {
      response: { ok: true, regras: regras.map((r) => ({ id: r.id, regra: r.rule, categoria: r.category })) },
      action: {
        kind: 'report',
        title: 'Regras Aprendidas do Usuário',
        lines: [
          { label: 'Total de Regras', value: regras.length.toString() },
          ...(regras.slice(0, 3).map((r, i) => ({ label: `Regra ${i + 1}`, value: r.rule }))),
        ],
      },
    }
  }

  if (acao === 'aprender') {
    const texto = String(args.regra || '').trim()
    if (!texto) {
      return { response: { ok: false, erro: 'Texto da regra não informado.' } }
    }
    const novaRegra = addLearnedRule(userUid, texto)
    return {
      response: { ok: true, id: novaRegra.id, regra: novaRegra.rule },
      action: {
        kind: 'created',
        title: '🧠 Nova Regra Memorizada!',
        lines: [
          { label: 'Regra Fixada', value: novaRegra.rule },
          { label: 'Status', value: 'Ativa em futuros cálculos e lançamentos' },
        ],
      },
    }
  }

  if (acao === 'remover') {
    const termoOuId = String(args.id || args.regra || '').trim()
    if (!termoOuId) {
      return { response: { ok: false, erro: 'Informe o ID ou trecho da regra a ser removida.' } }
    }
    const ok = removeLearnedRule(userUid, termoOuId)
    return {
      response: { ok, mensagem: ok ? 'Regra esquecida com sucesso.' : 'Regra não encontrada.' },
      action: {
        kind: 'updated',
        title: ok ? 'Regra Removida da Memória' : 'Regra Não Encontrada',
        lines: [{ label: 'Referência', value: termoOuId }],
      },
    }
  }

  return { response: { ok: false, erro: 'Ação inválida para gerenciarRegrasEMemoria.' } }
}

