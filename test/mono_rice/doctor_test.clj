(ns mono-rice.doctor-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.doctor :as doctor]
            [mono-rice.core :as core]))

(deftest test-doctor-module
  (let [manifest (core/load-manifest!)]
    (testing "run-diagnostics returns a list of health check records"
      (let [checks (doctor/run-diagnostics manifest)]
        (is (vector? checks))
        (is (pos? (count checks)))
        (doseq [c checks]
          (is (contains? c :check))
          (is (contains? #{:ok :warn} (:status c))))))

    (testing "auto-repair! dry-run runs cleanly"
      (is (nil? (doctor/auto-repair! manifest {:dry-run true}))))))
