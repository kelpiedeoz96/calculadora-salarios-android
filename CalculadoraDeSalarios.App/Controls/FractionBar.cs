using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
namespace CalculadoraDeSalarios.App.Controls;
public class FractionBar : Control
{
    public static readonly StyledProperty<double> FractionProperty =
        AvaloniaProperty.Register<FractionBar, double>(nameof(Fraction));
    static FractionBar()
    {
        AffectsRender<FractionBar>(FractionProperty);
    }
    public FractionBar()
    {
        ActualThemeVariantChanged += (_, _) => InvalidateVisual();
    }
    public double Fraction
    {
        get => GetValue(FractionProperty);
        set => SetValue(FractionProperty, value);
    }
    IBrush Brush(string key, IBrush fallback)
        => this.TryFindResource(key, ActualThemeVariant, out var r) && r is IBrush b ? b : fallback;
    protected override Size MeasureOverride(Size availableSize)
    {
        double w = double.IsInfinity(availableSize.Width) ? 100 : availableSize.Width;
        return new Size(w, 10);
    }
    public override void Render(DrawingContext context)
    {
        base.Render(context);
        double w = Bounds.Width;
        if (w <= 0) return;
        double frac = double.IsNaN(Fraction) ? 0 : Math.Clamp(Fraction, 0.0, 1.0);
        var track = Brush("Surface3Brush", Brushes.Gray);
        var fill = Brush("PrimaryBrush", Brushes.Teal);
        context.DrawRectangle(track, null, new Rect(0, 0, w, 10), 5, 5);
        double barW = frac * w;
        if (barW > 0)
            context.DrawRectangle(fill, null, new Rect(0, 0, barW, 10), 5, 5);
    }
}
