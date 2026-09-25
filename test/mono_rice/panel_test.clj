(ns mono-rice.panel-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.panel :as panel]
            [mono-rice.core :as core]))

(deftest test-panel-module
  (let [manifest (core/load-manifest!)]
    (testing "list-available-presets returns declared presets"
      (let [res (panel/list-available-presets manifest)]
        (is (map? res))
        (is (contains? res :declared))
        (is (contains? (:declared res) :main-setup))))

    (testing "reload-panels! dry-run runs cleanly"
      (is (nil? (panel/reload-panels! {:dry-run true}))))

    (testing "apply-panel-preset! dry-run on valid preset succeeds"
      (is (true? (panel/apply-panel-preset! "Main Setup" {:dry-run true})))
      (is (false? (panel/apply-panel-preset! "NonExistentPresetXYZ" {:dry-run true}))))))
