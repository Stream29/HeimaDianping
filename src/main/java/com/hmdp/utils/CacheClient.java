package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(
            @NotNull String key,
            @NotNull Object value,
            @NotNull Long time,
            @NotNull TimeUnit unit
    ) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(
            @NotNull String key,
            @NotNull Object value,
            @NotNull Long time,
            @NotNull TimeUnit unit
    ) {
        // 设置逻辑过期
        val redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> @Nullable R queryWithPassThrough(
            @NotNull String keyPrefix,
            @NotNull ID id,
            @NotNull Class<R> type,
            @NotNull Function<ID, R> dbFallback,
            @NotNull Long time,
            @NotNull TimeUnit unit
    ) {
        val key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        val json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 返回一个错误信息
            return null;
        }

        // 4.不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        // 5.不存在，返回错误
        if (r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        } else {
            // 6.存在，写入redis
            this.set(key, r, time, unit);
        }
        return r;
    }

    public <R, ID> @Nullable R queryWithLogicalExpire(
            @NotNull String keyPrefix,
            @NotNull ID id,
            @NotNull Class<R> type,
            @NotNull Function<ID, R> dbFallback,
            @NotNull Long time,
            @NotNull TimeUnit unit
    ) {
        val key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        val json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.存在，直接返回
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        val redisData = JSONUtil.toBean(json, RedisData.class);
        val r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        val expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return r;
        }
        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        val lockKey = LOCK_SHOP_KEY + id;
        val lock = new RedisLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (lock.tryLock()) {
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 重建缓存
                    setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            });
        }
        // 6.4.返回过期的商铺信息
        return r;
    }

    public <R, ID> @Nullable R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, @NotNull Function<ID, R> dbFallback, Long time, @NotNull TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(shopJson, type);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }

        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        try(RedisLock lock = new RedisLock(lockKey).also(RedisLock::lock)) {
            R r = dbFallback.apply(id);
            // 5.不存在，返回错误
            if (r == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，写入redis
            this.set(key, r, time, unit);
            return r;
        }
    }

    @AllArgsConstructor
    private class RedisLock implements AutoCloseable,Functional<RedisLock> {
        private final String key;

        public boolean tryLock() {
            Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
            return BooleanUtil.isTrue(flag);
        }

        public void lock() {
            while (!tryLock()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void unlock() {
            stringRedisTemplate.delete(key);
        }

        @Override
        public void close() {
            unlock();
        }
    }
}

