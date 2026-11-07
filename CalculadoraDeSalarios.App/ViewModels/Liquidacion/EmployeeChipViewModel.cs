using Avalonia.Media;
using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.ComponentModel;
namespace CalculadoraDeSalarios.App.ViewModels.Liquidacion;
public partial class EmployeeChipViewModel : ObservableObject
{
    public Employee Emp { get; }
    public string Id => Emp.Id;
    public string Name => string.IsNullOrWhiteSpace(Emp.Name) ? "Sin nombre" : Emp.Name;
    public string Initials => Fmt.Initials(Emp.Name);
    public string? PhotoRelPath => Emp.PhotoRelPath;
    public IBrush AvatarBrush { get; }
    [ObservableProperty]
    string _payFormatted = "$ 0";
    [ObservableProperty]
    bool _isSelected;
    public EmployeeChipViewModel(Employee emp, long pay, bool isSelected)
    {
        Emp = emp;
        _payFormatted = Fmt.Money(pay);
        _isSelected = isSelected;

        int h = Fmt.HueOf(emp.Name);
        AvatarBrush = new SolidColorBrush(Color.FromRgb(
            (byte)(80 + (h % 100)),
            (byte)(100 + ((h * 3) % 80)),
            (byte)(120 + ((h * 7) % 80))));
    }
    public void UpdatePay(long pay, bool isSelected)
    {
        PayFormatted = Fmt.Money(pay);
        IsSelected = isSelected;
    }
}
