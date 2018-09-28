package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.MapData;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.pipelines.PipelineRecord;
import nu.xom.Attribute;
import nu.xom.Element;

import java.util.*;

/**
 * @author mtutaj
 * @since Aug 13, 2010
 * represents an ensemble gene as read from the input file
 */
public class EnsemblGene extends PipelineRecord {

    int recNo; // unique record number -- assigned automatically by file parser

    // incoming data from Ensembl
    String ensemblGeneId;
    String externalGeneDb; // like 'NCBI Gene', 'RGD Symbol', 'MGI (automatic)'
    String externalSymbol; // like 'Pqlc3'
    String geneBioType;

    // genomic position
    String chromosome;
    int startPos, stopPos;
    String strand;
    boolean isExactPosMatch; // if true, there is an exact match by position
    boolean isPartialPosMatch; // if true, there is an partial match by position

    List<EnsemblTranscript> ensemblTranscripts = new ArrayList<EnsemblTranscript>();
    List<String> ensemblProteinIds = new ArrayList<String>();
    List<String> ncbiGeneIds = new ArrayList<String>();
    List<Integer> rgdIds = new ArrayList<Integer>();

    // data set by QC process
    List<String> conflicts = new ArrayList<String>();


    // matching rgd data for ensemble gene id ('A|RGDID' for active rgd id, 'I|RGDID' for inactive)
    List<String> rgdIdsMatchingByEnsemblGeneId = new ArrayList<String>();
    // matching rgd data for rgd id ('A|RGDID' for active  id, 'I|RGDID' for inactive)
    List<String> rgdIdsMatchingByRgdId = new ArrayList<String>();
    // matching rgd data for NCBI gene id ('A|RGDID' for active  id, 'I|RGDID' for inactive)
    List<String> rgdIdsMatchingByNcbiGeneId = new ArrayList<String>();
    // matching rgd data for symbol ('A|RGDID' for active  id, 'I|RGDID' for inactive)
    List<String> rgdIdsMatchingBySymbol = new ArrayList<String>();
    // matching rgd data by genomic position ('A|RGDID' for active  id, 'I|RGDID' for inactive)
    List<String> rgdIdsMatchingByGenePos = new ArrayList<String>();

    Collection<String> qcFlags;

    // to be used by loading
    public Collection<Integer> activeRgdIdsMatchingByRgdId = new ArrayList<Integer>();
    public Collection<Integer> activeRgdIdsMatchingByNcbiId = new ArrayList<Integer>();
    public Collection<Integer> activeRgdIdsMatchingByEnsemblId = new ArrayList<Integer>();
    public Collection<Integer> activeRgdIdsMatchingBySymbolId = new ArrayList<Integer>();

    // rgd_id matching ensembl gene id
    int matchingRgdId;

    // delta data to be used directly by loader to UPDATE gene in RGD
    public MapData mdForInsert; // maps data to be inserted, or NULL
    public MapData mdForUpdate; // maps data to be updated, or NULL
    public List<MapData> mdsForDelete; // maps data to be deleted if any
    public Alias aliasForInsert; // new alias for insert
    public List<XdbId> xdbIdsForInsert;
    public List<XdbId> xdbIdsForDelete;

    // add a new conflict, but do not add duplicate conflicts
    public void addConflict(String conflict) {
        if( !conflicts.contains(conflict) )
            conflicts.add(conflict);
    }

    // dump the contents of this record to xml
    public String toXml() {
        // create root element
        //Element root = new Element("EnsembleSet");
        //root.addAttribute(new Attribute("ver", "1.0.1"));
        //Document doc = new Document(root);

        Element el = new Element("Gene");
        el.addAttribute(new Attribute("recNo", Integer.toString(recNo)));

        // incoming ensembl data
        Element ens = new Element("Ensembl");
        ens.addAttribute(new Attribute("geneId", this.getEnsemblGeneId()));
        ens.addAttribute(new Attribute("externalDb", this.getExternalGeneDb()));
        ens.addAttribute(new Attribute("dbSymbol", this.getExternalSymbol()));
        ens.addAttribute(new Attribute("biotype", this.getGeneBioType()));
        ens.addAttribute(new Attribute("genePos", "chr"+this.getChromosome()+" "+this.getStartPos()+".."+this.getStopPos()+"("+this.getStrand()+")"));

        // dump all rgd ids present in ensembl
        if( getRgdIds()!=null && getRgdIds().size()>0 ) {
            Element eg = new Element("RgdIds");
            for( Integer rgdId: getRgdIds() ) {
                Element e = new Element("RgdId");
                e.appendChild(rgdId.toString());
                eg.appendChild(e);
            }
            ens.appendChild(eg);
        }

        // dump all NCBI gene ids present in ensembl
        if( getNcbiGeneIds()!=null && getNcbiGeneIds().size()>0 ) {
            Element eg = new Element("NcbiGene");
            for( String egId: getNcbiGeneIds() ) {
                Element e = new Element("GeneId");
                e.appendChild(egId);
                eg.appendChild(e);
            }
            ens.appendChild(eg);
        }
        el.appendChild(ens);

        // matching rgd
        Element rgd = new Element("Rgd");

        // matching gene rgd by ensembl gene id
        if( getRgdIdsMatchingByEnsemblGeneId().size()>0 ) {
            Element em = new Element("match");
            em.addAttribute(new Attribute("by", "EnsemblGeneId"));
            for( String s: getRgdIdsMatchingByEnsemblGeneId() ) {
                Element e2 = new Element("rgd");
                e2.addAttribute(new Attribute("rgdId", s.substring(2)));
                e2.addAttribute(new Attribute("status", s.charAt(0)=='A'?"ACTIVE":s.charAt(0)=='I'?"INACTIVE":"NOT_IN_RGD"));
                em.appendChild(e2);
            }
            rgd.appendChild(em);
        }

        // matching by rgd ids
        generateMatchBy(rgd, "RgdId", getRgdIdsMatchingByRgdId() );

        // matching by NCBI gene ids
        generateMatchBy(rgd, "NcbiGeneId", getRgdIdsMatchingByNcbiGeneId() );

        // matching by symbol
        generateMatchBy(rgd, "Symbol", getRgdIdsMatchingBySymbol() );

        // matching by genomic position
        Element em = generateMatchBy(rgd, "GenePos", getRgdIdsMatchingByGenePos() );
        if( em!=null ) {
            // custom attribute for partial/exact matching
            if( isPartialPosMatch() )
                em.addAttribute(new Attribute("method", "partial"));
            else
            if( isExactPosMatch() )
                em.addAttribute(new Attribute("method", "exact"));
        }

        el.appendChild(rgd);

        return el.toXML();
    }

    Element generateMatchBy(Element rgdEl, String matchBy, List<String> rgdIds) {

        Element em = null;
        if( rgdIds.size()>0 ) {
            em = new Element("match");
            em.addAttribute(new Attribute("by", matchBy));
            for( String s: rgdIds ) {
                // s: "A|12345|ENSRNOG000123|chr3 100..200(-)"
                Element e2 = new Element("rgd");
                String[] cols = s.split("\\|", -1);
                e2.addAttribute(new Attribute("rgdId", cols[1]));
                e2.addAttribute(new Attribute("status", cols[0].equals("A")?"ACTIVE":"INACTIVE"));
                if( !cols[2].isEmpty() )
                    e2.addAttribute(new Attribute("ensembl", cols[2]));
                e2.addAttribute(new Attribute("pos", cols[3]));
                em.appendChild(e2);
            }
            rgdEl.appendChild(em);
        }
        return em;
    }

    public String getEnsemblGeneId() {
        return ensemblGeneId;
    }

    public void setEnsemblGeneId(String ensemblGeneId) {
        this.ensemblGeneId = ensemblGeneId;
    }

    public List<EnsemblTranscript> getEnsemblTranscripts() {
        return ensemblTranscripts;
    }

    public void setEnsemblTranscripts(List<EnsemblTranscript> ensemblTranscripts) {
        this.ensemblTranscripts = ensemblTranscripts;
    }

    public List<String> getEnsemblProteinIds() {
        return ensemblProteinIds;
    }

    public void setEnsemblProteinIds(List<String> ensemblProteinIds) {
        this.ensemblProteinIds = ensemblProteinIds;
    }

    public List<String> getNcbiGeneIds() {
        return ncbiGeneIds;
    }

    public void setNcbiGeneIds(List<String> ncbiGeneIds) {
        this.ncbiGeneIds = ncbiGeneIds;
    }

    public List<Integer> getRgdIds() {
        return rgdIds;
    }

    public void setRgdIds(List<Integer> rgdIds) {
        this.rgdIds = rgdIds;
    }

    public int getRecNo() {
        return recNo;
    }

    public void setRecNo(int recNo) {
        this.recNo = recNo;
    }

    public List<String> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<String> conflicts) {
        this.conflicts = conflicts;
    }

    public int getMatchingRgdId() {
        return matchingRgdId;
    }

    public void setMatchingRgdId(int matchingRgdId) {
        this.matchingRgdId = matchingRgdId;
    }

    public String getExternalGeneDb() {
        return externalGeneDb;
    }

    public void setExternalGeneDb(String externalGeneDb) {
        this.externalGeneDb = externalGeneDb;
    }

    public String getExternalSymbol() {
        return externalSymbol;
    }

    public void setExternalSymbol(String externalSymbol) {
        this.externalSymbol = externalSymbol;
    }

    public List<String> getRgdIdsMatchingByEnsemblGeneId() {
        return rgdIdsMatchingByEnsemblGeneId;
    }

    public void setRgdIdsMatchingByEnsemblGeneId(List<String> activeRgdIdsMatchingByEnsemblGeneId) {
        this.rgdIdsMatchingByEnsemblGeneId = activeRgdIdsMatchingByEnsemblGeneId;
    }

    public List<String> getRgdIdsMatchingByRgdId() {
        return rgdIdsMatchingByRgdId;
    }

    public void setRgdIdsMatchingByRgdId(List<String> rgdIdsMatchingByRgdId) {
        this.rgdIdsMatchingByRgdId = rgdIdsMatchingByRgdId;
    }

    public List<String> getRgdIdsMatchingByNcbiGeneId() {
        return rgdIdsMatchingByNcbiGeneId;
    }

    public void setRgdIdsMatchingByNcbiGeneId(List<String> rgdIdsMatchingByNcbiGeneId) {
        this.rgdIdsMatchingByNcbiGeneId = rgdIdsMatchingByNcbiGeneId;
    }

    public List<String> getRgdIdsMatchingBySymbol() {
        return rgdIdsMatchingBySymbol;
    }

    public void setRgdIdsMatchingBySymbol(List<String> rgdIdsMatchingBySymbol) {
        this.rgdIdsMatchingBySymbol = rgdIdsMatchingBySymbol;
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

    public String getStrand() {
        return strand;
    }

    public void setStrand(String strand) {
        this.strand = strand;
    }

    public List<String> getRgdIdsMatchingByGenePos() {
        return rgdIdsMatchingByGenePos;
    }

    public void setRgdIdsMatchingByGenePos(List<String> rgdIdsMatchingByGenePos) {
        this.rgdIdsMatchingByGenePos = rgdIdsMatchingByGenePos;
    }

    public boolean isExactPosMatch() {
        return isExactPosMatch;
    }

    public void setExactPosMatch(boolean exactPosMatch) {
        isExactPosMatch = exactPosMatch;
    }

    public boolean isPartialPosMatch() {
        return isPartialPosMatch;
    }

    public void setPartialPosMatch(boolean partialPosMatch) {
        isPartialPosMatch = partialPosMatch;
    }

    public Collection<String> getQcFlags() {
        return qcFlags;
    }

    public void setQcFlags(Collection<String> qcFlags) {
        this.qcFlags = qcFlags;
    }

    public boolean isFlagSet(String flagSymbol) {
        return getQcFlags().contains(flagSymbol);
    }

    public String getGeneBioType() {
        return geneBioType;
    }

    public void setGeneBioType(String geneBioType) {
        this.geneBioType = geneBioType;
    }
}