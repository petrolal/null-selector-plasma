(ns mono-rice.rollback-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.fs :as rfs]))

(deftest test-backup-listing-and-restore-dryrun
  (testing "list-backups returns a vector"
    (let [backups (rfs/list-backups)]
      (is (vector? backups))))

  (testing "restore-backup! handles non-existent snapshot gracefully"
    (is (false? (rfs/restore-backup! "/tmp/non_existent_snapshot_mono_test" {:dry-run true})))))
