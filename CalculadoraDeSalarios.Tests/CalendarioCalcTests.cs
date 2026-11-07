using System;
using System.Collections.Generic;
using System.IO;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class CalendarioCalcTests
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
    public void CalendarioCalc_Octubre2026_Empleado1_ReflejaElOraculo()
    {
        var state = Backup.Load(FindTestDataPath("respaldo_sintetico.json"));
        var festSet = new HashSet<string>(state.Festivos);
        var emp1 = state.Emps[0];
        var from = new DateOnly(2026, 10, 1);
        var to = new DateOnly(2026, 10, 15);
        var today = new DateOnly(2026, 10, 8);
        var analysis = Engine.Analyze(emp1, state.Cfg, festSet, from, to);
        var mes = CalendarioCalc.Construir(2026, 10, analysis.Rows, from, to, today);
        Assert.Equal(3, mes.Pad0);
        Assert.Equal(5, mes.Semanas);
        Assert.Equal(31, mes.Dias.Count);
        var d3 = mes.Dias[2];
        Assert.Equal(3, d3.Dia);
        Assert.True(d3.TieneFila);
        Assert.Equal(540, d3.DayMin);
        Assert.Equal(180, d3.ExtraMin);
        Assert.True(d3.BarraExtra);
        Assert.Equal(0.375, d3.Fraccion);
        Assert.False(d3.Dom);
        Assert.False(d3.Festivo);
        Assert.True(d3.EnPeriodo);
        var d4 = mes.Dias[3];
        Assert.Equal(240, d4.DayMin);
        Assert.Equal(240, d4.ExtraMin);
        Assert.True(d4.BarraExtra);
        Assert.Equal(0.5, d4.Fraccion);
        var d12 = mes.Dias[11];
        Assert.True(d12.Festivo);
        Assert.True(d12.Dom);
        Assert.Equal(540, d12.DayMin);
        Assert.Equal(0, d12.ExtraMin);
        Assert.False(d12.BarraExtra);
        Assert.Equal(1.0, d12.Fraccion);
        Assert.True(mes.Dias[7].EsHoy);
        Assert.False(d3.EsHoy);
        Assert.False(d12.EsHoy);
        Assert.True(mes.Dias[14].EnPeriodo);
        Assert.False(mes.Dias[15].EnPeriodo);
        var d31 = mes.Dias[30];
        Assert.False(d31.TieneFila);
        Assert.Equal(0, d31.DayMin);
        Assert.Equal(0.0, d31.Fraccion);
        Assert.Equal("", d31.Turnos);
    }
    [Fact]
    public void CalendarioCalc_MesesDeControl_SinFilas()
    {
        var emptyRows = new List<DayRow>();
        var today = new DateOnly(2027, 2, 10);
        var feb2027 = CalendarioCalc.Construir(2027, 2, emptyRows, new DateOnly(2027, 2, 1), new DateOnly(2027, 2, 28), today);
        Assert.Equal(0, feb2027.Pad0);
        Assert.Equal(4, feb2027.Semanas);
        Assert.Equal(28, feb2027.Dias.Count);
        var nov2026 = CalendarioCalc.Construir(2026, 11, emptyRows, new DateOnly(2026, 11, 1), new DateOnly(2026, 11, 30), today);
        Assert.Equal(6, nov2026.Pad0);
        Assert.Equal(6, nov2026.Semanas);
        Assert.Equal(30, nov2026.Dias.Count);
        var feb2028 = CalendarioCalc.Construir(2028, 2, emptyRows, new DateOnly(2028, 2, 1), new DateOnly(2028, 2, 29), today);
        Assert.Equal(29, feb2028.Dias.Count);
    }
    [Fact]
    public void ObtenerTextoTurnos_SoloParesCompletos()
    {
        var r1 = new DayRecord { In1 = "12:00", Out1 = "16:00", In2 = "17:00", Out2 = "21:00" };
        Assert.Equal("12:00-16:00 / 17:00-21:00", CalendarioCalc.ObtenerTextoTurnos(r1));
        var r2 = new DayRecord { In1 = "12:00", Out1 = "16:00" };
        Assert.Equal("12:00-16:00", CalendarioCalc.ObtenerTextoTurnos(r2));
        var r3 = new DayRecord { In1 = "12:00", Out1 = "" };
        Assert.Equal("", CalendarioCalc.ObtenerTextoTurnos(r3));
        Assert.Equal("", CalendarioCalc.ObtenerTextoTurnos(null));
    }
}
