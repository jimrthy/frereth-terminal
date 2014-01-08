(ns frereth-terminal.system
  (:require [penumbra.app :as app]
            [penumbra.text :as text])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn init
  "Empty system to build upon later"
  []
  {:text ""
   ;; TODO: Start with current system time?
   :time 0})

(defn close [state]
  (println "Closing")
  (println state))

(defn update [[delta time] state]
  ;; The important thing should be that this updates the state so
  ;; that...what can draw it?
  (println "Updating:")
  (println "Delta: " delta "\nTime: " time "\nState: " state)
  (assoc state :time time))

(defn display
  "I'm pretty sure this is supposed to actually draw the state that
update set up."
  [[dt t] state]
  (text/write-to-screen (:text state) 0 0)
  (app/repaint!))

(defn restore-last-session
  "For now, at least, set up a Penumbra window.
Hopefully this will get more robust in the future.
Session restoration gets complicated."
  [dead]
  (into dead
        {:width 1024
         :height 768
         :title "Frerminal"
         :text ""}))

(defn configure-window
  [state]
  (app/title! "frerepl")
  (app/vsync! true))

(defn key-press [key state]
  (cond
   (= :escape key) (app/stop!)
   :else (assoc state :text (str (:text state) key))))

(defn start
  "Run all the side-effects associated with starting a system"
  [dead]
  (let [prev (restore-last-session dead)]
    (app/start
     {:init configure-window
      :update update
      :display display
      :key-press key-press
      :close close}
     prev)
    prev))

(defn stop
  "Run all the side-effects to kill a system off"
  [living]
  living)
