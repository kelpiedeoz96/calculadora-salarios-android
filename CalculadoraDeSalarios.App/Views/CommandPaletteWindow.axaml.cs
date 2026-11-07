using System;
using System.Collections.Generic;
using System.Linq;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using CalculadoraDeSalarios.App.ViewModels;
namespace CalculadoraDeSalarios.App.Views;
public partial class CommandPaletteWindow : Window
{
    readonly List<CommandPaletteItem> _allItems;
    public CommandPaletteWindow() : this(new List<CommandPaletteItem>())
    {
    }
    public CommandPaletteWindow(List<CommandPaletteItem> items)
    {
        InitializeComponent();
        _allItems = items;

        UpdateFilter("");
        SearchBox.TextChanged += (_, _) => UpdateFilter(SearchBox.Text ?? "");
        CommandsList.DoubleTapped += (_, _) => ExecuteSelected();
        AddHandler(KeyDownEvent, OnPreviewKeyDown, RoutingStrategies.Tunnel);
        Opened += (_, _) => SearchBox.Focus();
    }
    void OnPreviewKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            Close();
            e.Handled = true;
        }
        else if (e.Key == Key.Enter)
        {
            ExecuteSelected();
            e.Handled = true;
        }
        else if (e.Key == Key.Down)
        {
            if (CommandsList.SelectedIndex < CommandsList.ItemCount - 1)
                CommandsList.SelectedIndex++;
            e.Handled = true;
        }
        else if (e.Key == Key.Up)
        {
            if (CommandsList.SelectedIndex > 0)
                CommandsList.SelectedIndex--;
            e.Handled = true;
        }
    }
    void UpdateFilter(string query)
    {
        var q = query.Trim().ToLowerInvariant();
        var filtered = _allItems.Where(i =>
            string.IsNullOrEmpty(q) ||
            i.Title.ToLowerInvariant().Contains(q) ||
            i.Category.ToLowerInvariant().Contains(q)
        ).ToList();

        CommandsList.ItemsSource = filtered;
        if (filtered.Count > 0) CommandsList.SelectedIndex = 0;
    }
    void ExecuteSelected()
    {
        if (CommandsList.SelectedItem is CommandPaletteItem item)
        {
            Close();
            item.Action?.Invoke();
        }
    }
}
