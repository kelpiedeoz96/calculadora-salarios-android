#pragma warning disable MVVMTK0034
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using CalculadoraDeSalarios.Core;
using CommunityToolkit.Mvvm.ComponentModel;
namespace CalculadoraDeSalarios.App.ViewModels.Liquidacion;
public partial class DayRowViewModel : ObservableObject
{
    readonly Action<DayRowViewModel, string, string> _onCellCommitted;
    readonly Action<DayRowViewModel, bool> _onRestToggled;
    readonly Action<DayRowViewModel, string> _onPresetApplied;
    public DateOnly Date { get; private set; }
    public int Dow { get; private set; }
    public string DateKey { get; private set; } = "";
    [ObservableProperty]
    string _dateLabel = "";
    [ObservableProperty]
    bool _isDom;
    [ObservableProperty]
    bool _isFest;
    [ObservableProperty]
    bool _inPeriod;
    [ObservableProperty]
    bool _rest;
    [ObservableProperty]
    string _in1 = "";
    [ObservableProperty]
    string _out1 = "";
    [ObservableProperty]
    string _in2 = "";
    [ObservableProperty]
    string _out2 = "";
    [ObservableProperty]
    string _brkText = "";
    [ObservableProperty]
    string _dayHoursFormatted = "";
    [ObservableProperty]
    string _statusText = "";
    [ObservableProperty]
    bool _isWorkedRest;
    [ObservableProperty]
    string _dayAmountFormatted = "";
    public ObservableCollection<DayChipItem> Chips { get; } = new();
    public DayRowViewModel(
        DayRow row,
        Action<DayRowViewModel, string, string> onCellCommitted,
        Action<DayRowViewModel, bool> onRestToggled,
        Action<DayRowViewModel, string> onPresetApplied)
    {
        _onCellCommitted = onCellCommitted;
        _onRestToggled = onRestToggled;
        _onPresetApplied = onPresetApplied;
        UpdateFrom(row);
    }
    public void UpdateFrom(DayRow row)
    {
        Date = row.Date;
        Dow = row.Dow;
        DateKey = Engine.Key(row.Date);
        DateLabel = $"{Engine.Dow[row.Dow]} {Fmt.Dmy(row.Date)}";
        IsDom = row.Dom;
        IsFest = row.IsFest;
        InPeriod = row.InPeriod;
        Rest = row.Rest;
        var rec = row.Rec;
        _in1 = rec?.In1 ?? "";
        _out1 = rec?.Out1 ?? "";
        _in2 = rec?.In2 ?? "";
        _out2 = rec?.Out2 ?? "";
        _brkText = rec != null && rec.Brk > 0 ? ((int)rec.Brk).ToString() : "";
        OnPropertyChanged(nameof(In1));
        OnPropertyChanged(nameof(Out1));
        OnPropertyChanged(nameof(In2));
        OnPropertyChanged(nameof(Out2));
        OnPropertyChanged(nameof(BrkText));
        DayHoursFormatted = row.DayMin > 0 ? Engine.FmtHM(row.DayMin) : "";
        DayAmountFormatted = row.DayAmount > 0 ? Fmt.Money(row.DayAmount) : "";
        if (row.Rest && row.DayMin == 0)
        {
            StatusText = "Descansó";
            IsWorkedRest = false;
        }
        else if (row.Rest && row.DayMin > 0)
        {
            StatusText = "Trabajó descanso";
            IsWorkedRest = true;
        }
        else
        {
            StatusText = "";
            IsWorkedRest = false;
        }
        Chips.Clear();
        foreach (var c in Engine.Cats)
        {
            int min = row.Cats[(int)c];
            if (min > 0)
            {
                Chips.Add(new DayChipItem
                {
                    Cat = c,
                    ShortLabel = Engine.CatShort[c],
                    TimeFormatted = Engine.FmtHM(min)
                });
            }
        }
    }
    public void RefreshFields()
    {
        OnPropertyChanged(nameof(In1));
        OnPropertyChanged(nameof(Out1));
        OnPropertyChanged(nameof(In2));
        OnPropertyChanged(nameof(Out2));
        OnPropertyChanged(nameof(BrkText));
    }
    public void CommitTime(string field, string? raw)
    {
        var n = JornadaHelper.NormTime(raw);
        if (n != null) _onCellCommitted(this, field, n);
        RefreshFields();
    }
    public void CommitBreak(string? raw)
    {
        _onCellCommitted(this, "brk", raw ?? "");
        RefreshFields();
    }

    public void ToggleRest(bool value)
    {
        _onRestToggled(this, value);
    }

    public void ApplyPreset(string preset)
    {
        _onPresetApplied(this, preset);
    }
}
