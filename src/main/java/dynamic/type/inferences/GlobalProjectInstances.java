package dynamic.type.inferences;

import com.intellij.openapi.application.PathManager;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Class for defining variables that are used in a whole project.
 */
public final class GlobalProjectInstances {

    //    Lots of static variables
    public static final String MODEL_NAME = "/eeee.pt";
    public static final String MODEL_PATH = PathManager.getConfigPath() + MODEL_NAME;
    public static final Integer MAX_VALUES_TO_SHOW = 5;
    public static final Integer BERT_LIMITATION = 512;
    @NotNull
    public static final URL URL_VOCAB = Objects.requireNonNull(GlobalProjectInstances.class.getClassLoader().getResource("/data/torchBERT/vocab.txt"));
    @NotNull
    public static final URL URL_RANKS = Objects.requireNonNull(GlobalProjectInstances.class.getClassLoader().getResource("/data/torchBERT/modelRanks.txt"));
    @NotNull
    public static final URL URL_TOKEN = Objects.requireNonNull(GlobalProjectInstances.class.getClassLoader().getResource("/data/torchBERT/token"));

    public static final String NEW_LINE = "<br/>";
    public static final String BOLD_START = "<b>";
    public static final String BOLD_END = "</b>";
    public static final String OPEN_BRACKET = "(";
    public static final String CLOSE_BRACKET = ")";
    public static final String SPACE_DEF_SPACE = " def ";

    /**
     * Method for reading all ranks - recognizable by model variable types.
     *
     * @return the list of all types as strings
     */
    public static List<String> getRanksFromFile() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(URL_RANKS.openStream()));
            List<String> ranks = reader.lines().collect(Collectors.toList());
            reader.close();
            return ranks;
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * The same as method above, but
     *
     * @return the list of all types as JBList
     */
    public static JBScrollPane getRanksScrollPanel() {
        JBList<String> jbList = new JBList<>(getRanksFromFile());
        JBScrollPane scrollPane = new JBScrollPane(jbList);
        scrollPane.createVerticalScrollBar();
        return scrollPane;
    }
}
