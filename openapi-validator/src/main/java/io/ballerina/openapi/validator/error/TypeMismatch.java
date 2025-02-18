/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */


package io.ballerina.openapi.validator.error;

import io.ballerina.openapi.validator.Constants;
import io.ballerina.tools.diagnostics.Location;

/**
 * This for identify the fields that are same names with different data type in given json schema and bVarsymbol.
 */
public class TypeMismatch extends ValidationError {
    private String recordName;
    private String fieldName;
    private Location location;
    private Constants.Type typeJsonSchema;
    private Constants.Type typeBallerinaType;

    public TypeMismatch(String fieldName, Constants.Type typeJsonSchema, Constants.Type typeBallerinaType,
                        Location location) {
        this.fieldName = fieldName;
        this.typeJsonSchema = typeJsonSchema;
        this.typeBallerinaType = typeBallerinaType;
        this.recordName = null;
        this.location = location;
    }
    public TypeMismatch(String fieldName, Constants.Type typeJsonSchema, Constants.Type typeBallerinaType,
                        String recordName, Location location) {
        this.fieldName = fieldName;
        this.typeJsonSchema = typeJsonSchema;
        this.typeBallerinaType = typeBallerinaType;
        this.recordName = recordName;
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setTypeJsonSchemaType(Constants.Type typeJsonSchema) {
        this.typeJsonSchema = typeJsonSchema;
    }

    public void setTypeBallerinaType(Constants.Type typeBallerinaType) {
        this.typeBallerinaType = typeBallerinaType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public  Constants.Type getTypeJsonSchema() {
        return typeJsonSchema;
    }

    public  Constants.Type getTypeBallerinaType() {
        return typeBallerinaType;
    }

    public String getRecordName() {
        return recordName;
    }

}
