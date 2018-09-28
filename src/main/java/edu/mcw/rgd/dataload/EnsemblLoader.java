package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.pipelines.PipelineManager;
import edu.mcw.rgd.process.PipelineLogFlagManager;
import edu.mcw.rgd.process.PipelineLogger;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;


/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Aug 13, 2010
 * Time: 10:07:53 AM
 */
public class EnsemblLoader {

    EnsemblDAO ensemblDAO;
    PipelineLogger dbLogger = PipelineLogger.getInstance();
    PipelineLogFlagManager dbFlagManager = new PipelineLogFlagManager(dbLogger);

    EnsemblPipelinePreprocessor pipelinePreprocessor;
    EnsemblQualityChecker2 dataQC;
    EnsemblGeneLoader dataLoader;
    private String version;
    private Log log = LogFactory.getLog("status");

    /**
     * starts the pipeline; properties are read from properties/AppConfigure.xml file
     * @param args cmd line arguments, like species
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf= new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        EnsemblLoader loader=(EnsemblLoader) (bf.getBean("loader"));
        loader.ensemblDAO = new EnsemblDAO();

        // parse cmd line params
        if( args.length<2 ) {
            usage();
            return;
        }
        int speciesTypeKey = -1;
        if( args[0].equals("-species") ) {
            speciesTypeKey = SpeciesType.parse(args[1]);
        }

        // if species type key is all, run for all species
        if( speciesTypeKey<0 ) {
            throw new Exception("Aborted: please specify the species in cmd line");
        }
        if( speciesTypeKey==SpeciesType.ALL ) {
            loader.run(SpeciesType.RAT);
            loader.run(SpeciesType.MOUSE);
            loader.run(SpeciesType.HUMAN);
        }
        else {
            loader.run(speciesTypeKey);
        }
    }

    /**
     * run the Ensembl pipeline in download+process mode;
     * <ol>
     *     <li>download genes data from Ensembl biomart and store it locally in data folder</li>
     *     <li>download file with NcbiGene ids mapped to Ensembl ids</li>
     * </ol>
     * @param speciesTypeKey species type key
     * @throws Exception
     */
    public void run(int speciesTypeKey) throws Exception {

        log.info(SpeciesType.getCommonName(speciesTypeKey)+" " +getVersion());

        dbLogger.init(speciesTypeKey, "download+process", "Ensembl");

        // create a new pipeline framework
        PipelineManager manager = new PipelineManager();

        // configure pipeline preprocessor, responsible for creating a stream of EnsemblGene records
        pipelinePreprocessor.setSpeciesTypeKey(speciesTypeKey);
        manager.addPipelineWorkgroup(pipelinePreprocessor, "PP", 1, 0);

        // configure a pool of QC threads
        dataQC.setSpeciesTypeKey(speciesTypeKey);
        dataQC.setDbFlagManager(dbFlagManager);
        dataQC.init();
        manager.addPipelineWorkgroup(dataQC, "QC", 6, 0);

        // configure one loader
        dataLoader.setSpeciesTypeKey(speciesTypeKey);
        manager.addPipelineWorkgroup(dataLoader, "DL", 1, 0);

        try {

            // run the pipeline
            manager.run();

            //geneSummary.dumpSummary(log);

            // dump counter statistics
            for( String counter: manager.getSession().getCounters() ) {
                int count = manager.getSession().getCounterValue(counter);
                if( count>0 ) {
                    //System.out.println(counter+": "+count);
                    dbLogger.log(counter, Integer.toString(count), PipelineLogger.TOTAL);
                }
            }

            manager.dumpCounters(log);

            dbLogger.getPipelineLog().setSuccess("OK");
            dbLogger.close(true);
        }
        catch(Exception e) {
            e.printStackTrace();
            dbLogger.getPipelineLog().setSuccess(e.getMessage());
            dbLogger.close(false);

            // rethrow the exception
            throw e;
        }

        log.info("OK");
    }

    /**
     * print to stdout the information about command line parameters
     */
    static public void usage() {
        System.out.println("Command line parameters required:");
        System.out.println(" -species 0|1|2|3|Rat|Mouse|Human|All");
    }

    public EnsemblQualityChecker2 getDataQC() {
        return dataQC;
    }

    public void setDataQC(EnsemblQualityChecker2 dataQC) {
        this.dataQC = dataQC;
    }

    public EnsemblPipelinePreprocessor getPipelinePreprocessor() {
        return pipelinePreprocessor;
    }

    public void setPipelinePreprocessor(EnsemblPipelinePreprocessor pipelinePreprocessor) {
        this.pipelinePreprocessor = pipelinePreprocessor;
    }

    public EnsemblGeneLoader getDataLoader() {
        return dataLoader;
    }

    public void setDataLoader(EnsemblGeneLoader geneLoader) {
        this.dataLoader = geneLoader;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
