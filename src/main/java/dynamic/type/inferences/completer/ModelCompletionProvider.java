package dynamic.type.inferences.completer;

import ai.djl.modality.Classifications;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyGotoDeclarationHandler;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import dynamic.type.inferences.lookUpElement.ModelLookUpElement;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.ModelNotLoadedNotification;
import dynamic.type.inferences.startUpActive.ModelStartUpActive;
import dynamic.type.inferences.visitors.VariablesVisitor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ModelCompletionProvider extends CompletionProvider<CompletionParameters> {
    private Project project;
    private Map<String, String> suitableVariables = new HashMap<>();

    private final Map<String, PyTargetExpression> allVariablesMap = new HashMap<>();
    private final TorchBert torchBert = ModelStartUpActive.getTorchBertInstance();
    private final Object sharedObject = new Object();

    private static final String modelPath = PathManager.getConfigPath() + "/eeee.pt";
    private static final Integer MODEL_PRIORITY = Integer.MAX_VALUE - 99;
    private static final Integer ELEM_PRIORITY = Integer.MAX_VALUE - 100;
    private static final Integer NUM_ELEMENTS_TO_SHOW = 5;
    private static final List<String> blackList = new ArrayList<String>() {{
        add("venv");
        add("idea");
    }};

    public ModelCompletionProvider() {
    }

    public Map<String, String> getSuitableVariables() {
        return suitableVariables;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        //if not python file do nothing
        FileType fileType = parameters.getOriginalFile().getFileType();
        if (!(fileType instanceof PythonFileType)) {
            return;
        }
        CaretModel caret = parameters.getEditor().getCaretModel();
        PsiElement currentElem = parameters.getOriginalFile().findElementAt(caret.getOffset());

        // case for last line inside file (it is null when the user is typing here)
        // needs to be handled, because information about functions is collected for the whole project
        if (currentElem != null) {
            project = currentElem.getProject();

            // Typing case
            getData(project);
            //Calling inside parenthesis: "()"
            if (currentElem.getParent() instanceof PyArgumentList) {
                PyCallExpression callExpression = PsiTreeUtil
                        .getParentOfType(currentElem, PyCallExpression.class);
                PyExpression callee = Objects.requireNonNull(callExpression).getCallee();
                PyGotoDeclarationHandler handler = new PyGotoDeclarationHandler();
//                try {
                // get current function at callee position and check if it is
                // in user-defined functions
                PsiElement pyFunction = handler.getGotoDeclarationTarget(callee, parameters.getEditor());
                //There are cases when user can call variable and function the same.
                // For such cases PyCharm returns only first occurrences - variable what causes errors
                // so, user should firstly refactor his code (rename function/variable)
                ModelNotLoadedNotification notification = new ModelNotLoadedNotification();
                if (torchBert.isInitialized()) {
                    if (pyFunction instanceof PyFunction) {
                        // get function call from everywhere (not only user defined functions)
                        PsiElement position = parameters.getPosition();
                        PyCallExpression call = PsiTreeUtil.getParentOfType(position, PyCallExpression.class);
                        TypeEvalContext evalContext = TypeEvalContext
                                .userInitiated(project, pyFunction.getContainingFile());
                        PyResolveContext resolveContext = PyResolveContext
                                .defaultContext().withTypeEvalContext(evalContext);

                        // check if everything is ok
                        // (there are cases when callexpression is not null,
                        // but multiResolveCallee returns null)
                        if (call != null) {
                            List<PyCallableType> callableTypes = call.multiResolveCallee(resolveContext);
                            PyCallableType callableItem = null;
                            if (callableTypes.size() > 0)
                                callableItem = callableTypes.get(0);
                            if (callableItem != null) {
                                PyCallable callable = callableItem.getCallable();
                                if (callable != null) {
                                    String functionCode = callableItem.getCallable().getText();
                                    //BERT limitation
                                    if (functionCode.length() <= 512) {
                                        //get default completion result list and filter it
                                        //taking the variables
                                        Map<String, String> suitableVariables = result.runRemainingContributors(parameters, false)
                                                .stream()
                                                .filter(elem -> {
                                                            PsiElement psiElement = elem
                                                                    .getLookupElement()
                                                                    .getPsiElement();
                                                            if (psiElement instanceof PyTargetExpression) {
                                                                String innerKey = VariablesVisitor.generateKeyForNode(psiElement);
                                                                return allVariablesMap.containsKey(innerKey);
                                                            } else
                                                                return false;
                                                        }
                                                )
                                                .map(elem -> elem.getLookupElement().getPsiElement())
                                                .collect(Collectors.toMap(
                                                        VariablesVisitor::generateKeyForNode,
                                                        elem -> PythonDocumentationProvider
                                                                .getTypeHint(evalContext.getType((PyTypedElement) elem), evalContext)
                                                ));

                                        //check case if function is inside class
                                        PyClass pyClass = PsiTreeUtil.getParentOfType(callExpression, PyClass.class);

                                        //get variable like self.<name>
                                        Map<String, String> selfVariables =
                                                PsiTreeUtil
                                                        .findChildrenOfType(pyClass, PyTargetExpression.class)
                                                        .stream()
                                                        .filter(elem -> Objects.requireNonNull(elem.asQualifiedName())
                                                                .getComponents()
                                                                .contains("self"))
                                                        .collect(Collectors.toMap(
                                                                VariablesVisitor::generateKeyForNode,
                                                                elem -> PythonDocumentationProvider
                                                                        .getTypeHint(evalContext.getType(elem), evalContext)
                                                        ));

                                        // take variable names from function declaration
                                        PyFunction callableFunction = PsiTreeUtil.getParentOfType(callee, PyFunction.class);
                                        Map<String, String> namedParameters =
                                                PsiTreeUtil
                                                        .findChildrenOfType(callableFunction, PyNamedParameter.class)
                                                        .stream()
                                                        .filter(elem -> {
                                                            String elemName = Objects.requireNonNull(elem.getNameIdentifier()).getText();
                                                            // if function is inside class, then remove self (first argument)
                                                            return !elemName.equals("self");
                                                        })
                                                        .collect(Collectors.toMap(
                                                                VariablesVisitor::generateKeyForNode,
                                                                // variable type can be defined
                                                                elem -> {
                                                                    PyType argType = elem.getArgumentType(evalContext);
                                                                    if (argType != null) {
                                                                        String argName = argType.getName();
                                                                        return argName != null ? argName : "Any";
                                                                    }
                                                                    return "Any";
                                                                }
                                                        ));

                                        suitableVariables.putAll(namedParameters);
                                        // If call is inside class
                                        if (pyClass != null)
                                            suitableVariables.putAll(selfVariables);

                                        this.suitableVariables = suitableVariables;

                                        String prefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters);
                                        CompletionResultSet resultSetWithPrefix = result.withPrefixMatcher(prefix);
                                        ModelLookUpElement modelLookUpElement = new ModelLookUpElement();
                                        try {
                                            List<Classifications.Classification> predicts =
                                                    torchBert.predictOne(functionCode);

                                            predicts.forEach(predict -> {
                                                LookupElement element = modelLookUpElement.createModelElement(predict);
                                                resultSetWithPrefix.addElement(
                                                        PrioritizedLookupElement
                                                                .withPriority(element, MODEL_PRIORITY + predict.getProbability()));
                                            });
                                        } catch (TranslateException e) {
                                            Notifications.Bus.notify(notification.createErrorNotification());
                                            try {
                                                BertModelLoader loader = new BertModelLoader(sharedObject);
                                                loader.loadTo(modelPath);
                                                synchronized (sharedObject) {
                                                    torchBert.setInitialized(true);
                                                }
                                            } catch (IOException | DbxException ignored) {
                                            }
                                        }
                                        //default number of
                                        List<LookupElement> suitableLookUpElements = modelLookUpElement
                                                .createTopNSuggestedVariablesTypes(suitableVariables, NUM_ELEMENTS_TO_SHOW);
                                        suitableLookUpElements.stream()
                                                .map(suitableElem ->
                                                        PrioritizedLookupElement.withPriority(suitableElem, ELEM_PRIORITY)
                                                ).forEach(resultSetWithPrefix::addElement);
                                    } else
                                        Notifications.Bus.notify(notification.create512Notification(callableItem.getName()));
                                }
                            }
                        }
                    }
                }
                // if model is not initialized then notify about it
                else
                    Notifications.Bus.notify(notification.createNotLoadedNotification());
                result.restartCompletionOnAnyPrefixChange();
                result.runRemainingContributors(parameters, true);
                result.stopHere();
            }
        }
    }

    private void getData(Project project) {
        if (!project.isDisposed()) {
            ProjectFileIndex
                    .SERVICE
                    .getInstance(project)
                    .iterateContent(new ModelContentIterator());
        }
    }

    private class ModelContentIterator implements ContentIterator {
        @Override
        public boolean processFile(@NotNull VirtualFile fileInProject) {
            if (fileInProject.isDirectory()) {
                Arrays.stream(fileInProject.getChildren())
                        .filter(child -> blackList
                                .stream()
                                .noneMatch(entry ->
                                        Objects
                                                .requireNonNull(child.getCanonicalPath())
                                                .endsWith(entry)))
                        .forEach(this::processFile);
            } else if (fileInProject.getFileType() instanceof PythonFileType && project != null) {
                PsiFile innerFile = Objects.requireNonNull(PsiManager.getInstance(project).findFile(fileInProject));
                VariablesVisitor variablesVisitor = new VariablesVisitor();

                innerFile.accept(variablesVisitor);
                allVariablesMap.putAll(variablesVisitor.getVariablesMap());
                return true;
            }
            return false;
        }
    }
}
