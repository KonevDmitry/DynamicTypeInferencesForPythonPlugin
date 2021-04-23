package dynamic.type.inferences.startUpActive;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import com.dropbox.core.DbxException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import dynamic.type.inferences.model.runner.TorchBert;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;

public class ModelStartUpActivity implements StartupActivity {

    private static final TorchBert torchBertInstance = new TorchBert();

    public static TorchBert getTorchBertInstance() {
        return torchBertInstance;
    }

    public ModelStartUpActivity() {
    }

    @Override
    public void runActivity(@NotNull Project project) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "VaDima loader") {
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(true);
                        indicator.setText("Loading model. Please wait...");
                        try {
                            torchBertInstance.modelInit();
                            indicator.stop();
                        } catch (IOException | MalformedModelException | ModelNotFoundException |
                                URISyntaxException | DbxException | InterruptedException ignored) {
                        }
                    }
                });
    }
}
