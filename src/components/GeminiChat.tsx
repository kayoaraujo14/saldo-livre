import { useState, useRef, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/input'
import { useFinance } from '@/store/FinanceContext'
import type { ActionCardData, ChatMessage } from '@/types'
import {
  getGeminiApiKey,
  runGeminiAgent,
  buildSystemInstruction,
} from '@/lib/gemini'
import { runDemoAgent } from '@/lib/demoAgent'
import { monthLabel, todayKey } from '@/lib/utils'
import {
  Bot,
  User,
  Send,
  Sparkles,
  KeyRound,
  Trash2,
  CheckCircle2,
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
- Lançar compras de terceiros neutras (ex: *"Minha mãe usou R$ 120 na farmácia"*)
- Consultar seu limite diário e pedir projeções (ex: *"Quanto ainda posso gastar no fim de semana?"*)

Como posso ajudar hoje?`,
}

const CHIPS = [
  'A conta de luz veio R$ 245 para o dia 22',
  'Jantar de R$ 160 no cartão compartilhado dividido 50/50',
  'Minha mãe usou R$ 120 do meu cartão na farmácia',
  'Quanto ainda posso gastar no fim de semana sem furar o mês?',
  'Me dê um relatório de onde posso economizar este mês',
]

export function GeminiChat({ onOpenApiKey }: GeminiChatProps) {
  const { me, partner, month, cards, executeTool } = useFinance()
  const [messages, setMessages] = useState<ChatMessage[]>([INITIAL_MESSAGE])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const apiKey = getGeminiApiKey()
  const isLiveGemini = Boolean(apiKey)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, loading])

  const handleSend = async (textToSend?: string) => {
    const text = (textToSend ?? input).trim()
    if (!text || loading) return

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
        // Run with live Gemini 2.5 Flash via Function Calling
        const sys = buildSystemInstruction({
          userName: me?.name ?? 'Usuário',
          partnerName: partner?.name ?? null,
          today: todayKey(),
          monthLabel: monthLabel(month),
          cards: cards.map((c) => `${c.cardName} (${c.ownerName})`),
        })

        result = await runGeminiAgent({
          apiKey,
          history: messages,
          userMessage: text,
          systemInstruction: sys,
          execute: executeTool,
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
      const errorMsg: ChatMessage = {
        id: `err_${Date.now()}`,
        role: 'assistant',
        content: `Ocorreu um erro ao processar sua solicitação: ${(err as Error).message}. Se a chave estiver inválida, clique no ícone 🔑 para atualizar.`,
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
                  <span className="size-1.5 rounded-full bg-emerald-400" />
                  gemini-2.5-flash
                </Badge>
              ) : (
                <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-amber-950/60 text-amber-300 border-amber-800 gap-1 font-mono">
                  <span className="size-1.5 rounded-full bg-amber-400" />
                  demo offline
                </Badge>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={onOpenApiKey}
            className="size-7 text-slate-400 hover:text-emerald-400"
            title="Configurar Chave Gemini"
          >
            <KeyRound className="size-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => setMessages([INITIAL_MESSAGE])}
            className="size-7 text-slate-400 hover:text-slate-200"
            title="Limpar conversa"
          >
            <Trash2 className="size-3.5" />
          </Button>
        </div>
      </div>

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
        <div className="text-[10px] text-slate-400 mb-1.5 flex items-center gap-1 font-medium">
          <Sparkles className="size-2.5 text-amber-400" />
          Sugestões rápidas:
        </div>
        <div className="flex gap-1.5 overflow-x-auto pb-1 scrollbar-none">
          {CHIPS.map((chip, idx) => (
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
