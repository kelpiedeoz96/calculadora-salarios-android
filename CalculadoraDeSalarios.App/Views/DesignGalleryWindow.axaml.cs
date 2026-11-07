using Avalonia.Controls;
using CalculadoraDeSalarios.App.Services;
namespace CalculadoraDeSalarios.App.Views;
public partial class DesignGalleryWindow : Window
{
    readonly IAppearanceService _appearanceService;
    public DesignGalleryWindow() : this(new AppearanceService(), false)
    {
    }
    public DesignGalleryWindow(IAppearanceService appearanceService, bool initialReduceMotion)
    {
        InitializeComponent();
        _appearanceService = appearanceService;
        BtnThemeLight.Click += (_, _) => _appearanceService.ApplyTheme("light");
        BtnThemeDark.Click += (_, _) => _appearanceService.ApplyTheme("dark");
        BtnThemeSystem.Click += (_, _) => _appearanceService.ApplyTheme("system");
        ChkReduceMotion.IsChecked = initialReduceMotion;
        ChkReduceMotion.IsCheckedChanged += (_, _) =>
            _appearanceService.ApplyReduceAnimations(ChkReduceMotion.IsChecked == true);
    }
}
