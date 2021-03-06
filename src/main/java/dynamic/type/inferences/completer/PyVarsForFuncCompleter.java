package dynamic.type.inferences.completer;

import com.intellij.codeInsight.completion.*;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public class PyVarsForFuncCompleter extends CompletionContributor {

    public PyVarsForFuncCompleter() throws NullPointerException {
        extend(CompletionType.BASIC,
                psiElement()
                        .inFile(psiFile()
                                .withFileType(instanceOf(PythonFileType.class))),
                new ModelCompletionProvider());
    }

    @Override
    public void fillCompletionVariants(@NotNull final CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);
    }
}
