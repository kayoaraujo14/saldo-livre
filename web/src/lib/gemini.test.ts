import { describe, it, expect } from 'vitest'
import { formatGeminiErrorMessage } from './gemini'

describe('formatGeminiErrorMessage', () => {
  it('deve retornar mensagem genérica quando err for null/undefined/vazio', () => {
    expect(formatGeminiErrorMessage(null)).toBe('Erro desconhecido ao comunicar com a inteligência artificial.')
    expect(formatGeminiErrorMessage(undefined)).toBe('Erro desconhecido ao comunicar com a inteligência artificial.')
    expect(formatGeminiErrorMessage('')).toBe('Erro desconhecido ao comunicar com a inteligência artificial.')
  })

  it('deve lidar com instâncias de Error genéricas e recuperar apenas a message original se não casar com as regras conhecidas', () => {
    const error = new Error('Some random error occurred')
    expect(formatGeminiErrorMessage(error)).toBe('Some random error occurred')
  })

  describe('Parseando JSON Error Object', () => {
    it('deve capturar falhas de alta demanda / 503 com código numérico ou de status', () => {
      const err503Code = new Error(JSON.stringify({ error: { code: 503 } }))
      const errUnavailableStatus = new Error(JSON.stringify({ error: { status: 'UNAVAILABLE' } }))

      const expectedMessage = 'O modelo do Gemini está enfrentando alta demanda temporária nos servidores do Google (503). Por favor, aguarde alguns segundos e tente novamente.'

      expect(formatGeminiErrorMessage(err503Code)).toBe(expectedMessage)
      expect(formatGeminiErrorMessage(errUnavailableStatus)).toBe(expectedMessage)
    })

    it('deve capturar falhas de alta demanda interpretando mensagens específicas no JSON', () => {
      const errHighDemandMessage = new Error(JSON.stringify({ error: { message: 'High Demand on servers.' } }))
      const errSpikesMessage = new Error(JSON.stringify({ error: { message: 'Server is experiencing spikes in demand' } }))

      const expectedMessage = 'O modelo do Gemini está enfrentando alta demanda temporária nos servidores do Google (503). Por favor, aguarde alguns segundos e tente novamente.'

      expect(formatGeminiErrorMessage(errHighDemandMessage)).toBe(expectedMessage)
      expect(formatGeminiErrorMessage(errSpikesMessage)).toBe(expectedMessage)
    })

    it('deve capturar limites de requisição atingidos / 429 com código numérico ou de status', () => {
      const err429Code = new Error(JSON.stringify({ error: { code: 429 } }))
      const errResourceExhaustedStatus = new Error(JSON.stringify({ error: { status: 'RESOURCE_EXHAUSTED' } }))

      const expectedMessage = 'Limite de requisições por minuto (RPM) atingido temporariamente no Google AI Studio. Aguarde alguns segundos.'

      expect(formatGeminiErrorMessage(err429Code)).toBe(expectedMessage)
      expect(formatGeminiErrorMessage(errResourceExhaustedStatus)).toBe(expectedMessage)
    })

    it('deve retornar a mensagem enviada no campo message do JSON caso não bata com alta demanda ou limit (429/503)', () => {
      const errCustom = new Error(JSON.stringify({ error: { code: 400, message: 'Invalid API key provided.' } }))
      expect(formatGeminiErrorMessage(errCustom)).toBe('Invalid API key provided.')
    })

    it('deve extrair a propriedade message caso o JSON já seja o próprio objeto error e não tenha o wrapper .error', () => {
       const errCustomDirect = new Error(JSON.stringify({ code: 404, message: 'Model not found.' }))
       expect(formatGeminiErrorMessage(errCustomDirect)).toBe('Model not found.')
    })
  })

  describe('Fallback via Text Catch (Quando não é um JSON válido)', () => {
    it('deve capturar erro 503 / demanda em strings de erros mal formatadas', () => {
       const str503 = 'Error: API returned 503'
       const strUnavailable = 'SERVICE_UNAVAILABLE - The service is currently down'
       const strHighDemand = 'high demand detected'
       const strSpikes = 'we are seeing spikes in demand'

       const expectedMessage = 'O modelo do Gemini está enfrentando alta demanda temporária nos servidores do Google (503). Por favor, aguarde alguns segundos e tente novamente.'

       expect(formatGeminiErrorMessage(str503)).toBe(expectedMessage)
       expect(formatGeminiErrorMessage(strUnavailable)).toBe(expectedMessage)
       expect(formatGeminiErrorMessage(strHighDemand)).toBe(expectedMessage)
       expect(formatGeminiErrorMessage(strSpikes)).toBe(expectedMessage)
    })

    it('deve capturar erro 429 / limite na string de fallback', () => {
      const str429 = 'Error HTTP 429 received from Google.'
      const strResourceExhausted = 'Exception: RESOURCE_EXHAUSTED'

      const expectedMessage = 'Limite de requisições temporariamente atingido no Google AI Studio. Aguarde alguns instantes.'

      expect(formatGeminiErrorMessage(str429)).toBe(expectedMessage)
      expect(formatGeminiErrorMessage(strResourceExhausted)).toBe(expectedMessage)
    })
  })
})
