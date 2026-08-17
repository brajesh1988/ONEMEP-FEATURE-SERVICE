package com.netlink.onemep_feature.discussion.service;

import com.netlink.onemep_feature.user.dto.UserSummary;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code @mentions} in message text against the people eligible to be mentioned
 * (ONEMEP-41).
 *
 * <p>Three rules from the ticket, all handled here rather than by callers:
 *
 * <ul>
 *   <li>matching is case-insensitive;
 *   <li>the same person mentioned twice in one message resolves once, so they get one notification;
 *   <li>text that matches nobody eligible stays ordinary prose — it must never notify a
 *       similarly-named unrelated user.
 * </ul>
 */
public final class MentionParser {
  private MentionParser() {}

  /**
   * Matches an {@code @} followed by a name. Letters, digits, dot, underscore and hyphen are taken
   * as part of the handle; anything else ends it, so trailing punctuation is not swallowed.
   */
  private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9._-]+)");

  /**
   * @param text the message body
   * @param candidates users who may be mentioned, keyed by id — typically the Project's members
   * @return ids of everyone genuinely mentioned, in the order they first appear
   */
  public static Set<Long> resolve(String text, Map<Long, UserSummary> candidates) {
    Set<Long> mentioned = new LinkedHashSet<>();
    if (text == null || text.isBlank() || candidates.isEmpty()) {
      return mentioned;
    }

    Matcher matcher = MENTION.matcher(text);
    while (matcher.find()) {
      String handle = matcher.group(1).toLowerCase(Locale.ROOT);
      candidates.forEach(
          (id, user) -> {
            if (matches(handle, user)) {
              mentioned.add(id);
            }
          });
    }
    return mentioned;
  }

  /**
   * A handle matches on the display name with spaces removed ({@code @AlexCarter}), on the first
   * word of it ({@code @Alex}), or on the local part of the email. Anything looser risks notifying
   * the wrong person, which the ticket forbids outright.
   */
  private static boolean matches(String handle, UserSummary user) {
    String name = user.displayName();
    if (name != null && !name.isBlank()) {
      String collapsed = name.replace(" ", "").toLowerCase(Locale.ROOT);
      String firstWord = name.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
      if (collapsed.equals(handle) || firstWord.equals(handle)) {
        return true;
      }
    }
    String email = user.email();
    if (email != null && email.contains("@")) {
      return email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT).equals(handle);
    }
    return false;
  }
}
