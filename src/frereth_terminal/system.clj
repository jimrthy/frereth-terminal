(ns frereth-terminal.system
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer [pprint]]
            [frereth-terminal.frerepl :as repl]
            [penumbra.app :as app]
            [penumbra.text :as text]
            [penumbra.app.window :as wndo])
  (:gen-class))

"I'm starting to think of this as a terminal emulator"

(set! *warn-on-reflection* true)

(defn init
  "Empty system to build upon later"
  []
  {:width 640
   :height 480
   :title "nothing happening yet"
   :text ""
   ;; For sending input messages from the user to...whatever program is running
   :input nil
   ;; For getting output from the "program" being run and providing feedback to
   ;; the end-user
   :output nil
   ;; And suddenly this distinction makes a lot of sense
   :std-err nil
   ;; Place to hold rules for dealing with line history
   :history-rules {}
   ;; TODO: Start with current system time?
   :time 0})

(defn close
  "Window/app/terminal is going away"
  [state]
  ;; TODO: Save the current state so it can be restored during the next run.
  (println "Closing")
  (pprint state)
  state)

(defn update-screen-contents
  "This should really be pretty fancy and tricky, with lots of state manipulation.

modifiers could, at least theoretically, include pretty much anything that's legal
in any terminal, such as BEL, go-to position, start color/face/font, CR, LF, BS,
forward char, up line, etc, etc.

original is what we start with.

Honestly, I'm only beginning to grapple with what this means and how it might
all apply. Consider the man pages for termcap, terminfo, and termios for
starters.

Learning how ncurses works under the covers might well be highly profitable.

Or it might be a complete and total waste of time. It's not like I have a lot
of reason to worry about maintaining backwards "
  [original modifiers]
  ;; For now, take the cheeseball easy way out.
  (str original modifiers))

(defn prune-history
  "Think of a terminal that lets you scroll back some configurable
number of lines.

The first place this is obviously interesting is that shared structure
seems to mean that old discarded lines probably won't get garbage-collected.

The second place is that this really should be customizable.

The third is the actual scrolling in the first place."
  [history rules]
  ;; For now, take the low road [yet again]
  history)

(defn update
  "Perform behind-the-scenes state updates just before display gets called each frame"
  [[delta time] state]
  ;; TODO: Don't really want to be handling STDERR here, but I have to start someplace
  ;; This does not seem to be where my state is getting so thoroughly corrupted
  (async/thread (let [err (:err state)
                      result
                      (loop [to (async/timeout 1)
                             error-message ""]
                        (let [[v c] (async/alts!! [err to])]
                          (if (= c err)
                            (recur (async/timeout 1)
                                   (str error-message v))
                            error-message)))]
                  (when (seq result)
                    (pprint result))))

  ;; Actual output
  (if-let [c (:output state)]
    (do
      (comment
        ;; Verified: This is good and gets called a lot.
        ;; Right up until I handle a keystroke.
        (println "Updating. Current State:")
        (pprint state))

      ;; Don't spend more than 0.1 sec in here
      (let [final-timeout (async/timeout 100)]
        ;; Append output sent from the "real process" 
        (let [appended-text
              ;; Don't wait more than 2 ms for 'shell' output
              (loop [to (async/timeout 2)
                     updated-text ""]
                (let [[v resp] (async/alts!! [c to final-timeout])]
                  (if (= resp c)
                    (recur (async/timeout 2)
                           (str updated-text v))
                    updated-text)))
              original-text (:text state)
              modified-text (update-screen-contents original-text appended-text)
              result-text (prune-history original-text (:history-rules state))]
          (assoc state
            :text result-text
            :delta-t delta
            :time time))))
    (do
      (println "Missing output channel!\nState:")
      ;; I'm getting an async/channel here. WTF?
      (pprint state)
      (println \newline)
      (throw (RuntimeException. "Initialization failure")))))

(defn display
  "I'm pretty sure this is supposed to actually draw the state that
update set up."
  [[dt t] state]
  (text/write-to-screen (:text state) 0 0)
  (app/repaint!))

(defn restore-last-session
  "Should really be saving the previous session and restoring it here.
Then again, that should be an option specified by the caller.
For now, just make it something different than dead"
  [dead]
  (into dead
        {:width 1024
         :height 768
         :title "Restored"}))

(defn bring-to-life
  "Take a dead system, with settings [possibly] restored from the previous
run, and update state to make things happen"
  [restored shell]
  (let [in (async/chan 10)
        out (async/chan 10)
        err (async/chan 10)]
    (assoc restored
      :input in
      :output out
      :std-err err
      :shell (shell in out err))))

(defn update-title!
  [title]
  (app/title! title))

(defn configure-window!
  [state]
  (update-title! (:title state))
  (app/vsync! true)
  state)

(defn key-press
  "Send a key-press message to the function this provides the front-end for.
TODO: Should really be forwarding along all messages.

OTOH: It might make a whole lot more sense to establish the idea of 'focus'
and send the message to whichever input channel has the focus. Which really
leads to a hierarchical DOM sort of thing, where events bubble as far down
as possible, but then they'd have to bubble right back up until they find a
handler that marks them 'done'.

For that matter, update events should then bubble out to any cells that were
watching for changes.

Having subscribers registering for interesting events seems like a mighty
good compromise. It's definitely something interesting to think about, though
it doesn't much apply here."
  [key state]
  (let [c (:input state)]
    ;; There don't seem to be many reasons to make this happen in a
    ;; background thread, but the only reasons not to do so involve
    ;; keystrokes arriving in the wrong order...and I seriously doubt
    ;; that anyone types fast enough for that to be an issue.
    ;; Of course, that goes out the window as soon as someone puts a
    ;; PTTY in front of this and we need to go back to worrying about
    ;; things like parity bits and all the nonsense that really go
    ;; along with low-level terminals.
    ;; For now, go with the "I'm totally totally abusing the abundance
    ;; of hardware resources" route.
    ;; TODO: Add a configuration option to decide how this gets sent.
    (async/go
     (println "Handling Key Press: " key)
     (async/>! c key)))
  state)

(defn start
  "Run all the side-effects associated with starting a system"
  [dead]
  (let [prev (restore-last-session dead)
        alive (bring-to-life prev repl/shell)]
    (println "Bringing Good Things to life. Initial State:")
    (pprint alive)

    (app/start
     {:init configure-window!
      :update update
      :display display
      :key-press key-press
      :close close}
     alive)))

(defn stop
  "Run all the side-effects to kill a system off"
  [living]
  (if-let [main (app/app)]
    (wndo/destroy! main)
    (println "Warning: no application"))

  (if-let [in (:input living)]
    ;; It's very tempting to send the shell some
    ;; sort of terminate signal via the :input
    ;; channel.
    ;; That temptation seems severely misguided.
    ;; If nothing else, it should just check for
    ;; nil coming from :input and wrap up because
    ;; that channel's closed.
    (async/close! in)
    (println "Warning: No STDIN"))

    ;; OTOH:
    ;; Honestly, the shell should close these when
    ;; it finishes with whatever it's doing.
    ;; This
    ;; should continue to show whatever output it
    ;; portrays through them until it's actually
    ;; done.
  (if-let [out (:output living)]
    (println "Waiting on the shell to exit and close STDOUT")
    (println "Warning: Missing STDOUT"))
  (if-let [err (:std-err living)]
    (println "Waiting on the shell to exit and close STDERR")
    (println "Warning: Missing STDERR"))

    ;; TODO: Wait until the shell thread is done.
    ;; Or, maybe, read from the original channel
    ;; that its creation should have returned when
    ;; it kicked off its go block.
    ;; Q: What should actually happen here?
    
  (assoc living
    :input nil
    :output nil
    :std-err nil
    :shell nil))
