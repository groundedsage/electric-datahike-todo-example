(ns electric-starter-app.main
  (:require [contrib.str :refer [empty->nil]]
            #?(:clj [datahike.api :as d])
            ;; #?(:clj [datahike-s3.core])
            #?(:clj [datahike-jdbc.core])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

(e/def db) 

;; Datahike
#?(:clj (def cfg {:store {:backend :jdbc
                          :dbtype "postgresql"
                          :host "aws-0-ap-southeast-1.pooler.supabase.com"
                          :user "postgres.ngzcoqqvbiwsekodbpde"
                          :password "datahike-todo"
                          :dbname "postgres"}
                   :schema-flexibility :read}
          #_{:store {:backend :file :path "/tmp/example"}
                  :schema-flexibility :read}))

#?(:clj (when-not (d/database-exists? cfg) (d/create-database cfg)))
#?(:clj (def !conn (d/connect cfg)))



(e/defn TodoItem [id]
  (e/server
    (let [e (d/entity db id)
          status (:task/status e)]
      (e/client
        (dom/li
          (ui/checkbox
            (case status :active false, :done true)
            (e/fn [v]
              (e/server
                (e/offload #(do (d/transact !conn [{:db/id id
                                                    :task/status (if v :done :active)}])
                              nil))))
            (dom/props {:id id}))
          (dom/label (dom/props {:for id})
            (dom/text (e/server (:task/description e)))))))))

(e/defn InputSubmit [F]
  ; Custom input control using lower dom interface for Enter handling
  (e/client
    (dom/input (dom/props {:placeholder "Buy milk"})
      (dom/on "keydown" (e/fn [e]
                          (when (= "Enter" (.-key e))
                            (when-some [v (empty->nil (-> e .-target .-value))]
                              (new F v)
                              (set! (.-value dom/node) ""))))))))

(e/defn TodoCreate []
  (e/client
    (InputSubmit. (e/fn [v]
                    (e/server
                      (println "transacting data" v)
                      (e/offload #(do (d/transact !conn [{:task/description v
                                                          :task/status :active}])
                                    nil))
                      nil)))))

#?(:clj (defn todo-count [db]
          (count
            (d/q '[:find [?e ...] :in $ ?status
                   :where [?e :task/status ?status]]
              db :active))))

#?(:clj (defn todo-records [db]
          (->> (d/q '[:find [(pull ?e [:db/id :task/description]) ...]
                      :where [?e :task/status]] db)
            (sort-by :task/description))))

(e/defn Main [ring-request]
  (e/server
    (binding [db (e/watch !conn)]
      (e/client
        (binding [dom/node js/document.body]
          (dom/div (dom/props {:class "todo-list"})
            (TodoCreate.)
            (dom/ul (dom/props {:class "todo-items"})
              (e/server
                (e/for-by :db/id [{:keys [db/id]} (e/offload #(todo-records db))]
                  (TodoItem. id))))
            (dom/p (dom/props {:class "counter"})
              (dom/span (dom/props {:class "count"})
                (dom/text (e/server (e/offload #(todo-count db)))))
              (dom/text " items left"))))))))


(comment 
  

  (d/delete-database cfg)
  
  )