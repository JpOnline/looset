(ns looset-diagram-mvp.code-blocks
  (:require
    [looset-diagram-mvp.core :as lexical-analyzer]

    [clojure.test :refer :all]
    [clojure.pprint :refer [pprint]]
    ))

(def another-random-def
  random-value)

(defn identifier
  ([{:keys [token-list] :as big-state}]
   (identifier token-list (merge {:state :initial-state :indentation-level-to-search 0 :indentation-level 0}
                                 (dissoc big-state :token-list))))
  ([[token & token-list] big-state]
   (when token
     (let [big-state (process-token (assoc big-state :token token))
           tail (when (not (:errors big-state)) (lazy-seq (identifier token-list big-state)))]
       (cons (-> big-state (dissoc :token-list :token-occurrencies))
             tail)))))
