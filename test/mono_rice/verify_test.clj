(ns mono-rice.verify-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.verify :as verify]
            [mono-rice.core :as core]
            [mono-rice.kde :as kde]))

(deftest test-verify-module
  (let [manifest (core/load-manifest!)]
    (testing "check-system! runs without throwing when prompt? is false"
      (is (nil? (kde/check-system! {:prompt? false})))
      (is (nil? (kde/check-system! {}))))

    (testing "check-system! with auto-yes / yes runs without throwing"
      (is (nil? (kde/check-system! {:prompt? true :auto-yes true})))
      (is (nil? (kde/check-system! {:prompt? true :yes true})))
      (is (nil? (kde/check-system! {:prompt? true :dry-run true}))))

    (testing "verify! runs cleanly without aborting"
      (is (nil? (verify/verify! manifest {:dry-run true}))))))
