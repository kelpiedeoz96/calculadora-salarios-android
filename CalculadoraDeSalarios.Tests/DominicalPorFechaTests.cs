using System;
using System.Collections.Generic;
using System.Linq;
using CalculadoraDeSalarios.Core;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class DominicalPorFechaTests
{
    static readonly DateOnly Domingo1 = new(2027, 6, 27);
    static readonly DateOnly Domingo2 = new(2027, 7, 4);
    static Employee EmpleadoConDomingos()
    {
        var emp = new Employee { Name = "Empleado prueba", Salario = 0, RestDay = 0 };
        foreach (var d in new[] { Domingo1, Domingo2 })
            emp.Days[Engine.Key(d)] = new DayRecord { In1 = "08:00", Out1 = "12:00" };
        return emp;
    }
    static long OrdDomingo(Analysis a, DateOnly d)
        => a.Rows.Single(r => r.Date == d).Amounts[(int)Cat.ODom];
    static Analysis Analizar(Config cfg, DateOnly from, DateOnly to)
        => Engine.Analyze(EmpleadoConDomingos(), cfg, new HashSet<string>(), from, to);
    [Fact]
    public void Los_dias_de_prueba_son_domingo()
    {
        Assert.Equal(DayOfWeek.Sunday, Domingo1.DayOfWeek);
        Assert.Equal(DayOfWeek.Sunday, Domingo2.DayOfWeek);
    }
    [Fact]
    public void EffectiveDom_cambia_justo_el_1_de_julio_de_2027()
    {
        var cfg = new Config();
        Assert.Equal(90, Engine.EffectiveDom(cfg, new DateOnly(2027, 6, 30)));
        Assert.Equal(100, Engine.EffectiveDom(cfg, new DateOnly(2027, 7, 1)));
        Assert.Equal(90, Engine.EffectiveDom(cfg, new DateOnly(2026, 10, 15)));
    }
    [Fact]
    public void Antes_y_despues_del_cambio_se_paga_90_y_100()
    {
        var a = Analizar(new Config(), new DateOnly(2027, 6, 21), new DateOnly(2027, 7, 11));

        Assert.Equal(30016, OrdDomingo(a, Domingo1));
        Assert.Equal(33352, OrdDomingo(a, Domingo2));
    }
    [Fact]
    public void Con_el_cambio_automatico_apagado_sigue_en_90()
    {
        var cfg = new Config { DomAutoOn = false };
        var a = Analizar(cfg, new DateOnly(2027, 6, 21), new DateOnly(2027, 7, 11));
        Assert.Equal(30016, OrdDomingo(a, Domingo1));
        Assert.Equal(30016, OrdDomingo(a, Domingo2));
    }
    [Fact]
    public void Un_porcentaje_mayor_a_100_configurado_se_respeta()
    {
        var cfg = new Config();
        cfg.Pct.Dom = 110;
        var a = Analizar(cfg, new DateOnly(2027, 6, 21), new DateOnly(2027, 7, 11));
        Assert.Equal(36688, OrdDomingo(a, Domingo1));
        Assert.Equal(36688, OrdDomingo(a, Domingo2));
    }
    [Fact]
    public void Las_tarifas_mostradas_son_las_vigentes_al_final_del_periodo()
    {
        var antes = Analizar(new Config(), new DateOnly(2027, 6, 21), new DateOnly(2027, 6, 30));
        Assert.Equal(7504, antes.Rates[Cat.ODom]);

        var cruza = Analizar(new Config(), new DateOnly(2027, 6, 21), new DateOnly(2027, 7, 11));
        Assert.Equal(8338, cruza.Rates[Cat.ODom]);
    }
    [Fact]
    public void GetRates_sin_fecha_no_cambia_y_con_fecha_antigua_es_identico()
    {
        var emp = new Employee();
        var cfg = new Config();
        var sinFecha = Engine.GetRates(emp, cfg);
        var conFecha = Engine.GetRates(emp, cfg, new DateOnly(2026, 10, 15));
        for (int i = 0; i < 8; i++) Assert.Equal(sinFecha.ByCat[i], conFecha.ByCat[i]);
        Assert.Equal(sinFecha.Hv, conFecha.Hv);
    }
    [Fact]
    public void Una_semana_normal_de_2026_no_cambia_con_la_regla_nueva()
    {
        var emp = EmpleadoConDomingos();
        emp.Days["2026-10-04"] = new DayRecord { In1 = "08:00", Out1 = "12:00" };
        var a = Engine.Analyze(emp, new Config(), new HashSet<string>(), new DateOnly(2026, 9, 28), new DateOnly(2026, 10, 11));
        Assert.Equal(30016, a.Rows.Single(r => r.Date == new DateOnly(2026, 10, 4)).Amounts[(int)Cat.ODom]);
    }
}
