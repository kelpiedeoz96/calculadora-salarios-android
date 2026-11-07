using System.Linq;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class AjustesCalcTests
{
    [Theory]
    [InlineData("42", 42d)]
    [InlineData(" 7,5 ", 7.5d)]
    [InlineData("7.5", 7.5d)]
    [InlineData("-3", -3d)]
    [InlineData("", 0d)]
    [InlineData("   ", 0d)]
    public void TryParseNumero_acepta_validos(string texto, double esperado)
    {
        Assert.True(AjustesCalc.TryParseNumero(texto, out var v));
        Assert.Equal(esperado, v, 6);
    }
    [Theory]
    [InlineData("abc")]
    [InlineData("NaN")]
    [InlineData("Infinity")]
    [InlineData("1,2,3")]
    [InlineData("12x")]
    public void TryParseNumero_rechaza_invalidos(string texto)
    {
        Assert.False(AjustesCalc.TryParseNumero(texto, out _));
    }
    [Fact]
    public void TryParseNumero_null_es_cero()
    {
        Assert.True(AjustesCalc.TryParseNumero(null, out var v));
        Assert.Equal(0d, v);
    }
    [Fact]
    public void FormatearNumero_usa_cultura_invariante()
    {
        Assert.Equal("7.5", AjustesCalc.FormatearNumero(7.5));
        Assert.Equal("1750905", AjustesCalc.FormatearNumero(1750905));
    }
    [Theory]
    [InlineData("6:00", "06:00")]
    [InlineData("06:00", "06:00")]
    [InlineData("600", "06:00")]
    [InlineData("0600", "06:00")]
    [InlineData("21:30", "21:30")]
    [InlineData("23:59", "23:59")]
    [InlineData(" 7:05 ", "07:05")]
    public void TryParseHora_normaliza(string texto, string esperado)
    {
        Assert.True(AjustesCalc.TryParseHora(texto, out var h));
        Assert.Equal(esperado, h);
    }
    [Theory]
    [InlineData("")]
    [InlineData(null)]
    [InlineData("24:00")]
    [InlineData("12:60")]
    [InlineData("abc")]
    [InlineData("6")]
    public void TryParseHora_rechaza(string? texto)
    {
        Assert.False(AjustesCalc.TryParseHora(texto, out _));
    }
    [Fact]
    public void ParseTurnos_filtra_invalidos_y_conserva_orden_y_duplicados()
    {
        var txt = "06:00-14:00\r\nbasura\n14:00-22:00\n25:00-26:00\n06:00-14:00\n  22:00-06:00  \n12:60-13:00\n6:00-14:00";
        var r = AjustesCalc.ParseTurnos(txt);
        Assert.Equal(new[] { "06:00-14:00", "14:00-22:00", "06:00-14:00", "22:00-06:00" }, r);
    }
    [Fact]
    public void ParseTurnos_vacio_da_lista_vacia()
    {
        Assert.Empty(AjustesCalc.ParseTurnos(null));
        Assert.Empty(AjustesCalc.ParseTurnos("  \n "));
    }
    [Fact]
    public void ParseFestivos_ordena_deduplica_y_descarta_fechas_inexistentes()
    {
        var txt = "2026-12-25\n2026-01-01, 2026-02-30\n2026-12-25\nfoo 2026-07-20 bar\n2026-13-01";
        var r = AjustesCalc.ParseFestivos(txt);
        Assert.Equal(new[] { "2026-01-01", "2026-07-20", "2026-12-25" }, r);
    }
    [Fact]
    public void ParseFestivos_vacio()
    {
        Assert.Empty(AjustesCalc.ParseFestivos(null));
        Assert.Empty(AjustesCalc.ParseFestivos(""));
    }
    [Fact]
    public void UnirLineas_une_con_salto_y_acepta_null()
    {
        Assert.Equal("a\nb", AjustesCalc.UnirLineas(new[] { "a", "b" }));
        Assert.Equal(string.Empty, AjustesCalc.UnirLineas(null));
    }
    [Fact]
    public void Ida_y_vuelta_de_turnos_por_defecto_es_estable()
    {
        var def = new Config().Turnos;
        var texto = AjustesCalc.UnirLineas(def);
        Assert.Equal(def, AjustesCalc.ParseTurnos(texto));
    }
    [Fact]
    public void Ida_y_vuelta_de_festivos_por_defecto_es_estable()
    {
        var def = new AppState().Festivos;
        var texto = AjustesCalc.UnirLineas(def);
        Assert.Equal(def.OrderBy(x => x, System.StringComparer.Ordinal).ToList(), AjustesCalc.ParseFestivos(texto));
    }
}
