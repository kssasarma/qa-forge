package com.qaforge.application.agent.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** File-naming conventions from PRD §14.2. */
class NamingUtilTest {

    @Test
    void snakeCaseNormalizesTitleForPlaywrightFileNames() {
        assertThat(NamingUtil.snakeCase("User completes checkout")).isEqualTo("user_completes_checkout");
        assertThat(NamingUtil.snakeCase("  Checkout > Payment  ")).isEqualTo("checkout_payment");
    }

    @Test
    void pascalCaseBuildsJavaClassNames() {
        assertThat(NamingUtil.pascalCase("place order")).isEqualTo("PlaceOrder");
        assertThat(NamingUtil.pascalCase("orders")).isEqualTo("Orders");
    }

    @Test
    void camelCaseBuildsMethodNames() {
        assertThat(NamingUtil.camelCase("User completes checkout")).isEqualTo("userCompletesCheckout");
    }
}
