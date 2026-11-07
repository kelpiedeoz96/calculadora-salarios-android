using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.ComponentModel;

namespace CalculadoraDeSalarios.App.ViewModels.Panel;

public partial class DashboardViewModel : ViewModelBase, IRefreshable
{
    const string DashboardTab = "dashboard";

    readonly MainViewModel _mainVm;

    [ObservableProperty] string _periodLabel = "";
    [ObservableProperty] string _kpiEmpleados = "0";
    [ObservableProperty] string _kpiHorasTotales = "0:00";
    [ObservableProperty] string _kpiRecargos = "$ 0";
    [ObservableProperty] string _kpiHorasExtra = "0:00";
    [ObservableProperty] string _kpiHorasExtraPct = "—";
    [ObservableProperty] bool _hasNoCategorias = true;
    public ObservableCollection<CategoriaBarItemViewModel> Categorias { get; } = new();
    [ObservableProperty] bool _hasNoTopEmpleados = true;
    public ObservableCollection<TopEmpleadoItemViewModel> TopEmpleados { get; } = new();
    [ObservableProperty] bool _hasAlertas;
    public ObservableCollection<string> Alertas { get; } = new();
    [ObservableProperty] string _distOrdinarias = "0:00";
    [ObservableProperty] string _distNocturnas = "0:00";
    [ObservableProperty] string _distDominicales = "0:00";
    [ObservableProperty] string _distExtra = "0:00";
    [ObservableProperty] string _resumenTotalRecargos = "$ 0";
    [ObservableProperty] string _resumenConExtra = "0";
    [ObservableProperty] string _resumenPromedio = "$ 0";
    [ObservableProperty] string _resumenTotalPagar = "$ 0";
    public DashboardViewModel(MainViewModel mainVm)
    {
        _mainVm = mainVm;
        _mainVm.PeriodChanged += OnPeriodOrStateChanged;
    }
    void OnPeriodOrStateChanged()
    {
        if (_mainVm.CurrentTabId == DashboardTab)
            Refresh();
    }
    public void Refresh()
    {
        PeriodLabel = _mainVm.PeriodLabel;
        var (f, t) = _mainVm.GetPeriod();
        var festSet = new HashSet<string>(_mainVm.State.Festivos);
        var cfg = _mainVm.State.Cfg;
        var summaries = _mainVm.State.Emps.Select(e =>
        {
            var analysis = Engine.Analyze(e, cfg, festSet, f, t);
            var payroll = Engine.GetPayroll(e, cfg, analysis.TotalAmount);
            long pay = _mainVm.Ui.Incl ? payroll.Neto : analysis.TotalAmount;
            return new EmpSummary(e, analysis, payroll, pay);
        }).ToList();
        var res = PanelCalc.Calcular(summaries);
        KpiEmpleados = res.EmpleadosCount.ToString();
        KpiHorasTotales = Engine.FmtHM(res.TotalMin);
        KpiRecargos = Fmt.Money(res.TotalAmount);
        KpiHorasExtra = Engine.FmtHM(res.ExtraMin);
        KpiHorasExtraPct = res.PctExtraTexto;
        Categorias.Clear();
        foreach (var c in Engine.Cats)
        {
            int min = res.MinutosPorCategoria[(int)c];
            if (min <= 0) continue;
            Categorias.Add(new CategoriaBarItemViewModel
            {
                Cat = c,
                ShortLabel = Engine.CatShort[c],
                Label = Engine.CatLabel[c].Replace(" (incluida en el salario)", ""),
                HoursFormatted = Engine.FmtHM(min),
                Fraction = (double)min / res.MaxCategoria
            });
        }
        HasNoCategorias = Categorias.Count == 0;
        TopEmpleados.Clear();
        foreach (var s in res.Top5)
            TopEmpleados.Add(new TopEmpleadoItemViewModel(s, OnAbrirEmpleado));
        HasNoTopEmpleados = TopEmpleados.Count == 0;
        Alertas.Clear();
        foreach (var a in res.Alertas.Take(6))
            Alertas.Add("⚠ " + a);
        HasAlertas = Alertas.Count > 0;
        DistOrdinarias = Engine.FmtHM(res.TotalMin - res.ExtraMin);
        DistNocturnas = Engine.FmtHM(res.NightMin);
        DistDominicales = Engine.FmtHM(res.DomMin);
        DistExtra = Engine.FmtHM(res.ExtraMin);
        ResumenTotalRecargos = Fmt.Money(res.TotalAmount);
        ResumenConExtra = res.ConExtra.ToString();
        ResumenPromedio = Fmt.Money(res.Promedio);
        ResumenTotalPagar = Fmt.Money(res.TotalAmount);
    }
    void OnAbrirEmpleado(string empId)
    {
        _mainVm.Ui.Sel = empId;
        _mainVm.SaveState();
        _mainVm.Navigate("liq");
    }
}
