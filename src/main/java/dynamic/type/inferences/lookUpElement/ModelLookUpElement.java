package dynamic.type.inferences.lookUpElement;

import ai.djl.modality.Classifications.Classification;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                .withBoldness(true)
                .withIcon(PlatformIcons.VARIABLE_ICON)
                .withTailText(" Predicted by VaDima")
                .withTypeText(" Suggested as: ".concat(String.valueOf(classification.getProbability())))
                .withTypeIconRightAligned(true);
    }

    public LookupElementBuilder createSuggestedVariableType(Map.Entry<String, String> entry) {
        String fullKey = entry.getKey();
        String type = entry.getValue();
        String varName = fullKey.substring(fullKey.lastIndexOf('/')+1);
        if (!fullKey.endsWith("_"))
            return LookupElementBuilder
                    .create(varName)
                    .withPresentableText(varName)
                    .withItemTextForeground(JBColor.BLUE)
                    .withItemTextItalic(true)
                    .withBoldness(true)
                    .withTailText(" VaDima plugin info", true)
                    .withIcon(PlatformIcons.VARIABLE_ICON)
                    .withTypeText(" Variable type: ".concat(type))
                    .withTypeIconRightAligned(true);
        else
            return null;
    }

    //TODO: add filter to type
    // For example, if model predicts next: "def fun(elem1:str, elem2:int) -> int"
    // Than filter elements by their type (int as first, than str, the latest - Any) and show top N
    // variants (as tuple or one by one, need more to google it)
    public List<LookupElement> createTopNSuggestedVariablesTypes(Map<String, String> map, Integer n) {
        Map<Boolean, List<Map.Entry<String, String>>> partitioned =
                map
                        .entrySet()
                        .stream()
                        // do not suggest unneded variables
                        .filter(e -> !e.getKey().endsWith("_"))
                        .collect(Collectors.partitioningBy(
                                //TODO: here change later to model predictions
                                //Also think about multiple variables
                                e -> e.getValue().startsWith("str")
                        ));
        if (partitioned.get(true).size() > n)
            return partitioned
                    .get(true)
                    .stream()
                    .map(this::createSuggestedVariableType)
                    .limit(n)
                    .collect(Collectors.toList());
        else {
            List<LookupElement> suitableType =
                    partitioned
                            .get(true)
                            .stream()
                            .map(this::createSuggestedVariableType)
                            .collect(Collectors.toList());
            List<LookupElement> unsuitableType =
                    partitioned
                            .get(false)
                            .stream()
                            .map(this::createSuggestedVariableType)
                            .limit(n - partitioned.get(true).size())
                            .collect(Collectors.toList());
            suitableType.addAll(unsuitableType);
            return suitableType;
        }
    }

    public List<LookupElement> createSuggestedVariablesTypes(Map<String, String> map) {
        return createTopNSuggestedVariablesTypes(map, map.size());
    }
}
