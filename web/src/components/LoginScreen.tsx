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
  TrendingUp,
  Target,
  HeartPulse,
  Users,
  ArrowRight,
  CheckCircle2,
  PieChart,
  Zap,
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
    <div className="min-h-screen bg-[#070a12] text-slate-100 flex flex-col relative overflow-x-hidden selection:bg-emerald-500/20 selection:text-emerald-300">
      {/* Background Ambient Glows */}
      <div className="absolute top-[-120px] left-1/2 -translate-x-1/2 w-[900px] h-[550px] bg-gradient-to-b from-emerald-500/15 via-teal-500/10 to-transparent rounded-full blur-[160px] pointer-events-none" />
      <div className="absolute top-[40%] right-[-100px] w-[600px] h-[600px] bg-indigo-600/10 rounded-full blur-[180px] pointer-events-none" />
      <div className="absolute bottom-[-100px] left-[-100px] w-[500px] h-[500px] bg-emerald-600/10 rounded-full blur-[160px] pointer-events-none" />

      {/* Top Navbar */}
      <header className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-5 flex items-center justify-between relative z-20">
        <div className="flex items-center gap-3">
          <div className="size-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 shadow-[0_0_25px_-5px_rgba(16,185,129,0.5)]">
            <div className="size-full bg-[#0b101b] rounded-[10px] flex items-center justify-center">
              <Sparkles className="size-5 text-emerald-400" />
            </div>
          </div>
          <div>
            <span className="font-extrabold text-lg text-white tracking-tight flex items-center gap-2">
              Saldo Livre
              <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                v2.5
              </span>
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2 sm:gap-3">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={enterDemo}
            className="text-xs text-slate-300 hover:text-white hover:bg-white/[0.06] cursor-pointer"
          >
            Modo Demonstração
          </Button>
          <Button
            size="sm"
            onClick={handleGoogleLogin}
            disabled={loading}
            className="bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold text-xs rounded-lg gap-2 shadow-lg shadow-emerald-500/20 cursor-pointer"
          >
            {loading ? <Loader2 className="size-3.5 animate-spin" /> : <Zap className="size-3.5" />}
            <span>Acessar Painel</span>
          </Button>
        </div>
      </header>

      {/* Hero Section */}
      <main className="flex-1 w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-14 relative z-10 space-y-16">
        <div className="max-w-3xl mx-auto text-center space-y-6">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/25 text-emerald-300 text-xs font-medium">
            <Sparkles className="size-3.5 text-emerald-400 animate-pulse" />
            <span>Novo: Envelopes Mensais com IA & Saúde Financeira em 4 Pilares</span>
          </div>

          <h1 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-[1.15]">
            Saber seu extrato não basta. Você precisa saber seu{' '}
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-400">
              Saldo Livre Real.
            </span>
          </h1>

          <p className="text-base sm:text-lg text-slate-300/90 max-w-2xl mx-auto leading-relaxed">
            A plataforma financeira moderna que desconta faturas futuras, compromissos fixos e despesas compartilhadas antes de você gastar. Tenha previsibilidade matemática, envelopes mensais dinâmicos e aconselhamento proativo com IA.
          </p>

          {/* Call to Actions */}
          <div className="pt-2 flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-4 max-w-md mx-auto">
            <Button
              size="lg"
              onClick={handleGoogleLogin}
              disabled={loading}
              className="w-full sm:w-auto h-12 px-6 bg-white hover:bg-slate-100 text-slate-900 font-bold text-sm rounded-xl gap-3 shadow-xl shadow-black/40 cursor-pointer hover:scale-[1.02] active:scale-[0.99] transition-all"
            >
              {loading ? (
                <>
                  <Loader2 className="size-5 shrink-0 animate-spin text-slate-700" />
                  <span>Conectando com o Google...</span>
                </>
              ) : (
                <>
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
                  <span>Entrar com o Google</span>
                </>
              )}
            </Button>

            <Button
              type="button"
              variant="outline"
              size="lg"
              onClick={enterDemo}
              className="w-full sm:w-auto h-12 px-6 border-white/10 bg-slate-900/70 hover:bg-slate-800 text-slate-200 font-semibold text-sm rounded-xl gap-2 cursor-pointer transition-all hover:border-emerald-500/30"
            >
              <Sparkles className="size-4 text-emerald-400" />
              <span>Testar Modo Demonstração</span>
              <ArrowRight className="size-3.5 text-slate-400" />
            </Button>
          </div>

          <p className="text-xs text-slate-400 flex items-center justify-center gap-1.5 pt-1">
            <Lock className="size-3.5 text-emerald-400" />
            <span>Sem cadastro longo. Privacidade estrita e isolamento total entre usuários.</span>
          </p>
        </div>

        {/* Feature Grid: 4 Grandes Inovações */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Card 1: Saldo Livre Real */}
          <div className="glass-card rounded-2xl p-5 border border-white/[0.08] hover:border-emerald-500/30 transition-all bg-slate-900/40 space-y-3">
            <div className="size-10 rounded-xl bg-emerald-500/15 border border-emerald-500/25 flex items-center justify-center text-emerald-400">
              <TrendingUp className="size-5" />
            </div>
            <h3 className="font-bold text-sm text-white">Saldo Livre Real</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Diferente de apps tradicionais que só mostram extratos passados, calculamos quanto você realmente pode gastar hoje sem comprometer contas e faturas futuras.
            </p>
            <div className="pt-1 flex items-center gap-1.5 text-[11px] text-emerald-400 font-medium">
              <CheckCircle2 className="size-3.5" />
              <span>Adeus ansiedade financeira</span>
            </div>
          </div>

          {/* Card 2: Envelopes Mensais por IA */}
          <div className="glass-card rounded-2xl p-5 border border-white/[0.08] hover:border-emerald-500/30 transition-all bg-slate-900/40 space-y-3">
            <div className="size-10 rounded-xl bg-cyan-500/15 border border-cyan-500/25 flex items-center justify-center text-cyan-400">
              <Target className="size-5" />
            </div>
            <h3 className="font-bold text-sm text-white">Envelopes Mensais Dinâmicos</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Defina tetos que mudam mês a mês conforme sua renda e necessidades. A IA calibra seus limites respeitando o que você já gastou e a entrada prevista do mês.
            </p>
            <div className="pt-1 flex items-center gap-1.5 text-[11px] text-cyan-400 font-medium">
              <CheckCircle2 className="size-3.5" />
              <span>Orçamento flexível mês a mês</span>
            </div>
          </div>

          {/* Card 3: Saúde Financeira 4 Pilares */}
          <div className="glass-card rounded-2xl p-5 border border-white/[0.08] hover:border-emerald-500/30 transition-all bg-slate-900/40 space-y-3">
            <div className="size-10 rounded-xl bg-purple-500/15 border border-purple-500/25 flex items-center justify-center text-purple-400">
              <HeartPulse className="size-5" />
            </div>
            <h3 className="font-bold text-sm text-white">Saúde Financeira 4 Pilares</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Auditoria matemática contínua: Liquidez da reserva, Taxa de poupança com investimentos reais, Endividamento de faturas e Previsibilidade de contas.
            </p>
            <div className="pt-1 flex items-center gap-1.5 text-[11px] text-purple-400 font-medium">
              <CheckCircle2 className="size-3.5" />
              <span>Score de 0 a 100 com conselhos</span>
            </div>
          </div>

          {/* Card 4: Cartões Compartilhados & Amigos */}
          <div className="glass-card rounded-2xl p-5 border border-white/[0.08] hover:border-emerald-500/30 transition-all bg-slate-900/40 space-y-3">
            <div className="size-10 rounded-xl bg-amber-500/15 border border-amber-500/25 flex items-center justify-center text-amber-400">
              <Users className="size-5" />
            </div>
            <h3 className="font-bold text-sm text-white">Privacidade & Compartilhamento</h3>
            <p className="text-xs text-slate-400 leading-relaxed">
              Divida gastos com seu parceiro(a) ou amigos de forma justa. Suas despesas particulares e faturas privadas nunca são exibidas para terceiros.
            </p>
            <div className="pt-1 flex items-center gap-1.5 text-[11px] text-amber-400 font-medium">
              <CheckCircle2 className="size-3.5" />
              <span>Isolamento total de dados</span>
            </div>
          </div>
        </div>

        {/* Feature Highlight: Copiloto Gemini IA Integrado */}
        <div className="glass-card rounded-3xl p-6 sm:p-10 border border-white/[0.08] bg-gradient-to-b from-slate-900/80 to-[#0b101b] relative overflow-hidden">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
            <div className="lg:col-span-7 space-y-4">
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-500/15 border border-indigo-500/30 text-indigo-300 text-xs font-semibold">
                <Bot className="size-3.5" />
                <span>O Coração do Projeto: Copiloto Gemini IA</span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-extrabold text-white">
                Seu consultor financeiro pessoal 24 horas por dia.
              </h2>
              <p className="text-sm text-slate-300 leading-relaxed">
                Converse com a IA naturalmente por texto ou voz. Peça para lançar despesas, analisar se você pode comprar algo hoje, rebalancear categorias estouradas e diagnosticar gargalos no seu orçamento mensal.
              </p>
              <div className="pt-2 grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs text-slate-300">
                <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-950/60 border border-white/[0.06]">
                  <PieChart className="size-4 text-emerald-400 shrink-0" />
                  <span>Rebalanceamento dinâmico de envelopes</span>
                </div>
                <div className="flex items-center gap-2 p-2.5 rounded-xl bg-slate-950/60 border border-white/[0.06]">
                  <CreditCard className="size-4 text-violet-400 shrink-0" />
                  <span>Simulação de impacto em compras</span>
                </div>
              </div>
            </div>

            <div className="lg:col-span-5 p-5 rounded-2xl bg-slate-950/90 border border-white/[0.08] shadow-2xl space-y-3.5">
              <div className="flex items-center justify-between border-b border-white/[0.06] pb-3">
                <div className="flex items-center gap-2">
                  <div className="size-2 rounded-full bg-emerald-400 animate-pulse" />
                  <span className="text-xs font-bold text-white">Copiloto Financeiro</span>
                </div>
                <span className="text-[10px] text-slate-400 font-mono">Gemini Pro</span>
              </div>
              <div className="space-y-2.5 text-xs">
                <div className="p-3 rounded-xl bg-slate-900 border border-white/[0.06] text-slate-200">
                  "Gastei R$ 120 em um jantar japonês ontem no cartão Nubank com meu amigo Carlos (ele vai me pagar metade)."
                </div>
                <div className="p-3 rounded-xl bg-emerald-950/30 border border-emerald-500/30 text-emerald-200 space-y-1">
                  <div className="font-semibold text-emerald-400 flex items-center gap-1.5">
                    <Sparkles className="size-3.5" />
                    Transação e cobrança preparadas:
                  </div>
                  <p className="text-[11px] text-slate-300">
                    • R$ 60,00 lançados em Alimentação (seu impacto)
                    <br />• R$ 60,00 marcados a receber de Carlos
                    <br />• Envelope de Alimentação em 68% do teto.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Security & Isolation Banner */}
        <div className="p-5 sm:p-6 rounded-2xl bg-slate-900/50 border border-white/[0.08] flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="size-11 rounded-xl bg-emerald-500/15 border border-emerald-500/25 flex items-center justify-center text-emerald-400 shrink-0">
              <ShieldCheck className="size-6" />
            </div>
            <div>
              <h4 className="font-bold text-sm text-white">Blindagem por Firestore Security Rules</h4>
              <p className="text-xs text-slate-400">
                Seus dados são protegidos por regras declarativas no banco de dados. Nenhum outro usuário ou requisição consegue ler suas finanças pessoais.
              </p>
            </div>
          </div>
          <Button
            size="sm"
            onClick={enterDemo}
            className="shrink-0 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs h-9 px-4 rounded-xl border border-white/[0.08] cursor-pointer"
          >
            Experimentar Sem Cadastro
          </Button>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full border-t border-white/[0.06] py-6 relative z-10 text-center text-xs text-slate-400">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>Saldo Livre © 2026 • Gestão Financeira Pessoal com IA</span>
          <span className="text-[11px] text-slate-400">
            Ambiente 100% Criptografado & Isolado
          </span>
        </div>
      </footer>
    </div>
  )
}
