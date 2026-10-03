package io.github.tufein.duofrost.plugins

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the baked-in default catalogue URL. The store came up empty once because
 * this pointed at a repo that didn't exist; this guards against silently
 * regressing it to a dead/wrong host again.
 */
class PluginPrefsDefaultsTest {

    @Test fun defaultCatalogUrlPointsAtDuoFrostRepository() {
        val url = PluginPrefs.DEFAULT_CATALOG_URL
        assertTrue("must be https", url.startsWith("https://"))
        assertTrue("must be a raw GitHub URL", url.startsWith("https://raw.githubusercontent.com/"))
        assertTrue("must be the DuoFrost repo", url.contains("/Tufein/DuoFrost/"))
        assertTrue("must be served from the main branch", url.contains("/main/plugins/"))
        assertTrue("must resolve to the catalogue document", url.endsWith("/catalog.json"))
    }
}
