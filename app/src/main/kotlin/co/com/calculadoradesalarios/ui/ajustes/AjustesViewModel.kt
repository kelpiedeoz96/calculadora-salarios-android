package co.com.calculadoradesalarios.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.com.calculadoradesalarios.datos.InfoRespaldos
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.datos.TipoMensaje
import co.com.calculadoradesalarios.engine.AjustesCalc
import co.com.calculadoradesalarios.engine.Config
import co.com.calculadoradesalarios.engine.FestivosCalc
import co.com.calculadoradesalarios.engine.HistorialCalc
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.RespaldoAutomatico
import co.com.calculadoradesalarios.ui.comun.EntradaNumerica
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

enum class TipoCampo { Numero, Hora, Texto }

/** Campos de AjustesViewModel del escritorio, con sus etiquetas y en el mismo orden. */
enum class CampoAjuste(val etiqueta: String, val tipo: TipoCampo) {
    TopeSemanal("Tope semanal (horas)", TipoCampo.Numero),
    JornadaDiariaRef("Jornada diaria de referencia (horas)", TipoCampo.Numero),
    NocturnaDesde("Nocturna desde", TipoCampo.Hora),
    NocturnaHasta("Nocturna hasta", TipoCampo.Hora),
    MinutosMinimos("Minutos mínimos para que cuenten", TipoCampo.Numero),
    RecargoNocturno("Nocturno", TipoCampo.Numero),
    ExtraDiurna("Extra diurna", TipoCampo.Numero),
    ExtraNocturna("Extra nocturna", TipoCampo.Numero),
    Dominical("Dominical/festivo/descanso", TipoCampo.Numero),
    Divisor("Divisor del valor hora", TipoCampo.Numero),
    Smmlv("Salario mínimo mensual", TipoCampo.Numero),
    Auxilio("Auxilio de transporte", TipoCampo.Numero),
    SaludPct("Salud (%)", TipoCampo.Numero),
    PensionPct("Pensión (%)", TipoCampo.Numero),
    RazonSocial("Razón social", TipoCampo.Texto),
    Nit("NIT", TipoCampo.Texto);

    fun leer(c: Config): String = when (this) {
        TopeSemanal -> AjustesCalc.FormatearNumero(c.weeklyCap)
        JornadaDiariaRef -> AjustesCalc.FormatearNumero(c.dailyRef)
        NocturnaDesde -> c.nightStart
        NocturnaHasta -> c.nightEnd
        MinutosMinimos -> AjustesCalc.FormatearNumero(c.minValid)
        RecargoNocturno -> AjustesCalc.FormatearNumero(c.pct.noc)
        ExtraDiurna -> AjustesCalc.FormatearNumero(c.pct.hed)
        ExtraNocturna -> AjustesCalc.FormatearNumero(c.pct.hen)
        Dominical -> AjustesCalc.FormatearNumero(c.pct.dom)
        Divisor -> AjustesCalc.FormatearNumero(c.divisor)
        Smmlv -> AjustesCalc.FormatearNumero(c.smmlv)
        Auxilio -> AjustesCalc.FormatearNumero(c.auxilio)
        SaludPct -> AjustesCalc.FormatearNumero(c.saludPct)
        PensionPct -> AjustesCalc.FormatearNumero(c.pensionPct)
        RazonSocial -> c.razonSocial.trim()
        Nit -> c.nit.trim()
    }

    internal fun leerNumero(c: Config): Double = when (this) {
        TopeSemanal -> c.weeklyCap
        JornadaDiariaRef -> c.dailyRef
        MinutosMinimos -> c.minValid
        RecargoNocturno -> c.pct.noc
        ExtraDiurna -> c.pct.hed
        ExtraNocturna -> c.pct.hen
        Dominical -> c.pct.dom
        Divisor -> c.divisor
        Smmlv -> c.smmlv
        Auxilio -> c.auxilio
        SaludPct -> c.saludPct
        PensionPct -> c.pensionPct
        else -> error("$this no es numérico")
    }

    internal fun escribirNumero(c: Config, v: Double) = when (this) {
        TopeSemanal -> c.weeklyCap = v
        JornadaDiariaRef -> c.dailyRef = v
        MinutosMinimos -> c.minValid = v
        RecargoNocturno -> c.pct.noc = v
        ExtraDiurna -> c.pct.hed = v
        ExtraNocturna -> c.pct.hen = v
        Dominical -> c.pct.dom = v
        Divisor -> c.divisor = v
        Smmlv -> c.smmlv = v
        Auxilio -> c.auxilio = v
        SaludPct -> c.saludPct = v
        PensionPct -> c.pensionPct = v
        else -> error("$this no es numérico")
    }

    internal fun escribirTexto(c: Config, s: String) = when (this) {
        NocturnaDesde -> c.nightStart = s
        NocturnaHasta -> c.nightEnd = s
        RazonSocial -> c.razonSocial = s
        Nit -> c.nit = s
        else -> error("$this no es de texto")
    }
}

enum class Interruptor(val etiqueta: String, val ayuda: String?) {
    DailyCapOn("Contar como extra lo que pase de la jornada diaria", "Apagado: solo el tope semanal cuenta, como se liquida actualmente."),
    MinuteRuleOn("Regla de minutos en la hora de salida", "Si los minutos de la salida son menos del mínimo, se redondean hacia abajo (9:29 → 9:00). Si llegan al mínimo, se pagan reales."),
    DomAutoOn("Subir el dominical a 100% desde el 1 de julio de 2027", "Los combinados se suman automáticamente. El dominical es 90% hasta el 30 de junio de 2027 y 100% desde el 1 de julio de 2027 (Ley 2466 de 2025). Si pones un porcentaje mayor, se respeta."),
    RateRound("Redondear valor hora al peso", null),
    DomOrdFull("Ordinarias dominicales suman valor completo", "Nota: esta casilla no cambia el cálculo, igual que en la app anterior.");

    fun leer(c: Config): Boolean = when (this) {
        DailyCapOn -> c.dailyCapOn
        MinuteRuleOn -> c.minuteRuleOn
        DomAutoOn -> c.domAutoOn
        RateRound -> c.rateRound
        DomOrdFull -> c.domOrdFull
    }

    internal fun escribir(c: Config, v: Boolean) = when (this) {
        DailyCapOn -> c.dailyCapOn = v
        MinuteRuleOn -> c.minuteRuleOn = v
        DomAutoOn -> c.domAutoOn = v
        RateRound -> c.rateRound = v
        DomOrdFull -> c.domOrdFull = v
    }
}

data class AjustesEstado(
    val cargando: Boolean = true,
    val valores: Map<CampoAjuste, String> = emptyMap(),
    val errores: Map<CampoAjuste, String> = emptyMap(),
    val interruptores: Map<Interruptor, Boolean> = emptyMap(),
    val deductBase: String = "basico",
    val turnos: List<String> = emptyList(),
    val festivos: List<String> = emptyList(),
    val tema: String = "system",
    val logo: String? = null,
    val respaldos: InfoRespaldos = InfoRespaldos(),
    val carpetaExtra: String? = null,
) {
    val textoUltimoRespaldo: String
        get() = respaldos.ultimo?.let {
            "Último respaldo: %02d/%02d/%d %02d:%02d  ·  %d guardados (se conservan los últimos %d).".format(
                it.dayOfMonth, it.monthValue, it.year, it.hour, it.minute, respaldos.cantidad, RespaldoAutomatico.MaxRespaldos,
            )
        } ?: "Todavía no hay respaldos."
}

class AjustesViewModel(
    private val repo: NominaRepository,
    private val hoy: () -> LocalDate = LocalDate::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val errores = MutableStateFlow<Map<CampoAjuste, String>>(emptyMap())

    val estado: StateFlow<AjustesEstado> = combine(repo.datos, errores, repo.infoRespaldos, repo.carpetaExtra) { d, err, info, extra ->
        if (d == null) AjustesEstado() else {
            val c = d.state.cfg
            AjustesEstado(
                cargando = false,
                valores = CampoAjuste.entries.associateWith { it.leer(c) },
                errores = err,
                interruptores = Interruptor.entries.associateWith { it.leer(c) },
                deductBase = c.deductBase,
                turnos = c.turnos.toList(),
                festivos = d.state.festivos.toList(),
                tema = d.ui.themeMode,
                logo = c.logoCustomRelPath,
                respaldos = info,
                carpetaExtra = extra,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AjustesEstado())

    private fun editarCfg(cambio: (Config) -> Boolean) = repo.editar("Edición de datos") { st, _ -> cambio(st.cfg) }

    private fun fijarError(campo: CampoAjuste, mensaje: String?) {
        errores.value = if (mensaje == null) errores.value - campo else errores.value + (campo to mensaje)
    }

    /** CampoAjusteViewModel.Confirmar: valida, aplica si cambió y deja copia en el historial. */
    fun confirmar(campo: CampoAjuste, texto: String) {
        val cfgActual = repo.datos.value?.state?.cfg ?: return
        when (campo.tipo) {
            TipoCampo.Numero -> {
                val normal = when (val r = EntradaNumerica.decimal(texto)) {
                    is EntradaNumerica.Resultado.Invalido -> return fijarError(campo, r.mensaje)
                    is EntradaNumerica.Resultado.Valido -> r.texto
                }
                val (ok, v) = AjustesCalc.TryParseNumero(normal)
                if (!ok) return fijarError(campo, "Valor no válido: se mantuvo el anterior.")
                if (campo == CampoAjuste.Divisor && v <= 0) return fijarError(campo, "El divisor debe ser mayor que 0.")
                fijarError(campo, null)
                if (campo.leerNumero(cfgActual) == v) return
                editarCfg { campo.escribirNumero(it, v); true }
            }
            TipoCampo.Hora -> {
                val (ok, norm) = AjustesCalc.TryParseHora(texto)
                if (!ok) return fijarError(campo, "Hora no válida: usa HH:MM.")
                fijarError(campo, null)
                if (campo.leer(cfgActual) == norm) return
                editarCfg { campo.escribirTexto(it, norm); true }
            }
            TipoCampo.Texto -> {
                val nuevo = texto.trim()
                fijarError(campo, null)
                if (campo.leer(cfgActual) == nuevo) return
                editarCfg { campo.escribirTexto(it, nuevo); true }
            }
        }
    }

    fun fijarInterruptor(i: Interruptor, valor: Boolean) = editarCfg { c ->
        if (i.leer(c) == valor) false else { i.escribir(c, valor); true }
    }

    fun fijarBaseDescuentos(conRecargos: Boolean) = editarCfg { c ->
        val nuevo = if (conRecargos) "con_recargos" else "basico"
        if (c.deductBase == nuevo) false else { c.deductBase = nuevo; true }
    }

    fun fijarTema(modo: String) = repo.editarUi { it.themeMode = modo }

    // ---------- turnos rápidos: misma validación que AjustesCalc.ParseTurnos ----------

    fun agregarTurno(inicio: String, fin: String): Boolean {
        val turno = AjustesCalc.ParseTurnos("$inicio-$fin").singleOrNull()
            ?: return false.also { repo.avisar("Turno no válido: usa HH:MM-HH:MM.", TipoMensaje.Aviso) }
        return editarCfg { it.turnos = (it.turnos + turno).toMutableList(); true }
    }

    fun quitarTurno(indice: Int): String? {
        val turno = repo.datos.value?.state?.cfg?.turnos?.getOrNull(indice) ?: return null
        editarCfg { it.turnos = it.turnos.toMutableList().apply { removeAt(indice) }; true }
        return turno
    }

    fun reinsertarTurno(indice: Int, turno: String) = editarCfg {
        it.turnos = it.turnos.toMutableList().apply { add(indice.coerceIn(0, size), turno) }; true
    }

    // ---------- festivos: ParseFestivos ordena y quita duplicados ----------

    fun agregarFestivo(fecha: LocalDate) = editarCfgFestivos { it + "%04d-%02d-%02d".format(fecha.year, fecha.monthValue, fecha.dayOfMonth) }

    fun quitarFestivo(fecha: String) = editarCfgFestivos { it - fecha }

    private fun editarCfgFestivos(cambio: (List<String>) -> List<String>) = repo.editar("Edición de datos") { st, _ ->
        val nuevos = AjustesCalc.ParseFestivos(AjustesCalc.UnirLineas(cambio(st.festivos)))
        if (nuevos == st.festivos) false else { st.festivos = nuevos.toMutableList(); true }
    }

    /** AgregarFestivosAnio del escritorio, con los mismos mensajes. */
    fun agregarFestivosAnio(texto: String) {
        val anio = texto.trim().toIntOrNull()
        if (anio == null || anio < 2000 || anio > 2100) return repo.avisar("Escribe un año entre 2000 y 2100.", TipoMensaje.Aviso)
        var agregados = 0
        repo.editar("Edición de datos") { st, _ -> agregados = FestivosCalc.AgregarAnio(st.festivos, anio); agregados > 0 }
        if (agregados == 0) repo.avisar("Los festivos de $anio ya estaban en la lista.", TipoMensaje.Info)
        else repo.avisar("Se agregaron $agregados festivos de $anio.", TipoMensaje.Exito)
    }

    fun anioSugerido(): String = (hoy().year + 1).toString()

    // ---------- logo ----------

    fun elegirLogo(abrir: () -> InputStream, extension: String) = viewModelScope.launch {
        try {
            repo.guardarLogo(abrir, extension)
            repo.avisar("Logo actualizado.", TipoMensaje.Exito)
        } catch (e: Exception) {
            repo.avisar(e.message ?: "No se pudo guardar el logo.", TipoMensaje.Error)
        }
    }

    fun quitarLogo() {
        if (editarCfg { if (it.logoCustomRelPath == null) false else { it.logoCustomRelPath = null; true } })
            repo.avisar("Logo restablecido.", TipoMensaje.Exito)
    }

    // ---------- respaldos, importar y exportar ----------

    fun respaldarAhora() = viewModelScope.launch {
        try {
            val r = repo.respaldarAhora()
            when {
                r.errorExtra != null -> repo.avisar("Respaldo guardado, pero no se pudo copiar a la carpeta extra: ${r.errorExtra}", TipoMensaje.Aviso)
                r.rutaExtra != null -> repo.avisar("Respaldo guardado (también en la carpeta extra).", TipoMensaje.Exito)
                else -> repo.avisar("Respaldo guardado.", TipoMensaje.Exito)
            }
        } catch (e: Exception) {
            repo.avisar("No se pudo crear el respaldo: ${e.message}", TipoMensaje.Error)
        }
    }

    fun restaurar(abrir: () -> InputStream) = viewModelScope.launch {
        try {
            repo.restaurar(abrir)
            repo.avisar("Respaldo restaurado.", TipoMensaje.Exito)
        } catch (e: Exception) {
            repo.avisar("No se pudo restaurar: ${e.message}", TipoMensaje.Error)
        }
    }

    fun elegirCarpetaExtra(uri: String) = viewModelScope.launch {
        try {
            repo.fijarCarpetaExtra(uri)
            repo.avisar("Carpeta extra guardada. Cada respaldo nuevo también se copiará allí.", TipoMensaje.Exito)
        } catch (e: Exception) {
            repo.avisar("No se pudo usar esa carpeta: ${e.message}", TipoMensaje.Error)
        }
    }

    fun quitarCarpetaExtra() = viewModelScope.launch {
        repo.fijarCarpetaExtra(null)
        repo.avisar("Carpeta extra quitada.", TipoMensaje.Info)
    }

    /** Escribe el estado (mismo formato que "Exportar" del escritorio) en [destino]. */
    fun exportarDatos(destino: () -> OutputStream) = viewModelScope.launch {
        try {
            val json = repo.exportarJson()
            withContext(io) { destino().use { it.write(json.toByteArray()) } }
            repo.avisar("Datos exportados.", TipoMensaje.Exito)
        } catch (e: Exception) {
            repo.avisar("No se pudieron exportar los datos: ${e.message}", TipoMensaje.Error)
        }
    }

    /** Importa un data.json o un estado exportado (del escritorio o del celular). */
    fun importarDatos(abrir: () -> InputStream) = viewModelScope.launch {
        try {
            val texto = withContext(io) { abrir().use { it.readBytes().decodeToString() } }
            repo.reemplazarEstado(LeerEstado(texto))
            repo.avisar("Datos importados.", TipoMensaje.Exito)
        } catch (e: Exception) {
            repo.avisar("Ese archivo no tiene datos válidos de la app.", TipoMensaje.Error)
        }
    }

    companion object {
        /** Acepta el formato anidado de data.json ({"state":…}) y el plano del exportar. */
        fun LeerEstado(texto: String): AppState {
            val root = JsonConfig.json.parseToJsonElement(texto) as kotlinx.serialization.json.JsonObject
            val nodo = root["state"] ?: root
            require(nodo is kotlinx.serialization.json.JsonObject && ("emps" in nodo || "cfg" in nodo)) { "sin estado" }
            return HistorialCalc.ParsePayload(nodo.toString()) ?: error("sin estado")
        }
    }
}
