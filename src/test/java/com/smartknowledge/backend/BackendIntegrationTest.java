package com.smartknowledge.backend;

import com.fasterxml.jackson.databind.*;
import com.smartknowledge.backend.account.SessionUser;
import com.smartknowledge.backend.cloud.*;
import com.smartknowledge.backend.common.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class BackendIntegrationTest {
    static MySQLContainer<?> container;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("TEST_DB_URL");
        if(url==null || url.isBlank()) {
            container=new MySQLContainer<>("mysql:8.0.46").withDatabaseName("smart_knowledge_test"); container.start();
            registry.add("spring.datasource.url",container::getJdbcUrl); registry.add("spring.datasource.username",container::getUsername); registry.add("spring.datasource.password",container::getPassword);
        } else {
            if(!url.matches("jdbc:mysql://[^/]+/[A-Za-z0-9_]*_test(?:\\?.*)?")) throw new IllegalArgumentException("集成测试数据库名必须以 _test 结尾");
            registry.add("spring.datasource.url",()->url); registry.add("spring.datasource.username",()->System.getenv().getOrDefault("TEST_DB_USERNAME","root")); registry.add("spring.datasource.password",()->System.getenv().getOrDefault("TEST_DB_PASSWORD",""));
        }
    }
    @AfterAll static void stopContainer() { if(container!=null) container.stop(); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SqlMapper db;
    @Autowired Json json;
    @Autowired AiLedgerService ledger;
    @Autowired Environment environment;
    @Autowired RetentionService retention;
    private record Client(String user,String token,String refresh,String device,String session) {}
    private String id() { return UUID.randomUUID().toString(); }
    private JsonNode call(MockHttpServletRequestBuilder request,Object body,Client client,int status) throws Exception {
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        if(client!=null) request.header("Authorization","Bearer "+client.token());
        MvcResult result=mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        JsonNode root=mapper.readTree(result.getResponse().getContentAsByteArray()); assertThat(root.path("requestId").asText()).isNotBlank(); return root;
    }
    private Client client(boolean subscribed) throws Exception {
        String username="u"+id().replace("-","");
        call(post("/api/v1/auth/register"),Map.of("username",username,"password","CourseTest123!","nickname","课程用户"),null,200);
        Client c=login(username,id());
        if(subscribed) {
            call(post("/api/v1/subscriptions/demo-activate"),Map.of("requestId",id()),c,200);
            consent(c,true,true,true);
        }
        return c;
    }
    private Client login(String username,String device) throws Exception {
        JsonNode data=call(post("/api/v1/auth/login"),Map.of("username",username,"password","CourseTest123!","deviceUuid",device,"deviceName","测试设备","platform","test"),null,200).path("data");
        return new Client(data.path("userId").asText(),data.path("accessToken").asText(),data.path("refreshToken").asText(),device,data.path("deviceId").asText());
    }
    private void consent(Client c,boolean sync,boolean backup,boolean ai) throws Exception { call(put("/api/v1/me/preferences"),Map.of("syncEnabled",sync,"backupEnabled",backup,"aiEnabled",ai,"consentVersion","2026-10-v1"),c,200); }
    private Map<String,Object> note(String note,long version,String content) { return Map.of("id",note,"baseVersion",version,"title","Flutter 数据同步","content",content,"tags",List.of(Map.of("name","课程","source","manual"),Map.of("name","Flutter","source","llm"))); }
    private void limits(Client c,long storage,int ai) {
        String plan=id(); Instant now=Instant.now();
        db.update("INSERT INTO subscription_plans(id,code,name,price_cents,duration_days,storage_bytes,history_days,ai_monthly_limit,created_at,updated_at) VALUES(?,?,?,9900,365,?,30,?,?,?)",plan,plan.substring(0,20),"测试独立套餐",storage,ai,now,now);
        db.update("UPDATE user_subscriptions SET plan_id=? WHERE user_id=?",plan,c.user());
    }
    @Test void migrationsCreatedFifteenBusinessTables() {
        long count=db.count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'"); assertThat(count).isEqualTo(15);
    }
    @Test void refreshRotatesAndLogoutAndDeviceRevocationInvalidateTokens() throws Exception {
        Client c=client(false);
        JsonNode refreshed=call(post("/api/v1/auth/refresh"),Map.of("refreshToken",c.refresh()),null,200).path("data");
        call(get("/api/v1/me"),null,c,401); call(post("/api/v1/auth/refresh"),Map.of("refreshToken",c.refresh()),null,401);
        Client next=new Client(c.user(),refreshed.path("accessToken").asText(),refreshed.path("refreshToken").asText(),c.device(),c.session());
        call(get("/api/v1/me"),null,next,200);
        call(post("/api/v1/auth/logout"),null,next,200); call(get("/api/v1/me"),null,next,401);
        call(post("/api/v1/auth/refresh"),Map.of("refreshToken",next.refresh()),null,401);
        Client revoked=client(false); call(delete("/api/v1/devices/"+revoked.session().toUpperCase(Locale.ROOT)),null,revoked,200); call(get("/api/v1/me"),null,revoked,401);
    }
    @Test void validationConsentSubscriptionAndExpiredEntitlements() throws Exception {
        Client c=client(false); String n=id();
        call(post("/api/v1/notes"),note(n,0,"内容"),c,403);
        consent(c,true,true,true); call(post("/api/v1/notes"),note(n,0,"内容"),c,403);
        String activation=id(); var first=call(post("/api/v1/subscriptions/demo-activate"),Map.of("requestId",activation),c,200).path("data");
        assertThat(call(post("/api/v1/subscriptions/demo-activate"),Map.of("requestId",activation),c,200).path("data")).isEqualTo(first);
        call(post("/api/v1/notes"),note(n,0,"内容"),c,200);
        call(post("/api/v1/notes"),Map.of("id",id(),"baseVersion",0,"title","","content","","tags",List.of()),c,400);
        call(post("/api/v1/notes"),note(id(),0,"中".repeat(87382)),c,413);
        consent(c,false,false,false); call(put("/api/v1/notes/"+n),note(n,1,"不允许上传"),c,403); call(get("/api/v1/notes/"+n),null,c,200);
        db.update("UPDATE user_subscriptions SET expires_at=? WHERE user_id=?",Instant.now().minusSeconds(60),c.user());
        consent(c,true,true,true); call(put("/api/v1/notes/"+n),note(n,1,"已过期"),c,403);
        call(delete("/api/v1/notes/"+n),Map.of("baseVersion",1),c,200);
    }
    @Test void notesTagsFiltersAndTenantIsolation() throws Exception {
        Client a=client(true),b=client(true); String n=id();
        call(post("/api/v1/notes"),note(n,0,"界面掉帧优化"),a,200);
        call(get("/api/v1/notes/"+n),null,b,404); call(put("/api/v1/notes/"+n),note(n,1,"窃取"),b,404);
        call(delete("/api/v1/notes/"+n),Map.of("baseVersion",1),b,404);
        assertThat(call(get("/api/v1/notes").param("keyword","掉帧").param("tags","课程").param("from","2000-01-01T00:00:00Z"),null,a,200).path("data").path("total").asInt()).isEqualTo(1);
        assertThat(call(get("/api/v1/notes").param("keyword","%"),null,a,200).path("data").path("total").asInt()).isZero();
        JsonNode tagList=call(get("/api/v1/tags"),null,a,200).path("data"); assertThat(tagList).hasSize(2); assertThat(tagList.get(0).path("createdAt").asText()).endsWith("Z");
        call(put("/api/v1/notes/"+n),note(n,1,"更新"),a,200); call(put("/api/v1/notes/"+n),note(n,1,"旧版本"),a,409);
        call(get("/api/v1/notes").param("size","101"),null,a,400);
        call(delete("/api/v1/devices/"+a.session()),null,b,404);
        assertThat(db.count("SELECT COUNT(*) FROM note_tags WHERE user_id=? AND note_id=? AND source='llm'",a.user(),n)).isEqualTo(1);
    }
    @Test void syncIdempotencyConflictsRechecksAndDeletionPagination() throws Exception {
        Client a=client(true); String username=(String)db.required("SELECT username FROM users WHERE id=?",a.user()).get("username"); Client b=login(username,id());
        String n=id(),r=id(); Object push=Map.of("deviceUuid",a.device(),"requestId",r,"changes",List.of(Map.of("operation","upsert","note",note(n,0,"初稿"))));
        var saved=call(post("/api/v1/sync/push"),push,a,200).path("data");
        assertThat(call(post("/api/v1/sync/push"),push,a,200).path("data")).isEqualTo(saved);
        call(post("/api/v1/sync/push"),Map.of("deviceUuid",a.device(),"requestId",r,"changes",List.of(Map.of("operation","upsert","note",note(n,1,"变更请求")))),a,409);
        call(put("/api/v1/notes/"+n),note(n,1,"云端第二稿"),a,200);
        var conflict=call(post("/api/v1/sync/push"),Map.of("deviceUuid",b.device(),"requestId",id(),"changes",List.of(Map.of("operation","upsert","note",note(n,1,"设备离线稿")))),b,200).path("data").path("results").get(0).path("conflictId").asText();
        call(put("/api/v1/notes/"+n),note(n,2,"云端第三稿"),a,200);
        call(post("/api/v1/sync/conflicts/"+conflict+"/resolve"),Map.of("serverVersion",2,"choice","submitted"),b,409);
        var resolved=call(post("/api/v1/sync/conflicts/"+conflict.toUpperCase(Locale.ROOT)+"/resolve"),Map.of("serverVersion",3,"choice","submitted"),b,200).path("data");
        assertThat(resolved.path("content").asText()).isEqualTo("设备离线稿"); assertThat(resolved.path("version").asLong()).isEqualTo(4);
        call(delete("/api/v1/notes/"+n),Map.of("baseVersion",4),a,200);
        long after=0; int changes=0; boolean more;
        do { var page=call(get("/api/v1/sync/pull").param("after",""+after).param("size","2"),null,b,200).path("data"); changes+=page.path("items").size(); after=page.path("nextCursor").asLong(); more=page.path("hasMore").asBoolean(); } while(more);
        assertThat(changes).isEqualTo(5); assertThat(after).isEqualTo(5);
        assertThat(db.count("SELECT COUNT(*) FROM sync_changes WHERE user_id=? AND operation='delete'",a.user())).isEqualTo(1);
        assertThat(call(get("/api/v1/sync/conflicts"),null,a,200).path("data").path("total").asInt()).isZero();
    }
    @Test void backupAndVersionRecoveryPreserveContentTagsAndCurrentNotes() throws Exception {
        Client c=client(true),other=client(true); String n=id(); call(post("/api/v1/notes"),note(n,0,"第一稿正文"),c,200);
        String v=call(get("/api/v1/notes/"+n+"/versions"),null,c,200).path("data").path("items").get(0).path("id").asText();
        String backup=call(post("/api/v1/backups"),Map.of("name","复习前备份"),c,200).path("data").path("id").asText();
        call(get("/api/v1/backups/"+backup),null,other,404); call(post("/api/v1/backups/"+backup+"/restore"),null,other,404);
        assertThat(call(get("/api/v1/backups/"+backup+"/download"),null,c,200).path("data").path("notes").get(0).path("tags")).hasSize(2);
        call(put("/api/v1/notes/"+n),note(n,1,"当前稿"),c,200);
        var restored=call(post("/api/v1/backups/"+backup+"/restore"),null,c,200).path("data").path("notes").get(0);
        assertThat(restored.path("id").asText()).isNotEqualTo(n); assertThat(restored.path("sourceNoteId").asText()).isEqualTo(n); assertThat(restored.path("content").asText()).isEqualTo("第一稿正文");
        assertThat(call(get("/api/v1/notes/"+n),null,c,200).path("data").path("content").asText()).isEqualTo("当前稿");
        call(post("/api/v1/notes/"+n+"/versions/"+v+"/restore"),Map.of("baseVersion",1),c,409);
        assertThat(call(post("/api/v1/notes/"+n+"/versions/"+v+"/restore"),Map.of("baseVersion",2),c,200).path("data").path("content").asText()).isEqualTo("第一稿正文");
        consent(c,false,false,false); call(get("/api/v1/backups/"+backup.toUpperCase(Locale.ROOT)+"/download"),null,c,200); call(delete("/api/v1/backups/"+backup.toUpperCase(Locale.ROOT)),null,c,200); call(get("/api/v1/backups/"+backup),null,c,404);
    }
    @Test void storageLimitRollsBackAllNoteWrites() throws Exception {
        Client c=client(true); limits(c,10,100); String n=id(); call(post("/api/v1/notes"),note(n,0,"正文"),c,413);
        assertThat(db.count("SELECT COUNT(*) FROM notes WHERE user_id=?",c.user())).isZero(); assertThat(db.count("SELECT sync_revision FROM user_preferences WHERE user_id=?",c.user())).isZero();
    }
    @Test void batchFailureRollsBackEarlierChangesAndIdempotencyRecord() throws Exception {
        Client c=client(true); String request=id();
        Object batch=Map.of("deviceUuid",c.device(),"requestId",request,"changes",List.of(Map.of("operation","upsert","note",note(id(),0,"合法新增")),Map.of("operation","upsert","note",note(id(),1,"不存在的旧版本"))));
        call(post("/api/v1/sync/push"),batch,c,404);
        assertThat(db.count("SELECT COUNT(*) FROM notes WHERE user_id=?",c.user())).isZero();
        assertThat(db.count("SELECT COUNT(*) FROM sync_requests WHERE user_id=?",c.user())).isZero();
        assertThat(db.count("SELECT sync_revision FROM user_preferences WHERE user_id=?",c.user())).isZero();
    }
    @Test void backupCountAndRestoreCapacityAreEnforcedAndAtomic() throws Exception {
        Client c=client(true); String n=id(); call(post("/api/v1/notes"),note(n,0,"原始正文"),c,200);
        String backup=call(post("/api/v1/backups"),Map.of("name","第一份"),c,200).path("data").path("id").asText();
        for(int i=1;i<10;i++) call(post("/api/v1/backups"),Map.of("name","备份"+i),c,200);
        call(post("/api/v1/backups"),Map.of("name","第十一份"),c,409);
        limits(c,1,100); call(post("/api/v1/backups/"+backup+"/restore"),null,c,413);
        assertThat(db.count("SELECT COUNT(*) FROM notes WHERE user_id=?",c.user())).isEqualTo(1);
        call(delete("/api/v1/backups/"+backup),null,c,200);
    }
    @Test void cleanupRemovesExpiredSnapshotsAndRecoversAbandonedAiReservations() throws Exception {
        Client c=client(true); limits(c,104857600,1); String n=id(); call(post("/api/v1/notes"),note(n,0,"需要保留的当前正文"),c,200);
        String backup=call(post("/api/v1/backups"),Map.of("name","过期备份"),c,200).path("data").path("id").asText();
        var reserved=ledger.reserve(c.user(),new AiRequests.Tags(id(),"标题","正文",List.of()),"mock","test"); Instant past=Instant.now().minusSeconds(60);
        db.update("UPDATE ai_requests SET expires_at=? WHERE user_id=? AND id=?",past,c.user(),reserved.id());
        db.update("UPDATE backups SET expires_at=? WHERE user_id=?",past,c.user()); db.update("UPDATE note_versions SET expires_at=? WHERE user_id=?",past,c.user());
        retention.cleanup();
        assertThat(db.count("SELECT COUNT(*) FROM backups WHERE user_id=?",c.user())).isZero(); assertThat(db.count("SELECT COUNT(*) FROM backup_items WHERE backup_id=?",backup)).isZero();
        assertThat(db.count("SELECT COUNT(*) FROM note_versions WHERE user_id=?",c.user())).isZero();
        assertThat(db.required("SELECT status FROM ai_requests WHERE id=?",reserved.id()).get("status")).isEqualTo("failed");
        call(get("/api/v1/notes/"+n),null,c,200); call(get("/api/v1/sync/pull"),null,c,200);
        call(post("/api/v1/ai/tags"),Map.of("requestId",id(),"title","回收后请求","content","正文","existingTags",List.of()),c,200);
    }
    @Test void serverAndMergedConflictChoicesPreserveExpectedVersion() throws Exception {
        Client c=client(true); String n=id(); call(post("/api/v1/notes"),note(n,0,"服务器正文"),c,200);
        for(String choice:List.of("server","merged")) {
            String conflict=call(post("/api/v1/sync/push"),Map.of("deviceUuid",c.device(),"requestId",id(),"changes",List.of(Map.of("operation","upsert","note",note(n,0,"离线稿")))),c,200).path("data").path("results").get(0).path("conflictId").asText();
            Map<String,Object> body=new HashMap<>(Map.of("serverVersion",1,"choice",choice)); if(choice.equals("merged")) body.put("merged",note(n,1,"用户合并正文"));
            var result=call(post("/api/v1/sync/conflicts/"+conflict+"/resolve"),body,c,200).path("data");
            assertThat(result.path("version").asLong()).isEqualTo(choice.equals("server")?1:2);
            assertThat(result.path("content").asText()).isEqualTo(choice.equals("server")?"服务器正文":"用户合并正文");
        }
    }
    @Test void openApiAndSwaggerAreAvailableWithoutAuthentication() throws Exception {
        var docs=mvc.perform(get("/v3/api-docs")).andReturn(); assertThat(docs.getResponse().getStatus()).isEqualTo(200);
        JsonNode schema=mapper.readTree(docs.getResponse().getContentAsByteArray()); assertThat(schema.path("paths").has("/api/v1/sync/push")).isTrue(); assertThat(schema.path("components").path("securitySchemes").has("bearer")).isTrue();
        assertThat(mvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
    @Test void invalidMethodsContentTypeAndMalformedRequestsHaveCorrectStatus() throws Exception {
        Client c=client(false);
        call(get("/api/v1/auth/login"),null,c,405);
        call(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("invalid"),null,null,415);
        call(get("/api/v1/notes").param("from","not-a-date"),null,c,400);
        call(post("/api/v1/notes").contentType(MediaType.APPLICATION_JSON).content("{}"),null,c,400);
    }
    @Test void aiReplayConsumesOnceAndPayloadMismatchFails() throws Exception {
        Client c=client(true); limits(c,104857600,1); String r=id(); Object input=Map.of("requestId",r,"title","Flutter SQL","content","性能优化","existingTags",List.of());
        var first=call(post("/api/v1/ai/tags"),input,c,200).path("data"); assertThat(first.path("simulated").asBoolean()).isTrue();
        assertThat(call(post("/api/v1/ai/tags"),input,c,200).path("data")).isEqualTo(first);
        call(post("/api/v1/ai/tags"),Map.of("requestId",r,"title","改过标题","content","性能优化","existingTags",List.of()),c,409);
        call(post("/api/v1/ai/tags"),Map.of("requestId",id(),"title","新请求","content","性能优化","existingTags",List.of()),c,429);
        assertThat(call(get("/api/v1/subscriptions/me"),null,c,200).path("data").path("aiUsedThisMonth").asInt()).isEqualTo(1);
    }
    @Test void concurrentAiReservationsCannotExceedQuota() throws Exception {
        Client c=client(true); limits(c,104857600,1); ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> work=new ArrayList<>(); for(int i=0;i<4;i++) work.add(()->{ try { ledger.reserve(c.user(),new AiRequests.Tags(id(),"并发","正文",List.of()),"mock","test"); return true; } catch(BusinessException e) { assertThat(e.code).isEqualTo("AI_QUOTA_EXCEEDED"); return false; } });
            int accepted=0; for(Future<Boolean> task:pool.invokeAll(work)) if(task.get()) accepted++; assertThat(accepted).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void realProviderSuccessMalformedFailureAndTimeoutReleaseQuota() throws Exception {
        Client c=client(true); limits(c,104857600,1); HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{ exchange.getRequestBody().readAllBytes(); byte[] body="{\"choices\":[{\"message\":{\"content\":\"{\\\"tags\\\":[\\\"数据库\\\"]}\"}}]}".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close(); }); server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";
            AiService real=new AiService(ledger,json,mapper,environment,"real",base,"test-model","test-key",1,262144);
            Object success=real.generate(c.user(),new AiRequests.Tags(id(),"数据库","正文",List.of())); assertThat(json.write(success)).contains("数据库").contains("\"simulated\":false");
            Client failed=client(true); limits(failed,104857600,1); server.removeContext("/v1/chat/completions"); server.createContext("/v1/chat/completions",e->{ e.getRequestBody().readAllBytes(); byte[] body="bad-json".getBytes(StandardCharsets.UTF_8); e.sendResponseHeaders(200,body.length); e.getResponseBody().write(body); e.close(); });
            assertThatThrownBy(()->real.generate(failed.user(),new AiRequests.Tags(id(),"标题","正文",List.of()))).isInstanceOf(BusinessException.class);
            assertThat(db.count("SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND status IN ('pending','succeeded')",failed.user())).isZero();
            server.removeContext("/v1/chat/completions"); server.createContext("/v1/chat/completions",e->{try { Thread.sleep(1500); e.sendResponseHeaders(503,-1); } catch(Exception ignored) {} finally { e.close(); }});
            assertThatThrownBy(()->real.generate(failed.user(),new AiRequests.Tags(id(),"标题","正文",List.of()))).isInstanceOf(BusinessException.class);
            assertThat(db.count("SELECT COUNT(*) FROM ai_requests WHERE user_id=? AND status IN ('pending','succeeded')",failed.user())).isZero();
        } finally { server.stop(0); }
    }
    @Test void concurrentSyncCommitsProduceNoMissingRevisions() throws Exception {
        Client c=client(true); ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> work=new ArrayList<>(); for(int i=0;i<8;i++) work.add(()->{ call(post("/api/v1/sync/push"),Map.of("deviceUuid",c.device(),"requestId",id(),"changes",List.of(Map.of("operation","upsert","note",note(id(),0,"并发内容")))),c,200); return null; });
            for(Future<Void> task:pool.invokeAll(work)) task.get();
            var page=call(get("/api/v1/sync/pull"),null,c,200).path("data"); assertThat(page.path("items")).hasSize(8); assertThat(page.path("nextCursor").asLong()).isEqualTo(8);
            for(int i=0;i<8;i++) assertThat(page.path("items").get(i).path("revision").asLong()).isEqualTo(i+1);
        } finally { pool.shutdownNow(); }
    }
}
