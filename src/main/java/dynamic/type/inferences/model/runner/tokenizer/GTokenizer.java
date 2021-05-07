package dynamic.type.inferences.model.runner.tokenizer;

import ai.djl.modality.nlp.preprocess.TextProcessor;

import java.util.List;

/**
 * One of RoBERTa's tokenizers. Needs to be implemented for correct work of model.
 * <p>
 * For such purposes DJL prepared simple TextProcessor for creating own tokenizers.
 * This one adds Ġ for tokens.
 */
public class GTokenizer implements TextProcessor {

    /**
     * Adding Ġ to
     *
     * @param tokens all tokens of current function
     * @return updated list of tokens
     */
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
