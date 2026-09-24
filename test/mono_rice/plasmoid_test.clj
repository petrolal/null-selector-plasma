(ns mono-rice.plasmoid-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.plasmoid :as plasmoid]))

(deftest test-plasmoid-module
  (testing "list-installed-plasmoids returns a vector"
    (is (vector? (plasmoid/list-installed-plasmoids))))

  (testing "install-plasmoid! dry-run runs cleanly"
    (is (nil? (plasmoid/install-plasmoid! "org.kde.test.applet" {:dry-run true}))))

  (testing "remove-plasmoid! dry-run runs cleanly"
    (is (nil? (plasmoid/remove-plasmoid! "org.kde.test.applet" {:dry-run true})))))
