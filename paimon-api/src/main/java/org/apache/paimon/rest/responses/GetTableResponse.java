/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.rest.responses;

import org.apache.paimon.rest.RESTResponse;
import org.apache.paimon.schema.Schema;

import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonGetter;
import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.paimon.shade.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

/** Response for getting table. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetTableResponse extends AuditRESTResponse implements RESTResponse {

    private static final String FIELD_ID = "id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_IS_EXTERNAL = "isExternal";
    private static final String FIELD_SCHEMA_ID = "schemaId";
    private static final String FIELD_SCHEMA = "schema";

    @JsonProperty(FIELD_ID)
    private final String id;

    @JsonProperty(FIELD_NAME)
    private final String name;

    @JsonProperty(FIELD_PATH)
    private final String path;

    @JsonProperty(FIELD_IS_EXTERNAL)
    private final boolean isExternal;

    @JsonProperty(FIELD_SCHEMA_ID)
    private final long schemaId;

    @JsonProperty(FIELD_SCHEMA)
    private final Schema schema;

    @JsonCreator
    public GetTableResponse(
            @JsonProperty(FIELD_ID) String id,
            @JsonProperty(FIELD_NAME) String name,
            @JsonProperty(FIELD_PATH) String path,
            @JsonProperty(FIELD_IS_EXTERNAL) boolean isExternal,
            @JsonProperty(FIELD_SCHEMA_ID) long schemaId,
            @JsonProperty(FIELD_SCHEMA) Schema schema,
            @JsonProperty(FIELD_OWNER) String owner,
            @JsonProperty(FIELD_CREATED_AT) long createdAt,
            @JsonProperty(FIELD_CREATED_BY) String createdBy,
            @JsonProperty(FIELD_UPDATED_AT) long updatedAt,
            @JsonProperty(FIELD_UPDATED_BY) String updatedBy) {
        super(owner, createdAt, createdBy, updatedAt, updatedBy);
        this.id = id;
        this.name = name;
        this.path = path;
        this.isExternal = isExternal;
        this.schemaId = schemaId;
        this.schema = schema;
    }

    @JsonGetter(FIELD_ID)
    public String getId() {
        return this.id;
    }

    @JsonGetter(FIELD_NAME)
    public String getName() {
        return this.name;
    }

    @JsonGetter(FIELD_PATH)
    public String getPath() {
        return this.path;
    }

    @JsonGetter(FIELD_IS_EXTERNAL)
    public boolean isExternal() {
        return isExternal;
    }

    @JsonGetter(FIELD_SCHEMA_ID)
    public long getSchemaId() {
        return this.schemaId;
    }

    @JsonGetter(FIELD_SCHEMA)
    public Schema getSchema() {
        return this.schema;
    }
}
