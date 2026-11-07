using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class ScenarioDto
{
    public string Escenario { get; set; } = "";
    public List<string> Periodo { get; set; } = new();
    public bool IncluirSueldo { get; set; }
    public Config CfgAplicada { get; set; } = new();
    public List<EmpleadoExpectedDto> Empleados { get; set; } = new();
}
public class EmpleadoExpectedDto
{
    public string Nombre { get; set; } = "";
    public TarifaExpectedDto Tarifas { get; set; } = new();
    public Dictionary<string, TotCatDto> Totales { get; set; } = new();
    public int TotalMin { get; set; }
    public long TotalRecargos { get; set; }
    public PayrollExpectedDto Nomina { get; set; } = new();
    public List<WeekExpectedDto> Semanas { get; set; } = new();
    public List<string> Alertas { get; set; } = new();
    public List<DayExpectedDto> Dias { get; set; } = new();
}
public class TarifaExpectedDto
{
    public double Hv { get; set; }
    public double ODiu { get; set; }
    public double ONoc { get; set; }
    public double ODom { get; set; }
    public double ONocDom { get; set; }
    public double HED { get; set; }
    public double HEN { get; set; }
    public double HEDDom { get; set; }
    public double HENDom { get; set; }
}
public class TotCatDto
{
    public int Min { get; set; }
    public long Amount { get; set; }
}
public class PayrollExpectedDto
{
    public double Sal { get; set; }
    public bool AuxOn { get; set; }
    public long BasicoQ { get; set; }
    public long AuxQ { get; set; }
    public long Recargos { get; set; }
    public long Devengado { get; set; }
    public double Base { get; set; }
    public long Salud { get; set; }
    public long Pension { get; set; }
    public long Descuentos { get; set; }
    public long Neto { get; set; }
}
public class WeekExpectedDto
{
    public string Start { get; set; } = "";
    public string End { get; set; } = "";
    public int WorkedMin { get; set; }
    public int ExtraMin { get; set; }
    public bool Straddle { get; set; }
}
public class DayExpectedDto
{
    public string Fecha { get; set; } = "";
    public int Dow { get; set; }
    public bool Descanso { get; set; }
    public bool Festivo { get; set; }
    public bool Dominical { get; set; }
    public bool EnPeriodo { get; set; }
    public Dictionary<string, int> MinPorCategoria { get; set; } = new();
    public int DayMin { get; set; }
    public int ExtraMin { get; set; }
    public Dictionary<string, long> ValorPorCategoria { get; set; } = new();
    public long DayAmount { get; set; }
}
public class OracleRootDto
{
    public string Descripcion { get; set; } = "";
    public List<ScenarioDto> Escenarios { get; set; } = new();
}
public class RegressionOracleTests
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
    static OracleRootDto LoadOracle()
    {
        var oraclePath = FindTestDataPath("salida_esperada.json");
        var oracleJson = File.ReadAllText(oraclePath);
        return JsonSerializer.Deserialize<OracleRootDto>(oracleJson, Backup.Opts)
               ?? throw new InvalidOperationException("No se pudo deserializar el oráculo.");
    }
    static AppState LoadBackupState()
    {
        var backupPath = FindTestDataPath("respaldo_sintetico.json");
        return Backup.Load(backupPath);
    }
    static void CheckEqual<T>(T expected, T actual, string scenario, string employee, string detail)
    {
        Assert.True(
            EqualityComparer<T>.Default.Equals(expected, actual),
            $"[Fallo Escenario: {scenario}] [Empleado: {employee}] [{detail}] -> Esperado: {expected}, Obtenido: {actual}");
    }
    [Theory]
    [InlineData(0)]
    [InlineData(1)]
    [InlineData(2)]
    [InlineData(3)]
    [InlineData(4)]
    public void Escenario_DebeReproducirExactamenteElOraculo(int scenarioIndex)
    {
        var oracle = LoadOracle();
        Assert.True(scenarioIndex >= 0 && scenarioIndex < oracle.Escenarios.Count);
        var sc = oracle.Escenarios[scenarioIndex];
        var backupState = LoadBackupState();
        var festSet = new HashSet<string>(backupState.Festivos);
        var from = DateOnly.Parse(sc.Periodo[0]);
        var to = DateOnly.Parse(sc.Periodo[1]);
        var cfg = sc.CfgAplicada;
        foreach (var expectedEmp in sc.Empleados)
        {
            var actualEmp = backupState.Emps.FirstOrDefault(e => e.Name == expectedEmp.Nombre);
            Assert.True(actualEmp != null, $"[Escenario: {sc.Escenario}] Empleado '{expectedEmp.Nombre}' no encontrado en respaldo.");
            var analysis = Engine.Analyze(actualEmp!, cfg, festSet, from, to);
            var payroll = Engine.GetPayroll(actualEmp!, cfg, analysis.TotalAmount);
            CheckEqual(expectedEmp.TotalMin, analysis.TotalMin, sc.Escenario, expectedEmp.Nombre, "TotalMin");
            CheckEqual(expectedEmp.TotalRecargos, analysis.TotalAmount, sc.Escenario, expectedEmp.Nombre, "TotalRecargos ($)");
            CheckEqual(expectedEmp.Tarifas.Hv, analysis.Rates.Hv, sc.Escenario, expectedEmp.Nombre, "Tarifa Hv");
            CheckEqual(expectedEmp.Tarifas.ODiu, analysis.Rates[Cat.ODiu], sc.Escenario, expectedEmp.Nombre, "Tarifa ODiu");
            CheckEqual(expectedEmp.Tarifas.ONoc, analysis.Rates[Cat.ONoc], sc.Escenario, expectedEmp.Nombre, "Tarifa ONoc");
            CheckEqual(expectedEmp.Tarifas.ODom, analysis.Rates[Cat.ODom], sc.Escenario, expectedEmp.Nombre, "Tarifa ODom");
            CheckEqual(expectedEmp.Tarifas.ONocDom, analysis.Rates[Cat.ONocDom], sc.Escenario, expectedEmp.Nombre, "Tarifa ONocDom");
            CheckEqual(expectedEmp.Tarifas.HED, analysis.Rates[Cat.HED], sc.Escenario, expectedEmp.Nombre, "Tarifa HED");
            CheckEqual(expectedEmp.Tarifas.HEN, analysis.Rates[Cat.HEN], sc.Escenario, expectedEmp.Nombre, "Tarifa HEN");
            CheckEqual(expectedEmp.Tarifas.HEDDom, analysis.Rates[Cat.HEDDom], sc.Escenario, expectedEmp.Nombre, "Tarifa HEDDom");
            CheckEqual(expectedEmp.Tarifas.HENDom, analysis.Rates[Cat.HENDom], sc.Escenario, expectedEmp.Nombre, "Tarifa HENDom");
            foreach (var c in Engine.Cats)
            {
                var catName = c.ToString();
                var expCat = expectedEmp.Totales[catName];
                CheckEqual(expCat.Min, analysis.TotMin[(int)c], sc.Escenario, expectedEmp.Nombre, $"TotMin {catName}");
                CheckEqual(expCat.Amount, analysis.TotAmount[(int)c], sc.Escenario, expectedEmp.Nombre, $"TotAmount {catName}");
            }
            CheckEqual(expectedEmp.Nomina.Sal, payroll.Sal, sc.Escenario, expectedEmp.Nombre, "Nomina Salario");
            CheckEqual(expectedEmp.Nomina.AuxOn, payroll.AuxOn, sc.Escenario, expectedEmp.Nombre, "Nomina AuxOn");
            CheckEqual(expectedEmp.Nomina.BasicoQ, payroll.BasicoQ, sc.Escenario, expectedEmp.Nombre, "Nomina BasicoQ");
            CheckEqual(expectedEmp.Nomina.AuxQ, payroll.AuxQ, sc.Escenario, expectedEmp.Nombre, "Nomina AuxQ");
            CheckEqual(expectedEmp.Nomina.Recargos, payroll.Recargos, sc.Escenario, expectedEmp.Nombre, "Nomina Recargos");
            CheckEqual(expectedEmp.Nomina.Devengado, payroll.Devengado, sc.Escenario, expectedEmp.Nombre, "Nomina Devengado");
            CheckEqual(expectedEmp.Nomina.Base, payroll.Base, sc.Escenario, expectedEmp.Nombre, "Nomina Base");
            CheckEqual(expectedEmp.Nomina.Salud, payroll.Salud, sc.Escenario, expectedEmp.Nombre, "Nomina Salud");
            CheckEqual(expectedEmp.Nomina.Pension, payroll.Pension, sc.Escenario, expectedEmp.Nombre, "Nomina Pension");
            CheckEqual(expectedEmp.Nomina.Descuentos, payroll.Descuentos, sc.Escenario, expectedEmp.Nombre, "Nomina Descuentos");
            CheckEqual(expectedEmp.Nomina.Neto, payroll.Neto, sc.Escenario, expectedEmp.Nombre, "Nomina Neto");
            CheckEqual(expectedEmp.Semanas.Count, analysis.Weeks.Count, sc.Escenario, expectedEmp.Nombre, "Cant Semanas");
            for (int w = 0; w < expectedEmp.Semanas.Count; w++)
            {
                var expW = expectedEmp.Semanas[w];
                var actW = analysis.Weeks[w];
                CheckEqual(expW.Start, Engine.Key(actW.Start), sc.Escenario, expectedEmp.Nombre, $"Semana {w} Start");
                CheckEqual(expW.End, Engine.Key(actW.End), sc.Escenario, expectedEmp.Nombre, $"Semana {w} End");
                CheckEqual(expW.WorkedMin, actW.WorkedMin, sc.Escenario, expectedEmp.Nombre, $"Semana {w} WorkedMin");
                CheckEqual(expW.ExtraMin, actW.ExtraMin, sc.Escenario, expectedEmp.Nombre, $"Semana {w} ExtraMin");
                CheckEqual(expW.Straddle, actW.Straddle, sc.Escenario, expectedEmp.Nombre, $"Semana {w} Straddle");
            }
            CheckEqual(expectedEmp.Alertas.Count, analysis.Alerts.Count, sc.Escenario, expectedEmp.Nombre, "Cant Alertas");
            for (int a = 0; a < expectedEmp.Alertas.Count; a++)
            {
                CheckEqual(expectedEmp.Alertas[a], analysis.Alerts[a], sc.Escenario, expectedEmp.Nombre, $"Alerta {a}");
            }
            CheckEqual(expectedEmp.Dias.Count, analysis.Rows.Count, sc.Escenario, expectedEmp.Nombre, "Cant Dias");
            for (int d = 0; d < expectedEmp.Dias.Count; d++)
            {
                var expD = expectedEmp.Dias[d];
                var actD = analysis.Rows[d];
                var f = expD.Fecha;
                CheckEqual(expD.Fecha, Engine.Key(actD.Date), sc.Escenario, expectedEmp.Nombre, $"Dia {f} Fecha");
                CheckEqual(expD.Dow, actD.Dow, sc.Escenario, expectedEmp.Nombre, $"Dia {f} Dow");
                CheckEqual(expD.Descanso, actD.Rest, sc.Escenario, expectedEmp.Nombre, $"Dia {f} Rest");
                CheckEqual(expD.Festivo, actD.IsFest, sc.Escenario, expectedEmp.Nombre, $"Dia {f} Fest");
                CheckEqual(expD.Dominical, actD.Dom, sc.Escenario, expectedEmp.Nombre, $"Dia {f} Dom");
                CheckEqual(expD.EnPeriodo, actD.InPeriod, sc.Escenario, expectedEmp.Nombre, $"Dia {f} InPeriod");
                CheckEqual(expD.DayMin, actD.DayMin, sc.Escenario, expectedEmp.Nombre, $"Dia {f} DayMin");
                CheckEqual(expD.ExtraMin, actD.ExtraMin, sc.Escenario, expectedEmp.Nombre, $"Dia {f} ExtraMin");
                CheckEqual(expD.DayAmount, actD.DayAmount, sc.Escenario, expectedEmp.Nombre, $"Dia {f} DayAmount");
                foreach (var c in Engine.Cats)
                {
                    var catName = c.ToString();
                    CheckEqual(expD.MinPorCategoria[catName], actD.Cats[(int)c], sc.Escenario, expectedEmp.Nombre, $"Dia {f} Min {catName}");
                    CheckEqual(expD.ValorPorCategoria[catName], actD.Amounts[(int)c], sc.Escenario, expectedEmp.Nombre, $"Dia {f} Amount {catName}");
                }
            }
        }
    }
}
