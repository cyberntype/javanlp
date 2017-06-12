package buildgrid.graph;

import javafx.util.Pair;
import jdk.internal.org.objectweb.asm.util.TraceSignatureVisitor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.codehaus.plexus.util.StringOutputStream;
import org.neo4j.driver.v1.*;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;
import sun.reflect.generics.tree.Tree;

import static org.neo4j.driver.v1.Values.parameters;

/**
 * Created by Cameron on 6/8/2017.
 */
public class GraphBuilder {
    private static final String[] STOPWORDS = {".", ",", "?", "!", "'", "\"", "\'\'", "`", "``", "*", "-", "/", "+", "a", "about", "above", "according", "across", "after", "afterwards",
            "again", "against", "albeit", "all", "almost", "alone", "along", "already", "also", "although", "always", "am", "among", "amongst", "an", "and", "another", "any", "anybody",
            "anyhow", "anyone", "anything", "anyway", "anywhere", "apart", "are", "around", "as", "at", "av", "be", "became", "because", "become", "becomes", "becoming", "been", "before",
            "beforehand", "behind", "being", "below", "beside", "besides", "between", "beyond", "both", "but", "by", "can", "cannot", "canst", "certain", "cf", "choose", "contrariwise",
            "can", "cos", "could", "cu", "day", "do", "does", "doesn't", "doing", "dost", "doth", "double", "down", "dual", "during", "each", "either", "else", "elsewhere", "enough", "et",
            "etc", "even", "ever", "every", "everybody", "everyone", "everything", "everywhere", "except", "excepted", "excepting", "exception", "exclude", "excluding", "exclusive",
            "far", "farther", "farthest", "few", "ff", "first", "for", "formerly", "forth", "forward", "from", "front", "further", "furthermore", "furthest", "get", "go", "had",
            "halves", "hardly", "has", "hast", "hath", "have", "he", "hence", "henceforth", "her", "here", "hereabouts", "hereafter", "hereby", "herein", "hereto", "hereupon", "hers",
            "herself", "him", "himself", "hindmost", "his", "hither", "hitherto", "how", "however", "howsoever", "i", "ie", "if", "in", "inasmuch", "inc", "include", "includes", "included",
            "including", "indeed", "indoors", "inside", "insomuch", "instead", "into", "inward", "inwards", "is", "it", "its", "itself", "just", "kind", "kg", "km", "last", "latter",
            "latterly", "less", "lest", "let", "like", "little", "ltd", "many", "may", "maybe", "me", "meantime", "meanwhile", "might", "moreover", "most", "mostly", "more", "mr",
            "mrs", "ms", "much", "must", "my", "myself", "namely", "need", "neither", "never", "nevertheless", "next", "no", "nobody", "none", "nonetheless", "noone", "nope", "nor",
            "not", "nothing", "notwithstanding", "now", "nowadays", "nowhere", "of", "off", "often", "ok", "on", "once", "one", "only", "onto", "or", "other", "others", "otherwise",
            "ought", "our", "ours", "ourselves", "out", "outside", "over", "own", "per", "perhaps", "plenty", "provide", "quite", "rather", "really", "round", "said", "sake", "same",
            "sang", "save", "saw", "see", "seeing", "seem", "seemed", "seeming", "seems", "seen", "seldom", "selves", "sent", "several", "shalt", "she", "should", "shown", "sideways",
            "since", "slept", "slew", "slung", "slunk", "smote", "so", "some", "somebody", "somehow", "someone", "something", "sometime", "sometimes", "somewhat", "somewhere", "spake",
            "spat", "spoke", "spoken", "sprang", "sprung", "stave", "staves", "still", "such", "supposing", "than", "that", "the", "thee", "their", "them", "themselves", "then", "thence",
            "thenceforth", "there", "thereabout", "thereabouts", "thereafter", "thereby", "therefore", "therein", "thereof", "thereon", "thereto", "thereupon", "these", "they", "this",
            "those", "thou", "though", "thrice", "through", "throughout", "thru", "thus", "thy", "thyself", "till", "to", "together", "too", "toward", "towards", "ugh", "unable", "under",
            "underneath", "unless", "unlike", "until", "up", "upon", "upward", "upwards", "us", "use", "used", "using", "very", "via", "vs", "want", "was", "we", "week", "well", "were",
            "what", "whatever", "whatsoever", "when", "whence", "whenever", "whensoever", "where", "whereabouts", "whereafter", "whereas", "whereat", "whereby", "wherefore", "wherefrom",
            "wherein", "whereinto", "whereof", "whereon", "wheresoever", "whereto", "whereunto", "whereupon", "wherever", "wherewith", "whether", "whew", "which", "whichever", "whichsoever",
            "while", "whilst", "whither", "who", "whoa", "whoever", "whole", "whom", "whomever", "whomsoever", "whose", "whosoever", "why", "will", "wilt", "with", "within", "without",
            "worse", "worst", "would", "wow", "ye", "yet", "year", "yippee", "you", "your", "yours", "yourself", "yourselves"};

    private static final String WORDREGEX = "[a-zA-Z]+";
    private static final String INSERT = "MERGE (w0:Word {word: $wordzero}) " +
            "MERGE (w1:Word {word: $wordone}) " +
            "CREATE UNIQUE (w0)-[:LINK {score: $score}]->(w1)";
    private static final String[] EXCLUDE = {"steel","iron","aluminum","metal","copper","plastic","glass","nickel","silver","aluminium","chrome","gold","water"};
    private static final String MERGE = "MATCH (s:Word {word: $word})\n" +
            "MATCH path=(s)-[r:LINK*..3]->(neighbor)\n" +
            "WHERE NONE(x IN tail(NODES(path)) WHERE x.word IN [$words] )\n" +
            "WITH COLLECT(distinct neighbor.word) AS col, \n" +
            "round(REDUCE(dist = 0.0, d in r | dist + d.score) * 10)/10 as distance\n" +
            "WHERE distance < 1.0\n" +
            "RETURN col, distance";

    private File materials;
    private File features;
    private Driver driver;
    private Session session;

    public GraphBuilder(String materialFile, String featureFile) {
        this.materials = new File(materialFile);
        this.features = new File(featureFile);
        this.driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "root"));
    }

    public HashSet<String> readProcessFile(File process) throws IOException {
        FileInputStream fs = new FileInputStream(process);
        BufferedReader br = new BufferedReader(new InputStreamReader(fs));
        String rule = "\\w+(?:s|es|en|ing)$";        //in this order
        String match = "(?:s|es|en|ing)$";        //in this order
        Set<String> reduced = new TreeSet<String>();
        String line;
        while((line = br.readLine())!=null) {
            String[] parts = line.split(":");
            if(parts[0].matches(rule)) {
         //       System.out.println("MATCH "+parts[0]);
                parts[0] = parts[0].replaceAll(match, "");
          //      System.out.println(parts[0]);
            }
            reduced.add(parts[0]);
        }
        Iterator<String> kset = reduced.iterator();
        while(kset.hasNext()) {
            System.out.println(kset.next());
        }

        return null;
    }

    public HashSet<String[]> readMaterialFiles() throws IOException {
        HashSet<String> primes = new HashSet<String>();
        FileInputStream fsf = new FileInputStream(this.features);
        BufferedReader brf = new BufferedReader(new InputStreamReader(fsf));
        String line;

        while((line = brf.readLine())!=null) {
            primes.add(line);
        }

        brf.close();

        FileInputStream fs = new FileInputStream(this.materials);
        BufferedReader br = new BufferedReader(new InputStreamReader(fs));
        Map<String, Integer> repeats = new TreeMap<String, Integer>();
        DistComparator bvc = new DistComparator(repeats);
        TreeMap<String, Integer> sorted_map = new TreeMap<String, Integer>(bvc);

        HashSet<String[]> filter = new HashSet<String[]>();
       // SnowballStemmer stemmer = (SnowballStemmer) new englishStemmer();


        while((line = br.readLine())!=null) {
            String entity = line.split(",")[0];
            entity = entity.substring(line.indexOf("buildingmaterial:")+17);
            String[] tokens = entity.split("_");
            String[] keep = new String[tokens.length];
            int ctr = 0;
            for(int ii=0; ii < tokens.length; ii++) {
                if(tokens[ii].matches(WORDREGEX)) {
                    boolean matched = false;
                    for(int jj=0; jj < STOPWORDS.length; jj++) {
                        if(tokens[ii].equals(STOPWORDS[jj])) {
                            matched = true;
                        }
                    }
                    if(!matched && primes.contains(tokens[ii])) {
                //        stemmer.setCurrent(tokens[ii]);
                 //       stemmer.stem();
                 //       tokens[ii] = stemmer.getCurrent();
                        keep[ctr] = tokens[ii];
                        ctr++;
                      /*  if(tokens[ii].length() >= 3 && !primes.contains(tokens[ii])) {
                            if (repeats.containsKey(tokens[ii])) {
                                repeats.put(tokens[ii], repeats.get(tokens[ii]) + 1);
                            } else {
                                repeats.put(tokens[ii], 1);
                            }
                        }*/
                    }
                }
            }
            if(ctr > 0) {
                filter.add(Arrays.copyOf(keep, ctr));
            }
        }

/*
        sorted_map.putAll(repeats);
        Iterator<Map.Entry<String, Integer>> kset = sorted_map.entrySet().iterator();
        while(kset.hasNext()) {
            System.out.println(kset.next().getKey());
        }
*/
        return filter;
    }

    public void getDistances(HashSet<String[]> list) throws IOException {
        List<String> dontSearch = new ArrayList<String>(Arrays.asList(EXCLUDE));
        File out = new File("B:/zetsa/csv/finalout.txt");

        HashSet<String> sparse = new HashSet<String>();
        Iterator<String[]> listtrav = list.iterator();
        while(listtrav.hasNext()) {
            String[] tok = listtrav.next();
            for(int ii=0; ii < tok.length; ii++) {
                sparse.add(tok[ii]);
            }
        }
        int ii=0;
        this.session = this.driver.session();
        Iterator<String> query = sparse.iterator();
        StringBuilder build = new StringBuilder();
        while(query.hasNext()) {
            String queryStr = query.next();
            System.out.println(queryStr);

            try {
                HashMap<String, Double> distances = new HashMap<String, Double>();
                int f = dontSearch.indexOf(queryStr);
                if( f > -1) {
                    dontSearch.remove(f);
                }
                List<Pair<String[], Double>> results = this.getDistances(queryStr, dontSearch);
                if( f > -1) {
                    dontSearch.add(queryStr);
                }
                for(int jj=0; jj < results.size(); jj++) {
                    Pair<String[], Double> rec = results.get(jj);
                    double score = rec.getValue();
                    String[] words = rec.getKey();
                    for(int hh=0; hh<words.length; hh++) {
                        String tok = words[hh].replace("\"","");
                        String key = queryStr+":"+tok;
                        String rev = tok+":"+queryStr;
                        if(distances.containsKey(key)) {
                            if(distances.get(key) > score) {
                                distances.put(key, score);
                            }
                        } else {
                            distances.put(key,score);
                        }

                        if(distances.containsKey(rev) && distances.get(rev) > score) {
                            distances.remove(rev);
                        }
                    }
                }

                Iterator<String> disttrav = distances.keySet().iterator();
                while(disttrav.hasNext()) {
                    String tok = disttrav.next();
                  //  System.out.println(tok);
                    build.append(tok + ":" + distances.get(tok) + "\r\n");
                }
            } catch(Exception e) {
                System.out.println("E"+e);
            }

            ii++;
        }
        FileUtils.writeStringToFile(out, build.toString(), "utf-8");
    }

    public void buildMaterialsGraph(HashSet<String[]> list) {
        Iterator<String[]> items = list.iterator();
        ArrayList<String[][]> inst = new ArrayList<String[][]>();
        HashMap<String, Integer> distances = new HashMap<String, Integer>();

        while(items.hasNext()) {
            String[] tokens = items.next();
            int len = tokens.length - 1;
            if(len == 0) {
                continue;   //skip 1 elem pairs
            }
            String[][] pairs = new String[len][2];
            String prev = null;
            int ctr = 0;
            int jj=0;
            for(int ii=0; ii < tokens.length; ii++) {
                if(jj%2 == 0) {
                    if(jj > 0) {
                        ctr++;
                    }

                    if(prev != null) {
                        pairs[ctr][0] = prev;
                        prev = null;
                        ii--;
                    } else {
                        pairs[ctr][0] = tokens[ii];
                    }
                } else {
                    pairs[ctr][1] = tokens[ii];
                    prev = pairs[ctr][1];
                }
                jj++;
            }
            for(int ii=0; ii < len;ii++) {
                String key = pairs[ii][0]+":"+pairs[ii][1];
                if(distances.containsKey(key)) {
                    distances.put(key, distances.get(key)+1);
                } else {
                    distances.put(key, 1);
                }
            }
            inst.add(pairs);
        }

        this.session = this.driver.session();

        Iterator<String[][]> trav= inst.iterator();
            int ii=0;
            while(trav.hasNext()) {
                String[][] addAll = trav.next();

                for(int jj=0; jj < addAll.length; jj++) {
                    double len = 1.0;
                    String key = addAll[jj][0]+":"+addAll[jj][1];
                    if(distances.containsKey(key)) {
                        int dist = distances.get(key);
                        len = 1.0 / Math.sqrt((double)dist);
                    }
              //      System.out.println(addAll[jj][0]+":"+addAll[jj][1]+":"+ len);
                    addNode(addAll[jj][0], addAll[jj][1], len);
                }
                ii++;
            }
        this.session.close();

    }

    private void addNode(final String from, final String to, final double distance) {
        try {
            this.session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    return createNode(tx, from, to, distance);
                }
            });
        } catch(Exception e) {
            System.out.println(e);
        }
    }

    private List<Pair<String[], Double>> getDistances(final String from, final List<String> exclude) {
        try {
            return this.session.readTransaction(new TransactionWork<List<Pair<String[], Double>>>() {
                @Override
                public List<Pair<String[], Double>> execute(Transaction tx) {
                    List<Pair<String[], Double>> ret = new ArrayList<Pair<String[], Double>>();
                    List<Record> recs = readDistances(tx, from, exclude);
                    for(int ii=0; ii < recs.size(); ii++) {
                        List<String> items = recs.get(ii).get(0).asList(Values.ofToString());
                        String[] con = items.toArray(new String[items.size()]);
                        ret.add(new Pair<String[], Double>(con, recs.get(ii).get(1).asDouble()));
                    }
                    return ret;
                }
            });
        } catch(Exception e) {
            System.out.println(e);
        }

        return null;
    }

    private static int createNode( Transaction tx, String from, String to, double distance )
    {
        tx.run(INSERT, parameters("wordzero", from, "wordone", to, "score", distance));
        return 1;
    }

    private static List<Record> readDistances( Transaction tx, String from, List<String> words )
    {
        StatementResult rs = tx.run(MERGE, parameters("word", from, "words", words));
        return rs.list();
    }


}

class DistComparator implements Comparator<String> {
    Map<String, Integer> base;

    public DistComparator(Map<String, Integer> base) {
        this.base = base;
    }

    // Note: this comparator imposes orderings that are inconsistent with
    // equals.
    public int compare(String a, String b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        } // returning 0 would merge keys
    }
}