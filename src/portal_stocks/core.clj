(ns portal-stocks.core
  (:require [camel-snake-kebab.core :as csk :refer [->kebab-case]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.core.async :as async :refer [<!! >!! go]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [org.bytedeco.javacpp opencv_core$Mat opencv_imgproc]
           [org.bytedeco.javacv
            FFmpegFrameGrabber
            FFmpegFrameRecorder
            Frame
            OpenCVFrameConverter$ToMat]))

(let [converter (OpenCVFrameConverter$ToMat.)]
  (defn frame->mat ^opencv_core$Mat [frame]
    (.convert converter frame))
  (defn mat->frame ^Frame [^opencv_core$Mat mat]
    (.convert converter mat)))

(defn grabber->map [^FFmpegFrameGrabber grabber]
  (let [m (-> grabber bean (assoc :handle grabber))]
    (-> (transform-keys ->kebab-case m)
        (update :image-mode #(-> % .name csk/->kebab-case-keyword))
        (update :format     #(->> (str/split % #",") (map keyword) (into #{}))))))

(defn grabber->recorder
  [out-file format {:keys [image-width image-height audio-channels] :as g}]
  (doto (FFmpegFrameRecorder.
         out-file image-width image-height audio-channels)
    (.setAudioCodec   (:audio-codec   g))
    (.setAudioBitrate (:audio-bitrate g))
    (.setVideoBitrate (:video-bitrate g))
    (.setVideoCodec   (:video-codec   g))
    (.setFrameRate    (:frame-rate    g))
    (.setSampleRate   (:sample-rate   g))
    (.setSampleFormat (:sample-format g))
    (.setFormat       (name format))))

(defn- do-frames!
  "Calls f from frame processing thread, discarding its result"
  [f {^FFmpegFrameGrabber grabber :handle}]
  (async/thread
    (loop []
      (when-let [frame (.grabFrame grabber)]
        (f {:grabber grabber
            :frame frame
            :type (cond (.. frame samples) :audio
                        (.. frame image)   :image)
            :timestamp (.getTimestamp grabber)})
        (recur)))))

(defmulti handle-frame (fn [_ {:keys [type]}] type))

(defmethod handle-frame :audio [recorder {:keys [^Frame frame timestamp]}]
  (.record recorder frame))

;; (defn pixels [mat]
;;   (let [buffer (.getByteBuffer mat)]
;;     (dotimes [y (.height image)]
;;       (dotimes [x (.width image)]
;;         (let [index (+ (* y (.step mat)) (* x (.channels mat)))]
;;           [(bit-and (.get buffer index) 0xFF)
;;            (bit-and (.get buffer (inc index)) 0xFF)
;;            (bit-and (.get buffer (inc (inc index))) 0xFF)])))))

(defmethod handle-frame :image [recorder {:keys [^Frame frame timestamp]}]
  (let [mat     (frame->mat frame)]
    (if (< (.getTimestamp recorder) timestamp)
      (.setTimestamp recorder timestamp))
    (.record recorder (mat->frame mat))))

(defn -main []
  (let [in "resources/rects.mp4"
        grabber  (grabber->map (doto (FFmpegFrameGrabber. in) (.start)))
        recorder (grabber->recorder "resources/lol.mp4" :mp4 grabber)]
    (.start recorder)
    (go
      (async/<! (do-frames! (partial handle-frame recorder) grabber))
      (.stop recorder))))
