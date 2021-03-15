# DynamicTypeInferencesForPythonPlugin

![Build](https://github.com/KonevDmitry/DynamicTypeInferencesForPythonPlugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

## Plugin principles of work

When the user installs the plugin, the model is downloaded to the plugin config folder. By default, this is PyCharm config folder:
```~/.config/JetBrains/PyCharm<PyCharmVersion>```. If a programmer uses type hints for variable suggestions for the call of user-defined function during the model load then he
will be notified about it.

### How the model is downloaded?

The model is downloaded from dropbox via dropbox-api. The model itself is downloaded inside [BertModelLoader (
/src/main/java/dynamic/type/inferences/model/loader/BertModelLoader.java). This class is used on project opening
is [StartUpActive.](/src/main/java/dynamic/type/inferences/startUpActive/StartUpActive.java)

### How notifications are working?

Instances of model notifications are declared inside [ModelNotLoadedNotification (
/src/main/java/dynamic/type/inferences/notification/ModelNotLoadedNotification.java) and are called
in [ModelCompletionProvider.](/src/main/java/dynamic/type/inferences/completer/ModelCompletionProvider.java)

Finally, after the model is downloaded, the predictions for user-defined functions will be added to model predictions.

### How the type hint works?

1) All variables and user-defined functions from the whole project are stored as separated maps.
    1) Variables are collected
       via [VariablesVisitor](/src/main/java/dynamic/type/inferences/visitors/VariablesVisitor.java)
    2) User-defined functions are collected
       via [AllUserFunctionVisitor](/src/main/java/dynamic/type/inferences/visitors/AllUserFunctionsVisitor.java)
2) The main work happens
   inside [ModelCompletionProvider.](/src/main/java/dynamic/type/inferences/completer/ModelCompletionProvider.java) that
   is called inside
   [PyVarsForFuncCompleter](/src/main/java/dynamic/type/inferences/completer/PyVarsForFuncCompleter.java)
    1) When the document is changed then the background process of collecting data runs.
    2) The next step is checking if the parent (Goto target) of the current callable function is a user-defined
       function.
    3) If so, then the suitable variables are filtered according to function scope. There is 4 type of variables to
       handle:
        1) Variables inside function argument list. Example: ```def fun(var1, var2)```. For this example var1 and var2
           are needed to be collected. Also, if a function is defined inside class, it contains
        2) If a function is inside a class, then "self" variables can be accepted.
        3) Variables that are defined inside the function body.
        4) Variables from global scope (that are simply defined inside python file and not in function/class)
3) After the suitable variables according to the scope were collected, the suggested model type should be in the top 5
   predictions. For showing the results LookUpElements are used. They can be seen
   in [ModelLookUpElement](/src/main/java/dynamic/type/inferences/lookUpElement/ModelLookUpElement.java). For now, there
   are no predictions for a possible value type, so it can be changed only inside code (line 66
   in [ModelLookUpElement](/src/main/java/dynamic/type/inferences/lookUpElement/ModelLookUpElement.java)).
    1) Variables are separated into 2 classes: suitable and unsuitable. If the amount of suitable variables is more than
       5 then the top 5 elements are taken from here (later will be sorted by a prefix if there are not suitable).
       Otherwise, all possible variables are taken and taken 5-n unsuitable elements, where n is the number of suitable
       variables. Later also will be sorted by a prefix.

### How the documentation prediction works?

The plugin supports predicting the function input and output for documentation providing (```ctrl+mouse```
and ```ctrl+q```
actions). Both actions are defined inside
[ModelDocumentationProvider](/src/main/java/dynamic/type/inferences/documentationProvider/ModelDocumentationProvider.java)
. Documentation providing is supported not only for user-defined functions but also for library methods. It is done
because documentation providing operation is not time-consuming as for type hinting. The full code of the function is
passed as an input for TorchModel. Also, the model predicts variants for python stubs (it can be used for model check).

### This is great but... How does the model work?

When the model is downloaded, the TorchBert is initialized
inside [TorchBert](/src/main/java/dynamic/type/inferences/model/runner/TorchBert.java), where the DJL (Deep Java
Library)
Predictor and [BertTranslator](/src/main/java/dynamic/type/inferences/model/translator/BertTranslator.java) are
initialized. BertTranslator defines how input and output are processed.

As it was mentioned before, all user-defined functions are collected inside a map. During the user work, the plugin
defines if the current callable element is a user-defined function. After that, the whole code of the function is passed
as the model input. The output of the model are possible types of variables that after are passed to already mentioned
LookUpElements and Documentation.

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Verify the [pluginGroup](/gradle.properties), [plugin ID](/src/main/resources/META-INF/plugin.xml)
  and [sources package](/src/main/kotlin).
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html).
- [ ] [Publish a plugin manually](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/publishing_plugin.html)
  for the first time.
- [ ] Set the Plugin ID in the above README badges.
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified
  about releases containing new features and fixes.

<!-- Plugin description -->
This Fancy IntelliJ Platform Plugin is going to be your implementation of the brilliant ideas that you have.

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml) file which will be
extracted by the [Gradle](/build.gradle.kts) during the build process.

To keep everything working, do not remove `<!-- ... -->` sections.
<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:

  <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "
  DynamicTypeInferencesForPythonPlugin"</kbd> >
  <kbd>Install Plugin</kbd>

- Manually:

  Download the [latest release](https://github.com/KonevDmitry/DynamicTypeReferencesForPythonPlugin/releases/latest) and
  install it manually using
  <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

# Papers

## Grapphs and GNN based models

1. [Typilus: Neural Type Hints](https://arxiv.org/abs/2004.10657)
2. [Lambdanet: Probabilistic Type Inference Using Graph Neural Networks](https://openreview.net/forum?id=Hkx6hANtwH)
3. [Inferring Javascript Types Using Graph Neural Networks](https://arxiv.org/abs/1905.06707)

### Probabalistic

4.1. [Advanced Graph-Based Deep Learning for Probabilistic Type Inference](https://arxiv.org/abs/2009.05949)
4.2. [Python Probabilistic Type Inference with Natural Language Support](https://dl.acm.org/doi/10.1145/2950290.2950343)
4.3. [OptTyper: Probabilistic Type Inference by Optimising Logical and Natural Constraints ](https://arxiv.org/abs/2004.00348)

5. [Hoppity: Learning Graph Transformations To Detect And Fix Bugs In Programs](https://openreview.net/forum?id=SJeqs6EFvB) (
   Not Type Inference per se, but is related)

## Embedding + NonGraph NN models

1. [NL2Type: Inferring JavaScript Function Types from Natural Language Information](https://ieeexplore.ieee.org/document/8811893)
2. [Deep Learning Type Inference](https://vhellendoorn.github.io/PDF/fse2018-j2t.pdf)
3. [TypeWriter: Neural Type Prediction with Search-based Validation](https://arxiv.org/abs/1912.03768)
4. [DLTPy: Deep Learning Type Inference Of Python Function Signatures Using Natural Language Context](https://arxiv.org/abs/1912.00680)

## Embeddings for code

1. [Learning and Evaluating Contextual Embedding of Source Code (CuBERT)](https://arxiv.org/abs/2001.00059)
2. [CodeBERT: A Pre-Trained Model for Programming and Natural Languages](https://arxiv.org/abs/2002.08155)
3. [A Literature Study Of Embeddings On Source Code](https://arxiv.org/abs/1904.03061v1)
4. [Contrastive Code Representation Learning](https://arxiv.org/abs/2007.04973)







