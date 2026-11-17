package app.gigastt.demo

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Локальное хранилище и одноразовая загрузка бандла GigaAM v3 RNN-T
 * плюс модели WeSpeaker для диаризации.
 */
object ModelStore {
    private const val BASE_GITHUB =
        "https://github.com/ekhodzitsky/gigastt/releases/download/models-v3-2026-10-09"

    // Прямая ссылка на файл модели WeSpeaker в репозитории Hugging Face
    private const val WESPEAKER_URL =
        "https://huggingface.co/onnx-community/wespeaker-voxceleb-resnet34-LM/resolve/main/onnx/model.onnx"

    internal data class ModelFile(
        val localName: String,
        val sha256: String,
        val downloadUrl: String
    )

    // internal ради верификационного юнит-теста (ModelStoreTest)
    internal val FILES = listOf(
        ModelFile(
            "v3_e2e_rnnt_encoder_int8.onnx",
            "16805903b102d8b27bb0253de044f25e33a8a5bf07fce0c4da4ac1a5a7544091",
            "$BASE_GITHUB/v3_e2e_rnnt_encoder_int8.onnx"
        ),
        ModelFile(
            "v3_e2e_rnnt_decoder.onnx",
            "7b0a16d67fd2cb37061decc93c69e364a9ab27afee3c57495d55b1c974cf7231",
            "$BASE_GITHUB/v3_e2e_rnnt_decoder.onnx"
        ),
        ModelFile(
            "v3_e2e_rnnt_joint.onnx",
            "602ff7017a93311aad34df1437c8d7f49911353c13d6eae7a6ee7b041339465c",
            "$BASE_GITHUB/v3_e2e_rnnt_joint.onnx"
        ),
        ModelFile(
            "v3_e2e_rnnt_vocab.txt",
            "39abae20e692998290c574e606f11a9edef2902a1995463fcff63d1490cf22b7",
            "$BASE_GITHUB/v3_e2e_rnnt_vocab.txt"
        ),
        // Диаризация: скачивается как model.onnx с HF, но на устройство сохраняется как wespeaker_resnet34.onnx
        ModelFile(
            "wespeaker_resnet34.onnx",
            "3955447b0499dc9e0a4541a895df08b03c69098eba4e56c02b5603e9f7f4fcbb",
            WESPEAKER_URL
        )
    )

    // Требуемое свободное место (~800 МБ с запасом под RNN-T и веса WeSpeaker)
    private const val NEEDED_BYTES = 800L * 1024 * 1024

    fun dir(ctx: Context) = File(ctx.filesDir, "gigastt_models")

    /** Файлы переименовываются в целевые только после успешной проверки SHA-256, поэтому наличие == верификация. */
    fun isReady(ctx: Context): Boolean =
        FILES.all { file -> File(dir(ctx), file.localName).let { it.isFile && it.length() > 0 } }

    /** Блокирующий метод. Обязательно вызывать из фонового потока (worker). */
    fun download(ctx: Context, onProgress: (String) -> Unit) {
        val dir = dir(ctx).apply { mkdirs() }
        if (dir.usableSpace < NEEDED_BYTES) {
            throw IOException("Недостаточно свободного места: нужно ~800 МБ")
        }
        for (file in FILES) {
            val target = File(dir, file.localName)
            if (target.isFile && target.length() > 0) continue
            val part = File(dir, "${file.localName}.part")

            fetch(file.downloadUrl, part, file.localName, onProgress)

            val actual = sha256(part)
            if (!actual.equals(file.sha256, ignoreCase = true)) {
                part.delete()
                throw IOException("Контрольная сумма ${file.localName} не совпала — файл повреждён, повторите загрузку")
            }
            if (!part.renameTo(target)) throw IOException("Не удалось сохранить ${file.localName}")
        }
    }

    internal fun fetch(url: String, dest: File, label: String, onProgress: (String) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true // Поддержка редиректов CDN (GitHub/HuggingFace)
        try {
            if (conn.responseCode != 200) {
                throw IOException("Сервер вернул HTTP ${conn.responseCode} для $label")
            }
            val total = conn.contentLengthLong
            var done = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 400) {
                            lastReport = now
                            val mb = done / (1024 * 1024)
                            onProgress(
                                if (total > 0) "Загрузка $label: ${done * 100 / total}% ($mb/${total / (1024 * 1024)} МБ)"
                                else "Загрузка $label: $mb МБ"
                            )
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Загрузка $label оборвалась")
        } finally {
            conn.disconnect()
        }
    }

    internal fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}