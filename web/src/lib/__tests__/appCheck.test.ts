// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { initFirebaseAppCheck } from '../firebase'
import type { FirebaseApp } from 'firebase/app'

describe('Firebase App Check (Web Integration)', () => {
  const dummyApp = {
    name: '[DEFAULT]',
    options: {
      projectId: 'saldo-livre-c3ca3',
      apiKey: 'test-api-key',
    },
  } as unknown as FirebaseApp

  beforeEach(() => {
    vi.restoreAllMocks()
    delete (globalThis as unknown as { FIREBASE_APPCHECK_DEBUG_TOKEN?: boolean | string }).FIREBASE_APPCHECK_DEBUG_TOKEN
  })

  it('não quebra nem lança erro quando as chaves não estão configuradas (fallback gracioso)', () => {
    const result = initFirebaseAppCheck(dummyApp)
    expect(result).toBeDefined()
  })

  it('injeta token de debug em ambiente de desenvolvimento quando configurado como "true"', () => {
    import.meta.env.VITE_FIREBASE_APPCHECK_DEBUG = 'true'
    import.meta.env.VITE_FIREBASE_APPCHECK_DEBUG_TOKEN = 'mock-debug-token-12345'

    initFirebaseAppCheck(dummyApp)

    const debugToken = (globalThis as unknown as { FIREBASE_APPCHECK_DEBUG_TOKEN?: boolean | string }).FIREBASE_APPCHECK_DEBUG_TOKEN
    expect(debugToken).toBe('mock-debug-token-12345')
  })

  it('não ativa token de debug em produção quando VITE_FIREBASE_APPCHECK_DEBUG for "false"', () => {
    const prevDev = import.meta.env.DEV
    import.meta.env.DEV = false
    import.meta.env.VITE_FIREBASE_APPCHECK_DEBUG = 'false'
    import.meta.env.VITE_FIREBASE_APPCHECK_DEBUG_TOKEN = 'should-not-be-used'

    initFirebaseAppCheck(dummyApp)

    const debugToken = (globalThis as unknown as { FIREBASE_APPCHECK_DEBUG_TOKEN?: boolean | string }).FIREBASE_APPCHECK_DEBUG_TOKEN
    expect(debugToken).toBeUndefined()
    import.meta.env.DEV = prevDev
  })
})
