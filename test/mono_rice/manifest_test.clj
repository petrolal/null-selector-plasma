(ns mono-rice.manifest-test
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]))

(deftest test-rice-manifest-syntax
  (testing "rice.edn is valid and contains required top-level keys"
    (let [manifest-file (fs/path "rice.edn")]
      (is (fs/exists? manifest-file))
      (let [data (edn/read-string (slurp (str manifest-file)))]
        (is (map? data))
        (is (= (:rice/name data) "null-sector-plasma"))
        (is (map? (:theme data)))
        (is (map? (:terminal data)))
        (is (map? (:browser data)))
        (is (map? (:dependencies data)))
        (is (vector? (get-in data [:dependencies :pacman])))
        (is (vector? (get-in data [:dependencies :aur])))
        (is (vector? (get-in data [:dependencies :git])))
        (is (vector? (:backup-targets data)))))))
