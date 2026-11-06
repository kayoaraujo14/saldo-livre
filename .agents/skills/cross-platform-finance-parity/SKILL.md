---
name: cross-platform-finance-parity
description: Garante paridade entre os motores financeiros Web (TypeScript) e Android (Kotlin). Use sempre que modificar cálculos de saldo livre, saúde financeira, fechamento de mês (MonthClosure), faturas de cartão, parcelamentos, rateio entre amigos (splits/receivables) ou projeções futuras.
---

# Paridade Financeira Web ↔ Android

Toda mudança em um lado deve ser espelhada no outro no mesmo plano de trabalho (mesmas fórmulas, mesmo arredondamento de centavos, mesmos tratamentos de borda).

## Mapeamento Obrigatório de Arquivos

| Domínio | Web | Android (`android/app/src/main/java/com/saldolivre/app/`) |
|---|---|---|
| Motor Financeiro | `web/src/lib/finance.ts` | `data/engine/FinanceEngine.kt` |
| Motor de Faturas | `web/src/lib/invoices.ts` | `data/engine/InvoiceEngine.kt` |
| Parsers de Extrato | `web/src/lib/pdfParser.ts` | `data/parser/StatementParser.kt` |

## Exigência de Testes
Toda modificação exige casos de teste **idênticos** (mesmos inputs e outputs esperados) em ambas as plataformas:

- Web (`web/src/lib/__tests__/`): `finance.test.ts`, `budgets.test.ts`, `invoices.test.ts`
- Android (`android/app/src/test/java/com/saldolivre/app/data/engine/`): `FinanceEngineBudgetTest.kt`, `FinanceEngineProjectionTest.kt`, `InvoiceEngineTest.kt`

## Procedimento
1. Altere o motor de uma plataforma e anote assinatura, fórmula e arredondamento.
2. Replique no motor correspondente.
3. Adicione/atualize os testes idênticos nos dois lados.
4. Execute `npm test` (em `web/`) e `./gradlew testDebugUnitTest` (em `android/`); ambos devem passar.
5. Se o schema mudou, aplique também a skill `firestore-contract-sync`.
