package com.delta.dms.ops.aidevopsplatform.web.api;

import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.NoSuchElementException;

@RestControllerAdvice(basePackageClasses=ManagedServiceController.class)
@Profile("demo")
public class QueryErrorHandler {
    public record ErrorBody(String code) {}
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorBody> missing() { return ResponseEntity.status(404).body(new ErrorBody("NOT_FOUND")); }
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ErrorBody> denied() { return ResponseEntity.status(403).body(new ErrorBody("FORBIDDEN")); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> invalid() { return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST")); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorBody> unavailable() { return ResponseEntity.status(503).body(new ErrorBody("QUERY_UNAVAILABLE")); }
}
