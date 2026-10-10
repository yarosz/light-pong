package com.yarosz.pong.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FieldTest {

    @Test
    fun `the field is the LP3's app area`() {
        assertEquals(1080, Field.WIDTH)
        assertEquals(1168, Field.HEIGHT)
    }
}
