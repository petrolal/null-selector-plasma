(ns mono-rice.completion-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [mono-rice.cmd.completion :as comp]))

(deftest completion-generators-test
  (testing "Zsh completion generation"
    (let [zsh (comp/generate-zsh-completion)]
      (is (string? zsh))
      (is (str/includes? zsh "#compdef mono-rice"))
      (is (str/includes? zsh "theme)"))
      (is (str/includes? zsh "boot)"))))

  (testing "Bash completion generation"
    (let [bash (comp/generate-bash-completion)]
      (is (string? bash))
      (is (str/includes? bash "_mono_rice_completions()"))
      (is (str/includes? bash "complete -F _mono_rice_completions"))))

  (testing "Fish completion generation"
    (let [fish (comp/generate-fish-completion)]
      (is (string? fish))
      (is (str/includes? fish "complete -c mono-rice"))))

  (testing "Completion install dry-run"
    (is (true? (comp/generate "zsh" {:dry-run true :install true})))
    (is (true? (comp/generate "bash" {:dry-run true :install true})))
    (is (true? (comp/generate "fish" {:dry-run true :install true})))
    (is (false? (comp/generate "powershell" {:dry-run true :install true})))))
