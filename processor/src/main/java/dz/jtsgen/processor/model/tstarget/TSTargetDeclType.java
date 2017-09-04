/*
 * Copyright (c) 2017 Dragan Zuvic
 *
 * This file is part of jtsgen.
 *
 * jtsgen is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jtsgen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jtsgen.  If not, see http://www.gnu.org/licenses/
 *
 */

package dz.jtsgen.processor.model.tstarget;

import dz.jtsgen.processor.dsl.model.*;
import dz.jtsgen.processor.dsl.model.TypeMappingExpression;
import dz.jtsgen.processor.model.ConversionCoverage;
import dz.jtsgen.processor.model.NameSpaceMapper;
import dz.jtsgen.processor.model.TSTargetType;
import dz.jtsgen.processor.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dz.jtsgen.processor.model.tstarget.TSTargetFactory.mapNamespaces;
import static dz.jtsgen.processor.util.LilltleLazy.lazy;

/**
 * represents a simple type direct conversion for declaration types
 */
final class TSTargetDeclType implements TSTargetType {

    //the canonical java type, lazyly evaluated
    private final Supplier<String> javaType;
    private final Supplier<String> nameSpace;

    private final TypeMappingExpression mappingExpression;
    private final Map<String, TSTargetType> typeParametersTypes;

    private TSTargetDeclType(TypeMappingExpression mappingExpression, Map<String, TSTargetType> typeParametersTypes, Supplier<String> javaType, Supplier<String> namespace) {
        assert mappingExpression != null;
        this.javaType = javaType == null ? lazy(() -> mappingExpression.names().stream().collect(Collectors.joining("."))) : javaType;
        this.mappingExpression = mappingExpression;
        this.typeParametersTypes = typeParametersTypes == null ? new HashMap<>() : typeParametersTypes;
        this.nameSpace = namespace == null ? lazy(() -> StringUtils.untill(javaType())) : namespace;
    }

    TSTargetDeclType(TypeMappingExpression mappingExpression, Map<String, TSTargetType> typeParametersTypes, boolean ignoreSelfNamespace) {
        this(mappingExpression, typeParametersTypes, null, ignoreSelfNamespace ? lazy(() -> "") : null);
    }


    public String getJavaType() {
        return this.javaType.get();
    }

    @Override
    public ConversionCoverage conversionCoverage() {
        return this.mappingExpression.conversionCoverage();
    }

    /**
     * this toString is meant as an textual representation for the renderer
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        // IMPROVE: add implicit name space to DSL and dont't do the following if not specified
        if (!"".equals(this.nameSpace.get())) result.append(this.nameSpace.get()).append(".");
        result.append(tsLiteralsToString(mappingExpression.tsExpressionElements(), typeParametersTypes));
        return result.toString();
    }

    private static String tsLiteralsToString(List<TSExpressionElement> tsExpressionElements, Map<String, TSTargetType> typeParametersTypes) {
        TSExpressionVisitor<String> visitor = new TSExpressionVisitorToStringConverter<String>(typeParametersTypes);
        return tsExpressionElements.stream().map(x -> x.accept(visitor)).collect(Collectors.joining(""));
    }

    @Override
    public boolean isReferenceType() {
        return true;
    }

    @Override
    public List<String> typeParameters() {
        return this.mappingExpression.typeVariables();
    }

    @Override
    public Map<String, TSTargetType> typeParameterTypes() {
        return this.typeParametersTypes;
    }

    private String javaType() {
        return this.javaType.get();
    }

    @Override
    public TSTargetType mapNameSpace(NameSpaceMapper nsm) {
        final String newNameSpace = nsm.mapNameSpace(this.nameSpace.get());
        return new TSTargetDeclType(
                this.mappingExpression
                , mapNamespaces(this.typeParametersTypes, nsm), this.javaType, () -> newNameSpace);
    }

    @Override
    public TSTargetType withTypeParams(Map<String, TSTargetType> typeParams) {
        return new TSTargetDeclType(this.mappingExpression, typeParams, this.javaType, this.nameSpace);
    }

}

class TSExpressionVisitorToStringConverter<T> implements TSExpressionVisitor<String> {

    private final Map<String, TSTargetType> resolvedTypeVars;

    TSExpressionVisitorToStringConverter(Map<String, TSTargetType> typeParametersTypes) {
        this.resolvedTypeVars = typeParametersTypes;
    }

    @Override
    public String visitTerminal(TSMappedTerminal t) {
        return t.value();
    }

    @Override
    public String visitTypeVar(TSMappedTypeVar t) {
        return resolvedTypeVars.get(t.value()) != null ? resolvedTypeVars.get(t.value()).toString() : "";
    }

    @Override
    public String visitTypeContainer(TSMappedTypeContainer t) {
        return t.expressions().stream().map( x -> x.accept(this)).collect(Collectors.joining(""));
    }
}


