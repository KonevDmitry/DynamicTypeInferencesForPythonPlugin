package dynamic.type.inferences.model.runner.Tokenizer;

import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.preprocess.SimpleTokenizer;

import java.util.ArrayList;
import java.util.List;

public class ModelWordpieceTokenizer extends SimpleTokenizer {

    private final String unknown;
    private final int maxInputChars;
    private final SimpleVocabulary vocabulary;

    public ModelWordpieceTokenizer(SimpleVocabulary vocabulary, String unknown, int maxInputChars) {
        this.unknown = unknown;
        this.maxInputChars = maxInputChars;
        this.vocabulary = vocabulary;
    }

    @Override
    public List<String> tokenize(String sentence) {
        StringBuilder sb = new StringBuilder();
        List<String> subTokens = new ArrayList<>();
        List<String> outputTokens = new ArrayList<>();
        for (String token : super.tokenize(sentence.trim())) {
            char[] chars = token.toCharArray();
            if (chars.length > maxInputChars) {
                outputTokens.add(unknown);
                continue;
            }
            boolean isBad = false;
            int start = 0;
            subTokens.clear();
            String currentSubString = null;
            while (start < chars.length) {
                int end = chars.length;
                while (start < end) {
                    sb.setLength(0);
                    sb.append(token, start, end);
                    if (start > 0) {
                        sb.insert(0, "Ġ");
                    }
                    String subString = sb.toString();
                    if (vocabulary.contains(subString)) {
                        currentSubString = subString;
                        break;
                    } else {
                        currentSubString = null;
                    }
                    end--;
                }
                if (currentSubString == null) {
                    isBad = true;
                    break;
                }
                subTokens.add(currentSubString);
                if (subTokens.size() > maxInputChars) {
                    throw new IllegalStateException("Too many subTokens for: '" + sentence + '\'');
                }
                start = end;
            }
            if (isBad) {
                outputTokens.add(unknown);
            } else {
                outputTokens.addAll(subTokens);
            }
        }
        return outputTokens;
    }
}
