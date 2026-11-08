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

export const ACTIVE_CATEGORIES = [
  ...EXPENSE_CATEGORIES,
  ...INCOME_CATEGORIES,
] as const

export const CATEGORIES = [
  ...EXPENSE_CATEGORIES,
  ...INCOME_CATEGORIES,
  ...LEGACY_CATEGORIES,
] as const

function foldCategoryAccents(s: string): string {
  return (s || '')
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
}

const CATEGORY_CANONICAL_MAP: Record<string, Category> = {
  'assinaturas': 'Assinaturas & Serviços',
  'assinaturas & servicos': 'Assinaturas & Serviços',
  'assinaturas e servicos': 'Assinaturas & Serviços',
  'servicos': 'Assinaturas & Serviços',
  'streaming': 'Assinaturas & Serviços',
  'educacao': 'Educação & Desenvolvimento',
  'educacao & desenvolvimento': 'Educação & Desenvolvimento',
  'educacao e desenvolvimento': 'Educação & Desenvolvimento',
  'cursos': 'Educação & Desenvolvimento',
  'lazer': 'Lazer & Entretenimento',
  'lazer & entretenimento': 'Lazer & Entretenimento',
  'lazer e entretenimento': 'Lazer & Entretenimento',
  'entretenimento': 'Lazer & Entretenimento',
  'compras': 'Compras & Estilo de Vida',
  'compras & estilo de vida': 'Compras & Estilo de Vida',
  'compras e estilo de vida': 'Compras & Estilo de Vida',
  'compras & vestuario': 'Compras & Estilo de Vida',
  'compras e vestuario': 'Compras & Estilo de Vida',
  'vestuario': 'Compras & Estilo de Vida',
  'estilo de vida': 'Compras & Estilo de Vida',
  'cuidados pessoais': 'Cuidados Pessoais & Estética',
  'cuidados pessoais & estetica': 'Cuidados Pessoais & Estética',
  'cuidados pessoais e estetica': 'Cuidados Pessoais & Estética',
  'estetica': 'Cuidados Pessoais & Estética',
  'familia': 'Família & Dependentes',
  'familia & dependentes': 'Família & Dependentes',
  'familia e dependentes': 'Família & Dependentes',
  'dependentes': 'Família & Dependentes',
  'familia & pets': 'Família & Dependentes',
  'familia e pets': 'Família & Dependentes',
  'familia & filhos': 'Família & Dependentes',
  'familia e filhos': 'Família & Dependentes',
  'trabalho': 'Trabalho / PJ / Carreira',
  'carreira': 'Trabalho / PJ / Carreira',
  'pj': 'Trabalho / PJ / Carreira',
  'trabalho / pj / carreira': 'Trabalho / PJ / Carreira',
  'financiamentos': 'Financiamentos & Dívidas',
  'financiamento': 'Financiamentos & Dívidas',
  'financiamentos & emprestimos': 'Financiamentos & Dívidas',
  'financiamentos e emprestimos': 'Financiamentos & Dívidas',
  'financiamentos & dividas': 'Financiamentos & Dívidas',
  'financiamentos e dividas': 'Financiamentos & Dívidas',
  'emprestimos': 'Financiamentos & Dívidas',
  'emprestimo': 'Financiamentos & Dívidas',
  'dividas': 'Financiamentos & Dívidas',
  'divida': 'Financiamentos & Dívidas',
  'impostos': 'Impostos & Tributos',
  'impostos & tributos': 'Impostos & Tributos',
  'impostos e tributos': 'Impostos & Tributos',
  'tributos': 'Impostos & Tributos',
  'pets': 'Pets',
  'pet': 'Pets',
  'alimentacao': 'Alimentação',
  'moradia': 'Moradia',
  'manutencao & servicos': 'Moradia',
  'manutencao e servicos': 'Moradia',
  'transporte': 'Transporte',
  'transportes': 'Transporte',
  'saude': 'Saúde',
  'investimentos': 'Investimentos',
  'investimento': 'Investimentos',
  'outros': 'Outros',
  'outro': 'Outros',
  'receita': 'Outras Receitas',
  'receitas': 'Outras Receitas',
  'outras receitas': 'Outras Receitas',
  'cartao terceiros': 'Outros',
  'estorno': 'Outros',
  'salario': 'Salário & Remuneração',
  'salario & remuneracao': 'Salário & Remuneração',
  'salario e remuneracao': 'Salário & Remuneração',
  'remuneracao': 'Salário & Remuneração',
  'beneficios': 'Benefícios',
  'beneficio': 'Benefícios',
  'freelance': 'Freelance & Serviços',
  'freelance & servicos': 'Freelance & Serviços',
  'freelance e servicos': 'Freelance & Serviços',
  'freela': 'Freelance & Serviços',
  'rendimentos': 'Rendimentos & Investimentos',
  'rendimentos & investimentos': 'Rendimentos & Investimentos',
  'rendimentos e investimentos': 'Rendimentos & Investimentos',
  'investimentos & rendimentos': 'Rendimentos & Investimentos',
  'investimentos e rendimentos': 'Rendimentos & Investimentos',
  'reembolsos': 'Reembolsos & Acertos',
  'reembolsos & acertos': 'Reembolsos & Acertos',
  'reembolsos e acertos': 'Reembolsos & Acertos',
  'reembolso': 'Reembolsos & Acertos',
  'vendas': 'Vendas & Desapegos',
  'vendas & desapegos': 'Vendas & Desapegos',
  'vendas e desapegos': 'Vendas & Desapegos',
  'desapegos': 'Vendas & Desapegos',
  'presentes & doacoes': 'Outras Receitas',
  'presentes e doacoes': 'Outras Receitas',
}

export function normalizeCategory(cat: string): Category {
  const trimmed = (cat || '').trim()
  if (!trimmed) return '' as Category
  const folded = foldCategoryAccents(trimmed)
  if (CATEGORY_CANONICAL_MAP[folded]) {
    return CATEGORY_CANONICAL_MAP[folded]
  }
  const match = (CATEGORIES as readonly string[]).find((c) => foldCategoryAccents(c) === folded)
  return (match || trimmed) as Category
}

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
  // Mapeamentos de tolerância para nomes legados ou similares (fonte única: normalizeCategory)
  const canonical = normalizeCategory(name)
  if (canonical in CATEGORY_GROUPS && canonical !== name) {
    return CATEGORY_GROUPS[canonical]
  }

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

export interface StrictCategoryResult {
  category: Category
  subcategory: string | null
}

const SUBCATEGORY_TO_CATEGORY_MAP: Record<string, { category: Category; officialSubcategory: string }> = {}

for (const [catName, detail] of Object.entries(CATEGORY_GROUPS)) {
  const category = catName as Category
  if (!detail.subcategories) continue
  for (const sub of detail.subcategories) {
    const folded = foldCategoryAccents(sub)
    if (!SUBCATEGORY_TO_CATEGORY_MAP[folded]) {
      SUBCATEGORY_TO_CATEGORY_MAP[folded] = { category, officialSubcategory: sub }
    }
  }
}

const SUBCATEGORY_ALIASES: Record<string, { category: Category; officialSubcategory: string }> = {
  // Alimentação
  'mercado': { category: 'Alimentação', officialSubcategory: 'Supermercado' },
  'supermercado': { category: 'Alimentação', officialSubcategory: 'Supermercado' },
  'restaurante': { category: 'Alimentação', officialSubcategory: 'Restaurante' },
  'almoco': { category: 'Alimentação', officialSubcategory: 'Restaurante' },
  'jantar': { category: 'Alimentação', officialSubcategory: 'Restaurante' },
  'delivery': { category: 'Alimentação', officialSubcategory: 'Delivery & Lanches' },
  'lanches': { category: 'Alimentação', officialSubcategory: 'Delivery & Lanches' },
  'lanche': { category: 'Alimentação', officialSubcategory: 'Delivery & Lanches' },
  'ifood': { category: 'Alimentação', officialSubcategory: 'Delivery & Lanches' },
  'rappi': { category: 'Alimentação', officialSubcategory: 'Delivery & Lanches' },
  'padaria': { category: 'Alimentação', officialSubcategory: 'Padaria & Café' },
  'cafe': { category: 'Alimentação', officialSubcategory: 'Padaria & Café' },
  'feira': { category: 'Alimentação', officialSubcategory: 'Feira & Hortifruti' },
  'hortifruti': { category: 'Alimentação', officialSubcategory: 'Feira & Hortifruti' },
  'sacolao': { category: 'Alimentação', officialSubcategory: 'Feira & Hortifruti' },
  'acougue': { category: 'Alimentação', officialSubcategory: 'Açougue & Peixaria' },
  'peixaria': { category: 'Alimentação', officialSubcategory: 'Açougue & Peixaria' },
  'carnes': { category: 'Alimentação', officialSubcategory: 'Açougue & Peixaria' },
  'bar': { category: 'Alimentação', officialSubcategory: 'Bares & Bebidas' },
  'bares': { category: 'Alimentação', officialSubcategory: 'Bares & Bebidas' },
  'bebidas': { category: 'Alimentação', officialSubcategory: 'Bares & Bebidas' },
  'cerveja': { category: 'Alimentação', officialSubcategory: 'Bares & Bebidas' },
  'chope': { category: 'Alimentação', officialSubcategory: 'Bares & Bebidas' },

  // Moradia
  'aluguel': { category: 'Moradia', officialSubcategory: 'Aluguel & Condomínio' },
  'condominio': { category: 'Moradia', officialSubcategory: 'Aluguel & Condomínio' },
  'energia': { category: 'Moradia', officialSubcategory: 'Energia Elétrica' },
  'energia eletrica': { category: 'Moradia', officialSubcategory: 'Energia Elétrica' },
  'luz': { category: 'Moradia', officialSubcategory: 'Energia Elétrica' },
  'enel': { category: 'Moradia', officialSubcategory: 'Energia Elétrica' },
  'cemig': { category: 'Moradia', officialSubcategory: 'Energia Elétrica' },
  'copel': { category: 'Moradia', officialSubcategory: 'Energia Elétrica' },
  'agua': { category: 'Moradia', officialSubcategory: 'Água & Saneamento' },
  'saneamento': { category: 'Moradia', officialSubcategory: 'Água & Saneamento' },
  'sabesp': { category: 'Moradia', officialSubcategory: 'Água & Saneamento' },
  'sanepar': { category: 'Moradia', officialSubcategory: 'Água & Saneamento' },
  'copasa': { category: 'Moradia', officialSubcategory: 'Água & Saneamento' },
  'gas': { category: 'Moradia', officialSubcategory: 'Gás' },
  'botijao': { category: 'Moradia', officialSubcategory: 'Gás' },
  'internet': { category: 'Moradia', officialSubcategory: 'Internet & TV a Cabo' },
  'wifi': { category: 'Moradia', officialSubcategory: 'Internet & TV a Cabo' },
  'tv a cabo': { category: 'Moradia', officialSubcategory: 'Internet & TV a Cabo' },
    'reforma': { category: 'Moradia', officialSubcategory: 'Manutenção & Reparos' },
  'manutencao': { category: 'Moradia', officialSubcategory: 'Manutenção & Reparos' },
  'moveis': { category: 'Moradia', officialSubcategory: 'Móveis & Eletrodomésticos' },
  'eletrodomesticos': { category: 'Moradia', officialSubcategory: 'Móveis & Eletrodomésticos' },

  // Transporte
  'combustivel': { category: 'Transporte', officialSubcategory: 'Combustível' },
  'gasolina': { category: 'Transporte', officialSubcategory: 'Combustível' },
  'etanol': { category: 'Transporte', officialSubcategory: 'Combustível' },
  'posto': { category: 'Transporte', officialSubcategory: 'Combustível' },
  'abastecer': { category: 'Transporte', officialSubcategory: 'Combustível' },
  'uber': { category: 'Transporte', officialSubcategory: 'Aplicativos (Uber/99)' },
  '99': { category: 'Transporte', officialSubcategory: 'Aplicativos (Uber/99)' },
  '99app': { category: 'Transporte', officialSubcategory: 'Aplicativos (Uber/99)' },
  '99pop': { category: 'Transporte', officialSubcategory: 'Aplicativos (Uber/99)' },
  'corrida': { category: 'Transporte', officialSubcategory: 'Aplicativos (Uber/99)' },
  'indrive': { category: 'Transporte', officialSubcategory: 'Aplicativos (Uber/99)' },
  'taxi': { category: 'Transporte', officialSubcategory: 'Aplicativos (Uber/99)' },
  'metro': { category: 'Transporte', officialSubcategory: 'Transporte Público (Metrô/Ônibus)' },
  'onibus': { category: 'Transporte', officialSubcategory: 'Transporte Público (Metrô/Ônibus)' },
  'trem': { category: 'Transporte', officialSubcategory: 'Transporte Público (Metrô/Ônibus)' },
  'bilhete unico': { category: 'Transporte', officialSubcategory: 'Transporte Público (Metrô/Ônibus)' },
  'transporte publico': { category: 'Transporte', officialSubcategory: 'Transporte Público (Metrô/Ônibus)' },
  'estacionamento': { category: 'Transporte', officialSubcategory: 'Estacionamento & Pedágio' },
  'pedagio': { category: 'Transporte', officialSubcategory: 'Estacionamento & Pedágio' },
  'sem parar': { category: 'Transporte', officialSubcategory: 'Estacionamento & Pedágio' },
  'veloe': { category: 'Transporte', officialSubcategory: 'Estacionamento & Pedágio' },
  'conectcar': { category: 'Transporte', officialSubcategory: 'Estacionamento & Pedágio' },
  'oficina': { category: 'Transporte', officialSubcategory: 'Manutenção & Revisão' },
  'mecanico': { category: 'Transporte', officialSubcategory: 'Manutenção & Revisão' },
  'revisao': { category: 'Transporte', officialSubcategory: 'Manutenção & Revisão' },
  'seguro auto': { category: 'Transporte', officialSubcategory: 'Seguro Auto & Moto' },
  'seguro carro': { category: 'Transporte', officialSubcategory: 'Seguro Auto & Moto' },
  'lavagem': { category: 'Transporte', officialSubcategory: 'Lavagem & Estética Automotiva' },

  // Saúde
  'farmacia': { category: 'Saúde', officialSubcategory: 'Farmácia & Remédios' },
  'drogaria': { category: 'Saúde', officialSubcategory: 'Farmácia & Remédios' },
  'remedios': { category: 'Saúde', officialSubcategory: 'Farmácia & Remédios' },
  'remedio': { category: 'Saúde', officialSubcategory: 'Farmácia & Remédios' },
  'medicamentos': { category: 'Saúde', officialSubcategory: 'Farmácia & Remédios' },
  'medicamento': { category: 'Saúde', officialSubcategory: 'Farmácia & Remédios' },
  'drogasil': { category: 'Saúde', officialSubcategory: 'Farmácia & Remédios' },
  'raia': { category: 'Saúde', officialSubcategory: 'Farmácia & Remédios' },
  'consultas': { category: 'Saúde', officialSubcategory: 'Consultas & Exames' },
  'consulta': { category: 'Saúde', officialSubcategory: 'Consultas & Exames' },
  'medico': { category: 'Saúde', officialSubcategory: 'Consultas & Exames' },
  'exames': { category: 'Saúde', officialSubcategory: 'Consultas & Exames' },
  'exame': { category: 'Saúde', officialSubcategory: 'Consultas & Exames' },
  'laboratorio': { category: 'Saúde', officialSubcategory: 'Consultas & Exames' },
  'plano de saude': { category: 'Saúde', officialSubcategory: 'Plano de Saúde & Dental' },
  'academia': { category: 'Saúde', officialSubcategory: 'Academia & Treinos' },
  'treinos': { category: 'Saúde', officialSubcategory: 'Academia & Treinos' },
  'treino': { category: 'Saúde', officialSubcategory: 'Academia & Treinos' },
  'smartfit': { category: 'Saúde', officialSubcategory: 'Academia & Treinos' },
  'gympass': { category: 'Saúde', officialSubcategory: 'Academia & Treinos' },
  'musculacao': { category: 'Saúde', officialSubcategory: 'Academia & Treinos' },
  'terapia': { category: 'Saúde', officialSubcategory: 'Terapia & Psicologia' },
  'psicologia': { category: 'Saúde', officialSubcategory: 'Terapia & Psicologia' },
  'psicologo': { category: 'Saúde', officialSubcategory: 'Terapia & Psicologia' },
  'psicologa': { category: 'Saúde', officialSubcategory: 'Terapia & Psicologia' },
  'dentista': { category: 'Saúde', officialSubcategory: 'Odontologia' },
  'odontologia': { category: 'Saúde', officialSubcategory: 'Odontologia' },

  // Cuidados Pessoais & Estética
  'barbearia': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Cabelereiro & Barbearia' },
  'barbeiro': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Cabelereiro & Barbearia' },
  'cabelereiro': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Cabelereiro & Barbearia' },
  'cabeleireiro': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Cabelereiro & Barbearia' },
  'salao': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Cabelereiro & Barbearia' },
  'cosmeticos': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Cosméticos & Maquiagem' },
  'maquiagem': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Cosméticos & Maquiagem' },
  'skincare': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Skincare & Perfumaria' },
  'perfume': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Skincare & Perfumaria' },
  'perfumaria': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Skincare & Perfumaria' },
  'estetica': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Tratamentos & Estética' },
  'manicure': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Tratamentos & Estética' },
  'higiene': { category: 'Cuidados Pessoais & Estética', officialSubcategory: 'Higiene Pessoal' },

  // Lazer & Entretenimento
  'cinema': { category: 'Lazer & Entretenimento', officialSubcategory: 'Cinema, Teatro & Shows' },
  'teatro': { category: 'Lazer & Entretenimento', officialSubcategory: 'Cinema, Teatro & Shows' },
  'shows': { category: 'Lazer & Entretenimento', officialSubcategory: 'Cinema, Teatro & Shows' },
  'show': { category: 'Lazer & Entretenimento', officialSubcategory: 'Cinema, Teatro & Shows' },
  'viagens': { category: 'Lazer & Entretenimento', officialSubcategory: 'Viagens & Hospedagem' },
  'viagem': { category: 'Lazer & Entretenimento', officialSubcategory: 'Viagens & Hospedagem' },
  'hospedagem': { category: 'Lazer & Entretenimento', officialSubcategory: 'Viagens & Hospedagem' },
  'hotel': { category: 'Lazer & Entretenimento', officialSubcategory: 'Viagens & Hospedagem' },
  'passeios': { category: 'Lazer & Entretenimento', officialSubcategory: 'Passeios & Experiências' },
  'passeio': { category: 'Lazer & Entretenimento', officialSubcategory: 'Passeios & Experiências' },
  'festas': { category: 'Lazer & Entretenimento', officialSubcategory: 'Festas, Baladas & Eventos' },
  'festa': { category: 'Lazer & Entretenimento', officialSubcategory: 'Festas, Baladas & Eventos' },
  'balada': { category: 'Lazer & Entretenimento', officialSubcategory: 'Festas, Baladas & Eventos' },
  'jogos': { category: 'Lazer & Entretenimento', officialSubcategory: 'Jogos & Games' },
  'games': { category: 'Lazer & Entretenimento', officialSubcategory: 'Jogos & Games' },
  'hobbies': { category: 'Lazer & Entretenimento', officialSubcategory: 'Hobbies & Atividades' },

  // Assinaturas & Serviços
  'netflix': { category: 'Assinaturas & Serviços', officialSubcategory: 'Streaming de Vídeo (Netflix/Max/Disney)' },
  'spotify': { category: 'Assinaturas & Serviços', officialSubcategory: 'Streaming de Música (Spotify/Apple)' },
  'chatgpt': { category: 'Assinaturas & Serviços', officialSubcategory: 'Softwares, IA & Nuvem (iCloud/ChatGPT)' },
  'icloud': { category: 'Assinaturas & Serviços', officialSubcategory: 'Softwares, IA & Nuvem (iCloud/ChatGPT)' },
  'telefonia': { category: 'Assinaturas & Serviços', officialSubcategory: 'Telefonia Celular & Planos' },
  'celular': { category: 'Assinaturas & Serviços', officialSubcategory: 'Telefonia Celular & Planos' },

  // Compras & Estilo de Vida
  'roupas': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Roupas & Acessórios' },
  'roupa': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Roupas & Acessórios' },
  'calcados': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Calçados' },
  'tenis': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Calçados' },
  'sapato': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Calçados' },
  'eletronicos': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Eletrônicos & Informática' },
  'informatica': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Eletrônicos & Informática' },
  'decoracao': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Casa & Decoração' },
  'presentes': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Presentes' },
  'presente': { category: 'Compras & Estilo de Vida', officialSubcategory: 'Presentes' },

  // Educação & Desenvolvimento
  'cursos': { category: 'Educação & Desenvolvimento', officialSubcategory: 'Cursos Online & Certificações' },
  'curso': { category: 'Educação & Desenvolvimento', officialSubcategory: 'Cursos Online & Certificações' },
  'faculdade': { category: 'Educação & Desenvolvimento', officialSubcategory: 'Faculdade & Pós-Graduação' },
  'pos-graduacao': { category: 'Educação & Desenvolvimento', officialSubcategory: 'Faculdade & Pós-Graduação' },
  'livros': { category: 'Educação & Desenvolvimento', officialSubcategory: 'Livros & E-books' },
  'livro': { category: 'Educação & Desenvolvimento', officialSubcategory: 'Livros & E-books' },
  'idiomas': { category: 'Educação & Desenvolvimento', officialSubcategory: 'Idiomas' },

  // Família & Dependentes
  'creche': { category: 'Família & Dependentes', officialSubcategory: 'Escola & Creche' },
  'escola': { category: 'Família & Dependentes', officialSubcategory: 'Escola & Creche' },
  'fraldas': { category: 'Família & Dependentes', officialSubcategory: 'Fraldas & Itens de Bebê' },
  'fralda': { category: 'Família & Dependentes', officialSubcategory: 'Fraldas & Itens de Bebê' },
  'pediatra': { category: 'Família & Dependentes', officialSubcategory: 'Pediatra & Saúde Infantil' },

  // Pets
  'racao': { category: 'Pets', officialSubcategory: 'Ração & Petiscos' },
  'veterinario': { category: 'Pets', officialSubcategory: 'Veterinário & Medicamentos' },
  'banho e tosa': { category: 'Pets', officialSubcategory: 'Banho, Tosa & Cuidados' },
  'banho': { category: 'Pets', officialSubcategory: 'Banho, Tosa & Cuidados' },

  // Investimentos
  'cdb': { category: 'Investimentos', officialSubcategory: 'Renda Fixa (CDB / Tesouro / LCI / LCA)' },
  'tesouro': { category: 'Investimentos', officialSubcategory: 'Renda Fixa (CDB / Tesouro / LCI / LCA)' },
  'acoes': { category: 'Investimentos', officialSubcategory: 'Ações & Fundos Imobiliários (FIIs)' },
  'fiis': { category: 'Investimentos', officialSubcategory: 'Ações & Fundos Imobiliários (FIIs)' },
  'cripto': { category: 'Investimentos', officialSubcategory: 'Criptomoedas & Ativos Digitais' },
  'criptomoedas': { category: 'Investimentos', officialSubcategory: 'Criptomoedas & Ativos Digitais' },
  'reserva de emergencia': { category: 'Investimentos', officialSubcategory: 'Reserva de Emergência' },

  // Financiamentos & Dívidas
  'financiamento imobiliario': { category: 'Financiamentos & Dívidas', officialSubcategory: 'Financiamento Imobiliário' },
  'financiamento veicular': { category: 'Financiamentos & Dívidas', officialSubcategory: 'Financiamento Veicular' },
  'emprestimo pessoal': { category: 'Financiamentos & Dívidas', officialSubcategory: 'Empréstimo Pessoal' },
  'emprestimo': { category: 'Financiamentos & Dívidas', officialSubcategory: 'Empréstimo Pessoal' },
  'consignado': { category: 'Financiamentos & Dívidas', officialSubcategory: 'Empréstimo Consignado' },

  // Impostos & Tributos
  'ipva': { category: 'Impostos & Tributos', officialSubcategory: 'IPVA & Licenciamento' },
  'iptu': { category: 'Impostos & Tributos', officialSubcategory: 'IPTU' },
  'irpf': { category: 'Impostos & Tributos', officialSubcategory: 'Imposto de Renda (IRPF)' },
  'darf': { category: 'Impostos & Tributos', officialSubcategory: 'DARFs & Tributos de Renda' },

  // Trabalho / PJ / Carreira
  'coworking': { category: 'Trabalho / PJ / Carreira', officialSubcategory: 'Coworking & Escritório' },
  'das mei': { category: 'Trabalho / PJ / Carreira', officialSubcategory: 'DAS MEI & Contabilidade' },
  'mei': { category: 'Trabalho / PJ / Carreira', officialSubcategory: 'DAS MEI & Contabilidade' },

  // Outros
  'doacao': { category: 'Outros', officialSubcategory: 'Doações & Caridade' },
  'doacoes': { category: 'Outros', officialSubcategory: 'Doações & Caridade' },
  'tarifas bancarias': { category: 'Outros', officialSubcategory: 'Tarifas Bancárias & Juros' },
  'imprevistos': { category: 'Outros', officialSubcategory: 'Imprevistos' },
  'diversos': { category: 'Outros', officialSubcategory: 'Diversos' },

  // Receitas
  'salario': { category: 'Salário & Remuneração', officialSubcategory: 'Salário Principal' },
  'adiantamento': { category: 'Salário & Remuneração', officialSubcategory: 'Adiantamento / Quinzenal' },
  'ferias': { category: 'Salário & Remuneração', officialSubcategory: 'Férias' },
  'va': { category: 'Benefícios', officialSubcategory: 'Vale Alimentação (VA)' },
  'vr': { category: 'Benefícios', officialSubcategory: 'Vale Refeição (VR)' },
  'freelance': { category: 'Freelance & Serviços', officialSubcategory: 'Projetos Freelance' },
  'dividendos': { category: 'Rendimentos & Investimentos', officialSubcategory: 'Dividendos & JCP' },
  'reembolso': { category: 'Reembolsos & Acertos', officialSubcategory: 'Reembolso de Empresa' },
  'desapegos': { category: 'Vendas & Desapegos', officialSubcategory: 'Desapegos & Brechó' },
  'pix recebido': { category: 'Outras Receitas', officialSubcategory: 'Pix Recebido' },
}

for (const [k, v] of Object.entries(SUBCATEGORY_ALIASES)) {
  const folded = foldCategoryAccents(k)
  if (!SUBCATEGORY_TO_CATEGORY_MAP[folded]) {
    SUBCATEGORY_TO_CATEGORY_MAP[folded] = v
  }
}

/**
 * Resolução estrita de categorias para o Copiloto e cadastro de lançamentos.
 * - Tolerância zero a categorias arbitrárias fora de ACTIVE_CATEGORIES.
 * - Resolução reversa de subcategorias passadas no campo categoria (ex: "Supermercado" -> Alimentação › Supermercado).
 * - Conversão de categorias legadas para versões modernas ativas (ex: "Lazer" -> "Lazer & Entretenimento").
 * - Normalização canônica sem acentos e case-insensitive.
 * - Fallback seguro inviolável para "Outros" (despesas) ou "Outras Receitas" (receitas).
 */
export function resolveStrictCategory(
  rawCategory: string | undefined | null,
  rawSubcategory?: string | null,
  type: TransactionType = 'expense',
  description?: string | null,
  guessFallback?: (description: string, type: TransactionType) => { category: string; subcategory?: string | null }
): StrictCategoryResult {
  const trimmedCat = (rawCategory || '').trim()
  const trimmedSub = (rawSubcategory || '').trim() || null
  const foldedCat = foldCategoryAccents(trimmedCat)
  const foldedSub = trimmedSub ? foldCategoryAccents(trimmedSub) : null

  let resolvedCat: Category | null = null
  let resolvedSub: string | null = trimmedSub

  // 1. Tentar mapeamento canônico de categoria direta (inclusive legadas para ativas)
  if (foldedCat && CATEGORY_CANONICAL_MAP[foldedCat]) {
    resolvedCat = CATEGORY_CANONICAL_MAP[foldedCat]
  }

  // 2. Tentar match exato/insensível contra ACTIVE_CATEGORIES
  if (!resolvedCat && foldedCat) {
    const activeMatch = (ACTIVE_CATEGORIES as readonly string[]).find((c) => foldCategoryAccents(c) === foldedCat)
    if (activeMatch) {
      resolvedCat = activeMatch as Category
    }
  }

  // 3. Tentar resolução reversa de subcategoria passada no campo categoria
  if (!resolvedCat && foldedCat) {
    const subMatch = SUBCATEGORY_TO_CATEGORY_MAP[foldedCat]
    if (subMatch) {
      resolvedCat = subMatch.category
      if (!resolvedSub) {
        resolvedSub = subMatch.officialSubcategory
      }
    }
  }

  // 4. Se a categoria era vazia ou não resolveu, mas temos uma subcategoria válida informada
  if (!resolvedCat && foldedSub) {
    const subMatch = SUBCATEGORY_TO_CATEGORY_MAP[foldedSub]
    if (subMatch) {
      resolvedCat = subMatch.category
      resolvedSub = subMatch.officialSubcategory
    }
  }

  // 5. Se já temos categoria oficial resolvida e uma subcategoria foi passada, tentar normalizar a subcategoria para o nome oficial
  if (resolvedCat && resolvedSub) {
    const detail = CATEGORY_GROUPS[resolvedCat]
    if (detail && detail.subcategories) {
      const foldedTargetSub = foldCategoryAccents(resolvedSub)
      const officialSub = detail.subcategories.find((s) => foldCategoryAccents(s) === foldedTargetSub)
      if (officialSub) {
        resolvedSub = officialSub
      } else {
        const aliasMatch = SUBCATEGORY_ALIASES[foldedTargetSub]
        if (aliasMatch && aliasMatch.category === resolvedCat) {
          resolvedSub = aliasMatch.officialSubcategory
        }
      }
    }
  }

  // 6. Se ainda não resolveu uma categoria válida e nenhuma foi informada explicitamente, tentar heurística/neural via descrição
  if (!resolvedCat && !trimmedCat && description && guessFallback) {
    try {
      const guess = guessFallback(description, type)
      if (guess && guess.category) {
        const foldedGuess = foldCategoryAccents(guess.category)
        const matched = (ACTIVE_CATEGORIES as readonly string[]).find((c) => foldCategoryAccents(c) === foldedGuess)
        if (matched && matched !== 'Outros' && matched !== 'Outras Receitas') {
          resolvedCat = matched as Category
          if (!resolvedSub && guess.subcategory) {
            resolvedSub = guess.subcategory
          }
        }
      }
    } catch {
      // Ignora erro do classificador e prossegue para fallback seguro
    }
  }

  // 7. Validação estrita por tipo e fallback inviolável
  if (type === 'income') {
    if (!resolvedCat || !(INCOME_CATEGORIES as readonly string[]).includes(resolvedCat)) {
      resolvedCat = 'Outras Receitas'
    }
  } else {
    if (!resolvedCat || !(EXPENSE_CATEGORIES as readonly string[]).includes(resolvedCat)) {
      resolvedCat = 'Outros'
    }
  }

  // 8. Sanitização inviolável da subcategoria: deve pertencer obrigatoriamente à categoria final resolvida
  let finalSub: string | null = null
  if (resolvedSub) {
    const detail = CATEGORY_GROUPS[resolvedCat]
    if (detail && detail.subcategories) {
      const foldedTargetSub = foldCategoryAccents(resolvedSub)
      const officialSub = detail.subcategories.find((s) => foldCategoryAccents(s) === foldedTargetSub)
      if (officialSub) {
        finalSub = officialSub
      } else {
        const aliasMatch = SUBCATEGORY_ALIASES[foldedTargetSub]
        if (aliasMatch && aliasMatch.category === resolvedCat) {
          finalSub = aliasMatch.officialSubcategory
        }
      }
    }
  }

  return {
    category: resolvedCat,
    subcategory: finalSub,
  }
}

/** Coleção `public_profiles`: perfil público desprovido de dados sensíveis para busca de amigos e exibição em rateios */
export interface PublicUserProfile {
  uid: string
  email: string
  displayName: string
  photoURL: string | null
  updatedAt?: any
}

/** Coleção `users` */
export interface UserProfile {
  uid: string
  email: string
  displayName: string
  photoURL: string | null
  monthlyIncome: number
  resolvedRecurring?: Record<string, string[]>
  investmentMonthlyTarget?: number
  investmentAssetBalances?: Record<string, number>
  emergencyReserveBalance?: number
  categoryBudgets?: Record<string, number>
  /** Orçamentos de envelopes configurados mês a mês (formato: "AAAA-MM": { "Categoria": limite }) */
  monthlyCategoryBudgets?: Record<string, Record<string, number>>
}

// ============================================================
// Envelopes Orçamentários Inteligentes & Análise de Gastos
// ============================================================

export type CategoryBudgetPaceStatus = 'normal' | 'fast' | 'critical'

export type CategoryBudgetBadgeLabel =
  | 'Estourado'
  | 'Vai Estourar'
  | 'Quase no Limite'
  | 'Previsto no Teto'
  | 'Ritmo Rápido'
  | 'Sob Controle'
  | 'Fixo em Dia'
  | 'No Limite'
  | 'Quitado / Dentro do Previsto'
  | 'Definir Teto'

export interface CategoryBudgetStatus {
  category: string
  limit: number
  rawLimit?: number
  spent: number // alias de spentAmount para compatibilidade
  spentAmount: number // valor já pago / efetivado
  pendingAmount: number // comprometido a vencer (pendente + recorrentes ativas não lançadas)
  committedAmount: number // spentAmount + pendingAmount
  remaining: number // limit - committedAmount (assinado)
  realRemaining: number // Math.max(0, limit - committedAmount)
  percentage: number // consumo pago (spentAmount / limit * 100)
  pendingPercentage: number // comprometimento a vencer (pendingAmount / limit * 100)
  totalPercentage: number // consumo total comprometido ((spentAmount + pendingAmount) / limit * 100)
  status: 'ok' | 'warning' | 'danger'
  badgeLabel: CategoryBudgetBadgeLabel
  dailyPace: number
  projectedSpent: number
  projectedPercentage: number
  recommendedDailyRemaining: number
  burnRateStatus: CategoryBudgetPaceStatus
  daysRemaining: number
  currentDay: number
  totalDays: number
  isFixedCategory?: boolean
}

export interface CategoryBudgetReport {
  items: CategoryBudgetStatus[]
  totalBudgeted: number
  rawTotalBudgeted?: number
  expectedIncome?: number
  carryoverAmount?: number
  previousMonthDeficit?: number
  totalAvailableIncome?: number
  isCappedToIncome?: boolean
  incomeUsagePercentage?: number
  unallocatedIncome?: number
  totalSpent: number
  totalPending: number
  totalCommitted: number
  totalRemaining: number
  totalRealRemaining: number
  overallPercentage: number
  status: 'ok' | 'warning' | 'danger'
  criticalCount: number
  warningCount: number
  topOverspentCategory?: CategoryBudgetStatus
  // Meta de Investimentos / 50-30-20 isolada dos envelopes de despesas
  investmentTarget?: number
  investmentSpent?: number
  investmentPercentage?: number
  /** Meta de aporte da regra 50/30/20 (20% da renda prevista), independente do teto de envelope */
  investmentGoal?: number
}

export type BudgetInsightSeverity = 'info' | 'attention' | 'danger'

export type BudgetInsightActionType =
  | 'adjust_limit'
  | 'rebalance'
  | 'apply_plan'
  | 'open_config'
  | 'ask_copilot'

export interface BudgetInsightAction {
  type: BudgetInsightActionType
  label: string
  category?: string
  /** adjust_limit: valor (pode ser negativo) somado ao teto · rebalance: valor transferido */
  amount?: number
  fromCategory?: string
  prompt?: string
}

export interface BudgetInsight {
  id: string
  severity: BudgetInsightSeverity
  title: string
  message: string
  action?: BudgetInsightAction
  fromAi?: boolean
}

export interface BudgetRebalanceSuggestion {
  overspentCategory: string
  overspentAmount: number
  compensateFrom: string
  availableSurplus: number
  suggestedTransfer: number
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
  /** Antecipação inteligente de parcelas */
  isAnticipated?: boolean | null
  anticipatedFromMonth?: string | null
  discountAmount?: number | null
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
  cushionAmount?: number
  investmentSubcategory?: string | null
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
  previousMonthClosure?: MonthClosure | null
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

export interface AccountCashFlowSummary {
  previousMonthsSurplus: number
  previousMonthLabel: string
  accountIncomesReceived: number
  accountExpensesPaid: number
  paidInvoicesAmount: number
  totalOutflowSoFar: number
  currentBankBalance: number
  futureIncomesRemaining: number
  futureCommittedRemaining: number
  projectedFreeBalance: number
  dailyLimit: number | null
}

export type FinancialHealthClassification = 'critical' | 'warning' | 'healthy' | 'excellent'

export interface FinancialHealthScore {
  score: number // 0 a 100
  classification: FinancialHealthClassification
  label: string // 'Crítico' | 'Atenção' | 'Saudável' | 'Excelente'
  description: string
  savingsScore: number // 0 a 30 (meta >= 20% da renda)
  spendingLimitScore: number // 0 a 30 (despesas totais vs receita)
  emergencyReserveScore: number // 0 a 25 (meta 3 a 6 meses do custo de vida)
  envelopeScore: number // 0 a 15 (disciplina e aderência aos envelopes)
  savingsRatePercentage: number // % poupada da renda
  spendingRatioPercentage: number // % despesas sobre a renda
  emergencyCoverageMonths: number // meses de cobertura da reserva
  budgetAdherenceScore?: number // 0 a 100 (% de aderência aos envelopes)
  budgetConfiguredCount?: number
  budgetOverspentCount?: number
  monthlyLivingCost?: number
  potentialSavingsRatePercentage?: number
  actionableTips?: string[]
}

export interface SmartBudgetAllocation {
  category: string
  suggestedLimit: number
  reason: string
}

export interface SmartBudgetPlan {
  summaryInsight: string
  allocations: SmartBudgetAllocation[]
}

export interface FinancialHealthAiDiagnosis {
  overallAssessment: string
  actionPlan: string[]
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
  carryoverAmount?: number
  previousMonthDeficit?: number
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
  descricao?: string
  categoria_filtro: 'valida' | 'ignorada'
  categoria?: string
  subcategoria?: string
  metodoPagamento?: 'pix' | 'credit_card' | 'debit' | 'bill'
  cartao?: string
  tipoDivisao?: SplitType | string
  nomeAmigo?: string
  emailAmigo?: string
  porcentagemAmigo?: number
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


