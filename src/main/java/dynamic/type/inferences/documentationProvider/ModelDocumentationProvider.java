package dynamic.type.inferences.documentationProvider;

import ai.djl.modality.Classifications.Classification;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyFunction;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.ModelNotLoadedNotification;
import dynamic.type.inferences.startUpActive.ModelStartUpActive;
import dynamic.type.inferences.visitors.AllUserFunctionsVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ModelDocumentationProvider extends PythonDocumentationProvider {
    private final TorchBert torchBert = ModelStartUpActive.getTorchBertInstance();
    private final AllUserFunctionsVisitor visitor = new AllUserFunctionsVisitor();
    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);
    private static final String modelPath = PathManager.getConfigPath() + "/eeee.pt";
    private final DocumentationProvider provider = new PythonDocumentationProvider();

    // ctrl+ mouse navigation
    @Override
    public String getQuickNavigateInfo(PsiElement element, @NotNull PsiElement originalElement) {
        String defaultString = Objects.requireNonNull(provider.getQuickNavigateInfo(element, originalElement));
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
            } catch (TranslateException e) {
                // Too big input or empty text. Translator goes crazy and crashes everything)))))))))))))
                return defaultString;
            }
        }
        return defaultString;
    }

    //ctrl+q
    @Override
    public String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {

        // Calling provider.generateDoc causes an OSProcessHandler - Synchronous execution under ReadAction
        // Operation is time consuming, so it is getting really impossible to run it.
        // Alternative way to generate is the next:
        //   * DEFINITION_START + definition + DEFINITION_END +
        //   * CONTENT_START + main description + CONTENT_END +
        //   * SECTIONS_START +
        //   *   SECTION_HEADER_START + section name +
        //   *     SECTION_SEPARATOR + "&lt;p&gt;" + section content + SECTION_END +
        //   *   ... +
        //   * SECTIONS_END

        final String defaultString = provider.getQuickNavigateInfo(element, originalElement);
        PsiFile file = element.getContainingFile();
        file.accept(visitor);
        if (torchBert.isInitialized()) {
            if (element instanceof PyFunction) {
                try {
                    PyFunction pyFunction = (PyFunction) element;
                    String codeText = pyFunction.getText();
                    String key = Objects.requireNonNull(
                            element
                                    .getContainingFile()
                                    .getVirtualFile()
                                    .getCanonicalPath())
                            .concat("/")
                            .concat(element.getText());
                    if (visitor.getFunctionCodeMap().containsKey(key)) {
                        List<Classification> predicts = torchBert.predictOne(codeText);
                        String predictions = getBeautifulPredictions("", predicts);
                        if (defaultString != null)
                            return defaultString.concat(predictions);
                        else
                            return "";
                    } else
                        return defaultString;
                } catch (TranslateException e) {
                    // never should happen in normal situation, just in case
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
            } else
                return defaultString;
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
                .concat("<br/><b>VaDima predictions:</b><br/>\b")
                .concat(modelPredicts);
    }
}
