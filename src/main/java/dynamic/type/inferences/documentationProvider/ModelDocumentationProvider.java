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
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.VaDimaNotification;
import dynamic.type.inferences.startUpActivity.ModelStartUpActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;


/**
 * Class extending base DocumentationProvider. Extends mouse moving, ctrl+hover and ctrl+q actions
 */
public class ModelDocumentationProvider implements DocumentationProvider {

    private final TorchBert torchBertInstance = ModelStartUpActivity.getTorchBertInstance();
    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);
    private final DocumentationProvider provider = new PythonDocumentationProvider();

    /**
     * Method extending mouse moving navigation into function, where
     *
     * @param element         is the current element (navigable)
     * @param originalElement is the original element
     * @return string with predictions for functions and default for other causes
     */
    @Override
    public String generateHoverDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
//        Super method
        String defaultString = provider.generateHoverDoc(element, originalElement);
//        If current element is function -> predict, otherwise return default
//        Default string may be null, handle that
        if (element instanceof PyFunction) {
            if (defaultString != null) {
                return defaultString.concat(addInfoWithPredictions(element));
            } else
                return addInfoWithPredictions(element);
        }
        return defaultString;
    }

    /**
     * Method extending ctrl+q action function, where
     *
     * @param element         is the current element (navigable)
     * @param originalElement is the original element
     * @return string with predictions for functions and default for other causes
     */
    @Override
    public String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
//        Super method
        String defaultString = provider.generateDoc(element, originalElement);
//        If current element is function -> predict, otherwise return default
//        Default string may be null, handle that
        if (element instanceof PyFunction) {
            if (defaultString != null) {
                return defaultString.concat(addInfoWithPredictions(element));
            } else
                return addInfoWithPredictions(element);
        }
        return defaultString;
    }

    /**
     * Method adding predictions to default string provided by PyCharm, where
     *
     * @param element is the function for prediction
     * @return string with top-5 predictions for each variable
     */
    private String addInfoWithPredictions(PsiElement element) {
//        Create notification
        VaDimaNotification notification = new VaDimaNotification();
        if (torchBertInstance.isInitialized()) {
            if (element instanceof PyFunction) {
                String funcName = ((PyFunctionImpl) element).getName();
                try {
//                     Add predictions to output
                    List<Classification> predicts = torchBertInstance.predictOne((PyFunction) element);
                    if (predicts != null) {
                        return getBeautifulPredictions(funcName, predicts);
                    }
                } catch (TranslateException e) {
                    // Never should happen in normal situation, just in case
                    try {
                        Notifications.Bus.notify(notification.createErrorNotification());
                        loader.loadTo(GlobalProjectInstances.MODEL_PATH);
                        synchronized (sharedObject) {
                            torchBertInstance.setInitialized(true);
                        }
                    } catch (IOException | DbxException ignored) {
//                        In case if something happened -> return empty line
                        return "";
                    }
                }
            }
        } else
//            Otherwise notify about not loaded model and return empty line
            Notifications.Bus.notify(notification.createNotLoadedNotification());
        return "";
    }

    /**
     * Function for beautiful representation of model predictions, where
     *
     * @param funcName is a function name
     * @param predicts all predictions
     * @return beautiful representation
     */
    private String getBeautifulPredictions(String funcName, List<Classification> predicts) {
        StringBuilder modelPredicts = new StringBuilder();
        for (int i = 1; i <= predicts.size(); i++) {
//            For each parameter create beautiful line
            String predictName = predicts.get(i - 1).getClassName();
            modelPredicts
                    .append(i)
                    .append(GlobalProjectInstances.CLOSE_BRACKET)
                    .append(GlobalProjectInstances.SPACE_DEF_SPACE)
                    .append(GlobalProjectInstances.BOLD_START)
                    .append(funcName)
                    .append(GlobalProjectInstances.BOLD_END)
                    .append(GlobalProjectInstances.OPEN_BRACKET)
                    .append(predictName)
                    .append(GlobalProjectInstances.CLOSE_BRACKET)
                    .append(GlobalProjectInstances.NEW_LINE);
        }

        return DocumentationMarkup.DEFINITION_START
                .concat(GlobalProjectInstances.NEW_LINE)
                .concat(GlobalProjectInstances.BOLD_START)
                .concat("VaDima predictions:")
                .concat(GlobalProjectInstances.BOLD_END)
                .concat(GlobalProjectInstances.NEW_LINE)
                .concat(String.valueOf(modelPredicts))
                .concat(DocumentationMarkup.DEFINITION_END);
    }
}
