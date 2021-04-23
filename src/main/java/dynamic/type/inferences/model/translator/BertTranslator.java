package dynamic.type.inferences.model.translator;

import ai.djl.Device;
import ai.djl.modality.Classifications;
import ai.djl.modality.nlp.SimpleVocabulary;
import ai.djl.modality.nlp.bert.BertToken;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.pytorch.engine.PtNDArray;
import ai.djl.translate.Batchifier;
import ai.djl.translate.StackBatchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import com.intellij.navigation.NavigationItem;
import com.jetbrains.python.psi.PyFunction;
import dynamic.type.inferences.GlobalProjectInstances;
import dynamic.type.inferences.model.runner.Tokenizer.ModelBertFullTokenizer;
import org.apache.commons.lang.StringUtils;
import org.javatuples.Triplet;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BertTranslator implements Translator<String, Classifications> {

    private final ModelBertFullTokenizer tokenizer;
    private final SimpleVocabulary vocab;
    private final List<String> ranks;

    private List<String> unparsedParameters;
    private PyFunction pyFunction;

    public BertTranslator(ModelBertFullTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.vocab = tokenizer.getVocabulary();
        this.ranks = GlobalProjectInstances.getRanksFromFile();
        this.pyFunction = null;
        this.unparsedParameters = null;
    }

    @Override
    public Batchifier getBatchifier() {
        return new StackBatchifier();
    }

    public int getFunctionParametersLength() {
        return unparsedParameters != null ? unparsedParameters.size() : 0;
    }

    /**
     * Function set is called in the TorchBert method before starting predictions.
     * Calling this method inside of pipeline will lead to predicting results of the another function.
     *
     * @param pyFunction is instance of called Function
     */
    public void setFunctionForBertModel(PyFunction pyFunction) {
        this.pyFunction = pyFunction;
        this.unparsedParameters = getPyFunctionParametersAsStrings(pyFunction);
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        BertToken token = tokenizer.encode(input);
        List<String> tokens = token.getTokens();

        while (tokens.size() < GlobalProjectInstances.BERT_LIMITATION)
            tokens.add("<pad>");

//         DJL doesn't support token indices. It is crucial for our model
//         because input variables are analyzed... So, taking input variables
//         and their indices was done by hands...

        unparsedParameters = getPyFunctionParametersAsStrings(pyFunction);

        // allTokens are sorted by token start
        List<Triplet<String, Integer, Integer>> allTokens = indexTokens(tokens, input);
        List<Triplet<String, Integer, Integer>> functionTokens = indexFunctionTokens(allTokens, pyFunction.getName());

//        parametersTokens can be null in case when function doesn't have parameters,
//        but this step is checked on the side of plugin (we simply don't call predictBatch method).
//        Anyway, for safety, we check it.
        if (functionTokens != null) {
            List<Triplet<String, Integer, Integer>> parametersTokens = indexParameters(allTokens, functionTokens);

            boolean[] IDMask = createIDMask(allTokens, parametersTokens);
            long[] indices = tokens
                    .stream()
                    .mapToLong(vocab::getIndex)
                    .toArray();
            long[] attentionMask = new long[tokens.size()];
            Arrays.fill(attentionMask, 1);

            NDManager manager = ctx.getNDManager();
            NDArray indicesArray = manager.create(indices).toDevice(Device.cpu(), false);
            NDArray attentionMaskArray = manager.create(attentionMask).toDevice(Device.cpu(), false);

            PtNDArray nDMask = (PtNDArray) manager.create(IDMask);
            PtNDArray nDMaskNullShape = (PtNDArray) manager.create(IDMask);
            NDList inputList = new NDList();

            inputList = inputList.toDevice(Device.cpu(), false);
            inputList.add(indicesArray.squeeze());
            inputList.add(attentionMaskArray.squeeze());
            inputList.add(nDMask.squeeze().toDevice(Device.cpu(), false));
            inputList.add(nDMaskNullShape.squeeze().toDevice(Device.cpu(), false));
            return inputList.toDevice(Device.cpu(), false);
        } else
            return new NDList();
    }

    private List<Triplet<String, Integer, Integer>> indexParameters(
            List<Triplet<String, Integer, Integer>> allTokens,
            List<Triplet<String, Integer, Integer>> functionTokens) {
//        functionTokens are already sorted
        int lastParameterPosition = functionTokens.get(functionTokens.size() - 1).getValue2();

//         Firstly, get elements that occur after function init and finish with "):" <- function def end
//         we guarantee that this will happen. Otherwise, function is not still defined
//         and this situation is handled by PSI element.
        List<Triplet<String, Integer, Integer>> parameters = new ArrayList<>();
        for (int i = 0; i < allTokens.size() - 2; i++) {
            Triplet<String, Integer, Integer> currentToken = allTokens.get(i);
            Triplet<String, Integer, Integer> closeBracketCheck = allTokens.get(i + 1);
            Triplet<String, Integer, Integer> colonCheck = allTokens.get(i + 2);
            if (currentToken.getValue1() > lastParameterPosition) {
                parameters.add(currentToken);
                if (closeBracketCheck.getValue0().equals(")") &&
                        colonCheck.getValue0().equals(":"))
                    break;
            }
        }
//        Secondly, remove elements, that are not parameters (can be defined types or values)
//         The only way is to check signs from ":" or "=" till the "," or end of list.
//         After removing types and values delete commas.
//
//         Example of handling all needed cases:
//         def abc(c1, u2, q3: str, a4="acsca", b5:list=(1,((2),{3}),4)):
        List<Triplet<String, Integer, Integer>> found = new ArrayList<>();
        for (int i = 0; i < parameters.size() - 1; i++) {
            String currentElemName = parameters.get(i).getValue0();
            //also, we suggest that text of function is finished
            // (what is purpose to use documentation/type hinting for not finished function)
            // there can be some crazy guy who would init list of lists of lists of ...
            if (currentElemName.equals(":") || currentElemName.equals("=")) {
                int openBracketsCount = 0;
                int counter = 1;
                found.add(parameters.get(i));
                Triplet<String, Integer, Integer> nextElem = parameters.get(i + 1);
                while (!nextElem.getValue0().equals(",")) {
                    int index = i + counter;
                    if (index < parameters.size()) {
                        nextElem = parameters.get(index);
                        //list case
                        if (nextElem.getValue0().equals("(")) {
                            found.add(nextElem);
                            openBracketsCount += 1;
                            int bracketCounter = 1;
                            while (openBracketsCount != 0) {
                                nextElem = parameters.get(index + bracketCounter);
                                if (nextElem.getValue0().equals("("))
                                    openBracketsCount += 1;
                                if (nextElem.getValue0().equals(")"))
                                    openBracketsCount -= 1;
                                found.add(nextElem);
                                bracketCounter += 1;
                            }
                        }
                        found.add(nextElem);
                        counter += 1;
                    } else
                        break;
                }
            }
            parameters.removeAll(found);
        }
        // now most part of brackets is removed. Remove all commas and just in case brackets.
        return parameters
                .stream()
                .filter(elem -> {
                    String elemName = elem.getValue0();
                    return !elemName.equals(",") &&
                            !elemName.equals("}") &&
                            !elemName.equals("{") &&
                            !elemName.equals(")") &&
                            !elemName.equals("(") &&
                            !elemName.equals("[") &&
                            !elemName.equals("]");
                })
                .collect(Collectors.toList());
    }

    @Override
    public Classifications processOutput(TranslatorContext ctx, NDList list) {
        NDArray raw = list.get(0);
//        NDArray computed = raw.exp().div(raw.exp().sum(new int[]{0}, true));
        return new Classifications(ranks, raw);
    }

    private List<Triplet<String, Integer, Integer>> indexFunctionTokens(List<Triplet<String, Integer, Integer>> tokens, String funcName) {
        //sort tokens and remove empty that are ["",0,0]
        tokens = tokens
                .stream()
                .filter(elem -> !elem.getValue0().equals(""))
                .sorted(Comparator.comparing(Triplet::getValue1)).collect(Collectors.toList());

        // function name start after "def" and finishes with "("
        // as a reminder - we are sure that function is finished
        List<Triplet<String, Integer, Integer>> result = new ArrayList<>();
        for (Triplet<String, Integer, Integer> token : tokens) {
            if (token.getValue0().equals("("))
                break;
            result.add(token);
        }
        //remove "def"
        return result
                .stream()
//              we are sure that there will be token "def" token
                .filter(elem -> !elem.getValue0().equals("def"))
                .collect(Collectors.toList());
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
//      model takes only first occurrence of token
//      cannot be done during loop above, because length of token is unknown
        return cancelMultipleTrue(mask);
    }

    private boolean[] cancelMultipleTrue(boolean[] mask) {
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                int start = i;
                int end = start;
                int incr = 1;
                boolean next = mask[i + incr];
                while (next) {
                    incr = incr + 1;
                    next = mask[i + incr];
                    end = start + incr;
                }
                if (end != start)
                    Arrays.fill(mask, start + 1, end, false);
                i = start + incr;
            }
        }
        return mask;
    }

    private List<String> getPyFunctionParametersAsStrings(PyFunction pyFunction) {
        return Arrays.stream(pyFunction
                .getParameterList()
                .getParameters())
                .map(NavigationItem::getName)
                .collect(Collectors.toList());
    }

}