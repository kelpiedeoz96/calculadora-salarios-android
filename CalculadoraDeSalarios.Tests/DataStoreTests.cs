using System;
using System.IO;
using CalculadoraDeSalarios.Core;
using CalculadoraDeSalarios.Core.Services;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class DataStoreTests
{
    static string FindTestDataPath(string fileName)
    {
        var dir = AppDomain.CurrentDomain.BaseDirectory;
        var direct = Path.Combine(dir, "TestData", fileName);
        if (File.Exists(direct)) return direct;
        var current = new DirectoryInfo(dir);
        while (current != null)
        {
            var p = Path.Combine(current.FullName, "TestData", fileName);
            if (File.Exists(p)) return p;
            current = current.Parent;
        }
        throw new FileNotFoundException($"No se encontró '{fileName}'.");
    }
    [Fact]
    public void Load_DebeLeerFormatoAnidadoActual_YMigrarDarkAThemeMode()
    {
        var fixturePath = FindTestDataPath("data_formato_actual_v1.json");
        var tempDir = Path.Combine(Path.GetTempPath(), "test_nomina_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempDir);
        try
        {
            File.Copy(fixturePath, Path.Combine(tempDir, "data.json"));
            var store = new JsonDataStore(tempDir);
            var (state, ui, history) = store.Load();
            Assert.Equal(6, state.Emps.Count);
            Assert.True(state.Festivos.Count >= 18);
            Assert.Equal("liq", ui.Tab);
            Assert.True(ui.Incl);
            Assert.Equal("dark", ui.ThemeMode);
            Assert.Null(ui.Dark);
            Assert.Single(history);
            Assert.Equal("Respaldo inicial", history[0].Reason);
        }
        finally
        {
            if (Directory.Exists(tempDir)) Directory.Delete(tempDir, true);
        }
    }
    [Fact]
    public void Save_SeguidoDeLoad_DebeMantenerIdaYVueltaExacta()
    {
        var fixturePath = FindTestDataPath("data_formato_actual_v1.json");
        var tempDir = Path.Combine(Path.GetTempPath(), "test_roundtrip_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempDir);
        try
        {
            File.Copy(fixturePath, Path.Combine(tempDir, "data.json"));
            var store = new JsonDataStore(tempDir);
            var (state1, ui1, hist1) = store.Load();
            state1.Emps[0].Cargo = "Jefe de Cocina";
            ui1.Month = "2026-11";
            store.Save(state1, ui1, hist1);
            var (state2, ui2, hist2) = store.Load();
            Assert.Equal(6, state2.Emps.Count);
            Assert.Equal("Jefe de Cocina", state2.Emps[0].Cargo);
            Assert.True(state2.Emps[0].Days.ContainsKey("2026-10-01"));
            Assert.Equal(60, state2.Emps[0].Days["2026-10-01"].Brk);
            Assert.Equal("2026-11", ui2.Month);
            Assert.Single(hist2);
        }
        finally
        {
            if (Directory.Exists(tempDir)) Directory.Delete(tempDir, true);
        }
    }
    [Fact]
    public void ArchivoCorrupto_DebeCrearCopiaCorrupted_YNoLanzarExcepcion()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), "test_corrupt_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tempDir);
        try
        {
            var dataFile = Path.Combine(tempDir, "data.json");
            File.WriteAllText(dataFile, "{ json_invalido_totalmente_danado: [");
            var store = new JsonDataStore(tempDir);
            var (state, ui, history) = store.Load();
            Assert.NotNull(state);
            Assert.Equal(1, state.Emps.Count);
            Assert.Empty(history);
            var corruptedFiles = Directory.GetFiles(tempDir, "data.json.corrupted.*");
            Assert.Single(corruptedFiles);
        }
        finally
        {
            if (Directory.Exists(tempDir)) Directory.Delete(tempDir, true);
        }
    }
}
