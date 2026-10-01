(ns mono-rice.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.schema :as schema]))

(deftest test-valid-manifest
  (testing "Valid minimal manifest passes validation"
    (let [manifest {:rice/name "test-rice"
                    :system {:desktop :plasma-6 :compatibility [:arch :nixos]}
                    :theme {:color-scheme "Monochrome" :fonts {:font "JetBrainsMono Nerd Font"}}
                    :dependencies {:pacman ["base-devel"]
                                   :nix ["babashka" "fastfetch"]}}]
      (is (empty? (schema/validate-manifest manifest)))
      (is (= manifest (schema/validate! manifest))))))

(deftest test-invalid-manifest
  (testing "Missing required keys returns informative errors"
    (let [invalid {:rice/name "incomplete"}]
      (is (seq (schema/validate-manifest invalid)))
      (is (thrown? Exception (schema/validate! invalid))))))
