package dynamic.type.inferences.documentationProvider;

import ai.djl.modality.Classifications.Classification;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.notification.Notifications;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyFunction;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.VaDimaNotification;
import dynamic.type.inferences.startUpActive.ModelStartUpActive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;


public class ModelDocumentationProvider extends PythonDocumentationProvider {

    private final TorchBert torchBertInstance = ModelStartUpActive.getTorchBertInstance();
    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);
    private final DocumentationProvider provider = new PythonDocumentationProvider();

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
        VaDimaNotification notification = new VaDimaNotification();
        if (torchBertInstance.isInitialized()) {
            if (element instanceof PyFunction) {
                String elementText = element.getText();
                elementText = elementText.substring(0, Math.min(element.getTextLength(),
                        GlobalProjectInstances.BERT_LIMITATION));
                try {
                    // add predictions to out
                    defaultString = defaultString != null ? defaultString : "";
                    List<Classification> predicts = torchBertInstance.predictOne(elementText);
                    return getBeautifulPredictions(defaultString, predicts);
                } catch (TranslateException e) {
                    // never should happen in normal situation, just in case
                    try {
                        Notifications.Bus.notify(notification.createErrorNotification());
                        loader.loadTo(GlobalProjectInstances.MODEL_PATH);
                        synchronized (sharedObject) {
                            torchBertInstance.setInitialized(true);
                        }
                    } catch (IOException | DbxException ignored) {
                    }
                }
            }
            return defaultString;
        } else
            Notifications.Bus.notify(notification.createNotLoadedNotification());
        return defaultString;
    }

    private String getBeautifulPredictions(String defaultString, List<Classification> predicts) {
        String modelPredicts = "";
        for (int i = 1; i <= predicts.size(); i++) {
            modelPredicts = modelPredicts
                    .concat(i + ") " + predicts.get(i - 1).getClassName())
                    .concat(" -><i>")
                    .concat(String.valueOf(predicts.get(i - 1).getProbability()))
                    .concat("</i><br/>");
        }
        return defaultString
                .concat(DocumentationMarkup.DEFINITION_START)
                .concat("<br/><b>VaDima predictions:</b><br/>")
                .concat(modelPredicts)
                .concat(DocumentationMarkup.DEFINITION_END);
    }
}
