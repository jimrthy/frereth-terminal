(defproject frereth-terminal "0.1.0-SNAPSHOT"
  :description "UI's more important than eye candy"
  :url "http://www.frereth.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[com.frereth/penumbra "0.6.7-SNAPSHOT"]
                 [im.chit/ribol "0.4.0"]
                 [org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [prismatic/schema "0.4.2"]]
  :jvm-opts [~(str "-Djava.library.path=/usr/local/lib:"
                   (System/getProperty "java.library.path"))]
  :main frereth-terminal.core
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[com.stuartsierra/component "0.2.3"]
                                  [org.clojure/tools.namespace "0.2.10"]
                                  [org.clojure/java.classpath "0.2.2"]]}}
  :repl-options {:init-ns user})
