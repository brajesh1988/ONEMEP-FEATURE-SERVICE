package com.netlink.onemep_feature.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Programmatic transaction control, for the few places where {@code @Transactional} is the wrong
 * tool.
 *
 * <p>File upload is the case that needs it: the work has to be split so the bytes are written
 * <em>outside</em> a transaction, and each file in a batch commits independently. Neither is
 * expressible with a method-level annotation.
 */
@Configuration
public class TransactionConfig {

  @Bean
  TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
    return new TransactionTemplate(transactionManager);
  }
}
