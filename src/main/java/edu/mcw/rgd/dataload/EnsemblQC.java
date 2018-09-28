package edu.mcw.rgd.dataload;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Sep 3, 2010
 * Time: 10:33:50 AM
 * Quality Checking interface
 */
public interface EnsemblQC {

    /**
     * perform quality checking on given ensembl gene
     * @param genes list of EnsemblGene objects
     * @throws Exception
     */
    public abstract EnsemblGeneSummary checkAll(Map<String, EnsemblGene> genes) throws Exception;

    /**
     * perform quality checking on given ensembl gene
     * @param gene EnsemblGene object
     * @throws Exception
     */
    public abstract void check(EnsemblGene gene) throws Exception;

    public abstract void setSpeciesTypeKey(int speciesTypeKey);
}
