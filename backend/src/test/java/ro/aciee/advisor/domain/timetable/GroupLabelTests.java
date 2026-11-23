package ro.aciee.advisor.domain.timetable;

import org.junit.jupiter.api.Test;
import ro.aciee.advisor.domain.timetable.service.GroupLabel;

import static org.assertj.core.api.Assertions.assertThat;

class GroupLabelTests {

    @Test
    void splitsTheSubgroupLetterAndReadsCyrillicLookAlikesAsLatin() {
        assertThat(GroupLabel.of("22C31A")).isEqualTo(new GroupLabel("22C31", "A"));
        assertThat(GroupLabel.of(" 22С32В ")).isEqualTo(new GroupLabel("22C32", "B"));
        assertThat(GroupLabel.of("2241C")).isEqualTo(new GroupLabel("2241", "C"));
        assertThat(GroupLabel.of("RS41")).isEqualTo(new GroupLabel("RS41", null));
    }
}
