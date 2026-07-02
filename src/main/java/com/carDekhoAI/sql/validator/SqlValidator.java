package com.carDekhoAI.sql.validator;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Last line of defense before any LLM-generated SQL is executed (execution itself is a
 * later milestone). Validates via real AST parsing (JSqlParser) rather than keyword
 * regex, so forbidden statement types, multiple statements, and comment-based
 * obfuscation are rejected structurally instead of by pattern matching.
 */
@Component
public class SqlValidator {

    private static final Pattern COMMENT_PATTERN = Pattern.compile("--.*|/\\*[\\s\\S]*?\\*/|#.*");
    private static final Pattern SELECT_WORD = Pattern.compile("\\bSELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final String ALLOWED_TABLE = "cars";
    private static final Set<String> ALLOWED_COLUMNS = Set.of(
            "id",
            "brand",
            "model",
            "variant",
            "body_type",
            "fuel_type",
            "transmission",
            "price",
            "engine",
            "power",
            "torque",
            "mileage",
            "safety_rating",
            "boot_space",
            "ground_clearance",
            "seat_capacity",
            "review_score",
            "created_at",
            "updated_at");

    public SqlValidationResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            return SqlValidationResult.reject("SQL is empty");
        }
        if (COMMENT_PATTERN.matcher(sql).find()) {
            return SqlValidationResult.reject("SQL must not contain comments");
        }

        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException e) {
            return SqlValidationResult.reject(
                    "SQL could not be parsed (invalid syntax or multiple statements): " + e.getMessage());
        }

        if (statements == null || statements.size() != 1) {
            return SqlValidationResult.reject("SQL must be a single statement");
        }
        Statement statement = statements.get(0);

        if (!(statement instanceof PlainSelect plainSelect)) {
            return SqlValidationResult.reject(
                    "Only a single simple SELECT statement is allowed (no UNION/INTERSECT/EXCEPT "
                            + "or other statement types)");
        }

        if (!(plainSelect.getFromItem() instanceof Table table) || !ALLOWED_TABLE.equalsIgnoreCase(table.getName())) {
            return SqlValidationResult.reject("SQL must only query the 'cars' table");
        }

        if (plainSelect.getJoins() != null && !plainSelect.getJoins().isEmpty()) {
            return SqlValidationResult.reject("SQL must not contain JOIN — only the cars table may be queried");
        }

        if (containsNestedSelect(plainSelect)) {
            return SqlValidationResult.reject("SQL must not contain subqueries");
        }

        Set<String> invalidColumns = invalidColumns(plainSelect);
        if (!invalidColumns.isEmpty()) {
            return SqlValidationResult.reject("SQL contains unknown column(s): " + String.join(", ", invalidColumns));
        }

        return SqlValidationResult.ok();
    }

    private boolean containsNestedSelect(PlainSelect plainSelect) {
        if (expressionContainsSelect(plainSelect.getWhere())) {
            return true;
        }
        if (expressionContainsSelect(plainSelect.getHaving())) {
            return true;
        }
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem<?> item : plainSelect.getSelectItems()) {
                if (expressionContainsSelect(item.getExpression())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean expressionContainsSelect(Expression expression) {
        return expression != null && SELECT_WORD.matcher(expression.toString()).find();
    }

    private Set<String> invalidColumns(PlainSelect plainSelect) {
        Set<String> invalidColumns = new LinkedHashSet<>();
        ColumnCollector columnCollector = new ColumnCollector(invalidColumns);

        collectColumns(plainSelect.getWhere(), columnCollector);
        collectColumns(plainSelect.getHaving(), columnCollector);

        if (plainSelect.getSelectItems() != null) {
            for (SelectItem<?> item : plainSelect.getSelectItems()) {
                collectColumns(item.getExpression(), columnCollector);
            }
        }

        if (plainSelect.getOrderByElements() != null) {
            for (OrderByElement orderByElement : plainSelect.getOrderByElements()) {
                collectColumns(orderByElement.getExpression(), columnCollector);
            }
        }

        if (plainSelect.getGroupBy() != null && plainSelect.getGroupBy().getGroupByExpressionList() != null) {
            for (Object expression : plainSelect.getGroupBy().getGroupByExpressionList()) {
                if (expression instanceof Expression groupByExpression) {
                    collectColumns(groupByExpression, columnCollector);
                }
            }
        }

        return invalidColumns;
    }

    private void collectColumns(Expression expression, ColumnCollector columnCollector) {
        if (expression != null) {
            expression.accept(columnCollector, null);
        }
    }

    private static final class ColumnCollector extends ExpressionVisitorAdapter<Void> {

        private final Set<String> invalidColumns;

        private ColumnCollector(Set<String> invalidColumns) {
            this.invalidColumns = invalidColumns;
        }

        @Override
        public <S> Void visit(Column column, S context) {
            String tableName = column.getUnquotedTableName();
            String columnName = column.getUnquotedColumnName();

            if ((tableName != null && !ALLOWED_TABLE.equalsIgnoreCase(tableName))
                    || columnName == null
                    || !ALLOWED_COLUMNS.contains(columnName.toLowerCase())) {
                invalidColumns.add(column.getFullyQualifiedName());
            }

            return null;
        }
    }
}
