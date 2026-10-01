(ns mono-rice.fetch-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.fetch :as fetch]))

(deftest test-fetch-module
  (testing "ascii-presets contains expected art logos"
    (is (contains? fetch/ascii-presets :nier-automata))
    (is (contains? fetch/ascii-presets :null-sector))
    (is (contains? fetch/ascii-presets :cyberpunk-skull))
    (is (contains? fetch/ascii-presets :sanguine))
    (is (contains? fetch/ascii-presets :biopunk-horror))
    (is (contains? fetch/ascii-presets :arch))
    (is (contains? fetch/ascii-presets :cachyos))
    (is (contains? fetch/ascii-presets :nixos))
    (is (contains? fetch/ascii-presets :fedora)))

  (testing "apply-fetch-preset! dry-run succeeds"
    (is (true? (fetch/apply-fetch-preset! :null-sector {:dry-run true})))
    (is (true? (fetch/apply-fetch-preset! :sanguine {:dry-run true})))
    (is (true? (fetch/apply-fetch-preset! :biopunk-horror {:dry-run true})))
    (is (true? (fetch/apply-fetch-preset! :arch {:dry-run true})))
    (is (true? (fetch/apply-fetch-preset! :cachyos {:dry-run true})))
    (is (true? (fetch/apply-fetch-preset! :nixos {:dry-run true})))
    (is (true? (fetch/apply-fetch-preset! :fedora {:dry-run true})))
    (is (false? (fetch/apply-fetch-preset! :nonexistent-logo-key {:dry-run true})))))
