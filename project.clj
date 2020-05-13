(defproject pog-meter-chat-read "0.1.0-SNAPSHOT"
  :description "An app to allow the creation of a socket by id and allows another client(s) to subscribe to that socket by the same id."
  :url "http://dicken.dev"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [http-kit "2.3.0"]
                 [compojure "1.6.1"]
                 [ring-cors "0.1.13"]
                 [org.clojure/data.json "1.0.0"]
                 [stylefruits/gniazdo "1.1.3"]]
  :main pog-meter-chat-read.core
  :repl-options {:init-ns pog-meter-chat-read.core})
