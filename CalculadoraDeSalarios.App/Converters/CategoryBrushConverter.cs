
using System;
using System.Globalization;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Data.Converters;
using Avalonia.Media;
namespace CalculadoraDeSalarios.App.Converters;
public class CategoryBrushConverter : IValueConverter
{
    public static readonly CategoryBrushConverter Background = new("Bg");
    public static readonly CategoryBrushConverter Foreground = new("Fg");
    readonly string _suffix;
    public CategoryBrushConverter(string suffix)
    {
        _suffix = suffix;
    }
    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value == null) return null;
        string catName = value.ToString() ?? "";
        string resourceKey = $"Cat{catName}{_suffix}Brush";
        if (Application.Current != null
            && Application.Current.TryFindResource(resourceKey, Application.Current.ActualThemeVariant, out var res)
            && res is IBrush brush)
        {
            return brush;
        }
        return null;
    }
    public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        throw new NotSupportedException();
    }
}
