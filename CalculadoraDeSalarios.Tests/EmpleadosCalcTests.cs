using System;
using System.Collections.Generic;
using System.IO;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class EmpleadosCalcTests
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
    [Theory]
    [InlineData("Empleado 1", "1001", "Operario", "empleado", true)]
    [InlineData("Empleado 2", "1002", "Cajero", "1002", true)]
    [InlineData("Empleado 3", "1003", "Administrador", "admin", true)]
    [InlineData("Empleado 4", "1004", "Cocina", "GERENTE", false)]
    [InlineData("Empleado 5", "1005", "Auxiliar", "", true)]
    [InlineData("Empleado 6", "1006", "Operario", "   ", true)]
    public void Matches_DebeFiltrarPorNombreCedulaCargo_InsensibleAMayusculasYEspacios(
        string nombre, string cedula, string cargo, string query, bool expected)
    {
        var emp = new Employee { Name = nombre, Cedula = cedula, Cargo = cargo };
        bool result = EmpleadosCalc.Matches(emp, query);
        Assert.Equal(expected, result);
    }
    [Fact]
    public void SumCats_DebeSumarDomFestYHorasExtraCorrectamente()
    {
        var backupPath = FindTestDataPath("respaldo_sintetico.json");
        var state = Backup.Load(backupPath);
        var festSet = new HashSet<string>(state.Festivos);
        var emp1 = state.Emps[0];
        var from = new DateOnly(2026, 10, 1);
        var to = new DateOnly(2026, 10, 15);
        var analysis = Engine.Analyze(emp1, state.Cfg, festSet, from, to);
        int domFest = EmpleadosCalc.SumCats(analysis, Cat.ODom, Cat.ONocDom);
        Assert.Equal(540, domFest);
        int extra = EmpleadosCalc.SumCats(analysis, Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom);
        Assert.Equal(840, extra);
    }
    [Fact]
    public void CalcularTotales_DebeSumarFilasConYSinSueldo()
    {
        var backupPath = FindTestDataPath("respaldo_sintetico.json");
        var state = Backup.Load(backupPath);
        var festSet = new HashSet<string>(state.Festivos);
        var from = new DateOnly(2026, 10, 1);
        var to = new DateOnly(2026, 10, 15);
        var summaries = new List<EmpSummary>();
        foreach (var emp in state.Emps)
        {
            var a = Engine.Analyze(emp, state.Cfg, festSet, from, to);
            var p = Engine.GetPayroll(emp, state.Cfg, a.TotalAmount);
            summaries.Add(new EmpSummary(emp, a, p, a.TotalAmount));
        }
        var totSin = EmpleadosCalc.CalcularTotales(summaries, inclSueldo: false);
        Assert.True(totSin.TotalMin > 0);
        Assert.True(totSin.RecargosAmount > 0);
        Assert.Equal(0, totSin.BasicoQ);
        Assert.Equal(0, totSin.Neto);
        var totCon = EmpleadosCalc.CalcularTotales(summaries, inclSueldo: true);
        Assert.Equal(totSin.TotalMin, totCon.TotalMin);
        Assert.Equal(totSin.RecargosAmount, totCon.RecargosAmount);
        Assert.True(totCon.BasicoQ > 0);
        Assert.True(totCon.Neto > 0);
        Assert.True(totCon.Descuentos > 0);
    }
}
