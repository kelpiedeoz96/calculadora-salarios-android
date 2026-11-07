using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
namespace CalculadoraDeSalarios.Core.Services;
public class JsonDataStore : IDataStore
{
    public string DataDirectory { get; }
    public string DataPath => Path.Combine(DataDirectory, "data.json");
    static readonly JsonSerializerOptions ExportOpts = new(Backup.Opts)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };
    public JsonDataStore(string? customDataDirectory = null)
    {
        DataDirectory = customDataDirectory ?? Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "NominaApp");
    }
    public (AppState State, UiState Ui, List<HistoryEntry> History) Load()
    {
        if (!File.Exists(DataPath))
            return (CreateFreshState(), new UiState(), new List<HistoryEntry>());
        try
        {
            var text = File.ReadAllText(DataPath);
            using var doc = JsonDocument.Parse(text);
            var root = doc.RootElement;
            AppState state;
            UiState ui;
            List<HistoryEntry> history;
            if (root.TryGetProperty("state", out var stateEl))
            {
                state = JsonSerializer.Deserialize<AppState>(stateEl.GetRawText(), Backup.Opts) ?? new AppState();
                ui = root.TryGetProperty("ui", out var uiEl)
                    ? JsonSerializer.Deserialize<UiState>(uiEl.GetRawText(), Backup.Opts) ?? new UiState()
                    : new UiState();
                history = root.TryGetProperty("history", out var histEl)
                    ? JsonSerializer.Deserialize<List<HistoryEntry>>(histEl.GetRawText(), Backup.Opts) ?? new List<HistoryEntry>()
                    : new List<HistoryEntry>();
            }
            else
            {
                state = JsonSerializer.Deserialize<AppState>(text, Backup.Opts) ?? new AppState();
                ui = root.TryGetProperty("ui", out var uiEl)
                    ? JsonSerializer.Deserialize<UiState>(uiEl.GetRawText(), Backup.Opts) ?? new UiState()
                    : new UiState();
                history = root.TryGetProperty("history", out var histEl)
                    ? JsonSerializer.Deserialize<List<HistoryEntry>>(histEl.GetRawText(), Backup.Opts) ?? new List<HistoryEntry>()
                    : new List<HistoryEntry>();
            }
            if (ui.Dark.HasValue)
            {
                ui.ThemeMode = ui.Dark.Value ? "dark" : "light";
                ui.Dark = null;
            }
            else if (string.IsNullOrWhiteSpace(ui.ThemeMode))
            {
                ui.ThemeMode = "system";
            }
            return (state, ui, history);
        }
        catch
        {
            try
            {
                var corruptedPath = DataPath + ".corrupted." + DateTimeOffset.UtcNow.ToUnixTimeSeconds();
                File.Copy(DataPath, corruptedPath, true);
            }
            catch { }
            return (CreateFreshState(), new UiState(), new List<HistoryEntry>());
        }
    }
    public void Save(AppState state, UiState ui, List<HistoryEntry> history)
    {
        Directory.CreateDirectory(DataDirectory);
        var container = new PersistContainer
        {
            State = state,
            Ui = ui,
            History = history
        };
        var json = JsonSerializer.Serialize(container, Backup.Opts);
        var tempFile = Path.Combine(DataDirectory, Guid.NewGuid().ToString("N") + ".tmp");
        File.WriteAllText(tempFile, json);
        try
        {
            if (File.Exists(DataPath))
            {
                File.Move(tempFile, DataPath, overwrite: true);
            }
            else
            {
                File.Move(tempFile, DataPath);
            }
        }
        finally
        {
            if (File.Exists(tempFile))
            {
                try { File.Delete(tempFile); } catch { }
            }
        }
    }
    public string ExportJson(AppState state)
        => JsonSerializer.Serialize(state, ExportOpts);
    public AppState ImportBackup(string filePath)
        => Backup.Load(filePath);
    public string CopyLogoToDataDirectory(string sourceFilePath)
    {
        Directory.CreateDirectory(DataDirectory);
        var ext = Path.GetExtension(sourceFilePath);
        var targetFileName = "empresa_logo" + ext;
        var targetFullPath = Path.Combine(DataDirectory, targetFileName);
        File.Copy(sourceFilePath, targetFullPath, true);
        return targetFileName;
    }
    public byte[]? LoadLogoBytes(string? customLogoRelPath)
    {
        if (!string.IsNullOrWhiteSpace(customLogoRelPath))
        {
            var fullPath = Path.Combine(DataDirectory, customLogoRelPath);
            if (File.Exists(fullPath))
            {
                try { return File.ReadAllBytes(fullPath); } catch { }
            }
        }
        return null;
    }
    static readonly string[] PhotoExtensions = { ".png", ".jpg", ".jpeg" };
    public string SaveEmployeePhoto(string employeeId, string sourceFilePath)
    {
        var ext = Path.GetExtension(sourceFilePath).ToLowerInvariant();
        if (Array.IndexOf(PhotoExtensions, ext) < 0)
            throw new InvalidOperationException("Formato de imagen no permitido (usa PNG o JPG).");
        var safeId = new string((employeeId ?? "").Where(char.IsLetterOrDigit).ToArray());
        if (safeId.Length == 0) safeId = "emp";

        Directory.CreateDirectory(DataDirectory);
        long stamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var targetFileName = $"foto_{safeId}_{stamp}{ext}";
        while (File.Exists(Path.Combine(DataDirectory, targetFileName)))
            targetFileName = $"foto_{safeId}_{++stamp}{ext}";
        File.Copy(sourceFilePath, Path.Combine(DataDirectory, targetFileName), false);
        return targetFileName;
    }
    public byte[]? LoadEmployeePhotoBytes(string? photoRelPath)
    {
        if (string.IsNullOrWhiteSpace(photoRelPath)) return null;
        if (!string.Equals(Path.GetFileName(photoRelPath), photoRelPath, StringComparison.Ordinal)) return null;
        var fullPath = Path.Combine(DataDirectory, photoRelPath);
        if (!File.Exists(fullPath)) return null;
        try { return File.ReadAllBytes(fullPath); } catch { return null; }
    }
    static AppState CreateFreshState()
    {
        var st = new AppState();
        st.Emps.Add(new Employee { Name = "Empleado 1", Salario = st.Cfg.Smmlv });
        return st;
    }
}
