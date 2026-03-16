(ns tasks.cli
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]))

(defn die
  "Print an error message to stderr and exit with status 1."
  [msg]
  (binding [*out* *err*]
    (println msg))
  (System/exit 1))

;; ── help parsing ─────────────────────────────────────────────────────────────

(defn- help-section
  "Parse help flag from args. Returns :all, \"requirements\", or nil.
   Other --help=<value> strings pass through but are treated as :all by show-doc."
  [args]
  (some (fn [arg]
          (cond
            (#{"--help" "-h"} arg) :all
            (str/starts-with? arg "--help=")
            (str/lower-case (subs arg 7))))
        args))

;; ── schema-derived options ───────────────────────────────────────────────────

(defn- type-display [schema]
  (case (m/type schema)
    :string "string"  :boolean "boolean"  :int "int"
    :re "string"
    (:+ :*) (str (type-display (first (m/children schema))) "...")
    (name (m/type schema))))

(defn- auto-options [{:keys [schema cli]}]
  (when (= :map (m/type schema))
    (let [positional (set (:args->opts cli))
          inv-alias (reduce-kv (fn [m k v] (update m v (fnil conj []) k))
                               {} (:alias cli {}))]
      (->> (m/children schema)
           (remove (fn [[field _ _]] (positional field)))
           (filter (fn [[_ props _]] (:optional props)))
           (map (fn [[field props type-schema]]
                  {:name (name field)
                   :description (:description props)
                   :type (type-display type-schema)
                   :aliases (mapv name (get inv-alias field))}))))))

;; ── auto-help display ────────────────────────────────────────────────────────

(defn- show-help [{:keys [cmd usage doc] :as spec}]
  (println (str "Usage: " cmd (when usage (str " " usage))))
  (when doc
    (println)
    (println doc))
  (when-let [opts (seq (auto-options spec))]
    (println)
    (println "Options:")
    (doseq [{:keys [name description type aliases]} opts]
      (let [flags (str/join ", " (cons (str "--" name)
                                       (map #(str "-" %) aliases)))
            desc  (if description
                    (str description " (" type ")")
                    type)]
        (println (str "  " flags "  " desc))))))

;; ── doc file support ─────────────────────────────────────────────────────────

(defn- cmd->doc-path [cmd]
  (str "bb/tasks/docs/"
       (-> cmd (str/replace #"^bb " "") (str/replace ":" "-"))
       ".md"))

(defn- parse-sections
  "Split markdown content on H1 headings. Returns {\"reference\" \"...\" \"requirements\" \"...\"}."
  [content]
  (let [parts (str/split content #"(?m)^# ")
        sections (->> (rest parts)
                      (map (fn [part]
                             (let [lines (str/split-lines part)
                                   heading (str/lower-case (str/trim (first lines)))
                                   body (str/join "\n" (rest lines))]
                               [heading (str/trim body)])))
                      (into {}))]
    sections))

(defn- show-doc
  "Show doc file content. For :all or any non-requirements section, prepend
   auto-help then append doc file content. For \"requirements\", extract just
   that H1 section."
  [spec path section]
  (if (= "requirements" section)
    (let [content  (slurp path)
          sections (parse-sections content)]
      (if-let [text (get sections "requirements")]
        (do (println "# REQUIREMENTS")
            (println)
            (println text)
            (println (str "\n---\nSource: " path)))
        (println (str "No \"requirements\" section in " path))))
    (do (show-help spec)
        (println)
        (println (slurp path))
        (println (str "---\nSource: " path)))))

;; ── validation errors ────────────────────────────────────────────────────────

(defn- format-errors [humanized]
  (->> (if (map? humanized)
         (for [[k vs] humanized, v vs] (str (name k) " " v))
         (flatten humanized))
       (remove str/blank?)
       distinct
       (str/join ", ")))

(defn- show-error [{:keys [cmd] :as spec} explanation]
  (println (str cmd ": " (format-errors (me/humanize explanation))))
  (println)
  (show-help spec))

;; ── run ──────────────────────────────────────────────────────────────────────

(defn run
  "Run a task with babashka.cli parsing + Malli validation.

   spec keys:
     :cmd    - command name for help display
     :usage  - argument syntax for help display
     :doc    - description of what the task does
     :cli    - babashka.cli opts {:args->opts [...] :coerce {...}}
     :schema - Malli schema for the PARSED map (not raw args)
     :run    - (fn [validated-map]) receives named, validated args"
  [{:keys [cli schema run] :as spec} raw-args]
  (let [raw-args (or raw-args [])]
    (if-let [section (help-section raw-args)]
      (let [path (cmd->doc-path (:cmd spec))]
        (if (.exists (java.io.File. path))
          (show-doc spec path section)
          (if (= "requirements" section)
            (println (str "No requirements defined. Create " path))
            (show-help spec))))
      (let [{:keys [opts args]} (cli/parse-args raw-args cli)]
        (cond
          ;; Extra positional args not consumed by :args->opts
          (seq args)
          (do (println (str (:cmd spec) ": unexpected arguments: " (str/join " " args)))
              (println)
              (show-help spec))

          ;; Malli validation of parsed map
          :else
          (if-let [explanation (m/explain schema opts)]
            (show-error spec explanation)
            (run opts)))))))
