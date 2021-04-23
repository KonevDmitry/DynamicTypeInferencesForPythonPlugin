package dynamic.type.inferences.lookUpElement;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModelLookUpElement {

    private static final int ADD_VARIABLES_OF_EACH_TYPE = 2;
    private static final String ANY = "Any";
    private static final String UNK = "Unk";

    public LookupElementBuilder createSuggestedVariableType(Map.Entry<String, String> entry) {
        String fullKey = entry.getKey();
        String type = entry.getValue();
        String varName = fullKey.substring(fullKey.lastIndexOf('/') + 1);
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
    }

    public List<LookupElement> createTopNSuggestedVariablesTypes(Map<String, String> map, List<String> classNames) {
        List<LookupElement> suitableVariables = new ArrayList<>();

        for (String className : classNames) {
            Map<Boolean, List<Map.Entry<String, String>>> partitioned =
                    map
                            .entrySet()
                            .stream()
                            .collect(Collectors.partitioningBy(
                                    e -> e.getValue().startsWith(className.trim())
                            ));

            // if found suitable variable types - suggest 2 at most.
            if (partitioned.get(true).size() > 0) {
                List<Map.Entry<String, String>> currentSuitable =
                        partitioned
                                .get(true)
                                .stream()
                                .limit(ADD_VARIABLES_OF_EACH_TYPE)
                                .collect(Collectors.toList());
                suitableVariables.addAll(currentSuitable
                        .stream()
                        .map(this::createSuggestedVariableType)
                        .collect(Collectors.toList()));
                currentSuitable.forEach(map.entrySet()::remove);
            }
        }
        // when where found 2 suitable variable of each type (or not).
        // Left only "Any" values and unsuitable types. Add also 2 types of "Any" and mark as UNK.

        suitableVariables.addAll(map
                .entrySet()
                .stream()
                .collect(Collectors.partitioningBy(
                        e -> e.getValue().startsWith(ANY)
                ))
                .get(true)
                .stream()
                .limit(ADD_VARIABLES_OF_EACH_TYPE)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        elem -> UNK
                ))
                .entrySet()
                .stream()
                .map(this::createSuggestedVariableType)
                .collect(Collectors.toList()));
        return suitableVariables;
    }
}
