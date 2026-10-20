---
name: react-rules
description: Regras e padrões de desenvolvimento para a aplicação Web (React + Tailwind CSS)
trigger:
  type: glob
  patterns:
    - "web/**/*.js"
    - "web/**/*.jsx"
    - "web/**/*.ts"
    - "web/**/*.tsx"
---

# Diretrizes de Desenvolvimento Web (React + Tailwind CSS)

Ao desenvolver, manter ou refatorar código no diretório `/web`, você deve seguir rigorosamente as diretrizes abaixo:

## 1. Componentes Funcionais Exclusivos
- Todos os componentes React devem ser **componentes funcionais**.
- É expressamente proibido o uso de componentes baseados em classes (`class Component extends React.Component`).
- Gerencie estados e efeitos colaterais utilizando **React Hooks** (`useState`, `useEffect`, `useMemo`, `useCallback`, `useContext` ou hooks personalizados).
- Tipagem rigorosa com **TypeScript** para todas as props, retornos de funções e referências (`interface` ou `type`).

## 2. Estilização Exclusiva com Tailwind CSS
- Toda e qualquer estilização visual deve ser implementada via **classes utilitárias do Tailwind CSS**.
- **Não** utilize arquivos CSS externos adicionais, CSS Modules ad-hoc ou styled-components.
- Evite atributos inline (`style={{ ... }}`), exceto quando estritamente indispensável para valores dinâmicos de runtime (ex.: posições calculadas em pixels).
- Adote as classes de layout responsivo do Tailwind (`sm:`, `md:`, `lg:`, `xl:`), suporte a temas (dark mode) e paletas consistentes.

## 3. Padrões de Código e Arquitetura
- Mantenha componentes coesos, desacoplados e de responsabilidade única.
- Separe componentes de apresentação de lógica de negócios e chamadas à API / banco de dados (Firebase / Supabase).
