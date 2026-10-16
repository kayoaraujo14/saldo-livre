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
import { KeyRound, CheckCircle2, Sparkles, AlertCircle, Loader2, Cpu, ExternalLink } from 'lucide-react'

interface ApiKeyModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ApiKeyModal({ open, onOpenChange }: ApiKeyModalProps) {
  const currentKey = getGeminiApiKey() || ''
  const [keyInput, setKeyInput] = useState(currentKey)
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
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <KeyRound className="size-5" />
            </div>
            <div>
              <DialogTitle>Configurar API do Google Gemini</DialogTitle>
              <DialogDescription>
                Conecte sua API Key do Google AI Studio para ativar o Agente Copiloto inteligente
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {hasEnvKey ? (
            <div className="p-3 rounded-xl bg-emerald-950/40 border border-emerald-500/30 flex items-start gap-3">
              <CheckCircle2 className="size-4 text-emerald-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300">
                <span className="font-semibold text-emerald-300">Chave configurada via .env</span>
                <p className="mt-0.5 text-slate-400">
                  A variável <code className="text-slate-300 font-mono">VITE_GEMINI_API_KEY</code> está carregada no ambiente.
                </p>
              </div>
            </div>
          ) : isKeyActive ? (
            <div className="p-3 rounded-xl bg-emerald-950/40 border border-emerald-500/30 flex items-start gap-3">
              <Sparkles className="size-4 text-emerald-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300">
                <span className="font-semibold text-emerald-300">Chave personalizada ativa no navegador</span>
                <p className="mt-0.5 text-slate-400">
                  O Gemini está conectado e responderá a comandos e análises financeiras em tempo real.
                </p>
              </div>
            </div>
          ) : (
            <div className="p-3 rounded-xl bg-amber-950/30 border border-amber-500/30 flex items-start gap-3">
              <AlertCircle className="size-4 text-amber-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300">
                <span className="font-semibold text-amber-300">Modo Demo Ativo (Offline Heurístico)</span>
                <p className="mt-0.5 text-slate-400">
                  Sem a chave, o chat lateral utiliza o Agente Heurístico inteligente embutido com suporte aos comandos básicos.
                </p>
              </div>
            </div>
          )}

          {/* Model Selector */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-medium text-slate-300 flex items-center gap-1.5">
                <Cpu className="size-3.5 text-emerald-400" />
                <span>Modelo de Inteligência Artificial</span>
              </label>
              <span className="text-[10px] text-emerald-400/90 font-medium bg-emerald-950/60 border border-emerald-500/20 px-1.5 py-0.5 rounded">
                Maior RPD
              </span>
            </div>
            <Select value={selectedModel} onValueChange={setSelectedModel}>
              <SelectTrigger className="bg-slate-900 border-slate-700 text-xs">
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
              O <strong className="text-emerald-300 font-medium">Gemini 3.5 Flash-Lite</strong> oferece maior cota de requisições por dia (RPD) e menor latência na API do Google AI Studio, perfeito para o chat e lançamentos rápidos.
            </p>
          </div>

          {/* Key Input */}
          <div className="space-y-2">
            <label className="text-xs font-medium text-slate-300">Chave da API Gemini (Google AI Studio)</label>
            <div className="flex gap-2">
              <Input
                type="password"
                placeholder="AIzaSy..."
                value={keyInput}
                onChange={(e) => {
                  setKeyInput(e.target.value)
                  setTestResult(null)
                }}
                className="font-mono text-xs bg-slate-900/80 border-slate-700/70"
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={isTesting || (!keyInput.trim() && !currentKey)}
                onClick={handleTest}
                className="h-9 px-3 text-xs border-emerald-500/30 text-emerald-300 hover:bg-emerald-500/10 cursor-pointer shrink-0"
              >
                {isTesting ? <Loader2 className="size-3.5 animate-spin" /> : 'Testar'}
              </Button>
            </div>

            {testResult && (
              <div
                className={`p-2 rounded-lg text-xs flex items-center gap-2 animate-in fade-in ${
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

            <p className="text-[11px] text-slate-400 flex items-center gap-1">
              <span>Obtenha uma chave gratuita no</span>
              <a
                href="https://aistudio.google.com/app/apikey"
                target="_blank"
                rel="noreferrer"
                className="text-emerald-400 hover:underline inline-flex items-center gap-0.5"
              >
                <span>Google AI Studio</span>
                <ExternalLink className="size-2.5" />
              </a>
            </p>
          </div>
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          {keyInput && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleClear}
              className="text-xs text-slate-400 hover:text-rose-400 mr-auto"
            >
              Limpar
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
          <Button
            size="sm"
            onClick={handleSave}
            className="bg-emerald-600 hover:bg-emerald-500 text-white font-medium cursor-pointer"
          >
            {savedSuccess ? 'Salvo!' : 'Salvar Chave'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
