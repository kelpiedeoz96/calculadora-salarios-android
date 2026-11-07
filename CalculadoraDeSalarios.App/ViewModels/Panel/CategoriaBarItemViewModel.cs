using CalculadoraDeSalarios.Core;

namespace CalculadoraDeSalarios.App.ViewModels.Panel;

public class CategoriaBarItemViewModel
{
    public Cat Cat { get; init; }
    public string ShortLabel { get; init; } = "";
    public string Label { get; init; } = "";
    public string HoursFormatted { get; init; } = "";
    public double Fraction { get; init; }
}
