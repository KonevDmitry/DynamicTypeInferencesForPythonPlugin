# DynamicTypeReferencesForPythonPlugin

![Build](https://github.com/KonevDmitry/DynamicTypeInferencesForPythonPlugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Verify the [pluginGroup](/gradle.properties), [plugin ID](/src/main/resources/META-INF/plugin.xml) and [sources package](/src/main/kotlin).
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html).
- [ ] [Publish a plugin manually](https://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/publishing_plugin.html) for the first time.
- [ ] Set the Plugin ID in the above README badges.
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.

<!-- Plugin description -->
This Fancy IntelliJ Platform Plugin is going to be your implementation of the brilliant ideas that you have.

This specific section is a source for the [plugin.xml](/src/main/resources/META-INF/plugin.xml) file which will be
extracted by the [Gradle](/build.gradle.kts) during the build process.

To keep everything working, do not remove `<!-- ... -->` sections. 
<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:
  
  <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "DynamicTypeReferencesForPythonPlugin"</kbd> >
  <kbd>Install Plugin</kbd>
  
- Manually:

  Download the [latest release](https://github.com/KonevDmitry/DynamicTypeReferencesForPythonPlugin/releases/latest) and install it manually using
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
5. [Hoppity: Learning Graph Transformations To Detect And Fix Bugs In Programs](https://openreview.net/forum?id=SJeqs6EFvB) (Not Type Inference per se, but is related)

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







