using System;
using System.IO;
using System.Threading.Tasks;
using Avalonia.Controls;
using Avalonia.Platform.Storage;
using CalculadoraDeSalarios.App.Views;
namespace CalculadoraDeSalarios.App.Services;
public class DialogService : IDialogService
{
    public Window? MainWindow { get; set; }
    public Action<string, ToastKind>? ToastNotifier { get; set; }

    public async Task<bool> ConfirmAsync(string title, string message, bool isDanger = false,
        string confirmText = "Confirmar", string cancelText = "Cancelar")
    {
        if (MainWindow == null) return false;
        var dlg = new ConfirmDialogWindow(title, message, isDanger, confirmText, cancelText);
        return await dlg.ShowDialog<bool>(MainWindow);
    }
    public async Task OfferOpenFileAsync(string path)
    {
        if (MainWindow == null || string.IsNullOrEmpty(path) || !File.Exists(path)) return;
        bool open = await ConfirmAsync(
            "Archivo guardado",
            $"Se guardó en:\n{path}\n\n¿Quieres abrirlo ahora?",
            false, "Abrir", "Ahora no");
        if (open && !FileOpener.Open(path))
            ShowToast("No se pudo abrir el archivo. Está en: " + path, ToastKind.Warning);
    }
    public async Task<string?> ShowSaveFileDialogAsync(string title, string defaultFileName, string extension, string filterName)
    {
        if (MainWindow == null) return null;
        var file = await MainWindow.StorageProvider.SaveFilePickerAsync(new FilePickerSaveOptions
        {
            Title = title,
            SuggestedFileName = defaultFileName,
            DefaultExtension = extension,
            FileTypeChoices = new[]
            {
                new FilePickerFileType(filterName) { Patterns = new[] { "*." + extension } }
            }
        });
        var local = file?.TryGetLocalPath();
        if (!string.IsNullOrEmpty(local) && !Path.HasExtension(local))
            local += "." + extension;
        return local;
    }
    public async Task<string?> ShowOpenFileDialogAsync(string title, string[] extensions, string filterName)
    {
        if (MainWindow == null) return null;
        var files = await MainWindow.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = title,
            AllowMultiple = false,
            FileTypeFilter = new[]
            {
                new FilePickerFileType(filterName) { Patterns = extensions.SelectMany(e => { var x = e.TrimStart("*.".ToCharArray()); return new[] { "*." + x.ToLowerInvariant(), "*." + x.ToUpperInvariant() }; }).Distinct().ToList() }
            }
        });
        return files.Count > 0 ? files[0].TryGetLocalPath() : null;
    }
    public async Task<string?> ShowFolderDialogAsync(string title)
    {
        if (MainWindow == null) return null;
        var carpetas = await MainWindow.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions
        {
            Title = title,
            AllowMultiple = false
        });
        return carpetas.Count > 0 ? carpetas[0].TryGetLocalPath() : null;
    }
    public void ShowToast(string message, ToastKind kind = ToastKind.Info)
    {
        ToastNotifier?.Invoke(message, kind);
    }
}
