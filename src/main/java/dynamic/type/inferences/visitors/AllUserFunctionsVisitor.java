package dynamic.type.inferences.visitors;

import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implemented only for used-defined functions, because fetching library code periodically takes
 * too much time. The only thing that works fast is ctrl+mouse action (getting documentation).
 * So, predictions are available via fast documentation for all functions.
 */
//TODO: delete when it will be clear that it is not needed
public class AllUserFunctionsVisitor extends PyRecursiveElementVisitor {
    private final StringBuilder fullCode = new StringBuilder();
    private final Map<String, String> functionCodeMap = new HashMap<>();

    public AllUserFunctionsVisitor() {
    }

    public StringBuilder getFullCode() {
        return fullCode;
    }

    public Map<String, String> getFunctionCodeMap() {
        return functionCodeMap;
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
        String nodeText = node.getText();
        fullCode.append(nodeText);
        if (node.getName() != null) {
            String key = Objects.requireNonNull(node
                    .getContainingFile()
                    .getVirtualFile()
                    .getCanonicalPath())
                    .concat("/")
                    .concat(nodeText);
            functionCodeMap.put(key, nodeText);
            super.visitPyFunction(node);
        }
    }
}
