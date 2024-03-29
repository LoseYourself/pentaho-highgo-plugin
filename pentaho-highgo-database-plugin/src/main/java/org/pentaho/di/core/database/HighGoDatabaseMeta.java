/*******************************************************************************
 *
 * 
 *
 * Copyright (C) 2011-2019 by Sun : http://www.kingbase.com.cn
 *
 *******************************************************************************
 *
 *
 *    Email : snj1314@163.com
 *
 *
 ******************************************************************************/

package org.pentaho.di.core.database;

import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleDatabaseException;
import org.pentaho.di.core.plugins.DatabaseMetaPlugin;
import org.pentaho.di.core.row.ValueMetaInterface;

/**
 * 
 * 
 * @author Sun
 * @since 2019年9月5日
 * @version
 * 
 */
@DatabaseMetaPlugin(type = "HIGHGO", typeDescription = "HighGo Database")
public class HighGoDatabaseMeta extends BaseDatabaseMeta implements DatabaseInterface {

  @Override
  public String getExtraOptionSeparator() {
    return "&";
  }

  @Override
  public String getExtraOptionIndicator() {
    return "?";
  }

  @Override
  public int[] getAccessTypeList() {
    return new int[] { DatabaseMeta.TYPE_ACCESS_NATIVE, DatabaseMeta.TYPE_ACCESS_ODBC, };
  }

  @Override
  public int getDefaultDatabasePort() {
    if (getAccessType() == DatabaseMeta.TYPE_ACCESS_NATIVE) {
      return 5866;
    }
    return -1;
  }

  @Override
  public String getDriverClass() {
    if (getAccessType() == DatabaseMeta.TYPE_ACCESS_ODBC) {
      return "sun.jdbc.odbc.JdbcOdbcDriver";
    } else {
      return "com.highgo.jdbc.Driver";
    }
  }

  @Override
  public String getURL(String hostname, String port, String databaseName) throws KettleDatabaseException {
    if (getAccessType() == DatabaseMeta.TYPE_ACCESS_ODBC) {
      return "jdbc:odbc:" + databaseName;
    } else {
      return "jdbc:highgo://" + hostname + ":" + port + "/" + databaseName;
    }
  }

  @Override
  public boolean supportsBitmapIndex() {
    return false;
  }

  @Override
  public boolean supportsSequences() {
    return true;
  }

  @Override
  public boolean supportsSequenceNoMaxValueOption() {
    return true;
  }

  @Override
  public String getLimitClause(int nrRows) {
    return " limit " + nrRows;
  }

  @Override
  public String getSQLQueryFields(String tableName) {
    return "SELECT * FROM " + tableName + getLimitClause(1);
  }

  @Override
  public String getSQLTableExists(String tablename) {
    return getSQLQueryFields(tablename);
  }

  @Override
  public String getSQLColumnExists(String columnname, String tablename) {
    return getSQLQueryColumnFields(columnname, tablename);
  }

  public String getSQLQueryColumnFields(String columnname, String tableName) {
    return "SELECT " + columnname + " FROM " + tableName + getLimitClause(1);
  }

  @Override
  public boolean needsToLockAllTables() {
    return false;
  }

  @Override
  public String getSQLListOfSequences() {
    return "SELECT relname AS sequence_name FROM pg_catalog.pg_statio_all_sequences";
  }

  @Override
  public String getSQLNextSequenceValue(String sequenceName) {
    return "SELECT nextval('" + sequenceName + "')";
  }

  @Override
  public String getSQLCurrentSequenceValue(String sequenceName) {
    return "SELECT currval('" + sequenceName + "')";
  }

  @Override
  public String getSQLSequenceExists(String sequenceName) {
    return "SELECT relname AS sequence_name FROM pg_catalog.pg_statio_all_sequences WHERE relname = '" + sequenceName.toLowerCase() + "'";
  }

  @Override
  public String getAddColumnStatement(String tablename, ValueMetaInterface v, String tk, boolean use_autoinc, String pk, boolean semicolon) {
    return "ALTER TABLE " + tablename + " ADD COLUMN " + getFieldDefinition(v, tk, pk, use_autoinc, true, false);
  }

  @Override
  public String getModifyColumnStatement(String tablename, ValueMetaInterface v, String tk, boolean use_autoinc, String pk, boolean semicolon) {
    String retval = "";

    ValueMetaInterface tmpColumn = v.clone();

    String tmpName = v.getName();
    boolean isQuoted = tmpName.startsWith(getStartQuote()) && tmpName.endsWith(getEndQuote());
    if (isQuoted) {
      // remove the quotes first.
      //
      tmpName = tmpName.substring(1, tmpName.length() - 1);
    }

    tmpName += "_KTL";

    // put the quotes back if needed.
    //
    if (isQuoted) {
      tmpName = getStartQuote() + tmpName + getEndQuote();
    }
    tmpColumn.setName(tmpName);

    // Create a new tmp column
    retval += getAddColumnStatement(tablename, tmpColumn, tk, use_autoinc, pk, semicolon) + ";" + Const.CR;
    // copy the old data over to the tmp column
    retval += "UPDATE " + tablename + " SET " + tmpColumn.getName() + "=" + v.getName() + ";" + Const.CR;
    // drop the old column
    retval += getDropColumnStatement(tablename, v, tk, use_autoinc, pk, semicolon) + ";" + Const.CR;
    // rename the temp column to replace the removed column
    retval += "ALTER TABLE " + tablename + " RENAME " + tmpColumn.getName() + " TO " + v.getName() + ";" + Const.CR;
    return retval;
  }

  @Override
  public String getDropColumnStatement(String tablename, ValueMetaInterface v, String tk, boolean use_autoinc, String pk, boolean semicolon) {
    return "ALTER TABLE " + tablename + " DROP COLUMN " + v.getName();
  }

  @Override
  public String getFieldDefinition(ValueMetaInterface v, String tk, String pk, boolean use_autoinc, boolean add_fieldname, boolean add_cr) {
    String retval = "";

    String fieldname = v.getName();
    int length = v.getLength();
    int precision = v.getPrecision();

    if (add_fieldname) {
      retval += fieldname + " ";
    }

    int type = v.getType();
    switch (type) {
    case ValueMetaInterface.TYPE_TIMESTAMP:
    case ValueMetaInterface.TYPE_DATE:
      retval += "TIMESTAMP";
      break;
    case ValueMetaInterface.TYPE_BOOLEAN:
      if (supportsBooleanDataType()) {
        retval += "BOOLEAN";
      } else {
        retval += "CHAR(1)";
      }
      break;
    case ValueMetaInterface.TYPE_NUMBER:
    case ValueMetaInterface.TYPE_INTEGER:
    case ValueMetaInterface.TYPE_BIGNUMBER:
      if (fieldname.equalsIgnoreCase(tk) || // Technical key
          fieldname.equalsIgnoreCase(pk) // Primary key
      ) {
        retval += "BIGSERIAL";
      } else {
        if (length > 0) {
          if (precision > 0 || length > 18) {
            // Numeric(Precision, Scale): Precision = total length; Scale = decimal places
            retval += "NUMERIC(" + (length + precision) + ", " + precision + ")";
          } else {
            if (length > 9) {
              retval += "BIGINT";
            } else {
              if (length < 5) {
                retval += "SMALLINT";
              } else {
                retval += "INTEGER";
              }
            }
          }

        } else {
          retval += "DOUBLE PRECISION";
        }
      }
      break;
    case ValueMetaInterface.TYPE_STRING:
      if (length < 1 || length >= DatabaseMeta.CLOB_LENGTH) {
        retval += "TEXT";
      } else {
        retval += "VARCHAR(" + length + ")";
      }
      break;
    default:
      retval += " UNKNOWN";
      break;
    }

    if (add_cr) {
      retval += Const.CR;
    }

    return retval;
  }

  @Override
  public String getSQLListOfProcedures() {
    return "select proname " + "from pg_proc, pg_user " + "where pg_user.usesysid = pg_proc.proowner " + "and upper(pg_user.usename) = '"
        + getUsername().toUpperCase() + "' " + "order by proname";
  }
  
  @Override
  public String[] getReservedWords() {
    return new String[] {
      // http://www.postgresql.org/docs/8.1/static/sql-keywords-appendix.html
      // added also non-reserved key words because there is progress from the Postgre developers to add them
      "A", "ABORT", "ABS", "ABSOLUTE", "ACCESS", "ACTION", "ADA", "ADD", "ADMIN", "AFTER", "AGGREGATE", "ALIAS", "ALL",
      "ALLOCATE", "ALSO", "ALTER", "ALWAYS", "ANALYSE", "ANALYZE", "AND", "ANY", "ARE", "ARRAY", "AS", "ASC",
      "ASENSITIVE", "ASSERTION", "ASSIGNMENT", "ASYMMETRIC", "AT", "ATOMIC", "ATTRIBUTE", "ATTRIBUTES",
      "AUTHORIZATION", "AVG", "BACKWARD", "BEFORE", "BEGIN", "BERNOULLI", "BETWEEN", "BIGINT", "BINARY", "BIT",
      "BITVAR", "BIT_LENGTH", "BLOB", "BOOLEAN", "BOTH", "BREADTH", "BY", "C", "CACHE", "CALL", "CALLED",
      "CARDINALITY", "CASCADE", "CASCADED", "CASE", "CAST", "CATALOG", "CATALOG_NAME", "CEIL", "CEILING", "CHAIN",
      "CHAR", "CHARACTER", "CHARACTERISTICS", "CHARACTERS", "CHARACTER_LENGTH", "CHARACTER_SET_CATALOG",
      "CHARACTER_SET_NAME", "CHARACTER_SET_SCHEMA", "CHAR_LENGTH", "CHECK", "CHECKED", "CHECKPOINT", "CLASS",
      "CLASS_ORIGIN", "CLOB", "CLOSE", "CLUSTER", "COALESCE", "COBOL", "COLLATE", "COLLATION", "COLLATION_CATALOG",
      "COLLATION_NAME", "COLLATION_SCHEMA", "COLLECT", "COLUMN", "COLUMN_NAME", "COMMAND_FUNCTION",
      "COMMAND_FUNCTION_CODE", "COMMENT", "COMMIT", "COMMITTED", "COMPLETION", "CONDITION", "CONDITION_NUMBER",
      "CONNECT", "CONNECTION", "CONNECTION_NAME", "CONSTRAINT", "CONSTRAINTS", "CONSTRAINT_CATALOG", "CONSTRAINT_NAME",
      "CONSTRAINT_SCHEMA", "CONSTRUCTOR", "CONTAINS", "CONTINUE", "CONVERSION", "CONVERT", "COPY", "CORR",
      "CORRESPONDING", "COUNT", "COVAR_POP", "COVAR_SAMP", "CREATE", "CREATEDB", "CREATEROLE", "CREATEUSER", "CROSS",
      "CSV", "CUBE", "CUME_DIST", "CURRENT", "CURRENT_DATE", "CURRENT_DEFAULT_TRANSFORM_GROUP", "CURRENT_PATH",
      "CURRENT_ROLE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_TRANSFORM_GROUP_FOR_TYPE", "CURRENT_USER",
      "CURSOR", "CURSOR_NAME", "CYCLE", "DATA", "DATABASE", "DATE", "DATETIME_INTERVAL_CODE",
      "DATETIME_INTERVAL_PRECISION", "DAY", "DEALLOCATE", "DEC", "DECIMAL", "DECLARE", "DEFAULT", "DEFAULTS",
      "DEFERRABLE", "DEFERRED", "DEFINED", "DEFINER", "DEGREE", "DELETE", "DELIMITER", "DELIMITERS", "DENSE_RANK",
      "DEPTH", "DEREF", "DERIVED", "DESC", "DESCRIBE", "DESCRIPTOR", "DESTROY", "DESTRUCTOR", "DETERMINISTIC",
      "DIAGNOSTICS", "DICTIONARY", "DISABLE", "DISCONNECT", "DISPATCH", "DISTINCT", "DO", "DOMAIN", "DOUBLE", "DROP",
      "DYNAMIC", "DYNAMIC_FUNCTION", "DYNAMIC_FUNCTION_CODE", "EACH", "ELEMENT", "ELSE", "ENABLE", "ENCODING",
      "ENCRYPTED", "END", "END-EXEC", "EQUALS", "ESCAPE", "EVERY", "EXCEPT", "EXCEPTION", "EXCLUDE", "EXCLUDING",
      "EXCLUSIVE", "EXEC", "EXECUTE", "EXISTING", "EXISTS", "EXP", "EXPLAIN", "EXTERNAL", "EXTRACT", "FALSE", "FETCH",
      "FILTER", "FINAL", "FIRST", "FLOAT", "FLOOR", "FOLLOWING", "FOR", "FORCE", "FOREIGN", "FORTRAN", "FORWARD",
      "FOUND", "FREE", "FREEZE", "FROM", "FULL", "FUNCTION", "FUSION", "G", "GENERAL", "GENERATED", "GET", "GLOBAL",
      "GO", "GOTO", "GRANT", "GRANTED", "GREATEST", "GROUP", "GROUPING", "HANDLER", "HAVING", "HEADER", "HIERARCHY",
      "HOLD", "HOST", "HOUR", "IDENTITY", "IGNORE", "ILIKE", "IMMEDIATE", "IMMUTABLE", "IMPLEMENTATION", "IMPLICIT",
      "IN", "INCLUDING", "INCREMENT", "INDEX", "INDICATOR", "INFIX", "INHERIT", "INHERITS", "INITIALIZE", "INITIALLY",
      "INNER", "INOUT", "INPUT", "INSENSITIVE", "INSERT", "INSTANCE", "INSTANTIABLE", "INSTEAD", "INT", "INTEGER",
      "INTERSECT", "INTERSECTION", "INTERVAL", "INTO", "INVOKER", "IS", "ISNULL", "ISOLATION", "ITERATE", "JOIN", "K",
      "KEY", "KEY_MEMBER", "KEY_TYPE", "LANCOMPILER", "LANGUAGE", "LARGE", "LAST", "LATERAL", "LEADING", "LEAST",
      "LEFT", "LENGTH", "LESS", "LEVEL", "LIKE", "LIMIT", "LISTEN", "LN", "LOAD", "LOCAL", "LOCALTIME",
      "LOCALTIMESTAMP", "LOCATION", "LOCATOR", "LOCK", "LOGIN", "LOWER", "M", "MAP", "MATCH", "MATCHED", "MAX",
      "MAXVALUE", "MEMBER", "MERGE", "MESSAGE_LENGTH", "MESSAGE_OCTET_LENGTH", "MESSAGE_TEXT", "METHOD", "MIN",
      "MINUTE", "MINVALUE", "MOD", "MODE", "MODIFIES", "MODIFY", "MODULE", "MONTH", "MORE", "MOVE", "MULTISET",
      "MUMPS", "NAME", "NAMES", "NATIONAL", "NATURAL", "NCHAR", "NCLOB", "NESTING", "NEW", "NEXT", "NO", "NOCREATEDB",
      "NOCREATEROLE", "NOCREATEUSER", "NOINHERIT", "NOLOGIN", "NONE", "NORMALIZE", "NORMALIZED", "NOSUPERUSER", "NOT",
      "NOTHING", "NOTIFY", "NOTNULL", "NOWAIT", "NULL", "NULLABLE", "NULLIF", "NULLS", "NUMBER", "NUMERIC", "OBJECT",
      "OCTETS", "OCTET_LENGTH", "OF", "OFF", "OFFSET", "OIDS", "OLD", "ON", "ONLY", "OPEN", "OPERATION", "OPERATOR",
      "OPTION", "OPTIONS", "OR", "ORDER", "ORDERING", "ORDINALITY", "OTHERS", "OUT", "OUTER", "OUTPUT", "OVER",
      "OVERLAPS", "OVERLAY", "OVERRIDING", "OWNER", "PAD", "PARAMETER", "PARAMETERS", "PARAMETER_MODE",
      "PARAMETER_NAME", "PARAMETER_ORDINAL_POSITION", "PARAMETER_SPECIFIC_CATALOG", "PARAMETER_SPECIFIC_NAME",
      "PARAMETER_SPECIFIC_SCHEMA", "PARTIAL", "PARTITION", "PASCAL", "PASSWORD", "PATH", "PERCENTILE_CONT",
      "PERCENTILE_DISC", "PERCENT_RANK", "PLACING", "PLI", "POSITION", "POSTFIX", "POWER", "PRECEDING", "PRECISION",
      "PREFIX", "PREORDER", "PREPARE", "PREPARED", "PRESERVE", "PRIMARY", "PRIOR", "PRIVILEGES", "PROCEDURAL",
      "PROCEDURE", "PUBLIC", "QUOTE", "RANGE", "RANK", "READ", "READS", "REAL", "RECHECK", "RECURSIVE", "REF",
      "REFERENCES", "REFERENCING", "REGR_AVGX", "REGR_AVGY", "REGR_COUNT", "REGR_INTERCEPT", "REGR_R2", "REGR_SLOPE",
      "REGR_SXX", "REGR_SXY", "REGR_SYY", "REINDEX", "RELATIVE", "RELEASE", "RENAME", "REPEATABLE", "REPLACE", "RESET",
      "RESTART", "RESTRICT", "RESULT", "RETURN", "RETURNED_CARDINALITY", "RETURNED_LENGTH", "RETURNED_OCTET_LENGTH",
      "RETURNED_SQLSTATE", "RETURNS", "REVOKE", "RIGHT", "ROLE", "ROLLBACK", "ROLLUP", "ROUTINE", "ROUTINE_CATALOG",
      "ROUTINE_NAME", "ROUTINE_SCHEMA", "ROW", "ROWS", "ROW_COUNT", "ROW_NUMBER", "RULE", "SAVEPOINT", "SCALE",
      "SCHEMA", "SCHEMA_NAME", "SCOPE", "SCOPE_CATALOG", "SCOPE_NAME", "SCOPE_SCHEMA", "SCROLL", "SEARCH", "SECOND",
      "SECTION", "SECURITY", "SELECT", "SELF", "SENSITIVE", "SEQUENCE", "SERIALIZABLE", "SERVER_NAME", "SESSION",
      "SESSION_USER", "SET", "SETOF", "SETS", "SHARE", "SHOW", "SIMILAR", "SIMPLE", "SIZE", "SMALLINT", "SOME",
      "SOURCE", "SPACE", "SPECIFIC", "SPECIFICTYPE", "SPECIFIC_NAME", "SQL", "SQLCODE", "SQLERROR", "SQLEXCEPTION",
      "SQLSTATE", "SQLWARNING", "SQRT", "STABLE", "START", "STATE", "STATEMENT", "STATIC", "STATISTICS", "STDDEV_POP",
      "STDDEV_SAMP", "STDIN", "STDOUT", "STORAGE", "STRICT", "STRUCTURE", "STYLE", "SUBCLASS_ORIGIN", "SUBLIST",
      "SUBMULTISET", "SUBSTRING", "SUM", "SUPERUSER", "SYMMETRIC", "SYSID", "SYSTEM", "SYSTEM_USER", "TABLE",
      "TABLESAMPLE", "TABLESPACE", "TABLE_NAME", "TEMP", "TEMPLATE", "TEMPORARY", "TERMINATE", "THAN", "THEN", "TIES",
      "TIME", "TIMESTAMP", "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TO", "TOAST", "TOP_LEVEL_COUNT", "TRAILING",
      "TRANSACTION", "TRANSACTIONS_COMMITTED", "TRANSACTIONS_ROLLED_BACK", "TRANSACTION_ACTIVE", "TRANSFORM",
      "TRANSFORMS", "TRANSLATE", "TRANSLATION", "TREAT", "TRIGGER", "TRIGGER_CATALOG", "TRIGGER_NAME",
      "TRIGGER_SCHEMA", "TRIM", "TRUE", "TRUNCATE", "TRUSTED", "TYPE", "UESCAPE", "UNBOUNDED", "UNCOMMITTED", "UNDER",
      "UNENCRYPTED", "UNION", "UNIQUE", "UNKNOWN", "UNLISTEN", "UNNAMED", "UNNEST", "UNTIL", "UPDATE", "UPPER",
      "USAGE", "USER", "USER_DEFINED_TYPE_CATALOG", "USER_DEFINED_TYPE_CODE", "USER_DEFINED_TYPE_NAME",
      "USER_DEFINED_TYPE_SCHEMA", "USING", "VACUUM", "VALID", "VALIDATOR", "VALUE", "VALUES", "VARCHAR", "VARIABLE",
      "VARYING", "VAR_POP", "VAR_SAMP", "VERBOSE", "VIEW", "VOLATILE", "WHEN", "WHENEVER", "WHERE", "WIDTH_BUCKET",
      "WINDOW", "WITH", "WITHIN", "WITHOUT", "WORK", "WRITE", "YEAR", "ZONE" };
  }

  @Override
  public String getSQLLockTables(String[] tableNames) {
    String sql = "LOCK TABLE ";
    for (int i = 0; i < tableNames.length; i++) {
      if (i > 0) {
        sql += ", ";
      }
      sql += tableNames[i] + " ";
    }
    sql += "IN ACCESS EXCLUSIVE MODE;" + Const.CR;

    return sql;
  }

  @Override
  public boolean isDefaultingToUppercase() {
    return false;
  }

  @Override
  public String[] getUsedLibraries() {
    return new String[] { "hgdb-4.x-jdbc41.jar" };
  }

  @Override
  public String getExtraOptionsHelpText() {
    return "http://jdbc.postgresql.org/documentation/83/connect.html#connection-parameters";
  }

  @Override
  public boolean supportsErrorHandlingOnBatchUpdates() {
    return false;
  }

  @Override
  public String quoteSQLString(String string) {
    string = string.replaceAll("'", "''");
    string = string.replaceAll("\\n", "\\\\n");
    string = string.replaceAll("\\r", "\\\\r");
    return "E'" + string + "'";
  }

  @Override
  public boolean requiresCastToVariousForIsNull() {
    return true;
  }

  @Override
  public boolean supportsGetBlob() {
    return false;
  }

  @Override
  public boolean useSafePoints() {
    return true;
  }

}
