package dynamic.type.inferences.documentationProvider;

import ai.djl.modality.Classifications.Classification;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.notification.Notifications;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.documentation.PyDocumentationBuilder;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.VaDimaNotification;
import dynamic.type.inferences.startUpActive.ModelStartUpActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;


public class ModelDocumentationProvider implements DocumentationProvider {

    private final TorchBert torchBertInstance = ModelStartUpActivity.getTorchBertInstance();
    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);
    private final DocumentationProvider provider = new PythonDocumentationProvider();

    private static final String NEW_LINE = "<br/>";
    private static final String BOLD_START = "<b>";
    private static final String BOLD_END = "</b>";
    private static final String OPEN_BRACKET = "(";
    private static final String CLOSE_BRACKET = ")";
    private static final String SPACE_DEF_SPACE = " def ";

    //mouse move
    @Override
    public String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        PyDocumentationBuilder builder = new PyDocumentationBuilder(element, originalElement);
        return addInfoWithPredictions(element, builder.build());
    }

    // ctrl+ hover move
    @Override
    public String getQuickNavigateInfo(PsiElement element, @NotNull PsiElement originalElement) {
        PyDocumentationBuilder builder = new PyDocumentationBuilder(element, originalElement);
        return addInfoWithPredictions(element, builder.build());
    }

    //ctrl+q
    @Override
    public String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
        PyDocumentationBuilder builder = new PyDocumentationBuilder(element, originalElement);
        return addInfoWithPredictions(element, builder.build());
    }

    private String addInfoWithPredictions(PsiElement element, String defaultString) {
        VaDimaNotification notification = new VaDimaNotification();
        if (torchBertInstance.isInitialized()) {
            if (element instanceof PyFunction) {
                String funcName = ((PyFunctionImpl) element).getName();
                try {
                    // add predictions to out
                    defaultString = defaultString != null ? defaultString : "";
                    List<Classification> predicts = torchBertInstance.predictOne((PyFunction) element);
                    if (predicts != null) {
                        return getBeautifulPredictions(defaultString, funcName, predicts);
                    } else
                        return defaultString;
                } catch (TranslateException e) {
                    // never should happen in normal situation, just in case
                    try {
                        Notifications.Bus.notify(notification.createErrorNotification());
                        loader.loadTo(GlobalProjectInstances.MODEL_PATH);
                        synchronized (sharedObject) {
                            torchBertInstance.setInitialized(true);
                        }
                    } catch (IOException | DbxException ignored) {
                        return defaultString;
                    }
                }
            }
            return defaultString;
        } else
            Notifications.Bus.notify(notification.createNotLoadedNotification());
        return defaultString;
    }

    private String getBeautifulPredictions(String defaultString, String funcName, List<Classification> predicts) {
        StringBuilder modelPredicts = new StringBuilder();
        for (int i = 1; i <= predicts.size(); i++) {
            long startTime = System.currentTimeMillis();
            String predictName = predicts.get(i - 1).getClassName();
            modelPredicts
                    .append(i)
                    .append(CLOSE_BRACKET)
                    .append(SPACE_DEF_SPACE)
                    .append(BOLD_START)
                    .append(funcName)
                    .append(BOLD_END)
                    .append(OPEN_BRACKET)
                    .append(predictName)
                    .append(CLOSE_BRACKET)
                    .append(NEW_LINE);
            long endTime = System.currentTimeMillis();
            System.out.println("predict end "+(endTime - startTime));
        }

        long startTime = System.currentTimeMillis();
        String allInfo = DocumentationMarkup.DEFINITION_START +
                NEW_LINE +
                BOLD_START +
                "VaDima predictions:" +
                BOLD_END +
                NEW_LINE +
                modelPredicts +
                DocumentationMarkup.DEFINITION_END;
        long endTime = System.currentTimeMillis();
        System.out.println("all info end "+(endTime - startTime));
        return defaultString.concat(allInfo);
    }
}
