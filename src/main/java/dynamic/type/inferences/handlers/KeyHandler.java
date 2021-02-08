package dynamic.type.inferences.handlers;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import dynamic.type.inferences.model.runner.TorchBert;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.List;

public class KeyHandler extends TypedHandlerDelegate {

    private final Map<String, String> functionCodeMap = new HashMap<>();

    public Map<String, String> getFunctionCodeMap() {
        return functionCodeMap;
    }

    public StringBuilder getAllFunctionCode(Project project, PsiFile psiFile) {
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
                } else if (fileInProject.getFileType() instanceof PythonFileType) {
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
//                        @Override
//                        public void visitElement(PsiElement element){
//                            // вот тут подумать, как доставать все функции, которые не def
////                            MethodCallExpression callExpression = new MethodCallExpression();
//                            //P.S ну рекурсия, да, а чё ещё
//                            for(PsiElement elem: element.getChildren()){
//                                System.out.println(elem.getText()+"sacs"+elem.getNode());
//
//                            }
//                        }
                    };
                    innerFile.accept(visitor);
                    return true;
                }
                return false;
            }
        });
        return fullCode;
    }

    @NotNull
    @Override
    public Result charTyped(char c, @NotNull Project project, @NotNull Editor editor,
                            @NotNull PsiFile psiFile) {
        StringBuilder fullFunctionCode = getAllFunctionCode(project, psiFile);
        System.out.println(fullFunctionCode);

        return Result.CONTINUE;
    }


}