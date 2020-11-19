(ns file-analyzer.cli
  (:require
    [clojure.string :as string]
    [clojure.tools.cli]
    [file-analyzer.cbs-to-graph :as cbs-to-graph]

    [clojure.java.shell :as shell]
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    )
  (:gen-class))

(defn usage [options-summary]
  (->> ["Looset diagram automatically generates a graph of dependencies by analyzing the Code Blocks of your project."
        ""
        "Usage: lein run <dirs-to-analyze> [options]"
        "or lein run gen-interface-files-to-analyze <dirs-to-analyze> [options]"
        ""
        "Options:"
        options-summary
        ""
        "Examples:"
        ". --use-gitignore                                                               Analyze file-analyzer itself."
        "../projects-example/mapbox-gl-draw/src ../projects-example/mapbox-gl-draw/test  Analyze files only from these dirs. "
        "../projects-example/Articulate/ --file-extensions c|cpp                         Analyze the given file extensions, e.g. c|cpp|asm."
        "gen-interface-files-to-analyze ../projects-example/Articulate/                  Do not analyze, only spit the discoverd files in interface-files/files-to-analyze.edn."
        "gen-interface-files-to-analyze ../projects-example/Articulate/ --indentation-level-to-search 4"
        "--files-to-analyze-default                                                      Use the file list described in interface-files/files-to-analyze.edn."
        "--files-to-analyze-location interface-files/files-to-analyze.edn                Use the file list described in interface-files/files-to-analyze.edn."
        ]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(def cbs-to-graph-options
  [["-g" "--use-gitignore" "Analyze files specified in a git repository."]
   ["-d" "--files-to-analyze-default" "Analyze files specified in interface-files/files-to-analyze.edn."]
   ["-l" "--files-to-analyze-location <path>" "Analyze files specified in the file input."]
   ["-f" "--file-extensions <ext>[|...ext-n]" "Only analyze files with the given extension. E.g. js|jsx|css|rb"]
   ["-i" "--indentation-level-to-search n" "Assume Code Blocks of all files start in the given column number. Use the action gen-interface-files-to-analyze to specify it by file."
    :validate [#(>= % 0) "Must be a number"]
    :parse-fn #(Integer/parseInt %)
    :default 0]
   ["-h" "--help" "Show this help and exit."]
   ])

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary] :as args-opt} (clojure.tools.cli/parse-opts args cbs-to-graph-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}

      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}

      (some #{:files-to-analyze-default :files-to-analyze-location} (keys options))
      {:action "analyze" :options options}

      (and (= "gen-interface-files-to-analyze" (first arguments))
           (empty? (rest arguments)))
      {:exit-message "Please provide a path for a software project directory to be analyzed."}

      (= "gen-interface-files-to-analyze" (first arguments))
      {:action "gen-interface-files-to-analyze" :dirs-to-analyze (rest arguments) :options options}

      (> (count arguments) 0)
      {:action "analyze" :dirs-to-analyze arguments :options options}

      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn gen-interface-files-to-analyze [dirs-to-analyze options])

(defn -main [& args]
  (let [{:keys [action dirs-to-analyze options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "gen-interface-files-to-analyze" (do (cbs-to-graph/gen-interface-files-to-analyze dirs-to-analyze options)
                                             (println "files-to-analyze.edn generated."))
        "analyze" (do (cbs-to-graph/-main dirs-to-analyze options)
                      (println "Graph generated."))))))

(defn verbose-shell [& args]
  (let [result (apply shell/sh args)]
    (if (= 1 (:exit result))
      (throw (Exception. (:err result)))
      result)))

(deftest files-to-analyze-default-test
  (testing "parse-opts"
    (is (= true
           (:files-to-analyze-default (:options (clojure.tools.cli/parse-opts ["--files-to-analyze-default"] cbs-to-graph-options))))))
  (testing "validate-args"
    (is (= "analyze"
           (:action (validate-args ["--files-to-analyze-default"])))))
  (testing "main\n"
    ;; Arrange
    (require '[clojure.java.shell :as shell])
    (shell/sh "mv" "../interface-files/files-to-analyze.edn" "../interface-files/files-to-analyze.edn.changed-while-testing")
    (shell/sh "mv" "../interface-files/dependencies-graph.edn" "../interface-files/dependencies-graph.edn.changed-while-testing")
    (spit "../interface-files/files-to-analyze.edn" [{:indentation-level-to-search 0 :file-path "test-resources/source-code-examples/cbs_to_graph.clj"}
                                                     {:indentation-level-to-search 0 :file-path "test-resources/source-code-examples/code_blocks.clj"}])
    ;; Test
    (-main "--files-to-analyze-default")

    ;; Assert
    (is (= (slurp "../interface-files/dependencies-graph.edn")
           (slurp "test-resources/file-results/files-to-analyze-test")))

    ;; Clean up
    (shell/sh "mv" "../interface-files/files-to-analyze.edn.changed-while-testing" "../interface-files/files-to-analyze.edn")
    (shell/sh "mv" "../interface-files/dependencies-graph.edn.changed-while-testing" "../interface-files/dependencies-graph.edn")))

(deftest gen-interface-files-to-analyze-test
  (testing "parse-opts"
    (is (= ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape"]
           (:arguments (clojure.tools.cli/parse-opts ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape"] cbs-to-graph-options)))))
  (testing "validate-args"
    (is (= "gen-interface-files-to-analyze"
           (:action (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape"]))))
    (is (= ["../projects-example/draw-map-shape"]
           (:dirs-to-analyze (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape"]))))
    (is (= ["../projects-example/draw-map-shape" "../projects-example/some-other"]
           (:dirs-to-analyze (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape" "../projects-example/some-other"])))))
  (testing "validate-args indentation-level-to-search"
    (is (= 4
           (:indentation-level-to-search (:options (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape" "--indentation-level-to-search" "4"])))))
    (is (= true
           (:use-gitignore (:options (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape" "--use-gitignore"]))))))
  (testing "validate-args use-gitignore"
    (is (= nil
           (:use-gitignore (:options (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape"])))))
    (is (= true
           (:use-gitignore (:options (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape" "-g"])))))
    (is (= true
           (:use-gitignore (:options (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape" "--use-gitignore"]))))))
  (testing "validate-args file-extensions"
    (is (= "css|svg"
           (:file-extensions (:options (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape" "-f" "css|svg"])))))
    (is (= "css|svg"
           (:file-extensions (:options (validate-args ["gen-interface-files-to-analyze" "../projects-example/draw-map-shape" "--file-extensions" "css|svg"]))))))
  (testing "main"
    ;; Arrange
    (require '[clojure.java.shell :as shell])
    (shell/sh "mv" "../interface-files/files-to-analyze.edn" "../interface-files/files-to-analyze.edn.changed-while-testing")

    ;; Test
    (-main "gen-interface-files-to-analyze" "../../../projects-example/draw-map-shape")

    ;; Assert
    (is (= 379
           (count (read-string (slurp "../interface-files/files-to-analyze.edn")))))

    ;; Clean up
    (shell/sh "mv" "../interface-files/files-to-analyze.edn.changed-while-testing" "../interface-files/files-to-analyze.edn")))

(deftest files-to-analyze-test
  (testing "parse-opts"
    (is (= "../interface-files/other-file.edn"
           (:files-to-analyze-location (:options (clojure.tools.cli/parse-opts ["--files-to-analyze-location" "../interface-files/other-file.edn"] cbs-to-graph-options))))))
  (testing "validate-args"
    (is (= "/some/path"
           (:files-to-analyze-location (:options (validate-args ["--files-to-analyze-location" "/some/path"]))))))
  (testing "main"
    ;; Arrange
    (require '[clojure.java.shell :as shell])
    (shell/sh "mv" "../interface-files/dependencies-graph.edn" "../interface-files/dependencies-graph.edn.changed-while-testing")
    (spit "../interface-files/another-path.edn" [{:indentation-level-to-search 0 :file-path "test-resources/source-code-examples/cbs_to_graph.clj"}
                                                 {:indentation-level-to-search 0 :file-path "test-resources/source-code-examples/code_blocks.clj"}])
    ;; Test
    (-main "--files-to-analyze-location" "../interface-files/another-path.edn")

    ;; Assert
    (is (= (slurp "../interface-files/dependencies-graph.edn")
           (slurp "test-resources/file-results/files-to-analyze-test")))

    ;; Clean up
    (verbose-shell "rm" "../interface-files/another-path.edn")
    (shell/sh "mv" "../interface-files/dependencies-graph.edn.changed-while-testing" "../interface-files/dependencies-graph.edn")))

(deftest analyze-test
  (testing "parse-opts"
    (is (= 8
           (:indentation-level-to-search (:options (clojure.tools.cli/parse-opts ["../projects-example/Articulate/" "-i" "8"] cbs-to-graph-options))))) 
    (is (= ["../projects-example/Articulate/"]
           (:arguments (clojure.tools.cli/parse-opts ["../projects-example/Articulate/" "-i" "8"] cbs-to-graph-options)))))
  (testing "validate-args"
    (is (= "analyze"
           (:action (validate-args ["../projects-example/Articulate/" "-i" "8"]))))))
