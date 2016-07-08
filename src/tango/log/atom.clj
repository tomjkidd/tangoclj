(ns tango.log.atom)

(defn log
  "Create an atom-based Tango log
:position is the index of the current tail
:entries is the list of log entries"
  []
  (atom {:position nil
         :entries []}))

(defn- next-pos
  "Get the next position given the current log map"
  [log-map]
  (let [pos (:position log-map)]
    (if (nil? pos)
      0
      (inc pos))))

(defn- next-entries
  "Get the next entries to use given the current log map and a new entry"
  [log-map entry]
  (let [es (:entries log-map)]
    (conj es entry)))

(defn- append-swap
  "Handle updating a log atom for an append"
  [entry]
  (fn [log-map]
    (let [pos (next-pos log-map)
          e (assoc entry :position pos)
          es (next-entries log-map e)]
      (-> (assoc log-map :position pos)
          (assoc :entries es)))))

(defn append
  "Append an entry to the given log"
  [log entry]
  (swap! log (append-swap entry)))

(defn read
  "Use a position to retreive an entry from the given log"
  [log position]
  (let [l @log
        es (:entries l)
        e (get es position)]
    (if (nil? e)
      {:left (str "tango.log.atom/read:"
                  "ReadError:"
                  "The log could not find an entry at position " position)}
      {:right e})))

(defn tail
  "Get a reference to the position of the tail of the given log"
  [log]
  (:position @log))

(defn trim
  "Indicate that a position in the given log can be garbage collected"
  [log position]
  {:left (str "tango.log.atom/trim:"
              "TrimError:"
              "Trim is not implemented")})
