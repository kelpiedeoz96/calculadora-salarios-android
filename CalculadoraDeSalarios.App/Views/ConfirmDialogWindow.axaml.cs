using Avalonia.Controls;
using Avalonia.Input;
namespace CalculadoraDeSalarios.App.Views;
public partial class ConfirmDialogWindow : Window
{
    public ConfirmDialogWindow() : this("Confirmar", "¿Deseas continuar?", false)
    {
    }

    public ConfirmDialogWindow(string title, string message, bool isDanger,
        string confirmText = "Confirmar", string cancelText = "Cancelar")
    {
        InitializeComponent();
        TxtTitle.Text = title;
        TxtMessage.Text = message;
        BtnConfirm.Content = confirmText;
        BtnCancel.Content = cancelText;
        if (isDanger)
        {
            BtnConfirm.Classes.Remove("primary");
            BtnConfirm.Classes.Add("danger");
        }
        BtnConfirm.Click += (_, _) => Close(true);
        BtnCancel.Click += (_, _) => Close(false);
        KeyDown += (_, e) =>
        {
            if (e.Key == Key.Escape) Close(false);
        };
    }
}
