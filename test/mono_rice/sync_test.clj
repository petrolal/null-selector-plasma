(ns mono-rice.sync-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.sync :as sync-cmd]
            [mono-rice.core :as core]))

(deftest test-sync-module
  (let [manifest (core/load-manifest!)]
    (testing "check-git-status returns status map"
      (let [res (sync-cmd/check-git-status)]
        (is (map? res))
        (is (contains? res :clean?))))

    (testing "sync-pull! dry-run runs cleanly"
      (is (nil? (sync-cmd/sync-pull! manifest {:dry-run true}))))

    (testing "sync-push! dry-run runs cleanly"
      (is (nil? (sync-cmd/sync-push! manifest {:dry-run true}))))))
