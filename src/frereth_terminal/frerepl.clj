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

(defn shell
  "I'm strongly tempted to do something like proxy/reify here.
This seems like the sort of place where a stateful/OOP/functional
mix like you find in common lisp or scala seems *extremely*
appropriate.

That's probably because I still have lots of bad habits."
  [in out err]
  (let [io (agent [in out err])]
    ;; This is where life gets interesting.
    ;; And the approach is probably mostly wrong.
    ;; Should start a thread that reads from in and processes
    ;; it, spitting the result to either out or err
    ;; (depending on whether it's an exception or not).
    ;;
    ;; The form it's reading should really be stored in
    ;; something like an agent
    ;;
    ;; Really need to deal with actual key strokes, backspaces,
    ;; ctrl-chars, Unicode, etc, etc.
    (throw (RuntimeException. "What should happen here?"))))
