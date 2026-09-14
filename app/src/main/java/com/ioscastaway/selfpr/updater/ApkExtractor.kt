package com.ioscastaway.selfpr.updater

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * A workflow artifact downloads as a zip, whatever was uploaded. This pulls the one APK out of it
 * and refuses anything else: no APK, more than one, or an entry that tries to escape its name.
 */
object ApkExtractor {
    class Extracted(val entryName: String, val bytes: Long)

    fun extract(zip: InputStream, into: OutputStream, onProgress: (Long) -> Unit = {}): Extracted {
        var found: Extracted? = null
        ZipInputStream(zip).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory || !e.name.endsWith(".apk")) continue
                require(!e.name.contains("..")) { "Refusing zip entry ${e.name}" }
                check(found == null) { "The artifact holds more than one APK (${found!!.entryName}, ${e.name})" }
                var total = 0L
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = z.read(buf)
                    if (n < 0) break
                    into.write(buf, 0, n); total += n; onProgress(total)
                }
                found = Extracted(e.name, total)
            }
        }
        return checkNotNull(found) { "The artifact holds no APK" }
    }
}
