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
import dynamic.type.inferences.model.runner.tokenizer.ModelBertFullTokenizer;
import org.apache.commons.lang.StringUtils;
import org.javatuples.Triplet;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Translator interpreters provides model pre-processing and postprocessing functionality.
 * Translator functionality is needed for predictor.
 *
 * @link {https://github.com/deepjavalibrary/djl/blob/master/api/src/main/java/ai/djl/inference/Predictor.java}
 * <p>
 * Our implementation processes input and output that is acceptable
 * by RoBERTa. We couldn't achive identical representation as in original Python code, but results are very close.
 */
public class BertTranslator implements Translator<String, Classifications> {

    private final ModelBertFullTokenizer tokenizer;
    private final SimpleVocabulary vocab;
    private final List<String> ranks;

    private List<String> unparsedParameters;
    private PyFunction pyFunction;

    /**
     * Simple creation of translator from already existing tokenizer.
     * {@link ModelBertFullTokenizer}, where
     *
     * @param tokenizer is the instance of mentioned class
     */
    public BertTranslator(ModelBertFullTokenizer tokenizer) {
        this.tokenizer = tokenizer;
//        Tokenizer already has a vocabulary,there is no need to parse it one again.
        this.vocab = tokenizer.getVocabulary();
//        Get all predictable types from file.
        this.ranks = GlobalProjectInstances.getRanksFromFile();
//        Predictions works for python functions. If something will go wrong, we can always handle that
        this.pyFunction = null;
//        Predictions work for parameters, so we need them for getting their tokens. Details will be below
        this.unparsedParameters = null;
    }

    /**
     * Check {@link Batchifier} or the same but as link
     *
     * @return base stackBatchifier
     * @link {https://github.com/deepjavalibrary/djl/blob/7ada1408dae2547db721b3c1f19ee9a8f2a5e7a9/api/src/main/java/ai/djl/translate/Batchifier.java#L28}
     */
    @Override
    public Batchifier getBatchifier() {
        return new StackBatchifier();
    }

    /**
     * Plugin can be used for functions that do not have parameters.
     * In our case there is nothing to check, but before prediction we check it.
     *
     * @return number of parameters when such exists. Otherwise null
     */
    public int getFunctionParametersLength() {
        return unparsedParameters != null ? unparsedParameters.size() : 0;
    }

    /**
     * Function set is called in the TorchBert method before starting predictions.
     * Here we define current function for predictions: set current function and get her parameters size
     * <p>
     * Calling this method inside of pipeline will lead to predicting results of the another function.
     *
     * @param pyFunction is instance of called Function
     */
    public void setFunctionForBertModel(PyFunction pyFunction) {
        this.pyFunction = pyFunction;
        this.unparsedParameters = getPyFunctionParametersAsStrings(pyFunction);
    }

    /**
     * Simulation of the pre-processing functionality as in our model written in Python.
     *
     * @param ctx   The toolkit for creating the input NDArray
     * @param input The text of the function
     * @return the {@link NDList} after pre-processing
     */
    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
//        Firstly, get all tokens
        BertToken token = tokenizer.encode(input);
        List<String> tokens = token.getTokens();

//        Typical padding until 512 tokens - BERT limitation.
        while (tokens.size() < GlobalProjectInstances.BERT_LIMITATION)
            tokens.add("<pad>");

//         DJL doesn't support token indices. It is crucial for our model
//         because input variables are analyzed... So, taking input variables
//         and their indices was done by hands...

        unparsedParameters = getPyFunctionParametersAsStrings(pyFunction);

//        As mentioned earlier: DJL doesn't have a built-in way for token indexation, so we do it by hands/
//        The most important indexes for us are, obviously, parameters tokens.
//        Our "getting out" algorithm:
//              1) Index all tokens and sort them by their start.
//              2) After that find tokens of current function. As a reminder - we exactly know
//                    function name from JetBrains PyFunction class.
//              3) Finally, get tokens of parameters.
//                  The end of "getting parameters" is string "):"
//                  [this is not a sad face but the end of function].
//                  For better understanding here is the similar example: def func(a:int, b:str, list=(1,2,3)):
//        All steps will be further explained.
//
//        Here we get all tokens, sort them, and find tokens of function name
//        Saving token, its' start and end position was implemented with Triplets
        List<Triplet<String, Integer, Integer>> allTokens = indexTokens(tokens, input);
        List<Triplet<String, Integer, Integer>> functionTokens = indexFunctionTokens(allTokens);

//        Here indexing of parameters happens.
//        They can be null in case when function doesn't have parameters,
//        but this step is checked on the side of plugin (we simply don't call predictBatch method).
//        Anyway, for safety, we check it.
        if (functionTokens != null) {
//            Finally, get tokens for parameters
            List<Triplet<String, Integer, Integer>> parametersTokens = indexParameters(allTokens, functionTokens);

//            Next steps are default steps with filling the attention mask for model.
//            IDMask creates mask of 512 elements filled with false.
//            Places where parameters are found filled with true.
            boolean[] IDMask = createIDMask(allTokens, parametersTokens);

//            Find the indices of words that are met in body of function.
            long[] indices = tokens
                    .stream()
                    .mapToLong(vocab::getIndex)
                    .toArray();

//            Attention mask is a default attribute. For us all indexes are needed, so fill it with 1's
            long[] attentionMask = new long[tokens.size()];
            Arrays.fill(attentionMask, 1);

//            Finally, move all entities to DJL recognizable way via DJL NDManager
//            To be mentioned: all work is done on cpu, because not all users have gpu
//            Also the runnable model was moved to cpu.

//            All initialized below NDArrays and PtNDArrays fully copy
//            the behaviour of model written on Python
            NDManager manager = ctx.getNDManager();
            NDArray indicesArray = manager.create(indices).toDevice(Device.cpu(), false);
            NDArray attentionMaskArray = manager.create(attentionMask).toDevice(Device.cpu(), false);

            PtNDArray nDMask = (PtNDArray) manager.create(IDMask);
            PtNDArray nDMaskNullShape = (PtNDArray) manager.create(IDMask);

//            Finally, put all DJL entities as an input
            NDList inputList = new NDList();
            inputList = inputList.toDevice(Device.cpu(), false);
            inputList.add(indicesArray.squeeze());
            inputList.add(attentionMaskArray.squeeze());
            inputList.add(nDMask.squeeze().toDevice(Device.cpu(), false));
            inputList.add(nDMaskNullShape.squeeze().toDevice(Device.cpu(), false));
            return inputList.toDevice(Device.cpu(), false);
        } else
//            Reminder about case, when function doesn't have parameters.
//            We simply need to return something acceptable by method and model.
            return new NDList();
    }

    /**
     * Function needed for indexing the tokens of parameters, where
     *
     * @param allTokens      are all tokens that we already have
     * @param functionTokens tokens of function name. Needed for small optimization
     * @return All indexed tokens of parameters
     */
    private List<Triplet<String, Integer, Integer>> indexParameters(
            List<Triplet<String, Integer, Integer>> allTokens,
            List<Triplet<String, Integer, Integer>> functionTokens) {
//        Reminder: functionTokens are already sorted, so we can get the last token of function
//        Explanation: depending on the length of the function name, it can be split into a lot of
//        tokens. Let's say, function "geohash." It is split into 2 words: "geo" and "hash"
//        We are sure that before "hash" there are no parameters and that is why we can start
//        searching from the token "hash." We take it's end position.
        int lastFunctionPosition = functionTokens.get(functionTokens.size() - 1).getValue2();

//        Algorithm:
//         Firstly, get elements that occur after function init and finish with "):" <- function def end.
//         We guarantee that this will happen. Otherwise, function is not still defined
//         and this situation is handled by PSI element.
        List<Triplet<String, Integer, Integer>> parameters = new ArrayList<>();
        for (int i = 0; i < allTokens.size() - 2; i++) {
//            That sad smile "):" is separated into 2 tokens: ")" and ":" that
//            and followed sequentially. So, we can get all tokens until such occurrence.
            Triplet<String, Integer, Integer> currentToken = allTokens.get(i);
            Triplet<String, Integer, Integer> closeBracketCheck = allTokens.get(i + 1);
            Triplet<String, Integer, Integer> colonCheck = allTokens.get(i + 2);
//            If current index is more than start of current token, than
//            such tokens suits us
            if (currentToken.getValue1() > lastFunctionPosition) {
                parameters.add(currentToken);
//                If next 2 tokens are ")" and ":" then we are finished
                if (closeBracketCheck.getValue0().equals(")") &&
                        colonCheck.getValue0().equals(":"))
//                    Below will be explained, why we need the last close parenthesis
                    break;
            }
        }
//        Secondly, remove elements, that are not parameters (can be defined types or values)
//         The only way is to check signs from ":" or "=" till the "," or end of list.
//         After removing types and values delete commas.
//
//         Example of handling all needed cases:
//         def abc(c1, u2, q3: str, a4="acsca", b5:list=(1,((2),{3}),4)):
//
//        We cannot simply remove everything by comma or by close parenthesis or by equals sign.
//        The crucial are only close parenthesis. For example above we have next representation as text:
//        def abc(c1, u2, q3: str, a4="acsca", b5:list=(1,((2),{3}),4)
//
//        The solution is finding the last acceptable close parenthesis. Simple count during iteration.
//        As for the last parameter - anyway, it will be the last index and there will not be nowhere to index.


        // We need to check such behaviour, because
        // there can be some crazy guy who would init list of lists of lists of ...

        // Also, we suggest that text of function is finished
        // (what is purpose to use documentation/type hinting for not finished function definition?
        // Even PyCharm does not recognize it as a function)

        List<Triplet<String, Integer, Integer>> found = new ArrayList<>();
        for (int i = 0; i < parameters.size() - 1; i++) {
            String currentElemName = parameters.get(i).getValue0();
            if (currentElemName.equals(":") || currentElemName.equals("=")) {
//                Be ready for parenthesis...
//                Counter is needed for taking next tokens after comma. You will see it later
                int openBracketsCount = 0;
                int counter = 1;
//                We found the pattern where to start removing tokens
                found.add(parameters.get(i));
                Triplet<String, Integer, Integer> nextElem = parameters.get(i + 1);
//                Found occurrence to remove... Oh, god
                while (!nextElem.getValue0().equals(",")) {
//                    Take next index and check if everything is Ok
                    int index = i + counter;
                    if (index < parameters.size()) {
//                        After that take next elements
                        nextElem = parameters.get(index);
//                        List case. Increase bracketCounter if met open parenthesis
//                        If met close parenthesis - decrease
//                        And add all tokens for removal in this interval
                        if (nextElem.getValue0().equals("(")) {
                            found.add(nextElem);
                            openBracketsCount += 1;
                            int bracketCounter = 1;
//                            Find the last acceptable close parenthesis
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
//                        Do not forget about comma itself.
                        found.add(nextElem);
                        counter += 1;
                    }
//                    Happens when last parameter is a list. We did not add last two tokens.
//                    Example was described at line 225 of this file.
                    else
                        break;
                }
            }
//            Remove all found incorrect tokens
            parameters.removeAll(found);
        }
//         Now most part of brackets is removed. Remove all commas and just in case brackets.
//         Should be useless, but just in case.

        ArrayList<String> forbiddenValues = new ArrayList<>() {{
            add(",");
            add("}");
            add("{");
            add(")");
            add("(");
            add("[");
            add("]");
        }};
        return parameters
                .stream()
                .filter(elem -> {
                    String elemName = elem.getValue0();
                    return !forbiddenValues.contains(elemName);
                })
                .collect(Collectors.toList());
    }

    /**
     * Process the output that is DJL NDList to classifications
     *
     * @param ctx  is a context - the toolkit used for post-processing
     * @param list - result after inference
     * @return list of classifications for current function
     */
    @Override
    public Classifications processOutput(TranslatorContext ctx, NDList list) {
        NDArray raw = list.get(0);
        return new Classifications(ranks, raw);
    }

    /**
     * Function for getting indexes of function name.
     *
     * @param tokens are all tokens that were counted before
     * @return list of function name tokens.
     */
    private List<Triplet<String, Integer, Integer>> indexFunctionTokens(List<Triplet<String, Integer, Integer>> tokens) {
//         Firstly, remove empty tokens that are ["",0,0]
        tokens = tokens
                .stream()
                .filter(elem -> !elem.getValue0().equals(""))
                .collect(Collectors.toList());

//         Function name starts after "def" and finishes with "("
//         Reminder - we are sure that function is finished
        List<Triplet<String, Integer, Integer>> result = new ArrayList<>();
        for (Triplet<String, Integer, Integer> token : tokens) {
            if (token.getValue0().equals("("))
                break;
            result.add(token);
        }
//         Remove "def"
        return result
                .stream()
//              We are sure that there will be token "def" token
                .filter(elem -> !elem.getValue0().equals("def"))
                .collect(Collectors.toList());
    }

    /**
     * Method for token indexation. There is no built-in solution, that is why we implemented it by hands.
     * Also, we tried to optimized it...
     *
     * @param tokens all tokens that are represented as a String's
     * @param input  the string body of a function. Needed for finding indexes in original text.
     * @return indexed tokens
     */
    private List<Triplet<String, Integer, Integer>> indexTokens(List<String> tokens, String input) {
//        Idea is searching for token in original text. The main problem is:
//              There can be small variables values like "a", "b", "c" and etc.
//                    They can be net anywhere. Let's say, we have parameters "var" and "a".
//                    "a" is found in both of them
//        Solution is creating a mask that will check where variables are already found.

//        Create mask of input length
        Integer[] mask = new Integer[input.length()];
        Arrays.fill(mask, 0);

//        Remove from tokens Ġ. After that create a map that will represent
//        token with it's amount of occurrences. The tokens are sorted, so we can be sure that
//        there will not be overlap in searches.
        List<Triplet<String, Integer, Integer>> finalTokens = new ArrayList<>();
        Map<String, Long> tokenCount = tokens
                .stream()
                .map(elem -> elem.replaceAll("Ġ", ""))
                .collect(
                        Collectors.groupingBy(Function.identity(),
                                LinkedHashMap::new,
                                Collectors.counting())
                );

//        For each met unique token start searching for it's positions
        for (Map.Entry<String, Long> entry : tokenCount.entrySet()) {
//            Some variable can be met several times (lots of times)
            String key = entry.getKey().trim();
            Long value = entry.getValue();
            int counter = 1;
            while (counter <= value) {
//                ordinalIndex finds the n-th index inside a String
                int start = StringUtils.ordinalIndexOf(input, key, counter);
                int end = start + key.length();
//                Get the borders of a token and check if
//                it is possible to increase value in this borders
                if (canBeIncreased(mask, start, end)) {
//                    If so, then needed token is found. Add it to results and increase values in found borders
                    Triplet<String, Integer, Integer> elem = new Triplet<>(key, start, end);
                    finalTokens.add(elem);
                    increaseValues(mask, start, end);
                }
//                Do this for all findings.
                counter += 1;
            }
        }
//        Finally, sort tokens
        finalTokens.sort(Comparator.comparing(Triplet::getValue1));
        return finalTokens;
    }

    /**
     * Method for checking if values of mask can be increased within defined borders.
     * Was not implemented with true/false, because it was harder...
     *
     * @param arr   the mask itself.
     * @param start token start. In triplet represents the second value.
     * @param end   token end. In triplet represents the third value.
     * @return true if can be increased. Otherwise false.
     * Can be increased if all values within borders are 0's
     */
    private boolean canBeIncreased(Integer[] arr, int start, int end) {
        if (start < 0)
            return false;
        return IntStream.range(start, end).noneMatch(i -> arr[i] + 1 > 1);
    }

    /**
     * If token is met and it's borders suit the mask, then increase values within this borders.
     *
     * @param arr   mask to be increased.
     * @param start token start. In triplet represents the second value.
     * @param end   token start. In triplet represents the second value.
     */
    private void increaseValues(Integer[] arr, int start, int end) {
        if (start < 0)
            return;
        IntStream.range(start, end).forEach(i -> arr[i] += 1);
    }

    /**
     * Creation of a mask that represent where the parameters are placed.
     *
     * @param allTokens           all tokens of a current function
     * @param tokensForParameters tokens of parameters
     * @return mask filled with true's at parameters positions
     */
    private boolean[] createIDMask(List<Triplet<String, Integer, Integer>> allTokens,
                                   List<Triplet<String, Integer, Integer>> tokensForParameters) {
//        Default mask is filled with false
        boolean[] mask = new boolean[GlobalProjectInstances.BERT_LIMITATION];
        Arrays.fill(mask, false);
//        For all found parameters
        for (Triplet<String, Integer, Integer> elem : tokensForParameters) {
            Integer paramStart = elem.getValue1();
            Integer paramEnd = elem.getValue2();
            for (int iterator = 0; iterator < allTokens.size() - 1; iterator++) {
                Triplet<String, Integer, Integer> param = allTokens.get(iterator);
                Integer start = param.getValue1();
                Integer end = param.getValue2();
//                If found token parameter lies inside borders of all tokens, then
//                mark it as suitable inside all tokens (in such a way, we keep position)
                if (start >= paramStart && end <= paramEnd) {
                    mask[iterator + 1] = true;
                    break;
                }
            }
        }
//      Model takes only first occurrence of token. So, we need to cancel
//      all occurrences after the first for all tokens.
//
//      It cannot be done during loop above, because length of token is unknown.
        return cancelMultipleTrue(mask);
    }

    /**
     * If token takes more than one position inside mask -> mask is updated for all token parts as true n times
     * Model needs only first occurrence. This method removes running in a row "true" and leaves only the first one.
     *
     * @param mask is an IDMask
     * @return updated IDMask
     */
    private boolean[] cancelMultipleTrue(boolean[] mask) {
        for (int i = 0; i < mask.length; i++) {
//            If found first occurrence
            if (mask[i]) {
                int start = i;
                int end = start;
                int incr = 1;
//                Take the next element
                boolean next = mask[i + incr];
//                Find the end of "true"'s
                while (next) {
                    incr = incr + 1;
                    next = mask[i + incr];
                    end = start + incr;
                }
//                If there where multiple true -> fill all as false starting from the second
                if (end != start)
                    Arrays.fill(mask, start + 1, end, false);
                i = start + incr;
            }
        }
        return mask;
    }

    /**
     * With the help of JetBrains PyFunction module get all parameters of function
     * and pass their string value.
     *
     * @param pyFunction is the input function
     * @return list of parameters as strings
     */
    private List<String> getPyFunctionParametersAsStrings(PyFunction pyFunction) {
        return Arrays.stream(pyFunction
                .getParameterList()
                .getParameters())
                .map(NavigationItem::getName)
                .collect(Collectors.toList());
    }

}