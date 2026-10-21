package com.saldolivre.app.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class CategoryDetail(
    val name: String,
    val color: Color,
    val icon: ImageVector,
    val description: String,
    val subcategories: List<String>
)

object CategoryRegistry {
    val CATEGORIES = listOf(
        CategoryDetail(
            name = "Alimentação",
            color = Color(0xFF10B981),
            icon = Icons.Default.Restaurant,
            description = "Refeições, mantimentos e consumo alimentar",
            subcategories = listOf(
                "Supermercado",
                "Restaurante",
                "Delivery & Lanches",
                "Padaria & Café",
                "Feira & Hortifruti",
                "Açougue & Peixaria",
                "Bares & Bebidas"
            )
        ),
        CategoryDetail(
            name = "Moradia",
            color = Color(0xFF06B6D4),
            icon = Icons.Default.Home,
            description = "Despesas com residência e utilidades",
            subcategories = listOf(
                "Aluguel & Condomínio",
                "Energia Elétrica",
                "Água & Saneamento",
                "Gás",
                "Internet & TV a Cabo",
                "Manutenção & Reparos",
                "Móveis & Eletrodomésticos",
                "IPTU & Taxas"
            )
        ),
        CategoryDetail(
            name = "Transporte",
            color = Color(0xFF3B82F6),
            icon = Icons.Default.DirectionsCar,
            description = "Locomoção, veículos e mobilidade urbana",
            subcategories = listOf(
                "Combustível",
                "Aplicativos (Uber/99)",
                "Transporte Público (Metrô/Ônibus)",
                "Estacionamento & Pedágio",
                "Manutenção & Revisão",
                "Seguro & IPVA",
                "Lavagem & Estética Automotiva"
            )
        ),
        CategoryDetail(
            name = "Saúde",
            color = Color(0xFFF43F5E),
            icon = Icons.Default.MedicalServices,
            description = "Saúde física, mental e bem-estar",
            subcategories = listOf(
                "Farmácia & Remédios",
                "Consultas & Exames",
                "Plano de Saúde & Dental",
                "Academia & Treinos",
                "Terapia & Psicologia",
                "Cuidados Pessoais & Estética"
            )
        ),
        CategoryDetail(
            name = "Lazer",
            color = Color(0xFFA855F7),
            icon = Icons.Default.Celebration,
            description = "Entretenimento, passeios e viagens",
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
            description = "Serviços recorrentes e digitais",
            subcategories = listOf(
                "Streaming de Vídeo (Netflix/Max/Disney)",
                "Streaming de Música (Spotify/Apple)",
                "Softwares, IA & Cloud (iCloud/ChatGPT)",
                "Telefonia Celular",
                "Clubes & Assinaturas Recorrentes"
            )
        ),
        CategoryDetail(
            name = "Educação",
            color = Color(0xFFEAB308),
            icon = Icons.Default.School,
            description = "Estudos, capacitação e desenvolvimento",
            subcategories = listOf(
                "Cursos Online & Certificações",
                "Faculdade & Pós-Graduação",
                "Livros & E-books",
                "Material Escolar & Papelaria",
                "Idiomas"
            )
        ),
        CategoryDetail(
            name = "Compras",
            color = Color(0xFFEC4899),
            icon = Icons.Default.ShoppingBag,
            description = "Bens de consumo pessoal e vestuário",
            subcategories = listOf(
                "Roupas & Acessórios",
                "Calçados",
                "Eletrônicos & Informática",
                "Casa & Decoração",
                "Presentes"
            )
        ),
        CategoryDetail(
            name = "Investimentos",
            color = Color(0xFF059669),
            icon = Icons.Default.TrendingUp,
            description = "Aportes, patrimônio e aplicações financeiras",
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
            name = "Financiamentos & Empréstimos",
            color = Color(0xFF0284C7),
            icon = Icons.Default.AccountBalance,
            description = "Financiamentos imobiliários, veiculares e empréstimos",
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
            name = "Pets",
            color = Color(0xFFF97316),
            icon = Icons.Default.Pets,
            description = "Despesas com animais de estimação",
            subcategories = listOf(
                "Ração & Petiscos",
                "Veterinário & Medicamentos",
                "Banho, Tosa & Cuidados",
                "Acessórios & Brinquedos"
            )
        ),
        CategoryDetail(
            name = "Cartão Terceiros",
            color = Color(0xFF6366F1),
            icon = Icons.Default.Group,
            description = "Compras passadas para terceiros no cartão",
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
            description = "Créditos, cancelamentos e cashback na fatura",
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
            description = "Entradas, salários e rendimentos",
            subcategories = listOf(
                "Salário Principal",
                "Adiantamento / 13º Salário",
                "Freelance & Prestação de Serviços",
                "Rendimentos & Investimentos",
                "Reembolsos & Devoluções",
                "Pix Recebido",
                "Outras Receitas"
            )
        ),
        CategoryDetail(
            name = "Outros",
            color = Color(0xFF64748B),
            icon = Icons.Default.Label,
            description = "Despesas diversas não classificadas",
            subcategories = listOf(
                "Tarifas Bancárias & Juros",
                "Impostos & Taxas Governamentais",
                "Doações & Caridade",
                "Diversos"
            )
        )
    )

    private val categoryMap = CATEGORIES.associateBy { it.name }

    fun get(name: String): CategoryDetail =
        categoryMap[name] ?: CategoryDetail(
            name = name,
            color = Color(0xFF64748B),
            icon = Icons.Default.Label,
            description = "Outros gastos",
            subcategories = emptyList()
        )
}
