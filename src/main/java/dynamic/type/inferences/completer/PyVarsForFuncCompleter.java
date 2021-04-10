package dynamic.type.inferences.completer;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.jetbrains.python.PythonFileType;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public class PyVarsForFuncCompleter extends CompletionContributor {

    // Instance for running completer that is registered in plugin.xml
    public PyVarsForFuncCompleter() throws NullPointerException {
        extend(CompletionType.BASIC,
                psiElement()
                        .inFile(psiFile()
                                .withFileType(instanceOf(PythonFileType.class))),
                new ModelCompletionProvider());
    }

}
