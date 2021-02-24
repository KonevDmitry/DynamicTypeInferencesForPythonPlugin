package dynamic.type.inferences.model.runner;

import ai.djl.*;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.translator.BertTranslator;

import javax.swing.*;


public class TorchBert {
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

    public ArrayList<Classifications> predict(List<String> inputs)
            throws MalformedModelException, ModelNotFoundException, IOException,
            TranslateException {

        //TODO: cab be change only for one function.
        ArrayList<Classifications> predicts = new ArrayList<>();
        for (String input : inputs) {
            Classifications res = predictor.predict(input);
            predicts.add(res);
        }

        return predicts;
    }

}