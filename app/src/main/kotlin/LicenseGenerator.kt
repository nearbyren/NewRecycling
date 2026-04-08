import android.os.Build
import androidx.annotation.RequiresApi
import com.serial.port.utils.Loge
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

object LicenseGenerator {
    /***
     * 生成密钥对
     */
    @Throws(Exception::class) fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }

    /***
     * 生成许可证
     */
    @RequiresApi(Build.VERSION_CODES.O) @Throws(Exception::class)
    fun generateLicense(privateKey: PrivateKey?, licensee: String?, maxUsers: Int, expiration: Long): String {
        val licenseBuilder = StringBuilder().apply {
            append("Licensee=$licensee\n")
            append("MaxUsers=$maxUsers\n")
            append("Expiration=$expiration\n")
        }
        val remainingDays = (expiration - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
        Loge.d("自定义有限期证书 剩余有效期天数: $remainingDays 天")
        val data = licenseBuilder.toString().toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(data)
        }.sign()

        val signatureB64 = Base64.getEncoder().encodeToString(signature)
        licenseBuilder.append("Signature=$signatureB64\n")

        return licenseBuilder.toString()
    }

    @RequiresApi(Build.VERSION_CODES.O) fun main() {
        try {
            val keyPair = generateKeyPair()
            val license =
                    generateLicense(privateKey = keyPair.private, licensee = "User11", maxUsers = 10, expiration = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
            Loge.d("自定义有限期证书 Generated License: \n$license")

            val isValid: Boolean = LicenseValidator.validateLicense(license, keyPair.public)
            Loge.d("自定义有限期证书 Is License Valid: $isValid")
            Loge.d("------------------------------------------------------------------------------------------------------------------------------------------------------")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Is License Valid: true
        try {
            // 生成密钥对
            val keyPair = generateKeyPair()

            // 生成合法许可证（数据未篡改）
            val license =
                    generateLicense(privateKey = keyPair.private, licensee = "User1", maxUsers = 10, expiration = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)

            // 使用正确的公钥验证
            val isValid = LicenseValidator.validateLicense(license, keyPair.public)
            Loge.d("自定义有限期证书 Is License Valid: $isValid") // 输出 true
            Loge.d("------------------------------------------------------------------------------------------------------------------------------------------------------")

        } catch (e: Exception) {
            e.printStackTrace()
        }

        //Is License Valid: false
        try {
            // 生成密钥对
            val keyPair = generateKeyPair()

            // 生成原始合法许可证
            val originalLicense =
                    generateLicense(privateKey = keyPair.private, licensee = "User1", maxUsers = 10, expiration = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)

            // 篡改数据：将 MaxUsers=10 改为 MaxUsers=1000
            val tamperedLicense = originalLicense.replace("MaxUsers=10", "MaxUsers=1000")

            // 使用正确的公钥验证篡改后的许可证
            val isValid = LicenseValidator.validateLicense(tamperedLicense, keyPair.public)
            Loge.d("自定义有限期证书 Is License Valid: $isValid") // 输出 false
            Loge.d("------------------------------------------------------------------------------------------------------------------------------------------------------")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}