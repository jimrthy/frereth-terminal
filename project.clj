(defproject frereth-terminal "0.1.0-SNAPSHOT"
  :description "UI's more important than eye candy"
  :url "http://www.frereth.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.frereth/penumbra "0.6.7-SNAPSHOT"]
                 [org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :main frereth-terminal.core

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[com.stuartsierra/component "0.2.2"]
                                  [org.clojure/tools.namespace "0.2.9"]
                                  [org.clojure/java.classpath "0.2.2"]
                                  [prismatic/schema "0.3.7"]]}}
  :repl-options {:init-ns user})
