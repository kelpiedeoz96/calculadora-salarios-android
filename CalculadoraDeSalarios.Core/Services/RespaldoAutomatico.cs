using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text.Json;
namespace CalculadoraDeSalarios.Core.Services;
public sealed record ResultadoRespaldo(string Ruta, string? RutaExtra, string? ErrorExtra);
public static class RespaldoAutomatico
{
    public const string CarpetaNombre = "respaldos";
    public const string Prefijo = "respaldo_";
    public const int MaxRespaldos = 20;
    public static readonly TimeSpan IntervaloMinimo = TimeSpan.FromHours(1);
    public static string CarpetaDe(string dataDir) => Path.Combine(dataDir, CarpetaNombre);
    public static List<FileInfo> Listar(string carpeta)
    {
        if (!Directory.Exists(carpeta)) return new List<FileInfo>();
        return new DirectoryInfo(carpeta)
            .GetFiles(Prefijo + "*.zip")
            .OrderByDescending(f => f.Name, StringComparer.Ordinal)
            .ToList();
    }
    public static bool DebeRespaldar(string dataDir, DateTime ahora)
    {
        var dataJson = Path.Combine(dataDir, "data.json");
        if (!File.Exists(dataJson)) return false;

        var ultimo = Listar(CarpetaDe(dataDir)).FirstOrDefault();
        if (ultimo == null) return true;

        if (File.GetLastWriteTime(dataJson) <= ultimo.LastWriteTime) return false;
        return ahora - ultimo.LastWriteTime >= IntervaloMinimo;
    }
    public static ResultadoRespaldo Crear(string dataDir, DateTime ahora, string? carpetaExtra = null)
    {
        var dataJson = Path.Combine(dataDir, "data.json");
        if (!File.Exists(dataJson))
            throw new InvalidOperationException("Todavía no hay datos guardados para respaldar.");
        var carpeta = CarpetaDe(dataDir);
        Directory.CreateDirectory(carpeta);
        var baseName = Prefijo + ahora.ToString("yyyyMMdd_HHmmss");
        var destino = Path.Combine(carpeta, baseName + ".zip");
        int n = 1;
        while (File.Exists(destino))
            destino = Path.Combine(carpeta, $"{baseName}_{n++}.zip");
        var temporal = destino + ".tmp";
        try
        {
            using (var fs = new FileStream(temporal, FileMode.Create, FileAccess.Write))
            using (var zip = new ZipArchive(fs, ZipArchiveMode.Create))
            {
                foreach (var archivo in Directory.GetFiles(dataDir))
                {
                    var nombre = Path.GetFileName(archivo);
                    if (nombre.EndsWith(".tmp", StringComparison.OrdinalIgnoreCase)) continue;
                    if (nombre.Contains(".corrupted.", StringComparison.Ordinal)) continue;
                    var entrada = zip.CreateEntry(nombre, CompressionLevel.Optimal);
                    entrada.LastWriteTime = new DateTimeOffset(File.GetLastWriteTime(archivo));
                    using var origen = new FileStream(archivo, FileMode.Open, FileAccess.Read,
                        FileShare.ReadWrite | FileShare.Delete);
                    using var salida = entrada.Open();
                    origen.CopyTo(salida);
                }
            }
            File.Move(temporal, destino);
        }
        finally
        {
            if (File.Exists(temporal)) { try { File.Delete(temporal); } catch { } }
        }
        Podar(carpeta, MaxRespaldos);
        string? rutaExtra = null, errorExtra = null;
        if (!string.IsNullOrWhiteSpace(carpetaExtra))
        {
            try
            {
                Directory.CreateDirectory(carpetaExtra);
                rutaExtra = Path.Combine(carpetaExtra, Path.GetFileName(destino));
                File.Copy(destino, rutaExtra, true);
                Podar(carpetaExtra, MaxRespaldos);
            }
            catch (Exception ex)
            {
                rutaExtra = null;
                errorExtra = ex.Message;
            }
        }
        return new ResultadoRespaldo(destino, rutaExtra, errorExtra);
    }
    public static int Podar(string carpeta, int conservar)
    {
        int borrados = 0;
        foreach (var viejo in Listar(carpeta).Skip(Math.Max(1, conservar)))
        {
            try { viejo.Delete(); borrados++; } catch { }
        }
        return borrados;
    }
    public static void Restaurar(string zipPath, string dataDir)
    {
        if (!File.Exists(zipPath)) throw new FileNotFoundException("No se encontró el respaldo.", zipPath);
        var tmp = Path.Combine(Path.GetTempPath(), "nomina_restore_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tmp);
        try
        {
            using (var zip = ZipFile.OpenRead(zipPath))
            {
                foreach (var e in zip.Entries)
                {
                    if (string.IsNullOrEmpty(e.Name) || e.FullName != e.Name) continue;
                    if (e.Name.Contains("..", StringComparison.Ordinal)) continue;
                    e.ExtractToFile(Path.Combine(tmp, e.Name), true);
                }
            }
            var dataTmp = Path.Combine(tmp, "data.json");
            if (!File.Exists(dataTmp))
                throw new InvalidOperationException("Ese .zip no es un respaldo válido (no trae data.json).");
            using (JsonDocument.Parse(File.ReadAllText(dataTmp))) { }
            Directory.CreateDirectory(dataDir);
            var actual = Path.Combine(dataDir, "data.json");
            if (File.Exists(actual))
                File.Copy(actual, actual + ".antes_de_restaurar", true);
            foreach (var f in Directory.GetFiles(tmp))
                File.Copy(f, Path.Combine(dataDir, Path.GetFileName(f)), true);
        }
        finally
        {
            try { Directory.Delete(tmp, true); } catch { }
        }
    }
}
