/*
 * JBoss DNA (http://www.jboss.org/dna)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors.
 *
 * JBoss DNA is free software. Unless otherwise indicated, all code in JBoss DNA
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * JBoss DNA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.dna.graph.query;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.jcip.annotations.NotThreadSafe;
import org.jboss.dna.common.util.CheckArg;
import org.jboss.dna.graph.ExecutionContext;
import org.jboss.dna.graph.GraphI18n;
import org.jboss.dna.graph.property.Binary;
import org.jboss.dna.graph.property.DateTime;
import org.jboss.dna.graph.property.Name;
import org.jboss.dna.graph.property.Path;
import org.jboss.dna.graph.property.PropertyType;
import org.jboss.dna.graph.property.ValueFactories;
import org.jboss.dna.graph.property.ValueFormatException;
import org.jboss.dna.graph.query.model.AllNodes;
import org.jboss.dna.graph.query.model.And;
import org.jboss.dna.graph.query.model.BindVariableName;
import org.jboss.dna.graph.query.model.ChildNode;
import org.jboss.dna.graph.query.model.ChildNodeJoinCondition;
import org.jboss.dna.graph.query.model.Column;
import org.jboss.dna.graph.query.model.Comparison;
import org.jboss.dna.graph.query.model.Constraint;
import org.jboss.dna.graph.query.model.DescendantNode;
import org.jboss.dna.graph.query.model.DescendantNodeJoinCondition;
import org.jboss.dna.graph.query.model.DynamicOperand;
import org.jboss.dna.graph.query.model.EquiJoinCondition;
import org.jboss.dna.graph.query.model.FullTextSearch;
import org.jboss.dna.graph.query.model.FullTextSearchScore;
import org.jboss.dna.graph.query.model.Join;
import org.jboss.dna.graph.query.model.JoinCondition;
import org.jboss.dna.graph.query.model.JoinType;
import org.jboss.dna.graph.query.model.Length;
import org.jboss.dna.graph.query.model.Limit;
import org.jboss.dna.graph.query.model.Literal;
import org.jboss.dna.graph.query.model.LowerCase;
import org.jboss.dna.graph.query.model.NamedSelector;
import org.jboss.dna.graph.query.model.NodeDepth;
import org.jboss.dna.graph.query.model.NodeLocalName;
import org.jboss.dna.graph.query.model.NodeName;
import org.jboss.dna.graph.query.model.NodePath;
import org.jboss.dna.graph.query.model.Not;
import org.jboss.dna.graph.query.model.Operator;
import org.jboss.dna.graph.query.model.Or;
import org.jboss.dna.graph.query.model.Ordering;
import org.jboss.dna.graph.query.model.PropertyExistence;
import org.jboss.dna.graph.query.model.PropertyValue;
import org.jboss.dna.graph.query.model.Query;
import org.jboss.dna.graph.query.model.QueryCommand;
import org.jboss.dna.graph.query.model.SameNode;
import org.jboss.dna.graph.query.model.SameNodeJoinCondition;
import org.jboss.dna.graph.query.model.Selector;
import org.jboss.dna.graph.query.model.SelectorName;
import org.jboss.dna.graph.query.model.SetQuery;
import org.jboss.dna.graph.query.model.Source;
import org.jboss.dna.graph.query.model.UpperCase;
import org.jboss.dna.graph.query.model.Visitors;
import org.jboss.dna.graph.query.model.SetQuery.Operation;

/**
 * A component that can be used to programmatically create {@link QueryCommand} objects. Simply call methods to build the selector
 * clause, from clause, join criteria, where criteria, limits, and ordering, and then {@link #query() obtain the query}. This
 * builder should be adequate for most queries; however, any query that cannot be expressed by this builder can always be
 * constructed by directly creating the Abstract Query Model classes.
 * <p>
 * This builder is stateful and therefore should only be used by one thread at a time. However, once a query has been built, the
 * builder can be {@link #clear() cleared} and used to create another query.
 * </p>
 * <p>
 * The order in which the methods are called are (for the most part) important. Simply call the methods in the same order that
 * would be most natural in a normal SQL query. For example, the following code creates a Query object that is equivalent to "
 * <code>SELECT * FROM table</code>":
 * 
 * <pre>
 * QueryCommand query = builder.selectStar().from(&quot;table&quot;).query();
 * </pre>
 * 
 * </p>
 * <p>
 * Here are a few other examples:
 * <table border="1" cellspacing="0" cellpadding="3" summary="">
 * <tr>
 * <th>SQL Statement</th>
 * <th>QueryBuilder code</th>
 * </tr>
 * <tr>
 * <td>
 * 
 * <pre>
 * SELECT * FROM table1
 *    INNER JOIN table2
 *            ON table2.c0 = table1.c0
 * </pre>
 * 
 * </td>
 * <td>
 * 
 * <pre>
 * query = builder.selectStar().from(&quot;table1&quot;).join(&quot;table2&quot;).on(&quot;table2.c0=table1.c0&quot;).query();
 * </pre>
 * 
 * </td>
 * </tr>
 * <tr>
 * <td>
 * 
 * <pre>
 * SELECT * FROM table1 AS t1
 *    INNER JOIN table2 AS t2
 *            ON t1.c0 = t2.c0
 * </pre>
 * 
 * </td>
 * <td>
 * 
 * <pre>
 * query = builder.selectStar().from(&quot;table1 AS t1&quot;).join(&quot;table2 AS t2&quot;).on(&quot;t1.c0=t2.c0&quot;).query();
 * </pre>
 * 
 * </td>
 * </tr>
 * <tr>
 * <td>
 * 
 * <pre>
 * SELECT * FROM table1 AS t1
 *    INNER JOIN table2 AS t2
 *            ON t1.c0 = t2.c0
 *    INNER JOIN table3 AS t3
 *            ON t1.c1 = t3.c1
 * </pre>
 * 
 * </td>
 * <td>
 * 
 * <pre>
 * query = builder.selectStar()
 *                .from(&quot;table1 AS t1&quot;)
 *                .innerJoin(&quot;table2 AS t2&quot;)
 *                .on(&quot;t1.c0=t2.c0&quot;)
 *                .innerJoin(&quot;table3 AS t3&quot;)
 *                .on(&quot;t1.c1=t3.c1&quot;)
 *                .query();
 * </pre>
 * 
 * </td>
 * </tr>
 * <tr>
 * <td>
 * 
 * <pre>
 * SELECT * FROM table1
 * UNION
 * SELECT * FROM table2
 * </pre>
 * 
 * </td>
 * <td>
 * 
 * <pre>
 * query = builder.selectStar().from(&quot;table1&quot;).union().selectStar().from(&quot;table2&quot;).query();
 * </pre>
 * 
 * </td>
 * </tr>
 * <tr>
 * <td>
 * 
 * <pre>
 * SELECT t1.c1,t1.c2,t2.c3 FROM table1 AS t1
 *    INNER JOIN table2 AS t2
 *            ON t1.c0 = t2.c0
 * UNION ALL
 * SELECT t3.c1,t3.c2,t4.c3 FROM table3 AS t3
 *    INNER JOIN table4 AS t4
 *            ON t3.c0 = t4.c0
 * </pre>
 * 
 * </td>
 * <td>
 * 
 * <pre>
 * query = builder.select(&quot;t1.c1&quot;,&quot;t1.c2&quot;,&quot;t2.c3&quot;,)
 *                .from(&quot;table1 AS t1&quot;)
 *                .innerJoin(&quot;table2 AS t2&quot;)
 *                .on(&quot;t1.c0=t2.c0&quot;)
 *                .union()
 *                .select(&quot;t3.c1&quot;,&quot;t3.c2&quot;,&quot;t4.c3&quot;,)
 *                .from(&quot;table3 AS t3&quot;)
 *                .innerJoin(&quot;table4 AS t4&quot;)
 *                .on(&quot;t3.c0=t4.c0&quot;)
 *                .query();
 * </pre>
 * 
 * </td>
 * </tr>
 * </table>
 * </pre>
 */
@NotThreadSafe
public class QueryBuilder {

    protected final ExecutionContext context;
    protected Source source = new AllNodes();
    protected Constraint constraint;
    protected List<Column> columns = new LinkedList<Column>();
    protected List<Ordering> orderings = new LinkedList<Ordering>();
    protected Limit limit = Limit.NONE;
    protected boolean distinct;
    protected QueryCommand firstQuery;
    protected Operation firstQuerySetOperation;
    protected boolean firstQueryAll;

    /**
     * Create a new builder that uses the supplied execution context.
     * 
     * @param context the execution context
     * @throws IllegalArgumentException if the context is null
     */
    public QueryBuilder( ExecutionContext context ) {
        CheckArg.isNotNull(context, "context");
        this.context = context;
    }

    /**
     * Clear this builder completely to start building a new query.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder clear() {
        return clear(true);
    }

    /**
     * Utility method that does all the work of the clear, but with a flag that defines whether to clear the first query. This
     * method is used by {@link #clear()} as well as the {@link #union() many} {@link #intersect() set} {@link #except()
     * operations}.
     * 
     * @param clearFirstQuery true if the first query should be cleared, or false if the first query should be retained
     * @return this builder object, for convenience in method chaining
     */
    protected QueryBuilder clear( boolean clearFirstQuery ) {
        source = new AllNodes();
        constraint = null;
        columns = new LinkedList<Column>();
        orderings = new LinkedList<Ordering>();
        limit = Limit.NONE;
        distinct = false;
        if (clearFirstQuery) {
            this.firstQuery = null;
            this.firstQuerySetOperation = null;
        }
        return this;
    }

    /**
     * Convenience method that creates a selector name object using the supplied string.
     * 
     * @param name the name of the selector; may not be null
     * @return the selector name; never null
     */
    protected SelectorName selector( String name ) {
        return new SelectorName(name.trim());
    }

    /**
     * Convenience method that creates a {@link NamedSelector} object given a string that contains the selector name and
     * optionally an alias. The format of the string parameter is <code>name [AS alias]</code>. Leading and trailing whitespace
     * are trimmed.
     * 
     * @param nameWithOptionalAlias the name and optional alias; may not be null
     * @return the named selector object; never null
     */
    protected NamedSelector namedSelector( String nameWithOptionalAlias ) {
        String[] parts = nameWithOptionalAlias.split("\\sAS\\s");
        if (parts.length == 2) {
            return new NamedSelector(selector(parts[0]), selector(parts[1]));
        }
        return new NamedSelector(selector(parts[0]));
    }

    /**
     * Convenience method that creates a {@link Name} object given the supplied string. Leading and trailing whitespace are
     * trimmed.
     * 
     * @param name the name; may not be null
     * @return the name; never null
     * @throws IllegalArgumentException if the supplied name is not a valid {@link Name} object
     */
    protected Name name( String name ) {
        try {
            return context.getValueFactories().getNameFactory().create(name.trim());
        } catch (ValueFormatException e) {
            throw new IllegalArgumentException(GraphI18n.expectingValidName.text(name));
        }
    }

    /**
     * Convenience method that creates a {@link Path} object given the supplied string. Leading and trailing whitespace are
     * trimmed.
     * 
     * @param path the path; may not be null
     * @return the path; never null
     * @throws IllegalArgumentException if the supplied string is not a valid {@link Path} object
     */
    protected Path path( String path ) {
        try {
            return context.getValueFactories().getPathFactory().create(path.trim());
        } catch (ValueFormatException e) {
            throw new IllegalArgumentException(GraphI18n.expectingValidPath.text(path));
        }
    }

    /**
     * Create a {@link Column} given the supplied expression. The expression has the form "<code>[tableName.]columnName</code>",
     * where "<code>tableName</code>" must be a valid table name or alias. If the table name/alias is not specified, then there is
     * expected to be a single FROM clause with a single named selector.
     * 
     * @param nameExpression the expression specifying the columm name and (optionally) the table's name or alias; may not be null
     * @return the column; never null
     * @throws IllegalArgumentException if the table's name/alias is not specified, but the query has more than one named source
     */
    protected Column column( String nameExpression ) {
        String[] parts = nameExpression.split("(?<!\\\\)\\."); // a . not preceded by an escaping slash
        for (int i = 0; i != parts.length; ++i) {
            parts[i] = parts[i].trim();
        }
        SelectorName name = null;
        Name propertyName = null;
        String columnName = null;
        if (parts.length == 2) {
            name = selector(parts[0]);
            propertyName = name(parts[1]);
            columnName = parts[1];
        } else {
            if (source instanceof Selector) {
                Selector selector = (Selector)source;
                name = selector.hasAlias() ? selector.getAlias() : selector.getName();
                propertyName = name(parts[0]);
                columnName = parts[0];
            } else {
                throw new IllegalArgumentException(GraphI18n.columnMustBeScoped.text(parts[0]));
            }
        }
        return new Column(name, propertyName, columnName);
    }

    /**
     * Select all of the single-valued columns.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder selectStar() {
        columns.clear();
        return this;
    }

    /**
     * Add to the select clause the columns with the supplied names. Each column name has the form "
     * <code>[tableName.]columnName</code>", where " <code>tableName</code>" must be a valid table name or alias. If the table
     * name/alias is not specified, then there is expected to be a single FROM clause with a single named selector.
     * 
     * @param columnNames the column expressions; may not be null
     * @return this builder object, for convenience in method chaining
     * @throws IllegalArgumentException if the table's name/alias is not specified, but the query has more than one named source
     */
    public QueryBuilder select( String... columnNames ) {
        for (String expression : columnNames) {
            columns.add(column(expression));
        }
        return this;
    }

    /**
     * Select all of the distinct values from the single-valued columns.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder selectDistinctStar() {
        distinct = true;
        return selectStar();
    }

    /**
     * Select the distinct values from the columns with the supplied names. Each column name has the form "
     * <code>[tableName.]columnName</code>", where " <code>tableName</code>" must be a valid table name or alias. If the table
     * name/alias is not specified, then there is expected to be a single FROM clause with a single named selector.
     * 
     * @param columnNames the column expressions; may not be null
     * @return this builder object, for convenience in method chaining
     * @throws IllegalArgumentException if the table's name/alias is not specified, but the query has more than one named source
     */
    public QueryBuilder selectDistinct( String... columnNames ) {
        distinct = true;
        return select(columnNames);
    }

    /**
     * Specify that the query should select from the "__ALLNODES__" built-in table.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder fromAllNodes() {
        this.source = new AllNodes();
        return this;
    }

    /**
     * Specify that the query should select from the "__ALLNODES__" built-in table using the supplied alias.
     * 
     * @param alias the alias for the "__ALL_NODES" table; may not be null
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder fromAllNodesAs( String alias ) {
        AllNodes allNodes = new AllNodes(selector(alias));
        SelectorName oldName = this.source instanceof Selector ? ((Selector)source).getName() : null;
        // Go through the columns and change the selector name to use the new alias ...
        for (int i = 0; i != columns.size(); ++i) {
            Column old = columns.get(i);
            if (old.getSelectorName().equals(oldName)) {
                columns.set(i, new Column(allNodes.getAliasOrName(), old.getPropertyName(), old.getColumnName()));
            }
        }
        this.source = allNodes;
        return this;
    }

    /**
     * Specify the name of the table from which tuples should be selected. The supplied string is of the form "
     * <code>tableName [AS alias]</code>".
     * 
     * @param tableNameWithOptionalAlias the name of the table, optionally including the alias
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder from( String tableNameWithOptionalAlias ) {
        Selector selector = namedSelector(tableNameWithOptionalAlias);
        SelectorName oldName = this.source instanceof Selector ? ((Selector)source).getName() : null;
        // Go through the columns and change the selector name to use the new alias ...
        for (int i = 0; i != columns.size(); ++i) {
            Column old = columns.get(i);
            if (old.getSelectorName().equals(oldName)) {
                columns.set(i, new Column(selector.getAliasOrName(), old.getPropertyName(), old.getColumnName()));
            }
        }
        this.source = selector;
        return this;
    }

    /**
     * Begin the WHERE clause for this query by obtaining the constraint builder. When completed, be sure to call
     * {@link ConstraintBuilder#end() end()} on the resulting constraint builder, or else the constraint will not be applied to
     * the current query.
     * 
     * @return the constraint builder that can be used to specify the criteria; never null
     */
    public ConstraintBuilder where() {
        return new ConstraintBuilder(null);
    }

    /**
     * Perform an inner join between the already defined source with the supplied table. The supplied string is of the form "
     * <code>tableName [AS alias]</code>".
     * 
     * @param tableName the name of the table, optionally including the alias
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause join( String tableName ) {
        return innerJoin(tableName);
    }

    /**
     * Perform an inner join between the already defined source with the supplied table. The supplied string is of the form "
     * <code>tableName [AS alias]</code>".
     * 
     * @param tableName the name of the table, optionally including the alias
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause innerJoin( String tableName ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(tableName), JoinType.INNER);
    }

    /**
     * Perform a cross join between the already defined source with the supplied table. The supplied string is of the form "
     * <code>tableName [AS alias]</code>". Cross joins have a higher precedent than other join types, so if this is called after
     * another join was defined, the resulting cross join will be between the previous join's right-hand side and the supplied
     * table.
     * 
     * @param tableName the name of the table, optionally including the alias
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause crossJoin( String tableName ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(tableName), JoinType.CROSS);
    }

    /**
     * Perform a full outer join between the already defined source with the supplied table. The supplied string is of the form "
     * <code>tableName [AS alias]</code>".
     * 
     * @param tableName the name of the table, optionally including the alias
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause fullOuterJoin( String tableName ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(tableName), JoinType.FULL_OUTER);
    }

    /**
     * Perform a left outer join between the already defined source with the supplied table. The supplied string is of the form "
     * <code>tableName [AS alias]</code>".
     * 
     * @param tableName the name of the table, optionally including the alias
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause leftOuterJoin( String tableName ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(tableName), JoinType.LEFT_OUTER);
    }

    /**
     * Perform a right outer join between the already defined source with the supplied table. The supplied string is of the form "
     * <code>tableName [AS alias]</code>".
     * 
     * @param tableName the name of the table, optionally including the alias
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause rightOuterJoin( String tableName ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(tableName), JoinType.RIGHT_OUTER);
    }

    /**
     * Perform an inner join between the already defined source with the "__ALLNODES__" table using the supplied alias.
     * 
     * @param alias the alias for the "__ALL_NODES" table; may not be null
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause joinAllNodesAs( String alias ) {
        return innerJoinAllNodesAs(alias);
    }

    /**
     * Perform an inner join between the already defined source with the "__ALL_NODES" table using the supplied alias.
     * 
     * @param alias the alias for the "__ALL_NODES" table; may not be null
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause innerJoinAllNodesAs( String alias ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(AllNodes.ALL_NODES_NAME + " AS " + alias), JoinType.INNER);
    }

    /**
     * Perform a cross join between the already defined source with the "__ALL_NODES" table using the supplied alias. Cross joins
     * have a higher precedent than other join types, so if this is called after another join was defined, the resulting cross
     * join will be between the previous join's right-hand side and the supplied table.
     * 
     * @param alias the alias for the "__ALL_NODES" table; may not be null
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause crossJoinAllNodesAs( String alias ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(AllNodes.ALL_NODES_NAME + " AS " + alias), JoinType.CROSS);
    }

    /**
     * Perform a full outer join between the already defined source with the "__ALL_NODES" table using the supplied alias.
     * 
     * @param alias the alias for the "__ALL_NODES" table; may not be null
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause fullOuterJoinAllNodesAs( String alias ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(AllNodes.ALL_NODES_NAME + " AS " + alias), JoinType.FULL_OUTER);
    }

    /**
     * Perform a left outer join between the already defined source with the "__ALL_NODES" table using the supplied alias.
     * 
     * @param alias the alias for the "__ALL_NODES" table; may not be null
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause leftOuterJoinAllNodesAs( String alias ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(AllNodes.ALL_NODES_NAME + " AS " + alias), JoinType.LEFT_OUTER);
    }

    /**
     * Perform a right outer join between the already defined source with the "__ALL_NODES" table using the supplied alias.
     * 
     * @param alias the alias for the "__ALL_NODES" table; may not be null
     * @return the component that must be used to complete the join specification; never null
     */
    public JoinClause rightOuterJoinAllNodesAs( String alias ) {
        // Expect there to be a source already ...
        return new JoinClause(namedSelector(AllNodes.ALL_NODES_NAME + " AS " + alias), JoinType.RIGHT_OUTER);
    }

    /**
     * Specify the maximum number of rows that are to be returned in the results. By default there is no limit.
     * 
     * @param rowLimit the maximum number of rows
     * @return this builder object, for convenience in method chaining
     * @throws IllegalArgumentException if the row limit is not a positive integer
     */
    public QueryBuilder limit( int rowLimit ) {
        this.limit.withRowLimit(rowLimit);
        return this;
    }

    /**
     * Specify the number of rows that results are to skip. The default offset is '0'.
     * 
     * @param offset the number of rows before the results are to begin
     * @return this builder object, for convenience in method chaining
     * @throws IllegalArgumentException if the row limit is a negative integer
     */
    public QueryBuilder offset( int offset ) {
        this.limit.withOffset(offset);
        return this;
    }

    /**
     * Perform a UNION between the query as defined prior to this method and the query that will be defined following this method.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder union() {
        this.firstQuery = query();
        this.firstQuerySetOperation = Operation.UNION;
        this.firstQueryAll = false;
        clear(false);
        return this;
    }

    /**
     * Perform a UNION ALL between the query as defined prior to this method and the query that will be defined following this
     * method.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder unionAll() {
        this.firstQuery = query();
        this.firstQuerySetOperation = Operation.UNION;
        this.firstQueryAll = true;
        clear(false);
        return this;
    }

    /**
     * Perform an INTERSECT between the query as defined prior to this method and the query that will be defined following this
     * method.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder intersect() {
        this.firstQuery = query();
        this.firstQuerySetOperation = Operation.INTERSECT;
        this.firstQueryAll = false;
        clear(false);
        return this;
    }

    /**
     * Perform an INTERSECT ALL between the query as defined prior to this method and the query that will be defined following
     * this method.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder intersectAll() {
        this.firstQuery = query();
        this.firstQuerySetOperation = Operation.INTERSECT;
        this.firstQueryAll = true;
        clear(false);
        return this;
    }

    /**
     * Perform an EXCEPT between the query as defined prior to this method and the query that will be defined following this
     * method.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder except() {
        this.firstQuery = query();
        this.firstQuerySetOperation = Operation.EXCEPT;
        this.firstQueryAll = false;
        clear(false);
        return this;
    }

    /**
     * Perform an EXCEPT ALL between the query as defined prior to this method and the query that will be defined following this
     * method.
     * 
     * @return this builder object, for convenience in method chaining
     */
    public QueryBuilder exceptAll() {
        this.firstQuery = query();
        this.firstQuerySetOperation = Operation.EXCEPT;
        this.firstQueryAll = true;
        clear(false);
        return this;
    }

    /**
     * Return a {@link QueryCommand} representing the currently-built query.
     * 
     * @return the resulting query command; never null
     * @see #clear()
     */
    public QueryCommand query() {
        QueryCommand result = new Query(source, constraint, orderings, columns, limit, distinct);
        if (this.firstQuery != null) {
            // EXCEPT has a higher precedence than INTERSECT or UNION, so if the first query is
            // an INTERSECT or UNION SetQuery, the result should be applied to the RHS of the previous set ...
            if (firstQuery instanceof SetQuery && firstQuerySetOperation == Operation.EXCEPT) {
                SetQuery setQuery = (SetQuery)firstQuery;
                QueryCommand left = setQuery.getLeft();
                QueryCommand right = setQuery.getRight();
                SetQuery exceptQuery = new SetQuery(right, Operation.EXCEPT, result, firstQueryAll);
                result = new SetQuery(left, setQuery.getOperation(), exceptQuery, setQuery.isAll());
            } else {
                result = new SetQuery(this.firstQuery, this.firstQuerySetOperation, result, this.firstQueryAll);
            }
        }
        return result;
    }

    /**
     * Class used to specify a join clause of a query.
     * 
     * @see QueryBuilder#join(String)
     * @see QueryBuilder#innerJoin(String)
     * @see QueryBuilder#leftOuterJoin(String)
     * @see QueryBuilder#rightOuterJoin(String)
     * @see QueryBuilder#fullOuterJoin(String)
     */
    public class JoinClause {
        private final NamedSelector rightSource;
        private final JoinType type;

        protected JoinClause( NamedSelector rightTable,
                              JoinType type ) {
            this.rightSource = rightTable;
            this.type = type;
        }

        /**
         * Walk the current source or the 'rightSource' to find the named selector with the supplied name or alias
         * 
         * @param tableName the table name
         * @return the selector name matching the supplied table name; never null
         * @throws IllegalArgumentException if the table name could not be resolved
         */
        protected SelectorName nameOf( String tableName ) {
            final SelectorName name = new SelectorName(tableName);
            // Look at the right source ...
            if (rightSource.getAliasOrName().equals(name)) return name;
            // Look through the left source ...
            final AtomicBoolean notFound = new AtomicBoolean(true);
            Visitors.visitAll(source, new Visitors.AbstractVisitor() {
                @Override
                public void visit( AllNodes selector ) {
                    if (notFound.get() && selector.getAliasOrName().equals(name)) notFound.set(false);
                }

                @Override
                public void visit( NamedSelector selector ) {
                    if (notFound.get() && selector.getAliasOrName().equals(name)) notFound.set(false);
                }
            });
            if (notFound.get()) {
                throw new IllegalArgumentException("Expected \"" + tableName + "\" to be a valid table name or alias");
            }
            return name;
        }

        /**
         * Define the join as using an equi-join criteria by specifying the expression equating two columns. Each column reference
         * must be qualified with the appropriate table name or alias.
         * 
         * @param columnEqualExpression the equality expression between the two tables; may not be null
         * @return the query builder instance, for method chaining purposes
         * @throws IllegalArgumentException if the supplied expression is not an equality expression
         */
        public QueryBuilder on( String columnEqualExpression ) {
            String[] parts = columnEqualExpression.split("=");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Expected equality expression for columns, but found \""
                                                   + columnEqualExpression + "\"");
            }
            return createJoin(new EquiJoinCondition(column(parts[0]), column(parts[1])));
        }

        /**
         * Define the join criteria to require the two tables represent the same node. The supplied tables must be a valid name or
         * alias.
         * 
         * @param table1 the name or alias of the first table
         * @param table2 the name or alias of the second table
         * @return the query builder instance, for method chaining purposes
         */
        public QueryBuilder onSameNode( String table1,
                                        String table2 ) {
            return createJoin(new SameNodeJoinCondition(nameOf(table1), nameOf(table2)));
        }

        /**
         * Define the join criteria to require the node in one table is a descendant of the node in another table. The supplied
         * tables must be a valid name or alias.
         * 
         * @param ancestorTable the name or alias of the table containing the ancestor node
         * @param descendantTable the name or alias of the table containing the descendant node
         * @return the query builder instance, for method chaining purposes
         */
        public QueryBuilder onDescendant( String ancestorTable,
                                          String descendantTable ) {
            return createJoin(new DescendantNodeJoinCondition(nameOf(ancestorTable), nameOf(descendantTable)));
        }

        /**
         * Define the join criteria to require the node in one table is a child of the node in another table. The supplied tables
         * must be a valid name or alias.
         * 
         * @param parentTable the name or alias of the table containing the parent node
         * @param childTable the name or alias of the table containing the child node
         * @return the query builder instance, for method chaining purposes
         */
        public QueryBuilder onChildNode( String parentTable,
                                         String childTable ) {
            return createJoin(new ChildNodeJoinCondition(nameOf(parentTable), nameOf(childTable)));
        }

        protected QueryBuilder createJoin( JoinCondition condition ) {
            // CROSS joins have a higher precedence, so we may need to adjust the existing left side in this case...
            if (type == JoinType.CROSS && source instanceof Join && ((Join)source).getType() != JoinType.CROSS) {
                // A CROSS join follows a non-CROSS join, so the CROSS join becomes precendent ...
                Join left = (Join)source;
                Join cross = new Join(left.getRight(), type, rightSource, condition);
                source = new Join(left.getLeft(), left.getType(), cross, left.getJoinCondition());
            } else {
                // Otherwise, just create using usual precedence ...
                source = new Join(source, type, rightSource, condition);
            }
            return QueryBuilder.this;
        }
    }

    /**
     * Interface that defines a dynamic operand portion of a criteria.
     */
    public interface DynamicOperandBuilder {
        /**
         * Constrains the nodes in the the supplied table such that they must have a property value whose length matches the
         * criteria.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @param property the name of the property; may not be null and must refer to a valid property name
         * @return the interface for completing the value portion of the criteria specification; never null
         */
        public ComparisonBuilder length( String table,
                                         String property );

        /**
         * Constrains the nodes in the the supplied table such that they must have a matching value for the named property.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @param property the name of the property; may not be null and must refer to a valid property name
         * @return the interface for completing the value portion of the criteria specification; never null
         */
        public ComparisonBuilder propertyValue( String table,
                                                String property );

        /**
         * Constrains the nodes in the the supplied table such that they must satisfy the supplied full-text search on the nodes'
         * property values.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @return the interface for completing the value portion of the criteria specification; never null
         */
        public ComparisonBuilder fullTextSearchScore( String table );

        /**
         * Constrains the nodes in the the supplied table based upon criteria on the node's depth.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @return the interface for completing the value portion of the criteria specification; never null
         */
        public ComparisonBuilder depth( String table );

        /**
         * Constrains the nodes in the the supplied table based upon criteria on the node's path.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @return the interface for completing the value portion of the criteria specification; never null
         */
        public ComparisonBuilder path( String table );

        /**
         * Constrains the nodes in the the supplied table based upon criteria on the node's local name.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @return the interface for completing the value portion of the criteria specification; never null
         */
        public ComparisonBuilder nodeLocalName( String table );

        /**
         * Constrains the nodes in the the supplied table based upon criteria on the node's name.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @return the interface for completing the value portion of the criteria specification; never null
         */
        public ComparisonBuilder nodeName( String table );

        /**
         * Begin a constraint against the uppercase form of a dynamic operand.
         * 
         * @return the interface for completing the criteria specification; never null
         */
        public DynamicOperandBuilder upperCaseOf();

        /**
         * Begin a constraint against the lowercase form of a dynamic operand.
         * 
         * @return the interface for completing the criteria specification; never null
         */
        public DynamicOperandBuilder lowerCaseOf();

    }

    public class ConstraintBuilder implements DynamicOperandBuilder {
        private final ConstraintBuilder parent;
        /** Used for the current operations */
        private Constraint constraint;
        /** Set when a logical criteria is started */
        private Constraint left;
        private boolean and;
        private boolean negateConstraint;

        protected ConstraintBuilder( ConstraintBuilder parent ) {
            this.parent = parent;
        }

        /**
         * Complete this constraint specification.
         * 
         * @return the query builder, for method chaining purposes
         */
        public QueryBuilder end() {
            buildLogicalConstraint();
            QueryBuilder.this.constraint = constraint;
            return QueryBuilder.this;
        }

        /**
         * Simulate the use of an open parenthesis in the constraint. The resulting builder should be used to define the
         * constraint within the parenthesis, and should always be terminated with a {@link #closeParen()}.
         * 
         * @return the constraint builder that should be used to define the portion of the constraint within the parenthesis;
         *         never null
         * @see #closeParen()
         */
        public ConstraintBuilder openParen() {
            return new ConstraintBuilder(this);
        }

        /**
         * Complete the specification of a constraint clause, and return the builder for the parent constraint clause.
         * 
         * @return the constraint builder that was used to create this parenthetical constraint clause builder; never null
         */
        public ConstraintBuilder closeParen() {
            assert parent != null;
            buildLogicalConstraint();
            return parent.setConstraint(constraint);
        }

        /**
         * Signal that the previous constraint clause be AND-ed together with another constraint clause that will be defined
         * immediately after this method call.
         * 
         * @return the constraint builder for the remaining constraint clause; never null
         */
        public ConstraintBuilder and() {
            buildLogicalConstraint();
            left = constraint;
            constraint = null;
            and = true;
            return this;
        }

        /**
         * Signal that the previous constraint clause be OR-ed together with another constraint clause that will be defined
         * immediately after this method call.
         * 
         * @return the constraint builder for the remaining constraint clause; never null
         */
        public ConstraintBuilder or() {
            buildLogicalConstraint();
            left = constraint;
            constraint = null;
            and = false;
            return this;
        }

        /**
         * Signal that the next constraint clause (defined immediately after this method) should be negated.
         * 
         * @return the constraint builder for the constraint clause that is to be negated; never null
         */
        public ConstraintBuilder not() {
            negateConstraint = true;
            return this;
        }

        protected ConstraintBuilder buildLogicalConstraint() {
            if (negateConstraint && constraint != null) {
                constraint = new Not(constraint);
                negateConstraint = false;
            }
            if (left != null && constraint != null) {
                if (and) {
                    // If the left constraint is an OR, we need to rearrange things since AND is higher precedence ...
                    if (left instanceof Or) {
                        Or previous = (Or)left;
                        constraint = new Or(previous.getLeft(), new And(previous.getRight(), constraint));
                    } else {
                        constraint = new And(left, constraint);
                    }
                } else {
                    constraint = new Or(left, constraint);
                }
                left = null;
            }
            return this;
        }

        /**
         * Define a constraint clause that the node within the named table is the same node as that appearing at the supplied
         * path.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @param asNodeAtPath the path to the node
         * @return the constraint builder that was used to create this clause; never null
         */
        public ConstraintBuilder isSameNode( String table,
                                             String asNodeAtPath ) {
            return setConstraint(new SameNode(selector(table), QueryBuilder.this.path(asNodeAtPath)));
        }

        /**
         * Define a constraint clause that the node within the named table is the child of the node at the supplied path.
         * 
         * @param childTable the name of the table; may not be null and must refer to a valid name or alias of a table appearing
         *        in the FROM clause
         * @param parentPath the path to the parent node
         * @return the constraint builder that was used to create this clause; never null
         */
        public ConstraintBuilder isChild( String childTable,
                                          String parentPath ) {
            return setConstraint(new ChildNode(selector(childTable), QueryBuilder.this.path(parentPath)));
        }

        /**
         * Define a constraint clause that the node within the named table is a descendant of the node at the supplied path.
         * 
         * @param descendantTable the name of the table; may not be null and must refer to a valid name or alias of a table
         *        appearing in the FROM clause
         * @param ancestorPath the path to the ancestor node
         * @return the constraint builder that was used to create this clause; never null
         */
        public ConstraintBuilder isBelowPath( String descendantTable,
                                              String ancestorPath ) {
            return setConstraint(new DescendantNode(selector(descendantTable), QueryBuilder.this.path(ancestorPath)));
        }

        /**
         * Define a constraint clause that the node within the named table has at least one value for the named property.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @param propertyName the name of the property
         * @return the constraint builder that was used to create this clause; never null
         */
        public ConstraintBuilder hasProperty( String table,
                                              String propertyName ) {
            return setConstraint(new PropertyExistence(selector(table), name(propertyName)));
        }

        /**
         * Define a constraint clause that the node within the named table have at least one property that satisfies the full-text
         * search expression.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @param searchExpression the full-text search expression
         * @return the constraint builder that was used to create this clause; never null
         */
        public ConstraintBuilder search( String table,
                                         String searchExpression ) {
            return setConstraint(new FullTextSearch(selector(table), searchExpression));
        }

        /**
         * Define a constraint clause that the node within the named table have a value for the named property that satisfies the
         * full-text search expression.
         * 
         * @param table the name of the table; may not be null and must refer to a valid name or alias of a table appearing in the
         *        FROM clause
         * @param propertyName the name of the property to be searched
         * @param searchExpression the full-text search expression
         * @return the constraint builder that was used to create this clause; never null
         */
        public ConstraintBuilder search( String table,
                                         String propertyName,
                                         String searchExpression ) {
            return setConstraint(new FullTextSearch(selector(table), name(propertyName), searchExpression));
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#length(java.lang.String, java.lang.String)
         */
        public ComparisonBuilder length( String table,
                                         String property ) {
            return new ComparisonBuilder(this, new Length(new PropertyValue(selector(table), name(property))));
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#propertyValue(String, String)
         */
        public ComparisonBuilder propertyValue( String table,
                                                String property ) {
            return new ComparisonBuilder(this, new PropertyValue(selector(table), name(property)));
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#fullTextSearchScore(String)
         */
        public ComparisonBuilder fullTextSearchScore( String table ) {
            return new ComparisonBuilder(this, new FullTextSearchScore(selector(table)));
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#depth(java.lang.String)
         */
        public ComparisonBuilder depth( String table ) {
            return new ComparisonBuilder(this, new NodeDepth(selector(table)));
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#path(java.lang.String)
         */
        public ComparisonBuilder path( String table ) {
            return new ComparisonBuilder(this, new NodePath(selector(table)));
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#nodeLocalName(String)
         */
        public ComparisonBuilder nodeLocalName( String table ) {
            return new ComparisonBuilder(this, new NodeLocalName(selector(table)));
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#nodeName(String)
         */
        public ComparisonBuilder nodeName( String table ) {
            return new ComparisonBuilder(this, new NodeName(selector(table)));
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#upperCaseOf()
         */
        public DynamicOperandBuilder upperCaseOf() {
            return new UpperCaser(this);
        }

        /**
         * {@inheritDoc}
         * 
         * @see org.jboss.dna.graph.query.QueryBuilder.DynamicOperandBuilder#lowerCaseOf()
         */
        public DynamicOperandBuilder lowerCaseOf() {
            return new LowerCaser(this);
        }

        protected ConstraintBuilder setConstraint( Constraint constraint ) {
            if (this.constraint != null && this.left == null) {
                and();
            }
            this.constraint = constraint;
            return buildLogicalConstraint();
        }
    }

    /**
     * A specialized form of the {@link ConstraintBuilder} that always wraps the generated constraint in a {@link UpperCase}
     * instance.
     */
    protected class UpperCaser extends ConstraintBuilder {
        private final ConstraintBuilder delegate;

        protected UpperCaser( ConstraintBuilder delegate ) {
            super(null);
            this.delegate = delegate;
        }

        @Override
        protected ConstraintBuilder setConstraint( Constraint constraint ) {
            Comparison comparison = (Comparison)constraint;
            return delegate.setConstraint(new Comparison(new UpperCase(comparison.getOperand1()), comparison.getOperator(),
                                                         comparison.getOperand2()));
        }
    }

    /**
     * A specialized form of the {@link ConstraintBuilder} that always wraps the generated constraint in a {@link LowerCase}
     * instance.
     */
    protected class LowerCaser extends ConstraintBuilder {
        private final ConstraintBuilder delegate;

        protected LowerCaser( ConstraintBuilder delegate ) {
            super(null);
            this.delegate = delegate;
        }

        @Override
        protected ConstraintBuilder setConstraint( Constraint constraint ) {
            Comparison comparison = (Comparison)constraint;
            return delegate.setConstraint(new Comparison(new LowerCase(comparison.getOperand1()), comparison.getOperator(),
                                                         comparison.getOperand2()));
        }
    }

    public class CastAs {
        private final RightHandSide rhs;
        private final Object value;

        protected CastAs( RightHandSide rhs,
                          Object value ) {
            this.rhs = rhs;
            this.value = value;
        }

        private ValueFactories factories() {
            return QueryBuilder.this.context.getValueFactories();
        }

        /**
         * Define the right-hand side literal value cast as the specified type.
         * 
         * @param type the property type; may not be null
         * @return the constraint builder; never null
         */
        public ConstraintBuilder as( PropertyType type ) {
            return rhs.comparisonBuilder.is(rhs.operator, factories().getValueFactory(type).create(value));
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#STRING}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asString() {
            return as(PropertyType.STRING);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#BOOLEAN}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asBoolean() {
            return as(PropertyType.BOOLEAN);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#LONG}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asLong() {
            return as(PropertyType.LONG);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#DOUBLE}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asDouble() {
            return as(PropertyType.DOUBLE);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#DECIMAL}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asDecimal() {
            return as(PropertyType.DECIMAL);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#DATE}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asDate() {
            return as(PropertyType.DATE);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#NAME}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asName() {
            return as(PropertyType.NAME);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#PATH}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asPath() {
            return as(PropertyType.PATH);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#BINARY}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asBinary() {
            return as(PropertyType.BINARY);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#REFERENCE}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asReference() {
            return as(PropertyType.REFERENCE);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#URI}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asUri() {
            return as(PropertyType.URI);
        }

        /**
         * Define the right-hand side literal value cast as a {@link PropertyType#UUID}.
         * 
         * @return the constraint builder; never null
         */
        public ConstraintBuilder asUuid() {
            return as(PropertyType.UUID);
        }
    }

    public class RightHandSide {
        protected final Operator operator;
        protected final ComparisonBuilder comparisonBuilder;

        protected RightHandSide( ComparisonBuilder comparisonBuilder,
                                 Operator operator ) {
            this.operator = operator;
            this.comparisonBuilder = comparisonBuilder;
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( String literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( int literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( long literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( float literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( double literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( DateTime literal ) {
            return comparisonBuilder.is(operator, literal.toUtcTimeZone());
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( Path literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( Name literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( URI literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( UUID literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( Binary literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( BigDecimal literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value;
         * @return the constraint builder; never null
         */
        public ConstraintBuilder literal( boolean literal ) {
            return comparisonBuilder.is(operator, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param variableName the name of the variable
         * @return the constraint builder; never null
         */
        public ConstraintBuilder variable( String variableName ) {
            return comparisonBuilder.is(operator, variableName);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( int literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( String literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( boolean literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( long literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( double literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( BigDecimal literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( DateTime literal ) {
            return new CastAs(this, literal.toUtcTimeZone());
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( Name literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( Path literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( UUID literal ) {
            return new CastAs(this, literal);
        }

        /**
         * Define the right-hand side of a comparison.
         * 
         * @param literal the literal value that is to be cast
         * @return the constraint builder; never null
         */
        public CastAs cast( URI literal ) {
            return new CastAs(this, literal);
        }
    }

    /**
     * An interface used to set the right-hand side of a constraint.
     */
    public class ComparisonBuilder {
        private final DynamicOperand left;
        private final ConstraintBuilder constraintBuilder;

        protected ComparisonBuilder( ConstraintBuilder constraintBuilder,
                                     DynamicOperand left ) {
            this.left = left;
            this.constraintBuilder = constraintBuilder;
        }

        /**
         * Define the operator that will be used in the comparison, returning an interface that can be used to define the
         * right-hand-side of the comparison.
         * 
         * @param operator the operator; may not be null
         * @return the interface used to define the right-hand-side of the comparison
         */
        public RightHandSide is( Operator operator ) {
            CheckArg.isNotNull(operator, "operator");
            return new RightHandSide(this, operator);
        }

        /**
         * Use the 'equal to' operator in the comparison, returning an interface that can be used to define the right-hand-side of
         * the comparison.
         * 
         * @return the interface used to define the right-hand-side of the comparison
         */
        public RightHandSide isEqualTo() {
            return is(Operator.EQUAL_TO);
        }

        /**
         * Use the 'equal to' operator in the comparison, returning an interface that can be used to define the right-hand-side of
         * the comparison.
         * 
         * @return the interface used to define the right-hand-side of the comparison
         */
        public RightHandSide isNotEqualTo() {
            return is(Operator.NOT_EQUAL_TO);
        }

        /**
         * Use the 'equal to' operator in the comparison, returning an interface that can be used to define the right-hand-side of
         * the comparison.
         * 
         * @return the interface used to define the right-hand-side of the comparison
         */
        public RightHandSide isGreaterThan() {
            return is(Operator.GREATER_THAN);
        }

        /**
         * Use the 'equal to' operator in the comparison, returning an interface that can be used to define the right-hand-side of
         * the comparison.
         * 
         * @return the interface used to define the right-hand-side of the comparison
         */
        public RightHandSide isGreaterThanOrEqualTo() {
            return is(Operator.GREATER_THAN_OR_EQUAL_TO);
        }

        /**
         * Use the 'equal to' operator in the comparison, returning an interface that can be used to define the right-hand-side of
         * the comparison.
         * 
         * @return the interface used to define the right-hand-side of the comparison
         */
        public RightHandSide isLessThan() {
            return is(Operator.LESS_THAN);
        }

        /**
         * Use the 'equal to' operator in the comparison, returning an interface that can be used to define the right-hand-side of
         * the comparison.
         * 
         * @return the interface used to define the right-hand-side of the comparison
         */
        public RightHandSide isLessThanOrEqualTo() {
            return is(Operator.LESS_THAN_OR_EQUAL_TO);
        }

        /**
         * Use the 'equal to' operator in the comparison, returning an interface that can be used to define the right-hand-side of
         * the comparison.
         * 
         * @return the interface used to define the right-hand-side of the comparison
         */
        public RightHandSide isLike() {
            return is(Operator.LIKE);
        }

        /**
         * Define the right-hand-side of the constraint using the supplied operator.
         * 
         * @param operator the operator; may not be null
         * @param variableName the name of the variable
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isVariable( Operator operator,
                                             String variableName ) {
            CheckArg.isNotNull(operator, "operator");
            return this.constraintBuilder.setConstraint(new Comparison(left, operator, new BindVariableName(variableName)));
        }

        /**
         * Define the right-hand-side of the constraint using the supplied operator.
         * 
         * @param operator the operator; may not be null
         * @param literal the literal value
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder is( Operator operator,
                                     Object literal ) {
            assert operator != null;
            return this.constraintBuilder.setConstraint(new Comparison(left, operator, new Literal(literal)));
        }

        /**
         * Define the right-hand-side of the constraint to be equivalent to the value of the supplied variable.
         * 
         * @param variableName the name of the variable
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isEqualToVariable( String variableName ) {
            return isVariable(Operator.EQUAL_TO, variableName);
        }

        /**
         * Define the right-hand-side of the constraint to be greater than the value of the supplied variable.
         * 
         * @param variableName the name of the variable
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isGreaterThanVariable( String variableName ) {
            return isVariable(Operator.GREATER_THAN, variableName);
        }

        /**
         * Define the right-hand-side of the constraint to be greater than or equal to the value of the supplied variable.
         * 
         * @param variableName the name of the variable
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isGreaterThanOrEqualToVariable( String variableName ) {
            return isVariable(Operator.GREATER_THAN_OR_EQUAL_TO, variableName);
        }

        /**
         * Define the right-hand-side of the constraint to be less than the value of the supplied variable.
         * 
         * @param variableName the name of the variable
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isLessThanVariable( String variableName ) {
            return isVariable(Operator.LESS_THAN, variableName);
        }

        /**
         * Define the right-hand-side of the constraint to be less than or equal to the value of the supplied variable.
         * 
         * @param variableName the name of the variable
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isLessThanOrEqualToVariable( String variableName ) {
            return isVariable(Operator.LESS_THAN_OR_EQUAL_TO, variableName);
        }

        /**
         * Define the right-hand-side of the constraint to be LIKE the value of the supplied variable.
         * 
         * @param variableName the name of the variable
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isLikeVariable( String variableName ) {
            return isVariable(Operator.LIKE, variableName);
        }

        /**
         * Define the right-hand-side of the constraint to be not equal to the value of the supplied variable.
         * 
         * @param variableName the name of the variable
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isNotEqualToVariable( String variableName ) {
            return isVariable(Operator.NOT_EQUAL_TO, variableName);
        }

        /**
         * Define the right-hand-side of the constraint to be equivalent to the supplied literal value.
         * 
         * @param literal the literal value
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isEqualTo( Object literal ) {
            return is(Operator.EQUAL_TO, literal);
        }

        /**
         * Define the right-hand-side of the constraint to be greater than the supplied literal value.
         * 
         * @param literal the literal value
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isGreaterThan( Object literal ) {
            return is(Operator.GREATER_THAN, literal);
        }

        /**
         * Define the right-hand-side of the constraint to be greater than or equal to the supplied literal value.
         * 
         * @param literal the literal value
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isGreaterThanOrEqualTo( Object literal ) {
            return is(Operator.GREATER_THAN_OR_EQUAL_TO, literal);
        }

        /**
         * Define the right-hand-side of the constraint to be less than the supplied literal value.
         * 
         * @param literal the literal value
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isLessThan( Object literal ) {
            return is(Operator.LESS_THAN, literal);
        }

        /**
         * Define the right-hand-side of the constraint to be less than or equal to the supplied literal value.
         * 
         * @param literal the literal value
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isLessThanOrEqualTo( Object literal ) {
            return is(Operator.LESS_THAN_OR_EQUAL_TO, literal);
        }

        /**
         * Define the right-hand-side of the constraint to be LIKE the supplied literal value.
         * 
         * @param literal the literal value
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isLike( Object literal ) {
            return is(Operator.LIKE, literal);
        }

        /**
         * Define the right-hand-side of the constraint to be not equal to the supplied literal value.
         * 
         * @param literal the literal value
         * @return the builder used to create the constraint clause, ready to be used to create other constraints clauses or
         *         complete already-started clauses; never null
         */
        public ConstraintBuilder isNotEqualTo( Object literal ) {
            return is(Operator.NOT_EQUAL_TO, literal);
        }
    }
}
