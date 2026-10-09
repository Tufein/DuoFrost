package io.github.tufein.duofrost.scenes

import io.github.tufein.duofrost.services.AppProfileSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneSelectionPolicyTest {
    private val evaluation = SceneEvaluation("scene", null, 20, listOf("Night"), 50)

    @Test fun temporaryChoiceWinsButKeepsGlobalDimming() {
        val choice = SceneSelectionPolicy.choose("held", AppProfileSelection.Source.APP_MAPPING, "app", evaluation, "base")
        assertEquals("held", choice.presetId)
        assertEquals(50, SceneSelectionPolicy.brightnessLimit(choice, evaluation))
    }

    @Test fun exactAppProfileWinsOverBroadSceneWithoutBorrowingItsCap() {
        val choice = SceneSelectionPolicy.choose(null, AppProfileSelection.Source.APP_MAPPING, "app", evaluation, "base")
        assertEquals("app", choice.presetId)
        assertEquals(50, SceneSelectionPolicy.brightnessLimit(choice, evaluation))
    }

    @Test fun smartRuleWinsOverGeneralGameSceneAndUsesOwnCap() {
        val choice = SceneSelectionPolicy.choose(null, AppProfileSelection.Source.GAME_SCENE, "game", evaluation, "base")
        assertEquals("scene", choice.presetId)
        assertEquals(20, SceneSelectionPolicy.brightnessLimit(choice, evaluation))
    }

    @Test fun missingLegacyFallbackRetainsItsOffSemantics() {
        val choice = SceneSelectionPolicy.choose(null, AppProfileSelection.Source.FALLBACK, null,
            SceneEvaluation(null, null, null, emptyList()), "base")
        assertNull(choice.presetId)
        assertEquals(SceneSelectionPolicy.Source.DEFAULT, choice.source)
    }

    @Test fun noAutomationReturnsManualBaseline() {
        val choice = SceneSelectionPolicy.choose(null, null, null, SceneEvaluation(null, null, null, emptyList()), "base")
        assertEquals("base", choice.presetId)
        assertEquals(SceneSelectionPolicy.Source.MANUAL, choice.source)
    }
}
