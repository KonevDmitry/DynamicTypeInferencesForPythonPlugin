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
import com.jetbrains.python.psi.PyFunction;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;


public class TorchBert {

    private boolean initialized = false;
    private Predictor<String, Classifications> predictor;
    private BertTranslator bertTranslator;

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

    public void createPredictor() throws IOException, ModelNotFoundException,
            MalformedModelException, DbxException {
        try {
            BufferedReader brVocab = new BufferedReader(
                    new InputStreamReader(GlobalProjectInstances.URL_VOCAB.openStream()));

            SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                    .optMinFrequency(1)
                    .add(brVocab
                            .lines()
                            .collect(Collectors.toList()))
                    .optReservedTokens(new ArrayList<String>() {{
                        add("<s>");
                        add("</s>");
                        add("<pad>");
                    }})
                    .build();

            ModelBertFullTokenizer tokenizer = new ModelBertFullTokenizer(vocabulary, true);
            bertTranslator = new BertTranslator(tokenizer);

            Criteria<String, Classifications> criteria =
                    Criteria.builder()
                            .optApplication(Application.NLP.SENTIMENT_ANALYSIS)
                            .optDevice(Device.cpu())
                            .setTypes(String.class, Classifications.class)
                            .optModelUrls(GlobalProjectInstances.MODEL_PATH)
                            .optTranslator(bertTranslator)
                            .optProgress(new ProgressBar())
                            .build();
            ZooModel<String, Classifications> model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor(bertTranslator);

        } catch (EngineException ignored) {
            loader.loadTo(GlobalProjectInstances.MODEL_PATH);
            synchronized (sharedObject) {
                createPredictorAndSetInitialized();
            }
        }
    }

    public List<Classification> predictOne(PyFunction pyFunction) throws TranslateException {
        try {
            String input = prepareFunctionCode(pyFunction);
            input = input.substring(0,
                    Math.min(GlobalProjectInstances.BERT_LIMITATION, input.length()));
            bertTranslator.setFunctionForBertModel(pyFunction);
            if (bertTranslator.getFunctionParametersLength() > 0) {
                List<Classifications> allPredicts = predictor.batchPredict(new ArrayList<>(Collections.singleton(input)));
                if (allPredicts.size() > 0) {
                    String[] finalNames = new String[GlobalProjectInstances.MAX_VALUES_TO_SHOW];
                    Arrays.fill(finalNames, "");
                    double[] averageScore = new double[GlobalProjectInstances.MAX_VALUES_TO_SHOW];
                    Arrays.fill(averageScore, 0.0F);
                    for (int k = 0; k < allPredicts.size(); k++) {
                        List<Classification> top5 = allPredicts.get(k).topK(GlobalProjectInstances.MAX_VALUES_TO_SHOW);
                        for (int i = 0; i < top5.size(); i++) {
                            Classification currentElem = top5.get(i);
                            finalNames[i] = k == allPredicts.size() - 1 ?
                                    finalNames[i].concat(currentElem.getClassName()) :
                                    finalNames[i].concat(currentElem.getClassName()).concat(", ");
                            averageScore[i] += currentElem.getProbability() / GlobalProjectInstances.MAX_VALUES_TO_SHOW;
                        }
                    }
                    List<String> finalNamesList = Arrays.asList(finalNames);
                    List<Double> averageScoreList = DoubleStream.of(averageScore).boxed().collect(Collectors.toList());
                    Classifications finalRes = new Classifications(finalNamesList, averageScoreList);
                    return finalRes.topK(GlobalProjectInstances.MAX_VALUES_TO_SHOW);
                }
            }
        } catch (TranslateException ignored) {
            return null;
        }
        return null;
    }

    /**
     * Some functions have decorators. Specially built-in libraries have them. They are not needed for our model.
     * We remove them from pyFunction text because did not find a way to get text without them.
     * To be more specific, we decided to remove everything before "def <func_name>"
     *
     * @param pyFunction given function
     * @return function string without decorators
     */
    private String prepareFunctionCode(PyFunction pyFunction) {
        String pyFunctionText = pyFunction.getNavigationElement().getText();
        String pyFunctionName = pyFunction.getName();
        if (pyFunctionName != null) {
            String searchableString = "def ".concat(pyFunction.getName());
            return pyFunctionText.substring(pyFunctionText.indexOf(searchableString));
        }
        //we use plugin only for ready functions. But for safety will return all text if smth goes wrong
        // in such a case a predictor may crush.
        return pyFunction.getText();
    }

    private void createPredictorAndSetInitialized() throws ModelNotFoundException,
            MalformedModelException, IOException, DbxException {
        createPredictor();
        setInitialized(true);
    }
}