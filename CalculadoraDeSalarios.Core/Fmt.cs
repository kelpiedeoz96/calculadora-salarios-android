using System;
using System.Globalization;
using System.Linq;
using System.Text.RegularExpressions;
namespace CalculadoraDeSalarios.Core;
public static class Fmt
{
    static readonly Regex Thousands = new(@"\B(?=(\d{3})+(?!\d))", RegexOptions.Compiled);
    public static string Money(long n)
    {
        bool neg = n < 0;
        var s = Thousands.Replace(Math.Abs(n).ToString(CultureInfo.InvariantCulture), ".");
        return (neg ? "-" : "") + "$ " + s;
    }
    public static string Dmy(DateOnly d) => $"{d.Day:00}/{d.Month:00}";
    public static string DmyFull(DateOnly d) => $"{d.Day:00}/{d.Month:00}/{d.Year}";
    public static string Initials(string name)
    {
        var parts = (name ?? "").Split(' ', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        var s = string.Concat(parts.Take(2).Select(p => p[0]));
        return s.Length == 0 ? "·" : s.ToUpperInvariant();
    }
    public static int HueOf(string name)
    {
        int h = 0;
        foreach (char c in name ?? "") h = (h * 31 + c) % 360;
        return h;
    }
}
