using System;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using CalculadoraDeSalarios.App.ViewModels.Liquidacion;
namespace CalculadoraDeSalarios.App.Views;
public partial class LiquidacionView : UserControl
{
    public LiquidacionView()
    {
        InitializeComponent();
        AddHandler(GotFocusEvent, OnAnyGotFocus);
    }
    LiquidacionViewModel? Vm => DataContext as LiquidacionViewModel;
    void OnAnyGotFocus(object? sender, RoutedEventArgs e)
    {
        if (e.Source is TextBox tb) tb.SelectAll();
    }
    void OnFormLostFocus(object? sender, RoutedEventArgs e)
    {
        if (sender is TextBox tb && tb.Tag is string tag) Vm?.CommitEmpField(tag);
    }
    void OnFormKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter && sender is TextBox tb && tb.Tag is string tag) Vm?.CommitEmpField(tag);
    }
    void OnRestDayChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (sender is ComboBox cb) Vm?.CommitRestDay(cb.SelectedIndex);
    }
    void OnAuxClick(object? sender, RoutedEventArgs e)
    {
        if (sender is CheckBox cb) Vm?.CommitAux(cb.IsChecked == true);
    }
    void OnDayRestClick(object? sender, RoutedEventArgs e)
    {
        if (sender is CheckBox cb && cb.DataContext is DayRowViewModel row)
            row.ToggleRest(cb.IsChecked == true);
    }
    void OnPresetSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (sender is ComboBox combo
            && combo.DataContext is DayRowViewModel row
            && combo.SelectedIndex > 0
            && combo.SelectedItem is string preset)
        {
            row.ApplyPreset(preset);
            combo.SelectedIndex = 0;
        }
    }
    void OnTimeBoxLostFocus(object? sender, RoutedEventArgs e)
    {
        if (sender is TextBox tb && tb.DataContext is DayRowViewModel row && tb.Tag is string field)
            row.CommitTime(field, tb.Text);
    }
    void OnBrkBoxLostFocus(object? sender, RoutedEventArgs e)
    {
        if (sender is TextBox tb && tb.DataContext is DayRowViewModel row)
            row.CommitBreak(tb.Text);
    }
    void OnTimeBoxKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key != Key.Enter || sender is not TextBox tb || tb.DataContext is not DayRowViewModel row) return;
        if (tb.Tag is string field && field != "brk") row.CommitTime(field, tb.Text);
        else row.CommitBreak(tb.Text);
        e.Handled = true;
    }
}
