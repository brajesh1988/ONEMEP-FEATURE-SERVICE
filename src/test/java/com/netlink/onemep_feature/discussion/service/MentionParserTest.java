package com.netlink.onemep_feature.discussion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.netlink.onemep_feature.user.dto.UserSummary;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mention resolution (ONEMEP-41). */
class MentionParserTest {

  private static final Map<Long, UserSummary> TEAM = team();

  @Test
  void resolve_matchesTheFirstName() {
    assertThat(MentionParser.resolve("@Himlesh please review this.", TEAM)).containsExactly(2L);
  }

  @Test
  void resolve_matchesTheFullNameWithoutSpaces() {
    assertThat(MentionParser.resolve("@AlexCarter can you look?", TEAM)).containsExactly(1L);
  }

  @Test
  void resolve_matchesTheEmailLocalPart() {
    assertThat(MentionParser.resolve("@priya.nair over to you", TEAM)).containsExactly(3L);
  }

  @Test
  void resolve_isCaseInsensitive() {
    assertThat(MentionParser.resolve("@HIMLESH and @alexcarter", TEAM)).containsExactly(2L, 1L);
  }

  /**
   * ONEMEP-41: two mentions of one person in a message must still produce a single notification.
   */
  @Test
  void resolve_deduplicatesRepeatedMentionsOfTheSamePerson() {
    Set<Long> mentioned =
        MentionParser.resolve("@Alex please review this. @Alex this is required today.", TEAM);
    assertThat(mentioned).containsExactly(1L);
  }

  @Test
  void resolve_handlesSeveralDistinctPeople() {
    assertThat(MentionParser.resolve("@Alex @Himlesh please review", TEAM))
        .containsExactlyInAnyOrder(1L, 2L);
  }

  /**
   * Text that matches nobody eligible stays prose — it must never notify a similar-looking user.
   */
  @Test
  void resolve_ignoresAHandleThatMatchesNobody() {
    assertThat(MentionParser.resolve("@Alexander please review", TEAM)).isEmpty();
    assertThat(MentionParser.resolve("@nobody at all", TEAM)).isEmpty();
  }

  @Test
  void resolve_doesNotSwallowTrailingPunctuation() {
    assertThat(MentionParser.resolve("Thanks @Alex, that works.", TEAM)).containsExactly(1L);
    assertThat(MentionParser.resolve("Over to @Himlesh!", TEAM)).containsExactly(2L);
  }

  @Test
  void resolve_withNoMentions_returnsEmpty() {
    assertThat(MentionParser.resolve("No mentions in this message at all.", TEAM)).isEmpty();
  }

  @Test
  void resolve_withNoCandidates_returnsEmpty() {
    assertThat(MentionParser.resolve("@Alex please review", Map.of())).isEmpty();
  }

  @Test
  void resolve_withBlankText_returnsEmpty() {
    assertThat(MentionParser.resolve("   ", TEAM)).isEmpty();
    assertThat(MentionParser.resolve(null, TEAM)).isEmpty();
  }

  @Test
  void resolve_ignoresAnEmailAddressInProse() {
    // The local part before '@' is not a mention; only what follows the '@' is considered, and
    // "onemep.local" matches nobody.
    assertThat(MentionParser.resolve("Write to alex@onemep.local for details", TEAM)).isEmpty();
  }

  private static Map<Long, UserSummary> team() {
    Map<Long, UserSummary> team = new LinkedHashMap<>();
    team.put(1L, new UserSummary(1L, "Alex Carter", "alex.carter@onemep.local"));
    team.put(2L, new UserSummary(2L, "Himlesh Rao", "himlesh@onemep.local"));
    team.put(3L, new UserSummary(3L, "Priya Nair", "priya.nair@onemep.local"));
    return team;
  }
}
