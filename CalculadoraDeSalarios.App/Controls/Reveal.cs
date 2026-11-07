using Avalonia;
using Avalonia.Animation;
using Avalonia.Media;
namespace CalculadoraDeSalarios.App.Controls;
public sealed class Reveal
{
    public const double DistanciaY = 16;
    public const double DistanciaX = 28;
    public static readonly AttachedProperty<double> ProgressProperty =
        AvaloniaProperty.RegisterAttached<Reveal, Visual, double>("Progress", 1.0);
    public static readonly AttachedProperty<double> ProgressXProperty =
        AvaloniaProperty.RegisterAttached<Reveal, Visual, double>("ProgressX", 1.0);
    public static double GetProgress(Visual v) => v.GetValue(ProgressProperty);
    public static void SetProgress(Visual v, double value) => v.SetValue(ProgressProperty, value);
    public static double GetProgressX(Visual v) => v.GetValue(ProgressXProperty);
    public static void SetProgressX(Visual v, double value) => v.SetValue(ProgressXProperty, value);
    static Reveal()
    {
        ProgressProperty.Changed.AddClassHandler<Visual>((v, _) => Aplicar(v));
        ProgressXProperty.Changed.AddClassHandler<Visual>((v, _) => Aplicar(v));
    }
    Reveal() { }
    static void Aplicar(Visual v)
    {
        double py = v.GetValue(ProgressProperty);
        double px = v.GetValue(ProgressXProperty);
        if (py >= 1.0 && px >= 1.0)
        {
            v.ClearValue(Visual.RenderTransformProperty);
            if (v is Animatable a) a.ClearValue(Animatable.TransitionsProperty);
            return;
        }
        if (v is Animatable an) an.Transitions = null;
        v.RenderTransform = new TranslateTransform((1.0 - px) * DistanciaX, (1.0 - py) * DistanciaY);
    }
}
