package com.saldolivre.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.Normalizer
import java.util.Locale

enum class BudgetNature {
    NEED, WANT, INVESTMENT, INCOME
}

@Immutable
data class CategoryDetail(
    val name: String,
    val color: Color,
    val icon: ImageVector,
    val description: String,
    val subcategories: List<String>,
    val subcategoriesNature: Map<String, BudgetNature> = emptyMap(),
    val defaultNature: BudgetNature = BudgetNature.WANT
)

object CategoryRegistry {
    val EXPENSE_CATEGORIES = listOf(
        CategoryDetail(
            name = "Alimentação",
            color = Color(0xFF10B981),
            icon = Icons.Default.Restaurant,
            description = "Supermercado, feira, refeições e consumo alimentar",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Supermercado",
                "Restaurante",
                "Delivery & Lanches",
                "Padaria & Café",
                "Feira & Hortifruti",
                "Açougue & Peixaria",
                "Bares & Bebidas"
            ),
            subcategoriesNature = mapOf(
                "Supermercado" to BudgetNature.NEED,
                "Feira & Hortifruti" to BudgetNature.NEED,
                "Açougue & Peixaria" to BudgetNature.NEED,
                "Padaria & Café" to BudgetNature.NEED,
                "Restaurante" to BudgetNature.WANT,
                "Delivery & Lanches" to BudgetNature.WANT,
                "Bares & Bebidas" to BudgetNature.WANT
            )
        ),
        CategoryDetail(
            name = "Moradia",
            color = Color(0xFF06B6D4),
            icon = Icons.Default.Home,
            description = "Despesas com residência, aluguel e utilidades",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Aluguel & Condomínio",
                "Energia Elétrica",
                "Água & Saneamento",
                "Gás",
                "Internet & TV a Cabo",
                "Manutenção & Reparos",
                "Móveis & Eletrodomésticos",
                "IPTU & Taxas"
            ),
            subcategoriesNature = mapOf(
                "Aluguel & Condomínio" to BudgetNature.NEED,
                "Energia Elétrica" to BudgetNature.NEED,
                "Água & Saneamento" to BudgetNature.NEED,
                "Gás" to BudgetNature.NEED,
                "Internet & TV a Cabo" to BudgetNature.NEED,
                "Manutenção & Reparos" to BudgetNature.NEED,
                "IPTU & Taxas" to BudgetNature.NEED,
                "Móveis & Eletrodomésticos" to BudgetNature.WANT
            )
        ),
        CategoryDetail(
            name = "Transporte",
            color = Color(0xFF3B82F6),
            icon = Icons.Default.DirectionsCar,
            description = "Locomoção diária, veículos e mobilidade urbana",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Combustível",
                "Aplicativos (Uber/99)",
                "Transporte Público (Metrô/Ônibus)",
                "Estacionamento & Pedágio",
                "Manutenção & Revisão",
                "Seguro Auto & Moto",
                "Lavagem & Estética Automotiva"
            ),
            subcategoriesNature = mapOf(
                "Combustível" to BudgetNature.NEED,
                "Transporte Público (Metrô/Ônibus)" to BudgetNature.NEED,
                "Estacionamento & Pedágio" to BudgetNature.NEED,
                "Manutenção & Revisão" to BudgetNature.NEED,
                "Seguro Auto & Moto" to BudgetNature.NEED,
                "Aplicativos (Uber/99)" to BudgetNature.WANT,
                "Lavagem & Estética Automotiva" to BudgetNature.WANT
            )
        ),
        CategoryDetail(
            name = "Saúde",
            color = Color(0xFFF43F5E),
            icon = Icons.Default.MedicalServices,
            description = "Saúde física, medicamentos, médicos e bem-estar",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Farmácia & Remédios",
                "Consultas & Exames",
                "Plano de Saúde & Dental",
                "Academia & Treinos",
                "Terapia & Psicologia",
                "Odontologia",
                "Cuidados Pessoais & Estética"
            ),
            subcategoriesNature = mapOf(
                "Farmácia & Remédios" to BudgetNature.NEED,
                "Consultas & Exames" to BudgetNature.NEED,
                "Plano de Saúde & Dental" to BudgetNature.NEED,
                "Academia & Treinos" to BudgetNature.NEED,
                "Terapia & Psicologia" to BudgetNature.NEED,
                "Odontologia" to BudgetNature.NEED,
                "Cuidados Pessoais & Estética" to BudgetNature.WANT
            )
        ),
        CategoryDetail(
            name = "Cuidados Pessoais & Estética",
            color = Color(0xFFD946EF),
            icon = Icons.Default.Face,
            description = "Barbearia, salão de beleza, cosméticos e estética pessoal",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Cabelereiro & Barbearia",
                "Cosméticos & Maquiagem",
                "Skincare & Perfumaria",
                "Tratamentos & Estética",
                "Higiene Pessoal"
            ),
            subcategoriesNature = mapOf(
                "Cabelereiro & Barbearia" to BudgetNature.WANT,
                "Cosméticos & Maquiagem" to BudgetNature.WANT,
                "Skincare & Perfumaria" to BudgetNature.WANT,
                "Tratamentos & Estética" to BudgetNature.WANT,
                "Higiene Pessoal" to BudgetNature.NEED
            )
        ),
        CategoryDetail(
            name = "Lazer & Entretenimento",
            color = Color(0xFFA855F7),
            icon = Icons.Default.Celebration,
            description = "Entretenimento, cultura, passeios, diversão e viagens",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Cinema, Teatro & Shows",
                "Viagens & Hospedagem",
                "Passeios & Experiências",
                "Festas, Baladas & Eventos",
                "Jogos & Games",
                "Hobbies & Atividades"
            )
        ),
        CategoryDetail(
            name = "Assinaturas & Serviços",
            color = Color(0xFF8B5CF6),
            icon = Icons.Default.Subscriptions,
            description = "Serviços digitais recorrentes, streaming e telefonia",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Streaming de Vídeo (Netflix/Max/Disney)",
                "Streaming de Música (Spotify/Apple)",
                "Softwares, IA & Nuvem (iCloud/ChatGPT)",
                "Telefonia Celular & Planos",
                "Clubes & Assinaturas Recorrentes"
            ),
            subcategoriesNature = mapOf(
                "Telefonia Celular & Planos" to BudgetNature.NEED
            )
        ),
        CategoryDetail(
            name = "Compras & Estilo de Vida",
            color = Color(0xFFEC4899),
            icon = Icons.Default.ShoppingBag,
            description = "Vestuário, calçados, eletrônicos e compras em geral",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Roupas & Acessórios",
                "Calçados",
                "Eletrônicos & Informática",
                "Casa & Decoração",
                "Presentes"
            )
        ),
        CategoryDetail(
            name = "Educação & Desenvolvimento",
            color = Color(0xFFEAB308),
            icon = Icons.Default.School,
            description = "Estudos, capacitação, faculdade, cursos e livros",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Cursos Online & Certificações",
                "Faculdade & Pós-Graduação",
                "Livros & E-books",
                "Material Escolar & Papelaria",
                "Idiomas"
            )
        ),
        CategoryDetail(
            name = "Família & Dependentes",
            color = Color(0xFFFB923C),
            icon = Icons.Default.Group,
            description = "Despesas com filhos, creche/escola, fraldas e dependentes",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Escola & Creche",
                "Fraldas & Itens de Bebê",
                "Pediatra & Saúde Infantil",
                "Roupas & Calçados Infantis",
                "Brinquedos & Atividades",
                "Apoio Familiar & Mesada"
            ),
            subcategoriesNature = mapOf(
                "Escola & Creche" to BudgetNature.NEED,
                "Fraldas & Itens de Bebê" to BudgetNature.NEED,
                "Pediatra & Saúde Infantil" to BudgetNature.NEED,
                "Roupas & Calçados Infantis" to BudgetNature.NEED,
                "Brinquedos & Atividades" to BudgetNature.WANT,
                "Apoio Familiar & Mesada" to BudgetNature.WANT
            )
        ),
        CategoryDetail(
            name = "Pets",
            color = Color(0xFFF97316),
            icon = Icons.Default.Pets,
            description = "Despesas com animais de estimação",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Ração & Petiscos",
                "Veterinário & Medicamentos",
                "Banho, Tosa & Cuidados",
                "Acessórios & Brinquedos"
            ),
            subcategoriesNature = mapOf(
                "Ração & Petiscos" to BudgetNature.NEED,
                "Veterinário & Medicamentos" to BudgetNature.NEED,
                "Banho, Tosa & Cuidados" to BudgetNature.WANT,
                "Acessórios & Brinquedos" to BudgetNature.WANT
            )
        ),
        CategoryDetail(
            name = "Investimentos",
            color = Color(0xFF059669),
            icon = Icons.Default.TrendingUp,
            description = "Aportes, patrimônio e aplicações financeiras",
            defaultNature = BudgetNature.INVESTMENT,
            subcategories = listOf(
                "Renda Fixa (CDB / Tesouro / LCI / LCA)",
                "Ações & Fundos Imobiliários (FIIs)",
                "Criptomoedas & Ativos Digitais",
                "Reserva de Emergência",
                "Previdência Privada",
                "Fundos de Investimento",
                "Outros Investimentos"
            )
        ),
        CategoryDetail(
            name = "Financiamentos & Dívidas",
            color = Color(0xFF0284C7),
            icon = Icons.Default.AccountBalance,
            description = "Financiamentos imobiliários, veiculares, empréstimos e parcelamentos bancários",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Financiamento Imobiliário",
                "Financiamento Veicular",
                "Empréstimo Pessoal",
                "Empréstimo Consignado",
                "Amortização de Financiamento",
                "Crédito Estudantil (FIES/Pravaler)",
                "Renegociação de Dívidas",
                "Juros & Tarifas de Empréstimo",
                "Outros Empréstimos"
            )
        ),
        CategoryDetail(
            name = "Impostos & Tributos",
            color = Color(0xFF64748B),
            icon = Icons.Default.Description,
            description = "IPTU, IPVA, IRPF, DARFs e tributos governamentais",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "IPVA & Licenciamento",
                "IPTU",
                "Imposto de Renda (IRPF)",
                "DARFs & Tributos de Renda",
                "Taxas Cartorárias & Governamentais"
            )
        ),
        CategoryDetail(
            name = "Trabalho / PJ / Carreira",
            color = Color(0xFF0EA5E9),
            icon = Icons.Default.Work,
            description = "Despesas de trabalho, equipamentos, coworking, MEI e reembolsáveis",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Equipamentos & Ferramentas de Trabalho",
                "Despesas Corporativas (A Reembolsar)",
                "Coworking & Escritório",
                "DAS MEI & Contabilidade",
                "Almoço / Transporte a Trabalho"
            )
        ),
        CategoryDetail(
            name = "Outros",
            color = Color(0xFF94A3B8),
            icon = Icons.Default.Label,
            description = "Despesas diversas não classificadas",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Doações & Caridade",
                "Tarifas Bancárias & Juros",
                "Imprevistos",
                "Diversos"
            ),
            subcategoriesNature = mapOf(
                "Tarifas Bancárias & Juros" to BudgetNature.NEED,
                "Imprevistos" to BudgetNature.NEED,
                "Doações & Caridade" to BudgetNature.WANT,
                "Diversos" to BudgetNature.WANT
            )
        )
    )

    val INCOME_CATEGORIES = listOf(
        CategoryDetail(
            name = "Salário & Remuneração",
            color = Color(0xFF22C55E),
            icon = Icons.Default.AttachMoney,
            description = "Salário principal, adiantamentos, férias e 13º salário",
            defaultNature = BudgetNature.INCOME,
            subcategories = listOf(
                "Salário Principal",
                "Adiantamento / Quinzenal",
                "13º Salário",
                "Férias",
                "Bônus & PLR"
            )
        ),
        CategoryDetail(
            name = "Benefícios",
            color = Color(0xFF10B981),
            icon = Icons.Default.CardGiftcard,
            description = "Vales e auxílios de trabalho",
            defaultNature = BudgetNature.INCOME,
            subcategories = listOf(
                "Vale Alimentação (VA)",
                "Vale Refeição (VR)",
                "Auxílio Mobilidade / Combustível",
                "Auxílio Home Office",
                "Outros Benefícios"
            )
        ),
        CategoryDetail(
            name = "Freelance & Serviços",
            color = Color(0xFF06B6D4),
            icon = Icons.Default.Work,
            description = "Prestação de serviços, projetos freelancer e MEI",
            defaultNature = BudgetNature.INCOME,
            subcategories = listOf(
                "Projetos Freelance",
                "Consultorias & Aulas",
                "Faturamento MEI / PJ",
                "Comissões & Bonificações"
            )
        ),
        CategoryDetail(
            name = "Rendimentos & Investimentos",
            color = Color(0xFF059669),
            icon = Icons.Default.TrendingUp,
            description = "Dividendos, juros sobre capital e rendimentos de aplicações",
            defaultNature = BudgetNature.INCOME,
            subcategories = listOf(
                "Dividendos & JCP",
                "Rendimentos de FIIs",
                "Rendimentos de Renda Fixa",
                "Resgates de Aplicação",
                "Lucro na Venda de Ativos"
            )
        ),
        CategoryDetail(
            name = "Reembolsos & Acertos",
            color = Color(0xFF6366F1),
            icon = Icons.Default.Refresh,
            description = "Devoluções de compras compartilhadas e reembolsos corporativos",
            defaultNature = BudgetNature.INCOME,
            subcategories = listOf(
                "Acerto de Amigo / Cartão",
                "Divisão de Casal",
                "Reembolso de Empresa",
                "Cashback & Estornos Recebidos"
            )
        ),
        CategoryDetail(
            name = "Vendas & Desapegos",
            color = Color(0xFFF59E0B),
            icon = Icons.Default.ShoppingBag,
            description = "Venda de itens usados, veículos ou patrimônio",
            defaultNature = BudgetNature.INCOME,
            subcategories = listOf(
                "Venda de Eletrônicos & Bens",
                "Venda de Veículo",
                "Desapegos & Brechó",
                "Outras Vendas"
            )
        ),
        CategoryDetail(
            name = "Outras Receitas",
            color = Color(0xFF14B8A6),
            icon = Icons.Default.AccountBalanceWallet,
            description = "Pix recebidos, presentes e entradas eventuais",
            defaultNature = BudgetNature.INCOME,
            subcategories = listOf(
                "Pix Recebido",
                "Presentes & Doações Recebidas",
                "Restituição de Imposto de Renda",
                "Entradas Diversas"
            )
        )
    )

    val LEGACY_CATEGORIES = listOf(
        CategoryDetail(
            name = "Lazer",
            color = Color(0xFFA855F7),
            icon = Icons.Default.Celebration,
            description = "Entretenimento, passeios e viagens (legado)",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Cinema, Teatro & Shows",
                "Viagens & Hospedagem",
                "Passeios & Experiências",
                "Jogos & Games",
                "Festas, Baladas & Eventos",
                "Hobbies & Atividades"
            )
        ),
        CategoryDetail(
            name = "Assinaturas",
            color = Color(0xFF8B5CF6),
            icon = Icons.Default.Subscriptions,
            description = "Serviços recorrentes e digitais (legado)",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Streaming de Vídeo (Netflix/Max/Disney)",
                "Streaming de Música (Spotify/Apple)",
                "Softwares, IA & Cloud (iCloud/ChatGPT)",
                "Telefonia Celular",
                "Clubes & Assinaturas Recorrentes"
            )
        ),
        CategoryDetail(
            name = "Compras",
            color = Color(0xFFEC4899),
            icon = Icons.Default.ShoppingBag,
            description = "Bens de consumo pessoal e vestuário (legado)",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Roupas & Acessórios",
                "Calçados",
                "Eletrônicos & Informática",
                "Casa & Decoração",
                "Presentes"
            )
        ),
        CategoryDetail(
            name = "Educação",
            color = Color(0xFFEAB308),
            icon = Icons.Default.School,
            description = "Estudos, capacitação e desenvolvimento (legado)",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Cursos Online & Certificações",
                "Faculdade & Pós-Graduação",
                "Livros & E-books",
                "Material Escolar & Papelaria",
                "Idiomas"
            )
        ),
        CategoryDetail(
            name = "Financiamentos & Empréstimos",
            color = Color(0xFF0284C7),
            icon = Icons.Default.AccountBalance,
            description = "Financiamentos imobiliários, veiculares e empréstimos (legado)",
            defaultNature = BudgetNature.NEED,
            subcategories = listOf(
                "Financiamento Imobiliário",
                "Financiamento Veicular",
                "Empréstimo Pessoal",
                "Empréstimo Consignado",
                "Amortização de Financiamento",
                "Crédito Estudantil (FIES/Pravaler)",
                "Renegociação de Dívidas",
                "Juros & Tarifas de Empréstimo",
                "Outros Empréstimos"
            )
        ),
        CategoryDetail(
            name = "Cartão Terceiros",
            color = Color(0xFF6366F1),
            icon = Icons.Default.Group,
            description = "Compras passadas para terceiros no cartão (legado)",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Compras de Amigos",
                "Compras de Familiares",
                "Outros Terceiros"
            )
        ),
        CategoryDetail(
            name = "Estorno",
            color = Color(0xFF14B8A6),
            icon = Icons.Default.Refresh,
            description = "Créditos, cancelamentos e cashback na fatura (legado)",
            defaultNature = BudgetNature.WANT,
            subcategories = listOf(
                "Crédito na Fatura",
                "Devolução / Cancelamento",
                "Cashback Recebido"
            )
        ),
        CategoryDetail(
            name = "Receita",
            color = Color(0xFF22C55E),
            icon = Icons.Default.AttachMoney,
            description = "Entradas, salários e rendimentos (legado)",
            defaultNature = BudgetNature.INCOME,
            subcategories = listOf(
                "Salário Principal",
                "Adiantamento / 13º Salário",
                "Freelance & Prestação de Serviços",
                "Rendimentos & Investimentos",
                "Reembolsos & Devoluções",
                "Pix Recebido",
                "Outras Receitas"
            )
        )
    )

    val CATEGORIES: List<CategoryDetail> = EXPENSE_CATEGORIES + INCOME_CATEGORIES

    val ACTIVE_CATEGORIES: List<String> = CATEGORIES.map { it.name }

    private val categoryMap = (CATEGORIES + LEGACY_CATEGORIES).associateBy { it.name }

    fun foldCategoryAccents(str: String?): String {
        if (str.isNullOrBlank()) return ""
        val nfd = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
        return Regex("""\p{M}""").replace(nfd, "").lowercase(java.util.Locale.ROOT).trim()
    }

    val CATEGORY_CANONICAL_MAP: Map<String, String> = mapOf(
        "assinaturas" to "Assinaturas & Serviços",
        "assinaturas & servicos" to "Assinaturas & Serviços",
        "assinaturas e servicos" to "Assinaturas & Serviços",
        "servicos" to "Assinaturas & Serviços",
        "streaming" to "Assinaturas & Serviços",
        "educacao" to "Educação & Desenvolvimento",
        "educacao & desenvolvimento" to "Educação & Desenvolvimento",
        "educacao e desenvolvimento" to "Educação & Desenvolvimento",
        "cursos" to "Educação & Desenvolvimento",
        "lazer" to "Lazer & Entretenimento",
        "lazer & entretenimento" to "Lazer & Entretenimento",
        "lazer e entretenimento" to "Lazer & Entretenimento",
        "entretenimento" to "Lazer & Entretenimento",
        "compras" to "Compras & Estilo de Vida",
        "compras & estilo de vida" to "Compras & Estilo de Vida",
        "compras e estilo de vida" to "Compras & Estilo de Vida",
        "compras & vestuario" to "Compras & Estilo de Vida",
        "compras e vestuario" to "Compras & Estilo de Vida",
        "vestuario" to "Compras & Estilo de Vida",
        "estilo de vida" to "Compras & Estilo de Vida",
        "cuidados pessoais" to "Cuidados Pessoais & Estética",
        "cuidados pessoais & estetica" to "Cuidados Pessoais & Estética",
        "cuidados pessoais e estetica" to "Cuidados Pessoais & Estética",
        "estetica" to "Cuidados Pessoais & Estética",
        "familia" to "Família & Dependentes",
        "familia & dependentes" to "Família & Dependentes",
        "familia e dependentes" to "Família & Dependentes",
        "dependentes" to "Família & Dependentes",
        "familia & pets" to "Família & Dependentes",
        "familia e pets" to "Família & Dependentes",
        "familia & filhos" to "Família & Dependentes",
        "familia e filhos" to "Família & Dependentes",
        "trabalho" to "Trabalho / PJ / Carreira",
        "carreira" to "Trabalho / PJ / Carreira",
        "pj" to "Trabalho / PJ / Carreira",
        "trabalho / pj / carreira" to "Trabalho / PJ / Carreira",
        "financiamentos" to "Financiamentos & Dívidas",
        "financiamento" to "Financiamentos & Dívidas",
        "financiamentos & emprestimos" to "Financiamentos & Dívidas",
        "financiamentos e emprestimos" to "Financiamentos & Dívidas",
        "financiamentos & dividas" to "Financiamentos & Dívidas",
        "financiamentos e dividas" to "Financiamentos & Dívidas",
        "emprestimos" to "Financiamentos & Dívidas",
        "emprestimo" to "Financiamentos & Dívidas",
        "dividas" to "Financiamentos & Dívidas",
        "divida" to "Financiamentos & Dívidas",
        "impostos" to "Impostos & Tributos",
        "impostos & tributos" to "Impostos & Tributos",
        "impostos e tributos" to "Impostos & Tributos",
        "tributos" to "Impostos & Tributos",
        "pets" to "Pets",
        "pet" to "Pets",
        "alimentacao" to "Alimentação",
        "moradia" to "Moradia",
        "manutencao & servicos" to "Moradia",
        "manutencao e servicos" to "Moradia",
        "transporte" to "Transporte",
        "transportes" to "Transporte",
        "saude" to "Saúde",
        "investimentos" to "Investimentos",
        "investimento" to "Investimentos",
        "outros" to "Outros",
        "outro" to "Outros",
        "receita" to "Outras Receitas",
        "receitas" to "Outras Receitas",
        "outras receitas" to "Outras Receitas",
        "cartao terceiros" to "Outros",
        "estorno" to "Outros",
        "salario" to "Salário & Remuneração",
        "salario & remuneracao" to "Salário & Remuneração",
        "salario e remuneracao" to "Salário & Remuneração",
        "remuneracao" to "Salário & Remuneração",
        "beneficios" to "Benefícios",
        "beneficio" to "Benefícios",
        "freelance" to "Freelance & Serviços",
        "freelance & servicos" to "Freelance & Serviços",
        "freelance e servicos" to "Freelance & Serviços",
        "freela" to "Freelance & Serviços",
        "rendimentos" to "Rendimentos & Investimentos",
        "rendimentos & investimentos" to "Rendimentos & Investimentos",
        "rendimentos e investimentos" to "Rendimentos & Investimentos",
        "investimentos & rendimentos" to "Rendimentos & Investimentos",
        "investimentos e rendimentos" to "Rendimentos & Investimentos",
        "reembolsos" to "Reembolsos & Acertos",
        "reembolsos & acertos" to "Reembolsos & Acertos",
        "reembolsos e acertos" to "Reembolsos & Acertos",
        "reembolso" to "Reembolsos & Acertos",
        "vendas" to "Vendas & Desapegos",
        "vendas & desapegos" to "Vendas & Desapegos",
        "vendas e desapegos" to "Vendas & Desapegos",
        "desapegos" to "Vendas & Desapegos",
        "presentes & doacoes" to "Outras Receitas",
        "presentes e doacoes" to "Outras Receitas"
    )

    val SUBCATEGORY_ALIASES: Map<String, Pair<String, String>> = mapOf(
        // Alimentação
        "mercado" to Pair("Alimentação", "Supermercado"),
        "supermercado" to Pair("Alimentação", "Supermercado"),
        "restaurante" to Pair("Alimentação", "Restaurante"),
        "almoco" to Pair("Alimentação", "Restaurante"),
        "jantar" to Pair("Alimentação", "Restaurante"),
        "delivery" to Pair("Alimentação", "Delivery & Lanches"),
        "lanches" to Pair("Alimentação", "Delivery & Lanches"),
        "lanche" to Pair("Alimentação", "Delivery & Lanches"),
        "ifood" to Pair("Alimentação", "Delivery & Lanches"),
        "rappi" to Pair("Alimentação", "Delivery & Lanches"),
        "padaria" to Pair("Alimentação", "Padaria & Café"),
        "cafe" to Pair("Alimentação", "Padaria & Café"),
        "feira" to Pair("Alimentação", "Feira & Hortifruti"),
        "hortifruti" to Pair("Alimentação", "Feira & Hortifruti"),
        "sacolao" to Pair("Alimentação", "Feira & Hortifruti"),
        "acougue" to Pair("Alimentação", "Açougue & Peixaria"),
        "peixaria" to Pair("Alimentação", "Açougue & Peixaria"),
        "carnes" to Pair("Alimentação", "Açougue & Peixaria"),
        "bar" to Pair("Alimentação", "Bares & Bebidas"),
        "bares" to Pair("Alimentação", "Bares & Bebidas"),
        "bebidas" to Pair("Alimentação", "Bares & Bebidas"),
        "cerveja" to Pair("Alimentação", "Bares & Bebidas"),
        "chope" to Pair("Alimentação", "Bares & Bebidas"),
        // Moradia
        "aluguel" to Pair("Moradia", "Aluguel & Condomínio"),
        "condominio" to Pair("Moradia", "Aluguel & Condomínio"),
        "energia" to Pair("Moradia", "Energia Elétrica"),
        "energia eletrica" to Pair("Moradia", "Energia Elétrica"),
        "luz" to Pair("Moradia", "Energia Elétrica"),
        "enel" to Pair("Moradia", "Energia Elétrica"),
        "cemig" to Pair("Moradia", "Energia Elétrica"),
        "copel" to Pair("Moradia", "Energia Elétrica"),
        "agua" to Pair("Moradia", "Água & Saneamento"),
        "saneamento" to Pair("Moradia", "Água & Saneamento"),
        "sabesp" to Pair("Moradia", "Água & Saneamento"),
        "sanepar" to Pair("Moradia", "Água & Saneamento"),
        "copasa" to Pair("Moradia", "Água & Saneamento"),
        "gas" to Pair("Moradia", "Gás"),
        "botijao" to Pair("Moradia", "Gás"),
        "internet" to Pair("Moradia", "Internet & TV a Cabo"),
        "wifi" to Pair("Moradia", "Internet & TV a Cabo"),
        "tv a cabo" to Pair("Moradia", "Internet & TV a Cabo"),
        "iptu" to Pair("Moradia", "IPTU & Taxas"),
        "reforma" to Pair("Moradia", "Manutenção & Reparos"),
        "manutencao" to Pair("Moradia", "Manutenção & Reparos"),
        "moveis" to Pair("Moradia", "Móveis & Eletrodomésticos"),
        "eletrodomesticos" to Pair("Moradia", "Móveis & Eletrodomésticos"),
        // Transporte
        "combustivel" to Pair("Transporte", "Combustível"),
        "gasolina" to Pair("Transporte", "Combustível"),
        "etanol" to Pair("Transporte", "Combustível"),
        "posto" to Pair("Transporte", "Combustível"),
        "abastecer" to Pair("Transporte", "Combustível"),
        "uber" to Pair("Transporte", "Aplicativos (Uber/99)"),
        "99" to Pair("Transporte", "Aplicativos (Uber/99)"),
        "99app" to Pair("Transporte", "Aplicativos (Uber/99)"),
        "99pop" to Pair("Transporte", "Aplicativos (Uber/99)"),
        "corrida" to Pair("Transporte", "Aplicativos (Uber/99)"),
        "indrive" to Pair("Transporte", "Aplicativos (Uber/99)"),
        "taxi" to Pair("Transporte", "Aplicativos (Uber/99)"),
        "metro" to Pair("Transporte", "Transporte Público (Metrô/Ônibus)"),
        "onibus" to Pair("Transporte", "Transporte Público (Metrô/Ônibus)"),
        "trem" to Pair("Transporte", "Transporte Público (Metrô/Ônibus)"),
        "bilhete unico" to Pair("Transporte", "Transporte Público (Metrô/Ônibus)"),
        "transporte publico" to Pair("Transporte", "Transporte Público (Metrô/Ônibus)"),
        "estacionamento" to Pair("Transporte", "Estacionamento & Pedágio"),
        "pedagio" to Pair("Transporte", "Estacionamento & Pedágio"),
        "sem parar" to Pair("Transporte", "Estacionamento & Pedágio"),
        "veloe" to Pair("Transporte", "Estacionamento & Pedágio"),
        "conectcar" to Pair("Transporte", "Estacionamento & Pedágio"),
        "oficina" to Pair("Transporte", "Manutenção & Revisão"),
        "mecanico" to Pair("Transporte", "Manutenção & Revisão"),
        "revisao" to Pair("Transporte", "Manutenção & Revisão"),
        "seguro auto" to Pair("Transporte", "Seguro Auto & Moto"),
        "seguro carro" to Pair("Transporte", "Seguro Auto & Moto"),
        "lavagem" to Pair("Transporte", "Lavagem & Estética Automotiva"),
        // Saúde
        "farmacia" to Pair("Saúde", "Farmácia & Remédios"),
        "drogaria" to Pair("Saúde", "Farmácia & Remédios"),
        "remedios" to Pair("Saúde", "Farmácia & Remédios"),
        "remedio" to Pair("Saúde", "Farmácia & Remédios"),
        "medicamentos" to Pair("Saúde", "Farmácia & Remédios"),
        "medicamento" to Pair("Saúde", "Farmácia & Remédios"),
        "drogasil" to Pair("Saúde", "Farmácia & Remédios"),
        "raia" to Pair("Saúde", "Farmácia & Remédios"),
        "consultas" to Pair("Saúde", "Consultas & Exames"),
        "consulta" to Pair("Saúde", "Consultas & Exames"),
        "medico" to Pair("Saúde", "Consultas & Exames"),
        "exames" to Pair("Saúde", "Consultas & Exames"),
        "exame" to Pair("Saúde", "Consultas & Exames"),
        "laboratorio" to Pair("Saúde", "Consultas & Exames"),
        "plano de saude" to Pair("Saúde", "Plano de Saúde & Dental"),
        "academia" to Pair("Saúde", "Academia & Treinos"),
        "treinos" to Pair("Saúde", "Academia & Treinos"),
        "treino" to Pair("Saúde", "Academia & Treinos"),
        "smartfit" to Pair("Saúde", "Academia & Treinos"),
        "gympass" to Pair("Saúde", "Academia & Treinos"),
        "musculacao" to Pair("Saúde", "Academia & Treinos"),
        "terapia" to Pair("Saúde", "Terapia & Psicologia"),
        "psicologia" to Pair("Saúde", "Terapia & Psicologia"),
        "psicologo" to Pair("Saúde", "Terapia & Psicologia"),
        "psicologa" to Pair("Saúde", "Terapia & Psicologia"),
        "dentista" to Pair("Saúde", "Odontologia"),
        "odontologia" to Pair("Saúde", "Odontologia"),
        // Cuidados Pessoais & Estética
        "barbearia" to Pair("Cuidados Pessoais & Estética", "Cabelereiro & Barbearia"),
        "barbeiro" to Pair("Cuidados Pessoais & Estética", "Cabelereiro & Barbearia"),
        "cabelereiro" to Pair("Cuidados Pessoais & Estética", "Cabelereiro & Barbearia"),
        "cabeleireiro" to Pair("Cuidados Pessoais & Estética", "Cabelereiro & Barbearia"),
        "salao" to Pair("Cuidados Pessoais & Estética", "Cabelereiro & Barbearia"),
        "cosmeticos" to Pair("Cuidados Pessoais & Estética", "Cosméticos & Maquiagem"),
        "maquiagem" to Pair("Cuidados Pessoais & Estética", "Cosméticos & Maquiagem"),
        "skincare" to Pair("Cuidados Pessoais & Estética", "Skincare & Perfumaria"),
        "perfume" to Pair("Cuidados Pessoais & Estética", "Skincare & Perfumaria"),
        "perfumaria" to Pair("Cuidados Pessoais & Estética", "Skincare & Perfumaria"),
        "estetica" to Pair("Cuidados Pessoais & Estética", "Tratamentos & Estética"),
        "manicure" to Pair("Cuidados Pessoais & Estética", "Tratamentos & Estética"),
        "higiene" to Pair("Cuidados Pessoais & Estética", "Higiene Pessoal"),
        // Lazer & Entretenimento
        "cinema" to Pair("Lazer & Entretenimento", "Cinema, Teatro & Shows"),
        "teatro" to Pair("Lazer & Entretenimento", "Cinema, Teatro & Shows"),
        "shows" to Pair("Lazer & Entretenimento", "Cinema, Teatro & Shows"),
        "show" to Pair("Lazer & Entretenimento", "Cinema, Teatro & Shows"),
        "viagens" to Pair("Lazer & Entretenimento", "Viagens & Hospedagem"),
        "viagem" to Pair("Lazer & Entretenimento", "Viagens & Hospedagem"),
        "hospedagem" to Pair("Lazer & Entretenimento", "Viagens & Hospedagem"),
        "hotel" to Pair("Lazer & Entretenimento", "Viagens & Hospedagem"),
        "passeios" to Pair("Lazer & Entretenimento", "Passeios & Experiências"),
        "passeio" to Pair("Lazer & Entretenimento", "Passeios & Experiências"),
        "festas" to Pair("Lazer & Entretenimento", "Festas, Baladas & Eventos"),
        "festa" to Pair("Lazer & Entretenimento", "Festas, Baladas & Eventos"),
        "balada" to Pair("Lazer & Entretenimento", "Festas, Baladas & Eventos"),
        "jogos" to Pair("Lazer & Entretenimento", "Jogos & Games"),
        "games" to Pair("Lazer & Entretenimento", "Jogos & Games"),
        "hobbies" to Pair("Lazer & Entretenimento", "Hobbies & Atividades"),
        // Assinaturas & Serviços
        "netflix" to Pair("Assinaturas & Serviços", "Streaming de Vídeo (Netflix/Max/Disney)"),
        "spotify" to Pair("Assinaturas & Serviços", "Streaming de Música (Spotify/Apple)"),
        "chatgpt" to Pair("Assinaturas & Serviços", "Softwares, IA & Nuvem (iCloud/ChatGPT)"),
        "icloud" to Pair("Assinaturas & Serviços", "Softwares, IA & Nuvem (iCloud/ChatGPT)"),
        "telefonia" to Pair("Assinaturas & Serviços", "Telefonia Celular & Planos"),
        "celular" to Pair("Assinaturas & Serviços", "Telefonia Celular & Planos"),
        // Compras & Estilo de Vida
        "roupas" to Pair("Compras & Estilo de Vida", "Roupas & Acessórios"),
        "roupa" to Pair("Compras & Estilo de Vida", "Roupas & Acessórios"),
        "calcados" to Pair("Compras & Estilo de Vida", "Calçados"),
        "tenis" to Pair("Compras & Estilo de Vida", "Calçados"),
        "sapato" to Pair("Compras & Estilo de Vida", "Calçados"),
        "eletronicos" to Pair("Compras & Estilo de Vida", "Eletrônicos & Informática"),
        "informatica" to Pair("Compras & Estilo de Vida", "Eletrônicos & Informática"),
        "decoracao" to Pair("Compras & Estilo de Vida", "Casa & Decoração"),
        "presentes" to Pair("Compras & Estilo de Vida", "Presentes"),
        "presente" to Pair("Compras & Estilo de Vida", "Presentes"),
        // Educação & Desenvolvimento
        "cursos" to Pair("Educação & Desenvolvimento", "Cursos Online & Certificações"),
        "curso" to Pair("Educação & Desenvolvimento", "Cursos Online & Certificações"),
        "faculdade" to Pair("Educação & Desenvolvimento", "Faculdade & Pós-Graduação"),
        "pos-graduacao" to Pair("Educação & Desenvolvimento", "Faculdade & Pós-Graduação"),
        "livros" to Pair("Educação & Desenvolvimento", "Livros & E-books"),
        "livro" to Pair("Educação & Desenvolvimento", "Livros & E-books"),
        "idiomas" to Pair("Educação & Desenvolvimento", "Idiomas"),
        // Família & Dependentes
        "creche" to Pair("Família & Dependentes", "Escola & Creche"),
        "escola" to Pair("Família & Dependentes", "Escola & Creche"),
        "fraldas" to Pair("Família & Dependentes", "Fraldas & Itens de Bebê"),
        "fralda" to Pair("Família & Dependentes", "Fraldas & Itens de Bebê"),
        "pediatra" to Pair("Família & Dependentes", "Pediatra & Saúde Infantil"),
        // Pets
        "racao" to Pair("Pets", "Ração & Petiscos"),
        "veterinario" to Pair("Pets", "Veterinário & Medicamentos"),
        "banho e tosa" to Pair("Pets", "Banho, Tosa & Cuidados"),
        "banho" to Pair("Pets", "Banho, Tosa & Cuidados"),
        // Investimentos
        "cdb" to Pair("Investimentos", "Renda Fixa (CDB / Tesouro / LCI / LCA)"),
        "tesouro" to Pair("Investimentos", "Renda Fixa (CDB / Tesouro / LCI / LCA)"),
        "acoes" to Pair("Investimentos", "Ações & Fundos Imobiliários (FIIs)"),
        "fiis" to Pair("Investimentos", "Ações & Fundos Imobiliários (FIIs)"),
        "cripto" to Pair("Investimentos", "Criptomoedas & Ativos Digitais"),
        "criptomoedas" to Pair("Investimentos", "Criptomoedas & Ativos Digitais"),
        "reserva de emergencia" to Pair("Investimentos", "Reserva de Emergência"),
        // Financiamentos & Dívidas
        "financiamento imobiliario" to Pair("Financiamentos & Dívidas", "Financiamento Imobiliário"),
        "financiamento veicular" to Pair("Financiamentos & Dívidas", "Financiamento Veicular"),
        "emprestimo pessoal" to Pair("Financiamentos & Dívidas", "Empréstimo Pessoal"),
        "emprestimo" to Pair("Financiamentos & Dívidas", "Empréstimo Pessoal"),
        "consignado" to Pair("Financiamentos & Dívidas", "Empréstimo Consignado"),
        // Impostos & Tributos
        "ipva" to Pair("Impostos & Tributos", "IPVA & Licenciamento"),
        "iptu" to Pair("Impostos & Tributos", "IPTU"),
        "irpf" to Pair("Impostos & Tributos", "Imposto de Renda (IRPF)"),
        "darf" to Pair("Impostos & Tributos", "DARFs & Tributos de Renda"),
        // Trabalho / PJ / Carreira
        "coworking" to Pair("Trabalho / PJ / Carreira", "Coworking & Escritório"),
        "das mei" to Pair("Trabalho / PJ / Carreira", "DAS MEI & Contabilidade"),
        "mei" to Pair("Trabalho / PJ / Carreira", "DAS MEI & Contabilidade"),
        // Outros
        "doacao" to Pair("Outros", "Doações & Caridade"),
        "doacoes" to Pair("Outros", "Doações & Caridade"),
        "tarifas bancarias" to Pair("Outros", "Tarifas Bancárias & Juros"),
        "imprevistos" to Pair("Outros", "Imprevistos"),
        "diversos" to Pair("Outros", "Diversos"),
        // Receitas
        "salario" to Pair("Salário & Remuneração", "Salário Principal"),
        "adiantamento" to Pair("Salário & Remuneração", "Adiantamento / Quinzenal"),
        "ferias" to Pair("Salário & Remuneração", "Férias"),
        "va" to Pair("Benefícios", "Vale Alimentação (VA)"),
        "vr" to Pair("Benefícios", "Vale Refeição (VR)"),
        "freelance" to Pair("Freelance & Serviços", "Projetos Freelance"),
        "dividendos" to Pair("Rendimentos & Investimentos", "Dividendos & JCP"),
        "reembolso" to Pair("Reembolsos & Acertos", "Reembolso de Empresa"),
        "desapegos" to Pair("Vendas & Desapegos", "Desapegos & Brechó"),
        "pix recebido" to Pair("Outras Receitas", "Pix Recebido")
    )

    val SUBCATEGORY_TO_CATEGORY_MAP: Map<String, Pair<String, String>> = buildMap {
        for (cat in CATEGORIES + LEGACY_CATEGORIES) {
            for (sub in cat.subcategories) {
                val folded = foldCategoryAccents(sub)
                if (!containsKey(folded)) {
                    put(folded, Pair(cat.name, sub))
                }
            }
        }
        for ((alias, target) in SUBCATEGORY_ALIASES) {
            val folded = foldCategoryAccents(alias)
            if (!containsKey(folded)) {
                put(folded, target)
            }
        }
    }

    fun resolveStrictCategory(
        rawCategory: String?,
        rawSubcategory: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        description: String? = null,
        guessFallback: ((String, TransactionType) -> Pair<String, String?>)? = null
    ): Pair<String, String?> {
        val trimmedCat = rawCategory?.trim() ?: ""
        val trimmedSub = rawSubcategory?.trim()?.ifBlank { null }
        val foldedCat = foldCategoryAccents(trimmedCat)
        val foldedSub = trimmedSub?.let { foldCategoryAccents(it) }

        var resolvedCat: String? = null
        var resolvedSub: String? = trimmedSub

        // 1. Mapeamento canônico direto (inclusive legadas para ativas)
        if (foldedCat.isNotBlank()) {
            CATEGORY_CANONICAL_MAP[foldedCat]?.let { resolvedCat = it }
        }

        // 2. Match insensível contra ACTIVE_CATEGORIES
        if (resolvedCat == null && foldedCat.isNotBlank()) {
            val activeMatch = ACTIVE_CATEGORIES.find { foldCategoryAccents(it) == foldedCat }
            if (activeMatch != null) {
                resolvedCat = activeMatch
            }
        }

        // 3. Resolução reversa de subcategoria passada no campo categoria
        if (resolvedCat == null && foldedCat.isNotBlank()) {
            val subMatch = SUBCATEGORY_TO_CATEGORY_MAP[foldedCat]
            if (subMatch != null) {
                resolvedCat = subMatch.first
                if (resolvedSub == null) {
                    resolvedSub = subMatch.second
                }
            }
        }

        // 4. Se a categoria era vazia ou não resolveu, mas temos uma subcategoria válida informada
        if (resolvedCat == null && !foldedSub.isNullOrBlank()) {
            val subMatch = SUBCATEGORY_TO_CATEGORY_MAP[foldedSub]
            if (subMatch != null) {
                resolvedCat = subMatch.first
                resolvedSub = subMatch.second
            }
        }

        // 5. Se já temos categoria oficial resolvida e uma subcategoria foi passada, tentar normalizar a subcategoria para o nome oficial
        if (resolvedCat != null && !resolvedSub.isNullOrBlank()) {
            val detail = categoryMap[resolvedCat]
            if (detail != null && detail.subcategories.isNotEmpty()) {
                val foldedTargetSub = foldCategoryAccents(resolvedSub)
                val officialSub = detail.subcategories.find { foldCategoryAccents(it) == foldedTargetSub }
                if (officialSub != null) {
                    resolvedSub = officialSub
                } else {
                    val aliasMatch = SUBCATEGORY_TO_CATEGORY_MAP[foldedTargetSub]
                    if (aliasMatch != null && aliasMatch.first == resolvedCat) {
                        resolvedSub = aliasMatch.second
                    }
                }
            }
        }

        // 6. Se ainda não resolveu uma categoria válida e nenhuma foi informada explicitamente, tentar heurística/neural via descrição
        if (resolvedCat == null && trimmedCat.isBlank() && !description.isNullOrBlank() && guessFallback != null) {
            try {
                val (guessCat, guessSub) = guessFallback(description, type)
                if (guessCat.isNotBlank()) {
                    val foldedGuess = foldCategoryAccents(guessCat)
                    val matched = ACTIVE_CATEGORIES.find { foldCategoryAccents(it) == foldedGuess }
                    if (matched != null && matched != "Outros" && matched != "Outras Receitas") {
                        resolvedCat = matched
                        if (resolvedSub == null && !guessSub.isNullOrBlank()) {
                            resolvedSub = guessSub
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignora erro do classificador e prossegue para fallback seguro
            }
        }

        // 7. Validação estrita por tipo e fallback inviolável
        val finalCat = if (type == TransactionType.INCOME) {
            if (resolvedCat == null || INCOME_CATEGORIES.none { it.name == resolvedCat }) {
                "Outras Receitas"
            } else {
                resolvedCat!!
            }
        } else {
            if (resolvedCat == null || EXPENSE_CATEGORIES.none { it.name == resolvedCat }) {
                "Outros"
            } else {
                resolvedCat!!
            }
        }

        return Pair(finalCat, resolvedSub)
    }

    fun get(name: String): CategoryDetail {
        categoryMap[name]?.let { return it }
        return when (name) {
            "Lazer" -> get("Lazer & Entretenimento")
            "Assinaturas" -> get("Assinaturas & Serviços")
            "Compras" -> get("Compras & Estilo de Vida")
            "Educação" -> get("Educação & Desenvolvimento")
            "Financiamentos & Empréstimos" -> get("Financiamentos & Dívidas")
            "Receita" -> get("Outras Receitas")
            "Cartão Terceiros" -> get("Outros")
            "Estorno" -> get("Outros")
            else -> CategoryDetail(
                name = name,
                color = Color(0xFF64748B),
                icon = Icons.Default.Label,
                description = "Outros gastos",
                subcategories = emptyList()
            )
        }
    }

    fun getBudgetNature(category: String, subcategory: String? = null): BudgetNature {
        if (category == "Investimentos" || category == "Rendimentos & Investimentos") {
            return BudgetNature.INVESTMENT
        }
        val isIncome = INCOME_CATEGORIES.any { it.name == category } || category == "Receita"
        if (isIncome) {
            return BudgetNature.INCOME
        }

        val detail = get(category)
        if (!subcategory.isNullOrBlank()) {
            detail.subcategoriesNature[subcategory]?.let { return it }
        }
        return detail.defaultNature
    }
}
