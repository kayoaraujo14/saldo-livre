---
name: firebase-firestore
description: >-
  Sets up, manages, queries, and configures Cloud Firestore databases (Standard edition), including data modeling, security rules, indexes, and SDK integrations (Web and Android). Use when creating/listing Firestore databases, defining data models/indexes, writing SDK queries, or integrating Firestore SDKs. For authoring or modifying Firestore Security Rules (firestore.rules), delegate to the firestore-rules-author subagent if subagent delegation is available, or use firestore-rules-creation otherwise. Don't use for Firebase Hosting, Data Connect, Auth, Storage/GCS, Crashlytics, Functions, or BigQuery.
compatibility: This skill is best used with the Firebase CLI, but does not require it. Firebase CLI can be accessed through `npx -y firebase-tools@latest`.
metadata:
  author: Google LLC
  category: Databases
---

# Cloud Firestore Database and Operations

> [!IMPORTANT] **Security Rules Authoring (`firestore.rules`)** Whenever your
> task requires creating, authoring, or modifying `firestore.rules`:
>
> - **If subagent delegation AND the `firestore-rules-author` subagent are
>   available**: delegate authoring `firestore.rules` to the
>   `firestore-rules-author` subagent rather than writing `firestore.rules`
>   directly in the main agent.
> - **If subagent delegation is unavailable** (e.g., subagents not enabled in
>   the IDE) **OR `firestore-rules-author` is not installed**: read and follow
>   the `firestore-rules-creation` skill to write `firestore.rules` directly.

Before setting up dependencies, writing data models, or configuring security
Este projeto (Saldo Livre) usa **Firestore Standard Edition** com SDKs Web
(TypeScript) e Android (Kotlin). Somente as referências `references/standard/`
são mantidas.

## 1. Instance Selection

Liste e inspecione os bancos existentes:

```bash
npx -y firebase-tools@latest firestore:databases:list
npx -y firebase-tools@latest firestore:databases:get <database-id>
```

Se nenhum banco existir, pergunte a localização (`firestore:locations`) e crie
um banco **Standard**:

```bash
npx -y firebase-tools@latest firestore:databases:create <database-id> --edition="standard" --location="<selected-location>"
```

______________________________________________________________________

## 2. Specialized Guides (`references/standard/`)

- **Provisioning**: Read [provisioning.md](references/standard/provisioning.md)
- **Security Rules**: See the `firestore-rules-creation` skill
- **SDK Usage**: Read [web_sdk_usage.md](references/standard/web_sdk_usage.md)
  or [android_sdk_usage.md](references/standard/android_sdk_usage.md)
- **Indexes**: Read [indexes.md](references/standard/indexes.md)
