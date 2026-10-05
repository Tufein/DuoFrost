package io.github.tufein.duofrost

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ArchiveSchemaProbeTest {
    @Test fun manifestFirstBackupDoesNotExpandFollowingLargePayload() {
        val bytes = zip(
            "manifest.json" to manifest("bifrost_full_backup"),
            "large-user-data.bin" to ByteArray(9 * 1024 * 1024)
        )
        // A preset import would reject the 9 MiB expanded file. Schema dispatch
        // must stop before it, preserving the full-backup importer's own policy.
        val result = ArchiveSchemaProbe.read(ByteArrayInputStream(bytes))
        assertEquals(ArchiveSchemaProbe.Type.BACKUP, result.type)
        assertTrue(result.errors.isEmpty())
    }

    @Test fun reasonableReorderedPresetArchiveStillDispatchesCorrectly() {
        val result = ArchiveSchemaProbe.read(ByteArrayInputStream(zip(
            "icons/" to byteArrayOf(),
            "icons/shared.png" to ByteArray(100),
            "manifest.json" to manifest("bifrost_preset_bundle")
        )))
        assertEquals(ArchiveSchemaProbe.Type.PRESET, result.type)
        assertTrue(result.errors.isEmpty())
    }

    @Test fun oversizedPayloadBeforePresetManifestIsBoundedDuringProbe() {
        assertProbeFails("byte limit", zip(
            "icons/large.png" to ByteArray(8 * 1024 * 1024 + 1),
            "manifest.json" to manifest("bifrost_preset_bundle")
        ))
    }

    @Test fun tooManyEntriesBeforeManifestAreRejected() {
        val entries = (0 until 513).map { "dir-$it/" to byteArrayOf() }.toTypedArray()
        assertProbeFails("too many files", zip(*entries))
    }

    @Test fun manifestLimitAppliesToBackupAndPresetSchemas() {
        assertProbeFails("manifest.json", zip("manifest.json" to ByteArray(2 * 1024 * 1024 + 1)))
    }

    @Test fun malformedUnknownOrMissingManifestCannotChooseAnImporter() {
        for (bytes in listOf(
            zip("manifest.json" to "not JSON".toByteArray()),
            zip("manifest.json" to manifest("unrecognized")),
            zip("icons/a.png" to byteArrayOf(1))
        )) {
            val result = ArchiveSchemaProbe.read(ByteArrayInputStream(bytes))
            assertNull(result.type)
            assertTrue(result.errors.isNotEmpty())
        }
    }

    @Test fun cancellationPropagatesDuringProbe() {
        var checks = 0
        val canceled = IllegalStateException("canceled")
        try {
            ArchiveSchemaProbe.read(ByteArrayInputStream(zip("icons/a.png" to ByteArray(100)))) {
                checks++
                if (checks == 3) throw canceled
            }
            fail("Expected cancellation")
        } catch (error: IllegalStateException) {
            assertSame(canceled, error)
        }
    }

    private fun assertProbeFails(message: String, bytes: ByteArray) {
        try {
            ArchiveSchemaProbe.read(ByteArrayInputStream(bytes))
            fail("Expected archive probe failure")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains(message))
        }
    }

    private fun manifest(schema: String): ByteArray =
        """{"schema":"$schema","version":2}""".toByteArray(Charsets.UTF_8)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
