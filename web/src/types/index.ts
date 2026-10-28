// ============================================================
// Modelo de dados (espelha as coleções do Cloud Firestore)
// ============================================================

export type TransactionType = 'income' | 'expense'
export type PaymentMethod = 'pix' | 'credit_card' | 'debit' | 'bill'
export type TransactionStatus = 'estimated' | 'pending' | 'paid'
export type EstimationRule = 'fixed' | 'last_month' | 'avg_3_months'

export type SplitType = 'mine' | 'split_50' | 'partner' | 'friend' | 'external' | 'custom' | 'THIRD_PARTY' | 'SHARED_50_50'

export const EXPENSE_CATEGORIES = [
  'Alimentação',
  'Moradia',
  'Transporte',
  'Saúde',
  'Cuidados Pessoais & Estética',
  'Lazer & Entretenimento',
  'Assinaturas & Serviços',
  'Compras & Estilo de Vida',
  'Educação & Desenvolvimento',
  'Família & Dependentes',
  'Pets',
  'Investimentos',
  'Financiamentos & Dívidas',
  'Impostos & Tributos',
  'Trabalho / PJ / Carreira',
  'Outros',
] as const

export const INCOME_CATEGORIES = [
  'Salário & Remuneração',
  'Benefícios',
  'Freelance & Serviços',
  'Rendimentos & Investimentos',
  'Reembolsos & Acertos',
  'Vendas & Desapegos',
  'Outras Receitas',
] as const

export const LEGACY_CATEGORIES = [
  'Lazer',
  'Assinaturas',
  'Compras',
  'Educação',
  'Financiamentos & Empréstimos',
  'Cartão Terceiros',
  'Estorno',
  'Receita',
] as const

export const CATEGORIES = [
  ...EXPENSE_CATEGORIES,
  ...INCOME_CATEGORIES,
  ...LEGACY_CATEGORIES,
] as const

export type Category = (typeof CATEGORIES)[number]
export type ExpenseCategory = (typeof EXPENSE_CATEGORIES)[number]
export type IncomeCategory = (typeof INCOME_CATEGORIES)[number]
export type LegacyCategory = (typeof LEGACY_CATEGORIES)[number]

export type BudgetNature = 'need' | 'want' | 'investment' | 'income'

export interface CategoryDetail {
  icon: string
  color: string
  description: string
  subcategories: readonly string[]
  subcategoriesNature?: Record<string, BudgetNature>
  defaultNature?: BudgetNature
}

export const CATEGORY_GROUPS: Record<Category, CategoryDetail> = {
  // --- DESPESAS (EXPENSES) ---
  Alimentação: {
    icon: 'Utensils',
    color: '#10b981',
    description: 'Supermercado, feira, refeições e consumo alimentar',
    defaultNature: 'need',
    subcategories: [
      'Supermercado',
      'Restaurante',
      'Delivery & Lanches',
      'Padaria & Café',
      'Feira & Hortifruti',
      'Açougue & Peixaria',
      'Bares & Bebidas',
    ],
    subcategoriesNature: {
      'Supermercado': 'need',
      'Feira & Hortifruti': 'need',
      'Açougue & Peixaria': 'need',
      'Padaria & Café': 'need',
      'Restaurante': 'want',
      'Delivery & Lanches': 'want',
      'Bares & Bebidas': 'want',
    },
  },
  Moradia: {
    icon: 'Home',
    color: '#06b6d4',
    description: 'Despesas com residência, aluguel e utilidades',
    defaultNature: 'need',
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
    subcategoriesNature: {
      'Aluguel & Condomínio': 'need',
      'Energia Elétrica': 'need',
      'Água & Saneamento': 'need',
      'Gás': 'need',
      'Internet & TV a Cabo': 'need',
      'Manutenção & Reparos': 'need',
      'IPTU & Taxas': 'need',
      'Móveis & Eletrodomésticos': 'want',
    },
  },
  Transporte: {
    icon: 'Car',
    color: '#3b82f6',
    description: 'Locomoção diária, veículos e mobilidade urbana',
    defaultNature: 'need',
    subcategories: [
      'Combustível',
      'Aplicativos (Uber/99)',
      'Transporte Público (Metrô/Ônibus)',
      'Estacionamento & Pedágio',
      'Manutenção & Revisão',
      'Seguro Auto & Moto',
      'Lavagem & Estética Automotiva',
    ],
    subcategoriesNature: {
      'Combustível': 'need',
      'Transporte Público (Metrô/Ônibus)': 'need',
      'Estacionamento & Pedágio': 'need',
      'Manutenção & Revisão': 'need',
      'Seguro Auto & Moto': 'need',
      'Aplicativos (Uber/99)': 'want',
      'Lavagem & Estética Automotiva': 'want',
    },
  },
  Saúde: {
    icon: 'HeartPulse',
    color: '#f43f5e',
    description: 'Saúde física, medicamentos, médicos e bem-estar',
    defaultNature: 'need',
    subcategories: [
      'Farmácia & Remédios',
      'Consultas & Exames',
      'Plano de Saúde & Dental',
      'Academia & Treinos',
      'Terapia & Psicologia',
      'Odontologia',
      'Cuidados Pessoais & Estética',
    ],
    subcategoriesNature: {
      'Farmácia & Remédios': 'need',
      'Consultas & Exames': 'need',
      'Plano de Saúde & Dental': 'need',
      'Academia & Treinos': 'need',
      'Terapia & Psicologia': 'need',
      'Odontologia': 'need',
      'Cuidados Pessoais & Estética': 'want',
    },
  },
  'Cuidados Pessoais & Estética': {
    icon: 'Sparkles',
    color: '#d946ef',
    description: 'Barbearia, salão de beleza, cosméticos e estética pessoal',
    defaultNature: 'want',
    subcategories: [
      'Cabelereiro & Barbearia',
      'Cosméticos & Maquiagem',
      'Skincare & Perfumaria',
      'Tratamentos & Estética',
      'Higiene Pessoal',
    ],
    subcategoriesNature: {
      'Cabelereiro & Barbearia': 'want',
      'Cosméticos & Maquiagem': 'want',
      'Skincare & Perfumaria': 'want',
      'Tratamentos & Estética': 'want',
      'Higiene Pessoal': 'need',
    },
  },
  'Lazer & Entretenimento': {
    icon: 'Sparkles',
    color: '#a855f7',
    description: 'Entretenimento, cultura, passeios, diversão e viagens',
    defaultNature: 'want',
    subcategories: [
      'Cinema, Teatro & Shows',
      'Viagens & Hospedagem',
      'Passeios & Experiências',
      'Festas, Baladas & Eventos',
      'Jogos & Games',
      'Hobbies & Atividades',
    ],
    subcategoriesNature: {
      'Cinema, Teatro & Shows': 'want',
      'Viagens & Hospedagem': 'want',
      'Passeios & Experiências': 'want',
      'Festas, Baladas & Eventos': 'want',
      'Jogos & Games': 'want',
      'Hobbies & Atividades': 'want',
    },
  },
  'Assinaturas & Serviços': {
    icon: 'RefreshCw',
    color: '#8b5cf6',
    description: 'Serviços digitais recorrentes, streaming e telefonia',
    defaultNature: 'want',
    subcategories: [
      'Streaming de Vídeo (Netflix/Max/Disney)',
      'Streaming de Música (Spotify/Apple)',
      'Softwares, IA & Nuvem (iCloud/ChatGPT)',
      'Telefonia Celular & Planos',
      'Clubes & Assinaturas Recorrentes',
    ],
    subcategoriesNature: {
      'Streaming de Vídeo (Netflix/Max/Disney)': 'want',
      'Streaming de Música (Spotify/Apple)': 'want',
      'Softwares, IA & Nuvem (iCloud/ChatGPT)': 'want',
      'Clubes & Assinaturas Recorrentes': 'want',
      'Telefonia Celular & Planos': 'need',
    },
  },
  'Compras & Estilo de Vida': {
    icon: 'ShoppingBag',
    color: '#ec4899',
    description: 'Vestuário, calçados, eletrônicos e compras em geral',
    defaultNature: 'want',
    subcategories: [
      'Roupas & Acessórios',
      'Calçados',
      'Eletrônicos & Informática',
      'Casa & Decoração',
      'Presentes',
    ],
    subcategoriesNature: {
      'Roupas & Acessórios': 'want',
      'Calçados': 'want',
      'Eletrônicos & Informática': 'want',
      'Casa & Decoração': 'want',
      'Presentes': 'want',
    },
  },
  'Educação & Desenvolvimento': {
    icon: 'GraduationCap',
    color: '#eab308',
    description: 'Estudos, capacitação, faculdade, cursos e livros',
    defaultNature: 'need',
    subcategories: [
      'Cursos Online & Certificações',
      'Faculdade & Pós-Graduação',
      'Livros & E-books',
      'Material Escolar & Papelaria',
      'Idiomas',
    ],
    subcategoriesNature: {
      'Cursos Online & Certificações': 'need',
      'Faculdade & Pós-Graduação': 'need',
      'Livros & E-books': 'need',
      'Material Escolar & Papelaria': 'need',
      'Idiomas': 'need',
    },
  },
  'Família & Dependentes': {
    icon: 'Users',
    color: '#fb923c',
    description: 'Despesas com filhos, creche/escola, fraldas e dependentes',
    defaultNature: 'need',
    subcategories: [
      'Escola & Creche',
      'Fraldas & Itens de Bebê',
      'Pediatra & Saúde Infantil',
      'Roupas & Calçados Infantis',
      'Brinquedos & Atividades',
      'Apoio Familiar & Mesada',
    ],
    subcategoriesNature: {
      'Escola & Creche': 'need',
      'Fraldas & Itens de Bebê': 'need',
      'Pediatra & Saúde Infantil': 'need',
      'Roupas & Calçados Infantis': 'need',
      'Brinquedos & Atividades': 'want',
      'Apoio Familiar & Mesada': 'want',
    },
  },
  Pets: {
    icon: 'Dog',
    color: '#f97316',
    description: 'Despesas com animais de estimação',
    defaultNature: 'need',
    subcategories: [
      'Ração & Petiscos',
      'Veterinário & Medicamentos',
      'Banho, Tosa & Cuidados',
      'Acessórios & Brinquedos',
    ],
    subcategoriesNature: {
      'Ração & Petiscos': 'need',
      'Veterinário & Medicamentos': 'need',
      'Banho, Tosa & Cuidados': 'want',
      'Acessórios & Brinquedos': 'want',
    },
  },
  Investimentos: {
    icon: 'TrendingUp',
    color: '#059669',
    description: 'Aportes, patrimônio e aplicações financeiras',
    defaultNature: 'investment',
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
  'Financiamentos & Dívidas': {
    icon: 'Landmark',
    color: '#0284c7',
    description: 'Financiamentos imobiliários, veiculares, empréstimos e parcelamentos bancários',
    defaultNature: 'need',
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
    subcategoriesNature: {
      'Financiamento Imobiliário': 'need',
      'Financiamento Veicular': 'need',
      'Empréstimo Pessoal': 'need',
      'Empréstimo Consignado': 'need',
      'Amortização de Financiamento': 'need',
      'Crédito Estudantil (FIES/Pravaler)': 'need',
      'Renegociação de Dívidas': 'need',
      'Juros & Tarifas de Empréstimo': 'need',
      'Outros Empréstimos': 'need',
    },
  },
  'Impostos & Tributos': {
    icon: 'FileText',
    color: '#64748b',
    description: 'IPTU, IPVA, IRPF, DARFs e tributos governamentais',
    defaultNature: 'need',
    subcategories: [
      'IPVA & Licenciamento',
      'IPTU',
      'Imposto de Renda (IRPF)',
      'DARFs & Tributos de Renda',
      'Taxas Cartorárias & Governamentais',
    ],
    subcategoriesNature: {
      'IPVA & Licenciamento': 'need',
      'IPTU': 'need',
      'Imposto de Renda (IRPF)': 'need',
      'DARFs & Tributos de Renda': 'need',
      'Taxas Cartorárias & Governamentais': 'need',
    },
  },
  'Trabalho / PJ / Carreira': {
    icon: 'Briefcase',
    color: '#0ea5e9',
    description: 'Despesas de trabalho, equipamentos, coworking, MEI e reembolsáveis',
    defaultNature: 'need',
    subcategories: [
      'Equipamentos & Ferramentas de Trabalho',
      'Despesas Corporativas (A Reembolsar)',
      'Coworking & Escritório',
      'DAS MEI & Contabilidade',
      'Almoço / Transporte a Trabalho',
    ],
    subcategoriesNature: {
      'Equipamentos & Ferramentas de Trabalho': 'need',
      'Despesas Corporativas (A Reembolsar)': 'need',
      'Coworking & Escritório': 'need',
      'DAS MEI & Contabilidade': 'need',
      'Almoço / Transporte a Trabalho': 'need',
    },
  },
  Outros: {
    icon: 'Tag',
    color: '#94a3b8',
    description: 'Despesas diversas não classificadas',
    defaultNature: 'want',
    subcategories: [
      'Doações & Caridade',
      'Tarifas Bancárias & Juros',
      'Imprevistos',
      'Diversos',
    ],
    subcategoriesNature: {
      'Tarifas Bancárias & Juros': 'need',
      'Imprevistos': 'need',
      'Doações & Caridade': 'want',
      'Diversos': 'want',
    },
  },

  // --- RECEITAS (INCOME) ---
  'Salário & Remuneração': {
    icon: 'TrendingUp',
    color: '#22c55e',
    description: 'Salário principal, adiantamentos, férias e 13º salário',
    defaultNature: 'income',
    subcategories: [
      'Salário Principal',
      'Adiantamento / Quinzenal',
      '13º Salário',
      'Férias',
      'Bônus & PLR',
    ],
  },
  Benefícios: {
    icon: 'Gift',
    color: '#10b981',
    description: 'Vales e auxílios corporativos',
    defaultNature: 'income',
    subcategories: [
      'Vale Alimentação (VA)',
      'Vale Refeição (VR)',
      'Auxílio Mobilidade / Combustível',
      'Auxílio Home Office',
      'Outros Benefícios',
    ],
  },
  'Freelance & Serviços': {
    icon: 'Briefcase',
    color: '#06b6d4',
    description: 'Prestação de serviços, projetos freelancer e MEI',
    defaultNature: 'income',
    subcategories: [
      'Projetos Freelance',
      'Consultorias & Aulas',
      'Faturamento MEI / PJ',
      'Comissões & Bonificações',
    ],
  },
  'Rendimentos & Investimentos': {
    icon: 'TrendingUp',
    color: '#059669',
    description: 'Dividendos, juros sobre capital e rendimentos de aplicações',
    defaultNature: 'income',
    subcategories: [
      'Dividendos & JCP',
      'Rendimentos de FIIs',
      'Rendimentos de Renda Fixa',
      'Resgates de Aplicação',
      'Lucro na Venda de Ativos',
    ],
  },
  'Reembolsos & Acertos': {
    icon: 'RotateCcw',
    color: '#6366f1',
    description: 'Devoluções de compras compartilhadas e reembolsos corporativos',
    defaultNature: 'income',
    subcategories: [
      'Acerto de Amigo / Cartão',
      'Divisão de Casal',
      'Reembolso de Empresa',
      'Cashback & Estornos Recebidos',
    ],
  },
  'Vendas & Desapegos': {
    icon: 'ShoppingBag',
    color: '#f59e0b',
    description: 'Venda de itens usados, veículos ou patrimônio',
    defaultNature: 'income',
    subcategories: [
      'Venda de Eletrônicos & Bens',
      'Venda de Veículo',
      'Desapegos & Brechó',
      'Outras Vendas',
    ],
  },
  'Outras Receitas': {
    icon: 'Sparkles',
    color: '#14b8a6',
    description: 'Pix recebidos, presentes e entradas eventuais',
    defaultNature: 'income',
    subcategories: [
      'Pix Recebido',
      'Presentes & Doações Recebidas',
      'Restituição de Imposto de Renda',
      'Entradas Diversas',
    ],
  },

  // --- COMPATIBILIDADE RETROATIVA (LEGACY CATEGORIES) ---
  Lazer: {
    icon: 'Sparkles',
    color: '#a855f7',
    description: 'Entretenimento, passeios e viagens (legado)',
    defaultNature: 'want',
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
    description: 'Serviços recorrentes e digitais (legado)',
    defaultNature: 'want',
    subcategories: [
      'Streaming de Vídeo (Netflix/Max/Disney)',
      'Streaming de Música (Spotify/Apple)',
      'Softwares, IA & Cloud (iCloud/ChatGPT)',
      'Telefonia Celular',
      'Clubes & Assinaturas Recorrentes',
    ],
  },
  Compras: {
    icon: 'ShoppingBag',
    color: '#ec4899',
    description: 'Bens de consumo pessoal e vestuário (legado)',
    defaultNature: 'want',
    subcategories: [
      'Roupas & Acessórios',
      'Calçados',
      'Eletrônicos & Informática',
      'Casa & Decoração',
      'Presentes',
    ],
  },
  Educação: {
    icon: 'GraduationCap',
    color: '#eab308',
    description: 'Estudos, capacitação e desenvolvimento (legado)',
    defaultNature: 'need',
    subcategories: [
      'Cursos Online & Certificações',
      'Faculdade & Pós-Graduação',
      'Livros & E-books',
      'Material Escolar & Papelaria',
      'Idiomas',
    ],
  },
  'Financiamentos & Empréstimos': {
    icon: 'Landmark',
    color: '#0284c7',
    description: 'Financiamentos imobiliários, veiculares, empréstimos e amortizações (legado)',
    defaultNature: 'need',
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
  Receita: {
    icon: 'TrendingUp',
    color: '#22c55e',
    description: 'Entradas, salários e rendimentos (legado)',
    defaultNature: 'income',
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
    description: 'Compras passadas para terceiros no cartão (legado)',
    defaultNature: 'want',
    subcategories: [
      'Compras de Amigos',
      'Compras de Familiares',
      'Outros Terceiros',
    ],
  },
  Estorno: {
    icon: 'RotateCcw',
    color: '#14b8a6',
    description: 'Créditos, cancelamentos e cashback na fatura (legado)',
    defaultNature: 'want',
    subcategories: [
      'Crédito na Fatura',
      'Devolução / Cancelamento',
      'Cashback Recebido',
    ],
  },
}

export function getCategoryDetail(name: string): CategoryDetail {
  if (name in CATEGORY_GROUPS) {
    return CATEGORY_GROUPS[name as Category]
  }
  // Mapeamentos de tolerância para nomes legados ou similares
  if (name === 'Lazer') return CATEGORY_GROUPS['Lazer & Entretenimento']
  if (name === 'Assinaturas') return CATEGORY_GROUPS['Assinaturas & Serviços']
  if (name === 'Compras') return CATEGORY_GROUPS['Compras & Estilo de Vida']
  if (name === 'Educação') return CATEGORY_GROUPS['Educação & Desenvolvimento']
  if (name === 'Financiamentos & Empréstimos') return CATEGORY_GROUPS['Financiamentos & Dívidas']

  return {
    icon: 'Tag',
    color: '#94a3b8',
    description: 'Categoria não classificada',
    subcategories: [],
    defaultNature: 'want',
  }
}

export function getBudgetNature(category: string, subcategory?: string | null): BudgetNature {
  if (category === 'Investimentos' || category === 'Rendimentos & Investimentos') {
    return 'investment'
  }
  if ((INCOME_CATEGORIES as readonly string[]).includes(category) || category === 'Receita') {
    return 'income'
  }

  const detail = getCategoryDetail(category)
  if (subcategory && detail.subcategoriesNature?.[subcategory]) {
    return detail.subcategoriesNature[subcategory]
  }
  if (detail.defaultNature) {
    return detail.defaultNature
  }

  const needsCats = new Set([
    'Moradia',
    'Saúde',
    'Financiamentos & Dívidas',
    'Financiamentos & Empréstimos',
    'Impostos & Tributos',
    'Família & Dependentes',
    'Educação',
    'Educação & Desenvolvimento',
  ])
  if (needsCats.has(category)) return 'need'
  return 'want'
}

/** Coleção `users` */
export interface UserProfile {
  uid: string
  email: string
  displayName: string
  photoURL: string | null
  monthlyIncome: number
  resolvedRecurring?: Record<string, string[]>
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
  /** Limite de crédito (opcional; compartilhado com o app Android) */
  limit?: number | null
}

export type LaunchTrigger = 'day_before' | 'on_due_date' | 'day_after' | 'first_day_of_month'

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
  cardId?: string | null
  /** 'expense' (padrão) ou 'income' para receitas recorrentes (salário, aluguel recebido, freela) */
  type?: TransactionType
  /** Nova Feature 1: Limite e parcelamento em modelos recorrentes */
  startMonth?: string // YYYY-MM
  endMonth?: string // YYYY-MM (null/undefined se for contínua por prazo indeterminado)
  installmentsCount?: number // Quantidade total de parcelas / meses
  /** Regra de disparo automático: 1 dia antes (padrão), no dia, 1 dia depois ou 1º dia do mês */
  launchTrigger?: LaunchTrigger
}

export interface PendingRecurringCandidate {
  id: string // bill.id ou `salary_${uid}`
  bill?: RecurringBill
  isSalary: boolean
  description: string
  category: Category
  subcategory?: string | null
  amount: number
  type: TransactionType
  paymentMethod: PaymentMethod
  cardId?: string | null
  dueDay: number
  dueDate: string // YYYY-MM-DD
  launchDate: string // YYYY-MM-DD
  matchedManualTx?: Transaction | null
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
  externalName?: string | null
  thirdPartyName?: string | null
  targetFriendName?: string | null
  /** ID da compra original no cartão que foi estornada */
  refundedTransactionId?: string | null
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

export interface MonthClosure {
  id: string
  uid: string
  closedMonth: string // YYYY-MM
  targetMonth: string // YYYY-MM
  totalSurplus: number
  carryoverToNextMonth: number
  investedAmount: number
  cushionAmount: number
  investmentTransactionId?: string | null
  closedAt: number
}

export interface MonthSummary {
  income: number
  paid: number
  committed: number
  committedEstimated: number
  committedPending: number
  freeBalance: number
  rawFreeBalance?: number
  cushionAmount?: number
  carryoverAmount?: number
  previousMonthDeficit?: number
  pendingClosureSurplus?: number | null
  pendingClosureMonth?: string | null
  closure?: MonthClosure | null
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

export interface ProjectedCategoryItem {
  category: string
  amount: number
  invoiceAmount: number
  accountAmount: number
  fixedBillAmount: number
  count: number
}

export interface ProjectedMonthItem {
  id: string
  description: string
  amount: number
  myImpact: number
  category: string
  subcategory?: string | null
  dueDate: string
  type: 'income' | 'expense'
  paymentMethod: PaymentMethod
  cardId?: string | null
  cardName?: string | null
  origin: 'invoice' | 'fixed_bill' | 'debit_pix' | 'income'
  status: 'paid' | 'pending' | 'estimated' | 'projected_recurring'
  isRecurringProjection?: boolean
  installmentInfo?: string | null
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
  /** Total de despesas originadas de Faturas de Cartão (parcelas + compras + assinaturas de cartão) */
  invoiceExpense?: number
  /** Total de Contas Fixas fora do cartão (Boleto, Pix, Débito recorrentes) */
  fixedBillsExpense?: number
  /** Total de gastos variáveis avulsos em Débito/Pix/Boleto já lançados no mês */
  variableAccountExpense?: number
  /** Categorias consolidadas do mês (somando itens de Fatura + Extrato + Contas Fixas previstas) */
  byCategory?: ProjectedCategoryItem[]
  /** Lista nominal unificada dos itens que compõem a projeção do mês */
  items?: ProjectedMonthItem[]
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


