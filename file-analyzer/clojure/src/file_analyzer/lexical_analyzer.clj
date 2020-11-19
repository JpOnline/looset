(ns file-analyzer.lexical-analyzer
  (:require
    [clojure.set]

    [clojure.test :refer :all]
    [clojure.pprint :refer [pprint]]
    ))

(defn get-pred
  "Returns the first element of coll that satisfy the predicate f."
  [f coll]
  (some #(if (f %) %) coll))

(def token-finite-automata
  {:first-char [{:regex #"[0-9]" :next-state :number}
                {:regex #"[_a-zA-Z]" :next-state :word}
                {:regex #"\n" :next-state :new-line}
                {:regex #"\s" :next-state :space}
                {:regex #"\W" :next-state :symbol}
                ]
   :new-line [{:regex #"[\s\S]" :next-state :first-char}]
   :word [{:regex #"[\s\n()\[\]{}<>]" :next-state :first-char}
          {:regex #"[0-9\-_a-zA-Z]" :next-state :word}
          {:regex #"\W" :next-state :first-char}]
   :symbol [{:regex #"[\s\S]" :next-state :first-char}]
   :space [{:regex #"[\S\s]" :next-state :first-char}]
   :number [{:regex #"[\s\n()\[\]{}<>]" :next-state :first-char}]
   })

(defn handle-exception [{:keys [next-char next-state] :as big-state}]
  (cond
    (not (nil? next-state))
    big-state

    ;; The next-char is nil when is the end of the file
    (nil? next-char)
    big-state

    :else
    (-> big-state
        (assoc :next-state :first-char)
        (update :errors conj {:position (:position big-state)
                                  :char next-char
                                  :state (:state big-state)}))))

(defn compute-token [{:keys [state token char] :as big-state} next-state]
  (if (= state next-state)
    (update big-state :token str char)
    (-> big-state
        (update :token-list conj (-> big-state
                                     (update :token str char)
                                     (select-keys [:token-position :token :category])
                                     (clojure.set/rename-keys {:token-position :position})
                                     ))
        (dissoc :token)
        )))

(defn process-char [{:keys [next-char next-state state position] :as big-state}]
  (-> big-state
      (update :position (fn [[lin column]] (if (= \newline next-char) [(inc lin) 1] [lin (inc column)])))
      (compute-token next-state)
      (update :category #(if (= state :first-char) next-state %))
      (update :token-position #(if (= state :first-char) position %))
      (assoc :state next-state)
      (assoc :char next-char))
  )

(defn lexical-analyzer [{:keys [state] :as big-state} code-to-process]
  (let [char (first code-to-process)
        transitions (state token-finite-automata)
        {:keys [next-state]} (get-pred (fn [{:keys [regex]}] (re-find regex (str char))) transitions)
        {:keys [next-state] :as big-state} (-> big-state
                                               (assoc :next-char char)
                                               (assoc :next-state next-state)
                                               (handle-exception))
        ;; big-state (update big-state :counter #(if (nil? %) 0 (inc %)))
        ;; _ (println)
        ;; _ (pprint big-state)
        ]
  (cond
    ;; (> (:counter big-state) 100)
    ;; big-state

    (nil? char)
    (process-char big-state)

    (= next-state :first-char)
    (-> big-state
        (assoc :state next-state)
        (recur code-to-process))

    (and (= next-state :symbol)
         (= \/ (first code-to-process) (second code-to-process)))
    (-> big-state
        (process-char)
        (update-in [:position 0] inc)
        (assoc-in [:position 1] 1)
        (assoc :category :line-comment)
        (assoc :token "/")
        (assoc :char "/")
        (recur (rest (drop-while #(not= \newline %) code-to-process)))
        )

    ;; Clojure comment
    (and (= next-state :symbol)
         (= \; (first code-to-process) (second code-to-process)))
    (-> big-state
        (process-char)
        (update-in [:position 0] inc)
        (assoc-in [:position 1] 1)
        (assoc :category :line-comment)
        (assoc :token ";")
        (assoc :char ";")
        (recur (rest (drop-while #(not= \newline %) code-to-process)))
        )

    :else
    (-> big-state
        (process-char)
        (recur (rest code-to-process))
        ))))

(defn generate-token-list [code-to-process]
  (let [code-to-process (concat code-to-process (seq "\nEOF"))
        char (first code-to-process)
        transitions (:first-char token-finite-automata)
        {:keys [next-state]} (get-pred (fn [{:keys [regex]}] (re-find regex (str char))) transitions)]
    (lexical-analyzer
      {:position [1 2]
       :char (first code-to-process)
       :state next-state
       :token-position [1 1]
       :category next-state
       :token-list []}
      (rest code-to-process))))

(defn file-code-blocks
  [{:keys [file-path options]
    :or {options {:indentation-level-to-search 0}}}]
  (slurp file-path)
  )

(comment
  (re-find #"." (str (nth (slurp "/home/smokeonline/projects/looset-diagram-mvp/test/source-code-examples/api.js")
                               38
                               )))
  (re-find #"[\-_]" "'")
  (update {:a '({:b 5})} :a conj {:b 3})
  (pprint (reduce lexical-analyzer
          {:position [1 1]
             :char "i"
             :state :first-char}
          "m 'lodash.isequal';"
          #_(drop 5 (take 8 (slurp "/home/smokeonline/projects/looset-diagram-mvp/test/source-code-examples/api.js")))))
  (pprint (update (generate-token-list
                    ;; "import 'lodash.isequal';\nimport somethinelse"
                    (take 300 (slurp "/home/smokeonline/projects/looset-diagram-mvp/test/source-code-examples/api.js"))
                    )
                  :token-occurrencies #(sort-by val %)))
                  ;; (take 4 (reverse (slurp "/home/smokeonline/projects/looset-diagram-mvp/test/source-code-examples/api.js")))  
  (pprint (:token-list (generate-token-list (take 300 (slurp "/home/smokeonline/projects/looset-diagram-mvp/test/source-code-examples/api.js")) #_"import 'lodash.isequal';\nimport somethinelse")))
  )

(deftest lexical-analyzer-test
  (let [file (slurp "test-resources/source-code-examples/api.js")]
    (is (= 12
           (-> (generate-token-list file)
               :token-list
               (->> (map :token))
               frequencies
               (get "import" 0)))))
  (let [file (slurp "test-resources/source-code-examples/api.js")]
    (is (= 35
           (-> (generate-token-list file)
               :token-list
               (->> (map :token))
               frequencies
               (get "api" 0)
               ))))
  (let [file (take 300 (slurp "test-resources/source-code-examples/api.js"))]
    (is (= {:token "const", :category :word, :position [7 31]}
           (-> (generate-token-list file)
               :token-list
               reverse
               (nth 2)
               ))))
  (is (= {"abc" 1, "//" 2, "oi" 1, "/" 1 "oci" 1, "lc" 1, "\n" 1, "EOF" 1}
         (-> "abc//w\noi/oci//b\nlc"
             generate-token-list
             :token-list
             (->> (map :token))
             frequencies)))
  (is (= {"abc" 1, "//" 1, "EOF" 1}
         (-> "abc//woi/oci//blc"
             generate-token-list
             :token-list
             (->> (map :token))
             frequencies)))
  )
