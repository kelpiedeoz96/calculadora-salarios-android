using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
namespace CalculadoraDeSalarios.Core;
public class Pct
{
    public double Noc { get; set; } = 35;
    public double Hed { get; set; } = 25;
    public double Hen { get; set; } = 75;
    public double Dom { get; set; } = 90;
}
public class Config
{
    public double WeeklyCap { get; set; } = 42;
    public double DailyRef { get; set; } = 7;
    public bool DailyCapOn { get; set; } = false;
    public string NightStart { get; set; } = "19:00";
    public string NightEnd { get; set; } = "06:00";
    public bool MinuteRuleOn { get; set; } = true;
    public double MinValid { get; set; } = 30;
    public double Divisor { get; set; } = 210;
    public bool RateRound { get; set; } = true;
    public bool DomOrdFull { get; set; } = false;
    public bool DomAutoOn { get; set; } = true;
    public double Smmlv { get; set; } = 1750905;
    public double Auxilio { get; set; } = 249095;
    public double SaludPct { get; set; } = 4;
    public double PensionPct { get; set; } = 4;
    public string DeductBase { get; set; } = "basico";
    public Pct Pct { get; set; } = new();
    public List<string> Turnos { get; set; } = new()
    {
        "08:00-12:00", "08:00-17:00", "14:00-22:00"
    };
    public string RazonSocial { get; set; } = "Mi Empresa";
    public string Nit { get; set; } = "";
    public string? LogoCustomRelPath { get; set; } = null;
}
public class DayRecord
{
    public string? In1 { get; set; }
    public string? Out1 { get; set; }
    public string? In2 { get; set; }
    public string? Out2 { get; set; }
    [JsonConverter(typeof(LenientDoubleConverter))]
    public double Brk { get; set; }
    public bool? Desc { get; set; }
}
public class Employee
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N")[..7];
    public string Name { get; set; } = "Nuevo empleado";
    public string Cedula { get; set; } = "";
    public string Cargo { get; set; } = "Operario";
    [JsonConverter(typeof(LenientDoubleConverter))]
    public double Salario { get; set; }
    public bool Aux { get; set; } = true;
    public int? RestDay { get; set; }
    public string? PhotoRelPath { get; set; }
    public Dictionary<string, DayRecord> Days { get; set; } = new();
}
public class AppState
{
    public int V { get; set; } = 2;
    public Config Cfg { get; set; } = new();
    public List<string> Festivos { get; set; } = new()
    {
        "2026-01-01","2026-01-12","2026-03-23","2026-04-02","2026-04-03","2026-05-01",
        "2026-05-18","2026-06-08","2026-06-15","2026-06-29","2026-07-20","2026-08-07",
        "2026-08-17","2026-10-12","2026-11-02","2026-11-16","2026-12-08","2026-12-25"
    };
    public List<Employee> Emps { get; set; } = new();
    public AppState Clone()
    {
        var json = JsonSerializer.Serialize(this, Backup.Opts);
        return JsonSerializer.Deserialize<AppState>(json, Backup.Opts) ?? new AppState();
    }
}
public class UiState
{
    public string Tab { get; set; } = "dashboard";
    public string Month { get; set; } = "";
    public string Mode { get; set; } = "q1";
    public string From { get; set; } = "";
    public string To { get; set; } = "";
    public string Sel { get; set; } = "";
    public bool Incl { get; set; }
    public string Search { get; set; } = "";
    public string ThemeMode { get; set; } = "system";
    public bool ReduceAnimations { get; set; } = false;
    public bool IsSidebarCollapsed { get; set; } = false;
    public string BackupExtraDir { get; set; } = "";
    [JsonPropertyName("dark")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public bool? Dark { get; set; }
    public UiState Clone()
    {
        var json = JsonSerializer.Serialize(this, Backup.Opts);
        return JsonSerializer.Deserialize<UiState>(json, Backup.Opts) ?? new UiState();
    }
}
public class HistoryEntry
{
    public long Ts { get; set; }
    public string Reason { get; set; } = "";
    public string Payload { get; set; } = "";
}
public class PersistContainer
{
    [JsonPropertyName("state")]
    public AppState State { get; set; } = new();
    [JsonPropertyName("ui")]
    public UiState Ui { get; set; } = new();
    [JsonPropertyName("history")]
    public List<HistoryEntry> History { get; set; } = new();
}
public class LenientDoubleConverter : JsonConverter<double>
{
    public override bool HandleNull => true;
    public override double Read(ref Utf8JsonReader r, Type t, JsonSerializerOptions o)
    {
        switch (r.TokenType)
        {
            case JsonTokenType.Number:
                return r.GetDouble();
            case JsonTokenType.String:
                return double.TryParse(r.GetString(), NumberStyles.Float,
                    CultureInfo.InvariantCulture, out var v) ? v : 0;
            default:
                return 0;
        }
    }
    public override void Write(Utf8JsonWriter w, double v, JsonSerializerOptions o)
        => w.WriteNumberValue(v);
}
public static class Backup
{
    public static readonly JsonSerializerOptions Opts = new()
    {
        PropertyNameCaseInsensitive = true,
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };
    public static AppState Load(string path)
        => JsonSerializer.Deserialize<AppState>(File.ReadAllText(path), Opts) ?? new AppState();
}
