package co.com.calculadoradesalarios.engine.exportar

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Lector mínimo de fuentes TrueType: métricas, anchos reales de glifo (hmtx), mapa de caracteres
 * (cmap formato 4 y 12) y subconjunto de glifos para incrustar en el PDF.
 *
 * El subconjunto conserva los números de glifo originales (los glifos no usados quedan vacíos), así
 * el PDF puede usar Identity-H con CIDToGIDMap /Identity y los anchos /W siguen siendo los de la fuente.
 */
class FuenteTtf(private val datos: ByteArray) {
    private val tablas: Map<String, Pair<Int, Int>> // etiqueta -> (offset, longitud)

    val unidadesPorEm: Int
    val ascendente: Int
    val descendente: Int
    val altoMayusculas: Int
    val cajaX0: Int
    val cajaY0: Int
    val cajaX1: Int
    val cajaY1: Int
    val nombrePostScript: String
    private val numGlifos: Int
    private val avances: IntArray
    private val cmap: Map<Int, Int>
    private val locaLarga: Boolean

    init {
        val numTablas = u16(4)
        tablas = (0 until numTablas).associate { i ->
            val r = 12 + i * 16
            String(datos, r, 4, Charsets.US_ASCII) to (u32(r + 8).toInt() to u32(r + 12).toInt())
        }
        val head = off("head")
        unidadesPorEm = u16(head + 18)
        cajaX0 = i16(head + 36); cajaY0 = i16(head + 38); cajaX1 = i16(head + 40); cajaY1 = i16(head + 42)
        locaLarga = i16(head + 50) == 1
        val hhea = off("hhea")
        ascendente = i16(hhea + 4)
        descendente = i16(hhea + 6)
        val numHMetrics = u16(hhea + 34)
        numGlifos = u16(off("maxp") + 4)
        val hmtx = off("hmtx")
        avances = IntArray(numGlifos) { g -> u16(hmtx + 4 * minOf(g, numHMetrics - 1)) }
        val os2 = tablas["OS/2"]?.first
        altoMayusculas = if (os2 != null && u16(os2) >= 2) i16(os2 + 88) else (ascendente * 0.7).toInt()
        cmap = leerCmap()
        nombrePostScript = leerNombre(6)?.filter { it.isLetterOrDigit() || it == '-' } ?: "Fuente"
    }

    private fun off(t: String) = tablas[t]?.first ?: throw IllegalArgumentException("La fuente no tiene la tabla $t")
    private fun u8(p: Int) = datos[p].toInt() and 0xFF
    private fun u16(p: Int) = (u8(p) shl 8) or u8(p + 1)
    private fun i16(p: Int) = u16(p).toShort().toInt()
    private fun u32(p: Int) = (u16(p).toLong() shl 16) or u16(p + 2).toLong()

    /** Glifo para un punto de código Unicode (0 = .notdef si la fuente no lo tiene). */
    fun glifo(cp: Int): Int = cmap[cp] ?: 0

    /** Ancho de avance en unidades de la fuente. */
    fun avance(g: Int): Int = avances[g.coerceIn(0, numGlifos - 1)]

    /** Ancho en puntos de [texto] a [tam] puntos, con los anchos reales de cada glifo. */
    fun ancho(texto: String, tam: Float): Float {
        var u = 0L
        texto.codePoints().forEach { u += avance(glifo(it)) }
        return u * tam / unidadesPorEm
    }

    private fun leerCmap(): Map<Int, Int> {
        val base = off("cmap")
        val n = u16(base + 2)
        val registros = (0 until n).map { i -> Triple(u16(base + 4 + i * 8), u16(base + 6 + i * 8), u32(base + 8 + i * 8).toInt()) }
        val sub12 = registros.firstOrNull { it.first == 3 && it.second == 10 }?.let { base + it.third }
        if (sub12 != null && u16(sub12) == 12) {
            val m = HashMap<Int, Int>()
            val grupos = u32(sub12 + 12).toInt()
            for (i in 0 until grupos) {
                val g = sub12 + 16 + i * 12
                val ini = u32(g).toInt(); val fin = u32(g + 4).toInt(); val gl = u32(g + 8).toInt()
                for (c in ini..fin) m[c] = gl + (c - ini)
            }
            return m
        }
        val sub4 = registros.firstOrNull { it.first == 3 && it.second == 1 }?.let { base + it.third }
            ?: registros.firstOrNull { it.first == 0 }?.let { base + it.third }
            ?: throw IllegalArgumentException("La fuente no tiene cmap Unicode")
        require(u16(sub4) == 4) { "cmap formato ${u16(sub4)} no soportado" }
        val segs = u16(sub4 + 6) / 2
        val fin = sub4 + 14
        val ini = fin + segs * 2 + 2
        val delta = ini + segs * 2
        val rango = delta + segs * 2
        val m = HashMap<Int, Int>()
        for (s in 0 until segs) {
            val e = u16(fin + s * 2); val st = u16(ini + s * 2); val d = i16(delta + s * 2); val ro = u16(rango + s * 2)
            if (st == 0xFFFF) continue
            for (c in st..e) {
                val g = if (ro == 0) (c + d) and 0xFFFF else {
                    val v = u16(rango + s * 2 + ro + 2 * (c - st))
                    if (v == 0) 0 else (v + d) and 0xFFFF
                }
                if (g != 0) m[c] = g
            }
        }
        return m
    }

    private fun leerNombre(id: Int): String? {
        val base = tablas["name"]?.first ?: return null
        val n = u16(base + 2)
        val cadenas = base + u16(base + 4)
        for (i in 0 until n) {
            val r = base + 6 + i * 12
            if (u16(r + 6) != id) continue
            val plat = u16(r); val len = u16(r + 8); val o = cadenas + u16(r + 10)
            return if (plat == 3 || plat == 0) String(datos, o, len, Charsets.UTF_16BE) else String(datos, o, len, Charsets.ISO_8859_1)
        }
        return null
    }

    private fun rangoGlifo(g: Int): Pair<Int, Int> {
        val loca = off("loca")
        val (a, b) = if (locaLarga) u32(loca + g * 4).toInt() to u32(loca + g * 4 + 4).toInt()
        else u16(loca + g * 2) * 2 to u16(loca + g * 2 + 2) * 2
        return off("glyf") + a to (b - a)
    }

    /** Agrega los componentes de los glifos compuestos (recursivo). */
    private fun cerrar(usados: Set<Int>): Set<Int> {
        val todos = HashSet(usados); todos += 0
        val pendientes = ArrayDeque(todos)
        while (pendientes.isNotEmpty()) {
            val (p, len) = rangoGlifo(pendientes.removeFirst())
            if (len == 0 || i16(p) >= 0) continue
            var q = p + 10
            while (true) {
                val flags = u16(q); val comp = u16(q + 2)
                if (todos.add(comp)) pendientes += comp
                q += 4 + (if (flags and 0x1 != 0) 4 else 2)
                q += when { flags and 0x8 != 0 -> 2; flags and 0x40 != 0 -> 4; flags and 0x80 != 0 -> 8; else -> 0 }
                if (flags and 0x20 == 0) break
            }
        }
        return todos
    }

    /** Fuente TrueType válida con solo los glifos [usados] (y sus componentes); el resto, vacíos. */
    fun subconjunto(usados: Set<Int>): ByteArray {
        val incluir = cerrar(usados)
        val glyf = ByteArrayOutputStream()
        val loca = ByteBuffer.allocate((numGlifos + 1) * 4)
        for (g in 0 until numGlifos) {
            loca.putInt(glyf.size())
            if (g in incluir) {
                val (p, len) = rangoGlifo(g)
                glyf.write(datos, p, len)
                while (glyf.size() % 4 != 0) glyf.write(0)
            }
        }
        loca.putInt(glyf.size())
        val conservar = listOf("OS/2", "cmap", "cvt ", "fpgm", "glyf", "head", "hhea", "hmtx", "loca", "maxp", "name", "post", "prep")
        val nuevas = sortedMapOf<String, ByteArray>()
        for (t in conservar) {
            nuevas[t] = when (t) {
                "glyf" -> glyf.toByteArray()
                "loca" -> loca.array()
                else -> tablas[t]?.let { (o, l) -> datos.copyOfRange(o, o + l) } ?: continue
            }
        }
        val head = nuevas.getValue("head")
        head[50] = 0; head[51] = 1                     // indexToLocFormat = 1 (loca larga)
        for (i in 8..11) head[i] = 0                   // checkSumAdjustment se recalcula al final
        return armar(nuevas)
    }

    companion object {
        private fun suma(b: ByteArray): Long {
            var s = 0L
            var i = 0
            while (i < b.size) {
                var v = 0L
                for (k in 0 until 4) v = (v shl 8) or (if (i + k < b.size) (b[i + k].toLong() and 0xFF) else 0L)
                s = (s + v) and 0xFFFFFFFFL
                i += 4
            }
            return s
        }

        private fun armar(t: Map<String, ByteArray>): ByteArray {
            val n = t.size
            var pot = 1; var exp = 0
            while (pot * 2 <= n) { pot *= 2; exp++ }
            val cab = ByteBuffer.allocate(12 + 16 * n)
            cab.putInt(0x00010000); cab.putShort(n.toShort()); cab.putShort((pot * 16).toShort()); cab.putShort(exp.toShort()); cab.putShort((n * 16 - pot * 16).toShort())
            var offset = 12 + 16 * n
            val cuerpo = ByteArrayOutputStream()
            for ((etiqueta, b) in t) {
                cab.put(etiqueta.toByteArray(Charsets.US_ASCII)); cab.putInt(suma(b).toInt()); cab.putInt(offset); cab.putInt(b.size)
                cuerpo.write(b)
                var relleno = (4 - b.size % 4) % 4
                while (relleno-- > 0) cuerpo.write(0)
                offset += (b.size + 3) / 4 * 4
            }
            val fuente = cab.array() + cuerpo.toByteArray()
            // head.checkSumAdjustment = 0xB1B0AFBA - suma de toda la fuente
            val ajuste = (0xB1B0AFBAL - suma(fuente)) and 0xFFFFFFFFL
            val p = (12 + 16 * n) + t.entries.takeWhile { it.key != "head" }.sumOf { (it.value.size + 3) / 4 * 4 } + 8
            ByteBuffer.wrap(fuente).putInt(p, ajuste.toInt())
            return fuente
        }
    }
}
