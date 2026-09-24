(ns mono-rice.theme-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.core :as core]
            [mono-rice.theme :as theme]))

(deftest test-theme-profiles
  (let [manifest (core/load-manifest!)]
    (testing "Profiles exist in manifest"
      (let [profiles (theme/list-profiles manifest)]
        (is (map? profiles))
        (is (contains? profiles :monochrome-dark))
        (is (contains? profiles :monochrome-light))
        (is (contains? profiles :amber-crt))
        (is (contains? profiles :cyberpunk-red))))

    (testing "Get profile resolves by keyword and string"
      (let [dark-kw (theme/get-profile manifest :monochrome-dark)
            dark-str (theme/get-profile manifest "monochrome-dark")
            light-str (theme/get-profile manifest "monochrome-light")]
        (is (some? dark-kw))
        (is (= (:color-scheme dark-kw) "Monochrome"))
        (is (= dark-kw dark-str))
        (is (= (:color-scheme light-str) "BreezeLight"))))

    (testing "Switch theme in dry-run mode succeeds"
      (is (true? (theme/switch-theme! manifest :monochrome-dark {:dry-run true})))
      (is (false? (theme/switch-theme! manifest :non-existent-theme {:dry-run true}))))))
