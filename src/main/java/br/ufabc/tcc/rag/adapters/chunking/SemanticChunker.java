package br.ufabc.tcc.rag.adapters.chunking;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.port.Chunker;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.JDKRandomGenerator;

public final class SemanticChunker implements Chunker {

    private static final String NAME = "semantic";
    private static final String STOPWORDS_RESOURCE = "/stopwords_pt.txt";
    private static final int SEED = 42;
    private static final int MAX_ITERATIONS = 300;

    private final int k;
    private final int maxWords;
    private final TfIdf tfIdf;

    public SemanticChunker(int k, int maxWords) {
        this.k = k;
        this.maxWords = maxWords;
        this.tfIdf = new TfIdf(loadStopwords());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Chunk> chunk(Corpus corpus) {
        List<Unit> units = corpus.units();
        List<String> texts = units.stream().map(Unit::text).toList();
        List<double[]> vectors = tfIdf.vectorize(texts);

        List<UnitPoint> points = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            points.add(new UnitPoint(i, vectors.get(i)));
        }
        KMeansPlusPlusClusterer<UnitPoint> clusterer = new KMeansPlusPlusClusterer<>(
                k, MAX_ITERATIONS, new EuclideanDistance(), new JDKRandomGenerator(SEED));
        List<CentroidCluster<UnitPoint>> clusters = clusterer.cluster(points);

        List<List<Integer>> clusterMembers = new ArrayList<>();
        for (CentroidCluster<UnitPoint> cluster : clusters) {
            List<Integer> members = cluster.getPoints().stream().map(UnitPoint::index).sorted().toList();
            if (!members.isEmpty()) {
                clusterMembers.add(members);
            }
        }
        clusterMembers.sort(Comparator.comparing(members -> members.get(0)));

        int[] words = UnitChunks.wordCounts(texts);
        List<List<Integer>> groups = new ArrayList<>();
        for (List<Integer> members : clusterMembers) {
            groups.addAll(UnitChunks.splitByWords(members, words, maxWords));
        }
        System.out.println("Semântico: vocabulário TF-IDF com " + vectors.get(0).length + " termos, "
                + clusterMembers.size() + " clusters não vazios de " + k + ", " + groups.size() + " chunks");
        return UnitChunks.build(NAME, units, groups);
    }

    private static Set<String> loadStopwords() {
        InputStream in = SemanticChunker.class.getResourceAsStream(STOPWORDS_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("Lista de stopwords não encontrada no classpath: " + STOPWORDS_RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + STOPWORDS_RESOURCE, e);
        }
    }

    private record UnitPoint(int index, double[] point) implements Clusterable {

        @Override
        public double[] getPoint() {
            return point;
        }
    }
}
