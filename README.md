# Saldo Livre — Controle Financeiro Pessoal e Compartilhado 🇧🇷

Um aplicativo web moderno, completo e em tempo real para **Gestão Financeira Pessoal e Compartilhada**, construído com **React**, **TypeScript**, **Tailwind CSS v4**, **Lucide Icons**, **Radix UI / shadcn**, **Firebase (Auth com Google SSO + Cloud Firestore)** e a **API do Google Gemini (Function Calling)**.

---

## 🚀 Como Executar Localmente

### 1. Pré-requisitos
- Node.js 18+ instalado.

### 2. Instalação e Execução
```bash
# Navegar até o diretório do projeto
cd "C:\Users\Kayo Araújo\.gemini\antigravity-ide\scratch\saldo-livre"

# Instalar dependências (já instaladas)
npm install

# Iniciar servidor de desenvolvimento
npm run dev

# Compilar build de produção
npm run build
```

O aplicativo estará disponível em: [http://localhost:5173](http://localhost:5173).

---

## ⚙️ Variáveis de Ambiente (`.env`)

Crie ou edite o arquivo `.env` na raiz do projeto (`saldo-livre/.env`):

```env
# Google Gemini API Key (Opcional no início — você pode configurar direto na UI pelo ícone 🔑)
VITE_GEMINI_API_KEY=AIzaSy...

# Firebase Web App Config (Opcional — sem estas variáveis o app entra no Modo Demo completo)
VITE_FIREBASE_API_KEY=AIzaSy...
VITE_FIREBASE_AUTH_DOMAIN=seu-app.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=seu-app
VITE_FIREBASE_STORAGE_BUCKET=seu-app.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=1234567890
VITE_FIREBASE_APP_ID=1:1234567890:web:abcdef
```

> **Nota:** Caso as variáveis do Firebase não estejam preenchidas, o aplicativo ativa o **Modo Demo com Mock Data realista** e persistência local (`localStorage`), permitindo alternar instantaneamente entre a visão de dois parceiros ("Kayo" e "Marina") para validar o espelhamento em tempo real.

---

## 🧠 Matemática Financeira & Regras de Negócio

### 1. Valor Efetivo (`actualAmount ?? estimatedAmount`)
- Contas recorrentes ou previsões de faturas iniciam como `status === 'estimated'` com `estimatedAmount`.
- Ao chegar a conta/boleto real, preenche-se `actualAmount` e o status passa para `pending` (A Pagar) ou `paid` (Pago).
- A tabela destaca tags comparando com a estimativa (`+R$ 25 vs estimado` ou `-R$ 12 economizado`).

### 2. Cards de KPI do Topo
- **Saldo Livre Real do Mês**: `Receitas do Mês - (Já Pago + Comprometido)`.
- **Disponível por Dia (Limite Diário Seguro)**: `Saldo Livre Real do Mês / Dias Restantes no Mês`.
- **Comprometido a Pagar**: Mostra a soma das despesas pendentes, detalhando o que é previsão 🟡 e o que já é boleto/fatura real 🟠.
- **Já Pago no Mês**: Total liquidado 🟢 que já impactou o caixa do usuário.
- **A Receber (Cartão / Terceiros)**: Valores pagos pelo titular no seu cartão para terceiros externos ou parceiro vinculado (`reimbursed === false`), com modal rápido para dar baixa.

### 3. Divisões e Cartões Compartilhados
- **👤 100% Meu**: Impacta 100% o Saldo Livre do usuário logado.
- **🤝 50/50 (Casal)**: Divide 50% para cada usuário vinculado.
- **🔄 100% Parceiro(a)**: Lançado no cartão do titular sem impactar seu saldo (neutro), gerando 100% em "A Receber".
- **🏷️ Terceiro Externo (Mãe, Irmão, etc.)**: Impacta R$ 0 no saldo do titular (não suja categorias de gastos pessoais) e gera 100% a receber.
- **🛡️ Reserva de Montante em Cartão**: Linha estimada anti-duplicidade (ex: R$ 500 para uso do cartão do parceiro). Conforme compras reais forem lançadas, abate da estimativa restante.

---

## 🤖 Agente IA Gemini (gemini-2.5-flash com Function Calling)

O Agente lateral no chat dispõe de 3 ferramentas automatizadas:
1. `registrarTransacao`: Registra novas receitas ou despesas com divisões automáticas (`allocations`, `userImpact`, `visibleToUids`).
2. `atualizarContaPrevista`: Localiza contas em aberto por busca fonética/sinônimos (ex: "luz", "água", "financiamento"), preenche o valor real e recalcula o Saldo Livre e limite diário.
3. `obterRelatorioEProjecaoMensal`: Gera projeções orçamentárias detalhadas em tabelas Markdown com recomendações de economia.
