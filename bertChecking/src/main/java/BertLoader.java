import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.ModelException;
import ai.djl.basicdataset.CsvDataset;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.modality.nlp.bert.BertTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDArrays;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BertLoader {

    private static final Logger logger = LoggerFactory.getLogger(BertLoader.class);

    private BertLoader() {
    }

    public static void main(String[] args) throws IOException, ModelException, TranslateException {

        logger.info("Using " + Engine.getInstance().getEngineName());

        List<String> inputs = new ArrayList<>();
        inputs.add("Sample input. Quite neutral");
        inputs.add("Hello, world! Quite positive");
        inputs.add("I hate everything. Quite negative.");
        inputs.add("DJL is awesome!!! Very positive");

        Classifications[] results = predict(inputs);
        if (results == null) {
            logger.error("This example only works for TensorFlow Engine");
        } else {
            for (int i = 0; i < inputs.size(); i++) {
                System.out.println("Prediction for: " + inputs.get(i) + "\n" + results[i].toString());
            }
        }
    }

    public static Classifications[] predict(List<String> inputs)
            throws MalformedModelException, ModelNotFoundException, IOException,
            TranslateException {

        // For now for work is taken tf models. For pyTorch there is another example.
        // If is needed than pipeline will ba changed a bit.
        if (!"TensorFlow".equals(Engine.getInstance().getEngineName())) {
            return null;
        }
        // refer to
        // https://medium.com/delvify/bert-rest-inference-from-the-fine-tuned-model-499997b32851
        // for converting public bert checkpoints to saved model format.

        // Model also can be loaded from url, but will be loaded into users PC somewhere in cash.
        // for that needed is to write like "URL:/// <url>" or just write url

        String modelUrl = "file:///home/dmitry/IdeaProjects/DynamicTypeInferencesForPythonPlugin/bertChecking/sources/model/pretrained/model";
        // String modelUrl = "file:///home/dmitry/IdeaProjects/DynamicTypeInferencesForPythonPlugin/bertChecking/sources/model/superBert";
        //  String vocabularyPath = "/home/dmitry/IdeaProjects/DynamicTypeInferencesForPythonPlugin/bertChecking/sources/model/pretrained/vocab.txt";
        String vocabularyPath = "/home/dmitry/IdeaProjects/DynamicTypeInferencesForPythonPlugin/bertChecking/sources/model/superBert/assets/vocab.txt";
        BufferedReader br = new BufferedReader(new FileReader(vocabularyPath));

        SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                .optMinFrequency(1)
                .add(br
                        .lines()
                        .collect(Collectors.toList()))
                .optUnknownToken("[UNK]")
                .build();

        BertFullTokenizer tokenizer = new BertFullTokenizer(vocabulary, true);

        OwnTranslator translator = new OwnTranslator(tokenizer);


        Criteria<String, Classifications> criteria =
                Criteria.builder()
                        .optApplication(Application.NLP.WORD_EMBEDDING)
                        .setTypes(String.class, Classifications.class)
                        .optModelUrls(modelUrl)
                        .optTranslator(translator)
                        .optProgress(new ProgressBar())
                        .build();

        ZooModel<String, Classifications> model = ModelZoo.loadModel(criteria);

        Predictor<String, Classifications> predictor = model.newPredictor(translator);

        ArrayList<Classifications> predicts = new ArrayList<>();
        for (String input : inputs) {
            Classifications res = predictor.predict(input);
            predicts.add(res);
        }

        return predicts.toArray(new Classifications[0]);
    }

}

class OwnTranslator implements Translator<String, Classifications> {

    private BertFullTokenizer tokenizer;
    private SimpleVocabulary vocab;
    private List<String> ranks;

    public OwnTranslator(BertFullTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        vocab = tokenizer.getVocabulary();
        ranks = Arrays.asList("Negative", "Semi-negative", "Neutral", "Semi-positive", "Positive");
    }

    @Override
    public Batchifier getBatchifier() {
        return new StackBatchifier();
    }

    @Override
    public void prepare(NDManager manager, Model model) throws IOException {
        String vocabularyPath = "/home/dmitry/IdeaProjects/BertChecking/sources/model/pretrained/vocab.txt";

        BufferedReader br = new BufferedReader(new FileReader(vocabularyPath));

        vocab = SimpleVocabulary.builder()
                .optMinFrequency(1)
                .add(br
                        .lines()
                        .collect(Collectors.toList()))
                .optUnknownToken("[UNK]")
                .build();
    }

    // Please don't touch it. It is temporal example how everything can break in predictions probability...
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

//        NDArray computed = raw.exp().div(raw.exp().sum(new int[]{0}, true));
        NDArray computed = raw.softmax(0);
        return new Classifications(ranks, computed);
    }
}
