---
name: global-rules
description: Diretrizes globais do monorepo Saldo Livre (Web + Android + Firestore)
trigger:
  type: glob
  patterns:
    - "**/*"
---

# Diretrizes Globais

## 1. Contexto Inicial
- SEMPRE leia `PROJECT_STATUS.md` (raiz) antes de iniciar qualquer planejamento arquitetural.
- SEMPRE atualize `PROJECT_STATUS.md` ao concluir uma feature.
- Carregue apenas os arquivos necessários; não leia `android/` em tarefas exclusivas de `web/` e vice-versa (exceto para espelhar lógica, ver regra 2).

## 2. Regra de Ouro: Paridade Multiplataforma
O Saldo Livre possui motores nativos independentes na Web (TypeScript) e no Android (Kotlin). Nenhuma regra matemática, cálculo de fatura, projeção, orçamento ou ferramenta do agente Gemini pode ser alterada em uma plataforma sem ser espelhada na outra **dentro do mesmo plano de trabalho**. Use as skills `cross-platform-finance-parity` e `gemini-agent-tools-sync`.

## 3. Sincronia de Contratos e Segurança
Toda alteração de schema de dados exige atualização simultânea em:
- a) `web/src/types/index.ts` e `web/src/store/types.ts`
- b) Modelos Kotlin em `android/app/src/main/java/com/saldolivre/app/data/model/`
- c) Dados de demonstração/mock: `web/src/lib/mockData.ts`, `web/src/store/demoState.ts`, `MockFinanceRepository.kt` e `FakeFinanceRepository.kt`
- d) Regras e índices do Firestore: `firestore.rules` e `firestore.indexes.json` (skill `firestore-contract-sync`)

Nunca versione chaves de API; garanta isolamento por `uid` em toda query.

## 4. Ciclo Plan-Act-Verify
- Mudanças médias/grandes: apresente um plano enxuto antes de codar.
- Nenhuma tarefa está concluída até que linters e testes unitários das plataformas afetadas sejam executados via terminal e passem com 100% de sucesso (Web: `npm run lint` + `npm test` em `web/`; Android: `./gradlew testDebugUnitTest`).
- UI alterada: siga a skill `ui-ux-validator`.
