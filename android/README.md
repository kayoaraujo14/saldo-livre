# Saldo Livre — Aplicativo Android Nativo (Kotlin + Jetpack Compose) 📱🇧🇷

Versão nativa oficial do **Saldo Livre** para a plataforma Android, desenvolvida inteiramente na linguagem oficial e nativa (**Kotlin**) utilizando o moderno ecossistema **Jetpack Compose** (Material Design 3), arquitetura limpa (**Clean Architecture + MVVM**), **Kotlin Coroutines & StateFlow**, e compatibilidade direta com **Cloud Firestore** e o **Google Gemini AI**.

---

## 🏗️ Arquitetura & Tecnologias Utilizadas

- **Linguagem**: Kotlin 1.9+ (100% Nativo Android).
- **Interface de Usuário**: Jetpack Compose com Material Design 3 e Tema escuro sob medida (paleta Emerald/Slate/Cyan).
- **Gerenciamento de Estado**: Kotlin StateFlow, SharedFlow e MVVM com AndroidX ViewModel.
- **Motor Financeiro Nativo (`FinanceEngine.kt`)**: Port das regras financeiras do projeto com precisão bancária em ponto flutuante arredondado a duas casas decimais.
- **Camada de Repositório Híbrida**:
  - `MockFinanceRepository`: Modo Demo completo e interativo com dados dos parceiros "Kayo Araújo" e "Marina Costa", alternador de visão na TopBar e reatividade em tempo real.
  - `FirestoreFinanceRepository`: Sincronização em tempo real com as coleções do Firebase Cloud Firestore (`transactions`, `cards`, `users`).
- **Copiloto IA Integrado (`GeminiChatScreen`)**: Assistente nativo conversacional com capacidade de responder sobre saldo livre, limite diário e criar transações por linguagem natural.

---

## 📂 Estrutura de Pacotes do Projeto

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/saldolivre/app/
│   │   │   ├── SaldoLivreApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── engine/
│   │   │   │   │   └── FinanceEngine.kt           # Motor de cálculo de KPIs, datas de fatura e divisões
│   │   │   │   ├── model/
│   │   │   │   │   ├── Transaction.kt             # Transações, tipos, status e alocações
│   │   │   │   │   ├── Category.kt                # 15 categorias nativas, ícones e cores
│   │   │   │   │   ├── Card.kt                    # Cartões com dias de fechamento e vencimento
│   │   │   │   │   ├── RecurringBill.kt           # Contas recorrentes e regras de estimativa
│   │   │   │   │   ├── UserProfile.kt             # Perfis de usuário
│   │   │   │   │   └── MonthSummary.kt            # KPIs consolidados do mês
│   │   │   │   └── repository/
│   │   │   │       ├── FinanceRepository.kt       # Interface do repositório
│   │   │   │       ├── MockFinanceRepository.kt   # Repositório de teste/demo
│   │   │   │       └── FirestoreFinanceRepository.kt # Conexão direta Cloud Firestore
│   │   │   └── ui/
│   │   │       ├── theme/
│   │   │       │   ├── Color.kt
│   │   │       │   ├── Theme.kt
│   │   │       │   └── Type.kt
│   │   │       ├── components/
│   │   │       │   ├── KpiCard.kt
│   │   │       │   ├── TransactionItem.kt
│   │   │       │   ├── MonthSelector.kt
│   │   │       │   ├── StatusBadge.kt
│   │   │       │   └── SplitBadge.kt
│   │   │       ├── screens/
│   │   │       │   ├── dashboard/                 # Tela Principal (Saldo Livre, Limite Diário, KPIs)
│   │   │       │   ├── transactions/              # Extrato Completo com busca e filtros
│   │   │       │   ├── form/                      # Formulário de Nova Despesa/Receita
│   │   │       │   ├── receivables/               # Valores a Receber (Parceiro, Amigos, Terceiros)
│   │   │       │   ├── cards/                     # Visualização dos Cartões Físicos/Virtuais
│   │   │       │   └── chat/                      # Copiloto Inteligente Gemini
│   │   │       └── navigation/
│   │   │           ├── BottomNavItem.kt
│   │   │           └── AppNavHost.kt
│   │   └── res/
│   │       ├── values/strings.xml, colors.xml, themes.xml
│   │       └── drawable/ic_launcher_...
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🚀 Como Executar o Projeto no Android Studio

### 1. Abrir o Projeto
1. Abra o **Android Studio** (Hedgehog, Iguana, Jellyfish ou superior).
2. Selecione **Open** e navegue até a pasta:
   `c:\Users\Kayo Araújo\.gemini\antigravity-ide\scratch\saldo-livre\android`
3. O Android Studio sincronizará automaticamente as dependências via Gradle.

### 2. Executar no Emulador ou Celular
1. Selecione um dispositivo virtual (Android Emulator com API 26+) ou conecte um smartphone Android via USB com Depuração USB ativada.
2. Clique no botão verde de **Run (Shift + F10)**.

### 3. Compilar APK Debug via Linha de Comando
Caso tenha o JDK e Android SDK configurados no PATH:
```bash
cd android
./gradlew assembleDebug
```
O APK gerado estará em: `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## ☁️ Conectando com o Firebase (Opcional)

Por padrão, o app já inicia 100% funcional no **Modo Demo interativo**, permitindo testar tudo imediatamente.

Para conectar ao seu projeto real do Firebase:
1. No [Console do Firebase](https://console.firebase.google.com/), adicione um **App Android** com o package name:
   `com.saldolivre.app`
2. Baixe o arquivo `google-services.json` gerado pelo Firebase.
3. Cole o arquivo em: `android/app/google-services.json`.
4. Em `android/app/build.gradle.kts`, descomente a aplicação do plugin:
   ```kotlin
   id("com.google.gms.google-services")
   ```
5. Em `SaldoLivreApplication.kt`, alterne `MockFinanceRepository()` para `FirestoreFinanceRepository()`.

---

## 💡 Recursos & Regras de Negócio Implementadas

1. **Saldo Livre Real do Mês**:
   `Receitas - (Já Pago + Comprometido)`.
2. **Disponível por Dia (Limite Diário Seguro)**:
   `Saldo Livre / Dias Restantes no Mês de Competência`.
3. **Cálculo Automático de Fatura de Cartão de Crédito**:
   Considera o dia da compra, o dia de fechamento e o dia de vencimento para jogar a despesa na competência correta.
4. **Divisões e Cartões Compartilhados**:
   - `100% Meu`: afeta apenas o usuário atual.
   - `50/50 (Casal)`: divide meio a meio com o parceiro.
   - `Amigo da Rede`: calcula percentual e gera item a receber.
   - `Terceiro Externo`: gera item em "A Receber" com opção de dar baixa rápida com 1 toque.
5. **Copiloto IA Gemini**:
   Entende comandos como *"Gastei 45 no almoço hoje"* e faz o lançamento automático, recalculando instantaneamente todos os KPIs.

---

## ⚙️ CI/CD: Compilação Assinada & Distribuição Automática (GitHub Actions)

O repositório conta com a esteira automatizada [`.github/workflows/android-release.yml`](../.github/workflows/android-release.yml), acionada a cada `push` na branch `main` (restrita a alterações em `android/**`) ou manualmente via **Workflow Dispatch** (`workflow_dispatch`).

### Passos Manuais Obrigatórios de Configuração (DevOps & Cyber Security)

Antes de acionar o pipeline pela primeira vez, execute os 3 passos manuais abaixo:

1. **Configurar o Firebase App Distribution:**
   - Acesse o [Console do Firebase](https://console.firebase.google.com/) → **Release & Monitor** → **App Distribution** e clique em **Get Started** para ativar o serviço.
   - Na aba **Testers & Groups**, crie o grupo obrigatório chamado **`qa-team`** e adicione os e-mails dos testadores.

2. **Baixar a Service Account Key (JSON) do Google Cloud:**
   - Acesse o [Google Cloud Console](https://console.cloud.google.com/iam-admin/serviceaccounts) vinculado ao seu projeto Firebase.
   - Crie ou selecione uma Service Account com o papel **Firebase App Distribution Admin** (`roles/firebaseappdistro.admin`).
   - Gere uma nova chave no formato **JSON** e salve o arquivo localmente de forma segura (nunca versione este arquivo no Git).

3. **Cadastrar os Secrets no GitHub:**
   - No repositório do GitHub, acesse **Settings → Secrets and variables → Actions → New repository secret** e cadastre os seguintes Secrets obrigatórios:

| GitHub Secret | Obrigatório | Descrição / Como Obter |
| :--- | :---: | :--- |
| `FIREBASE_APP_ID` | Sim | ID do Aplicativo Android nas Configurações do Projeto no Firebase (ex: `1:1234567890:android:abcdef123456`). |
| `FIREBASE_CREDENTIALS_JSON` | Sim | Conteúdo completo (JSON bruto) da Service Account Key baixada do Google Cloud. |
| `KEYSTORE_BASE64` | Sim | Seu arquivo `.jks` ou `.keystore` codificado em Base64 (`base64 -w 0 keystore.jks` no Linux/macOS ou `[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore.jks"))` no PowerShell). |
| `KEYSTORE_PASSWORD` | Sim | Senha do arquivo Keystore (`android.injected.signing.store.password`). |
| `KEY_ALIAS` | Sim | Alias da chave de assinatura dentro do Keystore (`android.injected.signing.key.alias`). |
| `KEY_PASSWORD` | Sim | Senha da chave específica (`android.injected.signing.key.password`). |
| `GOOGLE_SERVICES_JSON` | Opcional / Recomendado | Conteúdo JSON do `google-services.json` de produção (caso não exista no runner, utiliza o `google-services.json.example` como fallback). |

### O que o Workflow `android-release.yml` Executa:
1. **Ambiente & Cache**: Provisiona `ubuntu-latest`, configura o **JDK 17** (`temurin`) e ativa o cache nativo do Gradle.
2. **Assinatura Segura em Memória/Runner**: Decodifica `secrets.KEYSTORE_BASE64` diretamente para `android/app/keystore.jks` e concede permissão de execução ao `./gradlew`.
3. **Compilação Injetada (`assembleRelease`)**: Compila o APK de Release injetando as credenciais via `-Pandroid.injected.signing.*`.
4. **Artefato & Distribuição**:
   - Salva o `app-release.apk` nos **Artifacts** do GitHub Actions (`actions/upload-artifact@v4`).
   - Publica automaticamente o `app-release.apk` no **Firebase App Distribution** para o grupo **`qa-team`** via `wzieba/Firebase-Distribution-Github-Action@v1`.


