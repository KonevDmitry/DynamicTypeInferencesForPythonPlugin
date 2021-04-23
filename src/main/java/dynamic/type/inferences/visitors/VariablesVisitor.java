package dynamic.type.inferences.visitors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class VariablesVisitor extends PyRecursiveElementVisitor {

    private final Map<String, PyElement> variablesMap = new HashMap<>();

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
        String key = generateKeyForNode(node);
        variablesMap.put(String.valueOf(key), node);
        super.visitPyTargetExpression(node);
    }

    @Override
    public void visitPyNamedParameter(@NotNull PyNamedParameter node) {
        String key = generateKeyForNode(node);
        variablesMap.put(String.valueOf(key), node);
        super.visitPyNamedParameter(node);
    }

    public Map<String, PyElement> getVariablesMap() {
        return variablesMap;
    }

    public static String generateKeyForNode(PsiElement node) {
        PsiElement temporal = node;
        StringBuilder key = new StringBuilder();
        //get full path until the root directory
        while (!(temporal instanceof PsiDirectoryImpl && temporal.getChildren()[0] instanceof PsiDirectoryImpl)) {
            if (temporal instanceof PyNamedParameter)
                key.insert(0, "/"
                        .concat(temporal.toString())
                        .concat("/")
                        .concat(((PyNamedParameter) temporal)
                                .getRepr(true)));
            else if (temporal instanceof PyTargetExpression) {
                key.insert(0, "/" + ((PyTargetExpression) temporal).getName());
            } else
                key.insert(0, "/" + temporal.toString());
            temporal = temporal.getOriginalElement().getParent();
        }
        return String.valueOf(key);
    }
}
