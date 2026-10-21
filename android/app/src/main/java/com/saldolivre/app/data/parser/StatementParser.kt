package com.saldolivre.app.data.parser

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.TransactionType
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.InflaterInputStream

@Immutable
data class ParsedStatementItem(
    val id: String = UUID.randomUUID().toString(),
    val rawLine: String = "",
    val date: String = "", // YYYY-MM-DD
    val description: String = "",
    val counterparty: String = "",
    val amount: Double = 0.0,
    val type: TransactionType = TransactionType.EXPENSE,
    val paymentMethod: PaymentMethod = PaymentMethod.CREDIT_CARD,
    val category: String = "Outros",
    val subcategory: String? = null,
    val isIgnored: Boolean = false,
    val ignoredReason: String? = null,
    val installmentNumber: Int? = null,
    val installmentsTotal: Int? = null,
    val isSelected: Boolean = true,
    val targetCardId: String? = null
)

object StatementParser {

    private val MONTH_MAP = mapOf(
        "jan" to "01", "fev" to "02", "mar" to "03", "abr" to "04",
        "mai" to "05", "jun" to "06", "jul" to "07", "ago" to "08",
        "set" to "09", "out" to "10", "nov" to "11", "dez" to "12"
    )

    private val RDB_PATTERNS = listOf(
        Regex("""\b(aplica[çc][ãa]o|resgate)\s+rdb\b""", RegexOption.IGNORE_CASE),
        Regex("""\brdb\s+(resgate|aplica[çc][ãa]o|nubank)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bguardar\s+dinheiro\b""", RegexOption.IGNORE_CASE),
        Regex("""\bresgate\s+(de\s+investimento|di[áa]rio)\b""", RegexOption.IGNORE_CASE),
        Regex("""\brendimento\s+l[íi]quido\s+rdb\b""", RegexOption.IGNORE_CASE)
    )

    private val INVOICE_PAYMENT_PATTERNS = listOf(
        Regex("""pagamento (recebido|de fatura|efetuado)""", RegexOption.IGNORE_CASE),
        Regex("""pagamento de fatura recebido""", RegexOption.IGNORE_CASE),
        Regex("""saldo anterior""", RegexOption.IGNORE_CASE),
        Regex("""total a pagar""", RegexOption.IGNORE_CASE),
        Regex("""limite disponivel""", RegexOption.IGNORE_CASE),
        Regex("""data de vencimento""", RegexOption.IGNORE_CASE),
        Regex("""demonstrativo""", RegexOption.IGNORE_CASE)
    )

    private val SUBCATEGORY_HINTS = listOf(
        // Alimentação
        Triple("Alimentação", "Supermercado", Regex("""mercado|supermercado|assai|assaí|carrefour|pao de acucar|pão de açúcar|extra|atacadao|atacadão|dia%|hortifruti|feira|sacolao|sacolão""", RegexOption.IGNORE_CASE)),
        Triple("Alimentação", "Delivery & Lanches", Regex("""ifood|rappi|uber eats|lanche|burger|hamburguer|mc donalds|mcdonalds|burguer king|bk |subway|habib|delivery|habbib""", RegexOption.IGNORE_CASE)),
        Triple("Alimentação", "Restaurante", Regex("""restaurante|outback|madero|churrascaria|coco bambu|fogao|fogão|parrilla|cantina|pizzaria|sushi|almoço|almoco|jantar""", RegexOption.IGNORE_CASE)),
        Triple("Alimentação", "Padaria & Café", Regex("""padaria|panificadora|cafe|café|starbucks|confeitaria|doceria|doce""", RegexOption.IGNORE_CASE)),
        Triple("Alimentação", "Bares & Bebidas", Regex("""bar|cerveja|chopp|adega|pub|bebidas|vinho""", RegexOption.IGNORE_CASE)),

        // Transporte
        Triple("Transporte", "Combustível", Regex("""gasolina|combustivel|combustível|etanol|posto|ipiranga|shell|br distribuidora|abastec""", RegexOption.IGNORE_CASE)),
        Triple("Transporte", "Aplicativos (Uber/99)", Regex("""uber|99|indrive|cabify|pop|corrida""", RegexOption.IGNORE_CASE)),
        Triple("Transporte", "Estacionamento & Pedágio", Regex("""estacionamento|pedagio|pedágio|sem parar|veloe|conectcar|taggy|estapar""", RegexOption.IGNORE_CASE)),
        Triple("Transporte", "Transporte Público (Metrô/Ônibus)", Regex("""onibus|ônibus|metro|metrô|bilhete unico|cptm|sptrans""", RegexOption.IGNORE_CASE)),

        // Moradia
        Triple("Moradia", "Aluguel & Condomínio", Regex("""aluguel|condominio|condomínio|locacao|locação|imobiliaria""", RegexOption.IGNORE_CASE)),
        Triple("Moradia", "Energia Elétrica", Regex("""luz|energia|enel|cpfl|cemig|coelba|light|eletropaulo|energisa""", RegexOption.IGNORE_CASE)),
        Triple("Moradia", "Água & Saneamento", Regex("""agua|água|sabesp|sanepar|copasa|cedae|caesb""", RegexOption.IGNORE_CASE)),
        Triple("Moradia", "Internet & TV a Cabo", Regex("""internet|fibra|claro|vivo|oi |tim fibra|brisanet|net virtua""", RegexOption.IGNORE_CASE)),
        Triple("Moradia", "Gás", Regex("""gas|gás|ultragaz|liquigas|comgas""", RegexOption.IGNORE_CASE)),

        // Saúde
        Triple("Saúde", "Farmácia & Remédios", Regex("""farmacia|farmácia|drogaria|drogasil|droga raia|pacheco|sao paulo|são paulo|panvel|remedio|remédio|medicamento""", RegexOption.IGNORE_CASE)),
        Triple("Saúde", "Consultas & Exames", Regex("""consulta|exame|laboratorio|laboratório|dentista|oftalmo|medico|médico|clinica|clínica""", RegexOption.IGNORE_CASE)),
        Triple("Saúde", "Academia & Treinos", Regex("""academia|smartfit|smart fit|bluefit|bio ritmo|musculacao|musculação|pilates|crossfit|natacao|natação""", RegexOption.IGNORE_CASE)),

        // Assinaturas
        Triple("Assinaturas", "Streaming de Vídeo (Netflix/Max/Disney)", Regex("""netflix|disney|hbo|max|prime video|amazon prime|youtube premium|globoplay|telecine""", RegexOption.IGNORE_CASE)),
        Triple("Assinaturas", "Streaming de Música (Spotify/Apple)", Regex("""spotify|deezer|apple music|tidal|amazon music""", RegexOption.IGNORE_CASE)),
        Triple("Assinaturas", "Softwares, IA & Cloud (iCloud/ChatGPT)", Regex("""chatgpt|openai|icloud|google one|google storage|github|adobe|canva|microsoft|dropbox""", RegexOption.IGNORE_CASE)),

        // Pets
        Triple("Pets", "Ração & Petiscos", Regex("""pet|petz|cobasi|racao|ração|petisco""", RegexOption.IGNORE_CASE)),
        Triple("Pets", "Veterinário & Medicamentos", Regex("""veterinario|veterinário|vacina pet""", RegexOption.IGNORE_CASE)),

        // Compras
        Triple("Compras", "Presentes", Regex("""presente|presentes|lembrancinha|lembranca|lembrança|aniversario|aniversário|floricultura|flores|cesta|cacau show|kopenhagen|chocolates|brinquedo|ri happy""", RegexOption.IGNORE_CASE)),
        Triple("Compras", "Roupas & Acessórios", Regex("""zara|renner|riachuelo|c&a|shein|roupa|vestuario|vestuário|acessorio|acessório|nike|adidas|lingerie|joia|bijuteria""", RegexOption.IGNORE_CASE)),
        Triple("Compras", "Calçados", Regex("""calcado|calçado|tenis|tênis|sapato|sandalia|sandália|havaianas|arezzo|anacapri|vans|olympikus""", RegexOption.IGNORE_CASE)),
        Triple("Compras", "Eletrônicos & Informática", Regex("""kabum|pichau|terabyte|apple|samsung|dell|mercado livre|amazon|shopee|magalu|celular|smartphone|notebook|computador""", RegexOption.IGNORE_CASE)),
        Triple("Compras", "Casa & Decoração", Regex("""decoracao|decoração|cama|mesa|banho|tok&stok|tok e stok|etna|camicado|mobly|leroy|marabraz|utilidade|moveis|móveis""", RegexOption.IGNORE_CASE)),

        // Educação
        Triple("Educação", "Cursos Online & Certificações", Regex("""curso|udemy|alura|hotmart""", RegexOption.IGNORE_CASE)),
        Triple("Educação", "Livros & E-books", Regex("""livro|livraria|saraiva|leitura|kindle""", RegexOption.IGNORE_CASE)),

        // Lazer
        Triple("Lazer", "Cinema, Teatro & Shows", Regex("""cinema|cinemark|ingresso|sympla|eventim|show|teatro""", RegexOption.IGNORE_CASE)),
        Triple("Lazer", "Viagens & Hospedagem", Regex("""viagem|hotel|airbnb|booking|decolar|passagem aerea|latam|gol|azul""", RegexOption.IGNORE_CASE)),

        // Investimentos
        Triple("Investimentos", "Renda Fixa (CDB / Tesouro / LCI / LCA)", Regex("""cdb|tesouro|selic|lci|lca|ipca|renda fixa|debênture|debenture""", RegexOption.IGNORE_CASE)),
        Triple("Investimentos", "Ações & Fundos Imobiliários (FIIs)", Regex("""acoes|ações|fii|fiis|hglg|mxrf|knip|xpml|bova11|ivvb11|smal11|dividendo|jcp""", RegexOption.IGNORE_CASE)),
        Triple("Investimentos", "Criptomoedas & Ativos Digitais", Regex("""bitcoin|btc|ethereum|eth|solana|binance|mercado bitcoin|crypto|cripto""", RegexOption.IGNORE_CASE)),

        // Financiamentos
        Triple("Financiamentos & Empréstimos", "Financiamento Imobiliário", Regex("""financiamento habitacional|habitacional|caixa habitacional|financ(?:iamento)? imobil|parcela (?:do |de )?(?:ape|apê|apartamento|imovel|imóvel|casa)""", RegexOption.IGNORE_CASE)),
        Triple("Financiamentos & Empréstimos", "Financiamento Veicular", Regex("""financiamento (?:auto|veiculo|veículo|carro|moto)|parcela (?:do |de )?(?:carro|veiculo|veículo|moto)|bv financeira|pan auto""", RegexOption.IGNORE_CASE)),
        Triple("Financiamentos & Empréstimos", "Empréstimo Pessoal", Regex("""emprestimo pessoal|empréstimo pessoal|emprestimo nubank|emprestimo inter|credito pessoal""", RegexOption.IGNORE_CASE)),

        // Estorno
        Triple("Estorno", "Crédito na Fatura", Regex("""estorno|reembolso|devolucao|devolução|credito fatura|crédito fatura|credit voucher|cashback""", RegexOption.IGNORE_CASE))
    )

    private val CATEGORY_HINTS = listOf(
        Pair("Estorno", Regex("""estorno|reembolso|devolucao|devolução|credito fatura|crédito fatura|credit voucher|cashback""", RegexOption.IGNORE_CASE)),
        Pair("Financiamentos & Empréstimos", Regex("""financiamento|emprestimo|empréstimo|consignado|amortizacao|amortização|fies|pravaler|parcela auto|parcela carro|parcela moto""", RegexOption.IGNORE_CASE)),
        Pair("Investimentos", Regex("""investimento|aporte|cdb|tesouro|selic|lci|lca|fii|fiis|acoes|ações|cripto|bitcoin|btc|eth|corretora""", RegexOption.IGNORE_CASE)),
        Pair("Moradia", Regex("""aluguel|condom|luz|energia|agua|gas|internet|iptu|casa|apartamento""", RegexOption.IGNORE_CASE)),
        Pair("Alimentação", Regex("""jantar|almoco|cafe|mercado|supermercado|ifood|restaurante|lanche|pizza|padaria|comida|feira""", RegexOption.IGNORE_CASE)),
        Pair("Transporte", Regex("""uber|99|gasolina|combustivel|onibus|metro|estacionamento|carro|pedagio""", RegexOption.IGNORE_CASE)),
        Pair("Saúde", Regex("""farmacia|remedio|medic|consulta|exame|dentista|academia|plano de saude|drogaria""", RegexOption.IGNORE_CASE)),
        Pair("Lazer", Regex("""cinema|show|viagem|bar|festa|passeio|jogo|livro|teatro""", RegexOption.IGNORE_CASE)),
        Pair("Assinaturas", Regex("""netflix|spotify|prime|disney|hbo|assinatura|youtube|icloud|google one""", RegexOption.IGNORE_CASE)),
        Pair("Educação", Regex("""curso|faculdade|escola|livro|udemy|alura""", RegexOption.IGNORE_CASE)),
        Pair("Compras", Regex("""roupa|tenis|shopping|eletronico|celular|magazine|presente|lembranc|brinquedo""", RegexOption.IGNORE_CASE)),
        Pair("Pets", Regex("""pet|racao|veterinario|cobasi""", RegexOption.IGNORE_CASE)),
        Pair("Receita", Regex("""salario|salário|folha|pro-?labore|ted recebida|pix recebido|deposito|depósito""", RegexOption.IGNORE_CASE))
    )

    /**
     * Limpa prefixos bancários comuns para isolar o recebedor, pagador ou estabelecimento
     */
    fun extractCounterparty(description: String): String {
        var clean = description
            .replace(Regex("""^(transfer[êe]ncia\s+(recebida|enviada)\s+(pelo|via)?\s*pix(\s+de)?)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(pix\s+(recebido|enviado)(\s+de|\s+para)?)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(ted\s+(recebida|enviada)(\s+de|\s+para)?)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(doc\s+(recebida|enviada)(\s+de|\s+para)?)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(compra\s+(no\s+d[ée]bito|no\s+cr[ée]dito|d[ée]bito|cr[ée]dito)?)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(pagamento\s+(de\s+boleto|efetuado|via\s+pix|de\s+fatura)?)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[\s:–—\-]+"""), "")
            .trim()

        if (clean.length < 2) return description.trim()
        return clean
    }

    /**
     * Infere Categoria e Subcategoria através de heurísticas semânticas
     */
    fun guessCategoryAndSubcategory(text: String, amount: Double? = null): Pair<String, String?> {
        if (amount != null && amount < 0) {
            return Pair("Estorno", "Crédito na Fatura")
        }
        for ((cat, sub, re) in SUBCATEGORY_HINTS) {
            if (re.containsMatchIn(text)) return Pair(cat, sub)
        }
        for ((cat, re) in CATEGORY_HINTS) {
            if (re.containsMatchIn(text)) return Pair(cat, null)
        }
        return Pair("Outros", null)
    }

    /**
     * Ponto de entrada para ler a partir de um Uri do Android
     */
    fun parseFromUri(context: Context, uri: Uri, defaultMonthKey: String): List<ParsedStatementItem> {
        val cr = context.contentResolver
        val mimeType = cr.getType(uri)?.lowercase() ?: ""
        val fileName = uri.lastPathSegment?.lowercase() ?: ""

        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        return parseFromBytes(bytes, fileName, mimeType, defaultMonthKey)
    }

    /**
     * Ponto de entrada para ler a partir de um array de bytes (PDF, OFX, CSV, TXT)
     */
    fun parseFromBytes(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        defaultMonthKey: String
    ): List<ParsedStatementItem> {
        if (bytes.isEmpty()) return emptyList()
        val lowerName = fileName.lowercase()
        val lowerMime = mimeType.lowercase()

        return if (lowerName.endsWith(".pdf") || lowerMime.contains("pdf") || (bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte())) {
            val lines = extractTextFromPdfBytes(bytes)
            parseTextLines(lines, defaultMonthKey)
        } else {
            val textContent = String(bytes, Charsets.UTF_8)
            if (lowerName.endsWith(".ofx") || lowerName.endsWith(".qfx") || textContent.contains("<OFX>", ignoreCase = true) || textContent.contains("<STMTTRN>", ignoreCase = true)) {
                parseOfx(textContent, defaultMonthKey)
            } else if (lowerName.endsWith(".csv") || textContent.contains(";") || (textContent.contains(",") && textContent.lines().size > 2)) {
                parseCsv(textContent, defaultMonthKey)
            } else {
                parseTextLines(textContent.lines(), defaultMonthKey)
            }
        }
    }

    /**
     * Analisa texto colado ou inserido diretamente pelo usuário
     */
    fun parseFromRawText(rawText: String, defaultMonthKey: String): List<ParsedStatementItem> {
        val trimmed = rawText.trim()
        return when {
            trimmed.contains("<OFX>", ignoreCase = true) || trimmed.contains("<STMTTRN>", ignoreCase = true) ->
                parseOfx(trimmed, defaultMonthKey)
            trimmed.contains(";") || (trimmed.contains(",") && trimmed.lines().size > 2) ->
                parseCsv(trimmed, defaultMonthKey)
            else ->
                parseTextLines(trimmed.lines(), defaultMonthKey)
        }
    }

    /**
     * Parser para extratos bancários padrão OFX / QFX
     */
    fun parseOfx(content: String, defaultMonthKey: String): List<ParsedStatementItem> {
        val items = mutableListOf<ParsedStatementItem>()
        val year = defaultMonthKey.split("-").firstOrNull() ?: SimpleDateFormat("yyyy", Locale.US).format(Date())

        // Encontra blocos <STMTTRN>...</STMTTRN> ou SGML simples
        val trnRegex = Regex("""<STMTTRN>([\s\S]*?)(?:</STMTTRN>|(?=<STMTTRN>|$))""", RegexOption.IGNORE_CASE)
        val trnMatches = trnRegex.findAll(content)

        for (match in trnMatches) {
            val block = match.groupValues[1]

            // Valor
            val amtMatch = Regex("""<TRNAMT>([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(block) ?: continue
            val rawAmt = amtMatch.groupValues[1].toDoubleOrNull() ?: continue
            if (rawAmt == 0.0) continue

            // Data (DTPOSTED: YYYYMMDD...)
            val dateMatch = Regex("""<DTPOSTED>(\d{4})(\d{2})(\d{2})""", RegexOption.IGNORE_CASE).find(block)
            val txDate = if (dateMatch != null) {
                "${dateMatch.groupValues[1]}-${dateMatch.groupValues[2]}-${dateMatch.groupValues[3]}"
            } else {
                "$year-01-01"
            }

            // Descrição e Contraparte
            val memoMatch = Regex("""<MEMO>([^<\r\n]+)""", RegexOption.IGNORE_CASE).find(block)
            val nameMatch = Regex("""<NAME>([^<\r\n]+)""", RegexOption.IGNORE_CASE).find(block)
            val rawDesc = (memoMatch?.groupValues?.get(1) ?: nameMatch?.groupValues?.get(1) ?: "Transação").trim()

            val cleanCounterparty = extractCounterparty(rawDesc)
            val isIncome = rawAmt > 0
            val absAmount = Math.abs(rawAmt)

            val (guessedCat, guessedSub) = guessCategoryAndSubcategory(rawDesc, if (isIncome) null else absAmount)

            // Identifica pagamentos de fatura ou RDB
            val isRdb = RDB_PATTERNS.any { it.containsMatchIn(rawDesc) }
            val isInvoicePayment = INVOICE_PAYMENT_PATTERNS.any { it.containsMatchIn(rawDesc) }
            val isIgnored = isRdb || isInvoicePayment
            val ignoredReason = if (isRdb) "Aplicação / RDB Interno" else if (isInvoicePayment) "Pagamento de Fatura" else null

            // Detecta parcelamento
            val instMatch = Regex("""\b(\d{1,2})/(\d{1,2})\b""").find(rawDesc)
            val (curInst, totInst) = if (instMatch != null) {
                val c = instMatch.groupValues[1].toIntOrNull()
                val t = instMatch.groupValues[2].toIntOrNull()
                if (c != null && t != null && c in 1..t && t <= 48) Pair(c, t) else Pair(null, null)
            } else Pair(null, null)

            val paymentMethod = if (isIncome) {
                PaymentMethod.PIX
            } else if (rawDesc.contains("pix", ignoreCase = true)) {
                PaymentMethod.PIX
            } else if (rawDesc.contains("debito", ignoreCase = true) || rawDesc.contains("débito", ignoreCase = true)) {
                PaymentMethod.DEBIT
            } else if (rawDesc.contains("boleto", ignoreCase = true)) {
                PaymentMethod.BILL
            } else {
                PaymentMethod.CREDIT_CARD
            }

            items.add(
                ParsedStatementItem(
                    rawLine = block.take(120),
                    date = txDate,
                    description = rawDesc,
                    counterparty = cleanCounterparty,
                    amount = absAmount,
                    type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                    paymentMethod = paymentMethod,
                    category = if (isIncome) "Receita" else guessedCat,
                    subcategory = guessedSub,
                    isIgnored = isIgnored,
                    ignoredReason = ignoredReason,
                    installmentNumber = curInst,
                    installmentsTotal = totInst,
                    isSelected = !isIgnored
                )
            )
        }

        return items
    }

    /**
     * Parser para extratos em CSV (Nubank, C6, Inter, Itaú, etc.)
     */
    fun parseCsv(content: String, defaultMonthKey: String): List<ParsedStatementItem> {
        val items = mutableListOf<ParsedStatementItem>()
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val year = defaultMonthKey.split("-").firstOrNull() ?: SimpleDateFormat("yyyy", Locale.US).format(Date())

        // Identifica separador (, ou ;)
        val firstLine = lines.first()
        val separator = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','

        // Procura índice de cabeçalhos
        var dateIdx = 0
        var valIdx = 1
        var descIdx = 2

        val headerTokens = firstLine.split(separator).map { it.trim().lowercase() }
        val hasHeaders = headerTokens.any { it.contains("data") || it.contains("valor") || it.contains("desc") || it.contains("title") }

        if (hasHeaders) {
            headerTokens.forEachIndexed { i, token ->
                if (token.contains("data") || token.contains("date")) dateIdx = i
                else if (token.contains("valor") || token.contains("amount") || token.contains("preço")) valIdx = i
                else if (token.contains("desc") || token.contains("title") || token.contains("identificador") || token.contains("estabelecimento")) descIdx = i
            }
        }

        val startIndex = if (hasHeaders) 1 else 0

        for (i in startIndex until lines.size) {
            val line = lines[i]
            val cols = line.split(separator).map { it.trim().replace("\"", "") }
            if (cols.size <= maxOf(dateIdx, valIdx, descIdx)) continue

            val rawDate = cols[dateIdx]
            val rawVal = cols[valIdx]
            val rawDesc = cols[descIdx]

            // Limpeza de valor brasileiro: 1.234,56 ou -12,34 ou R$ 15,00
            val cleanValStr = rawVal.replace("R$", "").replace(" ", "").replace(".", "").replace(",", ".")
            val parsedVal = cleanValStr.toDoubleOrNull() ?: continue
            if (parsedVal == 0.0) continue

            // Formatação de data
            val slashMatch = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""").find(rawDate)
            val txDate = if (slashMatch != null) {
                val d = slashMatch.groupValues[1].padStart(2, '0')
                val m = slashMatch.groupValues[2].padStart(2, '0')
                val y = slashMatch.groupValues[3].let { if (it.length == 2) "20$it" else if (it.length == 4) it else year }
                "$y-$m-$d"
            } else if (rawDate.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
                rawDate
            } else {
                "$year-01-01"
            }

            val isIncome = parsedVal > 0 && !rawDesc.contains("cartao", ignoreCase = true)
            val absAmount = Math.abs(parsedVal)
            val cleanCounterparty = extractCounterparty(rawDesc)

            val (guessedCat, guessedSub) = guessCategoryAndSubcategory(rawDesc, if (isIncome) null else absAmount)

            val isRdb = RDB_PATTERNS.any { it.containsMatchIn(rawDesc) }
            val isInvoicePayment = INVOICE_PAYMENT_PATTERNS.any { it.containsMatchIn(rawDesc) }
            val isIgnored = isRdb || isInvoicePayment
            val ignoredReason = if (isRdb) "Aplicação / RDB Interno" else if (isInvoicePayment) "Pagamento de Fatura" else null

            val instMatch = Regex("""\b(\d{1,2})/(\d{1,2})\b""").find(rawDesc)
            val (curInst, totInst) = if (instMatch != null) {
                val c = instMatch.groupValues[1].toIntOrNull()
                val t = instMatch.groupValues[2].toIntOrNull()
                if (c != null && t != null && c in 1..t && t <= 48) Pair(c, t) else Pair(null, null)
            } else Pair(null, null)

            items.add(
                ParsedStatementItem(
                    rawLine = line,
                    date = txDate,
                    description = rawDesc,
                    counterparty = cleanCounterparty,
                    amount = absAmount,
                    type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                    paymentMethod = if (isIncome) PaymentMethod.PIX else PaymentMethod.CREDIT_CARD,
                    category = if (isIncome) "Receita" else guessedCat,
                    subcategory = guessedSub,
                    isIgnored = isIgnored,
                    ignoredReason = ignoredReason,
                    installmentNumber = curInst,
                    installmentsTotal = totInst,
                    isSelected = !isIgnored
                )
            )
        }

        return items
    }

    /**
     * Parser textual para extratos em PDF e linhas soltas (Idêntico ao pdfParser.ts da Web)
     */
    fun parseTextLines(lines: List<String>, defaultMonthKey: String): List<ParsedStatementItem> {
        val items = mutableListOf<ParsedStatementItem>()
        val year = defaultMonthKey.split("-").firstOrNull() ?: SimpleDateFormat("yyyy", Locale.US).format(Date())

        for (line in lines) {
            if (line.length < 5) continue

            // 1. Procura valor em reais: R$ 123,45 ou 123,45 ou -15,00
            val valueMatch = Regex("""(?:R\$\s*)?(-?\s*\d{1,3}(?:\.\d{3})*,\d{2}|-?\s*\d+,\d{2})\b""").find(line) ?: continue
            val rawValStr = valueMatch.groupValues[1].replace(" ", "").replace(".", "").replace(",", ".")
            val valNum = rawValStr.toDoubleOrNull() ?: continue
            if (valNum == 0.0) continue

            // 2. Filtro de itens ignorados (RDB, saldo anterior, pagamento fatura)
            val isRdb = RDB_PATTERNS.any { it.containsMatchIn(line) }
            val isInvoicePayment = INVOICE_PAYMENT_PATTERNS.any { it.containsMatchIn(line) }
            val isIgnored = isRdb || isInvoicePayment
            val ignoredReason = if (isRdb) "RDB / Aplicação Financeira (reserva interna)" else if (isInvoicePayment) "Pagamento de Fatura / Saldo Anterior" else null

            // 3. Sinais de crédito e débito
            val isCreditSignal = Regex("""\b(\+|c|crédito|credito|rec)\b""", RegexOption.IGNORE_CASE).containsMatchIn(line) && !line.contains("cartao de credito", ignoreCase = true)
            val isDebitSignal = Regex("""\b(-|d|débito|debito)\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)

            // 4. Procura por data (04/10, 04/10/2026, 04 OUT)
            var txDate = "$year-01-01"
            val slashDate = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""").find(line)
            if (slashDate != null) {
                val d = slashDate.groupValues[1].padStart(2, '0')
                val m = slashDate.groupValues[2].padStart(2, '0')
                val y = slashDate.groupValues[3].let { if (it.length == 2) "20$it" else if (it.length == 4) it else year }
                txDate = "$y-$m-$d"
            } else {
                val textDate = Regex("""\b(\d{1,2})\s+(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)\b""", RegexOption.IGNORE_CASE).find(line)
                if (textDate != null) {
                    val d = textDate.groupValues[1].padStart(2, '0')
                    val m = MONTH_MAP[textDate.groupValues[2].lowercase()] ?: "10"
                    txDate = "$year-$m-$d"
                }
            }

            // 5. Descrição removendo data e valor
            val desc = line
                .replace(Regex("""(?:R\$\s*)?(-?\s*\d{1,3}(?:\.\d{3})*,\d{2}|-?\s*\d+,\d{2})\b"""), "")
                .replace(Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b"""), "")
                .replace(Regex("""\b(\d{1,2})\s+(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""[-*#•]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            if (desc.length < 2) continue

            // 6. Parcela (ex: 2/6, 10/12)
            val instMatch = Regex("""\b(\d{1,2})/(\d{1,2})\b""").find(line)
            val (curInst, totInst) = if (instMatch != null) {
                val c = instMatch.groupValues[1].toIntOrNull()
                val t = instMatch.groupValues[2].toIntOrNull()
                if (c != null && t != null && c in 1..t && t <= 48) Pair(c, t) else Pair(null, null)
            } else Pair(null, null)

            // 7. Tipo e Método de Pagamento
            val isIncome = !isDebitSignal && (isCreditSignal || Regex("""(salario|salário|remunera|folha|pro-?labore|recebido|recebimento|pix recebido|ted recebida|deposito|depósito|rendimento|cashback)""", RegexOption.IGNORE_CASE).containsMatchIn(desc))
            val absAmount = Math.abs(valNum)

            val paymentMethod = if (isIncome) {
                PaymentMethod.PIX
            } else if (desc.contains("pix", ignoreCase = true)) {
                PaymentMethod.PIX
            } else if (desc.contains("debito", ignoreCase = true) || desc.contains("débito", ignoreCase = true)) {
                PaymentMethod.DEBIT
            } else if (Regex("""(boleto|energia|luz|agua|água|condominio|tributo|brisanet)""", RegexOption.IGNORE_CASE).containsMatchIn(desc)) {
                PaymentMethod.BILL
            } else {
                PaymentMethod.CREDIT_CARD
            }

            val cleanCounterparty = extractCounterparty(desc)
            val (guessedCat, guessedSub) = guessCategoryAndSubcategory(desc, if (isIncome) null else absAmount)

            items.add(
                ParsedStatementItem(
                    rawLine = line,
                    date = txDate,
                    description = desc,
                    counterparty = cleanCounterparty,
                    amount = absAmount,
                    type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                    paymentMethod = paymentMethod,
                    category = if (isIncome) "Receita" else guessedCat,
                    subcategory = guessedSub,
                    isIgnored = isIgnored,
                    ignoredReason = ignoredReason,
                    installmentNumber = curInst,
                    installmentsTotal = totInst,
                    isSelected = !isIgnored
                )
            )
        }

        return items
    }

    /**
     * Extrai linhas de texto a partir de bytes de um arquivo PDF
     * Suporta fluxos descomprimidos e compactados com FlateDecode (zlib)
     */
    fun extractTextFromPdfBytes(pdfBytes: ByteArray): List<String> {
        val lines = mutableListOf<String>()
        val content = String(pdfBytes, Charsets.ISO_8859_1)

        val streamRegex = Regex("""stream\r?\n([\s\S]*?)\r?\nendstream""")
        val matches = streamRegex.findAll(content)

        val textAccumulator = StringBuilder()

        for (match in matches) {
            val streamStartIndex = match.groups[1]?.range?.first ?: continue
            val streamLength = match.groups[1]!!.value.length

            val precedingHeader = content.substring(maxOf(0, streamStartIndex - 200), streamStartIndex)
            val isFlate = precedingHeader.contains("/FlateDecode")

            val decompressedText = if (isFlate) {
                try {
                    val streamSlice = pdfBytes.copyOfRange(streamStartIndex, streamStartIndex + streamLength)
                    InflaterInputStream(ByteArrayInputStream(streamSlice)).bufferedReader(Charsets.ISO_8859_1).readText()
                } catch (_: Exception) {
                    match.groups[1]?.value ?: ""
                }
            } else {
                match.groups[1]?.value ?: ""
            }

            // Operador (texto) Tj
            val tjRegex = Regex("""\(([^)]*)\)\s*Tj""")
            tjRegex.findAll(decompressedText).forEach {
                textAccumulator.append(it.groupValues[1]).append(" ")
            }

            // Operador [(texto) 10 (texto)] TJ
            val tjArrayRegex = Regex("""\[(.*?)\]\s*TJ""")
            tjArrayRegex.findAll(decompressedText).forEach { arrayMatch ->
                val inner = arrayMatch.groupValues[1]
                val partRegex = Regex("""\(([^)]*)\)""")
                val linePart = partRegex.findAll(inner).map { it.groupValues[1] }.joinToString("")
                if (linePart.isNotBlank()) {
                    textAccumulator.append(linePart).append("\n")
                }
            }
        }

        if (textAccumulator.isNotBlank()) {
            textAccumulator.toString().split("\n").forEach { l ->
                val trimmed = l.trim()
                if (trimmed.isNotBlank()) lines.add(trimmed)
            }
        }

        // Se o PDF não tiver streams tradicionais legíveis, varre literais
        if (lines.isEmpty()) {
            val literalMatches = Regex("""\(([^)]{4,})\)\s*Tj""").findAll(content)
            literalMatches.forEach {
                val s = it.groupValues[1].trim()
                if (s.isNotBlank()) lines.add(s)
            }
        }

        return lines
    }
}
