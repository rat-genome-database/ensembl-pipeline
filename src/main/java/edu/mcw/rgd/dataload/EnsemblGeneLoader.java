package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.process.CounterPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author mtutaj
 * @since 9/16/11
 */
public class EnsemblGeneLoader {

    int speciesTypeKey;
    EnsemblDAO dao;
    CounterPool counters;

    public EnsemblGeneLoader() throws Exception {
        dao = new EnsemblDAO();
    }

    public void run(Collection<EnsemblGene> genes, CounterPool counters) throws Exception {

        this.counters = counters;

        for( EnsemblGene gene: genes ) {
            run(gene);
        }
    }

    void run(EnsemblGene rec) throws Exception {

        //System.out.println("DL> "+rec.getRecNo()+" QC flags count ="+rec.getQcFlags().size()+", "+rec.getEnsemblGeneId());

        if( false ) { // turn off the real data loading -- entire pipeline is run in simulation mode

            if( rec.isFlagSet("LOAD_NEW_GENE") ) {
                loadNewGene(rec);
            }
            else {
                // these flags must be present: LOAD_WITH_WARNINGS, LOAD_MULTI_MATCH, LOAD_EXACT_MATCH
                if( rec.isFlagSet("LOAD_WITH_WARNINGS") ||
                    rec.isFlagSet("LOAD_MULTI_MATCH") ||
                    rec.isFlagSet("LOAD_EXACT_MATCH") ) {

                    updateGene(rec);
                }
            }
        }
    }

    void loadNewGene(EnsemblGene gene) throws Exception {

        // insert new gene type, if needed
        String geneTypeLc = gene.getGeneBioType().toLowerCase();
        if( !dao.existsGeneType(geneTypeLc) )
            dao.createGeneType(geneTypeLc);

        // create a new rgd id
        RgdId newRgdId = dao.createRgdId(RgdId.OBJECT_KEY_GENES, getSpeciesTypeKey());
        int rgdId = newRgdId.getRgdId();

        // insert genes table
        Gene newGene = new Gene();
        newGene.setSymbol(gene.getEnsemblGeneId());
        newGene.setName(newGene.getSymbol());
        newGene.setRgdId(rgdId);
        newGene.setType(geneTypeLc);
        dao.insertGene(newGene);

        // insert alias
        if( gene.getExternalSymbol()!=null && gene.getExternalSymbol().trim().length()>0 ) {
            Alias alias = new Alias();
            alias.setRgdId(rgdId);
            alias.setNotes("created by Ensembl pipeline");
            alias.setTypeName("alternate_symbol");
            alias.setValue(gene.getExternalSymbol());
        }

        // prepare MapData record from Ensembl
        MapData mdEnsembl = new MapData();
        mdEnsembl.setSrcPipeline("Ensembl");
        mdEnsembl.setChromosome(gene.getChromosome());
        mdEnsembl.setMapKey(dao.getPrimaryMapKey(speciesTypeKey));
        mdEnsembl.setRgdId(rgdId);
        mdEnsembl.setStartPos(gene.getStartPos());
        mdEnsembl.setStopPos(gene.getStopPos());
        mdEnsembl.setStrand(gene.getStrand());
        dao.insertMapData(mdEnsembl);

        // prepare xdb ids
        // incoming xdb ids consist of ensembl gene id and NCBI gene ids
        List<XdbId> ensemblXdbIds = new ArrayList<XdbId>();

        XdbId xdbId = new XdbId();
        xdbId.setRgdId(rgdId);
        xdbId.setSrcPipeline("Ensembl");
        xdbId.setAccId(gene.getEnsemblGeneId());
        xdbId.setXdbKey(XdbId.XDB_KEY_ENSEMBL_GENES);
        ensemblXdbIds.add(xdbId);

        for( String accId: gene.getNcbiGeneIds() ) {

            xdbId = new XdbId();
            xdbId.setRgdId(rgdId);
            xdbId.setSrcPipeline("Ensembl");
            xdbId.setAccId(accId);
            xdbId.setXdbKey(XdbId.XDB_KEY_NCBI_GENE);
            ensemblXdbIds.add(xdbId);
        }
        dao.insertXdbIds(ensemblXdbIds);
    }

    void updateGene(EnsemblGene gene) throws Exception {

        if( gene.mdForInsert != null ) {
            dao.insertMapData(gene.mdForInsert);
        }

        if( gene.mdForUpdate != null ) {
            dao.updateMapData(gene.mdForUpdate);
        }

        if( gene.mdsForDelete != null ) {
            dao.deleteMapData(gene.mdsForDelete);
        }

        if( gene.aliasForInsert != null ) {
            dao.insertAlias(gene.aliasForInsert);
        }

        if( gene.xdbIdsForInsert != null ) {
            dao.insertXdbIds(gene.xdbIdsForInsert);
        }

        if( gene.xdbIdsForDelete != null ) {
            dao.deleteXdbIds(gene.xdbIdsForDelete);
        }
    }

    public int getSpeciesTypeKey() {
        return speciesTypeKey;
    }

    public void setSpeciesTypeKey(int speciesTypeKey) {
        this.speciesTypeKey = speciesTypeKey;
    }
}
