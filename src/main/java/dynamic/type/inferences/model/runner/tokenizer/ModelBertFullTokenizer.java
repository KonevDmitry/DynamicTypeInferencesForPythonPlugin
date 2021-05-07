package dynamic.type.inferences.model.runner.tokenizer;

import ai.djl.modality.nlp.NlpUtils;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertToken;
import ai.djl.modality.nlp.preprocess.*;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One of RoBERTa's tokenizers. Needs to be implemented for correct work of model.
 * <p>
 * For such purposes DJL prepared simple TextProcessor for creating own tokenizers.
 * This one joins all implemented and existing tokenizers
 */
public class ModelBertFullTokenizer extends SimpleTokenizer {

    private final SimpleVocabulary vocabulary;
    private final List<TextProcessor> basicBertPreprocessors;
    private final ModelWordpieceTokenizer wordpieceTokenizer;

    //    if variable name is more than 200 symbols -> mark as unk
    private static final int MAX_INPUT_CHARS = 200;

    /**
     * Constructor of main tokenizer, where
     *
     * @param vocabulary is an instance of models vocabulary containing all recognizable worlds
     * @param lowerCase  option to lowercase words
     */
    public ModelBertFullTokenizer(SimpleVocabulary vocabulary, boolean lowerCase) {
        this.vocabulary = vocabulary;
        basicBertPreprocessors = getPreprocessors(lowerCase);
        wordpieceTokenizer = new ModelWordpieceTokenizer(vocabulary, "<unk>", MAX_INPUT_CHARS);
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
        List<String> tokens = new ArrayList<>(Collections.singletonList(input));
//        Run all tokenizers. There is the only way implementing the same behaviour as
//        tokenizing in Python.
        for (TextProcessor processor : basicBertPreprocessors)
            tokens = processor.preprocess(tokens);

        return wordpieceTokenizer.preprocess(tokens);
    }

    /**
     * Initializing all processors for tokenizing, where
     *
     * @param lowerCase is a option for lower casing tokens
     * @return list of initialized processors
     */
    public static List<TextProcessor> getPreprocessors(boolean lowerCase) {
//      Each of tokenizers contains comments what it does.
        List<TextProcessor> processors = new ArrayList<>(10);
        processors.add(new TextCleaner(c -> c == 0 || c == 0xfffd || NlpUtils.isControl(c), '\0'));
        processors.add(new LambdaProcessor(String::trim));
        if (lowerCase)
            processors.add(new LowerCaseConvertor());
        processors.add(new UnicodeNormalizer(Normalizer.Form.NFD));
        processors.add(new TextCleaner(c -> Character.getType(c) == Character.NON_SPACING_MARK, '\0'));
        processors.add(new LambdaProcessor(String::trim));
        processors.add(new TextCleaner(NlpUtils::isWhiteSpace, ' '));
        processors.add(new SimpleTokenizer());
        processors.add(new GTokenizer());
        processors.add(new PunctuationSeparator());
        return processors;
    }

    /**
     * Code encoding to acceptable by model format, where
     *
     * @param code is a code of a function
     * @return special BertToken defined by DJL for text tokenization. (Contains comments in DJL library)
     */
    public BertToken encode(String code) {
        List<String> tokens = tokenize(code);

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
