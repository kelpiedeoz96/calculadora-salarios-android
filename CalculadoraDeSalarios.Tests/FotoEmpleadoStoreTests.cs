using System;
using System.IO;
using CalculadoraDeSalarios.Core.Services;
using Xunit;
namespace CalculadoraDeSalarios.Tests;
public class FotoEmpleadoStoreTests : IDisposable
{
    readonly string _dir = Path.Combine(Path.GetTempPath(), "test_fotos_" + Guid.NewGuid().ToString("N"));
    readonly string _origen;
    readonly JsonDataStore _store;
    public FotoEmpleadoStoreTests()
    {
        Directory.CreateDirectory(_dir);
        _origen = Path.Combine(_dir, "mi foto.JPG");
        File.WriteAllBytes(_origen, new byte[] { 1, 2, 3, 4, 5 });
        _store = new JsonDataStore(Path.Combine(_dir, "datos"));
    }
    public void Dispose()
    {
        try { Directory.Delete(_dir, true); } catch { }
    }
    [Fact]
    public void Guarda_la_foto_con_nombre_propio_y_se_puede_leer()
    {
        var rel = _store.SaveEmployeePhoto("abc1234", _origen);
        Assert.StartsWith("foto_abc1234_", rel);
        Assert.EndsWith(".jpg", rel);
        Assert.Equal(new byte[] { 1, 2, 3, 4, 5 }, _store.LoadEmployeePhotoBytes(rel));
    }
    [Fact]
    public void Cada_cambio_de_foto_genera_un_archivo_nuevo_y_conserva_el_anterior()
    {
        var a = _store.SaveEmployeePhoto("abc1234", _origen);
        var b = _store.SaveEmployeePhoto("abc1234", _origen);
        Assert.NotEqual(a, b);
        Assert.NotNull(_store.LoadEmployeePhotoBytes(a));
        Assert.NotNull(_store.LoadEmployeePhotoBytes(b));
    }
    [Fact]
    public void Rechaza_formatos_que_no_son_png_o_jpg()
    {
        var gif = Path.Combine(_dir, "x.gif");
        File.WriteAllBytes(gif, new byte[] { 9 });
        Assert.Throws<InvalidOperationException>(() => _store.SaveEmployeePhoto("abc1234", gif));
    }
    [Fact]
    public void El_id_se_limpia_para_que_no_pueda_escapar_de_la_carpeta()
    {
        var rel = _store.SaveEmployeePhoto("../../mal id", _origen);
        Assert.DoesNotContain("/", rel);
        Assert.DoesNotContain("\\", rel);
        Assert.DoesNotContain("..", rel);
        Assert.NotNull(_store.LoadEmployeePhotoBytes(rel));
    }
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("no_existe.jpg")]
    [InlineData("../data.json")]
    [InlineData("sub/dir.jpg")]
    public void Leer_rutas_invalidas_o_inexistentes_devuelve_null(string? rel)
    {
        Assert.Null(_store.LoadEmployeePhotoBytes(rel));
    }
}
