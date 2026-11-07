using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
namespace CalculadoraDeSalarios.Core;
public static class FestivosCalc
{
    public static DateOnly DomingoPascua(int year)
    {
        int a = year % 19, b = year / 100, c = year % 100;
        int d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return new DateOnly(year, month, day);
    }
    static DateOnly LunesSiguiente(DateOnly d)
    {
        int dias = ((int)DayOfWeek.Monday - (int)d.DayOfWeek + 7) % 7;
        return d.AddDays(dias);
    }
    public static List<string> ParaAnio(int year)
    {
        var pascua = DomingoPascua(year);
        var set = new HashSet<DateOnly>
        {
            new(year, 1, 1), new(year, 5, 1), new(year, 7, 20), new(year, 8, 7),
            new(year, 12, 8), new(year, 12, 25),
            pascua.AddDays(-3), pascua.AddDays(-2),
        };
        foreach (var (mes, dia) in new[] { (1, 6), (3, 19), (6, 29), (8, 15), (10, 12), (11, 1), (11, 11) })
            set.Add(LunesSiguiente(new DateOnly(year, mes, dia)));
        foreach (int offset in new[] { 39, 60, 68 })
            set.Add(LunesSiguiente(pascua.AddDays(offset)));
        return set.Select(Clave).OrderBy(x => x, StringComparer.Ordinal).ToList();
    }
    static string Clave(DateOnly d) => d.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
    public static int AgregarAnio(List<string> festivos, int year)
    {
        var existentes = new HashSet<string>(festivos, StringComparer.Ordinal);
        int agregados = 0;
        foreach (var f in ParaAnio(year))
            if (existentes.Add(f)) { festivos.Add(f); agregados++; }
        if (agregados > 0)
            festivos.Sort(StringComparer.Ordinal);
        return agregados;
    }
    public static int CompletarAnios(List<string> festivos, IEnumerable<int> anios)
    {
        int total = 0;
        foreach (int year in anios.Distinct())
        {
            string prefijo = year.ToString("0000", CultureInfo.InvariantCulture) + "-";
            if (festivos.Any(f => f.StartsWith(prefijo, StringComparison.Ordinal))) continue;
            total += AgregarAnio(festivos, year);
        }
        return total;
    }
}
