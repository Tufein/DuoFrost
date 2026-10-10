package io.github.tufein.duofrost.ui.preview

import io.github.tufein.duofrost.animations.LedAnimationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetPreviewSourceTest {
    @Test fun loadsOnlyTheRequestedStableIdentity() {
        val raw = """[{"id":"first","name":"First","animationType":"STATIC"},
            {"id":"second","name":"Second","animationType":"BREATH","brightness":128}]"""
        val found = PresetPreviewSource.find(raw, "second")!!
        assertEquals("Second", found.name)
        assertEquals(LedAnimationType.BREATH, found.animationType)
        assertEquals(128, found.brightness)
        assertEquals("second", found.id)
    }

    @Test fun missingIdentityAndLegacyPresetsDoNotTriggerMigration() {
        val raw = """[{"name":"Legacy","animationType":"STATIC"}]"""
        assertNull(PresetPreviewSource.find(raw, "legacy"))
        assertNull(PresetPreviewSource.find(raw, null))
        assertNull(PresetPreviewSource.find(raw, ""))
    }

    @Test fun malformedOversizedAndUnknownEffectDataCannotProduceAWrongPreview() {
        assertNull(PresetPreviewSource.find("invalid", "test"))
        assertNull(PresetPreviewSource.find(" ".repeat(2_000_001), "test"))
        assertNull(PresetPreviewSource.find("""[{"id":"test","animationType":"FUTURE_EFFECT"}]""", "test"))
    }

    @Test fun duplicateIdentitiesAreRejectedInsteadOfChoosingAnArbitraryPreset() {
        assertNull(PresetPreviewSource.find("""[{"id":"test","animationType":"STATIC"},
            {"id":"test","animationType":"BREATH"}]""", "test"))
    }

    @Test fun legacyAnimationAliasCanStillBeIllustrated() {
        assertEquals(LedAnimationType.AMBIENT,
            PresetPreviewSource.find("""[{"id":"test","animationType":"AMBILIGHT"}]""", "test")?.animationType)
    }
}
