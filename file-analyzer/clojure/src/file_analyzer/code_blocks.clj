(ns file-analyzer.code-blocks
  (:require
    [file-analyzer.lexical-analyzer :as lexical-analyzer]

    [clojure.test :refer :all]
    [clojure.pprint :refer [pprint]]
    ))

(defn indentation-position [{:keys [indentation-level-to-search indentation-level]}]
  (cond
    (< indentation-level indentation-level-to-search) :left
    (= indentation-level indentation-level-to-search) :center
    (> indentation-level indentation-level-to-search) :right))

(defn update-cb-line-id [{:keys [cb-line-id] :as big-state} {:keys [token]}]
  (if (= "EOF" token)
    big-state
    (update big-state :cb-line-id #(conj (or % []) token))))

(defn update-words-in-cb [{:keys [words-in-cb] :as big-state} {:keys [token]}]
  (if (nil? words-in-cb)
    (assoc big-state :words-in-cb #{token})
    (update big-state :words-in-cb conj token)))

(defn record-code-block [{:keys [words-in-cb cb-line-id code-blocks] :as big-state}]
  (let [big-state (-> big-state (dissoc :words-in-cb) (dissoc :cb-line-id))]
    (if (empty? words-in-cb)
      big-state
      (update big-state :code-blocks merge {cb-line-id words-in-cb})
      )))

;; Order
;; center, right, left
;; word, new-line, space, symbol, number
(defn process-token [{:keys [state token] :as big-state}]
  (cond
    (and (= :initial-state state)
         (= :center (indentation-position big-state))
         (= :word (token :category)))
    (-> big-state
        (assoc :state :cb-first-line)
        (update-cb-line-id token))

    (and (= :initial-state state)
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0))

    (and (= :initial-state state)
         (= :center (indentation-position big-state))
         (= :space (token :category)))
    (update big-state :indentation-level inc)

    (and (= :initial-state state)
         (= :center (indentation-position big-state))
         (#{:number :symbol} (token :category)))
    (assoc big-state :state :cb-first-line)

    (and (= :initial-state state)
         (#{:right} (indentation-position big-state))
         (#{:word :space :symbol :number} (token :category)))
    (-> big-state
        (assoc :state :ignore-line))

    (and (= :initial-state state)
         (#{:right} (indentation-position big-state))
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0))

    (and (= :initial-state state)
         (#{:left} (indentation-position big-state))
         (#{:word :symbol :number} (token :category)))
    (-> big-state
        (assoc :state :ignore-line))

    (and (= :initial-state state)
         (#{:left} (indentation-position big-state))
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0))

    (and (= :initial-state state)
         (#{:left} (indentation-position big-state))
         (#{:space} (token :category)))
    (-> big-state
        (update :indentation-level inc))

    (and (= :cb-first-line state)
         (= :word (token :category)))
    (-> big-state
        (update-cb-line-id token))

    (and (= :cb-first-line state)
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0)
        (assoc :state :cb-second-line))

    (and (= :cb-first-line state)
         (#{:space :symbol :number} (token :category)))
    big-state

    (and (= :cb-second-line state)
         (#{:center} (indentation-position big-state))
         (= :word (token :category)))
    (-> big-state
        (assoc :state :cb-first-line)
        (update-cb-line-id token))

    (and (= :cb-second-line state)
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0)
        (assoc :state :cb-second-line-with-blank-line))

    (and (= :cb-second-line state)
         (#{:center :right} (indentation-position big-state))
         (= :space (token :category)))
    (-> big-state
        (update :indentation-level inc))

    (and (= :cb-second-line state)
         (#{:center} (indentation-position big-state))
         (#{:symbol :number} (token :category)))
    (-> big-state
        (assoc :state :cb-first-line))

    (and (= :cb-second-line state)
         (#{:right} (indentation-position big-state))
         (= :word (token :category)))
    (-> big-state
        (assoc :state :inside-cb)
        (update-words-in-cb token))

    (and (= :cb-second-line state)
         (#{:right} (indentation-position big-state))
         (#{:symbol :number} (token :category)))
    (-> big-state
        (assoc :state :inside-cb))

    (and (= :cb-second-line state)
         (#{:left} (indentation-position big-state))
         (#{:word :symbol :number} (token :category)))
    (-> big-state
        (dissoc :cb-line-id)
        (assoc :state :ignore-line))

    (and (= :cb-second-line state)
         (#{:left} (indentation-position big-state))
         (#{:space} (token :category)))
    (-> big-state
        (update :indentation-level inc))

    (and (= :cb-second-line state)
         (#{:left} (indentation-position big-state))
         (#{:symbol :number} (token :category)))
    (-> big-state
        (dissoc :cb-line-id)
        (assoc :state :ignore-line))

    (and (= :cb-second-line-with-blank-line state)
         (= :center (indentation-position big-state))
         (= :word (token :category)))
    (-> big-state
        (assoc :state :cb-first-line)
        (dissoc :cb-line-id)
        (update-cb-line-id token))

    (and (= :cb-second-line-with-blank-line state)
         (= :center (indentation-position big-state))
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0))

    (and (= :cb-second-line-with-blank-line state)
         (= :center (indentation-position big-state))
         (= :space (token :category)))
    (-> big-state
        (update :indentation-level inc))

    (and (= :cb-second-line-with-blank-line state)
         (= :center (indentation-position big-state))
         (#{:symbol :number} (token :category)))
    (-> big-state
        (assoc :state :cb-first-line)
        (dissoc :cb-line-id))

    (and (= :cb-second-line-with-blank-line state)
         (= :right (indentation-position big-state))
         (= :word (token :category)))
    (-> big-state
        (assoc :state :inside-cb)
        (update-words-in-cb token))

    (and (= :cb-second-line-with-blank-line state)
         (= :right (indentation-position big-state))
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0))

    (and (= :cb-second-line-with-blank-line state)
         (= :right (indentation-position big-state))
         (= :space (token :category)))
    (-> big-state
        (update :indentation-level inc))

    (and (= :cb-second-line-with-blank-line state)
         (= :right (indentation-position big-state))
         (#{:symbol :number} (token :category)))
    (-> big-state
        (assoc :state :inside-cb))

    (and (= :cb-second-line-with-blank-line state)
         (#{:left} (indentation-position big-state))
         (#{:word :symbol :number} (token :category)))
    (-> big-state
        (assoc :state :ignore-line))

    (and (= :cb-second-line-with-blank-line state)
         (= :left (indentation-position big-state))
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0))

    (and (= :cb-second-line-with-blank-line state)
         (#{:left} (indentation-position big-state))
         (#{:space} (token :category)))
    (-> big-state
        (update :indentation-level inc))

    (and (= :inside-cb state)
         (= :word (token :category)))
    (-> big-state
        (update-words-in-cb token))

    (and (= :inside-cb state)
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :state :inside-cb-new-line)
        (assoc :indentation-level 0))

    (and (= :inside-cb state)
         (#{:space :symbol :number} (token :category)))
    big-state

    (and (= :inside-cb-new-line state)
         (#{:center} (indentation-position big-state))
         (#{:word} (token :category)))
    (-> big-state
        (assoc :state :cb-first-line)
        (update-cb-line-id token)
        (record-code-block))

    (and (= :inside-cb-new-line state)
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :indentation-level 0))

    (and (= :inside-cb-new-line state)
         (#{:space} (token :category)))
    (-> big-state
        (update :indentation-level inc))

    (and (= :inside-cb-new-line state)
         (#{:center} (indentation-position big-state))
         (#{:symbol :number} (token :category)))
    (-> big-state
        (assoc :state :cb-first-line)
        (record-code-block))

    (and (= :inside-cb-new-line state)
         (#{:right} (indentation-position big-state))
         (#{:word} (token :category)))
    (-> big-state
        (assoc :state :inside-cb)
        (update-words-in-cb token))

    (and (= :inside-cb-new-line state)
         (#{:right} (indentation-position big-state))
         (#{:symbol :number} (token :category)))
    (-> big-state
        (assoc :state :inside-cb))

    (and (= :inside-cb-new-line state)
         (#{:left} (indentation-position big-state))
         (#{:word :symbol :number} (token :category)))
    (-> big-state
        (assoc :state :ignore-line)
        (record-code-block))

    (and (= :ignore-line state)
         (#{:line-comment :new-line} (token :category)))
    (-> big-state
        (assoc :state :initial-state)
        (assoc :indentation-level 0))

    (and (= :ignore-line state)
         (#{:word :space :symbol :number} (token :category)))
    big-state

    :else
    (-> big-state
        (update :errors conj (-> big-state
                                 (dissoc :errors))))
    ))

(defn identifier
  ([{:keys [token-list] :as big-state}]
   (identifier token-list (merge {:state :initial-state :indentation-level-to-search 0 :indentation-level 0 :cb-id-occurrencies {}}
                                 (dissoc big-state :token-list))))
  ([[token & token-list] big-state]
   (when token
     (let [big-state (process-token (assoc big-state :token token))
           ;; tail (lazy-seq (identifier token-list big-state))
           tail (when (not (:errors big-state)) (lazy-seq (identifier token-list big-state)))]
       (cons (-> big-state (dissoc :token-list))
             tail)
       #_(str (:state big-state) (apply str tail))))))

(comment
  (process-token {:state :cb-first-line :indentation-level-to-search 0 :indentation-level 2 :token {:token "\n", :category :new-line, :position [141 27]} :cb-line-id "export"})
  (pprint (identifier {:token-list {:token "isEqual", :category :word, :position  [1 8]}}
                                  {:token "const", :category :word, :position [7 31]}))
   (-> (slurp "/home/smokeonline/projects/looset/diagram-mvp/src/looset_diagram_mvp/core.clj")
       lexical-analyzer/generate-token-list
       (select-keys [:token-list])
       identifier
       ;; reverse
       last
       ;; (->> (take 550))
       ;; (->> (drop 540))
       ;; :code-blocks
       ;; keys
       pprint
       #_set)
  )

(deftest identifier-test
  (is (= ["im" "import"]
         (let [initial-value (-> "im import"
                                 (lexical-analyzer/generate-token-list)
                                 (select-keys [:token-list]))]
           (:cb-line-id (last (identifier initial-value))))
         ))
  (is (= ["im" "im" "import"]
         (let [initial-value (-> "im im import"
                                 (lexical-analyzer/generate-token-list)
                                 (select-keys [:token-list]))]
           (:cb-line-id (last (identifier initial-value))))
         ))
  (is (= ["im" "im" "import"]
         (-> "im im import"
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             identifier
             last
             :cb-line-id
             ;; pprint
             )))
  (is (= {["export" "default" "function" "ctx" "api"] #{"inside"}}
         (-> "export default function(ctx, api) {\n\n  inside\n\n"
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             identifier
             last
             :code-blocks)))
  (is (= {["export" "default" "function" "ctx" "api"] #{"inside"}}
         (->  "export default function(ctx, api)  \n  inside\n\n"
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             identifier
             last
             :code-blocks)))
  (is (= {["export" "default" "function" "ctx" "api"] #{"inside"}}
         (->  "export default function(ctx, api)\n{\n  inside\n\n"
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             identifier
             last
             :code-blocks)))
  (testing "test-resources 1"
    (is (= [["const" "featureTypes"] ["export" "default" "function" "ctx" "api"]]
         (-> (slurp "test-resources/source-code-examples/api.js")
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             identifier
             last
             :code-blocks
             keys))))
  (testing "test-resources 2"
    (is (= #{["api" "changeMode" "function" "mode" "modeOptions"]
           ["api" "getSelectedIds" "function"]
           ["api" "combineFeatures" "function"]
           ["api" "getAll" "function"]
           ["api" "getFeatureIdsAt" "function" "point"]
           ["api" "trash" "function"]
           ["api" "deleteAll" "function"]
           ["api" "set" "function" "featureCollection"]
           ["api" "delete" "function" "featureIds"]
           ["api" "getSelectedPoints" "function"]
           ["api" "add" "function" "geojson"]
           ["api" "setFeatureProperty" "function" "featureId" "property" "value"]
           ["api" "get" "function" "id"]
           ["api" "getMode" "function"]
           ["api" "getSelected" "function"]
           ["api" "uncombineFeatures" "function"]}
         (-> (slurp "test-resources/source-code-examples/api.js")
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             (merge {:indentation-level-to-search 2})
             identifier
             last
             :code-blocks
             keys
             set))))
  (testing "test-resources 3"
   (is (= #{"onSetup" "fireUpdate" "fireActionable" "getUniqueIds" "stopExtendedInteractions" "onStop" "onMouseMove" "onMouseOut" "onTap" "clickAnywhere" "clickOnVertex" "startOnActiveFeature" "clickOnFeature" "onMouseDown" "startBoxSelect" "onTouchStart" "onDrag" "whileBoxSelect" "dragMove" "onMouseUp" "toDisplayFeatures" "onTrash" "onCombineFeatures" "onUncombineFeatures"}
         (-> (slurp "test-resources/source-code-examples/simple_select.js")
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             (merge {:indentation-level-to-search 0})
             identifier
             last
             :code-blocks
             keys
             (->> (mapv second))
             set))))
  (is (= "EOF"
         (-> (slurp "test-resources/source-code-examples/simple_select.js")
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             (merge {:indentation-level-to-search 0})
             identifier
             last
             :token
             :token)))
  (is (= "EOF"
         (-> (slurp "test-resources/source-code-examples/api.js")
             lexical-analyzer/generate-token-list
             (select-keys [:token-list])
             (merge {:indentation-level-to-search 0})
             identifier
             last
             :token
             :token)))
  )
