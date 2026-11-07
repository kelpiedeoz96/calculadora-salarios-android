using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class HistorialCalcTests
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
    public void ParsePayload_IdaYVuelta_ConservaEmpleadosYResultadosDelOraculo()
    {
        var state = Backup.Load(FindTestDataPath("respaldo_sintetico.json"));
        var opts = new JsonSerializerOptions(Backup.Opts) { DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull };
        var payload = JsonSerializer.Serialize(state, opts);
        var restaurado = HistorialCalc.ParsePayload(payload);
        Assert.NotNull(restaurado);
        Assert.Equal(6, restaurado!.Emps.Count);
        Assert.Equal(state.Emps.Select(e => e.Name).ToArray(), restaurado.Emps.Select(e => e.Name).ToArray());
        var from = new DateOnly(2026, 10, 1);
        var to = new DateOnly(2026, 10, 15);
        var a1 = Engine.Analyze(state.Emps[0], state.Cfg, new HashSet<string>(state.Festivos), from, to);
        var a2 = Engine.Analyze(restaurado.Emps[0], restaurado.Cfg, new HashSet<string>(restaurado.Festivos), from, to);
        Assert.Equal(282660, a1.TotalAmount);
        Assert.Equal(6420, a1.TotalMin);
        Assert.Equal(a1.TotalAmount, a2.TotalAmount);
        Assert.Equal(a1.TotalMin, a2.TotalMin);
    }
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("{no es json")]
    [InlineData("[1,2")]
    [InlineData("12345")]
    public void ParsePayload_Invalido_DevuelveNullSinLanzar(string? payload)
    {
        Assert.Null(HistorialCalc.ParsePayload(payload));
    }
    [Fact]
    public void EstadoInicial_TieneSeisEmpleadosYElCuartoDescansaElMiercoles()
    {
        var st = HistorialCalc.EstadoInicial();
        Assert.Equal(6, st.Emps.Count);
        for (int i = 0; i < 6; i++)
            Assert.Equal($"Empleado {i + 1}", st.Emps[i].Name);
        Assert.Equal(3, st.Emps[3].RestDay);
        Assert.Null(st.Emps[0].RestDay);
        Assert.NotEmpty(st.Festivos);
    }
    [Fact]
    public void FormatearFecha_UsaFormatoInvariantEnLaZonaIndicada()
    {
        Assert.Equal("30/10/2026 20:35", HistorialCalc.FormatearFecha(1793392500000L, TimeZoneInfo.Utc));
    }
    [Theory]
    [InlineData(10, "q1")]
    [InlineData(15, "q1")]
    [InlineData(16, "q2")]
    [InlineData(20, "q2")]
    public void Normalizar_MesVacio_FijaMesYQuincenaDeHoy(int dia, string modoEsperado)
    {
        var st = HistorialCalc.EstadoInicial();
        var ui = new UiState { Month = "", Mode = "" };
        HistorialCalc.Normalizar(st, ui, new DateOnly(2026, 10, dia));
        Assert.Equal("2026-10", ui.Month);
        Assert.Equal(modoEsperado, ui.Mode);
    }
    [Fact]
    public void Normalizar_MesYaDefinido_NoLoCambia()
    {
        var st = HistorialCalc.EstadoInicial();
        var ui = new UiState { Month = "2026-03", Mode = "q2" };
        HistorialCalc.Normalizar(st, ui, new DateOnly(2026, 10, 10));
        Assert.Equal("2026-03", ui.Month);
        Assert.Equal("q2", ui.Mode);
    }
    [Fact]
    public void Normalizar_SelInexistente_PasaAlPrimerEmpleado()
    {
        var st = HistorialCalc.EstadoInicial();
        var ui = new UiState { Month = "2026-10", Mode = "q1", Sel = "no_existe" };
        HistorialCalc.Normalizar(st, ui, new DateOnly(2026, 10, 1));
        Assert.Equal(st.Emps[0].Id, ui.Sel);
    }
    [Fact]
    public void Normalizar_SelValido_NoCambia()
    {
        var st = HistorialCalc.EstadoInicial();
        var ui = new UiState { Month = "2026-10", Mode = "q1", Sel = st.Emps[2].Id };
        HistorialCalc.Normalizar(st, ui, new DateOnly(2026, 10, 1));
        Assert.Equal(st.Emps[2].Id, ui.Sel);
    }
    [Fact]
    public void Normalizar_SinEmpleados_DejaSelVacio()
    {
        var st = new AppState();
        var ui = new UiState { Month = "2026-10", Mode = "q1", Sel = "x" };
        HistorialCalc.Normalizar(st, ui, new DateOnly(2026, 10, 1));
        Assert.Equal("", ui.Sel);
    }
}
