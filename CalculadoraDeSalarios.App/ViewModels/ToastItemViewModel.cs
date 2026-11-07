using System;
using CalculadoraDeSalarios.App.Services;
using CommunityToolkit.Mvvm.ComponentModel;
namespace CalculadoraDeSalarios.App.ViewModels;
public partial class ToastItemViewModel : ObservableObject
{
    public Guid Id { get; } = Guid.NewGuid();
    public string Message { get; init; } = "";
    public ToastKind Kind { get; init; } = ToastKind.Info;
    public string IconKey => Kind switch
    {
        ToastKind.Success => "IconCheck",
        ToastKind.Warning => "IconWarn",
        ToastKind.Error => "IconX",
        _ => "IconInfo"
    };
    public bool IsOk => Kind == ToastKind.Success;
    public bool IsWarn => Kind == ToastKind.Warning;
    public bool IsDanger => Kind == ToastKind.Error;
    public bool IsInfo => Kind == ToastKind.Info;
}
