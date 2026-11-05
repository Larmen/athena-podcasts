package se.anders.athenapodcaster

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin
import se.anders.athenapodcaster.data.db.Episode

/**
 * Test-only audio: a 5-minute WAV whose tone changes every 10 seconds, so seeking is easy to verify.
 */
object SampleAudio {
    private const val RATE = 8000
    private const val SECONDS = 300

    fun write(context: Context, episode: Episode): String {
        val dir = File(context.getExternalFilesDir("episodes"), episode.feedId.toString()).apply { mkdirs() }
        val file = File(dir, "${episode.id}.wav")
        val dataBytes = RATE * SECONDS * 2
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray()).put("fmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(dataBytes)
            f.write(header.array())
            val buf = java.nio.ByteBuffer.allocate(RATE * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (second in 0 until SECONDS) {
                val hz = 220.0 + 40.0 * (second / 10 % 12)
                buf.clear()
                for (i in 0 until RATE) buf.putShort((sin(2 * PI * hz * i / RATE) * 6000).toInt().toShort())
                f.write(buf.array())
            }
        }
        return file.absolutePath
    }
}
