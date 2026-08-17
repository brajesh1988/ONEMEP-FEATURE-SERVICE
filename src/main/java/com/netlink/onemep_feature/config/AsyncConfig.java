package com.netlink.onemep_feature.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/**
 * Background execution for the spreadsheet importer (ONEMEP-35).
 *
 * <p>The import cannot run on the request thread: several 150 MB spreadsheets take minutes to parse
 * and the gateway would time out long before the response. POST therefore answers 202 and the work
 * continues here.
 *
 * <p>Three deliberate choices:
 *
 * <ul>
 *   <li><b>Platform threads, not virtual ones.</b> The service enables virtual threads for the MVC
 *       pool, and they are the wrong tool here: parsing is CPU- and heap-bound rather than blocked
 *       on I/O, so the useful property is a hard ceiling on how many run at once. Two concurrent
 *       150 MB parses is a memory budget; two hundred is an outage.
 *   <li><b>A bounded queue that rejects rather than grows.</b> An unbounded queue turns
 *       over-submission into heap exhaustion much later and somewhere unrelated. Rejection surfaces
 *       at submission time, where it can be reported.
 *   <li><b>The security context is carried across.</b> {@code SecurityContextHolder} is
 *       thread-local, so without this the importer would run unauthenticated and every imported
 *       Design would be attributed to "System" in its audit trail rather than to the person who
 *       uploaded the file.
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  /** Concurrent imports. Each may hold a large workbook's working set, so this stays small. */
  private static final int CONCURRENT_IMPORTS = 2;

  /** Submissions allowed to wait. Beyond this, POST is rejected rather than silently delayed. */
  private static final int QUEUED_IMPORTS = 50;

  public static final String IMPORT_EXECUTOR = "designImportExecutor";

  @Bean(name = IMPORT_EXECUTOR)
  public Executor designImportExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(CONCURRENT_IMPORTS);
    executor.setMaxPoolSize(CONCURRENT_IMPORTS);
    executor.setQueueCapacity(QUEUED_IMPORTS);
    executor.setThreadNamePrefix("design-import-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    // Let an import in flight finish rather than tearing it down mid-batch and leaving files
    // stranded in PROCESSING with no thread coming back for them.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();

    return new DelegatingSecurityContextAsyncTaskExecutor(executor);
  }
}
