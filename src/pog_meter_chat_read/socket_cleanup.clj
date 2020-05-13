(ns pog-meter-chat-read.socket-cleanup
    (:use [org.httpkit.server :only []]))

(defn find-expired-items [items expiration-time]
    (filter (fn [item] (< (:last-update-epoch-milli item) expiration-time)) items))

(defn timeout-expired-channels [duration-millis-for-timeout channel-info]
    (let [current-epoch-millis (System/currentTimeMillis)
          expiration-time (- current-epoch-millis duration-millis-for-timeout)
          expired-channels (find-expired-items (vals channel-info) expiration-time)]
        (if (or (nil? expired-channels) (= 0 (count expired-channels)))
            channel-info 
            (do
                (println "Expiring channels with ids " (map :id expired-channels))
                ; Call the server shutdown function for each expired channel
                (doseq [i expired-channels] ((:server-close i)))
                ; Dissoc each expired channel key and return
                (apply dissoc channel-info (map (fn [x] (keyword (:id x))) expired-channels))
        ))))

(defn scan-for-inactive-items [scan-sleep-time-millis duration-to-timeout channel-info-atom]
    (println "Kicking off inactive channel scan thread")
    (let [timeout-expired-channels-with-duration (partial timeout-expired-channels duration-to-timeout)]
        (future 
            (while true (do
                (println "Timeout thread scanning for timeouts!")
                (swap! channel-info-atom timeout-expired-channels-with-duration)
                (Thread/sleep scan-sleep-time-millis))))))