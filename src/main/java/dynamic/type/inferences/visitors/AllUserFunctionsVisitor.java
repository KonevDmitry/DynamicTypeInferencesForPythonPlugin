package dynamic.type.inferences.visitors;

import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AllUserFunctionsVisitor extends PyRecursiveElementVisitor {
    private final StringBuilder fullCode = new StringBuilder();
    private final Map<String, String> functionCodeMap = new HashMap<>();

    public StringBuilder getFullCode() {
        return fullCode;
    }

    public Map<String, String> getFunctionCodeMap() {
        return functionCodeMap;
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
        String nodeText = node.getText().concat("\n\n");
        fullCode.append(nodeText);
        if (node.getName() != null) {
            String key = Objects.requireNonNull(node
                    .getContainingFile()
                    .getVirtualFile()
                    .getCanonicalPath())
                    .concat("*")
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
}
