using System;
using System.Collections.Generic;
using System.Linq;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class FestivosCalcTests
{
    [Fact]
    public void DomingoPascua_FechasConocidas()
    {
        Assert.Equal(new DateOnly(2025, 4, 20), FestivosCalc.DomingoPascua(2025));
        Assert.Equal(new DateOnly(2026, 4, 5), FestivosCalc.DomingoPascua(2026));
        Assert.Equal(new DateOnly(2027, 3, 28), FestivosCalc.DomingoPascua(2027));
        Assert.Equal(new DateOnly(2028, 4, 16), FestivosCalc.DomingoPascua(2028));
    }
    [Fact]
    public void ParaAnio_2026_CoincideConLosFestivosPrecargadosDeLaApp()
    {
        var precargados = new AppState().Festivos.OrderBy(x => x, StringComparer.Ordinal).ToList();
        Assert.Equal(precargados, FestivosCalc.ParaAnio(2026));
    }
    [Fact]
    public void ParaAnio_2027_TieneLosDieciochoFestivosEsperados()
    {
        var esperados = new[]
        {
            "2027-01-01", "2027-01-11", "2027-03-22", "2027-03-25", "2027-03-26", "2027-05-01",
            "2027-05-10", "2027-05-31", "2027-06-07", "2027-07-05", "2027-07-20", "2027-08-07",
            "2027-08-16", "2027-10-18", "2027-11-01", "2027-11-15", "2027-12-08", "2027-12-25",
        };
        Assert.Equal(esperados, FestivosCalc.ParaAnio(2027));
    }
    [Fact]
    public void ParaAnio_2025_UnificaLosQueCaenElMismoLunes()
    {
        var f = FestivosCalc.ParaAnio(2025);
        Assert.Equal(17, f.Count);
        Assert.Single(f, x => x == "2025-06-30");
    }
    [Fact]
    public void AgregarAnio_NoBorraLoExistente_Ordena_YEsIdempotente()
    {
        var lista = new List<string> { "2026-12-25", "2026-01-01", "2030-05-05" };
        int primera = FestivosCalc.AgregarAnio(lista, 2027);
        Assert.Equal(18, primera);
        Assert.Contains("2030-05-05", lista);
        Assert.Contains("2026-12-25", lista);
        Assert.Equal(lista.OrderBy(x => x, StringComparer.Ordinal).ToList(), lista);
        Assert.Equal(0, FestivosCalc.AgregarAnio(lista, 2027));
    }
    [Fact]
    public void CompletarAnios_SoloAgregaAniosQueNoTienenNingunFestivo()
    {
        var lista = new AppState().Festivos.ToList();   // solo 2026
        int agregados = FestivosCalc.CompletarAnios(lista, new[] { 2026, 2027 });
        Assert.Equal(18, agregados);
        Assert.Equal(36, lista.Count);
    }
    [Fact]
    public void CompletarAnios_RespetaUnFestivoQueElUsuarioQuito()
    {
        var lista = new AppState().Festivos.ToList();
        lista.Remove("2026-10-12");   // el usuario lo quitó a propósito
        FestivosCalc.CompletarAnios(lista, new[] { 2026 });
        Assert.DoesNotContain("2026-10-12", lista);
        Assert.Equal(17, lista.Count);
    }
}
