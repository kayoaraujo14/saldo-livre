package com.saldolivre.app.data.notification

import com.saldolivre.app.data.ai.AgentSecuritySanitizer
import com.saldolivre.app.data.ai.LearnedRulesManager
import com.saldolivre.app.data.ai.NeuralFinanceClassifier
import com.saldolivre.app.data.engine.FinanceEngine
import com.saldolivre.app.data.model.Card
import com.saldolivre.app.data.model.CategoryRegistry
import com.saldolivre.app.data.model.ExpenseDraft
import com.saldolivre.app.data.model.PaymentMethod
import com.saldolivre.app.data.model.TransactionType
import com.saldolivre.app.data.model.UserLearnedRule
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache de deduplicação em memória para evitar criação de esboços duplicados
 * quando bancos emitem notificações repetidas ou atualizações em sequência.
 */
class NotificationDeduplicationCache(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS
) {
    companion object {
        const val DEFAULT_WINDOW_MILLIS = 2 * 60 * 1000L // 2 minutos
    }

    private val seenHashes = ConcurrentHashMap<String, Long>()

    fun computeKeyHash(packageName: String, title: String?, text: String?): String {
        val normPkg = packageName.trim().lowercase(Locale.ROOT)
        val normTitle = (title ?: "").trim().replace(Regex("""\s+"""), " ").lowercase(Locale.ROOT)
        val normText = (text ?: "").trim().replace(Regex("""\s+"""), " ").lowercase(Locale.ROOT)
        val raw = "$normPkg|$normTitle|$normText"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isDuplicateAndRecord(
        packageName: String,
        title: String?,
        text: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        purgeExpired(nowMs)
        val hash = computeKeyHash(packageName, title, text)
        val previous = seenHashes[hash]
        if (previous != null && (nowMs - previous) < windowMillis) {
            return true
        }
        seenHashes[hash] = nowMs
        return false
    }

    fun clear() {
        seenHashes.clear()
    }

    private fun purgeExpired(nowMs: Long) {
        val iterator = seenHashes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((nowMs - entry.value) >= windowMillis) {
                iterator.remove()
            }
        }
    }
}

/**
 * Parser inteligente de notificações bancárias brasileiras.
 * Sanitiza todo input com [AgentSecuritySanitizer], filtra mensagens promocionais/recebimentos/2FA,
 * extrai valor, método de pagamento, cartão vinculado, descrição e infere categoria em cascata.
 */
object NotificationExpenseParser {

    val SUPPORTED_BANK_PACKAGES: Set<String> = setOf(
        // Nubank
        "com.nu.production",
        // Itaú & Iti
        "com.itau",
        "com.itaucard.activity",
        "br.com.itau.iti",
        // Bradesco & Next
        "com.bradesco",
        "com.bradesco.cartoes",
        "br.com.bradesco.cartoes",
        "br.com.next",
        // Inter
        "br.com.intermedium",
        // Santander & Way
        "com.santander.app",
        "br.com.santander.way",
        // Banco do Brasil & Ourocard
        "br.com.bb.android",
        "br.com.bb.ourocard",
        // Caixa
        "br.com.gabba.Caixa",
        "br.gov.caixa.tem",
        "br.com.caixa.cartoes",
        // Sicredi
        "br.com.sicredi.app",
        "com.sicredi.x",
        "br.com.sicredi.mobile",
        // C6 Bank
        "com.c6bank.app",
        // PicPay & Mercado Pago
        "com.picpay",
        "com.mercadopago.wallet",
        // iFood & iFood Benefícios
        "br.com.brainweb.ifood",
        "br.com.ifood.benefits",
        // Outros bancos/carteiras e benefícios populares
        "com.btg.pactual.digital.mobile",
        "com.btgpactual.banking",
        "br.com.xp.carteira",
        "br.com.neon",
        "br.com.uol.ps.myaccount",
        "br.com.sicoob.sisbr.mobile",
        "com.caju.beneficios",
        "br.com.flashapp",
        "br.com.alelo.meualelo",
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay"
    )

    private val PACKAGE_BANK_HINTS: Map<String, List<String>> = mapOf(
        "com.nu.production" to listOf("nubank", "roxinho"),
        "com.itau" to listOf("itau", "itaú", "itaucard"),
        "com.itaucard.activity" to listOf("itaucard", "itau", "itaú"),
        "br.com.itau.iti" to listOf("iti", "itau", "itaú"),
        "com.bradesco" to listOf("bradesco"),
        "com.bradesco.cartoes" to listOf("bradesco"),
        "br.com.bradesco.cartoes" to listOf("bradesco"),
        "br.com.next" to listOf("next", "bradesco"),
        "br.com.intermedium" to listOf("inter"),
        "com.santander.app" to listOf("santander"),
        "br.com.santander.way" to listOf("santander", "way"),
        "br.com.bb.android" to listOf("banco do brasil", "ourocard", "bb"),
        "br.com.bb.ourocard" to listOf("ourocard", "banco do brasil", "bb"),
        "br.com.gabba.Caixa" to listOf("caixa"),
        "br.gov.caixa.tem" to listOf("caixa", "caixa tem"),
        "br.com.caixa.cartoes" to listOf("caixa"),
        "br.com.sicredi.app" to listOf("sicredi"),
        "com.sicredi.x" to listOf("sicredi"),
        "br.com.sicredi.mobile" to listOf("sicredi"),
        "com.c6bank.app" to listOf("c6", "c6 bank", "carbon"),
        "com.picpay" to listOf("picpay"),
        "com.mercadopago.wallet" to listOf("mercado pago", "mercadopago"),
        "br.com.brainweb.ifood" to listOf("ifood"),
        "br.com.ifood.benefits" to listOf("ifood beneficios", "ifood"),
        "com.btg.pactual.digital.mobile" to listOf("btg"),
        "com.btgpactual.banking" to listOf("btg"),
        "br.com.xp.carteira" to listOf("xp"),
        "br.com.neon" to listOf("neon"),
        "br.com.uol.ps.myaccount" to listOf("pagbank", "pagseguro"),
        "br.com.sicoob.sisbr.mobile" to listOf("sicoob"),
        "com.caju.beneficios" to listOf("caju"),
        "br.com.flashapp" to listOf("flash"),
        "br.com.alelo.meualelo" to listOf("alelo")
    )

    private val KNOWN_BANK_KEYWORDS = listOf(
        "nubank", "itaú", "itau", "itaucard", "iti", "bradesco", "next",
        "inter", "santander", "way", "banco do brasil", "ourocard", "caixa",
        "sicredi", "c6 bank", "c6", "picpay", "mercado pago", "btg", "xp",
        "neon", "pagbank", "sicoob", "ifood", "caju", "flash", "alelo",
        "black", "platinum", "ultravioleta", "infinite"
    )

    private val IGNORE_PATTERNS = listOf(
        // Recebimentos e rendimentos (não são despesas)
        Regex("""\bpix\s+recebido\b""", RegexOption.IGNORE_CASE),
        Regex("""\bvoc[eê]\s+recebeu\b""", RegexOption.IGNORE_CASE),
        Regex("""\brecebeu\s+(?:um\s+)?pix\b""", RegexOption.IGNORE_CASE),
        Regex("""\btransfer[eê]ncia\s+recebida\b""", RegexOption.IGNORE_CASE),
        Regex("""\bted\s+recebid[ao]\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdoc\s+recebid[ao]\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdep[oó]sito\s+recebido\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpagamento\s+recebido\b""", RegexOption.IGNORE_CASE),
        Regex("""\brecebimento\s+de\s+pix\b""", RegexOption.IGNORE_CASE),
        Regex("""\brendeu\s+r\$""", RegexOption.IGNORE_CASE),
        Regex("""\bcashback\s+(?:recebido|dispon[ií]vel|creditado)\b""", RegexOption.IGNORE_CASE),
        // Promocionais, limites e avisos de fatura
        Regex("""\bpe[çc]a\s+(?:j[aá]\s+)?seu\s+empr[eé]stimo\b""", RegexOption.IGNORE_CASE),
        Regex("""\bempr[eé]stimo\s+(?:dispon[ií]vel|pr[eé]-aprovado)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcr[eé]dito\s+pr[eé]-aprovado\b""", RegexOption.IGNORE_CASE),
        Regex("""\blimite\s+dispon[ií]vel\b""", RegexOption.IGNORE_CASE),
        Regex("""\baumento\s+de\s+limite\b""", RegexOption.IGNORE_CASE),
        Regex("""\baumentamos\s+seu\s+limite\b""", RegexOption.IGNORE_CASE),
        Regex("""\bnovo\s+limite\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfatura\s+(?:fechada|fechou|dispon[ií]vel|vence)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsua\s+fatura\s+(?:fechou|vence|j[aá]\s+est[aá])\b""", RegexOption.IGNORE_CASE),
        Regex("""\bparcele\s+sua\s+fatura\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:oferta|promo[çc][aã]o)\s+(?:imperd[ií]vel|exclusiva)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bindique\s+e\s+ganhe\b""", RegexOption.IGNORE_CASE),
        // Códigos 2FA e segurança
        Regex("""\bc[oó]digo\s+de\s+(?:verifica[çc][aã]o|seguran[çc]a|confirma[çc][aã]o|acesso)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bn[aã]o\s+compartilhe\s+este\s+c[oó]digo\b""", RegexOption.IGNORE_CASE),
        Regex("""\bseu\s+c[oó]digo\s+(?:[eé]|de)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:token|otp|2fa)\b""", RegexOption.IGNORE_CASE),
        // Compras recusadas / negadas / estornadas
        Regex("""\b(?:compra|transa[çc][aã]o|pagamento)\s+(?:negad[ao]|recusad[ao]|cancelad[ao]|estornad[ao]|n[aã]o\s+autorizad[ao])\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:saldo|limite)\s+insuficiente\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsenha\s+incorreta\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcompra\s+estornada\b""", RegexOption.IGNORE_CASE)
    )

    private val EXPENSE_TRIGGER_REGEX = Regex(
        """\b(compra|aprovad[ao]|d[eé]bito|cr[eé]dito|pix\s+enviado|voc[eê]\s+enviou|enviou\s+um\s+pix|transfer[eê]ncia\s+enviada|voc[eê]\s+transferiu|voc[eê]\s+pagou|pagamento\s+(?:efetuado|aprovado|realizado)|transa[çc][aã]o\s+aprovada|pagou\s+com\s+pix|pagou\s+r\$)\b""",
        RegexOption.IGNORE_CASE
    )

    private val BRL_CURRENCY_REGEX = Regex(
        """r\$\s*([\d.]+(?:,\d{1,2})?|\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val FALLBACK_VALUE_REGEX = Regex(
        """\b(?:no\s+)?valor\s+(?:de\s+)?([\d.]+,\d{2})\b""",
        RegexOption.IGNORE_CASE
    )

    private val CARD_LAST_4_REGEX = Regex(
        """\b(?:final|terminado\s+em|cart[aã]o(?:\s+de\s+cr[eé]dito)?(?:\s+final)?|[*•x]{2,4})\s*(\d{4})\b""",
        RegexOption.IGNORE_CASE
    )

    fun isSupportedBankPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return SUPPORTED_BANK_PACKAGES.contains(packageName.trim())
    }

    /**
     * Analisa uma notificação bancária e gera um [ExpenseDraft] se for uma despesa válida.
     * Retorna `null` para notificações promocionais, recebimentos, códigos 2FA, compras negadas ou sem valor válido.
     */
    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        cards: List<Card> = emptyList(),
        learnedRules: List<UserLearnedRule> = emptyList(),
        neuralClassifier: NeuralFinanceClassifier = NeuralFinanceClassifier(null),
        date: String = FinanceEngine.todayKey()
    ): ExpenseDraft? {
        // 1. Sanitização obrigatória antes de qualquer processamento ou envio ao Gemini
        val sanitizedTitle = AgentSecuritySanitizer.sanitizeText(title, maxLength = 150)
        val sanitizedText = AgentSecuritySanitizer.sanitizeText(text, maxLength = 500)
        val combinedRaw = listOf(sanitizedTitle, sanitizedText)
            .filter { it.isNotBlank() }
            .joinToString(" - ")

        if (combinedRaw.isBlank()) return null

        // 2. Descarta notificações ignoráveis (Pix recebido, propaganda, limite, 2FA, compra negada)
        if (IGNORE_PATTERNS.any { it.containsMatchIn(combinedRaw) }) {
            return null
        }

        // 3. Exige um gatilho claro de despesa realizada
        if (!EXPENSE_TRIGGER_REGEX.containsMatchIn(combinedRaw)) {
            return null
        }

        // 4. Extrai e valida o valor monetário em BRL
        val amount = parseBrlAmount(combinedRaw) ?: return null

        // 5. Identifica o método de pagamento (Crédito, Débito ou Pix)
        val paymentMethod = detectPaymentMethod(combinedRaw)

        // 6. Associa o cartão cadastrado se for crédito
        val matchedCard = if (paymentMethod == PaymentMethod.CREDIT_CARD) {
            resolveCard(packageName, combinedRaw, cards)
        } else null

        // 7. Extrai descrição limpa do estabelecimento / destinatário
        val description = extractMerchantOrRecipient(
            sanitizedTitle = sanitizedTitle,
            sanitizedText = sanitizedText,
            paymentMethod = paymentMethod
        )

        // 8. Infere categoria em cascata (LearnedRulesManager -> NeuralFinanceClassifier -> CategoryRegistry)
        val (category, subcategory) = inferCategoryCascade(
            description = description,
            sanitizedCombined = combinedRaw,
            amount = amount,
            learnedRules = learnedRules,
            neuralClassifier = neuralClassifier
        )

        return ExpenseDraft(
            amount = amount,
            description = description,
            category = category,
            subcategory = subcategory,
            paymentMethod = paymentMethod,
            cardId = matchedCard?.id,
            cardName = matchedCard?.cardName,
            date = date,
            rawNotificationText = combinedRaw,
            packageName = packageName.trim()
        )
    }

    /**
     * Sobrecarga conveniente que busca as regras aprendidas diretamente no [LearnedRulesManager].
     */
    fun parseWithManagers(
        packageName: String,
        title: String?,
        text: String?,
        uid: String,
        cards: List<Card>,
        rulesManager: LearnedRulesManager,
        neuralClassifier: NeuralFinanceClassifier,
        date: String = FinanceEngine.todayKey()
    ): ExpenseDraft? {
        val rules = rulesManager.getLocalRules(uid)
        return parse(
            packageName = packageName,
            title = title,
            text = text,
            cards = cards,
            learnedRules = rules,
            neuralClassifier = neuralClassifier,
            date = date
        )
    }

    fun parseBrlAmount(text: String): Double? {
        val match = BRL_CURRENCY_REGEX.find(text) ?: FALLBACK_VALUE_REGEX.find(text) ?: return null
        val raw = match.groupValues[1].trim()
        if (raw.isBlank()) return null

        val normalized = when {
            raw.contains(',') -> raw.replace(".", "").replace(',', '.')
            Regex("""^\d{1,3}(\.\d{3})+$""").matches(raw) -> raw.replace(".", "")
            else -> raw
        }

        return AgentSecuritySanitizer.sanitizeAmount(normalized)
    }

    fun detectPaymentMethod(text: String): PaymentMethod {
        val folded = CategoryRegistry.foldCategoryAccents(text)
        return when {
            folded.contains("debito") -> PaymentMethod.DEBIT
            folded.contains("pix no credito") || folded.contains("pix parcelado") -> PaymentMethod.CREDIT_CARD
            folded.contains("pix") ||
                folded.contains("transferencia enviada") ||
                folded.contains("voce transferiu") -> PaymentMethod.PIX
            folded.contains("boleto") -> PaymentMethod.BILL
            else -> PaymentMethod.CREDIT_CARD
        }
    }

    fun resolveCard(
        packageName: String,
        sanitizedCombined: String,
        cards: List<Card>
    ): Card? {
        if (cards.isEmpty()) return null

        // 1. Tenta associar pelos últimos 4 dígitos (ex: "final 4321")
        val last4 = CARD_LAST_4_REGEX.find(sanitizedCombined)?.groupValues?.get(1)
        if (!last4.isNullOrBlank()) {
            val digitMatch = cards.find {
                it.cardName.contains(last4) || it.id.contains(last4)
            }
            if (digitMatch != null) return digitMatch
        }

        val foldedText = CategoryRegistry.foldCategoryAccents(sanitizedCombined)

        // 2. Tenta associar pelo nome do cartão / banco citado explicitamente no texto
        val textBankMatches = KNOWN_BANK_KEYWORDS
            .map { CategoryRegistry.foldCategoryAccents(it) }
            .filter { kw ->
                Regex("""\b${Regex.escape(kw)}\b""").containsMatchIn(foldedText)
            }

        if (textBankMatches.isNotEmpty()) {
            val matchedByText = cards.find { card ->
                val foldedCard = CategoryRegistry.foldCategoryAccents(card.cardName)
                textBankMatches.any { kw -> foldedCard.contains(kw) || kw.contains(foldedCard) }
            }
            if (matchedByText != null) return matchedByText
        }

        // 3. Tenta associar pelo nome completo de algum cartão cadastrado no texto
        val directCardMatch = cards.find { card ->
            val foldedCard = CategoryRegistry.foldCategoryAccents(card.cardName)
            foldedCard.length >= 2 && foldedText.contains(foldedCard)
        }
        if (directCardMatch != null) return directCardMatch

        // 4. Tenta associar pelo pacote de origem da notificação (ex: com.nu.production -> Nubank)
        val pkgHints = PACKAGE_BANK_HINTS[packageName.trim()] ?: emptyList()
        if (pkgHints.isNotEmpty()) {
            val matchedByPkg = cards.find { card ->
                val foldedCard = CategoryRegistry.foldCategoryAccents(card.cardName)
                pkgHints.any { hint ->
                    val foldedHint = CategoryRegistry.foldCategoryAccents(hint)
                    foldedCard.contains(foldedHint) || foldedHint.contains(foldedCard)
                }
            }
            if (matchedByPkg != null) return matchedByPkg
        }

        return null
    }

    fun extractMerchantOrRecipient(
        sanitizedTitle: String,
        sanitizedText: String,
        paymentMethod: PaymentMethod
    ): String {
        val candidates = listOf(sanitizedText, sanitizedTitle, "$sanitizedTitle $sanitizedText")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (candidate in candidates) {
            val safeText = candidate.trim()

            // Padrão 1: "em <Estabelecimento>" ou "no estabelecimento <Estabelecimento>"
            val merchantRegex = Regex(
                """\b(?:no\s+estabelecimento|estabelecimento|em)\s+(.+?)(?=\s+(?:para\s+o\s+cart[aã]o|no\s+cart[aã]o|com\s+o\s+cart[aã]o|no\s+seu\s+cart[aã]o|cart[aã]o(?:\s+final)?|final\s+\d{4}|no\s+valor|valor\s+de|em\s+\d{1,2}/\d{1,2}|em\s+\d{4}|[àa]s\s+\d{1,2}[:h]|via\s+pix|com\s+pix|aprovad[ao]|realizada|efetuada|pelo\s+app)\b|\s*\[neutralizado\]|[.!?\n]|$)""",
                RegexOption.IGNORE_CASE
            )
            for (match in merchantRegex.findAll(safeText)) {
                val rawMerchant = match.groupValues[1].trim()
                if (isValidExtractedMerchant(rawMerchant)) {
                    return cleanMerchantName(rawMerchant)
                }
            }

            // Padrão 2 (Pix / Transferência): "para <Destinatário>"
            if (paymentMethod == PaymentMethod.PIX) {
                val pixRecipientRegex = Regex(
                    """\bpara\s+(.+?)(?=\s+(?:via\s+pix|com\s+pix|no\s+valor|valor\s+de|em\s+\d{1,2}/\d{1,2}|[àa]s\s+\d{1,2}[:h]|chave\s+pix|pelo\s+app)\b|\s*\[neutralizado\]|[.!?\n]|$)""",
                    RegexOption.IGNORE_CASE
                )
                for (match in pixRecipientRegex.findAll(safeText)) {
                    val rawRecipient = match.groupValues[1].trim()
                    if (isValidExtractedMerchant(rawRecipient) &&
                        !rawRecipient.startsWith("o cartão", ignoreCase = true) &&
                        !rawRecipient.startsWith("o cartao", ignoreCase = true) &&
                        !rawRecipient.startsWith("sua segurança", ignoreCase = true)
                    ) {
                        return cleanMerchantName(rawRecipient)
                    }
                }
            }

            // Padrão 3: Separador com hífen após valor (ex: "Débito em conta de R$ 75,00 - Farmácia São Paulo")
            val dashMerchantRegex = Regex(
                """r\$\s*[\d.,]+\s*[-–—]\s*(.+?)(?=\s+(?:no\s+cart[aã]o|final\s+\d{4}|[àa]s\s+\d{1,2}[:h]|em\s+\d{1,2}/\d{1,2})|\s*\[neutralizado\]|[.!?\n]|$)""",
                RegexOption.IGNORE_CASE
            )
            val dashMatch = dashMerchantRegex.find(safeText)
            if (dashMatch != null) {
                val rawDash = dashMatch.groupValues[1].trim()
                if (isValidExtractedMerchant(rawDash)) {
                    return cleanMerchantName(rawDash)
                }
            }
        }

        // Fallback seguro usando NeuralFinanceClassifier sem texto residual
        val fallbackRaw = sanitizedText.ifBlank { sanitizedTitle }
            .replace(Regex("""\[neutralizado\]:?""", RegexOption.IGNORE_CASE), "")
            .trim()
        val guessed = NeuralFinanceClassifier(null).extractDescription(fallbackRaw)
        return cleanMerchantName(guessed).ifBlank { "Compra detectada" }
    }

    private fun isValidExtractedMerchant(candidate: String): Boolean {
        if (candidate.length < 2) return false
        val lower = CategoryRegistry.foldCategoryAccents(candidate)
        if (Regex("""^\d{1,2}/\d{1,2}(?:/\d{2,4})?$""").matches(lower)) return false
        if (Regex("""^\d+\s*x$""").matches(lower)) return false
        if (Regex("""^(seu\s+cartao|sua\s+conta|processamento|analise|breve|reais|real|credito|debito)$""").matches(lower)) return false
        if (lower.startsWith("seu cartao") || lower.startsWith("sua conta")) return false
        return true
    }

    private fun cleanMerchantName(raw: String): String {
        var cleaned = raw
            .replace(Regex("""\[neutralizado\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:aprovad[ao]|realizada|efetuada|no\s+cr[eé]dito|no\s+d[eé]bito)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[-–—:\s]+|[-–—.,;:!?\s]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        cleaned = AgentSecuritySanitizer.sanitizeText(cleaned, maxLength = 100)
        return cleaned.ifBlank { "Compra detectada" }
    }

    fun inferCategoryCascade(
        description: String,
        sanitizedCombined: String,
        amount: Double,
        learnedRules: List<UserLearnedRule>,
        neuralClassifier: NeuralFinanceClassifier
    ): Pair<String, String?> {
        val searchSurface = CategoryRegistry.foldCategoryAccents("$description $sanitizedCombined")

        // 1º estágio da cascata: LearnedRulesManager (regras aprendidas ativas do usuário)
        val activeRules = learnedRules.filter { it.active }
        val candidateCategoryKeys = (CategoryRegistry.EXPENSE_CATEGORIES.map { it.name } +
            CategoryRegistry.CATEGORY_CANONICAL_MAP.keys)
            .distinct()
            .sortedByDescending { it.length }

        for (ruleObj in activeRules) {
            val foldedRule = CategoryRegistry.foldCategoryAccents(ruleObj.rule)
            val explicitCategory = ruleObj.category.takeIf {
                it.isNotBlank() && !it.equals("Geral", ignoreCase = true)
            }
            val matchedCatKey = explicitCategory?.let { CategoryRegistry.foldCategoryAccents(it) }
                ?: candidateCategoryKeys.firstOrNull { catKey ->
                    Regex("""\b${Regex.escape(CategoryRegistry.foldCategoryAccents(catKey))}\b""")
                        .containsMatchIn(foldedRule)
                }

            if (!matchedCatKey.isNullOrBlank()) {
                val (strictCat, strictSub) = CategoryRegistry.resolveStrictCategory(
                    rawCategory = matchedCatKey,
                    type = TransactionType.EXPENSE
                )

                val keywords = foldedRule
                    .replace(CategoryRegistry.foldCategoryAccents(matchedCatKey), " ")
                    .split(Regex("""[\s,;.=:\->]+"""))
                    .map { it.trim() }
                    .filter { kw ->
                        kw.length >= 2 && kw !in RULE_STOPWORDS
                    }

                if (keywords.any { kw -> searchSurface.contains(kw) }) {
                    return Pair(strictCat, strictSub)
                }
            }
        }

        // 2º estágio da cascata: NeuralFinanceClassifier
        val guess = neuralClassifier.guessCategoryAndSubcategory("$description $sanitizedCombined", amount)

        // 3º estágio: Garantia estrita contra taxonomia oficial de Category.kt
        return CategoryRegistry.resolveStrictCategory(
            rawCategory = guess.category,
            rawSubcategory = guess.subcategory,
            type = TransactionType.EXPENSE,
            description = description
        )
    }

    private val RULE_STOPWORDS = setOf(
        "de", "do", "da", "em", "no", "na", "eh", "ou", "ao", "os", "as", "um", "se",
        "sempre", "categoria", "coloque", "classifique", "como", "para", "gasto",
        "gastos", "despesa", "despesas", "lancamento", "lancamentos", "todo",
        "toda", "todos", "todas", "que", "vai", "ser", "sao", "com", "nos", "nas",
        "dos", "das", "meu", "minha", "lembre", "aprenda", "marque", "definir",
        "usar", "regra", "compra", "compras"
    )
}
