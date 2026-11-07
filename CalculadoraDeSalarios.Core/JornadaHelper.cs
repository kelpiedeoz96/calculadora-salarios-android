using System;
using System.Text.RegularExpressions;
namespace CalculadoraDeSalarios.Core;
public static class JornadaHelper
{
    static readonly Regex TimeRx = new(@"^(\d{1,2})(?::?(\d{2}))?$", RegexOptions.Compiled);
    public static string? NormTime(string? s)
    {
        s = (s ?? "").Trim();
        if (s == "") return "";
        var m = TimeRx.Match(s);
        if (!m.Success) return null;
        int h = int.Parse(m.Groups[1].Value);
        int mi = m.Groups[2].Success ? int.Parse(m.Groups[2].Value) : 0;
        if (h > 23 || mi > 59) return null;
        return $"{h:00}:{mi:00}";
    }
    public static void EditDay(Employee emp, DateOnly date, Action<DayRecord> mut)
    {
        var key = Engine.Key(date);
        if (!emp.Days.TryGetValue(key, out var rec))
        {
            rec = new DayRecord();
            emp.Days[key] = rec;
        }
        bool defRest = Engine.IsRest(emp, date, null);
        rec.Desc ??= defRest;
        mut(rec);
        bool empty = string.IsNullOrEmpty(rec.In1) && string.IsNullOrEmpty(rec.Out1)
                     && string.IsNullOrEmpty(rec.In2) && string.IsNullOrEmpty(rec.Out2)
                     && rec.Brk == 0 && rec.Desc == defRest;
        if (empty) emp.Days.Remove(key);
    }
    public static (int OrdMin, int ExtraMin, double OrdRatio, double ExtraRatio, double CapRatio) CalculateWeekGauge(int workedMin, int extraMin, int capMin)
    {
        int ord = Math.Max(0, workedMin - extraMin);
        double scale = Math.Max(Math.Max(capMin, workedMin), 1);
        double fo = ord / scale;
        double fe = extraMin / scale;
        double fc = Math.Min(1.0, (double)capMin / scale);
        return (ord, extraMin, fo, fe, fc);
    }
}
