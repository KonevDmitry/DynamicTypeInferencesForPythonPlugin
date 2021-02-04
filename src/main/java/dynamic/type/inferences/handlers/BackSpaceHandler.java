package dynamic.type.inferences.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;

public class BackSpaceHandler extends BaseEditorActionHandler {
    public BackSpaceHandler(EditorActionHandler editorActionHandler) {
        super(editorActionHandler);
    }

    @Override
    public void executeWriteAction(@NotNull Editor editor, Caret caret, DataContext dataContext) {
        super.executeWriteAction(editor, caret, dataContext);
    }

}
