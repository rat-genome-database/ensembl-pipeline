package edu.mcw.rgd.dataload;

import edu.mcw.rgd.dao.AbstractDAO;
import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.IntListQuery;
import edu.mcw.rgd.dao.spring.TranscriptQuery;
import edu.mcw.rgd.datamodel.*;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.MappingSqlQuery;

import java.sql.Types;
import java.util.*;
import java.util.Map;

/**
 * @author mtutaj
 * @since Aug 13, 2010
 * <p>
 * wrapper for all database handling
 */
public class EnsemblDAO extends AbstractDAO {

    XdbIdDAO xdbDAO = new XdbIdDAO();
    AliasDAO aliasDAO = new AliasDAO();
    GeneDAO geneDAO = new GeneDAO();
    RGDManagementDAO managementDAO = new RGDManagementDAO();
    MapDAO mapDAO = new MapDAO();
    int[] primaryMapKey = new int[4];

    // mapping of map keys to Map objects
    Map<Integer, edu.mcw.rgd.datamodel.Map> maps = new HashMap<Integer, edu.mcw.rgd.datamodel.Map>();

    public EnsemblDAO() throws Exception {
        primaryMapKey[1] = mapDAO.getPrimaryRefAssembly(SpeciesType.HUMAN).getKey();
        primaryMapKey[2] = mapDAO.getPrimaryRefAssembly(SpeciesType.MOUSE).getKey();
        primaryMapKey[3] = mapDAO.getPrimaryRefAssembly(SpeciesType.RAT).getKey();
    }

    public int getPrimaryMapKey(int speciesTypeKey) {
        return primaryMapKey[speciesTypeKey];
    }

    /**
     * wrapper: get RgdId object for given rgd id
     * @param rgdId rgd id
     * @return RgdId object or null if rgd id is invalid
     * @throws Exception
     */
    public RgdId getRgdId(int rgdId) throws Exception {
        return managementDAO.getRgdId2(rgdId);
    }

    RgdId createRgdId(int objectKey, int speciesTypeKey) throws Exception {
        return managementDAO.createRgdId(objectKey, "ACTIVE", "created by Ensembl pipeline", speciesTypeKey);
    }


    public boolean existsGeneType(String geneType) throws Exception {
        return geneDAO.existsGeneType(geneType);
    }

    public void createGeneType(String geneType) throws Exception {
        geneDAO.createGeneType(geneType, geneType, geneType);
    }

    public Gene getGene(int rgdId) throws Exception {
        return geneDAO.getGene(rgdId);
    }

    public void insertGene(Gene gene) throws Exception {
        geneDAO.insertGene(gene);
    }


    public List<Alias> getAliases(int rgdId) throws Exception {
        return aliasDAO.getAliases(rgdId);
    }

    public int insertAlias(Alias alias) throws Exception {
        List<Alias> aliases = new ArrayList<Alias>(1);
        aliases.add(alias);
        return aliasDAO.insertAliases(aliases);
    }


    /**
     * return map data for given rgd id, and the primary reference assembly map
     * @param geneRgdId gene rgd id
     * @param speciesTypeKey species type key
     * @return List of MapData objects if there is a map; could be empty
     * @throws Exception
     */
    public List<MapData> getMapDataForRefAssembly(int geneRgdId, int speciesTypeKey) throws Exception {

        return mapDAO.getMapData(geneRgdId, getPrimaryMapKey(speciesTypeKey));
    }

    /**
     * return map data for given rgd id, and all maps except the primary reference assembly map
     * @param geneRgdId gene rgd id
     * @param speciesTypeKey species type key
     * @return List of MapData objects if there is a map; could be empty
     * @throws Exception
     */
    public List<MapData> getAllMapData(int geneRgdId, int speciesTypeKey) throws Exception {

        return mapDAO.getMapData(geneRgdId, speciesTypeKey);
    }

    int updateMapData(MapData md) throws Exception {
        return mapDAO.updateMapData(md);
    }

    int insertMapData(MapData md) throws Exception {
        return mapDAO.insertMapData(md);
    }

    int deleteMapData(List<MapData> mds) throws Exception {
        return mapDAO.deleteMapData(mds);
    }


    public List<XdbId> getXdbIds(int geneRgdId) throws Exception {

        XdbId filter = new XdbId();
        filter.setRgdId(geneRgdId);
        filter.setSrcPipeline("Ensembl");

        return xdbDAO.getXdbIds(filter);
    }

    public int insertXdbIds(List<XdbId> xdbIds) throws Exception {
        return xdbDAO.insertXdbs(xdbIds);
    }

    public int deleteXdbIds(List<XdbId> xdbIds) throws Exception {
        return xdbDAO.deleteXdbIds(xdbIds);
    }


    /**
     * return a list of rgd ids for given xdb and accession id
     * @param xdbKey xdb key
     * @param accId accession id
     * @param speciesTypeKey species type key
     * @return list of rgd ids
     * @throws Exception
     */
    public List<Integer> getXdbIds(int xdbKey, String accId, int speciesTypeKey) throws Exception {

        String sql = "select distinct r.RGD_ID from RGD_ACC_XDB x,RGD_IDS r where XDB_KEY=? and ACC_ID=? and r.rgd_id=x.rgd_id and species_type_key=? and object_key=?";
        return IntListQuery.execute(this, sql, xdbKey, accId, speciesTypeKey, RgdId.OBJECT_KEY_GENES);
    }

    // return a list of genes matching by a symbol
    public Collection<Integer> getGenesBySymbol(String symbol, int speciesTypeKey) throws Exception {

        Set<Integer> geneRgdIds = new HashSet<Integer>();

        // get gene by gene symbol
        for( Gene gene: geneDAO.getAllGenesBySymbol(symbol, speciesTypeKey) ) {
            geneRgdIds.add(gene.getRgdId());
        }
        // get genes by alias
        //for( Gene gene: geneDAO.getGenesByAlias(symbol, speciesTypeKey) ) {
        //    geneRgdIds.add(gene.getRgdId());
        //}
        return geneRgdIds;
    }

    public List<Integer> getGenesByCoords(String chr, int startPos, int stopPos, int speciesTypeKey) throws Exception {

        // select genes that match exactly the genomic position
        String sql = "select g.RGD_ID from GENES g,RGD_IDS r,MAPS_DATA m "+
                "where g.RGD_ID=r.RGD_ID and r.SPECIES_TYPE_KEY=? and m.RGD_ID=r.RGD_ID and m.MAP_KEY=? "+
                " and m.CHROMOSOME=? and m.START_POS=? and m.STOP_POS=?";

        return IntListQuery.execute(this, sql, speciesTypeKey, getPrimaryMapKey(speciesTypeKey), chr, startPos, stopPos);
    }

    public List<Integer> getGenesByCoordsPartial(String chr, int startPos, int stopPos, int speciesTypeKey) throws Exception {

        // select genes that match exactly the genomic position
        String sql = "select g.RGD_ID from GENES g,RGD_IDS r,MAPS_DATA m "+
                "where g.RGD_ID=r.RGD_ID and r.SPECIES_TYPE_KEY=? and m.RGD_ID=r.RGD_ID and m.MAP_KEY=? "+
                " AND m.chromosome=? AND m.start_pos<=? AND m.stop_pos>=?";

        return IntListQuery.execute(this, sql, speciesTypeKey, getPrimaryMapKey(speciesTypeKey), chr, stopPos, startPos);
    }

    public List<Integer> getGenesByCoordsPartial(String chr, int startPos, int stopPos, int delta, int speciesTypeKey) throws Exception {

        delta -= 1;
        return getGenesByCoordsPartial(chr, startPos+delta, stopPos-delta, speciesTypeKey);
    }

    List<MapData> getGenePosition(int rgdId, int speciesTypeKey) throws Exception {

        return mapDAO.getMapData(rgdId, getPrimaryMapKey(speciesTypeKey), "Ensembl");
    }

    String getMapName(int mapKey) throws Exception {
        if( mapKey==0 ) {
            System.out.println("map_key==0???");
            return "";
        }
        edu.mcw.rgd.datamodel.Map map = maps.get(mapKey);
        if( map==null ) {
            map = mapDAO.getMap(mapKey);
            maps.put(mapKey, map);
        }
        return map==null ? "" : map.getName();
    }

    public List<Transcript> getTranscriptsByCoords(String chromosome, int startPos, int stopPos, int species) throws Exception {
        if( species!=SpeciesType.RAT )
            return null;

        String sql = "SELECT transcript_rgd_id,gene_rgd_id,acc_id,created_date,is_non_coding_ind FROM maps_data,transcripts "
            + "WHERE map_key=? AND chromosome=? AND start_pos=? AND stop_pos=? AND rgd_id=transcript_rgd_id";
        MappingSqlQuery query = new TranscriptQuery(this.getDataSource(), sql);
        query.declareParameter(new SqlParameter(Types.INTEGER));
        query.declareParameter(new SqlParameter(Types.VARCHAR));
        query.declareParameter(new SqlParameter(Types.INTEGER));
        query.declareParameter(new SqlParameter(Types.INTEGER));
        return query.execute(new Object[]{getPrimaryMapKey(species), chromosome, startPos, stopPos});
    }

}
