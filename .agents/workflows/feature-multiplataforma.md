# Workflow: Feature Multiplataforma

Use para qualquer feature que toque dados ou regras de negócio.

1. **Contexto:** leia `PROJECT_STATUS.md` e identifique os contratos de dados afetados (coleções, tipos, modelos).
2. **Contratos:** atualize modelos e tipos na Web (`web/src/types/index.ts`, `web/src/store/types.ts`) e no Android (`data/model/`), mais os repositórios/dados Mock e Demo (`mockData.ts`, `demoState.ts`, `MockFinanceRepository.kt`, `FakeFinanceRepository.kt`). Se necessário, aplique `firestore-contract-sync`.
3. **Regra de negócio:** implemente de forma espelhada usando a skill `cross-platform-finance-parity` (e `gemini-agent-tools-sync` se envolver o agente).
4. **UI:** implemente em React (Desktop + `components/mobile/`) e em Jetpack Compose; valide com `ui-ux-validator`.
5. **Testes e fechamento:** crie/atualize testes no Vitest e no JUnit, execute `npm run lint && npm test` (em `web/`) e `./gradlew testDebugUnitTest` (em `android/`) até passarem 100%, e atualize `PROJECT_STATUS.md`.
