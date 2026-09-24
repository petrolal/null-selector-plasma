(ns mono-rice.watch-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.watch :as watch]
            [mono-rice.core :as core]))

(deftest test-drift-sentinel
  (let [manifest (core/load-manifest!)]
    (testing "inspect-all-targets returns a vector of results for each backup target"
      (let [results (watch/inspect-all-targets manifest)]
        (is (vector? results))
        (is (= (count results) (count (:backup-targets manifest))))
        (doseq [res results]
          (is (contains? res :path))
          (is (contains? #{:synced :modified :missing-live :untracked-in-repo} (:status res))))))

    (testing "run-watch-cmd! with once/dry-run finishes cleanly"
      (let [results (watch/run-watch-cmd! manifest {:once true :dry-run true})]
        (is (vector? results))))))
