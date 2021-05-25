package dynamic.type.inferences.model.runner.tokenizer;

import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertToken;
import ai.djl.modality.nlp.preprocess.SimpleTokenizer;
import dynamic.type.inferences.GlobalProjectInstances;
import tfLite.GPT2Tokenizer;
import kotlin.Pair;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * One of RoBERTa's tokenizers. Needs to be implemented for correct work of model.
 * <p>
 * For such purposes DJL prepared simple TextProcessor for creating own tokenizers.
 * This one joins all implemented and existing tokenizers
 */
public class ModelBertFullTokenizer extends SimpleTokenizer {

    private final SimpleVocabulary vocabulary;

    /**
     * Constructor of main tokenizer, where
     *
     * @param vocabulary is an instance of models vocabulary containing all recognizable worlds
     */
    public ModelBertFullTokenizer(SimpleVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    /**
     * @return typical getter of vocabulary
     */
    public SimpleVocabulary getVocabulary() {
        return vocabulary;
    }

    /**
     * Tokenizing process, where
     *
     * @param input is a text of a function to preprocess
     * @return function tokens
     */
    @Override
    public List<String> tokenize(String input) {
//        Run GPT2 tokenizer.
        Map<Pair<String, String>, Integer> bpeRanks;
        try {
            bpeRanks = GlobalProjectInstances.loadBpeRanks();
            GPT2Tokenizer gpt2Tokenizer = new GPT2Tokenizer(bpeRanks);
            return gpt2Tokenizer.encode(input);
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Code encoding to acceptable by model format, where
     *
     * @param code is a code of a function
     * @return special BertToken defined by DJL for text tokenization. (Contains comments in DJL library)
     */
    public BertToken encode(String code) throws IOException {
        Map<Pair<String, String>, Integer> bpeRanks = GlobalProjectInstances.loadBpeRanks();
        GPT2Tokenizer gpt2Tokenizer = new GPT2Tokenizer(bpeRanks);
        List<String> tokens = gpt2Tokenizer.encode(code);

//      Never touch the code below. Tokens are added in a such way, because default DJL tokenizer sorts tokens:
//      <s> should be first, but after sorting it goes after </s>.
//      With default adding tokens </s> surprisingly will go first after <s>.
//      In such a way logic of model crushes. So, never touch next two lines :)
//      Also, DJL for now cannot fix it. There are no such much people who implement
//      NER tasks in Java on BERT-based models... (we are the only one :( )

        tokens.add(0, "</s>");
        tokens.add("<s>");

//        Create token and attention mask of tokens size
//        attention mask contains all tokens, so all values are marked as 1's
//        In our context BertToken is useless by itself, but it is rather easier to get all
//        tokens now and filter some of them later then filtering tokens here.
        int validLength = tokens.size();
        long[] tokenTypeArr = new long[validLength];
        Arrays.fill(tokenTypeArr, validLength, tokenTypeArr.length, 1);

        long[] attentionMaskArr = new long[tokens.size()];
        Arrays.fill(attentionMaskArr, 1);

        return new BertToken(
                tokens,
                Arrays.stream(tokenTypeArr).boxed().collect(Collectors.toList()),
                Arrays.stream(attentionMaskArr).boxed().collect(Collectors.toList()),
                validLength);
    }
}
