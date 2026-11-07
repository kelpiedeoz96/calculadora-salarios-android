using Avalonia.Controls;
using Avalonia.Interactivity;
using CalculadoraDeSalarios.App.ViewModels.Ajustes;
namespace CalculadoraDeSalarios.App.Views;
public partial class AjustesView : UserControl
{
    public AjustesView()
    {
        InitializeComponent();
    }
    AjustesViewModel? Vm => DataContext as AjustesViewModel;
    void OnCheckBoxClick(object? sender, RoutedEventArgs e)
    {
        if (sender is not CheckBox cb || Vm is not { } vm) return;
        bool value = cb.IsChecked == true;
        switch (cb.Tag as string)
        {
            case "DailyCapOn": vm.SetDailyCapOn(value); break;
            case "MinuteRuleOn": vm.SetMinuteRuleOn(value); break;
            case "RateRound": vm.SetRateRound(value); break;
            case "DomOrdFull": vm.SetDomOrdFull(value); break;
            case "DomAutoOn": vm.SetDomAutoOn(value); break;
        }
    }
    void OnDeductBaseSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (sender is ComboBox cb) Vm?.SetDeductBase(cb.SelectedIndex);
    }
}
