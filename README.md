# VaDima

![Build](https://github.com/KonevDmitry/DynamicTypeInferencesForPythonPlugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/VaDima)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/VaDima)

## Plugin principles of work

When the user installs the plugin, the model is downloaded to the plugin config folder. By default, this is PyCharm
config folder:
```~/.config/JetBrains/PyCharm<PyCharmVersion>```. If a programmer uses type hints for variable suggestions for the call
of user-defined function during the model load then he will be notified about it.

### How the model is downloaded?

The model is downloaded from dropbox via dropbox-api. The model itself is downloaded inside [BertModelLoader](
/src/main/java/dynamic/type/inferences/model/loader/BertModelLoader.java). This class is used on project opening
is [StartUpActivity.](/src/main/java/dynamic/type/inferences/startUpActivity/ModelStartUpActivity.java)

### How notifications are working?

Instances of model notifications are declared inside [ModelNotLoadedNotification (
/src/main/java/dynamic/type/inferences/notification/ModelNotLoadedNotification.java) and are called
in [ModelCompletionProvider.](/src/main/java/dynamic/type/inferences/completer/ModelCompletionProvider.java)

Finally, after the model is downloaded, the predictions for user-defined functions will be added to model predictions.

### How the type hint works?

1) All variables are stored as separated maps.
    1) Variables are collected
       via [VariablesVisitor](/src/main/java/dynamic/type/inferences/visitors/VariablesVisitor.java)
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
           are needed to be collected. Also, variable collector collects all suitable variables in current scope. Such
           are:
            1) If a function is inside a class, then "self" variables can be accepted.
            2) Variables that are defined inside the function body.
            3) Variables from global scope (that are simply defined inside python file and not in function/class)
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

The plugin supports predicting the function input and output for documentation providing (```ctrl+q```
actions and simple mouse navigation). Both actions are defined inside
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

As it was mentioned before, all functions are collected inside a map. During the user work, the plugin defines if the
current callable element is a function. After that, the whole code of the function is passed as the model input. The
output of the model are possible types of variables that after are passed to already mentioned LookUpElements and
Documentation.

## Template ToDo list

- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [x] Verify the [pluginGroup](/gradle.properties), [plugin ID](/src/main/resources/META-INF/plugin.xml).
- [x] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html).
- [ ] [Publish a plugin manually](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/publishing_plugin.html)
  for the first time.
- [x] Set the Plugin ID in the above README badges.
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified
  about releases containing new features and fixes.

<!-- Plugin description -->
### Function type annotations
Tired of undocumented code?<br/> Always spend a lot of time reading strange code?<br/>
Always have to look at "deep dark secrets"?

**VaDima** is a solution!

VaDima is a plugin that can help you to deal with functions that<br/>
were written by you and other people. <br/>
Using the most modern Machine Learning techniques it <br/>suggests the types of variables
for next functions:
* Written by you 
* Python build-in
* From libraries

This plugin uses BERT model that analyses the code of <br/>functions and 
returns top-5 most probable types <br/>for each function parameter. It can help in several ways:
1) **Documentation providing for mouse hoverboard <br/>and ctrl+q actions:**
<br/>
[comment]: <> (![documentationExample]&#40;data/documentation.gif&#41;  )
[comment]: <> (![Example]&#40;https://media.giphy.com/media/Pg0RuS2frxFjebsHgW/giphy.gif&#41;)
<img src="https://media.giphy.com/media/Pg0RuS2frxFjebsHgW/giphy.gif" width="350" height="200" />

2) **Type hinting**
<br/>
[comment]: <> (![typeHintingExample]&#40;data/typeHinting.gif&#41;)
[comment]: <> (![Example]&#40;https://media.giphy.com/media/qpXvotoDsbZaTsBMjZ/giphy.gif&#41;)
<img src="https://media.giphy.com/media/qpXvotoDsbZaTsBMjZ/giphy.gif" width="350" height="200" />
### Before plugin load

1) Plugin downloads BERT model for predictions<br/> and takes **475,9 mb** of free space.
   <br/>If you decide to delete plugin, then model path, <br/>recognizable types, and others <br/>can be found in plugin settings:
   <br/><br/>
   <img src="https://imgur.com/rsdR55A.jpg" width="350" height="200">
2) After model load wait a bit while needed libraries for model<br/> launch will be loaded. We cannot handle<br/> these process and
   check when they will be loaded.<br/> If something went wrong - reload your PyCharm.

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






