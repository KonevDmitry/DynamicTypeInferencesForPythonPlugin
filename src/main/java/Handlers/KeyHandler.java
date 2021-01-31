package Handlers;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class KeyHandler extends TypedHandlerDelegate {

    @NotNull
    @Override
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor,
                                  @NotNull PsiFile psiFile) {

        final Map<String, String> functionCodeMap = new HashMap<>();
        StringBuilder fullCode = new StringBuilder();

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
                } else if (fileInProject.getFileType().getClass().equals(PythonFileType.class)) {
                    PsiFile innerFile = Objects.requireNonNull(PsiManager.getInstance(project).findFile(fileInProject));

                    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                    Document docFile = documentManager.getDocument(psiFile);
                    documentManager.commitDocument(Objects.requireNonNull(docFile));

                    PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
                        @Override
                        public void visitPyFunction(@NotNull PyFunction node) {
                            String nodeText = node.getText().concat("\n\n");
                            fullCode.append(nodeText);
                            if (node.getName() != null) {
                                String key = Objects.requireNonNull(node
                                        .getContainingFile()
                                        .getVirtualFile()
                                        .getCanonicalPath())
                                        .concat(":")
                                        .concat(Objects.requireNonNull(node.getNameNode()).getText());
                                functionCodeMap.put(key, nodeText);
                                super.visitPyFunction(node);
                            }
                        }
                    };
                    innerFile.accept(visitor);
                    return true;
                }
                return false;
            }
        });
        System.out.println(functionCodeMap);
        return Result.CONTINUE;
    }

}