package dynamic.type.inferences.model.runner;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.engine.EngineException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.Classifications.Classification;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.intellij.openapi.application.PathManager;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.Tokenizer.ModelBertFullTokenizer;
import dynamic.type.inferences.model.translator.BertTranslator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


public class TorchBert {
    private boolean initialized = false;

    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);

    private static Predictor<String, Classifications> predictor;

    private static final Integer MAX_VALUES_TO_SHOW = 5;
    private static final URL urlVocab = TorchBert.class.getClassLoader().getResource("/data/torchBERT/vocab.txt");
    private static final String modelPath = PathManager.getConfigPath() + "/eeee.pt";

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public TorchBert() {
    }

    public synchronized void modelInit() throws IOException, MalformedModelException,
            ModelNotFoundException, URISyntaxException, DbxException, InterruptedException {
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(current);
        } finally {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                createPredictor(urlVocab, modelPath);
                setInitialized(true);
            } else {
                loader.loadTo(modelPath);
                synchronized (sharedObject) {
                    createPredictor(urlVocab, modelPath);
                    setInitialized(true);
                }
            }
        }
    }

    public void createPredictor(URL url, String path) throws IOException, ModelNotFoundException, MalformedModelException, DbxException {
        try {
            BufferedReader brVocab = new BufferedReader(
                    new InputStreamReader(
                            Objects.requireNonNull(url).openStream()));

            SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                    .optMinFrequency(1)
                    .add(brVocab
                            .lines()
                            .collect(Collectors.toList()))
                    .optUnknownToken("<unk>")
                    .optReservedTokens(new ArrayList<String>() {{
                        add("<s>");
                        add("</s>");
                        add("<pad>");
                        add("<mask>");
                    }})
                    .build();

            ModelBertFullTokenizer tokenizer = new ModelBertFullTokenizer(vocabulary, true);
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
        } catch (EngineException ignored) {
            loader.loadTo(modelPath);
            synchronized (sharedObject) {
                createPredictor(urlVocab, modelPath);
                setInitialized(true);
            }
        }
    }

    public List<Classification> predictOne(String input) throws TranslateException {
        try {
            Classifications res = predictor.predict(input);
            return res.topK(MAX_VALUES_TO_SHOW);
        } catch (TranslateException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<List<Classification>> predictAll(List<String> inputs)
            throws TranslateException {

        List<List<Classification>> predicts = new ArrayList<>();
        for (String input : inputs) {
            Classifications res = predictor.predict(input);
            List<Classification> items = res.items();
            items = res.topK(MAX_VALUES_TO_SHOW);
            predicts.add(items);
        }
        return predicts.size() > MAX_VALUES_TO_SHOW ? predicts.subList(0, MAX_VALUES_TO_SHOW) : predicts;
    }

}