using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Threading.Tasks;
using Avalonia.Media.Imaging;
using CalculadoraDeSalarios.App.Services;
using CalculadoraDeSalarios.Core;
using CalculadoraDeSalarios.Core.Services;
using Avalonia.Controls.ApplicationLifetimes;
using CommunityToolkit.Mvvm.Input;
namespace CalculadoraDeSalarios.App.ViewModels.Ajustes;
public partial class AjustesViewModel : ViewModelBase, IRefreshable
{
    readonly MainViewModel _mainVm;
    readonly IDialogService _dialogService;
    bool _busy;
    public CampoAjusteViewModel TopeSemanal { get; }
    public CampoAjusteViewModel JornadaDiariaRef { get; }
    public CampoAjusteViewModel NocturnaDesde { get; }
    public CampoAjusteViewModel NocturnaHasta { get; }
    public CampoAjusteViewModel MinutosMinimos { get; }
    public CampoAjusteViewModel RecargoNocturno { get; }
    public CampoAjusteViewModel ExtraDiurna { get; }
    public CampoAjusteViewModel ExtraNocturna { get; }
    public CampoAjusteViewModel Dominical { get; }
    public CampoAjusteViewModel Divisor { get; }
    public CampoAjusteViewModel Smmlv { get; }
    public CampoAjusteViewModel Auxilio { get; }
    public CampoAjusteViewModel SaludPct { get; }
    public CampoAjusteViewModel PensionPct { get; }
    public CampoAjusteViewModel TurnosRapidos { get; }
    public CampoAjusteViewModel Festivos { get; }
    public CampoAjusteViewModel RazonSocial { get; }
    public CampoAjusteViewModel Nit { get; }
    public bool DailyCapOn => _mainVm.State.Cfg.DailyCapOn;
    public bool MinuteRuleOn => _mainVm.State.Cfg.MinuteRuleOn;
    public bool RateRound => _mainVm.State.Cfg.RateRound;
    public bool DomOrdFull => _mainVm.State.Cfg.DomOrdFull;
    public bool DomAutoOn => _mainVm.State.Cfg.DomAutoOn;
    public int DeductBaseIndex => _mainVm.State.Cfg.DeductBase == "con_recargos" ? 1 : 0;
    public Bitmap? LogoBitmap => _mainVm.LogoBitmap;
    public bool HasCustomLogo => !string.IsNullOrEmpty(_mainVm.State.Cfg.LogoCustomRelPath);
    public string LogoEtiqueta => HasCustomLogo ? "Logo personalizado" : "Sin logo";
    public AjustesViewModel(MainViewModel mainVm, IDialogService dialogService)
    {
        _mainVm = mainVm;
        _dialogService = dialogService;
        _mainVm.PropertyChanged += OnMainViewModelPropertyChanged;
        TopeSemanal = Numero("Tope semanal (horas)", () => _mainVm.State.Cfg.WeeklyCap, v => _mainVm.State.Cfg.WeeklyCap = v);
        JornadaDiariaRef = Numero("Jornada diaria de referencia (horas)", () => _mainVm.State.Cfg.DailyRef, v => _mainVm.State.Cfg.DailyRef = v);
        NocturnaDesde = Hora("Nocturna desde", () => _mainVm.State.Cfg.NightStart, s => _mainVm.State.Cfg.NightStart = s);
        NocturnaHasta = Hora("Nocturna hasta", () => _mainVm.State.Cfg.NightEnd, s => _mainVm.State.Cfg.NightEnd = s);
        MinutosMinimos = Numero("Minutos mínimos para que cuenten", () => _mainVm.State.Cfg.MinValid, v => _mainVm.State.Cfg.MinValid = v);
        RecargoNocturno = Numero("Nocturno", () => _mainVm.State.Cfg.Pct.Noc, v => _mainVm.State.Cfg.Pct.Noc = v);
        ExtraDiurna = Numero("Extra diurna", () => _mainVm.State.Cfg.Pct.Hed, v => _mainVm.State.Cfg.Pct.Hed = v);
        ExtraNocturna = Numero("Extra nocturna", () => _mainVm.State.Cfg.Pct.Hen, v => _mainVm.State.Cfg.Pct.Hen = v);
        Dominical = Numero("Dominical/festivo/descanso", () => _mainVm.State.Cfg.Pct.Dom, v => _mainVm.State.Cfg.Pct.Dom = v);
        Divisor = Numero("Divisor del valor hora", () => _mainVm.State.Cfg.Divisor, v => _mainVm.State.Cfg.Divisor = v,
            validadorExtra: v => v > 0, errorValidadorExtra: "El divisor debe ser mayor que 0.");
        Smmlv = Numero("Salario mínimo mensual", () => _mainVm.State.Cfg.Smmlv, v => _mainVm.State.Cfg.Smmlv = v);
        Auxilio = Numero("Auxilio de transporte", () => _mainVm.State.Cfg.Auxilio, v => _mainVm.State.Cfg.Auxilio = v);
        SaludPct = Numero("Salud (%)", () => _mainVm.State.Cfg.SaludPct, v => _mainVm.State.Cfg.SaludPct = v);
        PensionPct = Numero("Pensión (%)", () => _mainVm.State.Cfg.PensionPct, v => _mainVm.State.Cfg.PensionPct = v);
        TurnosRapidos = new CampoAjusteViewModel(
            "Uno por línea (HH:MM-HH:MM)",
            leer: () => AjustesCalc.UnirLineas(_mainVm.State.Cfg.Turnos),
            aplicar: texto =>
            {
                var nuevos = AjustesCalc.ParseTurnos(texto);
                if (nuevos.SequenceEqual(_mainVm.State.Cfg.Turnos)) return ResultadoCampo.SinCambio;
                _mainVm.State.Cfg.Turnos = nuevos;
                return ResultadoCampo.Cambiado;
            },
            alCambiar: OnCambioAplicado,
            multilinea: true, alto: 150);

        Festivos = new CampoAjusteViewModel(
            "Uno por línea (AAAA-MM-DD)",
            leer: () => AjustesCalc.UnirLineas(_mainVm.State.Festivos),
            aplicar: texto =>
            {
                var nuevos = AjustesCalc.ParseFestivos(texto);
                if (nuevos.SequenceEqual(_mainVm.State.Festivos)) return ResultadoCampo.SinCambio;
                _mainVm.State.Festivos = nuevos;
                return ResultadoCampo.Cambiado;
            },
            alCambiar: OnCambioAplicado,
            multilinea: true, alto: 190);
        RazonSocial = Texto("Razón social", () => _mainVm.State.Cfg.RazonSocial, s => _mainVm.State.Cfg.RazonSocial = s);
        Nit = Texto("NIT", () => _mainVm.State.Cfg.Nit, s => _mainVm.State.Cfg.Nit = s);
    }

    void OnMainViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(MainViewModel.LogoBitmap))
            ActualizarPropiedadesLogo();
    }
    void OnCambioAplicado()
    {
        _mainVm.Snapshot("Edición de datos");
        _mainVm.NotifyStateChanged();
    }
    CampoAjusteViewModel Numero(string etiqueta, Func<double> getter, Action<double> setter,
        Func<double, bool>? validadorExtra = null, string? errorValidadorExtra = null)
    {
        return new CampoAjusteViewModel(
            etiqueta,
            leer: () => AjustesCalc.FormatearNumero(getter()),
            aplicar: texto =>
            {
                if (!AjustesCalc.TryParseNumero(texto, out double val))
                {
                    _dialogService.ShowToast("Valor no válido: se mantuvo el anterior.", ToastKind.Warning);
                    return ResultadoCampo.Invalido;
                }
                if (validadorExtra != null && !validadorExtra(val))
                {
                    _dialogService.ShowToast(errorValidadorExtra ?? "Valor no válido.", ToastKind.Warning);
                    return ResultadoCampo.Invalido;
                }
                if (getter() == val) return ResultadoCampo.SinCambio;
                setter(val);
                return ResultadoCampo.Cambiado;
            },
            alCambiar: OnCambioAplicado);
    }
    CampoAjusteViewModel Hora(string etiqueta, Func<string> getter, Action<string> setter)
    {
        return new CampoAjusteViewModel(
            etiqueta,
            leer: () => getter() ?? string.Empty,
            aplicar: texto =>
            {
                if (!AjustesCalc.TryParseHora(texto, out string normalizada))
                {
                    _dialogService.ShowToast("Hora no válida: usa HH:MM.", ToastKind.Warning);
                    return ResultadoCampo.Invalido;
                }
                if ((getter() ?? string.Empty) == normalizada) return ResultadoCampo.SinCambio;
                setter(normalizada);
                return ResultadoCampo.Cambiado;
            },
            alCambiar: OnCambioAplicado);
    }
    CampoAjusteViewModel Texto(string etiqueta, Func<string> getter, Action<string> setter)
    {
        return new CampoAjusteViewModel(
            etiqueta,
            leer: () => (getter() ?? string.Empty).Trim(),
            aplicar: texto =>
            {
                string nuevo = (texto ?? string.Empty).Trim();
                if (nuevo == (getter() ?? string.Empty).Trim()) return ResultadoCampo.SinCambio;

                setter(nuevo);
                return ResultadoCampo.Cambiado;
            },
            alCambiar: OnCambioAplicado);
    }

    public void Refresh()
    {
        _busy = true;
        try
        {
            foreach (var c in new[]
            {
                TopeSemanal, JornadaDiariaRef, NocturnaDesde, NocturnaHasta, MinutosMinimos,
                RecargoNocturno, ExtraDiurna, ExtraNocturna, Dominical, Divisor,
                Smmlv, Auxilio, SaludPct, PensionPct, TurnosRapidos, Festivos, RazonSocial, Nit,
            })
                c.Refrescar();
            OnPropertyChanged(nameof(DailyCapOn));
            OnPropertyChanged(nameof(MinuteRuleOn));
            OnPropertyChanged(nameof(RateRound));
            OnPropertyChanged(nameof(DomOrdFull));
            OnPropertyChanged(nameof(DomAutoOn));
            OnPropertyChanged(nameof(DeductBaseIndex));
            ActualizarPropiedadesLogo();
            ActualizarInfoRespaldos();
        }
        finally
        {
            _busy = false;
        }
    }
    public void SetDailyCapOn(bool value)
    {
        if (_busy || _mainVm.State.Cfg.DailyCapOn == value) return;
        _mainVm.State.Cfg.DailyCapOn = value;
        OnCambioAplicado();
        OnPropertyChanged(nameof(DailyCapOn));
    }

    public void SetMinuteRuleOn(bool value)
    {
        if (_busy || _mainVm.State.Cfg.MinuteRuleOn == value) return;
        _mainVm.State.Cfg.MinuteRuleOn = value;
        OnCambioAplicado();
        OnPropertyChanged(nameof(MinuteRuleOn));
    }

    public void SetRateRound(bool value)
    {
        if (_busy || _mainVm.State.Cfg.RateRound == value) return;
        _mainVm.State.Cfg.RateRound = value;
        OnCambioAplicado();
        OnPropertyChanged(nameof(RateRound));
    }
    public void SetDomOrdFull(bool value)
    {
        if (_busy || _mainVm.State.Cfg.DomOrdFull == value) return;
        _mainVm.State.Cfg.DomOrdFull = value;
        OnCambioAplicado();
        OnPropertyChanged(nameof(DomOrdFull));
    }
    public void SetDomAutoOn(bool value)
    {
        if (_busy || _mainVm.State.Cfg.DomAutoOn == value) return;
        _mainVm.State.Cfg.DomAutoOn = value;
        OnCambioAplicado();
        OnPropertyChanged(nameof(DomAutoOn));
    }
    string _anioFestivos = (DateTime.Today.Year + 1).ToString();
    public string AnioFestivos
    {
        get => _anioFestivos;
        set => SetProperty(ref _anioFestivos, value);
    }

    [RelayCommand]
    void AgregarFestivosAnio()
    {
        if (!int.TryParse((AnioFestivos ?? "").Trim(), out int anio) || anio < 2000 || anio > 2100)
        {
            _dialogService.ShowToast("Escribe un año entre 2000 y 2100.", ToastKind.Warning);
            return;
        }
        int agregados = FestivosCalc.AgregarAnio(_mainVm.State.Festivos, anio);
        if (agregados == 0)
        {
            _dialogService.ShowToast($"Los festivos de {anio} ya estaban en la lista.", ToastKind.Info);
            return;
        }
        OnCambioAplicado();
        Festivos.Refrescar();
        _dialogService.ShowToast($"Se agregaron {agregados} festivos de {anio}.", ToastKind.Success);
    }
    public string CarpetaRespaldosTexto => _mainVm.CarpetaRespaldos;
    public bool HayCarpetaExtra => !string.IsNullOrWhiteSpace(_mainVm.BackupExtraDir);
    public string CarpetaExtraTexto => HayCarpetaExtra ? _mainVm.BackupExtraDir : "Ninguna (solo se guarda en este equipo)";
    public string UltimoRespaldoTexto
    {
        get
        {
            var lista = RespaldoAutomatico.Listar(_mainVm.CarpetaRespaldos);
            if (lista.Count == 0) return "Todavía no hay respaldos.";
            return $"Último respaldo: {lista[0].LastWriteTime:dd/MM/yyyy HH:mm}  ·  {lista.Count} guardados (se conservan los últimos {RespaldoAutomatico.MaxRespaldos}).";
        }
    }
    void ActualizarInfoRespaldos()
    {
        OnPropertyChanged(nameof(CarpetaRespaldosTexto));
        OnPropertyChanged(nameof(HayCarpetaExtra));
        OnPropertyChanged(nameof(CarpetaExtraTexto));
        OnPropertyChanged(nameof(UltimoRespaldoTexto));
    }

    [RelayCommand]
    void RespaldarAhora()
    {
        try
        {
            var r = _mainVm.RespaldarAhora();
            if (r.ErrorExtra != null)
                _dialogService.ShowToast("Respaldo guardado, pero no se pudo copiar a la carpeta extra: " + r.ErrorExtra, ToastKind.Warning);
            else
                _dialogService.ShowToast(r.RutaExtra != null ? "Respaldo guardado (también en la carpeta extra)." : "Respaldo guardado.", ToastKind.Success);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo crear el respaldo: " + ex.Message, ToastKind.Error);
        }
        ActualizarInfoRespaldos();
    }
    [RelayCommand]
    void AbrirCarpetaRespaldos()
    {
        try { System.IO.Directory.CreateDirectory(_mainVm.CarpetaRespaldos); } catch { }
        if (!FileOpener.Open(_mainVm.CarpetaRespaldos))
            _dialogService.ShowToast("No se pudo abrir la carpeta. Está en: " + _mainVm.CarpetaRespaldos, ToastKind.Warning);
    }
    [RelayCommand]
    async Task ElegirCarpetaExtraAsync()
    {
        var carpeta = await _dialogService.ShowFolderDialogAsync("Elegir carpeta extra para los respaldos");
        if (string.IsNullOrWhiteSpace(carpeta)) return;
        _mainVm.SetBackupExtraDir(carpeta);
        _dialogService.ShowToast("Carpeta extra guardada. Cada respaldo nuevo también se copiará allí.", ToastKind.Success);
        ActualizarInfoRespaldos();
    }
    [RelayCommand]
    void QuitarCarpetaExtra()
    {
        _mainVm.SetBackupExtraDir("");
        _dialogService.ShowToast("Carpeta extra quitada.", ToastKind.Info);
        ActualizarInfoRespaldos();
    }
    [RelayCommand]
    async Task RestaurarRespaldoAsync()
    {
        var zip = await _dialogService.ShowOpenFileDialogAsync("Elegir respaldo para restaurar", new[] { "*.zip" }, "Respaldo (.zip)");
        if (string.IsNullOrWhiteSpace(zip)) return;

        bool ok = await _dialogService.ConfirmAsync(
            "Restaurar respaldo",
            "Se van a reemplazar los datos actuales (empleados, ajustes, historial y fotos) por los del respaldo.\n\n" +
            "Antes se guarda un respaldo de lo que tienes ahora, y la app se cierra para cargar los datos restaurados.",
            isDanger: true, confirmText: "Restaurar y cerrar", cancelText: "Cancelar");
        if (!ok) return;
        try
        {
            _mainVm.RespaldarAhora();   // red de seguridad con el estado actual
            RespaldoAutomatico.Restaurar(zip, _mainVm.DataDirectory);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo restaurar: " + ex.Message, ToastKind.Error);
            return;
        }

        _dialogService.ShowToast("Respaldo restaurado. Cerrando para cargar los datos…", ToastKind.Success);
        Avalonia.Threading.DispatcherTimer.RunOnce(() =>
        {
            if (Avalonia.Application.Current?.ApplicationLifetime is IClassicDesktopStyleApplicationLifetime d)
                d.Shutdown();
        }, TimeSpan.FromMilliseconds(1200));
    }

    public void SetDeductBase(int index)
    {
        if (_busy) return;
        string nuevo = index == 1 ? "con_recargos" : "basico";
        if (_mainVm.State.Cfg.DeductBase == nuevo) return;
        _mainVm.State.Cfg.DeductBase = nuevo;
        OnCambioAplicado();
        OnPropertyChanged(nameof(DeductBaseIndex));
    }

    [RelayCommand]
    async Task ElegirLogoAsync()
    {
        var path = await _dialogService.ShowOpenFileDialogAsync("Elegir logo", new[] { "png", "jpg", "jpeg" }, "Imágenes");
        if (string.IsNullOrWhiteSpace(path)) return;

        try
        {
            using var prueba = new Bitmap(path);
        }
        catch
        {
            _dialogService.ShowToast("El archivo no es una imagen válida.", ToastKind.Error);
            return;
        }

        try
        {
            _mainVm.SetCustomLogo(path);
            _dialogService.ShowToast("Logo actualizado.", ToastKind.Success);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo guardar el logo: " + ex.Message, ToastKind.Error);
        }
        ActualizarPropiedadesLogo();
    }

    [RelayCommand]
    async Task QuitarLogoAsync()
    {
        bool ok = await _dialogService.ConfirmAsync(
            "Quitar logo", "Se quitará el logo.",
            isDanger: false, confirmText: "Quitar", cancelText: "Cancelar");
        if (!ok) return;
        _mainVm.ClearCustomLogo();
        _dialogService.ShowToast("Logo restablecido.", ToastKind.Success);
        ActualizarPropiedadesLogo();
    }
    void ActualizarPropiedadesLogo()
    {
        OnPropertyChanged(nameof(LogoBitmap));
        OnPropertyChanged(nameof(HasCustomLogo));
        OnPropertyChanged(nameof(LogoEtiqueta));
    }
}
