package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.util.UUID

class FotoEmpleadoStoreTest {
    private lateinit var dir: File
    private lateinit var origen: File
    private lateinit var store: JsonDataStore

    @BeforeEach
    fun setUp() {
        val tmpRoot = System.getProperty("java.io.tmpdir")
        dir = File(tmpRoot, "test_fotos_" + UUID.randomUUID().toString().replace("-", ""))
        dir.mkdirs()
        origen = File(dir, "mi foto.JPG")
        origen.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        store = JsonDataStore(File(dir, "datos").path)
    }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun guarda_la_foto_con_nombre_propio_y_se_puede_leer() {
        val rel = store.saveEmployeePhoto("abc1234", origen.path)
        assertTrue(rel.startsWith("foto_abc1234_"))
        assertTrue(rel.endsWith(".jpg"))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), store.loadEmployeePhotoBytes(rel))
    }

    @Test
    fun cada_cambio_de_foto_genera_un_archivo_nuevo_y_conserva_el_anterior() {
        val a = store.saveEmployeePhoto("abc1234", origen.path)
        val b = store.saveEmployeePhoto("abc1234", origen.path)
        assertTrue(a != b)
        assertNotNull(store.loadEmployeePhotoBytes(a))
        assertNotNull(store.loadEmployeePhotoBytes(b))
    }

    @Test
    fun rechaza_formatos_que_no_son_png_o_jpg() {
        val gif = File(dir, "x.gif")
        gif.writeBytes(byteArrayOf(9))
        assertThrows(IllegalStateException::class.java) {
            store.saveEmployeePhoto("abc1234", gif.path)
        }
    }

    @Test
    fun el_id_se_limpia_para_que_no_pueda_escapar_de_la_carpeta() {
        val rel = store.saveEmployeePhoto("../../mal id", origen.path)
        assertFalse(rel.contains("/"))
        assertFalse(rel.contains("\\"))
        assertFalse(rel.contains(".."))
        assertNotNull(store.loadEmployeePhotoBytes(rel))
    }

    @ParameterizedTest
    @CsvSource(
        "NULL",
        "''",
        "'   '",
        "'no_existe.jpg'",
        "'../data.json'",
        "'sub/dir.jpg'"
    )
    fun leer_rutas_invalidas_o_inexistentes_devuelve_null(rel: String) {
        val r = if (rel == "NULL") null else rel
        assertNull(store.loadEmployeePhotoBytes(r))
    }
}
