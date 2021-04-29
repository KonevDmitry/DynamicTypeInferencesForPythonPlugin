package dynamic.type.inferences.completer;

import ai.djl.modality.Classifications.Classification;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
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
import com.jetbrains.python.psi.types.TypeEvalContext;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.lookUpElement.ModelLookUpElement;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.notification.VaDimaNotification;
import dynamic.type.inferences.startUpActive.ModelStartUpActivity;
import dynamic.type.inferences.visitors.VariablesVisitor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ModelCompletionProvider extends CompletionProvider<CompletionParameters> {

    private Project project;

    private final Map<String, PyElement> allVariablesMap = new HashMap<>();
    private final TorchBert torchBert = ModelStartUpActivity.getTorchBertInstance();
    private final Object sharedObject = new Object();

    private static final List<String> BLACK_LIST = new ArrayList<String>() {{
        add("venv");
        add("idea");
    }};

    private static final Integer ELEM_PRIORITY = Integer.MAX_VALUE;

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
//        if not python file do nothing
        FileType fileType = parameters.getOriginalFile().getFileType();
        if (!(fileType instanceof PythonFileType)) {
            return;
        }
        CaretModel caret = parameters.getEditor().getCaretModel();
        PsiElement currentElem = parameters.getOriginalFile().findElementAt(caret.getOffset());

//         case for last line inside file (it is null when the user is typing here)
//         needs to be handled, because information about functions is collected for the whole project
        if (currentElem != null) {
            project = currentElem.getProject();
            getData(project);
//            Calling inside parenthesis: "()"
            if (currentElem.getParent() instanceof PyArgumentList) {
                PyCallExpression callExpression = PsiTreeUtil.getParentOfType(currentElem, PyCallExpression.class);
                PyExpression callee = Objects.requireNonNull(callExpression).getCallee();
                PyGotoDeclarationHandler handler = new PyGotoDeclarationHandler();
//                 get current function at callee position and check if it is
//                 in user-defined scope
                PsiElement goToTarget = handler.getGotoDeclarationTarget(callee, parameters.getEditor());
//                 There are cases when user can call variable and function the same.
//                 For such cases PyCharm returns only first occurrences - variable what causes errors
//                 so, user should firstly refactor his code (rename function/variable)
                VaDimaNotification notification = new VaDimaNotification();
                if (torchBert.isInitialized()) {
                    if (goToTarget instanceof PyFunction) {
                        PyFunction pyFunction = (PyFunction) goToTarget;
//                      This comment is for those who is interested in this work and wants to make plugin better:
//                      For better work results of model prediction can be written with Indexer.
//                      Indexer can be more useful in common case: when the index is being processed, you can extend
//                      FileBasedIndexExtension, where you can store model predictions
//
//                      I did not do it because I need to check inference time for my thesis work
//                      and this is impossible if model will predict everything on index step (not real time, actually...)
//                      but anyway, each indexer makes work faster (this is get text issue)
//                      For anyone interested, here is the link, where Semyon Proshev answered me:
//                      https://intellij-support.jetbrains.com/hc/en-us/community/posts/360010578580-How-can-I-get-source-code-of-imported-libraries-functions-
//
//                      But there is a possibility that indexing will take too much time
//                      (just think only about ML libraries like torch, tf and others, they have thousands
//                      of methods).
//                      So, maybe now is implemented the best possible option.
//                      Moreover, we tried implementing it and ran into a problem:
//                      All prediction are remembered only on index-step. So, if user changes the behaviour
//                      of HIS/HER function, model prediction will not change, because
//                      it will take old version of function (from index step)
//                      Honestly, we didn't find a way out of this problem and decided to do real-time getText().
//                      get function call from everywhere (not only user defined functions)
//
//                      UPD from 29.04.2021 - we checked how long indexer works. Without libraries (only built-in)
//                      indexer works for ~0,5 hours. With installed tf - ... More than 1.5 hours))
//                      My laptop has 8 cores and ~40 gb of free space and it actually died...

//                      I sincerely wish health and patience to the one who will make the indexer.

/**
*                      As for our indexer - you can find developments in {@link dynamic.type.inferences.indexer}
**/
                        PsiElement position = parameters.getPosition();
                        PyCallExpression call = PsiTreeUtil.getParentOfType(position, PyCallExpression.class);
                        TypeEvalContext evalContext = TypeEvalContext
                                .userInitiated(project, pyFunction.getContainingFile());
                        PyResolveContext resolveContext = PyResolveContext
                                .defaultContext().withTypeEvalContext(evalContext);
//                         check if everything is ok
//                         (there are cases when callexpression is not null,
//                         but multiResolveCallee returns null)
                        if (call != null) {
                            List<PyCallableType> callableTypes = call.multiResolveCallee(resolveContext);
                            PyCallableType callableItem = null;
                            if (callableTypes.size() > 0)
                                callableItem = callableTypes.get(0);
                            if (callableItem != null) {
                                PyCallable callable = callableItem.getCallable();
                                if (callable != null) {
//                                    get default completion result list and filter it
//                                    taking the variables, pass results to show in type hint
                                    Map<String, String> suitableVariables = result.runRemainingContributors(parameters, true)
                                            .stream()
                                            .filter(elem -> {
                                                        PsiElement psiElement = elem
                                                                .getLookupElement()
                                                                .getPsiElement();
                                                        if (psiElement instanceof PyTargetExpression
                                                                || psiElement instanceof PyNamedParameter) {
//                                                             we have to store a list of variables because
//                                                             default contributors provide everything
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
                                    String prefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters);
                                    CompletionResultSet resultSetWithPrefix = result.withPrefixMatcher(prefix);
                                    ModelLookUpElement modelLookUpElement = new ModelLookUpElement();
                                    try {
                                        List<Classification> predicts = torchBert.predictOne(pyFunction);
                                        PyArgumentList argumentList = call.getArgumentList();
                                        if (predicts != null && argumentList != null) {
                                            int argPosition = argumentList.getArguments().length - 1;
                                            List<String> top5Types = new ArrayList<>();
                                            predicts
                                                    .forEach(elem ->
                                                    {
//                                                         for documentation providing all predictions were grouped as strings
//                                                         Each of them looks like "top1, top2, top3, top4, top5" for each arg
//                                                         We define current cursor position and take top_i element
                                                        String topIPrediction = elem
                                                                .getClassName().split(",")[argPosition];
                                                        top5Types.add(topIPrediction);
                                                    });
                                            List<LookupElement> suitableLookUpElements = modelLookUpElement
                                                    .createTopNSuggestedVariablesTypes(suitableVariables, top5Types);
                                            suitableLookUpElements.stream()
                                                    .map(suitableElem ->
                                                            PrioritizedLookupElement.withPriority(suitableElem, ELEM_PRIORITY))
                                                    .forEach(resultSetWithPrefix::addElement);
                                        }
                                    } catch (TranslateException e) {
                                        notification.createErrorNotification().notify(project);
                                        try {
                                            BertModelLoader loader = new BertModelLoader(sharedObject);
                                            loader.loadTo(GlobalProjectInstances.MODEL_PATH);
                                            synchronized (sharedObject) {
                                                torchBert.setInitialized(true);
                                            }
                                        } catch (IOException | DbxException ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
//                 if model is not initialized then notify about it
                else {
                    result.runRemainingContributors(parameters, true);
                    notification.createNotLoadedNotification().notify(project);
                }
                result.restartCompletionOnAnyPrefixChange();
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
                        .filter(child -> BLACK_LIST
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
