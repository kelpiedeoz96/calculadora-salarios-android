using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;
using CalculadoraDeSalarios.App.Services;
namespace CalculadoraDeSalarios.App.Controls;
public class EmpleadoAvatar : Border
{
    public static readonly StyledProperty<string?> InitialsProperty =
        AvaloniaProperty.Register<EmpleadoAvatar, string?>(nameof(Initials));
    public static readonly StyledProperty<IBrush?> FillProperty =
        AvaloniaProperty.Register<EmpleadoAvatar, IBrush?>(nameof(Fill));
    public static readonly StyledProperty<string?> PhotoRelPathProperty =
        AvaloniaProperty.Register<EmpleadoAvatar, string?>(nameof(PhotoRelPath));
    public static readonly StyledProperty<double> InitialsFontSizeProperty =
        AvaloniaProperty.Register<EmpleadoAvatar, double>(nameof(InitialsFontSize), 12);
    public string? Initials { get => GetValue(InitialsProperty); set => SetValue(InitialsProperty, value); }
    public IBrush? Fill { get => GetValue(FillProperty); set => SetValue(FillProperty, value); }
    public string? PhotoRelPath { get => GetValue(PhotoRelPathProperty); set => SetValue(PhotoRelPathProperty, value); }
    public double InitialsFontSize { get => GetValue(InitialsFontSizeProperty); set => SetValue(InitialsFontSizeProperty, value); }
    protected override Type StyleKeyOverride => typeof(Border);
    readonly TextBlock _texto;
    readonly Image _imagen;
    public EmpleadoAvatar()
    {
        ClipToBounds = true;
        _texto = new TextBlock
        {
            Foreground = Brushes.White,
            FontWeight = FontWeight.SemiBold,
            FontSize = 12,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        };
        _imagen = new Image
        {
            Stretch = Stretch.UniformToFill,
            IsVisible = false,
        };
        var contenido = new Grid();
        contenido.Children.Add(_texto);
        contenido.Children.Add(_imagen);
        Child = contenido;
    }
    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == InitialsProperty)
            _texto.Text = change.GetNewValue<string?>();
        else if (change.Property == InitialsFontSizeProperty)
            _texto.FontSize = change.GetNewValue<double>();
        else if (change.Property == FillProperty)
            Background = change.GetNewValue<IBrush?>();
        else if (change.Property == PhotoRelPathProperty)
            ActualizarFoto(change.GetNewValue<string?>());
    }
    void ActualizarFoto(string? rel)
    {
        var bmp = EmpleadoFotos.Get(rel);
        _imagen.Source = bmp;
        _imagen.IsVisible = bmp != null;
        _texto.IsVisible = bmp == null;
    }
}
