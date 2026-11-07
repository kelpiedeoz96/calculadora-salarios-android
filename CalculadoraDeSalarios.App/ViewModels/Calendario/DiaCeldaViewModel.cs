using System;
using System.Windows.Input;
using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.Input;
namespace CalculadoraDeSalarios.App.ViewModels.Calendario;
public class DiaCeldaViewModel
{
    public bool IsPlaceholder { get; }
    public int Dia { get; }
    public string HorasFormatted { get; } = "";
    public bool HasHoras { get; }
    public bool IsDom { get; }
    public bool IsFest { get; }
    public bool IsOutside { get; }
    public bool IsToday { get; }
    public double Fraccion { get; }
    public bool HasBar { get; }
    public bool BarraExtra { get; }
    public string TooltipText { get; } = "";
    public ICommand SeleccionarCommand { get; }
    public DiaCeldaViewModel()
    {
        IsPlaceholder = true;
        SeleccionarCommand = new RelayCommand(() => { });
    }
    public DiaCeldaViewModel(DiaCalendario d, Action<DateOnly> onPick)
    {
        Dia = d.Dia;
        HorasFormatted = d.DayMin > 0 ? Engine.FmtHM(d.DayMin) : "";
        HasHoras = d.DayMin > 0;
        IsDom = d.Dom;
        IsFest = d.Festivo;
        IsOutside = !d.EnPeriodo;
        IsToday = d.EsHoy;
        Fraccion = d.Fraccion;
        HasBar = d.Fraccion > 0;
        BarraExtra = d.BarraExtra;
        TooltipText = d.Clave + " · " + (string.IsNullOrEmpty(d.Turnos) ? "sin horas" : d.Turnos);
        var fecha = d.Fecha;
        SeleccionarCommand = new RelayCommand(() => onPick(fecha));
    }
}
