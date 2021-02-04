package dynamic.type.inferences.modelWorker.runner;

import dynamic.type.inferences.modelWorker.translator.BertTranslator;
import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.ModelException;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class BertLoader {
    private final String modelPath;
    private final String vocabularyPath;

    private Predictor<String, Classifications> predictor;

    public BertLoader() {
        modelPath = BertLoader
                .class
                .getResource("/model/pretrainedTF/model")
                .getPath();

        vocabularyPath = BertLoader
                .class
                .getResource("/model/pretrainedTF/model/vocab.txt")
                .getPath();
    }

    public void modelInit() throws IOException, MalformedModelException, ModelNotFoundException {
        BufferedReader br = new BufferedReader(new FileReader(vocabularyPath));

        SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                .optMinFrequency(1)
                .add(br
                        .lines()
                        .collect(Collectors.toList()))
                .optUnknownToken("[UNK]")
                .build();

        BertFullTokenizer tokenizer = new BertFullTokenizer(vocabulary, true);

        BertTranslator translator = new BertTranslator(tokenizer, vocabularyPath);

        Criteria<String, Classifications> criteria =
                Criteria.builder()
                        .optApplication(Application.NLP.WORD_EMBEDDING)
                        .setTypes(String.class, Classifications.class)
                        .optModelUrls(modelPath)
                        .optTranslator(translator)
                        .optProgress(new ProgressBar())
                        .build();

        ZooModel<String, Classifications> model = ModelZoo.loadModel(criteria);
        predictor = model.newPredictor(translator);
    }

//    public static void main(String[] args) throws IOException, ModelException, TranslateException {
//        List<String> inputs = new ArrayList<>();
//        inputs.add("Sample input.");
//        inputs.add("I'm so happy");
//        inputs.add("I hate everything(((");
//        inputs.add("DJL is awesome!!!");
//        Classifications[] results = predict(inputs);
//
//        if (results != null) {
//            for (int i = 0; i < inputs.size(); i++) {
//                System.out.println("Prediction for: " + inputs.get(i) + "\n" + results[i].toString());
//            }
//        }
//    }

    public ArrayList<Classifications> predict(List<String> inputs)
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

//        String modelUrl = "file:///home/dmitry/IdeaProjects/DynamicTypeInferencesForPythonPlugin/bertChecking/sources/model/pretrained/model";

//        String modelUrl = System.getProperty("user.dir").concat("/src/main/resources/model/pretrainedTF/model");
//        vocabularyPath = System.getProperty("user.dir").concat("/src/main/resources/model/pretrainedTF/vocab.txt");

        ArrayList<Classifications> predicts = new ArrayList<>();
        for (String input : inputs) {
            Classifications res = predictor.predict(input);
            predicts.add(res);
        }

        return predicts;
    }

}