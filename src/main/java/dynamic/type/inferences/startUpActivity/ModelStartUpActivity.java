package dynamic.type.inferences.startUpActivity;

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

/**
 * Loads (if needed)a model when project opens. If model is loaded - all other processes are gone.
 */
public class ModelStartUpActivity implements StartupActivity {

    private static final TorchBert torchBertInstance = new TorchBert();

    /**
     * For the whole project is run only one model. As it initializes from here, there is a need
     * to get defined instance of model here.
     *
     * @return instance of torchBert model
     */
    public static TorchBert getTorchBertInstance() {
        return torchBertInstance;
    }

    /**
     * Base empty constructor
     */
    public ModelStartUpActivity() {
    }

    /**
     * Main activity, where model is loaded
     *
     * @param project is a current project
     */
    @Override
    public void runActivity(@NotNull Project project) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "VaDima loader") {
                    public void run(@NotNull ProgressIndicator indicator) {
//                        Model load depends on a speed of a user internet, so indicator is indeterminate
                        indicator.setIndeterminate(true);
                        indicator.setText("Loading model. Please wait...");
                        try {
//                            Load model if it is not loaded, initialize BERT predictor, translator
//                            Briefly - do the whole work for model initialization.
                            torchBertInstance.modelInit();
//                            When everything is done - stop indicator
                            indicator.stop();
//                            All exceptions never should happen and methods for model initialization
//                            contain all comments about each possible exception
                        } catch (IOException | MalformedModelException | ModelNotFoundException |
                                URISyntaxException | DbxException | InterruptedException ignored) {
                        }
                    }
                });
    }
}
