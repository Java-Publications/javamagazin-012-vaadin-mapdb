package org.rapidpm.vaadin.javamagazin.mapdb.ui.image;

import org.rapidpm.frp.model.Result;

/**
 *
 */
public interface BlobService {

  public Result<byte[]> loadBlob(String blobID);
}
