/*
 * (c) Copyright 2023 Birch Solutions Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fern.java.client.generators.endpoint;

import com.fern.ir.v9.model.http.HttpEndpoint;
import com.fern.ir.v9.model.http.HttpService;
import com.fern.ir.v9.model.http.PathParameter;
import com.fern.java.client.ClientGeneratorContext;
import com.fern.java.client.GeneratedClientOptions;
import com.fern.java.output.GeneratedObjectMapper;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import okhttp3.Response;

public abstract class AbstractEndpointWriter {

    public static final String HTTP_URL_NAME = "_httpUrl";
    public static final String HTTP_URL_BUILDER_NAME = "_httpUrlBuilder";
    public static final String REQUEST_NAME = "_request";
    public static final String REQUEST_BUILDER_NAME = "_requestBuilder";
    public static final String REQUEST_BODY_NAME = "_requestBody";
    public static final String RESPONSE_NAME = "_response";
    private final HttpService httpService;
    private final HttpEndpoint httpEndpoint;
    private final GeneratedClientOptions generatedClientOptions;
    private final FieldSpec clientOptionsField;
    private final ClientGeneratorContext clientGeneratorContext;
    private final MethodSpec.Builder endpointMethodBuilder;
    private final GeneratedObjectMapper generatedObjectMapper;

    public AbstractEndpointWriter(
            HttpService httpService,
            HttpEndpoint httpEndpoint,
            GeneratedObjectMapper generatedObjectMapper,
            ClientGeneratorContext clientGeneratorContext,
            FieldSpec clientOptionsField,
            GeneratedClientOptions generatedClientOptions) {
        this.httpService = httpService;
        this.httpEndpoint = httpEndpoint;
        this.clientOptionsField = clientOptionsField;
        this.generatedClientOptions = generatedClientOptions;
        this.clientGeneratorContext = clientGeneratorContext;
        this.generatedObjectMapper = generatedObjectMapper;
        this.endpointMethodBuilder = MethodSpec.methodBuilder(
                        httpEndpoint.getName().get().getCamelCase().getSafeName())
                .addModifiers(Modifier.PUBLIC);
    }

    public final MethodSpec generate() {
        // Step 1: Add Path Params as parameters
        List<ParameterSpec> pathParameters = getPathParameters();
        for (ParameterSpec pathParameter : pathParameters) {
            endpointMethodBuilder.addParameter(pathParameter);
        }

        // Step 2: Add additional parameters
        endpointMethodBuilder.addParameters(additionalParameters());

        // Step 3: Get http client initializer
        CodeBlock httpClientInitializer =
                getInitializeHttpUrlCodeBlock(clientOptionsField, generatedClientOptions, pathParameters);
        endpointMethodBuilder.addCode(httpClientInitializer);

        // Step 4: Get request initializer
        CodeBlock requestInitializer = getInitializeRequestCodeBlock(
                clientOptionsField, generatedClientOptions, httpEndpoint, generatedObjectMapper);
        endpointMethodBuilder.addCode(requestInitializer);

        // Step 5: Make http request and handle responses
        CodeBlock responseParser = getResponseParserCodeBlock();
        endpointMethodBuilder.addCode(responseParser);
        return endpointMethodBuilder.build();
    }

    public abstract List<ParameterSpec> additionalParameters();

    public abstract CodeBlock getInitializeHttpUrlCodeBlock(
            FieldSpec clientOptionsMember, GeneratedClientOptions clientOptions, List<ParameterSpec> pathParameters);

    public abstract CodeBlock getInitializeRequestCodeBlock(
            FieldSpec clientOptionsMember,
            GeneratedClientOptions clientOptions,
            HttpEndpoint endpoint,
            GeneratedObjectMapper objectMapper);

    public final CodeBlock getResponseParserCodeBlock() {
        CodeBlock.Builder httpResponseBuilder = CodeBlock.builder()
                .beginControlFlow("try")
                .addStatement(
                        "$T $L = $N.$N().newCall($L).execute()",
                        Response.class,
                        RESPONSE_NAME,
                        clientOptionsField,
                        generatedClientOptions.httpClient(),
                        REQUEST_NAME)
                .beginControlFlow("if ($L.isSuccessful())", RESPONSE_NAME);
        if (httpEndpoint.getResponse().getType().isPresent()) {
            TypeName returnType = clientGeneratorContext
                    .getPoetTypeNameMapper()
                    .convertToTypeName(
                            true, httpEndpoint.getResponse().getType().get());
            endpointMethodBuilder.returns(returnType);
            httpResponseBuilder
                    .addStatement(
                            "return $T.$L.readValue($L.body().string(), $T.class)",
                            generatedObjectMapper.getClassName(),
                            generatedObjectMapper.jsonMapperStaticField().name,
                            RESPONSE_NAME,
                            returnType)
                    .endControlFlow();
        } else {
            httpResponseBuilder.addStatement("return").endControlFlow();
        }
        httpResponseBuilder.addStatement("throw new $T()", RuntimeException.class);
        httpResponseBuilder
                .endControlFlow()
                .beginControlFlow("catch ($T e)", Exception.class)
                .addStatement("throw new $T(e)", RuntimeException.class)
                .endControlFlow()
                .build();
        return httpResponseBuilder.build();
    }

    private List<ParameterSpec> getPathParameters() {
        List<ParameterSpec> pathParameterSpecs = new ArrayList<>();
        httpService.getPathParameters().forEach(pathParameter -> {
            pathParameterSpecs.add(convertPathParameter(pathParameter));
        });
        httpEndpoint.getPathParameters().forEach(pathParameter -> {
            pathParameterSpecs.add(convertPathParameter(pathParameter));
        });
        return pathParameterSpecs;
    }

    private ParameterSpec convertPathParameter(PathParameter pathParameter) {
        return ParameterSpec.builder(
                        clientGeneratorContext
                                .getPoetTypeNameMapper()
                                .convertToTypeName(true, pathParameter.getValueType()),
                        pathParameter.getName().getCamelCase().getSafeName())
                .build();
    }
}