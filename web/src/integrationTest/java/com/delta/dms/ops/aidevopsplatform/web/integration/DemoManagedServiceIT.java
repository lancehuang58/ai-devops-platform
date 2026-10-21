package com.delta.dms.ops.aidevopsplatform.web.integration;

import com.delta.dms.ops.aidevopsplatform.AiDevopsPlatformApplication;
import com.delta.dms.ops.aidevopsplatform.service.port.in.ManagedServiceQueryUseCase;
import com.delta.dms.ops.aidevopsplatform.service.port.out.*;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.Observation;
import com.delta.dms.ops.aidevopsplatform.evaluation.model.*;
import com.delta.dms.ops.aidevopsplatform.evaluation.tool.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("demo")
@SpringBootTest(classes=AiDevopsPlatformApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"platform.database.jdbc-url=jdbc:h2:mem:demo_http_it;DB_CLOSE_DELAY=-1",
                "platform.llm.model-name=integration-mock","platform.ldap.users.unassigned=demo-unassigned"})
class DemoManagedServiceIT {
    @LocalServerPort int port;
    @Autowired ManagedServiceQueryUseCase query;
    @Autowired ManagedServiceRepository services;
    @Autowired ServiceSummaryPort summary;
    @Autowired NotificationOutbox outbox;
    private HttpResponse<String> get(String path,String credentials) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10)).GET();
        if(credentials!=null)builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try(var client=HttpClient.newHttpClient()){return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
    }
    @Test void normalUserCanListOnlyEnabledManagedServices() throws Exception {
        var response=get("/api/v1/managed-services","normal:demo-normal");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(response.body(),"$..id")).containsExactly("jenkins-demo");
        assertThat(response.body()).doesNotContain("credential","password","api-key");
    }
    @Test void authenticatedQueryStoresReportAndDeliversItsEvent() throws Exception {
        var response=get("/api/v1/managed-services/jenkins-demo/status","normal:demo-normal");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(),"$.status")).isEqualTo("UP");
        assertThat(JsonPath.<Boolean>read(response.body(),"$.probeMocked")).isTrue();
        assertThat(JsonPath.<Boolean>read(response.body(),"$.modelMocked")).isTrue();
        assertThat(JsonPath.<String>read(response.body(),"$.modelName")).isEqualTo("integration-mock");
        assertThat(response.body()).doesNotContain("actorId","password","demo-normal");
        String id=JsonPath.read(response.body(),"$.reportId");
        var saved=get("/api/v1/reports/"+id,"admin:demo-admin");
        assertThat(saved.statusCode()).isEqualTo(200);assertThat(saved.body()).isEqualTo(response.body());
        assertThat(outbox.pending(100)).isEmpty();
    }
    @Test void missingWrongAndUnassignedCredentialsAreRejected() throws Exception {
        assertThat(get("/api/v1/managed-services",null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/managed-services","admin:wrong").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/managed-services","unassigned:demo-unassigned").statusCode()).isEqualTo(401);
    }
    @Test void disabledAndUnknownServicesCannotBeProbed() throws Exception {
        assertThat(get("/api/v1/managed-services/jenkins-disabled/status","normal:demo-normal").statusCode()).isEqualTo(404);
        assertThat(get("/api/v1/managed-services/missing/status","normal:demo-normal").statusCode()).isEqualTo(404);
    }
    @Test void allSixModulesParticipateWithoutMakingMockQualityPass() {
        var service=services.findById("jenkins-demo").orElseThrow();
        ModelQualityEvaluator mq=new ServiceSummaryEvaluator(summary,service,
                new Observation(Observation.Status.UP,"mock-jenkins-1",Instant.EPOCH,true));
        assertThat(mq.evaluate("MQ-DEMO-001").status()).isEqualTo(ModelEvaluationResult.Status.HARNESS_ONLY);
        SolutionToolRunner st=new ManagedServiceScenarioRunner(query,"normal",service.id());
        st.run(new ToolTestScenario("ST-DEMO-001",ToolTestScenario.Mode.OFFLINE,ToolTestScenario.Environment.LOCAL));
        assertThat(outbox.pending(100)).isEmpty();
    }
}
