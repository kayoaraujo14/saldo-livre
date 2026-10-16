import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { useFinance } from '@/store/FinanceContext'
import {
  ShieldCheck,
  CreditCard,
  Bot,
  Lock,
  Sparkles,
  Loader2,
} from 'lucide-react'

export function LoginScreen() {
  const { signIn, enterDemo } = useFinance()
  const [loading, setLoading] = useState(false)

  const handleGoogleLogin = async () => {
    setLoading(true)
    try {
      await signIn()
    } catch (err: any) {
      console.error(err)
      if (err?.code === 'auth/unauthorized-domain') {
        alert(
          'Domínio não autorizado pelo Firebase!\n\n' +
          'Como resolver:\n' +
          '1. Acesse o Firebase Console > Authentication > Configurações > Domínios autorizados.\n' +
          '2. Adicione os domínios: localhost e 127.0.0.1\n' +
          '(No ar, acesse via: https://saldo-livre-c3ca3.web.app)'
        )
      } else if (err?.code === 'auth/popup-closed-by-user') {
        // Usuário fechou o popup
      } else {
        alert(
          `Falha ao autenticar com o Google: ${err?.message || err}\n\n` +
          'Verifique se o provedor Google está ativado no Firebase Authentication.'
        )
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-[#090d16] text-slate-100 flex flex-col justify-center items-center px-4 py-12 relative overflow-hidden selection:bg-emerald-500/20 selection:text-emerald-300">
      {/* Background Glows */}
      <div className="absolute top-[-10%] left-[20%] w-[500px] h-[500px] bg-emerald-500/10 rounded-full blur-[140px] pointer-events-none" />
      <div className="absolute bottom-[-10%] right-[20%] w-[500px] h-[500px] bg-indigo-500/10 rounded-full blur-[140px] pointer-events-none" />

      <div className="w-full max-w-md relative z-10 space-y-6">
        {/* Brand Header */}
        <div className="text-center space-y-2">
          <div className="inline-flex size-14 rounded-2xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 shadow-[0_0_30px_-5px_rgba(16,185,129,0.5)] items-center justify-center mb-2">
            <div className="size-full bg-[#0b101b] rounded-[14px] flex items-center justify-center">
              <Sparkles className="size-7 text-emerald-400" />
            </div>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white m-0">
            Saldo Livre
          </h1>
          <p className="text-sm text-slate-400 max-w-sm mx-auto">
            Gestão Financeira Pessoal & Cartões Compartilhados com Privacidade Estrita
          </p>
        </div>

        {/* Login Card */}
        <div className="glass-card rounded-2xl p-6 sm:p-8 border border-white/[0.08] shadow-2xl space-y-6">
          <div className="space-y-1.5 text-center">
            <h2 className="text-lg font-semibold text-slate-100 m-0">Acesse sua Conta</h2>
            <p className="text-xs text-slate-400">
              Conecte-se com sua conta Google para acessar seu painel seguro
            </p>
          </div>

          {/* Primary Action */}
          <div className="space-y-3">
            <Button
              size="lg"
              onClick={handleGoogleLogin}
              disabled={loading}
              className="w-full h-12 bg-white hover:bg-slate-100 text-slate-900 font-semibold text-sm rounded-xl gap-3 shadow-lg shadow-black/30 transition-all cursor-pointer hover:shadow-xl active:scale-[0.99]"
            >
              {loading ? (
                <>
                  <Loader2 className="size-5 shrink-0 animate-spin text-slate-700" />
                  <span>Conectando com o Google...</span>
                </>
              ) : (
                <>
                  {/* Google Colored G Icon */}
                  <svg className="size-5 shrink-0" viewBox="0 0 24 24">
                    <path
                      fill="#4285F4"
                      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                    />
                    <path
                      fill="#34A853"
                      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                    />
                    <path
                      fill="#FBBC05"
                      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                    />
                    <path
                      fill="#EA4335"
                      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                    />
                  </svg>
                  <span>Entrar com Google</span>
                </>
              )}
            </Button>

            {/* Demo Mode Button */}
            <Button
              type="button"
              variant="outline"
              onClick={enterDemo}
              className="w-full h-11 border-slate-700/80 bg-slate-900/60 hover:bg-slate-800 text-slate-300 font-semibold text-xs rounded-xl gap-2 transition-all cursor-pointer"
            >
              <Sparkles className="size-4 text-violet-400" />
              <span>Experimentar Modo Demonstração (Sem Login)</span>
            </Button>
          </div>

          {/* Privacy Guarantee Note */}
          <div className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800/80 space-y-1.5">
            <div className="flex items-center gap-1.5 text-xs font-semibold text-emerald-400">
              <Lock className="size-3.5" />
              <span>Garantia de Isolamento e Privacidade</span>
            </div>
            <p className="text-[11px] text-slate-400 leading-relaxed">
              Gastos pessoais e faturas particulares <strong>nunca são exibidos</strong> para outros usuários. Apenas despesas explicitamente compartilhadas (50/50 ou no cartão de um para o outro) são refletidas nos saldos mútuos.
            </p>
          </div>
        </div>

        {/* Feature Pills */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-center text-[11px] text-slate-400 pt-2">
          <div className="flex items-center justify-center gap-1.5 p-2 rounded-lg bg-slate-900/40 border border-slate-800/40">
            <ShieldCheck className="size-3.5 text-emerald-400 shrink-0" />
            <span>Dados 100% isolados</span>
          </div>
          <div className="flex items-center justify-center gap-1.5 p-2 rounded-lg bg-slate-900/40 border border-slate-800/40">
            <CreditCard className="size-3.5 text-violet-400 shrink-0" />
            <span>Cartão Compartilhado</span>
          </div>
          <div className="flex items-center justify-center gap-1.5 p-2 rounded-lg bg-slate-900/40 border border-slate-800/40">
            <Bot className="size-3.5 text-cyan-400 shrink-0" />
            <span>Copiloto Gemini IA</span>
          </div>
        </div>
      </div>
    </div>
  )
}
