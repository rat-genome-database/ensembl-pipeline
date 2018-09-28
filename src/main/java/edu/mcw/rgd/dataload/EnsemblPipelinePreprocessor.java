package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.PipelineLog;
import edu.mcw.rgd.pipelines.RecordPreprocessor;
import edu.mcw.rgd.process.PipelineLogger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mtutaj
 * Date: 9/16/11 <br>
 * <p>
 * downloads ensembl files from ensembl biomart, parses them and breaks them into EnsemblGene objects
 */
public class EnsemblPipelinePreprocessor extends RecordPreprocessor {

    EnsemblDataPuller dataPuller;
    EnsemblDataParser dataParser;
    int speciesTypeKey;
    PipelineLogger dbLogger = PipelineLogger.getInstance();

    @Override
    public void process() throws Exception {

        // download genes data from Ensembl biomart and store it locally in data folder
        dataPuller.setSpeciesTypeKey(speciesTypeKey);
        String dataFile = dataPuller.downloadGenesFile();
        //String dataFile = "data/genes.txt.sorted";

        // parse the file; map key is 'ensembl gene id'
        Map<String, EnsemblGene> genes = dataParser.parseGene(dataFile);

        // download and parse entrezgene data
        dataFile = dataPuller.downloadGenesEgFile();
        int genesWithEg = dataParser.parseGeneEg(dataFile, genes);

        searchForMultiEnsemblGeneIdsMappedToOneRgdId(genes.values());

        // download and parse ensembl transcripts
        //dataFile = dataPuller.downloadTranscriptsFile();
        // int genesWithTranscript = dataParser.parseTranscripts(dataFile, genes);

        dbLogger.log(null, Integer.toString(genes.size()), PipelineLog.LOGPROP_RECCOUNT);

        // process all ensembl genes
        for( EnsemblGene ensemblGene: genes.values() ) {
            getSession().putRecordToFirstQueue(ensemblGene);
        }
    }

    int searchForMultiEnsemblGeneIdsMappedToOneRgdId(Collection<EnsemblGene> genes) throws Exception {

        // map of rgd ids to a list of ensembl gene ids (string comma separated)
        Map<Integer, String> multiMap = new HashMap<Integer, String>(4*genes.size()/3);
        for( EnsemblGene gene: genes ) {

            for( Integer rgdId: gene.getRgdIds() ) {

                String ensemblGeneIds = multiMap.get(rgdId);
                if( ensemblGeneIds==null )
                    ensemblGeneIds = gene.getEnsemblGeneId();
                else
                    ensemblGeneIds += ","+gene.getEnsemblGeneId();
                multiMap.put(rgdId, ensemblGeneIds);
            }
        }

        // dump rgd ids assigned to multiple ensembl gene ids into the log file
        Log log = LogFactory.getLog("multiEnsemblOneRgd");
        Integer hitCount = 0;

        for( Map.Entry<Integer, String> entry: multiMap.entrySet() ) {

            String ensemblGeneIds = entry.getValue();
            if( ensemblGeneIds.indexOf(',')>0 ) {
                // we have a hit
                hitCount++;
                log.info(entry.getKey()+" ==> "+ensemblGeneIds);
            }
        }
        // store total in db log
        dbLogger.log("count of multiple ensembl gene ids mapped to a single rgd id", hitCount.toString(), PipelineLog.LOGPROP_TOTAL);

        return hitCount;
    }


    public EnsemblDataPuller getDataPuller() {
        return dataPuller;
    }

    public void setDataPuller(EnsemblDataPuller dataPuller) {
        this.dataPuller = dataPuller;
        System.out.println(dataPuller.getVersion());
    }

    public EnsemblDataParser getDataParser() {
        return dataParser;
    }

    public void setDataParser(EnsemblDataParser dataParser) {
        this.dataParser = dataParser;
    }

    public int getSpeciesTypeKey() {
        return speciesTypeKey;
    }

    public void setSpeciesTypeKey(int speciesTypeKey) {
        this.speciesTypeKey = speciesTypeKey;
    }
}
