package dynamic.type.inferences.handlers;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyTargetExpression;
import dynamic.type.inferences.visitors.AllUserFunctionsVisitor;
import dynamic.type.inferences.visitors.VariablesVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.List;

public class KeyHandler extends TypedHandlerDelegate {
    private final StringBuilder allFullCode = new StringBuilder();
    private final Map<String, String> allFunctionCodeMap = new HashMap<>();
    private final Map<String, PyTargetExpression> allVariablesMap = new HashMap<>();

    public StringBuilder getAllFullCode() {
        return allFullCode;
    }

    public Map<String, String> getAllFunctionCodeMap() {
        return allFunctionCodeMap;
    }

    public Map<String, PyTargetExpression> getAllVariablesMap() {
        return allVariablesMap;
    }

    public StringBuilder getData(Project project, PsiFile psiFile) {

        List<String> blackList = new ArrayList<String>() {{
            add("venv");
            add("idea");
        }};

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

                    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                    Document docFile = documentManager.getDocument(psiFile);
                    documentManager.commitDocument(Objects.requireNonNull(docFile));
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
        return allFullCode;
    }

    @NotNull
    @Override
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor,
                            @NotNull PsiFile psiFile) {
        StringBuilder fullFunctionCode = getData(project, psiFile);
        System.out.println(fullFunctionCode);

        return Result.CONTINUE;
    }


}