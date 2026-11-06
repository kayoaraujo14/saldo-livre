---
name: firestore-contract-sync
description: Sincroniza o contrato de dados do Firestore entre Web, Android e regras de segurança. Use sempre que adicionar coleções, campos de documentos, queries compostas ou alterar permissões de leitura/escrita.
---

# Sincronia de Contrato Firestore

## Checklist de Sincronização
1. Atualizar interfaces em `web/src/types/index.ts` (e `web/src/store/types.ts` se afetar o store).
2. Atualizar data classes e mapeamentos em `android/app/src/main/java/com/saldolivre/app/data/repository/FirestoreFinanceRepository.kt` (e modelos em `data/model/`).
3. Atualizar regras de segurança em `firestore.rules` (delegue à skill `firestore-rules-creation`/subagente `firestore-rules-author`) e, se houver query composta, `firestore.indexes.json`.
4. Validar as regras executando `web/src/lib/__tests__/firestore-rules.test.ts` (`npm run test:rules` em `web/`).

## Lembretes
- Dados sempre sob `/users/{uid}/...` com isolamento por `uid`.
- Atualize também mocks/demo: `web/src/lib/mockData.ts`, `web/src/store/demoState.ts`, `MockFinanceRepository.kt`, `FakeFinanceRepository.kt`.
