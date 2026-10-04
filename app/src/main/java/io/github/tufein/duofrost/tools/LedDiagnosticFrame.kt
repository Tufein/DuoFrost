package io.github.tufein.duofrost.tools

data class LedDiagnosticFrame(val label: String, val left: Int, val right: Int) {
    fun colorAt(zone: Int, outputLimit: Int): Int = BatterySaverBrightness.limitRgb(
        if (zone < 2) left else right, minOf(outputLimit.coerceIn(0, 255), 64)
    )

    companion object {
        val steps = listOf(
            LedDiagnosticFrame("Red", 0xff0000, 0xff0000),
            LedDiagnosticFrame("Green", 0x00ff00, 0x00ff00),
            LedDiagnosticFrame("Blue", 0x0000ff, 0x0000ff),
            LedDiagnosticFrame("White", 0xffffff, 0xffffff),
            LedDiagnosticFrame("Left stick only", 0xffffff, 0),
            LedDiagnosticFrame("Right stick only", 0, 0xffffff)
        )
    }
}
