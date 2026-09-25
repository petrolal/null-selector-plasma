(ns mono-rice.vault-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [mono-rice.cmd.vault :as vault]))

(deftest vault-scan-and-sanitize-test
  (testing "Vault scan clean dotfiles"
    (let [res (vault/scan {:dry-run true})]
      (is (map? res))
      (is (contains? res :clean))
      (is (vector? (vec (:findings res))))))

  (testing "Vault sanitize on clean repo"
    (is (true? (vault/sanitize {:dry-run true}))))

  (testing "Vault restore dry-run"
    (is (true? (vault/restore {:dry-run true}))))

  (testing "Vault rule scanning on temporary file with fake token"
    (let [tmp (io/file "/tmp/test-secret-sample.txt")]
      (try
        (spit tmp "export GITHUB_TOKEN=\"ghp_fakeToken1234567890abcdef1234567890abc\"\nexport API_KEY=\"secret_key=1234567890abcdef12345678\"\n")
        (let [findings (vault/scan-file tmp vault/default-rules)]
          (is (= 2 (count findings)))
          (is (= "GitHub Token" (:rule (first findings)))))
        (finally
          (when (.exists tmp) (.delete tmp)))))))
