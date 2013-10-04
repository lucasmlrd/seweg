(ns seweg.protocols.snmp
  (:use aleph.udp
        seweg.protocols.snmp.oid-repository 
        seweg.coders.snmp 
        [clojure.set :only (map-invert)]
        [lamina core api]
        [gloss core io])
  (:require [taoensso.timbre :as log])
  (:import [java.io FileOutputStream IOException]
           [java.math BigInteger]
           [java.util Date]
           [java.net InetAddress ServerSocket DatagramSocket]
           [java.lang Exception]))
  ;;(:require [seweg.coders.snmp :as coder :refer (SNMP)]))

(load "/seweg/coders/snmp")

(def snmp-version {:v1 0
                   :v2c 1
                   :v3 3})

;;  A number used to match requests with replies. 
;;  It is generated by the device that sends a request
;;  and copied into this field in a Response-PDU by 
;;  the responding SNMP entity.

;; Error codeas:
;;  An integer value that is used in a Response-PDU 
;;  to tell the requesting SNMP entity the result of its request.
;;  A value of zero indicates that no error occurred.
;;  Other values indicate what sort of error happened.
(def error-codes {:noError 0
                  :tooBig 1
                  :noSuchName 2
                  :badValue 3
                  :readOnly 4
                  :genErr 5
                  :noAccess 6
                  :wrongType 7
                  :wrongLength 8
                  :wrongEncoding 9
                  :wrongValue 10
                  :noCreation 11
                  :inconsistentValue 12
                  :resourceUnavailable 13
                  :commitFailed 14
                  :undoFailed 15
                  :authorizationError 16
                  :notWritable 17
                  :inconsistentName 18})

(def error-type (map-invert error-codes))

(defn get-oids-map [oids]
  (vec (for [x oids] {:type :sequence
                      :value [{:type :OID :value (if (keyword? x)
                                                   (find-oid x)
                                                   x)}
                              {:type :Null}]})))

;; PDU 

(defn get-request-pdu 
  [^Integer rid oids & options]
  "Available options are :error-type and :error-number.
  oids argument should be vector of OID values."
   {:type :get-request
    :value [{:type :Integer :value rid}
            {:type :Integer :value (or (:error-type options) 0)}
            {:type :Integer :value (or (:error-number options) 0)}
            {:type :sequence :value (get-oids-map oids)}]})

(defn get-next-request-pdu 
  [^Integer rid oids & options]
  "Available options are :error-type and :error-index. 
  oids argument should be vector of OID values."
   {:type :get-next-request
    :value [{:type :Integer :value rid}
            {:type :Integer :value (or (:error-type options) 0)}
            {:type :Integer :value (or (:error-index options) 0)}
            {:type :sequence :value (get-oids-map oids)}]});

(defn get-bulk-pdu 
  [^Integer rid oids & options]
  "Available options are :non-repetitors and :max-repetitions.
  oids argument should be vector of OID values."
  {:type :get-bulk-request
   :value [{:type :Integer :value rid}
           {:type :Integer :value (or (:non-repetitors options) 0)}
           {:type :Integer :value (or (:max-repetitions options) 100)}
           {:type :sequence :value (get-oids-map oids)}]})

(def pdu-function {:get-request get-request-pdu
                   :get-next-request get-next-request-pdu
                   :get-bulk-request get-bulk-pdu})

;; Composition

(defn compose-snmp-packet [{:keys [version community pdu]}]
  {:type :sequence
   :value [{:type :Integer :value version}
           {:type :OctetString :value community}
           pdu]})

(defn decompose-snmp-response [snmp-packet-tree]
  (let [version (-> snmp-packet-tree :value first :value)
        community (-> snmp-packet-tree :value second :value)
        pdu (-> snmp-packet-tree :value (nth 2))]
    {:version version
     :community community
     :pdu {:type (:type pdu)
           :rid (-> pdu :value first :value)
           :error-type (get error-type (-> pdu :value (nth 1) :value))
           :error-index (-> pdu :value (nth 2) :value)
           :variable-bindings (-> pdu :value (nth 3) :value)}}))

(defn get-snmp-channel 
  ([] (udp-socket {:frame (compile-frame SNMP)
                   :buf-size 3000}))
  ([port] (udp-socket {:frame (compile-frame SNMP)
                       :port port 
                       :buf-size 3000})))

(load "snmp/utils")

;;(defn test-snmp []
;;  (poke "172.29.52.194" "spzROh"))
