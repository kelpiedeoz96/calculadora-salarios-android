using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
namespace CalculadoraDeSalarios.App.Controls;
public class Icon : Control
{
    public static readonly StyledProperty<Geometry?> DataProperty =
        AvaloniaProperty.Register<Icon, Geometry?>(nameof(Data));
    public static readonly StyledProperty<IBrush?> ForegroundProperty =
        TextBlock.ForegroundProperty.AddOwner<Icon>();
    public static readonly StyledProperty<double> SizeProperty =
        AvaloniaProperty.Register<Icon, double>(nameof(Size), 16.0);
    static Icon()
    {
        AffectsRender<Icon>(DataProperty, ForegroundProperty, SizeProperty);
        AffectsMeasure<Icon>(SizeProperty);
    }
    public Geometry? Data
    {
        get => GetValue(DataProperty);
        set => SetValue(DataProperty, value);
    }
    public IBrush? Foreground
    {
        get => GetValue(ForegroundProperty);
        set => SetValue(ForegroundProperty, value);
    }
    public double Size
    {
        get => GetValue(SizeProperty);
        set => SetValue(SizeProperty, value);
    }
    protected override Size MeasureOverride(Size availableSize)
    {
        var s = Size > 0 ? Size : 16.0;
        return new Size(s, s);
    }
    public override void Render(DrawingContext context)
    {
        base.Render(context);
        if (Data == null) return;
        var brush = Foreground ?? Brushes.Black;
        var s = Size > 0 ? Size : 16.0;
        var scale = s / 24.0;
        using (context.PushTransform(Matrix.CreateScale(scale, scale)))
        {
            var pen = new Pen(brush, 1.8, lineCap: PenLineCap.Round, lineJoin: PenLineJoin.Round);
            context.DrawGeometry(null, pen, Data);
        }
    }
}
