/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.qa.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a block of SQL into the statements it holds.
 * <p>
 * Holds no state and knows nothing about how the statements are then executed, so it is shared by
 * every step taking a block of SQL - a docstring or the contents of a .sql file.
 */
public final class SqlStatementSplitter {

    private SqlStatementSplitter() {
    }

    /**
     * Splits a block of SQL into the statements it holds, so a statement can span as many lines as
     * it needs instead of one per line.
     * <p>
     * Statements are separated by {@code ;}. A separator only counts when it sits in plain SQL:
     * {@code ;} inside a single or double quoted literal, inside a dollar-quoted body
     * ({@code $$ ... $$}, {@code $tag$ ... $tag$} - how functions and procedures are written) or
     * inside a comment is part of the statement. {@code --} runs to the end of the line and
     * {@code /* ... *}{@code /} blocks are dropped; both are stripped from the statement text so a
     * comment cannot swallow the rest of it. A trailing statement with no final {@code ;} is
     * returned as well, and empty statements - a stray {@code ;;}, a block of only comments - are
     * discarded.
     *
     * @param sql block of SQL to split
     * @return statements in the order they appear, trimmed, never empty strings
     */
    public static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                int end = endOfQuotedLiteral(sql, i, c);
                current.append(sql, i, end);
                i = end;
            } else if (c == '$' && dollarQuoteTag(sql, i) != null) {
                String tag = dollarQuoteTag(sql, i);
                int end = sql.indexOf(tag, i + tag.length());
                end = end < 0 ? sql.length() : end + tag.length();
                current.append(sql, i, end);
                i = end;
            } else if (c == '-' && sql.startsWith("--", i)) {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? sql.length() : end;
                current.append(' ');
            } else if (c == '/' && sql.startsWith("/*", i)) {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? sql.length() : end + 2;
                current.append(' ');
            } else if (c == ';') {
                addSqlStatement(statements, current);
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        addSqlStatement(statements, current);
        return statements;
    }

    /**
     * Index just past the quoted literal opening at {@code start}, honouring the doubled quote
     * escape ({@code 'it''s'}). An unterminated literal runs to the end of the block, so the error
     * comes from the database instead of from a silently truncated statement.
     */
    private static int endOfQuotedLiteral(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    /**
     * The dollar-quoting tag opening at {@code position} ({@code $$} or {@code $tag$}), or
     * {@code null} when what follows is not one - a positional parameter such as {@code $1}, or a
     * lone {@code $}.
     */
    private static String dollarQuoteTag(String sql, int position) {
        int close = position + 1;
        while (close < sql.length() && (Character.isLetterOrDigit(sql.charAt(close)) || sql.charAt(close) == '_')) {
            close++;
        }
        if (close < sql.length() && sql.charAt(close) == '$') {
            String tag = sql.substring(position, close + 1);
            // $1 is a parameter, $a1$ is a tag: a tag never starts with a digit
            if (tag.length() == 2 || !Character.isDigit(tag.charAt(1))) {
                return tag;
            }
        }
        return null;
    }

    /**
     * Adds what has been accumulated as a statement, unless it holds nothing but whitespace, and
     * empties the buffer either way.
     */
    private static void addSqlStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }
}
