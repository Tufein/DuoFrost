package io.github.tufein.duofrost

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Bounds the expanded data, including ZIP entries whose sizes are not declared. */
internal object PresetArchiveReader {
    internal data class Limits(
        val maxEntries: Int = 512,
        val maxManifestBytes: Long = 2L * 1024L * 1024L,
        val maxEntryBytes: Long = 8L * 1024L * 1024L,
        val maxTotalBytes: Long = 64L * 1024L * 1024L
    )

    fun read(
        input: InputStream,
        limits: Limits = Limits(),
        checkCancellation: () -> Unit = {}
    ): Map<String, ByteArray> = readEntries(input, limits, false, checkCancellation)

    fun readManifest(
        input: InputStream,
        limits: Limits = Limits(),
        checkCancellation: () -> Unit = {}
    ): ByteArray? = readEntries(input, limits, true, checkCancellation)["manifest.json"]

    private fun readEntries(
        input: InputStream,
        limits: Limits,
        manifestOnly: Boolean,
        checkCancellation: () -> Unit
    ): Map<String, ByteArray> {
        require(limits.maxEntries > 0 && limits.maxManifestBytes > 0 &&
            limits.maxEntryBytes > 0 && limits.maxTotalBytes > 0)
        val entries = linkedMapOf<String, ByteArray>()
        val seenNames = mutableSetOf<String>()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        var entryCount = 0

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                checkCancellation()
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > limits.maxEntries) {
                    throw IOException("Preset archive contains too many files (maximum ${limits.maxEntries}).")
                }
                if (!seenNames.add(entry.name)) {
                    throw IOException("Preset archive contains a duplicate file: '${entry.name}'.")
                }
                val entryLimit = if (entry.name == "manifest.json") {
                    minOf(limits.maxManifestBytes, limits.maxEntryBytes)
                } else {
                    limits.maxEntryBytes
                }
                if (entry.size > entryLimit) {
                    throw IOException("Preset archive file '${entry.name}' exceeds the $entryLimit byte limit.")
                }

                val bytes = ByteArrayOutputStream().use { output ->
                    var entryBytes = 0L
                    while (true) {
                        checkCancellation()
                        val count = zip.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        totalBytes += count
                        if (entryBytes > entryLimit) {
                            throw IOException("Preset archive file '${entry.name}' exceeds the $entryLimit byte limit.")
                        }
                        if (totalBytes > limits.maxTotalBytes) {
                            throw IOException("Preset archive exceeds the ${limits.maxTotalBytes} byte expanded size limit.")
                        }
                        // A schema probe streams earlier files without retaining their data.
                        // Directory payloads count against the same budgets too.
                        if (!entry.isDirectory && (!manifestOnly || entry.name == "manifest.json")) {
                            output.write(buffer, 0, count)
                        }
                    }
                    output.toByteArray()
                }
                if (!entry.isDirectory && (!manifestOnly || entry.name == "manifest.json")) {
                    entries[entry.name] = bytes
                }
                zip.closeEntry()
                // Official full backups put the manifest first. Their larger following
                // payload is left to the full backup importer and is never expanded here.
                if (manifestOnly && entry.name == "manifest.json") return entries
            }
        }
        return entries
    }
}
