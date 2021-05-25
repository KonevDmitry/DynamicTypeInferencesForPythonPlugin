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
import tfLite.GPT2Tokenizer;
import org.javatuples.Triplet;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
    public NDList processInput(TranslatorContext ctx, String input) throws IOException {
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
            List<Triplet<String, Integer, Integer>> parametersTokens = indexParameters(pyFunction, allTokens);

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
     * @param allTokens are all tokens that we already have
     * @return All indexed tokens of parameters
     */
    private List<Triplet<String, Integer, Integer>> indexParameters(
            PyFunction pyFunction,
            List<Triplet<String, Integer, Integer>> allTokens) throws IOException {
//        Algorithm: from PyCharm we now names of the parameters of current function.
//        The idea is tokenize each of them and find subsequence in all tokens.

//        The problem is that GPT2 tokenizer has an "interesting principles of work:"
//          Example below is just for illustration:
//          Let's say, we have a variable some_variable_with_value
//          When it tokenizes value inside of context, with no space before (it looks like "some_variable_with_value")
//          then the tokens will be: "some", "variable", "with", "value"
//        But inside the Python code it will look like in a next way:
//          "some", "var", "iable", "with", "value"
//        It happens so because of empty space before variable.
//        In such a case, we try tokenizing " some_variable_with_value"

//        Get parameters, tokenizer and names of tokens
        List<String> pyCharmParameters = getPyFunctionParametersAsStrings(pyFunction);
        GPT2Tokenizer gpt2Tokenizer = new GPT2Tokenizer(GlobalProjectInstances.loadBpeRanks());
        List<String> tokensNames = allTokens.stream().map(Triplet::getValue0).collect(Collectors.toList());

        List<Triplet<String, Integer, Integer>> parameters = new ArrayList<>();
//        Now find tokens for all found parameters
        for (String param : pyCharmParameters) {
//            Firstly, without space case
            List<String> gptNoSpaceTokens = gpt2Tokenizer.encode(param);
            int index = Collections.indexOfSubList(tokensNames, gptNoSpaceTokens);
//            If sublist is found, then add it
            if (index > 0) {
                List<Triplet<String, Integer, Integer>> sublist = allTokens.subList(index, index + gptNoSpaceTokens.size());
                parameters.addAll(sublist);
            } else {
//            Now cases when user doesn't use refactoring (wring tokens appear)
                List<String> gptSpaceTokens = gpt2Tokenizer.encode(" " + param);
//                For such tokenizing appears Ġ which we need to replace and remove empty (the space itself)
                gptSpaceTokens = gptSpaceTokens
                        .stream()
                        .map(elem -> elem.replaceAll("Ġ", ""))
                        .filter(elem -> !elem.isEmpty())
                        .collect(Collectors.toList());
                index = Collections.indexOfSubList(tokensNames, gptSpaceTokens);
//                If something went wrong even here, then we can do nothing
                if (index > 0) {
                    List<Triplet<String, Integer, Integer>> sublist = allTokens.subList(index, index + gptSpaceTokens.size());
                    parameters.addAll(sublist);
                }
            }
        }
        return parameters;
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
//        The idea is searching for token in original text. We take tokens without G, search
//        them in original text and remember them in recognizable format by model: <name, startIndex, endIndex>

//        Remove from tokens Ġ. After that create a map that will represent
//        token with it's amount of occurrences. The tokens are sorted, so we can be sure that
//        there will not be overlap in searches.
        List<String> noGTokens = tokens
                .stream()
                .map(elem -> elem.replaceAll("Ġ", ""))
                .collect(Collectors.toList());
        int tokenStart = 0;
        List<Triplet<String, Integer, Integer>> finalTokens = new ArrayList<>();
        for (String token : noGTokens) {
            int currentInd = input.indexOf(token);
//            Check if everything is Ok.
            if (currentInd != -1) {
                tokenStart += currentInd;
                int tokenEnd = tokenStart + token.length();
                Triplet<String, Integer, Integer> elem = new Triplet<>(token, tokenStart, tokenEnd);
                finalTokens.add(elem);
//                There are symbols that are accounted as special. For example: "=("
//                Such substring should be represented as "=\("
                token = token.replaceAll("[^a-zA-Z0-9]", "\\\\$0");
//                Remove from original string found occurrence
                input = input.replaceFirst(token, "");
                if (input.charAt(0) == ' ')
                    input = input.replaceFirst(" ", "");
                tokenStart = tokenEnd + 1;
            }
        }
//        Finally, sort tokens by their order
        finalTokens.sort(Comparator.comparing(Triplet::getValue1));
        return finalTokens;
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
        allTokens = allTokens.stream().filter(elem -> !elem.getValue0().equals("")).collect(Collectors.toList());
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