import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  getGeminiApiKey,
  setGeminiApiKey,
  envGeminiKey,
  GEMINI_MODELS,
  getSelectedGeminiModel,
  setSelectedGeminiModel,
  testGeminiConnection,
} from '@/lib/gemini'
import { KeyRound, CheckCircle2, Sparkles, AlertCircle, Loader2, Cpu, ExternalLink, Eye, EyeOff } from 'lucide-react'

interface ApiKeyModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ApiKeyModal({ open, onOpenChange }: ApiKeyModalProps) {
  const currentKey = getGeminiApiKey() || ''
  const [keyInput, setKeyInput] = useState(currentKey)
  const [showKey, setShowKey] = useState(false)
  const [selectedModel, setSelectedModel] = useState<string>(getSelectedGeminiModel())
  const [isTesting, setIsTesting] = useState(false)
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string } | null>(null)
  const [savedSuccess, setSavedSuccess] = useState(false)

  const handleTest = async () => {
    const key = keyInput.trim() || currentKey
    if (!key) {
      setTestResult({ ok: false, message: 'Digite uma chave para testar.' })
      return
    }

    setIsTesting(true)
    setTestResult(null)
    try {
      const res = await testGeminiConnection(key, selectedModel)
      setTestResult(res)
      if (res.ok && res.modelUsed) {
        setSelectedModel(res.modelUsed)
      }
    } finally {
      setIsTesting(false)
    }
  }

  const handleSave = () => {
    const trimmed = keyInput.trim() ? keyInput.trim() : null
    setGeminiApiKey(trimmed)
    setSelectedGeminiModel(selectedModel)
    setSavedSuccess(true)
    setTimeout(() => {
      setSavedSuccess(false)
      onOpenChange(false)
    }, 800)
  }

  const handleClear = () => {
    setKeyInput('')
    setGeminiApiKey(null)
    setTestResult(null)
  }

  const hasEnvKey = Boolean(envGeminiKey)
  const isKeyActive = Boolean(currentKey)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.06]">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="size-9 rounded-xl bg-gradient-to-tr from-emerald-500/20 to-teal-500/20 border border-emerald-500/30 text-emerald-400 flex items-center justify-center shrink-0">
              <KeyRound className="size-5" />
            </div>
            <div className="min-w-0 flex-1">
              <DialogTitle className="text-base sm:text-lg font-bold text-white truncate">
                Configurar Inteligência Artificial (Gemini)
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400 truncate">
                Conecte sua API Key do Google AI Studio para ativar o Copiloto financeiro
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* Status Banner */}
          {hasEnvKey ? (
            <div className="p-4 rounded-2xl bg-emerald-950/30 border border-emerald-500/30 flex items-start gap-3 shadow-md">
              <CheckCircle2 className="size-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300 space-y-0.5">
                <span className="font-bold text-emerald-300 text-sm">Chave ativa no arquivo .env</span>
                <p className="text-slate-400">
                  A variável <code className="text-emerald-300/90 font-mono bg-emerald-950/80 px-1.5 py-0.5 rounded border border-emerald-500/20">VITE_GEMINI_API_KEY</code> está em execução.
                </p>
              </div>
            </div>
          ) : isKeyActive ? (
            <div className="p-4 rounded-2xl bg-emerald-950/30 border border-emerald-500/30 flex items-start gap-3 shadow-md">
              <Sparkles className="size-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300 space-y-0.5">
                <span className="font-bold text-emerald-300 text-sm">Chave conectada com sucesso</span>
                <p className="text-slate-400">
                  O Copiloto está operacional com respostas ultrarrápidas e reconhecimento avançado de lançamentos.
                </p>
              </div>
            </div>
          ) : (
            <div className="p-4 rounded-2xl bg-amber-950/25 border border-amber-500/30 flex items-start gap-3 shadow-md">
              <AlertCircle className="size-5 text-amber-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300 space-y-0.5">
                <span className="font-bold text-amber-300 text-sm">Modo Heurístico Ativo (Sem Chave)</span>
                <p className="text-slate-400">
                  Sem a API Key, o chat utiliza o motor de processamento heurístico local para cadastrar despesas e comandos.
                </p>
              </div>
            </div>
          )}

          {/* Model Selector */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-slate-200 flex items-center gap-1.5">
                <Cpu className="size-4 text-emerald-400" />
                <span>Modelo de IA Selecionado</span>
              </label>
              <span className="text-[10px] text-emerald-400 font-bold bg-emerald-950/60 border border-emerald-500/30 px-2 py-0.5 rounded-full">
                Alta Capacidade
              </span>
            </div>
            <Select value={selectedModel} onValueChange={setSelectedModel}>
              <SelectTrigger className="h-10 bg-slate-900 border-slate-700/80 rounded-xl text-xs sm:text-sm">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {GEMINI_MODELS.map((m) => (
                  <SelectItem key={m.id} value={m.id} className="text-xs">
                    {m.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              O <strong className="text-emerald-300 font-medium">Gemini 3.5 Flash-Lite</strong> oferece o melhor equilíbrio de cota por dia e velocidade de resposta para processar seus comandos no chat.
            </p>
          </div>

          {/* Key Input */}
          <div className="space-y-2">
            <label className="text-xs font-semibold text-slate-200">Chave da API Gemini (Google AI Studio)</label>
            <div className="flex gap-2">
              <div className="relative flex-1">
                <Input
                  type={showKey ? 'text' : 'password'}
                  placeholder="AIzaSy..."
                  value={keyInput}
                  onChange={(e) => {
                    setKeyInput(e.target.value)
                    setTestResult(null)
                  }}
                  className="h-10 font-mono text-xs sm:text-sm bg-slate-900 border-slate-700/80 rounded-xl pr-10"
                />
                <button
                  type="button"
                  onClick={() => setShowKey(!showKey)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 cursor-pointer"
                >
                  {showKey ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>

              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={isTesting || (!keyInput.trim() && !currentKey)}
                onClick={handleTest}
                className="h-10 px-4 text-xs font-semibold border-emerald-500/30 bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20 rounded-xl cursor-pointer shrink-0"
              >
                {isTesting ? <Loader2 className="size-4 animate-spin" /> : 'Testar Conexão'}
              </Button>
            </div>

            {testResult && (
              <div
                className={`p-3 rounded-xl text-xs flex items-center gap-2.5 animate-in fade-in ${
                  testResult.ok
                    ? 'bg-emerald-500/15 border border-emerald-500/30 text-emerald-300'
                    : 'bg-rose-500/15 border border-rose-500/30 text-rose-300'
                }`}
              >
                {testResult.ok ? (
                  <CheckCircle2 className="size-4 shrink-0 text-emerald-400" />
                ) : (
                  <AlertCircle className="size-4 shrink-0 text-rose-400" />
                )}
                <span className="line-clamp-2">{testResult.message}</span>
              </div>
            )}

            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1.5 text-[11px] text-slate-400 pt-1">
              <span>A chave é guardada somente no seu navegador com segurança.</span>
              <a
                href="https://aistudio.google.com/app/apikey"
                target="_blank"
                rel="noreferrer"
                className="text-emerald-400 hover:underline inline-flex items-center gap-1 font-semibold shrink-0"
              >
                <span>Obter chave grátis</span>
                <ExternalLink className="size-3" />
              </a>
            </div>
          </div>
        </div>

        <DialogFooter className="flex flex-col-reverse sm:flex-row items-center justify-between gap-2 pt-3 border-t border-white/[0.06] w-full">
          {keyInput ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleClear}
              className="text-xs text-slate-400 hover:text-rose-400 h-9 rounded-xl self-start sm:self-auto cursor-pointer"
            >
              Remover Chave
            </Button>
          ) : (
            <div />
          )}

          <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
            <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} className="rounded-xl text-xs h-9">
              Fechar
            </Button>
            <Button
              size="sm"
              onClick={handleSave}
              className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs h-9 px-4 rounded-xl cursor-pointer shadow-sm"
            >
              {savedSuccess ? 'Salvo com Sucesso!' : 'Salvar Chave'}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
