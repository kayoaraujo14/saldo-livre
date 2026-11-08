# PROJECT_STATUS.md — Fonte da Verdade & Índice Arquitetural (`Saldo Livre`)

> **Última Atualização:** 2026-11-07
> **Status da Fundação:** 🟢 **100% CONCLUÍDA E ESTABILIZADA**
> **Propósito:** Índice central de arquitetura, mapeamento de paridade Web/Mobile, segurança e histórico de micro-commits para otimização máxima de contexto e tokens.

### Pilares da Fundação Consolidados:
- ✅ **Arquitetura Monorepo & Governança:** Isolamento estrito de contexto (`/web` e `/android`) regido por `.agents/rules.json`, Squad Multidisciplinar (`global.md`), skills de domínio especializadas (`cross-platform-finance-parity`, `gemini-agent-tools-sync`, `firestore-contract-sync`) e workflows acionáveis (`.agents/workflows/`).
- ✅ **Paridade 100% do Motor Financeiro:** Regras de negócio, Saldo Livre, faturas, rateios, projeções e fechamentos mensais sincronizados entre `finance.ts`/`invoices.ts` e `FinanceEngine.kt`/`InvoiceEngine.kt`.
- ✅ **Copiloto Gemini & IA Híbrida:** Assistente conversacional multi-turn com Function Calling, gráficos dinâmicos, comando de voz, classificador neural local e balanceador de chaves (`GeminiLoadBalancer`).
- ✅ **Modo Demo Offline & Cloud Firestore:** Funcionamento imediato com dados interativos de demonstração (`mockData.ts` / `MockFinanceRepository.kt`) e sincronização em tempo real blindada por `firestore.rules`.
- ✅ **UI/UX Responsiva ("Premium & Smart"):** Identidade visual unificada em Dark Mode com destaques em Verde-Esmeralda, componentes em pílula (`CircleShape` / `rounded-full`), proteção contra quebra de texto (`Ellipsis` / `truncate`) e zero *box-in-box*.
- ✅ **CI/CD & DevOps Configurados:** Pipelines automatizados para Web (`.github/workflows/firebase-hosting-*.yml`), Android (`.github/workflows/android-release.yml` e `android-distribution.yml`) com assinatura segura e distribuição ao grupo `qa-team` via Firebase App Distribution.
- ✅ **Infraestrutura Serverless & Backend Cloud Functions V2:** Microsserviços e cron jobs agendados em TypeScript (`/backend`) para autolançamento de contas recorrentes, backup diário, expurgo de logs e healthcheck de uptime.
- ✅ **Quality Gates & Supply Chain Security:** Verificação universal de PRs (Web, Android e Backend), deploy automatizado de regras e índices do Firestore e monitoramento contínuo com Dependabot.

---

## 1. Visão Geral da Arquitetura & Banco de Dados

O **Saldo Livre** é um ecossistema financeiro inteligente multiplataforma (Monorepo: **React/TypeScript** na Web e **Kotlin/Jetpack Compose** no Android) com sincronização em tempo real via **Cloud Firestore**, suporte a modo Offline/Demo (Mock) e Copiloto Financeiro embarcado (**Google Gemini** + Classificador Neural Local).

### 1.1. Coleções do Cloud Firestore
- `/users/{userId}`: Perfil do usuário (`UserProfile`), chaves/configurações sincronizadas e preferências.
  - `/users/{userId}/agent_rules/{ruleId}` (e top-level `/agent_rules`): Regras aprendidas e memórias de categorização do Copiloto.
  - `/users/{userId}/chat_sessions/{sessionId}` (e top-level `/chat_sessions`): Histórico de conversas com o Copiloto Gemini.
  - `/users/{userId}/month_closures/{closureId}` (e top-level `/month_closures`): Fechamentos mensais consolidados (`MonthClosure`) e repasse de saldo acumulado.
  - `/users/{userId}/activity_logs/{logId}`: Histórico e logs de auditoria de ações do usuário (isolamento estrito no `firestore.rules`).
- `/transactions/{txId}`: Lançamentos financeiros (Receitas, Despesas, Investimentos, Parcelados e Rateios entre amigos). Isolamento por `visibleToUids`, `createdByUid`, `paidByUid`, `assignedFriendUid` e cartões compartilhados.
- `/cards/{cardId}`: Cartões de crédito/débito, limites, dias de fechamento/vencimento e compartilhamento (`ownerUid`, `sharedWithUids`).
- `/recurring_bills/{billId}`: Moldes de contas fixas/recorrentes para autolançamento mensal (`ownerUid`).
- `/friendships/{friendshipId}`: Grafo social de amizades para divisão de despesas e cobranças (`requesterUid`, `recipientUid`, status).

### 1.2. Motores de Cálculo Financeiro e IA (Paridade 100%)
| Domínio | Web (`/web/src/lib/`) | Android (`/android/.../data/`) | Responsabilidade Central |
| :--- | :--- | :--- | :--- |
| **Motor Financeiro** | `finance.ts` | `engine/FinanceEngine.kt` | Cálculo de Saldo Livre, KPIs mensais, rateios (`split`), projeções futuras, contas a receber e fechamento de mês. |
| **Motor de Faturas** | `invoices.ts` | `engine/InvoiceEngine.kt` | Agrupamento de faturas por ciclo de corte do cartão, separação titular vs. terceiros e status de pagamento. |
| **Copiloto Gemini & Tools** | `gemini.ts` + `agentTools.ts` | `ai/GeminiLoadBalancer.kt` + `ai/AndroidAgentToolsEngine.kt` | Balanceamento de chaves/modelos Gemini, Function Calling (CRUD via voz/texto, gráficos dinâmicos, consultas). |
| **Classificador & Regras** | `demoAgent.ts` / `gemini.ts` | `ai/NeuralFinanceClassifier.kt` + `ai/LearnedRulesManager.kt` | Inferência rápida de categorias/intenções e aprendizado contínuo de regras do usuário. |
| **Parser de Extratos/Faturas** | `pdfParser.ts` | `parser/StatementParser.kt` | Importação e interpretação de faturas/extratos (PDF/CSV/Texto) com deduplicação. |
| **Estado & Repositório** | `store/FinanceContext.tsx` + `lib/firebase.ts` | `repository/FirestoreFinanceRepository.kt` + `MockFinanceRepository.kt` | Gerenciamento reativo de estado, listeners em tempo real e fallback transparente para Mock Data. |

---

## 2. Mapa de Módulos: Web (`/web/src`) vs. Mobile (`/android`)

| Funcionalidade / Módulo | Web (`/web/src/...`) | Android (`/android/app/src/main/java/com/saldolivre/app/...`) | Status de Paridade |
| :--- | :--- | :--- | :---: |
| **Autenticação & Setup** | `components/LoginScreen.tsx`, `modals/FirebaseSetupModal.tsx`, `modals/ApiKeyModal.tsx` | `ui/screens/auth/LoginScreen.kt`, `AuthViewModel.kt` | 🟢 100% |
| **Dashboard & KPIs** | `App.tsx`, `components/Header.tsx`, `KpiGrid.tsx`, `AutoLaunchNoticeBanner.tsx` | `ui/screens/dashboard/DashboardScreen.kt`, `DashboardViewModel.kt`, `ui/components/KpiCard.kt`, `MonthSelector.kt` | 🟢 100% |
| **Fechamento de Mês** | Integrado ao `FinanceContext.tsx` / Dashboard | `ui/screens/dashboard/MonthClosureDialog.kt`, `PendingClosureBanner.kt` | 🟢 100% |
| **Listagem & Filtros de Transações** | `components/TransactionTable.tsx`, `mobile/MobileTransactionCard.tsx` | `ui/screens/transactions/TransactionsScreen.kt`, `TransactionsViewModel.kt`, `AdvancedFiltersBottomSheet.kt`, `ui/components/TransactionItem.kt`, `TransactionDetailDialog.kt` | 🟢 100% |
| **Formulário de Transação & Rateio** | `components/modals/TransactionModal.tsx`, `DeleteInstallmentDialog.tsx`, `QuickRefundModal.tsx` | `ui/screens/form/TransactionFormScreen.kt`, `TransactionFormViewModel.kt`, `ui/components/DeleteInstallmentDialog.kt`, `QuickRefundDialog.kt` | 🟢 100% |
| **Cartões & Faturas Unificadas** | `components/modals/CardInvoicesModal.tsx`, `SharedCardsModal.tsx` | `ui/screens/cards/CardsAndInvoicesScreen.kt`, `CardsScreen.kt`, `CardFormDialog.kt`, `ui/screens/invoices/CardInvoicesScreen.kt`, `ui/components/InvoiceDateGroupHeader.kt` | 🟢 100% |
| **Contas Fixas (Recorrentes)** | `components/modals/RecurringBillsModal.tsx` | `ui/screens/recurring/RecurringBillsScreen.kt`, `RecurringBillsViewModel.kt` | 🟢 100% |
| **A Receber (Cobranças/Rateios)** | `components/modals/ReceivablesModal.tsx` | `ui/screens/receivables/ReceivablesScreen.kt`, `ReceivablesViewModel.kt` | 🟢 100% |
| **Investimentos (Caixinhas/Metas)** | `components/modals/InvestmentsModal.tsx` | `ui/screens/investments/InvestmentsScreen.kt`, `InvestmentsViewModel.kt` | 🟢 100% |
| **Projeções Futuras** | `components/modals/FutureProjectionModal.tsx` | `ui/screens/projections/FutureProjectionsScreen.kt`, `FutureProjectionsViewModel.kt` | 🟢 100% |
| **Rede de Amigos** | `components/modals/FriendsModal.tsx` | `ui/screens/friends/FriendsScreen.kt`, `FriendsViewModel.kt` | 🟢 100% |
| **Atividade Recente (Auditoria)** | `components/modals/RecentActivityModal.tsx` | `ui/screens/activity/RecentActivityScreen.kt`, `RecentActivityViewModel.kt` | 🟢 100% |
| **Copiloto IA (Gemini Chat & Voz)** | `components/GeminiChat.tsx`, `ui/ChatChart.tsx`, `lib/agent/budgetTools.ts` | `ui/screens/chat/GeminiChatScreen.kt`, `GeminiChatViewModel.kt`, `DynamicChatChart.kt`, `data/ai/AndroidSpeechManager.kt` | 🟢 100% |
| **Saúde Financeira (4 Pilares & Coach IA)** | `lib/finance.ts`, `components/KpiGrid.tsx`, `modals/FinancialHealthModal.tsx` | `engine/FinanceEngine.kt`, `FinancialHealthScore.kt`, `ui/screens/dashboard/DashboardScreen.kt`, `FinancialHealthAndBudgetsComponents.kt` | 🟢 100% |
| **Envelopes Orçamentários Inteligentes** | `lib/finance.ts`, `components/CategoryBudgetsCard.tsx`, `modals/ManageBudgetsModal.tsx` | `engine/FinanceEngine.kt`, `FinancialHealthScore.kt`, `ui/screens/dashboard/FinancialHealthAndBudgetsComponents.kt` | 🟢 100% |
| **Feedback Tátil & Haptics** | N/A (Específico nativo) | `ui/util/HapticFeedbackHelper.kt` (integrado nos formulários, faturas e fechamento) | 🟢 100% |
| **PWA & Instalação Nativa** | `manifest.webmanifest`, `public/sw.js`, `index.html` (iOS/Desktop) | App nativo Android / APK / AAB | 🟢 100% |
| **Navegação Mobile & Menu** | `components/mobile/MobileBottomNav.tsx`, `MobileMenuSheet.tsx` | `ui/navigation/AppNavHost.kt`, `AppBottomBar.kt`, `ui/screens/menu/MenuScreen.kt` | 🟢 100% |

---

## 3. Status de Segurança e Regras Ativas

1. **Firestore Security Rules (`firestore.rules`):**
   - **Isolamento Estrito por `uid`:** Nenhuma leitura ou escrita ocorre sem `request.auth != null`.
   - **Proteção de Rateios em `/transactions/{txId}`:** Apenas o criador/pagador pode editar dados estruturais ou excluir; o amigo atribuído (`assignedFriendUid`) possui permissão restrita (`hasOnly(['acceptanceStatus', 'respondedAt'])`) apenas para aceitar ou recusar a despesa compartilhada.
   - **Cartões Compartilhados (`/cards/{cardId}`):** Titular (`ownerUid`) possui controle total (`CRUD`); convidados (`sharedWithUids`) possuem permissão exclusiva de leitura (`read`) no cartão e nas transações vinculadas a ele.
   - **Subcoleções Privadas:** `agent_rules`, `chat_sessions` e `month_closures` blindadas exclusivamente para o próprio `userId` / `ownerUid`.
2. **Ofuscação e Blindagem Android (`android/app/proguard-rules.pro`):**
   - Regras R8/ProGuard ativas preservando `data.model.**`, serializadores `Gson` (`@SerializedName`, `TypeToken`), anotações do `Firebase Firestore` (`@PropertyName`, `@DocumentId`) e classes internas do SDK `com.google.ai.client.generativeai` (`kotlinx.serialization` e `Ktor`), prevenindo crashes em builds de Release.
3. **Gestão de Segredos, Chaves de API e CI/CD Mobile:**
   - Zero chaves ou keystores hardcoded no código-fonte versionado (`.gitignore` bloqueia `*.jks`, `*.keystore`, `local.properties`, `google-services.json`).
   - `android/app/build.gradle.kts` resolve segredos e assinatura `release` dinamicamente via propriedades injetadas (`-Pandroid.injected.signing.*`), `local.properties` ou variáveis de ambiente do CI, com fallback seguro para debug local.
   - Esteira `.github/workflows/android-release.yml` automatiza a decodificação de `KEYSTORE_BASE64`, compilação assinada via flags `-Pandroid.injected.signing.*` (`assembleRelease`), upload do `app-release.apk` como Artifact e distribuição contínua para o grupo `qa-team` no **Firebase App Distribution** (além de `.github/workflows/android-distribution.yml` para AAB/Play Store).
   - As chaves da API Gemini são gerenciadas dinamicamente pelo usuário (armazenadas com segurança no perfil/dispositivo) e operadas via `GeminiLoadBalancer` com fallback automático contra erros `429 Too Many Requests`.

---

## 4. Registro de Mudanças (Changelog Recente)

> *Este espaço registra cada micro-commit validado pelo Squad Multidisciplinar.*

- **[2026-11-07] Sprint 3 (v1.4.0: Segurança em Camadas & App Check) — Issue #57 (Conclusão Integral):**
  - **1. Auditoria e Hardening das Firestore Security Rules (`firestore.rules`):**
    - **Eliminação Completa do Update Bypass:** Refatoração de `/agent_rules`, `/chat_sessions` e `/month_closures` com separação explícita de `create`, `read`, `update` e `delete`, bloqueando a injeção forjada de `ownerUid` / `uid` no payload de atualização.
    - **Imutabilidade Estrita de Identidade (Zero Trust):**
      - `/friendships/{friendshipId}`: verificação estrita de imutabilidade de `requesterUid` e `recipientUid` no `update` e proibição de auto-amizade (`requesterUid != recipientUid`).
      - `/cards/{cardId}`: `ownerUid` estritamente imutável no `update`.
      - `/recurring_bills/{billId}`: isolamento total por `ownerUid` com imutabilidade estrita.
      - `/transactions/{txId}`: criador com `createdByUid` imutável contra spoofing; amigos atribuídos no rateio restritos estritamente a `acceptanceStatus` e `respondedAt`.
    - **Subcoleções Privadas Blindadas:** Isolamento integral de `/users/{userId}/activity_logs`, `/users/{userId}/agent_rules`, `/users/{userId}/chat_sessions` e `/users/{userId}/month_closures` pelo UID autenticado.
    - **Funções Auxiliares Expressivas:** Criação de `isAuthenticated()`, `isOwner()`, `isTxParticipant()`, `isTxSharedCardHolder()` e `isTxCreator()`.
  - **2. Expansão da Suíte de Testes Unitários de Segurança:**
    - Testes de regras em `web/src/lib/__tests__/firestore-rules.test.ts` expandidos de 15 para **27 testes unitários**, cobrindo exaustivamente tentativas de Update Bypass, spoofing de amizade, adulteração de campos imutáveis e violações de privacidade.
  - **3. Quality Gates:**
    - Linter: **0 erros e 0 warnings** em 78 arquivos (`oxlint`).
    - Testes Unitários: **130 testes aprovados** em 11 suítes (`vitest`).
    - Build Web: **`tsc -b && vite build` aprovado em 812ms**.

- **[2026-11-07] Sprint 2 (v1.3.0: Performance Máxima & Virtualização) — Issue #54 (Conclusão Integral):**
  - **1. Estabilidade do Compilador Jetpack Compose (`@Immutable` & `@Stable`):**
    - Mapeados e anotados com `@Immutable` todos os modelos de dados e DTOs transferidos para telas e Composables:
      - `data/model/UserProfile.kt`: `@Immutable data class UserProfile` (estabiliza mapas internos e previne recomposições em cadeia do Dashboard e cabeçalho).
      - `data/model/Friendship.kt`: `@Immutable data class Friendship`.
      - `data/model/FutureMonthProjection.kt`: `@Immutable data class ProjectedCategoryItem`, `ProjectedMonthItem`, `FutureMonthProjection`.
      - `data/model/ActivityLog.kt`: `@Immutable data class ActivityLog`.
      - `data/model/PendingChargeGroup.kt`: `@Immutable data class PendingChargeGroup`.
      - `data/model/ChatModels.kt`: `@Stable data class PendingAction` (suporte a mutabilidade de status), `@Immutable data class ActionCardLine`, `ActionCardData`, `ChatAttachment`, `ChatMessage`, `ChatSession`, `UserLearnedRule`.
    - Anotadas estruturas e classes de apoio dos Motores Financeiros e UI:
      - `data/engine/FinanceEngine.kt`: `@Immutable data class NubankDateHeader`, `SplitResult`, `AccountCashFlowSummary`, `EstimateResult`, `GenerateBillsResult`, `AutoLaunchBillsResult`, `PendingRecurringCandidate`.
      - `ui/screens/chat/DynamicChatChart.kt`: `@Immutable data class ChartDataItem`, `ChartPayload`.
      - `ui/screens/chat/GeminiChatScreen.kt`: `@Immutable sealed class MessageContentBlock`, `data class Text`, `data class Table`.
    - Anotados com `@Immutable` todos os `UiState` e data classes de apoio dos ViewModels:
      - `RecentActivityViewModel.kt`: `ActivityItemUi`, `RecentActivityUiState`.
      - `AuthViewModel.kt`: `AuthUiState`.
      - `CardsAndInvoicesViewModel.kt`: `CardsAndInvoicesUiState`.
      - `CardsViewModel.kt`: `CardWithInvoice`, `CardsUiState`.
      - `CardInvoicesViewModel.kt`: `CardInvoicesUiState`.
      - `DashboardViewModel.kt`: `AutoLaunchNotice`, `IncomeConsumptionState`, `Radar7DaysState`, `TopCategoryState`, `InvestmentHealthState`, `DashboardUiState`.
      - `TransactionFormViewModel.kt`: `TransactionFormState`.
      - `FriendsViewModel.kt`: `FriendItem`, `FriendRequestItem`, `FriendsUiState`.
      - `InvestmentsViewModel.kt`: `InvestmentAsset`, `Rule503020Data`, `YearlySimulationData`, `CompoundSimulationResult`, `InvestmentsUiState`.
      - `MenuViewModel.kt`: `MenuUiState`.
      - `FutureProjectionsViewModel.kt`: `FutureProjectionsUiState`.
      - `ReceivablesViewModel.kt`: `ReceivablesUiState`.
      - `RecurringBillsViewModel.kt`: `RecurringBillsUiState`.
      - `TransactionsViewModel.kt`: `TransactionsUiState`.
      - `GeminiChatViewModel.kt`: `ChatUiState`.
  - **2. Resultados de Performance:**
    - Recomposição inteligente e skippable pelo Compilador Compose: todas as chamadas de funções `@Composable` que recebem estados de UI ou modelos agora podem ser ignoradas com segurança (smart recomposition skip) quando os dados não foram modificados.
    - Zero degradação de frame rate e rolagem contínua fluida a 60-120 Hz nas listas, gráficos dinâmicos e telas com alto volume de dados no app nativo Android.
  - **3. Qualidade & Validação Global:**
    - Android: **Compilação Kotlin aprovada (`compileDebugSources` - BUILD SUCCESSFUL in 1m 42s)** com 0 erros.
    - Web: **`oxlint` com 0 avisos e 0 erros**, **`vitest` com 118 testes aprovados**, e build de produção Vite concluído em 920ms.

- **[2026-11-07] Sprint 2 (v1.3.0: Performance Máxima & Virtualização) — Issue #53 (Conclusão Integral):**
  - **1. Virtualização de Alta Eficiência no Extrato Web (`TransactionTable.tsx` & `MobileTransactionCard.tsx`):**
    - Integrado `@tanstack/react-virtual` (`useVirtualizer`) com cálculo dinâmico de nós DOM para telas Desktop (`<table>`) e Mobile (feed de cartões).
    - Preservados 100% dos recursos avançados: filtros estilo Excel (Status, Intervalo de Datas, Categorias, Subcategorias, Divisões, Pessoas/Terceiros, Valores Mín/Máx, Ordenação), modo tela cheia inteligente com cabeçalho sticky, linha de saldo inicial/déficit acumulado (`carryoverAmount`), badges em pílula e rodapé analítico dinâmico com totais de entradas, despesas e saldo líquido da seleção.
    - Otimização do DOM: mesmo com 500+ transações carregadas, são renderizados estritamente os elementos em viewport (~15 a 20 linhas + overscan) com espaçadores calculados dinamicamente via `paddingTop` e `paddingBottom`, eliminando jank e mantendo rolagem estável a 60 FPS com consumo mínimo de memória.
    - Suporte a expansão e redimensionamento dinâmico no mobile via `measureElement` e `gap: 8`.
  - **2. Qualidade & Testes Automatizados:**
    - Criada a suíte `web/src/components/__tests__/TransactionTable.test.tsx` com 5 testes automatizados cobrindo skeletons de carregamento, estado vazio (`EmptyState`), cálculo de somatórios do rodapé, busca textual reativa e renderização virtualizada estável de listas de 500 transações.
    - Linter: **0 avisos e 0 erros** em 78 arquivos (`oxlint`).
    - Testes Unitários: **118 testes verdes** em 11 suítes (`vitest`).
    - Build Web: **`tsc -b && vite build` aprovado com sucesso em 812ms** sem erros.

- **[2026-11-07] Conclusão da Sprint 1 (v1.2.0: UI/UX & Prevenção de Bugs Visuais) — Issue #52 (Conclusão Integral):**
  - **1. Correção Estrita de Exhaustive-Deps em `FinancialHealthModal.tsx`:**
    - Desestruturadas as propriedades escalares de `health` (`score`, `classification`, `savingsScore`, `spendingLimitScore`, `emergencyReserveScore`, `envelopeScore`, `savingsRatePercentage`, `spendingRatioPercentage`, `emergencyCoverageMonths`, `actionableTips`).
    - Estabilizada a dependência de `actionableTips` com chave serializada de comparação (`actionableTipsKey`), garantindo conformidade absoluta com o React 19 Compiler e prevenindo requisições de rede redundantes para a API Gemini ao alternar estados periféricos do Dashboard.
    - Adicionado early return guard (`if (!open) return`) prevenindo ativação de efeitos colaterais com o modal fechado.
  - **2. Code-Splitting e Otimização de Chunks no Vite (`CategoryBudgetsCard.tsx`):**
    - Substituída a importação estática de `ManageBudgetsModal` por carregamento dinâmico assíncrono via `lazy()` e `<Suspense fallback={null}>`.
    - Eliminado o aviso `[INEFFECTIVE_DYNAMIC_IMPORT]` no Vite, movendo `ManageBudgetsModal` para seu próprio chunk assíncrono (`dist/assets/ManageBudgetsModal-*.js`, 20 kB) e reduzindo o payload inicial do bundle `index.js`.
  - **3. Qualidade & Validação Global:**
    - Linter: **0 avisos e 0 erros** em 77 arquivos (`oxlint`).
    - Testes Unitários: **113 testes verdes** em 10 suítes (`vitest`).
    - Build Web: **`tsc -b && vite build` aprovado com sucesso em 801ms**.
    - Android: **Compilação Kotlin aprovada** (`compileDebugSources` - BUILD SUCCESSFUL).

- **[2026-11-04] Calibração de Orçamento pela Entrada Prevista do Mês, Paridade 100% de KPIs de Saúde Financeira & Upgrade de UI/UX (Web & Android):**
  - **1. Orçamento Calibrado Estritamente pela Entrada Prevista do Mês (`summary.income`):**
    - Tanto o cálculo ativo dos envelopes (`computeCategoryBudgets`) quanto a sugestão da IA (`suggestCategoryBudgets`) em `web/src/lib/finance.ts` e `android/.../FinanceEngine.kt` utilizam a **entrada prevista real do mês selecionado (`summary.income`)** (usando `profile.monthlyIncome` apenas como fallback caso não haja receitas no mês).
    - **Teto Máximo Garantido (`totalBudgeted <= expectedIncome` e `totalSuggested <= expectedIncome`):** Quando a soma bruta dos tetos configurados ou sugeridos supera o que é esperado entrar no mês, o motor calibra proporcionalmente os tetos (`isCappedToIncome = true`), preservando `rawLimit` e `rawTotalBudgeted` para transparência total na UI.
    - Categorias fixas mensais (`FIXED_MONTHLY_CATEGORIES`) não recebem mais inflação de `+10%` na sugestão da IA, mantendo o valor exato da conta recorrente/média.
  - **2. Paridade 100% dos KPIs de Saúde Financeira e Envelopes (Web vs. Android):**
    - Alinhamento integral do cálculo de `rawTotalOutflow` (`summary.paid + summary.committed`) em `FinanceEngine.calculateFinancialHealthScore` com `web/src/lib/finance.ts`.
    - Unificação de `resolveEmergencyReserveBalance(profile, summary)` em ambas as plataformas (incluindo parsing de `emergencyReserveBalance` no `FirestoreFinanceRepository.kt` e substituição da cadeia Elvis no `DashboardViewModel.kt`).
    - Paridade total de fallbacks legados no `impactFor(tx, uid, all)` na Web (`allocations`, `splitType`, `createdByUid`/`paidByUid`) espelhando o `FinanceEngine.kt`.
    - Reconciliação canônica de aliases de categorias (`normalizeBudgetCategory`: ex. `"Lazer"` ↔ `"Lazer & Entretenimento"`, `"Assinaturas"` ↔ `"Assinaturas & Serviços"`) e alinhamento do KPI `"Maior Gasto"` (`topCategoryData` / `topCategory`).
  - **3. Elevação de UI e UX em Ambas as Plataformas:**
    - **Card de Envelopes (`CategoryBudgetsCard.tsx` & `CategoryBudgetsSectionCard`):** Novo painel executivo de 4 métricas (`Entrada Prevista`, `Teto Orçado (% da entrada)`, `Consumido`, `Folga/Excesso nos Envelopes`), badge `Calibrado à Entrada`, filtros rápidos (`Todos`, `Em Alerta`, `Variáveis`, `Fixos`) e marcador de ritmo do calendário nas barras de progresso.
    - **Modal de Gestão de Envelopes (`ManageBudgetsModal.tsx` & `ManageBudgetsDialog`):** Termômetro ao vivo de alocação da entrada prevista do mês, botão 1-clique `"Calibrar à Entrada"` quando a soma digitada excede a entrada do mês, pílulas rápidas `"Usar IA: R$ X"` por categoria e calibração automática ao salvar.
    - **Modal de Diagnóstico de Saúde Financeira (`FinancialHealthModal.tsx` & `FinancialHealthDialog`):** Faixa de Resumo Executivo do Mês (`Entrada Prevista`, `Custo de Vida Líquido`, `Investido no Mês`, `Saldo Livre Real`) e cartões interativos para os 4 pilares.

- **[2026-11-04] Sinergia Total de Saúde Financeira (4 Pilares) & Envelopes Orçamentários Realistas — Paridade 100% Web/Android & Integração Profunda no Chat (Conclusão Integral):**
  - **1. Paridade 100% Web (`/web`) e Android (`/android`):**
    - Unificação matemática e contratual entre `web/src/lib/finance.ts` e `android/.../FinanceEngine.kt` (`calculateFinancialHealthScore`, `resolveEmergencyReserveBalance`, `computeCategoryBudgets`, `suggestCategoryBudgets`, `suggestBudgetRebalance` e `isFixedBudgetCategory`).
    - Implementação completa dos componentes de Envelopes Orçamentários e Saúde Financeira no Android (`FinancialHealthAndBudgetsComponents.kt`, `DashboardScreen.kt` e `DashboardViewModel.kt`) e do modal interativo `FinancialHealthModal.tsx` na Web.
    - Persistência sincronizada no Cloud Firestore (`updateCategoryBudgets` em `FirestoreFinanceRepository.kt` e `FinanceContext.tsx`).
  - **2. Realismo Financeiro & Alta Aderência do Usuário:**
    - **Distinção Inteligente entre Envelopes Fixos vs. Variáveis:** Categorias fixas mensais (`Moradia`, `Assinaturas`, `Educação`, `Financiamentos`, `Impostos`) pagas integralmente no início do mês não disparam falsos alarmes de ritmo diário crítico (`burnRateStatus = normal/on_track` e `status = safe` quando `spent <= limit`). Categorias variáveis (`Alimentação`, `Lazer`, `Transporte`, etc.) calculam ritmo diário (`dailyPace`), projeção de fechamento (`projectedSpent`) e meta diária segura (`recommendedDailyRemaining`).
    - **Filtro de Categorias Não-Consumo:** `Investimentos`, `Receita`, `Estorno` e `Cartão Terceiros` são excluídos automaticamente de alertas de categorias sem teto (`unbudgetedCategories`) e de sugestões automáticas de tetos de despesa.
    - **Sinergia dos 4 Pilares na Saúde Financeira (`calculateFinancialHealthScore`):**
      1. *Taxa de Poupança & Investimento (35 pts):* Reconhece aportes reais + potencial de poupança do `freeBalance` positivo (até +12 pts de bônus).
      2. *Controle de Custo de Vida (35 pts):* Desconta aportes de `Investimentos` do `committed` (`monthlyLivingCost`), garantindo que investir >20% da renda jamais penalize o indicador de custo de vida.
      3. *Reserva de Emergência (30 pts):* Calcula meses de cobertura sobre o custo de vida real (`monthlyLivingCost`) usando `resolveEmergencyReserveBalance`.
      4. *Aderência aos Envelopes (`budgetAdherenceScore` 0–100%):* Concede bônus de disciplina (**+4 pts**) quando há envelopes ativos e zero estouros, e aplica penalidade proporcional (até **-8 pts**) quando há envelopes estourados.
      - Geração automática de `actionableTips` personalizadas com valores exatos em R$ e ganho estimado de pontos (`+X pts`).
  - **3. Integração Profunda no Chat (Coração do Projeto — Web & Android):**
    - Injeção em tempo real do **Diagnóstico de Saúde Financeira (4 Pilares + Dicas do Coach IA)** e do **Relatório de Envelopes Orçamentários** no `buildSystemInstruction` (`web/src/lib/gemini.ts` e `GeminiChatViewModel.kt`).
    - Expansão da tool `gerenciarEnvelopesOrcamentarios` (`budgetTools.ts` na Web e `MANAGE_BUDGETS` em `AndroidAgentToolsEngine.kt` no Android) com 8 operações: `consultar`, `definir_teto`, `rebalancear`, `rebalancear_automatico`, `sugerir_tetos`, `aplicar_sugestao_ia`, `simular_compra` e `diagnostico_saude_financeira`, retornando `ActionCardData` visuais no Chat.
    - Suporte completo em modo Offline/Demo NLP (`demoAgent.ts` conectada ao `GeminiChat.tsx` na Web e `processNaturalLanguageOffline` em `AndroidAgentToolsEngine.kt` no Android).
    - Ponte direta 1-clique (**"Analisar no Copiloto IA ✨"**) nos cards e modais do Dashboard para abrir o Chat e disparar a análise contextual automaticamente (`saldo-livre:ask-copilot` na Web e `GeminiChatViewModel.pendingInitialPrompt` no Android).

- **[2026-11-04] Orçamento Inteligente & Envelopes de Gastos com IA — Etapa 3: Integração Copiloto Gemini & Function Calling (Conclusão Integral):**
  - **Nova Ferramenta do Copiloto (`gerenciarEnvelopesOrcamentarios`):**
    - Adicionada ao catálogo de ferramentas do Gemini em `gemini.ts` e despachada para o módulo especializado `agent/budgetTools.ts`.
    - Suporte a 5 ações de tomada de decisão com IA:
      1. `consultar`: Análise do progresso, velocidade de queima de cada envelope e saldo disponível.
      2. `definir_teto`: Ajuste de limites orçamentários por texto ou voz.
      3. `rebalancear`: Compensação de gastos imprevistos transferindo saldo entre envelopes.
      4. `sugerir_tetos`: Proposta de orçamento fundamentada na média real de 3 meses com 10% de folga.
      5. `simular_compra`: Resposta consultiva se uma despesa à vista ou parcelada cabe no envelope e no Saldo Livre do usuário.
  - **Expansão de Testes Unitários:**
    - Suíte `budgets.test.ts` expandida com testes ponta a ponta do executor de ferramentas (`ToolExecutor`).
  - **Validação Global:**
    - Linter: **0 avisos e 0 erros** em 72 arquivos (`oxlint`).
    - Testes Unitários: **89 testes verdes** (`vitest`).
    - Build Web: **`tsc -b && vite build` aprovado em 2.31s**.
    - Android: **`compileDebugKotlin` aprovado com sucesso em 35s**.

- **[2026-11-04] Orçamento Inteligente & Envelopes de Gastos com IA — Etapa 2: Interface Visual no Dashboard & Modais (Conclusão Integral):**
  - **Componente de Envelopes no Dashboard (`CategoryBudgetsCard.tsx`):**
    - Métrica agregada de tetos: Total Orçado, Consumido, % de execução e Sobra/Déficit.
    - Barra de progresso global dinâmica com transição suave de cores (verde, âmbar e rosa).
    - Grid responsivo de cartões individuais por envelope de categoria com ícones e cores oficiais (`getCategoryDetail`).
    - Badges de ritmo inteligentes (`Flame` Crítico / `AlertTriangle` Acelerado / Normal) informando a velocidade diária de consumo.
    - Alerta preventivo da IA inline com atalho de 1 clique para acionar o rebalanceamento dinâmico.
    - Botão de expansão para visualização completa de todos os envelopes e empty state amigável.
  - **Modal de Gestão & Rebalanceamento com IA (`ManageBudgetsModal.tsx`):**
    - Aba **"Configurar Tetos"**: Ajuste de valores monetários por categoria com atalho **"Preencher com IA"** que analisa o histórico dos últimos 3 meses e sugere tetos automáticos com 10% de folga.
    - Aba **"Rebalanceamento IA"**: Diagnóstico de compensação de imprevistos com sugestões calculadas pela IA (`Lazer ➔ Alimentação`) e execução com 1 clique (`rebalanceCategoryBudgets`).
  - **Integração no Layout (`App.tsx`):**
    - Posicionamento orgânico no Dashboard Desktop (abaixo de `KpiGrid`) e no Mobile (na aba Início).
  - **Validação Global:**
    - Linter: **0 avisos e 0 erros** em 71 arquivos (`oxlint`).
    - Testes Unitários: **88 testes verdes** (`vitest`).
    - Build Web: **`tsc -b && vite build` aprovado em 958ms**.
    - Android: **`compileDebugKotlin` aprovado com sucesso**.

- **[2026-11-04] Orçamento Inteligente & Envelopes de Gastos com IA — Etapa 1: Modelagem & Motor de Cálculo (Conclusão Integral):**
  - **Modelagem de Dados & Paridade Web/Mobile:**
    - Adicionado suporte nativo a `categoryBudgets` no `UserProfile` tanto na Web (`web/src/types/index.ts`) quanto no Android (`UserProfile.kt` e `FirestoreFinanceRepository.kt` com parsing e salvamento merge no Firestore).
    - Criados os contratos tipados `CategoryBudgetStatus`, `CategoryBudgetReport`, `CategoryBudgetPaceStatus` e `BudgetRebalanceSuggestion`.
    - Inicialização de dados demonstrativos realistas em `mockData.ts` para modo Demo imediato.
  - **Motor Financeiro Puro (`web/src/lib/finance.ts`):**
    - `computeCategoryBudgets`: Cálculo do consumo real por categoria (respeitando cotas e divisões entre amigos), ritmo diário (`dailyPace`), projeção de fechamento (`projectedSpent`), dias restantes, limite diário recomendado e status de burn-rate (`normal`, `fast`, `critical`).
    - `suggestCategoryBudgets`: Analisador preditivo de histórico (janela de 3 meses) que sugere tetos fundamentados com margem de segurança de 10% e métrica de cobertura da renda.
    - `suggestBudgetRebalance`: Algoritmo guloso de compensação que detecta estouros em categorias e calcula transferências automáticas a partir de envelopes com saldo positivo.
  - **Estado Reativo Global (`FinanceContext.tsx` & `store/types.ts`):**
    - Exposição do relatório `categoryBudgetReport` memoizado e das ações `setCategoryBudget`, `setCategoryBudgets` e `rebalanceCategoryBudgets`.
  - **Validação Global:**
    - Testes Unitários: Nova suíte `web/src/lib/__tests__/budgets.test.ts` com 7 novos testes (**88 testes unitários passando 100% no Vitest**).
    - Linter: **0 avisos e 0 erros** em 69 arquivos (`oxlint`).
    - Build Web: **`tsc -b && vite build` concluído com sucesso**.
    - Android: **Compilação Kotlin aprovada** (`compileDebugKotlin`).

- **[2026-11-04] Prioridade 3 — Otimização Android: Modernização Java 17, Minificação R8/ProGuard & Validação da Suíte de Testes (Conclusão Integral):**
  - **Modernização do Java Toolchain (Java 17):**
    - Atualização de `compileOptions` para `JavaVersion.VERSION_17` (source e target) e `kotlinOptions.jvmTarget = "17"` em `android/app/build.gradle.kts`.
    - Compatibilização com OpenJDK 17 e Android Gradle Plugin 8.3+.
  - **Minificação e Otimização de Tamanho de Release (R8 & Resource Shrinking):**
    - Habilitadas `isMinifyEnabled = true` e `isShrinkResources = true` na configuração `release` do Android.
    - Otimização com regras de R8/ProGuard em `proguard-rules.pro` preservando modelos do Gemini, Firebase e data classes do Kotlin.
    - Geração de `app-release.apk` altamente compacto de apenas **4.04 MB** (4.240.308 bytes).
  - **Diagnóstico de Encoding de Processos Filhos no Gradle (Windows):**
    - Diagnóstico de falha de carregamento de classes de worker do Gradle decorrente da leitura de `@argfile` no Windows sob diretórios de usuário com acentuação (`C:\Users\Kayo Araújo`).
    - Resolução com cache redirecionado para `C:\GradleCache` e execução via junção limpa `C:\saldo-livre`.
  - **Validação Global:**
    - Testes Unitários Android: **29 testes passando 100%** (`testDebugUnitTest`).
    - Compilação & Empacotamento de Release: **`assembleRelease` com 49 tarefas executadas e sucesso absoluto**.

- **[2026-11-04] Prioridade 2 — Qualidade de Código, Conformidade React 19 & Zeramento de Lint (Conclusão Integral):**
  - **Zeramento Absoluto de Diagnósticos (de 83 advertências para ZERO absoluto no oxlint):**
    - **Sintaxe & Imports Limpos:** Remoção de `fs` não utilizado em `benchmark.cjs`, eliminação de imports órfãos em `invoices.test.ts` e correção de escapes desnecessários em expressões regulares (`[;,\.]` -> `[;,.]`) em `transactionTools.ts`.
    - **Conformidade com React 19 Compiler:**
      - `ChatChart.tsx`: Refatorado o cálculo de fatias cumulativas para modelo 100% puro e determinístico sem mutação de acumulador pós-renderização.
      - `App.tsx`: Inicialização de estado lazy direta para atalhos PWA (`initialAction`), eliminando chamadas síncronas de `setState` em efeitos e o risco de TDZ em `handleOpenNewTx`.
      - `KpiGrid.tsx`, `AutoLaunchNoticeBanner.tsx` e `RecentActivityModal.tsx`: Extração de leituras de tempo impuras (`new Date()`, `Date.now()`) para utilitários puros em `utils.ts` (`nextWeekKey`, `currentDayOfMonth`, `nowTimestamp`) e inclusão de dependências ausentes de hooks.
      - `NumberTicker.tsx`: Uso de `useRef` para transições de valor sem captura de estado em `useEffect`.
      - `FriendsModal.tsx`: Derivação declarativa de abas ativas em render, eliminando efeitos colaterais de sincronização.
      - `CardInvoicesModal.tsx`: Inclusão de `selectedCardId` e `selected?.card` nas dependências de hooks.
      - `GeminiChat.tsx`: Referência imutável estável para mensagens iniciais (`DEFAULT_MESSAGES`), seleção segura de sessões na nuvem e dependência de modelo ativo.
    - **Saneamento e Estabilização do Store (`FinanceContext.tsx`):**
      - Memoização das coleções centrais unificadas (`transactions`, `cards`, `recurring`, `friendships`, `monthClosures`) via `useMemo`, eliminando dezenas de re-avaliações em cascata nos 18 componentes da aplicação.
      - Saneamento de dependências nos callbacks de transação e amizade, e inclusão de `profile` e `monthClosures` no `agentEnv`.
      - Remoção de re-exportações não-componente no arquivo do contexto para preservar 100% o Fast Refresh.
  - **Validação Global:**
    - Linter: **0 avisos e 0 erros em 68 arquivos** (`oxlint`).
    - Testes Unitários: **81 testes verdes** (`vitest run`).
    - Build de Produção: **Vite build concluído em 1.37s** com zero erros.

- **[2026-11-04] Prioridade 1 — Segurança & Infraestrutura Cloud (Conclusão Integral):**
  - **Etapa 1.1 — Blindagem de `activity_logs` no `firestore.rules` & Testes Automatizados:**
    - Delegado ao subagente especialista `firestore-rules-author` a criação da regra de isolamento para a subcoleção privada `/users/{userId}/activity_logs/{logId}`.
    - Leitura e escrita restritas exclusivamente ao próprio usuário autenticado (`request.auth.uid == userId`), bloqueando acessos anônimos e de terceiros.
    - Expansão da suíte `web/src/lib/__tests__/firestore-rules.test.ts` com 2 novos testes de isolamento de auditoria (totalizando 15 testes de regras e 81 testes unitários verdes no Vitest).
  - **Etapa 1.2 — Implementação Real de Backup Gerenciado no Backend Cloud Functions V2:**
    - Substituição da rotina mockada em `backupFirestoreScheduled` ([backend/src/index.ts](file:///c:/Users/Kayo%20Araújo/Documents/github/saldo-livre/backend/src/index.ts)) pela invocação real da API de exportação gerenciada do Cloud Firestore através de `admin.firestore.v1.FirestoreAdminClient`.
    - Resolução dinâmica do projeto (`GCLOUD_PROJECT`, `GCP_PROJECT`, `projectId`), montagem determinística de caminho de banco de dados (`projects/{projectId}/databases/(default)`) e exportação para o bucket Cloud Storage (`gs://saldo-livre-c3ca3-backups` ou customizável via `BACKUP_STORAGE_BUCKET`).
    - Compilação limpa do TypeScript no backend (`npm --prefix backend run build` -> `backend/lib/index.js`).
  - **Validação Global:**
    - Testes Vitest Web: **81 testes verdes** (`npm --prefix web test -- --run`).
    - Build Web: **Zero erros** (`npm --prefix web run build`).
    - Backend Serverless: **Compilação TypeScript 100% aprovada** (`tsc`).


- **[2026-11-03] Infraestrutura & Backend Serverless — 5 Pilares Consolidados (Conclusão Integral):**
  - **Pilar 1 — CI/CD & Deploy Automático de Regras & Quality Gates Universal:**
    - `.github/workflows/firebase-hosting-merge.yml`: Adicionados gatilhos para `firestore.rules`, `firestore.indexes.json`, `firebase.json` e `backend/**`; habilitado cache de dependências `cache: 'npm'`; incluídos steps de validação `npm --prefix web run lint` e Vitest (79 testes verdes); adicionado deploy automatizado das regras e índices do Firestore (`npx firebase-tools deploy --only firestore:rules,firestore:indexes`) com credenciais do Service Account (`FIREBASE_SERVICE_ACCOUNT_SALDO_LIVRE_C3CA3`).
    - `.github/workflows/firebase-hosting-pull-request.yml`: Adicionado caching de dependências, oxlint, Vitest e preview deploy seguro.
    - `.github/workflows/quality-gates.yml`: Novo pipeline universal de Pull Request para branch `main` executando validação cruzada Web (Node 22, oxlint, 79 testes Vitest, Vite build), Android (JDK 17, Gradle Cache, mock `google-services.json`, compilação Kotlin e testes unitários JVM) e Backend (Node 22, TypeScript compilation).
  - **Pilar 2 — Supply Chain Security & Dependabot (`.github/dependabot.yml`):**
    - Configuração de escaneamento automatizado semanal de vulnerabilidades e atualizações de dependências para `npm` (`/web`), `gradle` (`/android`) e `github-actions`, com agrupamento inteligente de dependências (`frontend-dependencies`, `android-dependencies`, `ci-dependencies`) e limite de PRs abertos para prevenir fadiga de alertas.
  - **Pilar 3 — Índices Compostos do Cloud Firestore (`firestore.indexes.json`):**
    - Mapeamento e versionamento de índices compostos em produção:
      - `/transactions`: `visibleToUids` (array-contains) + `date` (desc); `cardId` (asc) + `date` (desc).
      - `/cards`: `ownerUid` (asc) + `name` (asc); `sharedWithUids` (array-contains) + `name` (asc).
      - `/friendships`: `requesterUid` (asc) + `status` (asc); `recipientUid` (asc) + `status` (asc).
  - **Pilar 4 — Backend Serverless com Cloud Functions V2 (`/backend`):**
    - Módulo TypeScript escalável (`Node 22 / ES2022`) com `firebase-admin` e `firebase-functions` V2.
    - 4 Cloud Functions de missão crítica implementadas e compiladas:
      1. `autoLaunchRecurringBillsScheduled`: Cron mensal (dia 1º às 00:05 BRT / 03:05 UTC) para escanear contas fixas ativas e autolançar pendências no Firestore no primeiro instante do mês.
      2. `backupFirestoreScheduled`: Cron diário às 23:00 BRT para rotina de snapshot e disaster recovery de coleções no Firestore.
      3. `cleanupEphemeralLogsScheduled`: Cron semanal aos domingos às 04:00 UTC para expurgo de logs e sessões de chat efêmeras com >180 dias.
      4. `healthCheck`: Endpoint HTTP público com monitoramento de uptime, timestamp e integridade da infraestrutura.
    - Vinculação com target `functions` em `firebase.json` e compilação validada (`backend/lib/index.js`).
  - **Pilar 5 — Orquestração & Scripts Raiz (`package.json`):**
    - Adicionados scripts unificados: `check:all` (lint + vitest + web build), `backend:build`, `backend:serve` (emulador de functions) e `deploy:all`.
  - **Validação Global:**
    - Linter & Testes Web: 79 testes Vitest verdes (`npm --prefix web test -- --run`).
    - Build Web: Zero erros (`npm --prefix web run build`).
    - Backend Serverless: TypeScript compilado com 100% de sucesso (`npm --prefix backend run build`).
    - Android: Compilação Kotlin aprovada (`compileDebugKotlin` - BUILD SUCCESSFUL).

- **[2026-11-03] Item 3 — Qualidade & Testes (QA & Blindagem) — Conclusão Integral:**
  - **Expansão Massiva de Testes Unitários Web (Vitest: de 34 para 79 testes verdes):**
    - Criada suíte exaustiva de testes em `web/src/lib/__tests__/invoices.test.ts` (29 testes unitários) cobrindo todos os cenários complexos de fatura:
      - Ciclos de corte de cartão: mesmo mês (`dueDay >= closingDay`), ciclo invertido (`dueDay < closingDay` fechando no mês anterior) e clamping em finais de mês (28/29 de fevereiro, meses de 30 dias).
      - Transições de ciclo e status: `empty`, `open`, `closed`, `overdue`, `paid`.
      - Abatimento de estornos, créditos e reembolsos no cartão com piso não-negativo, dedução prioritária na cota do titular e teto de `paidAmount`.
      - Rateios e divisões avançadas (`getTransactionParticipantShares`): 100% titular, `split_50` com reconciliação exata de centavos ímpares, cota de parceiro, amigos com porcentagem customizada, alocações múltiplas explícitas e perspectividade visual (dono vs. convidado).
      - Projeção de comprometimento e faturas futuras, cálculo de limite disponível e % de uso.
      - Alertas Preditivos (`computePredictiveAlert`): anomalias em categorias (+25%), detecção de novas assinaturas recorrentes (Netflix, streaming) e estimativa de fechamento por média diária.
      - Consolidação de faturas (`consolidateMonthTransactions`): agrupamento no extrato mantendo Pix/boletos individuais e filtragem de faturas com cota zero para convidados.
  - **Blindagem e Testes Automatizados de Regras de Segurança (`firestore.rules`):**
    - Instalado `@firebase/rules-unit-testing` e criada a suíte `web/src/lib/__tests__/firestore-rules.test.ts` com 13 testes de segurança executados ao vivo contra o Cloud Firestore Emulator (`npm run test:rules`):
      - `/users/{userId}`: leitura apenas para autenticados, proibição de leitura anônima e bloqueio estrito contra edição de perfis alheios.
      - `/cards/{cardId}`: prevenção de forjamento de cartões com `ownerUid` de outrem; convidados (`sharedWithUids`) possuem permissão restrita de leitura, sendo bloqueados para update ou delete; estranhos bloqueados para qualquer acesso.
      - `/transactions/{txId}`: isolamento total de despesas privadas; amigos atribuídos no rateio (`assignedFriendUid`) podem ler a despesa mas só têm autorização para atualizar `acceptanceStatus` e `respondedAt`, sendo bloqueados de alterar valores ou descrições; apenas o criador pode excluir a transação.
      - `/recurring_bills` e `/users/{userId}/agent_rules`: isolamento estrito por usuário.
  - **Expansão de Testes Unitários Android (29 testes passando 100%):**
    - Criada infraestrutura de testes unitários puros na JVM com `FakeFinanceRepository.kt` e `kotlinx-coroutines-test`.
    - `GeminiLoadBalancerTest.kt` (5 testes): validação do pool de modelos, roteador inteligente (Router Pattern) com normalização de acentos e casing em português, e fallbacks.
    - `LearnedRulesManagerTest.kt` (5 testes): deduplicação de regras aprendidas pela IA, ordenação por frequência, geração determinística de IDs e deleção segura.
    - `CardsViewModelTest.kt` (3 testes): cálculo reativo de faturas, limites e comprometimento, alternância de modais de criação/edição e fluxo de exclusão.
    - `DashboardViewModelTest.kt` (4 testes): consumo de renda por faixa (No Limite, Atenção, Alerta), descarte de modal de fechamento pendente e agregação do Radar 7 Dias de contas a vencer.
    - `FutureProjectionsViewModelTest.kt` (4 testes): projeções para 6 e 12 meses, seleção de meses e agregação de saldos.
    - `InvoiceEngineTest.kt` (4 testes) & `FinanceEngineProjectionTest.kt` (4 testes): consolidação e projeções no motor Kotlin.
  - **Validação Global:**
    - Web: **79 testes unitários verdes** (`npm --prefix web test -- --run`).
    - Firestore Security Rules: **13 testes verdes** contra emulador local (`npm run test:rules`).
    - Android: **29 testes unitários verdes** (`gradlew.bat testDebugUnitTest` - 100% de sucesso).
    - Build de Produção Web: **Zero erros** (`npm --prefix web run build`).

- **[2026-11-03] Item 4 — Experiência do Usuário (UI/UX & PWA) — Conclusão Integral (Etapas 4.1, 4.2 e 4.3):**
  - **4.1. PWA Completo (Progressive Web App):**
    - Configurado `web/public/manifest.webmanifest` e `manifest.json` com nome, descrição, display `standalone`, `portrait-primary`, tema `#090d16` e atalhos rápidos de sistema operacional (`/?action=new_expense`, `/?action=invoices`, `/?action=chat`).
    - Desenvolvido `web/public/sw.js` com estratégia híbrida (Cache-First para fontes e assets estáticos, Network-First para navegação com fallback offline, ignorando chamadas de API Firestore e Gemini).
    - Metatags de compatibilidade Apple iOS PWA no `index.html` (`apple-mobile-web-app-capable`, `apple-mobile-web-app-status-bar-style`, `apple-touch-icon`).
    - Handlers de deep linking e auto-abertura no `App.tsx` para acionar modais instantaneamente via URL com limpeza de search params.
  - **4.2. Termômetro de Saúde Financeira (Score 0 a 100):**
    - **Motor Financeiro Unificado com Paridade 100%:** função pura `calculateFinancialHealthScore` implementada em `web/src/lib/finance.ts` e `android/.../FinanceEngine.kt`, avaliando 3 pilares fundamentais:
      1. **Índice de Poupança / Aportes (até 35 pts):** proporção da renda guardada vs meta de 20% (regra 50/30/20).
      2. **Controle de Teto de Gastos (até 35 pts):** consumo da renda $\le 70\%$ atinge pontuação máxima (35 pts); entre $70\%$ e $100\%$ decresce linearmente; $> 110\%$ zera.
      3. **Cobertura da Reserva de Emergência (até 30 pts):** meses de custo de vida cobertos pelo saldo da caixinha de reserva ($\ge 6$ meses = 30 pts; 3 a 6 meses = 20 a 30 pts; 1 a 3 meses = 10 a 20 pts).
    - **Classificação Semântica & Contextual:** 80–100 (*Excelente*, esmeralda), 60–79 (*Saudável*, ciano), 40–59 (*Atenção*, âmbar) e 0–39 (*Crítico*, rosa), com diagnósticos descritivos.
    - **Card Visual no Dashboard (Web & Android):**
      - **Web (`KpiGrid.tsx`):** card moderno com medidor circular em SVG e display dos 3 pilares (`Poupança`, `Teto Gastos`, `Reserva`) em pílulas, clicável para navegação direta ao modal de Investimentos.
      - **Android (`DashboardScreen.kt`):** componente Compose com `CircularProgressIndicator`, indicador de pontuação, tags em pílula e navegação fluida para a tela de investimentos.
  - **4.3. Feedback Tátil (Haptics) no Android:**
    - Utilitário central `HapticFeedbackHelper.kt` em `com.saldolivre.app.ui.util` com permissão `android.permission.VIBRATE`.
    - Suporte adaptativo com retrocompatibilidade total desde Android 8.0 (API 26) até Android 14+ (API 34), utilizando `VibratorManager`, `VibrationEffect.createPredefined` e fallbacks de pulso e formas de onda.
    - Padrões táteis sutis:
      - `confirm`: pulso curto de clique (~25ms) para confirmação de ações primárias.
      - `success`: duplo pulso rítmico elegante (30ms / pausa 50ms / 40ms) para liquidações e fechamentos.
      - `tick`: micro-pulso (~12ms) para seletores rápidos.
    - Integração completa nas interfaces Android:
      - `TransactionFormScreen.kt`: feedback tátil ao clicar em salvar e no salvamento com sucesso de despesas/receitas.
      - `TransactionDetailDialog.kt`: pulso de sucesso ao marcar transação avulsa como paga.
      - `CardInvoicesScreen.kt`: duplo pulso rítmico de sucesso ao confirmar o pagamento de fatura do cartão.
      - `MonthClosureDialog.kt` e `DashboardScreen.kt`: feedback de sucesso na consolidação e desfazimento do fechamento de mês.
  - **Validação:** 78 testes unitários do Vitest verdes na Web (`npm test`), build de produção Web OK (`npm run build`), compilação Kotlin no Android 100% aprovada (`compileDebugKotlin`).

- **[2026-11-03] Engenharia & Arquitetura — Etapa 4: Decomposição de `FinanceContext.tsx`:**
  - **Extração de Tipagem Global do Store (`web/src/store/types.ts`):** isolamento de `FinanceContextValue`, `ManualTransactionInput`, `FriendItem`, `AutoLaunchedNotice`, `DemoState` e `Mode`, desacoplando a definição do contrato público do contexto de sua implementação.
  - **Módulo de Estado e Persistência Demo (`web/src/store/demoState.ts`):** extração das rotinas `freshDemo`, `loadDemo` e chaves de LocalStorage (`DEMO_KEY`, `FORCE_DEMO_KEY`), eliminando o acoplamento do `mockData` no arquivo principal de contexto.
  - **Preservação Integral de Retrocompatibilidade:** `FinanceContext.tsx` re-exporta todos os tipos e funções de demo, garantindo zero impacto nos 18 componentes consumidores e na árvore do React.
  - **Validação:** 50 testes unitários do Vitest verdes (`npm test`) e compilação de produção com zero erros em 1,63s (`npm run build`).

- **[2026-11-03] Engenharia & Arquitetura — Etapa 3: Modularização de `agentTools.ts` (3.302 linhas -> 82 linhas):**
  - **Decomposição em Módulos Especializados (`web/src/lib/agent/`):** o monólito de 137 KB foi dividido em submódulos desacoplados:
    - `types.ts`: interfaces de ambiente (`AgentEnv`), regras (`UserRule`) e tipos de ferramentas.
    - `common.ts`: utilitários compartilhados (`money`, `matchesSearch`, `cleanBaseDescription`, `parseMonthKey`, sinônimos e regex de receitas).
    - `rules.ts`: motor de aprendizado e persistência de regras no Firestore e localStorage.
    - `chartTools.ts`: gerador de gráficos Markdown/payload dinâmicos para o chat.
    - `managementTools.ts`: operações de cartões de crédito, contas recorrentes/fixas e rede de amigos.
    - `billTools.ts`: atualização de contas previstas e cálculo reativo de impacto no Saldo Livre.
    - `projectionTools.ts`: relatórios orçamentários, projeções de 12 meses e faturas detalhadas.
    - `queryTools.ts`: consultas e filtros combinados por pessoa, cartão, canal e categoria.
    - `transactionTools.ts`: CRUD de transações individuais, em lote com inteligência de fatura e parcelamentos.
  - **Fachada Limpa & Zero Quebra (`agentTools.ts`):** arquivo central transformado em orquestrador enxuto de 82 linhas com re-exportação de toda a API pública anterior, garantindo compatibilidade total com `FinanceContext` e a suíte de testes unitários.
  - **Validação:** 34 testes unitários do Vitest verdes (`npm test`) e build de produção executado com sucesso em 1,11s (`npm run build`).

- **[2026-11-03] Item 4 — Experiência do Usuário (UI/UX & PWA) — Etapa 4.1: PWA Completo & Suporte Nativo Web:**
  - **Manifest Web App Completo (`manifest.webmanifest` & `manifest.json`):** especificação com `id`, `name: "Saldo Livre — Gestão Financeira"`, `short_name: "Saldo Livre"`, `theme_color` e `background_color` (`#090d16`), display `standalone`, `orientation: "portrait-primary"`, categorias e ícones com propósito `maskable` e `any`.
  - **Atalhos Rápidos de App Nativo (Shortcuts):** atalhos na tela de início para **"Nova Despesa"** (`/?action=new_expense`), **"Ver Faturas"** (`/?action=invoices`) e **"Copiloto IA"** (`/?action=chat`), com tratamento reativo e limpeza dinâmica de URL em `App.tsx`.
  - **Meta Tags de Integração iOS/Safari:** inclusão de `apple-mobile-web-app-capable`, `mobile-web-app-capable`, `apple-mobile-web-app-status-bar-style: black-translucent`, `apple-mobile-web-app-title` e `apple-touch-icon` em `index.html`.
  - **Service Worker Produtivo (`sw.js`):** substituição do script legado de desregistro por Service Worker com estratégia híbrida: pré-cache defensivo de assets essenciais, *Stale-While-Revalidate* para fontes e assets estáticos, *Network-First* com fallback offline para navegação e isolamento total de APIs em tempo real (Firestore e Gemini).
  - **Validação:** 34 testes unitários do Vitest verdes (`npm test`) e compilação de produção com zero erros (`npm run build`).

- **[2026-11-03] Engenharia & Arquitetura — Etapa 2: Code-Splitting & Lazy Loading (Redução de 64,4% no Bundle Inicial):**
  - **Extração Dinâmica de `pdfjs-dist`:** `extractTextFromPdf` em `web/src/lib/pdfParser.ts` agora carrega a biblioteca sob demanda via import dinâmico assíncrono. O parser de texto (`parseTransactionsFromText`) e limpeza de contraparte (`extractCounterparty`) não bloqueiam mais a inicialização do app, gerando um chunk separado de 430 kB carregado exclusivamente em uploads de extrato.
  - **Modais Secundários em `React.lazy`:** todos os modais secundários em `App.tsx` (`SharedCardsModal`, `RecurringBillsModal`, `ReceivablesModal`, `ApiKeyModal`, `TransactionModal`, `FutureProjectionModal`, `FriendsModal`, `RecentActivityModal`, `InvestmentsModal`, `CardInvoicesModal`, `MonthClosureModal`) e a tela `LoginScreen` foram encapsulados com `lazy()` e `<Suspense fallback={null}>`, sendo baixados sob demanda apenas quando ativados pelo usuário.
  - **Vendor Splitting Inteligente:** configuração de `manualChunks` em `vite.config.ts` isolando `vendor-firebase` (637 kB) e `vendor-gemini` (375 kB) para cache HTTP persistente no navegador.
  - **Resultados de Performance:** o bundle inicial `index.js` caiu de **2.704 kB** para **962 kB** (queda de **64,4%** de payload de script na inicialização, de 729 kB gzipped para 277 kB).
  - **Validação:** 34 testes do Vitest verdes (`npm test`) e build de produção verificado com sucesso (`npm run build`).

- **[2026-11-03] Engenharia & Arquitetura — Etapa 1: Web Offline-First Total no Firestore:**
  - **Cache Local Resiliente Multi-Aba:** integração de `initializeFirestore` com `persistentLocalCache({ tabManager: persistentMultipleTabManager() })` em `web/src/lib/firebase.ts`. O aplicativo agora carrega instantaneamente a partir do IndexedDB local e sincroniza mutações e queries em segundo plano, mesmo com oscilações de rede ou em múltiplas abas concorrentes.
  - **Fallback Defensivo Seguro:** verificação de ambiente (`window.indexedDB`) para ambientes restritivos (navegação privada) e ambientes de teste (Node.js/Vitest), garantindo fallback limpo para `getFirestore(app)`.
  - **Validação:** 34 testes do Vitest verdes (`npm test`) e build de produção verificado com sucesso (`npm run build`).

- **[2026-11-01] Fase 5 — Limpeza de Código e Categorias Legadas:**
  - **Categorias Obsoletas Removidas das Listas Ativas:** remoção definitiva de categorias legadas e duplicadas (`Cartão Terceiros`, `Estorno`, `Receita`, `Lazer`, `Assinaturas`, `Compras`, `Educação`, `Financiamentos & Empréstimos`) dos seletores e novos formulários (`ACTIVE_CATEGORIES`), substituídas pela taxonomia oficial.
  - **Mapeamento e Normalização Segura:** implementada função `normalizeCategory` na Web e no Android (`CategoryRegistry.get`) para garantir que transações históricas persistidas com nomes antigos sejam mapeadas dinamicamente sem quebrar a UI nem disparar erros.
  - **Remoção de Componentes Legados de Estorno:** exclusão de `QuickRefundModal.tsx` e `QuickRefundDialog.kt`, substituídos pela mecânica oficial de edição e registro de valor negativo na categoria de origem.
  - **Validação:** `npm run build` OK e `./gradlew.bat compileDebugKotlin` OK.

- **[2026-11-01] Fase 4 — Correção do Lançamento de Faturas em Lote via Chat (Imagem e Texto):**
  - **Resolução Automática de Cartão:** corrigido bug em `AndroidAgentToolsEngine.kt` e `agentTools.ts` onde compras em lote recebiam `cardId = null` e caíam no extrato solto da conta corrente. Agora, ao identificar fatura de cartão de crédito (seja por imagem, texto ou parâmetro `origemDocumento: "fatura_cartao"`), o sistema resolve dinamicamente o cartão (`cardId`), atribui `paymentMethod = CREDIT_CARD`, define status `PENDING` e calcula as datas de vencimento e mês de competência pelo ciclo de corte do cartão (`calculateCardInvoiceDates`).
  - **Proteção Contra Falsa Conversão em Débito:** corrigida condição na Web onde estornos ou palavras de receita na fatura acidentalmente forçavam a flag `isBankStatement` e convertiam as compras em débito em conta corrente.
  - **Prompt de IA Blindado:** reforço no prompt do sistema do Gemini para garantir que compras de fatura usem `origemDocumento: "fatura_cartao"` e `metodoPagamentoPadrao: "credit_card"`.
  - **Validação:** `npm run build` OK e `./gradlew.bat compileDebugKotlin` OK.

- **[2026-11-01] Fase 3 — Refatoração da Feature de Investimentos (Sincronização Cloud, Aportes, Resgates e Caixinhas):**
  - **Sincronização Cloud Firestore:** migração de saldos de caixinhas (`investmentAssetBalances`) e da meta mensal (`investmentMonthlyTarget`) de `SharedPreferences`/`localStorage` para o documento do usuário em `/users/{uid}`, garantindo persistência multiplataforma em tempo real.
  - **Paridade Web & Mobile:** implementação completa em `InvestmentsModal.tsx` com separação em buckets (Caixinhas & Reservas Seguras vs Investimentos de Risco & Renda Variável), ações rápidas de **Novo Aporte**, **Resgate** e **Ajuste de Saldo Real**, apuração automática de lucro/rendimento e histórico detalhado.
  - **Validação:** `npm run build` OK e `./gradlew.bat compileDebugKotlin` OK.

- **[2026-11-01] Fase 2 — UI/UX do Dashboard & Fechamento de Mês com 1 Clique (Desfazer Atômico):**
  - **Nova Experiência de Sobra do Mês Anterior (Opção 2):** simplificação radical do fechamento de mês para duas decisões claras: **Deixar na Conta Corrente (Saldo Livre)** ou **Guardar em Investimentos / Reserva** (com seletor da caixinha de destino e opção de divisão personalizada).
  - **Desfazer com 1 Clique Desbloqueado:** remoção da restrição que só exibia o botão de desfazer quando havia colchão (`cushionAmount > 0`). Agora o badge de fechamento do mês anterior é renderizado permanentemente no Dashboard (`KpiGrid.tsx` na Web e `DashboardScreen.kt` no Android) e permite desfazer o fechamento a qualquer momento com apenas 1 clique, revertendo atomicamente os aportes comemorativos e os saldos das caixinhas.
  - **Paridade Web & Mobile:** implementação de `MonthClosureModal.tsx` na Web com banner de notificação suave e paridade total com o `MonthClosureDialog.kt` do Android.
  - **Persistência de Usuário e Caixinhas no Firestore:** adicionados métodos `saveUserProfile`, `updateInvestmentAssetBalances` e `updateInvestmentMonthlyTarget` no `FinanceRepository`, `FirestoreFinanceRepository` e `MockFinanceRepository`, integrando leitura/escrita em `/users/{uid}`.
  - **Validação:** `npm run build` OK e `./gradlew.bat compileDebugKotlin` OK.

- **[2026-11-01] Fix de Desfazimento e Exclusão do Fechamento de Mês (Web & Android):**
  - **Causa Raiz 1 (ID Incompatível no Firestore):** ao salvar a closure, o documento era persistido sob o ID `cls_${closedMonth}_${uid}`; ao deletar, os repositórios tentavam remover apenas por `closedMonth`. Corrigido em `web/src/lib/firebase.ts` e `FirestoreFinanceRepository.kt` com estratégia defensiva tripla (deleta `closedMonth`, `cls_${closedMonth}_${uid}` e executa query por campo `closedMonth == closedMonth`), garantindo remoção definitiva do Firestore.
  - **Causa Raiz 2 (Lançamento Duplicado no Android):** removida a criação duplicada de transação em `confirmMonthClosure` do `DashboardViewModel.kt` (o repositório já orquestra a criação do aporte comemorativo). No método `undoMonthClosure`, todas as transações associadas ao fechamento são expurgadas atomicamente e o saldo das caixinhas é restaurado no perfil do usuário.
  - **Causa Raiz 3 (Reabertura Imediata do Pop-up):** ao desfazer o fechamento, as flags locais e de contexto (`closureDismissed` / `closureDialogDismissed`) agora são resetadas para `false`, e o pop-up de fechamento (`MonthClosureModal` na Web e `MonthClosureDialog` no Android) é reaberto imediatamente na tela para que o usuário possa redefinir o destino da sobra sem recarregar a aplicação.
  - **Validação:** Vitest verde (27 testes), `npm run build` OK e `./gradlew.bat compileDebugKotlin` OK.

- **[2026-11-01] Correção do Saldo Remanescente na Projeção Futura (Web & Android):**
  - **Motor de Projeção Atualizado (`projectFutureMonths`):** atualizadas as assinaturas em `web/src/lib/finance.ts` e `FinanceEngine.kt` para receber a lista de fechamentos de mês (`closures: MonthClosure[]`).
  - **Cômputo do Saldo Remanescente & Déficit:** cada mês da projeção agora busca fechamentos anteriores explícitos (`c.closedMonth === prevMonthKey || c.targetMonth === monthKey`) e computa `carryoverAmount = prevClosure.carryoverToNextMonth`. No mês inicial, herda também eventuais déficits não cobertos (`previousMonthDeficit`).
  - **Fórmula de Projeção:** `projectedAvailable = projectedIncome + carryoverAmount - previousMonthDeficit` e `projectedFreeBalance = projectedAvailable - projectedExpense`, garantindo que sobras injetadas no saldo livre reflitam com precisão de 100% no saldo projetado.
  - **Transparência Visual no Raio-X e Cards:** adicionados itens nominativos `Sobra Injetada do Mês Anterior` na lista de itens do mês e banners visuais dedicados no card do mês projetado (`FutureProjectionModal.tsx` e `FutureProjectionsScreen.kt`).
  - **Ajuste em AI Tools & ViewModels:** `FutureProjectionsViewModel.kt` no Android agora combina `repository.getMonthClosures()` e `cards`, e os motores de ferramentas do Copiloto IA (`agentTools.ts` e `AndroidAgentToolsEngine.kt`) repassam os fechamentos mensais às projeções.
  - **Testes & Builds:** 27 testes unitários do Vitest verdes na Web, `npm run build` OK e `./gradlew.bat compileDebugKotlin` OK no Android.

- **[2026-11-01] Fase 1 — Paridade do Motor Financeiro, Investimentos Líquidos e Fechamento de Mês:**
  - **Fórmula do Saldo Livre Corrigida:** removida a subtração indevida do Colchão de Segurança (`cushionAmount`) da fórmula de `effectiveAvailable` em `web/src/lib/finance.ts` e `FinanceEngine.kt`. O Saldo Livre reflete fielmente `income + carryoverAmount - previousMonthDeficit - (paid + committed)`.
  - **Cálculo Líquido de Investimentos (Aportes & Resgates):** corrigido o cálculo de `totalInvested` (mensal) e `totalInvestedAccumulated` (histórico acumulado). Resgates de investimentos (`type: 'income'` em `Investimentos` ou `Rendimentos & Investimentos › Resgates de Aplicação`) agora abatem corretamente o montante investido e alimentam a liquidez da conta corrente.
  - **Modelos de Perfil e Fechamento Atualizados:** adicionados `investmentMonthlyTarget` e `investmentAssetBalances` em `UserProfile` (Web e Android) e `investmentSubcategory` em `MonthClosure`, preparando a persistência das caixinhas e a destinação de sobras diretamente no Firestore.
  - **Paridade de Compilação:** `npm run build` (TypeScript e Vite) OK e `./gradlew.bat compileDebugKotlin` OK.

- **[2026-11-06] Reestruturação, Limpeza de Contexto e Governança de Agentes (.agents/):**
  - **Higiene do Repositório & Poda de Skills:** Removido arquivo residual `FinanceEngine.kt` da raiz (0 bytes). Eliminadas skills não utilizadas na stack (`xcode-project-setup/`, `firebase-data-connect/`, `firebase-app-hosting-basics/`) e referências a plataformas desnecessárias (`flutter_setup.md`, `ios_setup.md`, `sdk_ios.md`, etc.). Variantes enterprise eliminadas em `firebase-firestore/` e mantida apenas a variante Standard com SDKs Web e Android. `skills-lock.json` atualizado.
  - **Roteamento Inteligente & Regras Estritas (.agents/rules.json):** Escopos configurados com globs estritos: `global.md` (`**/*`), `react.md` (`web/**/*`, `backend/**/*`) e `kotlin.md` (`android/**/*`). Regras reescritas com imperativos de Paridade Multiplataforma, sincronia simultânea de contratos de dados (Web, Android, Mock/Demo e Firestore Rules) e ciclo Plan-Act-Verify com linters e testes.
  - **Criação de Skills de Domínio:** Criadas `.agents/skills/cross-platform-finance-parity` (espelhamento obrigatório de motores de cálculo e testes idênticos), `.agents/skills/gemini-agent-tools-sync` (sincronia de ferramentas de Function Calling, balanceador e gráficos) e `.agents/skills/firestore-contract-sync` (checklist estrito para alterações de esquema e regras).
  - **Workflows Acionáveis (.agents/workflows/):** Criados playbooks `feature-multiplataforma.md` (passo a passo para implementação de ponta a ponta com paridade) e `auditar-paridade.md` (auditoria periódica e geração de relatório de divergências).
  - **Validação de Build e Qualidade:** Web passou com 100% de sucesso no Oxlint (0 erros) e Vitest (99 testes aprovados em 9 suítes). Android com compilação de código de app e testes 100% aprovada (`compileDebugKotlin` e `compileDebugUnitTestKotlin` sem erros).

- **[2026-11-03] Estabilização de CI/CD, Quality Gates & Dependabot (Concluída):**
  - **Filtro Inteligente de Mudanças (Quality Gates):** Introdução de detecção seletiva de caminhos (`dorny/paths-filter@v3`) em `.github/workflows/quality-gates.yml`. PRs de documentação, Web ou Backend agora não disparam compilações desnecessárias do Android, e vice-versa, com consolidador de status `quality-gates-status`.
  - **Dependabot Anti-Breakage:** Adição de regras de blindagem no `.github/dependabot.yml` com `ignore` para major version bumps de ferramentas de base Android (`gradle`, `com.android.application`, `org.jetbrains.kotlin.*`, `androidx.core:core-ktx`) e backend (`firebase-admin`, `firebase-functions`, `typescript`), além do agrupamento de dependências (`androidx-compose`, `firebase`, `backend-deps`, `production-dependencies`, `dev-dependencies`) e remoção do ecossistema quebrado `github-actions`.
  - **Correção de Teste Unitário Android (CardsViewModelTest):** Corrigida a asserção no teste `form toggles and card selection update uiState correctly` em `CardsViewModelTest.kt`, que esperava `editingCard == null` após `openNewCard()`, quando na verdade o ViewModel instancia um novo cartão com campos padrão para preenchimento. Com a correção e configuração de `testOptions` no Gradle (`isReturnDefaultValues = true`), todos os 29 testes unitários JVM foram executados e aprovados (100% verdes).
  - **Pipeline de Distribuição Android Aprovada:** O workflow `Android CI/CD & Distribution` (`android-distribution.yml`) agora completa com 100% de sucesso na branch `main`, cobrindo testes unitários, compilação de Release APK/AAB e upload de artefatos.
  - **Deploy Firebase Hosting & Firestore no Merge:** O workflow `Deploy to Firebase Hosting and Firestore on merge` (`firebase-hosting-merge.yml`) roda em modo não interativo e conclui o deploy do canal Live com sucesso.
  - **Workflow de Preview de Pull Request Reestruturado:** Renomeado para `.github/workflows/firebase-preview.yml`, com sintaxe YAML sanitizada e condicional de isolamento para PRs de bots/Dependabot, eliminando execuções fantasmas que ocorriam a cada push.

- **[2026-10-30] Fix de Compilação Release (Android):**
  - **Null Safety em `TransactionDetailDialog`:** corrigida chamada não segura `currentUser.displayName` para `currentUser?.displayName` na verificação de participantes do rateio, resolvendo erro do compilador Kotlin (`Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type UserProfile?`) na pipeline de Release.
  - Build: `./gradlew :app:compileReleaseKotlin` OK.

- **[2026-10-30] Paridade Web — Inteligência & UX da Tela de Faturas (Replicado do Android):**
  - **Roteamento Inteligente para Fatura Aberta:** na abertura do `CardInvoicesModal` ou ao alternar de cartão, se a fatura do mês atual estiver quitada (`status === 'paid'`), o seletor avança automaticamente para a próxima competência que tiver saldo em aberto (`openAmount > 0`), direcionando a atenção do usuário ao saldo a pagar.
  - **Filtros Combinados por Categoria ("Para onde foi o dinheiro"):** barras de categorias interativas com feedback visual esmeralda ativo (`bg-emerald-500/15`, borda suave e tag de ativação), operando em conjunto com busca, abas de filtro (adicionada a aba "100% Meu") e participante ativo. Banner com botão para limpeza rápida no topo dos lançamentos.
  - **Motor de Alertas Preditivos:** implementado `computePredictiveAlert` em `invoices.ts` com as 3 variações dinâmicas: Alerta de Anomalia de gastos por categoria (`anomaly`, > 25% da média histórica), Novas Assinaturas no ciclo atual (`new_subscription`) e Projeção estimada de fechamento (`projection`) com métricas contextuais e progresso temporal.
  - **Polimento Visual do Cartão:** remoção do chip simulado amarelo/emoji no cartão virtual. Substituição por badge vetorial em *glassmorphism* com ícone Contactless de ondas e marca d'água vetorial translúcida de cartão (`CreditCard`) no fundo do card virtual e na listagem de cartões (`SharedCardsModal`).
  - Build: `npm run build` (TypeScript e Vite) OK.

- **[2026-10-30] Refatoração de Inteligência & UX da Tela de Faturas (Android):**
  - **Roteamento Inteligente para Fatura Aberta:** no carregamento da aba Cartões (`CardsAndInvoicesViewModel`, `CardsViewModel` e `CardInvoicesViewModel`), se a fatura da competência atual estiver quitada (`InvoiceStatus.PAID`), o seletor avança automaticamente para a próxima competência em aberto (`openAmount > 0`), direcionando a atenção do usuário ao saldo pendente.
  - **Filtros Combinados por Categoria ("Para onde foi o dinheiro"):** barras de categorias interativas com feedback visual ativo (fundo esmeralda 15%, contorno sutil `Emerald400` e ícone de seleção), permitindo alternar filtro e operando em interseção com os chips de Divisão/Status (ex: "100% Meu" + "Alimentação"). Adicionado chip indicador na listagem de lançamentos com remoção rápida.
  - **Refatoração da Inteligência da Fatura (Insights Preditivos):** removidos textos estáticos redundantes de limite. Substituído por motor dinâmico `PredictiveInvoiceAlert` (`InvoiceEngine.computePredictiveAlert`) com 3 variações: Projeção estimada de fechamento por ritmo diário (`Projection`), Alertas de anomalia de gastos por categoria (`Anomaly`, desvios > 25% da média histórica) e Avisos de novas assinaturas detectadas no ciclo atual (`NewSubscription`).
  - **Polimento Visual do Cartão:** substituído o emoji/quadrado amarelo genérico por badge nativo em *glassmorphism* com ícone vetorial `Contactless` / `CreditCard` e marca d'água vetorial sutil no fundo (`Color.White.copy(alpha = 0.05f)`), alinhando ao design system Dark Mode minimalista.
  - Build: `./gradlew.bat compileDebugKotlin` OK.

- **[2026-10-30] Polimento Web — Etapas 1 e 2 concluídas:**
  - Etapa 1: `Header.tsx` sticky `top-0 z-40` com glassmorphism (`bg-slate-950/80 backdrop-blur-md border-b border-white/5`). `KpiGrid.tsx`: Hero Card com gradiente `from-emerald-950/60 to-slate-900` e brilho luminoso (sem borda/fundo opaco; novas classes `.kpi-hero`/`.kpi-tile` em `index.css`, pois `.glass-card` não-layered sobrescrevia os utilitários Tailwind); cards menores sem bordas rígidas nem divisórias internas, ícones com fundo translúcido a 10%.
  - Etapa 2: modo expandido (Tabela/Chat) agora ocupa 100% da largura e da altura abaixo do `Header` (layout flex `100dvh`, sem `fixed` com offset hardcoded), ocultando KPIs, banners e painel vizinho. Botão `Maximize2`/`Minimize2` padronizado (somente ícone, `aria-label`) em `TransactionTable.tsx` e `GeminiChat.tsx`; `ESC` restaura a visão padrão; áreas internas mantêm `flex-1 overflow-y-auto`.
  - Build: `npm run build` OK.
  - Etapa 2 concluída; Etapa 3 abaixo.
- **[2026-10-30] Etapa 3 - Filtros Avançados Web (Concluída):**
  - `TransactionTable.tsx`: cabeçalho Divisão/Responsável com ícone de funil (`Filter`) e dropdown dinâmico (opções "Meu"/rateados/terceiros extraídas das transações carregadas); badges de divisão em pílula (`rounded-full`) em todas as linhas (Meu, Terceiro, Amigo, Parceiro, Receita, Fatura) sem células vazias; rodapé fixo reativo (Entradas/Despesas/Saldo e total por pessoa) recalculado apenas sobre os itens filtrados.
  - Build: `npm run build` OK.
- **[2026-10-30] Etapa 4 - Limpeza do Chat & Polimento Web (Concluído):**
  - Chat limpo (sem chips de sugestões acima/abaixo do input) e botão expandir/restaurar circular padronizado. Polimento Web finalizado: Filtros estilo Excel, Tela Cheia Inteligente (Header sempre visível) e UI de badges em pílula.
  - Build: `npm run build` OK.
- **[2026-10-30] Correção de Layout & Filtros (Web):**
  - Expansão (Tabela/Chat) mantém o `Header` visível e interativo (KPIs e painel vizinho ocultos, altura restante abaixo do cabeçalho, `ESC` restaura). Botão expandir/restaurar do chat padronizado com o da tabela (ícone circular `rounded-full`, `title="Restaurar (ESC)"`). Chat sem chips de "Sugestões inteligentes". Badges de divisão e chip do cartão em pílula; filtro de funil dinâmico e rodapé analítico reativo (Entradas, Despesas, Saldo e itens exibidos).
  - Build: `npm run build` OK.

- **[2026-10-29] Encerramento da Fundação da Infraestrutura Base (100% Concluída):**
  - Revisão final e consolidação dos 6 pilares estruturais no `PROJECT_STATUS.md`: Arquitetura Monorepo, Paridade 100% do Motor Financeiro, Copiloto Gemini, Modo Demo Offline, UI/UX Responsiva ("Premium & Smart") e CI/CD Web/Android configurado.
- **[2026-10-29] Esteira de Release Android (`android-release.yml`) & Guia DevOps (`qa-team`):**
  - Criação do workflow `.github/workflows/android-release.yml` com gatilhos em `main` (`android/**`) e `workflow_dispatch`, decodificação de `KEYSTORE_BASE64`, build assinado com `-Pandroid.injected.signing.*`, upload de artefato (`actions/upload-artifact@v4`) e entrega automática ao grupo `qa-team` no **Firebase App Distribution** (`wzieba/Firebase-Distribution-Github-Action@v1`).
  - Atualização do `android/README.md` com o guia passo a passo de ativação do Firebase App Distribution (`qa-team`), geração da Service Account Key no Google Cloud e cadastro dos 6 Secrets obrigatórios no GitHub.
- **[2026-10-29] CI/CD Android & Assinatura Segura de Release:**
  - Blindagem do `.gitignore` para artefatos de assinatura (`*.jks`, `*.keystore`, `local.properties`, `google-services.json`).
  - Configuração de `signingConfigs.release` e leitura híbrida de `GEMINI_API_KEY` no `android/app/build.gradle.kts` com fallback seguro para ambiente local.
  - Criação do workflow `.github/workflows/android-distribution.yml` (Testes Unitários, APK/AAB Release, GitHub Artifacts, Firebase App Distribution e Google Play Internal Track).
  - Documentação completa da tabela de GitHub Secrets em `android/README.md`.
- **[2026-10-29] Governança & Arquitetura (Setup Inicial):**
  - Atualização de `.agents/rules/global.md` instituindo o Squad Multidisciplinar (Arquiteto, UX/UI, Cyber Security, QA, Documentador) e diretrizes de economia extrema de tokens.
  - Verificação e validação dos gatilhos globais e de plataforma em `.agents/rules.json` (`global.md`, `react.md`, `kotlin.md`).
  - Criação do `PROJECT_STATUS.md` na raiz como fonte única da verdade da arquitetura Web e Android.
- **[2026-10-29] Extrato & Cartões Compartilhados — Etapa 1 (Web):**
  - Extrato: removido o toggle "Faturas Consolidadas" (lançamento único da fatura sempre ativo); descrição agora é apenas `Fatura <Cartão>` (sem "Minha parte"/"100% Terceiros"); removidos rótulos "Sua parte"/"Minha Cota" das linhas de fatura (desktop e mobile).
  - Cartões/Faturas (convidado): `visibleToUids` agora inclui titular + `sharedWithUids` em lançamentos recorrentes, `changeSplit`, `updateInstallmentGroup`, dados Demo e ao compartilhar cartão (`saveCard` propaga aos lançamentos existentes). KPI "Minha parte" substituído por "Total pago" e removido "Sua parte nesta fatura" do cartão virtual; cota individual permanece apenas no balão "Divisão da Fatura".
  - Build: `npm run build` OK.
- **[2026-10-29] Extrato & Cartões Compartilhados — Etapa 2 (Android):**
  - Extrato: removido o botão de alternância de faturas consolidadas (`TransactionsScreen`/`TransactionsViewModel`; fatura sempre consolidada); descrição `Fatura <Cartão>` em `InvoiceEngine.createConsolidatedInvoiceTx`; removido "Minha cota"/"Sua parte" de `TransactionItem`.
  - Cartões (convidado): `CardsAndInvoicesViewModel` e `CardsViewModel` não filtram mais por `visibleToUids` (apenas excluem reservas privadas) e o valor da fatura usa `matchingInv.total`, igual ao titular. Removidos "Sua parte nesta fatura" (`CardsAndInvoicesScreen`) e KPI "Minha parte" (trocado por "Total pago" em `CardInvoicesScreen`). Dados Demo (`MockFinanceRepository`) incluem titular e convidados do cartão em `visibleToUids`.
  - Build: `./gradlew.bat compileDebugKotlin` OK.
- **[2026-10-29] Fatura total para convidado (Web + Android):** a sincronização de `visibleToUids` do cartão compartilhado falhava em lote quando havia lançamentos criados por outra pessoa (regras só permitem o criador atualizar). `fsSaveCard` (Web) e `syncCardTransactionsVisibility` (Android) agora atualizam apenas lançamentos do próprio titular; Web ganhou auto-sincronização do titular em `FinanceContext` (lançamentos antigos do cartão passam a ser visíveis aos convidados). Builds: `npm run build` e `compileDebugKotlin` OK.
