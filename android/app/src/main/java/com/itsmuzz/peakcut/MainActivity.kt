package com.itsmuzz.peakcut

import androidx.activity.ComponentActivity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var selectedVideo: Uri? = null
    private lateinit var preview: VideoView
    private lateinit var status: TextView
    private lateinit var startSeconds: EditText
    private lateinit var endSeconds: EditText

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedVideo = uri
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            preview.setVideoURI(uri)
            preview.start()
            status.text = "Video ready. Start/end time seconds mein set karo."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 12, 20)
        window.navigationBarColor = android.graphics.Color.rgb(9, 12, 20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22, 34, 22, 24)
            setBackgroundColor(android.graphics.Color.rgb(9, 12, 20))
        }
        fun label(text: String, size: Float = 15f) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(android.graphics.Color.WHITE)
        }
        root.addView(label("PEAKCUT", 28f).apply { letterSpacing = 0.18f })
        root.addView(label("VIDEO STUDIO  •  ANDROID", 11f).apply {
            setTextColor(android.graphics.Color.rgb(130, 155, 190))
            setPadding(0, 4, 0, 22)
        })
        preview = VideoView(this)
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220).apply { bottomMargin = 18 })
        root.addView(Button(this).apply {
            text = "Choose video"
            setOnClickListener { pickVideo.launch(arrayOf("video/*")) }
        }, matchWidth())
        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startSeconds = EditText(this).apply {
            hint = "Start (sec)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("0"); setTextColor(android.graphics.Color.WHITE); setHintTextColor(android.graphics.Color.GRAY)
        }
        endSeconds = EditText(this).apply {
            hint = "End (sec)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("30"); setTextColor(android.graphics.Color.WHITE); setHintTextColor(android.graphics.Color.GRAY)
        }
        fields.addView(startSeconds, LinearLayout.LayoutParams(0, 58, 1f).apply { rightMargin = 8 })
        fields.addView(endSeconds, LinearLayout.LayoutParams(0, 58, 1f).apply { leftMargin = 8 })
        root.addView(fields)
        root.addView(Button(this).apply {
            text = "Export clip"
            setOnClickListener { exportClip() }
        }, matchWidth())
        status = label("Select a video to create your first clip.").apply {
            setPadding(0, 18, 0, 0)
            setTextColor(android.graphics.Color.rgb(160, 180, 205))
        }
        root.addView(status, matchWidth())
        root.addView(label("Milestone 1 • Local video trimming", 11f).apply {
            setTextColor(android.graphics.Color.rgb(100, 120, 145)); setPadding(0, 26, 0, 0)
        })
        setContentView(root)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }

    private fun exportClip() {
        val source = selectedVideo ?: run { status.text = "Pehle video select karo."; return }
        val startUs = ((startSeconds.text.toString().toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) * 1_000_000).toLong()
        val endUs = ((endSeconds.text.toString().toDoubleOrNull() ?: 0.0) * 1_000_000).toLong()
        if (endUs <= startUs) { status.text = "End time, start time se bada hona chahiye."; return }
        status.text = "Clip export ho raha hai…"
        executor.execute {
            try {
                val result = trimVideo(source, startUs, endUs)
                runOnUiThread { status.text = "Saved to Movies/PeakCut: $result" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Export failed: ${e.message ?: "unknown error"}" }
            }
        }
    }

    private fun trimVideo(source: Uri, startUs: Long, endUs: Long): String {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "PeakCut_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PeakCut")
        }
        val outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create output video")
        try {
            contentResolver.openFileDescriptor(outputUri, "w")!!.use { output ->
                contentResolver.openFileDescriptor(source, "r")!!.use { input ->
                    val extractor = MediaExtractor()
                    extractor.setDataSource(input.fileDescriptor)
                    val muxer = MediaMuxer(output.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    val trackMap = HashMap<Int, Int>()
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                            extractor.selectTrack(i)
                            trackMap[i] = muxer.addTrack(format)
                        }
                    }
                    require(trackMap.isNotEmpty()) { "No audio/video tracks found" }
                    muxer.start()
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val firstSampleTimeUs = extractor.sampleTime
                    require(firstSampleTimeUs >= 0) { "Start time is beyond the readable video range" }
                    val buffer = ByteBuffer.allocate(8 * 1024 * 1024)
                    val info = MediaCodec.BufferInfo()
                    var writtenSamples = 0
                    while (true) {
                        val track = extractor.sampleTrackIndex
                        val sampleTimeUs = extractor.sampleTime
                        if (track < 0 || sampleTimeUs < 0 || sampleTimeUs > endUs) break
                        if (trackMap.containsKey(track)) {
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) break
                            val outputTimeUs = (sampleTimeUs - firstSampleTimeUs).coerceAtLeast(0L)
                            info.set(0, size, outputTimeUs, extractor.sampleFlags)
                            muxer.writeSampleData(trackMap[track]!!, buffer, info)
                            writtenSamples++
                        }
                        if (!extractor.advance()) break
                    }
                    require(writtenSamples > 0) { "No media samples found in the selected time range" }
                    muxer.stop()
                    muxer.release()
                    extractor.release()
                }
            }
            return outputUri.lastPathSegment ?: "clip exported"
        } catch (e: Exception) {
            contentResolver.delete(outputUri, null, null)
            throw e
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
