(ns pog-meter-chat-read.core
  (:use [compojure.route :only [files not-found]]
      [compojure.core :only [defroutes GET POST DELETE ANY context]]
      [ring.middleware.cors :only [wrap-cors]]
      [clojure.data.json :only [write-str]]
      [pog-meter-chat-read.sockets-manager :only [create-channel 
                                                  get-channel-port-and-register-client
                                                  kick-off-channel-and-client-timeout-scan]]
      [pog-meter-chat-read.env :as env]
      org.httpkit.server))

;; TODO change all println to a logging framework

; Handles a request to create a channel
; It does this by 
;   1) Creating the channel in the channel map
;   2) Opening a socket for the creating client to use
;   3) Sending the port of the socket back to the client 
(defn create-channel-handler [req]
  (let [body (create-channel req)]
    (if (:success body)
      {:status 200 
       :headers {"Content-Type" "application/json"} 
       :body (write-str body)}
      {:status 500 
       :headers {"Content-Type" "application/json"} 
       :body (write-str {:error "Unable to create channel"})})))

(defn join-channel [req]
  (let [body (get-channel-port-and-register-client req)]
    (if (:success body)
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (write-str body)}
      {:status 500
       :headers {"Content-Type" "application/json"} 
       :body (write-str {:error "Unable to retrieve channel"})}
    )))

(defn pong [req]
  {:status 200
   :headers {"Content-Type" "text/html"} 
   :body "pong"})

(defroutes all-routes
  (GET "/ping" [] pong)
  (context "/api" [] 
    (POST "/create" [] create-channel-handler)
    (POST "/join" [] join-channel))) ;; all other, return 404

(def app
  (-> all-routes
    (wrap-cors
      :access-control-allow-origin [#".*"]
      :access-control-allow-headers ["Content-Type"]
      :access-control-allow-methods [:get :put :post :delete])))

(defn -main []
    (run-server app {:port env/port})
    (kick-off-channel-and-client-timeout-scan (* env/expire-channel-after 1000))
    (println (str "Listening on port " port)))
