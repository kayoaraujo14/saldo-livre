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
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-orange-500/10 text-orange-400 border border-orange-500/20">
              <Flame className="size-5" />
            </div>
            <div>
              <DialogTitle className="flex items-center gap-2">
                <span>Configurar Backend no Firebase</span>
                {isFirebaseConfigured ? (
                  <Badge variant="outline" className="bg-emerald-500/10 text-emerald-400 border-emerald-500/30 text-[10px]">
                    🟢 Ativo
                  </Badge>
                ) : (
                  <Badge variant="outline" className="bg-amber-500/10 text-amber-400 border-amber-500/30 text-[10px]">
                    Pendente
                  </Badge>
                )}
              </DialogTitle>
              <DialogDescription>
                Conecte seu Google SSO, banco Cloud Firestore e Hosting de produção
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <form onSubmit={handleSave} className="space-y-4 py-2">
          {/* Quick paste helper */}
          <div className="p-3 rounded-xl bg-slate-900 border border-slate-800 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-slate-200">Colar bloco de configuração do Firebase:</span>
              <a
                href="https://console.firebase.google.com"
                target="_blank"
                rel="noreferrer"
                className="text-orange-400 hover:text-orange-300 flex items-center gap-1 text-[11px]"
              >
                <span>Console Firebase</span>
                <ExternalLink className="size-3" />
              </a>
            </div>

            <div className="flex gap-2">
              <Input
                value={pasteSnippet}
                onChange={(e) => setPasteSnippet(e.target.value)}
                placeholder="Cole aqui o const firebaseConfig = { ... } do Console"
                className="h-8 text-xs bg-slate-950 border-slate-700 font-mono"
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleParseSnippet}
                className="h-8 text-xs border-slate-700 hover:bg-slate-800 text-slate-200"
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
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="space-y-1 sm:col-span-2">
              <label className="text-xs font-medium text-slate-300">Firebase API Key *</label>
              <Input
                required
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="AIzaSy..."
                className="h-8 text-xs bg-slate-950 border-slate-800 font-mono"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-300">Project ID *</label>
              <Input
                required
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                placeholder="saldo-livre-app"
                className="h-8 text-xs bg-slate-950 border-slate-800 font-mono"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-300">Auth Domain</label>
              <Input
                value={authDomain}
                onChange={(e) => setAuthDomain(e.target.value)}
                placeholder={`${projectId || 'seu-projeto'}.firebaseapp.com`}
                className="h-8 text-xs bg-slate-950 border-slate-800 font-mono"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-300">App ID (Web)</label>
              <Input
                value={appId}
                onChange={(e) => setAppId(e.target.value)}
                placeholder="1:123456789:web:abcdef..."
                className="h-8 text-xs bg-slate-950 border-slate-800 font-mono"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-300">Storage Bucket (Opcional)</label>
              <Input
                value={storageBucket}
                onChange={(e) => setStorageBucket(e.target.value)}
                placeholder={`${projectId || 'seu-projeto'}.appspot.com`}
                className="h-8 text-xs bg-slate-950 border-slate-800 font-mono"
              />
            </div>
          </div>

          {/* Architecture Checklist */}
          <div className="p-3 rounded-xl bg-slate-950/60 border border-slate-800/80 space-y-2 text-xs">
            <span className="font-semibold text-slate-300 flex items-center gap-1.5">
              <ShieldCheck className="size-3.5 text-emerald-400" />
              Recursos prontos no Firebase:
            </span>
            <ul className="space-y-1 text-[11px] text-slate-400">
              <li className="flex items-center gap-1.5">
                <CheckCircle2 className="size-3 text-emerald-400" />
                <strong>Google SSO:</strong> Login com 1 clique e isolamento por UID.
              </li>
              <li className="flex items-center gap-1.5">
                <CheckCircle2 className="size-3 text-emerald-400" />
                <strong>Cloud Firestore:</strong> Sincronização em tempo real e regras de privacidade.
              </li>
              <li className="flex items-center gap-1.5">
                <CheckCircle2 className="size-3 text-emerald-400" />
                <strong>Firebase Hosting:</strong> Configurado em <code>firebase.json</code> pronto para deploy.
              </li>
            </ul>
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
            {isFirebaseConfigured && (
              <Button
                type="button"
                variant="destructive"
                size="sm"
                onClick={handleDisconnect}
                className="mr-auto gap-1 text-xs"
              >
                <Trash2 className="size-3.5" />
                Desconectar
              </Button>
            )}
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
              className="text-xs"
            >
              Cancelar
            </Button>
            <Button
              type="submit"
              size="sm"
              className="bg-orange-600 hover:bg-orange-500 text-white font-medium text-xs gap-1.5 shadow-md shadow-orange-950"
            >
              <Flame className="size-3.5" />
              Salvar e Iniciar Produção
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
