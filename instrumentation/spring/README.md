# OpenTelemetry Instrumentation: Spring and Spring Boot

<!-- ReadMe is in progress -->
<!-- TO DO: Add sections for starter guide -->

This package streamlines the manual instrumentation process of OpenTelemetry for [Spring](https://spring.io/projects/spring-framework) and [Spring Boot](https://spring.io/projects/spring-boot) applications. It will enable you to add traces to requests and database calls with minimal changes to application code. This package will not fully automate your OpenTelemetry instrumentation, instead, it will provide you with better tools to instrument your own code.

## Settings

| System property                                                               | Type    | Default | Description                                                                                                                                                                                                                                                                                                                                             |
|-------------------------------------------------------------------------------|---------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `otel.instrumentation.spring-webmvc.experimental-span-attributes`             | Boolean | `false` | Enable the capture of experimental span attributes for Spring Web MVC version 3.1.                                                                                                                                                                                                                                                                      |
