package club.subjugated.overlord_exe

import club.subjugated.overlord_exe.util.deriveBotKeyPair
import club.subjugated.overlord_exe.util.extractRawScalarFromPrivateKey
import club.subjugated.overlord_exe.util.loadKeyPairFromBase64Pkcs8
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.util.Base64

// @SpringBootTest
class OverlordExeApplicationTests {

	@Test
	fun contextLoads() {
	}

	@Test
	fun `loadKeyPairFromBase64Pkcs8 supports SEC1 PKCS8 and raw 32-byte keys`() {
		val sec1B64 =
			"MHcCAQEEICOlEjRS6mUr9zp2KiPi5gsH9tpefhcz6A/eJ4E9YYlvoAoGCCqGSM49AwEHoUQDQgAEvAV5D//RrShzJycWplQRvh44rJhzkPb1Mprq70K32yrKm2Txyup1OTlHQK2fvF6Ne+Mghfue0oGlYpLLaYIErg=="
		val fromSec1 = loadKeyPairFromBase64Pkcs8(sec1B64)

		val pkcs8B64 = Base64.getEncoder().encodeToString(fromSec1.private.encoded)
		val fromPkcs8 = loadKeyPairFromBase64Pkcs8(pkcs8B64)

		val rawScalarB64 = Base64.getEncoder().encodeToString(extractRawScalarFromPrivateKey(fromSec1.private))
		val fromRaw = loadKeyPairFromBase64Pkcs8(rawScalarB64)

		assertArrayEquals(fromSec1.public.encoded, fromPkcs8.public.encoded)
		assertArrayEquals(fromSec1.public.encoded, fromRaw.public.encoded)

		val botFromSec1 = deriveBotKeyPair(fromSec1.private, "announcer", 1)
		val botFromPkcs8 = deriveBotKeyPair(fromPkcs8.private, "announcer", 1)
		assertArrayEquals(botFromSec1.public.encoded, botFromPkcs8.public.encoded)
	}

}
