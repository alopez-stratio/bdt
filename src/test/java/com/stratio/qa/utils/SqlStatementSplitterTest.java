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

import org.testng.annotations.Test;

import static com.stratio.qa.utils.SqlStatementSplitter.splitSqlStatements;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link SqlStatementSplitter}.
 */
public class SqlStatementSplitterTest {

    @Test
    public void splitsStatementsOnSemicolon() {
        assertThat(splitSqlStatements(
                "CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;\n"
                        + "CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;\n"))
                .containsExactly(
                        "CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public",
                        "CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public");
    }

    @Test
    public void keepsAMultiLineStatementInOnePiece() {
        assertThat(splitSqlStatements(
                "CREATE TABLE films (\n"
                        + "  code char(5),\n"
                        + "  title varchar(40)\n"
                        + ");"))
                .containsExactly("CREATE TABLE films (\n  code char(5),\n  title varchar(40)\n)");
    }

    @Test
    public void returnsATrailingStatementWithNoSemicolon() {
        assertThat(splitSqlStatements("SELECT 1;\nSELECT 2")).containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    public void discardsEmptyStatements() {
        assertThat(splitSqlStatements(";;\n  \nSELECT 1;;\n")).containsExactly("SELECT 1");
        assertThat(splitSqlStatements("")).isEmpty();
        assertThat(splitSqlStatements("-- nothing but a comment\n")).isEmpty();
    }

    @Test
    public void doesNotSplitOnASemicolonInsideALiteral() {
        assertThat(splitSqlStatements("INSERT INTO t VALUES ('a;b', \"c;d\");\nSELECT 1;"))
                .containsExactly("INSERT INTO t VALUES ('a;b', \"c;d\")", "SELECT 1");
    }

    @Test
    public void honoursTheDoubledQuoteEscape() {
        assertThat(splitSqlStatements("INSERT INTO t VALUES ('it''s; here');\nSELECT 1;"))
                .containsExactly("INSERT INTO t VALUES ('it''s; here')", "SELECT 1");
    }

    @Test
    public void doesNotSplitOnASemicolonInsideADollarQuotedBody() {
        assertThat(splitSqlStatements(
                "CREATE FUNCTION f() RETURNS int AS $$\n"
                        + "BEGIN\n"
                        + "  SELECT 1; SELECT 2;\n"
                        + "END;\n"
                        + "$$ LANGUAGE plpgsql;\n"
                        + "SELECT f();"))
                .containsExactly(
                        "CREATE FUNCTION f() RETURNS int AS $$\nBEGIN\n  SELECT 1; SELECT 2;\nEND;\n$$ LANGUAGE plpgsql",
                        "SELECT f()");
    }

    @Test
    public void doesNotSplitOnASemicolonInsideATaggedDollarQuotedBody() {
        assertThat(splitSqlStatements(
                "CREATE FUNCTION f() RETURNS int AS $body$ SELECT 1; $body$ LANGUAGE sql;\nSELECT 2;"))
                .containsExactly(
                        "CREATE FUNCTION f() RETURNS int AS $body$ SELECT 1; $body$ LANGUAGE sql",
                        "SELECT 2");
    }

    @Test
    public void treatsADollarNumberAsAParameterAndNotAsATag() {
        assertThat(splitSqlStatements("SELECT $1; SELECT $2;")).containsExactly("SELECT $1", "SELECT $2");
    }

    @Test
    public void dropsComments() {
        assertThat(splitSqlStatements(
                "-- Extensions required by Hindsight\n"
                        + "SELECT 1; -- a; semicolon in a comment\n"
                        + "SELECT/* and; in a block */2;"))
                .containsExactly("SELECT 1", "SELECT 2");
    }
}
