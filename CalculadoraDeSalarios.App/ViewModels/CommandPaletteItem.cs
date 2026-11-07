using System;
namespace CalculadoraDeSalarios.App.ViewModels;
public class CommandPaletteItem
{
    public string Category { get; init; } = "General";
    public string Title { get; init; } = "";
    public string IconKey { get; init; } = "IconInfo";
    public string? Shortcut { get; init; }
    public Action Action { get; init; } = () => { };
}
