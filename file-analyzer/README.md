# Looset Diagram MVP

A Minimum Viable Product of [Looset Diagram](https://jponline.github.io/looset-landing/#looset-diagram), a graph of call references.

If you want to run this code in your machine you need to be able to run Clojure code through Leiningen [(instalation steps)](https://leiningen.org/).

The simplest way to run the backend and generate the file `src/looset_diagram_mvp/ui/initial_state.cljs` is running

`lein run <path-to-your-project>`

For more options `lein run looset --help`

## Running the frontend

TODO: Review with new instructions

The frontend is the one the reads the generated `src/looset_diagram_mvp/ui/initial_state.cljs` and is basically a different project that uses [Figwheel](https://figwheel.org/), to fire up a local server you can run the following commands:

`npm install` - Need when running the code for the first time. It install node packages dependencies.

`npx webpack` - Need when running the code for the first time. Bundle the external javascript code into the index.bundle.js.

`lein fig:dev` - What will start a local server, available from http://localhost:5667/

## Specifying the indentation-level-to-search by file

Run `lein run gen-interface-files-to-analyze <path-to-your-project>` to generate the file `interface-files/files-to-analyze.edn` with a vector of maps as

```clojure
{:indentation-level-to-search 8 :file-path "/home/smokeonline/projects/looset/projects-example/Articulate/src/Articulate/Components/ArticulateComponent.cs"}
```

If you have code that looks like this

```python
def ansi_color(code, s):
    return '{}{}{}'.format(ansi(code), s, ansi(0))

def make_color_fn(code):
    return lambda s: ansi_color(code, s)
```

the `indentation-level-to-search` is 0. If you have

```python
class Container:
    @classmethod
    def from_id(cls, client, id):
        return cls(client, client.inspect_container(id), has_been_inspected=True)

    @classmethod
    def create(cls, client, **options):
        response = client.create_container(**options)
        return cls.from_id(client, response['Id'])
```

the `indentation-level-to-search` is 4.

Then you can run `lein run --files-to-analyze-default` to generate the new `src/looset_diagram_mvp/ui/initial_state.cljs` file.

