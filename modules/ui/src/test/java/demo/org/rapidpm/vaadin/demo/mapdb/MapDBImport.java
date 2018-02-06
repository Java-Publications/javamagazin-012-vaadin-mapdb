package demo.org.rapidpm.vaadin.demo.mapdb;

import org.mapdb.BTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArrayTuple;
import org.rapidpm.dependencies.core.logger.HasLogger;
import org.rapidpm.frp.functions.CheckedFunction;
import org.rapidpm.frp.functions.TriFunction;
import org.rapidpm.frp.model.Quad;
import org.rapidpm.frp.model.Tripel;
import org.rapidpm.frp.model.serial.Pair;
import org.rapidpm.vaadin.javamagazin.PersistenceFunctions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.rapidpm.vaadin.javamagazin.PersistenceFunctions.cachingDB;


/**
 *
 */
public class MapDBImport implements HasLogger {

  public static void main(String[] args) throws IOException {

//    new MapDBImport()
//        .cityStream()
//        .apply("_data/city_ger.csv")
//        .forEach(System.out::println);


    //generate emails
    final List<String> familyNames = Files
        .readAllLines(new File("_data/familynames.csv").toPath(), Charset.forName("UTF-8"));

    final List<String> foreNamesFemale = Files
        .readAllLines(new File("_data/forename_female.csv").toPath(), Charset.forName("UTF-8"));

    final List<String> foreNamesMale = Files
        .readAllLines(new File("_data/forename_Male.csv").toPath(), Charset.forName("UTF-8"));

    final Random rnd = new Random(System.nanoTime());


    final List<Quad<Boolean, String, String, String>> dataSet = familyNames
        .stream()
        .map(n -> new Tripel<>(n, rnd.nextInt(5) + 1, rnd.nextInt(3)))
        .flatMap(
            (Function<
                Tripel<String, Integer, Integer>,
                Stream<Quad<Boolean, String, String, String>>>) family -> {
              final Integer maleCount   = family.getT3();
              final Integer femaleCount = family.getT2() - maleCount;
              final String  familyName  = family.getT1();

              Stream<Quad<Boolean, String, String, String>> maleStream = IntStream
                  .range(0, maleCount)
                  .mapToObj(value -> {
                    final String foreName = foreNamesMale.get(rnd.nextInt(foreNamesMale.size()));
                    final String email    = foreName + "@" + familyName + ".de";
                    return Quad.next(Boolean.TRUE, foreName, familyName, email.toLowerCase());
                  });
              Stream<Quad<Boolean, String, String, String>> femaleStream = IntStream
                  .range(0, femaleCount)
                  .mapToObj(value -> {
                    final String foreName = foreNamesFemale.get(rnd.nextInt(foreNamesFemale.size()));
                    final String email    = foreName + "@" + familyName + ".de";
                    return Quad.next(Boolean.FALSE, foreName, familyName, email.toLowerCase());
                  });
              return Stream.concat(maleStream, femaleStream);
            })
        .collect(Collectors.toList());


    //write to MapDB
    final PersistenceFunctions.DatabasePair databasePair = cachingDB()
        .apply("customer-db");

    // forename, familyname, email, status, birthday


    final BTreeMap<Object[], String> users = databasePair
        .onDiscDB()
        .treeMap("customers")
        .keySerializer(new SerializerArrayTuple(
            Serializer.STRING,
            Serializer.STRING,
            Serializer.STRING
        ))
        .valueSerializer(Serializer.STRING)
        .counterEnable()
        .createOrOpen();


//    dataSet
//        .stream()
//        .map(q -> {
//          final String[] key = key().apply(q.getT2(), q.getT3(), q.getT4());
//          return Pair.next(key, q.getT4());
//        })
//        .forEach(v -> users.put(v.getT1(), v.getT2()));

    System.out.println("users.size() = " + users.size());


    users
        .prefixSubMap(key().apply("Sven", null, null))
        .navigableKeySet()
        .forEach(e -> {});


    final Object[] ceilingKey = users.ceilingKey(new Object[]{"Svenjaz"});
    final Object[] lowerKey    = users.lowerKey(new Object[]{"Svenja"});

    users
        .subMap(lowerKey, false, ceilingKey, false)
        .forEach((o, s) -> System.out.println(o[0] + " - " + o[1]));
  }


  private static TriFunction<String, String, String, String[]> key() {
    return (a, b, c) -> {
      final String[] key = new String[3];
      key[0] = a;
      key[1] = b;
      key[2] = c;
      return key;
    };

  }

  public static class City extends Pair<String, String> {

    public City(String s, String s2) {
      super(s, s2);
    }

    public String plz() { return getT1();}

    public String name() { return getT2();}
  }

  private Function<String, Stream<City>> cityStream() {
    return (filename) -> ((CheckedFunction<String, Stream<City>>) f -> Files
        .lines(new File(f).toPath(), Charset.forName("UTF-8"))
        .map(line -> line.split(" "))
        .peek(e -> { if (e.length != 2) logger().warning(" -> " + Arrays.toString(e));})
        .filter(e -> e.length == 2)
        .map(e -> new City(e[0], e[1])))
        .apply(filename)
        .getOrElse(Stream::empty);
  }


}
