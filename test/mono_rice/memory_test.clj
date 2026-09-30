(ns mono-rice.memory-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.memory :as memory]
            [mono-rice.core :as core]))

(deftest test-memory-module
  (let [manifest (core/load-manifest!)]
    (testing "get-memory-cfg defaults"
      (let [cfg (memory/get-memory-cfg manifest)]
        (is (map? cfg))
        (is (true? (get-in cfg [:zram :enabled])))
        (is (= "ram / 2" (get-in cfg [:zram :zram-size])))
        (is (true? (get-in cfg [:oomd :enabled])))
        (is (false? (get-in cfg [:baloo :indexing-file-content])))))

    (testing "Diagnostic status checks return valid format"
      (let [zram-res (memory/check-zram-status)
            oomd-res (memory/check-oomd-status)
            baloo-res (memory/check-baloo-status)]
        (is (contains? zram-res :check))
        (is (contains? #{:ok :warn} (:status zram-res)))
        (is (contains? oomd-res :check))
        (is (contains? #{:ok :warn} (:status oomd-res)))
        (is (contains? baloo-res :check))
        (is (contains? #{:ok :warn} (:status baloo-res)))))

    (testing "Dry-run memory configuration routines run without exceptions"
      (is (nil? (memory/configure-zram! manifest {:dry-run true})))
      (is (nil? (memory/configure-oomd! manifest {:dry-run true})))
      (is (nil? (memory/tune-baloo! manifest {:dry-run true})))
      (is (nil? (memory/apply-memory-tuning! manifest {:dry-run true}))))

    (testing "status command outputs diagnostics map"
      (let [res (memory/status {:dry-run true})]
        (is (map? res))
        (is (contains? res :ram-total))
        (is (contains? res :zram))
        (is (contains? res :oomd))
        (is (contains? res :baloo))))))
