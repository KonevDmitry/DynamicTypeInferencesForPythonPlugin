package dynamic.type.inferences.startUpActive;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import dynamic.type.inferences.windowOnStartUp.ModelDoNotShowOption;
import dynamic.type.inferences.windowOnStartUp.WindowExistingClasses;
import org.jetbrains.annotations.NotNull;

public class ShowClassesStartUpActive implements StartupActivity {

    private final ModelDoNotShowOption modelDoNotShowOption = ModelDoNotShowOption.getInstance();

    @Override
    public void runActivity(@NotNull Project project) {
        ModelDoNotShowOption.VaDimaState vaDimaState = modelDoNotShowOption.getState();
        if (!vaDimaState.toBeShown) {
            WindowExistingClasses windowExistingClasses = new WindowExistingClasses(project);
            windowExistingClasses.show();
        }
    }

}
