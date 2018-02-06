package demo.org.rapidpm.vaadin.demo.mapdb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.rapidpm.frp.functions.CheckedConsumer;
import org.rapidpm.frp.model.Pair;

import java.io.File;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.junit.jupiter.api.Assertions.*;
import static org.rapidpm.frp.memoizer.Memoizer.memoize;

/**
 *
 */
public class MapdbDemo01 {


  public static final String DATABASE = "database";

  private Function<String, Pair<DB, DB>> cachingDB() {
    return (name) -> {
      final File databaseFile = new File("target", this.getClass().getSimpleName() + "_" + name);

      final DB dbDisk = DBMaker
          .fileDB(databaseFile)
          .closeOnJvmShutdown()
          .concurrencyScale(10)
          .fileMmapEnableIfSupported()
          .make();

      final DB dbMemory = DBMaker
          .memoryDB()
          .closeOnJvmShutdown()
          .make();
      return Pair.next(dbMemory, dbDisk);
    };
  }

  private Function<String, Pair<DB, DB>> memoizedCachingDB() {
    return memoize(cachingDB());
  }

  private Function<String, Pair<DB, DB>> database = memoizedCachingDB();


  public Function<String, HTreeMap<Integer, String>> mapInMemory() {
    return (name) -> database.apply(DATABASE)
                             .getT1()
                             .hashMap(name + "_inMemory", Serializer.INTEGER, Serializer.STRING)
                             .createOrOpen();
  }

  public Function<String, HTreeMap<Integer, String>> mapOnDisc() {
    return (name) -> database.apply(DATABASE)
                             .getT2()
                             .hashMap(name + "_onDisc", Serializer.INTEGER, Serializer.STRING)
                             .expireCompactThreshold(0.4) //40%
                             .createOrOpen();
  }

  public Function<String, HTreeMap<Integer, String>> mapInMemoryPersistentOnDisc() {
    return (name) -> {

      final HTreeMap<Integer, String> overflowMap = mapOnDisc().apply(name);
      return database.apply(DATABASE)
                     .getT1()
                     .hashMap(name + "_inMemory", Serializer.INTEGER, Serializer.STRING)
//                     .expireAfterCreate(1, TimeUnit.SECONDS)
                     .expireAfterCreate()
                     .expireAfterUpdate()
                     .expireOverflow(overflowMap)
                     .expireExecutor(newScheduledThreadPool(2))
                     .createOrOpen();

    };
  }

  private CheckedConsumer<String> fillPersistentMap() {
    return mapName -> {
      final HTreeMap<Integer, String> map = mapOnDisc().apply(mapName);
      IntStream
          .rangeClosed(0, 1_000_000)
          .mapToObj(i -> Pair.next(i, "Hello World " + i))
          .forEach(p -> map.put(p.getT1(), p.getT2()));
      map.expireEvict();
      return null;
    };
  }

  private CheckedConsumer<String> fillTransientMap() {
    return mapName -> {
      final HTreeMap<Integer, String> map = mapInMemoryPersistentOnDisc().apply(mapName);
      IntStream
          .rangeClosed(0, 1_000_000)
          .mapToObj(i -> Pair.next(i, "Hello World " + i))
          .forEach(p -> map.put(p.getT1(), p.getT2()));
      map.expireEvict();
      return null;
    };
  }


  @Test
  @DisplayName("Read Values")
  void test000() {
    final String MAP_NAME = "map_test000";

    fillPersistentMap()
        .apply(MAP_NAME)
        .ifFailed(e -> Assertions.fail("init of the map failed " + e));

    final HTreeMap<Integer, String> map           = mapInMemoryPersistentOnDisc().apply(MAP_NAME);
    final HTreeMap<Integer, String> persistentMap = mapOnDisc().apply(MAP_NAME);

    Assertions.assertEquals(1_000_001, persistentMap.getSize());
    Assertions.assertEquals(0, map.getSize());

    assertTrue(persistentMap.containsKey(8_863));
    assertFalse(map.containsKey(8_863));
    assertNotNull(map.get(8_863));
    //positive only after get
    assertTrue(map.containsKey(8_863));
  }


  @Test
  @DisplayName("Delete Values")
  void test001() {
    final String MAP_NAME = "map_test001";

    fillPersistentMap()
        .apply(MAP_NAME)
        .ifFailed(e -> Assertions.fail("init of the map failed " + e));

    final HTreeMap<Integer, String> map           = mapInMemoryPersistentOnDisc().apply(MAP_NAME);
    final HTreeMap<Integer, String> persistentMap = mapOnDisc().apply(MAP_NAME);

    // be carefull -> cached data still exists
    assertNotNull(map.get(26_549));
    persistentMap.remove(26_549);
    assertFalse(persistentMap.containsKey(26_549));
    assertTrue(map.containsKey(26_549));
    assertNotNull(map.get(26_549));

    assertNotNull(map.get(332_038));
    map.remove(332_038);
    assertFalse(map.containsKey(332_038));

    assertFalse(persistentMap.containsKey(332_038));
    assertNull(persistentMap.get(332_038));


    //remove from persistent map
    map.get(12_456); //load to cache
    persistentMap.remove(12_456);
    assertNull(persistentMap.get(12_456));

    //be careful - cache holds value
    assertNotNull(map.get(12_456));

    //will not propagate up to cache
    persistentMap.expireEvict();
    assertNotNull(map.get(12_456));

    //will not "load" the deletion
    map.expireEvict();
    assertNull(persistentMap.get(12_456));
    assertNotNull(map.get(12_456));

    //uuups - value available again
    map.clearWithExpire();
    assertNotNull(persistentMap.get(12_456));
    assertNotNull(map.get(12_456));

  }

  @Test
  @DisplayName("Modifications on this Cache  - pre-filled persistent Map")
  void test002() {
    final String MAP_NAME = "map_test002";

    fillPersistentMap()
        .apply(MAP_NAME)
        .ifFailed(e -> Assertions.fail("init of the map failed " + e));

    final HTreeMap<Integer, String> map           = mapInMemoryPersistentOnDisc().apply(MAP_NAME);
    final HTreeMap<Integer, String> persistentMap = mapOnDisc().apply(MAP_NAME);

//    Change Value
    final String oldValueFromMemory = map.get(12_456);
    final String oldValueFromDisc   = persistentMap.get(12_456);

    Assertions.assertEquals(oldValueFromDisc, oldValueFromMemory);

    map.replace(12_456, "NEW VALUE");
    Assertions.assertEquals("NEW VALUE", map.get(12_456));
    Assertions.assertNotEquals("NEW VALUE", persistentMap.get(12_456));
    map.clearWithExpire();
    Assertions.assertEquals("NEW VALUE", persistentMap.get(12_456));
  }


  @Test
  @DisplayName("Modifications on this Cache - pre-filled transient Map")
  void test003() {
    final String MAP_NAME = "map_test003";

    mapOnDisc().apply(MAP_NAME).clearWithExpire();

    mapInMemoryPersistentOnDisc()
        .apply(MAP_NAME)
        .clearWithExpire();

    fillTransientMap()
        .apply(MAP_NAME)
        .ifFailed(e -> Assertions.fail("init of the map failed " + e));

    final HTreeMap<Integer, String> map           = mapInMemoryPersistentOnDisc().apply(MAP_NAME);
    final HTreeMap<Integer, String> persistentMap = mapOnDisc().apply(MAP_NAME);

    assertNotNull(map.get(12_456));
    assertNull(persistentMap.get(12_456));
    map.clearWithExpire();

    final String oldValueFromMemory = map.get(12_456);
    final String oldValueFromDisc   = persistentMap.get(12_456);
    Assertions.assertEquals(oldValueFromDisc, oldValueFromMemory);

    map.replace(12_456, "NEW VALUE");
    Assertions.assertEquals("NEW VALUE", map.get(12_456));
    Assertions.assertNotEquals("NEW VALUE", persistentMap.get(12_456));
    map.clearWithExpire();
    Assertions.assertEquals("NEW VALUE", persistentMap.get(12_456));
  }

  @Test
  @DisplayName("Modifications on this Cache - remove from persistent Map")
  void test004() {
    final String MAP_NAME = "map_test004";

    fillPersistentMap()
        .apply(MAP_NAME)
        .ifFailed(e -> Assertions.fail("init of the map failed " + e));

    final HTreeMap<Integer, String> map           = mapInMemoryPersistentOnDisc().apply(MAP_NAME);
    final HTreeMap<Integer, String> persistentMap = mapOnDisc().apply(MAP_NAME);

    assertNotNull(map.get(12_456));
    assertNotNull(persistentMap.get(12_456));

  }

}
