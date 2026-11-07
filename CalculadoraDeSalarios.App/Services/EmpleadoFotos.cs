using System.Collections.Generic;
using System.IO;
using Avalonia.Media.Imaging;
using CalculadoraDeSalarios.Core.Services;
namespace CalculadoraDeSalarios.App.Services;
public static class EmpleadoFotos
{
    const int AnchoAvatar = 256;
    const int MaxEnCache = 128;
    static IDataStore? _store;
    static readonly Dictionary<string, Bitmap> Cache = new();
    public static void Configure(IDataStore store)
    {
        _store = store;
        Cache.Clear();
    }
    public static Bitmap? Get(string? relPath)
    {
        if (string.IsNullOrWhiteSpace(relPath) || _store == null) return null;
        if (Cache.TryGetValue(relPath, out var cached)) return cached;
        var bytes = _store.LoadEmployeePhotoBytes(relPath);
        if (bytes == null || bytes.Length == 0) return null;
        try
        {
            using var ms = new MemoryStream(bytes);
            var bmp = Bitmap.DecodeToWidth(ms, AnchoAvatar);
            if (Cache.Count >= MaxEnCache) Cache.Clear();
            Cache[relPath] = bmp;
            return bmp;
        }
        catch
        {
            return null;
        }
    }
}
