package co.com.calculadoradesalarios.ui.comun

/**
 * Normalización de lo que se escribe en los campos numéricos, antes de pasarlo al motor.
 * El motor conserva la paridad con el escritorio (cultura invariante: "4,5" no es un número);
 * aquí se acepta la coma decimal de Colombia o se explica qué está mal.
 */
object EntradaNumerica {
    sealed interface Resultado {
        data class Valido(val texto: String) : Resultado
        data class Invalido(val mensaje: String) : Resultado
    }

    const val ErrorDosSeparadores = "Usa un solo separador decimal, por ejemplo 4.5 o 4,5."
    const val ErrorMiles = "Escribe el número sin separadores de miles, por ejemplo 1800000."
    const val ErrorSalario = "Escribe el salario en pesos, sin centavos: 1750905 o 1.750.905."
    const val ErrorMinutos = "Escribe los minutos sin decimales, por ejemplo 30."

    /** Ajustes: "4,5" -> "4.5". Coma y punto juntos, o más de un separador, es un error. */
    fun decimal(texto: String): Resultado {
        val t = texto.trim()
        val comas = t.count { it == ',' }
        val puntos = t.count { it == '.' }
        return when {
            comas > 0 && puntos > 0 -> Resultado.Invalido(ErrorDosSeparadores)
            comas > 1 || puntos > 1 -> Resultado.Invalido(ErrorMiles)
            else -> Resultado.Valido(t.replace(',', '.'))
        }
    }

    /**
     * Salario en pesos enteros. Los puntos se aceptan como separadores de miles (como el escritorio,
     * que se queda con los dígitos). Una coma indicaría centavos y el escritorio la ignoraría,
     * multiplicando el valor (1.750.905,50 -> 175090550): por eso se rechaza.
     */
    fun salario(texto: String): Resultado =
        if (texto.contains(',')) Resultado.Invalido(ErrorSalario) else Resultado.Valido(texto.trim())

    /** Minutos de descanso: el escritorio se queda con los dígitos ("1,5" -> 15); aquí se avisa. */
    fun minutos(texto: String): Resultado =
        if (texto.contains(',') || texto.contains('.')) Resultado.Invalido(ErrorMinutos) else Resultado.Valido(texto.trim())

    /** Horas: "8.30" o "8,30" -> "8:30"; el resto lo decide JornadaHelper.NormTime. */
    fun hora(texto: String): String = texto.trim().replace('.', ':').replace(',', ':')
}
