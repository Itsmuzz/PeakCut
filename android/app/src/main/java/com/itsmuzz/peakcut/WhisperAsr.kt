package com.itsmuzz.peakcut

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class TranscriptToken(val text: String, val startSeconds: Float)

object WhisperAsr {
    private const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
    private const val MODEL_DIR = "whisper-tiny"
    private val requiredFiles = listOf(
        "tiny-encoder.int8.onnx",
        "tiny-decoder.int8.onnx",
        "tiny-tokens.txt"
    )

    fun modelReady(context: Context): Boolean {
        val dir = File(context.filesDir, "models/$MODEL_DIR")
        return requiredFiles.all { name ->
            File(dir, name).let { it.isFile && it.length() > 1024 }
        }
    }

    /**
     * Downloads the multilingual Whisper tiny model once into app-private storage.
     * The model is not bundled into the APK.
     */
    fun downloadModel(context: Context, onProgress: (Int) -> Unit = {}) {
        if (modelReady(context)) {
            onProgress(100)
            return
        }
        val modelRoot = File(context.filesDir, "models").apply { mkdirs() }
        val archivePart = File(modelRoot, "whisper-tiny.tar.bz2.part")
        val archive = File(modelRoot, "whisper-tiny.tar.bz2")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PeakCut-Android")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Model download failed: HTTP ${connection.responseCode}"
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(archivePart).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var received = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (total > 0) onProgress(((received * 85) / total).toInt().coerceIn(0, 85))
                    }
                    output.fd.sync()
                }
            }
            archive.delete()
            check(archivePart.renameTo(archive)) { "Could not finalize downloaded model archive" }
        } finally {
            connection.disconnect()
        }

        val staging = File(modelRoot, "$MODEL_DIR.tmp")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            archive.inputStream().use { raw ->
                BZip2CompressorInputStream(raw.buffered()).use { bz ->
                    TarArchiveInputStream(bz).use { tar ->
                        while (true) {
                            val entry = tar.nextTarEntry ?: break
                            if (!entry.isFile) continue
                            val name = entry.name.substringAfterLast('/')
                            if (name !in requiredFiles) continue
                            FileOutputStream(File(staging, name)).use { output ->
                                var remaining = entry.size
                                val buffer = ByteArray(64 * 1024)
                                while (remaining > 0) {
                                    val count = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    check(count > 0) { "Unexpected end of model archive while extracting $name" }
                                    output.write(buffer, 0, count)
                                    remaining -= count
                                }
                            }
                        }
                    }
                }
            }
            check(requiredFiles.all { File(staging, it).let { f -> f.isFile && f.length() > 1024 } }) {
                "Whisper model archive is missing required files"
            }
            val target = File(modelRoot, MODEL_DIR)
            target.deleteRecursively()
            check(staging.renameTo(target)) { "Could not install speech model" }
            archive.delete()
            onProgress(100)
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }
    }

    /**
     * Input must be mono Float32 PCM at 16 kHz. Android audio extraction/resampling
     * supplies this array in the next media-pipeline milestone.
     */
    fun transcribe(
        context: Context,
        samples16kMono: FloatArray,
        language: String = "hi",
        threads: Int = 2
    ): Pair<String, List<TranscriptToken>> {
        require(samples16kMono.isNotEmpty()) { "No audio samples supplied" }
        check(modelReady(context)) { "Speech model is not downloaded yet" }

        val dir = File(context.filesDir, "models/$MODEL_DIR")
        val whisper = OfflineWhisperModelConfig(
            encoder = File(dir, requiredFiles[0]).absolutePath,
            decoder = File(dir, requiredFiles[1]).absolutePath,
            language = language,
            task = "transcribe",
            enableTokenTimestamps = true,
        )
        val model = OfflineModelConfig(
            whisper = whisper,
            tokens = File(dir, requiredFiles[2]).absolutePath,
            numThreads = threads.coerceIn(1, 4),
            debug = false,
            provider = "cpu",
        )
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = model,
                decodingMethod = "greedy_search",
            )
        )
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples16kMono, 16000)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            val tokens = result.tokens
            val times = result.timestamps
            val aligned = tokens.mapIndexed { index, token ->
                TranscriptToken(token, times.getOrNull(index) ?: 0f)
            }
            return result.text to aligned
        } finally {
            stream.release()
            recognizer.release()
        }
    }
}
