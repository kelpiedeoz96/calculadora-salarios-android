#pragma warning disable MVVMTK0034
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using CalculadoraDeSalarios.App.Services;
using CalculadoraDeSalarios.Core;
using CalculadoraDeSalarios.Core.Services;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
namespace CalculadoraDeSalarios.App.ViewModels.Liquidacion;
public partial class LiquidacionViewModel : ViewModelBase, IRefreshable
{
    readonly MainViewModel _mainVm;
    readonly IDialogService _dialogService;
    readonly IExportService _exportService;
    [ObservableProperty]
    bool _hasEmployees;
    [ObservableProperty]
    bool _hasNoEmployees;
    [ObservableProperty]
    bool _hasAlerts;
    [ObservableProperty]
    string _weekCapText = "";
    [ObservableProperty]
    string _saludLabel = "Salud";
    [ObservableProperty]
    string _pensionLabel = "Pensión";
    bool _busy;
    [ObservableProperty]
    string? _empPhotoRelPath;
    [ObservableProperty]
    string _empInitials = "";
    [ObservableProperty]
    IBrush? _empAvatarBrush;
    public bool HasEmpPhoto => !string.IsNullOrEmpty(EmpPhotoRelPath);
    partial void OnEmpPhotoRelPathChanged(string? value) => OnPropertyChanged(nameof(HasEmpPhoto));
    [ObservableProperty]
    string _empName = "";
    [ObservableProperty]
    string _empCedula = "";
    [ObservableProperty]
    string _empCargo = "";
    [ObservableProperty]
    string _empSalarioText = "";
    [ObservableProperty]
    bool _isSalarioBelowMin;
    [ObservableProperty]
    string _salarioBelowMinText = "";
    [ObservableProperty]
    int _empRestDayIndex;
    [ObservableProperty]
    bool _empAux;
    [ObservableProperty]
    string _smmlvPlaceholder = "";
    [ObservableProperty]
    string _periodLabel = "";
    [ObservableProperty]
    string _baseHourLabel = "";
    [ObservableProperty]
    string _totalHoursFormatted = "";
    [ObservableProperty]
    string _totalRecargosFormatted = "";
    [ObservableProperty]
    bool _isInclSueldo;
    [ObservableProperty]
    string _basicoQuincenal = "$ 0";
    [ObservableProperty]
    string _auxilioTransporte = "$ 0";
    [ObservableProperty]
    string _recargosQuincenal = "$ 0";
    [ObservableProperty]
    string _totalDevengado = "$ 0";
    [ObservableProperty]
    string _saludDescuento = "- $ 0";
    [ObservableProperty]
    string _pensionDescuento = "- $ 0";
    [ObservableProperty]
    string _netoPagar = "$ 0";
    [ObservableProperty]
    string _deduccionesNota = "";
    public ObservableCollection<EmployeeChipViewModel> EmployeeChips { get; } = new();
    public ObservableCollection<DayRowViewModel> DayRows { get; } = new();
    public ObservableCollection<WeekRowViewModel> Weeks { get; } = new();
    public ObservableCollection<string> Alerts { get; } = new();
    public ObservableCollection<AuditRowViewModel> AuditRows { get; } = new();
    public List<string> QuickShifts { get; } = new();
    public List<LegendItem> Legend { get; } = new();
    public LiquidacionViewModel(MainViewModel mainVm, IDialogService dialogService, IExportService exportService)
    {
        _mainVm = mainVm;
        _dialogService = dialogService;
        _exportService = exportService;
        _mainVm.PeriodChanged += OnPeriodOrStateChanged;
        InitQuickShifts();
        foreach (var c in Engine.Cats)
        {
            Legend.Add(new LegendItem
            {
                Cat = c,
                ShortLabel = Engine.CatShort[c],
                Label = Engine.CatLabel[c].Replace(" (incluida en el salario)", "")
            });
        }
        RefreshAll();
    }
    void InitQuickShifts()
    {
        QuickShifts.Clear();
        QuickShifts.Add("Turno…");
        foreach (var t in _mainVm.State.Cfg.Turnos)
        {
            QuickShifts.Add(t.Replace("-", " a "));
        }
    }
    void OnPeriodOrStateChanged()
    {
        RefreshAll();
    }
    public void Refresh() => RefreshAll();
    public void RefreshAll()
    {
        var prev = _busy;
        _busy = true;
        try { RefreshAllCore(); }
        finally { _busy = prev; }
        _mainVm.UpdateTitles();
    }
    void RefreshAllCore()
    {
        PeriodLabel = _mainVm.PeriodLabel;
        IsInclSueldo = _mainVm.Ui.Incl;
        var emps = _mainVm.State.Emps;
        HasEmployees = emps.Count > 0;
        HasNoEmployees = !HasEmployees;
        UpdateEmployeeChips();
        var emp = emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel) ?? emps.FirstOrDefault();
        if (emp == null)
        {
            DayRows.Clear();
            Weeks.Clear();
            Alerts.Clear();
            AuditRows.Clear();
            return;
        }
        if (_mainVm.Ui.Sel != emp.Id)
        {
            _mainVm.Ui.Sel = emp.Id;
            _mainVm.SaveState();
        }
        LoadEmployeeForm(emp);
        RecalculateAndBindAnalysis(emp);
    }
    void UpdateEmployeeChips()
    {
        var emps = _mainVm.State.Emps;
        var (f, t) = _mainVm.GetPeriod();
        var festSet = new HashSet<string>(_mainVm.State.Festivos);
        EmployeeChips.Clear();
        foreach (var e in emps)
        {
            var analysis = Engine.Analyze(e, _mainVm.State.Cfg, festSet, f, t);
            var payroll = Engine.GetPayroll(e, _mainVm.State.Cfg, analysis.TotalAmount);
            long pay = _mainVm.Ui.Incl ? payroll.Neto : analysis.TotalAmount;
            EmployeeChips.Add(new EmployeeChipViewModel(e, pay, e.Id == _mainVm.Ui.Sel));
        }
    }
    void LoadEmployeeForm(Employee emp)
    {
        _empName = emp.Name;
        _empCedula = emp.Cedula;
        _empCargo = emp.Cargo;
        _empSalarioText = emp.Salario > 0 ? ((long)emp.Salario).ToString(CultureInfo.InvariantCulture) : "";
        _empRestDayIndex = emp.RestDay.HasValue ? emp.RestDay.Value + 1 : 0;
        _empAux = emp.Aux;
        _smmlvPlaceholder = $"{_mainVm.State.Cfg.Smmlv:N0} (salario mínimo)";
        EmpPhotoRelPath = emp.PhotoRelPath;
        EmpInitials = Fmt.Initials(emp.Name);
        int hue = Fmt.HueOf(emp.Name);
        EmpAvatarBrush = new SolidColorBrush(Color.FromRgb(
            (byte)(80 + (hue % 100)),
            (byte)(100 + ((hue * 3) % 80)),
            (byte)(120 + ((hue * 7) % 80))));
        ValidateSalarioMinimo();
        OnPropertyChanged(nameof(EmpName));
        OnPropertyChanged(nameof(EmpCedula));
        OnPropertyChanged(nameof(EmpCargo));
        OnPropertyChanged(nameof(EmpSalarioText));
        OnPropertyChanged(nameof(EmpRestDayIndex));
        OnPropertyChanged(nameof(EmpAux));
        OnPropertyChanged(nameof(SmmlvPlaceholder));
    }
    void ValidateSalarioMinimo()
    {
        double sal = ParseSalario(_empSalarioText);
        if (sal > 0 && sal < _mainVm.State.Cfg.Smmlv)
        {
            IsSalarioBelowMin = true;
            SalarioBelowMinText = $"El salario es inferior al mínimo vigente ({Fmt.Money((long)_mainVm.State.Cfg.Smmlv)}).";
        }
        else
        {
            IsSalarioBelowMin = false;
            SalarioBelowMinText = "";
        }
    }
    static double ParseSalario(string text)
    {
        var digits = Regex.Replace(text ?? "", @"[^\d]", "");
        return double.TryParse(digits, NumberStyles.Integer, CultureInfo.InvariantCulture, out var v) ? v : 0;
    }
    void RecalculateAndBindAnalysis(Employee emp)
    {
        var prev = _busy;
        _busy = true;
        try { RecalculateCore(emp); }
        finally { _busy = prev; }
    }
    void RecalculateCore(Employee emp)
    {
        var (f, t) = _mainVm.GetPeriod();
        var festSet = new HashSet<string>(_mainVm.State.Festivos);
        var cfg = _mainVm.State.Cfg;

        var A = Engine.Analyze(emp, cfg, festSet, f, t);
        var P = Engine.GetPayroll(emp, cfg, A.TotalAmount);
        if (DayRows.Count != A.Rows.Count || (DayRows.Count > 0 && DayRows[0].Date != A.Rows[0].Date))
        {
            DayRows.Clear();
            foreach (var r in A.Rows)
            {
                DayRows.Add(new DayRowViewModel(r, OnCellCommitted, OnRestToggled, OnPresetApplied));
            }
        }
        else
        {
            for (int i = 0; i < A.Rows.Count; i++)
            {
                DayRows[i].UpdateFrom(A.Rows[i]);
            }
        }
        int capMin = (int)Engine.JsRound(cfg.WeeklyCap * 60);
        Weeks.Clear();
        foreach (var w in A.Weeks)
        {
            Weeks.Add(new WeekRowViewModel(w, capMin));
        }

        Alerts.Clear();
        foreach (var alt in A.Alerts)
        {
            Alerts.Add(alt);
        }
        HasAlerts = Alerts.Count > 0;
        WeekCapText = $"La marca vertical es el tope de {cfg.WeeklyCap:0.##} h. Lo que lo supera se paga como hora extra.";
        SaludLabel = $"Salud ({cfg.SaludPct:0.##}%)";
        PensionLabel = $"Pensión ({cfg.PensionPct:0.##}%)";
        BaseHourLabel = $"Valor hora base: {Fmt.Money((long)Engine.JsRound(A.Rates.Hv))} · salario ÷ {cfg.Divisor:0.##}";
        AuditRows.Clear();
        foreach (var c in Engine.Cats)
        {
            int min = A.TotMin[(int)c];
            long amt = A.TotAmount[(int)c];
            if (min == 0 && c != Cat.ODiu && c != Cat.ONoc) continue;

            string tipo = c == Cat.ODiu ? "(incluida en el salario)"
                : (c == Cat.ONoc || (!cfg.DomOrdFull && (c == Cat.ODom || c == Cat.ONocDom))) ? "(solo recargo)"
                : "(valor completo)";

            string vh = c == Cat.ODiu ? "—" : $"{Fmt.Money((long)Engine.JsRound(A.Rates[c]))} {tipo}";
            string pct = A.TotalAmount != 0 && amt != 0
                ? (amt * 100.0 / A.TotalAmount).ToString("0.0", CultureInfo.InvariantCulture) + "%"
                : "";
            AuditRows.Add(new AuditRowViewModel
            {
                Concepto = Engine.CatLabel[c].Replace(" (incluida en el salario)", ""),
                HorasFormatted = Engine.FmtHM(min),
                RateFormatted = vh,
                TotalFormatted = Fmt.Money(amt),
                PercentageFormatted = pct,
                IsBold = false
            });
        }
        TotalHoursFormatted = Engine.FmtHM(A.TotalMin);
        TotalRecargosFormatted = Fmt.Money(A.TotalAmount);
        BasicoQuincenal = Fmt.Money(P.BasicoQ);
        AuxilioTransporte = Fmt.Money(P.AuxQ);
        RecargosQuincenal = Fmt.Money(P.Recargos);
        TotalDevengado = Fmt.Money(P.Devengado);
        SaludDescuento = "- " + Fmt.Money(P.Salud);
        PensionDescuento = "- " + Fmt.Money(P.Pension);
        NetoPagar = Fmt.Money(P.Neto);
        string baseMode = cfg.DeductBase == "con_recargos" ? "básico + recargos" : "mitad del salario";
        DeduccionesNota = $"Descuentos sobre {Fmt.Money((long)Engine.JsRound(P.Base))} ({baseMode}).";
    }
    [RelayCommand]
    public void SelectEmployee(string empId)
    {
        if (_mainVm.Ui.Sel == empId) return;
        _mainVm.Ui.Sel = empId;
        _mainVm.SaveState();
        RefreshAll();
    }
    [RelayCommand]
    async System.Threading.Tasks.Task ElegirFotoAsync()
    {
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;

        var path = await _dialogService.ShowOpenFileDialogAsync("Elegir foto del empleado", new[] { "png", "jpg", "jpeg" }, "Imágenes");
        if (string.IsNullOrWhiteSpace(path)) return;
        try
        {
            if (new FileInfo(path).Length > 25L * 1024 * 1024)
            {
                _dialogService.ShowToast("La imagen es muy pesada (máximo 25 MB).", ToastKind.Warning);
                return;
            }
            using var fs = File.OpenRead(path);
            using var prueba = Bitmap.DecodeToWidth(fs, 64);
        }
        catch
        {
            _dialogService.ShowToast("El archivo no es una imagen válida.", ToastKind.Error);
            return;
        }
        try
        {
            _mainVm.SetEmployeePhoto(emp, path);
            _dialogService.ShowToast("Foto actualizada.", ToastKind.Success);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo guardar la foto: " + ex.Message, ToastKind.Error);
        }
    }
    [RelayCommand]
    void QuitarFoto()
    {
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null || string.IsNullOrEmpty(emp.PhotoRelPath)) return;
        _mainVm.ClearEmployeePhoto(emp);
        _dialogService.ShowToast("Foto quitada.", ToastKind.Info);
    }
    [RelayCommand]
    public void AddEmployee()
    {
        var n = new Employee { Name = "Nuevo empleado", RestDay = null, Aux = true };
        _mainVm.State.Emps.Add(n);
        _mainVm.Ui.Sel = n.Id;
        _mainVm.Snapshot("Empleado añadido");
        RefreshAll();
        _dialogService.ShowToast("Empleado añadido.", ToastKind.Success);
    }
    [RelayCommand]
    public async System.Threading.Tasks.Task DeleteEmployee()
    {
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;
        bool confirm = await _dialogService.ConfirmAsync(
            "Eliminar empleado",
            $"¿Eliminar a {emp.Name} y todas sus horas?",
            isDanger: true);
        if (!confirm) return;
        _mainVm.State.Emps.Remove(emp);
        _mainVm.Ui.Sel = _mainVm.State.Emps.Count > 0 ? _mainVm.State.Emps[0].Id : "";
        _mainVm.Snapshot("Empleado eliminado");
        RefreshAll();
        _dialogService.ShowToast("Empleado eliminado.", ToastKind.Warning);
    }
    public void CommitRestDay(int index)
    {
        if (_busy) return;
        EmpRestDayIndex = index;
        CommitEmpField("restDay");
    }
    public void CommitAux(bool value)
    {
        if (_busy) return;
        EmpAux = value;
        CommitEmpField("aux");
    }
    public void CommitEmpField(string fieldName)
    {
        if (_busy) return;
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;
        bool changed = false;
        switch (fieldName)
        {
            case "name":
                var n = (EmpName ?? "").Trim();
                if (n != emp.Name) { emp.Name = n; changed = true; }
                break;
            case "cedula":
                var c = (EmpCedula ?? "").Trim();
                if (c != emp.Cedula) { emp.Cedula = c; changed = true; }
                break;
            case "cargo":
                var cg = (EmpCargo ?? "").Trim();
                if (cg != emp.Cargo) { emp.Cargo = cg; changed = true; }
                break;
            case "salario":
                double s = ParseSalario(EmpSalarioText);
                if (s != emp.Salario) { emp.Salario = s; changed = true; }
                ValidateSalarioMinimo();
                break;
            case "restDay":
                int? r = EmpRestDayIndex <= 0 ? null : EmpRestDayIndex - 1;
                if (r != emp.RestDay) { emp.RestDay = r; changed = true; }
                break;
            case "aux":
                if (EmpAux != emp.Aux) { emp.Aux = EmpAux; changed = true; }
                break;
        }
        if (changed)
        {
            _mainVm.Snapshot("Edición de datos");
            RecalculateAndBindAnalysis(emp);
            UpdateEmployeeChips();
            _mainVm.UpdateTitles();
        }
    }
    void OnCellCommitted(DayRowViewModel rowVm, string field, string value)
    {
        if (_busy) return;
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;
        bool changed = false;
        JornadaHelper.EditDay(emp, rowVm.Date, rec =>
        {
            switch (field)
            {
                case "in1": if (rec.In1 != value) { rec.In1 = value; changed = true; } break;
                case "out1": if (rec.Out1 != value) { rec.Out1 = value; changed = true; } break;
                case "in2": if (rec.In2 != value) { rec.In2 = value; changed = true; } break;
                case "out2": if (rec.Out2 != value) { rec.Out2 = value; changed = true; } break;
                case "brk":
                    var digits = Regex.Replace(value ?? "", @"[^\d]", "");
                    double b = digits == "" ? 0 : double.Parse(digits, CultureInfo.InvariantCulture);
                    if (rec.Brk != b) { rec.Brk = b; changed = true; }
                    break;
            }
        });
        if (changed)
        {
            _mainVm.Snapshot("Edición de datos");
            RecalculateAndBindAnalysis(emp);
            UpdateEmployeeChips();
        }
    }
    void OnRestToggled(DayRowViewModel rowVm, bool value)
    {
        if (_busy) return;
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;
        JornadaHelper.EditDay(emp, rowVm.Date, rec => rec.Desc = value);
        _mainVm.Snapshot("Edición de datos");
        RecalculateAndBindAnalysis(emp);
        UpdateEmployeeChips();
    }
    void OnPresetApplied(DayRowViewModel rowVm, string preset)
    {
        if (_busy) return;
        if (string.IsNullOrEmpty(preset) || preset == "Turno…") return;
        var parts = preset.Split(new[] { " a ", "-" }, StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length != 2) return;

        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;
        JornadaHelper.EditDay(emp, rowVm.Date, rec =>
        {
            rec.In1 = parts[0].Trim();
            rec.Out1 = parts[1].Trim();
            rec.In2 = "";
            rec.Out2 = "";
        });
        _mainVm.Snapshot("Edición de datos");
        RecalculateAndBindAnalysis(emp);
        UpdateEmployeeChips();
    }
    [RelayCommand]
    public async System.Threading.Tasks.Task ClearPeriodHours()
    {
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;
        var (f, t) = _mainVm.GetPeriod();
        bool confirm = await _dialogService.ConfirmAsync(
            "Limpiar horas",
            $"¿Borrar horas de {emp.Name} entre {Fmt.DmyFull(f)} y {Fmt.DmyFull(t)}?",
            isDanger: true);
        if (!confirm) return;
        string kf = Engine.Key(f), kt = Engine.Key(t);
        foreach (var k in emp.Days.Keys.ToList())
        {
            if (string.CompareOrdinal(k, kf) >= 0 && string.CompareOrdinal(k, kt) <= 0)
                emp.Days.Remove(k);
        }
        _mainVm.Snapshot("Horas borradas");
        RecalculateAndBindAnalysis(emp);
        UpdateEmployeeChips();
        _dialogService.ShowToast("Horas del periodo borradas.", ToastKind.Info);
    }
    [RelayCommand]
    public async System.Threading.Tasks.Task ExportPdf()
    {
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;
        var (f, t) = _mainVm.GetPeriod();
        string cleanName = Regex.Replace(emp.Name.Trim(), @"\s+", "_");
        string defaultFileName = $"comprobante_{cleanName}_{Engine.Key(f)}_{Engine.Key(t)}.pdf";
        var path = await _dialogService.ShowSaveFileDialogAsync("Guardar PDF", defaultFileName, "pdf", "Documento PDF");
        if (string.IsNullOrEmpty(path)) return;
        try
        {
            var logoBytes = _mainVm.LoadLogoBytes();
            await _exportService.ExportPdfAsync(_mainVm.State, _mainVm.Ui, new[] { emp }, (f, t), path, logoBytes);
            _dialogService.ShowToast("PDF guardado: " + path, ToastKind.Success);
            await _dialogService.OfferOpenFileAsync(path);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo crear el PDF: " + ex.Message, ToastKind.Error);
        }
    }
    [RelayCommand]
    public async System.Threading.Tasks.Task ExportExcel()
    {
        var emp = _mainVm.State.Emps.FirstOrDefault(e => e.Id == _mainVm.Ui.Sel);
        if (emp == null) return;
        var (f, t) = _mainVm.GetPeriod();
        string defaultFileName = $"nomina_recargos_{Engine.Key(f)}_{Engine.Key(t)}.xlsx";
        var path = await _dialogService.ShowSaveFileDialogAsync("Guardar Excel", defaultFileName, "xlsx", "Libro de Excel");
        if (string.IsNullOrEmpty(path)) return;
        try
        {
            await _exportService.ExportExcelAsync(_mainVm.State, _mainVm.Ui, new[] { emp }, (f, t), path);
            _dialogService.ShowToast("Excel guardado: " + path, ToastKind.Success);
            await _dialogService.OfferOpenFileAsync(path);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo crear el Excel: " + ex.Message, ToastKind.Error);
        }
    }
}
