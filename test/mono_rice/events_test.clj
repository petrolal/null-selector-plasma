(ns mono-rice.events-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.events :as ev]))

(deftest events-handler-test
  (testing "Screen change event handling"
    (let [res (ev/handle-event "member=configChanged org.kde.KScreen" {:dry-run true})]
      (is (= :screen-change res))))

  (testing "Screen lock event handling"
    (let [res (ev/handle-event "org.freedesktop.ScreenSaver boolean true" {:dry-run true})]
      (is (= :screen-locked res))))

  (testing "Screen unlock event handling"
    (let [res (ev/handle-event "org.freedesktop.ScreenSaver boolean false" {:dry-run true})]
      (is (= :screen-unlocked res))))

  (testing "Power transition event handling"
    (let [res (ev/handle-event "org.freedesktop.UPower changed" {:dry-run true})]
      (is (= :power-transition res))))

  (testing "Unhandled event"
    (let [res (ev/handle-event "random.event.text" {:dry-run true})]
      (is (= :unhandled res)))))

(deftest events-emit-and-listen-test
  (testing "Event emit dry-run"
    (is (true? (ev/emit "org.kde.KScreen" {:dry-run true}))))

  (testing "Event listen dry-run"
    (is (true? (ev/listen {:dry-run true})))))
