(ns mono-rice.cmd.backup
  (:require [mono-rice.fs :as rfs]))

(defn backup! [manifest opts]
  (let [targets (:backup-targets manifest)]
    (rfs/backup-configs! targets opts)))
