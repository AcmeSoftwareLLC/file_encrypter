package np.com.sarbagyastha.file_encrypter

import android.util.Base64
import io.flutter.embedding.engine.plugins.FlutterPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


/** FileEncrypterPlugin */
class FileEncrypterPlugin : FlutterPlugin, FileEncrypterApi {
    private val algorithm = "AES"
    private val transformation = "AES/CBC/PKCS5Padding"

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        FileEncrypterApi.setUp(binding.binaryMessenger, this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        FileEncrypterApi.setUp(binding.binaryMessenger, null)
    }

    override fun encrypt(
        inFileName: String, outFileName: String, callback: (Result<String>) -> Unit
    ) {
        CoroutineScope(IO).launch {
            val cipher = Cipher.getInstance(transformation)
            val secretKey = KeyGenerator.getInstance("AES").generateKey()

            try {
                FileOutputStream(outFileName).use { fileOut ->
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    CipherOutputStream(fileOut, cipher).use { cipherOut ->
                        fileOut.write(cipher.iv)
                        val buffer = ByteArray(8192)
                        FileInputStream(inFileName).use { fileIn ->
                            var byteCount = fileIn.read(buffer)
                            while (byteCount != -1) {
                                cipherOut.write(buffer, 0, byteCount)
                                byteCount = fileIn.read(buffer)
                            }
                        }
                    }
                }

                withContext(Main) {
                    val cipheredText = Base64.encodeToString(secretKey.encoded, Base64.DEFAULT)
                    callback(Result.success(cipheredText))
                }
            } catch (e: Exception) {
                e.printStackTrace()

                withContext(Main) {
                    callback(Result.failure(FileEncrypterError("ENCRYPTION_FAILED", e.message)))
                }
            }
        }
    }

    override fun decrypt(
        key: String, inFileName: String, outFileName: String, callback: (Result<Unit>) -> Unit
    ) {
        CoroutineScope(IO).launch {
            val cipher = Cipher.getInstance(transformation)
            val encodedKey = Base64.decode(key, Base64.DEFAULT)
            val secretKey = SecretKeySpec(encodedKey, 0, encodedKey.size, algorithm)

            try {
                FileInputStream(inFileName).use { fileIn ->
                    val fileIv = ByteArray(16)
                    fileIn.read(fileIv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(fileIv))
                    CipherInputStream(fileIn, cipher).use { cipherIn ->
                        val buffer = ByteArray(8192)
                        FileOutputStream(outFileName).use { fileOut ->
                            var byteCount = cipherIn.read(buffer)
                            while (byteCount != -1) {
                                fileOut.write(buffer, 0, byteCount)
                                byteCount = cipherIn.read(buffer)
                            }
                        }
                    }
                }

                withContext(Main) {
                    callback(Result.success(Unit))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Main) {
                    callback(Result.failure(FileEncrypterError("ENCRYPTION_FAILED", e.message)))
                }
            }
        }
    }
}
