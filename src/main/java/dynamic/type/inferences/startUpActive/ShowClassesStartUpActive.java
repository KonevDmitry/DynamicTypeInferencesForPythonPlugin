package dynamic.type.inferences.startUpActive;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import dynamic.type.inferences.windowOnStartUp.WindowExistingClasses;
import org.jetbrains.annotations.NotNull;

public class ShowClassesStartUpActive implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        WindowExistingClasses windowExistingClasses = new WindowExistingClasses(project);
        windowExistingClasses.showAndGet();
    }

}
