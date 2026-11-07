using System;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class JornadaTests
{
    [Theory]
    [InlineData("8:30", "08:30")]
    [InlineData("0830", "08:30")]
    [InlineData("14", "14:00")]
    [InlineData("25", null)]
    [InlineData("8:75", null)]
    [InlineData("", "")]
    [InlineData("   ", "")]
    public void NormTime_DebeNormalizarCasosSegunEspecificacion(string input, string? expected)
    {
        var result = JornadaHelper.NormTime(input);
        Assert.Equal(expected, result);
    }

    [Fact]
    public void EditDay_DebeCrearRegistroSiNoExiste_YRespetarDescansoPorDefecto()
    {
        var emp = new Employee { Name = "Operario 1", RestDay = 0 };
        var domingo = new DateOnly(2026, 10, 4);
        JornadaHelper.EditDay(emp, domingo, rec =>
        {
            rec.In1 = "08:00";
            rec.Out1 = "12:00";
        });
        var key = Engine.Key(domingo);
        Assert.True(emp.Days.ContainsKey(key));
        Assert.Equal("08:00", emp.Days[key].In1);
        Assert.Equal("12:00", emp.Days[key].Out1);
        Assert.True(emp.Days[key].Desc); // Descanso asignado automáticamente
    }
    [Fact]
    public void EditDay_DebeBorrarRegistroAlQuedarVacio()
    {
        var emp = new Employee { Name = "Operario 2", RestDay = 3 };
        var lunes = new DateOnly(2026, 10, 5);
        JornadaHelper.EditDay(emp, lunes, rec =>
        {
            rec.In1 = "09:00";
            rec.Out1 = "17:00";
        });
        var key = Engine.Key(lunes);
        Assert.True(emp.Days.ContainsKey(key));
        JornadaHelper.EditDay(emp, lunes, rec =>
        {
            rec.In1 = "";
            rec.Out1 = "";
            rec.In2 = "";
            rec.Out2 = "";
            rec.Brk = 0;
            rec.Desc = false;
        });
        Assert.False(emp.Days.ContainsKey(key));
    }
    [Fact]
    public void CalculateWeekGauge_DebeCalcularProporcionesExactas()
    {
        int cap = 2520;
        int worked = 2880;
        int extra = 360;
        var (ord, ext, fo, fe, fc) = JornadaHelper.CalculateWeekGauge(worked, extra, cap);
        Assert.Equal(2520, ord);
        Assert.Equal(360, ext);
        Assert.Equal(2880.0, ord + ext);
        Assert.Equal(2520.0 / 2880.0, fo);
        Assert.Equal(360.0 / 2880.0, fe);
        Assert.Equal(2520.0 / 2880.0, fc);
    }
}
