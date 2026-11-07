using System;
using System.Collections.Generic;
using System.Linq;
namespace CalculadoraDeSalarios.Core;
public record EmpleadosTotales(
    int TotalMin,
    int NocMin,
    int DomMin,
    int ExtraMin,
    long RecargosAmount,
    long BasicoQ,
    long AuxQ,
    long Descuentos,
    long Neto
);
public static class EmpleadosCalc
{
    public static bool Matches(Employee emp, string? query)
    {
        if (string.IsNullOrWhiteSpace(query)) return true;
        var q = query.Trim().ToLowerInvariant();
        var haystack = $"{emp.Name} {emp.Cedula} {emp.Cargo}".ToLowerInvariant();
        return haystack.Contains(q);
    }
    public static int SumCats(Analysis a, params Cat[] cats)
    {
        int sum = 0;
        foreach (var c in cats)
        {
            sum += a.TotMin[(int)c];
        }
        return sum;
    }
    public static EmpleadosTotales CalcularTotales(IEnumerable<EmpSummary> summaries, bool inclSueldo)
    {
        int totalMin = 0;
        int nocMin = 0;
        int domMin = 0;
        int extraMin = 0;
        long recargos = 0;
        long basico = 0;
        long aux = 0;
        long desc = 0;
        long neto = 0;
        foreach (var s in summaries)
        {
            totalMin += s.R.TotalMin;
            nocMin += SumCats(s.R, Cat.ONoc);
            domMin += SumCats(s.R, Cat.ODom, Cat.ONocDom);
            extraMin += SumCats(s.R, Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom);
            recargos += s.R.TotalAmount;
            if (inclSueldo)
            {
                basico += s.P.BasicoQ;
                aux += s.P.AuxQ;
                desc += s.P.Descuentos;
                neto += s.P.Neto;
            }
        }
        return new EmpleadosTotales(
            totalMin,
            nocMin,
            domMin,
            extraMin,
            recargos,
            basico,
            aux,
            desc,
            neto
        );
    }
}
