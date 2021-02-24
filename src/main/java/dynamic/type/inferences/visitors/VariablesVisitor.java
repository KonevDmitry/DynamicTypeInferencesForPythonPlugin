package dynamic.type.inferences.visitors;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class VariablesVisitor extends PyRecursiveElementVisitor {
    private final Map<String, PyTargetExpression> variablesMap = new HashMap<>();

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
        PsiElement temporal = node;
        StringBuilder key = new StringBuilder();
        while (!(temporal.getParent() instanceof PsiDirectoryImpl)) {
            if (temporal instanceof PyTargetExpression)
                key.append(temporal.toString());
            temporal = temporal.getParent();
            key.insert(0, "/" + temporal.toString());
        }
        variablesMap.put(String.valueOf(key), node);
        super.visitPyTargetExpression(node);
    }

    public Map<String, PyTargetExpression> getVariablesMap() {
        return variablesMap;
    }
}
