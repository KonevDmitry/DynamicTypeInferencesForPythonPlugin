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
import ai.djl.translate.TranslateException;
import com.dropbox.core.DbxException;
import com.jetbrains.python.psi.PyFunction;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.model.loader.BertModelLoader;
import dynamic.type.inferences.model.runner.tokenizer.ModelBertFullTokenizer;
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


/**
 * Main class for working with model.
 */
public class TorchBert {

    private boolean initialized = false;
    private Predictor<String, Classifications> predictor;
    private BertTranslator bertTranslator;

    private final Object sharedObject = new Object();
    private final BertModelLoader loader = new BertModelLoader(sharedObject);

    /**
     * Method for checking when model is initialized and sending notifications to other threads
     *
     * @return if model is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Typical setter. Use when model is loaded
     *
     * @param initialized value to set
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * Simple builder
     */
    public TorchBert() {
    }

    /**
     * Model initialization. Actually, the integration of plugin development and DJL
     * is quite terrible. Some models are initialised in a "specific" way. This is handled only
     * with try-finally (this is from official documentation)
     * Official solution: @link {https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html}
     * No comments...
     * <p>
     * Exceptions:
     *
     * @throws IOException             - can occur from reading model vocabulary; token from DropBox
     * @throws MalformedModelException - loading model with DJL
     * @throws ModelNotFoundException  - the same
     * @throws URISyntaxException      - parsing URI from string for reading files data
     * @throws DbxException            - DropBox issues
     * @throws InterruptedException    thread problems
     */
    public synchronized void modelInit() throws IOException, MalformedModelException,
            ModelNotFoundException, URISyntaxException, DbxException, InterruptedException {
//        Official "not a bug a feature"
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(current);
        } finally {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

//            All processes are run at start-up including checking if the model is loaded.
//            If model exists - do needed work: create predictor (instance for predicting)
//            and notify threads about model preparedness
            String modelPath = GlobalProjectInstances.MODEL_PATH_FOR_WORK.replaceFirst("file:/", "");
            modelPath = modelPath.contains(":/") ? modelPath: "/"+ modelPath;
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                createPredictorAndSetInitialized();
            } else {
//                If there is no model - firstly load it and do the same as written above
                loader.loadTo();
                synchronized (sharedObject) {
                    createPredictorAndSetInitialized();
                }
            }
        }
    }

    /**
     * Predictor creation for stable model work.
     * <p>
     * Predictor process includes correct load within DJL model:
     * vocabulary initialization, tokenizer creation, model initialization according to mentioned steps.
     * Throws:
     *
     * @throws IOException             If vocabulary (file with recognizable tokens) not found
     * @throws ModelNotFoundException  DJL configuration did not find correct model for work. Never should happen
     * @throws MalformedModelException The same
     * @throws DbxException            DropBox issue. More details below
     */
    public void createPredictor() throws IOException, ModelNotFoundException,
            MalformedModelException, DbxException {
        try {
//            Create model vocabulary and add reserved tokens.
            BufferedReader brVocab = new BufferedReader(
                    new InputStreamReader(GlobalProjectInstances.URL_VOCAB.openStream()));

            SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                    .optMinFrequency(1)
                    .add(brVocab
                            .lines()
                            .collect(Collectors.toList()))
                    .optReservedTokens(new ArrayList<>() {{
                        add("<s>");
                        add("</s>");
                        add("<pad>");
                    }})
                    .build();

//            Create tokenizer from the vocabulary and create translator. About the last one:
//            more details in class itself.
            ModelBertFullTokenizer tokenizer = new ModelBertFullTokenizer(vocabulary);
            bertTranslator = new BertTranslator(tokenizer);
;
//            Define criteria for DJL model initialization
            Criteria<String, Classifications> criteria =
                    Criteria.builder()
                            .optApplication(Application.NLP.SENTIMENT_ANALYSIS)
                            .optDevice(Device.cpu())
                            .setTypes(String.class, Classifications.class)
                            .optModelUrls(GlobalProjectInstances.MODEL_PATH_FOR_WORK)
                            .optTranslator(bertTranslator)
                            .build();
//            Finally, create model and it's predictor.
            ZooModel<String, Classifications> model = ModelZoo.loadModel(criteria);
            predictor = model.newPredictor(bertTranslator);
        } catch (EngineException ignored) {
//            Finally, default DJL config defines that config can be wrong.
//            This never should happen, but if something will go wrong -> reload model and try
//            one more time to define predictor.
            loader.loadTo();
            synchronized (sharedObject) {
                createPredictorAndSetInitialized();
            }
        }
    }

    /**
     * The most needed and used method inside the whole project.
     * Predicts top-5 variable types for each parameter for function call.
     * Method is used for type hinting and documentation providing.
     *
     * @param pyFunction is a function that parameters will be suggested
     * @return List of possible variants containing DJL Classification class.
     * Classification is a tuple of 2 values:
     * type (in our case is a string instance),
     * probability - float value.
     * If described below error happens, then is returned null
     * @throws TranslateException error during build-in prediction process. Never should happen
     */
    public List<Classification> predictOne(PyFunction pyFunction) throws TranslateException {
        try {
//            Firstly, remove if something before "def <func_name>" occurs.
//            Model does not like it.
            String input = prepareFunctionCode(pyFunction);
//            BERT limitation is 512 tokens. For such purpose we decided to cut 512 symbols of text
//            to be sure that everything in further preprocessing will go correct.
//            Moreover, if something is cut - there will not be much difference how much to cut
            input = input.substring(0,
                    Math.min(GlobalProjectInstances.BERT_LIMITATION, input.length()));
//            Set current function for preprocessing
            bertTranslator.setFunctionForBertModel(pyFunction);
//            If function has parameters
            if (bertTranslator.getFunctionParametersLength() > 0) {
//                Get all predictions for all variables
                List<Classifications> allPredicts = predictor.batchPredict(new ArrayList<>(Collections.singleton(input)));
//                If everything is correct
                if (allPredicts.size() > 0) {
//                    Predictions look in a next way. Example for 2 variables:
//                              first row is all top1 - def func(someType1,someType2)
//                              second row is all top2 - def func(someType3,someType4)
//                              ...
//                              fifth row is all top5 - def func(someTypeN-1,someTypeN)
//
//                    Result at topK step is represented as a string with average probability of all topK parameters
//                    Probability is not printed, but may be someone will need it
                    String[] finalNames = new String[GlobalProjectInstances.MAX_VALUES_TO_SHOW];
                    Arrays.fill(finalNames, "");
                    double[] averageScore = new double[GlobalProjectInstances.MAX_VALUES_TO_SHOW];
                    Arrays.fill(averageScore, 0.0F);
//                    Continuing example above:
//                    allPredicts - variable meaning predicts for 2 parameters.
                    for (int k = 0; k < allPredicts.size(); k++) {
//                        For current parameter get top5 values and put it in final list as a string
//                        joined with comma
                        List<Classification> top5 = allPredicts.get(k).topK(GlobalProjectInstances.MAX_VALUES_TO_SHOW);
//                        Initially, we have:
//                          Top1: "" -> 0.0
//                          Top2: "" -> 0.0
//                          Top3: "" -> 0.0
//                          Top4: "" -> 0.0
//                          Top5: "" -> 0.0
                        for (int i = 0; i < top5.size(); i++) {
                            Classification currentElem = top5.get(i);
                            finalNames[i] = k == allPredicts.size() - 1 ?
                                    finalNames[i].concat(currentElem.getClassName()) :
                                    finalNames[i].concat(currentElem.getClassName()).concat(", ");
                            averageScore[i] += currentElem.getProbability() / GlobalProjectInstances.MAX_VALUES_TO_SHOW;
//                          After first step prediction will look like:
//                          Top1: "type1," -> some value
//                          Top2: "type2," -> some value
//                          Top3: "type3," -> some value
//                          Top4: "type4," -> some value
//                          Top5: "type5," -> some value

//                            And so on. For the last elem no comma needed.
                        }
                    }
//                    Concatenated results and their recalculated probability form new Classifications
                    List<String> finalNamesList = Arrays.asList(finalNames);
                    List<Double> averageScoreList = DoubleStream.of(averageScore).boxed().collect(Collectors.toList());
                    Classifications finalRes = new Classifications(finalNamesList, averageScoreList);
//                    Finally, return them as a topK list (nothing is broken)
                    return finalRes.topK(GlobalProjectInstances.MAX_VALUES_TO_SHOW);
                }
            }
        } catch (TranslateException ignored) {
        }
//        Finally, if error occurs or there are no parameters to predict - return null
        return null;
    }

    /**
     * Some functions have decorators. Specially built-in libraries have them. They are not needed for our model.
     * We remove them from pyFunction text because did not find a way to get text without them.
     * To be more specific, we decided to remove everything before "def <func_name>"
     *
     * @param pyFunction given function
     * @return function string without decorators and others that occur before "def <func_name>"
     */
    private String prepareFunctionCode(PyFunction pyFunction) {
        String pyFunctionText = pyFunction.getNavigationElement().getText();
        String pyFunctionName = pyFunction.getName();
//        If everything is fine, then ...
        if (pyFunctionName != null) {
//            Remove everything before "def <func_name>"
            String searchableString = "def ".concat(pyFunction.getName());
            return pyFunctionText.substring(pyFunctionText.indexOf(searchableString));
        }
        // We use plugin only for ready functions. But for safety will return all text if something goes wrong
        // in such a case a predictor may crush.
        return pyFunction.getNavigationElement().getText();
    }

    /**
     * Method that uses mentioned earlier methods:
     * {@link TorchBert#createPredictor()} and
     * {@link TorchBert#setInitialized(boolean)}
     * <p>
     * Method is used for removing duplicate code. Exceptions below are caused from mentioned above methods.
     *
     * @throws ModelNotFoundException  DJL configuration did not find correct model for work. Never should happen
     * @throws MalformedModelException The same
     * @throws IOException             If vocabulary (file with recognizable tokens) not found
     * @throws DbxException            DropBox issue. More details below
     */
    private void createPredictorAndSetInitialized() throws ModelNotFoundException,
            MalformedModelException, IOException, DbxException {
        createPredictor();
        setInitialized(true);
    }
}