package com.netlink.onemep_feature.project.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configured default rows for the DID tab, synthesized on GET when nothing has been saved yet
 * (never persisted just by opening the tab — see V15 migration notes).
 *
 * @param deliveryStages default MEP design stage names, in display order
 * @param clientContactDesignations default Client Information contact designations, in display
 *     order (e.g. Project Owner, Project Head, Project Coordinator)
 */
@ConfigurationProperties(prefix = "feature.did-defaults")
public record DidDefaultsProperties(
    List<String> deliveryStages, List<String> clientContactDesignations) {

  public DidDefaultsProperties {
    deliveryStages = deliveryStages == null ? List.of() : List.copyOf(deliveryStages);
    clientContactDesignations =
        clientContactDesignations == null ? List.of() : List.copyOf(clientContactDesignations);
  }
}
