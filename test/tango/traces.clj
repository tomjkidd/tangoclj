(ns tango.traces
  (:require  [clojure.test :as t]
             [clojure.data.csv :as csv]
             [clojure.java.io :as io]))

(require '[clojure.data.csv :as csv]
                     '[clojure.java.io :as io])

(defn get-trace-filenames
  "Look in resources/traces for all available .csv files"
  []
  (let [base "./resources/traces"
        files (-> base
                  io/file
                  .listFiles)]
    (map #(str base "/" (.getName %)) files)))

(defn load-csv-file
  [filepath]
  (let [raw-data (with-open [in-file (io/reader filepath)]
                   (doall (csv/read-csv in-file)))
        clean-data (filter #(= 4 (count %)) raw-data)
        implied-writes '(["Write" "" "1" "0"]
                         ["Write" "" "2" "0"])]
    {:file filepath
     :data (concat implied-writes clean-data)}))

(defn clean-up-csv-data
  "Make sure that the data is clean"
  []
  nil)

(defn csv->trace
  "All files use Op, runtime, register, value. Parse that way"
  [csv]
  (let [[raw-action runtime register raw-value] csv
        oid (case register
              "" nil
              register)
        runtime-id (case runtime
                     "" "main"
                     (str "tx-" runtime))
        register-id (case register
                      "" nil
                      (str "reg" register))
        action (keyword (clojure.string/lower-case raw-action))
        value (case action
                 :read (. Integer parseInt raw-value)
                 :write (. Integer parseInt raw-value)
                 :commit (. Boolean parseBoolean raw-value))]
    [oid runtime-id register-id action value]))

(defn get-traces
  "Get a map of name keys to list of csv fields"
  []
  (let [filenames (get-trace-filenames)
        csvs (map load-csv-file filenames)
        traces (map (fn [{:keys [file data]}]
                      {:file file
                       :data (map csv->trace data)})
                    csvs)]
    traces))
