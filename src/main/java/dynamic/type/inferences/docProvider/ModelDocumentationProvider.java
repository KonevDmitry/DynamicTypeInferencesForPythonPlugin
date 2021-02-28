package dynamic.type.inferences.docProvider;

import ai.djl.modality.Classifications.Classification;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyFunction;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.ModelNotLoadedNotification;
import dynamic.type.inferences.startUpActive.StartUpActive;
import dynamic.type.inferences.visitors.AllUserFunctionsVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ModelDocumentationProvider extends PythonDocumentationProvider {
    private final TorchBert torchBert = StartUpActive.getTorchBertInstance();
    private final AllUserFunctionsVisitor visitor = new AllUserFunctionsVisitor();
    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);
    private static final String modelPath = PathManager.getConfigPath() + "/eeee.pt";

    @Override
    public String getQuickNavigateInfo(PsiElement element, @NotNull PsiElement originalElement) {
        String defaultString = Objects.requireNonNull(super.getQuickNavigateInfo(element, originalElement));
        PsiFile file = element.getContainingFile();
        file.accept(visitor);
        if (torchBert.isInitialized() &&
                element instanceof PyFunction) {
            try {
                String key = Objects.requireNonNull(
                        element
                                .getContainingFile()
                                .getVirtualFile()
                                .getCanonicalPath())
                        .concat("/")
                        .concat(element.getText());
                if (visitor.getFunctionCodeMap().containsKey(key)) {
                    List<Classification> predicts = torchBert.predictOne(element.getText());
                    return getBeautifulPredictions(defaultString, predicts);
                }
            } catch (TranslateException ignored) {
                return defaultString;
            }
        }
        return defaultString;
    }

    @Override
    public String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        try {
            if (torchBert.isInitialized()) {
                if (element instanceof PyFunction) {
                    String defaultString = super.generateDoc(element, originalElement);
                    PyFunction pyFunction = (PyFunction) element;
                    String codeText = pyFunction.getText();
                    List<Classification> predicts = torchBert.predictOne(codeText);
                    String predictions = getBeautifulPredictions("", predicts);
                    if (defaultString != null)
                        return defaultString.concat(predictions);
                    else
                        return defaultString;
                } else
                    return super.generateDoc(element, originalElement);
            } else {
                ModelNotLoadedNotification notification = new ModelNotLoadedNotification();
                Notifications.Bus.notify(notification.createInfoNotification());
                return super.generateDoc(element, originalElement);
            }
        } catch (TranslateException e) {
            // never should happen, just in case
            try {
                loader.loadTo(modelPath);
                synchronized (sharedObject) {
                    torchBert.setInitialized(true);
                }
            } catch (IOException | DbxException ignored) {
                ModelNotLoadedNotification notification = new ModelNotLoadedNotification();
                Notifications.Bus.notify(notification.createErrorNotification());
            }
        }
        return "";
    }

    private String getBeautifulPredictions(String defaultString, List<Classification> predicts) {
        String modelPredicts = "";
        for (Classification predict : predicts) {
            modelPredicts = modelPredicts.concat(predict.getClassName())
                    .concat(" -> <i>")
                    .concat(String.valueOf(predict.getProbability()))
                    .concat("</i><br/>");
        }
        return defaultString
                .concat("<br/><br/><b>VaDima predictions:</b><br/>\b")
                .concat(modelPredicts);
    }
}
