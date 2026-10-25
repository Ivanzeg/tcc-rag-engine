package br.ufabc.tcc.rag.adapters.corpus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CleaningReport {

    public final Map<String, Integer> headerFooterForms = new LinkedHashMap<>();
    public final List<String> headerFooterRemoved = new ArrayList<>();
    public final List<String> hyphenJoinSamples = new ArrayList<>();
    public final List<String> hyphenRepeatSamples = new ArrayList<>();
    public final List<String> annotationsRemoved = new ArrayList<>();
    public final List<String> emptiedDiscarded = new ArrayList<>();
    public final List<String> footnoteCandidates = new ArrayList<>();
    public final List<String> vetoedDiscarded = new ArrayList<>();
    public int pageCount;
}
