# Workflow: Auditar Paridade

Auditoria somente-leitura entre Web e Android.

1. **Motores:** compare assinaturas e fórmulas matemáticas entre `web/src/lib/finance.ts` ↔ `android/.../data/engine/FinanceEngine.kt` e entre `web/src/lib/invoices.ts` ↔ `android/.../data/engine/InvoiceEngine.kt` (arredondamento de centavos, datas de fechamento/vencimento, parcelas, rateios).
2. **Ferramentas Gemini:** compare a lista de Function Declarations em `web/src/lib/agent/` (e `agentTools.ts`) com `android/.../data/ai/AndroidAgentToolsEngine.kt` (nomes, parâmetros, obrigatórios, comportamento).
3. **Relatório:** gere um relatório (tabela) apontando divergências de cálculo, arredondamento ou ferramentas ausentes em cada plataforma, com arquivo/linha e correção sugerida. Não altere código sem aprovação.
