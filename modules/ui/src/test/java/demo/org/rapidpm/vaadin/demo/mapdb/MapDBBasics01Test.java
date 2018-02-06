package demo.org.rapidpm.vaadin.demo.mapdb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;

/**
 *
 */
public class MapDBBasics01Test {

  @Test
  void test001() {
    final DB dbMemory = DBMaker
        .memoryDB()
        .closeOnJvmShutdown()
        .make();

    final HTreeMap<String, String> map001 = dbMemory
        .hashMap("map", Serializer.STRING, Serializer.STRING)
        .createOrOpen();

    final HTreeMap<String, String> map002 = dbMemory
        .hashMap("map", Serializer.STRING, Serializer.STRING)
        .createOrOpen();

    map001.put("A", "A");

    Assertions.assertEquals("A", map001.get("A"));
    Assertions.assertEquals("A", map002.get("A"));

  }

  @Test
  void test002() {
    final DB dbFile = DBMaker
        .fileDB(new File("target/", MapDBBasics01Test.class.getSimpleName() + "_test002.mapdb"))
        .closeOnJvmShutdown()
        .transactionEnable()
        .make();

    final HTreeMap<String, String> map001 = dbFile
        .hashMap("map", Serializer.STRING, Serializer.STRING)
        .createOrOpen();

    map001.put("A", "A");
    Assertions.assertEquals("A", map001.get("A"));
    dbFile.commit();

    dbFile.close();

    final DB dbFileReOpend = DBMaker
        .fileDB(new File("target/", MapDBBasics01Test.class.getSimpleName() + "_test002.mapdb"))
        .closeOnJvmShutdown()
        .transactionEnable()
        .make();

    final HTreeMap<String, String> map001ReOpend = dbFileReOpend
        .hashMap("map", Serializer.STRING, Serializer.STRING)
        .createOrOpen();
    Assertions.assertEquals("A", map001ReOpend.get("A"));

    map001ReOpend.put("A", "A with changes");

    dbFileReOpend.rollback();
    dbFileReOpend.close();

    final DB dbFileReOpendAgain = DBMaker
        .fileDB(new File("target/", MapDBBasics01Test.class.getSimpleName() + "_test002.mapdb"))
        .closeOnJvmShutdown()
        .transactionEnable()
        .make();

    final HTreeMap<String, String> map001ReOpendAgain = dbFileReOpendAgain
        .hashMap("map", Serializer.STRING, Serializer.STRING)
        .createOrOpen();
    Assertions.assertEquals("A", map001ReOpendAgain.get("A"));
  }

}
