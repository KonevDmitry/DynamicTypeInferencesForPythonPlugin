package dynamic.type.inferences.lookUpElement;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class for creation of LookupElementBuilder entities for type hinting
 */
public class ModelLookUpElement {

    private static final int ADD_VARIABLES_OF_EACH_TYPE = 2;
    private static final String ANY = "Any";
    private static final String UNK = "Unk";

    /**
     * Create one LookupElementBuilder element for variable, where
     *
     * @param entry represents the entry as (full-path-to-variable, variable-type)
     *              Example of representation: someProjects/dir1/dir2/file/method/varList/varName <-> int
     * @return LookupElementBuilder with information about variable and its type
     */
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

    /**
     * Method for creation for top N suggested types, where
     *
     * @param map        represents the map of all variables
     *                   Example of entry: someProjects/dir1/dir2/file/method/varList/varName <-> int
     * @param classNames suitable variable types
     * @return list of LookupElements with suitable types
     */
    public List<LookupElement> createTopNSuggestedVariablesTypes(Map<String, String> map, List<String> classNames) {
        List<LookupElement> suitableVariables = new ArrayList<>();

        for (String className : classNames) {
//            Firstly, separate map values into 2 categories: suitable and unsuitable
//            Suitable are those whose value starts with prediction
//            All classnames are unique
            Map<Boolean, List<Map.Entry<String, String>>> partitioned =
                    map
                            .entrySet()
                            .stream()
                            .collect(Collectors.partitioningBy(
                                    e -> e.getValue().startsWith(className.trim())
                            ));

            // If found suitable variable types - suggest 2 at most (otherwise there may be too much).
            if (partitioned.get(true).size() > 0) {
                List<Map.Entry<String, String>> currentSuitable =
                        partitioned
                                .get(true)
                                .stream()
                                .limit(ADD_VARIABLES_OF_EACH_TYPE)
                                .collect(Collectors.toList());
//                Add suggestions to list of all variable as LookUpElement
                suitableVariables.addAll(currentSuitable
                        .stream()
                        .map(this::createSuggestedVariableType)
                        .collect(Collectors.toList()));
//                And remove them from map.entrySet for removing duplicates
                currentSuitable.forEach(map.entrySet()::remove);
            }
        }
        // Last: feature
        // Left only "Any" values and unsuitable types. Add also 2 types of "Any" and mark as "Unk".

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
//                        Change "Any" suggested as PyCharm to "Unk"
                        elem -> UNK
                ))
                .entrySet()
                .stream()
                .map(this::createSuggestedVariableType)
                .collect(Collectors.toList()));
        return suitableVariables;
    }
}
