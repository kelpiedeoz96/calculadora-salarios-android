using System;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Media;
using CalculadoraDeSalarios.App.ViewModels.Ajustes;
namespace CalculadoraDeSalarios.App.Controls;
public partial class CampoAjusteControl : UserControl
{
    public CampoAjusteControl()
    {
        InitializeComponent();
        DataContextChanged += OnDataContextChanged;
    }
    void OnDataContextChanged(object? sender, EventArgs e)
    {
        if (DataContext is CampoAjusteViewModel vm && vm.EsMultilinea && vm.Alto > 0)
        {
            InputBox.Height = vm.Alto;
            InputBox.TextWrapping = TextWrapping.NoWrap;
        }
    }
    void OnInputLostFocus(object? sender, RoutedEventArgs e) => Commit();
    void OnInputKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter && DataContext is CampoAjusteViewModel vm && !vm.EsMultilinea)
        {
            Commit();
            e.Handled = true;
        }
    }
    void OnInputGotFocus(object? sender, RoutedEventArgs e)
    {
        if (DataContext is CampoAjusteViewModel vm && !vm.EsMultilinea)
            InputBox.SelectAll();
    }
    void Commit()
    {
        if (DataContext is CampoAjusteViewModel vm)
        {
            vm.Confirmar(InputBox.Text);
            InputBox.Text = vm.Texto;
        }
    }
}
