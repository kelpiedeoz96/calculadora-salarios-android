using System;
using System.Diagnostics;
using System.Text;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Threading;
namespace CalculadoraDeSalarios.App.Controls;
public class CountUpTextBlock : TextBlock
{
    public static readonly StyledProperty<string?> TargetProperty =
        AvaloniaProperty.Register<CountUpTextBlock, string?>(nameof(Target));
    public string? Target
    {
        get => GetValue(TargetProperty);
        set => SetValue(TargetProperty, value);
    }
    protected override Type StyleKeyOverride => typeof(TextBlock);
    static readonly TimeSpan Duracion = TimeSpan.FromMilliseconds(750);
    DispatcherTimer? _timer;
    readonly Stopwatch _reloj = new();
    string _plantilla = "";
    long _desde;
    long _hasta;
    long _mostrado = -1;
    string? _pendiente;
    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == TargetProperty)
            Iniciar(change.GetNewValue<string?>());
    }
    protected override void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        if (_pendiente != null)
        {
            var t = _pendiente;
            _pendiente = null;
            Iniciar(t);
        }
    }
    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnDetachedFromVisualTree(e);
        Detener();
        if (!string.IsNullOrEmpty(Target))
        {
            Text = Target;
            _pendiente = Target;
            _mostrado = -1;
        }
    }
    void Iniciar(string? objetivo)
    {
        Detener();
        if (string.IsNullOrEmpty(objetivo))
        {
            Text = objetivo;
            _mostrado = -1;
            return;
        }
        if (TopLevel.GetTopLevel(this) == null)
        {
            Text = objetivo;
            _pendiente = objetivo;
            return;
        }
        if (!TryExtraerNumero(objetivo, out long hasta) || ReducirMovimiento())
        {
            Text = objetivo;
            _mostrado = hasta;
            return;
        }
        long desde = _mostrado >= 0 ? _mostrado : 0;
        if (desde == hasta)
        {
            Text = objetivo;
            _mostrado = hasta;
            return;
        }
        _plantilla = objetivo;
        _desde = desde;
        _hasta = hasta;
        Text = Dibujar(_plantilla, _desde);
        _reloj.Restart();
        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(16) };
        _timer.Tick += OnTick;
        _timer.Start();
    }
    void OnTick(object? sender, EventArgs e)
    {
        double p = Math.Min(1.0, _reloj.Elapsed.TotalMilliseconds / Duracion.TotalMilliseconds);
        double eased = 1.0 - Math.Pow(1.0 - p, 3.0);   // ease-out cúbico
        long actual = _desde + (long)Math.Round((_hasta - _desde) * eased);
        if (p >= 1.0)
        {
            Detener();
            Text = _plantilla;
            _mostrado = _hasta;
            return;
        }
        _mostrado = actual;
        Text = Dibujar(_plantilla, actual);
    }
    void Detener()
    {
        if (_timer == null) return;
        _timer.Stop();
        _timer.Tick -= OnTick;
        _timer = null;
    }
    bool ReducirMovimiento()
        => TopLevel.GetTopLevel(this) is Window w && w.Classes.Contains("reduce-motion");
    static bool TryExtraerNumero(string texto, out long valor)
    {
        valor = 0;
        int n = 0;
        foreach (char c in texto)
        {
            if (c < '0' || c > '9') continue;
            if (++n > 15) return false;
            valor = valor * 10 + (c - '0');
        }
        return n > 0;
    }
    static string Dibujar(string plantilla, long valor)
    {
        int huecos = 0;
        foreach (char c in plantilla) if (c >= '0' && c <= '9') huecos++;
        long maximo = 1;
        for (int i = 0; i < huecos; i++) maximo *= 10;
        if (valor >= maximo) valor = maximo - 1;
        if (valor < 0) valor = 0;
        string s = valor.ToString(System.Globalization.CultureInfo.InvariantCulture);
        bool rellenarCeros = plantilla.Contains(':');
        int di = s.Length - 1;
        bool agotado = false;
        var sb = new StringBuilder();
        for (int i = plantilla.Length - 1; i >= 0; i--)
        {
            char c = plantilla[i];
            if (c >= '0' && c <= '9')
            {
                if (di >= 0) sb.Insert(0, s[di--]);
                else if (rellenarCeros) sb.Insert(0, '0');
                else agotado = true;
            }
            else if (agotado && (c == '.' || c == ','))
            {
            }
            else
            {
                sb.Insert(0, c);
            }
        }
        return sb.ToString();
    }
}
