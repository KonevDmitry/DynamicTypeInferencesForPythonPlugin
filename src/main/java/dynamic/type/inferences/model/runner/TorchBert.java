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
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.Tokenizer.ModelBertFullTokenizer;
import dynamic.type.inferences.model.translator.BertTranslator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class TorchBert {

    private boolean initialized = false;
    private Predictor<String, Classifications> predictor;

    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);

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
            File modelFile = new File(GlobalProjectInstances.MODEL_PATH);
            if (modelFile.exists()) {
                createPredictorAndSetInitialized();
            } else {
                loader.loadTo(GlobalProjectInstances.MODEL_PATH);
                synchronized (sharedObject) {
                    createPredictorAndSetInitialized();
                }
            }
        }
    }

    public void createPredictor() throws IOException, ModelNotFoundException, MalformedModelException, DbxException {
        try {
            BufferedReader brVocab = new BufferedReader(
                    new InputStreamReader(GlobalProjectInstances.URL_VOCAB.openStream()));

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
                            .optModelUrls(GlobalProjectInstances.MODEL_PATH)
                            .optTranslator(translator)
                            .optProgress(new ProgressBar())
                            .build();

            ZooModel<String, Classifications> model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor(translator);

        } catch (EngineException ignored) {
            loader.loadTo(GlobalProjectInstances.MODEL_PATH);
            synchronized (sharedObject) {
                createPredictorAndSetInitialized();
            }
        }
    }

    public List<Classification> predictOne(String input) throws TranslateException {
        try {
            Classifications res = predictor.predict(input);
            return res.topK(GlobalProjectInstances.MAX_VALUES_TO_SHOW);
        } catch (TranslateException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void createPredictorAndSetInitialized() throws ModelNotFoundException, MalformedModelException, IOException, DbxException {
        createPredictor();
        setInitialized(true);
    }
}