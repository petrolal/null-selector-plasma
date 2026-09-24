(ns mono-rice.tui-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.tui :as tui]
            [mono-rice.core :as core]))

(deftest test-tui-render-dryrun
  (let [manifest (core/load-manifest!)]
    (testing "TUI dashboard renders successfully in dry-run mode"
      (is (true? (tui/run-tui! manifest {:dry-run true}))))))
