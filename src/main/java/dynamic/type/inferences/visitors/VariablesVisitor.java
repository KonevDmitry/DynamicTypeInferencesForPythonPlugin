package dynamic.type.inferences.visitors;

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
        String key = generateKeyForNode(node);
        variablesMap.put(String.valueOf(key), node);
        super.visitPyTargetExpression(node);
    }

    public Map<String, PyTargetExpression> getVariablesMap() {
        return variablesMap;
    }

    public static String generateKeyForNode(PsiElement node) {
        PsiElement temporal = node;
        StringBuilder key = new StringBuilder();
        //get full path until the root directory
        while (!(temporal instanceof PsiDirectoryImpl && temporal.getChildren()[0] instanceof PsiDirectoryImpl)) {
            key.insert(0, "/" + temporal.toString());
            temporal = temporal.getOriginalElement().getParent();
        }
        return String.valueOf(key);
    }
}
