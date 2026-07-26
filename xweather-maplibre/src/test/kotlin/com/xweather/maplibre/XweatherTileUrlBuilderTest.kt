package com.xweather.maplibre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XweatherTileUrlBuilderTest {

    private val config = XweatherConfig(
        clientId = "testClientId",
        clientSecret = "testClientSecret",
        subdomains = listOf("1", "2"),
    )

    @Test
    fun `builds one url template per subdomain`() {
        val urls = XweatherTileUrlBuilder(config).tileUrlTemplates(XweatherLayer.Radar.Standard)

        assertEquals(2, urls.size)
        assertTrue(urls[0].startsWith("https://maps1.api.xweather.com/"))
        assertTrue(urls[1].startsWith("https://maps2.api.xweather.com/"))
    }

    @Test
    fun `embeds credentials and layer code in path`() {
        val url = XweatherTileUrlBuilder(config)
            .tileUrlTemplates(XweatherLayer.Radar.Standard)
            .first()

        assertTrue(url.contains("testClientId_testClientSecret"))
        assertTrue(url.endsWith("/radar/{z}/{x}/{y}/current.png"))
    }

    @Test
    fun `supports a custom offset`() {
        val url = XweatherTileUrlBuilder(config)
            .tileUrlTemplates(XweatherLayer.Radar.Standard, offset = "-30m")
            .first()

        assertTrue(url.endsWith("/-30m.png"))
    }

    @Test
    fun `custom layer codes are passed through`() {
        val url = XweatherTileUrlBuilder(config)
            .tileUrlTemplates(XweatherLayer.Custom("some-new-layer"))
            .first()

        assertTrue(url.contains("/some-new-layer/{z}/{x}/{y}/current.png"))
    }
}
