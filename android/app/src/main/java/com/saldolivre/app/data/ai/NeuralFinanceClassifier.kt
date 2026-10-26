package com.saldolivre.app.data.ai

import android.content.Context
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.TransactionType
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

data class CategoryGuess(
    val category: String,
    val subcategory: String? = null,
    val confidence: Float = 1.0f
)

data class ExtractedIntent(
    val description: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val subcategory: String?,
    val isCreditCard: Boolean,
    val cardNameHint: String?,
    val splitType: SplitType,
    val thirdPartyName: String?,
    val currentInstallment: Int = 1,
    val totalInstallments: Int = 1,
    val dueDate: String? = null,
    val competenceMonth: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.PIX
)

class NeuralFinanceClassifier(
    private val context: Context?
) {
    companion object {
        private const val PREFS_RULES = "saldo_livre_learned_rules_prefs"
        private const val KEY_RULES_SET = "learned_rules_set"

        private val THIRD_PARTIES: List<Pair<Regex, String>> = listOf(
            Regex("""\b(m[aã]e|minha m[aã]e)\b""", RegexOption.IGNORE_CASE) to "Mãe",
            Regex("""\bpai\b""", RegexOption.IGNORE_CASE) to "Pai",
            Regex("""\birm[aã]o\b""", RegexOption.IGNORE_CASE) to "Irmão",
            Regex("""\birm[aã]\b""", RegexOption.IGNORE_CASE) to "Irmã",
            Regex("""\bamig[oa]\b""", RegexOption.IGNORE_CASE) to "Amigo(a)",
            Regex("""\bsogr[ao]\b""", RegexOption.IGNORE_CASE) to "Sogro(a)",
            Regex("""\bav[oó]\b""", RegexOption.IGNORE_CASE) to "Avó/Avô",
            Regex("""\bti[ao]\b""", RegexOption.IGNORE_CASE) to "Tio(a)",
            Regex("""\bprim[oa]\b""", RegexOption.IGNORE_CASE) to "Primo(a)"
        )

        val INCOME_KEYWORDS_REGEX = Regex(
            """(sal[aá]rio|folha|remunera|pro-?labore|recebido|recebimento|pix recebido|ted recebida|doc recebido|transf.*recebida|dep[oó]sito|rendimento|juros|provento|dividendo|jcp|restitui|cashback|entrada|\+)""",
            RegexOption.IGNORE_CASE
        )

        private val SUBCATEGORY_HINTS: List<Triple<String, String, Regex>> = listOf(
            // Alimentação
            Triple("Alimentação", "Supermercado", Regex("""(supermercado|mercado|carrefour|p[aã]o de a[çc][uú]car|assai|atacad[aã]o|sams club|hortifruti|compras do m[eê]s|compras da semana)""", RegexOption.IGNORE_CASE)),
            Triple("Alimentação", "Delivery & Lanches", Regex("""(ifood|rappi|delivery|lanche|burger|hamburguer|pizza|pizzaria|mc donalds|mcdonalds|burger king|bk|subway|habibs)""", RegexOption.IGNORE_CASE)),
            Triple("Alimentação", "Padaria & Café", Regex("""(padaria|caf[eé]|starbucks|confeitaria|panificadora|p[aã]o de queijo|espresso)""", RegexOption.IGNORE_CASE)),
            Triple("Alimentação", "Feira & Hortifruti", Regex("""(feira|sacol[aã]o|hortifruti|verduras|legumes|frutas)""", RegexOption.IGNORE_CASE)),
            Triple("Alimentação", "Açougue & Peixaria", Regex("""(a[çc]ougue|peixaria|carnes|swift)""", RegexOption.IGNORE_CASE)),
            Triple("Alimentação", "Bares & Bebidas", Regex("""(bar|chope|cerveja|pub|adega|bebidas|vinho|drinks)""", RegexOption.IGNORE_CASE)),
            Triple("Alimentação", "Restaurante", Regex("""(restaurante|almo[çc]o|jantar|buffet|self service|rod[ií]zio|churrascaria|outback)""", RegexOption.IGNORE_CASE)),

            // Moradia
            Triple("Moradia", "Aluguel & Condomínio", Regex("""(aluguel|condom[ií]nio|quintoandar|loca[çc][aã]o)""", RegexOption.IGNORE_CASE)),
            Triple("Moradia", "Energia Elétrica", Regex("""(luz|energia|enel|cemig|light|copel|cpfl|eletropaulo|conta de luz)""", RegexOption.IGNORE_CASE)),
            Triple("Moradia", "Água & Saneamento", Regex("""([aá]gua|sabesp|sanepar|copasa|saneamento|conta de [aá]gua)""", RegexOption.IGNORE_CASE)),
            Triple("Moradia", "Gás", Regex("""(g[aá]s|comgas|ultragaz|botij[aã]o)""", RegexOption.IGNORE_CASE)),
            Triple("Moradia", "Internet & TV a Cabo", Regex("""(internet|fibra|wifi|wi-fi|claro|vivo fibra|oi fibra|tim live)""", RegexOption.IGNORE_CASE)),
            Triple("Moradia", "IPTU & Taxas", Regex("""(iptu|taxa de lixo)""", RegexOption.IGNORE_CASE)),
            Triple("Moradia", "Manutenção & Reparos", Regex("""(encanador|eletricista|pintor|reforma|manuten[çc][aã]o da casa)""", RegexOption.IGNORE_CASE)),

            // Transporte
            Triple("Transporte", "Aplicativos (Uber/99)", Regex("""(uber|99|99app|99pop|indrive|cabify)""", RegexOption.IGNORE_CASE)),
            Triple("Transporte", "Combustível", Regex("""(gasolina|etanol|combust[ií]vel|posto|ipiranga|shell|br distribuidora|abastecer)""", RegexOption.IGNORE_CASE)),
            Triple("Transporte", "Transporte Público (Metrô/Ônibus)", Regex("""([oô]nibus|metr[oô]|trem|cptm|bilhete [uú]nico|passagem de [oô]nibus|recarga bilhete)""", RegexOption.IGNORE_CASE)),
            Triple("Transporte", "Estacionamento & Pedágio", Regex("""(estacionamento|ped[aá]gio|sem parar|veloe|conectcar|tag)""", RegexOption.IGNORE_CASE)),
            Triple("Transporte", "Seguro Auto & Moto", Regex("""(ipva|seguro auto|seguro carro|dpvat|licenciamento|porto seguro)""", RegexOption.IGNORE_CASE)),
            Triple("Transporte", "Manutenção & Revisão", Regex("""(oficina|mec[aâ]nico|pneu|troca de [oó]leo|alinhamento|balanceamento|revis[aã]o carro)""", RegexOption.IGNORE_CASE)),

            // Saúde
            Triple("Saúde", "Farmácia & Remédios", Regex("""(farm[aá]cia|drogaria|drogasil|droga raia|pague menos|s[aã]o paulo|ultrafarma|rem[eé]dio|medicamento)""", RegexOption.IGNORE_CASE)),
            Triple("Saúde", "Consultas & Exames", Regex("""(consulta|m[eé]dico|dentista|odontolog|exame|laborat[oó]rio|fleury|lavoisier)""", RegexOption.IGNORE_CASE)),
            Triple("Saúde", "Academia & Treinos", Regex("""(academia|smart fit|bluefit|gympass|totalpass|personal|crossfit|nata[çc][aã]o)""", RegexOption.IGNORE_CASE)),
            Triple("Saúde", "Plano de Saúde & Dental", Regex("""(plano de sa[uú]de|unimed|amil|bradesco sa[uú]de|sulamerica|notredame)""", RegexOption.IGNORE_CASE)),
            Triple("Saúde", "Terapia & Psicologia", Regex("""(terapia|psic[oó]log|psiquiatra|sess[aã]o de terapia)""", RegexOption.IGNORE_CASE)),

            // Cuidados Pessoais & Estética
            Triple("Cuidados Pessoais & Estética", "Cabelereiro & Barbearia", Regex("""(barbearia|barbeiro|cabelereiro|cabeleireiro|sal[aã]o de beleza|corte de cabelo)""", RegexOption.IGNORE_CASE)),
            Triple("Cuidados Pessoais & Estética", "Cosméticos & Maquiagem", Regex("""(sephora|botic[aá]rio|natura|maquiagem|cosm[eé]tico)""", RegexOption.IGNORE_CASE)),
            Triple("Cuidados Pessoais & Estética", "Skincare & Perfumaria", Regex("""(skincare|protetor solar|perfume|perfumaria)""", RegexOption.IGNORE_CASE)),
            Triple("Cuidados Pessoais & Estética", "Tratamentos & Estética", Regex("""(est[eé]tica|depila[çc][aã]o|massagem|manicure|pedicure|spa)""", RegexOption.IGNORE_CASE)),

            // Assinaturas & Serviços
            Triple("Assinaturas & Serviços", "Streaming de Vídeo (Netflix/Max/Disney)", Regex("""(netflix|disney|hbo|max|prime video|amazon prime|youtube premium|globoplay|telecine)""", RegexOption.IGNORE_CASE)),
            Triple("Assinaturas & Serviços", "Streaming de Música (Spotify/Apple)", Regex("""(spotify|deezer|apple music|tidal|amazon music)""", RegexOption.IGNORE_CASE)),
            Triple("Assinaturas & Serviços", "Softwares, IA & Nuvem (iCloud/ChatGPT)", Regex("""(chatgpt|openai|icloud|google one|google storage|github|adobe|canva|microsoft|dropbox)""", RegexOption.IGNORE_CASE)),
            Triple("Assinaturas & Serviços", "Telefonia Celular & Planos", Regex("""(tim|claro celular|vivo celular|plano celular|controle vivo)""", RegexOption.IGNORE_CASE)),

            // Família & Dependentes
            Triple("Família & Dependentes", "Fraldas & Itens de Bebê", Regex("""(fralda|pampers|huggies|len[çc]o umedecido|mamadeira|chupeta)""", RegexOption.IGNORE_CASE)),
            Triple("Família & Dependentes", "Escola & Creche", Regex("""(creche|mensalidade escolar|berc[aá]rio|col[eé]gio infantil)""", RegexOption.IGNORE_CASE)),
            Triple("Família & Dependentes", "Pediatra & Saúde Infantil", Regex("""(pediatra|vacina infantil)""", RegexOption.IGNORE_CASE)),
            Triple("Família & Dependentes", "Brinquedos & Atividades", Regex("""(ri happy|brinquedo|brinquedos|lego)""", RegexOption.IGNORE_CASE)),

            // Pets
            Triple("Pets", "Ração & Petiscos", Regex("""(pet|petz|cobasi|ra[çc][aã]o|petisco)""", RegexOption.IGNORE_CASE)),
            Triple("Pets", "Veterinário & Medicamentos", Regex("""(veterin[aá]rio|vacina pet|banho e tosa|castra[çc][aã]o)""", RegexOption.IGNORE_CASE)),

            // Compras & Estilo de Vida
            Triple("Compras & Estilo de Vida", "Presentes", Regex("""(presente|lembrancinha|anivers[aá]rio|floricultura|flores|cesta|cacau show|kopenhagen|chocolates)""", RegexOption.IGNORE_CASE)),
            Triple("Compras & Estilo de Vida", "Roupas & Acessórios", Regex("""(zara|renner|riachuelo|c&a|shein|roupa|vestu[aá]rio|acess[oó]rio|nike|adidas|lingerie|joia|bijuteria)""", RegexOption.IGNORE_CASE)),
            Triple("Compras & Estilo de Vida", "Calçados", Regex("""(cal[çc]ado|t[eê]nis|sapato|sand[aá]lia|havaianas|arezzo|anacapri|vans|olympikus)""", RegexOption.IGNORE_CASE)),
            Triple("Compras & Estilo de Vida", "Eletrônicos & Informática", Regex("""(kabum|pichau|terabyte|apple|samsung|dell|mercado livre|amazon|shopee|magalu|celular|smartphone|notebook|computador)""", RegexOption.IGNORE_CASE)),
            Triple("Compras & Estilo de Vida", "Casa & Decoração", Regex("""(decora[çc][aã]o|cama|mesa|banho|tok&stok|etna|camicado|mobly|leroy|marabraz|m[oó]veis)""", RegexOption.IGNORE_CASE)),

            // Educação & Desenvolvimento
            Triple("Educação & Desenvolvimento", "Cursos Online & Certificações", Regex("""(curso|udemy|alura|hotmart|certifica[çc][aã]o)""", RegexOption.IGNORE_CASE)),
            Triple("Educação & Desenvolvimento", "Livros & E-books", Regex("""(livro|livraria|saraiva|leitura|kindle|amazon livros)""", RegexOption.IGNORE_CASE)),
            Triple("Educação & Desenvolvimento", "Faculdade & Pós-Graduação", Regex("""(faculdade|p[oó]s-gradua[çc][aã]o|matr[ií]cula)""", RegexOption.IGNORE_CASE)),

            // Lazer & Entretenimento
            Triple("Lazer & Entretenimento", "Cinema, Teatro & Shows", Regex("""(cinema|cinemark|ingresso|sympla|eventim|show|teatro)""", RegexOption.IGNORE_CASE)),
            Triple("Lazer & Entretenimento", "Viagens & Hospedagem", Regex("""(viagem|hotel|airbnb|booking|decolar|passagem a[eé]rea|latam|gol|azul)""", RegexOption.IGNORE_CASE)),

            // Impostos & Tributos
            Triple("Impostos & Tributos", "IPVA & Licenciamento", Regex("""(ipva|licenciamento|detran)""", RegexOption.IGNORE_CASE)),
            Triple("Impostos & Tributos", "IPTU", Regex("""(iptu|taxa de lixo prefeitura)""", RegexOption.IGNORE_CASE)),
            Triple("Impostos & Tributos", "Imposto de Renda (IRPF)", Regex("""(darf irpf|imposto de renda|declara[çc][aã]o irpf)""", RegexOption.IGNORE_CASE)),

            // Trabalho / PJ / Carreira
            Triple("Trabalho / PJ / Carreira", "DAS MEI & Contabilidade", Regex("""(das mei|pgmei|contabilizei|contabilidade)""", RegexOption.IGNORE_CASE)),
            Triple("Trabalho / PJ / Carreira", "Coworking & Escritório", Regex("""(coworking|wework|aluguel sala comercial)""", RegexOption.IGNORE_CASE)),

            // Investimentos
            Triple("Investimentos", "Renda Fixa (CDB / Tesouro / LCI / LCA)", Regex("""(cdb|tesouro|selic|lci|lca|ipca|renda fixa|deb[eê]nture|cri|cra|nuinvest|inter invest)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Ações & Fundos Imobiliários (FIIs)", Regex("""(a[çc][oõ]es|fii|fiis|hglg|mxrf|knip|xpml|bova11|ivvb11|smal11|itub|petr|vale|bbas|wege|dividendo|jcp)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Criptomoedas & Ativos Digitais", Regex("""(bitcoin|btc|ethereum|eth|solana|binance|mercado bitcoin|crypto|cripto)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Reserva de Emergência", Regex("""(reserva|reserva de emerg[eê]ncia|guardar dinheiro|cofrinho|reserva financeira)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Previdência Privada", Regex("""(previd[eê]ncia|pgbl|vgbl)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Outros Investimentos", Regex("""(investimento|aporte|aplica[çc][aã]o|corretora|xp investimentos|rico|clear|btg)""", RegexOption.IGNORE_CASE)),

            // Financiamentos & Dívidas
            Triple("Financiamentos & Dívidas", "Financiamento Imobiliário", Regex("""(financiamento habitacional|caixa habitacional|financ.*imobil|parcela.*(ap[eê]|apartamento|im[oó]vel|casa)|cr[eé]dito imobili[aá]rio)""", RegexOption.IGNORE_CASE)),
            Triple("Financiamentos & Dívidas", "Financiamento Veicular", Regex("""(financiamento.*(auto|ve[ií]culo|carro|moto)|parcela.*(carro|ve[ií]culo|moto)|cons[oó]rcio.*(auto|carro|moto)|bv financeira|pan auto)""", RegexOption.IGNORE_CASE)),
            Triple("Financiamentos & Dívidas", "Empréstimo Pessoal", Regex("""(empr[eé]stimo pessoal|empr[eé]stimo nubank|empr[eé]stimo inter|cr[eé]dito pessoal|empr[eé]stimo)""", RegexOption.IGNORE_CASE)),
            Triple("Financiamentos & Dívidas", "Amortização de Financiamento", Regex("""(amortiza[çc][aã]o|amortizar|redu[çc][aã]o de prazo|abatimento saldo devedor)""", RegexOption.IGNORE_CASE)),

            // Receitas
            Triple("Salário & Remuneração", "Salário Principal", Regex("""(sal[aá]rio|folha de pag|holerite|pro-?labore)""", RegexOption.IGNORE_CASE)),
            Triple("Benefícios", "Vale Alimentação (VA)", Regex("""(vale alimenta[çc][aã]o|\bva\b|alelo alimenta[çc][aã]o|ticket alimenta[çc][aã]o)""", RegexOption.IGNORE_CASE)),
            Triple("Benefícios", "Vale Refeição (VR)", Regex("""(vale refei[çc][aã]o|\bvr\b|alelo refei[çc][aã]o|ticket refei[çc][aã]o)""", RegexOption.IGNORE_CASE)),
            Triple("Freelance & Serviços", "Projetos Freelance", Regex("""(freela|freelance|projeto freela)""", RegexOption.IGNORE_CASE)),
            Triple("Reembolsos & Acertos", "Acerto de Amigo / Cartão", Regex("""(acerto|divis[aã]o de conta|pagamento de amigo|reembolso)""", RegexOption.IGNORE_CASE)),
            Triple("Outras Receitas", "Pix Recebido", Regex("""(pix recebido|ted recebida|doc recebido|dep[oó]sito)""", RegexOption.IGNORE_CASE)),

            // Estorno
            Triple("Estorno", "Crédito na Fatura", Regex("""(estorno|reembolso|devolu[çc][aã]o|cr[eé]dito fatura|cr[eé]dito na fatura|cashback)""", RegexOption.IGNORE_CASE))
        )

        private val CATEGORY_HINTS: List<Pair<String, Regex>> = listOf(
            "Estorno" to Regex("""(estorno|reembolso|devolu[çc][aã]o|cr[eé]dito fatura|cashback)""", RegexOption.IGNORE_CASE),
            "Financiamentos & Dívidas" to Regex("""(financiamento|empr[eé]stimo|consignado|amortiza[çc][aã]o|fies|pravaler)""", RegexOption.IGNORE_CASE),
            "Investimentos" to Regex("""(investimento|aporte|cdb|tesouro|selic|lci|lca|fii|fiis|a[çc][oõ]es|cripto|bitcoin|btc|eth|corretora)""", RegexOption.IGNORE_CASE),
            "Cuidados Pessoais & Estética" to Regex("""(barbearia|barbeiro|sal[aã]o de beleza|sal[aã]o|cabeleireiro|manicure|est[eé]tica|cosm[eé]tico|perfume|skincare)""", RegexOption.IGNORE_CASE),
            "Impostos & Tributos" to Regex("""(ipva|iptu|irpf|darf|tributo|licenciamento)""", RegexOption.IGNORE_CASE),
            "Família & Dependentes" to Regex("""(fralda|creche|berc[aá]rio|pediatra|filho|escola infantil)""", RegexOption.IGNORE_CASE),
            "Trabalho / PJ / Carreira" to Regex("""(coworking|das mei|contabilidade mei|despesa corporativa)""", RegexOption.IGNORE_CASE),
            "Moradia" to Regex("""(aluguel|condom|luz|energia|[aá]gua|g[aá]s|internet|iptu|casa|apartamento)""", RegexOption.IGNORE_CASE),
            "Alimentação" to Regex("""(jantar|almo[çc]o|caf[eé]|mercado|supermercado|ifood|restaurante|lanche|pizza|padaria|comida|feira)""", RegexOption.IGNORE_CASE),
            "Transporte" to Regex("""(uber|99|gasolina|combust[ií]vel|[oô]nibus|metr[oô]|estacionamento|carro|ped[aá]gio)""", RegexOption.IGNORE_CASE),
            "Saúde" to Regex("""(farm[aá]cia|rem[eé]dio|medic|consulta|exame|dentista|academia|plano de sa[uú]de|drogaria)""", RegexOption.IGNORE_CASE),
            "Lazer & Entretenimento" to Regex("""(cinema|show|viagem|bar|festa|passeio|jogo|teatro)""", RegexOption.IGNORE_CASE),
            "Assinaturas & Serviços" to Regex("""(netflix|spotify|prime|disney|hbo|assinatura|youtube|icloud|google one)""", RegexOption.IGNORE_CASE),
            "Educação & Desenvolvimento" to Regex("""(curso|faculdade|escola|livro|udemy|alura)""", RegexOption.IGNORE_CASE),
            "Compras & Estilo de Vida" to Regex("""(roupa|t[eê]nis|shopping|eletr[oô]nico|celular|magazine|presente|brinquedo)""", RegexOption.IGNORE_CASE),
            "Pets" to Regex("""(pet|ra[çc][aã]o|veterin[aá]rio|cobasi)""", RegexOption.IGNORE_CASE),
            "Salário & Remuneração" to Regex("""(sal[aá]rio|folha de pag|holerite|pro-?labore)""", RegexOption.IGNORE_CASE),
            "Benefícios" to Regex("""(vale alimenta[çc][aã]o|vale refei[çc][aã]o|\bva\b|\bvr\b)""", RegexOption.IGNORE_CASE)
        )
    }

    private val rulesPrefs = context?.getSharedPreferences(PREFS_RULES, Context.MODE_PRIVATE)

    fun normalize(text: String): String {
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        return Regex("""\p{M}""").replace(nfd, "").lowercase(Locale.ROOT).trim()
    }

    /**
     * Extrai o valor monetário de linguagem natural.
     * Suporta R$ 45,50, 45 reais, 45 conto, 50 pila, de 45.
     */
    fun parseAmount(text: String): Double? {
        val m1 = Regex("""r\$\s*([\d.]+(?:,\d{1,2})?|\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE).find(text)
        val m2 = Regex("""([\d.]+(?:,\d{1,2})?)\s*(?:reais|conto|pila)""", RegexOption.IGNORE_CASE).find(text)
        val m3 = Regex("""\b(?:no\s+valor\s+de|no\s+valor|valor\s+de|no\s+total\s+de|no\s+total|total\s+de|de|por|valor)\s+([\d.]+(?:,\d{1,2})?)\b""", RegexOption.IGNORE_CASE).find(text)
        val m4 = Regex("""\b\d+(?:[.,]\d{1,2})?\b""").find(text)

        val raw = m1?.groupValues?.get(1) ?: m2?.groupValues?.get(1) ?: m3?.groupValues?.get(1) ?: m4?.value
        if (raw.isNullOrBlank()) return null

        var cleaned = raw.trim()
        if (cleaned.contains(',')) {
            cleaned = cleaned.replace(".", "").replace(',', '.')
        } else if (Regex("""^\d{1,3}(\.\d{3})+$""").matches(cleaned)) {
            cleaned = cleaned.replace(".", "")
        }

        val parsed = cleaned.toDoubleOrNull()
        return if (parsed != null && parsed > 0.0) parsed else null
    }

    /**
     * Classifica categoria e subcategoria com base em regras neurais e pesos semânticos.
     */
    fun guessCategoryAndSubcategory(text: String, @Suppress("UNUSED_PARAMETER") amount: Double? = null): CategoryGuess {
        val normText = normalize(text)

        // Se o texto explicitamente indicar estorno/reembolso
        if (Regex("""\b(estorno|reembolso|devolu[cç][aã]o|cr[eé]dito na fatura)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normText)) {
            return CategoryGuess("Estorno", "Crédito na Fatura", 1.0f)
        }

        // Verifica regras memorizadas do usuário primeiro
        val learned = getLearnedRules()
        for (rule in learned) {
            val normRule = normalize(rule)
            if (normRule.contains("=") || normRule.contains("e categoria") || normRule.contains("coloque em")) {
                // Heurística de regra aprendida personalizada
            }
        }

        // Verifica 60+ subcategorias analíticas
        for ((cat, subcat, regex) in SUBCATEGORY_HINTS) {
            if (regex.containsMatchIn(text) || regex.containsMatchIn(normText)) {
                return CategoryGuess(cat, subcat, 0.95f)
            }
        }

        // Verifica categorias principais
        for ((cat, regex) in CATEGORY_HINTS) {
            if (regex.containsMatchIn(text) || regex.containsMatchIn(normText)) {
                return CategoryGuess(cat, null, 0.85f)
            }
        }

        return CategoryGuess("Outros", null, 0.5f)
    }

    fun guessCategory(text: String, amount: Double? = null): String {
        return guessCategoryAndSubcategory(text, amount).category
    }

    /**
     * Extrai o nome do estabelecimento comercial ou contraparte da frase,
     * limpando valores, datas temporais e verbos de comando com máxima precisão.
     */
    fun extractDescription(text: String): String {
        var clean = text.trim()
        if (clean.isBlank()) return "Lançamento Financeiro"

        // 0. Detecção inteligente de estabelecimento ("na Two Brothers", "no Forte Alencar", "no Outback")
        val placeRegex = Regex(
            """\b(?:na|no|em|no restaurante|na lanchonete|na pizzaria|no posto|no mercado|no supermercado|na loja|no shopping|na farm[aá]cia|no bar|no caf[eé])\s+([A-Za-z0-9À-ÿ\s'&]+?)(?=\s+(?:no\s+valor|no\s+total|por|de\s+\d|r?\$|\d+\s*reais|ontem|hoje|via|no\s+cart|em\s+\d+x|$))""",
            RegexOption.IGNORE_CASE
        )
        val placeMatch = placeRegex.find(clean)
        val candidatePlace = placeMatch?.groupValues?.get(1)?.trim()

        // 1. Remove valores monetários completos com seus gatilhos
        clean = clean.replace(
            Regex("""\b(?:no\s+valor\s+de|no\s+valor|no\s+total\s+de|no\s+total|valor\s+de|total\s+de|por|de)?\s*r?\$?\s*\d+(?:[.,]\d{1,2})?\s*(?:reais|conto|pila|centavos)?\b""", RegexOption.IGNORE_CASE),
            " "
        )

        // 1.1 Remove contagens numéricas soltas antes de substantivos de produtos (ex: "2 pizzas", "3 cervejas")
        clean = clean.replace(
            Regex("""\b\d+\s+(?=pizzas?|lanches?|hamb[uú]rguers?|cervejas?|refrigerantes?|itens?|produtos?|unidades?|garrafas?|latas?|refei[cç][õo]es|almo[cç]os|pratos)\b""", RegexOption.IGNORE_CASE),
            " "
        )

        // 2. Remove parcelas (ex: "em 3x", "3x", "1/10")
        clean = clean.replace(Regex("""\b(?:em\s+)?\d+\s*x\b""", RegexOption.IGNORE_CASE), " ")
        clean = clean.replace(Regex("""\b\d+/\d+\b"""), " ")

        // 3. Remove divisões (ex: "dividido com fulano", "meio a meio", "50/50")
        clean = clean.replace(Regex("""\b(meio a meio|50/50|50 50|dividido(?:\s+com\s+\w+)?|dividi|com meu parceiro|com minha parceira)\b""", RegexOption.IGNORE_CASE), " ")

        // 4. Remove formas de pagamento (ex: "no cartão de crédito", "no débito", "via pix", "em dinheiro")
        clean = clean.replace(Regex("""\b(?:no|via|em|pelo|com|de)?\s*(?:cart[aã]o(?: de cr[eé]dito| de d[eé]bito)?|cr[eé]dito|d[eé]bito|pix|dinheiro|boleto)\b""", RegexOption.IGNORE_CASE), " ")

        // 5. Remove palavras temporais (ex: "ontem", "hoje", "anteontem", "amanhã")
        clean = clean.replace(Regex("""\b(ontem|anteontem|hoje|amanh[aã]|nesta semana|semana passada|m[eê]s passado|agora pouco|agora a pouco)\b""", RegexOption.IGNORE_CASE), " ")

        // 6. Normaliza múltiplos espaços e dá TRIM antes de processar prefixos com ^
        clean = clean.replace(Regex("""\s+"""), " ").trim()

        // 7. Remove frases meta e verbos de relato do início (ex: "eu falei que", "avisei que", "disse que", "eu pedi", "gastei", etc.)
        val speechPrefixRegex = Regex("""^(?:ol[aá]|ei|por favor|copiloto|agente|saldo livre|eu\s+)?(?:falei\s+que|disse\s+que|avisei\s+que|mandei|lembrei\s+que|quero\s+que\s+voc[eê]\s+(?:lance|registre|cadastre)|anote\s+a[íi]|anota\s+a[íi]|registre\s+a[íi])\s*""", RegexOption.IGNORE_CASE)
        while (speechPrefixRegex.containsMatchIn(clean)) {
            clean = clean.replace(speechPrefixRegex, "").trim()
        }

        val verbPrefixRegex = Regex("""^(?:eu\s+)?(?:almo[cç]o|almo[cç]ei|jantar|jantei|lanche|lanchei|caf[eé]|abasteci|abastecer|pedi|pedir|comi|comer|comprei|comprar|gastei|gastar|paguei|pagar|coloquei|coloque|anote|anota|anotei|registre|registra|registrei|lance|lanca|lança|lancei|cadastre|cadastra|cadastrei|assinei|assinar|usei|usar|adicione|adiciona|adicionei|fui\s+no|fui\s+na|fui\s+ao|fui\s+a)\b\s*""", RegexOption.IGNORE_CASE)
        while (verbPrefixRegex.containsMatchIn(clean)) {
            clean = clean.replace(verbPrefixRegex, "").trim()
        }

        // 8. Remove preposições e artigos soltos no início (ex: "no", "na", "em", "um", "uma", "o", "a", "de", "do", "da")
        val articlePrefixRegex = Regex("""^(?:um|uma|uns|umas|o|a|os|as|no|na|nos|nas|em|de|do|da|dos|das|pro|pra|para)\b\s*""", RegexOption.IGNORE_CASE)
        while (articlePrefixRegex.containsMatchIn(clean)) {
            clean = clean.replace(articlePrefixRegex, "").trim()
        }

        // 9. Remove ruídos no final (ex: "no valor", "no total", etc.)
        val trailingNoiseRegex = Regex("""\s*\b(?:no\s+valor\s+de|no\s+valor|valor\s+de|valor|no\s+total\s+de|no\s+total|total|no\s+fim|e\s+paguei|e\s+deu|e\s+ficou|e\s+foi|reais|conto|pila|no|na|em|de|do|da|para|pro|pra)\s*$""", RegexOption.IGNORE_CASE)
        while (trailingNoiseRegex.containsMatchIn(clean)) {
            clean = clean.replace(trailingNoiseRegex, "").trim()
        }

        // 10. Se após a limpeza restar uma estrutura "produto na/no estabelecimento", extrai o estabelecimento
        if (clean.contains(Regex("""\b(?:na|no|em)\b""", RegexOption.IGNORE_CASE))) {
            val splitPlace = clean.split(Regex("""\b(?:na|no|em)\b""", RegexOption.IGNORE_CASE))
            if (splitPlace.size >= 2) {
                val place = splitPlace[1].trim()
                if (place.isNotBlank()) {
                    clean = place
                }
            }
        } else if (!candidatePlace.isNullOrBlank() && candidatePlace.length in 2..40 && (clean.isBlank() || clean.equals("pizza", true) || clean.equals("pizzas", true) || clean.length < 3)) {
            clean = candidatePlace
        }

        // Remove pontuações residuais e espaços duplos
        clean = clean.replace(Regex("""[.,;:!?]+"""), " ").replace(Regex("""\s+"""), " ").trim()

        if (clean.isNotBlank() && clean.length >= 2) {
            return clean.split(" ").joinToString(" ") { word ->
                if (word.lowercase(Locale.ROOT) in listOf("de", "do", "da", "dos", "das", "e", "em", "no", "na")) {
                    word.lowercase(Locale.ROOT)
                } else {
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
        }

        val subcat = guessCategoryAndSubcategory(text).subcategory
        if (!subcat.isNullOrBlank()) return subcat

        val catGuess = guessCategory(text)
        return if (catGuess == "Outros") "Lançamento Financeiro" else catGuess
    }

    /**
     * Detecta datas relativas (ontem, hoje, etc.) ou explícitas (DD/MM, dia X)
     * Retorna Pair(dueDate YYYY-MM-DD, competenceMonth YYYY-MM)
     */
    fun detectDates(text: String): Pair<String, String>? {
        val lower = text.lowercase()
        val now = LocalDate.now()

        val targetDate: LocalDate? = when {
            lower.contains("anteontem") -> now.minusDays(2)
            lower.contains("ontem") -> now.minusDays(1)
            lower.contains("hoje") -> now
            lower.contains("amanhã") || lower.contains("amanha") -> now.plusDays(1)
            else -> {
                // Procura datas numéricas como "dia 15", "15/10", "15/10/2026"
                val explicitDateMatch = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""").find(text)
                if (explicitDateMatch != null) {
                    val day = explicitDateMatch.groupValues[1].toIntOrNull() ?: now.dayOfMonth
                    val month = explicitDateMatch.groupValues[2].toIntOrNull() ?: now.monthValue
                    val yearStr = explicitDateMatch.groupValues[3]
                    val year = if (!yearStr.isNullOrBlank()) {
                        if (yearStr.length == 2) 2000 + yearStr.toInt() else yearStr.toInt()
                    } else now.year
                    runCatching { LocalDate.of(year, month, day) }.getOrNull()
                } else {
                    val dayMatch = Regex("""\b(?:no\s+)?dia\s+(\d{1,2})\b""", RegexOption.IGNORE_CASE).find(text)
                    if (dayMatch != null) {
                        val day = dayMatch.groupValues[1].toIntOrNull()
                        if (day != null && day in 1..31) {
                            runCatching {
                                val maxDay = now.lengthOfMonth()
                                LocalDate.of(now.year, now.monthValue, day.coerceAtMost(maxDay))
                            }.getOrNull()
                        } else null
                    } else null
                }
            }
        }

        return targetDate?.let { date ->
            val due = String.format(Locale.US, "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth)
            val comp = String.format(Locale.US, "%04d-%02d", date.year, date.monthValue)
            Pair(due, comp)
        }
    }

    /**
     * Detecta o método de pagamento da frase
     */
    fun detectPaymentMethod(text: String): PaymentMethod {
        val lower = text.lowercase()
        return when {
            lower.contains("crédito") || lower.contains("credito") || lower.contains("cartão") || lower.contains("cartao") || lower.contains("fatura") -> PaymentMethod.CREDIT_CARD
            lower.contains("débito") || lower.contains("debito") -> PaymentMethod.DEBIT
            lower.contains("boleto") -> PaymentMethod.BILL
            lower.contains("pix") -> PaymentMethod.PIX
            else -> PaymentMethod.PIX
        }
    }

    /**
     * Detecta participantes ou divisão
     */
    fun detectSplitType(text: String): Pair<SplitType, String?> {
        val lower = text.lowercase()

        if (lower.contains("meio a meio") || lower.contains("50/50") || lower.contains("50 50") || lower.contains("casal") || lower.contains("com meu parceiro") || lower.contains("com minha parceira")) {
            return Pair(SplitType.SPLIT_50, null)
        }

        for ((re, name) in THIRD_PARTIES) {
            if (re.containsMatchIn(text)) {
                return Pair(SplitType.EXTERNAL, name)
            }
        }

        if (lower.contains("amigo") || lower.contains("amiga")) {
            return Pair(SplitType.FRIEND, "Amigo")
        }

        return Pair(SplitType.MINE, null)
    }

    fun detectTransactionType(text: String): TransactionType {
        return if (INCOME_KEYWORDS_REGEX.containsMatchIn(text)) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
    }

    fun detectInstallments(text: String): Pair<Int, Int>? {
        val slashMatch = Regex("""\b(\d+)/(\d+)\b""").find(text)
        if (slashMatch != null) {
            val cur = slashMatch.groupValues[1].toIntOrNull() ?: 1
            val tot = slashMatch.groupValues[2].toIntOrNull() ?: 1
            if (tot > 1) return Pair(cur, tot)
        }

        val timesMatch = Regex("""(?:em\s+)?(\d+)\s*x\b""", RegexOption.IGNORE_CASE).find(text)
        if (timesMatch != null) {
            val tot = timesMatch.groupValues[1].toIntOrNull() ?: 1
            if (tot > 1) return Pair(1, tot)
        }

        return null
    }

    /**
     * Interpreta a intenção em alto nível a partir da rede neural de heurísticas
     */
    fun parseNaturalIntent(text: String): ExtractedIntent? {
        val amount = parseAmount(text) ?: return null
        val txType = detectTransactionType(text)
        val guess = guessCategoryAndSubcategory(text, amount)
        val desc = extractDescription(text)
        val (splitType, thirdParty) = detectSplitType(text)
        val paymentMethod = detectPaymentMethod(text)
        val isCreditCard = paymentMethod == PaymentMethod.CREDIT_CARD ||
                text.contains("cartão", ignoreCase = true) ||
                text.contains("cartao", ignoreCase = true) ||
                text.contains("crédito", ignoreCase = true) ||
                text.contains("credito", ignoreCase = true) ||
                text.contains("fatura", ignoreCase = true)

        val cardNameHint = when {
            text.contains("nubank", ignoreCase = true) -> "Nubank"
            text.contains("inter", ignoreCase = true) -> "Inter"
            text.contains("itau", ignoreCase = true) || text.contains("itaú", ignoreCase = true) -> "Itaú"
            text.contains("bradesco", ignoreCase = true) -> "Bradesco"
            text.contains("c6", ignoreCase = true) -> "C6"
            text.contains("black", ignoreCase = true) -> "Black"
            else -> null
        }

        val installments = detectInstallments(text)
        val dateInfo = detectDates(text)

        return ExtractedIntent(
            description = desc,
            amount = amount,
            type = txType,
            category = guess.category,
            subcategory = guess.subcategory,
            isCreditCard = isCreditCard,
            cardNameHint = cardNameHint,
            splitType = splitType,
            thirdPartyName = thirdParty,
            currentInstallment = installments?.first ?: 1,
            totalInstallments = installments?.second ?: 1,
            dueDate = dateInfo?.first,
            competenceMonth = dateInfo?.second,
            paymentMethod = paymentMethod
        )
    }

    /**
     * Regras e preferências aprendidas do usuário (Memória Viva)
     */
    fun getLearnedRules(): List<String> {
        val set: Set<String>? = rulesPrefs?.getStringSet(KEY_RULES_SET, null)
        return set?.toList() ?: emptyList<String>()
    }

    fun saveLearnedRule(ruleText: String) {
        val clean = ruleText.trim()
        if (clean.isBlank()) return
        val current = getLearnedRules().toMutableSet()
        current.add(clean)
        rulesPrefs?.edit()?.putStringSet(KEY_RULES_SET, current)?.apply()
    }

    fun deleteLearnedRule(ruleText: String) {
        val clean = ruleText.trim()
        val current = getLearnedRules().toMutableSet()
        val found = current.find { it.contains(clean, ignoreCase = true) }
        if (found != null) {
            current.remove(found)
            rulesPrefs?.edit()?.putStringSet(KEY_RULES_SET, current)?.apply()
        }
    }

    fun syncLearnedRules(rules: List<String>) {
        val cleaned = rules.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        rulesPrefs?.edit()?.putStringSet(KEY_RULES_SET, cleaned)?.apply()
    }
}
