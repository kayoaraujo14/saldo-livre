import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useFinance } from '@/store/FinanceContext'
import type { OpenFinanceConnection, NewTransaction } from '@/types'
import { dateInMonth, formatBRL, todayKey } from '@/lib/utils'
import {
  Landmark,
  ShieldCheck,
  RefreshCw,
  Plus,
  CheckCircle2,
  Trash2,
  ExternalLink,
  Layers,
  KeyRound,
  ArrowRight,
  Clock,
  Sparkles,
  Zap,
  Info,
} from 'lucide-react'

interface OpenFinanceModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

interface BankOption {
  code: string
  name: string
  color: string
  accentBg: string
  textColor: string
  description: string
}

const SUPPORTED_BANKS: BankOption[] = [
  {
    code: 'nubank',
    name: 'Nubank',
    color: '#820AD1',
    accentBg: 'bg-[#820AD1]/20 border-[#820AD1]/40 text-purple-300',
    textColor: 'text-[#a855f7]',
    description: 'Conta digital e Fatura de Cartão de Crédito',
  },
  {
    code: 'itau',
    name: 'Itaú Unibanco',
    color: '#EC7000',
    accentBg: 'bg-[#EC7000]/20 border-[#EC7000]/40 text-amber-300',
    textColor: 'text-[#f97316]',
    description: 'Conta Corrente, Cartões Personnalité / Itaú',
  },
  {
    code: 'inter',
    name: 'Banco Inter',
    color: '#FF7A00',
    accentBg: 'bg-[#FF7A00]/20 border-[#FF7A00]/40 text-orange-300',
    textColor: 'text-[#fb923c]',
    description: 'Conta Global, Extrato e Fatura Inter Mastercard',
  },
  {
    code: 'bradesco',
    name: 'Bradesco',
    color: '#CC092F',
    accentBg: 'bg-[#CC092F]/20 border-[#CC092F]/40 text-rose-300',
    textColor: 'text-[#f43f5e]',
    description: 'Conta Corrente Prime / Varejo e Cartões Elo/Visa',
  },
  {
    code: 'santander',
    name: 'Santander',
    color: '#EC0000',
    accentBg: 'bg-[#EC0000]/20 border-[#EC0000]/40 text-red-300',
    textColor: 'text-[#ef4444]',
    description: 'Conta Select e Cartões Santander SX / Elite',
  },
  {
    code: 'c6',
    name: 'C6 Bank',
    color: '#242424',
    accentBg: 'bg-slate-800/80 border-slate-600 text-slate-200',
    textColor: 'text-slate-200',
    description: 'Conta Corrente e Cartão C6 Carbon / Black',
  },
  {
    code: 'bb',
    name: 'Banco do Brasil',
    color: '#FFE600',
    accentBg: 'bg-yellow-500/20 border-yellow-500/40 text-yellow-300',
    textColor: 'text-yellow-400',
    description: 'Conta Corrente Estilo / BB e Ourocard',
  },
  {
    code: 'btg',
    name: 'BTG Pactual',
    color: '#001E62',
    accentBg: 'bg-blue-900/30 border-blue-700/50 text-blue-300',
    textColor: 'text-blue-400',
    description: 'Banking e Cartão Black com Cashback',
  },
]

const OPEN_FINANCE_STORAGE_KEY = 'saldo-livre:open-finance-connections'
const PLUGGY_CONFIG_KEY = 'saldo-livre:pluggy-config'

function loadSavedConnections(): OpenFinanceConnection[] {
  try {
    const raw = localStorage.getItem(OPEN_FINANCE_STORAGE_KEY)
    if (!raw) return []
    return JSON.parse(raw)
  } catch {
    return []
  }
}

function saveConnections(connections: OpenFinanceConnection[]) {
  localStorage.setItem(OPEN_FINANCE_STORAGE_KEY, JSON.stringify(connections))
}

export function OpenFinanceModal({ open, onOpenChange }: OpenFinanceModalProps) {
  const { me, month, addMany } = useFinance()
  const [activeTab, setActiveTab] = useState<'connections' | 'connect' | 'setup'>('connections')
  const [connections, setConnections] = useState<OpenFinanceConnection[]>(loadSavedConnections)
  const [connectingBank, setConnectingBank] = useState<BankOption | null>(null)
  const [connectStep, setConnectStep] = useState<'idle' | 'authorizing' | 'syncing' | 'success'>('idle')
  const [syncingId, setSyncingId] = useState<string | null>(null)
  const [syncSuccessToast, setSyncSuccessToast] = useState<string | null>(null)

  // Pluggy developer config
  const [pluggyClientId, setPluggyClientId] = useState(() => {
    try {
      const cfg = JSON.parse(localStorage.getItem(PLUGGY_CONFIG_KEY) || '{}')
      return cfg.clientId || ''
    } catch {
      return ''
    }
  })
  const [pluggyClientSecret, setPluggyClientSecret] = useState(() => {
    try {
      const cfg = JSON.parse(localStorage.getItem(PLUGGY_CONFIG_KEY) || '{}')
      return cfg.clientSecret || ''
    } catch {
      return ''
    }
  })
  const [pluggySaved, setPluggySaved] = useState(false)

  const handleSavePluggy = () => {
    localStorage.setItem(
      PLUGGY_CONFIG_KEY,
      JSON.stringify({ clientId: pluggyClientId.trim(), clientSecret: pluggyClientSecret.trim() }),
    )
    setPluggySaved(true)
    setTimeout(() => setPluggySaved(false), 2500)
  }

  const handleStartConnect = (bank: BankOption) => {
    setConnectingBank(bank)
    setConnectStep('authorizing')

    // Simulate OAuth consent flow
    setTimeout(() => {
      setConnectStep('syncing')
      setTimeout(() => {
        const newConn: OpenFinanceConnection = {
          id: `conn_${bank.code}_${Date.now()}`,
          provider: 'pluggy',
          institutionName: bank.name,
          institutionCode: bank.code,
          institutionColor: bank.color,
          status: 'connected',
          connectedAt: Date.now(),
          lastSyncAt: Date.now(),
          accounts: [
            {
              id: `acc_${bank.code}_checking`,
              institutionName: bank.name,
              institutionCode: bank.code,
              institutionColor: bank.color,
              type: 'checking',
              accountNumber: '**** 8492-1',
              balance: 4250.8,
              lastSyncAt: Date.now(),
              status: 'active',
            },
            {
              id: `acc_${bank.code}_card`,
              institutionName: bank.name,
              institutionCode: bank.code,
              institutionColor: bank.color,
              type: 'credit_card',
              accountNumber: '**** 4029',
              currentInvoice: 1845.3,
              invoiceDueDate: dateInMonth(month, 15),
              lastSyncAt: Date.now(),
              status: 'active',
            },
          ],
        }

        const updated = [newConn, ...connections.filter((c) => c.institutionCode !== bank.code)]
        setConnections(updated)
        saveConnections(updated)
        setConnectStep('success')

        // Automatically import historical/recent transactions into Saldo Livre
        const sampleTxs: NewTransaction[] = [
          {
            createdByUid: me?.uid || 'user',
            paidByUid: me?.uid || 'user',
            cardId: null,
            description: `${bank.name} - Mercado Extra`,
            category: 'Alimentação',
            type: 'expense',
            paymentMethod: 'credit_card',
            status: 'pending',
            estimatedAmount: null,
            actualAmount: 215.4,
            totalAmount: 215.4,
            dueDate: dateInMonth(month, 15),
            competenceMonth: month,
            visibleToUids: [me?.uid || 'user'],
            userImpact: { [me?.uid || 'user']: 215.4 },
            allocations: [{ type: 'PLATFORM_USER', uid: me?.uid || 'user', name: me?.name || 'Você', amount: 215.4, reimbursed: true }],
            splitType: 'mine',
            createdAt: Date.now() - 3600000,
          },
          {
            createdByUid: me?.uid || 'user',
            paidByUid: me?.uid || 'user',
            cardId: null,
            description: `${bank.name} - Posto Ipiranga`,
            category: 'Transporte',
            type: 'expense',
            paymentMethod: 'credit_card',
            status: 'pending',
            estimatedAmount: null,
            actualAmount: 180.0,
            totalAmount: 180.0,
            dueDate: dateInMonth(month, 15),
            competenceMonth: month,
            visibleToUids: [me?.uid || 'user'],
            userImpact: { [me?.uid || 'user']: 180.0 },
            allocations: [{ type: 'PLATFORM_USER', uid: me?.uid || 'user', name: me?.name || 'Você', amount: 180.0, reimbursed: true }],
            splitType: 'mine',
            createdAt: Date.now() - 7200000,
          },
          {
            createdByUid: me?.uid || 'user',
            paidByUid: me?.uid || 'user',
            cardId: null,
            description: `${bank.name} - Farmácia Raia`,
            category: 'Saúde',
            type: 'expense',
            paymentMethod: 'credit_card',
            status: 'pending',
            estimatedAmount: null,
            actualAmount: 64.9,
            totalAmount: 64.9,
            dueDate: dateInMonth(month, 15),
            competenceMonth: month,
            visibleToUids: [me?.uid || 'user'],
            userImpact: { [me?.uid || 'user']: 64.9 },
            allocations: [{ type: 'PLATFORM_USER', uid: me?.uid || 'user', name: me?.name || 'Você', amount: 64.9, reimbursed: true }],
            splitType: 'mine',
            createdAt: Date.now() - 10800000,
          },
        ]

        addMany(sampleTxs).catch(console.error)

        setTimeout(() => {
          setConnectingBank(null)
          setConnectStep('idle')
          setActiveTab('connections')
        }, 1600)
      }, 1400)
    }, 1200)
  }

  const handleSyncNow = async (connId: string) => {
    setSyncingId(connId)
    await new Promise((r) => setTimeout(r, 1200))

    const target = connections.find((c) => c.id === connId)
    if (target) {
      // Simulate fetching 2 new transactions
      const sampleTxs: NewTransaction[] = [
        {
          createdByUid: me?.uid || 'user',
          paidByUid: me?.uid || 'user',
          cardId: null,
          description: `${target.institutionName} - iFood Almoço`,
          category: 'Alimentação',
          type: 'expense',
          paymentMethod: 'pix',
          status: 'paid',
          estimatedAmount: null,
          actualAmount: 58.0,
          totalAmount: 58.0,
          dueDate: todayKey(),
          competenceMonth: month,
          visibleToUids: [me?.uid || 'user'],
          userImpact: { [me?.uid || 'user']: 58.0 },
          allocations: [{ type: 'PLATFORM_USER', uid: me?.uid || 'user', name: me?.name || 'Você', amount: 58.0, reimbursed: true }],
          splitType: 'mine',
          createdAt: Date.now(),
        },
      ]
      await addMany(sampleTxs)
    }

    const updated = connections.map((c) => (c.id === connId ? { ...c, lastSyncAt: Date.now() } : c))
    setConnections(updated)
    saveConnections(updated)
    setSyncingId(null)
    setSyncSuccessToast('Transações do banco sincronizadas com sucesso!')
    setTimeout(() => setSyncSuccessToast(null), 3000)
  }

  const handleDeleteConnection = (connId: string) => {
    const updated = connections.filter((c) => c.id !== connId)
    setConnections(updated)
    saveConnections(updated)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl bg-[#0b101b] border-slate-800 text-slate-100 p-0 overflow-hidden shadow-2xl rounded-2xl">
        {/* Header */}
        <div className="p-5 border-b border-white/[0.08] bg-gradient-to-r from-slate-900 via-[#0d1424] to-emerald-950/30">
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="size-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 p-0.5 shadow-md flex items-center justify-center shrink-0">
                <div className="size-full bg-[#0a0f1d] rounded-[10px] flex items-center justify-center text-emerald-400">
                  <Landmark className="size-5" />
                </div>
              </div>
              <div>
                <DialogTitle className="text-base font-bold text-white flex items-center gap-2">
                  <span>Open Finance Brasil</span>
                  <Badge className="bg-emerald-500/20 text-emerald-300 border-emerald-500/30 text-[10px] font-semibold py-0 px-1.5">
                    BACEN Certificado
                  </Badge>
                </DialogTitle>
                <DialogDescription className="text-xs text-slate-400 mt-0.5">
                  Conecte suas contas e faturas bancárias para sincronização automática em tempo real.
                </DialogDescription>
              </div>
            </div>

            <div className="flex items-center gap-1.5 text-[10px] text-slate-400 bg-slate-900/80 px-2.5 py-1 rounded-lg border border-slate-800 shrink-0">
              <ShieldCheck className="size-3.5 text-emerald-400 shrink-0" />
              <span>Somente Leitura</span>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div className="flex gap-2 mt-4 pt-3 border-t border-white/[0.06]">
            <button
              type="button"
              onClick={() => setActiveTab('connections')}
              className={`text-xs font-semibold py-1.5 px-3 rounded-lg transition-all cursor-pointer flex items-center gap-1.5 ${
                activeTab === 'connections'
                  ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-xs'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <Layers className="size-3.5" />
              <span>Minhas Conexões ({connections.length})</span>
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('connect')}
              className={`text-xs font-semibold py-1.5 px-3 rounded-lg transition-all cursor-pointer flex items-center gap-1.5 ${
                activeTab === 'connect'
                  ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-xs'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <Plus className="size-3.5" />
              <span>Conectar Novo Banco</span>
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('setup')}
              className={`text-xs font-semibold py-1.5 px-3 rounded-lg transition-all cursor-pointer flex items-center gap-1.5 ${
                activeTab === 'setup'
                  ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-xs'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <KeyRound className="size-3.5" />
              <span>Arquitetura & Pluggy API</span>
            </button>
          </div>
        </div>

        {/* Sync Toast Feedback */}
        {syncSuccessToast && (
          <div className="mx-5 mt-3 p-2.5 rounded-xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 text-xs flex items-center gap-2 animate-in fade-in">
            <CheckCircle2 className="size-4 text-emerald-400 shrink-0" />
            <span className="font-medium">{syncSuccessToast}</span>
          </div>
        )}

        {/* Tab 1: Conexões Ativas */}
        {activeTab === 'connections' && (
          <div className="p-5 max-h-[62vh] overflow-y-auto space-y-4">
            {connections.length === 0 ? (
              <div className="py-10 text-center flex flex-col items-center justify-center">
                <div className="size-16 rounded-2xl bg-slate-900 border border-slate-800 text-slate-500 flex items-center justify-center mb-3">
                  <Landmark className="size-8 text-slate-400" />
                </div>
                <h4 className="text-sm font-bold text-white">Nenhum banco conectado ainda</h4>
                <p className="text-xs text-slate-400 max-w-sm mt-1">
                  Ao conectar seu banco via Open Finance, suas despesas e faturas de cartão são sincronizadas automaticamente sem você precisar subir PDFs todo mês.
                </p>
                <Button
                  onClick={() => setActiveTab('connect')}
                  className="mt-4 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs h-9 px-4 rounded-xl flex items-center gap-2 cursor-pointer shadow-md"
                >
                  <Plus className="size-4" />
                  <span>Conectar Primeiro Banco</span>
                </Button>
              </div>
            ) : (
              <div className="space-y-3">
                {connections.map((conn) => {
                  const isSyncing = syncingId === conn.id
                  const lastSyncFormatted = new Date(conn.lastSyncAt).toLocaleTimeString('pt-BR', {
                    hour: '2-digit',
                    minute: '2-digit',
                  })

                  return (
                    <div
                      key={conn.id}
                      className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 hover:border-slate-700 transition-all space-y-3"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2.5">
                          <div
                            className="size-8 rounded-lg flex items-center justify-center text-white font-bold text-xs shadow-xs"
                            style={{ backgroundColor: conn.institutionColor || '#10b981' }}
                          >
                            {conn.institutionName.slice(0, 2).toUpperCase()}
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <h5 className="text-xs font-bold text-white">{conn.institutionName}</h5>
                              <Badge className="bg-emerald-500/20 text-emerald-300 border-emerald-500/30 text-[9px] py-0 px-1">
                                Conectado
                              </Badge>
                            </div>
                            <span className="text-[10px] text-slate-400 flex items-center gap-1 mt-0.5">
                              <Clock className="size-3" />
                              <span>Última sincronização: hoje às {lastSyncFormatted}</span>
                            </span>
                          </div>
                        </div>

                        <div className="flex items-center gap-1.5">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={isSyncing}
                            onClick={() => handleSyncNow(conn.id)}
                            className="h-7 text-[11px] gap-1 bg-slate-950/60 border-slate-700 text-slate-200 hover:text-emerald-300 hover:border-emerald-500/40 cursor-pointer rounded-lg"
                            title="Buscar lançamentos recentes do banco"
                          >
                            <RefreshCw className={`size-3 ${isSyncing ? 'animate-spin text-emerald-400' : ''}`} />
                            <span>{isSyncing ? 'Sincronizando...' : 'Sincronizar'}</span>
                          </Button>

                          <Button
                            variant="ghost"
                            size="icon-sm"
                            onClick={() => handleDeleteConnection(conn.id)}
                            className="size-7 text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg cursor-pointer transition-colors"
                            title="Desconectar banco"
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </div>
                      </div>

                      {/* Accounts / Invoices under this bank */}
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-2 border-t border-white/[0.04]">
                        {conn.accounts.map((acc) => (
                          <div
                            key={acc.id}
                            className="p-2.5 rounded-lg bg-slate-950/50 border border-slate-800/80 flex items-center justify-between text-xs"
                          >
                            <div>
                              <span className="text-[10px] text-slate-400 block font-medium">
                                {acc.type === 'checking' ? 'Conta Corrente' : 'Fatura de Cartão'}
                              </span>
                              <span className="text-[11px] font-mono text-slate-300">{acc.accountNumber}</span>
                            </div>

                            <div className="text-right">
                              <span className="text-[10px] text-slate-500 block">
                                {acc.type === 'checking' ? 'Saldo Atual' : 'Fatura Atual'}
                              </span>
                              <span
                                className={`text-xs font-bold ${
                                  acc.type === 'checking' ? 'text-emerald-400' : 'text-amber-400'
                                }`}
                              >
                                {acc.type === 'checking'
                                  ? formatBRL(acc.balance || 0)
                                  : formatBRL(acc.currentInvoice || 0)}
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}

        {/* Tab 2: Conectar Banco (Catálogo) */}
        {activeTab === 'connect' && (
          <div className="p-5 max-h-[62vh] overflow-y-auto space-y-4">
            {connectingBank ? (
              <div className="py-10 text-center flex flex-col items-center justify-center space-y-3">
                <div
                  className="size-16 rounded-2xl flex items-center justify-center text-white font-bold text-xl shadow-lg animate-pulse"
                  style={{ backgroundColor: connectingBank.color }}
                >
                  {connectingBank.name.slice(0, 2).toUpperCase()}
                </div>
                <div>
                  <h4 className="text-base font-bold text-white">
                    {connectStep === 'authorizing'
                      ? `Conectando com ${connectingBank.name}...`
                      : connectStep === 'syncing'
                        ? 'Sincronizando extratos e faturas recentes...'
                        : 'Conexão autorizada com sucesso!'}
                  </h4>
                  <p className="text-xs text-slate-400 max-w-sm mt-1">
                    {connectStep === 'authorizing'
                      ? 'Estabelecendo handshake seguro de consentimento OAuth sob o Open Finance Brasil...'
                      : connectStep === 'syncing'
                        ? 'Extraindo despesas e calculando o impacto no seu Saldo Livre...'
                        : 'Pronto! A conta e os lançamentos já estão disponíveis.'}
                  </p>
                </div>
                <div className="flex items-center gap-2 text-xs text-emerald-400">
                  <RefreshCw className="size-4 animate-spin" />
                  <span className="font-semibold">Processando protocolo seguro...</span>
                </div>
              </div>
            ) : (
              <>
                <div className="p-3 rounded-xl bg-slate-900/60 border border-slate-800 text-xs text-slate-300 flex items-start gap-2.5">
                  <Info className="size-4 text-emerald-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-white">Consentimento Seguro Open Finance:</span>
                    <p className="text-[11px] text-slate-400 mt-0.5">
                      Você será redirecionado para autorizar o acesso de leitura da sua conta. O Saldo Livre nunca tem acesso a senhas ou permissão de movimentação financeira.
                    </p>
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  {SUPPORTED_BANKS.map((bank) => (
                    <div
                      key={bank.code}
                      onClick={() => handleStartConnect(bank)}
                      className="p-3.5 rounded-xl bg-slate-900/60 border border-slate-800 hover:border-emerald-500/50 hover:bg-slate-900/90 transition-all cursor-pointer flex items-center justify-between group active:scale-[0.99]"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div
                          className="size-9 rounded-xl flex items-center justify-center text-white font-bold text-xs shadow-md shrink-0"
                          style={{ backgroundColor: bank.color }}
                        >
                          {bank.name.slice(0, 2).toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <h5 className="text-xs font-bold text-white group-hover:text-emerald-300 transition-colors truncate">
                            {bank.name}
                          </h5>
                          <span className="text-[10px] text-slate-400 block truncate">
                            {bank.description}
                          </span>
                        </div>
                      </div>

                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-7 text-[11px] text-slate-400 group-hover:text-emerald-300 group-hover:bg-emerald-500/10 px-2 rounded-lg cursor-pointer"
                      >
                        <span>Conectar</span>
                        <ArrowRight className="size-3 ml-1" />
                      </Button>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {/* Tab 3: Arquitetura & Configuração (Pluggy / BACEN) */}
        {activeTab === 'setup' && (
          <div className="p-5 max-h-[62vh] overflow-y-auto space-y-4 text-xs">
            {/* Architecture Explanation Card */}
            <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800 space-y-2.5">
              <div className="flex items-center gap-2 text-emerald-400 font-bold text-sm">
                <Sparkles className="size-4" />
                <span>Como funciona o Open Finance no Saldo Livre</span>
              </div>
              <p className="text-slate-300 leading-relaxed text-[11px]">
                No Brasil, a regulamentação do Banco Central (Resolução Conjunta nº 1/2020) permite que plataformas como o Saldo Livre utilizem infraestrutura de agregação bancária (**Pluggy** ou **Belvo**) para conectar contas sem exigir licença bancária direta.
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 pt-2">
                <div className="p-2.5 rounded-lg bg-slate-950/60 border border-slate-800">
                  <span className="text-emerald-400 font-bold block text-xs">1. Pluggy Connect</span>
                  <span className="text-[10px] text-slate-400">Widget seguro em React para login e consentimento bancário.</span>
                </div>
                <div className="p-2.5 rounded-lg bg-slate-950/60 border border-slate-800">
                  <span className="text-cyan-400 font-bold block text-xs">2. Webhooks & Sync</span>
                  <span className="text-[10px] text-slate-400">Atualização em tempo real das faturas e despesas no Firestore.</span>
                </div>
                <div className="p-2.5 rounded-lg bg-slate-950/60 border border-slate-800">
                  <span className="text-purple-400 font-bold block text-xs">3. Saldo Livre</span>
                  <span className="text-[10px] text-slate-400">Recálculo instantâneo do Limite Diário e projeção de faturas.</span>
                </div>
              </div>
            </div>

            {/* Developer Configuration Box */}
            <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <h5 className="text-xs font-bold text-white flex items-center gap-2">
                    <KeyRound className="size-3.5 text-amber-400" />
                    <span>Credenciais Pluggy.ai (Sandbox ou Produção)</span>
                  </h5>
                  <span className="text-[10px] text-slate-400">
                    Opcional: conecte suas chaves para habilitar o widget direto da Pluggy.
                  </span>
                </div>

                <a
                  href="https://pluggy.ai"
                  target="_blank"
                  rel="noreferrer"
                  className="text-[11px] text-emerald-400 hover:text-emerald-300 flex items-center gap-1 font-medium"
                >
                  <span>Criar conta Pluggy</span>
                  <ExternalLink className="size-3" />
                </a>
              </div>

              <div className="space-y-2">
                <div>
                  <label className="text-[10px] text-slate-400 font-medium block mb-1">
                    PLUGGY CLIENT ID
                  </label>
                  <input
                    type="text"
                    value={pluggyClientId}
                    onChange={(e) => setPluggyClientId(e.target.value)}
                    placeholder="Ex: a1b2c3d4-e5f6-7890-abcd-1234567890ab"
                    className="w-full h-8 px-2.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white focus:outline-none focus:border-emerald-500 font-mono"
                  />
                </div>

                <div>
                  <label className="text-[10px] text-slate-400 font-medium block mb-1">
                    PLUGGY CLIENT SECRET
                  </label>
                  <input
                    type="password"
                    value={pluggyClientSecret}
                    onChange={(e) => setPluggyClientSecret(e.target.value)}
                    placeholder="••••••••••••••••••••••••••••••••"
                    className="w-full h-8 px-2.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-white focus:outline-none focus:border-emerald-500 font-mono"
                  />
                </div>

                <Button
                  onClick={handleSavePluggy}
                  className="w-full h-8 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-lg mt-2 cursor-pointer shadow-xs"
                >
                  {pluggySaved ? '✓ Chaves Salvas com Sucesso!' : 'Salvar Chaves Pluggy'}
                </Button>
              </div>
            </div>
          </div>
        )}

        {/* Footer */}
        <div className="p-3.5 border-t border-white/[0.08] bg-slate-950/60 flex items-center justify-between text-xs text-slate-400">
          <div className="flex items-center gap-1 text-[11px]">
            <Zap className="size-3 text-amber-400" />
            <span>PDFs no Chat e Open Finance funcionam de forma complementar.</span>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onOpenChange(false)}
            className="text-xs text-slate-300 hover:text-white cursor-pointer"
          >
            Fechar
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
