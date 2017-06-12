package buildgrid.demo;

import buildgrid.demo.features.CSVRip;
import buildgrid.graph.*;
import buildgrid.extract.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Cameron on 6/7/2017.
 */
public class NLPPipeline {
    private static void graphStuff() throws IOException {
        GraphBuilder gb = new GraphBuilder("B:/zetsa/csv/buildmats3.txt", "B:/zetsa/csv/primematerials.txt");
        gb.readProcessFile(new File("B:/zetsa/csv/process.txt"));
        HashSet<String[]> extract = gb.readMaterialFiles();
        gb.buildMaterialsGraph(extract);
        gb.getDistances(extract);
    }

    public static void main(String[] args) throws IOException {
        FileParse fp = new FileParse(new File("./resources/takeoff_1.xlsx"), FileParse.FileSpecifier.XLSX);
        List<String[]> rows = fp.extractTables();
        TakeoffPreparse.process(rows);
      //  System.out.println(fp.preprocess());





     //   CSVRip.extractCandidates(new File("B:/zetsa/dls/candidates/candidates.csv"));
    }
}
