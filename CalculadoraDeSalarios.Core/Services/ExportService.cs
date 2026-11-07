using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
namespace CalculadoraDeSalarios.Core.Services;
public class ExportService : IExportService
{
    public Task ExportExcelAsync(AppState sSnapshot, UiState uiSnapshot, IList<Employee> emps, (DateOnly From, DateOnly To) period, string targetFilePath)
    {
        var stateClone = sSnapshot.Clone();
        var uiClone = uiSnapshot.Clone();
        var targetEmpIds = new HashSet<string>(emps.Select(e => e.Id));
        var empsClone = stateClone.Emps.Where(e => targetEmpIds.Contains(e.Id)).ToList();
        return Task.Run(() =>
        {
            var bytes = Export.Excel(stateClone, uiClone, empsClone, period);
            File.WriteAllBytes(targetFilePath, bytes);
        });
    }
    public Task ExportPdfAsync(AppState sSnapshot, UiState uiSnapshot, IList<Employee> emps, (DateOnly From, DateOnly To) period, string targetFilePath, byte[]? logoBytes)
    {
        var stateClone = sSnapshot.Clone();
        var uiClone = uiSnapshot.Clone();
        var targetEmpIds = new HashSet<string>(emps.Select(e => e.Id));
        var empsClone = stateClone.Emps.Where(e => targetEmpIds.Contains(e.Id)).ToList();
        return Task.Run(() =>
        {
            var bytes = Export.Pdf(stateClone, uiClone, empsClone, period, logoBytes);
            File.WriteAllBytes(targetFilePath, bytes);
        });
    }
}
