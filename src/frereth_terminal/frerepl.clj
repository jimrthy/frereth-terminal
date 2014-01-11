(ns frereth-terminal.frerepl
  (:require [clojure.core.async :as async])
  (:gen-class))

"A terminal's pretty boring without some sort of communication.
This might someday be worth consideration as some sort of 
foot-in-the-door along those lines.

Comparing it to something like /bin/sh obviously involves lots of
delusions of grandeur.

But it might not be too pretentious to hope that someday it might
grow up to rival command.com.

The main point is that frerminal needs somewhere to send the input
it receives from the user, and a source for the feedback that it
shows in response.

This is intended to be guide for something along those lines."

(defn prompt []
  "> ")

(defmulti handle-keyword
  "Update buffer based on a special character we just received"
  (fn [out err buffer c]
    c))

(defmethod handle-keyword :return
  [out err buffer c]
  (try
    ;; See if we have a full expression
    (let [expr (read-string buffer)]
      (try
        (let [result (eval expr)]
          (println result)
          (async/>!! out "\n")
          (async/>!! out result))
        (catch RuntimeException ex
          (async/>!! err ex)
          ;; TODO: Print the stacktrace, also
          ;; (FWIW)
          ))
      (async/>!! out "\n")
      (async/>!! out (prompt))
      "")
    (catch RuntimeException ex
      ;; This isn't really an exception, but
      ;; it seems like it might be worth
      ;; mentioning
      (let [msg (str "Warning:\n"
                     ex
                     "\ntrying to evaluate:\n"
                     buffer)]
        (async/>!! err ex))
      (str buffer \newline))))

(defmethod handle-keyword :default
  [_ err buffer c]
  (let [msg (str "Warning: unhandled keyword " c)]
    (async/>!! err c))
  buffer)

(defn shell
  "I'm strongly tempted to do something like proxy/reify here.
This seems like the sort of place where a stateful/OOP/functional
mix like you find in common lisp or scala seems *extremely*
appropriate.

That's probably because I still have lots of bad habits."
  [in out err]
  (println "Showing prompt")
  (async/>!! out (prompt))
  (let [result (async/go
                (loop [c (async/<! in)
                       ;; TODO: It's tempting to use some
                       ;; sort of java buffer
                       buffer ""]
                  (when c
                    (println "Received keypress: " c)
                    (let [update
                          (if (keyword? c)
                            (handle-keyword out err buffer c)
                            (do
                              (async/>! out c)
                              (str buffer c)))]
                      (recur (async/<! in) update))))
                (println "Cleaning up REPL shell")
                (async/close! out)
                (async/close! err))]
    result))
