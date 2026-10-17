package com.hdlee73.englishstudy.docvoice.core

/**
 * Edge 읽어주기(TTS) 접속용 Sec-MS-GEC 토큰 생성기(프로토콜 버전 2).
 *
 * 예전에는 "(현재 시각을 5분 단위로 내림) + 클라이언트 토큰" 문자열의 일반 SHA-256 해시를 그대로 보냈지만,
 * 지금의 서버는 입력 첫 7바이트에서 초기 상태를 유도하는 변형 SHA-256 과 자체 64비트 해시(CS64)를 조합한
 * 값을 요구한다. 값이 맞지 않으면 WebSocket 핸드셰이크가 403 으로 거부된다.
 *
 * 그래서 여기서는 [java.security.MessageDigest] 를 쓰지 않고 변형 해시를 직접 구현한다.
 * 알고리즘은 공식 클라이언트 구현(edge-tts)과 같은 값을 내며, 실제 서버와의 핸드셰이크(101)로 검증했다.
 */
internal object SecMsGec {

    /** 서버가 최신 클라이언트로 인정하는 Chromium 버전 표기. 값이 낡으면 403 이 날 수 있다. */
    const val CHROMIUM_FULL = "143.0.3650.75"

    /** 쿼리 문자열에 넣는 버전 표기. 접두사 `2-` 가 필요하다. */
    val version: String get() = "2-$CHROMIUM_FULL"

    private const val WIN_EPOCH = 11644473600L
    private const val WINDOW_SECONDS = 300L

    // 0x80000000 이상인 상수는 Long 리터럴이 되지 않도록 toInt() 로 고정한다.
    private val ADJ = 0xC9DC06A7.toInt()
    private val SIGMA0_ADJ = 0x059C0B80.toInt()
    private val SIGMA1_ADJ = 0x000B92C0.toInt()
    private val FACTOR = 0x82893933.toInt()
    private val ADJ0 = 0xF2F90B1A.toInt()
    private val ADJ1 = 0x0B1A0000.toInt()
    private val MUL_C3EFEA97 = 0xC3EFEA97.toInt()
    private val MUL_CF98B111 = 0xCF98B111.toInt()
    private val MUL_87085B9F = 0x87085B9F.toInt()
    private val MUL_A27416F5 = 0xA27416F5.toInt()
    private val MUL_D38396FF = 0xD38396FF.toInt()
    private val MUL_BFA49F69 = 0xBFA49F69.toInt()
    private val WORD_MUL_EVEN = 0xEF0569FB.toInt()
    private val WORD_MUL_ODD = 0xC31713DB.toInt()
    private val MUL_76F50F34 = 0x76F50F34
    private val MUL_1881CE3C = 0x1881CE3C
    private val MUL_35BD1EC9 = 0x35BD1EC9
    private val MUL_12CEB96D = 0x12CEB96D
    private val MUL_257E1D83 = 0x257E1D83
    private val MUL_7C932B89 = 0x7C932B89

    private fun ints(vararg values: Long): IntArray = IntArray(values.size) { values[it].toInt() }

    /** 변형 SHA-256 의 출력 오프셋(표준 IV). */
    private val SHA256_INITIAL = ints(
        0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C,
        0x1F83D9AB, 0x5BE0CD19,
    )

    /** 변형 SHA-256 의 초기 상태(첫 블록 계산용). */
    private val SHA256_STATE = ints(
        0xFD9FBEA2, 0x91243CE7, 0xBE499094, 0xCD2E1E0A, 0x6C006F49, 0x04BD35AB,
        0xFEB0A7E4, 0x03EFFBD2,
    )

    private val SHA256_SCHEDULE = ints(
        0xEB24BF9E, 0x1558691F, 0xA0C09EE2, 0x0CFA32BA, 0xA622EE8C, 0xD1DD9491,
        0xC0DD25A0, 0xF9E70AB6, 0xF0C31573, 0x7058CE4E, 0x7188E606, 0x00E5AB33,
        0xD258B060, 0x72766564,
    )

    private val SHA256_ROUND = ints(
        0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1,
        0x923F82A4, 0xAB1C5ED5, 0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3,
        0x72BE5D74, 0x80DEB1FE, 0x9BDC06A7, 0xC19BF174, 0xE49B69C1, 0xEFBE4786,
        0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA,
        0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7, 0xC6E00BF3, 0xD5A79147,
        0x06CA6351, 0x14292967, 0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13,
        0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85, 0xA2BFE8A1, 0xA81A664B,
        0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070,
        0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A,
        0x5B9CCA4F, 0x682E6FF3, 0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208,
        0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7, 0xC67178F2,
    )

    /** 첫 블록 전용 라운드 상수. 14번째 값만 다르다. */
    private val SHA256_BLOCK = IntArray(64) { if (it == 14) ADJ else SHA256_ROUND[it] }

    private val CS64_COEFF = ints(
        0xE82E91B1, 0x44DE24C6, 0x3BEB65BC, 0xC89C5933, 0x8D1EC211, 0x314EF5D5,
        0xE92491B1, 0xF0A5B9D5, 0x06064CF3, 0xDA79FA60, 0xBED20560, 0x89F9C9F8,
        0x35FA38C6, 0xCB49E3C6,
    )

    private val CS64_OFFSET = ints(
        0xB32700D0, 0x88DFF51D, 0x29514038, 0xB328CBDF, 0xD405B4D5, 0x26E55F3C,
        0x607801C6, 0x82B2C1FB, 0xFA51A3DA, 0x207CC464, 0x3C3C74F3, 0xD2D14B61,
        0xBB04A2C1, 0xC5626167,
    )

    /**
     * `Sec-MS-GEC` 쿼리 값. 형식은 `CS64(16진수 16자)-변형SHA256(16진수 64자)`.
     *
     * @param token 빌드/앱에서 받은 클라이언트 토큰
     * @param nowMillis 현재 시각(ms)
     * @param skewSeconds 서버 시각과의 차이(있으면 보정)
     */
    fun generate(token: String, nowMillis: Long, skewSeconds: Long = 0L): String {
        var seconds = nowMillis / 1000L + skewSeconds + WIN_EPOCH
        seconds -= seconds % WINDOW_SECONDS
        val ticks = seconds * 10_000_000L
        val data = "$ticks$token".toByteArray(Charsets.US_ASCII)
        val digest = modifiedSha256(data)
        val words = cs64(data, digest)
        val csBytes = ByteArray(8)
        putLe32(csBytes, 0, words[0])
        putLe32(csBytes, 4, words[1])
        return csBytes.toHexUpper() + "-" + digest.toHexUpper()
    }

    // ---- CS64 (자체 64비트 해시) ----

    private fun cs64(data: ByteArray, digest: ByteArray): IntArray {
        val keyA = readLe32(digest, 0) or 1
        val keyB = readLe32(digest, 4) or 1
        val factors = intArrayOf(keyA * FACTOR, keyB * FACTOR)

        val terms = ArrayList<Int>(64)
        val offsets = ArrayList<Int>(64)
        for (i in CS64_COEFF.indices) {
            terms.add(CS64_COEFF[i] * factors[i % 2])
            offsets.add(CS64_OFFSET[i])
        }

        // 원본은 데이터 앞에 0x00 한 바이트를 붙여 4바이트 리틀엔디언 워드로 읽는다.
        val salted = ByteArray(data.size + 1)
        System.arraycopy(data, 0, salted, 1, data.size)
        val words = leWords(salted)
        for (index in words.indices) {
            val word = words[index]
            val even = index % 2 == 0
            var term = word * (if (even) keyA else keyB)
            var offset = rol16(word) * (if (even) WORD_MUL_EVEN else WORD_MUL_ODD)
            if (index == 0) {
                term += ADJ0 * factors[0]
                offset += ADJ1
            }
            terms.add(term)
            offsets.add(offset)
        }

        var swapAcc = 0
        var swapLast = 0
        var state = 0
        var acc = 0
        var i = 0
        while (i < terms.size) {
            val x = terms[i]
            val y = terms[i + 1]

            var t = rol16(offsets[i] + x)
            var q = t * MUL_76F50F34
            swapAcc += rol16(q) * MUL_C3EFEA97 + q
            t = rol16(offsets[i + 1] + y)
            q = t * MUL_1881CE3C
            swapLast = rol16(q) * MUL_35BD1EC9 + q
            swapAcc += swapLast

            state = state * keyA + x
            state = rol16(state) * MUL_CF98B111
            state = rol16(state) * MUL_87085B9F
            state = rol16(state) * MUL_12CEB96D
            state = rol16(state) * MUL_257E1D83
            acc += state

            var t2 = state * keyB + y
            t2 = rol16(t2) * MUL_A27416F5
            t2 = rol16(t2) * MUL_D38396FF
            state = rol16(rol16(t2) * MUL_7C932B89) * MUL_BFA49F69
            acc += state

            i += 2
        }
        return intArrayOf(swapLast xor state, swapAcc xor acc)
    }

    /** 4바이트 리틀엔디언 워드 목록(홀수 개면 0 을 덧붙인다). */
    private fun leWords(data: ByteArray): IntArray {
        if (data.isEmpty()) return IntArray(0)
        val pad = (4 - data.size % 4) % 4
        val padded = ByteArray(data.size + pad)
        System.arraycopy(data, 0, padded, 0, data.size)
        val count = padded.size / 4
        val words = IntArray(if (count % 2 == 1) count + 1 else count)
        for (i in 0 until count) words[i] = readLe32(padded, i * 4)
        return words
    }

    // ---- 변형 SHA-256 ----

    private fun modifiedSha256(data: ByteArray): ByteArray {
        require(data.size >= 7) { "토큰 입력은 7바이트 이상이어야 합니다" }
        val length = 57 + data.size
        val zeroPad = ((55 - length) % 64 + 64) % 64
        val body = ByteArray(data.size - 7)
        System.arraycopy(data, 7, body, 0, body.size)
        val padded = ByteArray(body.size + 1 + zeroPad + 8)
        System.arraycopy(body, 0, padded, 0, body.size)
        padded[body.size] = 0x80.toByte()
        val bitLength = length.toLong() * 8
        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = (bitLength ushr (8 * (7 - i))).toByte()
        }

        var state = initState(data)
        var offset = 0
        while (offset < padded.size) {
            val words = IntArray(64)
            for (i in 0 until 16) words[i] = readBe32(padded, offset + i * 4)
            expandSchedule(words, 16)
            val working = rounds(state, words, 0)
            state = IntArray(8) { state[it] + working[it] }
            offset += 64
        }

        val out = ByteArray(32)
        for (i in 0 until 8) putBe32(out, i * 4, state[i])
        return out
    }

    /** 입력 첫 7바이트로 초기 상태를 만든다(표준 IV 를 쓰지 않는 이유). */
    private fun initState(data: ByteArray): IntArray {
        val words = IntArray(64)
        words[14] = ((data[0].toInt() and 0xFF) shl 16) or
            ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        words[15] = readBe32(data, 3)
        for (index in 16 until 30) {
            var value = SHA256_SCHEDULE[index - 16]
            if (index - 2 >= 14) {
                val s1 = sigma1(words[index - 2])
                value += if (index == 16) s1 xor SIGMA1_ADJ else s1
            }
            if (index - 7 >= 14) value += words[index - 7]
            if (index - 15 >= 14) {
                val s0 = sigma0(words[index - 15])
                value += if (index == 29) s0 xor SIGMA0_ADJ else s0
            }
            words[index] = value
        }
        words[30] = sigma1(words[28]) + words[23] + sigma0(words[15]) + words[14] + ADJ - SHA256_ROUND[14]
        expandSchedule(words, 31)
        val working = rounds(SHA256_STATE, words, 14)
        return IntArray(8) { SHA256_INITIAL[it] + working[it] }
    }

    private fun expandSchedule(words: IntArray, first: Int) {
        for (index in first until 64) {
            words[index] = sigma1(words[index - 2]) + words[index - 7] +
                sigma0(words[index - 15]) + words[index - 16]
        }
    }

    private fun rounds(state: IntArray, words: IntArray, firstRound: Int): IntArray {
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        val constants = if (firstRound == 14) SHA256_BLOCK else SHA256_ROUND
        for (i in firstRound until 64) {
            val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
            val ch = g xor (e and (f xor g))
            val t1 = h + s1 + ch + constants[i] + words[i]
            val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
            val maj = (a and b) or (c and (a or b))
            val t2 = s0 + maj
            h = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }
        return intArrayOf(a, b, c, d, e, f, g, h)
    }

    private fun sigma0(value: Int): Int =
        (value ushr 7) xor (value shl 25) xor (value ushr 18) xor (value shl 14) xor (value ushr 3)

    private fun sigma1(value: Int): Int =
        (value ushr 17) xor (value shl 15) xor (value ushr 19) xor (value shl 13) xor (value ushr 10)

    private fun rotr(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))

    private fun rol16(value: Int): Int = (value shl 16) or (value ushr 16)

    private fun readBe32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun readLe32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun putLe32(data: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) data[offset + i] = (value ushr (8 * i)).toByte()
    }

    private fun putBe32(data: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) data[offset + i] = (value ushr (8 * (3 - i))).toByte()
    }

    private fun ByteArray.toHexUpper(): String {
        val out = StringBuilder(size * 2)
        for (b in this) out.append(HEX[(b.toInt() ushr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
