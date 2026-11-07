using System.Threading.Tasks;
namespace CalculadoraDeSalarios.App.Services;
public enum ToastKind { Info, Success, Warning, Error }
public interface IDialogService
{
    Task<bool> ConfirmAsync(string title, string message, bool isDanger = false,
        string confirmText = "Confirmar", string cancelText = "Cancelar");
    Task OfferOpenFileAsync(string path);
    Task<string?> ShowSaveFileDialogAsync(string title, string defaultFileName, string extension, string filterName);
    Task<string?> ShowOpenFileDialogAsync(string title, string[] extensions, string filterName);
    Task<string?> ShowFolderDialogAsync(string title);
    void ShowToast(string message, ToastKind kind = ToastKind.Info);
}
