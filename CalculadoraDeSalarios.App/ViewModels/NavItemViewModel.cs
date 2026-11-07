using CommunityToolkit.Mvvm.ComponentModel;
namespace CalculadoraDeSalarios.App.ViewModels;
public partial class NavItemViewModel : ObservableObject
{
    public string Id { get; init; } = "";
    public string Label { get; init; } = "";
    public string Group { get; init; } = "";
    public string IconKey { get; init; } = "";
    public string ShortcutTip { get; init; } = "";
    [ObservableProperty]
    bool _isActive;
}
