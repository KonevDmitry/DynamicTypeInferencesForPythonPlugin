package dynamic.type.inferences.model.runner;

import ai.djl.*;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.Classifications.Classification;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.repository.zoo.*;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.*;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.dropbox.core.DbxException;
import com.intellij.openapi.application.PathManager;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.translator.BertTranslator;


public class TorchBert {
    private static final Integer MAX_VALUES_TO_SHOW = 5;

    private static Predictor<String, Classifications> predictor;
    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);
    private boolean initialized = false;

    public boolean isInitialized() {
        return initialized;
    }

    public TorchBert() {
    }

    public synchronized void modelInit() throws IOException, MalformedModelException, ModelNotFoundException, URISyntaxException, DbxException, InterruptedException {
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(current);
        } finally {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            URL urlVocab = getClass().getClassLoader().getResource("/data/torchBERT/vocab.txt");
            String modelPath = PathManager.getConfigPath() + "/eeee.pt";
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                createPredictor(urlVocab, modelPath);
                initialized = true;
            } else {
                loader.loadTo(modelPath);
                synchronized (sharedObject) {
                    createPredictor(urlVocab, modelPath);
                    initialized = true;
                }
            }
        }
    }

    public void createPredictor(URL url, String path) throws IOException, ModelNotFoundException, MalformedModelException {
        BufferedReader brVocab = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(url).openStream()));

        SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                .optMinFrequency(1)
                .add(brVocab
                        .lines()
                        .collect(Collectors.toList()))
                .optUnknownToken("[UNK]")
                .build();

        BertFullTokenizer tokenizer = new BertFullTokenizer(vocabulary, true);
        BertTranslator translator = new BertTranslator(tokenizer);

        Criteria<String, Classifications> criteria =
                Criteria.builder()
                        .optApplication(Application.NLP.SENTIMENT_ANALYSIS)
                        .optDevice(Device.cpu())
                        .setTypes(String.class, Classifications.class)
                        .optModelUrls(path)
                        .optTranslator(translator)
                        .optProgress(new ProgressBar())
                        .build();

        ZooModel<String, Classifications> model = ModelZoo.loadModel(criteria);
        predictor = model.newPredictor(translator);
    }
//    public void main(String[] args) throws IOException, ModelException, TranslateException {
//        modelInit();
//
//        List<String> inputs = new ArrayList<>();
//        inputs.add("Sample input");
//        inputs.add("I love you, DJL!");
//        inputs.add("I hate everything");
//        inputs.add("DJL is awesome. This is amazing");
//
//        ArrayList<Classifications> results = predict(inputs);
//        for (int i = 0; i < inputs.size(); i++) {
//            System.out.println("Prediction for: " + inputs.get(i) + "\n" + results.get(i).toString());
//        }
//
//    }

    public List<Classification> predictOne(String input) throws TranslateException {
        Classifications res = predictor.predict(input);
        List<Classification> items = res.items();
        return items.size() > MAX_VALUES_TO_SHOW ? items.subList(0, MAX_VALUES_TO_SHOW) : items;
    }

    public List<List<Classification>> predictAll(List<String> inputs)
            throws TranslateException {

        List<List<Classification>> predicts = new ArrayList<>();
        for (String input : inputs) {
            Classifications res = predictor.predict(input);
            List<Classification> items = res.items();
            items = items.size() > 5 ? items.subList(0, 5) : items;
            predicts.add(items);
        }
        return predicts.size() > 5 ? predicts.subList(0, 5) : predicts;
    }

}