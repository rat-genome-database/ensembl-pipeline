package edu.mcw.rgd.dataload;

import org.apache.commons.logging.Log;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Aug 16, 2010
 * Time: 1:15:36 PM
 * contains the summary for all processing
 */
public class EnsemblGeneSummary {

    public int ensemblGenesProcessed;
    public int ensemblGenesRgdMatched;
    public int ensemblGenesNotFoundInRgd;
    public int ensemblGenesRgdMultiMatch;
    public int ensemblGenesRgdSymbolMismatch;
    public int ensemblGenesNotActiveRgdGeneMatch;
    public int rgdGenesNotMatchingWithEnsembl;
    public int ensemblGenesMatchedByRgdSymbol;
    public int ensemblGenesMatchedByIncomingRgdIds;
    public int ensemblGenesMatchedByIncomingEGIds;
    public int ensemblGenesWithNcbiGeneIds;
    public int ensemblGenesWithAllMatchingTranscripts;
    public int ensemblGenesWithAllMatchingTranscripts2;
    public int ensemblGenesWithSomeMatchingTranscripts;
    public int ensemblGenesWithNonMatchingTranscripts;
    public int ensemblGenesMatchedByTranscripts;

    // dump summary to log
    public void dumpSummary(Log log) {
        log.warn("SUMMARY FOR GENES:");
        log.warn(". Ensembl genes processed: "+ensemblGenesProcessed);
        log.warn(". Ensembl genes having NCBI Gene ids: "+ensemblGenesWithNcbiGeneIds);

        log.warn("+ Ensembl genes matched with RGD by ENSRNOGxxx: "+ensemblGenesRgdMatched);
        log.warn("+ Ensembl genes matched with RGD by RGD symbol: "+ensemblGenesMatchedByRgdSymbol);
        log.warn("+ Ensembl genes matched by incoming RGD IDS: "+ensemblGenesMatchedByIncomingRgdIds);
        log.warn("+ Ensembl genes matched by incoming NCBI Gene IDS: "+ensemblGenesMatchedByIncomingEGIds);
        log.warn("! Ensembl genes matched with RGD by transcript position: "+ensemblGenesMatchedByTranscripts);

        log.warn("- Ensembl genes matched with multiple rgd ids: "+ensemblGenesRgdMultiMatch);
        log.warn("- Ensembl genes matched, RGD symbol mismatch: "+ensemblGenesRgdSymbolMismatch);
        log.warn("- Ensembl genes matched with non-active genes: "+ensemblGenesNotActiveRgdGeneMatch);
        log.warn("- Ensembl genes not found in RGD: "+ensemblGenesNotFoundInRgd);
        log.warn("- RGD genes not matching with Ensembl: "+rgdGenesNotMatchingWithEnsembl);

        log.warn("+ Ensembl genes will all matching transcripts by pos: "+ensemblGenesWithAllMatchingTranscripts);
        log.warn("- Ensembl genes will all matching transcripts by pos, but mismatched gene rgd id: "+ensemblGenesWithAllMatchingTranscripts2);
        log.warn(". Ensembl genes will some matching transcripts by pos: "+ensemblGenesWithSomeMatchingTranscripts);
        log.warn("- Ensembl genes will none matching transcript by pos: "+ensemblGenesWithNonMatchingTranscripts);
    }
}
