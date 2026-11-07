using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
namespace CalculadoraDeSalarios.App.Controls;
public class MiniBar : Control
{
    public static readonly StyledProperty<double> FractionProperty =
        AvaloniaProperty.Register<MiniBar, double>(nameof(Fraction));
    public static readonly StyledProperty<bool> UseAccentProperty =
        AvaloniaProperty.Register<MiniBar, bool>(nameof(UseAccent));
    static MiniBar()
    {
        AffectsRender<MiniBar>(FractionProperty, UseAccentProperty);
    }
    public MiniBar()
    {
        ActualThemeVariantChanged += (_, _) => InvalidateVisual();
    }
    public double Fraction
    {
        get => GetValue(FractionProperty);
        set => SetValue(FractionProperty, value);
    }
    public bool UseAccent
    {
        get => GetValue(UseAccentProperty);
        set => SetValue(UseAccentProperty, value);
    }
    protected override Size MeasureOverride(Size availableSize)
    {
        double w = double.IsFinite(availableSize.Width) && availableSize.Width > 0 ? availableSize.Width : 40;
        return new Size(w, 4);
    }
    public override void Render(DrawingContext context)
    {
        base.Render(context);
        double w = Bounds.Width;
        if (w <= 0) return;
        double frac = double.IsNaN(Fraction) ? 0 : Math.Clamp(Fraction, 0.0, 1.0);
        if (frac <= 0) return;
        string key = UseAccent ? "AccentBrush" : "PrimaryBrush";
        IBrush fallback = UseAccent ? Brushes.Orange : Brushes.Teal;
        IBrush fill = this.TryFindResource(key, ActualThemeVariant, out var r) && r is IBrush b ? b : fallback;
        context.DrawRectangle(fill, null, new Rect(0, 0, frac * w, 4), 2, 2);
    }
}
