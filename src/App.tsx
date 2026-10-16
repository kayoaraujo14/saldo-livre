import { useState } from 'react'
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
import { Button } from '@/components/ui/button'
import { Clock } from 'lucide-react'
import type { Transaction } from '@/types'

function MainDashboard() {
  const { setMonth, pendingChargeGroups, pendingFriendRequests } = useFinance()
  const [cardsModalOpen, setCardsModalOpen] = useState(false)
  const [recurringModalOpen, setRecurringModalOpen] = useState(false)
  const [receivablesModalOpen, setReceivablesModalOpen] = useState(false)
  const [apiKeyModalOpen, setApiKeyModalOpen] = useState(false)
  const [txModalOpen, setTxModalOpen] = useState(false)
  const [projectionModalOpen, setProjectionModalOpen] = useState(false)
  const [friendsModalOpen, setFriendsModalOpen] = useState(false)
  const [editingTx, setEditingTx] = useState<Transaction | null>(null)

  const handleOpenNewTx = () => {
    setEditingTx(null)
    setTxModalOpen(true)
  }

  const handleEditTx = (tx: Transaction) => {
    setEditingTx(tx)
    setTxModalOpen(true)
  }

  const totalPendingNotifications = pendingChargeGroups.length + pendingFriendRequests.length

  return (
    <div className="flex flex-col min-h-screen bg-[#090d16] text-slate-100 font-sans selection:bg-emerald-500/20 selection:text-emerald-300">
      {/* 1. Header */}
      <Header
        onOpenCards={() => setCardsModalOpen(true)}
        onOpenRecurring={() => setRecurringModalOpen(true)}
        onOpenApiKey={() => setApiKeyModalOpen(true)}
        onOpenProjection={() => setProjectionModalOpen(true)}
        onOpenFriends={() => setFriendsModalOpen(true)}
      />

      {/* 2. Main Content Area */}
      <main className="flex-1 max-w-[1720px] w-full mx-auto px-4 sm:px-6 py-4 space-y-4">
        {/* Top Notification Banner if there are pending actions */}
        {totalPendingNotifications > 0 && (
          <div className="p-3 rounded-xl bg-gradient-to-r from-amber-500/15 to-violet-500/15 border border-amber-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs shadow-lg animate-in fade-in slide-in-from-top-1">
            <div className="flex items-center gap-2.5 text-amber-200">
              <span className="size-2.5 rounded-full bg-amber-400 animate-ping shrink-0" />
              <span>
                {pendingChargeGroups.length > 0 ? (
                  <>
                    Você possui <strong>{pendingChargeGroups.length} despesa(s) no cartão</strong> enviada(s) por amigos aguardando seu aceite de comprometimento.
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
              className="bg-amber-500 hover:bg-amber-400 text-slate-950 font-semibold h-7 text-xs px-3 self-end sm:self-auto cursor-pointer"
            >
              <Clock className="size-3 mr-1" />
              Revisar e Aceitar
            </Button>
          </div>
        )}

        {/* KPI Grid (Top Section) */}
        <KpiGrid
          onOpenReceivables={() => setReceivablesModalOpen(true)}
          onOpenProjection={() => setProjectionModalOpen(true)}
          onOpenRecurring={() => setRecurringModalOpen(true)}
        />

        {/* Responsive Split-Screen: 65% Table / 35% Gemini Chat */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 lg:h-[calc(100vh-275px)] min-h-[640px]">
          {/* Left Panel: Financial Table (65% width) */}
          <section className="lg:col-span-7 xl:col-span-8 h-full flex flex-col">
            <TransactionTable
              onOpenNewTx={handleOpenNewTx}
              onEditTx={handleEditTx}
            />
          </section>

          {/* Right Panel: Gemini AI Agent Chat (35% width) */}
          <section className="lg:col-span-5 xl:col-span-4 h-full flex flex-col">
            <GeminiChat onOpenApiKey={() => setApiKeyModalOpen(true)} />
          </section>
        </div>
      </main>

      {/* Modals & Dialogs */}
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
