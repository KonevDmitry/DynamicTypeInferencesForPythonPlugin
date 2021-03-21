package dynamic.type.inferences.model.translator;

import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.Batchifier;
import ai.djl.translate.StackBatchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import dynamic.type.inferences.model.runner.Tokenizer.ModelBertFullTokenizer;

import java.util.Arrays;
import java.util.List;

public class BertTranslator implements Translator<String, Classifications> {

    private final ModelBertFullTokenizer tokenizer;
//    private final BertFullTokenizer tokenizer;
    private final SimpleVocabulary vocab;
    private final List<String> ranks;

    private static final RanksGetter ranksGetter = new RanksGetter();

    public BertTranslator(ModelBertFullTokenizer tokenizer) {
//    public BertTranslator(BertFullTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.vocab = tokenizer.getVocabulary();
        this.ranks = ranksGetter.getRanksFromFile();
    }

    @Override
    public Batchifier getBatchifier() {
        return new StackBatchifier();
    }

//    @Override
//    public NDList processInput(TranslatorContext ctx, String input) {
//        List<String> tokens = tokenizer.tokenize(input);
//        int[] indices = new int[tokens.size() + 2];
//        indices[0] = (int) vocab.getIndex("[CLS]");
//        for (int i = 0; i < tokens.size(); i++) {
//            indices[i + 1] = (int) vocab.getIndex(tokens.get(i));
//        }
//        indices[indices.length - 1] = (int) vocab.getIndex("[SEP]");
//        return new NDList(ctx.getNDManager().create(indices, new Shape()));
//    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        List<String> tokens = tokenizer.tokenize(input);
        long[] indices = tokens.stream().mapToLong(vocab::getIndex).toArray();
        long[] attentionMask = new long[tokens.size()];
        Arrays.fill(attentionMask, 1);
        NDManager manager = ctx.getNDManager();
//        NDArray indicesArray = manager.create(indices, new Shape());
        NDArray indicesArray = manager.create(indices);
        NDArray attentionMaskArray = manager.create(attentionMask);
        return new NDList(indicesArray, attentionMaskArray);
    }

    @Override
    public Classifications processOutput(TranslatorContext ctx, NDList list) {
        NDArray raw = list.get(0);

        NDArray computed = raw.exp().div(raw.exp().sum(new int[]{0}, true));
//        NDArray computed = raw.softmax(0);
        return new Classifications(ranks, computed);
    }
}