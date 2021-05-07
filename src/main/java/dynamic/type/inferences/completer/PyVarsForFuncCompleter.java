package dynamic.type.inferences.completer;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.jetbrains.python.PythonFileType;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
import static com.intellij.patterns.StandardPatterns.instanceOf;

/**
 * Instance for running completer that is registered in plugin.xml
 * Runs instance of ModelCompletionProvider
 */
public class PyVarsForFuncCompleter extends CompletionContributor {

    /**
     * Main method for running created provider
     */
    public PyVarsForFuncCompleter() {
        extend(CompletionType.BASIC,
                psiElement()
                        .inFile(psiFile()
                                .withFileType(instanceOf(PythonFileType.class))),
                new ModelCompletionProvider());
    }

}
