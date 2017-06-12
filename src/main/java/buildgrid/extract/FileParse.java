package buildgrid.extract;

import com.sun.xml.internal.ws.api.pipe.FiberContextSwitchInterceptor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by Cameron on 6/6/2017.
 **/
public class FileParse {
    static final int MAX_PARSE_LINES = 2500;

    public enum FileSpecifier {DOC, DOCX, XLS, XLSX, PDF}

    private FileInputStream content;
    private FileSpecifier fileType;
    private String parse;       //csv string

    public FileParse(File file, FileSpecifier specifier) throws IOException {
        FileInputStream fs = new FileInputStream(file);
        this.content = fs;
        this.fileType = specifier;
        this.parse = null;
    }

    public List<String[]>  extractTables() throws IOException {
        List<String[]>  summon = null;
        switch (this.fileType) {
            case XLS:
                summon = extractTablesXSL();
                break;
            case XLSX:
                summon = extractTablesXSLX();
                break;
            default:    //exception?
                break;
        }
        return summon;
    }

    private List<String[]>  extractTablesXSL() throws IOException {
        Workbook wb = new HSSFWorkbook(this.content);
        return iterateTablesFromExcel(wb);
    }

    private List<String[]>  extractTablesXSLX() throws IOException {
        Workbook wb = new XSSFWorkbook(this.content);
        return iterateTablesFromExcel(wb);
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
/*
    public String preprocess() throws IOException {
        List<String[]>  original = this.extractTables();
        Iterator<String[]> rows = original.iterator();
        //try to find first 'clustering' of units and search from there for column headers
        int hasFoundUnit = -1;
        int foundctr = 4;
        int certainUnitCol = -1;
        int rowCtr = 0;
        String[][] sampleRows = new String[4][];
        String[] cells;
        while(rows.hasNext()) {
            cells = rows.next();
            if(hasFoundUnit > -1 && matchUnit(cells[hasFoundUnit])) {
                foundctr--;
                sampleRows[foundctr] = cells;
                if(foundctr <= 0) {
                   certainUnitCol = rowCtr - 10;
                   break;   //found cluster in same col
               }
            } else {
                foundctr = 4;   //reset ctr
                for (int ii = 0; ii < cells.length; ii++) {
                    if (matchUnit(cells[ii])) {
                        hasFoundUnit = ii;
                    }
                }
            }
            rowCtr++;
        }

        if(certainUnitCol < 0) {
            return null; //giveup
        }

        Map<String, Integer> colMappings = new HashMap<String, Integer>();
        int[] maxcounts = new int[10];
        int startSearch = ((certainUnitCol) > 0) ? certainUnitCol : 0;
        int matchcount = 0;
        int startDescCol = -1;
        int certDestCol = -1;
        for(int ii=startSearch; ii < original.size(); ii++) {
            cells = original.get(ii);
            //try to find quantity or dimension next
            if(matchTitleUnit(cells[certainUnitCol])) {
                int rightCount = sampleRows.length;
                int leftCount = sampleRows.length;
                for(int jj=0; jj < sampleRows.length; jj++) {
                    if(certainUnitCol > 0 && matchQuatities(sampleRows[jj][certainUnitCol - 1])) {
                        leftCount--;
                    }
                    if(certainUnitCol < cells.length && matchQuatities(sampleRows[jj][certainUnitCol + 1])) {
                        rightCount--;
                    }
                }
                if(rightCount < sampleRows.length || leftCount < sampleRows.length) {
                    colMappings.put("unit", certainUnitCol);
                    startDescCol = certainUnitCol - 1;
                    if(leftCount <= rightCount) {
                        colMappings.put("quantity", certainUnitCol - 1);
                        startDescCol--;
                    } else {
                        colMappings.put("quantity", certainUnitCol + 1);
                    }
                } else {
                    return null;    //edge case
                }
            }

            //now match description starting from the left of units or ar 0
            if(startDescCol > -1) {

                for(int jj = startDescCol; jj >= 0; jj--) {
                    if(matchTitleDescription(cells[jj])) {
                        int descCtr = sampleRows.length;
                        for(int hh=0; hh < sampleRows.length; hh++) {
                            if (matchWords(sampleRows[hh][jj])) {
                                descCtr--;
                            }
                        }

                        if(descCtr <= sampleRows.length / 2) {
                            colMappings.put("description", jj);
                            certDestCol = jj;
                            break;
                        }
                    }
                }

                if(certDestCol < 0) {   //
                    return null; //edge case
                }
            }

        }
return null;
    }
*/
    private List<String[]>  iterateTablesFromExcel(Workbook wb) {
        int numSheets = wb.getNumberOfSheets();
        int parseCount = 0;
        boolean parseReached = false;
        List<String[]> table = null;
        StringBuilder strBuild = new StringBuilder();

        for(int ii=0; ii < numSheets; ii++ ) {
            Sheet sheet = wb.getSheetAt(ii);
            Iterator<Row> rowIterator = sheet.iterator();

            if(table == null) {
                 table = new ArrayList<String[]>(Math.min(MAX_PARSE_LINES, sheet.getLastRowNum() * numSheets));
            }

            while(rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Iterator<Cell> cellIterator = row.cellIterator();
                StringBuilder line = new StringBuilder();
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    switch (cell.getCellTypeEnum()) {
                        case NUMERIC:
                            line.append(cell.getNumericCellValue());
                            break;
                        case STRING:
                            line.append(cell.getStringCellValue().toLowerCase());
                            break;
                        default:
                            break;
                    }
                    line.append('\t');
                }
                table.add(line.toString().split("\t"));     //might be slow
                parseCount++;
            }
            strBuild.append("\r\n");
            if(parseCount > MAX_PARSE_LINES) {
                parseReached = true; //do something with this
                break;
            }

            //just get first for now
            break;
        }

        return table;
    }

    private static boolean matchTitleUnit(String chk) {
        boolean match = false;
        switch(chk.length()) {
            case 5:
                match = chk.equals("units");
                break;
            case 4:
                match = chk.equals("unit") || chk.equals("unts");
                break;
            case 3:
                match = chk.equals("unt");
                break;
            case 2:
                match = chk.equals("un");
                break;
            default:
                break;
        }

        return match;
    }

    private static boolean matchTitleDescription(String chk) {
        boolean match = false;
        switch(chk.length()) {
            case 11:
                match = chk.equals("DESCRIPTION");
                break;
            case 8:
                match = chk.equals("material");
                break;
            case 4:
                match = chk.equals("desc");
                break;
            default:
                break;
        }

        return match;
    }


}
