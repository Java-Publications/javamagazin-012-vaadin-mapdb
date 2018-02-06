package demo.org.rapidpm.vaadin.demo.mapdb;

import org.junit.jupiter.api.Test;
import org.mapdb.BTreeMap;
import org.mapdb.Serializer;
import org.rapidpm.vaadin.javamagazin.PersistenceFunctions;

import java.nio.charset.Charset;

import static org.rapidpm.vaadin.javamagazin.PersistenceFunctions.cachingDB;


/**
 *
 */
public class MapDBDemo02 {


  @Test
  void test001() {
    final PersistenceFunctions.DatabasePair databasePair = cachingDB()
        .apply(this.getClass().getSimpleName());

    final BTreeMap<byte[], String> users = databasePair
        .onDiscDB()
        .treeMap("users", Serializer.BYTE_ARRAY, Serializer.STRING)
        .counterEnable()
        .createOrOpen();

    users.put("aaa".getBytes(Charset.forName("UTF-8")), "aaa");
    users.put("aaaa".getBytes(Charset.forName("UTF-8")), "aaaa");
    users.put("aaaaa".getBytes(Charset.forName("UTF-8")), "aaaaa");
    users.put("aaaab".getBytes(Charset.forName("UTF-8")), "aaaab");
    users
        .subMap("aaa".getBytes(Charset.forName("UTF-8")),
                "aaaab".getBytes(Charset.forName("UTF-8"))

        )
        .keySet()
        .stream()
        .limit(2)
        .map(String::new)
        .forEach(System.out::println);
  }
}
