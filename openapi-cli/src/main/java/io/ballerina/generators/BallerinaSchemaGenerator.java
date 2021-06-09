/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.generators;

import io.ballerina.compiler.syntax.tree.AbstractNodeFactory;
import io.ballerina.compiler.syntax.tree.ArrayTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.RecordFieldNode;
import io.ballerina.compiler.syntax.tree.RecordTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypeReferenceNode;
import io.ballerina.error.ErrorMessages;
import io.ballerina.openapi.exception.BallerinaOpenApiException;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createEmptyNodeList;
import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createIdentifierToken;
import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createToken;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createBuiltinSimpleNameReferenceNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createSimpleNameReferenceNode;
import static io.ballerina.compiler.syntax.tree.NodeFactory.createTypeDefinitionNode;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.OPEN_BRACE_TOKEN;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.SEMICOLON_TOKEN;
import static io.ballerina.generators.GeneratorUtils.convertOpenAPITypeToBallerina;
import static io.ballerina.generators.GeneratorUtils.escapeIdentifier;
import static io.ballerina.generators.GeneratorUtils.extractReferenceType;
import static io.ballerina.generators.GeneratorUtils.getOneOfUnionType;

/**
 *This class wraps the {@link Schema} from openapi models inorder to overcome complications
 *while populating syntax tree.
 */
public class BallerinaSchemaGenerator {
    private static final PrintStream outStream = System.err;

    public static SyntaxTree generateSyntaxTree(Path definitionPath) throws IOException,
            BallerinaOpenApiException {
        OpenAPI openApi = parseOpenAPIFile(definitionPath.toString());
        // TypeDefinitionNodes their
        List<TypeDefinitionNode> typeDefinitionNodeList = new LinkedList<>();
        if (openApi.getComponents() != null) {
            //Create typeDefinitionNode
            Components components = openApi.getComponents();
            if (components.getSchemas() != null) {
                Map<String, Schema> schemas = components.getSchemas();
                for (Map.Entry<String, Schema> schema: schemas.entrySet()) {
                    List<String> required = schema.getValue().getRequired();
                    Token typeKeyWord = AbstractNodeFactory.createIdentifierToken("public type");
                    IdentifierToken typeName = AbstractNodeFactory.createIdentifierToken(
                            escapeIdentifier(schema.getKey().trim()));
                    Token recordKeyWord = AbstractNodeFactory.createIdentifierToken("record");
                    Token bodyStartDelimiter = AbstractNodeFactory.createIdentifierToken("{");
                    //Generate RecordFiled
                    List<Node> recordFieldList = new ArrayList<>();
                    Schema schemaValue = schema.getValue();
                    if (schemaValue instanceof ComposedSchema) {
                        ComposedSchema composedSchema = (ComposedSchema) schemaValue;
                        if (composedSchema.getAllOf() != null) {
                            List<Schema> allOf = composedSchema.getAllOf();
                            for (Schema allOfschema: allOf) {
                                if (allOfschema.getType() == null && allOfschema.get$ref() != null) {
                                    //Generate typeReferenceNode
                                    Token typeRef =
                                            AbstractNodeFactory.createIdentifierToken(escapeIdentifier(
                                                    extractReferenceType(allOfschema.get$ref())));
                                    Token asterisk = AbstractNodeFactory.createIdentifierToken("*");
                                    Token semicolon = AbstractNodeFactory.createIdentifierToken(";");
                                    TypeReferenceNode recordField =
                                            NodeFactory.createTypeReferenceNode(asterisk, typeRef, semicolon);
                                    recordFieldList.add(recordField);
                                } else if (allOfschema instanceof ObjectSchema &&
                                        (allOfschema.getProperties() != null)) {
                                    Map<String, Schema> properties = allOfschema.getProperties();
                                    for (Map.Entry<String, Schema> field : properties.entrySet()) {
                                        addRecordFields(required, recordFieldList, field);
                                    }
                                }
                            }
                            NodeList<Node> fieldNodes = AbstractNodeFactory.createNodeList(recordFieldList);
                            Token bodyEndDelimiter = AbstractNodeFactory.createIdentifierToken("}");
                            RecordTypeDescriptorNode recordTypeDescriptorNode =
                                    NodeFactory.createRecordTypeDescriptorNode(recordKeyWord, bodyStartDelimiter,
                                            fieldNodes, null, bodyEndDelimiter);
                            Token semicolon = AbstractNodeFactory.createIdentifierToken(";");
                            TypeDefinitionNode typeDefinitionNode = NodeFactory.createTypeDefinitionNode(null,
                                    null, typeKeyWord, typeName, recordTypeDescriptorNode, semicolon);
                            typeDefinitionNodeList.add(typeDefinitionNode);
                        } else if (composedSchema.getOneOf() != null) {
                            List<Schema> oneOf = composedSchema.getOneOf();
                            String unionTypeCont = getOneOfUnionType(oneOf);
                            String type  = escapeIdentifier(schema.getKey().trim());
                            TypeDefinitionNode typeDefNode = createTypeDefinitionNode(null, null,
                                    createIdentifierToken("public type "),
                                    createIdentifierToken(type),
                                    createSimpleNameReferenceNode(createIdentifierToken(unionTypeCont)),
                                    createToken(SEMICOLON_TOKEN));
                            typeDefinitionNodeList.add(typeDefNode);
                        } else if (composedSchema.getAnyOf() != null) {
                            List<Schema> anyOf = composedSchema.getAnyOf();
                            String unionTypeCont = getOneOfUnionType(anyOf);
                            String type  = escapeIdentifier(schema.getKey().trim());
                            TypeDefinitionNode typeDefNode = createTypeDefinitionNode(null, null,
                                    createIdentifierToken("public type "),
                                    createIdentifierToken(type),
                                    createSimpleNameReferenceNode(createIdentifierToken(unionTypeCont)),
                                    createToken(SEMICOLON_TOKEN));
                            typeDefinitionNodeList.add(typeDefNode);
                        }
                    } else if (schema.getValue().getProperties() != null
                            || (schema.getValue() instanceof ObjectSchema)) {
                        Map<String, Schema> fields = schema.getValue().getProperties();
                        TypeDefinitionNode typeDefinitionNode = getTypeDefinitionNodeForObjectSchema(required,
                                typeKeyWord, typeName, recordFieldList, fields);
                        typeDefinitionNodeList.add(typeDefinitionNode);
                    } else if (schema.getValue().getType().equals("array")) {
                        if (schemaValue instanceof ArraySchema) {
                            ArraySchema arraySchema = (ArraySchema) schemaValue;
                            Token openSBracketToken = AbstractNodeFactory.createIdentifierToken("[");
                            Token closeSBracketToken = AbstractNodeFactory.createIdentifierToken("]");
                            IdentifierToken fieldName =
                                    AbstractNodeFactory.createIdentifierToken(escapeIdentifier(
                                            schema.getKey().trim().toLowerCase(Locale.ENGLISH)) + "list");
                            Token semicolonToken = AbstractNodeFactory.createIdentifierToken(";");
                            TypeDescriptorNode fieldTypeName;
                            if (arraySchema.getItems() != null) {
                                //Generate RecordFiled
                                fieldTypeName = extractOpenApiSchema(arraySchema.getItems());
                            } else {
                                Token type =
                                        AbstractNodeFactory.createIdentifierToken("string ");
                                fieldTypeName =  NodeFactory.createBuiltinSimpleNameReferenceNode(null, type);
                            }
                            ArrayTypeDescriptorNode arrayField =
                                    NodeFactory.createArrayTypeDescriptorNode(fieldTypeName, openSBracketToken,
                                            null, closeSBracketToken);
                            RecordFieldNode recordFieldNode = NodeFactory.createRecordFieldNode(null,
                                    null, arrayField, fieldName, null, semicolonToken);
                            NodeList<Node> fieldNodes = AbstractNodeFactory.createNodeList(recordFieldNode);
                            Token bodyEndDelimiter = AbstractNodeFactory.createIdentifierToken("}");
                            RecordTypeDescriptorNode recordTypeDescriptorNode =
                                    NodeFactory.createRecordTypeDescriptorNode(recordKeyWord, bodyStartDelimiter,
                                            fieldNodes, null, bodyEndDelimiter);
                            Token semicolon = AbstractNodeFactory.createIdentifierToken(";");
                            TypeDefinitionNode typeDefinitionNode = NodeFactory.createTypeDefinitionNode(null,
                                    null, typeKeyWord, typeName, recordTypeDescriptorNode, semicolon);
                            typeDefinitionNodeList.add(typeDefinitionNode);
                        }
                    }
                }
            }
        }
        //Create imports
        NodeList<ImportDeclarationNode> imports = AbstractNodeFactory.createEmptyNodeList();
        // Create module member declaration
        NodeList<ModuleMemberDeclarationNode> moduleMembers =
                AbstractNodeFactory.createNodeList(typeDefinitionNodeList.toArray(
                        new TypeDefinitionNode[typeDefinitionNodeList.size()]));

        Token eofToken = AbstractNodeFactory.createIdentifierToken("");
        ModulePartNode modulePartNode = NodeFactory.createModulePartNode(imports, moduleMembers, eofToken);

        TextDocument textDocument = TextDocuments.from("");
        SyntaxTree syntaxTree = SyntaxTree.from(textDocument);
        return syntaxTree.modifyWith(modulePartNode);
    }

    /**
     * This function use to create typeDefinitionNode for objectSchema.
     *
     * @param required - This string list include required fields in properties.
     * @param typeKeyWord   - Type keyword for record.
     * @param typeName      - Record Name
     * @param recordFieldList - RecordFieldList
     * @param fields          - schema properties map
     * @return This return record TypeDefinitionNode
     * @throws BallerinaOpenApiException
     */

    public static TypeDefinitionNode getTypeDefinitionNodeForObjectSchema(List<String> required, Token typeKeyWord,
                                                                           IdentifierToken typeName,
                                                                           List<Node> recordFieldList,
                                                                           Map<String, Schema> fields)
            throws BallerinaOpenApiException {

        TypeDefinitionNode typeDefinitionNode;
        if (fields != null) {
            for (Map.Entry<String, Schema> field : fields.entrySet()) {
                addRecordFields(required, recordFieldList, field);
            }
            NodeList<Node> fieldNodes = AbstractNodeFactory.createNodeList(recordFieldList);
            RecordTypeDescriptorNode recordTypeDescriptorNode =
                    NodeFactory.createRecordTypeDescriptorNode(createToken(SyntaxKind.RECORD_KEYWORD),
                            createToken(OPEN_BRACE_TOKEN), fieldNodes, null,
                            createToken(SyntaxKind.CLOSE_BRACE_TOKEN));
            typeDefinitionNode = NodeFactory.createTypeDefinitionNode(null,
                    null, typeKeyWord, typeName, recordTypeDescriptorNode, createToken(SEMICOLON_TOKEN));
        } else {
            RecordTypeDescriptorNode recordTypeDescriptorNode =
                    NodeFactory.createRecordTypeDescriptorNode(createToken(SyntaxKind.RECORD_KEYWORD),
                            createToken(OPEN_BRACE_TOKEN), createEmptyNodeList(), null,
                            createToken(SyntaxKind.CLOSE_BRACE_TOKEN));
            typeDefinitionNode = NodeFactory.createTypeDefinitionNode(null,
                    null, typeKeyWord, typeName, recordTypeDescriptorNode, createToken(SEMICOLON_TOKEN));
        }
        return typeDefinitionNode;
    }

    /**
     * This util for generate record field with given schema properties.
     */
    private static void addRecordFields(List<String> required, List<Node> recordFieldList,
                                        Map.Entry<String, Schema> field) throws BallerinaOpenApiException {

        RecordFieldNode recordFieldNode;
        //FiledName
        IdentifierToken fieldName =
                AbstractNodeFactory.createIdentifierToken(escapeIdentifier(field.getKey().trim()));
        TypeDescriptorNode fieldTypeName = extractOpenApiSchema(field.getValue());
        Token semicolonToken = AbstractNodeFactory.createIdentifierToken(";");
        Token questionMarkToken = AbstractNodeFactory.createIdentifierToken("?");
        if (required != null) {
            if (!required.contains(field.getKey().trim())) {
                recordFieldNode = NodeFactory.createRecordFieldNode(null, null,
                        fieldTypeName, fieldName, questionMarkToken, semicolonToken);
            } else {
                recordFieldNode = NodeFactory.createRecordFieldNode(null, null,
                        fieldTypeName, fieldName, null, semicolonToken);
            }
        } else {
            recordFieldNode = NodeFactory.createRecordFieldNode(null, null,
                    fieldTypeName, fieldName, questionMarkToken, semicolonToken);
        }
        recordFieldList.add(recordFieldNode);
    }

    /**
     * Common method to extract OpenApi Schema type objects in to Ballerina type compatible schema objects.
     *
     * @param schema - OpenApi Schema
     */
    private static TypeDescriptorNode extractOpenApiSchema(Schema schema) throws BallerinaOpenApiException {

        if (schema.getType() != null || schema.getProperties() != null) {
            if (schema.getType() != null && ((schema.getType().equals("integer") || schema.getType().equals("number"))
                    || schema.getType().equals("string") || schema.getType().equals("boolean"))) {
                String type = convertOpenAPITypeToBallerina(schema.getType().trim());
                if (schema.getType().equals("number")) {
                    if (schema.getFormat() != null) {
                        type = convertOpenAPITypeToBallerina(schema.getFormat().trim());
                    }
                }
                if (schema.getNullable() != null) {
                    if (schema.getNullable()) {
                        type = type + "?";
                    }
                }
                Token typeName = AbstractNodeFactory.createIdentifierToken(type);
                return createBuiltinSimpleNameReferenceNode(null, typeName);
            } else if (schema.getType() != null && schema.getType().equals("array")) {
                if (schema instanceof ArraySchema) {
                    final ArraySchema arraySchema = (ArraySchema) schema;
                    if (arraySchema.getItems() != null) {
                        // single array
                        Token openSBracketToken = AbstractNodeFactory.createIdentifierToken("[");
                        Token closeSBracketToken = AbstractNodeFactory.createIdentifierToken("]");
                        String type;
                        Token typeName;
                        TypeDescriptorNode memberTypeDesc;
                        Schema schemaItem = arraySchema.getItems();
                        if (schemaItem.get$ref() != null) {
                            type = extractReferenceType(arraySchema.getItems().get$ref());
                            if (arraySchema.getNullable()) {
                                closeSBracketToken = AbstractNodeFactory.createIdentifierToken("]?");
                            }
                            typeName = AbstractNodeFactory.createIdentifierToken(type);
                            memberTypeDesc = createBuiltinSimpleNameReferenceNode(null, typeName);
                            return NodeFactory.createArrayTypeDescriptorNode(memberTypeDesc, openSBracketToken,
                                    null, closeSBracketToken);
                        } else if (schemaItem instanceof ArraySchema) {
                            memberTypeDesc = extractOpenApiSchema(arraySchema.getItems());
                            return NodeFactory.createArrayTypeDescriptorNode(memberTypeDesc, openSBracketToken,
                                    null, closeSBracketToken);
                        } else if (schemaItem instanceof ObjectSchema) {
                            //Array has inline record
                            ObjectSchema inlineSchema = (ObjectSchema) schemaItem;
                            memberTypeDesc = extractOpenApiSchema(inlineSchema);
                            return NodeFactory.createArrayTypeDescriptorNode(memberTypeDesc, openSBracketToken,
                                    null, closeSBracketToken);
                        } else if (schemaItem.getType() != null) {
                            type = schemaItem.getType();
                            if (arraySchema.getNullable()) {
                                type = type + "?";
                            }
                            typeName = AbstractNodeFactory.createIdentifierToken(convertOpenAPITypeToBallerina(type));
                            memberTypeDesc = createBuiltinSimpleNameReferenceNode(null, typeName);
                            return NodeFactory.createArrayTypeDescriptorNode(memberTypeDesc, openSBracketToken,
                                    null, closeSBracketToken);
                        } else {
                            type = "anydata";
                            if (arraySchema.getNullable()) {
                                type = type + "?";
                            }
                            typeName = AbstractNodeFactory.createIdentifierToken(type);
                            memberTypeDesc = createBuiltinSimpleNameReferenceNode(null, typeName);
                            return NodeFactory.createArrayTypeDescriptorNode(memberTypeDesc, openSBracketToken,
                                    null, closeSBracketToken);
                        }
                    }
                }
            } else if ((schema.getType() != null && schema.getType().equals("object"))) {
                if (schema.getProperties() != null) {
                    Map<String, Schema> properties = schema.getProperties();
                    Token recordKeyWord = AbstractNodeFactory.createIdentifierToken("record ");
                    Token bodyStartDelimiter = AbstractNodeFactory.createIdentifierToken("{ ");
                    Token bodyEndDelimiter = AbstractNodeFactory.createIdentifierToken("} ");
                    List<Node> recordFList = new ArrayList<>();
                    List<String> required = schema.getRequired();
                    for (Map.Entry<String, Schema> property: properties.entrySet()) {
                        addRecordFields(required, recordFList, property);
                    }
                    NodeList<Node> fieldNodes = AbstractNodeFactory.createNodeList(recordFList);

                    return NodeFactory.createRecordTypeDescriptorNode(recordKeyWord, bodyStartDelimiter, fieldNodes, null
                            , bodyEndDelimiter);
                } else if (schema.get$ref() != null) {
                    String type = extractReferenceType(schema.get$ref());
                    if (schema.getNullable() != null) {
                        if (schema.getNullable()) {
                            type = type + "?";
                        }
                    }
                    Token typeName = AbstractNodeFactory.createIdentifierToken(type);
                    return createBuiltinSimpleNameReferenceNode(null, typeName);
                } else {
                    Token typeName = AbstractNodeFactory.createIdentifierToken(
                                    convertOpenAPITypeToBallerina(schema.getType().trim()));
                    return createBuiltinSimpleNameReferenceNode(null, typeName);
                }
            } else {
                outStream.println("Encountered an unsupported type. Type `anydata` would be used for the field.");
                Token typeName = AbstractNodeFactory.createIdentifierToken("anydata");
                return createBuiltinSimpleNameReferenceNode(null, typeName);
            }
        } else if (schema.get$ref() != null) {
            String type = extractReferenceType(schema.get$ref());
            if (schema.getNullable() != null) {
                if (schema.getNullable()) {
                    type = type + "?";
                }
            }
            Token typeName = AbstractNodeFactory.createIdentifierToken(type);
            return createBuiltinSimpleNameReferenceNode(null, typeName);
        } else if (schema instanceof ComposedSchema) {
            ComposedSchema composedSchema = (ComposedSchema) schema;
            if (composedSchema.getOneOf() != null) {
                List<Schema> oneOf = composedSchema.getOneOf();
                Token typeName = AbstractNodeFactory.createIdentifierToken(getOneOfUnionType(oneOf));
                return createBuiltinSimpleNameReferenceNode(null, typeName);
            } else if (composedSchema.getAllOf() != null) {
                List<Schema> allOf = composedSchema.getAllOf();
                Token typeName = AbstractNodeFactory.createIdentifierToken(getOneOfUnionType(allOf));
                return createBuiltinSimpleNameReferenceNode(null, typeName);
            }
        } else {
            //This contains a fallback to Ballerina common type `any` if the OpenApi specification type is not defined
            // or not compatible with any of the current Ballerina types.
            outStream.println("Encountered an unsupported type. Type `anydata` would be used for the field.");
            String type = "anydata";
            if (schema.getNullable() != null) {
                if (schema.getNullable()) {
                    type = type + "?";
                }
            }
            Token typeName = AbstractNodeFactory.createIdentifierToken(type);
            return createBuiltinSimpleNameReferenceNode(null, typeName);
        }
        String type = "anydata";
        if (schema.getNullable() != null) {
            if (schema.getNullable()) {
                type = type + "?";
            }
        }
        Token typeName = AbstractNodeFactory.createIdentifierToken(type);
        return createBuiltinSimpleNameReferenceNode(null, typeName);
    }

    /**
     * Parse and get the {@link OpenAPI} for the given OpenAPI contract.
     *
     * @param definitionURI     URI for the OpenAPI contract
     * @return {@link OpenAPI}  OpenAPI model
     * @throws BallerinaOpenApiException in case of exception
     */
    public static OpenAPI parseOpenAPIFile(String definitionURI) throws IOException, BallerinaOpenApiException {
        Path contractPath = Paths.get(definitionURI);
        if (!Files.exists(contractPath)) {
            throw new BallerinaOpenApiException(ErrorMessages.invalidFilePath(definitionURI));
        }
        if (!(definitionURI.endsWith(".yaml") || definitionURI.endsWith(".json") || definitionURI.endsWith(".yml"))) {
            throw new BallerinaOpenApiException(ErrorMessages.invalidFileType());
        }
        String openAPIFileContent = Files.readString(Paths.get(definitionURI));
        SwaggerParseResult parseResult = new OpenAPIV3Parser().readContents(openAPIFileContent);
        if (!parseResult.getMessages().isEmpty()) {
            throw new BallerinaOpenApiException(ErrorMessages.invalidFile(definitionURI));
        }
        return parseResult.getOpenAPI();
    }
}
