using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.ComponentModel;
namespace CalculadoraDeSalarios.App.ViewModels.Liquidacion;
public partial class AuditRowViewModel : ObservableObject
{
    public string Concepto { get; init; } = "";
    public string HorasFormatted { get; init; } = "";
    public string RateFormatted { get; init; } = "";
    public string TotalFormatted { get; init; } = "";
    public string PercentageFormatted { get; init; } = "";
    public bool IsBold { get; init; }
}
