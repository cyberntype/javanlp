package buildgrid.extract;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Collections;
import java.util.regex.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by Cameron on 6/9/2017.
 */
public class Tokenize {
    static Pattern Contractions = Pattern.compile("(?i)(\\w+)(n['’′]t|['’′]ve|['’′]ll|['’′]d|['’′]re|['’′]s|['’′]m)$");
    static Pattern Whitespace = Pattern.compile("[\\s\\p{Zs}]+");
    static String punctChars = "['\"‘’.?!…,:;]";

    static String units = "cm|mm|yd|\"|\'|feet|ft|in";

    static String quickMeasure = "\\d+(?:\\.\\d+)?[\\s-]*(?:\\d+)?(?:\\/\\d+)?(?:"+units+")(?:\\s*x\\s*|\\s*by\\s*)?(?:\\d+(?:\\.\\d+)?[\\s*-]*(?:\\d+(?:\\/\\d+)?)?(?:"+units+")?)?";
    static String rollup = "\\s*(?:x|X||by)\\s*";

    static String urlStart1 = "(?:https?://|\\bwww\\.)";
    static String commonTLDs = "(?:com|org|edu|gov|net|mil|aero|asia|biz|cat|coop|info|int|jobs|mobi|museum|name|pro|tel|travel|xxx)";
    static String ccTLDs = "(?:ac|ad|ae|af|ag|ai|al|am|an|ao|aq|ar|as|at|au|aw|ax|az|ba|bb|bd|be|bf|bg|bh|bi|bj|bm|bn|bo|br|bs|bt|" +
            "bv|bw|by|bz|ca|cc|cd|cf|cg|ch|ci|ck|cl|cm|cn|co|cr|cs|cu|cv|cx|cy|cz|dd|de|dj|dk|dm|do|dz|ec|ee|eg|eh|" +
            "er|es|et|eu|fi|fj|fk|fm|fo|fr|ga|gb|gd|ge|gf|gg|gh|gi|gl|gm|gn|gp|gq|gr|gs|gt|gu|gw|gy|hk|hm|hn|hr|ht|" +
            "hu|id|ie|il|im|in|io|iq|ir|is|it|je|jm|jo|jp|ke|kg|kh|ki|km|kn|kp|kr|kw|ky|kz|la|lb|lc|li|lk|lr|ls|lt|" +
            "lu|lv|ly|ma|mc|md|me|mg|mh|mk|ml|mm|mn|mo|mp|mq|mr|ms|mt|mu|mv|mw|mx|my|mz|na|nc|ne|nf|ng|ni|nl|no|np|" +
            "nr|nu|nz|om|pa|pe|pf|pg|ph|pk|pl|pm|pn|pr|ps|pt|pw|py|qa|re|ro|rs|ru|rw|sa|sb|sc|sd|se|sg|sh|si|sj|sk|" +
            "sl|sm|sn|so|sr|ss|st|su|sv|sy|sz|tc|td|tf|tg|th|tj|tk|tl|tm|tn|to|tp|tr|tt|tv|tw|tz|ua|ug|uk|us|uy|uz|" +
            "va|vc|ve|vg|vi|vn|vu|wf|ws|ye|yt|za|zm|zw)";    //TODO: remove obscure country domains?
    static String urlStart2 = "\\b(?:[A-Za-z\\d-])+(?:\\.[A-Za-z0-9]+){0,3}\\." + "(?:" + commonTLDs + "|" + ccTLDs + ")" + "(?:\\." + ccTLDs + ")?(?=\\W|$)";
    static String urlBody = "(?:[^\\.\\s<>][^\\s<>]*?)?";
    static String urlExtraCrapBeforeEnd = "(?:" + punctChars  + ")+?";
    static String urlEnd = "(?:\\.\\.+|[<>]|\\s|$)";
    public static String url = "(?:" + urlStart1 + "|" + urlStart2 + ")" + urlBody + "(?=(?:" + urlExtraCrapBeforeEnd + ")?" + urlEnd + ")";

    static String timeLike = "\\d+(?::\\d+){1,2}";

    static String separators = "(?:--+|―|—|~|–|=)";
    static String thingsThatSplitWords = "[^\\s\\.,?\"]";
    static String Bound = "(?:\\W|^|$)";
    public static String Email = "(?<=" + Bound + ")[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}(?=" + Bound + ")";
    public static String OR(String... parts) {
        String prefix="(?:";
        StringBuilder sb = new StringBuilder();
        for (String s:parts){
            sb.append(prefix);
            prefix="|";
            sb.append(s);
        }
        sb.append(")");
        return sb.toString();
    }
    static Pattern ProtectMeasure = Pattern.compile(quickMeasure);
    static Pattern ProtectRoll = Pattern.compile(rollup);
    static Pattern Protected = Pattern.compile(
            OR(
                    url,
                    timeLike,
                    separators
            ));

    private static class Pair<T1, T2> {
        public T1 first;
        public T2 second;

        public Pair(T1 x, T2 y) {
            first = x;
            second = y;
        }
    }

    private static List<String> simpleTokenize(String text) {
        int textLength = text.length();
        Matcher matches = ProtectMeasure.matcher(text);
        List<List<String>> bads = new ArrayList<List<String>>();    //linked list?
        List<Pair<Integer, Integer>> badSpans = new ArrayList<Pair<Integer, Integer>>();
        int ctr = 0;
        int difference = 0;
        int prevEnd = -10;
        while (matches.find()) {
            // The spans of the "bads" should not be split.
            if (matches.start() != matches.end()) { //unnecessary?
                List<String> bad = new ArrayList<String>(1);
                difference = matches.start() - prevEnd;
                if(difference < 10) {
                    String chk = text.substring(prevEnd, matches.start());
                    Matcher ver = ProtectRoll.matcher(chk);
                    if(ver.matches()) {
                        List<String> hold = bads.get(ctr - 1);
                        Pair<Integer, Integer> hold2 = badSpans.get(ctr - 1);
                        bads.remove(ctr - 1);
                        badSpans.remove(ctr - 1);

                        bad.add(text.substring(hold2.first, matches.end()));
                        bads.add(bad);
                        badSpans.add(new Pair<Integer, Integer>(hold2.first, matches.end()));
                        prevEnd = matches.end();
                        continue;
                    }
                }

                bad.add(text.substring(matches.start(), matches.end()));
                bads.add(bad);
                badSpans.add(new Pair<Integer, Integer>(matches.start(), matches.end()));
                prevEnd = matches.end();
                ctr++;
            }
        }

        matches = Protected.matcher(text);
        while (matches.find()) {
            // The spans of the "bads" should not be split.
            if (matches.start() != matches.end()) { //unnecessary?
                List<String> bad = new ArrayList<String>(1);
                bad.add(text.substring(matches.start(), matches.end()));
                bads.add(bad);
                badSpans.add(new Pair<Integer, Integer>(matches.start(), matches.end()));
            }
        }

        List<Integer> indices = new ArrayList<Integer>(2 + 2 * badSpans.size());
        indices.add(0);
        for (Pair<Integer, Integer> p : badSpans) {
            indices.add(p.first);
            indices.add(p.second);
        }
        Collections.sort(indices);
        indices.add(textLength);

        List<List<String>> splitGoods = new ArrayList<List<String>>(indices.size()/2);
        for (int i=0; i<indices.size(); i+=2) {
            String goodstr = text.substring(indices.get(i),indices.get(i+1));
            List<String> splitstr = Arrays.asList(goodstr.trim().split(" |,|;"));
            splitGoods.add(splitstr);
        }

        List<String> zippedStr= new ArrayList<String>();
        int i;
        for(i=0; i < bads.size(); i++) {
            zippedStr = addAllnonempty(zippedStr,splitGoods.get(i));
            zippedStr = addAllnonempty(zippedStr,bads.get(i));
        }
        zippedStr = addAllnonempty(zippedStr,splitGoods.get(i));

        return zippedStr;
    }

    private static List<String> addAllnonempty(List<String> master, List<String> smaller){
        for (String s : smaller){
            String strim = s.trim();
            if (strim.length() > 0)
                master.add(strim);
        }
        return master;
    }

    public static void main(String[] args) {
        String line = "Concrete Work\n" +
                "Footing/Foundation\n" +
                "Building 1\n" +
                "F1 - 4.0'x4.0'x18\" w/ 6-#5 e.w.\n" +
                "F2 - 5.0'x5'x12\" w/ 6-#5 e.w.\n" +
                "Wall Footing - 2' wide\n" +
                "Wall Footing - 3' wide\n" +
                "Building 2\n" +
                "F1 - 3.0'x3.0'x18\" w/ 4-#5 e.w.\n" +
                "F2 - 5.5'x5.5'x15\" w/ 6-#5 e.w.\n" +
                "F3 - 8.0'x8'x18\" w/ 11-#5 e.w.\n" +
                "Wall Footing - 2' wide\n" +
                "Wall Footing - 4' wide\n" +
                "\n" +
                "Grade Beams\n" +
                "Building 1\n" +
                "GB1 - 24\"x18\" w/ 4-#6 @ top & bot w/ #3 stirrup 6\" o.c.\n" +
                "Building 2\n" +
                "GB1 - 30\"x18\" w/ 6-#6 @ top & bot w/ #3 stirrup 6\" o.c.\n" +
                "\n" +
                "Slab on Grade\n" +
                "Building 1\n" +
                "4\" concrete slab (2,500psi in 28 days) w/ #4 @ 16\" o.c. e.w. on 10 mil moisture barrier between two layersof 2\" aggregate\n" +
                "Building 2\n" +
                "4\" concrete slab (2,500psi in 28 days) w/ #4 @ 16\" o.c. e.w. on 10 mil moisture barrier between two layers of 2\" aggregate\n" +
                "\n" +
                "Rebar Work\n" +
                "Footing/Foundation\n" +
                "Building 1\n" +
                "Wall Footing - 2' wide\n" +
                "#5 conti. @ 12\" o.c. top & bot.\n" +
                "#5 @ 12\" o.c. top & bot.\n" +
                "#5 dowels\n" +
                "Wall Footing - 3' wide\n" +
                "#5 conti. @ 12\" o.c. top & bot.\n" +
                "#5 @ 12\" o.c. top & bot.\n" +
                "#5 dowels\n" +
                "Additional\n" +
                "4-#7\n" +
                "4-#7 vert. reinf jamb bar\n" +
                "6-#8 vert. reinf jamb bar\n" +
                "\n" +
                "Building 2\n" +
                "Wall Footing - 2' wide\n" +
                "#5 conti. @ 12\" o.c. top & bot.\n" +
                "#5 @ 12\" o.c. top & bot.\n" +
                "#5 dowels\n" +
                "Wall Footing - 4' wide\n" +
                "#5 conti. @ 12\" o.c. top & bot.\n" +
                "#5 @ 12\" o.c. top & bot.\n" +
                "#5 dowels\n" +
                "\n" +
                "Slab on Grade\n" +
                "Building 1\n" +
                "#4 dowels\n" +
                "Building 2\n" +
                "#4 dowels";
        System.out.println(quickMeasure);
        //System.out.println(simpleTokenize(line));
    }
}