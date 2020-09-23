/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.cache.decorators;

import java.text.MessageFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * 一个简单的阻塞装饰器。
 * 一个简单的低效的 EhCache's BlockingCache 装饰器。当元素不存在缓存中的时候，它设置一个锁。
 * 这样其他线程将会等待，直到元素被填充，而不是直接访问数据库。
 * 本质上，如果使用不当，它将会造成死锁。
 *
 * 主要是用在同一时刻，大量请求涌进来时，查询一个数据库中不存在的数据。当一个 session 查询时
 *
 * <p>Simple blocking decorator
 *
 * <p>Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * <p>By its nature, this implementation can cause deadlock when used incorrecly.
 *
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {

  private long timeout;
  private final Cache delegate;
  private final ConcurrentHashMap<Object, CountDownLatch> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value);
    } finally {
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    // 获取锁
    acquireLock(key);
    // 获取对象
    Object value = delegate.getObject(key);
    if (value != null) {
      // 获取的数据不为空，释放锁
      releaseLock(key);
    }
    // 如果 value 为空，则一直不释放锁，让其他查询此 key 的线程永久阻塞，直到该 key 对应的 value 被添加到缓存中，或者调用删除 key 操作，才会释放锁。
    // 这样的操作是用于解决缓存穿透问题，防止大量请求访问一个目前不存在的数据
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite of its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  private void acquireLock(Object key) {
    // 创建一个倒计时闭锁
    CountDownLatch newLatch = new CountDownLatch(1);
    while (true) {
      // 根据给定的 key，放入对应的闭锁
      // 如果 key 对应的闭锁不存在，则放入闭锁，如果存在则不放入，返回以前的值
      CountDownLatch latch = locks.putIfAbsent(key, newLatch);
      if (latch == null) {
        // latch 为 null 说明放入成功，则退出
        break;
      }
      // latch 不为空，说已经有线程放入了 key 对应的闭锁，那就让闭锁阻塞 await，直到闭锁被放入它的线程解锁
      try {
        if (timeout > 0) {
          boolean acquired = latch.await(timeout, TimeUnit.MILLISECONDS);
          if (!acquired) {
            throw new CacheException(
                "Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
          }
        } else {
          latch.await();
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    }
  }

  /**
   * 释放锁，它会在保存对象、查询到对象、移除对象时进行调用
   *
   * @param key
   */
  private void releaseLock(Object key) {
    // 释放一个锁
    CountDownLatch latch = locks.remove(key);
    if (latch == null) {
      throw new IllegalStateException("Detected an attempt at releasing unacquired lock. This should never happen.");
    }
    // 倒计时
    latch.countDown();
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
