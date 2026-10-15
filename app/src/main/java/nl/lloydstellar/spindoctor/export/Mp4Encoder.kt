package nl.lloydstellar.spindoctor.export

import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import nl.lloydstellar.spindoctor.audio.Pcm
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class EncodeCancelled : Exception()

/**
 * Turns frames and a PCM clip into an MP4 (H.264 + AAC) on the phone. This
 * replaces server/export-mp4.php, which did the same with ffmpeg on the web
 * server.
 *
 * Frames are drawn on the GPU and go to the hardware encoder through
 * [GlBridge] without passing through main memory; the encoder chip converts
 * to YUV itself.
 */
/* start-up of c2.android.aac.encoder (AAC-LC), and the samples in one AAC-LC frame */
private const val AAC_DELAY = 2048
private const val AAC_FRAME = 1024

class Mp4Encoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
) {
    /* plenty for a record on a plain background; Instagram and TikTok compress again anyway */
    private val bitrate = when {
        width * height >= 1080 * 1900 -> 8_000_000
        width * height >= 1080 * 1300 -> 7_000_000
        else -> 6_000_000
    }

    /**
     * Encodes [frameCount] frames and the sound [audio] into [output]. [render]
     * draws frame i on the canvas it gets; [progress] gets 0..1. Throws [EncodeCancelled] when
     * [isCancelled] turns true. Blocks; call it from a background thread.
     */
    fun encode(
        output: File,
        frameCount: Int,
        audio: Pcm?,
        render: (index: Int, canvas: Canvas) -> Unit,
        progress: (Double) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val audioTrack = audio?.let { encodeAudio(it, isCancelled) }
        val codec = createVideoEncoder()
        val surface = codec.createInputSurface()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxing = false
        var videoIndex = -1
        var audioIndex = -1
        var audioNext = 0
        var written = 0
        val info = MediaCodec.BufferInfo()

        fun writeAudioUpTo(us: Long) {
            val a = audioTrack ?: return
            while (audioNext < a.samples.size && a.samples[audioNext].pts <= us) {
                val s = a.samples[audioNext++]
                val bi = MediaCodec.BufferInfo().apply { set(0, s.data.size, s.pts, s.flags) }
                muxer.writeSampleData(audioIndex, ByteBuffer.wrap(s.data), bi)
            }
        }

        /* Hands encoded frames to the muxer, until nothing is ready or, with [untilEnd], the stream ends. */
        fun drain(untilEnd: Boolean) {
            while (true) {
                val out = codec.dequeueOutputBuffer(info, if (untilEnd) 10_000 else 0)
                when {
                    out == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEnd) return
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoIndex = muxer.addTrack(codec.outputFormat)
                        if (audioTrack != null) audioIndex = muxer.addTrack(audioTrack.format)
                        muxer.start()
                        muxing = true
                    }
                    out >= 0 -> {
                        val buf = codec.getOutputBuffer(out)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(videoIndex, buf, info)
                            written++
                            /* keep the file interleaved: audio up to half a second ahead of the video */
                            writeAudioUpTo(info.presentationTimeUs + 500_000)
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        var ok = false
        var bridge: GlBridge? = null
        try {
            codec.start()
            val gl = GlBridge(surface, width, height).also { bridge = it }
            for (index in 0 until frameCount) {
                if (isCancelled()) throw EncodeCancelled()
                drain(false)
                gl.frame(index * 1_000_000_000L / fps) { render(index, it) }
                progress((index + 1).toDouble() / frameCount)
            }
            codec.signalEndOfInputStream()
            drain(true)
            check(written == frameCount) { "The encoder gave $written of $frameCount frames" }
            writeAudioUpTo(Long.MAX_VALUE)
            ok = true
        } finally {
            bridge?.release()
            surface.release()
            try { codec.stop() } catch (_: Exception) { }
            codec.release()
            try { if (muxing) muxer.stop() } catch (_: Exception) { ok = false }
            muxer.release()
            if (!ok) output.delete()
        }
    }

    /* ---------------- video ---------------- */

    private fun videoFormat() = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
        setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        /* HD video is BT.709; say so, so the encoder converts with it and players show the right colours */
        setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
        setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
        setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
    }

    private fun createVideoEncoder(): MediaCodec {
        val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && MediaFormat.MIMETYPE_VIDEO_AVC in it.supportedTypes }
            .filter {
                val caps = it.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                caps.videoCapabilities?.isSizeSupported(width, height) == true &&
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in caps.colorFormats
            }
            /* hardware first; the software encoder is the fallback */
            .sortedBy { if (it.isHardwareAccelerated) 0 else 1 }
        var last: Exception? = null
        for (info in candidates) {
            val caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val high = caps.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
            for (useHigh in if (high) listOf(true, false) else listOf(false)) {
                val codec = MediaCodec.createByCodecName(info.name)
                try {
                    val format = videoFormat()
                    if (useHigh) format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    return codec
                } catch (e: Exception) {
                    last = e
                    codec.release()
                }
            }
        }
        throw IllegalStateException("No H.264 encoder for ${width}x$height", last)
    }

    /* ---------------- audio ---------------- */

    private class AacSample(val data: ByteArray, val pts: Long, val flags: Int)
    private class AacTrack(val format: MediaFormat, val samples: List<AacSample>)

    /**
     * Encodes the whole clip to AAC up front; it is small (about 700 kB for 30 s).
     *
     * An AAC encoder puts [AAC_DELAY] samples of start-up in front of the sound,
     * which would make the sound run 46 ms behind the picture. ffmpeg on the
     * server hides it with an edit list; MediaMuxer cannot write one, so the
     * frames holding the start-up are left out instead. The decoder then fades
     * the first 23 ms in, which also keeps the cut from clicking.
     */
    private fun encodeAudio(source: Pcm, isCancelled: () -> Boolean): AacTrack {
        val pcm = if (source.sampleRate > 48_000) resample(source, 48_000) else source
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, pcm.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        /* the platform's own encoder, on every phone since Android 10, with a known start-up */
        val codec = try {
            MediaCodec.createByCodecName("c2.android.aac.encoder")
        } catch (e: Exception) {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        }
        val samples = ArrayList<AacSample>()
        var outFormat: MediaFormat? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var next = 0
            var produced = 0
            var inputDone = false
            val ch = pcm.channels
            val bytesPerFrame = 2 * ch
            while (true) {
                if (isCancelled()) throw EncodeCancelled()
                var queued = false
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(0)
                    if (idx >= 0) {
                        queued = true
                        val buf = codec.getInputBuffer(idx)!!.order(ByteOrder.nativeOrder())
                        val pts = next * 1_000_000L / pcm.sampleRate
                        val framesLeft = pcm.frames - next
                        if (framesLeft <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val n = min(framesLeft, buf.capacity() / bytesPerFrame)
                            buf.clear()
                            buf.asShortBuffer().put(pcm.samples, next * ch, n * ch)
                            codec.queueInputBuffer(idx, 0, n * bytesPerFrame, pts, 0)
                            next += n
                        }
                    }
                }
                /* wait only when there was nothing to feed, so the loop never idles on a timeout */
                val out = codec.dequeueOutputBuffer(info, if (queued) 0 else 2_000)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outFormat = codec.outputFormat
                } else if (out >= 0) {
                    val buf = codec.getOutputBuffer(out)!!
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        /* AAC-LC frames are 1024 samples; the first ones hold the start-up */
                        val start = produced.toLong() * AAC_FRAME - AAC_DELAY
                        produced++
                        if (start >= 0) {
                            val data = ByteArray(info.size)
                            buf.position(info.offset)
                            buf.get(data)
                            samples.add(AacSample(data, start * 1_000_000L / pcm.sampleRate, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()))
                        }
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) { }
            codec.release()
        }
        return AacTrack(outFormat ?: throw IllegalStateException("AAC encoder gave no format"), samples)
    }

    /** Linear resampling for the rare 88.2/96 kHz file; AAC encoders stop at 48 kHz. */
    private fun resample(pcm: Pcm, rate: Int): Pcm {
        val ch = pcm.channels
        val frames = (pcm.frames.toLong() * rate / pcm.sampleRate).toInt()
        val out = ShortArray(frames * ch)
        val step = pcm.sampleRate.toDouble() / rate
        for (i in 0 until frames) {
            val pos = i * step
            val i0 = pos.toInt().coerceAtMost(pcm.frames - 1)
            val i1 = (i0 + 1).coerceAtMost(pcm.frames - 1)
            val f = pos - i0
            for (c in 0 until ch) {
                val a = pcm.samples[i0 * ch + c]; val b = pcm.samples[i1 * ch + c]
                out[i * ch + c] = (a + (b - a) * f).toInt().toShort()
            }
        }
        return Pcm(out, rate, ch)
    }
}
