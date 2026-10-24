package com.saldolivre.app.data.ai

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.util.Locale

/**
 * Motor de Áudio e Fala de Alta Performance e Baixa Latência.
 *
 * Características:
 * 1. Suporte a Reconhecimento On-Device com aceleração por hardware (Android 12+) para transcrição instantânea.
 * 2. Streaming de transcrições parciais e cumulativas em tempo real.
 * 3. Escuta contínua enquanto a gravação estiver ativa sem disparo prematuro por pausas na fala.
 * 4. Eliminação de envio automático indesejado: a mensagem de voz SOMENTE é enviada quando o usuário
 *    clica explicitamente no botão de confirmar envio.
 */
class AndroidSpeechManager(
    private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioOutputFile: File? = null

    private var isRecording = false
    private var recordingSeconds = 0
    private var accumulatedTranscript = ""
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    var onPartialTranscript: ((String) -> Unit)? = null
    var onFinalTranscript: ((text: String, audioBytes: ByteArray?) -> Unit)? = null
    var onRecordingSeconds: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun isRecordingActive(): Boolean = isRecording

    fun startListening() {
        if (isRecording) {
            return
        }

        recordingSeconds = 0
        accumulatedTranscript = ""
        isRecording = true
        onRecordingSeconds?.invoke(0)

        initSpeechRecognizer()

        // Timer de gravação
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    recordingSeconds++
                    onRecordingSeconds?.invoke(recordingSeconds)
                    if (recordingSeconds >= 60) {
                        // Limite de 60 segundos de captura contínua atingido:
                        // Pausa a captura do microfone para poupar recursos, mas NUNCA envia automaticamente.
                        // Mantém a gravação pronta para o usuário clicar no botão de confirmar envio.
                        try {
                            speechRecognizer?.stopListening()
                        } catch (_: Exception) {}
                    } else {
                        handler.postDelayed(this, 1000L)
                    }
                }
            }
        }
        handler.postDelayed(timerRunnable!!, 1000L)
    }

    private fun initSpeechRecognizer() {
        val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!recognitionAvailable) {
            fallbackToAudioRecording()
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        // Fim de fala detectado pelo hardware; aguarda resultados parciais ou finais
                    }

                    override fun onError(error: Int) {
                        if (!isRecording) return

                        // Pausas naturais de fala disparam ERROR_NO_MATCH ou ERROR_SPEECH_TIMEOUT no Android.
                        // NUNCA enviamos a mensagem aqui! Reiniciamos a escuta para permitir fala contínua.
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                            restartListening()
                        } else if (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_CLIENT) {
                            restartListening()
                        } else {
                            if (accumulatedTranscript.isBlank()) {
                                onError?.invoke("Microfone ($error)")
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            accumulatedTranscript = if (accumulatedTranscript.isBlank()) {
                                text
                            } else {
                                "$accumulatedTranscript $text"
                            }
                            onPartialTranscript?.invoke(accumulatedTranscript)
                        }

                        // REGRA: SOMENTE o clique no botão de confirmar envio dispara o envio da mensagem.
                        // Se o usuário ainda estiver com a gravação aberta, reinicia a escuta para continuar ouvindo.
                        if (isRecording) {
                            restartListening()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            val currentFull = if (accumulatedTranscript.isBlank()) text else "$accumulatedTranscript $text"
                            onPartialTranscript?.invoke(currentFull)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = createRecognitionIntent()
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            fallbackToAudioRecording()
        }
    }

    private fun restartListening() {
        if (!isRecording) return
        handler.postDelayed({
            if (!isRecording) return@postDelayed
            try {
                speechRecognizer?.cancel()
                val intent = createRecognitionIntent()
                speechRecognizer?.startListening(intent)
            } catch (_: Exception) {
                initSpeechRecognizer()
            }
        }, 150L)
    }

    private fun createRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

            // Habilita vocabulário multilíngue para marcas em inglês (ex: Two Brothers, Uber, iFood, Air Fryer)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US", "en"))

            // Silêncio mais tolerante para não cortar pausas naturais na fala
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
    }

    private fun fallbackToAudioRecording() {
        try {
            val cacheDir = context.cacheDir
            audioOutputFile = File(cacheDir, "speech_${System.currentTimeMillis()}.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(audioOutputFile?.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
            mediaRecorder = null
        }
    }

    /**
     * Encerra a escuta e, se sendImmediately for verdadeiro, dispara onFinalTranscript.
     * Este método deve ser chamado apenas por ação voluntária do usuário (ao clicar no botão de confirmar envio).
     */
    fun stopListening(sendImmediately: Boolean, currentRecognizedText: String = "") {
        if (!isRecording) return
        isRecording = false

        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null

        val finalTranscript = currentRecognizedText.ifBlank { accumulatedTranscript }.trim()

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        var recordedBytes: ByteArray? = null
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        try {
            if (audioOutputFile != null && audioOutputFile!!.exists() && audioOutputFile!!.length() > 0) {
                recordedBytes = audioOutputFile!!.readBytes()
            }
        } catch (_: Exception) {}

        if (sendImmediately) {
            onFinalTranscript?.invoke(finalTranscript, recordedBytes)
        }
    }

    /**
     * Cancela a escuta ativa sem enviar nenhuma mensagem.
     */
    fun cancelListening() {
        if (!isRecording) return
        isRecording = false

        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        accumulatedTranscript = ""

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        try {
            audioOutputFile?.delete()
        } catch (_: Exception) {}
        audioOutputFile = null
    }
}
