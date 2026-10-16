import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { getGeminiApiKey, setGeminiApiKey, envGeminiKey, GEMINI_MODEL } from '@/lib/gemini'
import { KeyRound, CheckCircle2, Sparkles, AlertCircle } from 'lucide-react'

interface ApiKeyModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ApiKeyModal({ open, onOpenChange }: ApiKeyModalProps) {
  const currentKey = getGeminiApiKey() || ''
  const [keyInput, setKeyInput] = useState(currentKey)
  const [savedSuccess, setSavedSuccess] = useState(false)

  const handleSave = () => {
    setGeminiApiKey(keyInput.trim() ? keyInput.trim() : null)
    setSavedSuccess(true)
    setTimeout(() => {
      setSavedSuccess(false)
      onOpenChange(false)
    }, 800)
  }

  const handleClear = () => {
    setKeyInput('')
    setGeminiApiKey(null)
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
                Modelo ativo: <code className="text-emerald-400 font-mono text-xs">{GEMINI_MODEL}</code>
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {hasEnvKey ? (
            <div className="p-3.5 rounded-xl bg-emerald-950/40 border border-emerald-500/30 flex items-start gap-3">
              <CheckCircle2 className="size-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300">
                <span className="font-semibold text-emerald-300">Chave configurada via .env</span>
                <p className="mt-1 text-slate-400">
                  A variável <code className="text-slate-300">VITE_GEMINI_API_KEY</code> está carregada no ambiente. Você pode sobrepor com uma chave individual abaixo caso deseje.
                </p>
              </div>
            </div>
          ) : isKeyActive ? (
            <div className="p-3.5 rounded-xl bg-emerald-950/40 border border-emerald-500/30 flex items-start gap-3">
              <Sparkles className="size-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300">
                <span className="font-semibold text-emerald-300">Chave personalizada ativa no navegador</span>
                <p className="mt-1 text-slate-400">
                  O Gemini está conectado e responderá a todas as perguntas e comandos em tempo real.
                </p>
              </div>
            </div>
          ) : (
            <div className="p-3.5 rounded-xl bg-amber-950/30 border border-amber-500/30 flex items-start gap-3">
              <AlertCircle className="size-5 text-amber-400 shrink-0 mt-0.5" />
              <div className="text-xs text-slate-300">
                <span className="font-semibold text-amber-300">Modo Demo Ativo (Offline Heurístico)</span>
                <p className="mt-1 text-slate-400">
                  Sem a chave, o chat lateral utiliza o Agente Heurístico inteligente embutido, simulando perfeitamente todas as ferramentas de registro e projeção.
                </p>
              </div>
            </div>
          )}

          <div className="space-y-2">
            <label className="text-xs font-medium text-slate-300">Chave da API Gemini (Google AI Studio)</label>
            <Input
              type="password"
              placeholder="AIzaSy..."
              value={keyInput}
              onChange={(e) => setKeyInput(e.target.value)}
              className="font-mono text-xs bg-slate-900/80 border-slate-700/70"
            />
            <p className="text-[11px] text-slate-400">
              Você pode obter uma chave gratuita no{' '}
              <a
                href="https://aistudio.google.com/app/apikey"
                target="_blank"
                rel="noreferrer"
                className="text-emerald-400 hover:underline"
              >
                Google AI Studio (gratuito)
              </a>
              . Sua chave fica salva apenas no armazenamento local deste navegador.
            </p>
          </div>
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          {keyInput && (
            <Button variant="ghost" size="sm" onClick={handleClear} className="text-xs text-slate-400 hover:text-rose-400 mr-auto">
              Limpar Chave
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            Fechar
          </Button>
          <Button size="sm" onClick={handleSave} className="bg-emerald-600 hover:bg-emerald-500 text-white font-medium">
            {savedSuccess ? 'Salvo!' : 'Salvar Chave'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
