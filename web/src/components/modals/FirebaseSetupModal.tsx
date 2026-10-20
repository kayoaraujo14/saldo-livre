import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  isFirebaseConfigured,
  firebaseConfig,
  saveFirebaseConfig,
  clearFirebaseConfig,
  getStoredFirebaseConfig,
} from '@/lib/firebase'
import {
  ShieldCheck,
  ExternalLink,
  Flame,
  CheckCircle2,
  Trash2,
  AlertCircle,
} from 'lucide-react'

interface FirebaseSetupModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function FirebaseSetupModal({ open, onOpenChange }: FirebaseSetupModalProps) {
  const currentConfig = getStoredFirebaseConfig() || (firebaseConfig as Record<string, string> | null)

  const [apiKey, setApiKey] = useState(currentConfig?.apiKey || '')
  const [projectId, setProjectId] = useState(currentConfig?.projectId || '')
  const [authDomain, setAuthDomain] = useState(currentConfig?.authDomain || '')
  const [appId, setAppId] = useState(currentConfig?.appId || '')
  const [messagingSenderId, setMessagingSenderId] = useState(currentConfig?.messagingSenderId || '')
  const [storageBucket, setStorageBucket] = useState(currentConfig?.storageBucket || '')

  const [pasteSnippet, setPasteSnippet] = useState('')
  const [parseError, setParseError] = useState<string | null>(null)

  const handleParseSnippet = () => {
    setParseError(null)
    try {
      const text = pasteSnippet.trim()
      if (!text) return

      // Extrai chaves por regex ou JSON
      const extract = (key: string) => {
        const match = text.match(new RegExp(`${key}['"\\s]*[:=]['"\\s]*([^'"\\s,]+)`))
        return match ? match[1] : ''
      }

      const parsedApiKey = extract('apiKey')
      const parsedProjectId = extract('projectId')
      const parsedAuthDomain = extract('authDomain')
      const parsedAppId = extract('appId')
      const parsedSenderId = extract('messagingSenderId')
      const parsedStorage = extract('storageBucket')

      if (parsedApiKey) setApiKey(parsedApiKey)
      if (parsedProjectId) setProjectId(parsedProjectId)
      if (parsedAuthDomain) setAuthDomain(parsedAuthDomain)
      if (parsedAppId) setAppId(parsedAppId)
      if (parsedSenderId) setMessagingSenderId(parsedSenderId)
      if (parsedStorage) setStorageBucket(parsedStorage)

      if (!parsedApiKey && !parsedProjectId) {
        setParseError('Não foi possível identificar as chaves no texto colado. Preencha os campos abaixo.')
      } else {
        setPasteSnippet('')
      }
    } catch {
      setParseError('Erro ao processar texto. Preencha os campos diretamente.')
    }
  }

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault()
    if (!apiKey.trim() || !projectId.trim()) {
      setParseError('API Key e Project ID são obrigatórios.')
      return
    }

    const cfg: Record<string, string> = {
      apiKey: apiKey.trim(),
      projectId: projectId.trim(),
      authDomain: authDomain.trim() || `${projectId.trim()}.firebaseapp.com`,
      storageBucket: storageBucket.trim() || `${projectId.trim()}.appspot.com`,
      messagingSenderId: messagingSenderId.trim(),
      appId: appId.trim() || `web:${projectId.trim()}`,
    }

    saveFirebaseConfig(cfg)
    onOpenChange(false)
  }

  const handleDisconnect = () => {
    if (confirm('Tem certeza que deseja desconectar o Firebase e voltar para o modo inicial?')) {
      clearFirebaseConfig()
      onOpenChange(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.06]">
          <div className="flex items-center gap-2.5">
            <div className="size-9 rounded-xl bg-gradient-to-tr from-orange-500/20 to-amber-500/20 border border-orange-500/30 text-orange-400 flex items-center justify-center shrink-0">
              <Flame className="size-5" />
            </div>
            <div>
              <DialogTitle className="text-base sm:text-lg font-bold text-white flex items-center gap-2">
                <span>Configurar Backend Google Firebase</span>
                {isFirebaseConfigured ? (
                  <Badge variant="outline" className="bg-emerald-500/10 text-emerald-400 border-emerald-500/30 text-[10px] font-semibold">
                    🟢 Conectado
                  </Badge>
                ) : (
                  <Badge variant="outline" className="bg-amber-500/10 text-amber-400 border-amber-500/30 text-[10px] font-semibold">
                    Local Demo
                  </Badge>
                )}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400">
                Conecte seu Google SSO, banco Cloud Firestore e Hosting de produção
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <form onSubmit={handleSave} className="space-y-4 py-2">
          {/* Quick paste helper */}
          <div className="p-4 rounded-2xl bg-slate-950/70 border border-white/[0.08] space-y-2.5 shadow-md">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-slate-200">Colar bloco de configuração do Firebase:</span>
              <a
                href="https://console.firebase.google.com"
                target="_blank"
                rel="noreferrer"
                className="text-orange-400 hover:text-orange-300 flex items-center gap-1 text-[11px] font-semibold"
              >
                <span>Console Firebase</span>
                <ExternalLink className="size-3" />
              </a>
            </div>

            <div className="flex gap-2">
              <Input
                value={pasteSnippet}
                onChange={(e) => setPasteSnippet(e.target.value)}
                placeholder="Cole aqui o snippet: const firebaseConfig = { ... }"
                className="h-10 text-xs bg-slate-900 border-slate-700/80 font-mono rounded-xl"
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleParseSnippet}
                className="h-10 text-xs px-3.5 border-slate-700 hover:bg-slate-800 text-slate-200 rounded-xl font-semibold shrink-0"
              >
                Preencher
              </Button>
            </div>
            {parseError && (
              <p className="text-[11px] text-rose-400 flex items-center gap-1">
                <AlertCircle className="size-3" /> {parseError}
              </p>
            )}
          </div>

          {/* Form fields */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            <div className="space-y-1.5 sm:col-span-2">
              <label className="text-xs font-semibold text-slate-300">Firebase API Key *</label>
              <Input
                required
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="AIzaSy..."
                className="h-10 text-xs sm:text-sm bg-slate-900 border-slate-700/80 font-mono rounded-xl"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-300">Project ID *</label>
              <Input
                required
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                placeholder="saldo-livre-app"
                className="h-10 text-xs sm:text-sm bg-slate-900 border-slate-700/80 font-mono rounded-xl"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-300">Auth Domain</label>
              <Input
                value={authDomain}
                onChange={(e) => setAuthDomain(e.target.value)}
                placeholder={`${projectId || 'seu-projeto'}.firebaseapp.com`}
                className="h-10 text-xs sm:text-sm bg-slate-900 border-slate-700/80 font-mono rounded-xl"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-300">App ID (Web)</label>
              <Input
                value={appId}
                onChange={(e) => setAppId(e.target.value)}
                placeholder="1:123456789:web:abcdef..."
                className="h-10 text-xs sm:text-sm bg-slate-900 border-slate-700/80 font-mono rounded-xl"
              />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-300">Storage Bucket (Opcional)</label>
              <Input
                value={storageBucket}
                onChange={(e) => setStorageBucket(e.target.value)}
                placeholder={`${projectId || 'seu-projeto'}.appspot.com`}
                className="h-10 text-xs sm:text-sm bg-slate-900 border-slate-700/80 font-mono rounded-xl"
              />
            </div>
          </div>

          {/* Architecture Checklist */}
          <div className="p-4 rounded-2xl bg-slate-950/70 border border-white/[0.08] space-y-2.5 text-xs shadow-md">
            <span className="font-bold text-slate-200 flex items-center gap-1.5">
              <ShieldCheck className="size-4 text-emerald-400" />
              Recursos integrados no Firebase:
            </span>
            <ul className="space-y-1.5 text-[11px] text-slate-400">
              <li className="flex items-center gap-2">
                <CheckCircle2 className="size-3.5 text-emerald-400 shrink-0" />
                <span><strong className="text-slate-200">Google SSO:</strong> Login seguro e isolamento por conta.</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="size-3.5 text-emerald-400 shrink-0" />
                <span><strong className="text-slate-200">Cloud Firestore:</strong> Sincronização em tempo real e regras de segurança.</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="size-3.5 text-emerald-400 shrink-0" />
                <span><strong className="text-slate-200">Firebase Hosting:</strong> Build de produção rápido com CDN global.</span>
              </li>
            </ul>
          </div>

          <DialogFooter className="flex flex-col-reverse sm:flex-row items-center justify-between gap-2 pt-3 border-t border-white/[0.06] w-full">
            {isFirebaseConfigured ? (
              <Button
                type="button"
                variant="destructive"
                size="sm"
                onClick={handleDisconnect}
                className="mr-auto gap-1.5 text-xs rounded-xl h-9"
              >
                <Trash2 className="size-3.5" />
                Desconectar
              </Button>
            ) : (
              <div />
            )}

            <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => onOpenChange(false)}
                className="text-xs rounded-xl h-9"
              >
                Cancelar
              </Button>
              <Button
                type="submit"
                size="sm"
                className="bg-orange-600 hover:bg-orange-500 text-white font-bold text-xs gap-1.5 rounded-xl h-9 px-4 shadow-md shadow-orange-950 cursor-pointer"
              >
                <Flame className="size-3.5" />
                Salvar e Sincronizar
              </Button>
            </div>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
