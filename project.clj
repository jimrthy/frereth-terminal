(defproject frereth-terminal "0.1.0-SNAPSHOT"
  :description "UI's more important than eye candy"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [jimrthy/penumbra "0.6.6-SNAPSHOT"]]
  :main frereth-terminal.core

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.classpath "0.2.1"]]}}
  :repl-options {:init-ns user})
