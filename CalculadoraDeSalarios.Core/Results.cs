using System;
using System.Collections.Generic;
namespace CalculadoraDeSalarios.Core;
public class Seg
{
    public int Min { get; set; }
    public bool Night { get; set; }
}
public class Rates
{
    public double Hv { get; set; }
    public double[] ByCat { get; init; } = new double[8];
    public double this[Cat c] => ByCat[(int)c];
}
public class DayRow
{
    public DateOnly Date { get; set; }
    public int Dow { get; set; }
    public DayRecord? Rec { get; set; }
    public bool Rest { get; set; }
    public bool IsFest { get; set; }
    public bool Dom { get; set; }
    public bool InPeriod { get; set; }
    public int[] Cats { get; init; } = new int[8];
    public int DayMin { get; set; }
    public int ExtraMin { get; set; }
    public long[] Amounts { get; init; } = new long[8];
    public long DayAmount { get; set; }
}
public class Week
{
    public DateOnly Start { get; set; }
    public DateOnly End { get; set; }
    public int WorkedMin { get; set; }
    public int ExtraMin { get; set; }
    public bool Straddle { get; set; }
}
public class Analysis
{
    public List<DayRow> Rows { get; init; } = new();
    public List<Week> Weeks { get; init; } = new();
    public List<string> Alerts { get; init; } = new();
    public int[] TotMin { get; init; } = new int[8];
    public long[] TotAmount { get; init; } = new long[8];
    public int TotalMin { get; set; }
    public long TotalAmount { get; set; }
    public Rates Rates { get; set; } = new();
}
public class Payroll
{
    public double Sal { get; set; }
    public bool AuxOn { get; set; }
    public long BasicoQ { get; set; }
    public long AuxQ { get; set; }
    public long Recargos { get; set; }
    public long Devengado { get; set; }
    public double Base { get; set; }
    public long Salud { get; set; }
    public long Pension { get; set; }
    public long Descuentos { get; set; }
    public long Neto { get; set; }
}
public record EmpSummary(Employee Emp, Analysis R, Payroll P, long Pay);
