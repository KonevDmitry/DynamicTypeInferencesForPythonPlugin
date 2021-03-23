package dynamic.type.inferences.documentationProvider;

import ai.djl.modality.Classifications.Classification;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.ModelNotLoadedNotification;
import dynamic.type.inferences.startUpActive.ModelStartUpActive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_START;
import static com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_END;

public class ModelDocumentationProvider extends PythonDocumentationProvider {
    private final TorchBert torchBert = ModelStartUpActive.getTorchBertInstance();
    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);
    private final DocumentationProvider provider = new PythonDocumentationProvider();

    private static final String MODEL_PATH = PathManager.getConfigPath() + "/eeee.pt";

    //mouse move
    @Override
    public String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        String defaultString = provider.generateHoverDoc(element, originalElement);
        return addInfoWithPredictions(element, defaultString);
    }

    // ctrl+ mouse move
    @Override
    public String getQuickNavigateInfo(PsiElement element, @NotNull PsiElement originalElement) {
        String defaultString = provider.getQuickNavigateInfo(element, originalElement);
        return addInfoWithPredictions(element, defaultString);
    }

    //ctrl+q
    @Override
    public String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        String defaultString = provider.generateDoc(element, originalElement);
        return addInfoWithPredictions(element, defaultString);
    }

    private String addInfoWithPredictions(PsiElement element, String defaultString) {
        ModelNotLoadedNotification notification = new ModelNotLoadedNotification();
        if (torchBert.isInitialized()) {
            if (element instanceof PyFunction) {
                if (element.getTextLength() <= 512) {
                    try {
                        defaultString = defaultString != null ? defaultString : "";
                        List<Classification> predicts = torchBert.predictOne(element.getText());
                        return getBeautifulPredictions(defaultString, predicts);
                    } catch (TranslateException e) {
                        // never should happen in normal situation, just in case
                        try {
                            Notifications.Bus.notify(notification.createErrorNotification());
                            loader.loadTo(MODEL_PATH);
                            synchronized (sharedObject) {
                                torchBert.setInitialized(true);
                            }
                        } catch (IOException | DbxException ignored) {
                        }
                    }
                    return defaultString;
                } else
                    Notifications.Bus
                            .notify(notification.create512Notification(
                                    ((PyFunctionImpl) element).getName()));
            } else
                return defaultString;
        } else
            Notifications.Bus.notify(notification.createNotLoadedNotification());
        return defaultString;
    }

    private String getBeautifulPredictions(String defaultString, List<Classification> predicts) {
        String modelPredicts = "";
        for (int i = 1; i <= predicts.size(); i++) {
            modelPredicts = modelPredicts
                    .concat(i + ") " + predicts.get(i-1).getClassName())
                    .concat(" -><i>")
                    .concat(String.valueOf(predicts.get(i-1).getProbability()))
                    .concat("</i><br/>");
        }
        return defaultString
                .concat(DEFINITION_START)
                .concat("<br/><b>VaDima predictions:</b><br/>")
                .concat(modelPredicts)
                .concat(DEFINITION_END);
    }
}
