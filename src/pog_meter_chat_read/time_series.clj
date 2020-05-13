(ns pog-meter-chat-read.time-series
    (:use [org.httpkit.server :only [send!]]
          [clojure.data.json :only [write-str]]
          [pog-meter-chat-read.util :only [num-to-key parse-long-or-negative]]))

(def buckets (atom {}))

; Buckets per second
(def bucket-granularity 1000)

; 5 total buckets, 5*1000 milli = 5 second span
(def buckets-to-maintain 5)

(defn format-and-parse-number-string [num-str]
    (Long. (format "%.0f" num-str)))

(defn upsert-increment-bucket [channel bucket-key channel-buckets]
    (let [buckets (get channel-buckets channel)
          bucket (get buckets bucket-key)]
        (if (nil? buckets)
            (assoc channel-buckets channel {bucket-key 1}))
            (if (nil? bucket)
                (assoc channel-buckets channel (assoc buckets bucket-key 1))
                (assoc channel-buckets channel (assoc buckets bucket-key (+ 1 bucket)))
            )))

(defn remove-old-buckets-from-channel [channel time channel-buckets]
    (let [buckets (get channel-buckets channel)
          bucket-keys (keys buckets)
          expired-buckets (filter #(< (parse-long-or-negative (name %)) time) bucket-keys)]
        (if (nil? buckets)
            channel-buckets
            (assoc channel-buckets channel (apply dissoc buckets expired-buckets)))
    ))

(defn remove-old-buckets [channel]
    (let [current-time (Long. (System/currentTimeMillis))
          removable-time (format-and-parse-number-string (Math/floor (/ (- current-time (* bucket-granularity buckets-to-maintain)) bucket-granularity)))]
        (swap! buckets (partial remove-old-buckets-from-channel (keyword channel) removable-time))
        (get @buckets (keyword channel))))

(defn increment-current-and-remove-old [channel]
    (let [current-time (Long. (System/currentTimeMillis))
          current-bucket (format-and-parse-number-string (Math/floor (/ current-time bucket-granularity)))
          removable-time (format-and-parse-number-string (Math/floor (/ (- current-time (* bucket-granularity buckets-to-maintain)) bucket-granularity)))]
        (swap! buckets (partial upsert-increment-bucket (keyword channel) (num-to-key current-bucket)))
        (remove-old-buckets channel)))

(defn handle-tailing-off-time-series [channel-id-key all-channel-clients]
    (let [bucket-result (remove-old-buckets channel-id-key)
          bucket-sum (reduce + (vals bucket-result))]
        (run! (fn [c] (send! c (write-str {:sum bucket-sum :reason "scan"}))) (map :channel (vals all-channel-clients)))))

(def sleep-time-millis 1000)
(defn scan-for-old-buckets-kockoff [channel-info-atom]
    (println "Kicking off old bucket scan thread")
    (future 
        (while true (do
            (doseq [channel (keys @channel-info-atom)] (handle-tailing-off-time-series channel (:clients (get @channel-info-atom channel))))
            (Thread/sleep sleep-time-millis)))))