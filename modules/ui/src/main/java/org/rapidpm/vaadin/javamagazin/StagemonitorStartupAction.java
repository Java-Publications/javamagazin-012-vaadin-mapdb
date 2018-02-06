package org.rapidpm.vaadin.javamagazin;

import org.rapidpm.ddi.DI;
import org.rapidpm.microservice.Main;
import org.rapidpm.microservice.MainUndertow;

import java.util.Optional;

/**
 *
 */
public class StagemonitorStartupAction implements Main.MainStartupAction {
  @Override
  public void execute(Optional<String[]> optional) {
    System.setProperty(MainUndertow.STAGEMONITOR_ACTIVE_PROPERTY, "true");
    DI.clearReflectionModel();
    DI.activatePackages("org.rapidpm");
  }
}
