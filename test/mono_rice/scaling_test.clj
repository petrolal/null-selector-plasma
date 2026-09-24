(ns mono-rice.scaling-test
  (:require [clojure.test :refer [deftest is testing]]
            [mono-rice.layout.scaling :as scaling]))

(deftest test-scaling-logic
  (testing "Resolution parsing"
    (is (= (scaling/parse-resolution-string "1920x1080") {:width 1920 :height 1080}))
    (is (= (scaling/parse-resolution-string "2560x1440") {:width 2560 :height 1440}))
    (is (= (scaling/parse-resolution-string "3840x2160") {:width 3840 :height 2160}))
    (is (nil? (scaling/parse-resolution-string "invalid"))))

  (testing "Scale factor calculation"
    (is (= (scaling/calculate-scale-factor {:height 1080}) 1.0))
    (is (= (scaling/calculate-scale-factor {:height 1440}) 1.33))
    (is (= (scaling/calculate-scale-factor {:height 2160}) 2.0)))

  (testing "Appletsrc dimensions adaptation"
    (let [input "thickness=44\npanelLength=1920\nheight=32"
          scaled (scaling/adapt-appletsrc-dimensions input 1.5)]
      (is (= scaled "thickness=66\npanelLength=2880\nheight=48")))

    (let [input "thickness=44\nheight=32"
          unchanged (scaling/adapt-appletsrc-dimensions input 1.0)]
      (is (= unchanged input)))))
