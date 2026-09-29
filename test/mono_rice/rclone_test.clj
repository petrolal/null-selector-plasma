(ns mono-rice.rclone-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.rclone :as rclone]
            [mono-rice.core :as core]))

(deftest test-rclone-unit-generator
  (let [manifest (core/load-manifest!)
        cfg      (rclone/get-rclone-cfg manifest)]
    (testing "service-unit-content generates valid systemd bisync unit"
      (let [unit (rclone/service-unit-content cfg)]
        (is (str/includes? unit "[Unit]"))
        (is (str/includes? unit "Description=Rclone Google Drive Bisync"))
        (is (str/includes? unit "bisync %h/GoogleDrive gdrive:"))
        (is (str/includes? unit "--resilient"))
        (is (str/includes? unit "--max-delete 15"))
        (is (str/includes? unit "--conflict-resolve newer"))))

    (testing "timer-unit-content generates valid systemd timer unit"
      (let [unit (rclone/timer-unit-content cfg)]
        (is (str/includes? unit "[Unit]"))
        (is (str/includes? unit "[Timer]"))
        (is (str/includes? unit "OnCalendar=*:0/5"))
        (is (str/includes? unit "Persistent=true"))
        (is (str/includes? unit "WantedBy=timers.target"))))

    (testing "deploy-service-and-timer! dry-run runs cleanly"
      (is (true? (rclone/deploy-service-and-timer! manifest {:dry-run true}))))

    (testing "setup-gdrive-remote! dry-run runs cleanly"
      (is (true? (rclone/setup-gdrive-remote! manifest {:dry-run true}))))

    (testing "sync-now! dry-run runs cleanly"
      (is (nil? (rclone/sync-now! manifest {:dry-run true}))))

    (testing "status returns diagnostic map"
      (let [res (rclone/status manifest {:dry-run true})]
        (is (map? res))
        (is (contains? res :installed?))
        (is (contains? res :configured?))
        (is (contains? res :timer-active?))))))
