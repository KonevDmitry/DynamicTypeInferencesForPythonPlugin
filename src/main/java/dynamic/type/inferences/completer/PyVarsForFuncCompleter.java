package dynamic.type.inferences.completer;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.Completion;

import java.util.Objects;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public class PyVarsForFuncCompleter extends CompletionContributor {

    public PyVarsForFuncCompleter() {
        extend(CompletionType.BASIC,
                psiElement()
                        .inFile(psiFile()
                                .withFileType(instanceOf(PythonFileType.class))),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        FileType fileType = parameters.getOriginalFile().getFileType();
                        if (!(fileType instanceof PythonFileType)) {
                            return;
                        }
                        CaretModel caret = parameters.getEditor().getCaretModel();
                        PsiElement currentElem = parameters.getOriginalFile().findElementAt(caret.getOffset());
                        if (currentElem != null && currentElem.getParent() instanceof PyArgumentList) {
                            System.out.println("AAAA");
                            String prefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters);

                            CompletionResultSet resultSetWithPrefix = result.withPrefixMatcher(prefix);
                            resultSetWithPrefix.addElement(LookupElementBuilder.create("hui1, hui2, hui3").bold());
                        }
                        System.out.println(parameters.getOriginalPosition());
                    }
                });
//        PsiReference#getVariants()
    }

    private final MultiMap<CompletionType,
            Pair<ElementPattern<? extends PsiElement>,
                    CompletionProvider<CompletionParameters>>> myMap = new MultiMap<>();

    @Override
    public void fillCompletionVariants(@NotNull final CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {

        super.fillCompletionVariants(parameters, result);
    }
}
