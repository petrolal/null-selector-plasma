(ns mono-rice.sanitizer-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mono-rice.layout.sanitizer :as san]))

(def sample-raw-appletsrc
  "[Containments][1]
activityId=11111111-2222-3333-4444-555555555555
plugin=org.kde.desktopcontainment
wallpaperplugin=luisbocanegra.smart.video.wallpaper.reborn

[Containments][1][Applets][10]
plugin=luisbocanegra.audio.visualizer
wallpaper=/home/petrolal/wallpapers/test.png

[Containments][2]
activityId=11111111-2222-3333-4444-555555555555
plugin=org.kde.panel
location=3

[Containments][2][Applets][20]
plugin=luisbocanegra.panel.colorizer

[Containments][3]
activityId=11111111-2222-3333-4444-555555555555
plugin=org.kde.panel
location=1

[ScreenMapping]
screenMapping=/home/petrolal/test
itemsOnDisabledScreens=something
")

(def sample-plasmashellrc
  "[PlasmaViews][Panel 2]
panelLengthMode=0

[PlasmaViews][Panel 3]
panelLengthMode=0

[PlasmaViews][Panel 99]
panelLengthMode=0

[Updates]
performedUpdates=foo
")

(deftest test-panel-containment-ids
  (testing "Detects panels correctly"
    (let [panel-ids (san/panel-containment-ids sample-raw-appletsrc)]
      (is (= #{"2" "3"} panel-ids)))))

(deftest test-sanitize-and-hydrate
  (testing "Sanitize replaces home and activity IDs"
    (let [sanitized (san/sanitize-harvest sample-raw-appletsrc {:home "/home/petrolal"})]
      (is (not (str/includes? sanitized "/home/petrolal")))
      (is (not (str/includes? sanitized "11111111-2222-3333-4444-555555555555")))
      (is (str/includes? sanitized "{{HOME}}"))
      (is (str/includes? sanitized "activityId={{ACTIVITY_ID}}"))
      (is (not (str/includes? sanitized "screenMapping=")))
      (is (not (str/includes? sanitized "itemsOnDisabledScreens=")))

      (testing "Hydrate restores home and activity ID"
        (let [hydrated (san/hydrate-install sanitized {:home "/home/alice"
                                                      :activity-id "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"})]
          (is (str/includes? hydrated "/home/alice"))
          (is (str/includes? hydrated "activityId=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
          (is (not (str/includes? hydrated "{{HOME}}")))
          (is (not (str/includes? hydrated "{{ACTIVITY_ID}}"))))))))

(deftest test-prune-stale-panels
  (testing "Prunes panels not in live set and updates sections"
    (let [res (san/prune-stale-panels sample-plasmashellrc #{"2" "3"})]
      (is (= 1 (:pruned res)))
      (is (str/includes? (:text res) "[PlasmaViews][Panel 2]"))
      (is (str/includes? (:text res) "[PlasmaViews][Panel 3]"))
      (is (not (str/includes? (:text res) "[PlasmaViews][Panel 99]")))
      (is (not (str/includes? (:text res) "[Updates]"))))))

(deftest test-verify-widgets
  (testing "Verifies present and missing plugins"
    (let [res (san/verify-widgets sample-raw-appletsrc)]
      (is (false? (:valid? res)))
      (is (some #(= (:plugin %) "luisbocanegra.audio.visualizer") (:results res)))
      (is (some #(= (:plugin %) "luisbocanegra.panel.colorizer") (:results res)))
      (is (some #(= (:plugin %) "org.kde.plasma.catwalkEnhanced") (:missing res))))))
