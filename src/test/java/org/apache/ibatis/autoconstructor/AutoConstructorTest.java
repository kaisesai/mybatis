/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.autoconstructor;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.util.List;
import java.util.Timer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoConstructorTest {

  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void setUp() throws Exception {
    // create a SqlSessionFactory
    try (
      Reader reader = Resources
        .getResourceAsReader("org/apache/ibatis/autoconstructor/mybatis-config.xml")
    ) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }

    // populate in-memory database
    BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
                           "org/apache/ibatis/autoconstructor/CreateDB.sql");
  }

  @Test
  void testBlockCache() {
    // 测试一级缓存：同一个 session 下的查询数据
    for (int i = 0; i < 10; i++) {
      new Thread(() -> {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
          final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
          mapper.selectOneById(999);
        }
      }).start();
    }

    // cache 中的 block 设置为 true，多个线程访问一个不存在的数据，会一直阻塞
    while(Thread.activeCount() > 1){
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  @Test
  void testLevel2Cache() {
    // 测试一级缓存：同一个 session 下的查询数据
    PrimitiveSubject ps1;
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
       ps1 = mapper.selectOneById(999);
      PrimitiveSubject ps2 = mapper.selectOneById(999);
      System.out.println("ps1 = " + ps1);
      System.out.println("ps2 = " + ps2);
      System.out.println(ps1 == ps2);

    }

    // 再开启一个 session
    try (SqlSession sqlSession2 = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper2 = sqlSession2.getMapper(AutoConstructorMapper.class);
      PrimitiveSubject ps3 = mapper2.selectOneById(999);
      System.out.println("ps3 = " + ps3);
      // 两个 session 查出来的数据不相同
      System.out.println(ps1 == ps3);
    }
  }

  @Test
  void testLevel1Cache() {
    // 测试一级缓存：同一个 session 下的查询数据
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      PrimitiveSubject ps1 = mapper.selectOneById(1);
      PrimitiveSubject ps2 = mapper.selectOneById(1);
      System.out.println("ps1 = " + ps2);
      System.out.println(ps1 == ps2);

      // 再开启一个 session
      // try (SqlSession sqlSession2 = sqlSessionFactory.openSession()) {
      //   final AutoConstructorMapper mapper2 = sqlSession2.getMapper(AutoConstructorMapper.class);
      //   PrimitiveSubject ps3 = mapper2.selectOneById(1);
      //   // 两个 session 查出来的数据不相同
      //   System.out.println(ps1 == ps3);
      // }
    }
  }

  @Test
  void selectOneById() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      // 测试环境
      Environment environment = sqlSessionFactory.getConfiguration().getEnvironment();
      System.out.println("environment = " + environment.getId());

      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      PrimitiveSubject ps1 = mapper.selectOneById(1);
      System.out.println("ps1 = " + ps1);
    }
  }

  @Test
  void fullyPopulatedSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      final Object subject = mapper.getSubject(1);
      System.out.println(subject);
      assertNotNull(subject);
    }
  }

  @Test
  void primitiveSubjects() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      assertThrows(PersistenceException.class, mapper::getSubjects);
    }
  }

  @Test
  void annotatedSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      verifySubjects(mapper.getAnnotatedSubjects());
    }
  }

  @Test
  void badSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      assertThrows(PersistenceException.class, mapper::getBadSubjects);
    }
  }

  @Test
  void extensiveSubject() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final AutoConstructorMapper mapper = sqlSession.getMapper(AutoConstructorMapper.class);
      verifySubjects(mapper.getExtensiveSubjects());
    }
  }

  private void verifySubjects(final List<?> subjects) {
    assertNotNull(subjects);
    Assertions.assertThat(subjects.size()).isEqualTo(3);
  }

}
