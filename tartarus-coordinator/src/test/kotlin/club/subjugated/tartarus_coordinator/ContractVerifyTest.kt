package club.subjugated.tartarus_coordinator

import club.subjugated.tartarus_coordinator.util.ValidatedPayload.ContractPayload
import club.subjugated.tartarus_coordinator.util.signedMessageBytesValidator
import java.nio.ByteBuffer
import java.security.Security
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Test

class ContractVerifyTest {
    @Test
    fun verifyContract() {
        Security.addProvider(BouncyCastleProvider())
        val signedMessageText =
            "EAAAAAAACgAQAAQACwAMAAoAAAAMAAAAAAAAAWQAAABAAAAAKJTFNQA8rWZPwHHSqrCNh/977VgVL0NTYaaq6sw9z1J6nOh4eFgSdH8xjBDQQsFZIYwItLpZF6gcy+Hhr3N65RwAAAAYABQAEAAAAAAAAAAAAAAABwAIAAAADwAYAAAAAAAAAhAAAAAAAAABDAAAAAQABAAEAAAAIQAAAAOLUWif/NV39e7fTrpo/O0Cwkp00mZNTYbM0zzjqP8hoAAAAA=="

        val decodedData = Base64.getDecoder().decode(signedMessageText)
        val buf = ByteBuffer.wrap(decodedData)

        val maybeContract = signedMessageBytesValidator(buf)
        assertThat(maybeContract).isNotNull()
        assertThat(maybeContract).isInstanceOf(ContractPayload::class.java)
    }
}
