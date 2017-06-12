package buildgrid.demo.features;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.*;
import java.nio.*;

import static java.util.Arrays.copyOfRange;

/**
 * Created by Cameron on 6/6/2017.
 */
public class CSVRip {
    private static final char[] DELIMS = {'\t', '\n'};
    private static final char[][] CATS = {"flooritem".toCharArray(), "householditem".toCharArray(), "furniture".toCharArray(), "buildingfeature".toCharArray(), "buildingmaterial".toCharArray()};
    private static final int BIGSIZE = 1000000;
    private static final int SIZE = 131072;

    private static char[] resize(char[] resize, int newSize) {
        char[] ret = new char[newSize];
        for(int ii=0; ii < resize.length; ii++) {
            if(ii >= newSize ) {
                break;
            }

            ret[ii] = resize[ii];
        }

        return ret;
    }


    private static boolean matchCategories(char[] process, int startInd, int endInd) {
        boolean match = false;
        char[] chk;
        for(int ii=0; ii < CATS.length; ii++) {
            chk = CATS[ii];
            int jj = 0;
            for(int hh=startInd; hh < endInd; hh++) {
                if(chk[jj] == process[hh]) {
                    jj++;
                    //we matched it all
                    if(jj == chk.length) {
                        match = true;
                        return match;
                    }
                    continue;
                }
                jj=0;
            }
        }

        return match;
    }

    private static char[] extractLines(CharBuffer work) {
        boolean slurp = true;   //assume we start at first column
        int startInd = 0;
        int endInd = -1;
        int ctr = 0;
        int colctr = 0;
        char next;
        char[] process = new char[1];
        int copyMax = 1024;
        char[] copy = new char[copyMax];
        char[] piece = new char[copyMax];
        boolean hasPiece = false;
        int pieceLength = 0;
        int copyStart = 0;
        int copyLength = 0;
        int slicelen = 0;
        while( work.hasRemaining( ) )
        {
            int len = work.remaining();
            if(len > 4096) {
                len = 4096;
            }

            if(hasPiece) {
                char[] tmp = Arrays.copyOfRange(process, startInd, process.length);
                process = new char[len+tmp.length];
                System.arraycopy(tmp, 0, process, 0, tmp.length);
                hasPiece = false;
                work.get(process, tmp.length, len);
                len += tmp.length;
                colctr=0;
            } else {
                process = new char[len];
                startInd = endInd = -1;
                work.get(process, 0, len);
            }
            for(int ii=0; ii < len; ii++) {
                next = process[ii];
                if(next == DELIMS[1]) {
                    colctr = 0;
                    startInd = ii;
                }

                if(next == DELIMS[0]) {
                    if(colctr == 0) {
                        endInd = ii;
                    }
                    colctr++;
                }

                if((startInd > -1 && endInd > -1) && matchCategories(process, startInd, endInd)) {
                    slicelen = endInd - startInd;

                    if(copyStart + slicelen >= copyMax) {
                        int nl = Math.max( copyStart + slicelen, copyMax*2 );
                        copy = resize(copy, nl);
                        copyMax = nl;
                    }
                    System.arraycopy(process, startInd, copy, copyStart, slicelen);
                    copyStart += slicelen;
                    copyLength += slicelen;
                    startInd = endInd = -1;
                }
            }
            //rolls over into next block
            //copy what we have
            if(endInd == -1 && startInd >= 0) {
                hasPiece = true;
            }
        }

        return resize(copy, copyLength);
    }

    public static void extractCandidates(File file) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter("B:/zetsa/csv/candidates-out.txt"));

        FileInputStream f = new FileInputStream( file );
        FileChannel ch = f.getChannel( );
        ByteBuffer bb = ByteBuffer.allocateDirect( BIGSIZE );
        byte[] barray = new byte[SIZE];
        int nRead, nGet;
        int ii=0;
        while ( (nRead=ch.read( bb )) != -1 )
        {
            if ( nRead == 0 )
                continue;
            bb.position( 0 );
            bb.limit( nRead );
            while( bb.hasRemaining( ) )
            {
                nGet = Math.min( bb.remaining( ), SIZE );
                bb.get( barray, 0, nGet );

                char[] extract = extractLines(Charset.forName("UTF-8").decode(ByteBuffer.wrap(barray)));
                if(extract.length > 0) {
                    for(int hh=0; hh < extract.length; hh++) {
                        bw.append(extract[hh]);
                    }
                }
            }
            bb.clear( );
            ii++;
            if(ii % 1000 == 0 && ii > 0) {
                System.out.println(ii +" mb processed");
                bw.flush();
            }
        }

        bw.close();
    }

    public static void main(String[] args) throws IOException {
        File csv = new File("B:/zetsa/dls/candidates/candidates.csv");
        extractCandidates(csv);
        /*
        FileInputStream fs = new FileInputStream(csv);
        BufferedReader br = new BufferedReader(new InputStreamReader(fs));
        System.out.println(br.readLine());
        System.out.println(br.readLine());
        System.out.println(br.readLine());*/
    }

    public static void copyCardinal(HashMap<String,Integer> parts, ArrayList<String> items) {
        for(int jj=0; jj < items.size(); jj++) {
            String elm = items.get(jj);
            if(parts.containsKey(elm)) {
                parts.put(elm, parts.get(elm) + 1);
            } else {
                parts.put(elm, 1);
            }
        }
    }
}

class CardinalCompare implements Comparator<String> {
    Map<String, Integer> base;
    public CardinalCompare(Map<String,Integer> base) {
        this.base = base;
    }

    public int compare(String a, String b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        } // returning 0 would merge keys
    }
}