package dynamic.type.inferences.startUpActive;

import ai.djl.MalformedModelException;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
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
import java.util.ArrayList;

public class ModelStartUpActive implements StartupActivity {
    private static final TorchBert torchBert = new TorchBert();

    public static TorchBert getTorchBertInstance() {
        return torchBert;
    }

    public ModelStartUpActive() {
    }

//    @Override
//    public void preload(@NotNull ProgressIndicator indicator) {
//        try {
//            indicator.setIndeterminate(true);
//            indicator.start();
//            indicator.setText("Loading model. Please wait...");
//            torchBert.modelInit();
//            indicator.stop();
////            System.out.println("AAAAAAAAAAAAAAAAA");
////            ArrayList<Classifications> res;
////            res = torchBert.predict(new ArrayList<String>() {{
////                add("scascsa");
////            }});
////            System.out.println(res);
//        } catch (ModelNotFoundException | URISyntaxException | DbxException | IOException | MalformedModelException | InterruptedException e) {
//            e.printStackTrace();
//        }
//
//    }

    @Override
    public void runActivity(@NotNull Project project) {
        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "VaDima loader") {
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(true);
                        indicator.setText("Loading model. Please wait...");
                        try {
                            torchBert.modelInit();
                            indicator.stop();
                        } catch (IOException | MalformedModelException | ModelNotFoundException | URISyntaxException | DbxException | InterruptedException ignored) {

                        }
                        //            System.out.println("AAAAAAAAAAAAAAAAA");
                        //            ArrayList<Classifications> res;
                        //            res = torchBert.predict(new ArrayList<String>() {{
                        //                add("scascsa");
                        //            }});
                        //            System.out.println(res);
                    }
                });
    }
}
