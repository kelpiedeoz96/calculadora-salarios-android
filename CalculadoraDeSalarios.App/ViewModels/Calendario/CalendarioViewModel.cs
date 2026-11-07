using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.ComponentModel;
namespace CalculadoraDeSalarios.App.ViewModels.Calendario;
public partial class CalendarioViewModel : ViewModelBase, IRefreshable
{
    const string CalendarTab = "calendar";
    readonly MainViewModel _mainVm;
    [ObservableProperty] string _titulo = "";
    [ObservableProperty] string _periodLabel = "";
    [ObservableProperty] bool _hayEmpleado;
    [ObservableProperty] bool _sinEmpleado;
    public ObservableCollection<DiaCeldaViewModel> Celdas { get; } = new();
    public CalendarioViewModel(MainViewModel mainVm)
    {
        _mainVm = mainVm;
        _mainVm.PeriodChanged += OnPeriodChanged;
    }
    void OnPeriodChanged()
    {
        if (_mainVm.CurrentTabId == CalendarTab)
            Refresh();
    }
    public void Refresh()
    {
        PeriodLabel = _mainVm.PeriodLabel;
        var emps = _mainVm.State.Emps;
        var emp = emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel) ?? emps.FirstOrDefault();
        Celdas.Clear();
        if (emp == null)
        {
            HayEmpleado = false;
            SinEmpleado = true;
            Titulo = "Sin empleados";
            return;
        }
        HayEmpleado = true;
        SinEmpleado = false;
        Titulo = string.IsNullOrWhiteSpace(emp.Name) ? "Sin nombre" : emp.Name;
        var (pFrom, pTo) = _mainVm.GetPeriod();
        var festSet = new HashSet<string>(_mainVm.State.Festivos);
        var analysis = Engine.Analyze(emp, _mainVm.State.Cfg, festSet, pFrom, pTo);
        var today = DateOnly.FromDateTime(DateTime.Today);
        var mes = CalendarioCalc.Construir(pFrom.Year, pFrom.Month, analysis.Rows, pFrom, pTo, today);
        for (int i = 0; i < mes.Pad0; i++)
            Celdas.Add(new DiaCeldaViewModel());
        foreach (var dia in mes.Dias)
            Celdas.Add(new DiaCeldaViewModel(dia, OnDiaSeleccionado));
    }
    void OnDiaSeleccionado(DateOnly fecha) => _mainVm.ShowSingleDay(fecha);
}
