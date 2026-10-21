// ============================================================
// Modelo de dados (espelha as coleções do Cloud Firestore)
// ============================================================

export type TransactionType = 'income' | 'expense'
export type PaymentMethod = 'pix' | 'credit_card' | 'debit' | 'bill'
export type TransactionStatus = 'estimated' | 'pending' | 'paid'
export type EstimationRule = 'fixed' | 'last_month' | 'avg_3_months'

export type SplitType = 'mine' | 'split_50' | 'partner' | 'friend' | 'external' | 'custom'

export const CATEGORIES = [
  'Alimentação',
  'Moradia',
  'Transporte',
  'Saúde',
  'Lazer',
  'Assinaturas',
  'Educação',
  'Compras',
  'Investimentos',
  'Financiamentos & Empréstimos',
  'Pets',
  'Cartão Terceiros',
  'Estorno',
  'Receita',
  'Outros',
] as const
export type Category = (typeof CATEGORIES)[number]

export interface CategoryDetail {
  icon: string
  color: string
  description: string
  subcategories: readonly string[]
}

export const CATEGORY_GROUPS: Record<Category, CategoryDetail> = {
  Alimentação: {
    icon: 'Utensils',
    color: '#10b981',
    description: 'Refeições, mantimentos e consumo alimentar',
    subcategories: [
      'Supermercado',
      'Restaurante',
      'Delivery & Lanches',
      'Padaria & Café',
      'Feira & Hortifruti',
      'Açougue & Peixaria',
      'Bares & Bebidas',
    ],
  },
  Moradia: {
    icon: 'Home',
    color: '#06b6d4',
    description: 'Despesas com residência e utilidades',
    subcategories: [
      'Aluguel & Condomínio',
      'Energia Elétrica',
      'Água & Saneamento',
      'Gás',
      'Internet & TV a Cabo',
      'Manutenção & Reparos',
      'Móveis & Eletrodomésticos',
      'IPTU & Taxas',
    ],
  },
  Transporte: {
    icon: 'Car',
    color: '#3b82f6',
    description: 'Locomoção, veículos e mobilidade urbana',
    subcategories: [
      'Combustível',
      'Aplicativos (Uber/99)',
      'Transporte Público (Metrô/Ônibus)',
      'Estacionamento & Pedágio',
      'Manutenção & Revisão',
      'Seguro & IPVA',
      'Lavagem & Estética Automotiva',
    ],
  },
  Saúde: {
    icon: 'HeartPulse',
    color: '#f43f5e',
    description: 'Saúde física, mental e bem-estar',
    subcategories: [
      'Farmácia & Remédios',
      'Consultas & Exames',
      'Plano de Saúde & Dental',
      'Academia & Treinos',
      'Terapia & Psicologia',
      'Cuidados Pessoais & Estética',
    ],
  },
  Lazer: {
    icon: 'Sparkles',
    color: '#a855f7',
    description: 'Entretenimento, passeios e viagens',
    subcategories: [
      'Cinema, Teatro & Shows',
      'Viagens & Hospedagem',
      'Passeios & Experiências',
      'Jogos & Games',
      'Festas, Baladas & Eventos',
      'Hobbies & Atividades',
    ],
  },
  Assinaturas: {
    icon: 'RefreshCw',
    color: '#8b5cf6',
    description: 'Serviços recorrentes e digitais',
    subcategories: [
      'Streaming de Vídeo (Netflix/Max/Disney)',
      'Streaming de Música (Spotify/Apple)',
      'Softwares, IA & Cloud (iCloud/ChatGPT)',
      'Telefonia Celular',
      'Clubes & Assinaturas Recorrentes',
    ],
  },
  Educação: {
    icon: 'GraduationCap',
    color: '#eab308',
    description: 'Estudos, capacitação e desenvolvimento',
    subcategories: [
      'Cursos Online & Certificações',
      'Faculdade & Pós-Graduação',
      'Livros & E-books',
      'Material Escolar & Papelaria',
      'Idiomas',
    ],
  },
  Compras: {
    icon: 'ShoppingBag',
    color: '#ec4899',
    description: 'Bens de consumo pessoal e vestuário',
    subcategories: [
      'Roupas & Acessórios',
      'Calçados',
      'Eletrônicos & Informática',
      'Casa & Decoração',
      'Presentes',
    ],
  },
  Investimentos: {
    icon: 'TrendingUp',
    color: '#059669',
    description: 'Aportes, patrimônio e aplicações financeiras',
    subcategories: [
      'Renda Fixa (CDB / Tesouro / LCI / LCA)',
      'Ações & Fundos Imobiliários (FIIs)',
      'Criptomoedas & Ativos Digitais',
      'Reserva de Emergência',
      'Previdência Privada',
      'Fundos de Investimento',
      'Outros Investimentos',
    ],
  },
  'Financiamentos & Empréstimos': {
    icon: 'Landmark',
    color: '#0284c7',
    description: 'Financiamentos imobiliários, veiculares, empréstimos e amortizações',
    subcategories: [
      'Financiamento Imobiliário',
      'Financiamento Veicular',
      'Empréstimo Pessoal',
      'Empréstimo Consignado',
      'Amortização de Financiamento',
      'Crédito Estudantil (FIES/Pravaler)',
      'Renegociação de Dívidas',
      'Juros & Tarifas de Empréstimo',
      'Outros Empréstimos',
    ],
  },
  Pets: {
    icon: 'Dog',
    color: '#f97316',
    description: 'Despesas com animais de estimação',
    subcategories: [
      'Ração & Petiscos',
      'Veterinário & Medicamentos',
      'Banho, Tosa & Cuidados',
      'Acessórios & Brinquedos',
    ],
  },
  Receita: {
    icon: 'TrendingUp',
    color: '#22c55e',
    description: 'Entradas, salários e rendimentos',
    subcategories: [
      'Salário Principal',
      'Adiantamento / 13º Salário',
      'Freelance & Prestação de Serviços',
      'Rendimentos & Investimentos',
      'Reembolsos & Devoluções',
      'Pix Recebido',
      'Outras Receitas',
    ],
  },
  'Cartão Terceiros': {
    icon: 'Users',
    color: '#6366f1',
    description: 'Compras passadas para terceiros no cartão',
    subcategories: [
      'Compras de Amigos',
      'Compras de Familiares',
      'Outros Terceiros',
    ],
  },
  Estorno: {
    icon: 'RotateCcw',
    color: '#14b8a6',
    description: 'Créditos, cancelamentos e cashback na fatura',
    subcategories: [
      'Crédito na Fatura',
      'Devolução / Cancelamento',
      'Cashback Recebido',
    ],
  },
  Outros: {
    icon: 'Tag',
    color: '#64748b',
    description: 'Despesas diversas não classificadas',
    subcategories: [
      'Tarifas Bancárias & Juros',
      'Impostos & Taxas Governamentais',
      'Doações & Caridade',
      'Diversos',
    ],
  },
}

/** Coleção `users` */
export interface UserProfile {
  uid: string
  email: string
  displayName: string
  photoURL: string | null
  monthlyIncome: number
}

export interface SharedUser {
  uid: string
  email: string
  name: string
}

/** Coleção `friendships` (Rede de Amizades) */
export interface Friendship {
  id: string
  requesterUid: string
  requesterEmail: string
  requesterName: string
  recipientUid: string
  recipientEmail: string
  recipientName: string
  status: 'pending' | 'accepted' | 'rejected'
  createdAt: number
}

/** Coleção `cards` */
export interface Card {
  id: string
  ownerUid: string
  ownerName: string
  cardName: string
  closingDay: number
  dueDay: number
  sharedWithUsers: SharedUser[]
  /** Campo derivado para permitir query `array-contains` no Firestore */
  sharedWithUids: string[]
  frequentThirdParties: string[]
  color?: string
}

/** Coleção `recurring_bills` */
export interface RecurringBill {
  id: string
  ownerUid: string
  description: string
  category: Category
  subcategory?: string | null
  dueDay: number
  defaultAmount: number
  estimationRule: EstimationRule
  active: boolean
  /** Extensão opcional: divisão padrão ao gerar (ex: aluguel 50/50) */
  splitType?: SplitType
  paymentMethod?: PaymentMethod
  /** 'expense' (padrão) ou 'income' para receitas recorrentes (salário, aluguel recebido, freela) */
  type?: TransactionType
  /** Nova Feature 1: Limite e parcelamento em modelos recorrentes */
  startMonth?: string // YYYY-MM
  endMonth?: string // YYYY-MM (null/undefined se for contínua por prazo indeterminado)
  installmentsCount?: number // Quantidade total de parcelas / meses
}

export interface Allocation {
  type: 'PLATFORM_USER' | 'EXTERNAL'
  uid?: string
  name: string
  amount: number
  reimbursed: boolean
}

/** Coleção `transactions` */
export interface Transaction {
  id: string
  createdByUid: string
  paidByUid: string
  cardId?: string | null
  description: string
  category: Category
  subcategory?: string | null
  type: TransactionType
  paymentMethod: PaymentMethod
  status: TransactionStatus
  estimatedAmount: number | null
  actualAmount: number | null
  totalAmount: number
  dueDate: string // YYYY-MM-DD
  competenceMonth: string // YYYY-MM
  visibleToUids: string[]
  userImpact: Record<string, number>
  allocations: Allocation[]
  /** Origem: molde recorrente que gerou esta linha */
  recurringBillId?: string | null
  /** Reserva de montante em um cartão (anti-duplicidade) */
  reserveCardId?: string | null
  createdAt?: number

  /** Nova Feature 2: Gasto de amigo no cartão com aceite prévio */
  assignedFriendUid?: string | null
  assignedFriendName?: string | null
  acceptanceStatus?: 'pending_approval' | 'accepted' | 'rejected' | null
  /** Percentual (1–100) da despesa que cabe ao amigo. Ausente = 100% (legado). */
  friendSharePct?: number | null
  respondedAt?: number | null

  installmentGroupId?: string | null
  installmentNumber?: number | null
  installmentsTotal?: number | null
  splitType?: SplitType | null
}

export interface FriendSplitInput {
  uid: string
  name: string
  pct?: number | null
}

export interface InstallmentGroupUpdateInput {
  description?: string
  amount?: number
  category?: Category
  subcategory?: string | null
  paymentMethod?: PaymentMethod
  cardId?: string | null
  splitType?: SplitType
  externalName?: string
  friend?: FriendSplitInput
  customAllocations?: Allocation[]
  date?: string
  status?: TransactionStatus
}

export interface PendingChargeGroup {
  id: string
  isInstallment: boolean
  installmentGroupId?: string | null
  installmentsCount: number
  baseDescription: string
  cardId?: string | null
  senderUid: string
  senderName: string
  totalAmount: number
  friendShareAmount: number
  friendSharePct: number
  firstDueDate: string
  lastDueDate: string
  perInstallmentAmount: number
  perInstallmentFriendShare: number
  items: Transaction[]
  primaryTx: Transaction
}

export type NewTransaction = Omit<Transaction, 'id'>

export interface MonthSummary {
  income: number
  paid: number
  committed: number
  committedEstimated: number
  committedPending: number
  freeBalance: number
  daysRemaining: number
  daysInMonth: number
  dailyLimit: number | null
  monthState: 'past' | 'current' | 'future'
  receivableTotal: number
  receivableFromPartner: number
  receivableFromExternal: number
  receivableFromFriends: number
  payableToOthers: number
  byCategory: { category: string; amount: number }[]
  bySubcategory?: { category: string; subcategory: string; amount: number; count: number }[]
  totalInvested: number
  totalInvestedAccumulated: number
  byInvestmentSubcategory?: { subcategory: string; amount: number; percentage: number }[]
}

export interface FutureMonthProjection {
  monthKey: string // YYYY-MM
  label: string // Outubro 2026
  projectedIncome: number
  projectedExpense: number
  projectedFreeBalance: number
  daysInMonth: number
  projectedDailyLimit: number | null
  isGenerated: boolean
  incomeCount: number
  expenseCount: number
}

export interface ReceivableItem {
  transactionId: string
  allocationIndex: number
  description: string
  dueDate: string
  name: string
  type: 'PLATFORM_USER' | 'EXTERNAL'
  amount: number
  cardName?: string
  acceptanceStatus?: 'pending_approval' | 'accepted' | 'rejected' | null
  friendSharePct?: number | null
}

export interface PendingAction {
  id: string
  toolName: string
  args: Record<string, unknown>
  title: string
  status: 'pending' | 'confirmed' | 'cancelled'
  createdAt: number
  lines: { label: string; value: string; tone?: 'positive' | 'negative' | 'neutral' }[]
  pending_transactions?: PendingBatchTransaction[]
}

export interface PendingBatchTransaction {
  data: string
  tipo: 'income' | 'expense'
  valor_exato: number
  contraparte: string
  categoria_filtro: 'valida' | 'ignorada'
  categoria?: string
  subcategoria?: string
  metodoPagamento?: 'pix' | 'credit_card' | 'debit' | 'bill'
  motivoIgnorado?: string
}

export interface ActionCardData {
  kind: 'created' | 'updated' | 'report' | 'error' | 'confirmation'
  title: string
  lines: { label: string; value: string; tone?: 'positive' | 'negative' | 'neutral' }[]
  pendingAction?: PendingAction
  pending_transactions?: PendingBatchTransaction[]
}

export interface ChatAttachment {
  name: string
  size: number
  type: string
  base64?: string
  extractedText?: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  attachment?: ChatAttachment
  actions?: ActionCardData[]
  pending_transactions?: PendingBatchTransaction[]
  pending?: boolean
  error?: boolean
}

export interface ChatSession {
  id: string
  title: string
  createdAt: number
  updatedAt: number
  messages: ChatMessage[]
  pending_transactions?: PendingBatchTransaction[]
}

export interface UserLearnedRule {
  id: string
  rule: string
  category?: string
  createdAt: number
}


