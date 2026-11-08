import { onSchedule } from 'firebase-functions/v2/scheduler'
import { onRequest } from 'firebase-functions/v2/https'
import * as logger from 'firebase-functions/logger'
import * as admin from 'firebase-admin'
import {
  getMonthKey,
  shiftMonth,
  isBillEligibleForMonth,
  buildAutoLaunchTransaction,
  type RecurringBillData,
} from './recurringBills.js'

if (!admin.apps.length) {
  admin.initializeApp()
}

const db = admin.firestore()

/**
 * 1. Cron de Autolançamento de Contas Recorrentes
 * Executado todo dia 1º às 03:05 UTC (00:05 Horário de Brasília)
 * Garante paridade contratual estrita com o motor financeiro e idempotência absoluta.
 */
export const autoLaunchRecurringBillsScheduled = onSchedule(
  {
    schedule: '5 3 1 * *',
    timeZone: 'America/Sao_Paulo',
    memory: '256MiB',
    maxInstances: 1,
  },
  async () => {
    const currentMonth = getMonthKey()
    logger.info(`Iniciando autolançamento de contas recorrentes para o mês: ${currentMonth}`)

    try {
      const billsSnapshot = await db
        .collection('recurring_bills')
        .where('active', '==', true)
        .get()

      if (billsSnapshot.empty) {
        logger.info('Nenhum molde de conta recorrente ativo encontrado.')
        return
      }

      let launchedCount = 0
      let skippedCount = 0

      for (const billDoc of billsSnapshot.docs) {
        const bill = billDoc.data() as RecurringBillData
        const billId = billDoc.id
        const ownerUid = bill.ownerUid

        if (!ownerUid) continue

        // 1. Verifica elegibilidade temporal da recorrência (startMonth e endMonth)
        if (!isBillEligibleForMonth(bill, currentMonth)) {
          skippedCount++
          continue
        }

        // 2. Verifica idempotência: já existe lançamento dessa conta recorrente neste mês?
        // Consulta simultânea por competenceMonth (canônico) e month (legado) para blindagem total contra duplicações
        const [existingByCompetence, existingByMonth] = await Promise.all([
          db
            .collection('transactions')
            .where('recurringBillId', '==', billId)
            .where('competenceMonth', '==', currentMonth)
            .limit(1)
            .get(),
          db
            .collection('transactions')
            .where('recurringBillId', '==', billId)
            .where('month', '==', currentMonth)
            .limit(1)
            .get(),
        ])

        if (!existingByCompetence.empty || !existingByMonth.empty) {
          skippedCount++
          continue
        }

        // 3. Resolução de valor pela regra last_month se aplicável
        let prevTxAmount: number | null = null
        if (bill.estimationRule === 'last_month') {
          const prevMonth = shiftMonth(currentMonth, -1)
          const prevTxSnap = await db
            .collection('transactions')
            .where('recurringBillId', '==', billId)
            .where('competenceMonth', '==', prevMonth)
            .limit(1)
            .get()

          if (!prevTxSnap.empty) {
            const prevData = prevTxSnap.docs[0].data()
            prevTxAmount =
              Number(prevData.actualAmount ?? prevData.estimatedAmount ?? prevData.totalAmount ?? prevData.amount) ||
              null
          }
        }

        // 4. Constrói o payload completo em total conformidade com o contrato canônico
        const txPayload = buildAutoLaunchTransaction({
          bill,
          billId,
          currentMonth,
          prevTxAmount,
        })

        const newTx = {
          ...txPayload,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        }

        await db.collection('transactions').add(newTx)
        launchedCount++
      }

      logger.info(
        `Autolançamento concluído com sucesso: ${launchedCount} lançadas, ${skippedCount} ignoradas/já existentes.`
      )
    } catch (error) {
      logger.error('Erro crítico no processamento de contas recorrentes:', error)
      throw error
    }
  }
)

/**
 * 2. Backup Diário Automatizado do Firestore (Disaster Recovery)
 * Disparado diariamente às 02:00 UTC (23:00 BRT)
 */
export const backupFirestoreScheduled = onSchedule(
  {
    schedule: '0 2 * * *',
    timeZone: 'America/Sao_Paulo',
    memory: '256MiB',
    maxInstances: 1,
  },
  async () => {
    logger.info('Iniciando rotina diária de backup e telemetria do Firestore...')
    try {
      const projectId =
        process.env.GCLOUD_PROJECT ||
        process.env.GCP_PROJECT ||
        admin.app().options.projectId ||
        'saldo-livre-c3ca3'

      const rawBucket = process.env.BACKUP_STORAGE_BUCKET || `gs://${projectId}-backups`
      const bucketName = rawBucket.startsWith('gs://') ? rawBucket : `gs://${rawBucket}`

      const firestoreAdmin = new (admin.firestore as any).v1.FirestoreAdminClient()
      const databaseName = firestoreAdmin.databasePath(projectId, '(default)')

      logger.info(`Disparando exportação para database: ${databaseName} no bucket: ${bucketName}`)

      const [operation] = await firestoreAdmin.exportDocuments({
        name: databaseName,
        outputUriPrefix: bucketName,
        collectionIds: [], // Coleções vazias exportam todas as coleções
      })

      logger.info(`Backup gerenciado do Firestore iniciado com sucesso. Operação: ${operation?.name || 'iniciada'}`)
    } catch (error) {
      logger.error('Falha crítica na rotina de backup agendado do Firestore:', error)
      throw error
    }
  }
)

/**
 * 3. Limpeza de Logs e Sessões Efêmeras (TTL / Data Retention)
 * Executado semanalmente aos domingos às 04:00 UTC
 */
export const cleanupEphemeralLogsScheduled = onSchedule(
  {
    schedule: '0 4 * * 0',
    timeZone: 'America/Sao_Paulo',
    memory: '256MiB',
    maxInstances: 1,
  },
  async () => {
    logger.info('Iniciando expurgo periódico de sessões efêmeras e logs antigos...')
    try {
      const cutoffDate = new Date()
      cutoffDate.setDate(cutoffDate.getDate() - 180) // 180 dias de retenção

      const oldLogs = await db
        .collectionGroup('activity_logs')
        .where('timestamp', '<', cutoffDate)
        .limit(200)
        .get()

      if (!oldLogs.empty) {
        const batch = db.batch()
        oldLogs.docs.forEach((doc) => batch.delete(doc.ref))
        await batch.commit()
        logger.info(`Expurgo finalizado: ${oldLogs.size} logs arquivados deletados.`)
      } else {
        logger.info('Nenhum log excedeu o limite de retenção de 180 dias.')
      }
    } catch (error) {
      logger.warn('Aviso durante expurgo de logs efêmeros:', error)
    }
  }
)

/**
 * 4. Endpoint HTTP de Health Check e Monitoramento de Uptime
 */
export const healthCheck = onRequest((req, res) => {
  res.status(200).json({
    status: 'healthy',
    service: 'saldo-livre-backend',
    version: '1.0.0',
    timestamp: new Date().toISOString(),
    uptime: process.uptime(),
  })
})
