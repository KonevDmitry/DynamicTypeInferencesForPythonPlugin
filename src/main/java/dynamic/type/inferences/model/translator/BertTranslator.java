package dynamic.type.inferences.model.translator;

import ai.djl.Device;
import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertToken;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.pytorch.engine.PtNDArray;
import ai.djl.translate.Batchifier;
import ai.djl.translate.StackBatchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.model.runner.Tokenizer.ModelBertFullTokenizer;
import org.apache.commons.lang.StringUtils;
import org.javatuples.Triplet;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BertTranslator implements Translator<String, Classifications> {

    private final ModelBertFullTokenizer tokenizer;
    private final SimpleVocabulary vocab;
    private final List<String> ranks;

    public BertTranslator(ModelBertFullTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.vocab = tokenizer.getVocabulary();
        this.ranks = GlobalProjectInstances.getRanksFromFile();
    }

    @Override
    public Batchifier getBatchifier() {
        return new StackBatchifier();
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        BertToken token = tokenizer.encode(input);
        List<String> tokens = token.getTokens();

        while (tokens.size() < GlobalProjectInstances.BERT_LIMITATION)
            tokens.add("<pad>");

        // DJL doesn't support token indices. It is crucial for our model
        // because input variables are analyzed... So, taking input variables
        // and their indices was done by hands...

        List<String> unparsedParameters = parseParametersFromString(input);
        List<Triplet<String, Integer, Integer>> finalTokens = indexTokens(tokens, input);
        List<Triplet<String, Integer, Integer>> parametersWithPositions = indexTokens(unparsedParameters, input);
        List<Triplet<String, Integer, Integer>> tokensForParameters = getTokensForParameters(finalTokens, parametersWithPositions);

        boolean[] IDMask = createIDMask(finalTokens, tokensForParameters);
        long[] indices = tokens.stream().mapToLong(vocab::getIndex).toArray();
        long[] attentionMask = new long[tokens.size()];
        Arrays.fill(attentionMask, 1);

        NDManager manager = ctx.getNDManager();
        NDArray indicesArray = manager.create(indices).toDevice(Device.cpu(), false);
        NDArray attentionMaskArray = manager.create(attentionMask).toDevice(Device.cpu(), false);

        PtNDArray nDMask = (PtNDArray) manager.create(IDMask, new Shape(1, IDMask.length));
        PtNDArray nDMaskNullShape = (PtNDArray) manager.create(IDMask);

        NDList inputList = new NDList();
        inputList = inputList.toDevice(Device.cpu(), false);
        inputList.add(indicesArray.squeeze());
        inputList.add(attentionMaskArray.squeeze());
        inputList.add(nDMask.squeeze().toDevice(Device.cpu(), false));
        inputList.add(nDMaskNullShape.squeeze().toDevice(Device.cpu(), false));

        return inputList.toDevice(Device.cpu(), false);
    }

    @Override
    public Classifications processOutput(TranslatorContext ctx, NDList list) {
        NDArray raw = list.get(0);
        NDArray computed = raw.exp().div(raw.exp().sum(new int[]{0}, true));
        return new Classifications(ranks, computed);
    }

    private List<String> parseParametersFromString(String input) {
        input = input.replaceAll("\n", " ");
        Pattern defExtractPattern = Pattern.compile("(?<=\\bdef \\b).*?(?=->|\\):)");
        Matcher matcher = defExtractPattern.matcher(input);
        if (matcher.find()) {
            String stringVar = matcher.group();
            stringVar = stringVar.substring(stringVar.indexOf("("));
            stringVar = stringVar.replaceAll("[()]", " ");
            stringVar = stringVar.replaceAll("(?<=\\b=\\b).*?(?=,|\\z)", "");
            stringVar = stringVar.replaceAll("=", "");
            return Arrays.stream(stringVar
                    .split(","))
                    .map(elem -> {
                        int lastIndex = elem.lastIndexOf(":");
                        return lastIndex > 0 ? elem.substring(0, lastIndex) : elem;
                    })
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<Triplet<String, Integer, Integer>> indexTokens(List<String> tokens, String input) {
        Integer[] mask = new Integer[input.length()];
        Arrays.fill(mask, 0);

        List<Triplet<String, Integer, Integer>> finalTokens = new ArrayList<>();
        Map<String, Long> tokenCount = tokens
                .stream()
                .map(elem -> elem.replaceAll("Ġ", ""))
                .collect(
                        Collectors.groupingBy(Function.identity(),
                                LinkedHashMap::new,
                                Collectors.counting())
                );

        for (Map.Entry<String, Long> entry : tokenCount.entrySet()) {
            String key = entry.getKey().trim();
            Long value = entry.getValue();
            int counter = 1;
            while (counter <= value) {
                int start = StringUtils.ordinalIndexOf(input, key, counter);
                int end = start + key.length();
                if (canBeIncreased(mask, start, end)) {
                    Triplet<String, Integer, Integer> elem = new Triplet<>(key, start, end);
                    finalTokens.add(elem);
                    increaseValues(mask, start, end);
                }
                counter += 1;
            }
        }
        finalTokens.sort(Comparator.comparing(Triplet::getValue1));
        return finalTokens;
    }

    private boolean canBeIncreased(Integer[] arr, int start, int end) {
        if (start < 0)
            return false;
        for (int i = start; i < end; i++)
            if (arr[i] + 1 > 1)
                return false;
        return true;
    }

    private void increaseValues(Integer[] arr, int start, int end) {
        if (start < 0)
            return;
        for (int i = start; i < end; i++) {
            arr[i] += 1;
        }
    }

    private List<Triplet<String, Integer, Integer>> getTokensForParameters(List<Triplet<String, Integer, Integer>> finalTokens,
                                                                           List<Triplet<String, Integer, Integer>> parametersWithPositions) {

        List<Triplet<String, Integer, Integer>> res = new ArrayList<>();
        parametersWithPositions
                .forEach(param -> {
                    Integer paramStart = param.getValue1();
                    Integer paramEnd = param.getValue2();
                    res.addAll(finalTokens
                            .stream()
                            .filter(elem -> elem.getValue1() >= paramStart && elem.getValue2() <= paramEnd)
                            .collect(Collectors.toList()));
                });
        return res;
    }

    private boolean[] createIDMask(List<Triplet<String, Integer, Integer>> finalTokens,
                                   List<Triplet<String, Integer, Integer>> tokensForParameters) {
        boolean[] mask = new boolean[GlobalProjectInstances.BERT_LIMITATION];
        Arrays.fill(mask, false);
        for (Triplet<String, Integer, Integer> elem : tokensForParameters) {
            Integer paramStart = elem.getValue1();
            Integer paramEnd = elem.getValue2();
            for (int iterator = 0; iterator < finalTokens.size() - 1; iterator++) {
                Triplet<String, Integer, Integer> param = finalTokens.get(iterator);
                Integer start = param.getValue1();
                Integer end = param.getValue2();
                if (start >= paramStart && end <= paramEnd) {
                    mask[iterator + 1] = true;
                    break;
                }
            }
        }
        return mask;
    }
}