package dynamic.type.inferences.lookUpElement;

import ai.djl.modality.Classifications.Classification;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ModelLookUpElement {
    public List<LookupElement> createElements(List<Classification> classifications) {
        List<LookupElement> lookupElements = new ArrayList<>();
        classifications
                .forEach(
                        classification -> lookupElements.add(createModelElement(classification)));
        return lookupElements;
    }

    public LookupElementBuilder createModelElement(Classification classification) {
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

    public LookupElementBuilder createSuggestedVariableType(Map.Entry<String, String> entry) {
        String fullKey = entry.getKey();
        String type = entry.getValue();
        String varName = fullKey.split(": ")[1];
        if (!fullKey.endsWith("_"))
            return LookupElementBuilder
                    .create(varName)
                    .withPresentableText(varName)
                    .withItemTextForeground(JBColor.BLUE)
                    .withItemTextItalic(true)
                    .withBoldness(true)
                    .withTailText(" Как-то так будет выглядеть", true)
                    .withIcon(PlatformIcons.VARIABLE_ICON)
                    .withTypeText(" Variable type: ".concat(type))
                    .withTypeIconRightAligned(true);
        else
            return null;
    }

    public List<LookupElement> createSuggestedVariablesTypes(Map<String, String> map) {
        return map
                .entrySet()
                .stream()
                .map(this::createSuggestedVariableType)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
