package com.hdlee73.englishstudy.docvoice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sec-MS-GEC(v2) 토큰이 공식 구현과 같은 값을 내는지 확인한다.
 *
 * 아래 기대값은 공식 클라이언트 구현(edge-tts)으로 같은 입력에 대해 계산한 값이며,
 * 현재 시각 토큰으로 실제 서버 WebSocket 핸드셰이크가 101 Switching Protocols 로 통과하는 것도 확인했다.
 */
class SecMsGecTest {
    private val token = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"

    private val vectors = mapOf(
        1750000000000L to "B029A7426816F097-4579D112DC47FC3BF432B499C0A282F427C659474AF9BF5FA33CE2C003F9E9C2",
        1800000000000L to "99E54519B490BE5C-E162DB0977FBDCB9B86DD8148BF1BCDC3C0303176CE39852A42B415935C20654",
        1893456000000L to "7442B6E630CDF0DE-CE82EC1509335DC856270C1EEB0F7F2575909AC26840D2C15B28A41EBB7E9CCF",
        1600000000123L to "1F5E89103EC98A08-E69D008C1241797258F0EE06F56044C11FAEC4B60F95C6AB0DB3C5E79BE2F010",
    )

    @Test fun matchesReferenceVectors() {
        for ((millis, expected) in vectors) {
            assertEquals("t=$millis", expected, SecMsGec.generate(token, millis))
        }
    }

    @Test fun tokenShape() {
        val t = SecMsGec.generate(token, 1750000000000L)
        assertTrue(t, Regex("^[0-9A-F]{16}-[0-9A-F]{64}$").matches(t))
    }

    @Test fun roundsDownToFiveMinuteWindow() {
        // 1750000000000ms = 1750000000s 는 1749999900~1750000199 구간(5분)에 속한다.
        val base = SecMsGec.generate(token, 1750000000000L)
        assertEquals(base, SecMsGec.generate(token, 1750000199999L))
        assertNotEquals(base, SecMsGec.generate(token, 1750000200000L))
    }

    @Test fun skewMovesTheWindow() {
        assertEquals(
            SecMsGec.generate(token, 1750000000000L),
            SecMsGec.generate(token, 1749999800000L, 200L),
        )
    }

    @Test fun versionIsV2WithChromium() {
        assertEquals("2-${SecMsGec.CHROMIUM_FULL}", SecMsGec.version)
        assertTrue(SecMsGec.version.substringAfter('-').startsWith("143."))
    }

    @Test fun differentTokenGivesDifferentValue() {
        assertNotEquals(
            SecMsGec.generate(token, 1750000000000L),
            SecMsGec.generate("6A5AA1D4EAFF4E9FB37E23D68491D6F5", 1750000000000L),
        )
    }
}
