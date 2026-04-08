
import android.os.Build
import androidx.annotation.RequiresApi
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

object LicenseValidator {
    /***
     * 验证许可证
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @Throws(Exception::class)
    fun validateLicense(license: String, publicKey: PublicKey?): Boolean {
        val lines = license.split("\n")
        val dataBuilder = StringBuilder()
        var signatureB64: String? = null

        for (line in lines) {
            when {
                line.startsWith("Signature=") ->
                    signatureB64 = line.substringAfter("Signature=")
                line.isNotEmpty() ->
                    dataBuilder.append(line).append("\n")
            }
        }

        requireNotNull(signatureB64) { "Missing signature in license." }
        val data = dataBuilder.toString().toByteArray()
        val signatureBytes = Base64.getDecoder().decode(signatureB64)

        return Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update(data)
            verify(signatureBytes)
        }
    }
}