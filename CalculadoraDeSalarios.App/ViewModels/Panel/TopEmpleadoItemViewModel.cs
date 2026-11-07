using System;
using System.Windows.Input;
using Avalonia.Media;
using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.Input;
namespace CalculadoraDeSalarios.App.ViewModels.Panel;
public class TopEmpleadoItemViewModel
{
    public string Id { get; }
    public string Name { get; }
    public string Initials { get; }
    public string? PhotoRelPath { get; }
    public IBrush AvatarBrush { get; }
    public string DetailFormatted { get; }
    public ICommand OpenCommand { get; }

    public TopEmpleadoItemViewModel(EmpSummary s, Action<string> onOpen)
    {
        Id = s.Emp.Id;
        Name = string.IsNullOrWhiteSpace(s.Emp.Name) ? "Sin nombre" : s.Emp.Name;
        Initials = Fmt.Initials(s.Emp.Name);
        PhotoRelPath = s.Emp.PhotoRelPath;
        int h = Fmt.HueOf(s.Emp.Name);
        AvatarBrush = new SolidColorBrush(Color.FromRgb(
            (byte)(80 + (h % 100)),
            (byte)(100 + ((h * 3) % 80)),
            (byte)(120 + ((h * 7) % 80))));
        DetailFormatted = $"{Engine.FmtHM(s.R.TotalMin)} · {Fmt.Money(s.R.TotalAmount)}";
        OpenCommand = new RelayCommand(() => onOpen(Id));
    }
}
