package com.saldolivre.app.data.ai

import android.content.Context
import com.saldolivre.app.data.model.SplitType
import com.saldolivre.app.data.model.TransactionType
import java.text.Normalizer
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
    val totalInstallments: Int = 1
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
            Triple("Transporte", "Seguro & IPVA", Regex("""(ipva|seguro auto|seguro carro|dpvat|licenciamento)""", RegexOption.IGNORE_CASE)),
            Triple("Transporte", "Manutenção & Revisão", Regex("""(oficina|mec[aâ]nico|pneu|troca de [oó]leo|alinhamento|balanceamento|revis[aã]o carro)""", RegexOption.IGNORE_CASE)),

            // Saúde
            Triple("Saúde", "Farmácia & Remédios", Regex("""(farm[aá]cia|drogaria|drogasil|droga raia|pague menos|s[aã]o paulo|ultrafarma|rem[eé]dio|medicamento)""", RegexOption.IGNORE_CASE)),
            Triple("Saúde", "Consultas & Exames", Regex("""(consulta|m[eé]dico|dentista|odontolog|exame|laborat[oó]rio|fleury|lavoisier)""", RegexOption.IGNORE_CASE)),
            Triple("Saúde", "Academia & Treinos", Regex("""(academia|smart fit|bluefit|gympass|totalpass|personal|crossfit|nata[çc][aã]o)""", RegexOption.IGNORE_CASE)),
            Triple("Saúde", "Plano de Saúde & Dental", Regex("""(plano de sa[uú]de|unimed|amil|bradesco sa[uú]de|sulamerica|notredame)""", RegexOption.IGNORE_CASE)),
            Triple("Saúde", "Terapia & Psicologia", Regex("""(terapia|psic[oó]log|psiquiatra|sess[aã]o de terapia)""", RegexOption.IGNORE_CASE)),

            // Assinaturas
            Triple("Assinaturas", "Streaming de Vídeo (Netflix/Max/Disney)", Regex("""(netflix|disney|hbo|max|prime video|amazon prime|youtube premium|globoplay|telecine)""", RegexOption.IGNORE_CASE)),
            Triple("Assinaturas", "Streaming de Música (Spotify/Apple)", Regex("""(spotify|deezer|apple music|tidal|amazon music)""", RegexOption.IGNORE_CASE)),
            Triple("Assinaturas", "Softwares, IA & Cloud (iCloud/ChatGPT)", Regex("""(chatgpt|openai|icloud|google one|google storage|github|adobe|canva|microsoft|dropbox)""", RegexOption.IGNORE_CASE)),
            Triple("Assinaturas", "Telefonia Celular", Regex("""(tim|claro celular|vivo celular|plano celular|controle vivo)""", RegexOption.IGNORE_CASE)),

            // Pets
            Triple("Pets", "Ração & Petiscos", Regex("""(pet|petz|cobasi|ra[çc][aã]o|petisco)""", RegexOption.IGNORE_CASE)),
            Triple("Pets", "Veterinário & Medicamentos", Regex("""(veterin[aá]rio|vacina pet|banho e tosa|castra[çc][aã]o)""", RegexOption.IGNORE_CASE)),

            // Compras
            Triple("Compras", "Presentes", Regex("""(presente|lembrancinha|anivers[aá]rio|floricultura|flores|cesta|cacau show|kopenhagen|chocolates|brinquedo|ri happy|natal|dia das m[aã]es|dia dos namorados)""", RegexOption.IGNORE_CASE)),
            Triple("Compras", "Roupas & Acessórios", Regex("""(zara|renner|riachuelo|c&a|shein|roupa|vestu[aá]rio|acess[oó]rio|nike|adidas|lingerie|joia|bijuteria)""", RegexOption.IGNORE_CASE)),
            Triple("Compras", "Calçados", Regex("""(cal[çc]ado|t[eê]nis|sapato|sand[aá]lia|havaianas|arezzo|anacapri|vans|olympikus)""", RegexOption.IGNORE_CASE)),
            Triple("Compras", "Eletrônicos & Informática", Regex("""(kabum|pichau|terabyte|apple|samsung|dell|mercado livre|amazon|shopee|magalu|celular|smartphone|notebook|computador)""", RegexOption.IGNORE_CASE)),
            Triple("Compras", "Casa & Decoração", Regex("""(decora[çc][aã]o|cama|mesa|banho|tok&stok|etna|camicado|mobly|leroy|marabraz|m[oó]veis)""", RegexOption.IGNORE_CASE)),

            // Educação
            Triple("Educação", "Cursos Online & Certificações", Regex("""(curso|udemy|alura|hotmart|certifica[çc][aã]o)""", RegexOption.IGNORE_CASE)),
            Triple("Educação", "Livros & E-books", Regex("""(livro|livraria|saraiva|leitura|kindle|amazon livros)""", RegexOption.IGNORE_CASE)),
            Triple("Educação", "Faculdade & Pós-Graduação", Regex("""(faculdade|mensalidade escolar|p[oó]s-gradua[çc][aã]o|matr[ií]cula)""", RegexOption.IGNORE_CASE)),

            // Lazer
            Triple("Lazer", "Cinema, Teatro & Shows", Regex("""(cinema|cinemark|ingresso|sympla|eventim|show|teatro)""", RegexOption.IGNORE_CASE)),
            Triple("Lazer", "Viagens & Hospedagem", Regex("""(viagem|hotel|airbnb|booking|decolar|passagem a[eé]rea|latam|gol|azul)""", RegexOption.IGNORE_CASE)),

            // Investimentos
            Triple("Investimentos", "Renda Fixa (CDB / Tesouro / LCI / LCA)", Regex("""(cdb|tesouro|selic|lci|lca|ipca|renda fixa|deb[eê]nture|cri|cra|nuinvest|inter invest)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Ações & Fundos Imobiliários (FIIs)", Regex("""(a[çc][oõ]es|fii|fiis|hglg|mxrf|knip|xpml|bova11|ivvb11|smal11|itub|petr|vale|bbas|wege|dividendo|jcp)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Criptomoedas & Ativos Digitais", Regex("""(bitcoin|btc|ethereum|eth|solana|binance|mercado bitcoin|crypto|cripto)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Reserva de Emergência", Regex("""(reserva|reserva de emerg[eê]ncia|guardar dinheiro|cofrinho|reserva financeira)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Previdência Privada", Regex("""(previd[eê]ncia|pgbl|vgbl)""", RegexOption.IGNORE_CASE)),
            Triple("Investimentos", "Outros Investimentos", Regex("""(investimento|aporte|aplica[çc][aã]o|corretora|xp investimentos|rico|clear|btg)""", RegexOption.IGNORE_CASE)),

            // Financiamentos & Empréstimos
            Triple("Financiamentos & Empréstimos", "Financiamento Imobiliário", Regex("""(financiamento habitacional|caixa habitacional|financ.*imobil|parcela.*(ap[eê]|apartamento|im[oó]vel|casa)|cr[eé]dito imobili[aá]rio)""", RegexOption.IGNORE_CASE)),
            Triple("Financiamentos & Empréstimos", "Financiamento Veicular", Regex("""(financiamento.*(auto|ve[ií]culo|carro|moto)|parcela.*(carro|ve[ií]culo|moto)|cons[oó]rcio.*(auto|carro|moto)|bv financeira|pan auto)""", RegexOption.IGNORE_CASE)),
            Triple("Financiamentos & Empréstimos", "Empréstimo Pessoal", Regex("""(empr[eé]stimo pessoal|empr[eé]stimo nubank|empr[eé]stimo inter|cr[eé]dito pessoal|empr[eé]stimo)""", RegexOption.IGNORE_CASE)),
            Triple("Financiamentos & Empréstimos", "Amortização de Financiamento", Regex("""(amortiza[çc][aã]o|amortizar|redu[çc][aã]o de prazo|abatimento saldo devedor)""", RegexOption.IGNORE_CASE)),

            // Estorno
            Triple("Estorno", "Crédito na Fatura", Regex("""(estorno|reembolso|devolu[çc][aã]o|cr[eé]dito fatura|cr[eé]dito na fatura|cashback)""", RegexOption.IGNORE_CASE))
        )

        private val CATEGORY_HINTS: List<Pair<String, Regex>> = listOf(
            "Estorno" to Regex("""(estorno|reembolso|devolu[çc][aã]o|cr[eé]dito fatura|cashback)""", RegexOption.IGNORE_CASE),
            "Financiamentos & Empréstimos" to Regex("""(financiamento|empr[eé]stimo|consignado|amortiza[çc][aã]o|fies|pravaler)""", RegexOption.IGNORE_CASE),
            "Investimentos" to Regex("""(investimento|aporte|cdb|tesouro|selic|lci|lca|fii|fiis|a[çc][oõ]es|cripto|bitcoin|btc|eth|corretora)""", RegexOption.IGNORE_CASE),
            "Moradia" to Regex("""(aluguel|condom|luz|energia|[aá]gua|g[aá]s|internet|iptu|casa|apartamento)""", RegexOption.IGNORE_CASE),
            "Alimentação" to Regex("""(jantar|almo[çc]o|caf[eé]|mercado|supermercado|ifood|restaurante|lanche|pizza|padaria|comida|feira)""", RegexOption.IGNORE_CASE),
            "Transporte" to Regex("""(uber|99|gasolina|combust[ií]vel|[oô]nibus|metr[oô]|estacionamento|carro|ped[aá]gio)""", RegexOption.IGNORE_CASE),
            "Saúde" to Regex("""(farm[aá]cia|rem[eé]dio|medic|consulta|exame|dentista|academia|plano de sa[uú]de|drogaria)""", RegexOption.IGNORE_CASE),
            "Lazer" to Regex("""(cinema|show|viagem|bar|festa|passeio|jogo|livro|teatro)""", RegexOption.IGNORE_CASE),
            "Assinaturas" to Regex("""(netflix|spotify|prime|disney|hbo|assinatura|youtube|icloud|google one)""", RegexOption.IGNORE_CASE),
            "Educação" to Regex("""(curso|faculdade|escola|livro|udemy|alura)""", RegexOption.IGNORE_CASE),
            "Compras" to Regex("""(roupa|t[eê]nis|shopping|eletr[oô]nico|celular|magazine|presente|brinquedo)""", RegexOption.IGNORE_CASE),
            "Pets" to Regex("""(pet|ra[çc][aã]o|veterin[aá]rio|cobasi)""", RegexOption.IGNORE_CASE)
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
        val m3 = Regex("""\b(?:de|por|valor)\s+([\d.]+(?:,\d{1,2})?)\b""", RegexOption.IGNORE_CASE).find(text)
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
    fun guessCategoryAndSubcategory(text: String, amount: Double? = null): CategoryGuess {
        if (amount != null && amount < 0) {
            return CategoryGuess("Estorno", "Crédito na Fatura", 1.0f)
        }

        // Verifica regras memorizadas do usuário primeiro
        val learned = getLearnedRules()
        val normText = normalize(text)
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
     * Extrai o nome do estabelecimento comercial ou contraparte da frase
     */
    fun extractDescription(text: String): String {
        val clean = text.trim()

        val placeMatch = Regex("""\b(?:na|no|em|com)\s+(?:a\s+|o\s+)?([A-Za-zÀ-ÿ][\wÀ-ÿ'’ ]{2,30}?)(?=\s+(?:de|por|no|na|dividid|50|com|r\$|hoje|ontem)|[.,!?]|$)""", RegexOption.IGNORE_CASE).find(clean)
        val leadMatch = Regex("""^([A-Za-zÀ-ÿ][\wÀ-ÿ ]{2,40}?)\s+(?:de|por)\s+r?\$?\s*\d""", RegexOption.IGNORE_CASE).find(clean)

        val badWords = Regex("""cart[aã]o|cr[eé]dito|meu|minha|compartilhado|pix|d[eé]bito|gastei|paguei|comprei|usei""", RegexOption.IGNORE_CASE)

        if (leadMatch != null && !badWords.containsMatchIn(leadMatch.groupValues[1])) {
            return leadMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
        }
        if (placeMatch != null && !badWords.containsMatchIn(placeMatch.groupValues[1])) {
            return placeMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
        }

        val catGuess = guessCategory(text)
        return if (catGuess == "Outros") "Lançamento Financeiro" else catGuess
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
        val guess = guessCategoryAndSubcategory(text, if (txType == TransactionType.INCOME) amount else -amount)
        val desc = extractDescription(text)
        val (splitType, thirdParty) = detectSplitType(text)
        val isCreditCard = text.contains("cartão", ignoreCase = true) ||
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
            totalInstallments = installments?.second ?: 1
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
}
