package dynamic.type.inferences.model.runner;

import java.io.*;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import ai.djl.*;
import ai.djl.util.*;
import ai.djl.ndarray.*;
import ai.djl.ndarray.types.*;
import ai.djl.inference.*;
import ai.djl.translate.*;
import ai.djl.training.util.*;
import ai.djl.repository.zoo.*;
import ai.djl.modality.nlp.*;
import ai.djl.modality.nlp.qa.*;
import ai.djl.modality.nlp.bert.*;

import com.google.gson.annotations.SerializedName;

import java.nio.charset.StandardCharsets;

public class BertQAMXNet {

    public static void main(String[] args) throws IOException, MalformedURLException, ModelNotFoundException, MalformedModelException, TranslateException {
        String question = "When did BBC Japan start broadcasting?";
        String resourceDocument = "BBC Japan was a general entertainment Channel.\n" +
                "Which operated between December 2004 and April 2006.\n" +
                "It ceased operations after its Japanese distributor folded.";

        QAInput input = new QAInput(question, resourceDocument);

        BertTokenizer tokenizer = new BertTokenizer();
        List<String> tokenQ = tokenizer.tokenize(question.toLowerCase());
        List<String> tokenA = tokenizer.tokenize(resourceDocument.toLowerCase());

        System.out.println("Question Token: " + tokenQ);
        System.out.println("Answer Token: " + tokenA);

        BertToken token = tokenizer.encode(question.toLowerCase(), resourceDocument.toLowerCase(), 384);
        System.out.println("Encoded tokens: " + token.getTokens());
        System.out.println("Encoded token type: " + token.getTokenTypes());
        System.out.println("Valid length: " + token.getValidLength());

        DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/nlp/question_answer/ai/djl/mxnet/bertqa/vocab.json", "build/mxnet/bertqa/vocab.json", new ProgressBar());

        String url = "build/mxnet/bertqa/vocab.json";
        SimpleVocabulary vocabulary = SimpleVocabulary.builder()
                .optMinFrequency(1)
                .addFromCustomizedFile(url, file -> {
                    try {
                        return VocabParser.parseToken(file);
                    } catch (MalformedURLException e) {
                        return Collections.singletonList(String.format("Error:\n %s", e.toString()));
                    }
                })
                .optUnknownToken("[UNK]")
                .build();

        long index = vocabulary.getIndex("car");
        String vocabToken = vocabulary.getToken(2482);
        System.out.println("The index of the car is " + index);
        System.out.println("The token of the index 2482 is " + vocabToken);

        DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/nlp/question_answer/ai/djl/mxnet/bertqa/0.0.1/static_bert_qa-symbol.json", "build/mxnet/bertqa/bertqa-symbol.json", new ProgressBar());
        DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/nlp/question_answer/ai/djl/mxnet/bertqa/0.0.1/static_bert_qa-0002.params.gz", "build/mxnet/bertqa/bertqa-0000.params", new ProgressBar());

        BertTranslator translator = new BertTranslator();
        Criteria<QAInput, String> criteria = Criteria.builder()
                .setTypes(QAInput.class, String.class)
                .optModelPath(Paths.get("build/mxnet/bertqa/")) // Search for models in the build/mxnet/bert folder
                .optTranslator(translator)
                .optProgress(new ProgressBar()).build();

        ZooModel model = ModelZoo.loadModel(criteria);

        String predictResult = null;
        QAInput qaInput = new QAInput(question, resourceDocument);

    // Create a Predictor and use it to predict the output
        try (Predictor<QAInput, String> predictor = model.newPredictor(translator)) {
            predictResult = predictor.predict(qaInput);
        }

        System.out.println(question);
        System.out.println(predictResult);
    }

    public static float[] toFloatArray(List<? extends Number> list) {
        float[] ret = new float[list.size()];
        int idx = 0;
        for (Number n : list) {
            ret[idx++] = n.floatValue();
        }
        return ret;
    }

    public static class VocabParser {
        @SerializedName("idx_to_token")
        List<String> idx2token;

        public static List<String> parseToken(String file) throws MalformedURLException {
            URL url = Paths.get(file).toUri().toURL();
            try (InputStream is = url.openStream();
                 Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return JsonUtils.GSON.fromJson(reader, (Type) VocabParser.class);
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid url: " + url, e);
            }
        }
    }

    public static class BertTranslator implements Translator<QAInput, String> {
        private List<String> tokens;
        private Vocabulary vocabulary;
        private BertTokenizer tokenizer;

        @Override
        public void prepare(NDManager manager, Model model) throws IOException {
            String path = "build/mxnet/bertqa/vocab.json";
            vocabulary =
                    SimpleVocabulary.builder()
                            .optMinFrequency(1)
                            .addFromCustomizedFile(path, file -> {
                                try {
                                    return VocabParser.parseToken(file);
                                } catch (MalformedURLException e) {
                                    return Collections.singletonList(String.format("Error:\n %s", e.toString()));
                                }
                            })
                            .optUnknownToken("[UNK]")
                            .build();
            tokenizer = new BertTokenizer();
        }

        @Override
        public Batchifier getBatchifier() {
            return null;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, QAInput input) {
            BertToken token =
                    tokenizer.encode(
                            input.getQuestion().toLowerCase(),
                            input.getParagraph().toLowerCase(),
                            384);
            // get the encoded tokens that would be used in precessOutput
            tokens = token.getTokens();
            // map the tokens(String) to indices(long)
            List<Long> indices =
                    token.getTokens().stream().map(vocabulary::getIndex).collect(Collectors.toList());
            float[] indexesFloat = toFloatArray(indices);
            float[] types = toFloatArray(token.getTokenTypes());
            int validLength = token.getValidLength();

            NDManager manager = ctx.getNDManager();
            NDArray data0 = manager.create(indexesFloat);
            data0.setName("data0");
            NDArray data1 = manager.create(types);
            data1.setName("data1");
            NDArray data2 = manager.create(new float[]{validLength});
            data2.setName("data2");
            return new NDList(data0, data1, data2);
        }

        @Override
        public String processOutput(TranslatorContext ctx, NDList list) {
            NDArray array = list.singletonOrThrow();
            NDList output = array.split(2, 2);
            // Get the formatted logits result
            NDArray startLogits = output.get(0).reshape(new Shape(1, -1));
            NDArray endLogits = output.get(1).reshape(new Shape(1, -1));
            int startIdx = (int) startLogits.argMax(1).getLong();
            int endIdx = (int) endLogits.argMax(1).getLong();
            return tokens.subList(startIdx, endIdx + 1).toString();
        }
    }
}
