---
name: kotlin-rules
description: Regras e padrões de desenvolvimento para a aplicação Mobile Android (Kotlin + Jetpack Compose + MVVM)
trigger:
  type: glob
  patterns:
    - "android/**/*.kt"
    - "android/**/*.xml"
---

# Diretrizes de Desenvolvimento Mobile (Android / Kotlin)

Ao desenvolver, manter ou refatorar código no diretório `/android`, você deve seguir rigorosamente as diretrizes abaixo:

## 1. Arquitetura MVVM (Model-View-ViewModel)
- O código da aplicação deve ser estruturado rigorosamente seguindo o padrão **MVVM**:
  - **Model**: Estruturas de dados, entidades e camadas de acesso a dados (Repository Pattern).
  - **ViewModel**: Herda de `androidx.lifecycle.ViewModel`, gerencia o estado da tela e concentra a lógica de apresentação e regras de negócios.
  - **View (UI)**: Telas declarativas desacopladas que apenas observam o estado emitido pela ViewModel e despacham intenções/eventos de usuário.
- Exponha estados de UI de forma reativa e imutável através de **Kotlin Coroutines** utilizando `StateFlow` ou `SharedFlow` (`asStateFlow()`).
- Mantenha o fluxo unidirecional de dados (Unidirectional Data Flow - UDF): eventos sobem da View para a ViewModel; o estado atualizado desce da ViewModel para a View.

## 2. Interface Declarativa com Jetpack Compose
- Toda a interface de usuário deve ser construída exclusivamente com **Jetpack Compose** e componentes do **Material Design 3**.
- **Não** crie novos layouts baseados no sistema legado de Views XML (`layout/*.xml`). Arquivos XML devem ser restritos à configuração do sistema (como `AndroidManifest.xml`, valores de `strings.xml`, `colors.xml` e drawables/launchers).
- Componentize as telas em funções `@Composable` pequenas, focadas, coesas e reutilizáveis.
- Utilize observação de estado otimizada no Compose com `collectAsStateWithLifecycle()` ou `collectAsState()`.
- Respeite o tema central do aplicativo (`MaterialTheme`) com suporte a Dark Mode e paletas semânticas.
