(ns looset-diagram-mvp.cbs-to-graph
  (:require
    [looset-diagram-mvp.code-blocks :as code-blocks]
    [looset-diagram-mvp.core :as lexical-analyzer]

    [clojure.test :refer :all]
    [clojure.pprint :refer [pprint]]
    ))

(def random-def
  code-blocks/another-random-def)

(defn file-info-with-code-blocks [{:keys [file-path] :as info}]
  (-> file-path
      slurp
      lexical-analyzer/generate-token-list
      (select-keys [:token-list :token-occurrencies])
      (merge info)
      code-blocks/identifier
      last
      (select-keys [:code-blocks :file-path])))

;; It's better to use with-file-path to avoid duplicated keys
(defn file-code-blocks-with-file-path [{:keys [file-path] :as info}]
  (->> info
      file-info-with-code-blocks
      :code-blocks
      (map (fn [[k v]] {(str file-path "<>" k) v}))
      (apply merge)
      (assoc {} :code-blocks)))

(defn -main []
  (let [files-to-analyze (read-string (slurp "interface-files/files-to-analyze.edn"))
        files-path (mapv :file-path (read-string (slurp "interface-files/files-to-analyze.edn")))
        closed-paths (-> files-path
                         file-paths->sub-dirs
                         (zipmap (repeat true)))]
    (-> files-to-analyze
        (file-list->graph)
        (->> (assoc-in {} [:domain :graph]))
        (assoc-in [:ui :closed-dirs] closed-paths)
        (->> (str "(ns looset-diagram-mvp.ui.initial-state)\n\n(def initial-state\n  "))
        (str ")")
        (->> (spit "src/looset_diagram_mvp/ui/initial_state.cljs"))
        )))
