package nl.lloydstellar.spindoctor.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** Interleaved 16-bit PCM. */
class Pcm(val samples: ShortArray, val sampleRate: Int, val channels: Int) {
    val frames: Int get() = samples.size / channels
    val duration: Double get() = frames.toDouble() / sampleRate
}

/**
 * Loudness overview of a whole track for the clip selector: the peak and the
 * RMS of every 10 ms. A mastered track peaks near full scale almost all the
 * time; the RMS is what shows the intro, the breakdown and the drop.
 */
class Peaks(val values: FloatArray, val rms: FloatArray, val perSecond: Int) {
    val duration: Double get() = values.size.toDouble() / perSecond
    val loudest: Float = values.maxOrNull()?.coerceAtLeast(0.001f) ?: 0.001f
    val loudestRms: Float = rms.maxOrNull()?.coerceAtLeast(0.001f) ?: 0.001f
}

class AudioException(message: String) : Exception(message)

/** Where decoded sound goes: [count] interleaved samples, the first at frame [position] of the track. */
private typealias Sink = (chunk: ShortArray, count: Int, sampleRate: Int, channels: Int, position: Long) -> Boolean

/**
 * Decodes audio files with the platform codecs (MP3, WAV, AAC/M4A, FLAC, OGG,
 * whatever the phone supports). This is where the browser used
 * decodeAudioData(); the result goes into the waveform, the beat envelope and
 * the video.
 *
 * MediaCodec costs about a millisecond per buffer, while one compressed frame
 * holds only 20 to 30 ms of sound, so the trick is to use few buffers:
 *   PCM (WAV, and FLAC, which the extractor already decodes)  read directly, no codec
 *   MP3   many frames back to back in one buffer; the decoder walks through them
 *   other the large frame mode of Android 15 where the codec has it, else frame by frame
 * The first block is placed by the decoder's timestamp (the timeline the player
 * uses after the same jump); from there samples are counted, so the sound
 * runs on without a gap or overlap even where a codec's timestamps wobble.
 */
object AudioDecoder {

    const val PEAKS_PER_SECOND = 100
    private const val TAG = "AudioDecoder"
    private const val BATCH_BYTES = 64 * 1024

    /** Duration in seconds without decoding, or an exception for files that are not audio. */
    fun probe(ctx: Context, uri: Uri): Double {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            val track = audioTrack(ex) ?: throw AudioException("No audio in this file")
            val f = ex.getTrackFormat(track)
            if (!f.containsKey(MediaFormat.KEY_DURATION)) throw AudioException("Unknown length")
            return f.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0
        } catch (e: AudioException) {
            throw e
        } catch (e: Exception) {
            throw AudioException("This file can't be read as audio")
        } finally {
            ex.release()
        }
    }

    fun peaks(ctx: Context, uri: Uri, isCancelled: () -> Boolean = { false }): Peaks {
        val peaks = FloatArrayBuilder()
        val rms = FloatArrayBuilder()
        var bucketSize = 0
        var inBucket = 0
        var peak = 0f
        var power = 0.0
        var ch = 1
        decode(ctx, uri, 0.0, isCancelled) { chunk, count, rate, channels, _ ->
            if (bucketSize == 0) bucketSize = max(1, rate / PEAKS_PER_SECOND)
            ch = channels
            var i = 0
            while (i < count) {
                for (c in 0 until channels) {
                    val v = abs(chunk[i + c].toFloat()) / 32768f
                    if (v > peak) peak = v
                    power += v * v
                }
                i += channels
                if (++inBucket == bucketSize) {
                    peaks.add(peak); rms.add(sqrt(power / (inBucket * channels)).toFloat())
                    peak = 0f; power = 0.0; inBucket = 0
                }
            }
            true
        }
        if (inBucket > 0) { peaks.add(peak); rms.add(sqrt(power / (inBucket * ch)).toFloat()) }
        return Peaks(peaks.toArray(), rms.toArray(), PEAKS_PER_SECOND)
    }

    /**
     * The samples from [start] to [end] seconds. Decoding starts a second
     * before [start] (the extractor jumps there), so the decoder has settled
     * by the time the segment begins.
     */
    fun region(ctx: Context, uri: Uri, start: Double, end: Double, isCancelled: () -> Boolean = { false }): Pcm {
        var rate = 0
        var ch = 0
        var first = 0L
        var last = 0L
        var buffer = ShortArray(0)
        var written = 0
        decode(ctx, uri, max(0.0, start - 1.0), isCancelled) { chunk, count, r, c, position ->
            if (rate == 0) {
                rate = r; ch = c
                first = floor(start * rate).toLong()
                last = floor(end * rate).toLong()
                buffer = ShortArray(((last - first).coerceAtLeast(0) * ch).toInt())
            }
            val frames = count / ch
            val from = max(first, position)
            val to = min(last, position + frames)
            if (to > from) {
                val srcOffset = ((from - position) * ch).toInt()
                val dst = ((from - first) * ch).toInt()
                val n = ((to - from) * ch).toInt()
                System.arraycopy(chunk, srcOffset, buffer, dst, n)
                written = max(written, dst + n)
            }
            position + frames < last
        }
        if (rate == 0) throw AudioException("No audio decoded")
        return Pcm(if (written == buffer.size) buffer else buffer.copyOf(written), rate, ch)
    }

    private fun audioTrack(ex: MediaExtractor): Int? {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun decode(ctx: Context, uri: Uri, from: Double, isCancelled: () -> Boolean, sink: Sink) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            val track = audioTrack(ex) ?: throw AudioException("No audio in this file")
            ex.selectTrack(track)
            if (from > 0) ex.seekTo((from * 1_000_000).toLong(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val format = ex.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val large = if (mime != MediaFormat.MIMETYPE_AUDIO_MPEG) largeFrameDecoder(mime) else null
            when {
                mime == MediaFormat.MIMETYPE_AUDIO_RAW -> readPcm(ex, format, isCancelled, sink)
                large != null -> decodeLargeFrames(ex, format, large, isCancelled, sink)
                else -> decodeSync(ex, format, isCancelled, sink)
            }
        } catch (e: AudioException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Decoding $uri failed", e)
            throw AudioException("This file can't be read as audio")
        } finally {
            ex.release()
        }
    }

    /* ---------------- PCM straight from the extractor ---------------- */

    private fun readPcm(ex: MediaExtractor, format: MediaFormat, isCancelled: () -> Boolean, sink: Sink) {
        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
        val buf = ByteBuffer.allocateDirect(BATCH_BYTES * 4).order(ByteOrder.nativeOrder())
        val converter = PcmConverter()
        var position = (ex.sampleTime.coerceAtLeast(0) * rate / 1e6).roundToLong()
        while (!isCancelled()) {
            buf.clear()
            var size = 0
            while (buf.capacity() - size >= 16 * 1024) {
                val n = ex.readSampleData(buf, size)
                if (n < 0) break
                size += n
                ex.advance()
            }
            if (size == 0) return
            buf.position(0); buf.limit(size)
            val (chunk, count, keep) = converter.convert(buf, encoding, channels)
            if (count > 0 && !sink(chunk, count, rate, keep, position)) return
            position += count / keep
        }
    }

    /* ---------------- MediaCodec, synchronous ---------------- */

    private fun decodeSync(ex: MediaExtractor, format: MediaFormat, isCancelled: () -> Boolean, sink: Sink) {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = try {
            inProcessDecoder(mime)?.let { MediaCodec.createByCodecName(it) } ?: MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            throw AudioException("This phone can't play this audio format")
        }
        try {
            /* MP3 frames are self-delimiting, so many fit in one buffer */
            val concat = mime == MediaFormat.MIMETYPE_AUDIO_MPEG
            if (concat) format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BATCH_BYTES)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val out = OutputReader(format)
            var inputDone = false
            while (!isCancelled()) {
                var queued = false
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(0)
                    if (idx >= 0) {
                        queued = true
                        val buf = codec.getInputBuffer(idx)!!
                        val pts = ex.sampleTime.coerceAtLeast(0)
                        var size = 0
                        while (true) {
                            val n = ex.readSampleData(buf, size)
                            if (n < 0) { inputDone = true; break }
                            size += n
                            ex.advance()
                            if (!concat || buf.capacity() - size < 2048) break
                        }
                        codec.queueInputBuffer(idx, 0, size, pts, if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    }
                }
                /* wait only when there was nothing to feed, so the loop never idles on a timeout */
                val idx = codec.dequeueOutputBuffer(info, if (queued) 0 else 2_000)
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) out.format(codec.outputFormat)
                if (idx >= 0) {
                    val more = out.read(codec.getOutputBuffer(idx)!!, info.offset, info.size, info.presentationTimeUs, sink)
                    codec.releaseOutputBuffer(idx, false)
                    if (!more || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) { }
            codec.release()
        }
    }

    /* ---------------- MediaCodec, large frames (Android 15+) ---------------- */

    private sealed interface Event
    private class Output(val index: Int, val offset: Int, val size: Int, val pts: Long, val flags: Int) : Event
    private class FormatChanged(val format: MediaFormat) : Event
    private class Failure(val error: Exception) : Event

    /**
     * Hands the codec up to 64 kB of compressed frames per buffer and gets as
     * many decoded frames back at once. This mode only exists asynchronously:
     * the codec reports free and filled buffers on a callback thread.
     */
    private fun decodeLargeFrames(ex: MediaExtractor, format: MediaFormat, name: String, isCancelled: () -> Boolean, sink: Sink) {
        if (Build.VERSION.SDK_INT < 35) return decodeSync(ex, format, isCancelled, sink)
        val codec = MediaCodec.createByCodecName(name)
        val thread = HandlerThread("audio-decoder").apply { start() }
        val inputs = LinkedBlockingQueue<Int>()
        val events = LinkedBlockingQueue<Event>()
        try {
            codec.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) { inputs.add(index) }
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    events.add(Output(index, info.offset, info.size, info.presentationTimeUs, info.flags))
                }
                override fun onOutputBuffersAvailable(codec: MediaCodec, index: Int, infos: ArrayDeque<MediaCodec.BufferInfo>) {
                    val head = infos.peekFirst()!!
                    val tail = infos.peekLast()!!
                    val flags = infos.fold(0) { f, i -> f or i.flags }
                    events.add(Output(index, head.offset, tail.offset + tail.size - head.offset, head.presentationTimeUs, flags))
                }
                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) { events.add(FormatChanged(format)) }
                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) { events.add(Failure(e)) }
            }, Handler(thread.looper))
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BATCH_BYTES)
            format.setInteger(MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE, 1024 * 1024)
            format.setInteger(MediaFormat.KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE, 256 * 1024)
            codec.configure(format, null, null, 0)
            codec.start()
            val out = OutputReader(format)
            var inputDone = false
            while (!isCancelled()) {
                var queued = false
                if (!inputDone) {
                    val idx = inputs.poll()
                    if (idx != null) {
                        queued = true
                        val buf = codec.getInputBuffer(idx)!!
                        val units = ArrayDeque<MediaCodec.BufferInfo>()
                        var size = 0
                        while (buf.capacity() - size >= 8 * 1024) {
                            val time = ex.sampleTime
                            val n = ex.readSampleData(buf, size)
                            if (n < 0) { inputDone = true; break }
                            units.add(MediaCodec.BufferInfo().apply { set(size, n, time, 0) })
                            size += n
                            ex.advance()
                        }
                        if (units.isEmpty()) units.add(MediaCodec.BufferInfo().apply { set(0, 0, 0, 0) })
                        if (inputDone) units.peekLast()!!.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        codec.queueInputBuffers(idx, units)
                    }
                }
                when (val e = events.poll(if (queued) 0 else 5, TimeUnit.MILLISECONDS)) {
                    is Failure -> throw e.error
                    is FormatChanged -> out.format(e.format)
                    is Output -> {
                        val more = out.read(codec.getOutputBuffer(e.index)!!, e.offset, e.size, e.pts, sink)
                        codec.releaseOutputBuffer(e.index, false)
                        if (!more || e.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                    null -> {}
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) { }
            codec.release()
            thread.quitSafely()
        }
    }

    /* ---------------- shared helpers ---------------- */

    /** Turns decoder output into 16-bit chunks: the first placed by its timestamp, the rest counted on. */
    private class OutputReader(input: MediaFormat) {
        private var rate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        private var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        private var encoding = AudioFormat.ENCODING_PCM_16BIT
        private val converter = PcmConverter()
        private var position = -1L

        fun format(f: MediaFormat) {
            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
        }

        fun read(buffer: ByteBuffer, offset: Int, size: Int, pts: Long, sink: Sink): Boolean {
            if (size <= 0) return true
            val buf = buffer.duplicate().order(ByteOrder.nativeOrder())
            buf.position(offset); buf.limit(offset + size)
            val (chunk, count, keep) = converter.convert(buf, encoding, channels)
            if (count == 0) return true
            if (position < 0) position = (pts * rate / 1e6).roundToLong()
            val at = position
            position += count / keep
            return sink(chunk, count, rate, keep, at)
        }
    }

    /** PCM in any of the usual encodings to interleaved 16-bit, at most two channels. */
    private class PcmConverter {
        private var chunk = ShortArray(0)

        fun convert(buf: ByteBuffer, encoding: Int, channels: Int): Triple<ShortArray, Int, Int> {
            val keep = min(channels, 2)
            val bytes = when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_8BIT -> 1
                else -> 2
            }
            val frames = buf.remaining() / (bytes * channels)
            val needed = frames * keep
            if (chunk.size < needed) chunk = ShortArray(needed)
            val base = buf.position()
            if (encoding == AudioFormat.ENCODING_PCM_16BIT && keep == channels) {
                buf.asShortBuffer().get(chunk, 0, needed)
            } else {
                for (i in 0 until frames) for (c in 0 until keep) {
                    val at = base + (i * channels + c) * bytes
                    chunk[i * keep + c] = when (encoding) {
                        AudioFormat.ENCODING_PCM_FLOAT -> (buf.getFloat(at) * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                        AudioFormat.ENCODING_PCM_32BIT -> (buf.getInt(at) shr 16).toShort()
                        AudioFormat.ENCODING_PCM_24BIT_PACKED -> ((buf.get(at + 1).toInt() and 0xFF) or (buf.get(at + 2).toInt() shl 8)).toShort()
                        AudioFormat.ENCODING_PCM_8BIT -> (((buf.get(at).toInt() and 0xFF) - 128) shl 8).toShort()
                        else -> buf.getShort(at)
                    }
                }
            }
            return Triple(chunk, needed, keep)
        }
    }

    private val decoders by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } }

    /** A decoder that runs inside the app (no trip to the media process per buffer), if the phone has one. */
    private fun inProcessDecoder(mime: String): String? =
        decoders.firstOrNull { ".inproc." in it.name && mime in it.supportedTypes }?.name

    /** The first decoder for [mime] that takes many frames per buffer (Android 15+). */
    private fun largeFrameDecoder(mime: String): String? {
        if (Build.VERSION.SDK_INT < 35) return null
        return decoders.firstOrNull {
            mime in it.supportedTypes &&
                it.getCapabilitiesForType(mime).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_MultipleFrames)
        }?.name
    }

    private class FloatArrayBuilder {
        private var data = FloatArray(4096)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }
}
