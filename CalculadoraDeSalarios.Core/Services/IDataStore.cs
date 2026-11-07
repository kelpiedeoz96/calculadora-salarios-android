using System.Collections.Generic;
namespace CalculadoraDeSalarios.Core.Services;
public interface IDataStore
{
    string DataPath { get; }
    string DataDirectory { get; }
    (AppState State, UiState Ui, List<HistoryEntry> History) Load();
    void Save(AppState state, UiState ui, List<HistoryEntry> history);
    string ExportJson(AppState state);
    AppState ImportBackup(string filePath);
    string CopyLogoToDataDirectory(string sourceFilePath);
    byte[]? LoadLogoBytes(string? customLogoRelPath);
    string SaveEmployeePhoto(string employeeId, string sourceFilePath);
    byte[]? LoadEmployeePhotoBytes(string? photoRelPath);
}
