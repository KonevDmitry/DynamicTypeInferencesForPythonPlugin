package dynamic.type.inferences.model.translator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RanksGetter {
    public List<String> getRanksFromFile() {
        try {
            URL url = getClass().getClassLoader().getResource("/data/torchBERT/modelRanks.txt");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            Objects.requireNonNull(url).openStream()));
            List<String> ranks = reader.lines().collect(Collectors.toList());
            reader.close();
            return ranks;
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }
}
