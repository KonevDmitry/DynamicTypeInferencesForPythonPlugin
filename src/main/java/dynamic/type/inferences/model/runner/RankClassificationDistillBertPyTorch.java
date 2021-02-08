package dynamic.type.inferences.model.runner;

import ai.djl.*;
import ai.djl.engine.Engine;
import ai.djl.basicdataset.CsvDataset;
import ai.djl.basicdataset.utils.DynamicBuffer;
import ai.djl.inference.Predictor;
import ai.djl.metric.Metrics;
import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertFullTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Activation;
import ai.djl.nn.Block;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.nn.norm.Dropout;
import ai.djl.repository.zoo.*;
import ai.djl.training.*;
import ai.djl.training.dataset.RandomAccessDataset;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.listener.CheckpointsTrainingListener;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;


public class RankClassificationDistillBertPyTorch {
    public static void main(String[] args) throws MalformedModelException, ModelNotFoundException, IOException, TranslateException {

        System.out.println("You are using: " + Engine.getInstance().getEngineName() + " Engine");

        // MXNet base model
        String modelUrls = "https://resources.djl.ai/test-models/distilbert.zip";
        if ("PyTorch".equals(Engine.getInstance().getEngineName())) {
            modelUrls = "https://resources.djl.ai/test-models/traced_distilbert_wikipedia_uncased.zip";
        }

        Criteria<NDList, NDList> criteria = Criteria.builder()
                .optApplication(Application.NLP.WORD_EMBEDDING)
                .setTypes(NDList.class, NDList.class)
                .optModelUrls(modelUrls)
                .optProgress(new ProgressBar())
                .build();
        ZooModel<NDList, NDList> embedding = ModelZoo.loadModel(criteria);

        Predictor<NDList, NDList> embedder = embedding.newPredictor();
        Block classifier = new SequentialBlock()
                // text embedding layer
                .add(
                        ndList -> {
                            NDArray data = ndList.singletonOrThrow();
                            NDList inputs = new NDList();
                            long batchSize = data.getShape().get(0);
                            float maxLength = data.getShape().get(1);

                            if ("PyTorch".equals(Engine.getInstance().getEngineName())) {
                                inputs.add(data.toType(DataType.INT64, false));
                                inputs.add(data.getManager().full(data.getShape(), 1, DataType.INT64));
                                inputs.add(data.getManager().arange(maxLength)
                                        .toType(DataType.INT64, false)
                                        .broadcast(data.getShape()));
                            } else {
                                inputs.add(data);
                                inputs.add(data.getManager().full(new Shape(batchSize), maxLength));
                            }
                            // run embedding
                            try {
                                return embedder.predict(inputs);
                            } catch (TranslateException e) {
                                throw new IllegalArgumentException("embedding error", e);
                            }
                        })
                // classification layer
                .add(Linear.builder().setUnits(768).build()) // pre classifier
                .add(Activation::relu)
                .add(Dropout.builder().optRate(0.2f).build())
                .add(Linear.builder().setUnits(5).build()) // 5 star rating
                .addSingleton(nd -> nd.get(":,0")); // Take [CLS] as the head
        Model model = Model.newInstance("AmazonReviewRatingClassification");
        model.setBlock(classifier);


        // Prepare the vocabulary
        SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                .optMinFrequency(1)
                .addFromTextFile(String.valueOf(embedding.getArtifact("vocab.txt")))
                .optUnknownToken("[UNK]")
                .build();
// Prepare dataset
        int maxTokenLength = 64; // cutoff tokens length
        int batchSize = 8;
        BertFullTokenizer tokenizer = new BertFullTokenizer(vocabulary, true);
        CsvDataset amazonReviewDataset = getDataset(batchSize, tokenizer, maxTokenLength);
// split data with 7:3 train:valid ratio
        RandomAccessDataset[] datasets = amazonReviewDataset.randomSplit(7, 3);
        RandomAccessDataset trainingSet = datasets[0];
        RandomAccessDataset validationSet = datasets[1];

        CheckpointsTrainingListener listener = new CheckpointsTrainingListener("build/model");
        listener.setSaveModelCallback(
                trainer -> {
                    TrainingResult result = trainer.getTrainingResult();
                    Model trainerModelodel = trainer.getModel();
                    // track for accuracy and loss
                    float accuracy = result.getValidateEvaluation("Accuracy");
                    trainerModelodel.setProperty("Accuracy", String.format("%.5f", accuracy));
                    trainerModelodel.setProperty("Loss", String.format("%.5f", result.getValidateLoss()));
                });
        DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss()) // loss type
                .addEvaluator(new Accuracy())
                .optDevices(Device.getDevices(1)) // train using single GPU
                .addTrainingListeners(TrainingListener.Defaults.logging("build/model"))
                .addTrainingListeners(listener);

        int epoch = 2;

        Trainer trainer = model.newTrainer(config);
        trainer.setMetrics(new Metrics());
        Shape encoderInputShape = new Shape(batchSize, maxTokenLength);
// initialize trainer with proper input shape
        trainer.initialize(encoderInputShape);
        EasyTrain.fit(trainer, epoch, trainingSet, validationSet);
        System.out.println(trainer.getTrainingResult());

        model.save(Paths.get("build/model"), "amazon-review.param");


        String review = "It works great, but it takes too long to update itself and slows the system";
        Predictor<String, Classifications> predictor = model.newPredictor(new MyTranslator(tokenizer));
        System.out.println(predictor.predict(review));
    }

    static CsvDataset getDataset(int batchSize, BertFullTokenizer tokenizer, int maxLength) {
        String amazonReview =
                "https://s3.amazonaws.com/amazon-reviews-pds/tsv/amazon_reviews_us_Digital_Software_v1_00.tsv.gz";
        float paddingToken = tokenizer.getVocabulary().getIndex("[PAD]");
        return CsvDataset.builder()
                .optCsvUrl(amazonReview) // load from Url
                .setCsvFormat(CSVFormat.TDF.withQuote(null).withHeader()) // Setting TSV loading format
                .setSampling(batchSize, true) // make sample size and random access
                .addFeature(
                        new CsvDataset.Feature(
                                "review_body", new BertFeaturizer(tokenizer, maxLength)))
                .addLabel(
                        new CsvDataset.Feature(
                                "star_rating", (buf, data) -> buf.put(Float.parseFloat(data) - 1.0f)))
                .optDataBatchifier(
                        PaddingStackBatchifier.builder()
                                .optIncludeValidLengths(false)
                                .addPad(0, 0, (m) -> m.ones(new Shape(1)).mul(paddingToken))
                                .build()) // define how to pad dataset to a fix length
                .build();
    }
}

final class BertFeaturizer implements CsvDataset.Featurizer {

    private final BertFullTokenizer tokenizer;
    private final int maxLength; // the cut-off length

    public BertFeaturizer(BertFullTokenizer tokenizer, int maxLength) {
        this.tokenizer = tokenizer;
        this.maxLength = maxLength;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void featurize(DynamicBuffer buf, String input) {
        SimpleVocabulary vocab = tokenizer.getVocabulary();
        // convert sentence to tokens (toLowerCase for uncased model)
        List<String> tokens = tokenizer.tokenize(input.toLowerCase());
        // trim the tokens to maxLength
        tokens = tokens.size() > maxLength ? tokens.subList(0, maxLength) : tokens;
        // BERT embedding convention "[CLS] Your Sentence [SEP]"
        buf.put(vocab.getIndex("[CLS]"));
        tokens.forEach(token -> buf.put(vocab.getIndex(token)));
        buf.put(vocab.getIndex("[SEP]"));
    }

}

class MyTranslator implements Translator<String, Classifications> {

    private BertFullTokenizer tokenizer;
    private SimpleVocabulary vocab;
    private List<String> ranks;

    public MyTranslator(BertFullTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        vocab = tokenizer.getVocabulary();
        ranks = Arrays.asList("1", "2", "3", "4", "5");
    }

    @Override
    public Batchifier getBatchifier() {
        return new StackBatchifier();
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        List<String> tokens = tokenizer.tokenize(input);
        float[] indices = new float[tokens.size() + 2];
        indices[0] = vocab.getIndex("[CLS]");
        for (int i = 0; i < tokens.size(); i++) {
            indices[i + 1] = vocab.getIndex(tokens.get(i));
        }
        indices[indices.length - 1] = vocab.getIndex("[SEP]");
        return new NDList(ctx.getNDManager().create(indices));
    }

    @Override
    public Classifications processOutput(TranslatorContext ctx, NDList list) {
        return new Classifications(ranks, list.singletonOrThrow().softmax(0));
    }
}
