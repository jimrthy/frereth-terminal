(ns frereth-terminal.system
  (:gen-class))

(defn init
  "Empty system to build upon later"
  []
  {})

(defn start
  "Run all the side-effects associated with starting a system"
  [dead]
  dead)

(defn stop
  "Run all the side-effects to kill a system off"
  [living]
  living)
