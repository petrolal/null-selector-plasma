(ns mono-rice.profile-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.profile :as profile]))

(deftest test-profile-module
  (testing "detect-hardware-profile returns valid hardware map"
    (let [hw (profile/detect-hardware-profile)]
      (is (map? hw))
      (is (contains? hw :form-factor))
      (is (contains? hw :cpu))
      (is (contains? hw :gpu))))

  (testing "apply-profile! dry-run runs cleanly"
    (is (true? (profile/apply-profile! :desktop {:dry-run true})))
    (is (true? (profile/apply-profile! :laptop {:dry-run true})))))
