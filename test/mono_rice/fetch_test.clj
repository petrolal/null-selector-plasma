(ns mono-rice.fetch-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.fetch :as fetch]))

(deftest test-fetch-module
  (testing "ascii-presets contains expected art logos"
    (is (contains? fetch/ascii-presets :nier-automata))
    (is (contains? fetch/ascii-presets :null-sector))
    (is (contains? fetch/ascii-presets :cyberpunk-skull)))

  (testing "apply-fetch-preset! dry-run succeeds"
    (is (true? (fetch/apply-fetch-preset! :null-sector {:dry-run true})))
    (is (false? (fetch/apply-fetch-preset! :nonexistent-logo-key {:dry-run true})))))
