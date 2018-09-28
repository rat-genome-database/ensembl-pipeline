package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.FileExternalSort;
import edu.mcw.rgd.process.PipelineLogger;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Aug 13, 2010
 * Time: 10:15:33 AM
 * pulls the data from Ensembl website and stores them locally in data subfolder
 */
public class EnsemblDataPuller {

    String websiteUrl; // ensembl biomart url

    String biomartQueryTemplate; // xml config file containing the query template to be POST-ed to biomart
    List<String> biomartQueryAttrsGenes; // list of attributes to be loaded for main genes query
    List<String> biomartQueryAttrsRatGenes; // extra attributes to be loaded for rat genes
    List<String> biomartQueryAttrsMouseGenes; // extra attributes to be loaded for mouse genes
    List<String> biomartQueryAttrsHumanGenes; // extra attributes to be loaded for human genes
    List<String> biomartQueryAttrsNcbiGene; // list of attributes to load all NCBI gene ids for ensembl genes
    List<String> biomartQueryAttrsTranscripts; // list of attributes to load all ensembl transcripts

    int speciesTypeKey;

    PipelineLogger dbLogger = PipelineLogger.getInstance();
    private String version;

    /**
     * download data from Ensembl biomart
     * @return name of the local copy of sorted data file
     * @throws Exception
     */
    public String downloadGenesFile() throws Exception {

        // create a list with all attributes needed by biomart query
        // it consist of attributes shared for any species, and attrs specific to species
        List<String> attrs = new ArrayList<String>(this.getBiomartQueryAttrsGenes());
        if( speciesTypeKey==SpeciesType.RAT )
            attrs.addAll(this.getBiomartQueryAttrsRatGenes());
        else
        if( speciesTypeKey==SpeciesType.MOUSE )
            attrs.addAll(this.getBiomartQueryAttrsMouseGenes());
        else
        if( speciesTypeKey==SpeciesType.HUMAN )
            attrs.addAll(this.getBiomartQueryAttrsHumanGenes());

        // build biomart url
        // and download the file from biomart given the url
        String inputFile = downloadFile(attrs, "genes.txt");
		// now sort the file using external merge sort
		return sortFile(inputFile);
    }

    public String downloadGenesEgFile() throws Exception {
        // build biomart url and download the file from biomart given the url
        return downloadFile(this.getBiomartQueryAttrsNcbiGene(), "ncbigene.txt");
    }

    public String downloadTranscriptsFile() throws Exception {

        // build biomart url
        // and download the file from biomart given the url
        String inputFile = downloadFile(this.getBiomartQueryAttrsTranscripts(), "transcripts.txt");
		// now sort the file using external merge sort
		return sortFile(inputFile);
    }

    // return the name of output file
    String downloadFile(List<String> attributes, String outFile) throws Exception {

        String data = buildBiomartQuery(attributes);

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(websiteUrl + "?" + data);
        downloader.setLocalFile("data/" + SpeciesType.getCommonName(speciesTypeKey).toLowerCase() + "_" + outFile);
        downloader.setPrependDateStamp(true); // every downloaded file will have the current date in the name
        String outPath = downloader.download();

        dbLogger.log("Downloaded file "+outPath, PipelineLogger.INFO);
        return outPath;
    }

    // return url encoded biomart xml query
    String buildBiomartQuery(List<String> attributes) throws Exception {

        // load the biomart config file and remove all new lines
        BufferedReader fileReader = new BufferedReader(new FileReader(this.getBiomartQueryTemplate()));
        String line;
        StringBuilder buf = new StringBuilder();
        while( (line=fileReader.readLine())!=null ) {
            line = line.trim();

            // replace any line with '#SPECIES#' tag
            if( line.contains("#SPECIES#") ) {
                // from species taxonomic name take first letter of 1st word and entire second word
                // f.e. 'Homo sapiens' --> 'hsapiens'
                String[] taxoNameWords = SpeciesType.getTaxonomicName(speciesTypeKey).split(" ");
                String taxoName = taxoNameWords[0].substring(0, 1).toLowerCase() + taxoNameWords[1];
                line = line.replaceAll("\\#SPECIES\\#", taxoName);
            }
            buf.append(line);

            // if we are processing <Attributes> line, add all custom attributes as well
            if( line.contains("Attribute") ) {
                int quoteStart = line.indexOf('"');
                int quoteStop = line.lastIndexOf('"');
                String prefix = line.substring(0, quoteStart+1);
                String suffix = line.substring(quoteStop);
                for( String attr: attributes ) {
                    buf.append(prefix).append(attr).append(suffix);
                }
            }
        }
        fileReader.close();

        return "query="+URLEncoder.encode(buf.toString(), "UTF-8");
    }

    String sortFile(String inputFile) throws IOException {
        // now sort the file using external merge sort
        String outputFile = inputFile + ".sorted";
        Comparator<String> comparator = new Comparator<String>() {
            public int compare(String r1, String r2){
                return r1.compareTo(r2);
            }
        };
        File inFile = new File(inputFile);
        List<File> l = FileExternalSort.sortInBatch(inFile, comparator) ;
        FileExternalSort.mergeSortedFiles(l, new File(outputFile), comparator, false);

        // delete the input file after the sorted file is available
        inFile.delete();

        return outputFile;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getBiomartQueryTemplate() {
        return biomartQueryTemplate;
    }

    public void setBiomartQueryTemplate(String biomartQueryTemplate) {
        this.biomartQueryTemplate = biomartQueryTemplate;
    }

    public List<String> getBiomartQueryAttrsGenes() {
        return biomartQueryAttrsGenes;
    }

    public void setBiomartQueryAttrsGenes(List<String> biomartQueryAttrsGenes) {
        this.biomartQueryAttrsGenes = biomartQueryAttrsGenes;
    }

    public List<String> getBiomartQueryAttrsNcbiGene() {
        return biomartQueryAttrsNcbiGene;
    }

    public void setBiomartQueryAttrsNcbiGene(List<String> biomartQueryAttrsNcbiGene) {
        this.biomartQueryAttrsNcbiGene = biomartQueryAttrsNcbiGene;
    }

    public List<String> getBiomartQueryAttrsTranscripts() {
        return biomartQueryAttrsTranscripts;
    }

    public void setBiomartQueryAttrsTranscripts(List<String> biomartQueryAttrsTranscripts) {
        this.biomartQueryAttrsTranscripts = biomartQueryAttrsTranscripts;
    }

    public int getSpeciesTypeKey() {
        return speciesTypeKey;
    }

    public void setSpeciesTypeKey(int speciesTypeKey) {
        this.speciesTypeKey = speciesTypeKey;
    }

    public List<String> getBiomartQueryAttrsRatGenes() {
        return biomartQueryAttrsRatGenes;
    }

    public void setBiomartQueryAttrsRatGenes(List<String> biomartQueryAttrsRatGenes) {
        this.biomartQueryAttrsRatGenes = biomartQueryAttrsRatGenes;
    }

    public List<String> getBiomartQueryAttrsMouseGenes() {
        return biomartQueryAttrsMouseGenes;
    }

    public void setBiomartQueryAttrsMouseGenes(List<String> biomartQueryAttrsMouseGenes) {
        this.biomartQueryAttrsMouseGenes = biomartQueryAttrsMouseGenes;
    }

    public List<String> getBiomartQueryAttrsHumanGenes() {
        return biomartQueryAttrsHumanGenes;
    }

    public void setBiomartQueryAttrsHumanGenes(List<String> biomartQueryAttrsHumanGenes) {
        this.biomartQueryAttrsHumanGenes = biomartQueryAttrsHumanGenes;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
