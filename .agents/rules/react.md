---
name: react-rules
description: Regras para Web (React + TypeScript + Tailwind) e Backend
trigger:
  type: glob
  patterns:
    - "web/**/*"
    - "backend/**/*"
---

# Diretrizes Web e Backend

- **Tipagem estrita:** TypeScript com `strict`; é proibido usar `any` (use `unknown` + narrowing, generics ou tipos de `web/src/types/index.ts`).
- **Componentes:** apenas funcionais com Hooks. Reutilize os primitivos de `web/src/components/ui/`.
- **Estilo:** somente Tailwind CSS, responsivo (`sm:`/`md:`/`lg:`), com paridade entre a visão Desktop e os componentes de `web/src/components/mobile/`. Sem CSS externo/styled-components.
- **Estado:** gerenciado via `FinanceContext.tsx`, mantendo compatibilidade total com o modo demonstração (`web/src/store/demoState.ts` e `web/src/lib/demoAgent.ts`).
- **Paridade:** lógica financeira e ferramentas Gemini devem ser espelhadas no Android (ver `global.md`).
- **UI:** ao alterar `web/src/components/**`, aplique a skill `ui-ux-validator`.
- **Verificação obrigatória:** ao finalizar alterações em `web/`, execute o linter Oxlint (`npm run lint`, config `web/.oxlintrc.json`) e os testes Vitest (`npm test`, `web/src/lib/__tests__/`) e garanta sucesso total.
