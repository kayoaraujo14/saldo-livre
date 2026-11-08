import { useState, useEffect, useMemo, lazy, Suspense } from 'react'
import { FinanceProvider, useFinance } from '@/store/FinanceContext'
import { Header } from '@/components/Header'
import { KpiGrid } from '@/components/KpiGrid'
import { CategoryBudgetsCard } from '@/components/CategoryBudgetsCard'
import { TransactionTable } from '@/components/TransactionTable'
import { GeminiChat } from '@/components/GeminiChat'
import { ScrollToTop } from '@/components/ui/ScrollToTop'
import { MobileBottomNav, type MobileTab } from '@/components/mobile/MobileBottomNav'
import { MobileMenuSheet } from '@/components/mobile/MobileMenuSheet'
import { MobileTransactionCard } from '@/components/mobile/MobileTransactionCard'
import { AutoLaunchNoticeBanner } from '@/components/AutoLaunchNoticeBanner'
import { NetworkStatusIndicator } from '@/components/NetworkStatusIndicator'
import { Button } from '@/components/ui/button'
import { Clock, ArrowRight, ReceiptText, Sparkles, Plus } from 'lucide-react'
import { EmptyState } from '@/components/ui/EmptyState'
import type { Transaction } from '@/types'
import { consolidateMonthTransactions } from '@/lib/invoices'
import { formatBRL, monthLabel } from '@/lib/utils'

// Lazy-loaded secondary modals & screens to optimize initial bundle size
const LoginScreen = lazy(() => import('@/components/LoginScreen').then((m) => ({ default: m.LoginScreen })))
const SharedCardsModal = lazy(() => import('@/components/modals/SharedCardsModal').then((m) => ({ default: m.SharedCardsModal })))
const RecurringBillsModal = lazy(() => import('@/components/modals/RecurringBillsModal').then((m) => ({ default: m.RecurringBillsModal })))
const ReceivablesModal = lazy(() => import('@/components/modals/ReceivablesModal').then((m) => ({ default: m.ReceivablesModal })))
const ApiKeyModal = lazy(() => import('@/components/modals/ApiKeyModal').then((m) => ({ default: m.ApiKeyModal })))
const TransactionModal = lazy(() => import('@/components/modals/TransactionModal').then((m) => ({ default: m.TransactionModal })))
const FutureProjectionModal = lazy(() => import('@/components/modals/FutureProjectionModal').then((m) => ({ default: m.FutureProjectionModal })))
const FriendsModal = lazy(() => import('@/components/modals/FriendsModal').then((m) => ({ default: m.FriendsModal })))
const RecentActivityModal = lazy(() => import('@/components/modals/RecentActivityModal').then((m) => ({ default: m.RecentActivityModal })))
const InvestmentsModal = lazy(() => import('@/components/modals/InvestmentsModal').then((m) => ({ default: m.InvestmentsModal })))
const FinancialHealthModal = lazy(() => import('@/components/modals/FinancialHealthModal').then((m) => ({ default: m.FinancialHealthModal })))
const ManageBudgetsModal = lazy(() => import('@/components/modals/ManageBudgetsModal').then((m) => ({ default: m.ManageBudgetsModal })))
const CardInvoicesModal = lazy(() => import('@/components/modals/CardInvoicesModal').then((m) => ({ default: m.CardInvoicesModal })))
const MonthClosureModal = lazy(() => import('@/components/modals/MonthClosureModal').then((m) => ({ default: m.MonthClosureModal })))

type DesktopViewMode = 'table_focus' | 'balanced' | 'chat_focus' | 'table_full' | 'chat_full'

function getInitialPwaAction(): string | null {
  if (typeof window === 'undefined') return null
  const params = new URLSearchParams(window.location.search)
  const action = params.get('action')
  if (action) {
    const cleanUrl = window.location.pathname + window.location.hash
    window.history.replaceState({}, '', cleanUrl)
  }
  return action
}

function MainDashboard() {
  const [initialAction] = useState(() => getInitialPwaAction())

  // Ensure scroll is at the top upon mounting the dashboard
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [])

  const {
    month,
    setMonth,
    pendingChargeGroups,
    pendingFriendRequests,
    monthTransactions,
    transactions,
    cards,
    me,
    cycleStatus,
    deleteTransaction,
    summary,
    closureModalOpen,
    setClosureModalOpen,
    openMonthClosureModal,
  } = useFinance()

  const [cardsModalOpen, setCardsModalOpen] = useState(false)
  const [recurringModalOpen, setRecurringModalOpen] = useState(false)
  const [receivablesModalOpen, setReceivablesModalOpen] = useState(false)
  const [apiKeyModalOpen, setApiKeyModalOpen] = useState(false)
  const [txModalOpen, setTxModalOpen] = useState(() => initialAction === 'new_expense')
  const [projectionModalOpen, setProjectionModalOpen] = useState(false)
  const [friendsModalOpen, setFriendsModalOpen] = useState(false)
  const [recentActivityModalOpen, setRecentActivityModalOpen] = useState(false)
  const [investmentsModalOpen, setInvestmentsModalOpen] = useState(false)
  const [financialHealthModalOpen, setFinancialHealthModalOpen] = useState(false)
  const [budgetsModalOpen, setBudgetsModalOpen] = useState(false)
  const [invoicesModalOpen, setInvoicesModalOpen] = useState(() => initialAction === 'invoices')
  const [invoicesInitialCardId, setInvoicesInitialCardId] = useState<string | null>(null)
  const [editingTx, setEditingTx] = useState<Transaction | null>(null)

  // Mobile navigation tab state: 'home' | 'transactions' | 'chat' | 'menu'
  const [mobileTab, setMobileTab] = useState<MobileTab>(() => (initialAction === 'chat' ? 'chat' : 'home'))

  // Desktop view layout mode: 'table_focus' (70/30) | 'balanced' (50/50) | 'chat_focus' (30/70) | 'table_full' (100%) | 'chat_full' (100%)
  const [desktopView, setDesktopView] = useState<DesktopViewMode>(() =>
    initialAction === 'chat' ? 'chat_full' : 'table_focus'
  )

  // ESC shortcut to return from full-screen view mode
  useEffect(() => {
    if (desktopView === 'table_focus' || desktopView === 'balanced' || desktopView === 'chat_focus') return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setDesktopView('table_focus')
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [desktopView])

  useEffect(() => {
    const onAskCopilot = () => {
      if (desktopView === 'table_full') {
        setDesktopView('table_focus')
      }
      if (typeof window !== 'undefined' && window.innerWidth < 1024) {
        setMobileTab('chat')
      }
    }
    window.addEventListener('saldo-livre:ask-copilot', onAskCopilot)
    return () => window.removeEventListener('saldo-livre:ask-copilot', onAskCopilot)
  }, [desktopView])

  const handleSelectMobileTab = (tab: MobileTab) => {
    setMobileTab(tab)
    window.scrollTo({ top: 0, behavior: 'instant' })
  }

  const handleOpenNewTx = () => {
    setEditingTx(null)
    setTxModalOpen(true)
  }

  const handleEditTx = (tx: Transaction) => {
    if (tx.id.startsWith('invoice_') && tx.cardId) {
      setInvoicesInitialCardId(tx.cardId)
      setInvoicesModalOpen(true)
      return
    }
    setEditingTx(tx)
    setTxModalOpen(true)
  }

  const isExpandedView = desktopView === 'table_full' || desktopView === 'chat_full'

  const totalPendingNotifications = pendingChargeGroups.length + pendingFriendRequests.length
  const currentUid = me?.uid ?? ''

  // Recent transactions preview for mobile home tab (consolidated)
  const recentTransactions = useMemo(() => {
    const list = consolidateMonthTransactions(monthTransactions, transactions, cards, currentUid, month, true)
    return list.slice(0, 4)
  }, [monthTransactions, transactions, cards, currentUid, month])

  return (
    <div className={`flex flex-col min-h-[100dvh] bg-[#090d16] text-slate-100 font-sans selection:bg-emerald-500/20 selection:text-emerald-300 pb-[max(6.5rem,calc(env(safe-area-inset-bottom,0px)+5.75rem))] lg:pb-6 overflow-x-hidden ${isExpandedView ? 'lg:h-[100dvh] lg:overflow-hidden' : ''}`}>
      {/* 1. Header (Sticky & Native Safe-Top Padded) */}
      <Header
        onOpenNewTx={handleOpenNewTx}
        onOpenCards={() => setCardsModalOpen(true)}
        onOpenRecurring={() => setRecurringModalOpen(true)}
        onOpenApiKey={() => setApiKeyModalOpen(true)}
        onOpenProjection={() => setProjectionModalOpen(true)}
        onOpenFriends={() => setFriendsModalOpen(true)}
        onOpenRecentActivity={() => setRecentActivityModalOpen(true)}
        onOpenInvestments={() => setInvestmentsModalOpen(true)}
        onOpenBudgets={() => setBudgetsModalOpen(true)}
        onOpenInvoices={() => {
          setInvoicesInitialCardId(null)
          setInvoicesModalOpen(true)
        }}
      />

      {/* 2. Main Content Area */}
      <main className={`flex-1 min-h-0 max-w-[1720px] w-full mx-auto px-3 sm:px-6 py-3.5 sm:py-4 ${isExpandedView ? 'lg:flex lg:flex-col' : 'space-y-4'}`}>
        {/* Offline / Reconnected Status Banner */}
        <NetworkStatusIndicator />

        
        {totalPendingNotifications > 0 && !isExpandedView && (
          <div className="p-3 sm:p-3.5 rounded-2xl bg-gradient-to-r from-amber-500/15 via-violet-500/15 to-transparent border border-amber-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs shadow-lg animate-in fade-in slide-in-from-top-1">
            <div className="flex items-center gap-2.5 text-amber-200">
              <span className="size-2.5 rounded-full bg-amber-400 animate-ping shrink-0" />
              <span>
                {pendingChargeGroups.length > 0 ? (
                  <>
                    Você possui <strong>{pendingChargeGroups.length} despesa(s) no cartão</strong> compartilhada(s) por amigos aguardando sua aprovação.
                  </>
                ) : (
                  <>
                    Você possui <strong>{pendingFriendRequests.length} convite(s) de amizade</strong> pendente(s).
                  </>
                )}
              </span>
            </div>
            <Button
              size="sm"
              onClick={() => setFriendsModalOpen(true)}
              className="bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold h-7 text-xs px-3 self-end sm:self-auto cursor-pointer rounded-lg shadow-sm"
            >
              <Clock className="size-3 mr-1" />
              Revisar e Aceitar
            </Button>
          </div>
        )}

        {/* Banner de Sobra Pendente do Mês Anterior */}
        {summary?.pendingClosureSurplus && summary.pendingClosureSurplus > 0 && !isExpandedView && (
          <div className="p-3 sm:p-3.5 rounded-2xl bg-gradient-to-r from-emerald-500/15 via-teal-500/15 to-transparent border border-emerald-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs shadow-lg animate-in fade-in slide-in-from-top-1">
            <div className="flex items-center gap-2.5 text-emerald-200">
              <span className="size-2.5 rounded-full bg-emerald-400 animate-ping shrink-0" />
              <span>
                Em <strong>{monthLabel(summary.pendingClosureMonth || '')}</strong> sobraram <strong>{formatBRL(summary.pendingClosureSurplus)}</strong>! Escolha se deseja manter na conta corrente ou guardar na sua caixinha.
              </span>
            </div>
            <Button
              size="sm"
              onClick={openMonthClosureModal}
              className="bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold h-7 text-xs px-3 self-end sm:self-auto cursor-pointer rounded-lg shadow-sm"
            >
              <Sparkles className="size-3 mr-1" />
              Destinar Sobra
            </Button>
          </div>
        )}

        {/* Banner de Lançamento Automático / Próximos Vencimentos (Desktop) */}
        {!isExpandedView && (
          <div className="hidden lg:block">
            <AutoLaunchNoticeBanner onGoToTransactions={() => setDesktopView('table_full')} />
          </div>
        )}

        {/* ============================================================== */}
        {/* DESKTOP LAYOUT (Screen >= 1024px / lg)                          */}
        {/* ============================================================== */}
        <div className={isExpandedView ? 'hidden lg:flex lg:flex-col flex-1 min-h-0' : 'hidden lg:block space-y-3'}>
          {/* KPI Dashboard (Compact Metric Ribbon) - Oculto no modo expandido */}
          {!isExpandedView && (
            <div className="space-y-3">
              <KpiGrid
                onOpenReceivables={() => setReceivablesModalOpen(true)}
                onOpenInvestments={() => setInvestmentsModalOpen(true)}
                onOpenFinancialHealth={() => setFinancialHealthModalOpen(true)}
              />
              <CategoryBudgetsCard />
            </div>
          )}

          {/* Desktop Workspace Mode Selector Bar */}
          {!isExpandedView && (
            <div className="hidden lg:flex items-center justify-between pt-1 pb-0.5 px-0.5 text-xs">
              <div className="flex items-center gap-2">
                <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                  <ReceiptText className="size-3.5 text-emerald-400" />
                  Painel de Controle Financeiro
                </span>
              </div>
              <div className="flex items-center gap-1 bg-slate-950/70 p-1 rounded-xl border border-white/[0.08] shadow-xs">
                <button
                  type="button"
                  onClick={() => setDesktopView('table_focus')}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                    desktopView === 'table_focus'
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shadow-xs'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                  }`}
                  title="Foco no Extrato (70% Tabela, 30% Copiloto)"
                >
                  <ReceiptText className="size-3" />
                  <span>Extrato Ampliado</span>
                </button>
                <button
                  type="button"
                  onClick={() => setDesktopView('balanced')}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                    desktopView === 'balanced'
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shadow-xs'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                  }`}
                  title="Divisão Equilibrada (50% Tabela, 50% Copiloto)"
                >
                  <span>Equilibrado (50/50)</span>
                </button>
                <button
                  type="button"
                  onClick={() => setDesktopView('chat_focus')}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                    desktopView === 'chat_focus'
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shadow-xs'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                  }`}
                  title="Foco no Copiloto (30% Tabela, 70% Copiloto)"
                >
                  <Sparkles className="size-3" />
                  <span>Foco na IA</span>
                </button>
              </div>
            </div>
          )}

          {/* Flexible Main Workspace Grid (Height optimized with compact KPIs) */}
          <div
            className={
              isExpandedView
                ? 'grid grid-cols-12 flex-1 min-h-0'
                : 'grid grid-cols-12 gap-3.5 h-[calc(100vh-275px)] min-h-[520px]'
            }
          >
            {/* Left Panel: Financial Table */}
            <section
              className={`${
                desktopView === 'table_full'
                  ? 'col-span-12 h-full min-h-0 flex flex-col animate-in fade-in duration-200'
                  : desktopView === 'chat_full'
                    ? 'hidden'
                    : desktopView === 'table_focus'
                      ? 'col-span-12 lg:col-span-8 xl:col-span-8 h-full min-h-0 flex flex-col transition-all duration-200'
                      : desktopView === 'balanced'
                        ? 'col-span-12 lg:col-span-6 xl:col-span-6 h-full min-h-0 flex flex-col transition-all duration-200'
                        : 'col-span-12 lg:col-span-5 xl:col-span-5 h-full min-h-0 flex flex-col transition-all duration-200'
              }`}
            >
              <TransactionTable
                onEditTx={handleEditTx}
                onNewTx={() => setTxModalOpen(true)}
                onOpenInvoices={(cardId) => {
                  setInvoicesInitialCardId(cardId || null)
                  setInvoicesModalOpen(true)
                }}
                onToggleExpand={() =>
                  setDesktopView((prev) => (prev === 'table_full' ? 'table_focus' : 'table_full'))
                }
                isExpanded={desktopView === 'table_full'}
              />
            </section>

            {/* Right Panel: Gemini AI Agent Chat */}
            <section
              className={`${
                desktopView === 'chat_full'
                  ? 'col-span-12 h-full min-h-0 flex flex-col animate-in fade-in duration-200'
                  : desktopView === 'table_full'
                    ? 'hidden'
                    : desktopView === 'table_focus'
                      ? 'col-span-12 lg:col-span-4 xl:col-span-4 h-full min-h-0 flex flex-col transition-all duration-200'
                      : desktopView === 'balanced'
                        ? 'col-span-12 lg:col-span-6 xl:col-span-6 h-full min-h-0 flex flex-col transition-all duration-200'
                        : 'col-span-12 lg:col-span-7 xl:col-span-7 h-full min-h-0 flex flex-col transition-all duration-200'
              }`}
            >
              <GeminiChat
                onOpenApiKey={() => setApiKeyModalOpen(true)}
                onToggleExpand={() =>
                  setDesktopView((prev) => (prev === 'chat_full' ? 'table_focus' : 'chat_full'))
                }
                isExpanded={desktopView === 'chat_full'}
              />
            </section>
          </div>
        </div>

        {/* ============================================================== */}
        {/* DEDICATED MOBILE LAYOUT (Screen < 1024px / lg:hidden)           */}
        {/* ============================================================== */}
        <div className="lg:hidden space-y-3.5">
          {/* View 1: Mobile INÍCIO (Dashboard Hero, KPIs & Quick Preview) */}
          {mobileTab === 'home' && (
            <div className="space-y-3.5 animate-in fade-in duration-200">


              {/* KPI Grid */}
              <KpiGrid
                onOpenReceivables={() => setReceivablesModalOpen(true)}
                onOpenInvestments={() => setInvestmentsModalOpen(true)}
                onOpenFinancialHealth={() => setFinancialHealthModalOpen(true)}
              />

              {/* Envelopes Orçamentários */}
              <CategoryBudgetsCard />

              {/* Banner de Lançamento Automático / Próximos Vencimentos (Mobile) */}
              <AutoLaunchNoticeBanner onGoToTransactions={() => handleSelectMobileTab('transactions')} />

              {/* Recent Transactions Preview Section (Directly below KPIs, with no duplicate button clutter!) */}
              <div className="glass-card rounded-2xl p-4 border border-white/[0.08] space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="font-bold text-sm text-white flex items-center gap-1.5">
                    <ReceiptText className="size-4 text-emerald-400" />
                    Últimos Lançamentos
                  </h3>
                  <button
                    type="button"
                    onClick={() => handleSelectMobileTab('transactions')}
                    className="text-xs text-emerald-400 font-semibold hover:underline flex items-center gap-0.5 cursor-pointer"
                  >
                    Ver todos <ArrowRight className="size-3" />
                  </button>
                </div>

                {recentTransactions.length === 0 ? (
                  <EmptyState
                    compact
                    icon={ReceiptText}
                    title="Nenhum lançamento recente"
                    description="Cadastre despesas ou receitas para visualizar seus lançamentos aqui."
                    action={{
                      label: 'Novo Lançamento',
                      onClick: () => setTxModalOpen(true),
                      icon: Plus,
                    }}
                  />
                ) : (
                  <div className="space-y-2">
                    {recentTransactions.map((tx) => (
                      <MobileTransactionCard
                        key={tx.id}
                        tx={tx}
                        currentUid={currentUid}
                        allTransactions={monthTransactions}
                        cards={cards}
                        onEdit={handleEditTx}
                        onDelete={(targetTx) => deleteTransaction(targetTx.id)}
                        onCycleStatus={cycleStatus}
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* View 2: Mobile EXTRATO (Full Transaction Feed with Mobile Cards) */}
          {mobileTab === 'transactions' && (
            <div className="h-[calc(100dvh-130px-env(safe-area-inset-bottom,0px))] min-h-[460px] flex flex-col animate-in fade-in duration-200">
              <TransactionTable
                onEditTx={handleEditTx}
                onNewTx={() => setTxModalOpen(true)}
                onOpenInvoices={(cardId) => {
                  setInvoicesInitialCardId(cardId || null)
                  setInvoicesModalOpen(true)
                }}
              />
            </div>
          )}

          {/* View 3: Mobile COPILOTO IA (Full-Height Conversational Assistant) */}
          {mobileTab === 'chat' && (
            <div className="h-[calc(100dvh-155px-env(safe-area-inset-bottom,0px))] min-h-[460px] flex flex-col animate-in fade-in duration-200">
              <GeminiChat
                onOpenApiKey={() => setApiKeyModalOpen(true)}
              />
            </div>
          )}

          {/* View 4: Mobile MENU & TOOLS (Quick Touch Access to Modals & Settings) */}
          {mobileTab === 'menu' && (
            <MobileMenuSheet
              onOpenCards={() => setCardsModalOpen(true)}
              onOpenRecurring={() => setRecurringModalOpen(true)}
              onOpenApiKey={() => setApiKeyModalOpen(true)}
              onOpenProjection={() => setProjectionModalOpen(true)}
              onOpenFriends={() => setFriendsModalOpen(true)}
              onOpenRecentActivity={() => setRecentActivityModalOpen(true)}
              onOpenInvestments={() => setInvestmentsModalOpen(true)}
              onOpenInvoices={() => {
                setInvoicesInitialCardId(null)
                setInvoicesModalOpen(true)
              }}
            />
          )}
        </div>
      </main>

      {/* Mobile Bottom Navigation Dock */}
      <MobileBottomNav
        currentTab={mobileTab}
        onSelectTab={handleSelectMobileTab}
        unreadAlertsCount={totalPendingNotifications}
        txCount={monthTransactions.length}
      />

      {/* Floating Scroll to Top Button */}
      <ScrollToTop />

      {/* Modals & Dialogs (Unified across Desktop and Mobile - Lazy loaded on demand) */}
      <Suspense fallback={null}>
        {cardsModalOpen && <SharedCardsModal open={cardsModalOpen} onOpenChange={setCardsModalOpen} />}
        {recurringModalOpen && <RecurringBillsModal open={recurringModalOpen} onOpenChange={setRecurringModalOpen} />}
        {receivablesModalOpen && <ReceivablesModal open={receivablesModalOpen} onOpenChange={setReceivablesModalOpen} />}
        {apiKeyModalOpen && <ApiKeyModal open={apiKeyModalOpen} onOpenChange={setApiKeyModalOpen} />}
        {txModalOpen && (
          <TransactionModal
            open={txModalOpen}
            onOpenChange={(open) => {
              setTxModalOpen(open)
              if (!open) {
                setEditingTx(null)
              }
            }}
            editingTx={editingTx}
          />
        )}
        {projectionModalOpen && (
          <FutureProjectionModal
            open={projectionModalOpen}
            onOpenChange={setProjectionModalOpen}
            onSelectMonth={setMonth}
          />
        )}
        {friendsModalOpen && (
          <FriendsModal
            open={friendsModalOpen}
            onOpenChange={setFriendsModalOpen}
          />
        )}
        {recentActivityModalOpen && (
          <RecentActivityModal
            open={recentActivityModalOpen}
            onOpenChange={setRecentActivityModalOpen}
            onEditTx={handleEditTx}
          />
        )}
        {investmentsModalOpen && (
          <InvestmentsModal
            open={investmentsModalOpen}
            onOpenChange={setInvestmentsModalOpen}
            onOpenNewTx={handleOpenNewTx}
          />
        )}
        {financialHealthModalOpen && (
          <FinancialHealthModal
            open={financialHealthModalOpen}
            onOpenChange={setFinancialHealthModalOpen}
            onOpenInvestments={() => setInvestmentsModalOpen(true)}
            onOpenBudgets={() => setBudgetsModalOpen(true)}
          />
        )}
        {budgetsModalOpen && (
          <ManageBudgetsModal
            open={budgetsModalOpen}
            onOpenChange={setBudgetsModalOpen}
          />
        )}
        {invoicesModalOpen && (
          <CardInvoicesModal
            open={invoicesModalOpen}
            onOpenChange={(open) => {
              setInvoicesModalOpen(open)
              if (!open) setInvoicesInitialCardId(null)
            }}
            initialCardId={invoicesInitialCardId}
            onEditTx={handleEditTx}
          />
        )}
        {closureModalOpen && (
          <MonthClosureModal
            open={closureModalOpen}
            onOpenChange={setClosureModalOpen}
          />
        )}
      </Suspense>
    </div>
  )
}

function MainContent() {
  const { isAuthenticated, authLoading } = useFinance()

  useEffect(() => {
    if (isAuthenticated) {
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
    }
  }, [isAuthenticated])

  if (authLoading) {
    return (
      <div className="min-h-screen bg-[#090d16] flex items-center justify-center text-slate-400">
        <div className="flex items-center gap-2 text-sm">
          <span className="size-3 rounded-full bg-emerald-400 animate-ping" />
          <span>Carregando sua sessão segura...</span>
        </div>
      </div>
    )
  }

  if (!isAuthenticated) {
    return (
      <Suspense fallback={<div className="min-h-screen bg-[#090d16]" />}>
        <LoginScreen />
      </Suspense>
    )
  }

  return <MainDashboard />
}

export default function App() {
  useEffect(() => {
    if (typeof window !== 'undefined' && 'scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual'
    }
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [])

  return (
    <FinanceProvider>
      <MainContent />
    </FinanceProvider>
  )
}
