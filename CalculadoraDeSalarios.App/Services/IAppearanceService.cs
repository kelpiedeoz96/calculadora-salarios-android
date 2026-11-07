using Avalonia.Controls;
namespace CalculadoraDeSalarios.App.Services;
public interface IAppearanceService
{
    string CurrentTheme { get; }
    bool ReduceAnimations { get; }
    void ApplyTheme(string themeMode);
    void ApplyReduceAnimations(bool reduce);
    void RegisterWindow(Window window, bool reduceAnimations);
}
