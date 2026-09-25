(ns mono-rice.kwin-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.kwin :as kwin]
            [mono-rice.core :as core]))

(deftest test-kwin-module
  (let [manifest (core/load-manifest!)]
    (testing "default-window-rules has expected structure"
      (is (vector? kwin/default-window-rules))
      (is (some #(= (:match-class %) "zen") kwin/default-window-rules)))

    (testing "apply-window-rules! dry-run runs cleanly"
      (is (nil? (kwin/apply-window-rules! manifest {:dry-run true}))))

    (testing "set-blur-strength! dry-run runs cleanly"
      (is (nil? (kwin/set-blur-strength! 5 {:dry-run true}))))))
