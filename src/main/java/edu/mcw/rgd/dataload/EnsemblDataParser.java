package edu.mcw.rgd.dataload;

import edu.mcw.rgd.process.PipelineLogger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Aug 13, 2010
 * Time: 5:07:56 PM
 * parse the input file; assume the 1st 3 lines contain ensembl gene id, transcript id and protein id,
 * followed by ncbi gene id and rgd id
 */
public class EnsemblDataParser {

    EnsemblGeneSummary summary;
    PipelineLogger dbLogger = PipelineLogger.getInstance();
    private String version;

    /**
     * read input file in TSV format, break it into genes and return a list of ensembl genes
     * @param inputFile name of the input file
     * @return map of EnsemblGene objects keyed by ensembl gene id
     * @throws Exception
     */
    public Map<String, EnsemblGene> parseGene(String inputFile) throws Exception  {
        // the lines are sorted on gene-by-gene basis, so this makes the processing simple
        //
        // open the input file
        System.out.println(getVersion());
        dbLogger.log(getVersion()+": parsing gene file ", inputFile, PipelineLogger.INFO);

        BufferedReader reader = new BufferedReader(new FileReader(inputFile));

        // read all ensembl genes
        Map<String, EnsemblGene> genes = new HashMap<>(40003);
        EnsemblGene gene = null;
        String line;
        while( (line=reader.readLine())!=null ) {

            // split line into tokens, tab delimited
            String[] cols = line.split("\t", -1);
            if( cols.length<7 ) {
                throw new Exception("7+ columns expected, but found only "+cols.length+" in the file "+inputFile+"\n"+
                "  offending line: ["+line+"]");
            }

            String ensemblGeneId = cols[0];
            String geneId = cols[1];
            //String status = cols[2];

            String chromosome = cols[2];
            String startPos = cols[3];
            String stopPos = cols[4];
            String strand = cols[5];

            String geneBiotype = cols[6];

            String rgdId="";
            if( cols.length>7 ) {
                rgdId =  cols[7];

                // some rgd ids contain a suffix, f.e. "1307640-201" for gene Arsj
                // rgd id must be fixed to "1307640"
                if( rgdId.contains("-") ) {
                    rgdId = rgdId.substring(0, rgdId.indexOf('-'));
                }
            }

            // if gene boundary detected, finish it
            if( gene!=null && !gene.getEnsemblGeneId().equals(ensemblGeneId) ) {
                // ensembl gene id changed -- assume the ensembl gene is fully constructed
                // and add it to gene list
                genes.put(gene.getEnsemblGeneId(), gene);
                gene.setRecNo(genes.size()); // add a unique recno number
                gene = null;
            }

            // start new ensembl gene, if needed
            if( gene==null ) {
                gene = new EnsemblGene();
                gene.setEnsemblGeneId(ensemblGeneId);
            }

            // add symbol and status
            if( gene.getExternalGeneDb()==null || gene.getExternalGeneDb().isEmpty() )
                gene.setExternalGeneDb("NcbiGene");

            if( gene.getExternalSymbol()==null || gene.getExternalSymbol().isEmpty() )
                gene.setExternalSymbol(geneId);

            if( gene.getGeneBioType()==null || gene.getGeneBioType().isEmpty() )
                gene.setGeneBioType(geneBiotype);

            if( !rgdId.isEmpty() ) {
                int rgdID = Integer.parseInt(rgdId);
                if( !gene.getRgdIds().contains(rgdID) )
                    gene.getRgdIds().add(rgdID);
            }

            // parse genomic coords
            gene.setChromosome(chromosome);
            if( strand.equals("1") )
                gene.setStrand("+");
            else if( strand.equals("-1") )
                gene.setStrand("-");
            else
                gene.setStrand(strand);
            gene.setStartPos(Integer.parseInt(startPos));
            gene.setStopPos(Integer.parseInt(stopPos));
        }
        reader.close();

        // add last gene
        if( gene!=null ) {
            genes.put(gene.getEnsemblGeneId(), gene);
            gene.setRecNo(genes.size()); // add a unique recno number
        }

        dbLogger.log("Parsing complete for file ", inputFile, PipelineLogger.INFO);
        return genes;
    }

    /**
     * read input file in TSV format, break it into genes and import NCBI gene ids for ensembl genes
     * @param inputFile name of the input file
     * @return count of ensembl genes having NCBI gene ids
     * @throws Exception
     */
    public int parseGeneEg(String inputFile, Map<String, EnsemblGene> genes) throws Exception  {
        // the lines are sorted on gene-by-gene basis, so this makes the processing simple
        //
        // open the input file
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));

        // read all ensembl genes
        int egGeneCounter = 0; // count of ensembl genes having at least one NCBI gene id
        String line;
        while( (line=reader.readLine())!=null ) {

            // split line into tokens, tab delimited
            String[] cols = line.split("\t", -1);
            String ensemblGeneId = cols[0];
            String ncbiGeneId = cols[1];

            if( ncbiGeneId.isEmpty() ) {
                continue;
            }

            // get EnsemblGene object by ensembl gene id
            EnsemblGene gene = genes.get(ensemblGeneId);
            if( gene==null ) {
                System.out.println("unexpected: "+ensemblGeneId+" not found in the map");
                continue;
            }

            // add NCBI gene id
            List<String> egIds = gene.getNcbiGeneIds();
            if( !egIds.contains(ncbiGeneId) )
                egIds.add(ncbiGeneId);
            if( egIds.size()==1 )
                egGeneCounter++;
        }
        reader.close();

        return egGeneCounter;
    }

    public EnsemblGeneSummary getSummary() {
        return summary;
    }

    public void setSummary(EnsemblGeneSummary summary) {
        this.summary = summary;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
