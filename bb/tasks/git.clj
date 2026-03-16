(ns tasks.git
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.string :as str]
            [tasks.cli :as cli]))

(def ^:private default-snapshot ".git/commit-snapshot")

(defn- exit! [code msg]
  (binding [*out* *err*]
    (println msg))
  (System/exit code))

(defn- run-git
  "Run git command, return stdout on success, exit on failure."
  ([args] (run-git nil args))
  ([env args]
   (let [opts (cond-> {:out :string :err :string}
                env (assoc :extra-env env))
         {:keys [exit out err]} (apply shell opts "git" args)]
     (if (zero? exit)
       out
       (exit! exit (if (str/blank? err)
                     (str "git " (str/join " " args) " failed")
                     (str/trim err)))))))

(defn- run-git-ok?
  "Run git command, return true on success, false on failure (no exit)."
  ([args] (run-git-ok? nil args))
  ([env args]
   (let [opts (cond-> {:out :string :err :string :continue true}
                env (assoc :extra-env env))
         {:keys [exit]} (apply shell opts "git" args)]
     (zero? exit))))

(defn- split-nul [s]
  (if (empty? s)
    []
    (->> (str/split s #"\u0000")
         (remove empty?)
         vec)))

(defn- parse-paths [s]
  (if (str/includes? s "\u0000")
    (split-nul s)
    (->> (str/split-lines s)
         (remove empty?)
         vec)))

(defn- staged-files
  "Return list of staged file paths.
   When no-renames? is true, disables rename detection so renames appear
   as separate delete + add operations."
  ([] (staged-files nil))
  ([env] (staged-files env false))
  ([env no-renames?]
   (let [args (cond-> ["diff" "--cached" "--name-only" "-z"]
                no-renames? (conj "--no-renames"))]
     (split-nul (run-git env args)))))

(defn- staged-files-with-status
  "Return map of {path {:status s, :from old-path?}} for staged files.
   Status codes: A/M/D/R/C/T/U. :from present only for renames."
  ([] (staged-files-with-status nil))
  ([env]
   (let [output (run-git env ["diff" "--cached" "--name-status" "-z"])
         parts (split-nul output)]
     ;; Output format: status\0path\0status\0path\0...
     ;; For renames: R100\0old-path\0new-path\0
     (loop [parts parts
            result {}]
       (if (empty? parts)
         result
         (let [[status path & rest] parts]
           (if (or (nil? status) (nil? path))
             result
             (if (str/starts-with? status "R")
               ;; Rename: path is old-path, next is new-path
               (let [[new-path & more] rest]
                 (recur (vec more)
                        (assoc result new-path {:status status :from path})))
               (recur (vec rest)
                      (assoc result path {:status status}))))))))))

(defn- staged-renames
  "Return map of {new-path old-path} for staged renames."
  ([] (staged-renames nil))
  ([env]
   (->> (staged-files-with-status env)
        (filter (fn [[_ v]] (:from v)))
        (into {} (map (fn [[new-path v]] [new-path (:from v)]))))))

(defn- snapshot-paths [path]
  (if (fs/exists? path)
    (parse-paths (slurp path))
    (exit! 1 (str "Snapshot not found: " path))))

(defn- temp-index-path []
  (str (java.nio.file.Files/createTempFile
        "git-index-"
        ".tmp"
        (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- current-commit-hash []
  (str/trim (run-git ["rev-parse" "--short" "HEAD"])))

(defn snapshot [args]
  (cli/run {:cmd    "bb git:snapshot"
            :usage  "[out]"
            :doc    "Save currently staged file list to .git/commit-snapshot (or provided path)."
            :cli    {:args->opts [:out]}
            :schema [:map [:out {:optional true} :string]]
            :run    (fn [{:keys [out]}]
                      (let [paths (staged-files)
                            out-path (or out default-snapshot)]
                        (when (empty? paths)
                          (exit! 1 "No staged files to snapshot."))
                        (when-let [parent (fs/parent out-path)]
                          (fs/create-dirs parent))
                        (spit out-path (str/join "\u0000" paths))
                        (println out-path)))}
           args))

(defn- unstage-paths!
  "Unstage paths from the main index. Works for all file states including deletions."
  [paths]
  (when (seq paths)
    ;; Use reset HEAD which works for adds, mods, and deletes
    ;; Unlike `restore --staged` which fails for deleted files
    (run-git (into ["reset" "-q" "HEAD" "--"] paths))))

(defn- get-index-entries
  "Get index entries for paths as lines suitable for update-index --index-info.
   Returns vector of entry strings, each formatted as: mode SP sha1 SP stage TAB path"
  [paths]
  (when (seq paths)
    (let [output (run-git (into ["ls-files" "--stage" "-z" "--"] paths))]
      (->> (split-nul output)
           ;; ls-files -z outputs: mode SP sha1 SP stage TAB path NUL
           ;; We need to convert to update-index --index-info format
           (remove empty?)
           vec))))

(defn- build-temp-index!
  "Build a temp index based on HEAD, then apply staged changes for requested paths.
   Returns the temp index path.

   For each requested path:
   - A/M: update temp index with staged blob from main index
   - D: remove from temp index (file was in HEAD, deleted in staging)
   - R: remove old path, add new path with staged blob"
  [requested-paths renames status-map]
  (let [temp-index (temp-index-path)
        env {"GIT_INDEX_FILE" temp-index}
        ;; Get entries for non-deleted paths from main index
        non-deleted-paths (remove #(= "D" (get-in status-map [% :status])) requested-paths)
        entries (get-index-entries non-deleted-paths)
        ;; Paths to remove: deleted files + old paths from renames
        deleted-paths (filter #(= "D" (get-in status-map [% :status])) requested-paths)
        old-rename-paths (keep renames requested-paths)
        paths-to-remove (concat deleted-paths old-rename-paths)]
    ;; Start with HEAD tree (not empty!)
    (run-git env ["read-tree" "HEAD"])
    ;; Remove deleted files and old rename paths from temp index
    (when (seq paths-to-remove)
      (run-git env (into ["update-index" "--force-remove" "--"] paths-to-remove)))
    ;; Update temp index with staged entries (adds and modifies)
    (when (seq entries)
      (let [input (str (str/join "\u0000" entries) "\u0000")]
        (let [opts {:out :string :err :string :in input
                    :extra-env env}
              {:keys [exit err]} (shell opts "git" "update-index" "-z" "--index-info")]
          (when-not (zero? exit)
            (exit! exit (str "Failed to build temp index: " (str/trim err)))))))
    temp-index))

(defn commit [args]
  (cli/run {:cmd    "bb git:commit"
            :usage  "<message> [<path>...] [--paths-from <file>]"
            :doc    "Commit staged content for listed paths using a temp index.
For renames, specify only the NEW (destination) path."
            :cli    {:args->opts [:message :paths]
                     :coerce     {:paths []
                                  :paths-from :string
                                  :from-snapshot :boolean
                                  :keep-staged :boolean}
                     :alias      {:p :paths-from
                                  :s :snapshot
                                  :k :keep-staged}}
            :schema [:map
                     [:message :string]
                     [:paths {:optional true} [:* :string]]
                     [:paths-from {:optional true
                                   :description "Read additional paths from file (one per line)"}
                      :string]
                     [:from-snapshot {:optional true
                                      :description "Validate paths against .git/commit-snapshot"}
                      :boolean]
                     [:snapshot {:optional true
                                 :description "Validate paths against custom snapshot file"}
                      :string]
                     [:keep-staged {:optional true
                                    :description "Don't unstage committed paths from main index"}
                      :boolean]]
            :run    (fn [{:keys [message paths paths-from from-snapshot snapshot keep-staged]}]
                      (when (str/blank? message)
                        (exit! 1 "Commit message is required."))
                      (let [file-paths (when paths-from
                                         (parse-paths (slurp paths-from)))
                            all-paths (concat paths file-paths)
                            requested (vec (distinct all-paths))
                            staged (staged-files)
                            staged-set (set staged)
                            status-map (staged-files-with-status)
                            renames (staged-renames)]
                        (when (empty? staged)
                          (exit! 1 "No staged files to commit."))
                        (when (empty? requested)
                          (exit! 1 "At least one path is required."))
                        ;; Check for paths that look like old rename paths
                        (let [old-rename-paths (set (vals renames))
                              requested-old-paths (filter old-rename-paths requested)]
                          (when (seq requested-old-paths)
                            (let [suggestions (->> requested-old-paths
                                                   (map (fn [old]
                                                          (let [new (some (fn [[n o]] (when (= o old) n)) renames)]
                                                            (str "  " old " -> use: " new))))
                                                   (str/join "\n"))]
                              (exit! 1 (str "Cannot commit old rename paths. Use the NEW path instead:\n" suggestions)))))
                        (let [missing (remove staged-set requested)]
                          (when (seq missing)
                            (exit! 1 (str "Paths not staged: " (str/join ", " missing)))))
                        (let [use-snapshot? (or from-snapshot (some? snapshot))]
                          (when use-snapshot?
                            (let [snap-path (or snapshot default-snapshot)
                                  snap-set (set (snapshot-paths snap-path))
                                  missing (remove snap-set requested)]
                              (when (seq missing)
                                (exit! 1 (str "Paths not in snapshot: " (str/join ", " missing)))))))
                        ;; Build temp index based on HEAD, applying staged changes for requested paths
                        (let [temp-index (build-temp-index! requested renames status-map)
                              env {"GIT_INDEX_FILE" temp-index}
                              ;; Expected diff includes requested paths + old paths from renames
                              old-rename-paths (keep renames requested)
                              expected-set (into (set requested) old-rename-paths)]
                          ;; Verify temp index contents by comparing to HEAD
                          ;; Use no-renames mode so renames show as delete+add, matching expected-set
                          (let [temp-staged (staged-files env true)]
                            (when (not= (set temp-staged) expected-set)
                              (exit! 1 (str "Temp index mismatch.\n"
                                            "Expected: " (str/join ", " (sort expected-set)) "\n"
                                            "Got: " (str/join ", " (sort temp-staged))))))
                          (run-git env ["commit" "-m" message])
                          (let [hash (current-commit-hash)
                                ;; For unstaging, include old rename paths
                                paths-to-unstage (into requested
                                                       (keep renames requested))]
                            (when-not keep-staged
                              (unstage-paths! paths-to-unstage))
                            (when (fs/exists? temp-index)
                              (fs/delete temp-index))
                            (println (str hash " " (first (str/split-lines message))))
                            (println "Files:" (str/join ", " requested))))))}
           args))

(defn unstage [args]
  (cli/run {:cmd    "bb git:unstage"
            :usage  "<path>..."
            :doc    "Unstage files from the index. Works for all file states (add/modify/delete).
For renames, use the NEW (destination) path."
            :cli    {:args->opts [:paths]
                     :coerce     {:paths []}}
            :schema [:map [:paths [:+ :string]]]
            :run    (fn [{:keys [paths]}]
                      (let [staged-set (set (staged-files))
                            renames (staged-renames)
                            old-rename-paths (set (vals renames))]
                        ;; Check for old rename paths
                        (let [requested-old-paths (filter old-rename-paths paths)]
                          (when (seq requested-old-paths)
                            (let [suggestions (->> requested-old-paths
                                                   (map (fn [old]
                                                          (let [new (some (fn [[n o]] (when (= o old) n)) renames)]
                                                            (str "  " old " -> use: " new))))
                                                   (str/join "\n"))]
                              (exit! 1 (str "Cannot unstage old rename paths. Use the NEW path instead:\n" suggestions)))))
                        (let [missing (remove staged-set paths)]
                          (when (seq missing)
                            (exit! 1 (str "Paths not staged: " (str/join ", " missing)))))
                        ;; For renames, also unstage the old path
                        (let [paths-to-unstage (into (vec paths)
                                                     (keep renames paths))]
                          (unstage-paths! paths-to-unstage)
                          (println "Unstaged" (count paths) "paths."))))}
           args))

(defn staged [_args]
  (let [status-map (staged-files-with-status)]
    (if (empty? status-map)
      (println "No staged files.")
      (doseq [[path {:keys [status from]}] (sort-by first status-map)]
        (if from
          (println (str status "\t" from " -> " path))
          (println (str status "\t" path)))))))

(defn- format-status [s]
  (case (first s)
    \A "added"
    \M "modified"
    \D "deleted"
    \R "renamed"
    \C "copied"
    \T "typechange"
    \U "unmerged"
    "unknown"))

(defn plan [_args]
  (let [status-map (staged-files-with-status)]
    (if (empty? status-map)
      (println "No staged files.")
      (let [groups (group-by (comp first :status val) status-map)
            has-renames? (contains? groups \R)]
        (println "Staged changes:")
        (println)
        (doseq [[status-char paths] (sort-by first groups)]
          (println (str "  " (format-status (str status-char)) ":"))
          (doseq [[path {:keys [from]}] (sort-by first paths)]
            (if from
              (println (str "    " from " -> " path))
              (println (str "    " path))))
          (println))
        (when has-renames?
          (println "Note: To commit renames, use the NEW path (destination).")
          (println))
        (println "Total:" (count status-map) "files")))))
