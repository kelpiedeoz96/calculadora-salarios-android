# Prompt: Calculadora de Salarios Colombia para Android (Kotlin + Jetpack Compose)

Eres un ingeniero Android senior. Vas a construir la versión **nativa de Android** de una aplicación de escritorio que ya existe y funciona (C# / Avalonia / .NET 10). La app liquida nómina por horas para Colombia: recargos nocturnos, horas extra, dominicales y festivos, auxilio de transporte y descuentos de salud y pensión, con exportación a PDF y Excel.

La lógica de cálculo ya está resuelta, especificada y probada en la versión de escritorio. **Tu trabajo es portarla sin cambiarla**, ponerle una interfaz pensada para celular y respetar las pruebas existentes. No inventes reglas, no "mejores" fórmulas, no simplifiques.

---

## 0. Material de referencia (léelo completo antes de escribir código)

Se te entrega en la carpeta `referencia/`:

1. `referencia/prompt_motor_nomina.md`: especificación exhaustiva del motor de cálculo. Está numerada desde la sección 15 hasta la 29 porque es un fragmento de un documento más grande; trátala como la especificación completa del motor. Incluye un ejemplo resuelto (semana 2026-10-12 a 2026-10-18: recargos 181.249, neto 1.111.213) y vectores de prueba (30.016, 33.352, 36.688).
2. `referencia/csharp/`: el repositorio de escritorio completo, solo lectura. Es la verdad cuando la especificación sea ambigua:
   - `CalculadoraDeSalarios.Core/`: motor, modelos, formato, festivos, respaldo, exportación.
   - `CalculadoraDeSalarios.App/ViewModels/` y `Views/`: comportamiento y diseño de cada pantalla.
   - `CalculadoraDeSalarios.Tests/`: 116 pruebas xUnit y `TestData/` (`data_formato_actual_v1.json`, `respaldo_sintetico.json`, `salida_esperada.json`).

Si algo no está claro en ninguno de los dos, **pregunta antes de decidir**. No adivines.

---

## 1. Reglas innegociables

1. **Paridad exacta con el escritorio.** Para las mismas entradas, el motor Kotlin debe dar exactamente las mismas cifras que el motor C#. Los valores esperados de las pruebas nunca se modifican: si una prueba falla, el error está en tu código.
2. **Redondeo:** `JsRound(x) = floor(x + 0.5)`. Impleméntalo explícitamente con `Math.floor(x + 0.5)`. No uses `BigDecimal`, no uses redondeo bancario, no cambies `Double` por otro tipo.
3. **Nada de web.** Prohibido HTML, `WebView`, PWA, Flutter, React Native, Capacitor, Tauri o cualquier capa web. Es una app 100% nativa en Kotlin con Jetpack Compose.
4. **Sin internet.** La app no declara el permiso `INTERNET`. No hay analítica, anuncios, telemetría ni servicios en la nube. Los datos de nómina son sensibles y se quedan en el teléfono.
5. **Sin datos reales** en el código, las pruebas ni los recursos. Todo es genérico (ver sección 7).
6. **Interfaz en español de Colombia** (`es-CO`). Dinero con el mismo formato que el escritorio (`$ 1.234`, negativo `-$ 1.234`; revisa `Fmt.cs`).
7. **No avances de fase con pruebas en rojo.**

---

## 2. Stack y arquitectura

- Kotlin (última versión estable), Gradle con Kotlin DSL y **version catalog** (`libs.versions.toml`).
- Jetpack Compose + Material 3. `minSdk 26`, `compileSdk` y `targetSdk` el más reciente estable que tengas instalado.
- Arquitectura MVVM: `ViewModel` + `StateFlow` + estado inmutable por pantalla. Sin frameworks de inyección de dependencias: constructores e interfaces simples.
- `kotlinx.serialization` para JSON, `java.time` para fechas (con `minSdk 26` no hace falta desugaring).
- Módulos:
  - `:engine`, módulo **Kotlin/JVM puro**, sin ninguna dependencia de Android. Contiene motor, modelos, festivos, formato, calculadoras de pantalla y lógica de respaldo. Sus pruebas corren en JVM con `gradle :engine:test`, rápido y sin emulador.
  - `:app`: interfaz, almacenamiento en disco, exportación y servicios de Android.
- Espeja la estructura de `Core` de C# (`Engine`, `FestivosCalc`, `JornadaHelper`, `EmpleadosCalc`, `PanelCalc`, `HistorialCalc`, `CalendarioCalc`, `AjustesCalc`, `Fmt`, `RespaldoAutomatico`) para que se pueda comparar archivo por archivo.

---

## 3. Fase 1: el motor (lo primero y lo más importante)

1. Porta `Core` completo a `:engine` respetando la especificación (minutos como unidad, segmentación por minuto, regla de salida de 30 minutos, descanso descontado primero de lo diurno, semanas lunes a domingo con tope de 42 h, las 8 categorías, factores aditivos, redondeo por día y por categoría, dominical 90 % que pasa a 100 % desde 2027-07-01, nómina quincenal, festivos por Ley Emiliani).
2. Los nombres y la forma del JSON (`@SerialName`) deben coincidir **exactamente** con `Models.cs` del escritorio. Así un `data.json` o un respaldo del escritorio se puede importar tal cual en Android y al revés.
3. Porta las 116 pruebas xUnit a JUnit 5 + Kotlin, sin cambiar un solo valor esperado. Agrega una prueba "oráculo" que cargue `data_formato_actual_v1.json`, calcule y compare contra `salida_esperada.json` campo por campo (como `RegressionOracleTests.cs`).
4. Agrega pruebas propias para los casos límite de la especificación: festivos 2026 y 2027 exactos, cambio de dominical el 2027-07-01, orden de descuento del descanso, regla de 30 minutos, formato de dinero negativo.
5. **Punto de control:** `gradle :engine:test` en verde. Muéstrame la salida completa y el conteo de pruebas antes de pasar a la fase 2.

---

## 4. Fase 2: almacenamiento y respaldos

- `data.json` en `filesDir`, escritura **atómica** (archivo temporal + renombrar) para que un cierre inesperado no lo corrompa.
- Fotos de empleados y logo de reportes: se eligen con el selector de fotos del sistema (`PickVisualMedia`) y se copian a `filesDir`.
- **Respaldo automático**, mismas reglas que `RespaldoAutomatico.cs`: zip en `filesDir/respaldos`, conservar los 20 más recientes, respaldar solo si `data.json` cambió y pasó al menos 1 hora desde el último. Se ejecuta al abrir la app y al pasar a segundo plano.
- Carpeta de respaldo extra opcional elegida con el selector de carpetas del sistema (`ACTION_OPEN_DOCUMENT_TREE`, con permiso persistente), para dejar copias en Drive, la tarjeta SD u otra ubicación.
- **Restaurar:** descomprimir a un temporal, validar el JSON y solo entonces reemplazar. Si falla, no se toca nada.
- **Importar y exportar** `data.json` y respaldos `.zip` del escritorio mediante el selector de archivos del sistema (`CreateDocument` / `OpenDocument`).
- Pruebas equivalentes a `RespaldoAutomaticoTests.cs` y `DataStoreTests.cs`.

---

## 5. Fase 3 y 4: interfaz

Navegación y comportamiento por pantalla: lee el `ViewModel` y la `View` correspondientes en `referencia/csharp/CalculadoraDeSalarios.App/`.

- **Navegación:** `NavigationBar` inferior con Panel, Liquidación, Empleados, Calendario e Historial. **Ajustes** va como ícono en la barra superior. En pantallas anchas (tableta, plegable, horizontal) usa `NavigationRail`, con `WindowSizeClass`.
- **Tema:** Material 3 claro, oscuro y seguir al sistema, seleccionable en Ajustes. Las 8 categorías de recargo tienen cada una un par de colores fondo/texto, propio para claro y oscuro, con contraste legible (WCAG AA). No dependas de color dinámico para ellas.
- Pantallas: Panel, Liquidación (la más compleja: días, turnos, semanas, auditoría de cálculo), Empleados (con foto), Calendario (festivos y dominicales), Historial, Ajustes (empresa, NIT, logo, SMMLV, auxilio, porcentajes, turnos rápidos, festivos, respaldos).
- Adaptación a celular: listas con tarjetas, teclado numérico para horas y valores, selector de hora nativo de Material 3, gestos de deslizar para borrar con opción de deshacer, estados vacíos con una explicación y el botón para empezar. Todo debe sobrevivir a la rotación y a la muerte del proceso (`rememberSaveable` y `SavedStateHandle`).
- Accesibilidad: `contentDescription` en íconos, objetivos táctiles de al menos 48 dp, escala de fuente del sistema respetada.
- Antes de decidir la distribución de una pantalla, mira cómo la resolvió el escritorio. Adáptala, no la copies tal cual.

---

## 6. Fase 5: exportación

- **PDF:** con `android.graphics.pdf.PdfDocument` (sin librerías externas). Mismo contenido que el PDF del escritorio (`Export.cs`): encabezado con razón social, NIT y logo, detalle por semana y día, totales por categoría, devengado, descuentos y neto. Hoja A4 horizontal, con paginación correcta.
- **Excel (.xlsx):** evalúa una librería ligera compatible con Android. Si ninguna es fiable, escribe un generador mínimo propio de `.xlsx` con `java.util.zip` y XML (SpreadsheetML), con una prueba que valide la estructura. Evita Apache POI completo por su peso.
- Compartir y guardar con `FileProvider` + `ACTION_SEND`, y con `CreateDocument` para guardar en una carpeta elegida.
- Pruebas: genera un PDF y un xlsx de ejemplo en un test instrumentado o con Robolectric y verifica que existan, que no estén vacíos y que el xlsx se pueda leer de nuevo.

---

## 7. Datos iniciales (genéricos)

La app arranca vacía con empresa "Mi Empresa", sin NIT y **un empleado de ejemplo**, y valores iniciales del 2026 (SMMLV, auxilio de transporte, porcentajes, festivos). Todo es editable en Ajustes y Empleados. Los textos del usuario final (razón social, NIT, logo) nunca van escritos en el código.

---

## 8. Fase 6: acabado y entrega

- Ícono adaptativo (`mipmap-anydpi`) con una versión monocromática para íconos temáticos. Usa un ícono neutro de calculadora, de diseño propio.
- `build.fish` en la raíz (**sintaxis Fish, nada de bash**) con acciones: `probar` (`gradle :engine:test :app:testDebugUnitTest`), `debug` (`assembleDebug`), `instalar` (`adb install -r`), `release` (APK/AAB firmado) y `todo`. Incluye cómo crear el keystore de firma **sin subirlo nunca al repositorio** (`.gitignore`).
- Reglas de ProGuard/R8 verificadas: el APK de release debe dar los mismos resultados que el de debug. Corre las pruebas contra el build release o agrega una prueba de humo.
- `README.md` en español: requisitos (JDK, Android SDK), cómo compilar, probar, instalar, importar datos del escritorio y estructura del proyecto. Licencia MIT.
- `.gitignore` completo (`build/`, `.gradle/`, `local.properties`, `*.jks`, `*.keystore`).

---

## 9. Cómo trabajar

- Trabaja **por fases**, en el orden de arriba. Al terminar cada una: corre las pruebas, muéstrame la salida completa y haz un commit con un mensaje claro. No empieces la siguiente sin mi confirmación.
- Escribe pruebas **antes o junto con** el código, nunca después. Si una prueba falla, no la debilites ni la borres: corrige el código.
- Si descubres una diferencia entre la especificación y el código C#, **detente y repórtala** con ambos valores, en vez de elegir uno en silencio.
- Código claro, sin comentarios innecesarios. Las funciones del motor deben ser puras (sin estado global ni acceso al reloj o al disco dentro del cálculo).
- Al final de cada fase, dime en pocas líneas qué quedó hecho, qué decisiones tomaste y qué falta.

**Empieza ahora por la sección 0** y confírmame qué entendiste de la especificación y del repositorio de referencia. Después empieza con la fase 1.
