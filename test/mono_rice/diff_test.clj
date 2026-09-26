(ns mono-rice.diff-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.diff :as diff]
            [mono-rice.fs :as rfs]))

(deftest test-find-repo-template
  (let [root (rfs/repo-root)]
    (testing "find-repo-template discovers core configs in repo"
      (is (some? (diff/find-repo-template root ".config/kdeglobals")))
      (is (some? (diff/find-repo-template root ".config/fish/config.fish")))
      (is (nil? (diff/find-repo-template root ".nonexistent_config_file"))))))
