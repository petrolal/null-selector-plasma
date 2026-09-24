(ns mono-rice.wallpaper-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.wallpaper :as wallpaper]))

(deftest test-wallpaper-module
  (testing "list-wallpapers discovers static and video wallpapers"
    (let [wallpapers (wallpaper/list-wallpapers)]
      (is (vector? wallpapers))
      (is (seq (filter #(clojure.string/ends-with? % ".mp4") wallpapers)))
      (is (seq (filter #(clojure.string/ends-with? % ".png") wallpapers)))))

  (testing "apply-wallpaper! dry-run succeeds for valid wallpaper"
    (is (true? (wallpaper/apply-wallpaper! "digital-gaze" {:dry-run true})))
    (is (false? (wallpaper/apply-wallpaper! "nonexistent-wallpaper-test" {:dry-run true})))))
