package dynamic.type.references;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class CodeGetter extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        final StringBuilder fullCode = new StringBuilder();
        Project project = Objects.requireNonNull(anActionEvent.getProject());
        Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = anActionEvent.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || psiFile == null) {
            return;
        }
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
                    fullCode.append(innerFile.getText()).append("\n\n");
                    return true;
                }
                return false;
            }

        });

        Messages.showMessageDialog(anActionEvent.getProject(), fullCode.toString(), "PSI Info", null);
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabled(editor != null && psiFile != null);
    }

}
