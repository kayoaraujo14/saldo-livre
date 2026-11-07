---
name: gemini-agent-tools-sync
description: Mantém sincronizadas as ferramentas de Function Calling do assistente Gemini entre Web e Android. Use sempre que criar, editar ou corrigir ferramentas de Function Calling, prompts de sistema, gráficos no chat ou classificadores inteligentes.
---

# Sincronia das Ferramentas do Agente Gemini

Toda ferramenta, prompt de sistema, gráfico ou classificador alterado em uma plataforma deve ser espelhado na outra no mesmo plano (nomes, parâmetros, schema de Function Declaration e comportamento).

## Mapeamento Obrigatório de Arquivos
Android = `android/app/src/main/java/com/saldolivre/app/`.

| Domínio | Web | Android |
|---|---|---|
| Orquestração e balanceamento de chaves | `web/src/lib/gemini.ts` | `data/ai/GeminiLoadBalancer.kt`, `data/ai/ChatSessionManager.kt` |
| Definição e execução de tools | `web/src/lib/agentTools.ts` e `web/src/lib/agent/` (`transactionTools.ts`, `budgetTools.ts`, `billTools.ts`, `projectionTools.ts`, `queryTools.ts`, `managementTools.ts`, `chartTools.ts`) | `data/ai/AndroidAgentToolsEngine.kt` |
| Regras aprendidas e classificação | `web/src/lib/agent/rules.ts` | `data/ai/LearnedRulesManager.kt`, `data/ai/NeuralFinanceClassifier.kt` |
| Gráficos no chat | `web/src/components/ui/ChatChart.tsx` | `ui/screens/chat/DynamicChatChart.kt` |

Modo demo Web: verifique também `web/src/lib/demoAgent.ts`.

## Exigência de Testes
- Web: `web/src/lib/__tests__/agentToolsBatch.test.ts` e `web/src/lib/gemini.test.ts`
- Android (`android/app/src/test/java/com/saldolivre/app/data/ai/`): `GeminiLoadBalancerTest.kt`, `LearnedRulesManagerTest.kt`

Valide operações em lote e regras aprendidas, depois execute `npm test` e `./gradlew testDebugUnitTest`.
