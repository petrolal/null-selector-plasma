(ns mono-rice.layout.scaling
  (:require [clojure.string :as str]
            [mono-rice.proc :refer [command-exists? sh!]]))

(def base-height 1080.0)

(defn parse-resolution-string
  "Parses '1920x1080' or '2560x1440' into {:width ... :height ...}."
  [res-str]
  (when-let [[_ w h] (and res-str (re-find #"(\d+)x(\d+)" (str res-str)))]
    {:width  (Integer/parseInt w)
     :height (Integer/parseInt h)}))

(defn detect-screen-resolution
  "Attempts to detect primary display resolution via kscreen-doctor or xrandr, defaulting to 1920x1080."
  []
  (or
   ;; 1. kscreen-doctor -o (Wayland / Plasma 6)
   (when (command-exists? "kscreen-doctor")
     (try
       (let [{:keys [exit out]} (sh! ["kscreen-doctor" "-o"] {:throw? false})]
         (when (and (zero? exit) (not (str/blank? out)))
           (when-let [m (re-find #"Geometry:\s*\d+,\d+\s+(\d+)x(\d+)" out)]
             {:width (Integer/parseInt (nth m 1))
              :height (Integer/parseInt (nth m 2))})))
       (catch Exception _ nil)))

   ;; 2. xrandr fallback
   (when (command-exists? "xrandr")
     (try
       (let [{:keys [exit out]} (sh! ["xrandr" "--current"] {:throw? false})]
         (when (and (zero? exit) (not (str/blank? out)))
           (when-let [m (re-find #"current\s+(\d+)\s+x\s+(\d+)" out)]
             {:width (Integer/parseInt (nth m 1))
              :height (Integer/parseInt (nth m 2))})))
       (catch Exception _ nil)))

   ;; 3. Default fallback 1080p
   {:width 1920 :height 1080}))

(defn calculate-scale-factor
  "Calculates proportional scale factor relative to 1080p base height."
  [{:keys [height] :or {height 1080}}]
  (let [factor (/ (double height) base-height)]
    (Double/parseDouble (format "%.2f" factor))))

(defn scale-dimension
  "Scales a pixel dimension by factor and rounds to nearest integer."
  [dim factor]
  (Math/round (double (* dim factor))))

(defn adapt-appletsrc-dimensions
  "Adjusts panel heights and thicknesses in appletsrc INI text according to scale factor."
  [content scale-factor]
  (if (== scale-factor 1.0)
    content
    (str/replace content
                 #"(thickness|height|panelLength|floatingOffset)=(\d+)"
                 (fn [[_ k v]]
                   (let [orig (Integer/parseInt v)
                         scaled (scale-dimension orig scale-factor)]
                     (str k "=" scaled))))))
