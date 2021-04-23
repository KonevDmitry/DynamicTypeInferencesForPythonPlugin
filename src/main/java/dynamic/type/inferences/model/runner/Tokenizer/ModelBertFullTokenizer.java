package dynamic.type.inferences.model.runner.Tokenizer;

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
 * Class for tokenizing code
 */
public class ModelBertFullTokenizer extends SimpleTokenizer {

    private final SimpleVocabulary vocabulary;
    private final List<TextProcessor> basicBertPreprocessors;
    private final ModelWordpieceTokenizer wordpieceTokenizer;

//    if variable name is more than 200 symbols -> mark as unk
    private static final int MAX_INPUT_CHARS = 200;

    public ModelBertFullTokenizer(SimpleVocabulary vocabulary, boolean lowerCase) {
        this.vocabulary = vocabulary;
        basicBertPreprocessors = getPreprocessors(lowerCase);
        wordpieceTokenizer = new ModelWordpieceTokenizer(vocabulary, "<unk>", MAX_INPUT_CHARS);
    }

    public SimpleVocabulary getVocabulary() {
        return vocabulary;
    }

    @Override
    public List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>(Collections.singletonList(input));
        for (TextProcessor processor : basicBertPreprocessors)
            tokens = processor.preprocess(tokens);

        return wordpieceTokenizer.preprocess(tokens);
    }

    public static List<TextProcessor> getPreprocessors(boolean lowerCase) {
        List<TextProcessor> processors = new ArrayList<>(10);
        processors.add(new TextCleaner(c -> c == 0 || c == 0xfffd || NlpUtils.isControl(c), '\0'));
        processors.add(new LambdaProcessor(String::trim));
        if (lowerCase) {
            processors.add(new LowerCaseConvertor());
        }
        processors.add(new UnicodeNormalizer(Normalizer.Form.NFD));
        processors.add(
                new TextCleaner(c -> Character.getType(c) == Character.NON_SPACING_MARK, '\0'));
        processors.add(new LambdaProcessor(String::trim));
        processors.add(new TextCleaner(NlpUtils::isWhiteSpace, ' '));
        processors.add(new SimpleTokenizer());
        processors.add(new GTokenizer());
        processors.add(new PunctuationSeparator());
        return processors;
    }

    public BertToken encode(String code) {
        List<String> tokens = tokenize(code);

//      Never touch the code below. Tokens are added in a such way, because default DJL tokenizer sorts tokens:
//      <s> should be first, but after sorting it goes after </s>.
//      With default adding tokens </s> surprisingly will go first after <s>.
//      In such a way logic of model crushes. So, do not touch next two lines :)

        tokens.add(0, "</s>");
        tokens.add("<s>");

        int validLength = tokens.size();
        long[] tokenTypeArr = new long[tokens.size()];
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
