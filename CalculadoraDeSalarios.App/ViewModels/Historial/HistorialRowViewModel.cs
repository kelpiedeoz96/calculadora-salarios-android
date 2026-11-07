using CalculadoraDeSalarios.Core;
using CalculadoraDeSalarios.Core.Services;
using CommunityToolkit.Mvvm.Input;
namespace CalculadoraDeSalarios.App.ViewModels.Historial;
public class HistorialRowViewModel
{
    public string Reason { get; }
    public string When { get; }
    public IAsyncRelayCommand RestoreCommand { get; }
    public HistorialRowViewModel(HistoryEntry entry, HistorialViewModel parent)
    {
        Reason = string.IsNullOrWhiteSpace(entry.Reason) ? "Cambio" : entry.Reason;
        When = HistorialCalc.FormatearFecha(entry.Ts);
        RestoreCommand = new AsyncRelayCommand(
            () => parent.RestaurarCopiaAsync(entry),
            () => !parent.IsBusy);
    }
}
