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

## 5. Rastreamento Contínuo de Issues no GitHub
Sempre que uma tarefa corresponder a uma Issue cadastrada no GitHub:
- **Ao Iniciar (`start`):** Registre a branch de trabalho, aplique a label `status: in-progress` e comente o início do desenvolvimento na Issue:
  ```bash
  node scripts/github-issue-helper.cjs start <issueNumber> <nomeDaBranch>
  ```
- **Em Andamento (`comment`):** Registre decisões técnicas ou marcos parciais relevantes na Issue:
  ```bash
  node scripts/github-issue-helper.cjs comment <issueNumber> "<mensagem>"
  ```
- **Ao Concluir (`close`):** Nenhuma issue pode ser finalizada sem antes:
  1. Validar que linters e testes unitários das plataformas afetadas passaram 100% verdes.
  2. Atualizar o checklist/critérios de aceite da Issue para concluídos (`[x]`).
  3. Publicar o comentário de fechamento com resumo das entregas e marcar como `status: done`:
     ```bash
     node scripts/github-issue-helper.cjs close <issueNumber> "<resumo das entregas e testes>"
     ```
  4. Garantir commits semânticos referenciando a issue (ex: `fix(web): ... (closes #ID)`).
