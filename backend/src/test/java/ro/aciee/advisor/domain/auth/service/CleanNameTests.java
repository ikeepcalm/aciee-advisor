package ro.aciee.advisor.domain.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CleanNameTests {

    @Test
    void collapsesWhitespaceAndControlCharactersAndBlanksBecomeNull() {
        assertThat(AccountService.cleanName("  Bohdan \t\n Horokh ")).isEqualTo("Bohdan Horokh");
        assertThat(AccountService.cleanName("Ana\u0000Maria")).isEqualTo("Ana Maria");
        assertThat(AccountService.cleanName("   ")).isNull();
        assertThat(AccountService.cleanName(null)).isNull();
    }
}
