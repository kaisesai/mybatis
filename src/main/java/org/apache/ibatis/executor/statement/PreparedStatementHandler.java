/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 */
public class PreparedStatementHandler extends BaseStatementHandler {

  public PreparedStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    super(executor, mappedStatement, parameter, rowBounds, resultHandler, boundSql);
  }

  @Override
  public int update(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    // 执行更新
    ps.execute();
    // 获取更新的行数
    int rows = ps.getUpdateCount();
    // 获取参数对象
    Object parameterObject = boundSql.getParameterObject();
    // 获取键生成器
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    // 后置处理器键，比如这里会针对 insert 语句，会设置插入之后的主键到参数对象上。
    keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
    return rows;
  }

  @Override
  public void batch(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    // 批量查询
    ps.addBatch();
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    // 执行查询
    ps.execute();
    // 通过结果集处理器处理结果
    return resultSetHandler.handleResultSets(ps);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    // 执行查询
    ps.execute();
    // 结果集处理器处理数据
    return resultSetHandler.handleCursorResultSets(ps);
  }

  /**
   * 初始化一个 Statement
   *
   * @param connection
   * @return
   * @throws SQLException
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    String sql = boundSql.getSql();
    if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
      String[] keyColumnNames = mappedStatement.getKeyColumns();
      if (keyColumnNames == null) {
        return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
      } else {
        return connection.prepareStatement(sql, keyColumnNames);
      }
    } else if (mappedStatement.getResultSetType() == ResultSetType.DEFAULT) {
      //
      return connection.prepareStatement(sql);
    } else {
      return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    }
  }

  @Override
  public void parameterize(Statement statement) throws SQLException {
    // 使用参数化对象进行设置参数
    parameterHandler.setParameters((PreparedStatement) statement);
  }

}
