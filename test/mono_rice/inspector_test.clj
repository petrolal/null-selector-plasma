(ns mono-rice.inspector-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.layout.inspector :as insp]))

(deftest test-parse-ini-sections
  (testing "Parses INI sections and key-values"
    (let [sample "[SectionA]\nkey1=val1\nkey2=val2\n\n[SectionB]\nfoo=bar\n"
          parsed (insp/parse-ini-sections sample)]
      (is (= {"SectionA" {"key1" "val1" "key2" "val2"}
              "SectionB" {"foo" "bar"}}
             parsed)))))
