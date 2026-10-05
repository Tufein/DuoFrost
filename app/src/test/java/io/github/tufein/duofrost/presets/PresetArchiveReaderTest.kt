package io.github.tufein.duofrost

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class PresetArchiveReaderTest {
    private val smallLimits = PresetArchiveReader.Limits(
        maxEntries = 4,
        maxManifestBytes = 64,
        maxEntryBytes = 128,
        maxTotalBytes = 256
    )

    @Test fun supportedPresetBundleVersionsRetainManifestAndImages() {
        for (version in 1..2) {
            val archive = validated("""{"schema":"bifrost_preset_bundle","version":$version,"presets":[{"name":"Shared preset"}]}""")
            assertTrue(archive.errors.isEmpty())
            assertEquals("Shared preset", archive.manifest!!.getJSONArray("presets").getJSONObject(0).getString("name"))
            assertArrayEquals(byteArrayOf(1, 2, 3), archive.entries["icons/shared.png"])
        }
    }

    @Test fun wrongSchemaExposesNoPresetsOrImagePayload() {
        val archive = validated("""{"schema":"another_bundle","version":2,"presets":[{"name":"Reject me"}]}""")
        assertRejected(archive, "schema")
    }

    @Test fun unsupportedVersionsExposeNoPresetsOrImagePayload() {
        for (version in listOf("0", "-1", "3", "2.5", "\"2\"", "null")) {
            val archive = validated("""{"schema":"bifrost_preset_bundle","version":$version,"presets":[{"name":"Reject me"}]}""")
            assertRejected(archive, "version")
        }
        assertRejected(validated("""{"schema":"bifrost_preset_bundle","presets":[{"name":"Reject me"}]}"""), "version")
    }

    @Test fun malformedOrMissingManifestIsRejectedBeforeArtwork() {
        assertRejected(validated("not JSON"), "JSON")
        val missing = PresetArchiveTransfer.readValidatedArchive(zip("icons/shared.png" to byteArrayOf(1)))
        assertRejected(missing, "manifest.json")
    }

    @Test fun unknownStreamedEntrySizeCannotBypassPerFileLimit() {
        // Deflated ZIP entries written this way declare their size only after their data.
        assertReadFails("byte limit") {
            PresetArchiveReader.read(zip("icons/large.png" to ByteArray(129)), smallLimits)
        }
    }

    @Test fun manifestHasItsOwnSmallerLimit() {
        assertReadFails("manifest.json") {
            PresetArchiveReader.read(zip("manifest.json" to ByteArray(65)), smallLimits)
        }
    }

    @Test fun manyIndividuallyValidFilesCannotExceedTotalExpandedBudget() {
        assertReadFails("expanded size limit") {
            PresetArchiveReader.read(zip(
                "icons/a.png" to ByteArray(100),
                "icons/b.png" to ByteArray(100),
                "icons/c.png" to ByteArray(100)
            ), smallLimits)
        }
    }

    @Test fun fileCountLimitIncludesEmptyDirectories() {
        assertReadFails("too many files") {
            PresetArchiveReader.read(zip(
                "first/" to byteArrayOf(),
                "second/" to byteArrayOf(),
                "third/" to byteArrayOf(),
                "fourth/" to byteArrayOf(),
                "fifth/" to byteArrayOf()
            ), smallLimits)
        }
    }

    @Test fun directoryPayloadCannotBypassExpandedBudget() {
        assertReadFails("byte limit") {
            PresetArchiveReader.read(zip("icons/" to ByteArray(129)), smallLimits)
        }
    }

    @Test fun exactLimitsAreAcceptedAndDirectoriesAreNotRetained() {
        val entries = PresetArchiveReader.read(zip(
            "icons/" to byteArrayOf(),
            "icons/a.png" to ByteArray(128),
            "icons/b.png" to ByteArray(128)
        ), smallLimits)
        assertEquals(setOf("icons/a.png", "icons/b.png"), entries.keys)
        assertEquals(128, entries.getValue("icons/a.png").size)
    }

    @Test fun duplicateNamesAreRejectedInsteadOfSilentlyReplacingData() {
        val bytes = zip("aa" to byteArrayOf(1), "bb" to byteArrayOf(2)).readBytes()
        // Replace the second entry's equal-length name in local and central ZIP headers.
        for (index in 0 until bytes.size - 1) {
            if (bytes[index] == 'b'.code.toByte() && bytes[index + 1] == 'b'.code.toByte()) {
                bytes[index] = 'a'.code.toByte()
                bytes[index + 1] = 'a'.code.toByte()
            }
        }
        assertReadFails("duplicate file") {
            PresetArchiveReader.read(ByteArrayInputStream(bytes), smallLimits)
        }
    }

    @Test fun cancellationInterruptsReadingAnEntry() {
        var checks = 0
        val canceled = IllegalStateException("canceled")
        try {
            PresetArchiveReader.read(zip("icons/a.png" to ByteArray(64)), smallLimits) {
                checks++
                if (checks == 3) throw canceled
            }
            fail("Expected cancellation")
        } catch (error: IllegalStateException) {
            assertSame(canceled, error)
        }
    }

    @Test fun sharedArtworkIsWrittenOncePerImportedBundle() {
        val cache = mutableMapOf<String, String?>()
        var writes = 0
        repeat(3) {
            assertEquals("stored-icon.png", PresetArchiveTransfer.cachedImportedIcon("shared.png", cache) {
                writes++
                "stored-icon.png"
            })
        }
        assertEquals(1, writes)
        assertEquals("other-icon.png", PresetArchiveTransfer.cachedImportedIcon("other.png", cache) {
            writes++
            "other-icon.png"
        })
        assertEquals(2, writes)
    }

    @Test fun missingArtworkIsNotRetriedForEveryPreset() {
        val cache = mutableMapOf<String, String?>()
        var attempts = 0
        repeat(3) {
            assertNull(PresetArchiveTransfer.cachedImportedIcon("missing.png", cache) {
                attempts++
                null
            })
        }
        assertEquals(1, attempts)
    }

    private fun validated(manifest: String): PresetArchiveTransfer.ValidatedArchive =
        PresetArchiveTransfer.readValidatedArchive(zip(
            "manifest.json" to manifest.toByteArray(Charsets.UTF_8),
            "icons/shared.png" to byteArrayOf(1, 2, 3)
        ))

    private fun assertRejected(archive: PresetArchiveTransfer.ValidatedArchive, message: String) {
        assertNull(archive.manifest)
        assertTrue(archive.entries.isEmpty())
        assertTrue(archive.errors.any { message in it })
    }

    private fun assertReadFails(message: String, action: () -> Unit) {
        try {
            action()
            fail("Expected an archive read failure")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains(message))
        }
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
