package buildgrid.extract;

import javafx.util.Pair;
import org.apache.commons.lang.ArrayUtils;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by Cameron on 6/6/2017.
 */
public class TakeoffPreparse {
    static final String PRODUCT_REGEX = "(([A-Z]+|[0-9]+)*([A-Z]+|[0-9]+)*)(-)?(([A-Z]+|[0-9]+)*([A-Z]+|[0-9]+)*)";
    static final String SINGWORD = "^(?:[a-z]{3,})$";
    static final String WORDGROUP = "(?:\\w{3,})(?:\\s|(?:,|;|\'|\")\\s+)(?:\\w{3,})";
    static final String QTYREG = "^\\s*(?:\\d+|\\d*.\\d+)\\s*$";
    static final String DIMREG = "\\d+(?:\\.\\d+)?[\\s-]*(?:\\d+)?(?:\\/\\d+)?(?:cm|mm|yd|\"|'|feet|ft|in)(?:\\s*x\\s*|\\s*by\\s*)?(?:\\d+(?:\\.\\d+)?[\\s*-]*(?:\\d+(?:\\/\\d+)?)?(?:cm|mm|yd|\"|'|feet|ft|in)?)?";
    static final Pattern NULLSPACE = Pattern.compile("^(?:\\s|,|;|-|_)+$");

    static final Pattern QUANT = Pattern.compile(QTYREG);
    static final Pattern DIM = Pattern.compile(DIMREG);
    static final Pattern WORD = Pattern.compile(WORDGROUP);
    static final Pattern INDWORD = Pattern.compile(SINGWORD);

    static final String[] UNITS = {"in","inch","ft","foot","yard","yd",
            "ea", "ac","as","cf","cy","da","ed","ga","gm",
            "hr","lb","lf","lo","ls","lu","mb","mh","mi","pi","pc","pcs","each",
            "ps","sf","sy","TN","tonnes","mm","m","m2","m3","cm"};

    public enum COLUMNS {
        REMOVE, DESCRIPTION, QUANTITY, UNIT, SUBPARTOF, LINK, ADDITIONAL, DIMENSION
    }

    public enum INTERIORS {
        WORDGROUP, QTY, DIM, UNITS
    }

    static final String POT_REMOVE = "^(?:item number|item num|item no|item #|part number|part num|part no|part #)[^ ]*$";
    static final String POT_DESC = "(?:description|desc|material|name)$";
    static final String POT_QUANTITY = "^(?:quantity|qty|quant)\\.?$";
    static final String POT_UNIT = "^(?:units|unit|unts|unt|un)\\.?$";
    static final String POT_DIM = "^(?:dimensions|dim)(s)?[.]?$";
    static final String POT_LINK = "[^\\w](?:link|url|email)[^\\w]";
    static final String POT_ADD = "(?:additional|note)\\w?";
    static final COLUMNS[] MAPPING_NAME = {COLUMNS.DESCRIPTION, COLUMNS.UNIT, COLUMNS.QUANTITY, COLUMNS.DIMENSION,
                                            COLUMNS.REMOVE,COLUMNS.LINK, COLUMNS.ADDITIONAL};
    static final Pattern[] POT_MAPPING_MAJOR = {    //order matters
            Pattern.compile(POT_DESC),
            Pattern.compile(POT_UNIT),
            Pattern.compile(POT_QUANTITY),
            Pattern.compile(POT_DIM)
    };
    static final Pattern[] POT_MAPPING_MINOR = {
            Pattern.compile(POT_REMOVE),
            Pattern.compile(POT_LINK),
            Pattern.compile(POT_ADD)
    };


    public static List<String[]> process(List<String[]> original) {
    //    List<String[]> original = this.extractTables();
        Iterator<String[]> rows = original.iterator();
        String[] cells;
        Map<COLUMNS, List<Integer>> sideMapping = new HashMap<COLUMNS, List<Integer>>();
        Map<COLUMNS, Integer> tempMapping = null;
        Map<COLUMNS, Integer> candidates = null;
        int rowCtr = 0;
        int tableRowStart = -1;
        //first try to match headers
        while(rows.hasNext()) {
            cells = rows.next();
            tempMapping = matchRowMajorColumns(cells);
            if(tempMapping.size() > 0) {
                tableRowStart = rowCtr;
                //consume second to check if another set of rows
                cells = rows.next();
                candidates = matchRowMajorColumns(cells);
                if(candidates.size() > 0) { //turn into one mapping
                    tempMapping = mergeRowHeaders(tempMapping, candidates);
                    tableRowStart++;
                }
                break;
            }
            rowCtr++;
        }

        if(tableRowStart != -1) {
            //match units or something for now ignore
            int maxChk = 5;
            List<String[]> chkRows = new ArrayList<String[]>(maxChk * 2);
            Set<COLUMNS> chkSet = tempMapping.keySet();
            for(int ii=tableRowStart + 1; ii < original.size(); ii++) {
                String[] row = original.get(ii);
                Iterator<COLUMNS> chkMappings = chkSet.iterator();
                boolean matchesAll = true;
                while(chkMappings.hasNext()) {
                    COLUMNS col = chkMappings.next();
                    String item = row[tempMapping.get(col)];
                    if(NULLSPACE.matcher(item).matches()) {
                        matchesAll = false;
                    }
                }

                if(matchesAll) {
                    chkRows.add(row);
                    if(chkRows.size() == maxChk * 2) {
                        break;
                    }
                }
            }

            if(chkRows.size() > 0) {
                int descCount = 0;
                int unitCount = 0;
                int qtyCount = 0;
                int dimCount = 0;
                int descBorder = (tempMapping.containsKey(COLUMNS.DESCRIPTION)) ? tempMapping.get(COLUMNS.DESCRIPTION) : -1;
                int[] additionalwords = new int[chkRows.get(0).length];
                Arrays.fill(additionalwords, 0);
                for(int ii=0; ii < chkRows.size(); ii++) {
                    String[] row = chkRows.get(ii);
                    boolean[] matches = matchInteriorsToCol(row, COLUMNS.DESCRIPTION);
                    boolean[] secondary = matchInteriorsToCol(row, COLUMNS.UNIT);
                    boolean[] tertiary = matchInteriorsToCol(row, COLUMNS.QUANTITY);
                    boolean[] quaternary = matchInteriorsToCol(row, COLUMNS.DIMENSION);

                    if(descBorder > -1 && matches[descBorder]) {
                        descCount++;
                        additionalwords[descBorder]++;
                    }
                    if(tempMapping.containsKey(COLUMNS.UNIT) && secondary[tempMapping.get(COLUMNS.UNIT)]) {
                        unitCount++;
                    }
                    if(tempMapping.containsKey(COLUMNS.QUANTITY) && tertiary[tempMapping.get(COLUMNS.QUANTITY)]) {
                        qtyCount++;
                    }
                    if(tempMapping.containsKey(COLUMNS.DIMENSION) && quaternary[tempMapping.get(COLUMNS.DIMENSION)]) {
                        dimCount++;
                    }

                    for(int jj=0; jj < descBorder; jj++) {
                        if(matches[jj]) {   //additional words matched
                            additionalwords[jj]++;
                        } else {
                            //try just a single word
                            if(INDWORD.matcher(row[jj]).matches()) {
                                additionalwords[jj]++;
                            }
                        }
                    }
                }

                if(descCount >= maxChk-1) {
                    System.out.println("FOUND DESC: "+tempMapping.get(COLUMNS.DESCRIPTION));
                }

                if(unitCount >= maxChk-1) {
                    System.out.println("FOUND UNIT: "+tempMapping.get(COLUMNS.UNIT));
                }

                if(qtyCount >= maxChk-1) {
                    System.out.println("FOUND QTY: "+tempMapping.get(COLUMNS.QUANTITY));
                }

                if(dimCount >= maxChk-1) {
                    System.out.println("FOUND DIM: "+tempMapping.get(COLUMNS.DIMENSION));
                }

                //find variance
                //two highest->desc
                //two lowest->subpaartof
                List<Pair<Integer, Integer>> variance = new ArrayList<Pair<Integer, Integer>>();
                for(int ii=0; ii <= descBorder; ii++) {
                    if(additionalwords[ii] >= maxChk - 1) {
                        Set<String> varpart = new HashSet<String>();
                        for(int jj=0; jj < chkRows.size(); jj++) {
                            varpart.add(chkRows.get(jj)[ii]);
                        }
                        variance.add(new Pair<Integer, Integer>(varpart.size(), ii));
                    }
                }
                variance.sort(new Comparator<Pair<Integer, Integer>>() {
                    public int compare(Pair<Integer, Integer> o1, Pair<Integer, Integer> o2) {
                        return o1.getKey().compareTo(o2.getKey());
                    }
                });

                int maxVar = 0;
                Pair<Integer,Integer> descvariance = variance.get(variance.size() - 1);
                Iterator<Pair<Integer, Integer>> order = variance.iterator();
                while(order.hasNext()) {
                    Pair<Integer,Integer> pair = order.next();
                    if(pair.getKey() < descvariance.getKey() && pair.getValue() != descBorder) {
                        List<Integer> colsMap = null;
                        if(!sideMapping.containsKey(COLUMNS.SUBPARTOF)) {
                            colsMap = new ArrayList<Integer>();
                        } else {
                            colsMap = sideMapping.get(COLUMNS.SUBPARTOF);
                        }
                        colsMap.add(pair.getValue());
                        sideMapping.put(COLUMNS.SUBPARTOF, colsMap);
                    }
                }
                //check that we dont rull up desc

                System.out.println(variance);
                System.out.println(sideMapping);
            }
        }

        //confirm mappings with sampling of text
        int knownCount = tempMapping.size();
        List<String[]> nonEmpty = new ArrayList<String[]>();

        return null;
    }

    private static Map<COLUMNS, Integer> mergeRowHeaders(Map<COLUMNS, Integer> first, Map<COLUMNS, Integer> second) {
        Iterator<COLUMNS> firstMatchesIt = first.keySet().iterator();
        Set<COLUMNS> secondMatches = second.keySet();
        while(firstMatchesIt.hasNext()) {
            COLUMNS match = firstMatchesIt.next();
            boolean found = false;
            Iterator<COLUMNS> secMatchesIt = secondMatches.iterator();
            while(secMatchesIt.hasNext()) {
                COLUMNS sec = secMatchesIt.next();
                if(match == sec) {      //remove any matching from second
                    secMatchesIt.remove();
                    found = true;
                }
            }
        }
        if(secondMatches.size() > 0) {
            first.putAll(second);
        }
        return first;
    }

    private static Map<COLUMNS, Integer> matchRowMajorColumns(String[] cells) {
        Map<COLUMNS, Integer> ret = new HashMap<COLUMNS, Integer>();
        for(int ii=0; ii < cells.length; ii++) {
            int match = matchMajorColumn(cells[ii]);
            if(match >= 0) {
                ret.put(MAPPING_NAME[match], ii);
            }
        }
        return ret;
    }

    private static int matchMajorColumn(String cell) {
        int ret = -1;
        for(int ii=0; ii < POT_MAPPING_MAJOR.length; ii++) {
            if(POT_MAPPING_MAJOR[ii].matcher(cell).matches()) {
                ret = ii;
                break;
            }
        }
        return ret;
    }

    private static boolean matchInterior(String chk, INTERIORS specific) {
        boolean match = false;
        switch(specific) {
            case WORDGROUP:
                match = WORD.matcher(chk).find();
                break;
            case QTY:
                match = QUANT.matcher(chk).matches();
                break;
            case DIM:
                match = DIM.matcher(chk).matches();
                break;
            case UNITS:
                match = matchUnit(chk);
                break;
            default: break;
        }
        return match;
    }

    private static boolean matchInteriorToCol(String chk, COLUMNS col) {
        return matchInterior(chk, colToInterior(col));
    }

    private static boolean[] matchInteriorsToCol(String[] chk, COLUMNS col) {
        boolean[] ret = new boolean[chk.length];
        INTERIORS interior = colToInterior(col);
        for(int ii=0; ii <chk.length; ii++) {
            ret[ii] = false;
            if(matchInterior(chk[ii], interior)) {
                ret[ii] = true;
            }
        }
        return ret;
    }

    private static INTERIORS colToInterior(COLUMNS col) {
        INTERIORS match = INTERIORS.WORDGROUP;
        switch (col) {
            case QUANTITY:
                match = INTERIORS.QTY;
                break;
            case UNIT:
                match = INTERIORS.UNITS;
                break;
            case DIMENSION:
                match = INTERIORS.DIM;
                break;
            default: break;
        }

        return match;
    }

    private static boolean matchUnit(String chk) {
        boolean match = false;
        for(int ii=0; ii < UNITS.length; ii++) {
            if(chk.equals(UNITS[ii])) {
                match = true;
                break;
            }
        }

        return match;
    }

       /*
    ** GENERAL OUTLINE
    * FIND BY UNIT - QTY PAIR
    * || FIND BY COLUMN NAMES
    * || FIND BY DESC
    *   - find first occurence
    *   - count ahead n steps at least 10 confirmed columns with data in others!
    *   - match individuals in every column
    *   - in order: DESC, DIM, SUB,NOTES
    *   -
    *
    * INDIVIDUAL HEURISTICS
    * DESCRIPTION
    *  - prefer to left of units
    *  - match words of atleast 3
    *  DIMENSION
     *  - regex with ^$
     * SUBPARTOF
     *  - demand to left of description
     *  - match word
     *  - get unique count of all possible cols to left of desc and desc
     *  - confirm desc is highest variance then
     *  - if > 1 cols and all sim variance then all subparts
     *  - else pick closest to desc var in range and add to desc with <special char escape>
     *  - not highest variance then match name to confirm or pick highest 2 var and combine to become new desc
     *  - rest are subparts
     * NOTES
     *  - words of 4 to right of units OR desc
     * UNIT
     *  - match units or (^qty units$)
     */
}