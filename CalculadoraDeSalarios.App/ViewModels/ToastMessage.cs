using CalculadoraDeSalarios.App.Services;
namespace CalculadoraDeSalarios.App.ViewModels;
public class ToastMessage
{
    public string Message { get; init; } = "";
    public ToastKind Kind { get; init; } = ToastKind.Info;
}
