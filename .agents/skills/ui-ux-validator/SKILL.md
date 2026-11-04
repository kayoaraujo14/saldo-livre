---
name: ui-ux-validator
description: Skill autônoma de validação visual e auditoria UI/UX para o projeto Saldo Livre (Web React e Mobile Android Jetpack Compose). Acione sempre que criar, modificar ou ajustar telas, componentes, modais, temas e layouts em web/ ou android/.
metadata:
  category: UI/UX Automation & Visual QA
---

# UI/UX Visual Validator — Saldo Livre

Esta skill estabelece o protocolo autônomo e contínuo de validação visual e auditoria UI/UX para o projeto **Saldo Livre**, abrangendo o frontend Web (React + Tailwind CSS) e o mobile nativo (Android + Jetpack Compose + Material Design 3).

> [!IMPORTANT]
> **Regra de Ouro do Loop de Auto-correção:**
> Toda alteração visual (em componentes, layouts, tipografia ou cores) DEVE ser auditada por captura de tela real antes da entrega. Caso qualquer inconsistência visual (quebra de layout, sobreposição, texto cortado, contraste inadequado ou desalinhamento) seja identificada no screenshot, corrija o código imediatamente (`tsx`/`css` ou `kt`), reexecute a captura e valide novamente até a UI atingir a perfeição ("Premium & Smart").

---

## 1. Diretórios de Artefatos e Capturas

As capturas de tela e dumps de tela intermediários são armazenados nos diretórios dedicados (já devidamente ignorados no `.gitignore`):
- Web: `.agents/screenshots/web/`
- Android: `.agents/screenshots/android/`

---

## 2. Protocolo de Validação Web (`web/`)

### 2.1. Inicialização do Servidor Local
1. Verifique se o servidor Vite já está ativo (porta padrão `5173`).
2. Se inativo, inicialize o servidor Vite em background:
   ```bash
   # Windows PowerShell / Bash
   npm --prefix web run dev
   ```
3. Confirme que o servidor responde em `http://localhost:5173`.

### 2.2. Acesso e Bypass de Autenticação (Modo Demo)
Para testar visualmente com dados financeiros realistas sem travar no fluxo OAuth do Firebase:
- Acesse `http://localhost:5173`.
- Se a tela apresentada for a `LoginScreen`:
  - **Opção Interativa:** Clique no botão `Experimentar Modo Demonstração (Sem Login)` (aciona `enterDemo()`).
  - **Opção Direta (Storage Injection):** No contexto do navegador, defina os itens no `localStorage`:
    ```javascript
    localStorage.setItem('saldo-livre:force-demo', '1');
    localStorage.setItem('saldo-livre:session-uid', 'usr_kayo');
    location.reload();
    ```
- O painel carregará o estado populado de `web/src/store/demoState.ts` contendo cartões, faturas, transações parceladas, amigos e metas.

### 2.3. Viewports Obrigatórias de Teste

#### A. Desktop Viewport (`1440x900`)
- **Tela Principal:**
  - `Header`: Perfil do usuário, seletor de mês, botão de notificações e ações rápidas.
  - `KpiGrid`: Cards de Saldo Livre, Entradas, Despesas Pessoais e Faturas de Cartão. Valores formatados via `NumberTicker` ou moeda sem quebras indevidas.
  - `CategoryBudgetsCard`: Gráficos e barras de progresso de orçamento por categoria.
  - `TransactionTable`: Alinhamento das colunas, badges de status (`StatusBadge`, `SplitBadge`), ações e paginação.
  - `GeminiChat`: Renderização do chat lateral ou tela cheia, alinhamento de bolhas, inputs e gráficos embutidos (`ChatChart.tsx`).
- **Modais Secundários (`web/src/components/modals/`):**
  - Acione e valide visualmente os modais principais:
    * `CardInvoicesModal` (Faturas e detalhes de cartões).
    * `TransactionModal` (Inclusão e edição de despesas/receitas).
    * `FinancialHealthModal` (Diagnóstico de saúde financeira).
    * `FutureProjectionModal` (Projeções futuras de fluxo de caixa).
    * `InvestmentsModal` (Alocações e saldos de investimento).
    * `ManageBudgetsModal` (Gestão de limites de gastos por categoria).
    * `SharedCardsModal`, `RecurringBillsModal`, `ReceivablesModal`, `FriendsModal`.

#### B. Mobile Web Viewport (`390x844` - iPhone 14 / PWA)
- **Adaptação Responsiva:**
  - Ocultação da barra desktop e ativação da `MobileBottomNav` fixa na base.
  - Substituição da tabela clássica por cards verticais `MobileTransactionCard`.
  - Comportamento de abertura da `MobileMenuSheet`.
  - Margem inferior suficiente (`pb-24` ou equivalente) para que a barra de navegação móvel nunca sobreponha o último item da lista.

### 2.4. Captura e Verificação de Console
- Salve as capturas em `.agents/screenshots/web/desktop_dashboard.png`, `.agents/screenshots/web/mobile_dashboard.png`, etc.
- Inspecione se há erros em vermelho no console do navegador (como hydration errors, avisos de CSS não suportado ou falhas de import).

---

## 3. Protocolo de Validação Android (`android/`)

### 3.1. Detecção do Dispositivo ou Emulador
Execute no terminal:
```bash
adb devices
```
Certifique-se de que pelo menos um dispositivo ou emulador esteja no estado `device`. Caso nenhum esteja rodando, inicie o AVD configurado.

### 3.2. Compilação e Instalação
A partir da raiz do repositório:
```bash
# Windows
cd android && .\gradlew.bat installDebug && cd ..

# Linux / macOS
cd android && ./gradlew installDebug && cd ..
```

### 3.3. Inicialização da Activity Principal
Inicie o aplicativo no pacote `com.saldolivre.app`:
```bash
adb shell am start -n com.saldolivre.app/.MainActivity
```

### 3.4. Bypass de Autenticação (Modo Demo Nativo)
Caso o app abra na `LoginScreen`:
1. Extraia o layout atual da tela:
   ```bash
   adb shell uiautomator dump /sdcard/view.xml && adb pull /sdcard/view.xml .agents/screenshots/android/view.xml
   ```
2. No arquivo `view.xml`, localize o nó contendo o texto `Experimentar Modo Demonstração (Sem Login)` e leia suas coordenadas `bounds="[x1,y1][x2,y2]"`.
3. Calcule o ponto central `(X = (x1+x2)/2, Y = (y1+y2)/2)` e envie o toque:
   ```bash
   adb shell input tap X Y
   ```
4. A tela transiciona para o `AppNavHost` inicializando o `MockFinanceRepository`.

### 3.5. Navegação Autônoma entre Telas do `AppNavHost`
Utilize o mesmo padrão de dump `uiautomator` e `adb shell input tap` para navegar pelas abas da `AppBottomBar` e rotas do app:
- **Abas Principais:**
  * `DashboardScreen` (Início / Visão Geral)
  * `TransactionsScreen` (Extrato de Transações)
  * `CardsAndInvoicesScreen` (Cartões e Faturas consolidadas)
  * `GeminiChatScreen` (Copiloto IA Gemini, acessível pelo botão flutuante central da bottom bar)
  * `MenuScreen` (Hub de ferramentas e configurações)
- **Sub-telas do Menu:**
  * `FutureProjectionsScreen` (Projeções de saldo)
  * `InvestmentsScreen` (Carteira de investimentos)
  * `RecurringBillsScreen` (Contas fixas e recorrentes)
  * `ReceivablesScreen` (A receber e reembolsos)
  * `FriendsScreen` (Divisões com amigos e convites)

### 3.6. Captura de Tela Nativa
Para capturar o screenshot da tela atual com máxima fidelidade:
```bash
adb exec-out screencap -p > .agents/screenshots/android/screen_current.png
```
Use o visualizador multimodal para analisar a imagem capturada diretamente.

---

## 4. Checklist de Auditoria Visual do Saldo Livre

Em cada screenshot inspecionado (Web ou Android), execute o seguinte checklist minucioso:

1. **Hierarquia e Valores Financeiros:**
   - [ ] Valores monetários (R$ xx,xx) estão em uma única linha, sem quebras estranhas entre o símbolo `R$` e o montante numérico.
   - [ ] Em cards compactos (`KpiCard`, `KpiGrid`), textos longos possuem tratamento com reticências (`truncate` / `TextOverflow.Ellipsis`).
   - [ ] As fontes e pesos numéricos transmitem clareza e credibilidade financeira.

2. **Gráficos e Chat IA:**
   - [ ] Componentes de gráficos (`ChatChart.tsx` na Web e `DynamicChatChart.kt` no Android) renderizam eixos, legendas e barras sem cortes laterais.
   - [ ] No `GeminiChat` / `GeminiChatScreen`, as bolhas do usuário e do assistente possuem contraste adequado e padding interno confortável.
   - [ ] O campo de input de mensagem permanece visível e não sofre oclusão por teclados virtuais ou barras de navegação.

3. **Espaçamento e Safe Areas (Edge-to-Edge):**
   - [ ] **Android:** O topo das telas respeita os `WindowInsets.statusBars` e o rodapé respeita a `navigationBars` e o contorno da `AppBottomBar`.
   - [ ] **Web Mobile:** O último card de transação tem espaçamento suficiente (`padding-bottom`) para não ser encoberto pela `MobileBottomNav`.
   - [ ] Não há "síndrome de box-in-box" (caixas com bordas aninhadas desnecessárias que poluem o visual).

4. **Contraste e Cores do Design System ("Premium & Smart"):**
   - [ ] Fundo escuro profundo consistente (`#090d16` / `DarkBackground`).
   - [ ] Acentos em Verde-Esmeralda (`Emerald400` / `Emerald500`) com boa legibilidade sobre fundos escuros.
   - [ ] Badges de divisão e status (`StatusBadge`, `SplitBadge`, `Chip`) utilizam bordas e preenchimentos suaves e legíveis.
   - [ ] Ícones e textos secundários utilizam opacidade controlada (`TextSecondary`), sem parecerem desbotados ou invisíveis.

---

## 5. Resumo Operacional: Loop de Auto-correção

```
   ┌───────────────────────────────────────────────┐
   │ 1. Alterar código da UI (React ou Compose)    │
   └──────────────────────┬────────────────────────┘
                          ▼
   ┌───────────────────────────────────────────────┐
   │ 2. Capturar Screenshot (Web ou Android)       │
   │    -> .agents/screenshots/web/                │
   │    -> .agents/screenshots/android/            │
   └──────────────────────┬────────────────────────┘
                          ▼
   ┌───────────────────────────────────────────────┐
   │ 3. Inspecionar visualmente via Multimodal     │
   │    Aplicar Checklist de Auditoria             │
   └──────────────────────┬────────────────────────┘
                          ▼
                  Inconsistência visual?
                 /                      \
             Sim                         Não
             /                             \
   ┌──────────────────────┐        ┌───────────────────────┐
   │ 4. Ajustar código    │        │ 5. Aprovar e avançar  │
   │    e repetir loop    │        │    para entrega       │
   └──────────────────────┘        └───────────────────────┘
```
