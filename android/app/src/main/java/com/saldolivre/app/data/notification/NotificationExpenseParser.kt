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
import kotlin.math.roundToLong

/**
 * Cache de deduplicação em memória para evitar criação de esboços duplicados
 * quando bancos emitem notificações repetidas, atualizações em sequência
 * ou quando Smart Wallets (Google Pay / Samsung Pay) e o app do banco notificam a mesma compra.
 */
class NotificationDeduplicationCache(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS
) {
    companion object {
        const val DEFAULT_WINDOW_MILLIS = 2 * 60 * 1000L // 2 minutos
    }

    private data class RecordedAmountEntry(
        val packageName: String,
        val isSmartWallet: Boolean,
        val timestampMs: Long
    )

    private val seenHashes = ConcurrentHashMap<String, Long>()
    private val seenAmounts = ConcurrentHashMap<Long, MutableList<RecordedAmountEntry>>()

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
        val normPkg = packageName.trim().lowercase(Locale.ROOT)
        val hash = computeKeyHash(normPkg, title, text)
        val previous = seenHashes[hash]
        if (previous != null && (nowMs - previous) < windowMillis) {
            return true
        }

        val combinedForAmount = listOfNotNull(title, text)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        val isSmartWallet = NotificationExpenseParser.isSmartWalletPackage(normPkg)
        val amountCents = if (combinedForAmount.isNotBlank() &&
            !NotificationExpenseParser.isIgnorableNotification(combinedForAmount)
        ) {
            NotificationExpenseParser.parseBrlAmount(combinedForAmount)?.let { (it * 100.0).roundToLong() }
        } else {
            null
        }

        if (amountCents != null) {
            val entries = seenAmounts[amountCents]
            if (entries != null) {
                val hasCrossAppWalletDuplicate = synchronized(entries) {
                    entries.any { entry ->
                        (nowMs - entry.timestampMs) < windowMillis &&
                            entry.packageName != normPkg &&
                            (isSmartWallet || entry.isSmartWallet)
                    }
                }
                if (hasCrossAppWalletDuplicate) {
                    return true
                }
            }
        }

        seenHashes[hash] = nowMs
        if (amountCents != null) {
            val list = seenAmounts.computeIfAbsent(amountCents) { mutableListOf() }
            synchronized(list) {
                list.add(
                    RecordedAmountEntry(
                        packageName = normPkg,
                        isSmartWallet = isSmartWallet,
                        timestampMs = nowMs
                    )
                )
            }
        }
        return false
    }

    fun clear() {
        seenHashes.clear()
        seenAmounts.clear()
    }

    private fun purgeExpired(nowMs: Long) {
        val hashIterator = seenHashes.entries.iterator()
        while (hashIterator.hasNext()) {
            val entry = hashIterator.next()
            if ((nowMs - entry.value) >= windowMillis) {
                hashIterator.remove()
            }
        }

        val amountIterator = seenAmounts.entries.iterator()
        while (amountIterator.hasNext()) {
            val entry = amountIterator.next()
            val list = entry.value
            val isEmpty = synchronized(list) {
                list.removeAll { (nowMs - it.timestampMs) >= windowMillis }
                list.isEmpty()
            }
            if (isEmpty) {
                amountIterator.remove()
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

    val SMART_WALLET_PACKAGES: Set<String> = setOf(
        "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.nbu.paisa.user",
        "com.samsung.android.spay",
        "com.samsung.android.spaymini",
        "com.samsung.android.samsungpay.gear"
    )

    private val SMART_WALLET_PACKAGES_LOWER: Set<String> =
        SMART_WALLET_PACKAGES.map { it.lowercase(Locale.ROOT) }.toSet()

    val SUPPORTED_BANK_PACKAGES: Set<String> = setOf(
        // Nubank
        "com.nu.production",
        // Itaú, Iti, Credicard, Hipercard
        "com.itau",
        "com.itaucard.activity",
        "br.com.itau.iti",
        "br.com.itau.personnalite",
        "com.itau.credicard",
        "com.itau.hipercard",
        // Bradesco & Next
        "com.bradesco",
        "com.bradesco.cartoes",
        "br.com.bradesco.cartoes",
        "br.com.bradesco.cartoesapp",
        "br.com.next",
        "br.com.bradesco.next",
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
        // Sicredi (G2, App principal, X, Mobile, Woop, Cartões)
        "br.com.sicredi.g2",
        "br.com.sicredi.app",
        "com.sicredi.x",
        "br.com.sicredi.mobile",
        "br.com.sicredi.woop",
        "br.com.sicredi.cartoes",
        "com.sicredi.app",
        // InfinitePay (CloudWalk)
        "io.cloudwalk.infinitepaydash",
        "io.cloudwalk.infinitepay",
        "br.com.infinitepay",
        "com.infinitepay.app",
        "io.cloudwalk.pos",
        "io.cloudwalk.infinitecash",
        // C6 Bank
        "com.c6bank.app",
        // PicPay & Mercado Pago
        "com.picpay",
        "com.mercadopago.wallet",
        // iFood & iFood Benefícios
        "br.com.brainweb.ifood",
        "br.com.ifood.benefits",
        // Cooperativas, bancos digitais, fintechs e carteiras
        "com.btg.pactual.digital.mobile",
        "com.btgpactual.banking",
        "br.com.xp.carteira",
        "br.com.neon",
        "br.com.uol.ps.myaccount",
        "br.com.sicoob.sisbr.mobile",
        "br.com.sicoobnet.pessoal",
        "br.com.unicred.mobile",
        "br.com.cresol.mobile",
        "br.com.banrisul",
        "br.com.brb.mobile",
        "br.com.brb.brbcard",
        "br.com.brb.fla",
        "pag.meu.app",
        "br.com.willbank",
        "br.com.digio.app",
        "br.com.bancopan.app",
        "com.bancopan.cartoes",
        "com.bv.app",
        "br.com.bancobmg.meu_bmg",
        "br.com.sofisadireto",
        "br.com.agibank.app",
        "br.com.original.bank",
        "com.safra.wallet",
        "br.com.stone.ton",
        "br.com.stone.app",
        "co.cora.app",
        "br.com.asaas.mobile",
        "br.com.gerencianet.app",
        "com.recargapay.wallet",
        "com.b2w.amedigital",
        "com.sumup.merchant",
        "com.nomad.app",
        "com.transferwise.android",
        "com.revolut.revolut",
        "us.avenue.app",
        // Benefícios VA / VR
        "com.caju.beneficios",
        "br.com.flashapp",
        "br.com.alelo.meualelo",
        "br.com.sodexo.sodexobr.sodexoclub",
        "com.pluxee.br",
        "br.com.ticket.app",
        "com.swile.app"
    ) + SMART_WALLET_PACKAGES

    private val SUPPORTED_BANK_PACKAGES_LOWER: Set<String> =
        SUPPORTED_BANK_PACKAGES.map { it.lowercase(Locale.ROOT) }.toSet()

    private val EXCLUDED_NON_BANK_PACKAGES: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "com.instagram.android",
        "com.facebook.orca",
        "com.facebook.katana",
        "com.discord",
        "com.slack",
        "com.twitter.android",
        "com.google.android.gm",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.chrome",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.android.systemui",
        "com.android.settings"
    )

    private val BANK_PACKAGE_KEYWORDS = listOf(
        "nubank", "nu.production", "itau", "credicard", "hipercard",
        "bradesco", "intermedium", "santander", "ourocard", "sicredi",
        "sicoob", "unicred", "cresol", "infinitepay", "cloudwalk",
        "c6bank", "picpay", "mercadopago", "pagbank", "pagseguro",
        "btg", "xp.carteira", "neon", "banrisul", "digio", "willbank",
        "bancopan", "bancobmg", "sofisa", "agibank", "safra",
        "cora", "stone", "asaas", "gerencianet", "recargapay",
        "nomad", "transferwise", "revolut", "pluxee", "sodexo",
        "alelo", "caju", "flashapp", "swile",
        "walletnfcrel", "samsungpay", "googlepay", "spay"
    )

    private val PACKAGE_BANK_HINTS: Map<String, List<String>> = mapOf(
        "com.nu.production" to listOf("nubank", "roxinho"),
        "com.itau" to listOf("itau", "itaú", "itaucard"),
        "com.itaucard.activity" to listOf("itaucard", "itau", "itaú"),
        "br.com.itau.iti" to listOf("iti", "itau", "itaú"),
        "br.com.itau.personnalite" to listOf("personnalite", "itau", "itaú"),
        "com.itau.credicard" to listOf("credicard", "itau", "itaú"),
        "com.itau.hipercard" to listOf("hipercard", "itau", "itaú"),
        "com.bradesco" to listOf("bradesco"),
        "com.bradesco.cartoes" to listOf("bradesco"),
        "br.com.bradesco.cartoes" to listOf("bradesco"),
        "br.com.bradesco.cartoesapp" to listOf("bradesco"),
        "br.com.next" to listOf("next", "bradesco"),
        "br.com.bradesco.next" to listOf("next", "bradesco"),
        "br.com.intermedium" to listOf("inter"),
        "com.santander.app" to listOf("santander"),
        "br.com.santander.way" to listOf("santander", "way"),
        "br.com.bb.android" to listOf("banco do brasil", "ourocard", "bb"),
        "br.com.bb.ourocard" to listOf("ourocard", "banco do brasil", "bb"),
        "br.com.gabba.caixa" to listOf("caixa"),
        "br.gov.caixa.tem" to listOf("caixa", "caixa tem"),
        "br.com.caixa.cartoes" to listOf("caixa"),
        "br.com.sicredi.g2" to listOf("sicredi"),
        "br.com.sicredi.app" to listOf("sicredi"),
        "com.sicredi.x" to listOf("sicredi"),
        "br.com.sicredi.mobile" to listOf("sicredi"),
        "br.com.sicredi.woop" to listOf("sicredi", "woop"),
        "br.com.sicredi.cartoes" to listOf("sicredi"),
        "com.sicredi.app" to listOf("sicredi"),
        "io.cloudwalk.infinitepaydash" to listOf("infinitepay", "infinite pay", "cloudwalk"),
        "io.cloudwalk.infinitepay" to listOf("infinitepay", "infinite pay", "cloudwalk"),
        "br.com.infinitepay" to listOf("infinitepay", "infinite pay", "cloudwalk"),
        "com.infinitepay.app" to listOf("infinitepay", "infinite pay", "cloudwalk"),
        "io.cloudwalk.pos" to listOf("infinitepay", "infinite pay", "cloudwalk"),
        "io.cloudwalk.infinitecash" to listOf("infinitepay", "infinite pay", "cloudwalk"),
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
        "br.com.sicoobnet.pessoal" to listOf("sicoob"),
        "br.com.unicred.mobile" to listOf("unicred"),
        "br.com.cresol.mobile" to listOf("cresol"),
        "br.com.banrisul" to listOf("banrisul"),
        "br.com.brb.mobile" to listOf("brb"),
        "br.com.brb.brbcard" to listOf("brb"),
        "br.com.brb.fla" to listOf("brb"),
        "pag.meu.app" to listOf("will", "will bank"),
        "br.com.willbank" to listOf("will", "will bank"),
        "br.com.digio.app" to listOf("digio"),
        "br.com.bancopan.app" to listOf("pan", "banco pan"),
        "com.bancopan.cartoes" to listOf("pan", "banco pan"),
        "com.bv.app" to listOf("bv"),
        "br.com.bancobmg.meu_bmg" to listOf("bmg"),
        "br.com.sofisadireto" to listOf("sofisa"),
        "br.com.agibank.app" to listOf("agibank"),
        "br.com.original.bank" to listOf("original"),
        "com.safra.wallet" to listOf("safra"),
        "br.com.stone.ton" to listOf("ton", "stone"),
        "br.com.stone.app" to listOf("stone"),
        "co.cora.app" to listOf("cora"),
        "br.com.asaas.mobile" to listOf("asaas"),
        "br.com.gerencianet.app" to listOf("efi", "gerencianet"),
        "com.recargapay.wallet" to listOf("recargapay"),
        "com.b2w.amedigital" to listOf("ame"),
        "com.sumup.merchant" to listOf("sumup"),
        "com.nomad.app" to listOf("nomad"),
        "com.transferwise.android" to listOf("wise"),
        "com.revolut.revolut" to listOf("revolut"),
        "us.avenue.app" to listOf("avenue"),
        "com.caju.beneficios" to listOf("caju"),
        "br.com.flashapp" to listOf("flash"),
        "br.com.alelo.meualelo" to listOf("alelo"),
        "br.com.sodexo.sodexobr.sodexoclub" to listOf("sodexo", "pluxee"),
        "com.pluxee.br" to listOf("pluxee", "sodexo"),
        "br.com.ticket.app" to listOf("ticket"),
        "com.swile.app" to listOf("swile")
    )

    private val KNOWN_BANK_KEYWORDS = listOf(
        "nubank", "itaú", "itau", "itaucard", "iti", "credicard", "hipercard",
        "bradesco", "next", "inter", "santander", "way", "banco do brasil",
        "ourocard", "caixa", "sicredi", "woop", "sicoob", "unicred", "cresol",
        "infinitepay", "infinite pay", "cloudwalk", "c6 bank", "c6", "picpay",
        "mercado pago", "btg", "xp", "neon", "pagbank", "banrisul", "brb",
        "will", "will bank", "digio", "banco pan", "bv", "bmg", "sofisa",
        "agibank", "original", "safra", "stone", "ton", "cora", "asaas", "efi",
        "recargapay", "nomad", "wise", "revolut", "avenue", "ifood", "caju",
        "flash", "alelo", "pluxee", "sodexo", "ticket", "swile",
        "black", "platinum", "ultravioleta", "infinite"
    )

    private val IGNORE_PATTERNS = listOf(
        // Vendas recebidas na maquininha/POS de lojistas (não confundir com movimentação pessoal) e promoções de rendimento/cashback
        Regex("""\bvenda\s+(?:aprovada|realizada|conclu[ií]da|no\s+(?:cr[eé]dito|d[eé]bito|pix))\b""", RegexOption.IGNORE_CASE),
        Regex("""\bvoc[eê]\s+vendeu\b""", RegexOption.IGNORE_CASE),
        Regex("""\brecebimento\s+aprovad[ao]\b""", RegexOption.IGNORE_CASE),
        Regex("""\bvalor\s+a\s+receber\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:pix|ted|valor)\s+(?:devolvido|estornado)\b""", RegexOption.IGNORE_CASE),
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

    private val INCOME_TRIGGER_REGEX = Regex(
        """\b(pix\s+recebido|recebimento\s+de\s+pix|voc[eê]\s+recebeu\s+(?:uma?\s+)?(?:pix|transfer[eê]ncia|pagamento|valor|cr[eé]dito|r\$)|voc[eê]\s+recebeu\b.*\bpix\b|recebeu\s+(?:uma?\s+)?pix|transfer[eê]ncia\s+(?:pix\s+)?recebida|dep[oó]sito\s+recebido|pagamento\s+recebido|ted\s+recebid[ao]|doc\s+recebid[ao]|pix\b.*\b(?:recebido|creditado|caiu\s+na\s+conta))\b""",
        RegexOption.IGNORE_CASE
    )

    private val EXPENSE_TRIGGER_REGEX = Regex(
        """\b(compra|aprovad[ao]|d[eé]bito|cr[eé]dito|pix\s+(?:enviado|realizado|efetuado|conclu[ií]do|pago|para)|envio\s+de\s+pix|voc[eê]\s+(?:enviou|transferiu|pagou|realizou\s+(?:um\s+)?pix|fez\s+(?:um\s+)?(?:pix|pagamento|compra))|enviou\s+(?:um\s+)?pix|transfer[eê]ncia\s+(?:enviada|realizada|conclu[ií]da)|pagamento\s+(?:efetuado|aprovado|realizado|conclu[ií]do|via\s+pix|com\s+pix|de\s+boleto)|transa[çc][aã]o\s+(?:aprovada|realizada|efetuada|no\s+valor|de\s+r\$)|pagou\s+(?:com\s+pix|via\s+pix|r\$)|pix\b.*\b(?:enviado|realizado|efetuado|conclu[ií]do|pago))\b""",
        RegexOption.IGNORE_CASE
    )

    private val STRONG_FALLBACK_BANK_REGEX = Regex(
        """\b(cart[aã]o|cr[eé]dito|d[eé]bito|pix|compra\s+aprovada|pagamento\s+(?:aprovado|realizado|efetuado)|transa[çc][aã]o\s+(?:aprovada|realizada))\b""",
        RegexOption.IGNORE_CASE
    )

    private val BRL_CURRENCY_REGEX = Regex(
        """r\$[\s\u00A0\u2007\u202F]*([\d.]+(?:,\d{1,2})?|\d+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val FALLBACK_VALUE_REGEX = Regex(
        """\bvalor(?:\s+da\s+(?:compra|transa[çc][aã]o|pagamento))?(?:\s+de)?[\s:]+r?\$?[\s\u00A0\u2007\u202F]*([\d.]+,\d{2})\b""",
        RegexOption.IGNORE_CASE
    )

    private val CARD_LAST_4_REGEX = Regex(
        """(?:(?:\b(?:final|terminado\s+em|cart[aã]o(?:\s+de\s+cr[eé]dito)?(?:\s+final)?)\b[:\s]*)|[*•·xX]{2,4}\s*)(\d{4})\b""",
        RegexOption.IGNORE_CASE
    )

    private val SMART_WALLET_CARD_HINT_REGEX = Regex(
        """(?:\b(?:visa|mastercard|maestro|elo|amex|american\s+express|hipercard|cart[aã]o|cr[eé]dito|d[eé]bito|aproxima[çc][aã]o|nfc|final)\b|[*•·xX]{2,4}\s*\d{4})""",
        RegexOption.IGNORE_CASE
    )

    private val GENERIC_NOTIFICATION_TITLES_REGEX = Regex(
        """^(?:compra(?:\s+(?:aprovada|no\s+(?:credito|debito)|com\s+cartao|realizada|efetuada))?|pagamento(?:\s+(?:aprovado|realizado|efetuado|enviado|via\s+pix))?|transacao(?:\s+(?:aprovada|realizada))?|transferencia(?:\s+(?:enviada|realizada|pix))?|envio\s+de\s+pix|pix(?:\s+(?:enviado|realizado))?|debito|credito|notificacao|aviso|alerta|google\s*(?:pay|wallet)|carteira\s+do\s+google|samsung\s*(?:pay|wallet)|apple\s*pay|nubank|itau|itaucard|iti|credicard|hipercard|bradesco(?:\s+debito|\s+cartoes)?|next|banco\s+inter|inter|santander(?:\s+way)?|way|banco\s+do\s+brasil|ourocard|caixa(?:\s+tem|\s+cartoes)?|sicredi(?:\s+cartoes|\s+pix)?|woop|infinite\s*pay|c6\s*bank|picpay|mercado\s+pago|btg(?:\s+pactual)?|xp|neon|pagbank|pagseguro|sicoob|unicred|cresol|banrisul|brb|will\s*bank|digio|banco\s+pan|bv|bmg|sofisa|agibank|original|safra|stone|ton|cora|asaas|efi|recargapay|nomad|wise|revolut|avenue|ifood\s+beneficios|caju|flash|alelo|pluxee|sodexo|ticket|swile)$""",
        RegexOption.IGNORE_CASE
    )

    fun isSmartWalletPackage(packageName: String?): Boolean {
        val norm = packageName?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (norm.isBlank()) return false
        if (norm in SMART_WALLET_PACKAGES_LOWER) return true
        return norm.contains("walletnfcrel") ||
            norm.contains("samsungpay") ||
            norm.endsWith(".spay") ||
            norm.endsWith(".spaymini")
    }

    fun isIgnorableNotification(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return IGNORE_PATTERNS.any { it.containsMatchIn(text) }
    }

    fun isExcludedNonBankPackage(packageName: String?): Boolean {
        val norm = packageName?.trim()?.lowercase(Locale.ROOT) ?: return true
        if (norm in SUPPORTED_BANK_PACKAGES_LOWER) return false
        return norm in EXCLUDED_NON_BANK_PACKAGES ||
            norm.startsWith("com.whatsapp") ||
            norm.startsWith("org.telegram")
    }

    fun isSupportedBankPackage(packageName: String?): Boolean {
        val norm = packageName?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (norm.isBlank()) return false
        if (norm in SUPPORTED_BANK_PACKAGES_LOWER) return true
        if (isExcludedNonBankPackage(norm)) return false
        return BANK_PACKAGE_KEYWORDS.any { norm.contains(it) }
    }

    fun containsCurrencyAmount(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return parseBrlAmount(text) != null
    }

    /**
     * Valida se a notificação vem de um app bancário conhecido OU de um pacote financeiro
     * não listado que apresente assinatura inequívoca de compra/Pix com valor em BRL.
     */
    fun isSupportedBankNotification(packageName: String?, title: String?, text: String?): Boolean {
        if (isSupportedBankPackage(packageName)) return true
        if (isExcludedNonBankPackage(packageName)) return false

        val sanitized = AgentSecuritySanitizer.sanitizeText(
            listOfNotNull(title, text).joinToString(" - "),
            maxLength = 600
        )
        if (sanitized.isBlank()) return false
        if (isIgnorableNotification(sanitized)) return false
        if (!containsCurrencyAmount(sanitized)) return false
        return INCOME_TRIGGER_REGEX.containsMatchIn(sanitized) ||
            (EXPENSE_TRIGGER_REGEX.containsMatchIn(sanitized) &&
                STRONG_FALLBACK_BANK_REGEX.containsMatchIn(sanitized))
    }

    /**
     * Analisa uma notificação bancária e gera um [ExpenseDraft] para despesa ou entrada (Pix recebido).
     * Retorna `null` para notificações promocionais, vendas POS de lojista, códigos 2FA, compras negadas ou sem valor válido.
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

        // 2. Descarta notificações ignoráveis (vendas POS lojista, propaganda, limite, 2FA, compra negada)
        if (isIgnorableNotification(combinedRaw)) {
            return null
        }

        // 3. Detecta se a notificação representa uma entrada (Pix/transferência recebida) ou despesa
        val isSmartWallet = isSmartWalletPackage(packageName)
        val transactionType = when {
            INCOME_TRIGGER_REGEX.containsMatchIn(combinedRaw) -> TransactionType.INCOME
            EXPENSE_TRIGGER_REGEX.containsMatchIn(combinedRaw) -> TransactionType.EXPENSE
            isSmartWallet &&
                (SMART_WALLET_CARD_HINT_REGEX.containsMatchIn(combinedRaw) ||
                    containsCurrencyAmount(sanitizedText)) -> TransactionType.EXPENSE
            else -> return null
        }

        // 4. Extrai e valida o valor monetário em BRL
        val amount = parseBrlAmount(combinedRaw) ?: return null

        // 5. Identifica o método de pagamento (Vale VA/VR, Pix, Crédito, Débito ou Boleto)
        val pkgLower = packageName.trim().lowercase(Locale.ROOT)
        val isValeApp = pkgLower.contains("alelo") ||
            pkgLower.contains("caju") ||
            pkgLower.contains("flash") ||
            pkgLower.contains("swile") ||
            pkgLower.contains("pluxee") ||
            pkgLower.contains("sodexo") ||
            pkgLower.contains("ticket")
        val detectedMethod = detectPaymentMethod(combinedRaw)
        val paymentMethod = when {
            isValeApp || detectedMethod == PaymentMethod.VA_VR -> PaymentMethod.VA_VR
            transactionType == TransactionType.INCOME -> PaymentMethod.PIX
            else -> detectedMethod
        }

        // 6. Associa o cartão cadastrado se for compra no crédito ou no cartão Vale (VA/VR)
        val matchedCard = when {
            paymentMethod == PaymentMethod.VA_VR ->
                resolveCard(packageName, combinedRaw, cards.filter { it.isVaVrCard })
                    ?: cards.firstOrNull { it.isVaVrCard }
            transactionType == TransactionType.EXPENSE && paymentMethod == PaymentMethod.CREDIT_CARD ->
                resolveCard(packageName, combinedRaw, cards.filter { !it.isVaVrCard })
            else -> null
        }

        // 7. Extrai o estabelecimento / destinatário / remetente bruto e infere categoria em cascata
        val rawMerchant = extractMerchantOrRecipient(
            sanitizedTitle = sanitizedTitle,
            sanitizedText = sanitizedText,
            paymentMethod = paymentMethod,
            transactionType = transactionType
        )

        // 8. Infere categoria em cascata (LearnedRulesManager -> NeuralFinanceClassifier -> CategoryRegistry)
        val (category, subcategory) = inferCategoryCascade(
            description = rawMerchant,
            sanitizedCombined = combinedRaw,
            amount = amount,
            learnedRules = learnedRules,
            neuralClassifier = neuralClassifier,
            transactionType = transactionType
        )

        // 9. Converte nome fantasia / código de maquininha em descrição financeira legível
        val smartDescription = humanizeTransactionDescription(
            rawMerchant = rawMerchant,
            sanitizedCombined = combinedRaw,
            paymentMethod = paymentMethod,
            category = category,
            subcategory = subcategory,
            transactionType = transactionType
        )

        return ExpenseDraft(
            amount = amount,
            description = smartDescription,
            type = transactionType,
            category = category,
            subcategory = subcategory,
            paymentMethod = paymentMethod,
            cardId = matchedCard?.id,
            cardName = matchedCard?.cardName,
            date = date,
            rawNotificationText = combinedRaw,
            packageName = packageName.trim(),
            rawMerchant = rawMerchant,
            aiEnriched = false
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
        val normalizedSpace = text.replace(Regex("""[\u00A0\u2007\u202F]+"""), " ")
        val match = BRL_CURRENCY_REGEX.find(normalizedSpace)
            ?: FALLBACK_VALUE_REGEX.find(normalizedSpace)
            ?: return null
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
            Regex("""\b(va|vr|va/vr|vale[- ]?alimentacao|vale[- ]?refeicao|ifood[- ]?beneficios|alelo|flash|caju|ticket|pluxee|sodexo|swile)\b""")
                .containsMatchIn(folded) -> PaymentMethod.VA_VR
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
        val normPkg = packageName.trim().lowercase(Locale.ROOT)
        val pkgHints = PACKAGE_BANK_HINTS[normPkg] ?: emptyList()
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
        paymentMethod: PaymentMethod,
        transactionType: TransactionType = TransactionType.EXPENSE
    ): String {
        val candidates = listOf(sanitizedText, sanitizedTitle, "$sanitizedTitle $sanitizedText")
            .map { it.replace(Regex("""[\u00A0\u2007\u202F]+"""), " ").trim() }
            .filter { it.isNotBlank() }

        if (transactionType == TransactionType.INCOME) {
            for (candidate in candidates) {
                val incomeSurface = candidate
                    .replace(
                        Regex("""\b(?:(?:no\s+)?valor\s+(?:de\s+)?|de\s+)?r\$[\s\u00A0\u2007\u202F]*[\d.,]+""", RegexOption.IGNORE_CASE),
                        " "
                    )
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                // Padrão 0 (Entrada): Rótulo explícito ("Remetente: ...", "Pagador: ...", "Origem: ...", "De: ...")
                val labeledSenderRegex = Regex(
                    """\b(?:remetente|pagador|origem|enviado\s+por|de)\s*:\s*(.+?)(?=\s+(?:no\s+valor|valor\s+de|com\s+sucesso|em\s+\d{1,2}/\d{1,2}|[àa]s\s+\d{1,2}[:h]|via\s+pix|na\s+sua\s+conta|em\s+sua\s+conta|pelo\s+app)\b|\s*\[neutralizado\]|\.(?=\s|$)|[!?\n]|$)""",
                    RegexOption.IGNORE_CASE
                )
                for (match in labeledSenderRegex.findAll(incomeSurface)) {
                    val rawSender = match.groupValues[1].trim()
                    if (isValidIncomeSender(rawSender)) {
                        return cleanMerchantName(rawSender)
                    }
                }

                // Padrão 1 (Entrada): "de <Remetente>" (após neutralizar trechos de valor monetário "de R$ ...")
                val senderRegex = Regex(
                    """\bde\s+(.+?)(?=\s+(?:no\s+valor|valor\s+de|com\s+sucesso|em\s+\d{1,2}/\d{1,2}|[àa]s\s+\d{1,2}[:h]|via\s+pix|na\s+sua\s+conta|em\s+sua\s+conta|pelo\s+app|foi\s+recebido)\b|\s*\[neutralizado\]|\.(?=\s|$)|[!?\n]|$)""",
                    RegexOption.IGNORE_CASE
                )
                for (match in senderRegex.findAll(incomeSurface)) {
                    val rawSender = match.groupValues[1].trim()
                    if (isValidIncomeSender(rawSender)) {
                        return cleanMerchantName(rawSender)
                    }
                }
            }
            return "Pix Recebido"
        }

        for (candidate in candidates) {
            val safeText = candidate.trim()

            // Padrão 0: Rótulo explícito (Sicredi / InfinitePay / bancos tradicionais)
            val labeledRegex = Regex(
                """\b(?:estabelecimento|local|favorecido|destinat[áa]rio|benefici[áa]rio|recebedor|loja)\s*:\s*(.+?)(?=\s+(?:para\s+o\s+cart[aã]o|no\s+cart[aã]o|cart[aã]o(?:\s+final)?|final\s+\d{4}|no\s+valor|valor\s+de|com\s+sucesso|em\s+\d{1,2}/\d{1,2}|[àa]s\s+\d{1,2}[:h]|via\s+pix|com\s+pix|aprovad[ao]|realizada|efetuada|pelo\s+app)\b|\s*\[neutralizado\]|\.(?=\s|$)|[!?\n]|$)""",
                RegexOption.IGNORE_CASE
            )
            for (match in labeledRegex.findAll(safeText)) {
                val rawLabeled = match.groupValues[1].trim()
                if (isValidExtractedMerchant(rawLabeled)) {
                    return cleanMerchantName(rawLabeled)
                }
            }

            // Padrão 1: "em <Estabelecimento>" ou "no estabelecimento <Estabelecimento>"
            val merchantRegex = Regex(
                """\b(?:no\s+estabelecimento|estabelecimento|em)\s+(.+?)(?=\s+(?:para\s+o\s+cart[aã]o|no\s+cart[aã]o|com\s+o\s+cart[aã]o|no\s+seu\s+cart[aã]o|cart[aã]o(?:\s+final)?|final\s+\d{4}|no\s+valor|valor\s+de|com\s+sucesso|em\s+\d{1,2}/\d{1,2}|em\s+\d{4}|[àa]s\s+\d{1,2}[:h]|via\s+pix|com\s+pix|aprovad[ao]|realizada|efetuada|pelo\s+app|pela\s+maquininha|com\s+(?:visa|mastercard|maestro|elo|amex|american\s+express|hipercard|cart[aã]o|final))\b|\s+com\s+[*•·xX]{2,4}|\s*[*•·xX]{2,4}\s*\d{4}|\s+[•·]\s+|\s*\[neutralizado\]|\.(?=\s|$)|[!?\n]|$)""",
                RegexOption.IGNORE_CASE
            )
            for (match in merchantRegex.findAll(safeText)) {
                val rawMerchant = match.groupValues[1].trim()
                if (isValidExtractedMerchant(rawMerchant)) {
                    return cleanMerchantName(rawMerchant)
                }
            }

            // Padrão 2 (Pix / Pagamento / Transferência): "para <Destinatário>"
            val foldedCandidate = CategoryRegistry.foldCategoryAccents(safeText)
            if (paymentMethod == PaymentMethod.PIX ||
                paymentMethod == PaymentMethod.BILL ||
                foldedCandidate.contains("pagamento") ||
                foldedCandidate.contains("pagou")
            ) {
                val pixRecipientRegex = Regex(
                    """\bpara\s+(.+?)(?=\s+(?:via\s+pix|com\s+pix|no\s+valor|valor\s+de|com\s+sucesso|em\s+\d{1,2}/\d{1,2}|[àa]s\s+\d{1,2}[:h]|chave\s+pix|pelo\s+app|pela\s+conta|na\s+conta|no\s+cart[aã]o|cart[aã]o(?:\s+final)?)\b|\s*\[neutralizado\]|\.(?=\s|$)|[!?\n]|$)""",
                    RegexOption.IGNORE_CASE
                )
                for (match in pixRecipientRegex.findAll(safeText)) {
                    val rawRecipient = match.groupValues[1].trim()
                    if (isValidExtractedMerchant(rawRecipient) &&
                        !rawRecipient.startsWith("o cartão", ignoreCase = true) &&
                        !rawRecipient.startsWith("o cartao", ignoreCase = true) &&
                        !rawRecipient.startsWith("sua segurança", ignoreCase = true) &&
                        !rawRecipient.startsWith("sua seguranca", ignoreCase = true)
                    ) {
                        return cleanMerchantName(rawRecipient)
                    }
                }
            }

            // Padrão 3: Separador com hífen após valor (ex: "Débito em conta de R$ 75,00 - Farmácia São Paulo")
            val dashMerchantRegex = Regex(
                """r\$\s*[\d.,]+\s*[-–—]\s*(.+?)(?=\s+(?:no\s+cart[aã]o|final\s+\d{4}|com\s+sucesso|[àa]s\s+\d{1,2}[:h]|em\s+\d{1,2}/\d{1,2}|com\s+(?:visa|mastercard|maestro|elo|amex|american\s+express|hipercard|cart[aã]o|final))\b|\s+com\s+[*•·xX]{2,4}|\s*[*•·xX]{2,4}\s*\d{4}|\s+[•·]\s+|\s*\[neutralizado\]|\.(?=\s|$)|[!?\n]|$)""",
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

        // Padrão 4: Título como nome do estabelecimento (comum em Google Wallet e Samsung Wallet)
        val cleanedTitleCandidate = sanitizedTitle
            .replace(Regex("""[\u00A0\u2007\u202F]+"""), " ")
            .replace(Regex("""\[neutralizado\]:?""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleanedTitleCandidate.isNotBlank() &&
            !containsCurrencyAmount(cleanedTitleCandidate) &&
            !GENERIC_NOTIFICATION_TITLES_REGEX.matches(
                CategoryRegistry.foldCategoryAccents(cleanMerchantName(cleanedTitleCandidate))
            ) &&
            isValidExtractedMerchant(cleanedTitleCandidate)
        ) {
            return cleanMerchantName(cleanedTitleCandidate)
        }

        // Fallback seguro usando NeuralFinanceClassifier sem texto residual
        val fallbackRaw = sanitizedText.ifBlank { sanitizedTitle }
            .replace(Regex("""\[neutralizado\]:?""", RegexOption.IGNORE_CASE), "")
            .trim()
        val guessed = NeuralFinanceClassifier(null).extractDescription(fallbackRaw)
        return cleanMerchantName(guessed).ifBlank { "Compra detectada" }
    }

    /**
     * Transforma nomes fantasia técnicos, razões sociais ou códigos de maquininha
     * em uma descrição financeira inteligente e legível para exibição imediata
     * (complementada e aprofundada pelo Gemini quando houver conectividade).
     */
    fun humanizeTransactionDescription(
        rawMerchant: String,
        sanitizedCombined: String,
        paymentMethod: PaymentMethod,
        category: String,
        subcategory: String? = null,
        transactionType: TransactionType = TransactionType.EXPENSE
    ): String {
        val normalizedRaw = stripPosAndCorporateNoise(rawMerchant)
        val folded = CategoryRegistry.foldCategoryAccents("$rawMerchant $normalizedRaw $sanitizedCombined")
        val foldedMerchant = CategoryRegistry.foldCategoryAccents(normalizedRaw.ifBlank { rawMerchant })

        if (transactionType == TransactionType.INCOME) {
            val cleanedIncomeSource = toSmartTitleCase(normalizedRaw.ifBlank { rawMerchant })
            if (foldedMerchant.isBlank() ||
                foldedMerchant == "pix recebido" ||
                foldedMerchant == "compra detectada" ||
                foldedMerchant == "recebimento" ||
                foldedMerchant == "recebimento de pix"
            ) {
                return "Pix recebido"
            }
            if (cleanedIncomeSource.startsWith("Pix ", ignoreCase = true)) {
                return AgentSecuritySanitizer.sanitizeText(cleanedIncomeSource, maxLength = 100)
            }
            return AgentSecuritySanitizer.sanitizeText("Pix recebido de $cleanedIncomeSource", maxLength = 100)
        }

        // Mapeamento inteligente de marcas com nomes técnicos/maquininha comuns
        val brandRule = when {
            foldedMerchant.contains("ifood") -> "Pedido no iFood"
            foldedMerchant.contains("99food") -> "Pedido no 99Food"
            foldedMerchant.contains("uber eats") -> "Pedido no Uber Eats"
            Regex("""\buber\b""").containsMatchIn(foldedMerchant) -> "Corrida Uber"
            Regex("""\b(99app|99pop|99\s*taxi)\b""").containsMatchIn(foldedMerchant) -> "Corrida 99"
            foldedMerchant.contains("rappi") -> "Pedido no Rappi"
            foldedMerchant.contains("ze delivery") -> "Pedido Zé Delivery"
            foldedMerchant.contains("mcdonald") -> "Lanche McDonald's"
            foldedMerchant.contains("burger king") -> "Lanche Burger King"
            foldedMerchant.contains("starbucks") -> "Café Starbucks"
            foldedMerchant.contains("drogasil") -> "Farmácia Drogasil"
            foldedMerchant.contains("droga raia") || foldedMerchant.contains("drogaraia") -> "Farmácia Droga Raia"
            foldedMerchant.contains("pague menos") -> "Farmácia Pague Menos"
            foldedMerchant.contains("panvel") -> "Farmácia Panvel"
            foldedMerchant.contains("ultrafarma") -> "Farmácia Ultrafarma"
            foldedMerchant.contains("netflix") -> "Assinatura Netflix"
            foldedMerchant.contains("spotify") -> "Assinatura Spotify"
            foldedMerchant.contains("disney") -> "Assinatura Disney+"
            foldedMerchant.contains("amazon prime") || foldedMerchant.contains("prime video") -> "Assinatura Amazon Prime"
            foldedMerchant.contains("amazon") -> "Compra na Amazon"
            foldedMerchant.contains("mercado livre") || foldedMerchant.contains("mercadolivre") -> "Compra no Mercado Livre"
            foldedMerchant.contains("shopee") -> "Compra na Shopee"
            foldedMerchant.contains("shein") -> "Compra na Shein"
            foldedMerchant.contains("magazine luiza") || foldedMerchant.contains("magalu") -> "Compra no Magazine Luiza"
            foldedMerchant.contains("carrefour") -> "Supermercado Carrefour"
            foldedMerchant.contains("assai") -> "Supermercado Assaí"
            foldedMerchant.contains("atacadao") -> "Supermercado Atacadão"
            foldedMerchant.contains("pao de acucar") -> "Supermercado Pão de Açúcar"
            foldedMerchant.contains("shell") -> "Combustível Posto Shell"
            foldedMerchant.contains("ipiranga") -> "Combustível Posto Ipiranga"
            foldedMerchant.contains("petrobras") || Regex("""\bposto\s+br\b""").containsMatchIn(foldedMerchant) ->
                "Combustível Posto BR"
            folded.contains("infinitepay") && foldedMerchant.isBlank() -> "Compra via InfinitePay"
            else -> null
        }

        if (brandRule != null) {
            return AgentSecuritySanitizer.sanitizeText(brandRule, maxLength = 100)
        }

        val cleanedTitleCase = toSmartTitleCase(normalizedRaw.ifBlank { rawMerchant })
            .ifBlank { "Compra detectada" }

        if (paymentMethod == PaymentMethod.PIX && !cleanedTitleCase.startsWith("Pix ", ignoreCase = true)) {
            return AgentSecuritySanitizer.sanitizeText("Pix para $cleanedTitleCase", maxLength = 100)
        }

        if (Regex("""\bposto\b""").containsMatchIn(foldedMerchant) &&
            !cleanedTitleCase.startsWith("Combustível", ignoreCase = true)
        ) {
            return AgentSecuritySanitizer.sanitizeText("Combustível $cleanedTitleCase", maxLength = 100)
        }

        if ((category == "Saúde" && subcategory == "Farmácia & Remédios") &&
            !foldedMerchant.contains("farmacia") &&
            !foldedMerchant.contains("drogaria")
        ) {
            return AgentSecuritySanitizer.sanitizeText("Farmácia $cleanedTitleCase", maxLength = 100)
        }

        return AgentSecuritySanitizer.sanitizeText(cleanedTitleCase, maxLength = 100)
    }

    private fun stripPosAndCorporateNoise(raw: String): String {
        return raw
            .replace(Regex("""^(?:ifd|pag|pg|mp|mercadopago|ec|ebw|sumup|ston|ton|infinitepay|infinite|cloudwalk|cielo|rede|getnet|zet|iz|pay|uol|google)\s*[*•\-]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\*+"""), " ")
            .replace(Regex("""\.(?:com(?:\.br)?|net|org|store|shop)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:filial|loja|unid(?:ade)?|lj)\s*(?:n?[°º]?\s*\d+)?\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:ltda|s/?a|eireli|mei?|epp|cia)\b\.?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""#\d+"""), "")
            .replace(Regex("""\s+\d{1,4}$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun toSmartTitleCase(input: String): String {
        val minorWords = setOf("de", "da", "do", "das", "dos", "e", "em", "no", "na", "nos", "nas", "para", "com")
        val upperAcronyms = setOf("br", "pix", "bb", "gn", "rj", "sp", "mg", "rs", "pr")
        return input.trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .mapIndexed { idx, word ->
                val lower = word.lowercase(Locale.ROOT)
                when {
                    idx > 0 && lower in minorWords -> lower
                    lower in upperAcronyms -> lower.uppercase(Locale.ROOT)
                    lower.length <= 1 -> lower.uppercase(Locale.ROOT)
                    else -> lower.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.forLanguageTag("pt-BR")) else ch.toString()
                    }
                }
            }
            .joinToString(" ")
    }

    private fun isValidIncomeSender(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (!isValidExtractedMerchant(trimmed)) return false
        if (Regex("""^(?:r\$\s*)?[\d.,]+(?:[.,]\d{1,2})?$""", RegexOption.IGNORE_CASE).matches(trimmed)) {
            return false
        }
        val folded = CategoryRegistry.foldCategoryAccents(trimmed)
        if (Regex("""^(?:pix|transferencia|pagamento|valor|dinheiro|sua\s+conta|banco|reais|uma|um)$""").matches(folded)) {
            return false
        }
        return true
    }

    private fun isValidExtractedMerchant(candidate: String): Boolean {
        if (candidate.length < 2) return false
        val lower = CategoryRegistry.foldCategoryAccents(candidate)
        if (Regex("""^\d{1,2}/\d{1,2}(?:/\d{2,4})?$""").matches(lower)) return false
        if (Regex("""^\d+\s*x$""").matches(lower)) return false
        if (Regex("""^(seu\s+cartao|sua\s+conta|processamento|analise|breve|reais|real|credito|debito|sucesso)$""").matches(lower)) return false
        if (lower.startsWith("seu cartao") || lower.startsWith("sua conta") || lower.startsWith("com sucesso")) return false
        return true
    }

    private fun cleanMerchantName(raw: String): String {
        var cleaned = raw
            .replace(Regex("""\[neutralizado\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:aprovad[ao]|realizada|efetuada|com\s+sucesso|no\s+cr[eé]dito|no\s+d[eé]bito)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[-–—:\s]+|[-–—.,;:!?\s]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        cleaned = AgentSecuritySanitizer.sanitizeText(cleaned, maxLength = 100)
        return cleaned.ifBlank { "Compra detectada" }
    }

    fun hasLearnedRuleMatch(
        description: String,
        sanitizedCombined: String,
        learnedRules: List<UserLearnedRule>,
        transactionType: TransactionType = TransactionType.EXPENSE
    ): Boolean {
        val searchSurface = CategoryRegistry.foldCategoryAccents("$description $sanitizedCombined")
        val targetCategories = if (transactionType == TransactionType.INCOME) {
            CategoryRegistry.INCOME_CATEGORIES
        } else {
            CategoryRegistry.EXPENSE_CATEGORIES
        }
        val candidateCategoryKeys = (targetCategories.map { it.name } +
            CategoryRegistry.CATEGORY_CANONICAL_MAP.keys)
            .distinct()
            .sortedByDescending { it.length }

        for (ruleObj in learnedRules.filter { it.active }) {
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
                val (strictCat, _) = CategoryRegistry.resolveStrictCategory(
                    rawCategory = matchedCatKey,
                    type = transactionType,
                    description = description
                )
                if (transactionType == TransactionType.INCOME &&
                    CategoryRegistry.INCOME_CATEGORIES.none { it.name == strictCat }
                ) {
                    continue
                }
                val keywords = foldedRule
                    .replace(CategoryRegistry.foldCategoryAccents(matchedCatKey), " ")
                    .split(Regex("""[\s,;.=:\->]+"""))
                    .map { it.trim() }
                    .filter { kw -> kw.length >= 2 && kw !in RULE_STOPWORDS }

                if (keywords.any { kw -> searchSurface.contains(kw) }) {
                    return true
                }
            }
        }
        return false
    }

    fun inferCategoryCascade(
        description: String,
        sanitizedCombined: String,
        amount: Double,
        learnedRules: List<UserLearnedRule>,
        neuralClassifier: NeuralFinanceClassifier,
        transactionType: TransactionType = TransactionType.EXPENSE
    ): Pair<String, String?> {
        val searchSurface = CategoryRegistry.foldCategoryAccents("$description $sanitizedCombined")

        // 1º estágio da cascata: LearnedRulesManager (regras aprendidas ativas do usuário)
        val activeRules = learnedRules.filter { it.active }
        val targetCategories = if (transactionType == TransactionType.INCOME) {
            CategoryRegistry.INCOME_CATEGORIES
        } else {
            CategoryRegistry.EXPENSE_CATEGORIES
        }
        val candidateCategoryKeys = (targetCategories.map { it.name } +
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
                    type = transactionType,
                    description = description
                )
                if (transactionType == TransactionType.INCOME &&
                    CategoryRegistry.INCOME_CATEGORIES.none { it.name == strictCat }
                ) {
                    continue
                }

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
        if (transactionType == TransactionType.INCOME) {
            val isOfficialIncome = CategoryRegistry.INCOME_CATEGORIES.any {
                it.name.equals(guess.category, ignoreCase = true)
            }
            val rawIncomeCat = if (isOfficialIncome) guess.category else "Outras Receitas"
            val rawIncomeSub = if (rawIncomeCat == "Outras Receitas" && guess.subcategory.isNullOrBlank()) {
                "Pix Recebido"
            } else {
                guess.subcategory
            }
            return CategoryRegistry.resolveStrictCategory(
                rawCategory = rawIncomeCat,
                rawSubcategory = rawIncomeSub,
                type = TransactionType.INCOME,
                description = description
            )
        }

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
