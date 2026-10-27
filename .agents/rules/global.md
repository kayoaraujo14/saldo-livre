**[REGRAS GLOBAIS DE EXECUÇÃO]**
- **ISOLAMENTO DE CONTEXTO**: Respeite rigorosamente os gatilhos das regras locais (`react.md` e `kotlin.md`). Nunca carregue arquivos da pasta `/android` para resolver problemas da pasta `/web` e vice-versa.
- **ARTEFATOS ANTES DE CÓDIGO**: Nunca inicie refatorações massivas gerando código diretamente. Primeiro, analise a base e gere um artefato Markdown de planejamento. Entregue o plano e PAUSE, aguardando minha aprovação.
- **MICRO-COMMITS**: Durante a execução de um plano, resolva apenas UM item por vez. Aplique a alteração, faça o build e PAUSE aguardando minha liberação antes de ir para o próximo passo.
- **VISÃO FULL-STACK**: Ao criar fluxos compartilhados (ex: integrando o mesmo banco de dados), centralize a análise arquitetural para me dar o contexto geral, mas mantenha a execução do código estritamente isolada por plataforma.
