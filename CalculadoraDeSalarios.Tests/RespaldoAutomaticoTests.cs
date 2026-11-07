using System;
using System.IO;
using System.IO.Compression;
using System.Linq;
using CalculadoraDeSalarios.Core.Services;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class RespaldoAutomaticoTests : IDisposable
{
    readonly string _dir = Path.Combine(Path.GetTempPath(), "nomina_resp_" + Guid.NewGuid().ToString("N"));
    public RespaldoAutomaticoTests() => Directory.CreateDirectory(_dir);
    public void Dispose() { try { Directory.Delete(_dir, true); } catch { } }
    string Data => Path.Combine(_dir, "datos");
    string Extra => Path.Combine(_dir, "extra");
    void Preparar(string json = "{\"state\":{}}")
    {
        Directory.CreateDirectory(Data);
        File.WriteAllText(Path.Combine(Data, "data.json"), json);
        File.WriteAllBytes(Path.Combine(Data, "foto_e1_1.png"), new byte[] { 1, 2, 3 });
        File.WriteAllText(Path.Combine(Data, "viejo.tmp"), "x");
        File.WriteAllText(Path.Combine(Data, "data.json.corrupted.123"), "x");
    }
    [Fact]
    public void SinDatos_NoDebeRespaldar_YCrearFalla()
    {
        Directory.CreateDirectory(Data);
        Assert.False(RespaldoAutomatico.DebeRespaldar(Data, DateTime.Now));
        Assert.Throws<InvalidOperationException>(() => RespaldoAutomatico.Crear(Data, DateTime.Now));
    }
    [Fact]
    public void Crear_IncluyeDatosYFotos_PeroNoTmpNiCorrupted()
    {
        Preparar();
        var r = RespaldoAutomatico.Crear(Data, DateTime.Now);
        using var zip = ZipFile.OpenRead(r.Ruta);
        var nombres = zip.Entries.Select(e => e.FullName).OrderBy(x => x).ToArray();
        Assert.Equal(new[] { "data.json", "foto_e1_1.png" }, nombres);
        Assert.Null(r.RutaExtra);
    }
    [Fact]
    public void DebeRespaldar_PrimeraVezSi_LuegoNoHastaQueCambie_YPasePorIntervalo()
    {
        Preparar();
        Assert.True(RespaldoAutomatico.DebeRespaldar(Data, DateTime.Now));
        var r = RespaldoAutomatico.Crear(Data, DateTime.Now);
        Assert.False(RespaldoAutomatico.DebeRespaldar(Data, DateTime.Now.AddDays(3)));
        var ultimo = File.GetLastWriteTime(r.Ruta);
        File.SetLastWriteTime(Path.Combine(Data, "data.json"), ultimo.AddMinutes(5));
        Assert.False(RespaldoAutomatico.DebeRespaldar(Data, ultimo.AddMinutes(10)));
        Assert.True(RespaldoAutomatico.DebeRespaldar(Data, ultimo.AddHours(2)));
    }
    [Fact]
    public void Crear_DosVecesEnElMismoSegundo_NoSobrescribe()
    {
        Preparar();
        var t = new DateTime(2026, 10, 30, 21, 0, 0);
        var a = RespaldoAutomatico.Crear(Data, t);
        var b = RespaldoAutomatico.Crear(Data, t);
        Assert.NotEqual(a.Ruta, b.Ruta);
        Assert.Equal(2, RespaldoAutomatico.Listar(RespaldoAutomatico.CarpetaDe(Data)).Count);
    }
    [Fact]
    public void Podar_DejaSoloLosMasNuevos()
    {
        Preparar();
        var carpeta = RespaldoAutomatico.CarpetaDe(Data);
        for (int i = 0; i < 25; i++)
            RespaldoAutomatico.Crear(Data, new DateTime(2026, 1, 1).AddDays(i));
        var lista = RespaldoAutomatico.Listar(carpeta);
        Assert.Equal(RespaldoAutomatico.MaxRespaldos, lista.Count);
        Assert.Contains("20260125", lista[0].Name);   // el más nuevo es el primero
        Assert.DoesNotContain(lista, f => f.Name.Contains("20260101"));
    }
    [Fact]
    public void CarpetaExtra_RecibeCopia_YErrorNoRompeElRespaldo()
    {
        Preparar();
        var ok = RespaldoAutomatico.Crear(Data, DateTime.Now, Extra);
        Assert.NotNull(ok.RutaExtra);
        Assert.True(File.Exists(ok.RutaExtra));
        var archivo = Path.Combine(_dir, "no_es_carpeta");
        File.WriteAllText(archivo, "x");
        var mal = RespaldoAutomatico.Crear(Data, DateTime.Now.AddSeconds(1), archivo);
        Assert.True(File.Exists(mal.Ruta));
        Assert.Null(mal.RutaExtra);
        Assert.False(string.IsNullOrEmpty(mal.ErrorExtra));
    }
    [Fact]
    public void Restaurar_DevuelveLosArchivos_YGuardaCopiaDeLoActual()
    {
        Preparar("{\"state\":{\"v\":1}}");
        var r = RespaldoAutomatico.Crear(Data, DateTime.Now);
        File.WriteAllText(Path.Combine(Data, "data.json"), "{\"state\":{\"v\":2}}");
        File.Delete(Path.Combine(Data, "foto_e1_1.png"));
        RespaldoAutomatico.Restaurar(r.Ruta, Data);
        Assert.Contains("\"v\":1", File.ReadAllText(Path.Combine(Data, "data.json")));
        Assert.True(File.Exists(Path.Combine(Data, "foto_e1_1.png")));
        Assert.Contains("\"v\":2", File.ReadAllText(Path.Combine(Data, "data.json.antes_de_restaurar")));
    }
    [Fact]
    public void Restaurar_RechazaZipSinDataJson_YJsonInvalido_SinTocarNada()
    {
        Preparar("{\"state\":{\"v\":1}}");
        var sinData = Path.Combine(_dir, "sin_data.zip");
        using (var z = ZipFile.Open(sinData, ZipArchiveMode.Create))
            z.CreateEntry("otra_cosa.txt");
        Assert.Throws<InvalidOperationException>(() => RespaldoAutomatico.Restaurar(sinData, Data));
        var malo = Path.Combine(_dir, "malo.zip");
        using (var z = ZipFile.Open(malo, ZipArchiveMode.Create))
        using (var w = new StreamWriter(z.CreateEntry("data.json").Open()))
            w.Write("{esto no es json");
        Assert.ThrowsAny<Exception>(() => RespaldoAutomatico.Restaurar(malo, Data));
        Assert.Contains("\"v\":1", File.ReadAllText(Path.Combine(Data, "data.json")));
    }
    [Fact]
    public void Restaurar_IgnoraEntradasConRutas()
    {
        Preparar();
        var zipPath = Path.Combine(_dir, "ruta.zip");
        using (var z = ZipFile.Open(zipPath, ZipArchiveMode.Create))
        {
            using (var w = new StreamWriter(z.CreateEntry("data.json").Open())) w.Write("{}");
            using (var w = new StreamWriter(z.CreateEntry("../escape.txt").Open())) w.Write("x");
            using (var w = new StreamWriter(z.CreateEntry("sub/otro.txt").Open())) w.Write("x");
        }
        RespaldoAutomatico.Restaurar(zipPath, Data);
        Assert.False(File.Exists(Path.Combine(_dir, "escape.txt")));
        Assert.False(File.Exists(Path.Combine(Data, "otro.txt")));
        Assert.False(File.Exists(Path.Combine(Data, "sub", "otro.txt")));
    }
}
