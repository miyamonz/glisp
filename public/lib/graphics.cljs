(defn artboard [id bounds & body]
  [(keyword (str "artboard#" id)) bounds
   (let
    [$width (rect/width bounds)
     $height (rect/height bounds)
     $size (rect/size bounds)
     background (fn (c) (fill c (rect [0 0] $size)))
     frame-guide (guide (rect [.5 .5] (vec2/- $size [1 1])))]
     (translate (rect/point bounds)
                (g [frame-guide] body)))])

(defn find-item [sel body]
  (first
   (find-list  #(= (first %) sel) body)))

(defn guide/stroke [& xs]
  (if (zero? (count xs))
    (stroke $guide-color)
    (stroke $guide-color (first xs))))

;; Color
(defn color? [x]
  (string? x))

(defn color [& xs]
  (case (count xs)
    1 (let [v (first xs)] (if (number? v) (format "rgba(%f,%f,%f)" v v v) v))
    3 (apply format "rgba(%f,%f,%f)" xs)
    "black"))

(defmacro background
  [color]
  `(do
     (def $background ~color)
     (vector :background ~color)))

(def background
  (with-meta background
    {:doc "Fill the entire view or artboard with a color"
     :params [{:type "color" :desc "A background color"}]}))

(defn enable-animation
  [& xs] (concat :enable-animation xs))

(defn element? [a] (and (sequential? a) (keyword? (first a))))

(defn column
  {:doc "Returns a vector of nums from *start* to *end* (both inclusive) that each of element is multiplied by *step*"
   :params [{:type "number" :desc "From"}
            {:type "number" :desc "To"}
            {:type "number" :desc "Step"}]}
  [from to step]
  (map #(* % step) (range from (inc to))))


;; Group
(defn g [& xs]
  (let [children (apply concat (map #(if (element? %) [%] %) xs))]
    (if (= 1 (count children)) (first children)
        (apply vector :g children))))

;; Transform
(defmacro
  view-center []
  `(translate (vec2/scale $size .5)))
(def view-center
  (with-meta view-center
    {:doc "Returns a translation matrix to move the origin onto the center of view or artboard"}))

;; Style
(defn fill
  {:doc "Creates a fill property"
   :params [{:label "Color" :type "color" :desc "Color to fill"}]}
  [color]
  {:fill true :fill-color color})

(defn stroke
  {:doc "Creates a stroke property"
   :params [{:label "Color" :type "color"}
            {:label "Width" :default 1 :type "number" :constraints {:min 0}}
            &
            {:keys [{:key :cap :type "string" :default "round"}
                    {:key :join :type "string" :default "round"}]}]}
  [color & args]
  (let [params (case (count args)
                 0 {}
                 1 {:width (first args)}
                 (apply hash-map (concat :width args)))]
    (->> params
         (seq params)
         (map (fn [[k v]] [(keyword (str "stroke-" (name k))) v]))
         (apply concat [:stroke true :stroke-color color])
         (apply hash-map))))

; (defn linear-gradient
;   {:doc "Define a linear gradient style to apply to fill or stroke"}
;   [x1 y1 x2 y2 & xs]
;   (let (args (apply hash-map xs))
;     (if (not (contains? args :stops))
;       (throw "[linear-gradient] odd number of arguments")
;       (vector :linear-gradient
;               (hash-map :points (vector x1 y1 x2 y2)
;                         :stops (get args :stops))))))

;; Shape Functions
(defn text
  {:doc "Generate a text shape"
   :params [{:type "string" :desc "the alphanumeric symbols to be displayed"}
            {:label "Pos" :type "vec2"}
            &
            {:keys [{:key :size :type "number" :default 12}
                    {:key :font :type "string" :default "Fira Code"}
                    {:key :align :type "string" :default "center"}
                    {:key :baseline :type "string" :default "middle"}]}]}
  [text [x y] & xs]
  [:text text [x y] (apply hash-map xs)])
