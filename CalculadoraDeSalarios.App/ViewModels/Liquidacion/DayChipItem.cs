using CalculadoraDeSalarios.Core;
namespace CalculadoraDeSalarios.App.ViewModels.Liquidacion;
public class DayChipItem
{
    public Cat Cat { get; init; }
    public string ShortLabel { get; init; } = "";
    public string TimeFormatted { get; init; } = "";
    public string Text => $"{ShortLabel} {TimeFormatted}";
}
public class LegendItem
{
    public Cat Cat { get; init; }
    public string ShortLabel { get; init; } = "";
    public string Label { get; init; } = "";
}
