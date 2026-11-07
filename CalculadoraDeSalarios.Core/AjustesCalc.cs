using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text.RegularExpressions;
namespace CalculadoraDeSalarios.Core;
public static class AjustesCalc
{
    static readonly Regex HoraRegex = new(@"^([01]?\d|2[0-3]):?([0-5]\d)$", RegexOptions.Compiled);
    static readonly Regex TurnoRegex = new(@"^(\d{2}):(\d{2})-(\d{2}):(\d{2})$", RegexOptions.Compiled);
    static readonly Regex FestivoRegex = new(@"\d{4}-\d{2}-\d{2}", RegexOptions.Compiled);
    public static bool TryParseNumero(string? texto, out double valor)
    {
        valor = 0;
        if (string.IsNullOrWhiteSpace(texto)) return true;

        var limpio = texto.Trim().Replace(',', '.');
        if (!double.TryParse(limpio, NumberStyles.Float, CultureInfo.InvariantCulture, out double parsed)) return false;
        if (!double.IsFinite(parsed)) return false;
        valor = parsed;
        return true;
    }

    public static string FormatearNumero(double v) => v.ToString(CultureInfo.InvariantCulture);
    public static bool TryParseHora(string? texto, out string normalizada)
    {
        normalizada = string.Empty;
        if (string.IsNullOrWhiteSpace(texto)) return false;
        var m = HoraRegex.Match(texto.Trim());
        if (!m.Success) return false;
        int h = int.Parse(m.Groups[1].Value, CultureInfo.InvariantCulture);
        int min = int.Parse(m.Groups[2].Value, CultureInfo.InvariantCulture);
        normalizada = $"{h:D2}:{min:D2}";
        return true;
    }
    public static List<string> ParseTurnos(string? texto)
    {
        var resultado = new List<string>();
        if (string.IsNullOrWhiteSpace(texto)) return resultado;

        foreach (var linea in texto.Split(new[] { "\r\n", "\r", "\n" }, StringSplitOptions.None))
        {
            var l = linea.Trim();
            var m = TurnoRegex.Match(l);
            if (!m.Success) continue;
            int h1 = int.Parse(m.Groups[1].Value, CultureInfo.InvariantCulture);
            int m1 = int.Parse(m.Groups[2].Value, CultureInfo.InvariantCulture);
            int h2 = int.Parse(m.Groups[3].Value, CultureInfo.InvariantCulture);
            int m2 = int.Parse(m.Groups[4].Value, CultureInfo.InvariantCulture);
            if (h1 <= 23 && m1 <= 59 && h2 <= 23 && m2 <= 59)
                resultado.Add(l);
        }
        return resultado;
    }
    public static List<string> ParseFestivos(string? texto)
    {
        if (string.IsNullOrWhiteSpace(texto)) return new List<string>();

        var encontrados = new HashSet<string>(StringComparer.Ordinal);
        foreach (Match m in FestivoRegex.Matches(texto))
        {
            if (DateOnly.TryParseExact(m.Value, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out _))
                encontrados.Add(m.Value);
        }
        return encontrados.OrderBy(x => x, StringComparer.Ordinal).ToList();
    }
    public static string UnirLineas(IEnumerable<string>? lineas)
        => lineas == null ? string.Empty : string.Join("\n", lineas);
}
