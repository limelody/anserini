# Anserini Regressions: Tweets2013 (MB13 &amp; MB14)

**Models**: various bag-of-words approaches

This page describes regressions for the Microblog Tracks from TREC 2013 and 2014 using the Tweets2013 collection.
The exact configurations for these regressions are stored in [this YAML file](../src/main/resources/regression/mb13.yaml).
Note that this page is automatically generated from [this template](../src/main/resources/docgen/templates/mb13.template) as part of Anserini's regression pipeline, so do not modify this page directly; modify the template instead.

From one of our Waterloo servers (e.g., `orca`), the following command will perform the complete regression, end to end:

```
python src/main/python/run_regression.py --index --verify --search --regression mb13
```

## Indexing

Note that the Tweets2013 collection is distributed as a list of tweet ids that you have to download yourself, so the
effectiveness results you'll get should be similar, but will likely not be identical to the scores reported here.

Indexing the Tweets2013 collection:

```
target/appassembler/bin/IndexCollection \
  -collection TweetCollection \
  -input /path/to/mb13 \
  -index indexes/lucene-index.mb13/ \
  -generator TweetGenerator \
  -threads 44 -storePositions -storeDocvectors -storeRaw -uniqueDocid -optimize -tweet.keepUrls -tweet.stemming \
  >& logs/log.mb13 &
```

More available indexing options:
* `-tweet.keepRetweets`: boolean switch to keep retweets while indexing, default `false`
* `-tweet.keepUrls`: boolean switch to keep URLs in the tweet, default `false`
* `-tweet.stemming`: boolean switch to apply Porter stemming while indexing tweets, default `false`
* `-tweet.maxId`: the max tweet Id for indexing. Tweet Ids that are larger (when being parsed to Long type) than this value will NOT be indexed, default `LONG.MAX_VALUE`
* `-tweet.deletedIdsFile`: a file that contains deleted tweetIds, one per line. these tweeets won't be indexed

For additional details, see explanation of [common indexing options](common-indexing-options.md).

## Retrieval

Topics and qrels are stored in [`src/main/resources/topics-and-qrels/`](../src/main/resources/topics-and-qrels/), downloaded from NIST:

+ [`topics.microblog2013.txt`](../src/main/resources/topics-and-qrels/topics.microblog2013.txt): [topics for the TREC 2013 Microblog Track](https://trec.nist.gov/data/microblog/2013/topics.MB111-170.txt)
+ [`topics.microblog2014.txt`](../src/main/resources/topics-and-qrels/topics.microblog2014.txt): [topics for the TREC 2014 Microblog Track](https://trec.nist.gov/data/microblog/2014/topics.MB171-225.txt)
+ [`qrels.microblog2013.txt`](../src/main/resources/topics-and-qrels/qrels.microblog2013.txt): [qrels for the TREC 2013 Microblog Track](https://trec.nist.gov/data/microblog/2013/qrels.txt)
+ [`qrels.microblog2014.txt`](../src/main/resources/topics-and-qrels/qrels.microblog2014.txt): [qrels for the TREC 2014 Microblog Track](https://trec.nist.gov/data/microblog/2014/qrels2014.txt)

After indexing has completed, you should be able to perform retrieval as follows:

```
target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2013.txt \
  -topicreader Microblog \
  -output runs/run.mb13.bm25.topics.microblog2013.txt \
  -searchtweets -bm25 &
target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2014.txt \
  -topicreader Microblog \
  -output runs/run.mb13.bm25.topics.microblog2014.txt \
  -searchtweets -bm25 &

target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2013.txt \
  -topicreader Microblog \
  -output runs/run.mb13.bm25+rm3.topics.microblog2013.txt \
  -searchtweets -bm25 -rm3 &
target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2014.txt \
  -topicreader Microblog \
  -output runs/run.mb13.bm25+rm3.topics.microblog2014.txt \
  -searchtweets -bm25 -rm3 &

target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2013.txt \
  -topicreader Microblog \
  -output runs/run.mb13.bm25+ax.topics.microblog2013.txt \
  -searchtweets -bm25 -axiom -axiom.beta 1.0 -axiom.deterministic -rerankCutoff 20 &
target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2014.txt \
  -topicreader Microblog \
  -output runs/run.mb13.bm25+ax.topics.microblog2014.txt \
  -searchtweets -bm25 -axiom -axiom.beta 1.0 -axiom.deterministic -rerankCutoff 20 &

target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2013.txt \
  -topicreader Microblog \
  -output runs/run.mb13.ql.topics.microblog2013.txt \
  -searchtweets -qld &
target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2014.txt \
  -topicreader Microblog \
  -output runs/run.mb13.ql.topics.microblog2014.txt \
  -searchtweets -qld &

target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2013.txt \
  -topicreader Microblog \
  -output runs/run.mb13.ql+rm3.topics.microblog2013.txt \
  -searchtweets -qld -rm3 &
target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2014.txt \
  -topicreader Microblog \
  -output runs/run.mb13.ql+rm3.topics.microblog2014.txt \
  -searchtweets -qld -rm3 &

target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2013.txt \
  -topicreader Microblog \
  -output runs/run.mb13.ql+ax.topics.microblog2013.txt \
  -searchtweets -qld -axiom -axiom.beta 1.0 -axiom.deterministic -rerankCutoff 20 &
target/appassembler/bin/SearchCollection \
  -index indexes/lucene-index.mb13/ \
  -topics src/main/resources/topics-and-qrels/topics.microblog2014.txt \
  -topicreader Microblog \
  -output runs/run.mb13.ql+ax.topics.microblog2014.txt \
  -searchtweets -qld -axiom -axiom.beta 1.0 -axiom.deterministic -rerankCutoff 20 &
```

Evaluation can be performed using `trec_eval`:

```
tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2013.txt runs/run.mb13.bm25.topics.microblog2013.txt
tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2014.txt runs/run.mb13.bm25.topics.microblog2014.txt

tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2013.txt runs/run.mb13.bm25+rm3.topics.microblog2013.txt
tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2014.txt runs/run.mb13.bm25+rm3.topics.microblog2014.txt

tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2013.txt runs/run.mb13.bm25+ax.topics.microblog2013.txt
tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2014.txt runs/run.mb13.bm25+ax.topics.microblog2014.txt

tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2013.txt runs/run.mb13.ql.topics.microblog2013.txt
tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2014.txt runs/run.mb13.ql.topics.microblog2014.txt

tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2013.txt runs/run.mb13.ql+rm3.topics.microblog2013.txt
tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2014.txt runs/run.mb13.ql+rm3.topics.microblog2014.txt

tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2013.txt runs/run.mb13.ql+ax.topics.microblog2013.txt
tools/eval/trec_eval.9.0.4/trec_eval -m map -m P.30 src/main/resources/topics-and-qrels/qrels.microblog2014.txt runs/run.mb13.ql+ax.topics.microblog2014.txt
```

## Effectiveness

With the above commands, you should be able to reproduce the following results:

| **MAP**                                                                                                      | **BM25**  | **+RM3**  | **+Ax**   | **QL**    | **+RM3**  | **+Ax**   |
|:-------------------------------------------------------------------------------------------------------------|-----------|-----------|-----------|-----------|-----------|-----------|
| [TREC 2013 Microblog Track Topics](../src/main/resources/topics-and-qrels/topics.microblog2013.txt)          | 0.2371    | 0.2499    | 0.2855    | 0.2602    | 0.2878    | 0.3152    |
| [TREC 2014 Microblog Track Topics](../src/main/resources/topics-and-qrels/topics.microblog2014.txt)          | 0.3931    | 0.4361    | 0.4796    | 0.4181    | 0.4667    | 0.4965    |
| **P30**                                                                                                      | **BM25**  | **+RM3**  | **+Ax**   | **QL**    | **+RM3**  | **+Ax**   |
| [TREC 2013 Microblog Track Topics](../src/main/resources/topics-and-qrels/topics.microblog2013.txt)          | 0.4339    | 0.4383    | 0.4728    | 0.4561    | 0.4839    | 0.5078    |
| [TREC 2014 Microblog Track Topics](../src/main/resources/topics-and-qrels/topics.microblog2014.txt)          | 0.6212    | 0.6406    | 0.6648    | 0.6430    | 0.6552    | 0.6727    |
