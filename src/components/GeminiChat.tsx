import { useState, useRef, useEffect, useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/input'
import { useFinance } from '@/store/FinanceContext'
import type { ActionCardData, ChatMessage, ChatSession, ChatAttachment, PendingAction, PendingBatchTransaction } from '@/types'
import {
  getGeminiApiKey,
  setGeminiApiKey,
  runGeminiAgent,
  buildSystemInstruction,
  getSelectedGeminiModel,
  testGeminiConnection,
  formatGeminiErrorMessage,
  type ToolName,
} from '@/lib/gemini'
import { runDemoAgent } from '@/lib/demoAgent'
import { effectiveAmount, SPLIT_LABELS } from '@/lib/finance'
import { formatBRL, formatShortDate, monthLabel, todayKey } from '@/lib/utils'
import { extractTextFromPdf } from '@/lib/pdfParser'
import { ChatChart } from '@/components/ui/ChatChart'
import {
  fsSaveChatSession,
  fsDeleteChatSession,
  subscribeChatSessions,
} from '@/lib/firebase'
import {
  Bot,
  User,
  Send,
  Sparkles,
  KeyRound,
  Trash2,
  CheckCircle2,
  RefreshCw,
  Maximize2,
  Minimize2,
  History,
  Plus,
  MessageSquare,
  X,
  ChevronRight,
  Brain,
  Lightbulb,
  Paperclip,
  FileText,
  UploadCloud,
  Check,
  XCircle,
  Mic,
} from 'lucide-react'


interface GeminiChatProps {
  onOpenApiKey: () => void
  onToggleExpand?: () => void
  isExpanded?: boolean
  onOpenOpenFinance?: () => void
}

const INITIAL_MESSAGE: ChatMessage = {
  id: 'msg_welcome',
  role: 'assistant',
  content: `Olá! Sou o **Copiloto Saldo Livre** 🤖

Estou conectado aos seus lançamentos em tempo real. Você pode falar comigo em linguagem natural para:
- 📄 **Importar faturas e extratos em PDF**: arraste ou anexe o arquivo PDF do seu banco (Nubank, Itaú, Inter, Bradesco, etc.) e eu cadastro todos os gastos anteriores de uma só vez!
- 🏦 **Open Finance**: consulte ou sincronize seus bancos conectados automaticamente.
- ⚡ **Lançamentos rápidos**: contas que chegaram ou compras parceladas (ex: *"A conta de luz veio R$ 245 para o dia 22"* ou *"Jantar R$ 160 50/50"*).
- 📊 **Consultor ativo**: pergunte seu limite diário, projeções futuras e sugestões de corte de custos.

Como posso ajudar você hoje?`,
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

const SESSIONS_STORAGE_KEY_PREFIX = 'saldo_livre_chat_sessions_'

function getStoredSessions(uid: string): ChatSession[] {
  try {
    const key = `${SESSIONS_STORAGE_KEY_PREFIX}${uid || 'guest'}`
    const raw = localStorage.getItem(key)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function saveStoredSessions(uid: string, sessions: ChatSession[]) {
  try {
    const key = `${SESSIONS_STORAGE_KEY_PREFIX}${uid || 'guest'}`
    localStorage.setItem(key, JSON.stringify(sessions))
  } catch {
    // ignore
  }
}

export function GeminiChat({ onOpenApiKey, onToggleExpand, isExpanded, onOpenOpenFinance }: GeminiChatProps) {
  const {
    me,
    partner,
    month,
    cards,
    friends,
    executeTool,
    monthTransactions,
    summary,
    pendingBatchTransactions,
    setPendingBatchTransactions,
    rules: syncedRules,
    addRule: contextAddRule,
    deleteRule: contextDeleteRule,
  } = useFinance()
  const currentUid = me?.uid ?? 'guest'

  const [sessions, setSessions] = useState<ChatSession[]>(() => {
    const loaded = getStoredSessions(currentUid)
    if (loaded.length > 0) return loaded
    return [
      {
        id: `sess_${Date.now()}`,
        title: 'Nova Conversa',
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messages: [INITIAL_MESSAGE],
      },
    ]
  })

  const [currentSessionId, setCurrentSessionId] = useState<string>(() => {
    return sessions[0]?.id || `sess_${Date.now()}`
  })

  const [historyOpen, setHistoryOpen] = useState(false)
  const [rulesOpen, setRulesOpen] = useState(false)
  const rules = syncedRules || []
  const [newRuleInput, setNewRuleInput] = useState('')
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [suggestionOffset, setSuggestionOffset] = useState(0)
  const [pendingAttachment, setPendingAttachment] = useState<ChatAttachment | null>(null)
  const [isDraggingOver, setIsDraggingOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // Voice / Audio message recognition & recorder state
  const [isRecording, setIsRecording] = useState(false)
  const isRecordingRef = useRef(false)
  const [recordingSeconds, setRecordingSeconds] = useState(0)
  const [audioTranscript, setAudioTranscript] = useState('')
  const transcriptRef = useRef('')
  const recognitionRef = useRef<any>(null)
  const recordingTimerRef = useRef<any>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const audioChunksRef = useRef<Blob[]>([])

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      isRecordingRef.current = false
      if (recordingTimerRef.current) clearInterval(recordingTimerRef.current)
      if (recognitionRef.current) {
        try { recognitionRef.current.abort() } catch { /* ignore */ }
      }
      if (mediaStreamRef.current) mediaStreamRef.current.getTracks().forEach((t) => t.stop())
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        try { mediaRecorderRef.current.stop() } catch { /* ignore */ }
      }
    }
  }, [])

  const startRecording = async () => {
    if (isRecordingRef.current) {
      stopRecording(true)
      return
    }

    transcriptRef.current = ''
    setAudioTranscript('')
    setRecordingSeconds(0)
    audioChunksRef.current = []

    const SpeechRecognitionClass =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition

    // 1. Web Speech API (Nativo, direto, SEM travar microfone com getUserMedia simultâneo)
    if (SpeechRecognitionClass) {
      try {
        const recognition = new SpeechRecognitionClass()
        recognition.lang = 'pt-BR'
        recognition.continuous = true
        recognition.interimResults = true
        recognition.maxAlternatives = 1

        recognition.onresult = (event: any) => {
          let interim = ''
          let currentFinal = ''
          for (let i = event.resultIndex; i < event.results.length; i++) {
            const item = event.results[i]
            if (item.isFinal) {
              currentFinal += item[0].transcript + ' '
            } else {
              interim += item[0].transcript
            }
          }
          if (currentFinal) {
            transcriptRef.current = (transcriptRef.current + ' ' + currentFinal).replace(/\s+/g, ' ').trim()
          }
          const fullText = (transcriptRef.current + ' ' + interim).replace(/\s+/g, ' ').trim()
          if (fullText) {
            setAudioTranscript(fullText)
            setInput(fullText)
          }
        }

        recognition.onerror = (event: any) => {
          console.warn('Reconhecimento de voz aviso:', event.error)
          if (event.error === 'not-allowed') {
            alert('Permissão de acesso ao microfone negada. Por favor, permita o acesso ao microfone nas configurações do seu navegador para usar o comando de voz.')
            cancelRecording()
          }
        }

        recognition.onend = () => {
          if (isRecordingRef.current) {
            setTimeout(() => {
              if (isRecordingRef.current && recognitionRef.current) {
                try {
                  recognitionRef.current.start()
                } catch {
                  // ignorar se já estiver rodando
                }
              }
            }, 120)
          }
        }

        recognitionRef.current = recognition
        recognition.start()

        isRecordingRef.current = true
        setIsRecording(true)

        recordingTimerRef.current = setInterval(() => {
          setRecordingSeconds((prev) => prev + 1)
        }, 1000)
        return
      } catch (speechErr) {
        console.warn('SpeechRecognition falhou ao iniciar, usando fallback MediaRecorder:', speechErr)
      }
    }

    // 2. Fallback MediaRecorder caso o navegador não tenha Web Speech API
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        alert('Seu navegador não possui suporte para microfone ou gravação de voz.')
        return
      }

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      mediaStreamRef.current = stream

      if (typeof MediaRecorder === 'undefined') {
        alert('Seu navegador não possui suporte para gravação de áudio.')
        return
      }

      audioChunksRef.current = []
      const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
        ? 'audio/webm;codecs=opus'
        : MediaRecorder.isTypeSupported('audio/mp4')
        ? 'audio/mp4'
        : MediaRecorder.isTypeSupported('audio/webm')
        ? 'audio/webm'
        : ''

      const recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          audioChunksRef.current.push(e.data)
        }
      }
      recorder.start(250)
      mediaRecorderRef.current = recorder

      isRecordingRef.current = true
      setIsRecording(true)

      recordingTimerRef.current = setInterval(() => {
        setRecordingSeconds((prev) => prev + 1)
      }, 1000)
    } catch (err: any) {
      console.warn('Erro ao acessar microfone:', err)
      alert('Não foi possível acessar o microfone. Verifique as permissões do navegador.')
    }
  }

  const stopRecording = async (sendImmediately = true) => {
    isRecordingRef.current = false
    if (recordingTimerRef.current) {
      clearInterval(recordingTimerRef.current)
      recordingTimerRef.current = null
    }

    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop()
      } catch {
        // ignore
      }
      recognitionRef.current = null
    }

    let recordedBlob: Blob | null = null
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      try {
        await new Promise<void>((resolve) => {
          mediaRecorderRef.current!.onstop = () => resolve()
          mediaRecorderRef.current!.stop()
        })
        if (audioChunksRef.current.length > 0) {
          const mime = mediaRecorderRef.current.mimeType || 'audio/webm'
          recordedBlob = new Blob(audioChunksRef.current, { type: mime })
        }
      } catch (err) {
        console.warn('Erro ao finalizar gravação:', err)
      }
      mediaRecorderRef.current = null
    }

    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop())
      mediaStreamRef.current = null
    }

    setIsRecording(false)
    setRecordingSeconds(0)

    const capturedText = (transcriptRef.current || input || audioTranscript).trim()
    setAudioTranscript('')
    transcriptRef.current = ''

    if (sendImmediately) {
      if (capturedText) {
        handleSend(capturedText)
      } else if (recordedBlob && recordedBlob.size > 500) {
        const reader = new FileReader()
        reader.onload = () => {
          const res = reader.result as string
          const b64 = res.includes(',') ? res.split(',')[1] : res
          const ext = recordedBlob!.type.includes('mp4') ? 'mp4' : 'webm'
          setPendingAttachment({
            name: `mensagem_de_voz.${ext}`,
            size: recordedBlob!.size,
            type: recordedBlob!.type || 'audio/webm',
            base64: b64,
          })
          handleSend('Por favor, processe esta gravação de áudio com as despesas ou solicitações financeiras faladas.')
        }
        reader.readAsDataURL(recordedBlob)
      }
    }
  }

  const cancelRecording = () => {
    isRecordingRef.current = false
    if (recordingTimerRef.current) {
      clearInterval(recordingTimerRef.current)
      recordingTimerRef.current = null
    }

    if (recognitionRef.current) {
      try {
        recognitionRef.current.abort()
      } catch {
        // ignore
      }
      recognitionRef.current = null
    }

    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      try {
        mediaRecorderRef.current.stop()
      } catch {
        // ignore
      }
      mediaRecorderRef.current = null
    }

    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop())
      mediaStreamRef.current = null
    }

    setIsRecording(false)
    setRecordingSeconds(0)
    setAudioTranscript('')
    transcriptRef.current = ''
    setInput('')
    audioChunksRef.current = []
  }

  const handleFileSelect = (file: File) => {
    if (!file) return
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      alert('Por favor, selecione um arquivo no formato PDF (extrato bancário ou fatura de cartão).')
      return
    }
    if (file.size > 25 * 1024 * 1024) {
      alert('O arquivo PDF selecionado ultrapassa o limite de 25MB.')
      return
    }

    const reader = new FileReader()
    reader.onload = async () => {
      const result = reader.result as string
      const base64Clean = result.includes(',') ? result.split(',')[1] : result
      let extractedText = ''
      try {
        const parsed = await extractTextFromPdf(file)
        extractedText = parsed.text
      } catch (err) {
        console.warn('Extração direta de texto do PDF indisponível, usando fallback multimodal:', err)
      }

      setPendingAttachment({
        name: file.name,
        size: file.size,
        type: file.type || 'application/pdf',
        base64: base64Clean,
        extractedText,
      })
    }
    reader.readAsDataURL(file)
  }

  // Current session & messages
  const currentSession = sessions.find((s) => s.id === currentSessionId) || sessions[0]
  const messages = currentSession?.messages || [INITIAL_MESSAGE]

  const activePendingTxs = useMemo(() => {
    if (pendingBatchTransactions && pendingBatchTransactions.length > 0) return pendingBatchTransactions
    return currentSession?.pending_transactions || []
  }, [pendingBatchTransactions, currentSession])

  // Persist sessions to LocalStorage and Firestore
  useEffect(() => {
    saveStoredSessions(currentUid, sessions)
    if (currentUid && currentUid !== 'guest') {
      const active = sessions.find((s) => s.id === currentSessionId)
      // Only persist to Firestore if the session contains actual user activity
      const hasContent = active && (active.messages.length > 1 || active.messages[0]?.id !== INITIAL_MESSAGE.id)
      if (hasContent) {
        fsSaveChatSession(currentUid, active)
      }
    }
  }, [sessions, currentUid, currentSessionId])

  // Real-time Firestore sync for chat sessions
  useEffect(() => {
    if (!currentUid || currentUid === 'guest') return

    const unsubSessions = subscribeChatSessions(currentUid, (cloudSessions) => {
      if (cloudSessions.length > 0) {
        setSessions(cloudSessions)
        saveStoredSessions(currentUid, cloudSessions)

        // Se a sessão atual for apenas uma inicial em branco, chaveia para a mais recente da nuvem
        setCurrentSessionId((prevId) => {
          const current = sessions.find((s) => s.id === prevId)
          const isBlank = !current || current.messages.length <= 1
          if (isBlank && cloudSessions[0]?.id) {
            return cloudSessions[0].id
          }
          return prevId
        })
      }
    })

    return () => {
      unsubSessions()
    }
  }, [currentUid])

  // Reload sessions if user profile changes
  useEffect(() => {
    const loaded = getStoredSessions(currentUid)
    if (loaded.length > 0) {
      setSessions(loaded)
      setCurrentSessionId(loaded[0].id)
    } else {
      const fresh: ChatSession = {
        id: `sess_${Date.now()}`,
        title: 'Nova Conversa',
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messages: [INITIAL_MESSAGE],
      }
      setSessions([fresh])
      setCurrentSessionId(fresh.id)
    }
  }, [currentUid])

  const handleAddRule = async (text?: string) => {
    const ruleText = (text ?? newRuleInput).trim()
    if (!ruleText) return
    await contextAddRule(ruleText)
    setNewRuleInput('')
  }

  const handleDeleteRule = async (id: string) => {
    await contextDeleteRule(id)
  }


  const updateSessionMessages = (
    newMessagesOrUpdater: ChatMessage[] | ((prev: ChatMessage[]) => ChatMessage[]),
    userPromptForTitle?: string,
  ) => {
    setSessions((prevSessions) => {
      return prevSessions.map((s) => {
        if (s.id !== currentSessionId) return s
        const nextMessages =
          typeof newMessagesOrUpdater === 'function'
            ? newMessagesOrUpdater(s.messages)
            : newMessagesOrUpdater

        let title = s.title
        if (
          (title === 'Nova Conversa' || title === 'Conversa') &&
          userPromptForTitle &&
          userPromptForTitle.trim()
        ) {
          const clean = userPromptForTitle.trim().replace(/^([a-z])/i, (m) => m.toUpperCase())
          title = clean.length > 30 ? clean.slice(0, 30) + '...' : clean
        }

        return {
          ...s,
          title,
          updatedAt: Date.now(),
          messages: nextMessages,
        }
      })
    })
  }

  const updateSessionPendingTransactions = (txs: PendingBatchTransaction[]) => {
    setSessions((prevSessions) => {
      return prevSessions.map((s) => {
        if (s.id !== currentSessionId) return s
        return {
          ...s,
          updatedAt: Date.now(),
          pending_transactions: txs,
        }
      })
    })
  }

  const handleNewChat = () => {
    const newSession: ChatSession = {
      id: `sess_${Date.now()}`,
      title: 'Nova Conversa',
      createdAt: Date.now(),
      updatedAt: Date.now(),
      messages: [INITIAL_MESSAGE],
    }
    setSessions((prev) => [newSession, ...prev])
    setCurrentSessionId(newSession.id)
    setHistoryOpen(false)
  }

  const handleSelectSession = (sessionId: string) => {
    setCurrentSessionId(sessionId)
    setHistoryOpen(false)
  }

  const handleDeleteSession = (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation()
    if (currentUid && currentUid !== 'guest') {
      fsDeleteChatSession(currentUid, sessionId)
    }
    setSessions((prev) => {
      const filtered = prev.filter((s) => s.id !== sessionId)
      if (filtered.length === 0) {
        const fresh: ChatSession = {
          id: `sess_${Date.now()}`,
          title: 'Nova Conversa',
          createdAt: Date.now(),
          updatedAt: Date.now(),
          messages: [INITIAL_MESSAGE],
        }
        setCurrentSessionId(fresh.id)
        if (currentUid && currentUid !== 'guest') {
          fsSaveChatSession(currentUid, fresh)
        }
        return [fresh]
      }
      if (currentSessionId === sessionId) {
        setCurrentSessionId(filtered[0].id)
      }
      return filtered
    })
  }

  const handleClearCurrentChat = () => {
    updateSessionMessages([INITIAL_MESSAGE])
  }

  const apiKey = getGeminiApiKey()
  const isLiveGemini = Boolean(apiKey)
  const [activeModel, setActiveModel] = useState<string>(getSelectedGeminiModel())

  // Sugestões inteligentes avançadas focadas em análise financeira real e tomada de decisão
  const dynamicSuggestions = useMemo(() => {
    const list: string[] = []
    const free = summary?.freeBalance ?? 0
    const daily = summary?.dailyLimit
    const invested = summary?.totalInvested ?? 0
    const income = summary?.income ?? 0
    const receivables = summary?.receivableTotal ?? 0
    const label = monthLabel(month)

    // 1. Gráficos & Visualizações Financeiras
    list.push(`📊 Gerar gráfico de distribuição dos meus gastos em ${label}`)
    if (invested > 0 || (summary?.byInvestmentSubcategory && summary.byInvestmentSubcategory.length > 0)) {
      list.push(`📈 Criar gráfico de alocação da minha carteira de investimentos`)
    } else {
      list.push(`📊 Criar gráfico comparativo das categorias onde mais gastei`)
    }

    // 2. Análise Estratégica & Regra 50/30/20
    list.push(`⚖️ Avaliar meus gastos de ${label} segundo a Regra 50/30/20`)

    // 3. Limite Diário Seguro e Projeção
    if (daily != null) {
      list.push(`💡 Qual é o meu limite diário seguro até o final de ${label}?`)
      list.push(`🎉 Quanto posso gastar no final de semana sem comprometer o mês?`)
    }

    // 4. Diagnóstico de Saúde do Saldo Livre
    if (free < 0) {
      list.push(`⚠️ Meu Saldo Livre está negativo em ${formatBRL(Math.abs(free))}. Onde cortar urgente?`)
    } else if (free > 0 && income > 0) {
      list.push(`✨ Tenho ${formatBRL(free)} de Saldo Livre. Quanto você recomenda aportar em investimentos?`)
    }

    // 5. Investimentos & Acúmulo Patrimonial
    if (invested > 0) {
      const rate = income > 0 ? (invested / income) * 100 : 0
      list.push(`💰 Analisar minha taxa de poupança (${rate.toFixed(1)}%) e aportes acumulados`)
    } else {
      list.push(`🌱 Como começar a investir e quanto poupar da minha receita?`)
    }

    // 6. Contas Fixas vs Variáveis
    const pendingBills = monthTransactions.filter(
      (t) => t.type === 'expense' && t.status !== 'paid' && t.totalAmount > 0,
    )
    if (pendingBills.length > 0) {
      const bill = pendingBills[0]
      const cleanName = bill.description.replace(/\s*\(\d+\/\d+\)/, '').trim()
      list.push(`⚡ Quanto falta pagar das minhas contas de ${label}?`)
      list.push(`A conta de ${cleanName} veio R$ ${effectiveAmount(bill)}, atualize para mim`)
    } else {
      list.push(`🔍 Onde estão os maiores ralos de dinheiro no meu orçamento deste mês?`)
    }

    // 7. Recebíveis e Amigos
    if (receivables > 0) {
      list.push(`📬 Detalhar todos os valores que tenho a receber (${formatBRL(receivables)})`)
    }

    // 8. Fatura de Cartões
    if (cards.length > 0) {
      const card = cards[0]
      list.push(`💳 Qual a previsão da fatura do cartão ${card.cardName} e compras ativas?`)
    }

    // 9. Projeção de Fluxo de Caixa Futuro
    list.push(`🔮 Projetar meu fluxo de caixa e saldo para os próximos 3 meses`)

    // Remove eventuais duplicatas
    return Array.from(new Set(list))
  }, [summary, monthTransactions, cards, month])

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

  const handleConfirmPendingAction = async (messageId: string, pendingAction: PendingAction) => {
    updateSessionMessages((prev) =>
      prev.map((msg) => {
        if (msg.id !== messageId || !msg.actions) return msg
        return {
          ...msg,
          actions: msg.actions.map((act) => {
            if (act.pendingAction?.id === pendingAction.id) {
              return {
                ...act,
                pendingAction: { ...act.pendingAction, status: 'confirmed' as const },
              }
            }
            return act
          }),
        }
      }),
    )

    setLoading(true)
    try {
      const result = await executeTool(
        pendingAction.toolName as ToolName,
        { ...pendingAction.args, __confirmed: true },
      )

      const botMsg: ChatMessage = {
        id: `bot_conf_${Date.now()}`,
        role: 'assistant',
        content: `✅ **Ação confirmada e executada com sucesso!**\n\nConcluí a operação **"${pendingAction.title}"** e atualizei os lançamentos e o cálculo do seu Saldo Livre.`,
        actions: result.action ? [result.action] : undefined,
      }
      setPendingBatchTransactions([])
      updateSessionPendingTransactions([])
      updateSessionMessages((prev) => [...prev, botMsg])
    } catch (err) {
      console.error(err)
      const botErrMsg: ChatMessage = {
        id: `bot_err_${Date.now()}`,
        role: 'assistant',
        content: `⚠️ Ocorreu um erro ao executar a ação: ${(err as Error).message}`,
        error: true,
      }
      updateSessionMessages((prev) => [...prev, botErrMsg])
    } finally {
      setLoading(false)
    }
  }

  const handleCancelPendingAction = (messageId: string, pendingAction: PendingAction) => {
    setPendingBatchTransactions([])
    updateSessionPendingTransactions([])
    updateSessionMessages((prev) =>
      prev.map((msg) => {
        if (msg.id !== messageId || !msg.actions) return msg
        return {
          ...msg,
          actions: msg.actions.map((act) => {
            if (act.pendingAction?.id === pendingAction.id) {
              return {
                ...act,
                pendingAction: { ...act.pendingAction, status: 'cancelled' as const },
              }
            }
            return act
          }),
        }
      }),
    )

    const cancelMsg: ChatMessage = {
      id: `bot_cancel_${Date.now()}`,
      role: 'assistant',
      content: `❌ **Ação cancelada.** Nada foi gravado ou alterado no seu Saldo Livre. Se quiser ajustar algum detalhe, basta me avisar!`,
    }
    updateSessionMessages((prev) => [...prev, cancelMsg])
  }

  const handleSend = async (textToSend?: string) => {
    let text = (textToSend ?? input).trim()
    const currentAttachment = pendingAttachment

    if ((!text && !currentAttachment) || loading) return

    // Verifica se há alguma confirmação pendente aguardando resposta
    const lastAssistantMsg = [...messages].reverse().find((m) => m.role === 'assistant')
    const activePendingAct = lastAssistantMsg?.actions?.find(
      (a) => a.kind === 'confirmation' && a.pendingAction?.status === 'pending',
    )?.pendingAction

    if (activePendingAct && lastAssistantMsg && text && !currentAttachment) {
      const isAffirmative = /^(sim|confirmo|confirmar|pode|pode lançar|pode lancar|pode salvar|ok|beleza|positivo|isso|correto|executar|pode cadastrar|pode excluir|pode sim|claro|com certeza)\b/i.test(text.trim())
      const isNegative = /^(não|nao|cancela|cancelar|esquece|esqueça|deixa pra lá|deixa pra la|abortar|errado|não quero|nao quero)\b/i.test(text.trim())

      if (isAffirmative) {
        setInput('')
        updateSessionMessages((prev) => [
          ...prev,
          { id: `usr_${Date.now()}`, role: 'user', content: text },
        ])
        await handleConfirmPendingAction(lastAssistantMsg.id, activePendingAct)
        return
      }

      if (isNegative) {
        setInput('')
        updateSessionMessages((prev) => [
          ...prev,
          { id: `usr_${Date.now()}`, role: 'user', content: text },
        ])
        handleCancelPendingAction(lastAssistantMsg.id, activePendingAct)
        return
      }
    }

    // Se estiver enviando um arquivo PDF sem texto digitado, define instrução padrão inteligente de onboarding
    if (!text && currentAttachment) {
      const isStatement = /extrato|conta|bank|statement|saldo/i.test(currentAttachment.name)
      if (isStatement) {
        text = `Por favor, analise o extrato bancário de conta corrente em PDF anexado (${currentAttachment.name}).
Atenção crítica:
1. Extraia linha a linha os lançamentos literais do extrato sem inventar ou estimar valores.
2. Identifique movimentações internas (como Aplicações/Resgates em RDB, Guardar Dinheiro) e pagamentos de fatura/saldo anterior marcando com categoria_filtro: "ignorada".
3. Identifique todas as RECEITAS / ENTRADAS válidas (salários, Pix recebidos, TEDs recebidas, depósitos) com tipo: "income", categoria: "Receita", método: "pix" e status: "paid".
4. Identifique todas as DESPESAS / SAÍDAS válidas (Pix enviados, compras no débito, boletos) com tipo: "expense", status: "paid" e método "pix", "debit" ou "bill" (sem cartão de crédito).
5. Preencha a contraparte exata de cada item (ex: Brisanet, nome do recebedor/pagador).
6. Chame a ferramenta "registrarTransacoesLote" com origemDocumento: "extrato_conta" e exiba de imediato a tabela detalhada com todas as transações para conferência antes de pedir confirmação.`
      } else {
        text = `Por favor, analise o documento em PDF anexado (${currentAttachment.name}). Se for extrato bancário de conta corrente, separe rigorosamente receitas/entradas (tipo: "income", método "pix", aumentam o Saldo Livre) de saídas em conta (método "pix" ou "debit", status "paid"). Ignore RDBs e pagamentos de fatura. Se for fatura de cartão de crédito, cadastre as compras a vencer no cartão correspondente. Extraia os dados literais e utilize a ferramenta "registrarTransacoesLote", apresentando a listagem completa de itens.`
      }
    }

    // Detecção mágica: se o usuário colar uma chave do Google AI Studio no chat
    if (text.startsWith('AIzaSy') && text.length >= 35 && text.length <= 45 && !text.includes(' ')) {
      setInput('')
      updateSessionMessages((prev) => [
        ...prev,
        { id: `usr_${Date.now()}`, role: 'user', content: '🔑 *[Chave de API do Gemini inserida]*' },
      ])
      setLoading(true)
      try {
        const test = await testGeminiConnection(text)
        if (test.ok) {
          setGeminiApiKey(text)
          if (test.modelUsed) setActiveModel(test.modelUsed)
          updateSessionMessages((prev) => [
            ...prev,
            {
              id: `bot_${Date.now()}`,
              role: 'assistant',
              content: `🎉 **Chave da API do Google Gemini validada e conectada com sucesso!**\n\nAgora estou conectado diretamente à nuvem do Google usando o modelo **${test.modelUsed || activeModel}** com Function Calling em tempo real.\n\nVocê já pode me pedir para lançar despesas, registrar compras divididas ou enviar extratos/faturas em PDF!`,
            },
          ])
        } else {
          updateSessionMessages((prev) => [
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
      attachment: currentAttachment
        ? {
            name: currentAttachment.name,
            size: currentAttachment.size,
            type: currentAttachment.type,
            extractedText: currentAttachment.extractedText,
          }
        : undefined,
    }

    updateSessionMessages((prev) => [...prev, userMsg], text)
    setInput('')
    setPendingAttachment(null)
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
          learnedRules: rules.map((r) => r.rule),
          summary: summary
            ? {
                income: summary.income,
                paid: summary.paid,
                committed: summary.committed,
                freeBalance: summary.freeBalance,
                dailyLimit: summary.dailyLimit,
                daysRemaining: summary.daysRemaining,
                receivableTotal: summary.receivableTotal,
                byCategory: summary.byCategory,
                bySubcategory: summary.bySubcategory,
                totalInvested: summary.totalInvested,
                totalInvestedAccumulated: summary.totalInvestedAccumulated,
                byInvestmentSubcategory: summary.byInvestmentSubcategory,
              }
            : null,
          recentTransactions: (monthTransactions || []).slice(0, 40).map((t) => ({
            id: t.id,
            date: formatShortDate(t.dueDate),
            description: t.description,
            amount: effectiveAmount(t),
            category: t.category,
            status: t.status,
            split: SPLIT_LABELS[t.splitType || 'mine']?.label || t.splitType || 'mine',
          })),
          pendingTransactions: activePendingTxs.length > 0 ? activePendingTxs : undefined,
        })

        result = await runGeminiAgent({
          apiKey,
          history: messages,
          userMessage: text,
          attachment: currentAttachment || undefined,
          systemInstruction: sys,
          execute: executeTool,
          model: activeModel,
          onModelChanged: (newModel) => setActiveModel(newModel),
        })
      } else {
        // Offline demo agent fallback with natural language heuristics
        result = await runDemoAgent(text, executeTool, month, currentAttachment || undefined, activePendingTxs)
      }

      // Se a ferramenta retornou transações em lote pendentes de aprovação, armazena no estado da sessão
      const batchAction = result.actions?.find((a) => a.pendingAction?.toolName === 'registrarTransacoesLote')
      const extractedPending = (batchAction?.pending_transactions ||
        batchAction?.pendingAction?.pending_transactions ||
        batchAction?.pendingAction?.args?.transacoes ||
        batchAction?.pendingAction?.args?.pending_transactions) as PendingBatchTransaction[] | undefined

      if (extractedPending && Array.isArray(extractedPending) && extractedPending.length > 0) {
        setPendingBatchTransactions(extractedPending)
        updateSessionPendingTransactions(extractedPending)
      }

      const botMsg: ChatMessage = {
        id: `bot_${Date.now()}`,
        role: 'assistant',
        content: result.text,
        actions: result.actions,
        pending_transactions: extractedPending && extractedPending.length > 0 ? extractedPending : undefined,
      }

      updateSessionMessages((prev) => [...prev, botMsg])
    } catch (err) {
      console.error(err)
      const friendlyMessage = formatGeminiErrorMessage(err)
      const errorMsg: ChatMessage = {
        id: `err_${Date.now()}`,
        role: 'assistant',
        content: `⚠️ ${friendlyMessage}\n\nSe a instabilidade nos servidores do Google persistir, você pode aguardar alguns instantes ou alternar para o Gemini 3.8 Flash no botão 🔑 acima.`,
        error: true,
      }
      updateSessionMessages((prev) => [...prev, errorMsg])
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
    <div
      onDragOver={(e) => {
        e.preventDefault()
        e.stopPropagation()
        if (!isDraggingOver) setIsDraggingOver(true)
      }}
      onDragLeave={(e) => {
        e.preventDefault()
        e.stopPropagation()
        if (e.currentTarget.contains(e.relatedTarget as Node)) return
        setIsDraggingOver(false)
      }}
      onDrop={(e) => {
        e.preventDefault()
        e.stopPropagation()
        setIsDraggingOver(false)
        const file = e.dataTransfer.files?.[0]
        if (file) handleFileSelect(file)
      }}
      className="glass-card rounded-2xl border border-white/[0.08] flex flex-col h-full overflow-hidden shadow-xl relative"
    >
      {/* Drag & Drop PDF Overlay */}
      {isDraggingOver && (
        <div className="absolute inset-0 z-50 bg-[#090d16]/95 border-2 border-dashed border-emerald-400 backdrop-blur-md rounded-2xl flex flex-col items-center justify-center gap-3 p-6 animate-in fade-in">
          <div className="size-16 rounded-2xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center animate-bounce shadow-lg">
            <UploadCloud className="size-8 stroke-[2.5]" />
          </div>
          <div className="text-center">
            <h4 className="text-sm font-bold text-white">Solte seu extrato ou fatura em PDF aqui</h4>
            <p className="text-xs text-slate-300 mt-1 max-w-xs">
              O Copiloto lerá as páginas e lançará todas as despesas e receitas automaticamente no sistema.
            </p>
          </div>
        </div>
      )}

      {/* Chat Top Header */}
      <div className="p-3 sm:p-3.5 border-b border-white/[0.06] flex items-center justify-between bg-slate-900/60 min-w-0">
        <div className="flex items-center gap-2 min-w-0 flex-1 mr-2">
          <div className="size-7 rounded-lg bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 flex items-center justify-center shrink-0">
            <div className="size-full bg-slate-950 rounded-[6px] flex items-center justify-center">
              <Bot className="size-4 text-emerald-400" />
            </div>
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5">
              <h3 className="text-xs font-bold text-white tracking-tight m-0 truncate">
                {currentSession.title !== 'Nova Conversa' ? currentSession.title : 'Copiloto Saldo Livre'}
              </h3>
              {isLiveGemini ? (
                <Badge variant="outline" className="text-[9px] py-0 px-1.5 bg-emerald-950/60 text-emerald-300 border-emerald-800 gap-1 font-mono shrink-0 hidden sm:inline-flex">
                  <span className="size-1.5 rounded-full bg-emerald-400 animate-pulse" />
                  {activeModel}
                </Badge>
              ) : (
                <Badge
                  variant="outline"
                  className="text-[9px] py-0 px-1.5 bg-amber-950/60 text-amber-300 border-amber-800 gap-1 font-mono shrink-0 hidden sm:inline-flex"
                >
                  <span className="size-1.5 rounded-full bg-amber-400" />
                  demo offline
                </Badge>
              )}
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1 shrink-0 ml-auto">
          {/* New Chat Button */}
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={handleNewChat}
            className="size-7 text-slate-400 hover:text-emerald-400 hover:bg-slate-800/60 cursor-pointer"
            title="Iniciar nova conversa (+)"
          >
            <Plus className="size-3.5" />
          </Button>

          {/* History Drawer Toggle */}
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => {
              setHistoryOpen((prev) => !prev)
              setRulesOpen(false)
            }}
            className={`size-7 transition-colors cursor-pointer relative ${
              historyOpen
                ? 'bg-emerald-500/20 text-emerald-400'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
            }`}
            title="Histórico de conversas salvas"
          >
            <History className="size-3.5" />
            {sessions.length > 1 && (
              <span className="absolute 1 top-0.5 right-0.5 size-1.5 rounded-full bg-emerald-400" />
            )}
          </Button>

          {/* Memory & Rules Drawer Toggle */}
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={() => {
              setRulesOpen((prev) => !prev)
              setHistoryOpen(false)
            }}
            className={`size-7 transition-colors cursor-pointer relative ${
              rulesOpen
                ? 'bg-purple-500/20 text-purple-400'
                : 'text-slate-400 hover:text-purple-300 hover:bg-slate-800/60'
            }`}
            title={`Regras & Memória do Agente (${rules.length} ativas)`}
          >
            <Brain className="size-3.5" />
            {rules.length > 0 && (
              <span className="absolute -top-0.5 -right-0.5 min-w-[13px] h-3 px-0.5 rounded-full bg-purple-500 text-[8px] font-bold text-white flex items-center justify-center">
                {rules.length}
              </span>
            )}
          </Button>

          {/* API Key */}
          {isLiveGemini && (
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={onOpenApiKey}
              className="size-7 text-slate-400 hover:text-emerald-400 cursor-pointer hidden sm:flex"
              title="Configurar Chave Gemini"
            >
              <KeyRound className="size-3.5" />
            </Button>
          )}

          {/* Clear Current Chat Messages */}
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={handleClearCurrentChat}
            className="size-7 text-slate-400 hover:text-rose-400 cursor-pointer"
            title="Limpar mensagens desta conversa"
          >
            <Trash2 className="size-3.5" />
          </Button>

          {/* Expand / Minimize */}
          {onToggleExpand && (
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={onToggleExpand}
              className={`size-7 text-slate-400 hover:text-white cursor-pointer hidden lg:flex rounded-lg border shrink-0 items-center justify-center transition-all ${
                isExpanded
                  ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40 hover:bg-emerald-500/25 shadow-xs'
                  : 'border-transparent hover:border-slate-700 hover:bg-slate-800/60'
              }`}
              title={isExpanded ? 'Restaurar divisão da tela' : 'Maximizar chat (tela cheia)'}
            >
              {isExpanded ? <Minimize2 className="size-3.5" /> : <Maximize2 className="size-3.5" />}
            </Button>
          )}
        </div>
      </div>

      {/* History Slide-over / Modal Panel */}
      {historyOpen && (
        <div className="absolute inset-0 bg-[#0a0f1d]/95 backdrop-blur-2xl z-30 p-4 flex flex-col animate-in fade-in zoom-in-95 duration-150">
          {/* History Header */}
          <div className="flex items-center justify-between pb-3 border-b border-white/[0.08]">
            <div className="flex items-center gap-2">
              <div className="size-7 rounded-lg bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 flex items-center justify-center">
                <History className="size-3.5" />
              </div>
              <div>
                <h4 className="text-xs font-bold text-white tracking-tight">Histórico de Conversas</h4>
                <p className="text-[10px] text-slate-400">
                  {sessions.length} conversa{sessions.length > 1 ? 's' : ''} salva{sessions.length > 1 ? 's' : ''} neste perfil
                </p>
              </div>
            </div>

            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => setHistoryOpen(false)}
              className="size-7 text-slate-400 hover:text-white cursor-pointer"
              title="Fechar histórico"
            >
              <X className="size-4" />
            </Button>
          </div>

          {/* Sessions List */}
          <div className="flex-1 overflow-y-auto py-3 space-y-2">
            {sessions.map((sess) => {
              const isActive = sess.id === currentSessionId
              const msgCount = sess.messages.filter((m) => m.role === 'user').length
              const timeFormatted = new Date(sess.updatedAt).toLocaleDateString('pt-BR', {
                day: '2-digit',
                month: 'short',
                hour: '2-digit',
                minute: '2-digit',
              })

              return (
                <div
                  key={sess.id}
                  onClick={() => handleSelectSession(sess.id)}
                  className={`p-3 rounded-xl border transition-all cursor-pointer flex items-center justify-between gap-3 group ${
                    isActive
                      ? 'bg-emerald-950/30 border-emerald-500/40 shadow-xs'
                      : 'bg-slate-900/60 border-slate-800/80 hover:border-slate-700 hover:bg-slate-900'
                  }`}
                >
                  <div className="flex items-center gap-2.5 min-w-0 flex-1">
                    <div
                      className={`size-8 rounded-lg flex items-center justify-center shrink-0 ${
                        isActive
                          ? 'bg-emerald-500/20 text-emerald-300'
                          : 'bg-slate-800 text-slate-400 group-hover:text-slate-200'
                      }`}
                    >
                      <MessageSquare className="size-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-semibold text-white truncate block">
                          {sess.title}
                        </span>
                        {isActive && (
                          <Badge
                            variant="outline"
                            className="text-[9px] px-1 py-0 bg-emerald-500/20 text-emerald-300 border-emerald-500/30 font-medium shrink-0"
                          >
                            Ativo
                          </Badge>
                        )}
                      </div>
                      <span className="text-[10px] text-slate-500 flex items-center gap-1.5 mt-0.5">
                        <span>{timeFormatted}</span>
                        <span>·</span>
                        <span>{msgCount} pergunta{msgCount === 1 ? '' : 's'}</span>
                      </span>
                    </div>
                  </div>

                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      type="button"
                      onClick={(e) => handleDeleteSession(sess.id, e)}
                      className="size-7 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 flex items-center justify-center cursor-pointer transition-colors"
                      title="Excluir esta conversa do histórico"
                    >
                      <Trash2 className="size-3.5" />
                    </button>
                    <ChevronRight className="size-4 text-slate-600 group-hover:text-slate-400 transition-colors" />
                  </div>
                </div>
              )
            })}
          </div>

          {/* Footer Action: + Nova Conversa */}
          <div className="pt-2 border-t border-white/[0.08]">
            <Button
              onClick={handleNewChat}
              className="w-full h-9 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 shadow-md cursor-pointer"
            >
              <Plus className="size-4 stroke-[2.5]" />
              <span>+ Iniciar Nova Conversa</span>
            </Button>
          </div>
        </div>
      )}

      {/* Memory & Rules Slide-over / Modal Panel */}
      {rulesOpen && (
        <div className="absolute inset-0 bg-[#0a0f1d]/95 backdrop-blur-2xl z-30 p-4 flex flex-col animate-in fade-in zoom-in-95 duration-150">
          {/* Rules Header */}
          <div className="flex items-center justify-between pb-3 border-b border-white/[0.08]">
            <div className="flex items-center gap-2">
              <div className="size-7 rounded-lg bg-purple-500/15 border border-purple-500/30 text-purple-400 flex items-center justify-center shrink-0">
                <Brain className="size-3.5" />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-1.5 flex-wrap">
                  <h4 className="text-xs font-bold text-white tracking-tight">Memória & Regras de Comportamento</h4>
                  <Badge variant="outline" className="text-[9px] px-1.5 py-0 bg-purple-500/20 text-purple-300 border-purple-500/30 font-medium">
                    {rules.length} {rules.length === 1 ? 'regra' : 'regras'}
                  </Badge>
                </div>
                <p className="text-[10px] text-slate-400 truncate">
                  Padrões memorizados que o agente aplica automaticamente
                </p>
              </div>
            </div>

            <Button
              variant="ghost"
              size="icon-sm"
              onClick={() => setRulesOpen(false)}
              className="size-7 text-slate-400 hover:text-white cursor-pointer shrink-0"
              title="Fechar painel de memória"
            >
              <X className="size-3.5" />
            </Button>
          </div>

          {/* Quick Add Rule Form */}
          <div className="pt-3 pb-2">
            <form
              onSubmit={(e) => {
                e.preventDefault()
                handleAddRule()
              }}
              className="flex items-center gap-2"
            >
              <input
                type="text"
                value={newRuleInput}
                onChange={(e) => setNewRuleInput(e.target.value)}
                placeholder="Ex: Todo gasto no Zé Delivery é Lazer e 100% meu..."
                className="flex-1 h-8 px-2.5 bg-slate-900/80 border border-white/[0.1] rounded-lg text-xs text-white placeholder:text-slate-500 focus:outline-none focus:border-purple-500/60"
              />
              <Button
                type="submit"
                disabled={!newRuleInput.trim()}
                className="h-8 px-3 bg-purple-600 hover:bg-purple-500 text-white font-medium text-xs rounded-lg cursor-pointer shrink-0 disabled:opacity-40"
              >
                <Plus className="size-3 mr-1" />
                Aprender
              </Button>
            </form>
          </div>

          {/* Rules List */}
          <div className="flex-1 overflow-y-auto space-y-2 py-2 pr-1 custom-scrollbar">
            {rules.length === 0 ? (
              <div className="h-full flex flex-col items-center justify-center text-center p-4">
                <div className="size-10 rounded-xl bg-purple-500/10 text-purple-400 flex items-center justify-center mb-2">
                  <Brain className="size-5" />
                </div>
                <p className="text-xs font-semibold text-white">Nenhuma regra aprendida ainda</p>
                <p className="text-[11px] text-slate-400 max-w-xs mt-1">
                  Você pode ensinar padrões dizendo no chat: <br />
                  <span className="text-purple-300 font-mono">"Aprenda que todo almoço de sexta é 50/50"</span> ou cadastrando no campo acima.
                </p>
              </div>
            ) : (
              rules.map((rule, idx) => (
                <div
                  key={rule.id}
                  className="p-2.5 rounded-xl border border-white/[0.06] bg-slate-900/60 hover:border-purple-500/30 transition-all flex items-start justify-between gap-2.5 group"
                >
                  <div className="flex items-start gap-2 min-w-0">
                    <span className="size-5 rounded-md bg-purple-500/20 text-purple-300 font-mono text-[10px] flex items-center justify-center shrink-0 mt-0.5">
                      {idx + 1}
                    </span>
                    <div className="min-w-0">
                      <p className="text-xs text-slate-200 font-medium leading-relaxed break-words">
                        {rule.rule}
                      </p>
                      <span className="text-[9px] text-slate-500 block mt-1">
                        Memorizado em {new Date(rule.createdAt).toLocaleDateString('pt-BR')}
                      </span>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => handleDeleteRule(rule.id)}
                    className="size-6 rounded-md text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 flex items-center justify-center cursor-pointer transition-colors shrink-0"
                    title="Esquecer / excluir esta regra"
                  >
                    <Trash2 className="size-3" />
                  </button>
                </div>
              ))
            )}
          </div>

          {/* Example Rules Bank */}
          <div className="pt-2 border-t border-white/[0.08]">
            <p className="text-[10px] font-semibold text-slate-400 mb-1.5 flex items-center gap-1">
              <Lightbulb className="size-3 text-amber-400 shrink-0" />
              Sugestões de regras bancárias para ensinar:
            </p>
            <div className="flex flex-wrap gap-1.5 max-h-24 overflow-y-auto">
              {[
                'Sempre que eu falar iFood é Alimentação e 50% com a Mari',
                'Posto de combustível é sempre pago no Débito',
                'Meu cartão Black fecha dia 2 e vence dia 10',
                'Todo gasto no Zé Delivery é Lazer e 100% meu',
                'Aluguel é despesa fixa de R$ 1.800 para o dia 10',
              ].map((ex, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => handleAddRule(ex)}
                  className="text-[10px] py-1 px-2 rounded-lg bg-slate-800/80 hover:bg-purple-950/60 hover:text-purple-300 text-slate-300 border border-white/[0.06] transition-colors cursor-pointer text-left truncate max-w-full"
                  title={`Clique para ensinar: "${ex}"`}
                >
                  + {ex}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}


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
              {/* PDF Attachment Badge in Message */}
              {msg.attachment && (
                <div
                  className={`mb-2.5 p-2 rounded-xl flex items-center gap-2 border ${
                    msg.role === 'user'
                      ? 'bg-emerald-700/60 border-emerald-400/40 text-white'
                      : 'bg-slate-950/60 border-slate-700 text-slate-200'
                  }`}
                >
                  <div className="size-7 rounded-lg bg-rose-500/20 border border-rose-500/30 text-rose-300 flex items-center justify-center shrink-0">
                    <FileText className="size-3.5 text-rose-400" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <span className="text-[11px] font-semibold truncate block leading-tight">
                      {msg.attachment.name}
                    </span>
                    <span
                      className={`text-[9px] ${
                        msg.role === 'user' ? 'text-emerald-200' : 'text-slate-400'
                      }`}
                    >
                      {(msg.attachment.size / 1024).toFixed(0)} KB · Fatura / Extrato PDF
                    </span>
                  </div>
                </div>
              )}

              {/* Markdown Content with Chart rendering */}
              <div className="chat-prose">
                <ReactMarkdown
                  remarkPlugins={[remarkGfm]}
                  components={{
                    code({ inline, className, children, ...props }: any) {
                      const match = /language-(\w+)/.exec(className || '')
                      const lang = match ? match[1].toLowerCase() : ''
                      if (!inline && (lang === 'chart' || lang === 'grafico')) {
                        return <ChatChart dataString={String(children).replace(/\n$/, '')} />
                      }
                      return (
                        <code className={className} {...props}>
                          {children}
                        </code>
                      )
                    },
                  }}
                >
                  {msg.content}
                </ReactMarkdown>
              </div>

              {/* Action Confirmation Cards */}
              {msg.actions && msg.actions.length > 0 && (
                <div className="mt-3 space-y-2 pt-2 border-t border-white/[0.08]">
                  {msg.actions.map((act, i) => {
                    if (act.kind === 'confirmation' && act.pendingAction) {
                      const pAct = act.pendingAction
                      if (pAct.status === 'pending') {
                        return (
                          <div
                            key={i}
                            className="p-3.5 rounded-2xl bg-gradient-to-br from-amber-950/40 via-slate-950/90 to-amber-900/20 border border-amber-500/50 space-y-3 shadow-xl shadow-amber-500/10 animate-in fade-in zoom-in-95 duration-200"
                          >
                            <div className="flex items-center justify-between gap-2">
                              <div className="flex items-center gap-2 text-amber-300 font-bold text-xs tracking-tight">
                                <span className="relative flex h-2 w-2">
                                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75" />
                                  <span className="relative inline-flex rounded-full h-2 w-2 bg-amber-500" />
                                </span>
                                <span>Aguardando sua confirmação</span>
                              </div>
                              <Badge
                                variant="outline"
                                className="text-[9px] px-1.5 py-0 bg-amber-500/20 text-amber-300 border-amber-500/40 font-semibold"
                              >
                                Requer Aprovação
                              </Badge>
                            </div>

                            <div className="text-xs font-bold text-white tracking-tight">
                              {act.title}
                            </div>

                            <div className="grid grid-cols-2 gap-2 text-[10px] bg-slate-900/90 rounded-xl p-2.5 border border-white/[0.08]">
                              {act.lines.map((l, j) => (
                                <div key={j} className="flex flex-col min-w-0">
                                  <span className="text-slate-400 text-[9px]">{l.label}</span>
                                  <span
                                    className={`font-semibold truncate ${
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

                            {/* Se for registrarTransacoesLote, exibe a tabela/lista resumida de transações para conferência imediata */}
                            {pAct.toolName === 'registrarTransacoesLote' && (() => {
                              const batchTxs: PendingBatchTransaction[] = (pAct.pending_transactions ||
                                (pAct.args?.transacoes as PendingBatchTransaction[] | undefined) ||
                                (pAct.args?.pending_transactions as PendingBatchTransaction[] | undefined) ||
                                activePendingTxs) || []
                              const validOnly = batchTxs.filter((t: PendingBatchTransaction) => t.categoria_filtro !== 'ignorada')
                              if (validOnly.length === 0) return null

                              return (
                                <div className="space-y-1.5 pt-1">
                                  <div className="flex items-center justify-between text-[11px] font-semibold text-slate-300">
                                    <span>📋 Transações extraídas ({validOnly.length} itens):</span>
                                    <span className="text-[10px] text-slate-400">Verificação transparente</span>
                                  </div>
                                  <div className="max-h-52 overflow-y-auto rounded-xl border border-white/[0.08] bg-slate-950/70 p-1.5 space-y-1 text-[11px]">
                                    {validOnly.map((tx: PendingBatchTransaction, idx: number) => (
                                      <div
                                        key={idx}
                                        className="flex items-center justify-between gap-2 p-1.5 rounded-lg bg-slate-900/60 hover:bg-slate-800/60 border border-white/[0.04]"
                                      >
                                        <div className="flex items-center gap-1.5 min-w-0">
                                          <span className="text-[10px] text-slate-400 shrink-0">
                                            {formatShortDate(tx.data)}
                                          </span>
                                          <span
                                            className={`text-[9px] px-1 py-0.5 rounded font-bold shrink-0 ${
                                              tx.tipo === 'income'
                                                ? 'bg-emerald-500/20 text-emerald-300'
                                                : 'bg-rose-500/20 text-rose-300'
                                            }`}
                                          >
                                            {tx.tipo === 'income' ? 'Entrada' : 'Saída'}
                                          </span>
                                          <span className="text-slate-200 font-medium truncate" title={tx.contraparte}>
                                            {tx.contraparte}
                                          </span>
                                        </div>
                                        <span
                                          className={`font-semibold shrink-0 ${
                                            tx.tipo === 'income' ? 'text-emerald-400' : 'text-rose-400'
                                          }`}
                                        >
                                          {formatBRL(tx.valor_exato)}
                                        </span>
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              )
                            })()}

                            <p className="text-[11px] text-amber-200/90 leading-relaxed bg-amber-500/10 border border-amber-500/20 rounded-lg p-2 font-medium">
                              👉 <strong>Entendi sua solicitação!</strong> Verifique se os dados acima estão corretos. Deseja aplicar essa alteração no seu Saldo Livre?
                            </p>

                            {/* Botoes de Acao */}
                            <div className="flex items-center gap-2 pt-1">
                              <button
                                type="button"
                                disabled={loading}
                                onClick={() => handleConfirmPendingAction(msg.id, pAct)}
                                className="flex-1 h-9 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold text-xs flex items-center justify-center gap-1.5 shadow-md shadow-emerald-950/60 cursor-pointer transition-all hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50"
                              >
                                <Check className="size-4 stroke-[2.5]" />
                                <span>Confirmar e Executar</span>
                              </button>
                              <button
                                type="button"
                                disabled={loading}
                                onClick={() => handleCancelPendingAction(msg.id, pAct)}
                                className="h-9 px-3.5 rounded-xl bg-slate-800 hover:bg-rose-950/60 text-slate-300 hover:text-rose-300 hover:border-rose-500/40 border border-white/[0.08] font-medium text-xs flex items-center justify-center gap-1 cursor-pointer transition-all hover:scale-[1.01] active:scale-[0.99] disabled:opacity-50"
                              >
                                <X className="size-4" />
                                <span>Cancelar</span>
                              </button>
                            </div>
                          </div>
                        )
                      } else if (pAct.status === 'confirmed') {
                        return (
                          <div
                            key={i}
                            className="p-2.5 rounded-xl bg-emerald-950/30 border border-emerald-500/40 space-y-1.5"
                          >
                            <div className="flex items-center justify-between text-emerald-400 font-semibold text-[11px]">
                              <div className="flex items-center gap-1.5">
                                <CheckCircle2 className="size-3.5 text-emerald-400" />
                                <span>{act.title}</span>
                              </div>
                              <Badge
                                variant="outline"
                                className="text-[9px] px-1.5 py-0 bg-emerald-500/20 text-emerald-300 border-emerald-500/40"
                              >
                                ✓ Confirmado e Realizado
                              </Badge>
                            </div>
                            <div className="grid grid-cols-2 gap-1 text-[10px] text-slate-300">
                              {act.lines.map((l, j) => (
                                <div key={j} className="flex flex-col">
                                  <span className="text-slate-500">{l.label}</span>
                                  <span className="font-semibold text-slate-200">{l.value}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )
                      } else {
                        return (
                          <div
                            key={i}
                            className="p-2.5 rounded-xl bg-slate-900/60 border border-slate-800 space-y-1 opacity-80"
                          >
                            <div className="flex items-center gap-1.5 text-slate-400 font-semibold text-[11px]">
                              <XCircle className="size-3.5 text-rose-400" />
                              <span className="line-through">{act.title}</span>
                              <Badge
                                variant="outline"
                                className="text-[9px] px-1.5 py-0 bg-slate-800 text-slate-400 border-slate-700 ml-auto"
                              >
                                ✕ Cancelado
                              </Badge>
                            </div>
                            <p className="text-[10px] text-slate-500">
                              Esta solicitação foi cancelada e nada foi alterado no seu Saldo Livre.
                            </p>
                          </div>
                        )
                      }
                    }

                    return (
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
                    )
                  })}
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
              onClick={() => {
                if (chip.includes('Anexar fatura')) {
                  fileInputRef.current?.click()
                } else if (chip.includes('Open Finance') && onOpenOpenFinance) {
                  onOpenOpenFinance()
                } else {
                  handleSend(chip)
                }
              }}
              className="text-[10px] py-1 px-2.5 rounded-lg bg-slate-900 border border-slate-800 hover:border-emerald-500/40 text-slate-300 hover:text-white whitespace-nowrap transition-all cursor-pointer disabled:opacity-50 text-left shrink-0"
            >
              {chip}
            </button>
          ))}
        </div>
      </div>

      {/* Input Box */}
      <div className="p-2.5 sm:p-3 border-t border-white/[0.06] bg-slate-900/60">
        {/* Pending PDF Attachment Preview */}
        {pendingAttachment && (
          <div className="mb-2 p-2 rounded-xl bg-slate-950/90 border border-emerald-500/40 flex items-center justify-between gap-2 shadow-lg animate-in fade-in slide-in-from-bottom-2">
            <div className="flex items-center gap-2 min-w-0 flex-1">
              <div className="size-8 rounded-lg bg-rose-500/20 border border-rose-500/30 text-rose-400 flex items-center justify-center shrink-0">
                <FileText className="size-4" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <span className="text-xs font-semibold text-white truncate block">
                    {pendingAttachment.name}
                  </span>
                  <Badge variant="outline" className="text-[9px] py-0 px-1 bg-emerald-500/20 text-emerald-300 border-emerald-500/30 shrink-0">
                    PDF Pronto
                  </Badge>
                </div>
                <span className="text-[10px] text-slate-400 block truncate">
                  {(pendingAttachment.size / 1024).toFixed(1)} KB · O Copiloto lerá e importará as despesas deste documento
                </span>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setPendingAttachment(null)}
              className="size-6 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 flex items-center justify-center transition-colors cursor-pointer shrink-0"
              title="Remover anexo"
            >
              <X className="size-3.5" />
            </button>
          </div>
        )}

        {/* Live Recording Indicator Bar (aparece acima do input quando gravando, sem cobrir o botão de enviar) */}
        {isRecording && (
          <div className="mb-2 px-3 py-1.5 rounded-xl bg-gradient-to-r from-rose-950/80 via-slate-950/90 to-rose-950/60 border border-rose-500/50 flex items-center justify-between gap-2 shadow-lg animate-in fade-in slide-in-from-bottom-1">
            <div className="flex items-center gap-2 shrink-0">
              <span className="relative flex h-2.5 w-2.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-rose-500" />
              </span>
              <span className="text-xs font-bold text-rose-300 font-mono">
                {Math.floor(recordingSeconds / 60).toString().padStart(2, '0')}:{(recordingSeconds % 60).toString().padStart(2, '0')}
              </span>
            </div>

            <div className="flex-1 truncate text-xs text-rose-200/90 font-medium italic">
              {audioTranscript ? `"${audioTranscript}"` : '🎙️ Ouvindo... Pode falar agora'}
            </div>

            <button
              type="button"
              onClick={cancelRecording}
              className="text-xs text-slate-400 hover:text-white px-2 py-0.5 rounded-md hover:bg-slate-800 transition-colors flex items-center gap-1 cursor-pointer shrink-0"
              title="Cancelar áudio"
            >
              <X className="size-3.5 text-rose-400" />
              <span className="text-[11px]">Cancelar</span>
            </button>
          </div>
        )}

        <div className="relative flex items-center gap-1.5 sm:gap-2 w-full max-w-full">
          {/* Hidden File Input */}
          <input
            ref={fileInputRef}
            type="file"
            accept="application/pdf,.pdf"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) handleFileSelect(file)
              e.target.value = ''
            }}
          />

          {/* Attachment Paperclip Button */}
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            disabled={isRecording || loading}
            onClick={() => fileInputRef.current?.click()}
            className="size-8 text-slate-400 hover:text-emerald-400 hover:bg-slate-800/80 rounded-lg shrink-0 cursor-pointer disabled:opacity-30"
            title="Anexar fatura ou extrato bancário em PDF"
          >
            <Paperclip className="size-4" />
          </Button>

          {/* Audio Message Record Button */}
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            onClick={isRecording ? () => stopRecording(true) : startRecording}
            className={`size-8 rounded-lg shrink-0 cursor-pointer transition-all ${
              isRecording
                ? 'bg-rose-500/25 text-rose-400 border border-rose-500/60 hover:bg-rose-500/35 shadow-md shadow-rose-950/40 animate-pulse'
                : 'text-slate-400 hover:text-rose-400 hover:bg-slate-800/80'
            }`}
            title={isRecording ? 'Concluir e enviar mensagem por áudio' : 'Enviar mensagem por voz / áudio'}
          >
            {isRecording ? <Mic className="size-4 text-rose-400 animate-bounce" /> : <Mic className="size-4" />}
          </Button>

          <div className="relative flex-1 min-w-0">
            <Textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={
                isRecording
                  ? 'Ouvindo sua voz... (fale agora)'
                  : pendingAttachment
                  ? 'Instruções opcionais (ou Enter para analisar o PDF)...'
                  : 'Pergunte, fale por áudio, lance gastos ou anexe PDFs...'
              }
              className={`min-h-[42px] max-h-24 pr-11 text-xs bg-slate-950/80 border-slate-800 resize-none focus-visible:ring-emerald-500/40 ${
                isRecording ? 'border-rose-500/40 ring-1 ring-rose-500/30' : ''
              }`}
              rows={1}
            />

            {/* Permanent Send Button: Anchor on right inside input, NEVER covered, ALWAYS visible */}
            <Button
              size="icon-sm"
              disabled={(!input.trim() && !pendingAttachment && !isRecording) || loading}
              onClick={() => {
                if (isRecording) {
                  stopRecording(true)
                } else {
                  handleSend()
                }
              }}
              className={`absolute right-1.5 top-1/2 -translate-y-1/2 size-7 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg shadow-sm cursor-pointer transition-all ${
                isRecording
                  ? 'bg-emerald-500 hover:bg-emerald-400 animate-pulse'
                  : 'disabled:opacity-30'
              }`}
              title="Enviar mensagem ou áudio"
            >
              <Send className="size-3.5" />
            </Button>
          </div>
        </div>
        <div className="flex items-center justify-between text-[10px] text-slate-500 mt-1.5 px-1">
          <span>🎙️ Clique no microfone para falar por áudio ou anexe PDFs</span>
          <span className="hidden sm:inline"><kbd className="font-mono text-slate-400">Enter</kbd> envia</span>
        </div>
      </div>
    </div>
  )
}
