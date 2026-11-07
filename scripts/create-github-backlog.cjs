const token = process.env.GITHUB_PERSONAL_ACCESS_TOKEN || process.env.GITHUB_TOKEN;
const owner = 'kayoaraujo14';
const repo = 'saldo-livre';

const headers = {
  'Authorization': `Bearer ${token}`,
  'Accept': 'application/vnd.github.v3+json',
  'User-Agent': 'SaldoLivre-Backlog-Seeder',
  'Content-Type': 'application/json',
};

async function api(path, options = {}) {
  const url = `https://api.github.com/repos/${owner}/${repo}${path}`;
  const res = await fetch(url, { ...options, headers: { ...headers, ...options.headers } });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(`API Error [${res.status}] ${path}: ${data.message || JSON.stringify(data)}`);
  }
  return data;
}

const labelsToCreate = [
  { name: 'scope: web', color: '1d76db', description: 'Impacta o frontend Web React' },
  { name: 'scope: android', color: '3ddc84', description: 'Impacta o aplicativo Android Kotlin' },
  { name: 'scope: multiplatform', color: '6f42c1', description: 'Paridade simultânea Web e Android' },
  { name: 'scope: backend', color: '0052cc', description: 'Cloud Functions ou Firestore Rules' },
  { name: 'type: bug', color: 'd73a4a', description: 'Correção de inconsistência visual ou lógica' },
  { name: 'type: enhancement', color: 'a2eeef', description: 'Melhoria de usabilidade, UI ou funcionalidade' },
  { name: 'type: performance', color: 'ffc107', description: 'Otimização de renderização, tempo ou recursos' },
  { name: 'type: security', color: 'b60205', description: 'Blindagem de segurança, App Check ou regras' },
  { name: 'priority: high', color: 'b60205', description: 'Prioridade alta para o produto' },
  { name: 'priority: medium', color: 'fbca04', description: 'Prioridade média' },
];

const milestonesToCreate = [
  { title: 'Sprint 1 — v1.2.0: UI/UX & Prevenção de Bugs Visuais', description: 'Polimento estético, truncamentos, safe-areas mobile, skeletons e empty states' },
  { title: 'Sprint 2 — v1.3.0: Performance Máxima & Virtualização', description: 'Virtualização do extrato web e estabilidade Compose no Android' },
  { title: 'Sprint 3 — v1.4.0: Segurança em Camadas & App Check', description: 'App Check, validação de ferramentas de IA e regras do Firestore' },
];

const issuesToCreate = [
  // Sprint 1
  {
    milestoneIndex: 0,
    title: '[UI/UX] [Web & Android] Padronização Tipográfica, Truncamento Seguro e Proteção Anti-Overflow',
    labels: ['scope: multiplatform', 'type: bug', 'priority: high'],
    body: `### Contexto & Objetivo
Garantir que valores monetários extensos (ex: \`R$ 123.456,78\`) e descrições longas de lançamentos ou categorias não quebrem o alinhamento visual, não causem overflow horizontal nem empurrem outros elementos para fora da tela.

### Escopo de Implementação
- **Web (\`web/src/...\`):**
  - Aplicar classes de proteção do Tailwind (\`truncate\`, \`min-w-0\`, \`shrink-0\`) nas células da \`TransactionTable.tsx\`, cards de \`KpiGrid.tsx\`, \`CategoryBudgetsCard.tsx\` e cards móveis \`MobileTransactionCard.tsx\`.
  - Garantir que badges (\`SplitBadge\`, \`StatusBadge\`) mantenham tamanho consistente com \`whitespace-nowrap\`.
- **Android (\`android/app/...\`):**
  - Adicionar \`TextOverflow.Ellipsis\` e \`maxLines = 1\` explícitos em \`TransactionItem.kt\`, \`KpiCard.kt\` e nos cartões virtuais em \`CardsScreen.kt\`.
  - Adicionar suporte a scroll horizontal defensivo ou auto-sizing onde a quebra for crítica.

### Critérios de Aceite (Definition of Done)
- [ ] Nenhuma quebra de layout ou estouro horizontal em resoluções de smartphone (360px a 430px) e desktop (1440px).
- [ ] Valores monetários e descrições longas exibem reticências (\`...\`) de forma limpa.
- [ ] Testes automatizados verdes em ambas as plataformas.`,
  },
  {
    milestoneIndex: 0,
    title: '[UI/UX] [Mobile] Otimização de Safe Area, Insets e Proteção contra Sobreposição da BottomBar',
    labels: ['scope: multiplatform', 'type: enhancement', 'priority: high'],
    body: `### Contexto & Objetivo
Assegurar que a barra de navegação móvel (\`MobileBottomNav\` na Web e \`AppBottomBar\` no Android) nunca sobreponha o último item das listas de lançamentos, formulários ou botões de ação primária.

### Escopo de Implementação
- **Web Mobile (\`web/src/...\`):**
  - Garantir espaçamento inferior consistente (\`pb-24\` ou equivalente) em todas as visões móveis (\`App.tsx\`, \`TransactionTable\`, modais de tela cheia).
  - Verificar margens de safe-area para dispositivos iOS/PWA (\`env(safe-area-inset-bottom)\`).
- **Android (\`android/app/...\`):**
  - Aplicar \`contentPadding\` com \`WindowInsets.navigationBars\` ou padding inferior seguro em todas as \`LazyColumn\` (\`TransactionsScreen.kt\`, \`DashboardScreen.kt\`, etc.).
  - Foco exclusivo em smartphones Android (sem dispersão para tablets neste ciclo).

### Critérios de Aceite (Definition of Done)
- [ ] O último item de todas as listas e telas rola completamente acima da barra de navegação.
- [ ] Nenhum botão flutuante ou de ação fica encoberto pela barra inferior.
- [ ] Testes visuais validados em dispositivos/emuladores móveis.`,
  },
  {
    milestoneIndex: 0,
    title: '[UI/UX] [Web & Android] Implementação de Esqueletos de Carregamento (Shimmer) e Estados Vazios (Empty States) Amigáveis',
    labels: ['scope: multiplatform', 'type: enhancement', 'priority: medium'],
    body: `### Contexto & Objetivo
Substituir indicadores genéricos de loading por esqueletos com efeito Shimmer nas tabelas e cards, eliminando saltos visuais (CLS - Cumulative Layout Shift), e humanizar estados vazios com ilustrações e orientações práticas de 1-clique.

### Escopo de Implementação
- **Web (\`web/src/...\`):**
  - Criar componente de esqueleto de carregamento com Tailwind para linhas de transações, faturas de cartão e cards de KPI.
  - Implementar empty states informativos em \`TransactionTable.tsx\` e modais para usuários novos (ex: "Nenhuma despesa neste mês — que tal cadastrar uma nova?").
- **Android (\`android/app/...\`):**
  - Reutilizar e aprimorar \`ShimmerLoading.kt\` nas listas de transações e cartões.
  - Aperfeiçoar o componente \`EmptyState.kt\` com botões contextuais de ação primária.

### Critérios de Aceite (Definition of Done)
- [ ] Transições de carregamento sem saltos de layout na inicialização da aplicação.
- [ ] Telas vazias guiam o usuário de forma amigável com botão de ação direta.`,
  },
  {
    milestoneIndex: 0,
    title: '[UI/UX] [Web] Correção de Dependências Exhaustive-Deps e Limpeza Estrita de Lint',
    labels: ['scope: web', 'type: bug', 'priority: medium'],
    body: `### Contexto & Objetivo
Garantir código 100% livre de advertências e em total conformidade com o compilador React 19.

### Escopo de Implementação
- Corrigir alerta de dependências do \`useEffect\` em \`web/src/components/modals/FinancialHealthModal.tsx\` (\`health.*\` e variáveis de resumo).
- Rodar o linter \`oxlint\` e assegurar zero warnings e zero errors em todo o frontend.

### Critérios de Aceite (Definition of Done)
- [ ] \`npm --prefix web run lint\` executado com 0 warnings e 0 errors em todos os arquivos.
- [ ] Suíte de testes do Vitest passando com 100% de sucesso.`,
  },

  // Sprint 2
  {
    milestoneIndex: 1,
    title: '[Performance] [Web] Virtualização Imediata do Extrato e Tabela de Transações',
    labels: ['scope: web', 'type: performance', 'priority: high'],
    body: `### Contexto & Objetivo
Implementar virtualização de lista imediatamente no extrato de transações Web, garantindo taxa fixa de 60 FPS e consumo mínimo de memória no navegador, independentemente do volume de dados.

### Escopo de Implementação
- Integrar virtualizador de nós DOM em \`web/src/components/TransactionTable.tsx\` e \`MobileTransactionCard.tsx\`.
- Preservar integralmente:
  - Filtros avançados combinados estilo Excel (categoria, tipo, participante, status).
  - Modo tela cheia inteligente com cabeçalho sticky.
  - Badges em pílula de rateio e divisão.
  - Rodapé analítico dinâmico (Entradas, Despesas, Saldo exibido).
- Validar renderização de listas extensas (500+ itens).

### Critérios de Aceite (Definition of Done)
- [ ] Rolagem fluida a 60 FPS com renderização exclusiva dos elementos em viewport.
- [ ] Filtros, busca e paginação funcionando perfeitamente sem quebras de layout.
- [ ] Build e testes do Vitest aprovados com 100% de sucesso.`,
  },
  {
    milestoneIndex: 1,
    title: '[Performance] [Android] Estabilidade do Compilador Compose (@Immutable / @Stable) nos Modelos de Dados',
    labels: ['scope: android', 'type: performance', 'priority: high'],
    body: `### Contexto & Objetivo
Garantir que as classes de modelo de dados sejam reconhecidas como imutáveis/estáveis pelo compilador do Jetpack Compose, prevenindo recomposições redundantes de telas inteiras no app móvel.

### Escopo de Implementação
- Auditar data classes em \`android/app/src/main/java/com/saldolivre/app/data/model/\` (\`Transaction\`, \`Card\`, \`MonthClosure\`, \`CategoryBudgetReport\`, etc.).
- Adicionar anotações \`@Immutable\` e \`@Stable\` onde aplicável.
- Garantir uso de coleções estáveis ou imutáveis em estados de ViewModel expostos à UI.

### Critérios de Aceite (Definition of Done)
- [ ] Recomposições desnecessárias de \`DashboardScreen\` e \`TransactionsScreen\` eliminadas.
- [ ] Testes unitários JVM do Android (\`testDebugUnitTest\`) 100% verdes.
- [ ] Compilação de Release aprovada com sucesso.`,
  },

  // Sprint 3
  {
    milestoneIndex: 2,
    title: '[Segurança] [Infra] Integração do Firebase App Check (reCAPTCHA Enterprise & Play Integrity)',
    labels: ['scope: backend', 'type: security', 'priority: high'],
    body: `### Contexto & Objetivo
Ativar e configurar o Firebase App Check para proteger os endpoints do Cloud Firestore e Cloud Functions contra requisições não autorizadas, scrapers e clientes forjados.

### Escopo de Implementação
- **Web:** Configurar App Check provider em \`web/src/lib/firebase.ts\` com \`ReCaptchaEnterpriseProvider\` ou \`ReCaptchaV3Provider\`.
- **Android:** Inicializar App Check no \`SaldoLivreApplication.kt\` utilizando \`PlayIntegrityAppCheckProviderFactory\` com fallback para provedor de debug local (\`DebugAppCheckProviderFactory\`).
- Configurar variáveis de ambiente e chaves de debug para CI/CD e testes locais.

### Critérios de Aceite (Definition of Done)
- [ ] Requisições com integridade atestada pelo App Check.
- [ ] Ambiente de desenvolvimento local e suíte de testes operando normalmente sem bloqueio.`,
  },
  {
    milestoneIndex: 2,
    title: '[Segurança] [AI Engine] Sanitização Estrita de Parâmetros de Function Calling e Blindagem contra Injection',
    labels: ['scope: multiplatform', 'type: security', 'priority: medium'],
    body: `### Contexto & Objetivo
Reforçar a validação defensiva de todos os parâmetros recebidos via Function Calling do Copiloto Gemini (Web e Android) e blindar o assistente contra prompt injections em extratos e descrições bancárias.

### Escopo de Implementação
- **Web (\`web/src/lib/agent/...\`):**
  - Sanitizar e validar tipos estritos em \`budgetTools.ts\`, \`transactionTools.ts\` e \`managementTools.ts\` (valores numéricos finitos $> 0$, limites de string, datas ISO válidas).
  - Escapar caracteres especiais em renderização de tabelas e gráficos inline.
- **Android (\`android/.../data/ai/...\`):**
  - Espelhar as validações defensivas em \`AndroidAgentToolsEngine.kt\`.
  - Tratamento elegante de exceções para ferramentas de IA com mensagens amigáveis ao usuário.

### Critérios de Aceite (Definition of Done)
- [ ] Bloqueio determinístico de valores inválidos ou parâmetros corrompidos.
- [ ] Testes unitários de ferramentas de IA cobrindo cenários de borda e entradas maliciosas.`,
  },
  {
    milestoneIndex: 2,
    title: '[Segurança] [Database] Auditoria e Hardening das Firestore Security Rules',
    labels: ['scope: backend', 'type: security', 'priority: high'],
    body: `### Contexto & Objetivo
Revisar e consolidar as regras de segurança do Cloud Firestore (\`firestore.rules\`), assegurando isolamento absoluto por \`request.auth.uid\` em todas as coleções e subcoleções privadas.

### Escopo de Implementação
- Auditar regras de \`/users/{userId}\`, \`/transactions/{txId}\`, \`/cards/{cardId}\`, \`/recurring_bills/{billId}\`.
- Confirmar isolamento estrito de subcoleções: \`activity_logs\`, \`month_closures\`, \`agent_rules\`, \`chat_sessions\`.
- Validar regras de rateio (amigo atribuído só pode atualizar \`acceptanceStatus\` e \`respondedAt\`).
- Executar os 15 testes de segurança do Firestore com \`@firebase/rules-unit-testing\` contra o emulador local.

### Critérios de Aceite (Definition of Done)
- [ ] Zero acessos não autenticados ou de leitura/escrita cruzada permitidos.
- [ ] \`npm run test:rules\` aprovado com 100% dos testes verdes.`,
  },
];

async function main() {
  console.log(`🚀 Iniciando criação do backlog no GitHub (${owner}/${repo})...\n`);

  // 1. Criar Labels
  console.log('📌 Criando/verificando Labels...');
  for (const label of labelsToCreate) {
    try {
      await api('/labels', {
        method: 'POST',
        body: JSON.stringify(label),
      });
      console.log(`  ✅ Label criada: ${label.name}`);
    } catch (e) {
      if (e.message && e.message.includes('already_exists')) {
        console.log(`  ℹ️ Label já existe: ${label.name}`);
      } else {
        console.warn(`  ⚠️ Alerta na label ${label.name}:`, e.message);
      }
    }
  }

  // 2. Criar Milestones
  console.log('\n🎯 Criando Milestones (Sprints)...');
  const createdMilestones = [];
  for (const m of milestonesToCreate) {
    try {
      const res = await api('/milestones', {
        method: 'POST',
        body: JSON.stringify(m),
      });
      createdMilestones.push(res);
      console.log(`  ✅ Milestone criada: ${m.title} (#${res.number})`);
    } catch (e) {
      console.warn(`  ⚠️ Alerta na milestone ${m.title}:`, e.message);
      // Tenta buscar se já existe
      const existing = await api('/milestones?state=all').catch(() => []);
      const found = existing.find(ex => ex.title === m.title);
      createdMilestones.push(found || null);
    }
  }

  // 3. Criar Issues
  console.log('\n📋 Criando Issues estruturadas...');
  for (const issue of issuesToCreate) {
    const milestoneObj = createdMilestones[issue.milestoneIndex];
    const payload = {
      title: issue.title,
      body: issue.body,
      labels: issue.labels,
      milestone: milestoneObj ? milestoneObj.number : undefined,
    };

    try {
      const res = await api('/issues', {
        method: 'POST',
        body: JSON.stringify(payload),
      });
      console.log(`  ✅ Issue criada: #${res.number} — ${res.title}`);
    } catch (e) {
      console.error(`  ❌ Erro ao criar issue "${issue.title}":`, e.message);
    }
  }

  console.log('\n🎉 Backlog completo criado com sucesso no GitHub!');
}

main().catch(err => {
  console.error('Falha geral:', err);
  process.exit(1);
});
