package io.github.tufein.duofrost.widgets

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class WidgetManifestTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private fun file(path: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            dir?.let { parent ->
                for (prefix in listOf("", "app/")) {
                    val candidate = File(parent, prefix + "src/main/" + path)
                    if (candidate.isFile) return candidate
                }
            }
            dir = dir?.parentFile
        }
        error("Missing $path")
    }
    private fun elements(path: String, tag: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = factory.newDocumentBuilder().parse(file(path)).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    @Test fun widgetMetadataPointsToDeclaredConfigurationAndNoPeriodicUpdates() {
        val info = elements("res/xml/duofrost_widget_info.xml", "appwidget-provider").single()
        assertEquals("0", info.getAttributeNS(ns, "updatePeriodMillis"))
        val configure = info.getAttributeNS(ns, "configure").removePrefix("io.github.tufein.duofrost")
        assertTrue(elements("AndroidManifest.xml", "activity").any { it.getAttributeNS(ns, "name") == configure })
        assertTrue(elements("AndroidManifest.xml", "meta-data").any {
            it.getAttributeNS(ns, "resource") == "@xml/duofrost_widget_info"
        })
    }
    @Test fun timerAndWidgetControlReceiversArePrivate() {
        val receivers = elements("AndroidManifest.xml", "receiver")
        for (name in listOf(".widgets.DuoFrostWidget", ".receivers.SleepTimerReceiver")) {
            val receiver = receivers.single { it.getAttributeNS(ns, "name") == name }
            assertEquals("false", receiver.getAttributeNS(ns, "exported"))
        }
    }
    @Test fun widgetUsesOnlyViewsSupportedByAndroidRemoteViews() {
        val nodes = elements("res/layout/widget_duofrost.xml", "*")
        assertTrue(nodes.isNotEmpty())
        assertTrue(nodes.all { it.tagName in setOf("LinearLayout", "TextView", "Button") })
    }
}
