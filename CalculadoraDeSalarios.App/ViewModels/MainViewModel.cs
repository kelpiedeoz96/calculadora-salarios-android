using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Avalonia.Media.Imaging;
using Avalonia.Threading;
using CalculadoraDeSalarios.App.Services;
using CalculadoraDeSalarios.App.ViewModels.Liquidacion;
using CalculadoraDeSalarios.App.Views;
using CalculadoraDeSalarios.Core;
using CalculadoraDeSalarios.Core.Services;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
namespace CalculadoraDeSalarios.App.ViewModels;
public partial class MainViewModel : ViewModelBase
{
    readonly IDataStore _dataStore;
    readonly IDialogService _dialogService;
    readonly IExportService _exportService;
    readonly IAppearanceService _appearanceService;
    readonly Dictionary<string, ViewModelBase> _viewModelsCache = new();
    [ObservableProperty]
    AppState _state;
    [ObservableProperty]
    UiState _ui;
    [ObservableProperty]
    List<HistoryEntry> _history;
    [ObservableProperty]
    ViewModelBase? _currentContentViewModel;
    [ObservableProperty]
    string _currentTabId = "dashboard";
    [ObservableProperty]
    string _currentTitle = "Panel";
    [ObservableProperty]
    string _currentSubtitle = "";
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(SidebarWidth))]
    bool _isSidebarCollapsed;
    public double SidebarWidth => IsSidebarCollapsed ? 68.0 : 232.0;
    [ObservableProperty]
    string _periodLabel = "";
    [ObservableProperty]
    Bitmap? _logoBitmap;
    [ObservableProperty]
    string _flyoutMonth = "";
    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsFlyoutCustomRange))]
    int _flyoutModeIndex = 0;
    public bool IsFlyoutCustomRange => FlyoutModeIndex == 2;
    [ObservableProperty]
    string _flyoutFrom = "";
    [ObservableProperty]
    string _flyoutTo = "";
    [ObservableProperty]
    string? _flyoutError;
    public ObservableCollection<NavItemViewModel> PrincipalNavItems { get; } = new();
    public ObservableCollection<NavItemViewModel> SistemaNavItems { get; } = new();
    public ObservableCollection<ToastItemViewModel> ActiveToasts { get; } = new();
    public event Action? RequestClosePeriodFlyout;
    public event Action? PeriodChanged;
    public MainViewModel(
        IDataStore dataStore,
        IDialogService dialogService,
        IExportService exportService,
        AppState preloadedState,
        UiState preloadedUi,
        List<HistoryEntry> preloadedHistory,
        IAppearanceService appearanceService)
    {
        _dataStore = dataStore;
        _dialogService = dialogService;
        _exportService = exportService;
        _appearanceService = appearanceService;
        _state = preloadedState;
        _ui = preloadedUi;
        _history = preloadedHistory;
        if (_dialogService is DialogService ds)
        {
            ds.ToastNotifier = EnqueueToast;
        }
        _viewModelsCache["dashboard"] = new Panel.DashboardViewModel(this);
        _viewModelsCache["liq"] = new LiquidacionViewModel(this, _dialogService, _exportService);
        _viewModelsCache["all"] = new Empleados.EmpleadosViewModel(this, _dialogService, _exportService);
        _viewModelsCache["calendar"] = new Calendario.CalendarioViewModel(this);
        _viewModelsCache["history"] = new Historial.HistorialViewModel(this, _dialogService);
        _viewModelsCache["cfg"] = new Ajustes.AjustesViewModel(this, _dialogService);
        EmpleadoFotos.Configure(_dataStore);
        InitNavItems();
        Normalize();
        CompletarFestivos();
        UpdatePeriodLabel();
        LoadLogo();
        _currentTabId = _ui.Tab;
        _isSidebarCollapsed = _ui.IsSidebarCollapsed;
        SyncFlyoutPeriodFields();
        NavigateInternal(_currentTabId);
    }
    void InitNavItems()
    {
        PrincipalNavItems.Add(new NavItemViewModel { Id = "dashboard", Label = "Panel", Group = "Principal", IconKey = "IconDashboard", ShortcutTip = "Ctrl+1" });
        PrincipalNavItems.Add(new NavItemViewModel { Id = "liq", Label = "Liquidación", Group = "Principal", IconKey = "IconReceipt", ShortcutTip = "Ctrl+2" });
        PrincipalNavItems.Add(new NavItemViewModel { Id = "all", Label = "Empleados", Group = "Principal", IconKey = "IconUsers", ShortcutTip = "Ctrl+3" });
        PrincipalNavItems.Add(new NavItemViewModel { Id = "calendar", Label = "Calendario", Group = "Principal", IconKey = "IconCalendar", ShortcutTip = "Ctrl+4" });
        SistemaNavItems.Add(new NavItemViewModel { Id = "history", Label = "Historial", Group = "Sistema", IconKey = "IconHistory", ShortcutTip = "Ctrl+5" });
        SistemaNavItems.Add(new NavItemViewModel { Id = "cfg", Label = "Ajustes", Group = "Sistema", IconKey = "IconSettings", ShortcutTip = "Ctrl+6" });
    }
    void Normalize()
    {
        if (string.IsNullOrEmpty(Ui.Month) || !Regex.IsMatch(Ui.Month, @"^\d{4}-(0[1-9]|1[0-2])$"))
        {
            var t = DateTime.Today;
            Ui.Month = $"{t.Year}-{t.Month:00}";
            Ui.Mode = t.Day <= 15 ? "q1" : "q2";
        }
        if (Ui.Mode != "q1" && Ui.Mode != "q2" && Ui.Mode != "custom")
        {
            Ui.Mode = "q1";
        }
        if (State.Emps.All(e => e.Id != Ui.Sel))
            Ui.Sel = State.Emps.Count > 0 ? State.Emps[0].Id : "";
    }
    public void NotifyStateChanged()
    {
        OnPropertyChanged(nameof(State));   // refresca bindings como la razón social del sidebar
        PeriodChanged?.Invoke();
    }
    public byte[]? LoadLogoBytes() => _dataStore.LoadLogoBytes(State.Cfg.LogoCustomRelPath);
    public void LoadLogo()
    {
        var bytes = _dataStore.LoadLogoBytes(State.Cfg.LogoCustomRelPath);
        LogoBitmap = null;
        if (bytes != null && bytes.Length > 0)
        {
            try
            {
                using var ms = new MemoryStream(bytes);
                LogoBitmap = new Bitmap(ms);
            }
            catch { }
        }
    }
    public (DateOnly From, DateOnly To) GetPeriod()
    {
        if (Ui.Mode == "custom"
            && DateOnly.TryParseExact(Ui.From, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var f)
            && DateOnly.TryParseExact(Ui.To, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var t))
            return f <= t ? (f, t) : (t, f);

        int y = DateTime.Today.Year;
        int m = DateTime.Today.Month;

        if (!string.IsNullOrEmpty(Ui.Month))
        {
            var parts = Ui.Month.Split('-');
            if (parts.Length == 2 && int.TryParse(parts[0], out var py) && int.TryParse(parts[1], out var pm))
            {
                y = Math.Clamp(py, 1, 9999);
                m = Math.Clamp(pm, 1, 12);
            }
        }
        int last = DateTime.DaysInMonth(y, m);
        return Ui.Mode == "q2"
            ? (new DateOnly(y, m, 16), new DateOnly(y, m, last))
            : (new DateOnly(y, m, 1), new DateOnly(y, m, 15));
    }
    public void ShowSingleDay(DateOnly d)
    {
        string key = Engine.Key(d);
        Ui.Mode = "custom";
        Ui.From = key;
        Ui.To = key;
        UpdatePeriodLabel();
        SaveState();
        _dialogService.ShowToast($"Mostrando solo {Fmt.DmyFull(d)}. Usa el selector de periodo para volver a la quincena.", ToastKind.Success);
        Navigate("liq");
    }
    public void UpdatePeriodLabel()
    {
        var (f, t) = GetPeriod();
        PeriodLabel = Fmt.DmyFull(f) + " – " + Fmt.DmyFull(t);
        UpdateTitles();
        PeriodChanged?.Invoke();
    }
    public void UpdateTitles()
    {
        var curEmp = State.Emps.FirstOrDefault(e => e.Id == Ui.Sel);
        (CurrentTitle, CurrentSubtitle) = CurrentTabId switch
        {
            "dashboard" => ("Panel", "Resumen del periodo · " + PeriodLabel),
            "liq" => ("Liquidación", curEmp != null ? curEmp.Name : "Sin empleado seleccionado"),
            "all" => ("Empleados", "Todos los empleados · " + PeriodLabel),
            "calendar" => ("Calendario", PeriodLabel),
            "history" => ("Historial", "Copias de seguridad y cambios recientes"),
            "cfg" => ("Ajustes", "Preferencias de cálculo y datos"),
            _ => ("Nómina", PeriodLabel)
        };
    }
    [RelayCommand]
    public void PrevPeriod() => ShiftPeriodInternal(-1);

    [RelayCommand]
    public void NextPeriod() => ShiftPeriodInternal(1);
    void ShiftPeriodInternal(int dir)
    {
        if (Ui.Mode == "custom") return;
        int y = DateTime.Today.Year;
        int m = DateTime.Today.Month;
        var parts = Ui.Month.Split('-');
        if (parts.Length == 2 && int.TryParse(parts[0], out var py) && int.TryParse(parts[1], out var pm))
        {
            y = py; m = pm;
        }
        if (dir > 0)
        {
            if (Ui.Mode == "q1") Ui.Mode = "q2";
            else { Ui.Mode = "q1"; m++; if (m > 12) { m = 1; y++; } }
        }
        else
        {
            if (Ui.Mode == "q2") Ui.Mode = "q1";
            else { Ui.Mode = "q2"; m--; if (m < 1) { m = 12; y--; } }
        }
        Ui.Month = $"{y}-{m:00}";
        SyncFlyoutPeriodFields();
        UpdatePeriodLabel();
        SaveState();
    }
    public void SyncFlyoutPeriodFields()
    {
        FlyoutMonth = Ui.Month;
        FlyoutModeIndex = Ui.Mode switch
        {
            "q2" => 1,
            "custom" => 2,
            _ => 0
        };
        FlyoutFrom = Ui.From;
        FlyoutTo = Ui.To;
        FlyoutError = null;
    }
    [RelayCommand]
    public void PrevFlyoutMonth() => ShiftFlyoutMonthInternal(-1);
    [RelayCommand]
    public void NextFlyoutMonth() => ShiftFlyoutMonthInternal(1);
    void ShiftFlyoutMonthInternal(int dir)
    {
        if (string.IsNullOrEmpty(FlyoutMonth)) return;
        var parts = FlyoutMonth.Split('-');
        if (parts.Length == 2 && int.TryParse(parts[0], out var y) && int.TryParse(parts[1], out var m))
        {
            m += dir;
            if (m < 1) { m = 12; y--; }
            if (m > 12) { m = 1; y++; }
            FlyoutMonth = $"{y}-{m:00}";
        }
    }
    [RelayCommand]
    public void ApplyFlyoutPeriod()
    {
        var monthText = (FlyoutMonth ?? "").Trim();
        if (!Regex.IsMatch(monthText, @"^\d{4}-(0[1-9]|1[0-2])$"))
        {
            FlyoutError = "El mes debe ser AAAA-MM, por ejemplo 2026-10.";
            return;
        }
        string newMode = FlyoutModeIndex switch
        {
            1 => "q2",
            2 => "custom",
            _ => "q1"
        };
        var fromText = (FlyoutFrom ?? "").Trim();
        var toText = (FlyoutTo ?? "").Trim();
        if (newMode == "custom")
        {
            bool validFrom = DateOnly.TryParseExact(fromText, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out _);
            bool validTo = DateOnly.TryParseExact(toText, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out _);
            if (!validFrom || !validTo)
            {
                FlyoutError = "Define un rango válido: AAAA-MM-DD en Desde y Hasta.";
                return;
            }
        }
        FlyoutError = null;
        Ui.Month = monthText;
        Ui.Mode = newMode;
        Ui.From = fromText;
        Ui.To = toText;
        UpdatePeriodLabel();
        SaveState();
        _dialogService.ShowToast("Periodo actualizado.", ToastKind.Success);
        RequestClosePeriodFlyout?.Invoke();
    }
    [RelayCommand]
    public void Navigate(string tabId)
    {
        if (CurrentTabId == tabId) return;
        NavigateInternal(tabId);
        SaveState();
    }
    void NavigateInternal(string tabId)
    {
        CurrentTabId = tabId;
        Ui.Tab = tabId;
        if (_viewModelsCache.TryGetValue(tabId, out var vm))
        {
            CurrentContentViewModel = vm;
            if (vm is IRefreshable refreshable)
            {
                refreshable.Refresh();
            }
        }
        foreach (var item in PrincipalNavItems.Concat(SistemaNavItems))
        {
            item.IsActive = (item.Id == tabId);
        }
        UpdateTitles();
    }
    [RelayCommand]
    public void ToggleSidebar()
    {
        IsSidebarCollapsed = !IsSidebarCollapsed;
        Ui.IsSidebarCollapsed = IsSidebarCollapsed;
        SaveState();
    }
    [RelayCommand]
    public void CycleTheme()
    {
        var next = Ui.ThemeMode switch
        {
            "system" => "light",
            "light" => "dark",
            _ => "system"
        };
        Ui.ThemeMode = next;
        _appearanceService.ApplyTheme(next);
        SaveState();
        PeriodChanged?.Invoke();   // las pantallas recalculan colores dependientes del tema
        _dialogService.ShowToast($"Tema establecido: {next}", ToastKind.Info);
    }
    [RelayCommand]
    public void OpenCommandPalette()
    {
        if (_dialogService is DialogService ds && ds.MainWindow != null)
        {
            var items = BuildCommands();
            var paletteWin = new CommandPaletteWindow(items);
            paletteWin.ShowDialog(ds.MainWindow);
        }
    }
    List<CommandPaletteItem> BuildCommands()
    {
        return new List<CommandPaletteItem>
        {
            new() { Category = "Navegación", Title = "Ir a Panel", IconKey = "IconDashboard", Shortcut = "Ctrl+1", Action = () => Navigate("dashboard") },
            new() { Category = "Navegación", Title = "Ir a Liquidación", IconKey = "IconReceipt", Shortcut = "Ctrl+2", Action = () => Navigate("liq") },
            new() { Category = "Navegación", Title = "Ir a Empleados", IconKey = "IconUsers", Shortcut = "Ctrl+3", Action = () => Navigate("all") },
            new() { Category = "Navegación", Title = "Ir a Calendario", IconKey = "IconCalendar", Shortcut = "Ctrl+4", Action = () => Navigate("calendar") },
            new() { Category = "Navegación", Title = "Ir a Historial", IconKey = "IconHistory", Shortcut = "Ctrl+5", Action = () => Navigate("history") },
            new() { Category = "Navegación", Title = "Ir a Ajustes", IconKey = "IconSettings", Shortcut = "Ctrl+6", Action = () => Navigate("cfg") },

            new() { Category = "Periodo", Title = "Quincena anterior", IconKey = "IconChevronL", Action = PrevPeriod },
            new() { Category = "Periodo", Title = "Quincena siguiente", IconKey = "IconChevronR", Action = NextPeriod },

            new() { Category = "Apariencia", Title = "Alternar Tema (Claro / Oscuro / Sistema)", IconKey = "IconSun", Action = CycleTheme },
            new() { Category = "Apariencia", Title = "Contraer / Expandir Barra lateral", IconKey = "IconChevronL", Shortcut = "Ctrl+B", Action = ToggleSidebar },
            new() { Category = "Apariencia", Title = "Alternar Reducción de animaciones", IconKey = "IconSparkles", Action = () =>
            {
                Ui.ReduceAnimations = !Ui.ReduceAnimations;
                _appearanceService.ApplyReduceAnimations(Ui.ReduceAnimations);
                SaveState();
                _dialogService.ShowToast($"Reducción de movimiento: {(Ui.ReduceAnimations ? "Activada" : "Desactivada")}", ToastKind.Info);
            }},
        };
    }
    void EnqueueToast(string message, ToastKind kind)
    {
        Dispatcher.UIThread.Post(() =>
        {
            var item = new ToastItemViewModel { Message = message, Kind = kind };
            ActiveToasts.Add(item);
            while (ActiveToasts.Count > 3)
            {
                ActiveToasts.RemoveAt(0);
            }

            var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
            timer.Tick += (_, _) =>
            {
                timer.Stop();
                ActiveToasts.Remove(item);
            };
            timer.Start();
        });
    }
    public void SaveState()
    {
        try
        {
            _dataStore.Save(State, Ui, History);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("Error al guardar datos: " + ex.Message, ToastKind.Error);
        }
    }
    public string DataDirectory => _dataStore.DataDirectory;
    public string CarpetaRespaldos => RespaldoAutomatico.CarpetaDe(_dataStore.DataDirectory);
    public string BackupExtraDir => Ui.BackupExtraDir ?? "";
    public void RespaldarSiCorresponde()
    {
        try
        {
            if (!RespaldoAutomatico.DebeRespaldar(_dataStore.DataDirectory, DateTime.Now)) return;
            RespaldoAutomatico.Crear(_dataStore.DataDirectory, DateTime.Now, Ui.BackupExtraDir);
        }
        catch { }
    }
    public ResultadoRespaldo RespaldarAhora()
    {
        SaveState();
        return RespaldoAutomatico.Crear(_dataStore.DataDirectory, DateTime.Now, Ui.BackupExtraDir);
    }
    public void SetBackupExtraDir(string? carpeta)
    {
        Ui.BackupExtraDir = carpeta?.Trim() ?? "";
        SaveState();
    }
    public void SetCustomLogo(string sourcePath)
    {
        var rel = _dataStore.CopyLogoToDataDirectory(sourcePath);
        State.Cfg.LogoCustomRelPath = rel;
        LoadLogo();
        Snapshot("Edición de datos");
        NotifyStateChanged();
    }
    public void ClearCustomLogo()
    {
        State.Cfg.LogoCustomRelPath = null;
        LoadLogo();
        Snapshot("Edición de datos");
        NotifyStateChanged();
    }
    public void SetEmployeePhoto(Employee emp, string sourcePath)
    {
        emp.PhotoRelPath = _dataStore.SaveEmployeePhoto(emp.Id, sourcePath);
        Snapshot("Edición de datos");
        NotifyStateChanged();
    }
    public void ClearEmployeePhoto(Employee emp)
    {
        emp.PhotoRelPath = null;
        Snapshot("Edición de datos");
        NotifyStateChanged();
    }
    void CompletarFestivos()
    {
        int y = DateTime.Today.Year;
        FestivosCalc.CompletarAnios(State.Festivos, new[] { y, y + 1 });
    }
    public string ExportStateJson() => _dataStore.ExportJson(State);
    public void ReplaceState(AppState nuevo)
    {
        State = nuevo;
        HistorialCalc.Normalizar(State, Ui, DateOnly.FromDateTime(DateTime.Today));
        CompletarFestivos();
        LoadLogo();
        UpdatePeriodLabel();
        SaveState();
    }
    public void Snapshot(string reason)
    {
        var last = History.FirstOrDefault();
        long now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        if (last != null && last.Reason == reason && now - last.Ts < 120_000)
            History.RemoveAt(0);
        History.Insert(0, new HistoryEntry
        {
            Ts = now,
            Reason = reason,
            Payload = _dataStore.ExportJson(State)
        });
        while (History.Count > 25) History.RemoveAt(History.Count - 1);
        SaveState();
    }
}
