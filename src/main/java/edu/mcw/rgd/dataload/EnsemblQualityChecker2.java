package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.pipelines.PipelineRecord;
import edu.mcw.rgd.pipelines.RecordProcessor;
import edu.mcw.rgd.process.PipelineLogFlagManager;
import edu.mcw.rgd.process.PipelineLogger;
import edu.mcw.rgd.process.Utils;
import edu.mcw.rgd.reporting.Link;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * @author mtutaj
 * @since Sep 3, 2010
 * checks ensembl genes only against RGD; only Ensembl Gene ID is matched
 */
public class EnsemblQualityChecker2 extends RecordProcessor {

    Log log;
    PipelineLogger dbLogger = PipelineLogger.getInstance();
    PipelineLogFlagManager dbFlagManager;
    int speciesTypeKey;

    EnsemblDAO dao;

    static EnsemblGeneSummary summary = new EnsemblGeneSummary();
    private String version;

    public EnsemblQualityChecker2() throws Exception {
        dao = new EnsemblDAO();
    }

    public void init() throws Exception {
        // register all db log flags used by QC module
        registerDbFlags();
    }

    @Override
    public void process(PipelineRecord pipelineRecord) throws Exception {

        EnsemblGene gene = (EnsemblGene) pipelineRecord;

        // run checks through the gene
        checkByEnsemblGeneId(gene);
        checkByNcbiGeneIds(gene);
        checkByRgdIds(gene);
        checkBySymbol(gene);
        checkByGenomicPosition(gene);

        // temporarily disabled - there will be no gene loading in nearest future
        //checkForLoading(gene);

        checkConflictBin1(gene);

        // generate XML record for this gene and write it and its QC flags into database
        String xml = gene.toXml();
        dbLogger.addLogProp(null, null, gene.getRecNo(), PipelineLogger.REC_XML, xml);
        // write log props to database
        dbLogger.writeLogProps(gene.getRecNo());
        // remove the log props from log in memory
        dbLogger.removeAllLogProps(gene.getRecNo());

        gene.setQcFlags(dbFlagManager.writeFlags(gene.getRecNo()));
        //System.out.println("QC> "+gene.getRecNo()+" QC flags count ="+gene.getQcFlags().size()+", "+gene.getEnsemblGeneId());

        // store xml gene in the log file
        if( gene.isFlagSet("LOAD_NEW_GENE") ) {
            log.info(xml);
        }

        // check incoming data against RGD
        prepareUpdate(gene);
    }

    void checkConflictBin1( EnsemblGene gene ) throws Exception {

        if( (gene.rgdIds.size()>1 && gene.activeRgdIdsMatchingByRgdId.size()> 1)
         || (gene.ncbiGeneIds.size()>1 && gene.activeRgdIdsMatchingByNcbiId.size()> 1) ) {

            // we have more than one incoming rgd id, each of which matches an active gene rgd id
            Collection<Integer> rgdIds = new HashSet<Integer>(gene.activeRgdIdsMatchingByRgdId);
            rgdIds.addAll(gene.activeRgdIdsMatchingByNcbiId);

            generateConflictBin1(gene, rgdIds, gene.rgdIds, gene.ncbiGeneIds);
        }
    }

    void generateConflictBin1( EnsemblGene gene, Collection<Integer> rgdIds, Collection<Integer> incomingRgdIds, Collection<String> egIds ) throws Exception {

        if( dbFlagManager.isFlagSet("CONFLICT_BIN1", gene.getRecNo()) ) {
            // the gene was already flagged
            return;
        }

        // build html for storing data for this conflict
        StringBuilder buf = new StringBuilder("<table class='conflict_bin1'>");
        buf.append("<tr><th>Ensembl Gene Id:</th><th>").append(gene.getEnsemblGeneId()).append("</th></tr>\n");
        buf.append("<tr><td>Ensembl Symbol:</td><td>").append(gene.getExternalSymbol()).append("</td></tr>\n");
        buf.append("<tr><td>Gene BioType:</td><td>").append(gene.getGeneBioType()).append("</td></tr>\n");
        buf.append("<tr><td>Position:</td><td>chr").append(gene.getChromosome()).append(":")
                .append(gene.getStartPos()).append("..").append(gene.getStopPos())
                .append(" (").append(gene.getStrand()).append(")</td></tr>\n");

        buf.append("<tr><td>Incoming RGD Ids:</td><td>");
        for( Integer rgdId: incomingRgdIds ) {
            buf.append("<a href=\"").append(Link.gene(rgdId)).append("\">").append(rgdId).append("</a> &nbsp; ");
        }
        buf.append("</td></tr>\n");

        buf.append("<tr><td>Gene Ids:</td><td>");
        for( String egId: egIds ) {
            buf.append("<a href=\"https://www.ncbi.nlm.nih.gov/gene/").append(egId).append("\">").append(egId).append("</a> &nbsp; ");
        }
        buf.append("</td></tr>\n");

        buf.append("<tr><td>Matching RGD IDs</td><td>");
        buf.append("<table>");

        int mappedToRefAssembly = 0;
        for( Integer rgdId: rgdIds ) {
            // see how many genes do have positions on reference assembly
            List<MapData> mds = dao.getAllMapData(rgdId, this.speciesTypeKey);
            int mdCount = 0;
            for( MapData md: mds ) {
                buf.append("<tr><td>");
                if( mdCount++ == 0 )
                    buf.append(rgdId);
                buf.append("</td><td>");
                buf.append(dao.getMapName(md.getMapKey()));
                buf.append("</td><td>");
                buf.append(md.toString());
                buf.append("</td></tr>\n");

                if( md.getMapKey()==dao.getPrimaryMapKey(this.speciesTypeKey) ) {
                    mappedToRefAssembly++;
                }
            }
        }
        buf.append("</table></td></tr>\n");
        buf.append("</table>\n");

        // if there is only one RGD ID mapped to reference assembly, and other RGD ids to others,
        // we have our condition satisfied
        if( mappedToRefAssembly==1 ) {
            dbLogger.addLogProp("CONFLICT_BIN1", buf.toString(), gene.getRecNo(), PipelineLog.LOGPROP_WARNMESSAGE);
            dbFlagManager.setFlag("CONFLICT_BIN1", gene.getRecNo());
            getSession().incrementCounter("CONFLICT_BIN1", 1);
        }
    }

    void prepareUpdate(EnsemblGene gene) throws Exception {

        // if a gene is to be updated, its matching rgd id must be known already
        if( gene.getMatchingRgdId()<=0 ) {
            return;
        }

        prepareMapPositions(gene);
        prepareAliases(gene);
        prepareXdbIds(gene);
    }

    void prepareMapPositions(EnsemblGene gene) throws Exception {

        // check gene position against RGD
        if( gene.isFlagSet("GENEPOS_NO_POS") ) {
            // ensembl gene does not have genomic position available, no position is to be updated
            getSession().incrementCounter("ENSEMBL_GENE_WITHOUT_POSITION", 1);
            return;
        }

        // prepare MapData record from Ensembl
        MapData mdEnsembl = new MapData();
        mdEnsembl.setSrcPipeline("Ensembl");
        mdEnsembl.setChromosome(gene.getChromosome());
        mdEnsembl.setMapKey(dao.getPrimaryMapKey(speciesTypeKey));
        mdEnsembl.setRgdId(gene.getMatchingRgdId());
        mdEnsembl.setStartPos(gene.getStartPos());
        mdEnsembl.setStopPos(gene.getStopPos());
        mdEnsembl.setStrand(gene.getStrand());

        // load positions from rgd -- those once loaded via Ensembl pipeline
        List<MapData> mapDataInRgd = dao.getGenePosition(gene.getMatchingRgdId(), speciesTypeKey);

        // if there is nothing in RGD, add the new ensemble map positions
        if( mapDataInRgd.isEmpty() ) {
            gene.mdForInsert = mdEnsembl;
            getSession().incrementCounter("INSERT_MAPDATA_FOR_GENE", 1);
            return;
        }

        // expect only one map position
        if( mapDataInRgd.size()>1 ) {
            throw new Exception("Gene "+gene.getEnsemblGeneId()+" has multiple positions -- unhandled");
        }

        // there are some map positions in rgd; remove map positions with exact match
        MapData mdInRgd = mapDataInRgd.get(0);
        if( mdInRgd.equalsByGenomicCoords(mdEnsembl) ) {
            getSession().incrementCounter("MATCHING_MAPDATA_FOR_GENE", 1);
            return;
        }

        // map data not matching -- update it
        mdInRgd.setStartPos(mdEnsembl.getStartPos());
        mdInRgd.setStopPos(mdEnsembl.getStopPos());
        mdInRgd.setStrand(mdEnsembl.getStrand());
        gene.mdForUpdate = mdInRgd;

        getSession().incrementCounter("UPDATE_MAPDATA_FOR_GENE", 1);

        // getSession().incrementCounter("DELETE_MAPDATA_FOR_GENE", mapDataInRgd.size());
    }

    void prepareAliases(EnsemblGene gene) throws Exception {

        // ensure that symbol for ensembl gene is in RGD -- either as a gene symbol, or alias
        String ensemblSymbol = gene.getExternalSymbol();
        if( ensemblSymbol==null || ensemblSymbol.trim().isEmpty() ) {

            getSession().incrementCounter("ENSEMBL_GENE_WITHOUT_SYMBOL", 1);
            return;
        }

        // try to match gene symbol
        Gene rgdGene = dao.getGene(gene.getMatchingRgdId());
        if( rgdGene.getSymbol()!=null && rgdGene.getSymbol().equalsIgnoreCase(ensemblSymbol) ) {

            getSession().incrementCounter("ENSEMBL_SYMBOL_MATCHES_RGD_GENE_SYMBOL", 1);
            return;
        }

        // gene symbol is different between rgd and ensembl -- try aliases
        List<Alias> aliases = dao.getAliases(gene.getMatchingRgdId());
        for( Alias alias: aliases ) {
            if( alias.getValue().equalsIgnoreCase(ensemblSymbol) ) {

                getSession().incrementCounter("ENSEMBL_SYMBOL_MATCHES_RGD_GENE_ALIAS", 1);
                return;
            }
        }

        // no match by gene symbol or alias -- add a new alias
        Alias alias = new Alias();
        alias.setRgdId(gene.getMatchingRgdId());
        alias.setNotes("created by Ensembl pipeline");
        alias.setTypeName("alternate_symbol");
        alias.setValue(ensemblSymbol);
        gene.aliasForInsert = alias;

        getSession().incrementCounter("ENSEMBL_SYMBOL_INSERTED", 1);
    }

    void prepareXdbIds(EnsemblGene gene) throws Exception {

        // incoming xdb ids consist of ensembl gene id and NCBI gene ids
        List<XdbId> ensemblXdbIds = new ArrayList<XdbId>();

        XdbId xdbId = new XdbId();
        xdbId.setRgdId(gene.getMatchingRgdId());
        xdbId.setSrcPipeline("Ensembl");
        xdbId.setAccId(gene.getEnsemblGeneId());
        xdbId.setXdbKey(XdbId.XDB_KEY_ENSEMBL_GENES);
        ensemblXdbIds.add(xdbId);

        for( String accId: gene.getNcbiGeneIds() ) {

            xdbId = new XdbId();
            xdbId.setRgdId(gene.getMatchingRgdId());
            xdbId.setSrcPipeline("Ensembl");
            xdbId.setAccId(accId);
            xdbId.setXdbKey(XdbId.XDB_KEY_NCBI_GENE);
            ensemblXdbIds.add(xdbId);
        }

        // load xdb ids for ensembl pipeline
        List<XdbId> rgdXdbIds = dao.getXdbIds(gene.getMatchingRgdId());

        // remove from xdb ids in rgd those that are already in incoming data from ensembl
        List<XdbId> sharedXdbIds = new ArrayList<XdbId>(rgdXdbIds);
        sharedXdbIds.retainAll(ensemblXdbIds);
        getSession().incrementCounter("MATCHING_XDB_IDS", sharedXdbIds.size());

        rgdXdbIds.removeAll(sharedXdbIds);
        gene.xdbIdsForDelete = rgdXdbIds;
        getSession().incrementCounter("DELETED_XDB_IDS", rgdXdbIds.size());

        ensemblXdbIds.removeAll(sharedXdbIds);
        gene.xdbIdsForInsert = ensemblXdbIds;
        getSession().incrementCounter("INSERTED_XDB_IDS", ensemblXdbIds.size());
    }


    private void intersect( Set<Integer> resultSet, Collection<Integer> set ) {

        // only non-empty set can be intersected with result set
        if( set.isEmpty() )
            return;

        // if the result set is empty, copy the set to the result set
        if( resultSet.isEmpty() ) {
            resultSet.addAll(set);
            return;
        }

        // both result set and current set are non empty -- perform intersect on result set
        resultSet.retainAll(set);
    }

    void checkByEnsemblGeneId( EnsemblGene gene ) throws Exception {

        // match ensembl gene against rgd
        List<Integer> rgdIds = dao.getXdbIds(XdbId.XDB_KEY_ENSEMBL_GENES, gene.getEnsemblGeneId(), speciesTypeKey);
        // no match with rgd
        if( rgdIds.isEmpty() ) {
            dbFlagManager.setFlag("ENSEMBL_GENEID_NO_MATCH", gene.getRecNo());
            getSession().incrementCounter("ENSEMBL_GENEID_NO_MATCH", 1);
            return;
        }

        gene.activeRgdIdsMatchingByEnsemblId = analyzeMatchingRgdIds(rgdIds, gene.getRgdIdsMatchingByEnsemblGeneId(), gene.getRecNo(), "ENSEMBL_GENEID");
    }

    void checkByNcbiGeneIds( EnsemblGene gene ) throws Exception {

        // check if there is at least one incoming NCBI gene id
        if( gene.getNcbiGeneIds().isEmpty() ) {
            dbFlagManager.setFlag("NCBIGENE_MISSING", gene.getRecNo());
            getSession().incrementCounter("NCBIGENE_MISSING", 1);
            return; // there are no incoming EG IDs present
        }

        // retrieve from db a list of RGD ids connected with NCBI gene ids
        Set<Integer> rgdIds = new HashSet<Integer>();
        for( String egId: gene.getNcbiGeneIds() ) {
            List<Integer> rgdIds0 = dao.getXdbIds(XdbId.XDB_KEY_NCBI_GENE, egId, speciesTypeKey);
            if( rgdIds0.size()> 0 ) {
                rgdIds.addAll(rgdIds0);
            }
        }

        // determine flag
        if( rgdIds.isEmpty() ) { // none of EG ids have a matching RGD_ID!
            dbFlagManager.setFlag("NCBIGENE_NO_MATCH", gene.getRecNo());
            getSession().incrementCounter("NCBIGENE_NO_MATCH", 1);
            return;
        }

        // analyze matching rgd ids: whether they are active/inactive, and assign flags
        gene.activeRgdIdsMatchingByNcbiId = analyzeMatchingRgdIds(rgdIds, gene.getRgdIdsMatchingByNcbiGeneId(), gene.getRecNo(), "NCBIGENE");
    }

    void checkByRgdIds( EnsemblGene gene ) throws Exception {

        // check if there is at least one incoming rgd id
        List<Integer> rgdIds = gene.getRgdIds();
        if( rgdIds.isEmpty() ) {
            dbFlagManager.setFlag("RGDIDS_MISSING", gene.getRecNo());
            getSession().incrementCounter("RGDIDS_MISSING", 1);
            return; // there are no incoming EG IDs present
        }

        // analyze matching rgd ids: whether they are active/inactive, and assign flags
        gene.activeRgdIdsMatchingByRgdId = analyzeMatchingRgdIds(rgdIds, gene.getRgdIdsMatchingByRgdId(), gene.getRecNo(), "RGDIDS");
    }

    void checkBySymbol( EnsemblGene gene ) throws Exception {

        // first check if any symbol has been read
        String symbol = gene.getExternalSymbol();
        if( symbol==null || symbol.trim().length()==0 ) {
            dbFlagManager.setFlag("SYMBOL_MISSING", gene.getRecNo());
            getSession().incrementCounter("SYMBOL_MISSING", 1);
            return;
        }

        // match symbol against gene symbol and gene alias
        Collection<Integer> geneRgdIds = dao.getGenesBySymbol(symbol, getSpeciesTypeKey());

        // if there is a match by gene symbol, analyze it
        gene.activeRgdIdsMatchingBySymbolId = analyzeMatchingRgdIds(geneRgdIds, gene.getRgdIdsMatchingBySymbol(), gene.getRecNo(), "SYMBOL");
    }

    void checkByGenomicPosition(EnsemblGene gene) throws Exception {

        // the gene positions must be non-zero
        if( !(gene.getStartPos()>0 && gene.getStartPos() < gene.getStopPos()) ) {
            dbFlagManager.setFlag("GENEPOS_NO_POS", gene.getRecNo());
            getSession().incrementCounter("GENEPOS_NO_POS", 1);
            return;
        }

        // try to do the exact matching
        List<Integer> geneRgdIds = dao.getGenesByCoords(gene.getChromosome(), gene.getStartPos(), gene.getStopPos(), getSpeciesTypeKey());
        // if no match, set a flag
        if( geneRgdIds.isEmpty() ) {
            // try to do partial matching
            geneRgdIds = dao.getGenesByCoordsPartial(gene.getChromosome(), gene.getStartPos(), gene.getStopPos(), getSpeciesTypeKey());
            if( geneRgdIds.isEmpty() ) {
                dbFlagManager.setFlag("GENEPOS_NO_MATCH", gene.getRecNo());
                getSession().incrementCounter("GENEPOS_NO_MATCH", 1);
                return;
            }

            dbFlagManager.setFlag("GENEPOS_PARTIAL_MATCH_MIN1BP", gene.getRecNo());
            getSession().incrementCounter("GENEPOS_PARTIAL_MATCH_MIN1BP", 1);
            gene.setPartialPosMatch(true);

            List<Integer> geneRgdIds1 = geneRgdIds;
            if( !geneRgdIds1.isEmpty() ) {
                geneRgdIds1 = dao.getGenesByCoordsPartial(gene.getChromosome(), gene.getStartPos(), gene.getStopPos(), 20, getSpeciesTypeKey());
                if (!geneRgdIds1.isEmpty()) {
                    dbFlagManager.setFlag("GENEPOS_PARTIAL_MATCH_MIN20BP", gene.getRecNo());
                    getSession().incrementCounter("GENEPOS_PARTIAL_MATCH_MIN20BP", 1);
                }
            }

            if( !geneRgdIds1.isEmpty() ) {
                geneRgdIds1 = dao.getGenesByCoordsPartial(gene.getChromosome(), gene.getStartPos(), gene.getStopPos(), 50, getSpeciesTypeKey());
                if (!geneRgdIds1.isEmpty()) {
                    dbFlagManager.setFlag("GENEPOS_PARTIAL_MATCH_MIN50BP", gene.getRecNo());
                    getSession().incrementCounter("GENEPOS_PARTIAL_MATCH_MIN50BP", 1);
                }
            }

            if( !geneRgdIds1.isEmpty() ) {
                geneRgdIds1 = dao.getGenesByCoordsPartial(gene.getChromosome(), gene.getStartPos(), gene.getStopPos(), 100, getSpeciesTypeKey());
                if (!geneRgdIds1.isEmpty()) {
                    dbFlagManager.setFlag("GENEPOS_PARTIAL_MATCH_MIN100BP", gene.getRecNo());
                    getSession().incrementCounter("GENEPOS_PARTIAL_MATCH_MIN100BP", 1);
                }
            }

            if( !geneRgdIds1.isEmpty() ) {
                geneRgdIds1 = dao.getGenesByCoordsPartial(gene.getChromosome(), gene.getStartPos(), gene.getStopPos(), 200, getSpeciesTypeKey());
                if (!geneRgdIds1.isEmpty()) {
                    dbFlagManager.setFlag("GENEPOS_PARTIAL_MATCH_MIN200BP", gene.getRecNo());
                    getSession().incrementCounter("GENEPOS_PARTIAL_MATCH_MIN200BP", 1);
                }
            }

            if( !geneRgdIds1.isEmpty() ) {
                geneRgdIds1 = dao.getGenesByCoordsPartial(gene.getChromosome(), gene.getStartPos(), gene.getStopPos(), 500, getSpeciesTypeKey());
                if (!geneRgdIds1.isEmpty()) {
                    dbFlagManager.setFlag("GENEPOS_PARTIAL_MATCH_MIN500BP", gene.getRecNo());
                    getSession().incrementCounter("GENEPOS_PARTIAL_MATCH_MIN500BP", 1);
                }
            }

            if( !geneRgdIds1.isEmpty() ) {
                geneRgdIds1 = dao.getGenesByCoordsPartial(gene.getChromosome(), gene.getStartPos(), gene.getStopPos(), 1000, getSpeciesTypeKey());
                if (!geneRgdIds1.isEmpty()) {
                    dbFlagManager.setFlag("GENEPOS_PARTIAL_MATCH_MIN1000BP", gene.getRecNo());
                    getSession().incrementCounter("GENEPOS_PARTIAL_MATCH_MIN1000BP", 1);
                }
            }
        }
        else
        if( geneRgdIds.size()>1 ) {
            dbFlagManager.setFlag("GENEPOS_MULTI_MATCH", gene.getRecNo());
            getSession().incrementCounter("GENEPOS_MULTI_MATCH", 1);
        } else {
            // there is single match by genomic position!
            dbFlagManager.setFlag("GENEPOS_EXACT_MATCH", gene.getRecNo());
            getSession().incrementCounter("GENEPOS_EXACT_MATCH", 1);
            gene.setExactPosMatch(true);
        }

        analyzeMatchingRgdIds(geneRgdIds, gene.getRgdIdsMatchingByGenePos(), gene.getRecNo(), "GENEPOS");
    }

    // return a subset of 'rgdIds', consisting of validated rgd ids with active status
    List<Integer> analyzeMatchingRgdIds(Collection<Integer> rgdIds, List<String> matchingRgdIds, int recNo, String flagPrefix) throws Exception {
        // analyze matching rgd ids: whether they are active/inactive,
        // and examine their matching ENSRNOG entries
        List<Integer> activeRgdIds = new ArrayList<Integer>(rgdIds.size());
        for( Integer rgdId: rgdIds ) {

            boolean isActive = true;
            String status;

            // check if the id is active
            RgdId id = dao.getRgdId(rgdId);
            if( id==null ) {
                isActive = false;
                status = "?|";
            }
            else {
                if( id.getObjectStatus().equals("ACTIVE") )
                    status = "A|";
                else {
                    isActive = false;
                    status = "I|";
                }
            }

            if( isActive ) {
                activeRgdIds.add(rgdId);
            }

            // get coordinates for this rgd id
            String coords = "|";
            for( MapData md: dao.getMapDataForRefAssembly(rgdId, getSpeciesTypeKey()) ) {
                if( coords.length()>1 )
                    coords += ", ";
                coords += "chr"+md.getChromosome()+" "+md.getStartPos()+".."+md.getStopPos()+"("+md.getStrand()+")";
            }
            matchingRgdIds.add(status + rgdId + "|" + coords);

            // old code
            //List<String> ensemblGenes = dao.getAccIdsByRgdId(XdbId.XDB_KEY_ENSEMBL_GENES, rgdGene.getRgdId());
            //if( ensemblGenes.isEmpty() || !ensemblGenes.contains(gene.getEnsemblGeneId()) )
            //    dbFlagManager.setFlag("SYMBOL_GENE_MISMATCH", gene.getRecNo());
            //gene.getRgdIdsMatchingByRgdId().add(status+rgdId+"|"+EnsemblUtils.listOfStringsAsString(ensemblGenes)+coords);
        }

        if( activeRgdIds.size()>1) {
            dbFlagManager.setFlag(flagPrefix+"_MULTI_MATCH", recNo);
            getSession().incrementCounter(flagPrefix+"_MULTI_MATCH", 1);
        }
        else if( activeRgdIds.size()==1) {
            dbFlagManager.setFlag(flagPrefix+"_SINGLE_MATCH", recNo);
            getSession().incrementCounter(flagPrefix+"_SINGLE_MATCH", 1);
        }

        // log if there is a match with some inactive rgd genes
        if( rgdIds.size() != activeRgdIds.size() ) {
            dbFlagManager.setFlag(flagPrefix+"_INACTIVE_MATCH", recNo);
            getSession().incrementCounter(flagPrefix+"_INACTIVE_MATCH", 1);
        }

        return activeRgdIds;
    }


    void checkTranscript(EnsemblGene gene) throws Exception {

        // count of transcripts matching rgd by pos exactly
        int matchedByPos = 0;

        // check every ensembl transcript
        for( EnsemblTranscript et: gene.getEnsemblTranscripts() ) {
            // match the transcript with rgd by exact position
            List<Transcript> rgdTranscripts = dao.getTranscriptsByCoords(et.getChromosome(), et.getStartPos(), et.getStopPos(), SpeciesType.RAT);
            if( rgdTranscripts.isEmpty() ) {
                gene.addConflict("T01 "+gene.getEnsemblGeneId()+" "+et.getTranscriptId()+ " does not match any rgd transcript by position");
            }
            else if( rgdTranscripts.size()>1 ) {
                gene.addConflict("T02 "+gene.getEnsemblGeneId()+" "+et.getTranscriptId()+ " matches multiple rgd transcripts by position");
            }
            else {
                gene.addConflict("T03 "+gene.getEnsemblGeneId()+" "+et.getTranscriptId()+ " matches exactly one rgd transcript by position");
                et.setRgdTranscript(rgdTranscripts.get(0));
                matchedByPos ++;
            }
        }

        // update transcript summaries
        //
        boolean allMatch = false;
        if( matchedByPos == gene.getEnsemblTranscripts().size() ) {
            summary.ensemblGenesWithAllMatchingTranscripts++;
            allMatch = true;
        } else
        if( matchedByPos == 0 )
            summary.ensemblGenesWithNonMatchingTranscripts++;
        else
            summary.ensemblGenesWithSomeMatchingTranscripts++;

        if( allMatch ) {
            // transcripts match perfectly by position with rgd
            // verify if gene rgd ids match as well
            if( gene.getMatchingRgdId()>0 ) {
                // 1. for already matching genes -- verify transcript list
                for( EnsemblTranscript et: gene.getEnsemblTranscripts() ) {
                    if( et.getRgdTranscript().getGeneRgdId()!=gene.getMatchingRgdId() ) {
                        gene.addConflict("T04 "+gene.getEnsemblGeneId()+" "+et.getTranscriptId()+ " matches exactly one rgd transcript by position, but matching gene rgd id is different");
                        summary.ensemblGenesWithAllMatchingTranscripts--;
                        summary.ensemblGenesWithAllMatchingTranscripts2++;
                        break;
                    }
                }
            }
            else {
                // 2. non-matching gene -- matching transcript, assume new match!!!
                int matchingRgdId = 0;
                for( EnsemblTranscript et: gene.getEnsemblTranscripts() ) {
                    if( matchingRgdId==0 )
                        matchingRgdId = et.getRgdTranscript().getGeneRgdId();
                    else
                    if( et.getRgdTranscript().getGeneRgdId()!=matchingRgdId ) {
                        gene.addConflict("T05 "+gene.getEnsemblGeneId()+" "+et.getTranscriptId()+ " matches exactly one rgd transcript by position, but matching gene rgd id is different");
                        summary.ensemblGenesWithAllMatchingTranscripts--;
                        summary.ensemblGenesWithAllMatchingTranscripts2++;
                        matchingRgdId = 0;
                        break;
                    }
                }
                if( matchingRgdId > 0 ) {
                    summary.ensemblGenesMatchedByTranscripts++;
                    gene.setMatchingRgdId(matchingRgdId);
                }
            }
        }
    }

    public EnsemblGeneSummary getSummary() {
        return summary;
    }

    synchronized protected void registerDbFlags() throws Exception {

        dbFlagManager.registerFlag(
            "ENSEMBL_GENEID_INACTIVE_MATCH",
            "ensembl gene matches by ensembl gene id with one or more inactive rgd genes");

        dbFlagManager.registerFlag(
            "ENSEMBL_GENEID_SINGLE_MATCH",
            "ensembl gene matches by ensembl gene id with exactly one active rgd gene");

        dbFlagManager.registerFlag(
            "ENSEMBL_GENEID_MULTI_MATCH",
            "ensembl gene matches by ensembl gene id with multiple active rgd genes");

        dbFlagManager.registerFlag(
            "ENSEMBL_GENEID_NO_MATCH",
            "ensembl gene does not match to any rgd gene by ensembl gene id");


        dbFlagManager.registerFlag(
            "NCBIGENE_MISSING",
            "no incoming NCBI gene ids for this ensembl gene");

        dbFlagManager.registerFlag(
            "NCBIGENE_NO_MATCH",
            "no incoming NCBI gene id matches rgd for this ensembl gene");

        dbFlagManager.registerFlag(
            "NCBIGENE_MULTI_MATCH",
            "incoming NCBI gene ids match multiple active genes in rgd");

        dbFlagManager.registerFlag(
            "NCBIGENE_SINGLE_MATCH",
            "incoming NCBI gene ids match a single active gene in rgd");

        dbFlagManager.registerFlag(
            "NCBIGENE_INACTIVE_MATCH",
            "some incoming NCBI gene ids match an inactive gene");



        dbFlagManager.registerFlag(
            "RGDIDS_MISSING",
            "no rgd ids for this ensembl gene");

        dbFlagManager.registerFlag(
            "RGDIDS_SINGLE_MATCH",
            "one exact match by rgd id for this ensembl gene -- matching rgd id is active");

        dbFlagManager.registerFlag(
            "RGDIDS_MULTI_MATCH",
            "multiple matches by rgd id for this ensembl gene -- multiple matching active rgd ids");

        dbFlagManager.registerFlag(
            "RGDIDS_INACTIVE_MATCH",
            "one or more rgd ids for this ensembl gene matches inactive rgd gene");


        dbFlagManager.registerFlag(
            "SYMBOL_MISSING",
            "no symbol found for this ensembl gene");

        dbFlagManager.registerFlag(
            "SYMBOL_NO_MATCH",
            "ensembl gene symbol does not match a symbol or alias in rgd");

        dbFlagManager.registerFlag(
            "SYMBOL_SINGLE_MATCH",
            "ensembl gene symbol matches a single symbol or alias in rgd");

        dbFlagManager.registerFlag(
            "SYMBOL_MULTI_MATCH",
            "ensembl gene symbol matches multiple symbols or aliases in rgd");

        dbFlagManager.registerFlag(
            "SYMBOL_INACTIVE_MATCH",
            "ensembl gene symbol matches an inactive symbol or alias in rgd");


        dbFlagManager.registerFlag(
            "GENEPOS_NO_POS",
            "ensembl gene does not have genomic position available");

        dbFlagManager.registerFlag(
            "GENEPOS_NO_MATCH",
            "ensembl gene does not match by genomic position with rgd");

        dbFlagManager.registerFlag(
            "GENEPOS_EXACT_MATCH",
            "ensembl gene matches exactly by genomic position with rgd");

        dbFlagManager.registerFlag(
            "GENEPOS_MULTI_MATCH",
            "ensembl gene matches by genomic position to multiple rgd genes");

        dbFlagManager.registerFlag(
            "GENEPOS_INACTIVE_MATCH",
            "ensembl gene matches by genomic position to an inactive rgd gene");

        dbFlagManager.registerFlag(
            "GENEPOS_SINGLE_MATCH",
            "ensembl gene matches by genomic position to a single active rgd gene");

        dbFlagManager.registerFlag(
            "GENEPOS_PARTIAL_MATCH",
            "ensembl gene matches by genomic position partially to an rgd gene");

        dbFlagManager.registerFlag(
            "GENEPOS_PARTIAL_MATCH_MIN1BP",
            "ensembl gene genomic position overlaps rgd gene by 1+ bp");

        dbFlagManager.registerFlag(
            "GENEPOS_PARTIAL_MATCH_MIN20BP",
            "ensembl gene genomic position overlaps rgd gene by 20+ bp");

        dbFlagManager.registerFlag(
            "GENEPOS_PARTIAL_MATCH_MIN50BP",
            "ensembl gene genomic position overlaps rgd gene by 50+ bp");

        dbFlagManager.registerFlag(
            "GENEPOS_PARTIAL_MATCH_MIN100BP",
            "ensembl gene genomic position overlaps rgd gene by 100+ bp");

        dbFlagManager.registerFlag(
            "GENEPOS_PARTIAL_MATCH_MIN200BP",
            "ensembl gene genomic position overlaps rgd gene by 200+ bp");

        dbFlagManager.registerFlag(
            "GENEPOS_PARTIAL_MATCH_MIN500BP",
            "ensembl gene genomic position overlaps rgd gene by 500+ bp");

        dbFlagManager.registerFlag(
            "GENEPOS_PARTIAL_MATCH_MIN1000BP",
            "ensembl gene genomic position overlaps rgd gene by 1000+ bp");


        dbFlagManager.registerFlag(
            "LOAD_NEW_GENE",
            "ensembl gene does not match a gene in RGD, new gene will be inserted");

        dbFlagManager.registerFlag(
            "LOAD_WITH_WARNINGS",
            "ensembl gene matches to one rgd ids, there are conflicting matches present");

        dbFlagManager.registerFlag(
            "LOAD_MULTI_MATCH",
            "ensembl gene matches to one rgd gene, there are secondary matches present");

        dbFlagManager.registerFlag(
            "LOAD_EXACT_MATCH",
            "ensembl gene matches perfectly to one gene in rgd");


        dbFlagManager.registerFlag(
            "STATUS_NOVEL",
            "ensembl gene has 'NOVEL' status");

        dbFlagManager.registerFlag(
            "STATUS_KNOWN",
            "ensembl gene has 'KNOWN' status");

        dbFlagManager.registerFlag(
            "STATUS_KNOWN_BY_PROJECTION",
            "ensembl gene has 'KNOWN_BY_PROJECTION' status");

        dbFlagManager.registerFlag(
            "STATUS_OTHER",
            "ensembl gene has status other than 'NOVEL','KNOWN','KNOWN_BY_PROJECTION'");


        dbFlagManager.registerFlag(
                "CONFLICT_BIN1",
                "this ensembl gene have multiple incoming RGD ids, from each only one is mapped to reference assembly");
    }

    public int getSpeciesTypeKey() {
        return speciesTypeKey;
    }

    public void setSpeciesTypeKey(int speciesTypeKey) {
        this.speciesTypeKey = speciesTypeKey;

        if( speciesTypeKey==SpeciesType.RAT )
            log = LogFactory.getLog("newRatGenes");
        else if( speciesTypeKey==SpeciesType.MOUSE )
            log = LogFactory.getLog("newMouseGenes");
        else if( speciesTypeKey==SpeciesType.HUMAN )
            log = LogFactory.getLog("newHumanGenes");
        else
            log = LogFactory.getLog("core");
    }

    public PipelineLogFlagManager getDbFlagManager() {
        return dbFlagManager;
    }

    public void setDbFlagManager(PipelineLogFlagManager dbFlagManager) {
        this.dbFlagManager = dbFlagManager;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}