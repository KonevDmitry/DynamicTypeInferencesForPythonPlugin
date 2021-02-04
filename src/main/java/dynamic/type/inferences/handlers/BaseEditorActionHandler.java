package dynamic.type.inferences.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseEditorActionHandler extends EditorWriteActionHandler {
    final private BaseHandler baseHandler;
    final private EditorActionHandler myEditorActionHandler;
    final private KeyHandler keyHandler = new KeyHandler();

    public BaseEditorActionHandler(EditorActionHandler editorActionHandler) {
        super(true);
        baseHandler = new BaseHandler();
        myEditorActionHandler = editorActionHandler;

    }

    @Override
    public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        baseHandler.execute(
                editor,
                () -> myEditorActionHandler.execute(editor, caret, dataContext)
        );
    }

}
