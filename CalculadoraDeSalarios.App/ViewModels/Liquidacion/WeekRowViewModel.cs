using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.ComponentModel;
namespace CalculadoraDeSalarios.App.ViewModels.Liquidacion;
public partial class WeekRowViewModel : ObservableObject
{
    [ObservableProperty]
    string _dateRangeLabel = "";
    [ObservableProperty]
    string _hoursLabel = "";
    [ObservableProperty]
    int _workedMin;
    [ObservableProperty]
    int _extraMin;
    [ObservableProperty]
    int _capMin;
    public WeekRowViewModel(Week w, int capMin)
    {
        _capMin = capMin;
        _workedMin = w.WorkedMin;
        _extraMin = w.ExtraMin;
        _dateRangeLabel = $"{Fmt.Dmy(w.Start)} – {Fmt.Dmy(w.End)}";

        string extraStr = w.ExtraMin > 0 ? $" · extra {Engine.FmtHM(w.ExtraMin)}" : "";
        _hoursLabel = $"{Engine.FmtHM(w.WorkedMin)} / {Engine.FmtHM(capMin)}{extraStr}";
    }
}
