package dynamic.type.inferences;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import dynamic.type.inferences.model.runner.TorchBert;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class StartUpActive extends PreloadingActivity {
    TorchBert listener = new TorchBert();

    public StartUpActive() throws IOException, ModelNotFoundException, MalformedModelException {
        listener.modelInit();
//        bert = new TorchBert();
        System.out.println(Arrays.toString(Device.getDevices(10)));
    }

    @Override
    public void preload(@NotNull ProgressIndicator indicator) {
        try {
            ArrayList<Classifications> res = listener.predict(new ArrayList<String>() {{
                add("scascsa");
            }});
            System.out.println(res);
        } catch (IOException | MalformedModelException | ModelNotFoundException | TranslateException e) {
            System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            e.printStackTrace();
        }
    }
}
