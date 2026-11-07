using System;
using System.Collections.ObjectModel;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using CalculadoraDeSalarios.App.Services;
using CalculadoraDeSalarios.Core;
using CalculadoraDeSalarios.Core.Services;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
namespace CalculadoraDeSalarios.App.ViewModels.Historial;
public partial class HistorialViewModel : ViewModelBase, IRefreshable
{
    readonly MainViewModel _mainVm;
    readonly IDialogService _dialogService;
    public ObservableCollection<HistorialRowViewModel> Rows { get; } = new();
    [ObservableProperty] bool _hasRows;
    [ObservableProperty] bool _isEmpty = true;
    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(DescargarRespaldoCommand))]
    [NotifyCanExecuteChangedFor(nameof(CargarRespaldoCommand))]
    [NotifyCanExecuteChangedFor(nameof(BorrarTodoCommand))]
    bool _isBusy;
    public HistorialViewModel(MainViewModel mainVm, IDialogService dialogService)
    {
        _mainVm = mainVm;
        _dialogService = dialogService;
    }
    bool CanExecuteAction() => !IsBusy;
    public void Refresh()
    {
        Rows.Clear();
        foreach (var entry in _mainVm.History)
            Rows.Add(new HistorialRowViewModel(entry, this));

        HasRows = Rows.Count > 0;
        IsEmpty = !HasRows;
    }
    partial void OnIsBusyChanged(bool value)
    {
        foreach (var row in Rows)
            row.RestoreCommand.NotifyCanExecuteChanged();
    }
    public async Task RestaurarCopiaAsync(HistoryEntry entry)
    {
        if (IsBusy) return;
        IsBusy = true;
        try
        {
            bool ok = await _dialogService.ConfirmAsync(
                "Restaurar copia",
                "¿Restaurar este estado? Se perderán los cambios posteriores.",
                isDanger: true, confirmText: "Restaurar", cancelText: "Cancelar");
            if (!ok) return;
            var nuevo = HistorialCalc.ParsePayload(entry.Payload);
            if (nuevo == null)
            {
                _dialogService.ShowToast("No se pudo restaurar.", ToastKind.Error);
                return;
            }
            _mainVm.Snapshot("Antes de restaurar");
            _mainVm.ReplaceState(nuevo);
            _dialogService.ShowToast("Estado restaurado.", ToastKind.Success);
            Refresh();
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo restaurar: " + ex.Message, ToastKind.Error);
        }
        finally
        {
            IsBusy = false;
        }
    }
    [RelayCommand(CanExecute = nameof(CanExecuteAction))]
    async Task DescargarRespaldoAsync()
    {
        IsBusy = true;
        try
        {
            var path = await _dialogService.ShowSaveFileDialogAsync(
                "Guardar respaldo", $"respaldo_nomina_{DateTime.Today:yyyy-MM-dd}.json", "json", "Respaldo JSON");
            if (string.IsNullOrEmpty(path)) return;
            await File.WriteAllTextAsync(path, _mainVm.ExportStateJson(), new UTF8Encoding(false));
            _dialogService.ShowToast("Respaldo guardado.", ToastKind.Success);
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo guardar el respaldo: " + ex.Message, ToastKind.Error);
        }
        finally
        {
            IsBusy = false;
        }
    }
    [RelayCommand(CanExecute = nameof(CanExecuteAction))]
    async Task CargarRespaldoAsync()
    {
        IsBusy = true;
        try
        {
            var path = await _dialogService.ShowOpenFileDialogAsync(
                "Elegir respaldo JSON", new[] { "json" }, "Respaldo JSON");
            if (string.IsNullOrEmpty(path)) return;
            AppState importado;
            try
            {
                importado = Backup.Load(path);
            }
            catch (Exception ex)
            {
                _dialogService.ShowToast("El archivo no es un respaldo válido: " + ex.Message, ToastKind.Error);
                return;
            }
            _mainVm.Snapshot("Antes de cargar respaldo");
            _mainVm.ReplaceState(importado);
            _dialogService.ShowToast($"Respaldo cargado: {importado.Emps.Count} empleados.", ToastKind.Success);
            Refresh();
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo cargar el respaldo: " + ex.Message, ToastKind.Error);
        }
        finally
        {
            IsBusy = false;
        }
    }
    [RelayCommand(CanExecute = nameof(CanExecuteAction))]
    async Task BorrarTodoAsync()
    {
        IsBusy = true;
        try
        {
            bool ok = await _dialogService.ConfirmAsync(
                "Borrar todo",
                "Se borran empleados, horas y ajustes. ¿Continuar?",
                isDanger: true, confirmText: "Borrar todo", cancelText: "Cancelar");
            if (!ok) return;
            _mainVm.Snapshot("Reinicio");
            _mainVm.ReplaceState(HistorialCalc.EstadoInicial());
            _dialogService.ShowToast("Datos reiniciados. Queda una copia en el historial.", ToastKind.Success);
            Refresh();
        }
        catch (Exception ex)
        {
            _dialogService.ShowToast("No se pudo reiniciar: " + ex.Message, ToastKind.Error);
        }
        finally
        {
            IsBusy = false;
        }
    }
}
