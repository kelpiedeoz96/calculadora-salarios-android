using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using ClosedXML.Excel;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;
namespace CalculadoraDeSalarios.Core;
public static class Export
{
    static readonly Cat[] Payc =
    {
        Cat.ONoc, Cat.ODom, Cat.ONocDom, Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom,
    };
    static string Lbl(Cat c) => Engine.CatLabel[c].Replace(" (incluida en el salario)", "");
    static double Dec(int min) => Engine.JsRound(min / 60.0 * 100) / 100;
    static object DominicalTexto(Config cfg, DateOnly desde, DateOnly hasta)
    {
        double a = Engine.EffectiveDom(cfg, desde), b = Engine.EffectiveDom(cfg, hasta);
        if (a == b) return a;
        return FormattableString.Invariant($"{a} hasta {Engine.Key(Engine.DominicalCienDesde.AddDays(-1))}, {b} desde {Engine.Key(Engine.DominicalCienDesde)}");
    }
    static string DayType(DayRow r)
        => r.Rest && r.IsFest ? "Descanso y festivo" : r.Rest ? "Descanso" : r.IsFest ? "Festivo" : "Normal";
    static string ShiftText(DayRecord? r)
    {
        if (r == null) return "";
        var parts = new List<string>();
        if (!string.IsNullOrEmpty(r.In1) && !string.IsNullOrEmpty(r.Out1)) parts.Add(r.In1 + "-" + r.Out1);
        if (!string.IsNullOrEmpty(r.In2) && !string.IsNullOrEmpty(r.Out2)) parts.Add(r.In2 + "-" + r.Out2);
        return string.Join(" / ", parts);
    }
    static IEnumerable<DayRow> DetailRows(Analysis a) => a.Rows.Where(r => r.InPeriod && (r.DayMin > 0 || r.Rest));
    static string Money(double v) => Fmt.Money((long)Engine.JsRound(v));
    public static byte[]? LoadEmbeddedLogo()
    {
        var asm = typeof(Export).Assembly;
        var name = asm.GetManifestResourceNames().FirstOrDefault(n => n.EndsWith("logo.png", StringComparison.OrdinalIgnoreCase));
        if (name == null) return null;
        using var s = asm.GetManifestResourceStream(name);
        if (s == null) return null;
        using var ms = new MemoryStream();
        s.CopyTo(ms);
        return ms.ToArray();
    }
    static void PutRow(IXLWorksheet ws, int row, IEnumerable<object> vals)
    {
        int c = 0;
        foreach (var v in vals)
        {
            c++;
            var cell = ws.Cell(row, c);
            switch (v)
            {
                case string s: cell.Value = s; break;
                case double d: cell.Value = d; break;
                case long l: cell.Value = (double)l; break;
                case int i: cell.Value = (double)i; break;
                case bool b: cell.Value = b; break;
                default: cell.Value = v?.ToString() ?? ""; break;
            }
        }
    }
    static void Sheet(XLWorkbook wb, string name, List<List<object>> rows, double[] widths)
    {
        var ws = wb.Worksheets.Add(name);
        for (int i = 0; i < rows.Count; i++) PutRow(ws, i + 1, rows[i]);
        int cols = rows[0].Count;
        for (int i = 1; i <= cols; i++) ws.Column(i).Width = i <= widths.Length ? widths[i - 1] : 16;
        ws.Row(1).Style.Font.Bold = true;
        ws.Row(1).Style.Fill.BackgroundColor = XLColor.FromHtml("#E2EFEE");
    }
    public static byte[] Excel(AppState sSnapshot, UiState uiSnapshot, IList<Employee> emps, (DateOnly From, DateOnly To) period)
    {
        var cfg = sSnapshot.Cfg;
        var (pf, pt) = period;
        bool incl = uiSnapshot.Incl;
        var festSet = new HashSet<string>(sSnapshot.Festivos);

        var list = emps.Select(e =>
        {
            var r = Engine.Analyze(e, cfg, festSet, pf, pt);
            var p = Engine.GetPayroll(e, cfg, r.TotalAmount);
            return new EmpSummary(e, r, p, incl ? p.Neto : r.TotalAmount);
        }).ToList();

        var head = new List<object> { "Empleado", "Cédula", "Cargo", "Salario mensual", "Valor hora", "Horas totales" };
        head.AddRange(Engine.Cats.Select(c => (object)("Horas: " + Lbl(c))));
        head.AddRange(Payc.Select(c => (object)("$ " + Engine.CatLabel[c])));
        head.Add("Recargos + extras ($)");
        if (incl) head.AddRange(new object[] { "Básico quincenal", "Auxilio", "Total devengado", "Salud", "Pensión", "Neto a pagar" });
        var sum = new List<List<object>> { head };
        foreach (var s in list)
        {
            var row = new List<object>
            {
                s.Emp.Name, s.Emp.Cedula, s.Emp.Cargo, s.P.Sal, s.R.Rates.Hv, Dec(s.R.TotalMin),
            };
            row.AddRange(Engine.Cats.Select(c => (object)Dec(s.R.TotMin[(int)c])));
            row.AddRange(Payc.Select(c => (object)s.R.TotAmount[(int)c]));
            row.Add(s.R.TotalAmount);
            if (incl)
                row.AddRange(new object[] { s.P.BasicoQ, s.P.AuxQ, s.P.Devengado, -s.P.Salud, -s.P.Pension, s.P.Neto });
            sum.Add(row);
        }
        var dh = new List<object> { "Empleado", "Semana", "Fecha", "Día", "Tipo", "Jornada", "Descanso (min)", "Horas" };
        dh.AddRange(Engine.Cats.Select(c => (object)("H: " + Lbl(c))));
        dh.AddRange(Payc.Select(c => (object)("$ " + Engine.CatLabel[c])));
        dh.Add("Total del día ($)");
        var det = new List<List<object>> { dh };
        foreach (var s in list)
            foreach (var r in DetailRows(s.R))
            {
                var row = new List<object>
                {
                    s.Emp.Name, Engine.Key(Engine.MondayOf(r.Date)), Engine.Key(r.Date), Engine.DowFull[r.Dow],
                    DayType(r), ShiftText(r.Rec), r.Rec?.Brk ?? 0.0, Dec(r.DayMin),
                };
                row.AddRange(Engine.Cats.Select(c => (object)Dec(r.Cats[(int)c])));
                row.AddRange(Payc.Select(c => (object)r.Amounts[(int)c]));
                row.Add(r.DayAmount);
                det.Add(row);
            }
        var par = new List<List<object>>
        {
            new() { "Parámetro", "Valor" },
            new() { "Periodo", Engine.Key(pf) + " a " + Engine.Key(pt) },
            new() { "Empresa", cfg.RazonSocial },
            new() { "NIT", string.IsNullOrWhiteSpace(cfg.Nit) ? "—" : cfg.Nit },
            new() { "Tope semanal (h)", cfg.WeeklyCap },
            new() { "Jornada diaria (h)", cfg.DailyRef },
            new() { "Tope diario como extra", cfg.DailyCapOn ? "Sí" : "No" },
            new() { "Nocturna", cfg.NightStart + " a " + cfg.NightEnd },
            new() { "Regla de minutos", cfg.MinuteRuleOn ? "Salida <" + cfg.MinValid + "min → trunca a la hora" : "No" },
            new() { "Recargo nocturno %", cfg.Pct.Noc },
            new() { "Extra diurna %", cfg.Pct.Hed },
            new() { "Extra nocturna %", cfg.Pct.Hen },
            new() { "Dominical %", DominicalTexto(cfg, pf, pt) },
            new() { "Divisor valor hora", cfg.Divisor },
            new() { "Salario mínimo", cfg.Smmlv },
            new() { "Auxilio", cfg.Auxilio },
            new() { "Salud %", cfg.SaludPct },
            new() { "Pensión %", cfg.PensionPct },
            new() { "Descuentos sobre", cfg.DeductBase == "basico" ? "Básico" : "Básico + recargos" },
            new() { "Festivos", string.Join(", ", sSnapshot.Festivos) },
        };
        using var wb = new XLWorkbook();
        Sheet(wb, "Resumen", sum, new double[] { 26, 14, 20, 14, 11, 12 });
        Sheet(wb, "Detalle diario", det, new double[] { 26, 13, 12, 11, 16, 24, 12, 12 });
        Sheet(wb, "Parámetros", par, new double[] { 36, 60 });
        using var ms = new MemoryStream();
        wb.SaveAs(ms);
        return ms.ToArray();
    }
    const string Teal = "#0e5a61";
    static IContainer HCell(IContainer c) => c.Background(Teal).PaddingVertical(2).PaddingHorizontal(3);
    static IContainer BCell(IContainer c) => c.BorderBottom(0.5f).BorderColor("#d0d7d6").PaddingVertical(1.5f).PaddingHorizontal(3);
    static void Head(IContainer c, string t, bool right, float size = 8.5f)
    {
        var x = HCell(c);
        if (right) x = x.AlignRight();
        x.Text(t).FontSize(size).Bold().FontColor("#ffffff");
    }
    static void Body(IContainer c, string t, bool right, bool bold = false, float size = 8.5f)
    {
        var x = BCell(c);
        if (right) x = x.AlignRight();
        var d = x.Text(t).FontSize(size);
        if (bold) d.Bold();
    }
    static readonly string[] Meses =
    {
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    };
    public static byte[] Pdf(AppState sSnapshot, UiState uiSnapshot, IList<Employee> emps, (DateOnly From, DateOnly To) period, byte[]? logo)
    {
        QuestPDF.Settings.License = LicenseType.Community;
        var cfg = sSnapshot.Cfg;
        var (pf, pt) = period;
        bool incl = uiSnapshot.Incl;
        var festSet = new HashSet<string>(sSnapshot.Festivos);
        var hoy = DateTime.Today;
        string fechaHoy = $"{hoy.Day:00} de {Meses[hoy.Month - 1]} de {hoy.Year}";
        string hmz(int m) => m > 0 ? Engine.FmtHM(m) : "";
        var doc = Document.Create(container =>
        {
            foreach (var emp in emps)
            {
                var r = Engine.Analyze(emp, cfg, festSet, pf, pt);
                var p = Engine.GetPayroll(emp, cfg, r.TotalAmount);
                var s = new EmpSummary(emp, r, p, incl ? p.Neto : r.TotalAmount);
                container.Page(page =>
                {
                    page.Size(PageSizes.Letter.Landscape());
                    page.Margin(14, Unit.Millimetre);
                    page.DefaultTextStyle(x => x.FontSize(9));
                    page.Content().Column(col =>
                    {
                        col.Spacing(8);
                        col.Item().Row(row =>
                        {
                            if (logo != null) row.ConstantItem(34, Unit.Millimetre).Image(logo);
                            row.RelativeItem().PaddingLeft(logo != null ? 8 : 0).Column(c =>
                            {
                                c.Item().Text("Comprobante de recargos y horas extra").FontSize(15).Bold();
                                c.Item().Text(cfg.RazonSocial).FontSize(10);
                                c.Item().Text($"Periodo: {Engine.Key(pf)} a {Engine.Key(pt)}").FontSize(10);
                                c.Item().Text("Empleado: " + emp.Name
                                    + (emp.Cedula != "" ? "   C.C. " + emp.Cedula : "")
                                    + (emp.Cargo != "" ? "   Cargo: " + emp.Cargo : "")).FontSize(10);
                                c.Item().Text($"Salario mensual: {Money(s.P.Sal)}   Valor hora: {Money(s.R.Rates.Hv)}   Tope semanal: {cfg.WeeklyCap:0.##} h").FontSize(10);
                            });
                        });
                        col.Item().Row(row =>
                        {
                            row.RelativeItem(3).Table(t =>
                            {
                                t.ColumnsDefinition(cd =>
                                {
                                    cd.RelativeColumn(4);
                                    cd.ConstantColumn(42);
                                    cd.ConstantColumn(58);
                                    cd.ConstantColumn(66);
                                });
                                t.Header(h =>
                                {
                                    Head(h.Cell(), "Concepto", false);
                                    Head(h.Cell(), "Horas", true);
                                    Head(h.Cell(), "Valor/hora", true);
                                    Head(h.Cell(), "Total", true);
                                });
                                foreach (var c in Engine.Cats)
                                {
                                    int min = s.R.TotMin[(int)c];
                                    if (min == 0 && c != Cat.ODiu) continue;
                                    Body(t.Cell(), Engine.CatLabel[c], false);
                                    Body(t.Cell(), Engine.FmtHM(min), true);
                                    Body(t.Cell(), c == Cat.ODiu ? "-" : Money(s.R.Rates[c]), true);
                                    Body(t.Cell(), Fmt.Money(s.R.TotAmount[(int)c]), true);
                                }
                                Body(t.Cell(), "Total", false, true);
                                Body(t.Cell(), Engine.FmtHM(s.R.TotalMin), true, true);
                                Body(t.Cell(), "", true, true);
                                Body(t.Cell(), Fmt.Money(s.R.TotalAmount), true, true);
                            });
                            row.ConstantItem(18);
                            row.RelativeItem(2).Table(t =>
                            {
                                t.ColumnsDefinition(cd =>
                                {
                                    cd.RelativeColumn(3);
                                    cd.RelativeColumn(2);
                                });
                                t.Header(h =>
                                {
                                    Head(h.Cell(), "Liquidación", false, 9);
                                    Head(h.Cell(), "Valor", true, 9);
                                });
                                var liq = incl
                                    ? new List<(string, string)>
                                    {
                                        ("Salario básico quincenal", Fmt.Money(s.P.BasicoQ)),
                                        ("Auxilio de transporte", Fmt.Money(s.P.AuxQ)),
                                        ("Recargos y horas extra", Fmt.Money(s.P.Recargos)),
                                        ("Total devengado", Fmt.Money(s.P.Devengado)),
                                        ($"Salud ({cfg.SaludPct:0.##}%)", "- " + Fmt.Money(s.P.Salud)),
                                        ($"Pensión ({cfg.PensionPct:0.##}%)", "- " + Fmt.Money(s.P.Pension)),
                                        ("Neto a pagar", Fmt.Money(s.P.Neto)),
                                    }
                                    : new List<(string, string)>
                                    {
                                        ("Recargos y horas extra", Fmt.Money(s.R.TotalAmount)),
                                        ("Neto a pagar", Fmt.Money(s.R.TotalAmount)),
                                    };
                                for (int i = 0; i < liq.Count; i++)
                                {
                                    bool last = i == liq.Count - 1;
                                    Body(t.Cell(), liq[i].Item1, false, last, 9);
                                    Body(t.Cell(), liq[i].Item2, true, last, 9);
                                }
                            });
                        });
                        col.Item().Table(t =>
                        {
                            t.ColumnsDefinition(cd =>
                            {
                                cd.ConstantColumn(58);
                                cd.ConstantColumn(86);
                                cd.RelativeColumn(3);
                                for (int i = 0; i < 9; i++) cd.ConstantColumn(36);
                                cd.ConstantColumn(60);
                            });
                            string[] hd = { "Fecha", "Tipo", "Jornada", "Horas", "Ord.", "Noc.", "Dom.", "Noc-Dom", "HED", "HEN", "HED-Dom", "HEN-Dom", "Recargos" };
                            t.Header(h =>
                            {
                                for (int i = 0; i < hd.Length; i++) Head(h.Cell(), hd[i], i >= 3, 7.5f);
                            });
                            foreach (var rw in DetailRows(s.R))
                            {
                                Body(t.Cell(), Fmt.Dmy(rw.Date) + " " + Engine.Dow[rw.Dow], false, false, 7.5f);
                                Body(t.Cell(), DayType(rw), false, false, 7.5f);
                                Body(t.Cell(), ShiftText(rw.Rec), false, false, 7.5f);
                                Body(t.Cell(), hmz(rw.DayMin), true, false, 7.5f);
                                foreach (var c in Engine.Cats) Body(t.Cell(), hmz(rw.Cats[(int)c]), true, false, 7.5f);
                                Body(t.Cell(), rw.DayAmount > 0 ? Fmt.Money(rw.DayAmount) : "", true, false, 7.5f);
                            }
                        });
                        col.Item().PaddingTop(20).ShowEntire().Column(sig =>
                        {
                            sig.Item().Row(rw =>
                            {
                                rw.ConstantItem(190).Column(a =>
                                {
                                    a.Item().Height(26).BorderBottom(0.7f).BorderColor("#5a5a5a");
                                    a.Item().PaddingTop(3).Text("Firma del empleado").FontSize(8).FontColor("#3c3c3c");
                                    a.Item().Text("Nombre: " + emp.Name).FontSize(7.5f).FontColor("#6e6e6e");
                                    a.Item().Text("C.C.: " + (emp.Cedula != "" ? emp.Cedula : "____________________")).FontSize(7.5f).FontColor("#6e6e6e");
                                });
                                rw.ConstantItem(40);
                                rw.ConstantItem(190).Column(b =>
                                {
                                    b.Item().Height(26).BorderBottom(0.7f).BorderColor("#5a5a5a");
                                    b.Item().PaddingTop(3).Text("Firma del empleador").FontSize(8).FontColor("#3c3c3c");
                                    b.Item().Text(cfg.RazonSocial).FontSize(7.5f).FontColor("#6e6e6e");
                                    b.Item().Text("NIT: " + (string.IsNullOrWhiteSpace(cfg.Nit) ? "____________________" : cfg.Nit)).FontSize(7.5f).FontColor("#6e6e6e");
                                });
                            });
                            sig.Item().PaddingTop(14).Text("Lugar y fecha: ____________________,  " + fechaHoy)
                                .FontSize(7.5f).FontColor("#6e6e6e");
                        });
                    });
                });
            }
        });

        return doc.GeneratePdf();
    }
}
