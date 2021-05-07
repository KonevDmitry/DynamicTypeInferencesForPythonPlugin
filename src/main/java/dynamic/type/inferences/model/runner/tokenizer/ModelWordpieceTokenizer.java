package dynamic.type.inferences.model.runner.tokenizer;

import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.preprocess.SimpleTokenizer;

import java.util.ArrayList;
import java.util.List;

/**
 * One of RoBERTa's tokenizers implemented by hands. Needs to be implemented for correct work of model.
 * <p>
 * For such purposes DJL prepared simple TextProcessor for creating own tokenizers.
 * This one separates code to words, filters suitable and marks bad tokens as unk's
 */
public class ModelWordpieceTokenizer extends SimpleTokenizer {

    private final String unknown;
    private final int maxInputChars;
    private final SimpleVocabulary vocabulary;

    /**
     * Builder with
     *
     * @param vocabulary    all known words
     * @param unknown       value how to mark unk's
     * @param maxInputChars limitation of token's length
     */
    public ModelWordpieceTokenizer(SimpleVocabulary vocabulary, String unknown, int maxInputChars) {
        this.unknown = unknown;
        this.maxInputChars = maxInputChars;
        this.vocabulary = vocabulary;
    }

    /**
     * Tokenizing process, where
     *
     * @param sentence is a function text
     * @return list of tokens
     */
    @Override
    public List<String> tokenize(String sentence) {
//        The code below was modified from DJL WordPieceTokenizer.
//        No comments about it's work, modification are insignificant but important for our model.
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
