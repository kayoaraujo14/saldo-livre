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

class AndroidSpeechManager(
    private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioOutputFile: File? = null

    private var isRecording = false
    private var recordingSeconds = 0
    private var handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    var onPartialTranscript: ((String) -> Unit)? = null
    var onFinalTranscript: ((text: String, audioBytes: ByteArray?) -> Unit)? = null
    var onRecordingSeconds: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun isRecordingActive(): Boolean = isRecording

    fun startListening() {
        if (isRecording) {
            stopListening(sendImmediately = true)
            return
        }

        recordingSeconds = 0
        isRecording = true
        onRecordingSeconds?.invoke(0)

        // 1. Inicia o MediaRecorder em segundo plano para capturar o áudio real
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
        } catch (e: Exception) {
            mediaRecorder = null
        }

        // 2. Inicia o SpeechRecognizer nativo do Android
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            // Erros de SpeechRecognizer não interrompem o gravador de áudio
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                onPartialTranscript?.invoke(text)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                onPartialTranscript?.invoke(text)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
            }
        } catch (e: Exception) {
            // Se falhar o speech recognizer, o gravador de mídia garante a entrada
        }

        // 3. Inicia contador de tempo
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    recordingSeconds++
                    onRecordingSeconds?.invoke(recordingSeconds)
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        handler.postDelayed(timerRunnable!!, 1000L)
    }

    fun stopListening(sendImmediately: Boolean, currentRecognizedText: String = "") {
        if (!isRecording) return
        isRecording = false

        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null

        var recordedBytes: ByteArray? = null
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {}
        mediaRecorder = null

        try {
            if (audioOutputFile != null && audioOutputFile!!.exists() && audioOutputFile!!.length() > 0) {
                recordedBytes = audioOutputFile!!.readBytes()
            }
        } catch (e: Exception) {}

        if (sendImmediately) {
            onFinalTranscript?.invoke(currentRecognizedText.trim(), recordedBytes)
        }
    }

    fun cancelListening() {
        if (!isRecording) return
        isRecording = false

        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {}
        mediaRecorder = null

        try {
            audioOutputFile?.delete()
        } catch (e: Exception) {}
        audioOutputFile = null
    }
}
