(ns mono-rice.boot-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.boot :as boot]))

(deftest boot-status-and-list-test
  (testing "Boot status check returns map"
    (let [res (boot/status {:dry-run true})]
      (is (map? res))
      (is (contains? res :sddm-active))
      (is (contains? res :plymouth-active))))

  (testing "Boot list themes"
    (is (true? (boot/list-themes {:dry-run true})))))

(deftest boot-preview-and-apply-test
  (testing "SDDM preview dry-run"
    (is (true? (boot/preview-sddm {:dry-run true}))))

  (testing "SDDM apply dry-run"
    (is (true? (boot/apply-sddm {:dry-run true}))))

  (testing "Plymouth apply dry-run for dot-lock"
    (is (true? (boot/apply-plymouth :dot-lock {:dry-run true}))))

  (testing "Plymouth apply dry-run for dot-lock-g"
    (is (true? (boot/apply-plymouth :dot-lock-g {:dry-run true}))))

  (testing "Plymouth apply invalid theme key"
    (is (false? (boot/apply-plymouth :invalid-theme {:dry-run true})))))
