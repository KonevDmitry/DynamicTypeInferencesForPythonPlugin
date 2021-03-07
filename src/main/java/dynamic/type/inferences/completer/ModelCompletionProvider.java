package dynamic.type.inferences.completer;

import ai.djl.modality.Classifications;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
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
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import dynamic.type.inferences.lookUpElement.ModelLookUpElement;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.ModelNotLoadedNotification;
import dynamic.type.inferences.startUpActive.StartUpActive;
import dynamic.type.inferences.visitors.AllUserFunctionsVisitor;
import dynamic.type.inferences.visitors.VariablesVisitor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ModelCompletionProvider extends CompletionProvider<CompletionParameters> {

    private Project project;
    private Map<String, String> suitableVariables = new HashMap<>();

    private final StringBuilder allFullCode = new StringBuilder();
    private final Map<String, String> allFunctionCodeMap = new HashMap<>();
    private final Map<String, PyTargetExpression> allVariablesMap = new HashMap<>();
    private final TorchBert torchBert = StartUpActive.getTorchBertInstance();
    private final Object sharedObject = new Object();

    private static final String modelPath = PathManager.getConfigPath() + "/eeee.pt";
    private static final Integer modelPriority = Integer.MAX_VALUE - 99;
    private static final Integer elemPriority = Integer.MAX_VALUE - 100;
    private static final Integer numElementsToShow = 5;
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
            EditorFactory
                    .getInstance()
                    .getEventMulticaster()
                    .addDocumentListener(new ModelDocumentListener());

            // Typing case
            getData(project);
            //Calling inside parenthesis: "()"
            if (currentElem.getParent() instanceof PyArgumentList) {
                PyCallExpression callExpression = PsiTreeUtil
                        .getParentOfType(currentElem, PyCallExpression.class);
                PyExpression callee = Objects.requireNonNull(callExpression).getCallee();
                PyGotoDeclarationHandler handler = new PyGotoDeclarationHandler();
                try {
                    // get current function at callee position and check if it is
                    // in user-defined functions
                    PyFunction pyFunction = (PyFunction)
                            handler.getGotoDeclarationTarget(callee, parameters.getEditor());

                    if (pyFunction != null) {
                        String funcFilePath =
                                pyFunction
                                        .getContainingFile()
                                        .getVirtualFile()
                                        .getPath();

                        String key = funcFilePath
                                .concat("/")
                                .concat(pyFunction.getText());

                        //user variables only
                        if (allFunctionCodeMap.containsKey(key)) {
                            //get default completion result list and filter it
                            //taking the variables
                            TypeEvalContext evalContext = TypeEvalContext
                                    .userInitiated(project, pyFunction.getContainingFile());
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
                            if (torchBert.isInitialized()) {
                                try {
                                    List<Classifications.Classification> predicts =
                                            torchBert.predictOne(allFunctionCodeMap.get(key));

                                    predicts.forEach(predict -> {
                                        LookupElement element = modelLookUpElement.createModelElement(predict);
                                        resultSetWithPrefix.addElement(
                                                PrioritizedLookupElement
                                                        .withPriority(element, modelPriority + predict.getProbability()));
                                    });
                                } catch (TranslateException e) {
                                    // never should happen, just in case
                                    ModelNotLoadedNotification notification = new ModelNotLoadedNotification();
                                    Notifications.Bus.notify(notification.createErrorNotification());

                                    BertModelLoader loader = new BertModelLoader(sharedObject);
                                    loader.loadTo(modelPath);
                                    synchronized (sharedObject) {
                                        torchBert.setInitialized(true);
                                    }
                                }
                            }
                            // if model is not initialized then notify about it
                            else {
                                ModelNotLoadedNotification notification = new ModelNotLoadedNotification();
                                Notifications.Bus.notify(notification.createInfoNotification());
                            }

                            //default number of
                            List<LookupElement> suitableLookUpElements = modelLookUpElement
                                    .createTopNSuggestedVariablesTypes(suitableVariables, numElementsToShow);
                            suitableLookUpElements.stream()
                                    .map(suitableElem ->
                                            PrioritizedLookupElement.withPriority(suitableElem, elemPriority)
                                    ).forEach(resultSetWithPrefix::addElement);
                        }
                    }
                } catch (IOException | DbxException ignored) {
                    // There are cases when model cannot process input
                    // (e.g. when commit is too huge [150+ lines of comments])
                    // that is why prediction crashes.
                    // For such cases predictions will not be shown and other processes
                    // will continue their default work.
                }
            }
            result.restartCompletionOnAnyPrefixChange();
            result.runRemainingContributors(parameters, true);
            result.stopHere();
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

    private class ModelDocumentListener implements DocumentListener {
        // Handle each change inside document to keep relevant code for model
        // Done for all actions except writing (typing)
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            getData(project);
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
                AllUserFunctionsVisitor functionsVisitor = new AllUserFunctionsVisitor();
                VariablesVisitor variablesVisitor = new VariablesVisitor();

                innerFile.accept(functionsVisitor);
                innerFile.accept(variablesVisitor);
                allFullCode.append(functionsVisitor.getFullCode());
                allFunctionCodeMap.putAll(functionsVisitor.getFunctionCodeMap());
                allVariablesMap.putAll(variablesVisitor.getVariablesMap());
                return true;
            }
            return false;
        }
    }
}
