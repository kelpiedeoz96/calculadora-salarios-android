using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text.RegularExpressions;
namespace CalculadoraDeSalarios.Core;
public static class Engine
{
    public static readonly Cat[] Cats = Enum.GetValues<Cat>();
    public static readonly Dictionary<Cat, string> CatLabel = new()
    {
        [Cat.ODiu] = "Ordinaria diurna (incluida en el salario)",
        [Cat.ONoc] = "Ordinaria nocturna",
        [Cat.ODom] = "Ordinaria dominical/festiva/descanso",
        [Cat.ONocDom] = "Ordinaria nocturna dominical/festiva/descanso",
        [Cat.HED] = "Extra diurna",
        [Cat.HEN] = "Extra nocturna",
        [Cat.HEDDom] = "Extra diurna dominical/festiva/descanso",
        [Cat.HENDom] = "Extra nocturna dominical/festiva/descanso",
    };
    public static readonly Dictionary<Cat, string> CatShort = new()
    {
        [Cat.ODiu] = "Ord", [Cat.ONoc] = "Noc", [Cat.ODom] = "Dom", [Cat.ONocDom] = "Noc-Dom",
        [Cat.HED] = "HED", [Cat.HEN] = "HEN", [Cat.HEDDom] = "HED-Dom", [Cat.HENDom] = "HEN-Dom",
    };
    public static readonly string[] Dow = { "dom", "lun", "mar", "mié", "jue", "vie", "sáb" };
    public static readonly string[] DowFull = { "domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado" };
    public static double JsRound(double x) => Math.Floor(x + 0.5);
    public static string Key(DateOnly d) => d.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
    public static int Weekday(DateOnly d) => (int)d.DayOfWeek;
    public static DateOnly MondayOf(DateOnly d) => d.AddDays(-((Weekday(d) + 6) % 7));
    static string Dm(DateOnly d) => $"{d.Day:00}/{d.Month:00}";
    static readonly Regex HmRx = new(@"^[0-9]{1,2}:[0-9]{2}\z", RegexOptions.Compiled);
    public static int? ParseHM(string? s)
    {
        if (string.IsNullOrEmpty(s) || !HmRx.IsMatch(s)) return null;
        var p = s.Split(':');
        return int.Parse(p[0]) * 60 + int.Parse(p[1]);
    }
    public static string FmtHM(int min) => (min / 60) + ":" + ((int)JsRound(min % 60)).ToString("00");
    static bool IsNight(int t, int ns, int ne)
    {
        int m = ((t % 1440) + 1440) % 1440;
        return ns > ne ? (m >= ns || m < ne) : (m >= ns && m < ne);
    }
    public static List<Seg> DaySegments(DayRecord rec, Config cfg)
    {
        var segs = new List<Seg>();
        int ns = ParseHM(cfg.NightStart) ?? 0, ne = ParseHM(cfg.NightEnd) ?? 0;
        foreach (var (i, o) in new[] { (rec.In1, rec.Out1), (rec.In2, rec.Out2) })
        {
            var a = ParseHM(i); var bn = ParseHM(o);
            if (a == null || bn == null) continue;
            int b = bn.Value;
            if (b <= a.Value) b += 1440;
            if (cfg.MinuteRuleOn)
            {
                int mm = b % 60;
                if (mm < cfg.MinValid) b -= mm;
            }
            if (b <= a.Value) continue;
            for (int t = a.Value; t < b; t++)
            {
                bool n = IsNight(t, ns, ne);
                var last = segs.Count > 0 ? segs[^1] : null;
                if (last != null && last.Night == n) last.Min++;
                else segs.Add(new Seg { Min = 1, Night = n });
            }
        }
        int brk = (int)Math.Max(0, rec.Brk);
        foreach (var pass in new[] { false, true })
            foreach (var s in segs)
                if (s.Night == pass && brk > 0)
                {
                    int d = Math.Min(s.Min, brk);
                    s.Min -= d; brk -= d;
                }
        return segs.Where(s => s.Min > 0).ToList();
    }
    public static bool IsRest(Employee emp, DateOnly date, DayRecord? rec)
        => rec?.Desc != null ? rec.Desc.Value : (emp.RestDay != null && Weekday(date) == emp.RestDay);
    public static readonly DateOnly DominicalCienDesde = new(2027, 7, 1);
    public static double EffectiveDom(Config cfg, DateOnly date)
    {
        double dom = cfg.Pct.Dom;
        if (cfg.DomAutoOn && date >= DominicalCienDesde) dom = Math.Max(dom, 100);
        return dom;
    }
    public static Rates GetRates(Employee emp, Config cfg) => BuildRates(emp, cfg, cfg.Pct.Dom);
    public static Rates GetRates(Employee emp, Config cfg, DateOnly date) => BuildRates(emp, cfg, EffectiveDom(cfg, date));
    static Rates BuildRates(Employee emp, Config cfg, double domPct)
    {
        double sal = emp.Salario > 0 ? emp.Salario : cfg.Smmlv;
        double hv = sal / cfg.Divisor;
        if (cfg.RateRound) hv = JsRound(hv);
        double N = cfg.Pct.Noc / 100, D = domPct / 100, A = cfg.Pct.Hed / 100, B = cfg.Pct.Hen / 100;
        var r = new Rates { Hv = hv };
        r.ByCat[(int)Cat.ODiu] = 0;
        r.ByCat[(int)Cat.ONoc] = hv * N;
        r.ByCat[(int)Cat.ODom] = hv * D;
        r.ByCat[(int)Cat.ONocDom] = hv * (D + N);
        r.ByCat[(int)Cat.HED] = hv * (1 + A);
        r.ByCat[(int)Cat.HEN] = hv * (1 + B);
        r.ByCat[(int)Cat.HEDDom] = hv * (1 + D + A);
        r.ByCat[(int)Cat.HENDom] = hv * (1 + D + B);
        if (cfg.RateRound)
            for (int i = 0; i < 8; i++) r.ByCat[i] = JsRound(r.ByCat[i]);
        return r;
    }
    public static Analysis Analyze(Employee emp, Config cfg, HashSet<string> fest, DateOnly from, DateOnly to)
    {
        var rtCache = new Dictionary<double, Rates>();
        Rates RatesFor(DateOnly d)
        {
            double dom = EffectiveDom(cfg, d);
            if (!rtCache.TryGetValue(dom, out var r)) rtCache[dom] = r = BuildRates(emp, cfg, dom);
            return r;
        }
        var res = new Analysis { Rates = RatesFor(to) };
        int capMin = (int)JsRound(cfg.WeeklyCap * 60), refMin = (int)JsRound(cfg.DailyRef * 60);
        int iHED = (int)Cat.HED, iHEN = (int)Cat.HEN, iHEDD = (int)Cat.HEDDom, iHEND = (int)Cat.HENDom;
        for (var w = MondayOf(from); w <= to; w = w.AddDays(7))
        {
            int acc = 0, wkWorked = 0, wkExtra = 0;
            var missing = new List<DateOnly>();
            bool straddle = false;
            for (int i = 0; i < 7; i++)
            {
                var date = w.AddDays(i);
                if (date > to) break;
                emp.Days.TryGetValue(Key(date), out var rec);
                bool rest = IsRest(emp, date, rec);
                bool isFest = fest.Contains(Key(date));
                bool dom = rest || isFest;
                bool inPeriod = date >= from && date <= to;
                if (!inPeriod) straddle = true;
                var segs = rec != null ? DaySegments(rec, cfg) : new List<Seg>();
                var cats = new int[8];
                int dayOrd = 0;
                foreach (var s in segs)
                {
                    int remW = Math.Max(0, capMin - acc);
                    int remD = cfg.DailyCapOn ? Math.Max(0, refMin - dayOrd) : int.MaxValue;
                    int ord = Math.Min(s.Min, Math.Min(remW, remD));
                    int ext = s.Min - ord;
                    acc += ord; dayOrd += ord;
                    if (ord > 0)
                        cats[(int)(dom ? (s.Night ? Cat.ONocDom : Cat.ODom)
                                       : (s.Night ? Cat.ONoc : Cat.ODiu))] += ord;
                    if (ext > 0)
                        cats[(int)(dom ? (s.Night ? Cat.HENDom : Cat.HEDDom)
                                       : (s.Night ? Cat.HEN : Cat.HED))] += ext;
                }
                int dayMin = cats.Sum();
                int extraMin = cats[iHED] + cats[iHEN] + cats[iHEDD] + cats[iHEND];
                wkWorked += dayMin; wkExtra += extraMin;
                var rt = RatesFor(date);
                var amounts = new long[8];
                long dayAmount = 0;
                for (int c = 0; c < 8; c++)
                {
                    amounts[c] = (long)JsRound(cats[c] / 60.0 * rt.ByCat[c]);
                    dayAmount += amounts[c];
                }
                if (inPeriod)
                {
                    for (int c = 0; c < 8; c++) { res.TotMin[c] += cats[c]; res.TotAmount[c] += amounts[c]; }
                    res.TotalMin += dayMin; res.TotalAmount += dayAmount;
                    if (extraMin > 120)
                        res.Alerts.Add($"Más de 2 horas extra el {Dow[Weekday(date)]} {Dm(date)} ({FmtHM(extraMin)}).");
                }
                else if (date < from && dayMin == 0 && !rest && rec == null)
                {
                    missing.Add(date);
                }
                res.Rows.Add(new DayRow
                {
                    Date = date, Dow = Weekday(date), Rec = rec, Rest = rest, IsFest = isFest,
                    Dom = dom, InPeriod = inPeriod, Cats = cats, DayMin = dayMin, ExtraMin = extraMin,
                    Amounts = amounts, DayAmount = dayAmount
                });
            }
            if (wkExtra > 720)
                res.Alerts.Add($"Más de 12 horas extra en la semana del {Dm(w)} ({FmtHM(wkExtra)}).");
            if (straddle && missing.Count > 0)
                res.Alerts.Add($"Semana del {Dm(w)}: sin datos en {string.Join(", ", missing.Select(Dm))}. El tope de 42 h se cuenta con la semana completa.");
            var wEnd = w.AddDays(6);
            res.Weeks.Add(new Week
            {
                Start = w, End = wEnd > to ? to : wEnd,
                WorkedMin = wkWorked, ExtraMin = wkExtra, Straddle = straddle
            });
        }
        return res;
    }
    public static Payroll GetPayroll(Employee emp, Config cfg, long recargos)
    {
        double sal = emp.Salario > 0 ? emp.Salario : cfg.Smmlv;
        bool auxOn = emp.Aux && sal <= 2 * cfg.Smmlv;
        long auxQ = auxOn ? (long)JsRound(cfg.Auxilio / 2) : 0;
        long totalQ = (long)JsRound((sal + (auxOn ? cfg.Auxilio : 0)) / 2);
        long basicoQ = totalQ - auxQ;
        double bas = sal / 2 + (cfg.DeductBase == "con_recargos" ? recargos : 0);
        long salud = (long)JsRound(bas * cfg.SaludPct / 100);
        long pension = (long)JsRound(bas * cfg.PensionPct / 100);
        long devengado = basicoQ + auxQ + recargos;
        return new Payroll
        {
            Sal = sal, AuxOn = auxOn, BasicoQ = basicoQ, AuxQ = auxQ, Recargos = recargos,
            Devengado = devengado, Base = bas, Salud = salud, Pension = pension,
            Descuentos = salud + pension, Neto = devengado - salud - pension
        };
    }
}
