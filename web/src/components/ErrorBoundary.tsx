import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle, RefreshCw, Trash2, Sparkles, Home } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
  errorInfo: ErrorInfo | null
  showDetails: boolean
}

export class ErrorBoundary extends Component<Props, State> {
  public override state: State = {
    hasError: false,
    error: null,
    errorInfo: null,
    showDetails: false,
  }

  public static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error }
  }

  public override componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary capturou uma falha não tratada:', error, errorInfo)
    this.setState({ errorInfo })
  }

  private handleReload = () => {
    window.location.reload()
  }

  private handleResetToDemo = () => {
    try {
      localStorage.removeItem('saldo-livre:session-uid')
      localStorage.setItem('saldo-livre:force-demo', '1')
    } catch {
      // Ignora erro de acesso a localStorage
    }
    window.location.reload()
  }

  private handleFullClearAndReset = async () => {
    try {
      localStorage.clear()
      sessionStorage.clear()
      if ('serviceWorker' in navigator) {
        const registrations = await navigator.serviceWorker.getRegistrations()
        for (const registration of registrations) {
          await registration.unregister()
        }
      }
      if ('caches' in window) {
        const cacheNames = await caches.keys()
        for (const name of cacheNames) {
          await caches.delete(name)
        }
      }
    } catch (e) {
      console.warn('Erro ao limpar cache local:', e)
    }
    window.location.href = '/'
  }

  public override render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-[#090d16] text-slate-100 flex flex-col justify-center items-center px-4 py-12 relative overflow-hidden selection:bg-emerald-500/20 selection:text-emerald-300">
          {/* Subtle Ambient Glows */}
          <div className="absolute top-[-10%] left-[20%] w-[500px] h-[500px] bg-rose-500/10 rounded-full blur-[140px] pointer-events-none" />
          <div className="absolute bottom-[-10%] right-[20%] w-[500px] h-[500px] bg-amber-500/10 rounded-full blur-[140px] pointer-events-none" />

          <div className="w-full max-w-lg relative z-10 space-y-6">
            {/* Header Icon */}
            <div className="text-center space-y-3">
              <div className="inline-flex size-16 rounded-2xl bg-gradient-to-tr from-rose-500 to-amber-500 p-0.5 shadow-[0_0_35px_-5px_rgba(244,63,94,0.4)] items-center justify-center mb-2">
                <div className="size-full bg-[#0b101b] rounded-[14px] flex items-center justify-center">
                  <AlertTriangle className="size-8 text-rose-400" />
                </div>
              </div>
              <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white m-0">
                Ops! Algo deu errado ao carregar
              </h1>
              <p className="text-sm text-slate-400 max-w-md mx-auto leading-relaxed">
                Ocorreu uma falha inesperada durante a inicialização da tela. Seus dados estão preservados e você pode restaurar a aplicação com as opções abaixo.
              </p>
            </div>

            {/* Recovery Actions Card */}
            <div className="glass-card rounded-2xl p-6 sm:p-7 border border-white/[0.08] shadow-2xl space-y-4">
              <div className="space-y-2">
                <Button
                  onClick={this.handleReload}
                  className="w-full h-11 bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-semibold text-sm rounded-xl gap-2 shadow-lg shadow-emerald-500/20 transition-all cursor-pointer"
                >
                  <RefreshCw className="size-4 shrink-0" />
                  <span>Recarregar Aplicativo</span>
                </Button>

                <Button
                  variant="outline"
                  onClick={this.handleResetToDemo}
                  className="w-full h-11 border-slate-700/80 bg-slate-900/60 hover:bg-slate-800 text-slate-200 font-semibold text-xs rounded-xl gap-2 transition-all cursor-pointer"
                >
                  <Sparkles className="size-4 text-violet-400 shrink-0" />
                  <span>Restaurar em Modo Demonstração (Sem Login)</span>
                </Button>

                <Button
                  variant="ghost"
                  onClick={this.handleFullClearAndReset}
                  className="w-full h-10 hover:bg-rose-500/10 text-rose-400 hover:text-rose-300 font-medium text-xs rounded-xl gap-2 transition-all cursor-pointer"
                >
                  <Trash2 className="size-3.5 shrink-0" />
                  <span>Limpar Cache Local e Reiniciar</span>
                </Button>
              </div>

              {/* Technical Details Toggle */}
              <div className="pt-2 border-t border-slate-800/80">
                <button
                  type="button"
                  onClick={() => this.setState((prev) => ({ showDetails: !prev.showDetails }))}
                  className="text-[11px] text-slate-400 hover:text-slate-200 transition-colors cursor-pointer flex items-center justify-between w-full"
                >
                  <span>Detalhes técnicos do erro</span>
                  <span>{this.state.showDetails ? '▲ Ocultar' : '▼ Expandir'}</span>
                </button>

                {this.state.showDetails && (
                  <div className="mt-3 p-3 rounded-lg bg-black/60 border border-slate-800/80 font-mono text-[11px] text-rose-300 break-words whitespace-pre-wrap max-h-48 overflow-y-auto">
                    {this.state.error?.toString() || 'Erro desconhecido'}
                    {this.state.errorInfo?.componentStack && (
                      <span className="text-slate-400 block mt-2 text-[10px]">
                        {this.state.errorInfo.componentStack}
                      </span>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Back to Home Link */}
            <div className="text-center">
              <a
                href="/"
                className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200 transition-colors"
              >
                <Home className="size-3.5" />
                <span>Voltar para a página inicial</span>
              </a>
            </div>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
