import { describe, it, expect } from 'vitest'
import {
  parseOfx,
  parseCsv,
  deduplicateTransactions,
  parseUniversalStatement,
} from '../bankStatementParser'
import type { Transaction } from '@/types'

describe('Universal Bank Statement Parser (Web)', () => {
  const SAMPLE_OFX_SGML = `
OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE

<OFX>
<BANKMSGSRSV1>
<STMTTRNRS>
<STMTRS>
<CURDEF>BRL
<BANKTRANLIST>
<DTSTART>20261101000000
<DTEND>20261108235959
<STMTTRN>
<TRNTYPE>DEBIT
<DTPOSTED>20261104120000
<TRNAMT>-85.50
<FITID>202611040001
<MEMO>SUPERMERCADO ASSAI
</STMTTRN>
<STMTTRN>
<TRNTYPE>DEBIT
<DTPOSTED>20261105080000
<TRNAMT>-24.90
<FITID>202611050002
<NAME>UBER *TRIP
</STMTTRN>
<STMTTRN>
<TRNTYPE>CREDIT
<DTPOSTED>20261106150000
<TRNAMT>1500.00
<FITID>202611060003
<MEMO>PIX RECEBIDO DE EMPRESA XYZ
</STMTTRN>
<STMTTRN>
<TRNTYPE>OTHER
<DTPOSTED>20261107100000
<TRNAMT>-500.00
<FITID>202611070004
<MEMO>PAGAMENTO DE FATURA RECEBIDO
</STMTTRN>
</BANKTRANLIST>
</STMTRS>
</STMTTRNRS>
</BANKMSGSRSV1>
</OFX>
`

  const SAMPLE_CSV_SEMICOLON = `Data;Descrição;Valor;Identificador
04/11/2026;Supermercado Carrefour;-142,30;tx_01
05/11/2026;Posto Ipiranga;-90,00;tx_02
06/11/2026;Transferência Pix Recebida;350,00;tx_03
07/11/2026;Aplicação RDB Nubank;-200,00;tx_04
`

  const SAMPLE_CSV_COMMA = `date,title,amount,id
2026-11-02,Droga Raia,-45.80,csv_1
2026-11-03,Netflix,-55.90,csv_2
`

  it('faz o parsing correto de extrato OFX (SGML) com valores, FITID e datas', () => {
    const items = parseOfx(SAMPLE_OFX_SGML, '2026-11')

    expect(items).toHaveLength(4)

    // Item 1: Supermercado
    expect(items[0].date).toBe('2026-11-04')
    expect(items[0].amount).toBe(85.5)
    expect(items[0].type).toBe('expense')
    expect(items[0].fitId).toBe('202611040001')
    expect(items[0].category).toBe('Alimentação')
    expect(items[0].subcategory).toBe('Supermercado')
    expect(items[0].isIgnored).toBe(false)

    // Item 2: Uber
    expect(items[1].date).toBe('2026-11-05')
    expect(items[1].amount).toBe(24.9)
    expect(items[1].category).toBe('Transporte')
    expect(items[1].subcategory).toBe('Aplicativos (Uber/99)')

    // Item 3: Receita PIX
    expect(items[2].date).toBe('2026-11-06')
    expect(items[2].amount).toBe(1500)
    expect(items[2].type).toBe('income')
    expect(items[2].category).toBe('Receita')

    // Item 4: Pagamento de fatura (ignorado)
    expect(items[3].isIgnored).toBe(true)
    expect(items[3].ignoredReason).toContain('Pagamento de Fatura')
  })

  it('faz o parsing correto de extrato CSV bancário com delimitador de ponto e vírgula e formato monetário BRL', () => {
    const items = parseCsv(SAMPLE_CSV_SEMICOLON, '2026-11')

    expect(items).toHaveLength(4)
    expect(items[0].date).toBe('2026-11-04')
    expect(items[0].amount).toBe(142.3)
    expect(items[0].type).toBe('expense')
    expect(items[0].category).toBe('Alimentação')

    expect(items[1].date).toBe('2026-11-05')
    expect(items[1].amount).toBe(90.0)
    expect(items[1].category).toBe('Transporte')

    expect(items[2].date).toBe('2026-11-06')
    expect(items[2].amount).toBe(350.0)
    expect(items[2].type).toBe('income')

    expect(items[3].isIgnored).toBe(true)
    expect(items[3].ignoredReason).toContain('RDB')
  })

  it('faz o parsing correto de CSV com vírgula e datas no formato ISO', () => {
    const items = parseCsv(SAMPLE_CSV_COMMA, '2026-11')

    expect(items).toHaveLength(2)
    expect(items[0].date).toBe('2026-11-02')
    expect(items[0].amount).toBe(45.8)
    expect(items[0].category).toBe('Saúde')

    expect(items[1].date).toBe('2026-11-03')
    expect(items[1].amount).toBe(55.9)
    expect(items[1].category).toBe('Assinaturas')
  })

  it('detecta duplicatas por FITID e por assinatura composta (data + valor + contraparte)', () => {
    const parsedItems = parseOfx(SAMPLE_OFX_SGML, '2026-11')

    const mockExistingTxs: Transaction[] = [
      {
        id: 'tx_existing_1',
        amount: 85.5,
        type: 'expense',
        date: '2026-11-04',
        competenceMonth: '2026-11',
        description: 'Supermercado Assai',
        category: 'Alimentação',
        status: 'paid',
        paymentMethod: 'credit_card',
        userId: 'usr_1',
        createdAt: 1000,
        updatedAt: 1000,
      },
      {
        id: 'tx_existing_2',
        amount: 24.9,
        type: 'expense',
        date: '2026-11-05',
        competenceMonth: '2026-11',
        description: 'Corrida Aplicativo',
        category: 'Transporte',
        status: 'paid',
        paymentMethod: 'credit_card',
        userId: 'usr_1',
        metadata: {
          fitId: '202611050002', // Bate por FITID
        },
        createdAt: 1000,
        updatedAt: 1000,
      },
    ]

    const deduplicated = deduplicateTransactions(parsedItems, mockExistingTxs)

    // Item 1 (Assai): Deve ser marcado como duplicata pela assinatura composta
    expect(deduplicated[0].isDuplicate).toBe(true)
    expect(deduplicated[0].isSelected).toBe(false)
    expect(deduplicated[0].duplicateReason).toContain('já cadastrado')

    // Item 2 (Uber): Deve ser marcado como duplicata pelo FITID
    expect(deduplicated[1].isDuplicate).toBe(true)
    expect(deduplicated[1].isSelected).toBe(false)
    expect(deduplicated[1].duplicateReason).toContain('202611050002')

    // Item 3 (PIX recebido): Não existe no banco, deve permanecer não-duplicata e selecionado
    expect(deduplicated[2].isDuplicate).toBe(false)
    expect(deduplicated[2].isSelected).toBe(true)
  })

  it('parseUniversalStatement detecta automaticamente OFX e aplica deduplicação', () => {
    const items = parseUniversalStatement(SAMPLE_OFX_SGML, 'extrato_nov.ofx')
    expect(items).toHaveLength(4)
    expect(items[0].description).toBe('SUPERMERCADO ASSAI')
  })
})
