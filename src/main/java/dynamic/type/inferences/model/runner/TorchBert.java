package dynamic.type.inferences.model.runner;

import ai.djl.*;
import ai.djl.basicdataset.CsvDataset;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.pytorch.engine.PtEngine;
import ai.djl.repository.zoo.*;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import dynamic.type.inferences.model.translator.BertTranslator;

public class TorchBert {
    private static Predictor<String, Classifications> predictor;

    public TorchBert() throws MalformedModelException, ModelNotFoundException, IOException {
        modelInit();
    }

    public static void modelInit() throws IOException, MalformedModelException, ModelNotFoundException {
        Engine.debugEnvironment();
        String modelUrl = "file:///home/dmitry/IdeaProjects/DynamicTypeInferencesForPythonPlugin/src/main/resources/data/torchBERT/torchBertModel.zip?artifact_id=distiled&model_name=eeee";
        String vocabularyPath = "/home/dmitry/IdeaProjects/DynamicTypeInferencesForPythonPlugin/src/main/resources/data/torchBERT/vocab.txt";
//        InputStream vocabularyPath = getClass().getResourceAsStream("/data/torchBERT/vocab.txt");
//        Engine.debugEnvironment();
//        System.out.println(Engine.getAllEngines());
        BufferedReader br = new BufferedReader(new FileReader(vocabularyPath));
        SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                .optMinFrequency(1)
                .add(br
                        .lines()
                        .collect(Collectors.toList()))
                .optUnknownToken("[UNK]")
                .build();

        BertFullTokenizer tokenizer = new BertFullTokenizer(vocabulary, true);
        BertTranslator translator = new BertTranslator(tokenizer);

        // Taking only by CPU because not all users will have GPU
        // This setting is also set at configs
        Criteria<String, Classifications> criteria =
                Criteria.builder()
                        .optApplication(Application.NLP.TEXT_CLASSIFICATION)
                        .optDevice(Device.cpu())
                        .setTypes(String.class, Classifications.class)
                        .optModelUrls(modelUrl)
                        .optTranslator(translator)
                        .optProgress(new ProgressBar())
                        .build();

        System.out.println(ModelZoo.listModels());
        ZooModel<String, Classifications> model = ModelZoo.loadModel(criteria);
        predictor = model.newPredictor(translator);

    }

    public static void main(String[] args) throws IOException, ModelException, TranslateException {
        modelInit();

        List<String> inputs = new ArrayList<>();
        inputs.add("Sample input");
        inputs.add("I love you, DJL!");
        inputs.add("I hate everything");
        inputs.add("DJL is awesome. This is amazing");

        ArrayList<Classifications> results = predict(inputs);
        for (int i = 0; i < inputs.size(); i++) {
            System.out.println("Prediction for: " + inputs.get(i) + "\n" + results.get(i).toString());
        }

    }

    public static ArrayList<Classifications> predict(List<String> inputs)
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