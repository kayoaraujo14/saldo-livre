import { onSchedule } from 'firebase-functions/v2/scheduler'
import { onRequest } from 'firebase-functions/v2/https'
import * as logger from 'firebase-functions/logger'
import * as admin from 'firebase-admin'

if (!admin.apps.length) {
  admin.initializeApp()
}

const db = admin.firestore()

/**
 * Retorna a chave do mês no formato YYYY-MM
 */
function getMonthKey(date: Date = new Date()): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}`
}

/**
 * 1. Cron de Autolançamento de Contas Recorrentes
 * Executado todo dia 1º às 03:05 UTC (00:05 Horário de Brasília)
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
        const bill = billDoc.data()
        const billId = billDoc.id
        const ownerUid = bill.ownerUid

        if (!ownerUid) continue

        // Verifica se já existe lançamento dessa conta recorrente neste mês
        const existingTx = await db
          .collection('transactions')
          .where('recurringBillId', '==', billId)
          .where('month', '==', currentMonth)
          .limit(1)
          .get()

        if (!existingTx.empty) {
          skippedCount++
          continue
        }

        const dueDay = Math.min(Math.max(1, Number(bill.dueDay) || 1), 31)
        const dueDayPadded = String(dueDay).padStart(2, '0')
        const txDate = `${currentMonth}-${dueDayPadded}`

        const newTx = {
          description: bill.title || bill.description || 'Conta Recorrente',
          amount: Number(bill.amount) || 0,
          type: bill.type || 'expense',
          category: bill.category || 'Outros',
          subcategory: bill.subcategory || null,
          paymentMethod: bill.paymentMethod || 'bill',
          status: 'pending',
          date: txDate,
          dueDate: txDate,
          month: currentMonth,
          createdByUid: ownerUid,
          paidByUid: ownerUid,
          visibleToUids: Array.isArray(bill.visibleToUids) && bill.visibleToUids.length > 0
            ? bill.visibleToUids
            : [ownerUid],
          splitType: bill.splitType || 'mine',
          recurringBillId: billId,
          cardId: bill.cardId || null,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          isAutoLaunched: true,
        }

        await db.collection('transactions').add(newTx)
        launchedCount++
      }

      logger.info(
        `Autolançamento concluído com sucesso: ${launchedCount} lançadas, ${skippedCount} já existentes.`
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

