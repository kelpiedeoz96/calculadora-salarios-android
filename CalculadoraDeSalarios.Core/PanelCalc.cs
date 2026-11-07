using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
namespace CalculadoraDeSalarios.Core;
public record PanelResultados(
    int EmpleadosCount,
    int TotalMin,
    long TotalAmount,
    int ExtraMin,
    int NightMin,
    int DomMin,
    int ConExtra,
    int[] MinutosPorCategoria,
    int MaxCategoria,
    List<string> Alertas,
    List<EmpSummary> Top5,
    long Promedio,
    string PctExtraTexto
);
public static class PanelCalc
{
    public static PanelResultados Calcular(IEnumerable<EmpSummary> summaries)
    {
        var list = summaries.ToList();
        int empCount = list.Count;
        int totalMin = 0;
        long totalAmount = 0;
        int totalExtra = 0;
        int totalNight = 0;
        int totalDom = 0;
        int withExtra = 0;
        var catTotals = new int[8];
        var alerts = new List<string>();
        foreach (var s in list)
        {
            totalMin += s.R.TotalMin;
            totalAmount += s.R.TotalAmount;
            int empExtra = s.R.TotMin[(int)Cat.HED] +
                           s.R.TotMin[(int)Cat.HEN] +
                           s.R.TotMin[(int)Cat.HEDDom] +
                           s.R.TotMin[(int)Cat.HENDom];
            totalExtra += empExtra;
            if (empExtra > 0) withExtra++;
            totalNight += s.R.TotMin[(int)Cat.ONoc] + s.R.TotMin[(int)Cat.ONocDom];
            totalDom += s.R.TotMin[(int)Cat.ODom] + s.R.TotMin[(int)Cat.ONocDom];
            for (int i = 0; i < 8; i++)
                catTotals[i] += s.R.TotMin[i];
            string name = string.IsNullOrWhiteSpace(s.Emp.Name) ? "Sin nombre" : s.Emp.Name;
            foreach (var a in s.R.Alerts)
                alerts.Add($"{name}: {a}");
        }
        int maxCat = Math.Max(1, catTotals.Max());
        long promedio = empCount > 0 ? (long)Engine.JsRound((double)totalAmount / empCount) : 0;
        string pctExtra = totalMin > 0
            ? (totalExtra * 100.0 / totalMin).ToString("0.0", CultureInfo.InvariantCulture) + "% del total"
            : "—";
        var top5 = list.OrderByDescending(s => s.R.TotalAmount).Take(5).ToList();
        return new PanelResultados(
            empCount, totalMin, totalAmount, totalExtra, totalNight, totalDom, withExtra,
            catTotals, maxCat, alerts, top5, promedio, pctExtra);
    }
}
