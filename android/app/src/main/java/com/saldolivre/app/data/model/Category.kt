package com.saldolivre.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

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

    val CATEGORIES: List<CategoryDetail> = EXPENSE_CATEGORIES + INCOME_CATEGORIES + LEGACY_CATEGORIES

    private val categoryMap = CATEGORIES.associateBy { it.name }

    fun get(name: String): CategoryDetail {
        categoryMap[name]?.let { return it }
        return when (name) {
            "Lazer" -> get("Lazer & Entretenimento")
            "Assinaturas" -> get("Assinaturas & Serviços")
            "Compras" -> get("Compras & Estilo de Vida")
            "Educação" -> get("Educação & Desenvolvimento")
            "Financiamentos & Empréstimos" -> get("Financiamentos & Dívidas")
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
