package com.smartknowledge.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartknowledge.backend.cloud.*;
import com.smartknowledge.backend.common.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NormalEnvironmentTest {
    @Test void simulatedSubscriptionControllerRequiresExplicitDemoProfile() {
        assertThat(DemoSubscriptionController.class.getAnnotation(Profile.class).value()).containsExactly("demo");
    }
    @Test void missingRealConfigurationAndMockOutsideDemoNeverReserveQuota() {
        AiLedgerService ledger=mock(AiLedgerService.class); ObjectMapper mapper=new ObjectMapper(); Json json=new Json(mapper); MockEnvironment environment=new MockEnvironment();
        var request=new AiRequests.Tags(UUID.randomUUID().toString(),"标题","正文",List.of());
        for(String mode:List.of("real","mock")) {
            AiService service=new AiService(ledger,json,mapper,environment,mode,"","","",1,262144);
            assertThatThrownBy(()->service.generate("user",request)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code).isEqualTo("AI_UNAVAILABLE"));
        }
        verifyNoInteractions(ledger);
    }
}
