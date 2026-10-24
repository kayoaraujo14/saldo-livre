import { useState, useEffect } from 'react'
import { FinanceProvider, useFinance } from '@/store/FinanceContext'
import { Header } from '@/components/Header'
import { KpiGrid } from '@/components/KpiGrid'
import { TransactionTable } from '@/components/TransactionTable'
import { GeminiChat } from '@/components/GeminiChat'
import { LoginScreen } from '@/components/LoginScreen'
import { SharedCardsModal } from '@/components/modals/SharedCardsModal'
import { RecurringBillsModal } from '@/components/modals/RecurringBillsModal'
import { ReceivablesModal } from '@/components/modals/ReceivablesModal'
import { ApiKeyModal } from '@/components/modals/ApiKeyModal'
import { TransactionModal } from '@/components/modals/TransactionModal'
import { FutureProjectionModal } from '@/components/modals/FutureProjectionModal'
import { FriendsModal } from '@/components/modals/FriendsModal'
import { RecentActivityModal } from '@/components/modals/RecentActivityModal'
import { InvestmentsModal } from '@/components/modals/InvestmentsModal'
import { CardInvoicesModal } from '@/components/modals/CardInvoicesModal'
import { ScrollToTop } from '@/components/ui/ScrollToTop'
import { MobileBottomNav, type MobileTab } from '@/components/mobile/MobileBottomNav'
import { MobileMenuSheet } from '@/components/mobile/MobileMenuSheet'
import { MobileTransactionCard } from '@/components/mobile/MobileTransactionCard'
import { Button } from '@/components/ui/button'
import { Clock, ArrowRight, ReceiptText } from 'lucide-react'
import type { Transaction } from '@/types'

type DesktopViewMode = 'chat_focus' | 'chat_full' | 'table_full'

function MainDashboard() {
  const {
    setMonth,
    pendingChargeGroups,
    pendingFriendRequests,
    monthTransactions,
    cards,
    me,
    cycleStatus,
    deleteTransaction,
    generateBills,
  } = useFinance()

  const [cardsModalOpen, setCardsModalOpen] = useState(false)
  const [recurringModalOpen, setRecurringModalOpen] = useState(false)
  const [receivablesModalOpen, setReceivablesModalOpen] = useState(false)
  const [apiKeyModalOpen, setApiKeyModalOpen] = useState(false)
  const [txModalOpen, setTxModalOpen] = useState(false)
  const [projectionModalOpen, setProjectionModalOpen] = useState(false)
  const [friendsModalOpen, setFriendsModalOpen] = useState(false)
  const [recentActivityModalOpen, setRecentActivityModalOpen] = useState(false)
  const [investmentsModalOpen, setInvestmentsModalOpen] = useState(false)
  const [invoicesModalOpen, setInvoicesModalOpen] = useState(false)
  const [editingTx, setEditingTx] = useState<Transaction | null>(null)
  const [generatingBills, setGeneratingBills] = useState(false)

  // Mobile navigation tab state: 'home' | 'transactions' | 'chat' | 'menu'
  const [mobileTab, setMobileTab] = useState<MobileTab>('home')

  // Desktop view layout mode: 'chat_focus' (60/40) | 'equal' (50/50) | 'chat_full' (100%) | 'table_full' (100%)
  const [desktopView, setDesktopView] = useState<DesktopViewMode>('chat_focus')

  // ESC shortcut to return from full-screen view mode
  useEffect(() => {
    if (desktopView === 'chat_focus') return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setDesktopView('chat_focus')
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
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
    setEditingTx(tx)
    setTxModalOpen(true)
  }

  const handleGenerate = async () => {
    setGeneratingBills(true)
    try {
      await generateBills()
    } finally {
      setGeneratingBills(false)
    }
  }

  const totalPendingNotifications = pendingChargeGroups.length + pendingFriendRequests.length
  const currentUid = me?.uid ?? ''

  // Recent transactions preview for mobile home tab
  const recentTransactions = monthTransactions.slice(0, 4)

  return (
    <div className="flex flex-col min-h-[100dvh] bg-[#090d16] text-slate-100 font-sans selection:bg-emerald-500/20 selection:text-emerald-300 pb-[max(5rem,calc(env(safe-area-inset-bottom,0px)+4.75rem))] lg:pb-6 overflow-x-hidden">
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
        onOpenInvoices={() => setInvoicesModalOpen(true)}
      />

      {/* 2. Main Content Area */}
      <main className="flex-1 max-w-[1720px] w-full mx-auto px-3 sm:px-6 py-3.5 sm:py-4 space-y-4">
        {/* Top Notification Banner if there are pending actions */}
        {totalPendingNotifications > 0 && desktopView === 'chat_focus' && (
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

        {/* ============================================================== */}
        {/* DESKTOP LAYOUT (Screen >= 1024px / lg)                          */}
        {/* ============================================================== */}
        <div className="hidden lg:block space-y-3">
          {/* KPI Dashboard (Compact Metric Ribbon) - Oculto no modo expandido */}
          {desktopView === 'chat_focus' && (
            <KpiGrid
              onOpenReceivables={() => setReceivablesModalOpen(true)}
            />
          )}

          {/* Flexible Main Workspace Grid (Height optimized with compact KPIs) */}
          <div className="grid grid-cols-12 gap-3.5 h-[calc(100vh-160px)] max-h-[calc(100vh-160px)] min-h-[540px]">
            {/* Left Panel: Financial Table */}
            <section
              className={`${
                desktopView === 'table_full'
                  ? 'fixed top-[61px] sm:top-[65px] inset-x-0 bottom-0 z-30 w-full bg-[#090d16] p-3 sm:px-6 sm:pb-3 sm:pt-2 flex flex-col animate-in fade-in duration-200'
                  : desktopView === 'chat_full'
                    ? 'hidden'
                    : 'col-span-5 xl:col-span-5 h-full min-h-0 flex flex-col transition-all duration-200'
              }`}
            >
              <TransactionTable
                onEditTx={handleEditTx}
                onToggleExpand={() =>
                  setDesktopView((prev) => (prev === 'table_full' ? 'chat_focus' : 'table_full'))
                }
                isExpanded={desktopView === 'table_full'}
              />
            </section>

            {/* Right Panel: Gemini AI Agent Chat */}
            <section
              className={`${
                desktopView === 'chat_full'
                  ? 'fixed top-[61px] sm:top-[65px] inset-x-0 bottom-0 z-30 w-full bg-[#090d16] p-3 sm:px-6 sm:pb-3 sm:pt-2 flex flex-col animate-in fade-in duration-200'
                  : desktopView === 'table_full'
                    ? 'hidden'
                    : 'col-span-7 xl:col-span-7 h-full min-h-0 flex flex-col transition-all duration-200'
              }`}
            >
              <GeminiChat
                onOpenApiKey={() => setApiKeyModalOpen(true)}
                onToggleExpand={() =>
                  setDesktopView((prev) => (prev === 'chat_full' ? 'chat_focus' : 'chat_full'))
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
              />

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
                  <p className="text-xs text-slate-500 text-center py-6">
                    Nenhum lançamento registrado neste mês.
                  </p>
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
            <div className="h-[calc(100dvh-130px)] min-h-[460px] flex flex-col animate-in fade-in duration-200">
              <TransactionTable
                onEditTx={handleEditTx}
              />
            </div>
          )}

          {/* View 3: Mobile COPILOTO IA (Full-Height Conversational Assistant) */}
          {mobileTab === 'chat' && (
            <div className="h-[calc(100dvh-155px)] min-h-[460px] flex flex-col animate-in fade-in duration-200">
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
              onOpenInvoices={() => setInvoicesModalOpen(true)}
              onGenerateBills={handleGenerate}
              generating={generatingBills}
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

      {/* Modals & Dialogs (Unified across Desktop and Mobile) */}
      <SharedCardsModal open={cardsModalOpen} onOpenChange={setCardsModalOpen} />
      <RecurringBillsModal open={recurringModalOpen} onOpenChange={setRecurringModalOpen} />
      <ReceivablesModal open={receivablesModalOpen} onOpenChange={setReceivablesModalOpen} />
      <ApiKeyModal open={apiKeyModalOpen} onOpenChange={setApiKeyModalOpen} />
      <TransactionModal
        open={txModalOpen}
        onOpenChange={setTxModalOpen}
        editingTx={editingTx}
      />
      <FutureProjectionModal
        open={projectionModalOpen}
        onOpenChange={setProjectionModalOpen}
        onSelectMonth={setMonth}
      />
      <FriendsModal
        open={friendsModalOpen}
        onOpenChange={setFriendsModalOpen}
      />
      <RecentActivityModal
        open={recentActivityModalOpen}
        onOpenChange={setRecentActivityModalOpen}
        onEditTx={handleEditTx}
      />
      <InvestmentsModal
        open={investmentsModalOpen}
        onOpenChange={setInvestmentsModalOpen}
        onOpenNewTx={handleOpenNewTx}
      />
      <CardInvoicesModal
        open={invoicesModalOpen}
        onOpenChange={setInvoicesModalOpen}
        onEditTx={handleEditTx}
      />
    </div>
  )
}

function MainContent() {
  const { isAuthenticated, authLoading } = useFinance()

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
    return <LoginScreen />
  }

  return <MainDashboard />
}

export default function App() {
  return (
    <FinanceProvider>
      <MainContent />
    </FinanceProvider>
  )
}
