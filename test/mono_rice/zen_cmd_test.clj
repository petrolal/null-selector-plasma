(ns mono-rice.zen-cmd-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.zen :as zen-cmd]
            [mono-rice.core :as core]))

(deftest test-zen-cmd-module
  (let [manifest (core/load-manifest!)]
    (testing "zen-status returns a valid metadata map"
      (let [status (zen-cmd/zen-status manifest)]
        (is (map? status))
        (is (contains? status :profiles))
        (is (contains? status :extensions))
        (is (contains? status :mods))))))
