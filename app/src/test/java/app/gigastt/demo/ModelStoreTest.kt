package app.gigastt.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Верификация источника диаризации: скачивание идёт через боевой [ModelStore.fetch],
 * поэтому проверяются и HTTP-статус (404 упал бы), и итоговый SHA-256 файла.
 */
class ModelStoreTest {

    private val diarization = ModelStore.FILES.single { it.localName == "wespeaker_resnet34.onnx" }

    @Test
    fun diarizationEntryUsesHuggingFaceUrlAndLocalName() {
        assertEquals(
            "https://huggingface.co/onnx-community/wespeaker-voxceleb-resnet34-LM/resolve/main/onnx/model.onnx",
            diarization.downloadUrl
        )
        assertEquals(
            "3955447b0499dc9e0a4541a895df08b03c69098eba4e56c02b5603e9f7f4fcbb",
            diarization.sha256
        )
    }

    @Test
    fun diarizationUrlReturnsHttp200AndMatchingSha256() {
        val tmp = File.createTempFile("wespeaker", ".onnx")
        try {
            try {
                ModelStore.fetch(diarization.downloadUrl, tmp, diarization.localName) {}
            } catch (e: UnknownHostException) {
                skipOffline(e)
                return
            } catch (e: ConnectException) {
                skipOffline(e)
                return
            } catch (e: SocketTimeoutException) {
                skipOffline(e)
                return
            } catch (e: IOException) {
                throw AssertionError(
                    "Загрузка с ${diarization.downloadUrl} не удалась: ${e.message}", e
                )
            }
            assertTrue("Файл пуст", tmp.length() > 0)
            assertEquals(diarization.sha256, ModelStore.sha256(tmp))
        } finally {
            tmp.delete()
        }
    }

    private fun skipOffline(e: IOException) {
        assumeTrue("Пропуск: Hugging Face недоступен (${e.message})", false)
    }
}
