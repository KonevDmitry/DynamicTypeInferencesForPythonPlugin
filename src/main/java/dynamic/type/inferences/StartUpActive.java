package dynamic.type.inferences;

import ai.djl.MalformedModelException;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import dynamic.type.inferences.model.runner.TorchBert;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class StartUpActive extends PreloadingActivity {
    TorchBert torchBert = new TorchBert();

    public TorchBert getTorchBert() {
        return torchBert;
    }

    public StartUpActive() throws IOException, ModelNotFoundException, MalformedModelException, URISyntaxException, DbxException, InterruptedException {
    }

    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
        try {
            torchBert.modelInit();
            ArrayList<Classifications> res;
            res = torchBert.predict(new ArrayList<String>() {{
                add("scascsa");
            }});
            System.out.println(res);
        } catch (ModelNotFoundException | URISyntaxException | DbxException | TranslateException | IOException | MalformedModelException | InterruptedException e) {
            e.printStackTrace();
        }

    }
}

