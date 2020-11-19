(ns file-analyzer.cbs-to-graph
  (:require
    [clojure.java.shell :as shell]
    [file-analyzer.code-blocks :as code-blocks]
    [file-analyzer.lexical-analyzer :as lexical-analyzer]

    [clojure.test :refer :all]
    [clojure.pprint :refer [pprint]]
    ))

(defn- file-info-with-code-blocks [{:keys [file-path] :as info}]
  (print (str "\r" "Analyzing " file-path))
  (flush)
  (-> file-path
      slurp
      lexical-analyzer/generate-token-list
      (select-keys [:token-list])
      (merge info)
      code-blocks/identifier
      last
      (select-keys [:code-blocks :file-path])))

;; Choose code block id by the identifier that appear less in other code block first lines.
(defn- choose-cb-ids [info-coll]
  (let [cb-ids-frequencies (->> info-coll
                                (mapcat :code-blocks)
                                (keys)
                                (apply concat)
                                frequencies)
        choose-fn (fn choose-fn [chosen-id candidate]
                    (if (< (cb-ids-frequencies candidate) (cb-ids-frequencies chosen-id))
                      candidate
                      chosen-id))
        update-cb-ids (fn update-cb-ids [[ids-vec value]]
                        (when ids-vec
                          {(reduce choose-fn ids-vec) value}))
        update-code-blocks #(update % :code-blocks (fn [cbs] (apply merge (map update-cb-ids cbs))))]
    (map update-code-blocks info-coll)
    ))

;; It's better to use with-file-path to avoid duplicated keys
(defn- file-code-blocks-with-file-path [{:keys [file-path] :as info}]
  (->> info
      :code-blocks
      (map (fn [[k v]] {(str file-path "<>" k) v}))
      (apply merge)
      (assoc {} :code-blocks)))

;; It's still a copy of merge-with, I need to change it accordanly.
#_(defn merge-with-key-changer
  "Like merge-with, but instead of changing the value, it changes the key."
  [f & maps]
  (when (some identity maps)
    (let [merge-entry (fn [m e]
                        (let [k (key e) v (val e)]
                          (if (contains? m k)
                            (assoc m k (f (get m k) v))
                            (assoc m k v))))
          merge2 (fn [m1 m2]
                   (reduce1 merge-entry (or m1 {}) (seq m2)))]
      (reduce1 merge2 maps))))

(defn- path-last-part [path]
  (if (re-find #"^.*<>" path)
    (clojure.string/replace path #"^.*<>" "")
    (->> path clojure.string/reverse (re-find #"^[^/]*") clojure.string/reverse)
    ))

(defn- filter-cb-identifiers [code-blocks]
  (let [cb-identifiers (-> code-blocks
                           keys
                           (->> (map path-last-part))
                           set
                           (conj "DUPLICATED KEY!!"))]
    (apply merge (map (fn [[k v]] {k (set (filter cb-identifiers v))}) code-blocks))))

(defn- file-list->graph [file-list]
  (->> file-list
       (map file-info-with-code-blocks)
       (choose-cb-ids)
       (map file-code-blocks-with-file-path)
       (map :code-blocks)
       ;; (apply (partial merge-with (constantly #{"DUPLICATED KEY!!"}))) ;; Use merge-with-key-changer instead?
       (apply (partial merge-with clojure.set/union))
       (filter-cb-identifiers)))

(defn- file-paths->sub-dirs [file-paths]
  (let [split-paths (clojure.string/split (first file-paths) #"/")
        subpaths-fn (fn [dirs-set acc r]
                (let [new-acc (str acc "/" (first r))]
                  (if (empty? r)
                    dirs-set
                    (recur (conj dirs-set new-acc)
                           new-acc
                           (rest r)))))]
    (->> file-paths
         (map #(clojure.string/split % #"/"))
         (reduce #(subpaths-fn %1 (first %2) (rest %2)) #{}))))

(def default-file-extensions
  (clojure.string/join "|" ["html" "css" ;; Web
                            "clj" "cljs" "cljc" ;; Clojure(script)
                            "js" ;; Javascript
                            "erl" ;; Erlang
                            "ex" "exs" ;; Elixir
                            "elm"
                            "hs" ;; Haskell
                            "scala"
                            "rs" ;; Rust
                            "fs" ;; F#
                            "swift"
                            "vue" "jsx" ;; Web frameworks
                            "c" "cpp"
                            "java"
                            "py" ;; Python
                            "cs" ;; C#
                            "vb" ;; Visual Basic
                            "php"
                            "R"
                            "perl"
                            "groovy"
                            "rb" ;; Ruby
                            "go"
                            "kt" "kts" "ktm" ;; Kotlin
                            "m" ;; MATLAB or Objective-C
                            "sh" ;; Bash
                            "lua"
                            "cbl" ;; Cobol
                            "lol" ;; Lolcode
                            "rkt" "ss" "scm" "sch" ;; Racket
                            "rei" ;; ReasonMl
                            "purs" ;; PureScript
                            ]))

(defn verbose-shell [& args]
  (let [result (apply shell/sh args)]
    (if (= 1 (:exit result))
      (throw (Exception. (:err result)))
      result)))

(defn- get-files-to-analyze [dirs-to-analyze
                             {:keys [use-gitignore file-extensions indentation-level-to-search]
                              :or {file-extensions default-file-extensions
                                   indentation-level-to-search 0}}]
  (let [get-out #(if (zero? (:exit %)) (:out %) (throw (Exception. (:err %))))
        dirs (if use-gitignore (first dirs-to-analyze) (clojure.string/join " " dirs-to-analyze))
        get-files-command (if use-gitignore
                            (str "cd "dirs" && git ls-files")
                            (str "find "dirs))]
    (print (str "\r" "Discoverying files in "dirs))
    (flush)
    (-> (str get-files-command" | grep -E \"\\.("file-extensions")$\"")
        (->> (verbose-shell "bash" "-c"))
        get-out
        (clojure.string/split #"\n")
        (->> (mapv #(into {:indentation-level-to-search indentation-level-to-search :file-path %})))
        (#(do (print (str "\r" (count %) " files discovered."\newline)) (flush) %)))))

(defn gen-interface-files-to-analyze [dirs-to-analyze options]
  (->> (get-files-to-analyze dirs-to-analyze options)
       (#(with-out-str (pprint %)))
       (spit "../interface-files/files-to-analyze.edn")))

(defn- analyze-files [files-to-analyze]
  (let [files-path (mapv :file-path files-to-analyze)
        closed-paths (-> files-path
                         file-paths->sub-dirs
                         (zipmap (repeat true)))]
    (-> files-to-analyze
        (file-list->graph)
        (->> (assoc-in {} [:domain :graph]))
        (assoc-in [:ui :closed-dirs] closed-paths)
        list
        (->> (concat '(def initial-state)))
        (as-> $ (with-out-str (pprint $)))
        (->> (str "(ns looset-diagram-mvp.ui.initial-state)\n\n"))
        ;; Would you preffer to export to an edn file?
        (->> (spit "../interface-files/dependencies-graph.edn"))
        )))

(defn -main [dirs-to-analyze {:keys [files-to-analyze-default files-to-analyze-location] :as options}]
  (let [files-to-analyze (cond
                           files-to-analyze-default
                           (read-string (slurp "../interface-files/files-to-analyze.edn"))

                           files-to-analyze-location
                           (read-string (slurp files-to-analyze-location))

                           :else
                           (get-files-to-analyze dirs-to-analyze options))]
    (analyze-files files-to-analyze)))

(comment
  (require '[clojure.java.shell :as shell])
  (shell/sh "bash" "-c" "cd .. && ls")
  (loop [c 1]
    (if (= c 50)
      (print c)
      (do (Thread/sleep 100)
          (print "\r" c)
          (flush)
          (recur (inc c)))))

  (shell/sh "git" "ls-files")

  (let [file-extensions (clojure.string/join "|" ["html" "css" ;; Web
                                                  "clj" "cljs" "cljc" ;; Clojure(script)
                                                  "js" ;; Javascript
                                                  "erl" ;; Erlang
                                                  "ex" "exs" ;; Elixir
                                                  "elm"
                                                  "hs" ;; Haskell
                                                  "scala"
                                                  "rs" ;; Rust
                                                  "fs" ;; F#
                                                  "swift"
                                                  "vue" "jsx" ;; Web frameworks
                                                  "c" "cpp"
                                                  "java"
                                                  "py" ;; Python
                                                  "cs" ;; C#
                                                  "vb" ;; Visual Basic
                                                  "php"
                                                  "R"
                                                  "perl"
                                                  "groovy"
                                                  "rb" ;; Ruby
                                                  "go"
                                                  "kt" "kts" "ktm" ;; Kotlin
                                                  "m" ;; MATLAB or Objective-C
                                                  "sh" ;; Bash
                                                  "lua"
                                                  "cbl" ;; Cobol
                                                  "lol" ;; Lolcode
                                                  "rkt" "ss" "scm" "sch" ;; Racket
                                                  "rei" ;; ReasonMl
                                                  "purs" ;; PureScript
                                                  ])]
    (-> (shell/sh "bash" "-c" (str "find ./srcx | grep -E \".("file-extensions")$\""))
        ;; :out
        ;; (clojure.string/split #"\n")
        ;; (->> (map #(into {:x 0 :y %})))
        print
        ;; (as-> $ (with-out-str (pprint $)))
        )
    )
  )

(deftest get-files-to-analyze-test
  (is (= 379
         (count (get-files-to-analyze ["../projects-example/draw-map-shape"] {}))))
  (is (= 3
         (count (get-files-to-analyze ["../projects-example/draw-map-shape"] {:file-extensions "css|svg"}))))
  (is (= 378
         (count (get-files-to-analyze ["../projects-example/draw-map-shape"] {:use-gitignore true})))))

(deftest from-file-list-test
  (is (= {"test/source-code-examples/draw_polygon.js<>onStop"
          #{},
          "test/source-code-examples/simple_select.js<>whileBoxSelect"
          #{},
          "test/source-code-examples/draw_polygon.js<>onTrash"
          #{},
          "test/source-code-examples/simple_select.js<>onStop"
          #{},
          "test/source-code-examples/draw_polygon.js<>onSetup"
          #{},
          "test/source-code-examples/simple_select.js<>clickAnywhere"
          #{"stopExtendedInteractions"},
          "test/source-code-examples/simple_select.js<>fireUpdate"
          #{},
          "test/source-code-examples/draw_polygon.js<>onKeyUp"
          #{},
          "test/source-code-examples/simple_select.js<>onCombineFeatures"
          #{"fireActionable"},
          "test/source-code-examples/simple_select.js<>onTrash"
          #{"fireActionable"},
          "test/source-code-examples/simple_select.js<>onTouchStart"
          #{"startOnActiveFeature"},
          "test/source-code-examples/simple_select.js<>startBoxSelect"
          #{"stopExtendedInteractions"},
          "test/source-code-examples/simple_select.js<>onUncombineFeatures"
          #{"fireActionable"},
          "test/source-code-examples/draw_polygon.js<>onTap"
          #{"clickOnVertex" "clickAnywhere"},
          "test/source-code-examples/simple_select.js<>opts"
          #{"fireActionable" "opts"},
          "test/source-code-examples/simple_select.js<>toDisplayFeatures"
          #{"fireActionable"},
          "test/source-code-examples/simple_select.js<>onMouseUp"
          #{"fireUpdate" "stopExtendedInteractions" "getUniqueIds"},
          "test/source-code-examples/simple_select.js<>stopExtendedInteractions"
          #{},
          "test/source-code-examples/simple_select.js<>dragMove"
          #{},
          "test/source-code-examples/draw_polygon.js<>clickAnywhere"
          #{},
          "test/source-code-examples/simple_select.js<>startOnActiveFeature"
          #{"stopExtendedInteractions"},
          "test/source-code-examples/simple_select.js<>clickOnFeature"
          #{"stopExtendedInteractions"},
          "test/source-code-examples/simple_select.js<>clickOnVertex"
          #{},
          "test/source-code-examples/simple_select.js<>onDrag"
          #{"dragMove" "whileBoxSelect"},
          "test/source-code-examples/simple_select.js<>getUniqueIds"
          #{},
          "test/source-code-examples/simple_select.js<>onTap"
          #{"clickOnVertex" "clickOnFeature" "clickAnywhere"},
          "test/source-code-examples/simple_select.js<>onMouseMove"
          #{"stopExtendedInteractions"},
          "test/source-code-examples/simple_select.js<>fireActionable"
          #{},
          "test/source-code-examples/draw_polygon.js<>toDisplayFeatures"
          #{},
          "test/source-code-examples/simple_select.js<>onMouseDown"
          #{"startOnActiveFeature" "startBoxSelect"},
          "test/source-code-examples/draw_polygon.js<>onMouseMove"
          #{},
          "test/source-code-examples/simple_select.js<>onMouseOut"
          #{"fireUpdate"},
          "test/source-code-examples/draw_polygon.js<>clickOnVertex"
          #{}}
         (->> [{:indentation-level-to-search 0 :file-path "test/source-code-examples/draw_polygon.js" }
               {:indentation-level-to-search 0 :file-path "test/source-code-examples/simple_select.js" }]
              (file-list->graph))))

  (is (= {"test/source-code-examples/code_blocks.clj<>code-blocks" #{},
          "test/source-code-examples/code_blocks.clj<>another-random-def" #{},
          "test/source-code-examples/code_blocks.clj<>identifier" #{"identifier"},
          "test/source-code-examples/cbs_to_graph.clj<>cbs-to-graph" #{"code-blocks"},
          "test/source-code-examples/cbs_to_graph.clj<>random-def" #{"code-blocks" "another-random-def"},
          "test/source-code-examples/cbs_to_graph.clj<>file-info-with-code-blocks" #{"code-blocks" "identifier"},
          "test/source-code-examples/cbs_to_graph.clj<>file-code-blocks-with-file-path" #{"code-blocks" "file-info-with-code-blocks"},
          "test/source-code-examples/cbs_to_graph.clj<>main" #{}}
         (->> [{:indentation-level-to-search 0 :file-path "test/source-code-examples/code_blocks.clj" }
               {:indentation-level-to-search 0 :file-path "test/source-code-examples/cbs_to_graph.clj" }]
              file-list->graph)))

  (is (= (list (list "code-blocks" "another-random-def" "identifier")
               (list "cbs-to-graph" "random-def" "file-info-with-code-blocks" "file-code-blocks-with-file-path" "main"))
         (->> [{:indentation-level-to-search 0 :file-path "test/source-code-examples/code_blocks.clj" }
               {:indentation-level-to-search 0 :file-path "test/source-code-examples/cbs_to_graph.clj" }]
              (map file-info-with-code-blocks)
              choose-cb-ids
              (map :code-blocks)
              (map keys))))
  (is (= ["demo-blog" "mdl-textfield" "button" "comment__header" "comment__text" "comment__actions" "comment__answers"]
         (->> [{:file-path "../projects-example/Articulate/src/Articulate.Web/App_Plugins/Articulate/Themes/Material/Assets/css/styles.css" :indentation-level-to-search 8}]
              (map file-info-with-code-blocks)
              choose-cb-ids
              (map :code-blocks)
              (map keys)
              first
              vec))))

(deftest file-paths->sub-dirs-test
  (is (= #{"/home" "/home/smokeonline" "/home/smokeonline/projects" "/home/smokeonline/projects/looset" "/home/smokeonline/projects/looset/projects-example"}
         (file-paths->sub-dirs ["/home/smokeonline/projects/looset/projects-example"])
         ))
  (is (= #{"/home" "/home/smokeonline" "/home/smokeonline/projects" "/home/smokeonline/projects/looset"
           "/home/smokeonline/projects/looset/projects-example"
           "/home/smokeonline/projects/looset/x"}
         (file-paths->sub-dirs ["/home/smokeonline/projects/looset/projects-example"
                                "/home/smokeonline/projects/looset/x"])
         ))
  (is (= #{"/home" "/home/smokeonline" "/home/smokeonline/projects" "/home/smokeonline/projects/looset/projects-example"
           "/home/smokeonline/projects/looset"
           "/home/smokeonline/projects/looset/projects-example/present-macos"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Carthage"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Carthage/Checkouts"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Carthage/Checkouts/LaunchAtLogin"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Carthage/Checkouts/LaunchAtLogin/LaunchAtLogin"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Carthage/Checkouts/LaunchAtLogin/LaunchAtLogin/LaunchAtLogin.swift"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Carthage/Checkouts/LaunchAtLogin/LaunchAtLoginHelper"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Carthage/Checkouts/LaunchAtLogin/LaunchAtLoginHelper/main.swift"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Pods"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Pods/AXSwift"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Pods/AXSwift/Sources"
           "/home/smokeonline/projects/looset/projects-example/present-macos/Pods/AXSwift/Sources/AXSwift.swift"}
         (file-paths->sub-dirs ["/home/smokeonline/projects/looset/projects-example/present-macos/Carthage/Checkouts/LaunchAtLogin/LaunchAtLogin/LaunchAtLogin.swift"
                                "/home/smokeonline/projects/looset/projects-example/present-macos/Carthage/Checkouts/LaunchAtLogin/LaunchAtLoginHelper/main.swift"
                                "/home/smokeonline/projects/looset/projects-example/present-macos/Pods/AXSwift/Sources/AXSwift.swift"])
         ))
  )
