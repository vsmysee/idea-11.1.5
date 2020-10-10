package org.jetbrains.jps.incremental.artifacts.instructions;

import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public interface ArtifactRootProcessor {
  void process(ArtifactSourceRoot root, int rootIndex, Collection<DestinationInfo> destinations) throws IOException;
}
