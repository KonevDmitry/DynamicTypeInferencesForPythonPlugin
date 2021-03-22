package dynamic.type.inferences.model.runner.Tokenizer;

import ai.djl.modality.nlp.NlpUtils;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.preprocess.*;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModelBertFullTokenizer extends SimpleTokenizer {

    private final SimpleVocabulary vocabulary;
    private final List<TextProcessor> basicBertPreprocessors;
    private final ModelWordpieceTokenizer wordpieceTokenizer;

    public ModelBertFullTokenizer(SimpleVocabulary vocabulary, boolean lowerCase) {
        this.vocabulary = vocabulary;
        basicBertPreprocessors = getPreprocessors(lowerCase);
        wordpieceTokenizer = new ModelWordpieceTokenizer(vocabulary, "<unk>", 200);
//        wordpieceTokenizer = new WordpieceTokenizer(vocabulary, "[UNK]", 200);
    }

    public SimpleVocabulary getVocabulary() {

        return vocabulary;
    }

    @Override
    public List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>(Collections.singletonList(input));
        for (TextProcessor processor : basicBertPreprocessors) {
            tokens = processor.preprocess(tokens);
        }
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
}
