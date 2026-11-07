using Avalonia.Controls.Primitives;
using Avalonia.Controls;
using Avalonia.Input;
using CalculadoraDeSalarios.App.ViewModels;
namespace CalculadoraDeSalarios.App.Views;
public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();

        KeyDown += OnWindowKeyDown;
        DataContextChanged += OnDataContextChanged;
    }
    void OnDataContextChanged(object? sender, System.EventArgs e)
    {
        if (DataContext is MainViewModel vm)
        {
            PeriodButton.Click += (_, _) => vm.SyncFlyoutPeriodFields();
            vm.RequestClosePeriodFlyout += () =>
            {
                PeriodButton.Flyout?.Hide();
            };
        }
    }
    void OnWindowKeyDown(object? sender, KeyEventArgs e)
    {
        if (DataContext is not MainViewModel vm) return;

        if (e.KeyModifiers.HasFlag(KeyModifiers.Control))
        {
            switch (e.Key)
            {
                case Key.K:
                    vm.OpenCommandPalette();
                    e.Handled = true;
                    break;
                case Key.B:
                    vm.ToggleSidebar();
                    e.Handled = true;
                    break;
                case Key.D1:
                    vm.Navigate("dashboard");
                    e.Handled = true;
                    break;
                case Key.D2:
                    vm.Navigate("liq");
                    e.Handled = true;
                    break;
                case Key.D3:
                    vm.Navigate("all");
                    e.Handled = true;
                    break;
                case Key.D4:
                    vm.Navigate("calendar");
                    e.Handled = true;
                    break;
                case Key.D5:
                    vm.Navigate("history");
                    e.Handled = true;
                    break;
                case Key.D6:
                    vm.Navigate("cfg");
                    e.Handled = true;
                    break;
            }
        }
    }
}
