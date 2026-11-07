using System.Linq;
using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using CalculadoraDeSalarios.App.Services;
using CalculadoraDeSalarios.App.ViewModels;
using CalculadoraDeSalarios.App.Views;
using CalculadoraDeSalarios.Core.Services;
namespace CalculadoraDeSalarios.App;
public partial class App : Application
{
    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }
    public override void OnFrameworkInitializationCompleted()
    {
        var dataStore = new JsonDataStore();
        var (state, ui, history) = dataStore.Load();
        var appearanceService = new AppearanceService();
        appearanceService.ApplyTheme(ui.ThemeMode);
        appearanceService.ApplyReduceAnimations(ui.ReduceAnimations);
        var dialogService = new DialogService();
        var exportService = new ExportService();
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            if (desktop.Args != null && desktop.Args.Contains("--gallery"))
            {
                var galleryWin = new DesignGalleryWindow(appearanceService, ui.ReduceAnimations);
                appearanceService.RegisterWindow(galleryWin, ui.ReduceAnimations);
                desktop.MainWindow = galleryWin;
            }
            else
            {
                var mainVm = new MainViewModel(dataStore, dialogService, exportService, state, ui, history, appearanceService);
                var mainWin = new MainWindow { DataContext = mainVm };
                dialogService.MainWindow = mainWin;
                appearanceService.RegisterWindow(mainWin, ui.ReduceAnimations);
                desktop.MainWindow = mainWin;
                mainVm.RespaldarSiCorresponde();
                desktop.Exit += (_, _) => mainVm.RespaldarSiCorresponde();
            }
        }
        base.OnFrameworkInitializationCompleted();
    }
}
