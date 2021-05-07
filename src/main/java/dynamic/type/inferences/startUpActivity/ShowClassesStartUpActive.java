package dynamic.type.inferences.startUpActivity;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import dynamic.type.inferences.notification.settings.ModelApplicationSettingsComponent;
import dynamic.type.inferences.startUpActivity.windowOnStartUp.ModelDoNotShowOption;
import dynamic.type.inferences.startUpActivity.windowOnStartUp.WindowExistingClasses;
import org.jetbrains.annotations.NotNull;

/**
 * Optional activity for a user. If the user selects an option
 * "show classes at PyCharm start", then list of all recognizable types will be shown.
 * Settings, where this option is declared, can be found in {@link ModelApplicationSettingsComponent}
 */
public class ShowClassesStartUpActive implements StartupActivity {

    private final ModelDoNotShowOption modelDoNotShowOption = ModelDoNotShowOption.getInstance();

    /**
     * Activity at PyCharm start
     *
     * @param project is the current project
     */
    @Override
    public void runActivity(@NotNull Project project) {
        ModelDoNotShowOption.VaDimaState vaDimaState = modelDoNotShowOption.getState();
//        Check if option was selected
        if (vaDimaState.toBeShown) {
            WindowExistingClasses windowExistingClasses = new WindowExistingClasses(project);
            windowExistingClasses.show();
        }
    }
}
