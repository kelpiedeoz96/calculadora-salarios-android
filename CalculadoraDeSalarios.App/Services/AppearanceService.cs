using System.Collections.Generic;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Styling;
namespace CalculadoraDeSalarios.App.Services;
public class AppearanceService : IAppearanceService
{
    readonly List<Window> _trackedWindows = new();

    public string CurrentTheme { get; private set; } = "system";
    public bool ReduceAnimations { get; private set; } = false;
    public void ApplyTheme(string themeMode)
    {
        CurrentTheme = themeMode?.ToLowerInvariant() switch
        {
            "light" => "light",
            "dark" => "dark",
            _ => "system"
        };
        if (Application.Current != null)
        {
            Application.Current.RequestedThemeVariant = CurrentTheme switch
            {
                "light" => ThemeVariant.Light,
                "dark" => ThemeVariant.Dark,
                _ => ThemeVariant.Default
            };
        }
    }
    public void ApplyReduceAnimations(bool reduce)
    {
        ReduceAnimations = reduce;
        foreach (var win in _trackedWindows)
        {
            win.Classes.Set("reduce-motion", reduce);
        }
    }
    public void RegisterWindow(Window window, bool reduceAnimations)
    {
        if (!_trackedWindows.Contains(window))
        {
            _trackedWindows.Add(window);
            window.Closed += (_, _) => _trackedWindows.Remove(window);
        }
        window.Classes.Set("reduce-motion", reduceAnimations);
    }
}
