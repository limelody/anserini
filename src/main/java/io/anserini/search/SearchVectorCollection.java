/*
 * Anserini: A Lucene toolkit for reproducible information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.search;

import io.anserini.index.IndexVectorArgs;
import io.anserini.rerank.ScoredDocuments;
import io.anserini.search.query.VectorQueryGenerator;
import io.anserini.search.topicreader.TopicReader;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.ParserProperties;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main entry point for search.
 */
public final class SearchVectorCollection implements Closeable {
  // These are the default tie-breaking rules for documents that end up with the same score with respect to a query.
  // For most collections, docids are strings, and we break ties by lexicographic sort order. For tweets, docids are
  // longs, and we break ties by reverse numerical sort order (i.e., most recent tweet first). This means that searching
  // tweets requires a slightly different code path, which is enabled by the -searchtweets option in SearchVectorArgs.
  public static final Sort BREAK_SCORE_TIES_BY_DOCID =
      new Sort(SortField.FIELD_SCORE, new SortField(IndexVectorArgs.ID, SortField.Type.STRING_VAL));

  private static final Logger LOG = LogManager.getLogger(SearchVectorCollection.class);

  private final SearchVectorArgs args;
  private final IndexReader reader;

  private final class SearcherThread<K> extends Thread {
    final private IndexReader reader;
    final private IndexSearcher searcher;
    final private SortedMap<K, Map<String, String>> topics;
    final private String outputPath;
    final private String runTag;

    private SearcherThread(IndexReader reader, SortedMap<K, Map<String, String>> topics, String outputPath, String runTag) {
      this.reader = reader;
      this.topics = topics;
      this.runTag = runTag;
      this.outputPath = outputPath;
      this.searcher = new IndexSearcher(this.reader);
      setName(outputPath);
    }

    @Override
    public void run() {
      try {
        // A short descriptor of the ranking setup.
        final String desc = String.format("ranker: kNN");
        // ThreadPool for parallelizing the execution of individual queries:
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(args.parallelism);
        // Data structure for holding the per-query results, with the qid as the key and the results (the lines that
        // will go into the final run file) as the value.
        ConcurrentSkipListMap<K, String> results = new ConcurrentSkipListMap<>();
        AtomicInteger cnt = new AtomicInteger();

        final long start = System.nanoTime();
        for (Map.Entry<K, Map<String, String>> entry : topics.entrySet()) {
          K qid = entry.getKey();

          // This is the per-query execution, in parallel.
          executor.execute(() -> {
            // This is for holding the results.
            StringBuilder out = new StringBuilder();
            String queryString = entry.getValue().get(args.topicfield);
            ScoredDocuments docs;
            try {
              docs = search(this.searcher, queryString);
            } catch (IOException e) {
              throw new CompletionException(e);
            }

            // For removing duplicate docids.
            Set<String> docids = new HashSet<>();

            int rank = 1;
            for (int i = 0; i < docs.documents.length; i++) {
              String docid = docs.documents[i].get(IndexVectorArgs.ID);

              if (args.selectMaxPassage) {
                docid = docid.split(args.selectMaxPassage_delimiter)[0];
              }

              if (docids.contains(docid))
                continue;

              // Remove docids that are identical to the query id if flag is set.
              if (args.removeQuery && docid.equals(qid))
                continue;

              if ("msmarco".equals(args.format)) {
                // MS MARCO output format:
                out.append(String.format(Locale.US, "%s\t%s\t%d\n", qid, docid, rank));
              } else {
                // Standard TREC format:
                // + the first column is the topic number.
                // + the second column is currently unused and should always be "Q0".
                // + the third column is the official document identifier of the retrieved document.
                // + the fourth column is the rank the document is retrieved.
                // + the fifth column shows the score (integer or floating point) that generated the ranking.
                // + the sixth column is called the "run tag" and should be a unique identifier for your
                out.append(String.format(Locale.US, "%s Q0 %s %d %f %s\n",
                    qid, docid, rank, docs.scores[i], runTag));
              }

              // Note that this option is set to false by default because duplicate documents usually indicate some
              // underlying indexing issues, and we don't want to just eat errors silently.
              //
              // However, we we're performing passage retrieval, i.e., with "selectMaxSegment", we *do* want to remove
              // duplicates.
              if (args.removedups || args.selectMaxPassage) {
                docids.add(docid);
              }

              rank++;

              if (args.selectMaxPassage && rank > args.selectMaxPassage_hits) {
                break;
              }
            }

            results.put(qid, out.toString());
            int n = cnt.incrementAndGet();
            if (n % 100 == 0) {
              LOG.info(String.format("%s: %d queries processed", desc, n));
            }
          });
        }

        executor.shutdown();

        try {
          // Wait for existing tasks to terminate.
          while (!executor.awaitTermination(1, TimeUnit.MINUTES));
        } catch (InterruptedException ie) {
          // (Re-)Cancel if current thread also interrupted.
          executor.shutdownNow();
          // Preserve interrupt status.
          Thread.currentThread().interrupt();
        }
        final long durationMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);

        LOG.info(desc + ": " + topics.size() + " queries processed in " +
            DurationFormatUtils.formatDuration(durationMillis, "HH:mm:ss") +
            String.format(" = ~%.2f q/s", topics.size()/(durationMillis/1000.0)));

        // Now we write the results to a run file.
        PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(outputPath), StandardCharsets.UTF_8));

        // This is the default case: just dump out the qids by their natural order.
        for (K qid : results.keySet()) {
          out.print(results.get(qid));
        }
        out.flush();
        out.close();

      } catch (Exception e) {
        LOG.error(Thread.currentThread().getName() + ": Unexpected Exception: ", e);
      }
    }
  }

  public SearchVectorCollection(SearchVectorArgs args) throws IOException {
    this.args = args;
    Path indexPath = Paths.get(args.index);

    if (!Files.exists(indexPath) || !Files.isDirectory(indexPath) || !Files.isReadable(indexPath)) {
      throw new IllegalArgumentException(String.format("Index path '%s' does not exist or is not a directory.", args.index));
    }

    LOG.info("============ Initializing Searcher ============");
    LOG.info("Index: " + indexPath);
    this.reader = args.inmem ? DirectoryReader.open(MMapDirectory.open(indexPath)) :
        DirectoryReader.open(FSDirectory.open(indexPath));
    LOG.info("Vector Search:");
    LOG.info("Number of threads for running different parameter configurations: " + args.threads);
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  @SuppressWarnings("unchecked")
  public <K> void runTopics() throws IOException {
    TopicReader<K> tr;
    SortedMap<K, Map<String, String>> topics = new TreeMap<>();
    for (String singleTopicsFile : args.topics) {
      Path topicsFilePath = Paths.get(singleTopicsFile);
      if (!Files.exists(topicsFilePath) || !Files.isRegularFile(topicsFilePath) || !Files.isReadable(topicsFilePath)) {
        throw new IllegalArgumentException("Topics file : " + topicsFilePath + " does not exist or is not a (readable) file.");
      }
      try {
        tr = (TopicReader<K>) Class.forName("io.anserini.search.topicreader." + args.topicReader + "TopicReader")
            .getConstructor(Path.class).newInstance(topicsFilePath);
        topics.putAll(tr.read());
      } catch (Exception e) {
        e.printStackTrace();
        throw new IllegalArgumentException("Unable to load topic reader: " + args.topicReader);
      }
    }

    final String runTag = args.runtag == null ? "Anserini" : args.runtag;
    LOG.info("runtag: " + runTag);

    final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(args.threads);


    LOG.info("============ Launching Search Threads ============");

    String outputPath = args.output;
    if (args.skipexists && new File(outputPath).exists()) {
      LOG.info("Run already exists, skipping: " + outputPath);
    } else {
      executor.execute(new SearcherThread<>(reader, topics, outputPath, runTag));
      executor.shutdown();
    }

    try {
      // Wait for existing tasks to terminate
      while (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
      }
    } catch (InterruptedException ie) {
      // (Re-)Cancel if current thread also interrupted
      executor.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }

  public ScoredDocuments search(IndexSearcher searcher, String queryString) throws IOException {
    KnnVectorQuery query;
    VectorQueryGenerator generator;
    try {
      generator = (VectorQueryGenerator) Class.forName("io.anserini.search.query." + args.queryGenerator)
          .getConstructor().newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Unable to load QueryGenerator: " + args.topicReader);
    }

    // If fieldsMap isn't null, then it means that the -fields option is specified. In this case, we search across
    // multiple fields with the associated boosts.
    query = generator.buildQuery(IndexVectorArgs.VECTOR, queryString, args.efSearch);

    TopDocs rs = new TopDocs(new TotalHits(0, TotalHits.Relation.EQUAL_TO), new ScoreDoc[]{});
    rs = searcher.search(query, args.hits);
    ScoredDocuments scoredDocs;
    scoredDocs = ScoredDocuments.fromTopDocs(rs, searcher);

    return scoredDocs;
  }


  public static void main(String[] args) throws Exception {
    SearchVectorArgs searchArgs = new SearchVectorArgs();
    CmdLineParser parser = new CmdLineParser(searchArgs, ParserProperties.defaults().withUsageWidth(100));

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.err.println("Example: SearchCollection" + parser.printExample(OptionHandlerFilter.REQUIRED));
      return;
    }

    final long start = System.nanoTime();
    SearchVectorCollection searcher;

    // We're at top-level already inside a main; makes no sense to propagate exceptions further, so reformat the
    // exception messages and display on console.
    try {
      searcher = new SearchVectorCollection(searchArgs);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      return;
    }

    searcher.runTopics();
    searcher.close();
    final long durationMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    LOG.info("Total run time: " + DurationFormatUtils.formatDuration(durationMillis, "HH:mm:ss"));
  }
}
