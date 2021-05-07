package dynamic.type.inferences.indexer;

import ai.djl.modality.Classifications;
import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The code below is not used and provides some plain implementation of indexer.
 * We tried to create predictions on indexer step for better performance during typical PyCharm work.
 * <p>
 * But solution with indexer occurred to be too terrible. At least 30 minutes of indexing
 * and several gigabytes of memory...
 * <p>
 * Moreover, the code bellow is not finished - it includes only predictions for
 * functions without their name/path (the last one is more preferable because of same function names)
 * <p>
 * The code below is not commented because it is not used.
 */
public class ClassificationStringDescriptor implements KeyDescriptor<List<Classifications.Classification>> {
    private static class ClassificationStringDescriptorImpl
            extends ClassificationStringDescriptor
            implements DifferentSerializableBytesImplyNonEqualityPolicy {
    }

    public static final ClassificationStringDescriptor INSTANCE = new ClassificationStringDescriptorImpl();

    @Override
    public int getHashCode(List<Classifications.Classification> value) {
        return value.hashCode();
    }

    @Override
    public boolean isEqual(List<Classifications.Classification> val1, List<Classifications.Classification> val2) {

        Iterator<Classifications.Classification> val1Iterator = val1.iterator();
        Iterator<Classifications.Classification> val2Iterator = val2.iterator();

        while (val1Iterator.hasNext() && val2Iterator.hasNext()) {
            Classifications.Classification val1Current = val1Iterator.next();
            Classifications.Classification val2Current = val1Iterator.next();

            if (val1Current.getClassName().equals(val2Current.getClassName())
                    && val1Current.getProbability() == val2Current.getProbability()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void save(@NotNull DataOutput out, List<Classifications.Classification> value) throws IOException {
        // for memory optimization take top 5
        String res = value
                .stream()
                .map(elem -> elem.getClassName() + ", " + elem.getProbability())
                .collect(Collectors.joining("&&"));
        IOUtil.writeUTF(out, res);
    }

    @Override
    public List<Classifications.Classification> read(@NotNull DataInput in) throws IOException {
        String top5 = IOUtil.readUTF(in);
        List<Classifications.Classification> res = new ArrayList<>();
        Arrays.stream(top5
                .split("&&"))
                .forEach(elem -> {
                    String[] current = elem.split(", ");
                    res.add(new Classifications.Classification(current[0], Double.parseDouble(current[1])));
                });
        return res;
    }
}
