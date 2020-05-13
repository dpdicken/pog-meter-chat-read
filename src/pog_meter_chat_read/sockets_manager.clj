(ns pog-meter-chat-read.sockets-manager
    (:use [pog-meter-chat-read.util :only [uuid in?]]
          [pog-meter-chat-read.socket-cleanup :only [scan-for-inactive-items]]
          [pog-meter-chat-read.irc :as irc]
          [pog-meter-chat-read.time-series :as ts]
          [pog-meter-chat-read.env :as env]
          [org.httpkit.server :only [run-server with-channel on-close websocket? send! on-receive]]
          [clojure.string :only [split lower-case trim-newline]]
          [clojure.data.json :only [read-str write-str]]
          [clojure.walk :only [keywordize-keys]]))

;; TODO scan used ports and check stale activity and remove 

;; TODO also scan for stale users within a channel

; Maps channel ids to channel information. The channel information will contain port and a map of clients unique ids to client information. 
; The client information will contain last updated time in epoch seconds.
; Example:
; {
;   :channel-id {
;     :port 81
;     :server-close function-that-closes-server
;     :clients {
;       :client1 {
;         :last-update-epoch-milli 9001
;         :channel channel-object
;       }
;       :client2 {
;         :last-update-epoch-milli 5000 
;         :channel channel-object
;       }
;     }
;   }
; }
(def channel-info (atom {}))

; TODO should probably set a max and error out if no port available
(defn get-next-port []
  (let [used (concat [7999] (map :port (vals @channel-info))) ; concating 7999 will guarantee 8000 is the lowest socket port used
        plausible (map inc used)] 
    (first (filter (fn [x] (not (in? used x))) plausible))))

(defn remoteSocketAddressFromAsyncSocket [socket]
  (second (split (.toString socket) #"<->")))

(def keywords ["dangPOG" "POGGERS" "dangPogU" "PogU" "PogChamp" "POG"])
(defn contains-keywords [data-str]
  (> (count (filter #(.contains data-str %) keywords)) 0))

(defn update-time-for-channel [channel-key]
  (swap! channel-info #(assoc % channel-key (merge (get % channel-key) {:last-update-epoch-milli (System/currentTimeMillis)}) ) ))

(defn track-client [channel-id channel]
  (let [current-epoch-time (System/currentTimeMillis)
        unique-id (remoteSocketAddressFromAsyncSocket channel)]
      (swap! channel-info update-in [(keyword channel-id) :clients] 
        (fn [a] (merge a {(keyword unique-id) {:last-update-epoch-milli current-epoch-time :channel channel}}) ))))

(defn remove-client [channel-id channel info]
  (let [channel-key (keyword channel-id)
        clients (get-in info [channel-key :clients])
        client (first (filter #(= (:channel (val %)) channel) clients))]
    (assoc info channel-key (merge (get info channel-key) {:clients (dissoc clients (key client))}) ) ))

(defn track-channel [id port server-close irc-conn]
  (swap! channel-info update (keyword id) (fn [a b] b) {:id id :port port :server-close server-close :irc-conn irc-conn :last-update-epoch-milli (System/currentTimeMillis)}))

(defn handle-received-data [channel-id-key data]
  (let [all-channel-clients (:clients (get @channel-info (keyword channel-id-key)))]
    (println "Received data on channel" channel-id-key ". Producing to" (keys all-channel-clients))
    (update-time-for-channel (keyword channel-id-key))
    (cond 
      (contains-keywords data)
      (let [bucket-result (ts/increment-current-and-remove-old channel-id-key)
            bucket-sum (reduce + (vals bucket-result))]
        (run! (fn [c] (send! c (write-str {:sum bucket-sum :data data :reason "msg"}))) (map :channel (vals all-channel-clients))))
      :else (println (str "Keywords" keywords " not found in message '" (trim-newline data) "'"))
    )))

(defn instantiate-channel-server [channel-id channel-port req]
  (println "Received request to connect to socket on port" channel-port) 
  (with-channel req channel ; get the channel
    ; Store the channel and client information
    (track-client channel-id channel)
    ; communicate with client using method defined above
    (on-close channel (fn [status]
                        (println "Closing channel" channel-id "on port" channel-port)
                        (swap! channel-info #(remove-client channel-id channel %)))) 
    (on-receive channel (partial handle-received-data (keyword channel-id))))) ; data is sent directly to the client

(defn create-channel-server [channel-id]
  (let [existing-channel (get @channel-info (keyword channel-id))
        next-port (get-next-port)] 
    (if (nil? existing-channel )
      (do 
        (let [irc-conn (create-irc-connection env/irc-websocket-url env/irc-user channel-id handle-received-data)
              ; run-server returns a function which when called shuts down server
              server-close (run-server (partial instantiate-channel-server channel-id next-port) {:port next-port})]
          (track-channel channel-id next-port server-close irc-conn)
          (println "Success creating channel!")
          {:port next-port :id channel-id :success true}))
      {:success true :port (:port existing-channel) :id channel-id})))

; TODO check if channel already exists and dont allow overwrite
(defn create-channel [req]
    (let [data (keywordize-keys (read-str (slurp (:body req))))
          channel-id (:channel data)]
      (if (nil? channel-id)
        { :success false :message (str "Unknown channel " channel-id) }
        (create-channel-server channel-id))))

(defn get-channel-port-and-register-client [req]
  (let [data (keywordize-keys (read-str (slurp (:body req))))
        channel-key (keyword (lower-case (:channel data)))
        channel (get @channel-info channel-key)]
    ; TODO track client
    (if (nil? channel)
      {:success false :message (str "No channel found" (name channel-key))}
      {:port (:port channel) :channel-id (name channel-key) :success true})))

; For now scan every thirty seconds
(def timeout-scan-frequency-seconds 30)
(defn kick-off-channel-and-client-timeout-scan [inactivity-timeout-millis]
  (scan-for-inactive-items (* timeout-scan-frequency-seconds 1000) inactivity-timeout-millis channel-info)
  (ts/scan-for-old-buckets-kockoff channel-info))