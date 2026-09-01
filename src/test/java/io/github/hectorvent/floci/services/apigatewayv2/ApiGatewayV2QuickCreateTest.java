package io.github.hectorvent.floci.services.apigatewayv2;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * CreateApi's {@code target} parameter ("quick create") must provision an AWS_PROXY
 * integration, a "$default" catch-all route, and an auto-deploy "$default" stage —
 * exactly what AWS does — so the API is immediately invocable. Without a "$default"
 * stage, {@code ApiGatewayExecuteApiHostFilter} can't resolve one and every invocation
 * silently falls through to whichever other virtual-hosted-style filter (e.g. S3) claims
 * the request next, instead of reaching the API. See issue #1902.
 */
@QuarkusTest
class ApiGatewayV2QuickCreateTest {

    private static final String TARGET =
            "arn:aws:lambda:us-east-1:000000000000:function:quick-create-target";

    @Test
    void quickCreateProvisionsIntegrationRouteAndStage() {
        String apiId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"quick-create-api","protocolType":"HTTP","target":"%s"}
                        """.formatted(TARGET))
                .when().post("/v2/apis")
                .then()
                .statusCode(201)
                .body("apiId", notNullValue())
                .extract().path("apiId");

        given()
                .when().get("/v2/apis/" + apiId + "/integrations")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].integrationType", equalTo("AWS_PROXY"))
                .body("items[0].integrationUri", equalTo(TARGET));

        given()
                .when().get("/v2/apis/" + apiId + "/routes")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].routeKey", equalTo("$default"))
                .body("items[0].target", startsWith("integrations/"));

        given()
                .when().get("/v2/apis/" + apiId + "/stages/$default")
                .then()
                .statusCode(200)
                .body("stageName", equalTo("$default"))
                .body("autoDeploy", equalTo(true));
    }

    @Test
    void createApiWithoutTargetProvisionsNothing() {
        String apiId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"no-target-api","protocolType":"HTTP"}
                        """)
                .when().post("/v2/apis")
                .then()
                .statusCode(201)
                .extract().path("apiId");

        given()
                .when().get("/v2/apis/" + apiId + "/integrations")
                .then()
                .statusCode(200)
                .body("items", hasSize(0));

        given()
                .when().get("/v2/apis/" + apiId + "/routes")
                .then()
                .statusCode(200)
                .body("items", hasSize(0));

        given()
                .when().get("/v2/apis/" + apiId + "/stages/$default")
                .then()
                .statusCode(404);
    }
}
