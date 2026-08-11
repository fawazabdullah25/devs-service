package org.kstacks.devs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DevsServiceApplicationTests {
    @Test
    void contextLoadsWithAdminLockedAndProvidersDisabled() {}
}
