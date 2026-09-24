(ns mono-rice.daemon-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.daemon :as daemon]))

(deftest test-daemon-unit-generator
  (testing "service-unit-content generates valid systemd unit"
    (let [unit (daemon/service-unit-content "/usr/bin/mono-rice")]
      (is (str/includes? unit "[Unit]"))
      (is (str/includes? unit "ExecStart=/usr/bin/mono-rice daemon start"))
      (is (str/includes? unit "[Install]"))))

  (testing "install and uninstall service in dry-run mode succeed"
    (is (nil? (daemon/install-systemd-service! {:dry-run true})))
    (is (nil? (daemon/uninstall-systemd-service! {:dry-run true})))))
