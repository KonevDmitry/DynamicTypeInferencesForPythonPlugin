package dynamic.type.inferences.handlers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class BaseHandler {
    private KeyHandler keyHandler = new KeyHandler();
    public BaseHandler() {
    }

    public void execute(@NotNull Editor editor, Runnable originalHandler) {
        originalHandler.run();
//        KeymapManager.getInstance().getActiveKeymap()
        Project project = editor.getProject();
        PsiFile psiFile = PsiDocumentManager.getInstance(Objects.requireNonNull(project))
                .getPsiFile(editor.getDocument());

        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document docFile = documentManager.getDocument(Objects.requireNonNull(psiFile));
        documentManager.commitDocument(Objects.requireNonNull(docFile));

    }


}
