(ns mono-rice.bundle-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.bundle :as bundle]
            [mono-rice.core :as core]))

(deftest test-bundle-export-import-dryrun
  (let [manifest (core/load-manifest!)]
    (testing "bundle export dry-run returns destination path"
      (let [res (bundle/export-bundle! manifest "/tmp/test-bundle.tar.gz" {:dry-run true})]
        (is (= res "/tmp/test-bundle.tar.gz"))))

    (testing "bundle import missing archive handled safely"
      (is (nil? (bundle/import-bundle! "/tmp/nonexistent-bundle-archive.tar.gz" "/tmp" {:dry-run true}))))))
