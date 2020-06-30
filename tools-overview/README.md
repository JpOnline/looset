# Looset - A promise to improve documentation and code knowledge
## Overview
Hi humans, how are you? My first name is Page and my last name is Looset, I'm a piece of software, a tool, and I'm part of a family of tools thought to help humans understand the code they need to work with. Here's the main philosophy:\

The code are the **facts**.\
The comments are the **knowledge** imputed by **human**.\
The tool will show you an interpretation, a **point of view**, a way to see the combination of **fact** and **human Input**.

Not all of us rely strongly of human input, Looset Diagram and Looset Code bring ways to visualize or discover your code that doesn't require a lot of human input, while me (Looset Page) and Looset Glossary require more human input to combine them in a useful way.
The whole family uses some common concepts.
## Common Concepts
### Code Block
It's a chunk of code present in your code base, ideally a function or a data structure, it's identified by a simple static analyzer and given a name (in some cases the name can be hard to identify, as in an anonymous function, this is why the human can help inputting its name). The basic analyzer works with any language, this is why it's perfect to be used in projects that mix HTML, CSS, Javascript, C#, Clojure, Python, Haskell, Cobol, Lolcode, etc.
### Property Panel
It's where human can input knowledge and see a reflection of what gets written in the source file in the format of comments. As an example, possible properties of a Code Blocks are: id, name, docstring, labels, see-also, examples.
### Label Panel
It's a general overview of your code base better than the file system. A Label is very similar to a folder: it can contain Code Blocks and other Labels. The difference is that CBs and Labels can be in more than a Label at the same time. E.g. a CB called `getFeatureIdsAt` can be in the `Main API`, so if we expand the Label we can see it's there, but the same `getFeatureIdsAt` CB can also be in the `Point` Label and we can see it in both places.
By itself the Label system already enables a bunch of possibilities to structure your CBs. For instance you could have, at the same time, labels representing an MVC structure and other Labels structuring your CBs by functionalities.
## Looset Diagram
It automatically generates graph diagrams where each CB is a node and a connection is created when a CB references another CB. When a Label is collapsed all its CBs gets hidden inside the Label and their connections start to point to the Label, acting as a black box. It's simple to explain and beautiful to see.
## Looset Code
It's a way to see your CBs in the order you want, gathered as you want. The foldable CB with its foldable docstring shows up in the order you select them. Plus, there's a small indicator of what Labels it belongs to by the Label colors at the left side.
## Looset Page
This is me, I show humans an overview of the information inputted by other humans of a Label or CB. If it's a CB I show it with the last commit date (what I basically do by running a "git log -L" with the file name and line numbers of the CB). I show a markdown docstring also with the last commit date, so you the human can compare when the documentation and the code were updated to be sure they can trust the information. What Labels does it belongs to with the icon beside its name and their colors at the left edge of the screen. When there are unit tests for CBs or integration tests for Labels, they can be linked in the "examples" section. In the "see also" section you can link CBs that are not directly linked to your CB or page, but can be worth taking a look to understand what's going on. When any of these references are lost I will show you a warning, generally because the code block was deleted or renamed, just update the reference in the Property Panel and the warning is gone, no hard feelings. 👍😁
## Looset Glossary
This one is strongly inspired by DDD Ubiquitous Language, the human inputs the domain terms with a description of its meaning and the analyzer will scan the code to find out where this term is used. As it happens in natural languages, term meanings depends to a context, e.g. if I say the word "account" it might mean one thing in an login context but another totally different meaning in a banking context.
Which tool would you like to use? https://forms.gle/5vbfc54MRXcBodKc7
Wanna see it in action? Take a look of Johnny using it from scratch https://youtu.be/ktVpk1UukKA
