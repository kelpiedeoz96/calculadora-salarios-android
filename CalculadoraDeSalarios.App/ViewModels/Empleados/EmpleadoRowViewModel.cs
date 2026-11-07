using Avalonia.Media;
using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.ComponentModel;
namespace CalculadoraDeSalarios.App.ViewModels.Empleados;
public partial class EmpleadoRowViewModel : ObservableObject
{
    public Employee Emp { get; }
    public EmpSummary Summary { get; }
    public string Name => string.IsNullOrWhiteSpace(Emp.Name) ? "Sin nombre" : Emp.Name;
    public string Initials => Fmt.Initials(Emp.Name);
    public string? PhotoRelPath => Emp.PhotoRelPath;
    public IBrush AvatarBrush { get; }
    public string TotalHoras { get; }
    public string Nocturnas { get; }
    public string DomFest { get; }
    public string Extra { get; }
    public string Recargos { get; }
    public string Basico { get; }
    public string Auxilio { get; }
    public string Descuentos { get; }
    public string Neto { get; }
    public EmpleadoRowViewModel(EmpSummary s)
    {
        Emp = s.Emp;
        Summary = s;
        int h = Fmt.HueOf(s.Emp.Name);
        AvatarBrush = new SolidColorBrush(Color.FromRgb(
            (byte)(80 + (h % 100)),
            (byte)(100 + ((h * 3) % 80)),
            (byte)(120 + ((h * 7) % 80))));
        TotalHoras = Engine.FmtHM(s.R.TotalMin);
        Nocturnas = Engine.FmtHM(EmpleadosCalc.SumCats(s.R, Cat.ONoc));
        DomFest = Engine.FmtHM(EmpleadosCalc.SumCats(s.R, Cat.ODom, Cat.ONocDom));
        Extra = Engine.FmtHM(EmpleadosCalc.SumCats(s.R, Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom));
        Recargos = Fmt.Money(s.R.TotalAmount);
        Basico = Fmt.Money(s.P.BasicoQ);
        Auxilio = Fmt.Money(s.P.AuxQ);
        Descuentos = "- " + Fmt.Money(s.P.Descuentos);
        Neto = Fmt.Money(s.P.Neto);
    }
}
