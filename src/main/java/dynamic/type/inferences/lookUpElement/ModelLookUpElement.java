package dynamic.type.inferences.lookUpElement;

import ai.djl.modality.Classifications.Classification;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformIcons;

import java.util.ArrayList;
import java.util.List;

public class ModelLookUpElement {
    public List<LookupElement> createElements(List<Classification> classifications) {
        List<LookupElement> lookupElements = new ArrayList<>();
        classifications
                .forEach(
                        classification -> lookupElements.add(createElement(classification)));
        return lookupElements;
    }

    public LookupElementBuilder createElement(Classification classification) {
        return LookupElementBuilder
                .create(classification.getClassName())
                .withPresentableText(classification.getClassName())
                .withItemTextForeground(JBColor.RED)
                .bold()
                .withIcon(PlatformIcons.VARIABLE_ICON)
                .withTailText(" Predicted by VaDima")
                .withTypeText(" Suggested as: ".concat(String.valueOf(classification.getProbability())))
                .withTypeIconRightAligned(true);
    }
}
