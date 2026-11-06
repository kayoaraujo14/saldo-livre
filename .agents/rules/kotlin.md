---
name: kotlin-rules
description: Regras para Android nativo (Kotlin, Jetpack Compose, MVVM)
trigger:
  type: glob
  patterns:
    - "android/**/*"
---

# Diretrizes Android (Kotlin)

- **Arquitetura:** MVVM estrita com Jetpack Compose (Material 3) e Unidirectional Data Flow: eventos sobem da View, estado desce via `StateFlow` imutável (`asStateFlow()`) nos ViewModels. Sem novos layouts XML.
- **Assincronia:** operações assíncronas e chamadas ao Firestore/Gemini isoladas em Dispatchers apropriados (`Dispatchers.IO`/`Default`) via Coroutines; nunca bloqueie a main thread.
- **Camada de dados:** isolada pela interface `FinanceRepository.kt`. Mantenha `FirestoreFinanceRepository.kt`, `MockFinanceRepository.kt` e `FakeFinanceRepository.kt` (testutil) sempre sincronizados.
- **Paridade:** lógica financeira e ferramentas Gemini devem ser espelhadas na Web (ver `global.md`).
- **UI:** ao alterar `android/app/src/main/java/com/saldolivre/app/ui/**`, aplique a skill `ui-ux-validator`.
- **Verificação obrigatória:** ao finalizar alterações em `android/`, execute `./gradlew testDebugUnitTest` e garanta sucesso total.
