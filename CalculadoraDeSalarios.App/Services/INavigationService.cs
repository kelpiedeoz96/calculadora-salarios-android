using System;
namespace CalculadoraDeSalarios.App.Services;
public interface INavigationService
{
    string CurrentTab { get; }
    event Action<string>? Navigated;
    void NavigateTo(string tabId, object? parameter = null);
}
