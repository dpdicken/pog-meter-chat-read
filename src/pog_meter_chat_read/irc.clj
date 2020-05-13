(ns pog-meter-chat-read.irc
  (:use [gniazdo.core :as ws]
        [clojure.string :as str])
  (:import (java.net Socket)
           (java.io OutputStreamWriter BufferedWriter InputStreamReader BufferedReader) 
           (java.nio.charset Charset)))

(def channel-to-socket (atom {}))

(def irc-msg-end "\r")

(defn auth-connection [sock user]
    (ws/send-msg sock (str "PASS " (:oauth user) irc-msg-end))
    (ws/send-msg sock (str "NICK " (:nick user) irc-msg-end)))

(defn join-channel [sock channel]
    (ws/send-msg sock (str "JOIN #" channel irc-msg-end)))

(defn handle-incoming-message [channel received-callback message]
    (cond 
        ; Handle ping response to keep connection alive
        (re-find #"^PING" message)
        (let [sock (get @channel-to-socket (keyword channel))]
            (ws/send-msg sock (str "PONG "  (re-find #":.*" message))))
        :else 
        (let [split (str/split message (re-pattern (str "PRIVMSG #" channel " :")))
             user-msg (get split 1)]
             (cond (not (nil? user-msg))
                   (received-callback channel user-msg)))))

(defn create-irc-connection [ws-url user twitch-channel received-callback]
    (let [twitch-channel-lowercase (str/lower-case twitch-channel)
          sock (ws/connect ws-url :on-receive #(handle-incoming-message twitch-channel-lowercase received-callback %) :on-error #(println (str "error" %)))]
         (dosync 
            (auth-connection sock user)
            (println "Performed IRC AUTH")
            (join-channel sock twitch-channel-lowercase)
            (println "Joined IRC channel" twitch-channel-lowercase)
            (swap! channel-to-socket #(merge % {(keyword twitch-channel-lowercase) sock})))
        sock))