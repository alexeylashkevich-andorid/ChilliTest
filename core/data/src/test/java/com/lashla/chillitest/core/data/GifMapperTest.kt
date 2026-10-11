package com.lashla.chillitest.core.data

import com.lashla.chillitest.core.data.dto.GifDto
import com.lashla.chillitest.core.data.dto.ImageDto
import com.lashla.chillitest.core.data.dto.ImagesDto
import org.junit.Assert.*
import org.junit.Test

class GifMapperTest {
    private fun gif(width: String?, height: String?) = GifDto(
        id = "cat",
        title = null,
        url = null,
        username = null,
        images = ImagesDto(
            fixedWidth = ImageDto("https://example.com/preview.gif?token=keep", width, height),
            original = ImageDto("https://example.com/original.gif?token=keep", "400", "600"),
        ),
    )

    @Test fun `non finite dimensions cannot reach the layout`() {
        for (value in listOf("NaN", "Infinity", "-Infinity", "1e99")) {
            val mapped = gif(value, value).toDomainOrNull()!!
            assertTrue("Unsafe ratio for $value", mapped.aspectRatio.isFinite())
            assertTrue(mapped.aspectRatio > 0f)
        }
    }

    @Test fun `missing zero negative and malformed dimensions use safe fallback`() {
        for (value in listOf(null, "0", "-5", "", "invalid")) {
            assertEquals(1f, gif(value, value).toDomainOrNull()!!.aspectRatio, 0.001f)
        }
    }

    @Test fun `optional metadata falls back and media urls remain intact`() {
        val mapped = gif("200", "300").toDomainOrNull()!!
        assertEquals("", mapped.title)
        assertEquals("", mapped.creator)
        assertEquals("", mapped.sourceUrl)
        assertEquals("https://example.com/preview.gif?token=keep", mapped.previewUrl)
        assertEquals("https://example.com/original.gif?token=keep", mapped.originalUrl)
        assertEquals(2f / 3f, mapped.aspectRatio, 0.001f)
    }
}
