package dynamic.type.inferences.visitors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyTargetExpression;
import dynamic.type.inferences.completer.ModelCompletionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Variable visitor is needed, obviously, for collecting variables inside the whole project.
 * <p>
 * Python variables, about which user thinks, are {@link PyTargetExpression} and {@link PyNamedParameter}
 * The first type is variables that, for example, assign some values
 * The second one are variables defined in function declaration inside parentheses.
 * <p>
 * As it was mentioned - variables collected from the whole project. That is why we have to store them:
 * All variables are stored as a map, where key is the path to the variable, and key is a type:
 * Path is created with the PsiElement. Path starts from root folder, and finishes with variable name.
 * It was done in a such way, because there maybe similar variables in different files, and we need
 * to suggest only needed from current user scope.
 * <p>
 * Example of a key (separate into 2 lines for representation):
 * /PsiDirectory:/home/dmitry/PycharmProjects/ultratest/sca/casca/PyFile:acs.py/
 * PyFunction('multi')/PyParameterList/PyNamedParameter('a')/a -> {PyNamedParameterImpl@30325} "PyNamedParameter('a')"
 * <p>
 * VariablesVisitor is used inside type hinting class: {@link ModelCompletionProvider}
 */
public class VariablesVisitor extends PyRecursiveElementVisitor {

    private final Map<String, PyElement> variablesMap = new HashMap<>();

    /**
     * The first type of variables to collect
     *
     * @param node is the current PyTarget variables
     */
    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
//        Generate key and put the map
        String key = generateKeyForNode(node);
        variablesMap.put(String.valueOf(key), node);
//        After that launch default PyCharm behaviour
        super.visitPyTargetExpression(node);
    }

    /**
     * The first type of variables to collect
     *
     * @param node is the current PyNamedParameter variables
     */
    @Override
    public void visitPyNamedParameter(@NotNull PyNamedParameter node) {
//        Generate key and put the map
        String key = generateKeyForNode(node);
        variablesMap.put(String.valueOf(key), node);
//        After that launch default PyCharm behaviour
        super.visitPyNamedParameter(node);
    }

    /**
     * Simple getter of a collected variables
     *
     * @return map of variables with their types as strings
     */
    public Map<String, PyElement> getVariablesMap() {
        return variablesMap;
    }

    /**
     * Generate path for the current element. The needed one are
     * {@link PyTargetExpression} and {@link PyNamedParameter}
     *
     * @param node PsiElement node from which all elements extends
     * @return key for the current element
     */
    public static String generateKeyForNode(PsiElement node) {
//        Prepare element for "going to parent"
        PsiElement temporal = node;
        StringBuilder key = new StringBuilder();
//        Get full path until the root directory
        while (!(temporal instanceof PsiDirectoryImpl && temporal.getChildren()[0] instanceof PsiDirectoryImpl)) {
//            PyNamedParameter and PyTargetExpression have different representation,
//            so check each of them separately
            if (temporal instanceof PyNamedParameter)
                key.insert(0, "/"
                        .concat(temporal.toString())
                        .concat("/")
                        .concat(((PyNamedParameter) temporal).getRepr(false)));
            else if (temporal instanceof PyTargetExpression) {
                key.insert(0, "/" + ((PyTargetExpression) temporal).getName());
            } else
//                Else if current element is still not PsiDirectory, then add current name to path
//                and get parent
                key.insert(0, "/" + temporal.toString());
            temporal = temporal.getOriginalElement().getParent();
        }
        return String.valueOf(key);
    }
}
