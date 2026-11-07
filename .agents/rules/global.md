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

## 5. Rastreamento de Issues no GitHub & Metodologia de Branches (GitHub Flow)
Sempre que o usuário pedir para resolver ou trabalhar em uma Issue do GitHub (ex: "resolva a issue #52"):
1. **Branch Obrigatória (Nunca commitar direto na `main`):**
   - Atualize a `main` e crie a branch dedicada a partir dela no padrão:
     - `fix/issue-<ID>-<slug>` (para bugs/limpeza)
     - `feat/issue-<ID>-<slug>` (para novas funcionalidades/telas)
     - `perf/issue-<ID>-<slug>` (para performance/virtualização)
     - `sec/issue-<ID>-<slug>` (para segurança/regras/app check)
   - Exemplo: `git checkout -b fix/issue-52-lint-cleanup`.
2. **Notificar Início no GitHub:**
   - Execute o helper de rastreamento para aplicar a label `status: in-progress` e postar o comentário de início na Issue:
     ```bash
     npm run issue:start <issueNumber> <nomeDaBranch>
     ```
3. **Desenvolvimento com Ciclo Plan-Act-Verify:**
   - Implemente o código de forma focada e cirúrgica.
   - Se houver decisões intermediárias relevantes, registre: `npm run issue:comment <issueNumber> "<mensagem>"`.
   - Garanta 100% de sucesso nos linters e testes unitários locais antes de qualquer commit.
4. **Commit Semântico & Referência Explícita:**
   - Faça o commit semântico vinculando o fechamento:
     ```bash
     git commit -m "tipo(escopo): descrição concisa da entrega (closes #<issueNumber>)"
     ```
5. **Push, Pull Request & Fechamento:**
   - Suba a branch (`git push -u origin <nomeDaBranch>`).
   - Crie o Pull Request para disparar os Quality Gates de CI/CD.
   - Atualize a Issue no GitHub, marcando os checkboxes de aceite como `[x]`, adicionando comentário com o relatório de testes e rotulando como `status: done`:
     ```bash
     npm run issue:close <issueNumber> "<resumo das entregas, testes executados e status>"
     ```
