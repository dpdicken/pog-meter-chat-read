(ns pog-meter-chat-read.util)

(defn uuid [] (.toString (java.util.UUID/randomUUID)))

(defn in? 
  "true if coll contains elm"
  [coll elm]  
  (some #(= elm %) coll))

(defn num-to-key [num]
  (keyword (str num)))

(defn parse-long-or-negative [num-str]
  (try
    (Long. num-str)
    (catch Exception e (println (str "Unable to parse string" num-str "as int" -1)))))