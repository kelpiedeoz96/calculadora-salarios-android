plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// ---------------------------------------------------------------------------------------------
// Prueba de humo de R8: el motor (PDF, Excel, data.json, oráculo) pasa por el mismo R8 de AGP con las
// reglas que AGP generó para el release, y debe producir exactamente los mismos archivos.
// Uso: ./gradlew :engine:humoR8Comparar   (lo corre "./build.fish release")
// ---------------------------------------------------------------------------------------------
val humo: SourceSet = sourceSets.create("humo") {
    compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    runtimeClasspath += output + compileClasspath
    resources.srcDir("src/test/resources")
}
val herramientaR8: Configuration by configurations.creating
dependencies {
    "humoImplementation"(libs.kotlinx.serialization.json)
    herramientaR8("com.android.tools.build:builder:${libs.versions.agp.get()}") { isTransitive = false }
}

val dirHumo = layout.buildDirectory.dir("humo")
val javaHumo = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }

val humoJar by tasks.registering(Jar::class) {
    archiveFileName.set("humo.jar")
    destinationDirectory.set(dirHumo)
    from(humo.output)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    from(sourceSets.main.get().output)
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "**/module-info.class", "META-INF/versions/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to "co.com.calculadoradesalarios.humo.HumoR8") }
}

/** Reglas del release de la app, sin las opciones -print* (que escribirían sobre los archivos de la app). */
val reglasRelease by tasks.registering {
    val origen = rootProject.file("app/build/outputs/mapping/release/configuration.txt")
    val destino = dirHumo.map { it.file("reglas-release.pro") }
    dependsOn(":app:minifyReleaseWithR8")
    inputs.file(origen)
    outputs.file(destino)
    doLast {
        destino.get().asFile.writeText(origen.readLines().filterNot { it.trim().startsWith("-print") || it.trim().startsWith("-dump") }.joinToString("\n"))
    }
}

val humoR8 by tasks.registering(JavaExec::class) {
    dependsOn(humoJar, reglasRelease)
    javaLauncher.set(javaHumo)
    classpath = herramientaR8
    mainClass.set("com.android.tools.r8.R8")
    val entrada = dirHumo.map { it.file("humo.jar") }
    val salida = dirHumo.map { it.file("humo-r8.jar") }
    inputs.file(entrada); outputs.file(salida)
    doFirst {
        args(
            "--release", "--classfile",
            "--lib", javaHumo.get().metadata.installationPath.asFile.path,
            "--pg-conf", dirHumo.get().file("reglas-release.pro").asFile.path,
            "--pg-conf", file("src/humo/humo-keep.pro").path,
            "--pg-map-output", dirHumo.get().file("humo-r8-mapping.txt").asFile.path,
            "--output", salida.get().asFile.path,
            entrada.get().asFile.path,
        )
    }
}

fun correrHumo(nombre: String, jar: String, tarea: TaskProvider<*>) = tasks.register<JavaExec>(nombre) {
    dependsOn(tarea)
    javaLauncher.set(javaHumo)
    classpath = files(dirHumo.map { it.file(jar) })
    mainClass.set("co.com.calculadoradesalarios.humo.HumoR8")
    args(dirHumo.get().dir(nombre).asFile.path)
}
val humoNormal = correrHumo("humoNormal", "humo.jar", humoJar)
val humoReducido = correrHumo("humoReducido", "humo-r8.jar", humoR8)

tasks.register("humoR8Comparar") {
    group = "verification"
    description = "Comprueba que R8 (reglas del release) no cambia el PDF, el Excel ni data.json."
    dependsOn(humoNormal, humoReducido)
    doLast {
        val a = dirHumo.get().dir("humoNormal").asFile
        val b = dirHumo.get().dir("humoReducido").asFile
        val nombres = a.list()!!.sorted()
        check(nombres == b.list()!!.sorted()) { "R8: archivos distintos ${a.list()!!.toList()} vs ${b.list()!!.toList()}" }
        val distintos = nombres.filterNot { a.resolve(it).readBytes().contentEquals(b.resolve(it).readBytes()) }
        check(distintos.isEmpty()) { "R8 cambió: $distintos" }
        val mapa = dirHumo.get().file("humo-r8-mapping.txt").asFile.readText()
        val ofuscadas = Regex("""^co\.com\.calculadoradesalarios\.engine\.\S+ -> (\S+):""", RegexOption.MULTILINE).findAll(mapa)
            .count { !it.groupValues[1].startsWith("co.com.calculadoradesalarios.engine.") }
        val tamEntrada = dirHumo.get().file("humo.jar").asFile.length()
        val tamSalida = dirHumo.get().file("humo-r8.jar").asFile.length()
        check(ofuscadas > 0) { "R8 no renombró ninguna clase del motor: la prueba no estaría probando nada" }
        println("humoR8Comparar: ${nombres.size} archivos idénticos byte a byte; $ofuscadas clases del motor renombradas; jar $tamEntrada -> $tamSalida bytes")
    }
}
