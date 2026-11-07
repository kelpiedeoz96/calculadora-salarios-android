using Avalonia.Controls;
using Avalonia.Interactivity;
using CalculadoraDeSalarios.App.ViewModels.Empleados;
namespace CalculadoraDeSalarios.App.Views;
public partial class EmpleadosView : UserControl
{
    public EmpleadosView()
    {
        InitializeComponent();
    }
    void OnInclCheckBoxClick(object? sender, RoutedEventArgs e)
    {
        if (DataContext is EmpleadosViewModel vm && sender is CheckBox cb)
        {
            vm.OnInclSueldoUserClick(cb.IsChecked == true);
        }
    }
}
