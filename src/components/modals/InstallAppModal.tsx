import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Download, Sparkles, Share2, PlusSquare, CheckCircle2, Smartphone, ShieldCheck, Zap } from 'lucide-react'

interface InstallAppModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  isIOS: boolean
  isInstalled: boolean
  onPromptInstall: () => Promise<boolean>
}

export function InstallAppModal({
  open,
  onOpenChange,
  isIOS,
  isInstalled,
  onPromptInstall,
}: InstallAppModalProps) {
  const handleInstallClick = async () => {
    const success = await onPromptInstall()
    if (success) {
      onOpenChange(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md w-full max-h-[90vh] overflow-y-auto p-4 sm:p-6 bg-[#0a0f1d]/95 backdrop-blur-2xl border border-white/[0.1] rounded-2xl shadow-2xl">
        <DialogHeader className="space-y-1 pb-3 border-b border-white/[0.06]">
          <div className="flex items-center gap-2.5">
            <div className="size-9 rounded-xl bg-gradient-to-tr from-emerald-500/20 to-teal-500/20 border border-emerald-500/30 text-emerald-400 flex items-center justify-center shrink-0">
              <Smartphone className="size-5" />
            </div>
            <div>
              <DialogTitle className="text-base sm:text-lg font-bold text-white flex items-center gap-2">
                <span>Instalar o Aplicativo</span>
                {isInstalled && (
                  <Badge variant="outline" className="bg-emerald-500/10 text-emerald-400 border-emerald-500/30 text-[10px]">
                    Instalado
                  </Badge>
                )}
              </DialogTitle>
              <DialogDescription className="text-xs text-slate-400">
                Adicione o Saldo Livre à tela inicial para ter a experiência de um aplicativo 100% nativo
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {isInstalled ? (
            <div className="p-4 rounded-2xl bg-emerald-950/30 border border-emerald-500/30 flex items-start gap-3 shadow-md">
              <CheckCircle2 className="size-5 text-emerald-400 shrink-0 mt-0.5" />
              <div className="space-y-1 text-xs">
                <span className="font-bold text-emerald-300 text-sm">Aplicativo já instalado!</span>
                <p className="text-slate-400 leading-relaxed">
                  Você já pode abrir o Saldo Livre diretamente pelo ícone na tela inicial ou gaveta de aplicativos com tela cheia sem barras do navegador.
                </p>
              </div>
            </div>
          ) : isIOS ? (
            /* Guia especial para Safari no iOS */
            <div className="space-y-3">
              <div className="p-3.5 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-1">
                <span className="text-xs font-bold text-white flex items-center gap-1.5">
                  <Sparkles className="size-3.5 text-amber-400" />
                  Instruções para iPhone / iPad (Safari):
                </span>
                <p className="text-[11px] text-slate-400">
                  No iOS, a instalação é feita pelo menu de compartilhamento do Safari:
                </p>
              </div>

              <div className="space-y-2 text-xs">
                <div className="p-3 rounded-xl bg-slate-950/70 border border-white/[0.06] flex items-center gap-3">
                  <div className="size-8 rounded-lg bg-sky-500/15 text-sky-400 border border-sky-500/25 flex items-center justify-center shrink-0">
                    <Share2 className="size-4" />
                  </div>
                  <div>
                    <span className="font-semibold text-slate-200 block">1. Toque em Compartilhar</span>
                    <span className="text-[11px] text-slate-400">Na barra inferior do Safari, toque no ícone com a seta apontando para cima.</span>
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-slate-950/70 border border-white/[0.06] flex items-center gap-3">
                  <div className="size-8 rounded-lg bg-emerald-500/15 text-emerald-400 border border-emerald-500/25 flex items-center justify-center shrink-0">
                    <PlusSquare className="size-4" />
                  </div>
                  <div>
                    <span className="font-semibold text-slate-200 block">2. "Adicionar à Tela de Início"</span>
                    <span className="text-[11px] text-slate-400">Role a lista de opções e selecione o botão de adicionar.</span>
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-slate-950/70 border border-white/[0.06] flex items-center gap-3">
                  <div className="size-8 rounded-lg bg-emerald-500/15 text-emerald-400 border border-emerald-500/25 flex items-center justify-center shrink-0">
                    <CheckCircle2 className="size-4" />
                  </div>
                  <div>
                    <span className="font-semibold text-slate-200 block">3. Confirmar "Adicionar"</span>
                    <span className="text-[11px] text-slate-400">Toque em Concluir/Adicionar no canto superior direito.</span>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            /* Instalação Automática (Android / Chrome / Edge) */
            <div className="space-y-3">
              <div className="p-4 rounded-2xl bg-gradient-to-r from-emerald-950/40 via-slate-900/60 to-emerald-950/20 border border-emerald-500/30 flex items-center gap-3.5 shadow-md">
                <div className="size-12 rounded-2xl bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center shrink-0">
                  <Download className="size-6" />
                </div>
                <div className="space-y-0.5">
                  <h4 className="text-sm font-bold text-white">Instalar Saldo Livre no seu dispositivo</h4>
                  <p className="text-xs text-slate-300">
                    Acesso imediato com 1 clique, sem precisar digitar URL ou abrir navegadores.
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="p-3 rounded-xl bg-slate-950/70 border border-white/[0.06] flex items-start gap-2">
                  <Zap className="size-4 text-amber-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-slate-200 block">Carregamento Rápido</span>
                    <span className="text-[10px] text-slate-400">Cache local inteligente</span>
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-slate-950/70 border border-white/[0.06] flex items-start gap-2">
                  <ShieldCheck className="size-4 text-emerald-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-slate-200 block">Tela Cheia Nativa</span>
                    <span className="text-[10px] text-slate-400">Zero barras de navegação</span>
                  </div>
                </div>
              </div>

              <Button
                size="lg"
                onClick={handleInstallClick}
                className="w-full h-11 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs sm:text-sm rounded-xl gap-2 shadow-lg shadow-emerald-950 cursor-pointer"
              >
                <Download className="size-4" />
                <span>Instalar Aplicativo Agora</span>
              </Button>
            </div>
          )}
        </div>

        <DialogFooter className="pt-2 border-t border-white/[0.06]">
          <Button variant="outline" size="sm" onClick={() => onOpenChange(false)} className="rounded-xl text-xs h-9 w-full sm:w-auto">
            Fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
