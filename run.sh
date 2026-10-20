#!/usr/bin/env bash
set -e

# ==============================================================================
# Monorepo Orchestration Script (Web + Android)
# Inicia o servidor web em background e instala o app Android simultaneamente
# ==============================================================================

echo "🚀 Iniciando ambiente Monorepo..."

# 1. Iniciar servidor de desenvolvimento Web em background
echo "🌐 Iniciando frontend web (/web)..."
(
  cd web
  npm run dev
) &
WEB_PID=$!

# Encerra processos em background caso o script seja interrompido (Ctrl+C)
cleanup() {
  echo ""
  echo "🛑 Encerrando processos em execução..."
  kill "$WEB_PID" 2>/dev/null || true
  exit 0
}
trap cleanup SIGINT SIGTERM

# 2. Executar build e instalação do app Android
echo "📱 Executando build e instalação do aplicativo Android (/android)..."
(
  cd android
  ./gradlew installDebug
)

# 3. Aguardar processos em background
echo "✅ Processo de build Android concluído."
echo "⏳ Servidor Web ativo em background. Pressione Ctrl+C para finalizar."
wait
