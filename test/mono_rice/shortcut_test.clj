(ns mono-rice.shortcut-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.shortcut :as shortcut]
            [mono-rice.core :as core]))

(deftest test-shortcut-module
  (let [manifest (core/load-manifest!)]
    (testing "list-shortcuts returns shortcut map"
      (let [scs (shortcut/list-shortcuts manifest)]
        (is (map? scs))
        (is (contains? scs :terminal))))

    (testing "apply-shortcuts! dry-run runs cleanly"
      (is (nil? (shortcut/apply-shortcuts! manifest {:dry-run true}))))

    (testing "export-shortcuts dry-run returns output path"
      (is (= (shortcut/export-shortcuts manifest "/tmp/test-shortcuts.json" {:dry-run true})
             "/tmp/test-shortcuts.json")))))
