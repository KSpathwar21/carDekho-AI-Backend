package com.carDekhoAI.sql.validator;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.springframework.stereotype.Component;

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
}
