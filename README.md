
<center>
<a href="https://vaadin.com">
 <img src="https://vaadin.com/images/hero-reindeer.svg" width="200" height="200" /></a>
</center>

# An ImageCache based on MapDB used in a Vaadin App
Again and again we have to do it in applications with the subject of persistence.
The first thought is then very often to locate in the field of RDBMS.
But does it always have to be such a system? Well, too, you can
choose something from the field of No-SQL systems. However, these are also systems
often complex, require stand-alone infrastructure or are simple and easy to access
complex for the task that actually needs to be done.
Not every application needs to scale like a Netflix / Google and as the representatives of the huge system may be called.

Sometimes it is the small application that will agree
Dozens of employees enable their work to be done efficiently.
We will deal here with a possible variant based on Vaadin and MapDB.

## MapDB - [www.mapdb.org](www.mapdb.org)
This demo will show you how you could
use MapDB as an ImageCache.
The basic Idea behind is, that MapDB will give you the possibility to create 
a hierarchy of data structures.

In this example I am using **mapdb 3.0.5**.
You have to add this as a dependency to your pom.xml

```xml
    <!--Persistence-->
    <dependency>
      <groupId>org.mapdb</groupId>
      <artifactId>mapdb</artifactId>
      <version>3.0.5</version>
    </dependency>
```

All in all, the handling of MapDB is very simple. Let us now turn to a more ambitious example.
With MapDB we get the possibility to combine data structures with each other, for example
to build a cache of all the values that are in memory also in a persistent structure
stores. If a key is not in the transient map, it will be in the persistent data structure checked 
if there is a key with this value. If that is the case, it will load,
stored in the transient data structure 
and simultaneously delivered as a result of the request to the transient data structure.

To build such a structure, two instances of the class ```DB``` are generated
be used simultaneously. An instance is again a purely transient version, which is exclusively in memory
the JVM is located. The other instance is file-based and is responsible for the persistent part.

Since both instances belong together, they are held together in a ```Pair <DB, DB>```.
This results in a function that generates for a logical name, a ```Pair <DB, DB>```.

```java
  private Function<String, Pair<DB, DB>> cachingDB() {
    return (name) -> {
      final File databaseFile = new File("target", this.getClass().getSimpleName() + "_" + name);

      final DB dbDisk = DBMaker
          .fileDB(databaseFile)
          .closeOnJvmShutdown()
          .fileMmapEnableIfSupported()
          .make();

      final DB dbMemory = DBMaker
          .memoryDB()
          .closeOnJvmShutdown()
          .make();
      return Pair.next(dbMemory, dbDisk);
    };
  }
```

The first thing you need is the persistent map. Here in this example, the map is already completely provided with types.
Of course you can also formulate the following constructs generically. 

```java
  public static final String DATABASE = "database";

  public Function<String, HTreeMap<Integer, String>> mapOnDisc() {
    return (name) -> database.apply(DATABASE)
                             .getT2()
                             .hashMap(name + "_onDisc", Serializer.INTEGER, Serializer.STRING)
                             .expireCompactThreshold(0.4) //40%
                             .createOrOpen();
  }
```

The next step is to create the transient map and create the persistent one
indicate as overflow.

```java
  public Function<String, HTreeMap<Integer, String>> mapInMemoryPersistentOnDisc() {
    return (name) -> {
      return database.apply(DATABASE)
                     .getT1()
                     .hashMap(name + "_inMemory", Serializer.INTEGER, Serializer.STRING)
                     .expireAfterCreate()
                     .expireAfterUpdate()
                     .expireOverflow(mapOnDisc().apply(name))
                     .expireExecutor(newScheduledThreadPool(2))
                     .createOrOpen();
    };
  }
```

## Usage inside Vaadin

To use this in a Vaadin app you have to load a binary array and wrap it into a ```StreamRessource```.

```java
public class ImageSource implements StreamResource.StreamSource {

  @Inject private BlobService blobService;

  private final String imageID;

  public ImageSource(String imageID) {
    this.imageID = imageID;
  }

  @Override
  public InputStream getStream() {
    return blobService
        .loadBlob(imageID)
        .or(() -> Result.success(createFailedLoadImage(imageID)))
        .map(ByteArrayInputStream::new)
        .get();
  }


  private byte[] createFailedLoadImage(String imageID) {
    BufferedImage image = new BufferedImage(512, 512,
                                            BufferedImage.TYPE_INT_RGB);
        //SNIPP code - create a *failed* pic
   }
}
```

And here is the final implementation based on MapDB.

```java
public class BlobImageServiceMapDB implements HasLogger, BlobService {

  public static final String STORAGE_PREFIX = "_data/_nasa_pics/_0512px/";
  public static final String IMAGE_CACHE    = "imageCache";
  public static final String IMAGES         = "images";

  private static final PersistenceFunctions.DatabasePair CACHE               = memoize(cachingDB()).apply(IMAGE_CACHE);
  private static final HTreeMap<String, byte[]>          IMAGE_MAP_IN_MEMORY = mapInMemoryPersistentOnDisc().apply(CACHE, IMAGES);

  private CheckedFunction<String, byte[]> load
      = (blobID) -> readAllBytes(new File(STORAGE_PREFIX + blobID).toPath());

  @Override
  public Result<byte[]> loadBlob(String blobID) {
    //hard coded right now
    final byte[]  imageByteArray = IMAGE_MAP_IN_MEMORY.get(blobID);
    final boolean containsKey    = imageByteArray != null;
    logger().info("containsKey = " + containsKey);

    if (!containsKey) {
//      load data into system -> some slow remote system
      final Result<byte[]> imageFromRemoteSystem = load.apply(blobID);

      imageFromRemoteSystem
          .ifPresentOrElse(
              imageRAW -> IMAGE_MAP_IN_MEMORY.put(blobID, imageRAW),
              failed -> logger().warning("Image with ID " + blobID + " could not be loaded from external system")
          );
    }
    return Result.ofNullable(IMAGE_MAP_IN_MEMORY.get(blobID));
  }
}
```

You can find other implementations as well, to have something to compare in terms of complexity
and difficulties in production like Memory Leaks.

Feel free to try and enjoy.

If you have any comments 
ping my on Twitter [https://twitter.com/SvenRuppert](https://twitter.com/SvenRuppert)
or direct via email sven.ruppert (a) gmail.com