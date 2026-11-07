using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CalculadoraDeSalarios.App.Services;
using CalculadoraDeSalarios.Core;
using CalculadoraDeSalarios.Core.Services;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
namespace CalculadoraDeSalarios.App.ViewModels.Empleados;
public partial class EmpleadosViewModel : ViewModelBase, IRefreshable
{
    readonly MainViewModel _mainVm;
    readonly IDialogService _dialogService;
    readonly IExportService _exportService;
    List<EmpSummary> _allSummaries = new();
    bool _isBusy;
    [ObservableProperty]
    string _periodLabel = "";
    [ObservableProperty]
    string _searchText = "";
    [ObservableProperty]
    bool _isInclSueldo;
    [ObservableProperty]
    bool _hasNoMatches;
    [ObservableProperty]
    string _totHoras = "0:00";
    [ObservableProperty]
    string _totNocturnas = "0:00";
    [ObservableProperty]
    string _totDomFest = "0:00";
    [ObservableProperty]
    string _totExtra = "0:00";
    [ObservableProperty]
    string _totRecargos = "$ 0";
    [ObservableProperty]
    string _totBasico = "$ 0";
    [ObservableProperty]
    string _totAuxilio = "$ 0";
    [ObservableProperty]
    string _totDescuentos = "- $ 0";
    [ObservableProperty]
    string _totNeto = "$ 0";
    public ObservableCollection<EmpleadoRowViewModel> FilteredRows { get; } = new();
    public EmpleadosViewModel(MainViewModel mainVm, IDialogService dialogService, IExportService exportService)
    {
        _mainVm = mainVm;
        _dialogService = dialogService;
        _exportService = exportService;
        _mainVm.PeriodChanged += OnPeriodOrStateChanged;
        _searchText = _mainVm.Ui.Search ?? "";
        _isInclSueldo = _mainVm.Ui.Incl;
        Refresh();
    }
    void OnPeriodOrStateChanged()
    {
        Refresh();
    }
    public void Refresh()
    {
        _isBusy = true;
        PeriodLabel = _mainVm.PeriodLabel;
        IsInclSueldo = _mainVm.Ui.Incl;
        _isBusy = false;

        RecalculateAllSummaries();
        ApplyFilter();
    }
    void RecalculateAllSummaries()
    {
        var (f, t) = _mainVm.GetPeriod();
        var festSet = new HashSet<string>(_mainVm.State.Festivos);
        var cfg = _mainVm.State.Cfg;
        _allSummaries = _mainVm.State.Emps.Select(e =>
        {
            var analysis = Engine.Analyze(e, cfg, festSet, f, t);
            var payroll = Engine.GetPayroll(e, cfg, analysis.TotalAmount);
            long pay = _mainVm.Ui.Incl ? payroll.Neto : analysis.TotalAmount;
            return new EmpSummary(e, analysis, payroll, pay);
        }).ToList();
    }
    partial void OnSearchTextChanged(string value)
    {
        if (_isBusy) return;
        _mainVm.Ui.Search = value ?? "";
        _mainVm.SaveState();
        ApplyFilter();
    }
    public void OnInclSueldoUserClick(bool newValue)
    {
        if (_isBusy) return;

        _mainVm.Ui.Incl = newValue;
        _mainVm.SaveState();
        _mainVm.NotifyStateChanged();
    }
    void ApplyFilter()
    {
        var matched = _allSummaries
            .Where(s => EmpleadosCalc.Matches(s.Emp, SearchText))
            .ToList();
        FilteredRows.Clear();
        foreach (var s in matched)
        {
            FilteredRows.Add(new EmpleadoRowViewModel(s));
        }
        HasNoMatches = FilteredRows.Count == 0;
        var totales = EmpleadosCalc.CalcularTotales(matched, IsInclSueldo);
        TotHoras = Engine.FmtHM(totales.TotalMin);
        TotNocturnas = Engine.FmtHM(totales.NocMin);
        TotDomFest = Engine.FmtHM(totales.DomMin);
        TotExtra = Engine.FmtHM(totales.ExtraMin);
        TotRecargos = Fmt.Money(totales.RecargosAmount);
        TotBasico = Fmt.Money(totales.BasicoQ);
        TotAuxilio = Fmt.Money(totales.AuxQ);
        TotDescuentos = "- " + Fmt.Money(totales.Descuentos);
        TotNeto = Fmt.Money(totales.Neto);
    }
    [RelayCommand]
    public async Task ExportExcelAll()
    {
        var emps = _mainVm.State.Emps;
        if (emps.Count == 0)
        {
            _dialogService.ShowToast("No hay empleados para exportar.", ToastKind.Info);
            return;
        }
        var (f, t) = _mainVm.GetPeriod();
        string defaultFileName = $"nomina_recargos_{Engine.Key(f)}_{Engine.Key(t)}.xlsx";
        var path = await _dialogService.ShowSaveFileDialogAsync("Guardar Excel de todos", defaultFileName, "xlsx", "Libro de Excel");
        if (string.IsNullOrEmpty(path)) return;
        try
        {
            await _exportService.ExportExcelAsync(_mainVm.State, _mainVm.Ui, emps, (f, t), path);
            _dialogService.ShowToast("Excel guardado: " + path, ToastKind.Success);
            await _dialogService.OfferOpenFileAsync(path);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo crear el Excel: " + ex.Message, ToastKind.Error);
        }
    }
    [RelayCommand]
    public async Task ExportPdfAll()
    {
        var emps = _mainVm.State.Emps;
        if (emps.Count == 0)
        {
            _dialogService.ShowToast("No hay empleados para exportar.", ToastKind.Info);
            return;
        }
        var (f, t) = _mainVm.GetPeriod();
        string defaultFileName = $"comprobantes_todos_{Engine.Key(f)}_{Engine.Key(t)}.pdf";
        var path = await _dialogService.ShowSaveFileDialogAsync("Guardar PDF de todos", defaultFileName, "pdf", "Documento PDF");
        if (string.IsNullOrEmpty(path)) return;
        try
        {
            var logoBytes = _mainVm.LoadLogoBytes();
            await _exportService.ExportPdfAsync(_mainVm.State, _mainVm.Ui, emps, (f, t), path, logoBytes);
            _dialogService.ShowToast("PDF guardado: " + path, ToastKind.Success);
            await _dialogService.OfferOpenFileAsync(path);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo crear el PDF: " + ex.Message, ToastKind.Error);
        }
    }
}
