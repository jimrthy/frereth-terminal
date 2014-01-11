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

(defn init []
  {;; What's currently being manipulated?
   ;; TODO: It's tempting to use some
   ;; sort of java buffer
   :buffer ""
   ;; What's the state of everything?
   :keys {}
   :ps1 "> "})

(defn start 
  [dead]
  dead)

(defn stop
  [alive]
  alive)

(defn prompt [state]
  (:ps1 state))

(defmulti handle-keyword
  "Update buffer based on a special character we just received.
Possibly send feedback if it's something that should be visible"
  (fn [state c]
    c))

(defmethod handle-keyword :return
  [state c]
  (if-let [buffer (:buffer state)]
    (let [err (:std-err state)
          updated-buffer
          (try
            ;; See if we have a full expression
            (let [expr (read-string buffer)
                  out (:std-out state)]
              (try
                (let [result (eval expr)]
                  (async/>!! out "\n")
                  (async/>!! out result))
                (catch RuntimeException ex
                  (async/>!! err ex)
                  ;; TODO: Print the stacktrace, also
                  ;; (FWIW)
                  ))
              ;; FIXME: Newlines don't work
              (async/>!! out "\n")
              (async/>!! out (prompt state))
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
              (str buffer \newline)))]
      (into state {:buffer updated-buffer}))
    (throw (RuntimeException. (str "No buffer to manage in:\n'"
                                   state "'")))))

(defmethod handle-keyword :back
  [state c]
  ;; Note that this doesn't cope with any sort of 
  ;; idea of a cursor position anywhere except the end
  ;; of a string.
  ;; Oh well. It's a start
  (if-let [out (:std-out state)]
    (do
      (async/>!! out :backspace)
      (let [buffer (:buffer state)
            len (.length buffer)]
        (into state
              {:buffer
               (if (< 0 len)
                 (subs buffer 0 (dec len))
                 buffer)})))
    (throw (RuntimeException. (str "Error: Missing STDOUT in '\n"
                                   state "'")))))

(defmethod handle-keyword :none
  [state _]
  ;; I'm getting this when I press the start key.
  ;; It seems pretty likely that it involves some sort
  ;; of really funky keycode, since it's a magical
  ;; signal key used by my window manager
  state)

(defmethod handle-keyword :default
  [state c]
  (let [msg (str "Warning: unhandled keyword " c)]
    (if-let [err (:std-err state)]
      (async/>!! err msg)
      (throw (RuntimeException. (str "Error: Missing STDERR in\n'"
                                     state "'")))))
  state)

(defmulti handle-compound
  "This is really for finer control. Like tracking whether
a control key is pressed. In the future, could be expanded to
react to other events, like mouse clicks."
  (fn
    [state c]
    (first c)))

(defn handle-key-change
  [state c which]
  (let [key-state (:keys state)
        updated (into key-state [c which])]
    (into state (:keys updated))))

(defmethod handle-compound :down
  [state c]
  (handle-key-change state (second c) :down))

(defmethod handle-compound :up
  [state c]
  (handle-key-change state (second c) :up))

(defmethod handle-compound :default
  [state c]
  (let [err (:std-err state)
        msg (str "FAIL: Unhandled compound message:\n" c)]
    (async/>!! err msg)
    ;; Q: Is this error bad enough to warrant throwing an exception?
    ;; A: Yeah, I'd say so. This is a pretty nasty API mismatch
    (throw (RuntimeException. msg)))
  state)

(defn handle-ordinary-key
  "Some standard alphanumeric/symbol key was pressed."
  [state c]
  ;; TODO: Examine state to see if we're dealing with a
  ;; "chord"
  (if-let [out (:std-out state)]
    (async/>!! out c)
    (throw (RuntimeException. (str "Error: Missing STDOUT in "
                                   state))))
  (let [buffer (:buffer state)]
    (into state
          {:buffer (str buffer c)})))

(defn event-loop [initial-state]
 (async/go
  (let [in (:std-in initial-state)
        out (:std-out initial-state)]
    (loop [c (async/<! in)
           ;; More importantly: this needs to be
           ;; a member of a map that tracks the
           ;; view of state on this side of things.
           state initial-state]
      (when c
        (let [update
              (cond
               ;; Magic key, like backspace, shift,
               ;; or return
               (keyword? c)
               (handle-keyword state c)
               ;; Something more specialized
               (or (vector? c) (seq? c))
               (handle-compound state c)
               ;; Assume this means an ordinary
               ;; character was typed
               :else
               (handle-ordinary-key state c))]
          (recur (async/<! in) update))))
    (println "Cleaning up REPL shell")
    (async/close! out)
    (let [err (:std-err initial-state)]
      (async/close! err)))))

(defn shell
  "I'm strongly tempted to do something like proxy/reify here.
This seems like the sort of place where a stateful/OOP/functional
mix like you find in common lisp or scala seems *extremely*
appropriate.

That's probably because I still have lots of bad habits."
  [in out err]
  (let [dead-state (init)
        preliminary-state (start dead-state)]
    (println "Showing prompt")
    (async/>!! out (prompt preliminary-state))

    (let [state
          (into preliminary-state 
                {:std-in in
                 :std-out out
                 :std-err err})]
      (event-loop state))))
