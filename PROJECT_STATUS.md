# PROJECT_STATUS.md — Fonte da Verdade & Índice Arquitetural (`Saldo Livre`)

> **Última Atualização:** 2026-10-29
> **Status da Fundação:** 🟢 **100% CONCLUÍDA E ESTABILIZADA**
> **Propósito:** Índice central de arquitetura, mapeamento de paridade Web/Mobile, segurança e histórico de micro-commits para otimização máxima de contexto e tokens.

### Pilares da Fundação Consolidados:
- ✅ **Arquitetura Monorepo & Governança:** Isolamento estrito de contexto (`/web` e `/android`) regido por `.agents/rules.json` e Squad Multidisciplinar (`global.md`).
- ✅ **Paridade 100% do Motor Financeiro:** Regras de negócio, Saldo Livre, faturas, rateios, projeções e fechamentos mensais sincronizados entre `finance.ts`/`invoices.ts` e `FinanceEngine.kt`/`InvoiceEngine.kt`.
- ✅ **Copiloto Gemini & IA Híbrida:** Assistente conversacional multi-turn com Function Calling, gráficos dinâmicos, comando de voz, classificador neural local e balanceador de chaves (`GeminiLoadBalancer`).
- ✅ **Modo Demo Offline & Cloud Firestore:** Funcionamento imediato com dados interativos de demonstração (`mockData.ts` / `MockFinanceRepository.kt`) e sincronização em tempo real blindada por `firestore.rules`.
- ✅ **UI/UX Responsiva ("Premium & Smart"):** Identidade visual unificada em Dark Mode com destaques em Verde-Esmeralda, componentes em pílula (`CircleShape` / `rounded-full`), proteção contra quebra de texto (`Ellipsis` / `truncate`) e zero *box-in-box*.
- ✅ **CI/CD & DevOps Configurados:** Pipelines automatizados para Web (`.github/workflows/firebase-hosting-*.yml`) e Android (`.github/workflows/android-release.yml` e `android-distribution.yml`) com assinatura segura e distribuição ao grupo `qa-team` via Firebase App Distribution.

---

## 1. Visão Geral da Arquitetura & Banco de Dados

O **Saldo Livre** é um ecossistema financeiro inteligente multiplataforma (Monorepo: **React/TypeScript** na Web e **Kotlin/Jetpack Compose** no Android) com sincronização em tempo real via **Cloud Firestore**, suporte a modo Offline/Demo (Mock) e Copiloto Financeiro embarcado (**Google Gemini** + Classificador Neural Local).

### 1.1. Coleções do Cloud Firestore
- `/users/{userId}`: Perfil do usuário (`UserProfile`), chaves/configurações sincronizadas e preferências.
  - `/users/{userId}/agent_rules/{ruleId}` (e top-level `/agent_rules`): Regras aprendidas e memórias de categorização do Copiloto.
  - `/users/{userId}/chat_sessions/{sessionId}` (e top-level `/chat_sessions`): Histórico de conversas com o Copiloto Gemini.
  - `/users/{userId}/month_closures/{closureId}` (e top-level `/month_closures`): Fechamentos mensais consolidados (`MonthClosure`) e repasse de saldo acumulado.
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
| **Copiloto IA (Gemini Chat & Voz)** | `components/GeminiChat.tsx`, `ui/ChatChart.tsx` | `ui/screens/chat/GeminiChatScreen.kt`, `GeminiChatViewModel.kt`, `DynamicChatChart.kt`, `data/ai/AndroidSpeechManager.kt` | 🟢 100% |
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
