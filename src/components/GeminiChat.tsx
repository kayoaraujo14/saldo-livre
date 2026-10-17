import { useState, useRef, useEffect, useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/input'
import { useFinance } from '@/store/FinanceContext'
import type { ActionCardData, ChatMessage } from '@/types'
import {
  getGeminiApiKey,
  setGeminiApiKey,
  runGeminiAgent,
  buildSystemInstruction,
  getSelectedGeminiModel,
  testGeminiConnection,
  formatGeminiErrorMessage,
} from '@/lib/gemini'
import { runDemoAgent } from '@/lib/demoAgent'
import { effectiveAmount } from '@/lib/finance'
import { monthLabel, todayKey } from '@/lib/utils'
import {
  Bot,
  User,
  Send,
  Sparkles,
  KeyRound,
  Trash2,
  CheckCircle2,
  RefreshCw,
} from 'lucide-react'

interface GeminiChatProps {
  onOpenApiKey: () => void
}

const INITIAL_MESSAGE: ChatMessage = {
  id: 'msg_welcome',
  role: 'assistant',
  content: `Olá! Sou o **Copiloto Saldo Livre** 🤖

Estou conectado aos seus lançamentos em tempo real. Você pode falar comigo em linguagem natural para:
- Atualizar contas que chegaram ou foram pagas (ex: *"A conta de luz veio R$ 245 para o dia 22"*)
- Lançar compras individuais ou divididas no cartão (ex: *"Jantar de R$ 160 50/50"*)
- Analisar ativamente suas finanças e sugerir cortes de custos.
- Consultar seu limite diário, gerenciar cartões, amizades, assinaturas e pedir projeções (ex: *"Quanto ainda posso gastar no fim de semana?"*)

Sou o seu consultor financeiro. Como posso ajudar hoje?`,
}

const PROMPT_HISTORY_KEY = 'saldo_livre_user_prompt_history'

function getLearnedPrompts(): string[] {
  try {
    const raw = localStorage.getItem(PROMPT_HISTORY_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

function saveLearnedPrompt(prompt: string) {
  try {
    const trimmed = prompt.trim()
    if (trimmed.length < 5 || trimmed.startsWith('AIzaSy')) return
    const existing = getLearnedPrompts()
    const updated = [trimmed, ...existing.filter((p) => p.toLowerCase() !== trimmed.toLowerCase())].slice(0, 10)
    localStorage.setItem(PROMPT_HISTORY_KEY, JSON.stringify(updated))
  } catch {
    // ignore
  }
}

export function GeminiChat({ onOpenApiKey }: GeminiChatProps) {
  const { me, partner, month, cards, friends, executeTool, monthTransactions } = useFinance()
  const [messages, setMessages] = useState<ChatMessage[]>([INITIAL_MESSAGE])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [suggestionOffset, setSuggestionOffset] = useState(0)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const apiKey = getGeminiApiKey()
  const isLiveGemini = Boolean(apiKey)
  const [activeModel, setActiveModel] = useState<string>(getSelectedGeminiModel())

  // Sugestões inteligentes aprendendo com os dados e histórico do usuário
  const dynamicSuggestions = useMemo(() => {
    const list: string[] = []

    // 1. Prompts aprendidos do histórico do próprio usuário
    const learned = getLearnedPrompts()
    if (learned.length > 0) {
      list.push(...learned.slice(0, 2))
    }

    // 2. Contas pendentes ou estimadas do mês atual
    const pendingBills = monthTransactions.filter(
      (t) => t.type === 'expense' && t.status !== 'paid' && t.totalAmount > 0,
    )
    if (pendingBills.length > 0) {
      const bill = pendingBills[0]
      const cleanName = bill.description.replace(/\s*\(\d+\/\d+\)/, '').trim()
      list.push(`A conta de ${cleanName} veio R$ ${effectiveAmount(bill)}, atualize para mim`)
      list.push(`Marcar conta de ${cleanName} como paga`)
    }

    // 3. Divisões com amigos conectados
    if (friends.length > 0) {
      const topFriend = friends[0]
      list.push(`Almoço de R$ 90 dividido 50/50 com ${topFriend.name}`)
      list.push(`Lançar compra de R$ 120 onde ${topFriend.name} paga 100%`)
    } else if (partner) {
      list.push(`Jantar de R$ 160 dividido 50/50 com ${partner.name}`)
    }

    // 4. Cartões de crédito cadastrados
    if (cards.length > 0) {
      const card = cards[0]
      list.push(`Quanto falta para fechar a fatura do cartão ${card.cardName}?`)
    }

    // 5. Categorias mais frequentes
    const catCounts: Record<string, number> = {}
    monthTransactions.forEach((t) => {
      catCounts[t.category] = (catCounts[t.category] || 0) + 1
    })
    const topCategory = Object.keys(catCounts).sort((a, b) => catCounts[b] - catCounts[a])[0]
    if (topCategory && topCategory !== 'Receita') {
      list.push(`Quanto gastei com ${topCategory} em ${monthLabel(month)}?`)
      list.push(`Adicionar despesa de R$ 45 em ${topCategory}`)
    }

    // 6. Consultas de limite e planejamento
    list.push('Quanto ainda posso gastar hoje sem furar o Saldo Livre?')
    list.push('Quais contas ainda estão pendentes de pagamento este mês?')
    list.push('Quais compras parceladas estão ativas na minha fatura?')

    // Remove duplicatas
    return Array.from(new Set(list))
  }, [monthTransactions, friends, partner, cards, month])

  // Pega fatia rotacionada de 4-5 chips
  const displayedChips = useMemo(() => {
    if (dynamicSuggestions.length <= 5) return dynamicSuggestions
    const count = 5
    const start = (suggestionOffset * count) % dynamicSuggestions.length
    const slice = dynamicSuggestions.slice(start, start + count)
    if (slice.length < count) {
      return [...slice, ...dynamicSuggestions.slice(0, count - slice.length)]
    }
    return slice
  }, [dynamicSuggestions, suggestionOffset])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, loading])

  useEffect(() => {
    const current = getSelectedGeminiModel()
    if (current && current !== activeModel) {
      setActiveModel(current)
    }
  })

  const handleSend = async (textToSend?: string) => {
    const text = (textToSend ?? input).trim()
    if (!text || loading) return

    // Detecção mágica: se o usuário colar uma chave do Google AI Studio no chat
    if (text.startsWith('AIzaSy') && text.length >= 35 && text.length <= 45 && !text.includes(' ')) {
      setInput('')
      setMessages((prev) => [
        ...prev,
        { id: `usr_${Date.now()}`, role: 'user', content: '🔑 *[Chave de API do Gemini inserida]*' },
      ])
      setLoading(true)
      try {
        const test = await testGeminiConnection(text)
        if (test.ok) {
          setGeminiApiKey(text)
          if (test.modelUsed) setActiveModel(test.modelUsed)
          setMessages((prev) => [
            ...prev,
            {
              id: `bot_${Date.now()}`,
              role: 'assistant',
              content: `🎉 **Chave da API do Google Gemini validada e conectada com sucesso!**\n\nAgora estou conectado diretamente à nuvem do Google usando o modelo **${test.modelUsed || activeModel}** com Function Calling em tempo real.\n\nVocê já pode me pedir para lançar despesas, registrar compras divididas ou atualizar contas!`,
            },
          ])
        } else {
          setMessages((prev) => [
            ...prev,
            {
              id: `bot_${Date.now()}`,
              role: 'assistant',
              content: `⚠️ Não consegui validar a chave inserida no Google AI Studio:\n*${test.message}*.\n\nVerifique se a chave foi copiada corretamente ou utilize o botão 🔑 acima para configurar.`,
              error: true,
            },
          ])
        }
      } finally {
        setLoading(false)
      }
      return
    }

    saveLearnedPrompt(text)

    const userMsg: ChatMessage = {
      id: `usr_${Date.now()}`,
      role: 'user',
      content: text,
    }

    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setLoading(true)

    try {
      let result: { text: string; actions: ActionCardData[] }

      if (apiKey) {
        // Run with live Gemini Flash via Function Calling
        const sys = buildSystemInstruction({
          userName: me?.name ?? 'Usuário',
          partnerName: partner?.name ?? null,
          today: todayKey(),
          monthLabel: monthLabel(month),
          monthKey: month,
          cards: cards.map((c) => ({
            id: c.id,
            name: c.cardName,
            owner: c.ownerName,
            closingDay: c.closingDay,
            dueDay: c.dueDay,
          })),
          friends: (friends || []).map((f) => ({
            uid: f.uid,
            name: f.name,
            email: f.email,
          })),
        })

        result = await runGeminiAgent({
          apiKey,
          history: messages,
          userMessage: text,
          systemInstruction: sys,
          execute: executeTool,
          model: activeModel,
          onModelChanged: (newModel) => setActiveModel(newModel),
        })
      } else {
        // Offline demo agent fallback with natural language heuristics
        result = await runDemoAgent(text, executeTool, month)
      }

      const botMsg: ChatMessage = {
        id: `bot_${Date.now()}`,
        role: 'assistant',
        content: result.text,
        actions: result.actions,
      }

      setMessages((prev) => [...prev, botMsg])
    } catch (err) {
      console.error(err)
      const friendlyMessage = formatGeminiErrorMessage(err)
      const errorMsg: ChatMessage = {
        id: `err_${Date.now()}`,
        role: 'assistant',
        content: `⚠️ ${friendlyMessage}\n\nSe a instabilidade nos servidores do Google persistir, você pode aguardar alguns instantes ou alternar para o Gemini 3.8 Flash no botão 🔑 acima.`,
        error: true,
      }
      setMessages((prev) => [...prev, errorMsg])
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="glass-card rounded-2xl border border-white/[0.08] flex flex-col h-full overflow-hidden shadow-xl">
      {/* Chat Top Header */}
      <div className="p-3.5 border-b border-white/[0.06] flex items-center justify-between bg-slate-900/60">
        <div className="flex items-center gap-2">
          <div className="size-7 rounded-lg bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 flex items-center justify-center">
            <div className="size-full bg-slate-950 rounded-[6px] flex items-center justify-center">
              <Bot className="size-4 text-emerald-400" />
            </div>
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <h3 className="text-xs font-bold text-white tracking-tight m-0">Copiloto Saldo Livre</h3>
              {isLiveGemini ? (
                <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-emerald-950/60 text-emerald-300 border-emerald-800 gap-1 font-mono">
                  <span className="size-1.5 rounded-full bg-emerald-400 animate-pulse" />
                  {activeModel}
                </Badge>
              ) : (
                <Badge
                  variant="outline"
                  className="text-[9px] py-0 px-1.5 bg-amber-950/60 text-amber-300 border-amber-800 gap-1 font-mono"
                >
                  <span className="size-1.5 rounded-full bg-amber-400" />
                  demo offline
                </Badge>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1">
          {isLiveGemini && (
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={onOpenApiKey}
              className="size-7 text-slate-400 hover:text-emerald-400 cursor-pointer"
              title="Configurar Chave Gemini"
            >
              <KeyRound className="size-3.5" />
            </Button>
          )}
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => setMessages([INITIAL_MESSAGE])}
            className="size-7 text-slate-400 hover:text-slate-200 cursor-pointer"
            title="Limpar conversa"
          >
            <Trash2 className="size-3.5" />
          </Button>
        </div>
      </div>

      {/* Offline Alert Banner */}
      {!isLiveGemini && (
        <div className="mx-3.5 mt-2.5 p-2 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-between text-xs text-amber-300 animate-in fade-in">
          <div className="flex items-center gap-1.5">
            <Sparkles className="size-3.5 text-amber-400 shrink-0" />
            <span className="text-[11px]">Chat em modo offline. Conecte sua API Key do Gemini.</span>
          </div>
          <button
            type="button"
            onClick={onOpenApiKey}
            className="text-[11px] font-semibold text-amber-200 hover:text-white underline cursor-pointer shrink-0 ml-2"
          >
            Conectar
          </button>
        </div>
      )}

      {/* Message History */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`flex gap-2.5 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            {msg.role === 'assistant' && (
              <div className="size-7 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center shrink-0 mt-0.5">
                <Sparkles className="size-3.5" />
              </div>
            )}

            <div
              className={`max-w-[85%] rounded-2xl p-3.5 text-xs ${
                msg.role === 'user'
                  ? 'bg-emerald-600 text-white rounded-tr-xs shadow-md'
                  : msg.error
                    ? 'bg-rose-950/40 border border-rose-500/30 text-rose-200 rounded-tl-xs'
                    : 'bg-slate-900/80 border border-slate-800/80 text-slate-200 rounded-tl-xs shadow-sm'
              }`}
            >
              {/* Markdown Content */}
              <div className="chat-prose">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {msg.content}
                </ReactMarkdown>
              </div>

              {/* Action Confirmation Cards */}
              {msg.actions && msg.actions.length > 0 && (
                <div className="mt-3 space-y-2 pt-2 border-t border-white/[0.08]">
                  {msg.actions.map((act, i) => (
                    <div
                      key={i}
                      className="p-2.5 rounded-xl bg-slate-950/60 border border-emerald-500/30 space-y-1.5"
                    >
                      <div className="flex items-center gap-1.5 text-emerald-400 font-semibold text-[11px]">
                        <CheckCircle2 className="size-3.5 text-emerald-400" />
                        <span>{act.title}</span>
                      </div>
                      <div className="grid grid-cols-2 gap-1 text-[10px] text-slate-300">
                        {act.lines.map((l, j) => (
                          <div key={j} className="flex flex-col">
                            <span className="text-slate-500">{l.label}</span>
                            <span
                              className={`font-semibold ${
                                l.tone === 'positive'
                                  ? 'text-emerald-400'
                                  : l.tone === 'negative'
                                    ? 'text-rose-400'
                                    : 'text-slate-200'
                              }`}
                            >
                              {l.value}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {msg.role === 'user' && (
              <div className="size-7 rounded-lg bg-slate-800 border border-slate-700 text-slate-300 flex items-center justify-center shrink-0 mt-0.5">
                <User className="size-3.5" />
              </div>
            )}
          </div>
        ))}

        {loading && (
          <div className="flex gap-2.5 justify-start">
            <div className="size-7 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center shrink-0 mt-0.5">
              <Sparkles className="size-3.5 animate-spin" />
            </div>
            <div className="bg-slate-900/80 border border-slate-800/80 rounded-2xl rounded-tl-xs p-3 text-xs text-slate-400 flex items-center gap-2">
              <span className="size-2 rounded-full bg-emerald-400 animate-ping" />
              <span>O Gemini está calculando o impacto no seu Saldo Livre...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Suggestion Chips */}
      <div className="px-3 pt-2 pb-1 border-t border-white/[0.04] bg-slate-950/40">
        <div className="flex items-center justify-between text-[10px] text-slate-400 mb-1.5">
          <div className="flex items-center gap-1 font-medium">
            <Sparkles className="size-2.5 text-amber-400" />
            <span>Sugestões inteligentes:</span>
          </div>
          <button
            type="button"
            onClick={() => setSuggestionOffset((prev) => prev + 1)}
            className="text-[10px] text-slate-400 hover:text-emerald-400 flex items-center gap-1 cursor-pointer transition-colors"
            title="Ver outras sugestões inteligentes"
          >
            <RefreshCw className="size-2.5" />
            <span>Mais</span>
          </button>
        </div>
        <div className="flex gap-1.5 overflow-x-auto pb-1 scrollbar-none">
          {displayedChips.map((chip, idx) => (
            <button
              key={idx}
              type="button"
              disabled={loading}
              onClick={() => handleSend(chip)}
              className="text-[10px] py-1 px-2.5 rounded-lg bg-slate-900 border border-slate-800 hover:border-emerald-500/40 text-slate-300 hover:text-white whitespace-nowrap transition-all cursor-pointer disabled:opacity-50 text-left shrink-0"
            >
              {chip}
            </button>
          ))}
        </div>
      </div>

      {/* Input Box */}
      <div className="p-3 border-t border-white/[0.06] bg-slate-900/60">
        <div className="relative flex items-center gap-2">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Pergunte ao Gemini ou digite um lançamento..."
            className="min-h-[42px] max-h-24 pr-10 text-xs bg-slate-950/80 border-slate-800 resize-none focus-visible:ring-emerald-500/40"
            rows={1}
          />
          <Button
            size="icon-sm"
            disabled={!input.trim() || loading}
            onClick={() => handleSend()}
            className="absolute right-2 top-1/2 -translate-y-1/2 size-7 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg shadow-sm cursor-pointer disabled:opacity-30"
          >
            <Send className="size-3.5" />
          </Button>
        </div>
        <p className="text-[10px] text-slate-500 mt-1.5 text-center">
          Pressione <kbd className="font-mono text-slate-400">Enter</kbd> para enviar · <kbd className="font-mono text-slate-400">Shift+Enter</kbd> para nova linha
        </p>
      </div>
    </div>
  )
}
