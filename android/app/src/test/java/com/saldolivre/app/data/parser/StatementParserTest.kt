package com.saldolivre.app.data.parser

import com.saldolivre.app.data.model.Transaction
import com.saldolivre.app.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class StatementParserTest {

    private val SAMPLE_OFX = """
        OFXHEADER:100
        DATA:OFXSGML
        VERSION:102
        SECURITY:NONE
        <OFX>
        <BANKTRANLIST>
        <STMTTRN>
        <TRNTYPE>DEBIT
        <DTPOSTED>20261104120000
        <TRNAMT>-120.50
        <FITID>20261104001
        <MEMO>SUPERMERCADO ASSAI
        </STMTTRN>
        <STMTTRN>
        <TRNTYPE>CREDIT
        <DTPOSTED>20261105090000
        <TRNAMT>450.00
        <FITID>20261105002
        <MEMO>PIX RECEBIDO DE EMPRESA ABC
        </STMTTRN>
        <STMTTRN>
        <TRNTYPE>DEBIT
        <DTPOSTED>20261106180000
        <TRNAMT>-29.90
        <FITID>20261106003
        <NAME>UBER *TRIP
        </STMTTRN>
        </BANKTRANLIST>
        </OFX>
    """.trimIndent()

    private val SAMPLE_CSV_SEMICOLON = """
        Data;Descrição;Valor
        04/11/2026;Supermercado Pão de Açúcar;-210,40
        05/11/2026;Transferência Pix Recebida;600,00
        06/11/2026;Posto Shell;-85,00
    """.trimIndent()

    private val SAMPLE_CSV_COMMA = """
        date,title,amount,id
        2026-11-01,Farmacia Drogasil,-35.50,csv_101
        2026-11-02,Netflix,-55.90,csv_102
    """.trimIndent()

    @Test
    fun parseOfx_extraiCorretamenteItensComFitIdEValores() {
        val items = StatementParser.parseOfx(SAMPLE_OFX, "2026-11")

        assertEquals(3, items.size)

        // Item 1: Supermercado
        val item1 = items[0]
        assertEquals("2026-11-04", item1.date)
        assertEquals(120.50, item1.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, item1.type)
        assertEquals("20261104001", item1.fitId)
        assertEquals("Alimentação", item1.category)
        assertEquals("Supermercado", item1.subcategory)
        assertFalse(item1.isIgnored)

        // Item 2: Receita Pix
        val item2 = items[1]
        assertEquals("2026-11-05", item2.date)
        assertEquals(450.00, item2.amount, 0.001)
        assertEquals(TransactionType.INCOME, item2.type)
        assertEquals("20261105002", item2.fitId)
        assertEquals("Receita", item2.category)

        // Item 3: Uber
        val item3 = items[2]
        assertEquals("2026-11-06", item3.date)
        assertEquals(29.90, item3.amount, 0.001)
        assertEquals("Transporte", item3.category)
        assertEquals("Aplicativos (Uber/99)", item3.subcategory)
    }

    @Test
    fun parseCsv_comPontoEVirgulaEFormatoMoedaBRL() {
        val items = StatementParser.parseCsv(SAMPLE_CSV_SEMICOLON, "2026-11")

        assertEquals(3, items.size)
        assertEquals("2026-11-04", items[0].date)
        assertEquals(210.40, items[0].amount, 0.001)
        assertEquals(TransactionType.EXPENSE, items[0].type)
        assertEquals("Alimentação", items[0].category)

        assertEquals("2026-11-05", items[1].date)
        assertEquals(600.00, items[1].amount, 0.001)
        assertEquals(TransactionType.INCOME, items[1].type)

        assertEquals("2026-11-06", items[2].date)
        assertEquals(85.00, items[2].amount, 0.001)
        assertEquals("Transporte", items[2].category)
    }

    @Test
    fun parseCsv_comVirgulaEFormatoDecimalPonto() {
        val items = StatementParser.parseCsv(SAMPLE_CSV_COMMA, "2026-11")

        assertEquals(2, items.size)
        assertEquals("2026-11-01", items[0].date)
        assertEquals(35.50, items[0].amount, 0.001)
        assertEquals("Saúde", items[0].category)

        assertEquals("2026-11-02", items[1].date)
        assertEquals(55.90, items[1].amount, 0.001)
        assertEquals("Assinaturas", items[1].category)
    }

    @Test
    fun deduplicateStatementItems_identificaDuplicatasPorFitIdEAssinatura() {
        val items = StatementParser.parseOfx(SAMPLE_OFX, "2026-11")

        val existingTxs = listOf(
            Transaction(
                id = "tx_existing_1",
                totalAmount = 120.50,
                type = TransactionType.EXPENSE,
                dueDate = "2026-11-04",
                competenceMonth = "2026-11",
                description = "Supermercado Assai",
                category = "Alimentação",
                createdByUid = "usr_1"
            ),
            Transaction(
                id = "tx_existing_2",
                totalAmount = 29.90,
                type = TransactionType.EXPENSE,
                dueDate = "2026-11-06",
                competenceMonth = "2026-11",
                description = "Corrida Uber",
                category = "Transporte",
                createdByUid = "usr_1",
                notes = "FITID: 20261106003"
            )
        )

        val deduplicated = StatementParser.deduplicateStatementItems(items, existingTxs)

        // Item 1 (Assai): Duplicata por assinatura composta (data + valor + texto)
        assertTrue("Item 1 deve ser detectado como duplicata", deduplicated[0].isDuplicate)
        assertFalse("Item 1 duplicado não deve vir selecionado por padrão", deduplicated[0].isSelected)
        assertTrue("Justificativa deve mencionar lançamento já cadastrado", deduplicated[0].duplicateReason?.contains("já cadastrado") == true)

        // Item 2 (PIX Recebido): Não duplicata
        assertFalse("Item 2 não é duplicata", deduplicated[1].isDuplicate)
        assertTrue("Item 2 novo deve vir selecionado", deduplicated[1].isSelected)

        // Item 3 (Uber): Duplicata por FITID
        assertTrue("Item 3 deve ser detectado como duplicata por FITID", deduplicated[2].isDuplicate)
        assertFalse("Item 3 duplicado não deve vir selecionado", deduplicated[2].isSelected)
        assertTrue("Justificativa deve citar o FITID", deduplicated[2].duplicateReason?.contains("20261106003") == true)
    }
}
