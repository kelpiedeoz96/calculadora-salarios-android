using System;
using System.Globalization;
using System.Linq;
using System.Text.Json;
namespace CalculadoraDeSalarios.Core;

public static class HistorialCalc
{
    public static AppState? ParsePayload(string? payload)
    {
        if (string.IsNullOrWhiteSpace(payload)) return null;
        try
        {
            return JsonSerializer.Deserialize<AppState>(payload, Backup.Opts);
        }
        catch (Exception)
        {
            return null;
        }
    }
    public static AppState EstadoInicial()
    {
        var st = new AppState();   // conserva Cfg y festivos por defecto
        for (int i = 1; i <= 6; i++)
            st.Emps.Add(new Employee { Name = "Empleado " + i });
        st.Emps[3].RestDay = 3;
        return st;
    }
    public static string FormatearFecha(long tsMs, TimeZoneInfo? tz = null)
    {
        var dto = DateTimeOffset.FromUnixTimeMilliseconds(tsMs);
        var local = TimeZoneInfo.ConvertTime(dto, tz ?? TimeZoneInfo.Local);
        return local.ToString("dd/MM/yyyy HH:mm", CultureInfo.InvariantCulture);
    }
    public static void Normalizar(AppState s, UiState ui, DateOnly today)
    {
        if (string.IsNullOrEmpty(ui.Month))
        {
            ui.Month = $"{today.Year}-{today.Month:00}";
            ui.Mode = today.Day <= 15 ? "q1" : "q2";
        }

        if (s.Emps.All(e => e.Id != ui.Sel))
            ui.Sel = s.Emps.Count > 0 ? s.Emps[0].Id : "";
    }
}
