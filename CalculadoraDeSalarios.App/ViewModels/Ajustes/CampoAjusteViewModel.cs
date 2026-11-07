using System;
using CommunityToolkit.Mvvm.ComponentModel;

namespace CalculadoraDeSalarios.App.ViewModels.Ajustes;

public enum ResultadoCampo
{
    SinCambio,
    Cambiado,
    Invalido
}

public partial class CampoAjusteViewModel : ObservableObject
{
    readonly Func<string> _leer;
    readonly Func<string, ResultadoCampo> _aplicar;
    readonly Action _alCambiar;

    public string Etiqueta { get; }
    public bool EsMultilinea { get; }
    public double Alto { get; }

    [ObservableProperty] string _texto = string.Empty;

    public CampoAjusteViewModel(
        string etiqueta,
        Func<string> leer,
        Func<string, ResultadoCampo> aplicar,
        Action alCambiar,
        bool multilinea = false,
        double alto = 0)
    {
        Etiqueta = etiqueta;
        _leer = leer;
        _aplicar = aplicar;
        _alCambiar = alCambiar;
        EsMultilinea = multilinea;
        Alto = alto;

        Texto = _leer();
    }

    public void Confirmar(string? textoIngresado)
    {
        var res = _aplicar(textoIngresado ?? string.Empty);
        if (res == ResultadoCampo.Cambiado)
            _alCambiar();
        Texto = _leer();
    }

    public void Refrescar() => Texto = _leer();
}
