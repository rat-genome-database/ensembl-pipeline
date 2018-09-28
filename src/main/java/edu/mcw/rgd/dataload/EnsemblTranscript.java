package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.Transcript;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Sep 7, 2010
 * Time: 3:00:41 PM
 * represents the transcript read from ensembl
 */
public class EnsemblTranscript {
    String ensemblGeneId; // ensembl gene id this transcript belongs to
    String transcriptId; // ensembl transcript id
    String transcriptStableId; // canonical transcript stable id
    String biotype; // transcript biotype, like 'protein-coding'
    String status; // transcript status, like 'KNOWN'
    String chromosome; // chromosome transcript appears
    int startPos; // start position on the chromosome
    int stopPos; // stop position on the chromosome

    Transcript rgdTranscript; // matching rgd transcript

    // two ensembl transcripts match if they have the same ensembl transcript id
    @Override
    public boolean equals(Object obj) {
        if( obj instanceof String )
            return getTranscriptId().equals(obj);
        else
        if( obj instanceof EnsemblTranscript ) {
            EnsemblTranscript et = (EnsemblTranscript) obj;
            return getTranscriptId().equals(et.getTranscriptId());
        }
        else
            return super.equals(obj);
    }

    public String getEnsemblGeneId() {
        return ensemblGeneId;
    }

    public void setEnsemblGeneId(String ensemblGeneId) {
        this.ensemblGeneId = ensemblGeneId;
    }

    public String getTranscriptId() {
        return transcriptId;
    }

    public void setTranscriptId(String transcriptId) {
        this.transcriptId = transcriptId;
    }

    public void setTranscriptStableId(String transcriptId) {
        this.transcriptStableId = transcriptId;
    }

    public String getBiotype() {
        return biotype;
    }

    public void setBiotype(String biotype) {
        this.biotype = biotype;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChromosome() {
        return chromosome;
    }

    public void setChromosome(String chromosome) {
        this.chromosome = chromosome;
    }

    public int getStartPos() {
        return startPos;
    }

    public void setStartPos(int startPos) {
        this.startPos = startPos;
    }

    public int getStopPos() {
        return stopPos;
    }

    public void setStopPos(int stopPos) {
        this.stopPos = stopPos;
    }

    public Transcript getRgdTranscript() {
        return rgdTranscript;
    }

    public void setRgdTranscript(Transcript rgdTranscript) {
        this.rgdTranscript = rgdTranscript;
    }
}
