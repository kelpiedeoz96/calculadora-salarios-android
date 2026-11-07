using System;
using System.Diagnostics;
namespace CalculadoraDeSalarios.App.Services;
public static class FileOpener
{
    public static bool Open(string path)
    {
        try
        {
            if (OperatingSystem.IsWindows())
            {
                Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
            }
            else
            {
                var psi = new ProcessStartInfo(OperatingSystem.IsMacOS() ? "open" : "xdg-open")
                {
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                };
                psi.ArgumentList.Add(path);
                Process.Start(psi);
            }
            return true;
        }
        catch
        {
            return false;
        }
    }
}
