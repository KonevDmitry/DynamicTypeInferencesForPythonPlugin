package dynamic.type.inferences.completer;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
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
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.types.PyType;
import dynamic.type.inferences.model.runner.TorchBert;
import dynamic.type.inferences.startUpActive.StartUpActive;
import dynamic.type.inferences.visitors.AllUserFunctionsVisitor;
import dynamic.type.inferences.visitors.VariablesVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public class PyVarsForFuncCompleter extends CompletionContributor {
    private final StringBuilder allFullCode = new StringBuilder();
    private final Map<String, String> allFunctionCodeMap = new HashMap<>();
    private final Map<String, PyTargetExpression> allVariablesMap = new HashMap<>();
    private final TorchBert torchBert = StartUpActive.getTorchBertInstance();

    List<String> blackList = new ArrayList<String>() {{
        add("venv");
        add("idea");
    }};

    public PyVarsForFuncCompleter() {
        extend(CompletionType.BASIC,
                psiElement()
                        .inFile(psiFile()
                                .withFileType(instanceOf(PythonFileType.class))),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        FileType fileType = parameters.getOriginalFile().getFileType();
                        if (!(fileType instanceof PythonFileType)) {
                            return;
                        }
                        CaretModel caret = parameters.getEditor().getCaretModel();
                        PsiElement currentElem = parameters.getOriginalFile().findElementAt(caret.getOffset());
                        Project project = Objects.requireNonNull(currentElem).getProject();
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
                        //Calling inside parenthesis "()"
                        if (currentElem.getParent() instanceof PyArgumentList) {
                            PyCallExpression callExpression = PsiTreeUtil.getParentOfType(currentElem, PyCallExpression.class);
                            PyExpression callee = Objects.requireNonNull(callExpression).getCallee();

                            String funcName = Objects.requireNonNull(((PyReferenceExpressionImpl)
                                    Objects.requireNonNull(callee)).getReferencedName());

                            String funcFilePath = Objects.requireNonNull(
                                    callee
                                            .getContainingFile()
                                            .getVirtualFile()
                                            .getCanonicalPath());

                            String key = funcFilePath
                                    .concat("/")
                                    .concat(funcName);

                            if (allFunctionCodeMap.containsKey(key)) {
                                System.out.println(torchBert.isInitialized());
                                String prefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters);
                                CompletionResultSet resultSetWithPrefix = result.withPrefixMatcher(prefix);
                                if (torchBert.isInitialized()) {
                                    //TODO: сделать вывод с 5 предсказаниями
                                    resultSetWithPrefix.addElement(PrioritizedLookupElement
                                            .withPriority(LookupElementBuilder.create("AAAAAAAAAAAAA"), 10000));
                                } else {
                                    //TODO: сделать красивый вывод (warning), что VaDima model not loaded или игнорировать
                                    resultSetWithPrefix.addElement(PrioritizedLookupElement
                                            .withPriority(LookupElementBuilder.create("VaDima model not loaded"), 10000));
                                }
                            }
                        }
                        result.runRemainingContributors(parameters, true);
                        result.stopHere();
                    }
                });
    }

    private void getData(Project project) {
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

    @Override
    public void fillCompletionVariants(@NotNull final CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);
    }
}
