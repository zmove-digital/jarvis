package com.zmove.jarvis

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Wake word 100% offline e sem chave, usando o KeywordSpotter do sherpa-onnx.
 *
 * O modelo (zipformer KWS em inglês, ~6 MB) reconhece a palavra "JARVIS" a
 * partir dos tokens BPE gerados em [KEYWORDS_ASSET]. O áudio é processado no
 * próprio aparelho e nunca sai dele.
 *
 * O modelo é carregado uma única vez e mantido em memória entre pausas, para
 * que o ciclo "dizer Jarvis -> gravar comando -> voltar a ouvir" seja rápido.
 */
class SherpaWakeWordEngine(
    context: Context,
    private val onDetected: (String) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) : WakeWordEngine {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jarvis-kws").apply { priority = Thread.NORM_PRIORITY }
    }

    @Volatile
    private var running = false

    @Volatile
    private var released = false

    @Volatile
    private var recorder: AudioRecord? = null

    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var initFailed = false

    @Synchronized
    override fun start() {
        if (released || running || initFailed) return
        running = true
        executor.execute { runLoop() }
    }

    @Synchronized
    override fun pause() {
        running = false
        stopRecorder()
    }

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        running = false
        stopRecorder()
        try {
            // A limpeza roda na mesma thread do laço, depois que ele terminar.
            executor.execute { releaseResources() }
        } catch (_: RejectedExecutionException) {
            releaseResources()
        }
        executor.shutdown()
    }

    private fun stopRecorder() {
        val current = recorder ?: return
        try {
            if (current.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                current.stop()
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Falha ao parar o microfone", exception)
        }
    }

    @SuppressLint("MissingPermission")
    private fun runLoop() {
        try {
            ensureInitialized()
        } catch (throwable: Throwable) {
            if (!released) {
                Log.e(TAG, "Falha ao carregar o modelo Sherpa", throwable)
                initFailed = true
                onError(throwable)
            }
            releaseResources()
            return
        }

        if (!running) {
            releaseResources()
            return
        }

        val kws = spotter ?: return
        val audioStream = stream ?: return

        try {
            kws.reset(audioStream)

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer, FRAME_SAMPLES * 2 * 2)
            // MIC é a fonte usada pelo exemplo oficial do sherpa-onnx e foi
            // validada no aparelho. VOICE_RECOGNITION pode aplicar AEC e
            // supressão de ruído que degradam a detecção do KWS.
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("AudioRecord não foi inicializado")
            }

            recorder = record
            record.startRecording()
            Log.i(TAG, "Sherpa KWS ouvindo (${SAMPLE_RATE} Hz)")

            val shortBuffer = ShortArray(FRAME_SAMPLES)
            while (running) {
                val read = record.read(shortBuffer, 0, shortBuffer.size)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                        read == AudioRecord.ERROR_BAD_VALUE
                    ) {
                        Log.w(TAG, "Leitura de áudio falhou: $read")
                        break
                    }
                    continue
                }

                val samples = FloatArray(read) { shortBuffer[it] / 32768.0f }
                audioStream.acceptWaveform(samples, SAMPLE_RATE)
                while (kws.isReady(audioStream)) {
                    kws.decode(audioStream)

                    val result = kws.getResult(audioStream)
                    if (result.keyword.isNotEmpty()) {
                        Log.i(TAG, "Palavra de ativação detectada: ${result.keyword}")
                        kws.reset(audioStream)
                        onDetected(result.keyword)
                    }
                }
            }
        } catch (throwable: Throwable) {
            if (!released) {
                Log.e(TAG, "Erro durante a escuta do Sherpa", throwable)
            }
        } finally {
            val record = recorder
            recorder = null
            try {
                record?.stop()
            } catch (_: Exception) {
                // O microfone pode já ter sido parado por pause().
            }
            record?.release()
            releaseResources()
        }
    }

    private fun ensureInitialized() {
        if (spotter != null && stream != null) return

        val directory = copyAssets()
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(directory, ENCODER_ASSET).absolutePath,
                    decoder = File(directory, DECODER_ASSET).absolutePath,
                    joiner = File(directory, JOINER_ASSET).absolutePath
                ),
                tokens = File(directory, TOKENS_ASSET).absolutePath,
                numThreads = 2,
                modelType = "zipformer2",
                debug = false
            ),
            maxActivePaths = 4,
            keywordsFile = File(directory, KEYWORDS_ASSET).absolutePath,
            keywordsScore = KEYWORDS_SCORE,
            keywordsThreshold = KEYWORDS_THRESHOLD,
            numTrailingBlanks = 1
        )

        val kws = KeywordSpotter(null, config)
        spotter = kws
        stream = kws.createStream()
        Log.i(TAG, "Modelo Sherpa carregado de ${directory.absolutePath}")
    }

    private fun copyAssets(): File {
        val target = File(appContext.filesDir, MODEL_DIRECTORY)
        if (!target.exists() && !target.mkdirs()) {
            throw IllegalStateException("Não foi possível criar ${target.absolutePath}")
        }

        for (name in MODEL_FILES) {
            val output = File(target, name)
            if (output.exists() && output.length() > 0L) continue

            appContext.assets.open("$MODEL_DIRECTORY/$name").use { input ->
                FileOutputStream(output).use { out -> input.copyTo(out) }
            }
            Log.i(TAG, "Asset copiado: $name (${output.length()} bytes)")
        }
        return target
    }

    private fun releaseResources() {
        if (!released) return
        stream?.release()
        stream = null
        spotter?.release()
        spotter = null
    }

    private companion object {
        const val TAG = "SherpaWakeWordEngine"

        const val SAMPLE_RATE = 16_000
        const val FEATURE_DIM = 80
        const val FRAME_SAMPLES = 1_600 // 100 ms

        const val KEYWORDS_SCORE = 1.0f
        const val KEYWORDS_THRESHOLD = 0.25f

        const val MODEL_DIRECTORY = "kws"
        const val ENCODER_ASSET = "encoder.int8.onnx"
        const val DECODER_ASSET = "decoder.onnx"
        const val JOINER_ASSET = "joiner.int8.onnx"
        const val TOKENS_ASSET = "tokens.txt"
        const val KEYWORDS_ASSET = "keywords.txt"

        val MODEL_FILES = listOf(
            ENCODER_ASSET,
            DECODER_ASSET,
            JOINER_ASSET,
            TOKENS_ASSET,
            KEYWORDS_ASSET
        )
    }
}
