using System;
using System.Collections.Generic;
namespace CalculadoraDeSalarios.Core;
public record DiaCalendario(
    int Dia,
    DateOnly Fecha,
    string Clave,
    bool TieneFila,
    int DayMin,
    int ExtraMin,
    bool Dom,
    bool Festivo,
    bool EnPeriodo,
    bool EsHoy,
    double Fraccion,
    bool BarraExtra,
    string Turnos
);
public record MesCalendario(int Pad0, int Semanas, List<DiaCalendario> Dias);
public static class CalendarioCalc
{
    public static string ObtenerTextoTurnos(DayRecord? rec)
    {
        if (rec == null) return "";
        var parts = new List<string>();
        if (!string.IsNullOrWhiteSpace(rec.In1) && !string.IsNullOrWhiteSpace(rec.Out1))
            parts.Add(rec.In1 + "-" + rec.Out1);
        if (!string.IsNullOrWhiteSpace(rec.In2) && !string.IsNullOrWhiteSpace(rec.Out2))
            parts.Add(rec.In2 + "-" + rec.Out2);
        return string.Join(" / ", parts);
    }
    public static MesCalendario Construir(
        int year, int month, IEnumerable<DayRow> rows,
        DateOnly pFrom, DateOnly pTo, DateOnly today)
    {
        int diasDelMes = DateTime.DaysInMonth(year, month);
        var dia1 = new DateOnly(year, month, 1);
        int pad0 = (Engine.Weekday(dia1) + 6) % 7;      // la semana empieza el lunes
        int semanas = (pad0 + diasDelMes + 6) / 7;
        var rowDict = new Dictionary<DateOnly, DayRow>();
        foreach (var r in rows) rowDict[r.Date] = r;
        var dias = new List<DiaCalendario>(diasDelMes);
        for (int d = 1; d <= diasDelMes; d++)
        {
            var fecha = new DateOnly(year, month, d);
            string clave = Engine.Key(fecha);
            bool enPeriodo = fecha >= pFrom && fecha <= pTo;
            bool esHoy = fecha == today;
            if (rowDict.TryGetValue(fecha, out var row))
            {
                double fraccion = 0.0;
                bool barraExtra = false;
                if (row.ExtraMin > 0) { fraccion = row.ExtraMin / 480.0; barraExtra = true; }
                else if (row.DayMin > 0) { fraccion = 1.0; }
                dias.Add(new DiaCalendario(
                    d, fecha, clave, true, row.DayMin, row.ExtraMin, row.Dom, row.IsFest,
                    enPeriodo, esHoy, fraccion, barraExtra, ObtenerTextoTurnos(row.Rec)));
            }
            else
            {
                dias.Add(new DiaCalendario(
                    d, fecha, clave, false, 0, 0, false, false,
                    enPeriodo, esHoy, 0.0, false, ""));
            }
        }
        return new MesCalendario(pad0, semanas, dias);
    }
}
