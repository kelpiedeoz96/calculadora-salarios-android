using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class PanelCalcTests
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
    static EmpSummary Fake(string name, long amount, int min = 60)
    {
        var a = new Analysis { TotalAmount = amount, TotalMin = min };
        return new EmpSummary(new Employee { Name = name }, a, new Payroll(), amount);
    }
    [Fact]
    public void PanelCalc_OraculoEscenarioA_KpisDistribucionYTop5()
    {
        var state = Backup.Load(FindTestDataPath("respaldo_sintetico.json"));
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
        var res = PanelCalc.Calcular(summaries);
        Assert.Equal(6, res.EmpleadosCount);
        Assert.Equal(29934, res.TotalMin);
        Assert.Equal(2804543, res.TotalAmount);
        Assert.Equal(7214, res.ExtraMin);
        Assert.Equal(5, res.ConExtra);
        Assert.Equal(467424, res.Promedio);
        Assert.Equal(3614, res.NightMin);
        Assert.Equal(2130, res.DomMin);
        Assert.Equal(new[] { 17306, 3284, 1800, 330, 5474, 1740, 0, 0 }, res.MinutosPorCategoria);
        Assert.Equal(17306, res.MaxCategoria);
        Assert.Equal(new[] { "Empleado 2", "Empleado 3", "Empleado 1", "Empleado 4", "Empleado 6" },
                     res.Top5.Select(s => s.Emp.Name).ToArray());
        Assert.Equal(1270267, res.Top5[0].R.TotalAmount);
        Assert.Equal(22, res.Alertas.Count);
        Assert.All(res.Alertas, a => Assert.StartsWith("Empleado ", a));
        Assert.StartsWith("Empleado 1: Más de 2 horas extra el sáb 03/10", res.Alertas[0]);
        Assert.Equal("24.1% del total", res.PctExtraTexto);
    }
    [Fact]
    public void PanelCalc_SinEmpleados_DevuelveCerosYGuion()
    {
        var res = PanelCalc.Calcular(new List<EmpSummary>());
        Assert.Equal(0, res.EmpleadosCount);
        Assert.Equal(0, res.TotalMin);
        Assert.Equal(0, res.TotalAmount);
        Assert.Equal(0, res.Promedio);
        Assert.Equal(0, res.ConExtra);
        Assert.Equal("—", res.PctExtraTexto);
        Assert.Empty(res.Top5);
        Assert.Empty(res.Alertas);
        Assert.Equal(1, res.MaxCategoria);
    }
    [Fact]
    public void PanelCalc_Empate_ConservaElOrdenOriginalYLimitaACinco()
    {
        var list = new List<EmpSummary>
        {
            Fake("A", 100), Fake("B", 100), Fake("C", 500), Fake("D", 100),
            Fake("E", 100), Fake("F", 100), Fake("G", 100),
        };
        var res = PanelCalc.Calcular(list);
        Assert.Equal(new[] { "C", "A", "B", "D", "E" }, res.Top5.Select(s => s.Emp.Name).ToArray());
    }
}
