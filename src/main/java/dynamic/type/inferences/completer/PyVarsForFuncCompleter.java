package dynamic.type.inferences.completer;

import ai.djl.modality.Classifications.Classification;
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyGotoDeclarationHandler;
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
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public class PyVarsForFuncCompleter extends CompletionContributor {
    private final StringBuilder allFullCode = new StringBuilder();
    private final Map<String, String> allFunctionCodeMap = new HashMap<>();
    private final Map<String, PyTargetExpression> allVariablesMap = new HashMap<>();
    private final TorchBert torchBert = StartUpActive.getTorchBertInstance();
    private final Object sharedObject = new Object();

    private static final String modelPath = PathManager.getConfigPath() + "/eeee.pt";
    private static final Integer priority = Integer.MAX_VALUE - 100;
    List<String> blackList = new ArrayList<String>() {{
        add("venv");
        add("idea");
    }};

    public PyVarsForFuncCompleter() throws NullPointerException {
        extend(CompletionType.BASIC,
                psiElement()
                        .inFile(psiFile()
                                .withFileType(instanceOf(PythonFileType.class))),
                new CompletionProvider<CompletionParameters>() {
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
                            Project project = currentElem.getProject();
                            EditorFactory
                                    .getInstance()
                                    .getEventMulticaster()
                                    .addDocumentListener(new DocumentListener() {
                                        // Handle each change inside document to keep relevant code for model
                                        // Done for all actions except writing (typing)
                                        @Override
                                        public void documentChanged(@NotNull DocumentEvent event) {
                                            getData(project);
                                        }
                                    });
                            // Typing case
                            getData(project);
                            //Calling inside parenthesis: "()"
                            if (currentElem.getParent() instanceof PyArgumentList) {
                                PyCallExpression callExpression = PsiTreeUtil.getParentOfType(currentElem, PyCallExpression.class);
                                PyExpression callee = Objects.requireNonNull(callExpression).getCallee();
                                PyGotoDeclarationHandler handler = new PyGotoDeclarationHandler();

                                try {
                                    // get current function at callee position and check if it is
                                    // in user-defined functions
                                    PyFunction pyFunction = (PyFunction)
                                            handler.getGotoDeclarationTarget(callee, parameters.getEditor());

                                    String funcFilePath =
                                            pyFunction
                                                    .getContainingFile()
                                                    .getVirtualFile()
                                                    .getCanonicalPath();

                                    String key = funcFilePath
                                            .concat("/")
                                            .concat(pyFunction.getText());

                                    //user variables only
                                    if (allFunctionCodeMap.containsKey(key)) {
                                        //get default completion result list and filter it
                                        //taking the variables
                                        List<String> suitableVariables = result.runRemainingContributors(parameters, false)
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
                                                .map(elem -> elem.getLookupElement().getLookupString())
                                                .collect(Collectors.toList());

                                        //check case if function is inside class
                                        PyClass pyClass = PsiTreeUtil.getParentOfType(callExpression, PyClass.class);

                                        //get variable like self.<name>
                                        List<String> selfVariables =
                                                PsiTreeUtil
                                                        .findChildrenOfType(pyClass, PyTargetExpression.class)
                                                        .stream()
                                                        .filter(elem -> Objects.requireNonNull(elem.asQualifiedName())
                                                                .getComponents()
                                                                .contains("self"))
                                                        .map(elem -> Objects.requireNonNull(elem.asQualifiedName()).toString())
                                                        .collect(Collectors.toList());
                                        // take variable names from function declaration
                                        PyFunction callableFunction = PsiTreeUtil.getParentOfType(callee, PyFunction.class);
                                        List<String> namedParameters =
                                                PsiTreeUtil
                                                        .findChildrenOfType(callableFunction, PyNamedParameter.class)
                                                        .stream()
                                                        .filter(elem -> {
                                                            String elemName = Objects.requireNonNull(elem.getNameIdentifier()).getText();
                                                            // if function is inside class, then remove self (first argument)
                                                            return !elemName.equals("self");
                                                        })
                                                        .map(elem -> elem.getNameIdentifier().getText())
                                                        .collect(Collectors.toList());

                                        suitableVariables.addAll(namedParameters);
                                        // If call is inside class
                                        if (pyClass != null) {
                                            suitableVariables.addAll(selfVariables);
                                        }
                                        System.out.println(suitableVariables);
                                        String prefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters);
                                        CompletionResultSet resultSetWithPrefix = result.withPrefixMatcher(prefix);
                                        if (torchBert.isInitialized()) {
                                            try {
                                                List<Classification> predicts = torchBert.
                                                        predictOne(allFunctionCodeMap.get(key));
                                                ModelLookUpElement modelLookUpElement = new ModelLookUpElement();
                                                for (Classification predict : predicts) {
                                                    LookupElement element = modelLookUpElement.createElement(predict);
                                                    resultSetWithPrefix.addElement(
                                                            PrioritizedLookupElement
                                                                    .withPriority(element, priority + predict.getProbability()));
                                                }
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
                                        } else {
                                            ModelNotLoadedNotification notification = new ModelNotLoadedNotification();
                                            Notifications.Bus.notify(notification.createInfoNotification());
                                        }
                                    }
                                } catch (IOException | DbxException ignored) {
                                }
                            }
                            result.restartCompletionOnAnyPrefixChange();
                            result.runRemainingContributors(parameters, true);
                            result.stopHere();
                        }
                    }
                });
    }

    private void getData(Project project) {
        if (!project.isDisposed()) {
            ProjectFileIndex.SERVICE.getInstance(project).iterateContent(new ContentIterator() {
                @Override
                public boolean processFile(@NotNull VirtualFile fileInProject) {
                    if (fileInProject.isDirectory()) {
                        Arrays.stream(fileInProject.getChildren())
                                .filter(child -> blackList
                                        .stream()
                                        .noneMatch(entry ->
                                                Objects.requireNonNull(
                                                        child
                                                                .getCanonicalPath())
                                                        .endsWith(entry)))
                                .forEach(this::processFile);
                    } else if (fileInProject.getFileType() instanceof PythonFileType) {
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
            });
        }
    }

    @Override
    public void fillCompletionVariants(@NotNull final CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);
    }
}
