/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.stratio.connector.inmemory;

import com.stratio.connector.inmemory.datastore.InMemoryOperations;
import com.stratio.connector.inmemory.datastore.InMemoryRelation;
import com.stratio.connector.inmemory.datastore.datatypes.JoinValue;
import com.stratio.connector.inmemory.datastore.datatypes.SimpleValue;
import com.stratio.connector.inmemory.datastore.selector.*;
import com.stratio.crossdata.common.data.TableName;
import com.stratio.crossdata.common.exceptions.ExecutionException;
import com.stratio.crossdata.common.logicalplan.*;
import com.stratio.crossdata.common.statements.structures.*;

import java.util.*;

/**
 * Class that encapsulate the {@link com.stratio.crossdata.common.logicalplan.Project} parse.
 */
public class InMemoryQuery {

    /**
     * Map with the equivalences between crossdata operators and the ones supported by our datastore.
     */
    private static final Map<Operator, InMemoryOperations> OPERATIONS_TRANFORMATIONS = new HashMap<>();

    static {
        OPERATIONS_TRANFORMATIONS.put(Operator.EQ, InMemoryOperations.EQ);
        OPERATIONS_TRANFORMATIONS.put(Operator.GT, InMemoryOperations.GT);
        OPERATIONS_TRANFORMATIONS.put(Operator.LT, InMemoryOperations.LT);
        OPERATIONS_TRANFORMATIONS.put(Operator.GET, InMemoryOperations.GET);
        OPERATIONS_TRANFORMATIONS.put(Operator.LET, InMemoryOperations.LET);
        OPERATIONS_TRANFORMATIONS.put(Operator.IN , InMemoryOperations.IN);
    }

    private TableName tableName;
    private String catalogName;
    private List<InMemoryRelation> relations = new ArrayList<>();
    private List<InMemorySelector> outputColumns;
    private Project project;
    private OrderBy orderByStep = null;
    private Select selectStep;
    private Join joinStep;

    /**
     * Build a InMemoryQuery using a {@link com.stratio.crossdata.common.logicalplan.Project}.
     *
     * @param project a Project Initial Step
     * @throws ExecutionException
     */
    public InMemoryQuery(Project project) throws ExecutionException {
        this.project = project;
        extractSteps(project);
        tableName = project.getTableName();
        catalogName = project.getCatalogName();
        outputColumns = transformIntoSelectors(selectStep.getColumnMap().keySet());

        processJoins();
    }

    /**
     * Build a InMemoryQuery using a {@link com.stratio.crossdata.common.logicalplan.Project} and a
     * result of the others parts of the query, to by added as filters in this part.
     *
     * @param project
     * @param results
     * @throws ExecutionException
     */
    public InMemoryQuery(Project project, List<SimpleValue[]> results) throws ExecutionException {
        this(project);
        addJoinFilter(results);
    }

    /**
     * Return the JoinTerm of this project.
     * @return
     */
    private Selector getMyJoinTerm() {

        Selector myTerm;
        if(joinStep.getJoinRelations().get(0).getLeftTerm().getTableName().getName().equals(project.getTableName().getAlias())) {
            myTerm =joinStep.getJoinRelations().get(0).getLeftTerm();
        }else{
            myTerm = joinStep.getJoinRelations().get(0).getRightTerm();
        }
        return myTerm;
    }

    /**
     * Adds the new Output Columns that holds the JoinColumns
     */
    private void processJoins() {

        if (joinStep != null){
            String name = joinStep.toString();
            Selector myTerm = getMyJoinTerm();
            Selector otherTerm;

            if(joinStep.getJoinRelations().get(0).getLeftTerm().getTableName().getName().equals(project.getTableName().getAlias())) {
                otherTerm = joinStep.getJoinRelations().get(0).getRightTerm();
            }else{
                otherTerm =joinStep.getJoinRelations().get(0).getLeftTerm();
            }

            outputColumns.add(new InMemoryJoinSelector(name,myTerm,otherTerm));
        }
    }

    /**
     * Adds a "IN" filter for each JoinColumn, using the values from the result List.
     *
     * @param results the results of the other terms that will be used as filter values.
     */
    private void addJoinFilter(List<SimpleValue[]> results) {
        int columnIndex = 0;
        for (SimpleValue field: results.get(0)){


            if (field instanceof JoinValue){
                String columnName = getMyJoinTerm().getColumnName().getName();
                List<Object> joinValues = new ArrayList<>();
                for (SimpleValue[] row:results){
                    joinValues.add(row[columnIndex].getValue());
                }
                InMemoryRelation joinFilter =  new InMemoryRelation(columnName, OPERATIONS_TRANFORMATIONS.get(Operator.IN), joinValues);
                relations.add(joinFilter);
            }

            columnIndex++;
        }

    }

    /**
     * Parse and transform the {@link Project} until a {@link com.stratio.crossdata.common.logicalplan.Join} to a
     * InMemory Query representation.
     *
     * @param project
     * @throws ExecutionException
     */
    private void extractSteps(Project project) throws ExecutionException{
        try {
            LogicalStep currentStep = project;
            while(currentStep != null){
                if(currentStep instanceof OrderBy){
                    orderByStep = (OrderBy) (currentStep);
                }else if(currentStep instanceof Select){
                    selectStep = (Select) currentStep;
                }else if (currentStep instanceof Join){
                    joinStep = (Join) currentStep;
                    break;
                } else if(currentStep instanceof Filter){
                    relations.add(toInMemoryRelation(Filter.class.cast(currentStep)));
                }
                currentStep = currentStep.getNextStep();
            }
        } catch(ClassCastException e) {
            throw new ExecutionException("Invalid workflow received", e);
        }
    }

    /**
     * Transform a crossdata relationship into an in-memory relation.
     * @param f The {@link com.stratio.crossdata.common.logicalplan.Filter} logical step.
     * @return An equivalent {@link com.stratio.connector.inmemory.datastore.InMemoryRelation}.
     * @throws ExecutionException If the relationship cannot be translated.
     */
    private InMemoryRelation  toInMemoryRelation(Filter f) throws ExecutionException {
        ColumnSelector left = ColumnSelector.class.cast(f.getRelation().getLeftTerm());
        String columnName = left.getName().getName();
        InMemoryOperations relation;

        if(OPERATIONS_TRANFORMATIONS.containsKey(f.getRelation().getOperator())){
            relation = OPERATIONS_TRANFORMATIONS.get(f.getRelation().getOperator());
        }else{
            throw new ExecutionException("Operator " + f.getRelation().getOperator() + " not supported");
        }
        Selector rightSelector = f.getRelation().getRightTerm();
        Object rightPart = null;

        if(SelectorType.STRING.equals(rightSelector.getType())){
            rightPart = StringSelector.class.cast(rightSelector).getValue();
        }else if(SelectorType.INTEGER.equals(rightSelector.getType())){
            rightPart = IntegerSelector.class.cast(rightSelector).getValue();
        }else if(SelectorType.BOOLEAN.equals(rightSelector.getType())){
            rightPart = BooleanSelector.class.cast(rightSelector).getValue();
        }else if(SelectorType.FLOATING_POINT.equals(rightSelector.getType())){
            rightPart = FloatingPointSelector.class.cast(rightSelector).getValue();
        }

        return new InMemoryRelation(columnName, relation, rightPart);
    }

    /**
     * Transform a set of crossdata selectors into in-memory ones.
     * @param selectors The set of crossdata selectors.
     * @return A list of in-memory selectors.
     */
    private List<InMemorySelector> transformIntoSelectors(Set<Selector> selectors) {
        List<InMemorySelector> result = new ArrayList<>();
        for(Selector s: selectors){
            result.add(transformCrossdataSelector(s));
        }
        return result;
    }

    /**
     * Transform a Crossdata selector into an InMemory one.
     * @param selector The Crossdata selector.
     * @return The equivalent InMemory selector.
     */
    private InMemorySelector transformCrossdataSelector(Selector selector){
        InMemorySelector result;
        if(FunctionSelector.class.isInstance(selector)){
            FunctionSelector xdFunction = FunctionSelector.class.cast(selector);
            String name = xdFunction.getFunctionName();
            List<InMemorySelector> arguments = new ArrayList<>();
            for(Selector arg : xdFunction.getFunctionColumns()){
                arguments.add(transformCrossdataSelector(arg));
            }
            result = new InMemoryFunctionSelector(name, arguments);
        }else if(ColumnSelector.class.isInstance(selector)){
            ColumnSelector cs = ColumnSelector.class.cast(selector);
            result = new InMemoryColumnSelector(cs.getName().getName());
        }else{
            result = new InMemoryLiteralSelector(selector.getStringValue());
        }
        return result;
    }

    /**
     * Orthers the results using the orderStep
     * @param results
     * @return
     * @throws ExecutionException
     */
    public List<Object[]> orderResult(List<Object[]> results) throws ExecutionException {
        if (orderByStep != null) {
            List<Object[]> orderedResult = new ArrayList<>();
            if ((results != null) && (!results.isEmpty())) {
                for (Object[] row : results) {
                    if (orderedResult.isEmpty()) {
                        orderedResult.add(row);
                    } else {
                        int order = 0;
                        for (Object[] orderedRow : orderedResult) {
                            if (compareRows(row, orderedRow, outputColumns, orderByStep)) {
                                break;
                            }
                            order++;
                        }
                        orderedResult.add(order, row);
                    }
                }
            }

            return orderedResult;
        }

        return results;

    }

    private boolean compareRows(
            Object[] candidateRow,
            Object[] orderedRow,
            List<InMemorySelector> outputColumns,
            OrderBy orderByStep) {
        boolean result = false;

        List<String> columnNames = new ArrayList<>();
        for(InMemorySelector selector : outputColumns){
            columnNames.add(selector.getName());
        }

        for(OrderByClause clause: orderByStep.getIds()){
            int index = columnNames.indexOf(clause.getSelector().getColumnName().getName());
            int comparison = compareCells(candidateRow[index], orderedRow[index], clause.getDirection());
            if(comparison != 0){
                result = (comparison > 0);
                break;
            }
        }
        return result;
    }

    private int compareCells(Object toBeOrdered, Object alreadyOrdered, OrderDirection direction) {
        int result = -1;
        InMemoryOperations.GT.compare(toBeOrdered, alreadyOrdered);
        if(InMemoryOperations.EQ.compare(toBeOrdered, alreadyOrdered)){
            result = 0;
        } else if(direction == OrderDirection.ASC){
            if(InMemoryOperations.LT.compare(toBeOrdered, alreadyOrdered)){
                result = 1;
            }
        } else if(direction == OrderDirection.DESC){
            if(InMemoryOperations.GT.compare(toBeOrdered, alreadyOrdered)){
                result = 1;
            }
        }
        return result;
    }


    public TableName getTableName() {
        return tableName;
    }

    public void setTableName(TableName tableName) {
        this.tableName = tableName;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public void setCatalogName(String catalogName) {
        this.catalogName = catalogName;
    }

    public List<InMemoryRelation> getRelations() {
        return relations;
    }

    public void setRelations(List<InMemoryRelation> relations) {
        this.relations = relations;
    }

    public List<InMemorySelector> getOutputColumns() {
        return outputColumns;
    }

    public void setOutputColumns(List<InMemorySelector> outputColumns) {
        this.outputColumns = outputColumns;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public OrderBy getOrderByStep() {
        return orderByStep;
    }

    public void setOrderByStep(OrderBy orderByStep) {
        this.orderByStep = orderByStep;
    }

    public Select getSelectStep() {
        return selectStep;
    }

    public void setSelectStep(Select selectStep) {
        this.selectStep = selectStep;
    }

    public Join getJoinStep() {
        return joinStep;
    }

    public void setJoinStep(Join joinStep) {
        this.joinStep = joinStep;
    }

}
