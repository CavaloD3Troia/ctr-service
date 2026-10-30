package org.cavalodetroia.ctr.componenttests.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;

public record ApiResponse(int status, HttpHeaders headers, String body) {

    public <T> T json(final String path) {
        return JsonPath.read(body, path);
    }

    public String header(final String name) {
        return headers.getFirst(name);
    }
}
