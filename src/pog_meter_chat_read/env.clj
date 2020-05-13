(ns pog-meter-chat-read.env)

(def port (Integer/parseInt (or (System/getenv "API_PORT") "80")))
(def expire-channel-after (Integer/parseInt (or (System/getenv "EXPIRE_AFTER_SECONDS") "3600")))
(def irc-nick (or (System/getenv "TWITCH_IRC_NICK_NAME") ""))
(def irc-oauth (or (System/getenv "TWITCH_IRC_OAUTH") ""))
(def irc-user {:nick irc-nick :oauth irc-oauth})
(def irc-websocket-url (or (System/getenv "TWITCH_IRC_WEBSOCKET_URL") "ws://irc-ws.chat.twitch.tv:80"))