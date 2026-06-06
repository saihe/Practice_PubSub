package com.example.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * フロントエンドがルートからリンクを辿れること(HATEOAS)を検証する。
 * フロントは {@code /api} 以外のパス文字列をハードコードしない前提。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.task.item-interval-ms=1", "app.task.exception-probability=0.0"})
class TaskApiHateoasTest {

    @Autowired
    TestRestTemplate rest;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rootExposesTasksLink() throws Exception {
        JsonNode root = get("/api");
        assertThat(root.at("/_links/self/href").asText()).endsWith("/api");
        assertThat(root.at("/_links/tasks/href").asText()).contains("/api/tasks");
    }

    @Test
    void taskCollectionExposesCreateLink() throws Exception {
        JsonNode collection = get("/api/tasks");
        assertThat(collection.at("/_links/self/href").asText()).contains("/api/tasks");
        assertThat(collection.at("/_links/create/href").asText()).contains("/api/tasks");
    }

    @Test
    void creatingTaskReturns201WithSelfAndCancelLinks() throws Exception {
        ResponseEntity<String> created = rest.postForEntity("/api/tasks", null, String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode body = mapper.readTree(created.getBody());
        assertThat(body.at("/taskId").asText()).isNotBlank();
        assertThat(body.at("/status").asText()).isEqualTo("QUEUED");
        assertThat(body.at("/_links/self/href").asText()).contains("/api/tasks/");
        assertThat(body.at("/_links/statuses/href").asText()).contains("/statuses");
        // 投入直後は終端でないため cancel リンクが存在する。
        assertThat(body.at("/_links/cancel/href").asText()).contains("/cancel");
    }

    private JsonNode get(String path) throws Exception {
        ResponseEntity<String> res = rest.getForEntity(path, String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        return mapper.readTree(res.getBody());
    }
}
