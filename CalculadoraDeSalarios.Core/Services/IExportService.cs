using System;
using System.Collections.Generic;
using System.Threading.Tasks;
namespace CalculadoraDeSalarios.Core.Services;
public interface IExportService
{
    Task ExportExcelAsync(AppState sSnapshot, UiState uiSnapshot, IList<Employee> emps, (DateOnly From, DateOnly To) period, string targetFilePath);
    Task ExportPdfAsync(AppState sSnapshot, UiState uiSnapshot, IList<Employee> emps, (DateOnly From, DateOnly To) period, string targetFilePath, byte[]? logoBytes);
}
