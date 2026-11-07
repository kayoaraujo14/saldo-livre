# Workflow: Resolução de Issue do GitHub

Use este workflow sempre que o usuário solicitar a resolução ou desenvolvimento de uma Issue do repositório (ex: "resolva a issue #52").

## Passo a Passo Obrigatório:

1. **Obter Contexto da Issue:**
   - Identifique o ID e os critérios de aceite descritos na Issue.
2. **Isolamento de Branch:**
   - Certifique-se de que a `main` local está sincronizada.
   - Crie e mude para a branch no padrão `<tipo>/issue-<ID>-<slug>`:
     - `fix/issue-<ID>-...` para bugs visuais ou lógicos.
     - `feat/issue-<ID>-...` para novos módulos ou componentes.
     - `perf/issue-<ID>-...` para performance e virtualização.
     - `sec/issue-<ID>-...` para regras de segurança e autenticação.
3. **Notificar Início na Issue:**
   - Execute:
     ```bash
     npm run issue:start <ID> <nome-da-branch>
     ```
   - Isso adicionará a label `status: in-progress` e postará o aviso oficial na Issue no GitHub.
4. **Implementação & Qualidade (Plan-Act-Verify):**
   - Implemente as alterações necessárias respeitando a paridade (TypeScript/Kotlin se houver impacto no motor de cálculo).
   - Execute linters e testes obrigatórios:
     - Web: `npm run lint` e `npm test` em `web/`.
     - Android: `./gradlew testDebugUnitTest` em `android/`.
5. **Commit Semântico:**
   - Faça o commit semântico com a cláusula de fechamento:
     ```bash
     git commit -m "tipo(escopo): descrição da mudança (closes #<ID>)"
     ```
6. **Push & Pull Request:**
   - Envie a branch: `git push -u origin <nome-da-branch>`.
   - Abra o Pull Request para acionar os Quality Gates do GitHub Actions (`quality-gates.yml`).
7. **Fechamento e Relatório:**
   - Execute o fechamento com relatório:
     ```bash
     npm run issue:close <ID> "Resumo das entregas e testes validados 100%."
     ```
   - Isso marcará os checkboxes da Issue como concluídos (`[x]`), adicionará a tag `status: done` e publicará o comentário final.
