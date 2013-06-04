# rxjava-http-tail

A [RxJava](https://github.com/Netflix/RxJava/wiki)-based port of [Net::HTTP::FollowTail](https://github.com/broquaint/net-http-follow_tail) to the JVM-land.

This allows you to follow logs like `tail -f` through HTTP.

## Usage

It can be used from any language on the JVM, but here's an example in Clojure:

```clojure
(ns example
  (:import rx.HttpTail))

(defn read-offset []
  (Long/parseLong (slurp "offset.txt")))

(defn write-offset [o]
  (spit "offset.txt" o))

(defn on-result [result]
  (write-offset (.getOffset result))
  (prn (.getBody result)))

(-> (HttpTail. "http://crawl.akrasiac.org/logfile-git" (read-offset) 15000)
    .createObservable
    (.subscribe on-result))
```

```shell
$ echo "59690466" > offset.txt
$ lein run -m example
```
