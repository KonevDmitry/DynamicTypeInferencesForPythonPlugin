package dynamic.type.inferences.modelWorker.translator;

import ai.djl.Model;
import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Batchifier;
import ai.djl.translate.StackBatchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BertTranslator implements Translator<String, Classifications> {

    private final BertFullTokenizer tokenizer;
    private SimpleVocabulary vocab;
    private final List<String> ranks;
    private final String vocabularyPath;

    public BertTranslator(BertFullTokenizer tokenizer, String vocabularyPath) {
        this.tokenizer = tokenizer;
        vocab = tokenizer.getVocabulary();
        ranks = Arrays.asList("Negative", "Neutral", "Positive");
        this.vocabularyPath = vocabularyPath;
    }

    @Override
    public Batchifier getBatchifier() {
        return new StackBatchifier();
    }

    @Override
    public void prepare(NDManager manager, Model model) throws IOException {

        BufferedReader br = new BufferedReader(new FileReader(vocabularyPath));

        vocab = SimpleVocabulary.builder()
                .optMinFrequency(1)
                .add(br
                        .lines()
                        .collect(Collectors.toList()))
                .optUnknownToken("[UNK]")
                .build();
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

    //TODO: There are example in another classes (like SimpleText2TextTranslator). Look at example and change code below
    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        List<String> tokens = tokenizer.tokenize(input);
        int[] indices = tokens.stream().mapToInt(token -> (int) vocab.getIndex(token)).toArray();
        int[] attentionMask = new int[tokens.size()];
        Arrays.fill(attentionMask, 1);
        NDManager manager = ctx.getNDManager();
        NDArray indicesArray = manager.create(indices, new Shape());
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
