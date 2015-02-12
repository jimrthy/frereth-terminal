(ns frereth-terminal.frerepl
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
  (:require [clojure.core.async :as async]
            [clojure.repl :refer [pst]]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [penumbra.app :as app]
            [penumbra.app.manager :as manager]
            [penumbra.text :as text]
            [penumbra.utils :as util]
            [schema.core :as s])
  (:import [clojure.lang Atom]
           [com.stuartsierra.component SystemMap])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def terminal-state {:buffer [s/Str]
                     :first-visible-line s/Int
                     :input-buffer s/Str
                     :ps1 s/Str})

(declare draw-frame err-handler input-handler)
(s/defrecord Shell [penumbra :- SystemMap
                    stage
                    state :- Atom  ; of terminal-state
                    std-in :- util/async-channel
                    std-out :- util/async-channel
                    std-err :- util/async-channel
                    title :- s/Str
                    janitor :- util/async-channel
                    worker :- util/async-channel]
  component/Lifecycle
  (start
   [this]
   (let [real-title (or title "Frerminal")
         initial-state (into {:buffer [""]
                              :first-visible-line 0
                              :input-buffer ""
                              :ps1 (constantly "frepl =>")}
                             (or (and state @state)
                                 {}))
         ;; Need something useful to hand to event-loop
         basics (into this {:state (atom initial-state)
                            :std-in (async/chan)
                            :std-out (async/chan)
                            :std-err (async/chan)
                            :title real-title})
         ;; These handlers need access to those channels to do anything interesting
         stage (assoc basics
                      :worker (input-handler basics)
                      :janitor (err-handler basics))
         app (app/create-stage {:title title
                                :initial-state initial-state
                                ;; TODO: At the very least, it seems
                                ;; like we probably want to handle
                                ;; resize events.
                                ;; Although, really, the framework
                                ;; should handle those
                                :callbacks {:display draw-frame}
                                :channels {:char-input std-in}})
         started (component/start app)
         mgr (:manager penumbra)]
     ;; This fails. By definition, we need to supply an
     ;; App here. But that's overkill for what we have and
     ;; are doing.
     ;; Or, at least, this approach is over-simplified
     (manager/add-stage! mgr started)
     (assoc stage :stage started)))
  (stop
   [this]
   (doseq [c [std-in std-out std-err]]
     (util/close-when! c))
     ;; This next line is begging for trouble if something
     ;; exits unexpectedly

   (when worker
     (async/<!! worker))
   (when janitor
     (async/<!! janitor))
   (manager/clear-stage! (:manager penumbra) stage)
   (into this {:buffer nil
               :stage (component/stop stage)
               :std-in nil
               :std-out nil
               :std-err nil
               :janitor nil
               :worker nil})))

(defmulti handle-keyword
  "Update buffer based on a special character we just received.
Possibly send feedback if it's something that should be visible"
  (fn [state c]
    c))

(defmulti handle-compound
  "This is really for finer control. Like tracking whether
a control key is pressed. In the future, could be expanded to
react to other events, like mouse clicks."
  (fn
    [state c]
    (first c)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal

(comment (defn prompt [state]
           (:ps1 state)))

;; Completely arbitrary and ridiculous value because I have to start somewhere
(def line-height 10)
(defn draw-frame
  [[dt t] state]
  (let [top (:first-visible-line state)
        possibly-visible-lines (drop top (:buffer state))
        height (:height state)
        max (int (/ height line-height))
        bottom (+ top max)
        previous-lines (take max possibly-visible-lines)
        lines (if (< (count possibly-visible-lines) max)
                (conj (list (str ((:ps1 state)) " " (:input-buffer state)))
                      previous-lines))]
    (println "Writing\n" lines "\nto the buffer, because of\n"
             (with-out-str (pprint state)))
    (dorun (map-indexed (fn [idx line]
                          (text/write-to-screen line 0 (* idx line-height)))))))

(defmacro with-err-str
  "Evaluates exprs in a context in which *err* is bound to a fresh
StringWriter. Returns the string created by any nested printing calls.

Shamelessly stolen from
stackoverflow.com/questions/17314128/get-stack-trace-as-string

The real point is to get a stack trace.
Note that it would [almost definitely] be better to just do that."
  [& body]
  `(let [s# (new java.io.StringWriter)]
     ;; This seems to depend pretty heavily on execution
     ;; remaining under this particular thread of control.
     ;; Considering the use-case, that seems acceptable.
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn tb->string
  ([^Exception ex]
     (tb->string ex 30))
  ([^Exception ex ^long depth]
     (let [out (java.io.StringWriter.)]
       (binding [*err* out]
         (pst ex depth)
         (str out)))))

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
                  (let [tb (with-err-str (pst ex 30))]
                    (async/>!! err tb))))
              ;; FIXME: Newlines don't work
              (async/>!! out "\n")
              (async/>!! out (:ps1 state))
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
      (let [^String buffer (:buffer state)
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

(s/defn err-handler :- util/async-channel
  [this :- Shell]
  (async/go
    (let [err (:std-err this)]
      (loop []
        (when-let [v (async/<! err)]
          ;; TODO: Add things like timestamps and context
          (println v)
          (recur))))))

(s/defn input-handler :- util/async-channel
  [this :- Shell]
  (async/go
    (let [in (:std-in this)
          out (:std-out this)]
      (loop []
        (when-let [c (async/<! in)]
          (try          
            (cond
              ;; Magic key, like backspace, shift,
              ;; or return
              (keyword? c) (handle-keyword this c)
              ;; Something more specialized
              (or (vector? c) (seq? c)) (handle-compound this c)
              ;; Assume this means an ordinary
              ;; character was typed
              :else (handle-ordinary-key this c))
            (catch RuntimeException ex
              ;; N.B. This isn't fatal!
              (let [err (:std-err this)]
                (async/>! err ex))))
          (recur)))
    (println "Exiting REPL"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn ctor
  [{:keys [ps1]
    :or {ps1 "> "}}]
  (map->Shell {:ps1 ps1}))
