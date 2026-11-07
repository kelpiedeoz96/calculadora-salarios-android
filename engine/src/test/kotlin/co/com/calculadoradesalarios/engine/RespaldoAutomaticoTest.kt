package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class RespaldoAutomaticoTest {
    private lateinit var dir: File
    private lateinit var dataDir: File
    private lateinit var extraDir: File

    @BeforeEach
    fun setUp() {
        val tmpRoot = System.getProperty("java.io.tmpdir")
        dir = File(tmpRoot, "nomina_resp_" + UUID.randomUUID().toString().replace("-", ""))
        dir.mkdirs()
        dataDir = File(dir, "datos")
        extraDir = File(dir, "extra")
    }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun preparar(json: String = "{\"state\":{}}") {
        dataDir.mkdirs()
        File(dataDir, "data.json").writeText(json)
        File(dataDir, "foto_e1_1.png").writeBytes(byteArrayOf(1, 2, 3))
        File(dataDir, "viejo.tmp").writeText("x")
        File(dataDir, "data.json.corrupted.123").writeText("x")
    }

    @Test
    fun sinDatos_NoDebeRespaldar_YCrearFalla() {
        dataDir.mkdirs()
        assertFalse(RespaldoAutomatico.DebeRespaldar(dataDir.path, LocalDateTime.now()))
        assertThrows(IllegalStateException::class.java) {
            RespaldoAutomatico.Crear(dataDir.path, LocalDateTime.now())
        }
    }

    @Test
    fun crear_IncluyeDatosYFotos_PeroNoTmpNiCorrupted() {
        preparar()
        val r = RespaldoAutomatico.Crear(dataDir.path, LocalDateTime.now())
        val nombres = mutableListOf<String>()
        ZipFile(File(r.ruta)).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                nombres.add(entries.nextElement().name)
            }
        }
        nombres.sort()
        assertEquals(listOf("data.json", "foto_e1_1.png"), nombres)
        assertNull(r.rutaExtra)
    }

    @Test
    fun debeRespaldar_PrimeraVezSi_LuegoNoHastaQueCambie_YPasePorIntervalo() {
        preparar()
        val ahora = LocalDateTime.now()
        assertTrue(RespaldoAutomatico.DebeRespaldar(dataDir.path, ahora))
        val r = RespaldoAutomatico.Crear(dataDir.path, ahora)
        assertFalse(RespaldoAutomatico.DebeRespaldar(dataDir.path, ahora.plusDays(3)))

        val ultimo = File(r.ruta).lastModified()
        File(dataDir, "data.json").setLastModified(ultimo + 5 * 60 * 1000)
        val t10Min = java.time.Instant.ofEpochMilli(ultimo + 10 * 60 * 1000)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        assertFalse(RespaldoAutomatico.DebeRespaldar(dataDir.path, t10Min))

        val t2Horas = java.time.Instant.ofEpochMilli(ultimo + 2 * 3600 * 1000)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        assertTrue(RespaldoAutomatico.DebeRespaldar(dataDir.path, t2Horas))
    }

    @Test
    fun crear_DosVecesEnElMismoSegundo_NoSobrescribe() {
        preparar()
        val t = LocalDateTime.of(2026, 10, 30, 21, 0, 0)
        val a = RespaldoAutomatico.Crear(dataDir.path, t)
        val b = RespaldoAutomatico.Crear(dataDir.path, t)
        assertTrue(a.ruta != b.ruta)
        assertEquals(2, RespaldoAutomatico.Listar(RespaldoAutomatico.CarpetaDe(dataDir.path)).size)
    }

    @Test
    fun podar_DejaSoloLosMasNuevos() {
        preparar()
        val carpeta = RespaldoAutomatico.CarpetaDe(dataDir.path)
        for (i in 0 until 25) {
            RespaldoAutomatico.Crear(dataDir.path, LocalDateTime.of(2026, 1, 1, 0, 0, 0).plusDays(i.toLong()))
        }
        val lista = RespaldoAutomatico.Listar(carpeta)
        assertEquals(RespaldoAutomatico.MaxRespaldos, lista.size)
        assertTrue(lista[0].name.contains("20260125")) // el más nuevo es el primero
        assertFalse(lista.any { it.name.contains("20260101") })
    }

    @Test
    fun carpetaExtra_RecibeCopia_YErrorNoRompeElRespaldo() {
        preparar()
        val ok = RespaldoAutomatico.Crear(dataDir.path, LocalDateTime.now(), extraDir.path)
        assertNotNull(ok.rutaExtra)
        assertTrue(File(ok.rutaExtra!!).exists())

        val archivo = File(dir, "no_es_carpeta")
        archivo.writeText("x")
        val mal = RespaldoAutomatico.Crear(dataDir.path, LocalDateTime.now().plusSeconds(1), archivo.path)
        assertTrue(File(mal.ruta).exists())
        assertNull(mal.rutaExtra)
        assertFalse(mal.errorExtra.isNullOrEmpty())
    }

    @Test
    fun restaurar_DevuelveLosArchivos_YGuardaCopiaDeLoActual() {
        preparar("{\"state\":{\"v\":1}}")
        val r = RespaldoAutomatico.Crear(dataDir.path, LocalDateTime.now())
        File(dataDir, "data.json").writeText("{\"state\":{\"v\":2}}")
        File(dataDir, "foto_e1_1.png").delete()

        RespaldoAutomatico.Restaurar(r.ruta, dataDir.path)
        assertTrue(File(dataDir, "data.json").readText().contains("\"v\":1"))
        assertTrue(File(dataDir, "foto_e1_1.png").exists())
        assertTrue(File(dataDir, "data.json.antes_de_restaurar").readText().contains("\"v\":2"))
    }

    @Test
    fun restaurar_RechazaZipSinDataJson_YJsonInvalido_SinTocarNada() {
        preparar("{\"state\":{\"v\":1}}")
        val sinData = File(dir, "sin_data.zip")
        ZipOutputStream(FileOutputStream(sinData)).use { z ->
            z.putNextEntry(ZipEntry("otra_cosa.txt"))
            z.write(byteArrayOf(1))
            z.closeEntry()
        }
        assertThrows(IllegalStateException::class.java) {
            RespaldoAutomatico.Restaurar(sinData.path, dataDir.path)
        }

        val malo = File(dir, "malo.zip")
        ZipOutputStream(FileOutputStream(malo)).use { z ->
            z.putNextEntry(ZipEntry("data.json"))
            z.write("{esto no es json".toByteArray())
            z.closeEntry()
        }
        assertThrows(Exception::class.java) {
            RespaldoAutomatico.Restaurar(malo.path, dataDir.path)
        }
        assertTrue(File(dataDir, "data.json").readText().contains("\"v\":1"))
    }

    @Test
    fun restaurar_IgnoraEntradasConRutas() {
        preparar()
        val zipPath = File(dir, "ruta.zip")
        ZipOutputStream(FileOutputStream(zipPath)).use { z ->
            z.putNextEntry(ZipEntry("data.json"))
            z.write("{}".toByteArray())
            z.closeEntry()

            z.putNextEntry(ZipEntry("../escape.txt"))
            z.write("x".toByteArray())
            z.closeEntry()

            z.putNextEntry(ZipEntry("sub/otro.txt"))
            z.write("x".toByteArray())
            z.closeEntry()
        }

        RespaldoAutomatico.Restaurar(zipPath.path, dataDir.path)
        assertFalse(File(dir, "escape.txt").exists())
        assertFalse(File(dataDir, "otro.txt").exists())
        assertFalse(File(dataDir, "sub/otro.txt").exists())
    }

    @Test
    fun relojInyectado_ControlaRespaldoSinEsperar_Regla1HoraYModificacion() {
        // Reloj virtual: nada en esta prueba depende de la hora real ni espera.
        val t0 = LocalDateTime.of(2026, 3, 10, 9, 0, 0)
        fun ms(t: LocalDateTime) = t.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dataJson = File(dataDir, "data.json")

        preparar("{\"state\":{\"v\":1}}")
        dataJson.setLastModified(ms(t0.minusMinutes(1)))

        // 1. Primera vez: debe respaldar, y el .zip queda fechado con el reloj virtual
        assertTrue(RespaldoAutomatico.DebeRespaldar(dataDir.path, t0))
        val r = RespaldoAutomatico.Crear(dataDir.path, t0)
        assertTrue(r.ruta.endsWith("respaldo_20260310_090000.zip"))
        assertEquals(ms(t0), File(r.ruta).lastModified())

        // 2. Inmediatamente después: no
        assertFalse(RespaldoAutomatico.DebeRespaldar(dataDir.path, t0))

        // 3. Pasan 3 días sin cambios en data.json: no
        assertFalse(RespaldoAutomatico.DebeRespaldar(dataDir.path, t0.plusDays(3)))

        // 4. data.json cambia 5 min después del respaldo
        dataJson.setLastModified(ms(t0.plusMinutes(5)))

        // 5. A los 10 min: cambió, pero no ha pasado 1 h -> no
        assertFalse(RespaldoAutomatico.DebeRespaldar(dataDir.path, t0.plusMinutes(10)))

        // 6. Límite exacto: a los 59:59 no, a la 1:00:00 sí (>= 1 h, como el C#)
        assertFalse(RespaldoAutomatico.DebeRespaldar(dataDir.path, t0.plusMinutes(59).plusSeconds(59)))
        assertTrue(RespaldoAutomatico.DebeRespaldar(dataDir.path, t0.plusHours(1)))

        // 7. Nuevo respaldo con el reloj virtual
        val r2 = RespaldoAutomatico.Crear(dataDir.path, t0.plusHours(2))
        assertTrue(r2.ruta.endsWith("respaldo_20260310_110000.zip"))
        assertEquals(2, RespaldoAutomatico.Listar(RespaldoAutomatico.CarpetaDe(dataDir.path)).size)
        assertFalse(RespaldoAutomatico.DebeRespaldar(dataDir.path, t0.plusHours(5)))
    }

    @Test
    fun crear_25Veces_ConservaSolo20_LosMasNuevos() {
        preparar()
        val t0 = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
        for (i in 0 until 25) RespaldoAutomatico.Crear(dataDir.path, t0.plusHours(i.toLong()))
        val lista = RespaldoAutomatico.Listar(RespaldoAutomatico.CarpetaDe(dataDir.path))
        assertEquals(RespaldoAutomatico.MaxRespaldos, lista.size)
        assertEquals("respaldo_20260102_000000.zip", lista.first().name) // el más nuevo (t0 + 24 h)
        assertEquals("respaldo_20260101_050000.zip", lista.last().name)  // los 5 más viejos se borraron
    }

    @Test
    fun restaurar_RechazaJsonSinEstructuraDeEstado_SinTocarNada() {
        preparar("{\"state\":{\"v\":1}}")
        val jsonNoEstado = File(dir, "no_estado.zip")
        ZipOutputStream(FileOutputStream(jsonNoEstado)).use { z ->
            z.putNextEntry(ZipEntry("data.json"))
            z.write("[1, 2, 3]".toByteArray())
            z.closeEntry()
        }

        assertThrows(Exception::class.java) {
            RespaldoAutomatico.Restaurar(jsonNoEstado.path, dataDir.path)
        }

        // data.json original permanece intacto
        assertEquals("{\"state\":{\"v\":1}}", File(dataDir, "data.json").readText())
        // No se creó archivo de backup antes de restaurar
        assertFalse(File(dataDir, "data.json.antes_de_restaurar").exists())
    }
}
