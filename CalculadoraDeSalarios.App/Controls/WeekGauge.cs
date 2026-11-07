using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
namespace CalculadoraDeSalarios.App.Controls;
public class WeekGauge : Control
{
    public static readonly StyledProperty<int> WorkedMinProperty =
        AvaloniaProperty.Register<WeekGauge, int>(nameof(WorkedMin));
    public static readonly StyledProperty<int> ExtraMinProperty =
        AvaloniaProperty.Register<WeekGauge, int>(nameof(ExtraMin));
    public static readonly StyledProperty<int> CapMinProperty =
        AvaloniaProperty.Register<WeekGauge, int>(nameof(CapMin), 2520);
    static WeekGauge()
    {
        AffectsRender<WeekGauge>(WorkedMinProperty, ExtraMinProperty, CapMinProperty);
    }
    public WeekGauge()
    {
        ActualThemeVariantChanged += (_, _) => InvalidateVisual();
    }
    IBrush Brush(string key, IBrush fallback)
        => this.TryFindResource(key, ActualThemeVariant, out var r) && r is IBrush b ? b : fallback;
    public int WorkedMin
    {
        get => GetValue(WorkedMinProperty);
        set => SetValue(WorkedMinProperty, value);
    }
    public int ExtraMin
    {
        get => GetValue(ExtraMinProperty);
        set => SetValue(ExtraMinProperty, value);
    }
    public int CapMin
    {
        get => GetValue(CapMinProperty);
        set => SetValue(CapMinProperty, value);
    }
    protected override Size MeasureOverride(Size availableSize)
    {
        return new Size(double.IsNaN(Width) ? 100 : Width, 16);
    }
    public override void Render(DrawingContext context)
    {
        base.Render(context);
        double w = Bounds.Width;
        if (w <= 0) return;
        int ord = Math.Max(0, WorkedMin - ExtraMin);
        double scale = Math.Max(Math.Max(CapMin, WorkedMin), 1);
        double fo = ord / scale;
        double fe = ExtraMin / scale;
        double fc = Math.Min(1.0, (double)CapMin / scale);
        IBrush bgBrush = Brushes.Gray;
        IBrush primaryBrush = Brushes.Teal;
        IBrush accentBrush = Brushes.Orange;
        IBrush textBrush = Brushes.White;
        bgBrush = Brush("Surface3Brush", bgBrush);
        primaryBrush = Brush("PrimaryBrush", primaryBrush);
        accentBrush = Brush("AccentBrush", accentBrush);
        textBrush = Brush("TextBrush", textBrush);
        var trackRect = new Rect(0, 3, w, 10);
        context.DrawRectangle(bgBrush, null, trackRect, 5, 5);
        double ordW = Math.Clamp(fo * w, 0, w);
        if (ordW > 0)
        {
            var ordRect = new Rect(0, 3, ordW, 10);
            context.DrawRectangle(primaryBrush, null, ordRect, 5, 5);
        }
        double extW = Math.Clamp(fe * w, 0, w - ordW);
        if (extW > 0)
        {
            var extRect = new Rect(ordW, 3, extW, 10);
            context.DrawRectangle(accentBrush, null, extRect, 5, 5);
        }
        double markX = Math.Clamp(fc * w, 0, w - 2);
        var markRect = new Rect(markX, 0, 2, 16);
        context.DrawRectangle(textBrush, null, markRect, 1, 1);
    }
}
