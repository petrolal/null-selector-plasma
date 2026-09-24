(ns mono-rice.audio-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mono-rice.cmd.audio :as audio]))

(deftest test-audio-presets
  (testing "audio-presets map contains core configurations"
    (is (contains? audio/audio-presets :dense-cyberpunk))
    (is (contains? audio/audio-presets :minimal-monochrome))
    (is (contains? audio/audio-presets :high-fps-reactive)))

  (testing "render-cava-config generates valid INI"
    (let [rendered (audio/render-cava-config (get audio/audio-presets :dense-cyberpunk))]
      (is (str/includes? rendered "[general]"))
      (is (str/includes? rendered "bars = 64"))
      (is (str/includes? rendered "[output]"))))

  (testing "apply-audio-preset! dry-run succeeds"
    (is (true? (audio/apply-audio-preset! :dense-cyberpunk {:dry-run true})))
    (is (false? (audio/apply-audio-preset! :invalid-preset-key {:dry-run true})))))
