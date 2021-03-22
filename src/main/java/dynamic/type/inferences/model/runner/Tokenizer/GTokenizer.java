package dynamic.type.inferences.model.runner.Tokenizer;

import ai.djl.modality.nlp.preprocess.TextProcessor;

import java.util.List;

public class GTokenizer implements TextProcessor {

    @Override
    public List<String> preprocess(List<String> tokens) {
        tokens
                .replaceAll(e -> {
                    StringBuilder builder = new StringBuilder(e);
                    builder.insert(0, "Ġ");
                    return builder.toString();
                });
        return tokens;
    }
}
